package com.kuikly.stockchat.risk

import com.kuikly.stockchat.data.AlertInboxStore
import com.kuikly.stockchat.data.AlertKind
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.RiskSnapshot
import com.kuikly.stockchat.data.provider.CalendarEventKind
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.SourceStamp
import com.kuikly.stockchat.data.provider.SourceTier
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import com.kuikly.stockchat.risk.alert.state.RiskAlertCoordinator
import com.kuikly.stockchat.risk.state.RiskScheduledTask
import com.kuikly.stockchat.risk.state.RiskScheduler
import com.kuikly.stockchat.testing.reactive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 风险预警域单测：收件箱只写通路、已转/已生成 id 驱动、2.5s 提示清除时序
 * （fake scheduler，doc 47 B-3「提示时序必须原样保留」）。
 */
class RiskAlertCoordinatorTest {

    private class FakeScheduler : RiskScheduler {
        data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)

        private val entries = mutableListOf<Entry>()
        override fun schedule(delayMillis: Int, task: () -> Unit): RiskScheduledTask {
            val entry = Entry(delayMillis, task)
            entries += entry
            return RiskScheduledTask { entry.cancelled = true }
        }

        fun run(delay: Int) {
            entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
                it.cancelled = true
                it.task()
            }
        }

        fun hasEntryAt(delay: Int): Boolean = entries.any { it.delay == delay && !it.cancelled }
    }

    private class Fixture {
        val storage = InMemoryKeyValueStorage()
        val inbox = AlertInboxStore(storage)
        val scheduler = FakeScheduler()
        val coordinator = RiskAlertCoordinator(inboxStore = inbox, scheduler = scheduler)
    }

    private fun event(
        symbol: String = "600519.SH",
        date: String = "2026-09-20",
        kind: CalendarEventKind = CalendarEventKind.UNLOCK,
    ): MarketCalendarEvent = MarketCalendarEvent(
        date = date,
        symbol = symbol,
        name = "贵州茅台",
        kind = kind,
        title = "$kind 测试事件",
        stamp = SourceStamp("test", "2026-09-12", SourceTier.MARKET_DATA),
    )

    private fun pinnedEventMessage(id: String): AlertMessage = AlertMessage(
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
    fun refreshConvertedReadsInboxIdsOnce() = reactive {
        val f = Fixture()
        f.inbox.putExtraMessage(pinnedEventMessage("EVENT:600519.SH:2026-09-20"))
        f.coordinator.refreshConverted()
        assertTrue(f.coordinator.convertedEventIds.contains("EVENT:600519.SH:2026-09-20"))
        assertTrue(f.coordinator.generatedExposureIds.contains("EVENT:600519.SH:2026-09-20"))
    }

    @Test
    fun convertEventWritesPinnedMessageAndShowsHintThenClears() = reactive {
        val f = Fixture()
        val e = event()
        val eventId = "EVENT:${e.symbol}:${e.date}"
        f.coordinator.convertEventToInbox(e, eventId)

        // 只写通路：消息落库，pinned=true，id 规则严格
        val written = f.inbox.extraMessages().single()
        assertEquals(eventId, written.id)
        assertEquals(AlertKind.EVENT, written.kind)
        assertTrue(written.pinned)
        assertTrue(written.summary.contains("统计描述"), "事实句只陈述，不下结论")

        // 同步 id 集合触发刷新（不回读 store）
        assertTrue(f.coordinator.convertedEventIds.contains(eventId))

        // 提示时序：置文案 + 2.5s 清除任务注册
        assertEquals("已加入预警收件箱 ✓", f.coordinator.eventToInboxHint)
        assertTrue(f.scheduler.hasEntryAt(RiskAlertCoordinator.HINT_CLEAR_MS), "必须注册 2.5s 清除任务")
        f.scheduler.run(RiskAlertCoordinator.HINT_CLEAR_MS)
        assertEquals("", f.coordinator.eventToInboxHint, "清空走先写空串由 vif 消失")
    }

    @Test
    fun convertEarningsEventUsesEarningsTitle() = reactive {
        val f = Fixture()
        val e = event(kind = CalendarEventKind.EARNINGS)
        f.coordinator.convertEventToInbox(e, "EVENT:${e.symbol}:${e.date}")
        val written = f.inbox.extraMessages().single()
        assertTrue(written.title.contains("财报预约披露"))
        assertTrue(written.askQuestion.contains("意味着什么"))
    }

    @Test
    fun exposureAlertWritesMessageWithStrictId() = reactive {
        val f = Fixture()
        val older = RiskSnapshot(1000L, 5, "白酒", 3, 60, 130)
        val newer = RiskSnapshot(2000L, 6, "白酒", 4, 75, 150)
        val exposureId = "EXPOSURE:2000"
        f.coordinator.generateExposureAlert(older, newer, exposureId)
        val written = f.inbox.extraMessages().single()
        assertEquals(exposureId, written.id)
        assertEquals(AlertKind.EXPOSURE, written.kind)
        assertFalse(written.pinned)
        assertTrue(written.title.contains("更集中了"))
        assertTrue(f.coordinator.generatedExposureIds.contains(exposureId))
    }

    @Test
    fun togglePairFlipsSelection() = reactive {
        val f = Fixture()
        f.coordinator.togglePair("a|b")
        assertEquals("a|b", f.coordinator.selectedPair)
        f.coordinator.togglePair("a|b")
        assertEquals("", f.coordinator.selectedPair, "点同格子 = 取消")
    }
}
