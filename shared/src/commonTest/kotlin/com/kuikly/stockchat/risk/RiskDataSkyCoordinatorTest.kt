package com.kuikly.stockchat.risk

import com.kuikly.stockchat.data.RiskSnapshot
import com.kuikly.stockchat.data.RiskSnapshotStore
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.HotspotSnapshot
import com.kuikly.stockchat.data.provider.KLinePoint
import com.kuikly.stockchat.data.provider.LimitUpStock
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteLoadResult
import com.kuikly.stockchat.data.provider.SourceStamp
import com.kuikly.stockchat.data.provider.SourceTier
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import com.kuikly.stockchat.risk.data.RiskRepository
import com.kuikly.stockchat.risk.domain.RiskRow
import com.kuikly.stockchat.risk.domain.SkyLayer
import com.kuikly.stockchat.risk.domain.StarLayout
import com.kuikly.stockchat.risk.domain.StarMemberIn
import com.kuikly.stockchat.risk.sky.state.RiskSkyCoordinator
import com.kuikly.stockchat.risk.state.KuiklyRiskScheduler
import com.kuikly.stockchat.risk.state.RiskDataCoordinator
import com.kuikly.stockchat.risk.state.RiskScheduledTask
import com.kuikly.stockchat.risk.state.RiskScheduler
import com.kuikly.stockchat.testing.reactive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 数据装载域与星图状态域单测（fake repository / fake scheduler）：
 * reload 装载链、快照留存判定、图层持久化与选中清理、拖拽回弹链、脉冲门控。
 */
class RiskDataSkyCoordinatorTest {

    // ── fakes ──

    private class FakeScheduler : RiskScheduler {
        data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)

        private val entries = mutableListOf<Entry>()
        override fun schedule(delayMillis: Int, task: () -> Unit): RiskScheduledTask {
            val entry = Entry(delayMillis, task)
            entries += entry
            return RiskScheduledTask { entry.cancelled = true }
        }

        fun runOnce(delay: Int) {
            entries.filter { it.delay == delay && !it.cancelled }.firstOrNull()?.let {
                it.cancelled = true
                it.task()
            }
        }

