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

    /**
     * 回归（2026-09-10）：跳市场页会先走 onDisappear，把 420ms 的自动复位
     * 定时器取消掉，marketTabSelected 停在 true——返回聊天页后滑块卡在
     * 「看行情」。消失即必须复位，且返回后可以再次发起跳转。
     */
    @Test
    fun disappearingAfterMarketTapResetsTheTabSlider() {
        val scheduler = FakeWelcomeScheduler()
        val state = PlainChatWelcomeState()
        val effects = mutableListOf<ChatWelcomeEffect>()
        val coordinator = coordinator(state, scheduler, effects::add)
        coordinator.onAppear(sessionEmpty = true, fullMode = true)

        coordinator.onOpenMarketRequested()
        assertTrue(state.marketTabSelected)
        scheduler.runNext(240) // 跳转市场页，本页随之 onDisappear
        coordinator.onDisappear()
        assertFalse(state.marketTabSelected)
        scheduler.runAll() // 复位定时器不得在消失后再改状态
        assertFalse(state.marketTabSelected)

        coordinator.onAppear(sessionEmpty = true, fullMode = true)
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

    private fun coordinator(
        state: ChatWelcomeStatePort,
        scheduler: FakeWelcomeScheduler,
        onEffect: (ChatWelcomeEffect) -> Unit = {},
    ): ChatWelcomeCoordinator = ChatWelcomeCoordinator(
        state = state,
        starterStore = WelcomeStarterStore(InMemoryKeyValueStorage(), setOf("MOVE", "TERM")),
        scheduler = scheduler,
        reducedMotion = false,
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
