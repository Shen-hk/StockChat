package com.kuikly.stockchat.detail.chart.state

import com.kuikly.stockchat.chart.model.ChartViewportCommand
import com.kuikly.stockchat.detail.page.component.ChartFlag
import com.kuikly.stockchat.detail.domain.AnomalyPoint
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * Detail 页图表交互域的可渲染状态端口（Wave 2 第 2 刀，见 docs/39 §9 / docs/43 D2）。
 * Kuikly 实现用 `observable`，测试用 [PlainDetailChartState]。
 *
 * `selectedSentence`（② 句图联动的点句选中）刻意不在本域：它属于"AI 洞察正文 →
 * 图表高亮"的联动状态，仍由页面持有，经 [DetailChartInteractionCoordinator.setBandRange]
 * 写入区间带（见 docs/43 D2 边界条款）。
 */
internal interface DetailChartStatePort {
    var crosshairIndex: Int
    var selectedKLineIndex: Int
    var chartViewportCommand: ChartViewportCommand
    var chartScrubLock: Boolean
    var sonarPoints: List<AnomalyPoint>
    var selectedSonarIndex: Int
    var chartBubble: String
    var chartBubblePresented: Boolean
    var circleSelecting: Boolean
    var circleHintPresented: Boolean
    var prefillQuestion: String
    var chartFlags: List<ChartFlag>
    var bandRange: Triple<Int, Int, Boolean>?
}

internal class DetailChartState : DetailChartStatePort {
    override var crosshairIndex: Int by observable(-1)
    override var selectedKLineIndex: Int by observable(-1)
    override var chartViewportCommand: ChartViewportCommand by observable(ChartViewportCommand())
    override var chartScrubLock: Boolean by observable(false)
    override var sonarPoints: List<AnomalyPoint> by observable(emptyList())
    override var selectedSonarIndex: Int by observable(-1)
    override var chartBubble: String by observable("")
    override var chartBubblePresented: Boolean by observable(false)
    override var circleSelecting: Boolean by observable(false)
    override var circleHintPresented: Boolean by observable(false)
    override var prefillQuestion: String by observable("")
    override var chartFlags: List<ChartFlag> by observable(emptyList())
    override var bandRange: Triple<Int, Int, Boolean>? by observable(null)
}

internal class PlainDetailChartState : DetailChartStatePort {
    override var crosshairIndex = -1
    override var selectedKLineIndex = -1
    override var chartViewportCommand = ChartViewportCommand()
    override var chartScrubLock = false
    override var sonarPoints: List<AnomalyPoint> = emptyList()
    override var selectedSonarIndex = -1
    override var chartBubble = ""
    override var chartBubblePresented = false
    override var circleSelecting = false
    override var circleHintPresented = false
    override var prefillQuestion = ""
    override var chartFlags: List<ChartFlag> = emptyList()
    override var bandRange: Triple<Int, Int, Boolean>? = null
}
