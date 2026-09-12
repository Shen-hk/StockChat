package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.design.GlassRenderer

enum class CardDensity { FULL, COMPACT, MINI }

data class CardContext(
    val theme: StockChatTheme,
    val density: CardDensity,
    val onOpenStock: (String) -> Unit,
    val onExplainTerm: (String) -> Unit = {},
    val expanded: Boolean = false,
    val onToggleExpanded: (() -> Unit)? = null,
    val onOpenSheet: ((CardModel) -> Unit)? = null,
    val drilledKeys: Set<String> = emptySet(),
    val onToggleDrill: ((String) -> Unit)? = null,
    val onStartSubThread: ((CardModel) -> Unit)? = null,
    val cardKey: String = "",
    val focusedCardKey: String = "",
    val onFocusChanged: ((String, Boolean) -> Unit)? = null,
    val compareCandidateSymbol: String = "",
    val onCompareCandidate: ((String, String) -> Unit)? = null,
    val onCardEvent: ((String, CardEvent) -> Unit)? = null,
    /** Shared visual-quality decision. Card renderers consume it, never platform APIs directly. */
    val glass: GlassRenderer = GlassRenderer.Default,
    /**
     * 行情卡本体是否自己挂 click（打开详情）。嵌入横向手势容器（RowGestureLayer、
     * 卡片流视口）时必须传 false：Android 上可触摸子 View 会吞掉整条触摸流，
     * 外层容器的 touch/pan 收不到 → 滑不动。点击改由外层容器统一承担。
     * 放在参数表末尾：CardContext 有大量按位置传参的调用点，中途插参会错位。
     */
    val cardClickable: Boolean = true,
)
