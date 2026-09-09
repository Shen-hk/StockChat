package com.kuikly.stockchat.chat.sheet.state

import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CardModel
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer

internal enum class ChatSheetLevel(val ratio: Float, val density: CardDensity) {
    PEEK(0.25f, CardDensity.MINI),
    HALF(0.50f, CardDensity.COMPACT),
    FULL(0.90f, CardDensity.FULL),
}

internal interface CardSheetStatePort {
    var model: CardModel?
    var mounted: Boolean
    var presented: Boolean
    var interactive: Boolean
    var level: ChatSheetLevel
}

internal class CardSheetState : CardSheetStatePort {
    override var model: CardModel? by observable(null)
    override var mounted: Boolean by observable(false)
    override var presented: Boolean by observable(false)
    override var interactive: Boolean by observable(false)
    override var level: ChatSheetLevel by observable(ChatSheetLevel.HALF)
}

internal class PlainCardSheetState : CardSheetStatePort {
    override var model: CardModel? = null
    override var mounted = false
    override var presented = false
    override var interactive = false
    override var level = ChatSheetLevel.HALF
}

internal interface CardSheetScheduledTask { fun cancel() }

internal fun interface CardSheetScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): CardSheetScheduledTask
}

internal class KuiklyCardSheetScheduler : CardSheetScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): CardSheetScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return object : CardSheetScheduledTask { override fun cancel() = timer.cancel() }
    }
}

/** Owns sheet state transitions; the Page only supplies the model and UI callbacks. */
internal class CardSheetCoordinator(
    private val state: CardSheetStatePort,
    private val scheduler: CardSheetScheduler,
) {
    private var version = 0
    private var mounted = false
    private var presented = false
    private var panStartY = 0f
    private var pendingTask: CardSheetScheduledTask? = null

    fun open(model: CardModel, deferInteraction: Boolean = false) {
        val current = ++version
        pendingTask?.cancel()
        setMounted(false)
        state.model = model
        setLevel(if (model.cardType == "stock-chart") ChatSheetLevel.FULL else ChatSheetLevel.HALF)
        setPresented(!deferInteraction)
        setInteractive(!deferInteraction)
        if (!deferInteraction) {
            pendingTask = scheduler.schedule(0) {
                if (version == current) setMounted(true)
            }
        }
    }

    fun dismiss() {
        val current = ++version
        pendingTask?.cancel()
        setInteractive(false)
        setPresented(false)
        pendingTask = scheduler.schedule(DISMISS_DURATION_MS) {
            if (version == current && !presented) {
                setMounted(false)
                state.model = null
            }
        }
    }

    fun raise() = setLevel(
        when (stateLevel) {
            ChatSheetLevel.PEEK -> ChatSheetLevel.HALF
            ChatSheetLevel.HALF, ChatSheetLevel.FULL -> ChatSheetLevel.FULL
        },
    )

    fun lower() {
        when (stateLevel) {
            ChatSheetLevel.FULL -> setLevel(ChatSheetLevel.HALF)
            ChatSheetLevel.HALF -> setLevel(ChatSheetLevel.PEEK)
            ChatSheetLevel.PEEK -> dismiss()
        }
    }

    fun onPan(phase: String, y: Float) {
        when (phase) {
            "start" -> panStartY = y
            "end" -> when {
                y - panStartY <= PAN_THRESHOLD -> raise()
                y - panStartY >= -PAN_THRESHOLD -> lower()
            }
        }
    }

    fun reset() {
        ++version
        pendingTask?.cancel()
        pendingTask = null
        state.model = null
        setMounted(false)
        setPresented(false)
        setInteractive(false)
        setLevel(ChatSheetLevel.HALF)
    }

    private var stateLevel = ChatSheetLevel.HALF

    private fun setMounted(value: Boolean) { mounted = value; state.mounted = value }
    private fun setPresented(value: Boolean) { presented = value; state.presented = value }
    private fun setInteractive(value: Boolean) { state.interactive = value }
    private fun setLevel(value: ChatSheetLevel) { stateLevel = value; state.level = value }

    private companion object {
        const val PAN_THRESHOLD = -28f
        const val DISMISS_DURATION_MS = 420
    }
}
