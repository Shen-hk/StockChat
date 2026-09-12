package com.kuikly.stockchat.detail.chart.state

import com.kuikly.stockchat.chart.model.ChartViewportAction
import com.kuikly.stockchat.chart.model.ChartViewportCommand
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.page.components.ChartFlag
import com.kuikly.stockchat.page.detail.AnchorIndex
import com.kuikly.stockchat.page.detail.AnomalyPoint

/**
 * Detail 页图表交互域的唯一 owner（Wave 2 第 2 刀，见 docs/39 §9 / docs/43 D2）：
 * 十字线/K 线选中读写、scrub 锁与手势期背景动画暂停、圈选态与松手端侧统计、
 * 声呐点选中与气泡两帧入场、视口指令版本号、scrub 停顿预填、新闻旗标增删、
 * 区间高亮带（含页面 selectedSentence 联动写入）。
 *
 * 下游副作用只经 [DetailChartEffect] 通知（气泡展示的 overlay 仲裁、圈选 AI
 * 解读、无效区间 toast），不在本域执行。`selectedSentence` 本身与 circleAi*
 * 状态机、OverlayArbiter、声呐漂移/draw-on 动画仍属页面 / D3 / D4 地盘。
 */
internal class DetailChartInteractionCoordinator(
    private val state: DetailChartStatePort,
    private val host: DetailChartHostPort,
    private val scheduler: DetailChartScheduler,
    private val onEffect: (DetailChartEffect) -> Unit,
) {
    private var chartViewportRevision = 0
    /** 非响应式镜像：动画节拍器（页面 startSonarDrift）查询，交互期间暂停背景 Canvas 动画重绘。 */
    private var chartInteractionActive = false
    private val tasks = mutableListOf<DetailChartScheduledTask>()

    /** D1 的 quote/预取回调派生声呐异动点（同屏 ≤3，doc §4.4 呼吸预算 U4）。 */
    fun applySonarPoints(points: List<AnomalyPoint>) {
        state.sonarPoints = points
    }

    /** ④ 声呐点轻点：选中态 + 相关区间高亮带（±10 点）+ 就地气泡（doc §4.4）。 */
    fun tapSonar(index: Int) {
        val point = state.sonarPoints.firstOrNull { it.index == index } ?: return
        state.selectedSonarIndex = index
        state.bandRange = Triple(
            (index - 10).coerceAtLeast(0),
            (index + 10).coerceAtMost(AnchorIndex.INDEX_COUNT - 1),
            false,
        )
        showChartBubble(point.label)
    }

    /** ④/① 就地气泡统一入口：R4 两帧入场翻转；overlay 仲裁与圈选 AI 流中断经 Effect 交给页面。 */
    fun showChartBubble(text: String) {
        state.chartBubble = text
        onEffect(DetailChartEffect.ChartBubbleShown(text))
        state.chartBubblePresented = false
        schedule(0) { state.chartBubblePresented = true }
    }

    /**
     * ① 圈选松手：端侧统计（区间起止价、涨跌幅、极值，纯事实）立即入气泡，
     * 区间带高亮保留（随气泡关闭一起清除），AI 区间解读经 Effect 交页面触发。
     */
    fun onCircleSelected(start: Int, end: Int) {
        val series = host.timelineSeries()
        if (series.size < 2) return
        val lo = minOf(start, end).coerceIn(0, series.lastIndex)
        val hi = maxOf(start, end).coerceIn(0, series.lastIndex)
        if (hi - lo < 3) {
            onEffect(DetailChartEffect.CircleSelectionRejected("区间太短（不足 3 个点），松手前多拖一段"))
            return
        }
        val p0 = series[lo]
        val p1 = series[hi]
        val pct = if (p0 != 0.0) (p1 - p0) / p0 * 100.0 else 0.0
        val seg = series.subList(lo, hi + 1)
        // ① 松手后保留区间带高亮（brand 12%），随气泡关闭一起清除
        state.bandRange = Triple(lo, hi, false)
        showChartBubble(
            "${AnchorIndex.indexToTimeLabel(lo)}–${AnchorIndex.indexToTimeLabel(hi)} " +
                "区间${if (pct >= 0) "上行" else "下行"} ${Format.percent(pct)}，" +
                "区间极值 ${Format.price(seg.min())}–${Format.price(seg.max())}",
        )
        onEffect(DetailChartEffect.CircleSelectionCommitted(lo, hi))
    }

    /** ① 圈选态 hint：进入圈选时出现（R4 两帧入场）、松手消失。 */
    fun setCircleSelecting(selecting: Boolean) {
        state.circleSelecting = selecting
        if (selecting) {
            state.circleHintPresented = false
            schedule(0) { state.circleHintPresented = true }
        }
    }

    /** ⑤ scrub 移动：更新十字线并清空停顿预填（只预填不发送）。 */
    fun onScrub(index: Int) {
        state.crosshairIndex = index
        state.prefillQuestion = ""
    }

    /** ⑤ 十字线停顿 600ms：按走势方向模板预填（涨/跌/横盘三模板，均为可陈述事实问法）。 */
    fun onScrubPause(index: Int) {
        val series = host.timelineSeries()
        if (index !in series.indices) return
        val base = series.getOrNull((index - 4).coerceAtLeast(0)) ?: return
        val pct = if (base != 0.0) (series[index] - base) / base * 100.0 else 0.0
        state.prefillQuestion = when {
            pct > 0.15 -> "${AnchorIndex.indexToTimeLabel(index)} 前后这波涨是怎么回事？"
            pct < -0.15 -> "${AnchorIndex.indexToTimeLabel(index)} 前后这波跌是怎么回事？"
            else -> "${AnchorIndex.indexToTimeLabel(index)} 前后这段横盘是怎么回事？"
        }
    }

    /** ⑤ 松手离开 scrub：清预填。 */
    fun clearPrefill() {
        state.prefillQuestion = ""
    }

    fun selectKLineIndex(index: Int) {
        state.selectedKLineIndex = index
    }

    /** 周期/模式切换：清 K 线选中与十字线。 */
    fun resetChartSelection() {
        state.selectedKLineIndex = -1
        state.crosshairIndex = -1
    }

    /** 图表接管手势（scrub/捏合/十字线）时锁页面滚动；页面动画节拍器经 [isInteractionActive] 暂停背景重绘。 */
    fun setInteractionActive(active: Boolean) {
        chartInteractionActive = active
        state.chartScrubLock = active
    }

    fun isInteractionActive(): Boolean = chartInteractionActive

    /** 视口指令（缩放/平移/复位）：revision 递增使同按钮连点可被图表观测。 */
    fun issueViewportCommand(action: ChartViewportAction) {
        chartViewportRevision += 1
        state.chartViewportCommand = ChartViewportCommand(action, chartViewportRevision)
    }

    /** ② 句图联动（页面 selectedSentence 派生）与 D2 内部共用的区间带写入口。 */
    fun setBandRange(range: Triple<Int, Int, Boolean>?) {
        state.bandRange = range
    }

    fun clearBandRange() {
        state.bandRange = null
    }

    /** B2 落旗：旗标 + 区间高亮带（12 点宽）；同 index 旧旗先移除。 */
    fun applyNewsFlag(idx: Int, isPositive: Boolean, label: String) {
        state.chartFlags = state.chartFlags.filterNot { it.index == idx } + ChartFlag(idx, isPositive, label, dropped = true)
        state.bandRange = Triple(idx, (idx + 12).coerceAtMost(AnchorIndex.INDEX_COUNT - 1), false)
    }

    /** B2 收旗：移除该 index 旗标；若区间带由它而来则一并清除。 */
    fun removeFlagAt(idx: Int) {
        state.chartFlags = state.chartFlags.filterNot { it.index == idx }
        if (state.bandRange?.first == idx) state.bandRange = null
    }

    fun onDestroy() {
        tasks.forEach(DetailChartScheduledTask::cancel)
        tasks.clear()
    }

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }
}
