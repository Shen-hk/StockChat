package com.kuikly.stockchat.chat.session.state

import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer

internal interface MarketFallbackStatePort { var promptSymbol: String }
internal class MarketFallbackState : MarketFallbackStatePort {
    override var promptSymbol: String by observable("")
}
internal class PlainMarketFallbackState : MarketFallbackStatePort { override var promptSymbol = "" }
internal fun interface MarketFallbackTask { fun cancel() }
internal fun interface MarketFallbackScheduler { fun schedule(delayMillis: Int, task: () -> Unit): MarketFallbackTask }
internal class KuiklyMarketFallbackScheduler : MarketFallbackScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): MarketFallbackTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) { task(); timer.cancel() }
        return MarketFallbackTask(timer::cancel)
    }
}

/** Owns real-quote fallback eligibility and its 1.8s timeout. */
internal class MarketFallbackCoordinator(
    val state: MarketFallbackStatePort,
    private val scheduler: MarketFallbackScheduler,
) {
    private val pendingSymbols = mutableSetOf<String>()
    private val tasks = mutableListOf<MarketFallbackTask>()
    private var version = 0

    fun onRealQuoteRequested(symbol: String) {
        pendingSymbols += symbol
        val current = version
        tasks += scheduler.schedule(PROMPT_DELAY_MS) {
            if (current == version && symbol in pendingSymbols && state.promptSymbol.isEmpty()) {
                state.promptSymbol = symbol
            }
        }
    }

    fun onQuoteResolved(symbol: String) {
        pendingSymbols.remove(symbol)
        if (state.promptSymbol == symbol) state.promptSymbol = ""
    }

    fun dismissPrompt() { onQuoteResolved(state.promptSymbol) }

    fun takePromptSymbol(): String {
        val symbol = state.promptSymbol
        onQuoteResolved(symbol)
        return symbol
    }

    fun onDisappear() {
        ++version
        pendingSymbols.clear()
        state.promptSymbol = ""
    }

    fun onDestroy() {
        onDisappear()
        tasks.forEach(MarketFallbackTask::cancel)
        tasks.clear()
    }

    private companion object { const val PROMPT_DELAY_MS = 1_800 }
}
