package com.kuikly.stockchat.watchlist

import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.mock.MockQuoteProvider
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.DisclosureProvider
import com.kuikly.stockchat.data.provider.FundFlowProvider
import com.kuikly.stockchat.data.provider.FundamentalProvider
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.MarketInsightRepository
import com.kuikly.stockchat.data.provider.MarketOverviewProvider
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteProvider
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import com.kuikly.stockchat.testing.reactive
import com.kuikly.stockchat.watchlist.state.KuiklyWatchlistScheduler
import com.kuikly.stockchat.watchlist.state.WatchlistCoordinator
import com.kuikly.stockchat.watchlist.state.WatchlistScheduledTask
import com.kuikly.stockchat.watchlist.state.WatchlistScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 自选列表域单测（doc 47 B-4 完成定义：搜索/分组/筛选/理由/Undo 有测试覆盖）。
 * Store 用真实 [WatchlistStore] + InMemoryKeyValueStorage；行情/洞察用 fake Provider。
 */
class WatchlistCoordinatorTest {

    // ── fakes ──

    private class FakeScheduler : WatchlistScheduler {
        data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)

        private val entries = mutableListOf<Entry>()
        override fun schedule(delayMillis: Int, task: () -> Unit): WatchlistScheduledTask {
            val entry = Entry(delayMillis, task)
            entries += entry
            return WatchlistScheduledTask { entry.cancelled = true }
        }

        fun run(delay: Int) {
            entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
                it.cancelled = true
                it.task()
            }
        }

        fun pendingCount(): Int = entries.count { !it.cancelled }
    }

    private class Fixture {
        val store = WatchlistStore(InMemoryKeyValueStorage())
        val quoteRepository = QuoteRepository(
            online = SilentQuoteProvider(),
            offline = MockQuoteProvider(),
        )
        val insightRepository = silentInsightRepository()
        val scheduler = FakeScheduler()
        var warmCount = 0
        var warmedSymbols: List<String> = emptyList()

        val coordinator = WatchlistCoordinator(
            host = object : WatchlistCoordinator.WatchlistHost {
                override fun watchlistStore(): WatchlistStore = store

                override fun quoteRepository(): QuoteRepository = quoteRepository

                override fun insightRepository(): MarketInsightRepository = insightRepository

                override fun warmPrefetch(symbols: List<String>) {
                    warmCount++
                    warmedSymbols = symbols
                }
            },
            scheduler = scheduler,
        )
    }

    // ── reload 与筛选 ──

    @Test
    fun reloadFillsRowsAndDisplayListAndWarmsPrefetch() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.store.add("00700.HK", "腾讯控股")
        f.coordinator.reload()

        assertEquals(2, f.coordinator.rows.size)
        assertEquals(2, f.coordinator.displayList.size)
        assertEquals(0, f.warmCount, "reload 不触发预取；预取由 warmPrefetch 单独走")

        f.coordinator.warmPrefetch()
        assertEquals(1, f.warmCount)
        assertEquals(setOf("600519.SH", "00700.HK"), f.warmedSymbols.toSet())
    }

    @Test
    fun groupFilterNarrowsDisplayButNotAggregateSource() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.store.add("00700.HK", "腾讯控股")
        f.store.setGroup("600519.SH", "core")
        f.coordinator.reload()
        assertEquals(2, f.coordinator.displayList.size, "默认 all 全显")

        f.coordinator.selectGroup("core")
        assertEquals(1, f.coordinator.displayList.size, "分组过滤只影响渲染层")
        assertEquals(2, f.coordinator.rows.size, "rows 恒为完整自选（聚合头口径）")

        f.coordinator.selectAllFilter()
        assertEquals(2, f.coordinator.displayList.size)
    }

    // ── 搜索与加自选 ──

    @Test
    fun searchFindsCandidatesAndAddDeduplicates() = reactive {
        val f = Fixture()
        f.coordinator.search("茅台")
        assertTrue(f.coordinator.candidates.isNotEmpty(), "离线静态表可命中贵州茅台")

        val security = f.coordinator.candidates.first()
        f.coordinator.add(security)
        assertTrue(f.store.contains(security.symbol), "加自选落库")
        assertTrue(f.coordinator.hint.contains("已加入"), "ADDED 提示")

        f.coordinator.search("茅台")
        assertTrue(f.coordinator.candidates.none { it.symbol == security.symbol }, "已在自选的不再进候选")

        f.coordinator.add(security)
        assertTrue(f.coordinator.hint.contains("已在自选"), "ALREADY_IN 提示")
    }

    @Test
    fun searchBlankClearsCandidates() = reactive {
        val f = Fixture()
        f.coordinator.search("茅台")
        assertTrue(f.coordinator.candidates.isNotEmpty())
        f.coordinator.search("   ")
        assertTrue(f.coordinator.candidates.isEmpty(), "空输入清候选")
        f.coordinator.closeSearch()
        assertFalse(f.coordinator.searchOpen)
    }

    // ── 移除与撤销（Undo 计时归 Coordinator） ──

    @Test
    fun removeShowsUndoAndUndoRemoveRestores() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.coordinator.reload()

        f.coordinator.remove("600519.SH")
        assertFalse(f.store.contains("600519.SH"), "移除落库")
        assertTrue(f.coordinator.undoText.contains("已移除"), "撤销条出现")

        f.coordinator.undoRemove()
        assertTrue(f.store.contains("600519.SH"), "撤销恢复落库")
        assertEquals("", f.coordinator.undoText, "撤销后条消失")
    }

    @Test
    fun undoExpiresAfterTimeout() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.coordinator.reload()

        f.coordinator.remove("600519.SH")
        assertTrue(f.coordinator.undoText.isNotEmpty())
        f.scheduler.run(5000)
        assertEquals("", f.coordinator.undoText, "5s 过期由 Coordinator 计时清除")
        assertFalse(f.store.contains("600519.SH"), "过期后不可再恢复入口")
    }

    // ── 理由编辑（FR-W2） ──

    @Test
    fun reasonEditSavesChipAndTypedMutuallyExclusive() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.coordinator.reload()
        f.coordinator.openMenuFor("600519.SH")
        f.coordinator.beginReasonEdit()
        assertEquals("600519.SH", f.coordinator.reasonEditSymbol, "菜单入口转入编辑态")
        assertEquals("", f.coordinator.menuSymbol, "菜单关闭")

        f.coordinator.toggleReasonChip("业绩")
        assertEquals("业绩", f.coordinator.reasonChip)
        f.coordinator.onReasonTyped("前景")
        assertEquals("", f.coordinator.reasonChip, "打字让位 chip")
        assertEquals("前景", f.coordinator.reasonTyped)

        f.coordinator.saveReason()
        assertEquals("", f.coordinator.reasonEditSymbol)
        assertEquals("前景", f.store.list().first().reason, "手输理由落库")
    }

    @Test
    fun emptyReasonSaveSkips() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.coordinator.reload()
        f.coordinator.openMenuFor("600519.SH")
        f.coordinator.beginReasonEdit()
        f.coordinator.saveReason()
        assertEquals("", f.coordinator.reasonEditSymbol, "空输入跳过并关闭")
        assertEquals("", f.store.list().first().reason)
    }

    // ── 分组/置顶/步进排序（FR-W7） ──

    @Test
    fun setGroupPinToTopAndMoveRow() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.store.add("00700.HK", "腾讯控股")
        f.coordinator.reload()

        f.coordinator.setGroupTo("600519.SH", "research")
        assertEquals("research", f.store.list().first { it.symbol == "600519.SH" }.groupId)
        assertTrue(f.coordinator.hint.contains("研究"), "分组提示（groupLabel 口径：research=研究）")

        f.coordinator.pinToTop("00700.HK")
        assertEquals("00700.HK", f.store.list().first().symbol, "置顶到首位")

        // 置顶后顺序 = [00700.HK, 600519.SH]
        f.coordinator.moveRow("00700.HK", -1)
        assertTrue(f.coordinator.hint.contains("已经在最上面"), "到上边界给中性提示")

        f.coordinator.moveRow("600519.SH", +1)
        assertTrue(f.coordinator.hint.contains("已经在最下面"), "到下边界给中性提示")

        f.coordinator.moveRow("600519.SH", -1)
        assertEquals("600519.SH", f.store.list().first().symbol, "上移一位后茅台到首位")
    }

    @Test
    fun menuTitleContainsGroupLabel() = reactive {
        val f = Fixture()
        f.store.add("600519.SH", "贵州茅台")
        f.store.setGroup("600519.SH", "core")
        f.coordinator.reload()
        f.coordinator.openMenuFor("600519.SH")
        assertTrue(f.coordinator.menuTitle().contains("核心"), groupLabelCoversCore())
        assertTrue(f.coordinator.menuTitle().contains("贵州茅台"))
    }

    private fun groupLabelCoversCore(): String = "菜单标题带分组标签"

    // 防止 Securities 未使用告警（搜索路径经由 coordinator 内部静态表）。
    @Suppress("unused")
    private val securitiesRef = Securities.categories
}

