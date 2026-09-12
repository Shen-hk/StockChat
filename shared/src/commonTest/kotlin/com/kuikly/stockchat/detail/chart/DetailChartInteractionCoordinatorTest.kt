package com.kuikly.stockchat.detail.chart

import com.kuikly.stockchat.chart.model.ChartViewportAction
import com.kuikly.stockchat.detail.chart.state.DetailChartEffect
import com.kuikly.stockchat.detail.chart.state.DetailChartHostPort
import com.kuikly.stockchat.detail.chart.state.DetailChartInteractionCoordinator
import com.kuikly.stockchat.detail.chart.state.DetailChartScheduledTask
import com.kuikly.stockchat.detail.chart.state.DetailChartScheduler
import com.kuikly.stockchat.detail.chart.state.PlainDetailChartState
import com.kuikly.stockchat.page.detail.AnomalyPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailChartInteractionCoordinatorTest {

    // ── ① 圈选松手：bandRange + 气泡文案 + Effect 组合 ──

    @Test
    fun circleSelectionCommitProducesBandBubbleTextAndEffect() {
        val f = fixture(series = linearSeries())

        f.coordinator.onCircleSelected(5, 20)

        assertEquals(Triple(5, 20, false), f.state.bandRange)
        // 09:35–09:50 区间上行 +14.29%，区间极值 10.50–12.00（端侧统计逐字对齐原实现：
        // p0=10.50、p1=12.00、pct=(12.00-10.50)/10.50=+14.29%）
        assertEquals(
            "09:35–09:50 区间上行 +14.29%，区间极值 10.50–12.00",
            f.state.chartBubble,
        )
        val committed = f.effects.filterIsInstance<DetailChartEffect.CircleSelectionCommitted>().single()
        assertEquals(5, committed.lo)
        assertEquals(20, committed.hi)
    }

    @Test
    fun circleSelectionArgumentsAreNormalizedAndClamped() {
        val f = fixture(series = linearSeries())

        // 交换 start/end、越界钳制到序列范围
        f.coordinator.onCircleSelected(20, -4)

        assertEquals(Triple(0, 20, false), f.state.bandRange)
        assertTrue(f.effects.isNotEmpty())
    }

    @Test
    fun tooShortCircleSelectionIsRejectedWithoutStateChange() {
        val f = fixture(series = linearSeries())

        f.coordinator.onCircleSelected(10, 12) // hi-lo=2 < 3 → 拒绝（原实现阈值口径不变）

        assertNull(f.state.bandRange)
        assertEquals("", f.state.chartBubble)
        val rejected = f.effects.filterIsInstance<DetailChartEffect.CircleSelectionRejected>().single()
        assertEquals("区间太短（不足 3 个点），松手前多拖一段", rejected.message)
        assertTrue(f.effects.none { it is DetailChartEffect.CircleSelectionCommitted })
    }

    @Test
    fun circleSelectionNeedsAtLeastTwoPoints() {
        val f = fixture(series = listOf(10.0))

        f.coordinator.onCircleSelected(0, 0)

        assertTrue(f.effects.isEmpty())
        assertNull(f.state.bandRange)
    }

    // ── ④ 声呐点：选中态 + 区间带 + 气泡 ──

    @Test
    fun tapSonarSelectsPointBandAndBubble() {
        val f = fixture(series = linearSeries())
        f.coordinator.applySonarPoints(listOf(AnomalyPoint(30, true, "快速拉升")))

        f.coordinator.tapSonar(30)

        assertEquals(30, f.state.selectedSonarIndex)
        assertEquals(Triple(20, 40, false), f.state.bandRange)
        assertEquals("快速拉升", f.state.chartBubble)
        assertTrue(f.effects.filterIsInstance<DetailChartEffect.ChartBubbleShown>().any { it.text == "快速拉升" })
    }

    @Test
    fun tapUnknownSonarIndexIsIgnored() {
        val f = fixture(series = linearSeries())
        f.coordinator.applySonarPoints(listOf(AnomalyPoint(30, true, "快速拉升")))

        f.coordinator.tapSonar(99)

        assertEquals(-1, f.state.selectedSonarIndex)
        assertEquals("", f.state.chartBubble)
        assertTrue(f.effects.isEmpty())
    }

    // ── R4 两帧入场 ──

    @Test
    fun bubblePresentationFlipsInTwoFrames() {
        val f = fixture(series = linearSeries())

        f.coordinator.tapSonar(applySonarAndTap(f))

        // 第一帧：presented 复位为 false（重放入场）
        assertFalse(f.state.chartBubblePresented)
        f.scheduler.run(0)
        assertTrue(f.state.chartBubblePresented)
    }

    @Test
    fun circleHintPresentationFlipsInTwoFramesOnlyWhenSelecting() {
        val f = fixture(series = linearSeries())

        f.coordinator.setCircleSelecting(true)
        assertFalse(f.state.circleHintPresented)
        f.scheduler.run(0)
        assertTrue(f.state.circleHintPresented)

        // 松手：hint 随 circleSelecting 一起消失（vif 双条件门控）；circleHintPresented
        // 本身按原实现保持 stale true（下次进圈选时才复位重放两帧入场）
        f.coordinator.setCircleSelecting(false)
        assertFalse(f.state.circleSelecting)
        assertFalse(f.state.circleSelecting && f.state.circleHintPresented)
    }

    // ── ⑤ scrub：十字线 / 预填 / scrub 锁 ──

    @Test
    fun scrubUpdatesCrosshairAndClearsPrefill() {
        val f = fixture(series = linearSeries())

        f.coordinator.onScrubPause(10)
        assertTrue(f.state.prefillQuestion.isNotEmpty())
        f.coordinator.onScrub(12)

        assertEquals(12, f.state.crosshairIndex)
        assertEquals("", f.state.prefillQuestion)
    }

    @Test
    fun scrubPausePrefillUsesDirectionTemplates() {
        val f = fixture(series = linearSeries()) // 10.0 + 0.1*i：涨势

        f.coordinator.onScrubPause(10)
        assertEquals("09:40 前后这波涨是怎么回事？", f.state.prefillQuestion)
    }

    @Test
    fun scrubPauseOutOfRangeIsIgnored() {
        val f = fixture(series = linearSeries())

        f.coordinator.onScrubPause(999)
        assertEquals("", f.state.prefillQuestion)
    }

    @Test
    fun interactionActiveDrivesScrubLockAndMirror() {
        val f = fixture(series = linearSeries())

        f.coordinator.setInteractionActive(true)
        assertTrue(f.state.chartScrubLock)
        assertTrue(f.coordinator.isInteractionActive())

        f.coordinator.setInteractionActive(false)
        assertFalse(f.state.chartScrubLock)
        assertFalse(f.coordinator.isInteractionActive())
    }

    // ── 视口指令 / 选中 / 旗标 / 区间带 ──

    @Test
    fun viewportCommandRevisionIncrements() {
        val f = fixture(series = linearSeries())

        f.coordinator.issueViewportCommand(ChartViewportAction.ZOOM_IN)
        assertEquals(ChartViewportAction.ZOOM_IN, f.state.chartViewportCommand.action)
        assertEquals(1, f.state.chartViewportCommand.revision)

        f.coordinator.issueViewportCommand(ChartViewportAction.ZOOM_IN)
        assertEquals(2, f.state.chartViewportCommand.revision)
    }

    @Test
    fun resetChartSelectionClearsKLineAndCrosshair() {
        val f = fixture(series = linearSeries())
        f.coordinator.selectKLineIndex(7)
        f.coordinator.onScrub(9)

        f.coordinator.resetChartSelection()

        assertEquals(-1, f.state.selectedKLineIndex)
        assertEquals(-1, f.state.crosshairIndex)
    }

    @Test
    fun newsFlagApplyAndRemoveMaintainBandLinkage() {
        val f = fixture(series = linearSeries())

        f.coordinator.applyNewsFlag(30, isPositive = true, label = "+1.12%")
        assertEquals(1, f.state.chartFlags.size)
        assertEquals(Triple(30, 42, false), f.state.bandRange)

        // 同 index 再落旗：替换而非追加
        f.coordinator.applyNewsFlag(30, isPositive = false, label = "-0.5%")
        assertEquals(1, f.state.chartFlags.size)
        assertFalse(f.state.chartFlags.single().isPositive)

        f.coordinator.removeFlagAt(30)
        assertTrue(f.state.chartFlags.isEmpty())
        assertNull(f.state.bandRange, "band linked to the removed flag must be cleared")
    }

    @Test
    fun removeUnlinkedFlagKeepsBand() {
        val f = fixture(series = linearSeries())
        f.coordinator.applyNewsFlag(30, true, "+1.12%")
        f.coordinator.setBandRange(Triple(100, 120, true))

        f.coordinator.removeFlagAt(30)

        assertTrue(f.state.chartFlags.isEmpty())
        assertEquals(Triple(100, 120, true), f.state.bandRange, "band from sentence linkage must survive")
    }

    // ── 生命周期 ──

    @Test
    fun onDestroyCancelsPendingTwoFrameTask() {
        val f = fixture(series = linearSeries())
        f.coordinator.applySonarPoints(listOf(AnomalyPoint(30, true, "快速拉升")))
        f.coordinator.tapSonar(30)

        f.coordinator.onDestroy()
        f.scheduler.run(0)

        // 两帧任务已被取消：presented 停在复位态，不再有状态写入
        assertFalse(f.state.chartBubblePresented)
    }

    // ── fixture ──

    private fun applySonarAndTap(f: Fixture): Int {
        f.coordinator.applySonarPoints(listOf(AnomalyPoint(30, true, "快速拉升")))
        return 30
    }

    private fun fixture(series: List<Double>): Fixture {
        val state = PlainDetailChartState()
        val scheduler = FakeDetailChartScheduler()
        val effects = mutableListOf<DetailChartEffect>()
        val coordinator = DetailChartInteractionCoordinator(
            state = state,
            host = DetailChartHostPort { series },
            scheduler = scheduler,
            onEffect = effects::add,
        )
        return Fixture(state, scheduler, effects, coordinator)
    }

    /** 10.0 + 0.1*i 的 30 点线性序列（涨势），i=5→10.50、i=20→11.50。 */
    private fun linearSeries(): List<Double> = List(30) { 10.0 + 0.1 * it }

    private data class Fixture(
        val state: PlainDetailChartState,
        val scheduler: FakeDetailChartScheduler,
        val effects: MutableList<DetailChartEffect>,
        val coordinator: DetailChartInteractionCoordinator,
    )
}

private class FakeDetailChartScheduler : DetailChartScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): DetailChartScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return DetailChartScheduledTask { entry.cancelled = true }
    }

    fun run(delay: Int) {
        entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}
