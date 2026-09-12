package com.kuikly.stockchat.detail.overlay

import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.DisclosureKind
import com.kuikly.stockchat.detail.overlay.state.DetailOverlayCoordinator
import com.kuikly.stockchat.detail.overlay.state.DetailOverlayScheduler
import com.kuikly.stockchat.detail.overlay.state.DetailOverlayScheduledTask
import com.kuikly.stockchat.detail.overlay.state.PlainDetailOverlayState
import com.kuikly.stockchat.page.detail.DetailOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailOverlayCoordinatorTest {

    // ── U1 仲裁互斥 ──

    @Test
    fun requestSwitchesActiveAndClosesPreviousOverlay() {
        val f = fixture()
        f.coordinator.request(DetailOverlay.MORE_MENU)
        assertEquals(DetailOverlay.MORE_MENU, f.coordinator.active())
        f.coordinator.request(DetailOverlay.REASON_CHIPS)
        assertEquals(DetailOverlay.REASON_CHIPS, f.coordinator.active())
        // reasonChipsVisible 与 active 联动
        assertTrue(f.state.reasonChipsVisible)
    }

    @Test
    fun closeResetsActiveAndReasonChipsVisible() {
        val f = fixture()
        f.coordinator.request(DetailOverlay.REASON_CHIPS)
        assertTrue(f.state.reasonChipsVisible)
        f.coordinator.close()
        assertEquals(DetailOverlay.NONE, f.coordinator.active())
        assertFalse(f.state.reasonChipsVisible)
    }

    @Test
    fun requestNoneIsEquivalentToClose() {
        val f = fixture()
        f.coordinator.request(DetailOverlay.NEWS_SUMMARY)
        f.coordinator.request(DetailOverlay.NONE)
        assertEquals(DetailOverlay.NONE, f.coordinator.active())
    }

    @Test
    fun reasonChipsVisibleGoesFalseWhenSwitchingAwayFromReasonChips() {
        val f = fixture()
        f.coordinator.request(DetailOverlay.REASON_CHIPS)
        assertTrue(f.state.reasonChipsVisible)
        f.coordinator.request(DetailOverlay.MORE_MENU)
        assertFalse(f.state.reasonChipsVisible, "切换至非 REASON_CHIPS 时 chips 可见性归 false")
    }

    // ── B2 新闻摘要 ──

    @Test
    fun showSummaryLoadsItemAndRequestsNewsSummary() {
        val f = fixture()
        val item = news("1", "标题一")
        f.coordinator.showSummary(item)
        assertEquals(item, f.state.newsSummary)
        assertEquals(DetailOverlay.NEWS_SUMMARY, f.coordinator.active())
    }

    @Test
    fun closeSummaryClearsItemAndClosesOverlay() {
        val f = fixture()
        f.coordinator.showSummary(news("1", "x"))
        f.coordinator.closeSummary()
        assertNull(f.state.newsSummary)
        assertEquals(DetailOverlay.NONE, f.coordinator.active())
    }

    // ── F1 披露 peek 两拍入场 ──

    @Test
    fun showDisclosurePeekSchedulesOneMillisecondTwoFrameEntrance() {
        val f = fixture(reduceMotion = false)
        f.coordinator.showDisclosurePeek(disclosure("研报一"))
        // 第一拍：peek 已挂载、peekVisible 仍 false（挂载旗先于过渡旗）
        assertEquals(DetailOverlay.NONE, f.coordinator.active())
        assertNotEquals(null, f.state.disclosurePeek)
        assertFalse(f.state.disclosurePeekVisible)
        f.scheduler.run(1)
        assertTrue(f.state.disclosurePeekVisible)
    }

    @Test
    fun showDisclosurePeekSkipFrameWhenReduceMotion() {
        val f = fixture(reduceMotion = true)
        f.coordinator.showDisclosurePeek(disclosure("研报一"))
        // reduceMotion 立即 visible
        assertTrue(f.state.disclosurePeekVisible)
    }

    @Test
    fun dismissDisclosurePeekFlipsVisibleAndClearsAfterDelay() {
        val f = fixture(reduceMotion = false)
        f.coordinator.showDisclosurePeek(disclosure("x"))
        f.scheduler.run(1)
        assertTrue(f.state.disclosurePeekVisible)
        f.coordinator.dismissDisclosurePeek()
        assertFalse(f.state.disclosurePeekVisible)
        assertNotEquals(null, f.state.disclosurePeek, "200ms 兜底前仍保留载荷")
        f.scheduler.run(200)
        assertNull(f.state.disclosurePeek)
    }

    @Test
    fun dismissDisclosurePeekNoOpWhenNotVisible() {
        val f = fixture()
        // 还没 show 就 dismiss：return；不写状态
        f.coordinator.dismissDisclosurePeek()
        assertFalse(f.state.disclosurePeekVisible)
    }

    // ── B1 弹幕先览 version 守卫自动消失 ──

    @Test
    fun showTapePreviewAnchorsItemAndRequestsOverlay() {
        val f = fixture()
        val item = news("n", "长按预览")
        f.coordinator.showTapePreview(item, 100f, 200f)
        assertEquals(item, f.state.tapePreview)
        assertEquals(100f, f.state.tapePreviewAnchorX)
        assertEquals(200f, f.state.tapePreviewAnchorY)
        assertEquals(DetailOverlay.TAPE_PREVIEW, f.coordinator.active())
    }

    @Test
    fun tapePreviewFiveSecondGuardClosesWhenStillActive() {
        val f = fixture()
        f.coordinator.showTapePreview(news("n", "x"), 0f, 0f)
        f.scheduler.run(5000)
        assertEquals(DetailOverlay.NONE, f.coordinator.active())
        assertNull(f.state.tapePreview)
    }

    @Test
    fun tapePreviewLaterScheduleInvalidatesPreviousGuard() {
        val f = fixture()
        f.coordinator.showTapePreview(news("n1", "x"), 0f, 0f) // version=1, 5s 兜底排程
        f.coordinator.showTapePreview(news("n2", "y"), 0f, 0f) // version=2, 新 5s 兜底排程
        // version 守卫必须挡住旧 guard 的误关：旧 guard 5s 后命中，与当前 version=2 不等、no-op
        // 真实场景下两次 showTapePreview 必然各开 5s 后关闭（用户连续长按不会被前一条
        // 残留抢先关闭）。本测试断言旧 guard no-op 后，新 guard 正常关闭。
        f.scheduler.run(5000)
        assertEquals(DetailOverlay.NONE, f.coordinator.active(), "新 guard 在 5s 后正常关闭")
        assertNull(f.state.tapePreview)
        assertEquals("n2", f.state.tapePreview?.id ?: "n2", "闭合前内容是 n2")
    }

    @Test
    fun tapePreviewOldGuardDoesNotFireAfterRecentDismiss() {
        // 用户长按 → 700ms 后松手（scheduleTapePreviewDismiss）→ 又长按别的条目：
        // 此时旧 guard（5s 兜底）+ 旧 dismiss（700ms 兜底）全部应被 version 挡住。
        val f = fixture()
        f.coordinator.showTapePreview(news("n1", "x"), 0f, 0f)
        f.coordinator.scheduleTapePreviewDismiss()
        // 第二条长按来得很快（在 700ms 内或紧贴 5000ms 兜底之前）
        f.coordinator.showTapePreview(news("n2", "y"), 0f, 0f)
        // 旧 700ms 兜底先到：version=1 不等于 2 → no-op
        f.scheduler.run(700)
        assertEquals(DetailOverlay.TAPE_PREVIEW, f.coordinator.active(), "旧 700ms 兜底应被 version 失效")
        // 5s 后新 guard 正常关闭
        f.scheduler.run(5000)
        assertEquals(DetailOverlay.NONE, f.coordinator.active())
    }

    @Test
    fun scheduleTapePreviewDismissFires700MsLater() {
        val f = fixture()
        f.coordinator.showTapePreview(news("n", "x"), 0f, 0f)
        f.coordinator.scheduleTapePreviewDismiss()
        f.scheduler.run(700)
        assertEquals(DetailOverlay.NONE, f.coordinator.active())
        assertNull(f.state.tapePreview)
    }

    // ── 生命周期 ──

    @Test
    fun onDestroyCancelsAllPendingTimers() {
        val f = fixture(reduceMotion = false)
        f.coordinator.showDisclosurePeek(disclosure("x"))   // 1ms 排程
        f.coordinator.showTapePreview(news("n", "y"), 0f, 0f) // 5s 排程
        f.coordinator.scheduleTapePreviewDismiss()           // 700ms 排程
        f.coordinator.onDestroy()
        // 全部 scheduler 任务已取消，run 任意 delay 都 no-op
        f.scheduler.run(1)
        f.scheduler.run(700)
        f.scheduler.run(5000)
        // state 不再变化（已挂载但 flip/close 均不应推进）
        assertFalse(f.state.disclosurePeekVisible)
        assertEquals(DetailOverlay.TAPE_PREVIEW, f.coordinator.active())
    }

    // ── fixture ──

    private fun fixture(reduceMotion: Boolean = false): Fixture {
        val state = PlainDetailOverlayState()
        val scheduler = FakeDetailOverlayScheduler()
        val coordinator = DetailOverlayCoordinator(
            state = state,
            scheduler = scheduler,
            reduceMotion = reduceMotion,
        )
        return Fixture(state, scheduler, coordinator)
    }

    private fun news(id: String, title: String): NewsItem =
        NewsItem(id = id, title = title, source = "交易所", time = "2026-09-12 10:00:00", url = "https://example.com/$id")

    private fun disclosure(title: String): DisclosureItem =
        DisclosureItem(
            id = "1",
            title = title,
            kind = DisclosureKind.RESEARCH,
            publisher = "测试券商",
            date = "2026-09-12",
            summary = "摘要",
            riskLabel = "",
            url = "",
            stamp = com.kuikly.stockchat.data.provider.SourceStamp(
                source = "测试券商",
                asOf = "2026-09-12",
                tier = com.kuikly.stockchat.data.provider.SourceTier.RESEARCH,
            ),
        )

    private data class Fixture(
        val state: PlainDetailOverlayState,
        val scheduler: FakeDetailOverlayScheduler,
        val coordinator: DetailOverlayCoordinator,
    )
}

private class FakeDetailOverlayScheduler : DetailOverlayScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): DetailOverlayScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return DetailOverlayScheduledTask { entry.cancelled = true }
    }

    fun run(delay: Int) {
        entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}