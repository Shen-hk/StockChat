package com.kuikly.stockchat.chat.card.state

import com.kuikly.stockchat.cards.core.CardEvent
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.reactive.collection.ObservableList

internal data class SubThreadState(
    val cardId: String,
    val title: String,
    val input: String,
    val response: String,
    val streaming: Boolean = false,
    val collapsed: Boolean = false,
)

internal interface CardInteractionStatePort {
    var expandedCardKey: String
    var focusedCardKey: String
    var repairingCardKey: String
    val drilledKeys: MutableList<String>
    val subThreads: MutableList<SubThreadState>
}

internal class CardInteractionState : CardInteractionStatePort {
    override var expandedCardKey: String by observable("")
    override var focusedCardKey: String by observable("")
    override var repairingCardKey: String by observable("")
    private var observableDrilledKeys: ObservableList<String> by observableList()
    private var observableSubThreads: ObservableList<SubThreadState> by observableList()
    override val drilledKeys: MutableList<String> get() = observableDrilledKeys
    override val subThreads: MutableList<SubThreadState> get() = observableSubThreads
}

internal class PlainCardInteractionState : CardInteractionStatePort {
    override var expandedCardKey = ""
    override var focusedCardKey = ""
    override var repairingCardKey = ""
    override val drilledKeys = mutableListOf<String>()
    override val subThreads = mutableListOf<SubThreadState>()
}

internal sealed interface CardInteractionEffect {
    data object HapticImpact : CardInteractionEffect
    data class RetryCard(
        val messageId: String,
        val blockId: String,
        val cardType: String,
        val rawCard: String,
    ) : CardInteractionEffect
    data class RequestSubThread(val cardId: String, val prompt: String) : CardInteractionEffect
}

/** Card-local interaction owner; network and native feedback remain page effects. */
internal class CardInteractionCoordinator(
    val state: CardInteractionStatePort,
    private val onEffect: (CardInteractionEffect) -> Unit,
) {
    fun resetForNewSession() {
        state.expandedCardKey = ""
        state.focusedCardKey = ""
        state.repairingCardKey = ""
        state.drilledKeys.clear()
        state.subThreads.clear()
    }

    fun toggleExpanded(cardKey: String) {
        state.expandedCardKey = if (state.expandedCardKey == cardKey) "" else cardKey
    }

    fun setFocused(cardKey: String, focused: Boolean) {
        state.focusedCardKey = if (focused) cardKey else ""
    }

    fun clearFocused() {
        state.focusedCardKey = ""
    }

    fun onCardEvent(cardKey: String, event: CardEvent) {
        when (event) {
            is CardEvent.FocusStart -> onEffect(CardInteractionEffect.HapticImpact)
            is CardEvent.FocusEnd -> if (state.focusedCardKey == cardKey) state.focusedCardKey = ""
            else -> Unit
        }
    }

    fun toggleDrill(drillKey: String) {
        val index = state.drilledKeys.indexOf(drillKey)
        if (index >= 0) state.drilledKeys.removeAt(index) else state.drilledKeys.add(drillKey)
    }

    fun startSubThread(model: CardModel) {
        val insight = model as? InsightCardModel ?: return
        val index = state.subThreads.indexOfFirst { it.cardId == insight.cardId }
        if (index >= 0) {
            state.subThreads[index] = state.subThreads[index].copy(collapsed = false)
            return
        }
        state.subThreads += SubThreadState(
            cardId = insight.cardId,
            title = "分支：AI 解读深挖",
            input = "",
            response = "正在生成深入解读…",
            streaming = true,
        )
        onEffect(CardInteractionEffect.RequestSubThread(
            insight.cardId,
            "请围绕以下解读继续深入说明：${insight.summary}",
        ))
    }

    fun toggleSubThread(cardId: String) = updateSubThread(cardId) { it.copy(collapsed = !it.collapsed) }

    fun updateSubThreadInput(cardId: String, input: String) = updateSubThread(cardId) { it.copy(input = input) }

    fun sendSubThread(cardId: String) {
        val state = state.subThreads.firstOrNull { it.cardId == cardId } ?: return
        if (state.streaming || state.input.isBlank()) return
        updateSubThread(cardId) {
            it.copy(response = "正在生成深入解读…", input = "", streaming = true, collapsed = false)
        }
        onEffect(CardInteractionEffect.RequestSubThread(cardId, state.input))
    }

    fun retryCard(messageId: String, blockId: String, cardType: String, rawCard: String) {
        if (state.repairingCardKey.isNotEmpty()) return
        state.repairingCardKey = "$messageId:$blockId"
        onEffect(CardInteractionEffect.RetryCard(messageId, blockId, cardType.ifBlank { "stock-quote" }, rawCard))
    }

    fun onRetryDone() {
        state.repairingCardKey = ""
    }

    fun onRetryError() {
        state.repairingCardKey = ""
    }

    fun onSubThreadDelta(cardId: String, response: String) =
        updateSubThread(cardId) { it.copy(response = response, streaming = true) }

    fun onSubThreadDone(cardId: String, response: String) =
        updateSubThread(cardId) { it.copy(response = response.ifBlank { "暂未生成内容" }, streaming = false) }

    fun onSubThreadError(cardId: String, error: String) =
        updateSubThread(cardId) { it.copy(response = error, streaming = false) }

    private fun updateSubThread(cardId: String, update: (SubThreadState) -> SubThreadState) {
        val index = state.subThreads.indexOfFirst { it.cardId == cardId }
        if (index >= 0) state.subThreads[index] = update(state.subThreads[index])
    }
}
