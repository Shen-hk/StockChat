package com.kuikly.stockchat.chat.compare

import com.kuikly.stockchat.chat.compare.state.CompareInsightCoordinator
import com.kuikly.stockchat.chat.compare.state.CompareInsightEffect
import com.kuikly.stockchat.chat.compare.state.CompareInsightRequester
import com.kuikly.stockchat.chat.compare.state.CompareInsightState
import com.kuikly.stockchat.chat.compare.state.CompareTextRevealer
import com.kuikly.stockchat.chat.compare.state.CompareTextRevealerFactory
import com.kuikly.stockchat.chat.compare.state.PlainCompareInsightState
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.provider.Quote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompareInsightCoordinatorTest {
    @Test
    fun secondCardCandidateBuildsCompareCardAndStartsStream() {
        val f = fixture()
        val left = quote("600519.SH", "贵州茅台")
        val right = quote("000001.SZ", "平安银行")
        val quotes = mapOf(left.symbol to left, right.symbol to right)

        f.coordinator.selectCardCandidate("card-a", left.symbol) { quotes[it] }
        assertEquals(left.symbol, f.state.candidateSymbol)
        assertEquals<List<CompareInsightEffect>>(
            listOf(CompareInsightEffect.RequestQuote(left.symbol)),
            f.effects,
        )

        f.effects.clear()
        f.coordinator.selectCardCandidate("card-b", right.symbol) { quotes[it] }

        assertEquals(listOf(left.symbol, right.symbol), f.state.card?.quotes?.map { it.symbol })
        assertEquals("", f.state.candidateSymbol)
        assertEquals(CompareInsightState.LOADING, f.state.insightState)
        assertTrue(f.requester.prompt.contains("股票 A：贵州茅台"))
    }

    @Test
    fun resetInvalidatesAnInFlightTermStreamAndClearsCardSession() {
        val f = fixture()
        f.coordinator.syncIslandCard(quote("600519.SH", "贵州茅台"), quote("000001.SZ", "平安银行"))
        val left = Glossary.all.first()
        val right = Glossary.all.drop(1).first()
        f.coordinator.requestTermInsight(left, right)
        val stale = f.requester.callbacks

        f.coordinator.resetForNewSession()
        stale.onDelta("过期内容")
        stale.onDone()

        assertEquals(null, f.state.card)
        assertEquals("", f.state.candidateSymbol)
        assertEquals(CompareInsightState.IDLE, f.state.insightState)
        assertEquals("", f.state.insightText)
    }

    @Test
    fun completedStreamPublishesRevealedTextAndReadyState() {
        val f = fixture()
        val left = quote("600519.SH", "贵州茅台")
        val right = quote("000001.SZ", "平安银行")

        f.coordinator.requestStockInsight(left, right)
        f.requester.callbacks.onDelta("两只股票走势不同。")
        f.requester.callbacks.onDone()

        assertEquals("两只股票走势不同。", f.state.insightText)
        assertEquals(CompareInsightState.READY, f.state.insightState)
    }

    private fun fixture(): Fixture {
        val state = PlainCompareInsightState()
        val requester = FakeRequester()
        val effects = mutableListOf<CompareInsightEffect>()
        return Fixture(
            state = state,
            requester = requester,
            effects = effects,
            coordinator = CompareInsightCoordinator(
                state = state,
                requester = requester,
                revealerFactory = ImmediateRevealerFactory,
                onEffect = effects::add,
            ),
        )
    }

    private fun quote(symbol: String, name: String): Quote = Quote.placeholder(symbol, name)

    private data class Fixture(
        val state: PlainCompareInsightState,
        val requester: FakeRequester,
        val effects: MutableList<CompareInsightEffect>,
        val coordinator: CompareInsightCoordinator,
    )
}

private class FakeRequester : CompareInsightRequester {
    lateinit var prompt: String
    lateinit var callbacks: Callbacks

    override fun request(
        prompt: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        this.prompt = prompt
        callbacks = Callbacks(onDelta, onDone, onError)
    }

    data class Callbacks(
        val onDelta: (String) -> Unit,
        val onDone: () -> Unit,
        val onError: (String) -> Unit,
    )
}

private object ImmediateRevealerFactory : CompareTextRevealerFactory {
    override fun create(onRevealed: (String) -> Unit): CompareTextRevealer = object : CompareTextRevealer {
        private var text = ""
        private var cancelled = false

        override fun append(delta: String) {
            if (cancelled) return
            text += delta
            onRevealed(text)
        }

        override fun complete(onComplete: () -> Unit) {
            if (!cancelled) onComplete()
        }

        override fun cancel() {
            cancelled = true
        }
    }
}
