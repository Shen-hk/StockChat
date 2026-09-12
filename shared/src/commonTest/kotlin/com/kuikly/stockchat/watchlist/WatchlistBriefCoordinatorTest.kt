package com.kuikly.stockchat.watchlist

import com.kuikly.stockchat.data.AlertInboxStore
import com.kuikly.stockchat.data.AlertKind
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import com.kuikly.stockchat.testing.reactive
import com.kuikly.stockchat.watchlist.brief.state.WatchlistBriefCoordinator
import com.kuikly.stockchat.watchlist.state.Aggregate
import com.kuikly.stockchat.watchlist.state.WatchlistScheduledTask
import com.kuikly.stockchat.watchlist.state.WatchlistScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 简报/收件箱域单测（doc 47 B-4）：R4 两拍入场、pinned 合并去重、未读数、
 * 速览事实行生成（规则引擎口径，reduceMotion 直出）。
 */
class WatchlistBriefCoordinatorTest {

    private class FakeScheduler : WatchlistScheduler {
        val tasks = mutableListOf<Pair<Int, () -> Unit>>()
        override fun schedule(delayMillis: Int, task: () -> Unit): WatchlistScheduledTask {
            tasks += delayMillis to task
            return WatchlistScheduledTask { /* no-op */ }
        }

        fun run(delay: Int) {
            tasks.filter { it.first == delay }.forEach { it.second() }
        }

        fun pendingCount(): Int = tasks.size
    }

    private class Fixture(reduceMotion: Boolean = false) {
        val storage = InMemoryKeyValueStorage()
        val inboxStore = AlertInboxStore(storage)
        val watchlistStore = WatchlistStore(InMemoryKeyValueStorage())
        val scheduler = FakeScheduler()
        var aggregate = Aggregate(1, 1, 3.0, 1, 0, 0, "多数上涨，自选强于大盘", 1)

        val coordinator = WatchlistBriefCoordinator(
            host = object : WatchlistBriefCoordinator.BriefHost {
                override fun watchlist() = watchlistStore.list()

                override fun alertRules() = emptyList<com.kuikly.stockchat.data.AlertRule>()

                override fun snapshots() = emptyList<com.kuikly.stockchat.data.RiskSnapshot>()

                override fun mutedRuleSymbols(): Set<String> = inboxStore.mutedRuleSymbols()

                override fun exposureMuted(): Boolean = inboxStore.exposureMuted()

                override fun quietHoursEnabled(): Boolean = inboxStore.quietHoursEnabled()

                override fun extraMessages(): List<AlertMessage> = inboxStore.extraMessages()

                override fun unreadCount(messages: List<AlertMessage>): Int = inboxStore.unreadCount(messages)

                override fun reduceMotion(): Boolean = reduceMotion

                override fun aggregate(): Aggregate = this@Fixture.aggregate

                override fun rowsSnapshot() = emptyList<com.kuikly.stockchat.watchlist.state.WatchlistRow>()
            },
            scheduler = scheduler,
        )
    }

    private fun pinnedMessage(id: String): AlertMessage = AlertMessage(
        id = id,
        kind = AlertKind.EVENT,
        symbol = "600519.SH",
        name = "贵州茅台",
        title = "t",
        summary = "s",
        facts = emptyList(),
        createdAtMillis = 0L,
        askQuestion = "",
        termKey = "",
        pinned = true,
    )

    @Test
    fun inboxEntranceTwoBeatOrImmediate() = reactive {
        val f = Fixture(reduceMotion = false)
        f.coordinator.armInboxEntrance()
        assertFalse(f.coordinator.inboxPreviewPresented, "首帧隐藏（R4）")
        f.scheduler.run(0)
        assertTrue(f.coordinator.inboxPreviewPresented, "下一帧翻入")

        val rm = Fixture(reduceMotion = true)
        rm.coordinator.armInboxEntrance()
        assertTrue(rm.coordinator.inboxPreviewPresented, "reduceMotion 直出")
    }

    @Test
    fun rebuildInboxMergesPinnedAndCountsUnread() = reactive {
        val f = Fixture()
        f.inboxStore.putExtraMessage(pinnedMessage("EVENT:600519.SH:2026-09-20"))
        f.coordinator.updateEvents(emptyList())

        assertTrue(f.coordinator.inboxMessages.any { it.id == "EVENT:600519.SH:2026-09-20" }, "pinned 消息合并进预览")
        assertTrue(f.coordinator.inboxUnread >= 1, "未读数来自 AlertInboxStore 口径")
    }

    @Test
    fun updateEventsRebuildsAndDeduplicates() = reactive {
        val f = Fixture()
        f.inboxStore.putExtraMessage(pinnedMessage("X:1"))
        f.coordinator.updateEvents(emptyList())
        val first = f.coordinator.inboxMessages.toList()
        f.coordinator.updateEvents(emptyList())
        val second = f.coordinator.inboxMessages.toList()
        assertEquals(first.map { it.id }, second.map { it.id }, "重复重建结果幂等")
        assertEquals(first.size, second.size)
    }

    @Test
    fun briefLinesBuiltFromAggregateAndEntrancePlaysOnce() = reactive {
        val f = Fixture()
        f.coordinator.rebuildInbox()

        assertTrue(f.coordinator.hasBrief, "聚合结论有内容 → 速览出现")
        assertEquals(1, f.scheduler.pendingCount(), "首条事实到达调度两拍（R4）")
        f.scheduler.run(0)
        assertTrue(f.coordinator.briefPresented)
        assertTrue(f.coordinator.briefLines.first().contains("1 只自选等权"), "事实行含等权结论")

        // 再次重建：hasBrief 已 true，不再重复播入场
        f.coordinator.rebuildInbox()
        assertEquals(1, f.scheduler.pendingCount(), "不重复打扰")
    }

    @Test
    fun reduceMotionBriefKeepsOriginalBehavior() = reactive {
        // ⚠️ 原始口径逐字保留：原 rebuildBrief 的两拍入场仅在 !reduceMotion 时调度，
        // reduceMotion 下 briefPresented 恒 false（速览卡不显示）——本次迁移不改行为，
        // 此测试锁定该既有怪点，供后续行为修复时改写。
        val f = Fixture(reduceMotion = true)
        f.coordinator.rebuildInbox()
        assertTrue(f.coordinator.hasBrief)
        assertFalse(f.coordinator.briefPresented, "原始行为：reduceMotion 不调度 brief 两拍")
    }

    @Test
    fun toggleBriefFlipsOpenState() = reactive {
        val f = Fixture()
        assertFalse(f.coordinator.briefOpen)
        f.coordinator.toggleBrief()
        assertTrue(f.coordinator.briefOpen)
        f.coordinator.toggleBrief()
        assertFalse(f.coordinator.briefOpen)
    }
}
