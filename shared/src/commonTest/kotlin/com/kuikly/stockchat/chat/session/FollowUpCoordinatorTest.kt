package com.kuikly.stockchat.chat.session

import com.kuikly.stockchat.chat.session.state.FollowUpCoordinator
import com.kuikly.stockchat.chat.session.state.FollowUpScheduledTask
import com.kuikly.stockchat.chat.session.state.FollowUpScheduler
import com.kuikly.stockchat.chat.session.state.PlainFollowUpState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowUpCoordinatorTest {
    @Test
    fun presentsOnlyAfterMountAndSecondFrame() {
        val state = PlainFollowUpState()
        val scheduler = FakeFollowUpScheduler()
        val coordinator = FollowUpCoordinator(state, scheduler)

        coordinator.schedulePresentation()
        scheduler.run(320)
        assertTrue(state.mounted)
        assertFalse(state.presented)
        scheduler.run(16)
        assertTrue(state.presented)
    }

    @Test
    fun resetInvalidatesPendingPresentation() {
        val state = PlainFollowUpState()
        val scheduler = FakeFollowUpScheduler()
        val coordinator = FollowUpCoordinator(state, scheduler)

        coordinator.schedulePresentation()
        coordinator.reset()
        scheduler.run(320)

        assertFalse(state.mounted)
        assertFalse(state.presented)
    }
}

private class FakeFollowUpScheduler : FollowUpScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()
    override fun schedule(delayMillis: Int, task: () -> Unit): FollowUpScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return FollowUpScheduledTask { entry.cancelled = true }
    }

    fun run(delayMillis: Int) {
        entries.filter { it.delay == delayMillis && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}
