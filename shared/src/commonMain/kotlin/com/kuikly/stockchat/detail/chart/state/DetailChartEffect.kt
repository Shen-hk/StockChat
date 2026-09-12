package com.kuikly.stockchat.detail.chart.state

/** [DetailChartInteractionCoordinator] 对页面数据侧的最小依赖面，供页面适配、测试可 fake。 */
internal fun interface DetailChartHostPort {
    /** 当日分时价格序列（页面经 D1 的 quote 派生，`detailTimelineSeries(quote)`）。 */
    fun timelineSeries(): List<Double>
}

/**
 * 图表交互域需要页面（或后续 Wave2 其他 Coordinator）执行的下游副作用
 * （docs/43 D2）：overlay 仲裁（D4 地盘）与圈选 AI 解读（D3 地盘）仍由页面
 * 在 effect 处理函数里承接——本域只发通知，不代为执行。
 */
internal sealed interface DetailChartEffect {
    /**
     * 就地气泡内容已就位（声呐点/圈选统计共用入口，原 `showChartBubble`）。
     * 页面侧动作 = 中断圈选 AI 流 + `overlayArbiter.request(DetailOverlay.CHART_BUBBLE)`。
     */
    data class ChartBubbleShown(val text: String) : DetailChartEffect

    /**
     * 圈选松手且区间有效：端侧统计已入气泡、区间带已高亮，等待 AI 区间解读
     * （原 `onCircleSelected` 末尾的 `requestCircleAi(lo, hi)`）。
     */
    data class CircleSelectionCommitted(val lo: Int, val hi: Int) : DetailChartEffect

    /** 圈选区间无效（太短）：页面 toast 提示，不产生气泡/区间带。 */
    data class CircleSelectionRejected(val message: String) : DetailChartEffect
}
