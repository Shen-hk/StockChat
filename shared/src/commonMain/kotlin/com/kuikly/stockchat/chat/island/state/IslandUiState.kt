package com.kuikly.stockchat.chat.island.state

import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 灵动岛手势相位（自 `page/components/AppChrome` 迁入，ownership 归 Island 状态层）。
 * 渲染器（ChatTopNav）按相位注册/消费补间动画；状态层只负责写相位与 revision。
 */
enum class IslandGesturePhase {
    IDLE,
    DRAGGING,
    RETURNING,
    CLOSING,
    OPENING_DETAIL,
}

/**
 * 灵动岛形变运动量。phase 与 offsetY 必须封装在同一个 observable 值里：
 * 归位时一次原子写入（相位 + 目标偏移），Kuikly 就能从手指最后一帧动画到端点。
 */
data class IslandGestureMotion(
    val phase: IslandGesturePhase = IslandGesturePhase.IDLE,
    val offsetY: Float = 0f,
    // A reset can have the same visual values as the current idle state.
    // Revision still invalidates the reactive layout so native anchors and
    // transforms are written again after returning from another page.
    val revision: Int = 0,
    // A forced/lifecycle reset (page cover racing a timer, returning from a
    // background page) must land on the idle geometry with no visible tween;
    // otherwise the card replays its morph from whatever frame was last on
    // screen the instant it becomes visible again.
    val snap: Boolean = false,
)

internal const val ISLAND_ANIMATION_RETURN = "island-gesture-return"
internal const val ISLAND_ANIMATION_CLOSE = "island-gesture-close"
internal const val ISLAND_ANIMATION_DETAIL = "island-gesture-detail"

/**
 * Island 可渲染状态端口。Kuikly 实现用 `observable`，测试用 Plain 实现。
 * 注意：`islandWatchlisted` 属自选域，留在页面；对比卡与对比解读（AI）
 * 属 CompareInsight 域，也留在页面。
 */
internal interface IslandStatePort {
    var expanded: Boolean
    var symbol: String
    var mounted: Boolean
    var motion: IslandGestureMotion
    var compareVisible: Boolean
    var compareLeftSymbol: String
    var compareRightSymbol: String
    var termKey: String
    var termCompareLeftKey: String
    var termCompareRightKey: String
    var termCompareVisible: Boolean
}

internal class IslandState : IslandStatePort {
    override var expanded: Boolean by observable(false)
    override var symbol: String by observable("600519.SH")
    override var mounted: Boolean by observable(true)
    override var motion: IslandGestureMotion by observable(IslandGestureMotion())
    override var compareVisible: Boolean by observable(false)
    override var compareLeftSymbol: String by observable("")
    override var compareRightSymbol: String by observable("")
    override var termKey: String by observable("")
    override var termCompareLeftKey: String by observable("")
    override var termCompareRightKey: String by observable("")
    override var termCompareVisible: Boolean by observable(false)
}

internal class PlainIslandState : IslandStatePort {
    override var expanded = false
    override var symbol = "600519.SH"
    override var mounted = true
    override var motion = IslandGestureMotion()
    override var compareVisible = false
    override var compareLeftSymbol = ""
    override var compareRightSymbol = ""
    override var termKey = ""
    override var termCompareLeftKey = ""
    override var termCompareRightKey = ""
    override var termCompareVisible = false
}
