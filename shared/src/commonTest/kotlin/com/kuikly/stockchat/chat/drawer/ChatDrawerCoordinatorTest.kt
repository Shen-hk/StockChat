package com.kuikly.stockchat.chat.drawer

import com.kuikly.stockchat.chat.drawer.state.ChatDrawerCoordinator
import com.kuikly.stockchat.chat.drawer.state.ChatDrawerEffect
import com.kuikly.stockchat.chat.drawer.state.DrawerGesturePhase
import com.kuikly.stockchat.chat.drawer.state.DrawerScheduledTask
import com.kuikly.stockchat.chat.drawer.state.DrawerScheduler
import com.kuikly.stockchat.chat.drawer.state.PlainChatDrawerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatDrawerCoordinatorTest {
    @Test
    fun programmaticOpenKeepsMountedThenPresentedSequence() {
        val fixture = fixture()

        fixture.coordinator.setOpen(true)

        assertTrue(fixture.state.open)
        assertTrue(fixture.state.mounted)
        assertFalse(fixture.state.presented)
        assertEquals(
            listOf(ChatDrawerEffect.BLUR_COMPOSER, ChatDrawerEffect.RESET_HISTORY_QUERY),
            fixture.effects,
        )

        fixture.scheduler.run(0)
        assertTrue(fixture.state.presented)
        fixture.scheduler.run(395)
        assertEquals(ChatDrawerEffect.HAPTIC_IMPACT, fixture.effects.last())
    }

    @Test
    fun shortLeftMoveClosesAnOpenDrawerLikeTheLegacyGesture() {
        val fixture = fixture()
        fixture.coordinator.setOpen(true)
        fixture.scheduler.runAll()
        fixture.effects.clear()

        fixture.coordinator.onPan("start", 200f)
        fixture.coordinator.onPan("move", 195f)
        fixture.coordinator.onPan("end", 195f)

        assertFalse(fixture.state.open)
        assertFalse(fixture.state.presented)
        assertTrue(fixture.state.mounted)
        assertEquals(DrawerGesturePhase.SETTLING, fixture.state.motion.phase)
        assertEquals(ChatDrawerEffect.HAPTIC_IMPACT, fixture.effects.single())

        fixture.scheduler.run(375)
        assertFalse(fixture.state.mounted)
        fixture.scheduler.run(440)
        assertEquals(DrawerGesturePhase.IDLE, fixture.state.motion.phase)
    }

    @Test
    fun edgeDragPastHalfOpensAndSettles() {
        val fixture = fixture()

        fixture.coordinator.onPan("start", 0f)
        fixture.coordinator.onPan("move", 200f)
        fixture.coordinator.onPan("end", 200f)

        assertTrue(fixture.state.open)
        assertTrue(fixture.state.presented)
        assertEquals(0f, fixture.state.motion.offsetX)
        assertEquals(DrawerGesturePhase.SETTLING, fixture.state.motion.phase)
    }

    private fun fixture(): Fixture {
        val state = PlainChatDrawerState()
        val scheduler = FakeDrawerScheduler()
        val effects = mutableListOf<ChatDrawerEffect>()
        return Fixture(state, scheduler, effects, ChatDrawerCoordinator(state, scheduler, effects::add))
    }

    private data class Fixture(
        val state: PlainChatDrawerState,
        val scheduler: FakeDrawerScheduler,
        val effects: MutableList<ChatDrawerEffect>,
        val coordinator: ChatDrawerCoordinator,
    )
}

private class FakeDrawerScheduler : DrawerScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): DrawerScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return DrawerScheduledTask { entry.cancelled = true }
    }

    fun run(delay: Int) {
        entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }

    fun runAll() = entries.map { it.delay }.distinct().sorted().forEach(::run)
}
