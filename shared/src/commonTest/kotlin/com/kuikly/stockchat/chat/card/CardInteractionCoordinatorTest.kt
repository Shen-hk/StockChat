package com.kuikly.stockchat.chat.card

import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.chat.card.state.CardInteractionCoordinator
import com.kuikly.stockchat.chat.card.state.CardInteractionEffect
import com.kuikly.stockchat.chat.card.state.PlainCardInteractionState
import com.kuikly.stockchat.data.provider.Quote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardInteractionCoordinatorTest {
    @Test
    fun deepDiveThreadStreamsThenAcceptsFollowUp() {
        val f = fixture()
        val card = insightCard("card-1", "先分析成交量")

        f.coordinator.startSubThread(card)

        assertEquals(1, f.state.subThreads.size)
        assertTrue(f.state.subThreads.single().streaming)
        assertTrue(f.effects.any { it is CardInteractionEffect.RequestSubThread })
        f.coordinator.onSubThreadDone("card-1", "成交量显示资金正在流入")
        f.coordinator.updateSubThreadInput("card-1", "结合估值呢？")

        f.coordinator.sendSubThread("card-1")

        assertTrue(f.state.subThreads.single().streaming)
        assertEquals("", f.state.subThreads.single().input)
        assertTrue(f.effects.any {
            it == CardInteractionEffect.RequestSubThread("card-1", "结合估值呢？")
        })
    }

    @Test
    fun retryLockRejectsConcurrentRepairsAndResetsAfterError() {
        val f = fixture()

        f.coordinator.retryCard("m1", "b1", "", "raw")
        f.coordinator.retryCard("m2", "b2", "news", "other")

        assertEquals("m1:b1", f.state.repairingCardKey)
        assertEquals(1, f.effects.filterIsInstance<CardInteractionEffect.RetryCard>().size)
        f.coordinator.onRetryError()
        assertEquals("", f.state.repairingCardKey)
    }

    @Test
    fun resetClearsCardLocalPresentationState() {
        val f = fixture()
        f.coordinator.toggleExpanded("card")
        f.coordinator.setFocused("card", true)
        f.coordinator.toggleDrill("source")
        f.coordinator.retryCard("m", "b", "quote", "raw")

        f.coordinator.resetForNewSession()

        assertEquals("", f.state.expandedCardKey)
        assertEquals("", f.state.focusedCardKey)
        assertEquals("", f.state.repairingCardKey)
        assertTrue(f.state.drilledKeys.isEmpty())
        assertFalse(f.state.subThreads.isNotEmpty())
    }

    private fun fixture(): Fixture {
        val state = PlainCardInteractionState()
        val effects = mutableListOf<CardInteractionEffect>()
        return Fixture(state, effects, CardInteractionCoordinator(state, effects::add))
    }

    private fun insightCard(cardId: String, summary: String): CardModel = InsightCardModel(
        quote = Quote.placeholder("000001", "平安银行"),
        cardId = cardId,
        summary = summary,
    )

    private data class Fixture(
        val state: PlainCardInteractionState,
        val effects: MutableList<CardInteractionEffect>,
        val coordinator: CardInteractionCoordinator,
    )
}
