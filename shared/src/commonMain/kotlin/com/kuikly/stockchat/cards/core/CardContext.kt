package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.cards.theme.StockChatTheme

enum class CardDensity { FULL, COMPACT, MINI }

data class CardContext(
    val theme: StockChatTheme,
    val density: CardDensity,
    val onOpenStock: (String) -> Unit,
    val onExplainTerm: (String) -> Unit = {},
)
