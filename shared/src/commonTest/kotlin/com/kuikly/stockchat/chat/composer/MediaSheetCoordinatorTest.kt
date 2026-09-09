package com.kuikly.stockchat.chat.composer

import com.kuikly.stockchat.chat.composer.state.MediaSheetCoordinator
import com.kuikly.stockchat.chat.composer.state.MediaSheetScheduledTask
import com.kuikly.stockchat.chat.composer.state.MediaSheetScheduler
import com.kuikly.stockchat.chat.composer.state.PlainComposerAttachmentState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaSheetCoordinatorTest {
    @Test
    fun opensOnSecondFrameAndDismissesAfterExitWindow() {
        val scheduler = FakeMediaScheduler()
        val state = PlainComposerAttachmentState()
        val coordinator = MediaSheetCoordinator(state, scheduler)

        coordinator.open()
        assertTrue(state.mediaSheetMounted)
        assertFalse(state.mediaSheetPresented)
        scheduler.runNext(16)
        assertTrue(state.mediaSheetPresented)
        coordinator.dismiss()
        assertFalse(state.mediaSheetPresented)
        assertTrue(state.mediaSheetMounted)
        scheduler.runNext(260)
        assertFalse(state.mediaSheetMounted)
    }

    @Test
    fun reopeningCancelsStaleDismiss() {
        val scheduler = FakeMediaScheduler()
        val state = PlainComposerAttachmentState()
        val coordinator = MediaSheetCoordinator(state, scheduler)

        coordinator.open()
        scheduler.runNext(16)
        coordinator.dismiss()
        coordinator.open()
        scheduler.runNext(16)
        scheduler.runAll(260)

        assertTrue(state.mediaSheetMounted)
        assertTrue(state.mediaSheetPresented)
    }
}

private class FakeMediaScheduler : MediaSheetScheduler {
    private data class Task(val delay: Int, val action: () -> Unit, var cancelled: Boolean = false)
    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMillis: Int, task: () -> Unit): MediaSheetScheduledTask {
        val entry = Task(delayMillis, task)
        tasks += entry
        return object : MediaSheetScheduledTask { override fun cancel() { entry.cancelled = true } }
    }

    fun runNext(delay: Int) {
        val task = tasks.firstOrNull { it.delay == delay && !it.cancelled } ?: error("No task for $delay")
        task.cancelled = true
        task.action()
    }

    fun runAll(delay: Int) {
        while (tasks.any { it.delay == delay && !it.cancelled }) runNext(delay)
    }
}