// ── 共享 silent fakes（文件内私有；供本文件与拖拽测试复用） ──

private class SilentQuoteProvider : QuoteProvider {
    override val mode: DataMode = DataMode.OFFLINE
    override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) { /* 不回调：模拟未到达 */ }
    override fun timeline(symbol: String, onResult: (List<com.kuikly.stockchat.data.provider.QuotePoint>) -> Unit) {}
    override fun kLines(
        symbol: String,
        count: Int,
        interval: com.kuikly.stockchat.data.provider.KLineInterval,
        onResult: (List<com.kuikly.stockchat.data.provider.KLinePoint>) -> Unit,
    ) {}
}

private class SilentFundFlow : FundFlowProvider {
    override fun fundFlow(symbol: String, onResult: (com.kuikly.stockchat.data.provider.FundFlow?) -> Unit) {}
}

private class SilentFundamentals : FundamentalProvider {
    override fun fundamentals(symbol: String, onResult: (com.kuikly.stockchat.data.provider.FundamentalBundle?) -> Unit) {}
    override fun calendar(onResult: (List<MarketCalendarEvent>) -> Unit) { onResult(emptyList()) }
}

private class SilentDisclosures : DisclosureProvider {
    override fun disclosures(symbol: String, onResult: (List<com.kuikly.stockchat.data.provider.DisclosureItem>) -> Unit) {}
}

private class SilentOverview : MarketOverviewProvider {
    override fun overview(onResult: (com.kuikly.stockchat.data.provider.MarketOverview?) -> Unit) {}
    override fun hotspots(onResult: (com.kuikly.stockchat.data.provider.HotspotSnapshot?) -> Unit) {}
}

/** 全静音洞察仓储：所有在线回调不触发，日历回空表（离线降级路径）。 */
internal fun silentInsightRepository(): MarketInsightRepository = MarketInsightRepository(
    onlineFundFlow = SilentFundFlow(),
    onlineFundamentals = SilentFundamentals(),
    onlineDisclosures = SilentDisclosures(),
    onlineMarket = SilentOverview(),
)