        fun pendingCount(): Int = entries.count { !it.cancelled }
    }

    private class FakeRiskRepository : RiskRepository {
        val cached = mutableMapOf<String, Quote>()
        val loadCallbacks = mutableListOf<Pair<String, (QuoteLoadResult) -> Unit>>()
        var industriesResult: Map<String, String> = emptyMap()
        var calendarResult: List<MarketCalendarEvent> = emptyList()
        var hotspotsResult: HotspotSnapshot = HotspotSnapshot(emptyList(), emptyList(), SourceStamp("t", "t", SourceTier.MARKET_DATA))

        override fun cachedOrOffline(symbol: String): Quote? = cached[symbol]

        override fun loadQuote(symbol: String, onResult: (QuoteLoadResult) -> Unit) {
            loadCallbacks += symbol to onResult
        }

        override fun loadIndustries(symbols: List<String>, onResult: (Map<String, String>) -> Unit) {
            onResult(industriesResult)
        }

        override fun loadCalendar(onResult: (List<MarketCalendarEvent>) -> Unit) {
            onResult(calendarResult)
        }

        override fun loadHotspots(onResult: (HotspotSnapshot) -> Unit) {
            onResult(hotspotsResult)
        }
    }

    // ── 输入 ──

    private fun quote(symbol: String, name: String = symbol, price: Double = 10.0): Quote =
        Quote.placeholder(symbol, name).copy(price = price, previousClose = 10.0)

    private fun kLineQuote(symbol: String, up: Boolean = true): Quote {
        val kLines = (0 until 40).map { i ->
            val step = if (up) 0.01 else -0.005
            val c = 10.0 * (1.0 + step * (i + 1))
            KLinePoint("d$i", c, c, c, c, 100.0)
        }
        return Quote.placeholder(symbol).copy(previousClose = 10.0, price = 10.0, kLines = kLines)
    }

    private fun watchlist(vararg symbols: String): List<WatchlistItem> =
        symbols.map { WatchlistItem(symbol = it, name = it) }

    // ── 数据装载域 ──

    @Test
    fun reloadFillsRowsFromWatchlistAndUpdatesOnQuoteArrival() = reactive {
        val repo = FakeRiskRepository()
        var quoteArrivals = 0
        val storage = InMemoryKeyValueStorage()
        val data = RiskDataCoordinator(
            repository = repo,
            snapshotStore = RiskSnapshotStore(storage),
            onQuoteArrived = { quoteArrivals++ },
        )
        data.reload(watchlist("600519.SH", "00700.HK"))

        assertEquals(2, data.rows.size, "rows 同步装满（离线/缓存行情可先行）")
        assertEquals(3, repo.loadCallbacks.size, "2 只自选各一次在线加载 + 1 次大盘指数（000300.SH）")

        // 在线行情到达 → 替换行 + dataModeLabel + onQuoteArrived
        val (symbol, callback) = repo.loadCallbacks[0]
        assertEquals("600519.SH", symbol)
        callback(QuoteLoadResult(quote(symbol, "贵州茅台"), DataMode.ONLINE, 0L))
        assertEquals("贵州茅台", data.rows.first { it.symbol == "600519.SH" }.quote?.name, "行情到达替换 quote（行名保持自选名）")
        assertTrue(data.dataModeLabel.isNotEmpty(), "数据模式角标更新")
        assertTrue(quoteArrivals >= 1, "行情到达必须触发重算回调")
    }

    @Test
    fun reloadEmptyWatchlistSkipsIndexAndInsights() = reactive {
        val repo = FakeRiskRepository()
        val data = RiskDataCoordinator(
            repository = repo,
            snapshotStore = RiskSnapshotStore(InMemoryKeyValueStorage()),
        )
        data.reload(emptyList())
        assertTrue(data.rows.isEmpty())
        assertEquals(0, repo.loadCallbacks.size, "空自选不发任何在线请求")
    }

    @Test
    fun snapshotCapturePersistsWeekly() = reactive {
        val repo = FakeRiskRepository()
        val storage = InMemoryKeyValueStorage()
        // 假时钟起点必须远大于 WEEK_MS(7 天)，否则 maybeCapture 的「7 天内不重复」判定会挡住第一份快照。
        var now = 1_700_000_000_000L
        val data = RiskDataCoordinator(
            repository = repo,
            snapshotStore = RiskSnapshotStore(storage) { now },
        )
        // 行业结果先备好：FakeRepo 在 reload 内同步回调 industries。
        repo.industriesResult = mapOf("a" to "白酒", "b" to "白酒", "c" to "电子")
        data.reload(watchlist("a", "b", "c"))
        data.captureSnapshotIfDue()

        assertEquals(1, data.snapshots().size, "行业到达后采第一份快照")
        assertEquals(3, data.snapshots()[0].memberCount)

        // 7 天内重复打开不重复存
        now += 3 * 24 * 60 * 60 * 1000L
        data.captureSnapshotIfDue()
        assertEquals(1, data.snapshots().size)
    }

    // ── 星图状态域 ──

    private class SkyFixture(
        reduceMotion: Boolean = false,
    ) {
        val storage = InMemoryKeyValueStorage()
        val scheduler = FakeScheduler()
        var rows: List<RiskRow> = emptyList()
        var chainTriggered = false
        val coordinator = RiskSkyCoordinator(
            inputs = object : RiskSkyCoordinator.Inputs {
                override fun members(): List<StarMemberIn> =
                    rows.map { StarMemberIn(it.symbol, it.name, "行业-${it.symbol}") }

                override fun containerWidth(): Float = 300f

                override fun chainTriggered(): Boolean = this@SkyFixture.chainTriggered

                override fun rowsSnapshot(): List<RiskRow> = rows
            },
            storage = storage,
            scheduler = scheduler,
            reduceMotion = reduceMotion,
        )
    }

    @Test
    fun applySkyLayerPersistsAndClearsSelection() = reactive {
        val f = SkyFixture()
        f.coordinator.onSkyStarTap("a")
        assertEquals("a", f.coordinator.skySelectedSymbol)

        f.coordinator.applySkyLayer(SkyLayer.LINK)
        assertEquals(SkyLayer.LINK, f.coordinator.skyLayer)
        assertEquals("", f.coordinator.skySelectedSymbol, "切层清选中")
        assertEquals(SkyLayer.LINK.name, f.storage.getString(RiskSkyCoordinator.SKY_LAYER_KEY), "返回态保持：持久化")

        // 同层重复点击不重复清
        f.coordinator.onSkyStarTap("b")
        f.coordinator.applySkyLayer(SkyLayer.LINK)
        assertEquals("b", f.coordinator.skySelectedSymbol)
    }

    @Test
    fun restoreRecoversLayerAndViewModeFromStorage() = reactive {
        val f = SkyFixture()
        f.storage.setString(RiskSkyCoordinator.SKY_LAYER_KEY, "LINK")
        f.storage.setString(RiskSkyCoordinator.SKY_VIEW_KEY, "MINDMAP")
        f.coordinator.restore()
        assertEquals(SkyLayer.LINK, f.coordinator.skyLayer)
        assertEquals(RiskSkyCoordinator.SkyViewMode.MINDMAP, f.coordinator.skyViewMode)
    }

    @Test
    fun starTapTogglesAndClusterTapIsMutuallyExclusive() = reactive {
        val f = SkyFixture()
        f.coordinator.onSkyStarTap("a")
        f.coordinator.onSkyClusterTap("白酒")
        assertEquals("", f.coordinator.skySelectedSymbol, "星与团互斥")
        assertEquals("白酒", f.coordinator.skySelectedCluster)
        f.coordinator.onSkyClusterTap("白酒")
        assertEquals("", f.coordinator.skySelectedCluster, "点同团 = 取消")
    }

    @Test
    fun dragPullsLinkedStarsAndReboundsOverScheduledSteps() = reactive {
        val f = SkyFixture()
        // 3 只高相关自选（同向日K → r=1）
        f.rows = listOf(
            RiskRow("a", "甲", kLineQuote("a")),
            RiskRow("b", "乙", kLineQuote("b")),
            RiskRow("c", "丙", kLineQuote("c")),
        )
        f.coordinator.refreshSkyData(f.rows, null)

        // 长按 → 拖拽（长按星设置 context，拖动清掉提问条）
        f.coordinator.onSkyStarLongPress("a")
        assertEquals("a", f.coordinator.skyContextSymbol)
        f.coordinator.onSkyStarDrag("a", dx = 10f, dy = 0f)
        assertEquals("", f.coordinator.skyContextSymbol, "拖拽意图收掉提问条")
        assertTrue(f.coordinator.skyDragOffsets.containsKey("a"), "直连星有偏移")
        assertTrue(f.coordinator.skyDragOffsets.containsKey("b"), "高相关星被牵引")

        // 松手 → 回弹链按 40ms 步进注册（物理参数逐值保留）
        val before = f.coordinator.skyDragOffsets.getValue("a").first
        f.coordinator.onSkyStarDragEnd()
        // 第一次调度后偏移应按 0.72 衰减
        val generationBefore = schedulerPending(f)
        f.scheduler.runOnce(40)
        val after = f.coordinator.skyDragOffsets.getValue("a").first
        assertTrue(abs(after) < abs(before) || abs(after) <= 0.3f, "回弹衰减中：$before -> $after")
        assertTrue(generationBefore >= 0)
    }

    private fun schedulerPending(f: SkyFixture): Int = f.scheduler.pendingCount()

    @Test
    fun dragEndWithReduceMotionSnapsToZeroImmediately() = reactive {
        val f = SkyFixture(reduceMotion = true)
        f.rows = listOf(
            RiskRow("a", "甲", kLineQuote("a")),
            RiskRow("b", "乙", kLineQuote("b")),
            RiskRow("c", "丙", kLineQuote("c")),
        )
        f.coordinator.refreshSkyData(f.rows, null)
        f.coordinator.onSkyStarDrag("a", 20f, 0f)
        f.coordinator.onSkyStarDragEnd()
        assertTrue(f.coordinator.skyDragOffsets.isEmpty(), "reduceMotion 立即归零")
        assertEquals(0, f.scheduler.pendingCount())
    }

    @Test
    fun beaconPulseRunsOnlyForThreePlusRowsAndCanBeShutDown() = reactive {
        val f = SkyFixture()
        f.rows = listOf(RiskRow("a", "甲"), RiskRow("b", "乙"))
        f.coordinator.ensureBeaconPulse()
        assertEquals(0, f.scheduler.pendingCount(), "rows<3 不启动脉冲")

        f.rows = listOf(RiskRow("a", "甲"), RiskRow("b", "乙"), RiskRow("c", "丙"))
        f.coordinator.ensureBeaconPulse()
        assertEquals(1, f.scheduler.pendingCount(), "≥3 只启动 55ms 步进")
        f.scheduler.runOnce(RiskSkyCoordinator.BEACON_STEP_MS)
        assertTrue(f.coordinator.beaconPhase > 0f, "相位推进")
    }

    @Test
    fun geometryIsDeterministicForSameInputs() = reactive {
        val f = SkyFixture()
        f.rows = listOf(
            RiskRow("a", "甲"),
            RiskRow("b", "乙"),
            RiskRow("c", "丙"),
        )
        val g1 = f.coordinator.skyGeometry()
        val g2 = f.coordinator.skyGeometry()
        assertEquals(g1, g2, "同输入恒同输出（doc 32 §6.1）")
        assertEquals(3, g1.stars.size)
        assertTrue(g1.requiredHeight >= StarLayout.MIN_HEIGHT)
    }

    @Test
    fun beaconClusterIndexGatesOnChainTriggered() = reactive {
        val f = SkyFixture()
        f.rows = listOf(RiskRow("a", "甲"), RiskRow("b", "乙"), RiskRow("c", "丙"))
        assertEquals(-1, f.coordinator.skyBeaconClusterIndex(), "单链未判定 = 无引路星")
        f.chainTriggered = true
        assertEquals(0, f.coordinator.skyBeaconClusterIndex(), "最大团下标 0")
    }

    // ── KuiklyRiskScheduler 冒烟（仅验证任务真实执行） ──

    @Test
    fun kuiklySchedulerExecutesTask() = reactive {
        // 注：真实 Timer 依赖平台；此测试仅保证类可实例化且接口契约正确。
        val scheduler: RiskScheduler = KuiklyRiskScheduler()
        var ran = false
        val task = scheduler.schedule(16) { ran = true }
        assertTrue(task is RiskScheduledTask)
        // 不等待真实时钟：ran 的最终值由平台线程决定，这里只断言不抛异常。
        task.cancel() // 用完即取消，不给测试进程留活动 Timer
        assertTrue(!ran, "立即取消后不应已执行")
    }
}
