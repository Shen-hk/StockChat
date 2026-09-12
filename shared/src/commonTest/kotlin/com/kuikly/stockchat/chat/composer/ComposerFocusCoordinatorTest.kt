package com.kuikly.stockchat.chat.composer

import com.kuikly.stockchat.chat.composer.state.ComposerFocusCoordinator
import com.kuikly.stockchat.chat.composer.state.ComposerFocusEffect
import com.kuikly.stockchat.chat.composer.state.ComposerFocusScheduledTask
import com.kuikly.stockchat.chat.composer.state.ComposerFocusScheduler
import com.kuikly.stockchat.chat.composer.state.PlainComposerFocusState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerFocusCoordinatorTest {
    @Test
    fun firstFocusWaitsForThePostMountFrame() {
        val f = fixture()

        f.coordinator.expand(requestFocus = true, voiceBusy = false)

        assertTrue(f.state.expanded)
        assertTrue(f.effects.contains(ComposerFocusEffect.LeaveVoiceInputMode))
        assertTrue(f.effects.contains(ComposerFocusEffect.ScheduleChromePresentation(true)))
        assertFalse(f.effects.contains(ComposerFocusEffect.FocusInput))
        f.scheduler.run(500)
        assertTrue(f.effects.contains(ComposerFocusEffect.FocusInput))
    }

    @Test
    fun keyboardLayoutRecoversFocusBeforeFallbackAndInvalidatesIt() {
        val f = fixture()
        f.coordinator.expand(requestFocus = true, voiceBusy = false)
        f.coordinator.onKeyboardHeightChanged(height = 320f, durationSeconds = 0.2f)

        assertTrue(f.state.keyboardVisible)
        assertEquals(320f, f.state.keyboardHeight)
        f.scheduler.run(248)
        assertEquals(1, f.effects.count { it == ComposerFocusEffect.FocusInput })
        f.scheduler.run(500)
        assertEquals(1, f.effects.count { it == ComposerFocusEffect.FocusInput })
    }

    @Test
    fun collapseInvalidatesPendingFocusAndRequestsTheExistingCleanupEffects() {
        val f = fixture()
        f.coordinator.expand(requestFocus = true, voiceBusy = false)

        f.coordinator.collapse()
        f.scheduler.run(500)

        assertFalse(f.state.expanded)
        assertFalse(f.effects.contains(ComposerFocusEffect.FocusInput))
        assertTrue(f.effects.contains(ComposerFocusEffect.ScheduleChromePresentation(false)))
        assertTrue(f.effects.contains(ComposerFocusEffect.ClearActiveCommand))
        assertTrue(f.effects.contains(ComposerFocusEffect.CloseAssistantPanel))
        assertTrue(f.effects.contains(ComposerFocusEffect.CancelVoiceSession))
    }

    private fun fixture(): Fixture {
        val state = PlainComposerFocusState()
        val scheduler = FakeComposerFocusScheduler()
        val effects = mutableListOf<ComposerFocusEffect>()
        return Fixture(
            state = state,
            scheduler = scheduler,
            effects = effects,
            coordinator = ComposerFocusCoordinator(state, scheduler, effects::add),
        )
    }

    private data class Fixture(
        val state: PlainComposerFocusState,
        val scheduler: FakeComposerFocusScheduler,
        val effects: MutableList<ComposerFocusEffect>,
        val coordinator: ComposerFocusCoordinator,
    )
}

private class FakeComposerFocusScheduler : ComposerFocusScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): ComposerFocusScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return ComposerFocusScheduledTask { entry.cancelled = true }
    }

    fun run(delayMillis: Int) {
        entries.filter { it.delay == delayMillis && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}
