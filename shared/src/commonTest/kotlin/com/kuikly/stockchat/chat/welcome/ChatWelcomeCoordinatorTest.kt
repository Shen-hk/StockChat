package com.kuikly.stockchat.chat.welcome

import com.kuikly.stockchat.chat.welcome.data.WelcomeStarterStore
import com.kuikly.stockchat.chat.welcome.state.ChatWelcomeCoordinator
import com.kuikly.stockchat.chat.welcome.state.ChatWelcomeEffect
import com.kuikly.stockchat.chat.welcome.state.ChatWelcomeStatePort
import com.kuikly.stockchat.chat.welcome.state.PlainChatWelcomeState
import com.kuikly.stockchat.chat.welcome.state.WelcomeScheduledTask
import com.kuikly.stockchat.chat.welcome.state.WelcomeScheduler
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatWelcomeCoordinatorTest {

    @Test
    fun starterUsagePersistsAndResetsOnlyAfterAllKindsWereUsed() {
        val storage = InMemoryKeyValueStorage()
        val store = WelcomeStarterStore(storage, setOf("MOVE", "TERM"))

        store.markUsed("MOVE")
        assertEquals(setOf("MOVE"), store.usedKinds())
        store.markUsed("TERM")
        assertEquals(setOf("MOVE", "TERM"), store.usedKinds())
        store.markUsed("MOVE")
        assertEquals(setOf("MOVE"), store.usedKinds())
        store.markUsed("UNKNOWN")
        assertEquals(setOf("MOVE"), store.usedKinds())
    }

    @Test
    fun mountedWelcomeUsesTwoPhaseEntranceAndSafetyDoesNotOverrideIt() {
        val scheduler = FakeWelcomeScheduler()
        val state = PlainChatWelcomeState()
        val coordinator = coordinator(state, scheduler)

        coordinator.onAppear(sessionEmpty = true, fullMode = true)
        coordinator.onWelcomeMounted()

        assertFalse(state.entranceVisible)
        scheduler.runNext(32)
        assertTrue(state.entranceVisible)
        scheduler.runNext(600)
        assertTrue(state.entranceVisible)
    }

    @Test
    fun returningDuringEntranceRestartsTheSecondPhase() {
        val scheduler = FakeWelcomeScheduler()
        val state = PlainChatWelcomeState()
        val coordinator = coordinator(state, scheduler)

        coordinator.onAppear(sessionEmpty = true, fullMode = true)
        coordinator.onWelcomeMounted()
        coordinator.onDisappear()
        coordinator.onAppear(sessionEmpty = true, fullMode = true)

        scheduler.runNext(32)
        assertTrue(state.entranceVisible)
    }

    @Test
    fun leavingThePageCancelsDelayedEffectsAndLocksTheKeywordLoop() {
        val scheduler = FakeWelcomeScheduler()
        val state = PlainChatWelcomeState()
        val effects = mutableListOf<ChatWelcomeEffect>()
        val coordinator = coordinator(state, scheduler, effects::add)

        coordinator.onAppear(sessionEmpty = true, fullMode = true)
        coordinator.onOpenMarketRequested()
        coordinator.onConversationStarted()
        assertTrue(state.keywordStopped)
        coordinator.onDisappear()

        scheduler.runAll()
        assertTrue(effects.isEmpty())
        assertFalse(state.cursorVisible)
    }

    @Test
    fun marketTapPreservesHapticThenNavigationThenResetsSelection() {
        val scheduler = FakeWelcomeScheduler()
        val state = PlainChatWelcomeState()
        val effects = mutableListOf<ChatWelcomeEffect>()
        val coordinator = coordinator(state, scheduler, effects::add)
        coordinator.onAppear(sessionEmpty = true, fullMode = true)

        coordinator.onOpenMarketRequested()
        assertTrue(state.marketTabSelected)
        scheduler.runNext(240)
        assertEquals(
            listOf(ChatWelcomeEffect.HAPTIC_IMPACT, ChatWelcomeEffect.OPEN_MARKET),
            effects,
        )
        scheduler.runNext(420)
        assertFalse(state.marketTabSelected)
    }

    /** 市场页返回后才让 Tab 从“看行情”滑回“问AI”，而不是在遮罩下静默复位。 */
    @Test
    fun marketReturnAnimatesTheTabSliderHome() {
        val scheduler = FakeWelcomeScheduler()
        val state = PlainChatWelcomeState()
        val effects = mutableListOf<ChatWelcomeEffect>()
        val coordinator = coordinator(state, scheduler, effects::add)
        coordinator.onAppear(sessionEmpty = true, fullMode = true)

        coordinator.onOpenMarketRequested()
        assertTrue(state.marketTabSelected)
        scheduler.runNext(240) // 跳转市场页，本页随之 onDisappear
        coordinator.onDisappear()
        assertTrue(state.marketTabSelected)

        coordinator.onAppear(sessionEmpty = true, fullMode = true)
        scheduler.runNext(120)
        assertFalse(state.marketTabSelected)
        coordinator.onOpenMarketRequested()
        assertTrue(state.marketTabSelected)
        scheduler.runNext(240)
        assertEquals(
            listOf(
                ChatWelcomeEffect.HAPTIC_IMPACT,
                ChatWelcomeEffect.OPEN_MARKET,
                ChatWelcomeEffect.HAPTIC_IMPACT,
                ChatWelcomeEffect.OPEN_MARKET,
            ),
            effects,
        )
    }

    @Test
    fun marketReturnAndNewChatReplaceCardsThenReplayEntrance() {
        val scheduler = FakeWelcomeScheduler()
        val state = PlainChatWelcomeState()
        var refreshes = 0
        val coordinator = coordinator(state, scheduler, onRefreshStarters = { refreshes++ })

        coordinator.onAppear(sessionEmpty = true, fullMode = true)
        coordinator.onWelcomeMounted()
        scheduler.runNext(32)
        coordinator.onOpenMarketRequested()
        coordinator.onDisappear()
        coordinator.onAppear(sessionEmpty = true, fullMode = true)

        assertEquals(1, refreshes)
        assertFalse(state.entranceVisible)
        scheduler.runNext(32)
        assertTrue(state.entranceVisible)

        coordinator.onNewEmptySession()
        assertEquals(2, refreshes)
        assertFalse(state.entranceVisible)
        scheduler.runNext(32)
        assertTrue(state.entranceVisible)
    }

    private fun coordinator(
        state: ChatWelcomeStatePort,
        scheduler: FakeWelcomeScheduler,
        onEffect: (ChatWelcomeEffect) -> Unit = {},
        onRefreshStarters: () -> Unit = {},
    ): ChatWelcomeCoordinator = ChatWelcomeCoordinator(
        state = state,
        starterStore = WelcomeStarterStore(InMemoryKeyValueStorage(), setOf("MOVE", "TERM")),
        scheduler = scheduler,
        reducedMotion = false,
        onRefreshStarters = onRefreshStarters,
        onEffect = onEffect,
    )
}

private class FakeWelcomeScheduler : WelcomeScheduler {
    private data class Task(
        val delay: Int,
        val repeating: Boolean,
        val block: () -> Unit,
        var cancelled: Boolean = false,
    )

    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): WelcomeScheduledTask {
        val entry = Task(delayMillis, repeating, task)
        tasks += entry
        return object : WelcomeScheduledTask {
            override fun cancel() {
                entry.cancelled = true
            }
        }
    }

    fun runNext(delayMillis: Int) {
        val entry = tasks.firstOrNull { it.delay == delayMillis && !it.cancelled }
            ?: error("No live task for ${delayMillis}ms")
        if (!entry.repeating) entry.cancelled = true
        entry.block()
    }

    fun runAll() {
        tasks.filterNot { it.cancelled }.toList().forEach { entry ->
            if (!entry.repeating) entry.cancelled = true
            entry.block()
        }
    }
}
