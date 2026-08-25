package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.cards.theme.StockChatTheme

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
)
