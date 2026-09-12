package com.kuikly.stockchat.detail.ai.state

import com.tencent.kuikly.core.reactive.handler.observable

/**
 * Detail 页 AI 解读域的可渲染状态端口（Wave 2 第 3 刀）。
 * 两套会话（主解读 aiRemote* / 圈选解读 circleAi*）合并后挂在同一 StatePort
 * 上，分别由两个 [AiInsightSession] 实例驱动；awaitingFacts + revealLimit /
 * revealSource 是同一域的呈现态。session 内部私有的 generation / provider /
 * typewriter 不外露。
 */
internal interface DetailAiStatePort {
    // 主 AI 解读（aiRemote*）
    var mainState: Int
    var mainText: String
    var mainError: String
    var mainModel: String
    // 圈选 AI 解读（circleAi*）
    var circleState: Int
    var circleText: String
    var circleError: String
    var circleModel: String
    // 占位图与端侧模板逐字显示
    var awaitingFacts: Boolean
    var revealLimit: Int
    var revealSource: String
}

internal class DetailAiState : DetailAiStatePort {
    override var mainState: Int by observable(0)
    override var mainText: String by observable("")
    override var mainError: String by observable("")
    override var mainModel: String by observable("")
    override var circleState: Int by observable(0)
    override var circleText: String by observable("")
    override var circleError: String by observable("")
    override var circleModel: String by observable("")
    override var awaitingFacts: Boolean by observable(false)
    override var revealLimit: Int by observable(0)
    override var revealSource: String by observable("")
}

internal class PlainDetailAiState : DetailAiStatePort {
    override var mainState = 0
    override var mainText = ""
    override var mainError = ""
    override var mainModel = ""
    override var circleState = 0
    override var circleText = ""
    override var circleError = ""
    override var circleModel = ""
    override var awaitingFacts = false
    override var revealLimit = 0
    override var revealSource = ""
}