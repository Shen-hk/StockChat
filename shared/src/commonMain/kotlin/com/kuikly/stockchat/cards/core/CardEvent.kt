package com.kuikly.stockchat.cards.core

sealed class CardEvent {
    object Expand : CardEvent()
    object Collapse : CardEvent()
    object RequestFullScreen : CardEvent()
    data class DrillInto(val factorId: String) : CardEvent()
    data class StartSubThread(val context: String) : CardEvent()
    data class CompareWith(val otherSymbol: String) : CardEvent()
    object FocusStart : CardEvent()
    object FocusEnd : CardEvent()
    data class FocusPanEnd(val deltaY: Float) : CardEvent()
}
