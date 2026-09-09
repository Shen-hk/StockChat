package com.kuikly.stockchat.chat.scroll

import com.kuikly.stockchat.chat.scroll.state.ChatScrollCoordinator
import com.kuikly.stockchat.chat.scroll.state.ChatScrollScheduledTask
import com.kuikly.stockchat.chat.scroll.state.ChatScrollScheduler
import com.kuikly.stockchat.chat.scroll.state.ChatScrollState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatScrollCoordinatorTest {

    @Test
    fun sendAnimatesOnceThenStreamingTicksUseNonAnimatedScroll() {
        val scheduler = FakeScrollScheduler()
        val scrolls = mutableListOf<Boolean>()
        val coordinator = coordinator(scheduler, scrolls)
        coordinator.onAppear()

        coordinator.onSendRequested()
        coordinator.onStreamStateObserved(true)
        scheduler.runNext(120)

        assertEquals(listOf(true, false), scrolls)
    }

    @Test
    fun userScrollingAwaySuppressesFollowAndStreamEndDoesNotStealPosition() {
        val scheduler = FakeScrollScheduler()
        val scrolls = mutableListOf<Boolean>()
        val coordinator = coordinator(scheduler, scrolls)
        coordinator.onAppear()
        coordinator.onSendRequested()
        coordinator.onStreamStateObserved(true)
        coordinator.onUserScroll(isAtBottom = false)
        coordinator.onStreamStateObserved(false)
        scheduler.runNext(120)

        assertEquals(listOf(true), scrolls)
    }

    @Test
    fun streamEndGetsVersionedGraceWindowThenExpires() {
        val scheduler = FakeScrollScheduler()
        val scrolls = mutableListOf<Boolean>()
        val coordinator = coordinator(scheduler, scrolls)
        coordinator.onAppear()
        coordinator.onSendRequested()
        coordinator.onStreamStateObserved(true)
        scheduler.runNext(120)
        coordinator.onStreamStateObserved(false)
        scheduler.runNext(120)

        assertTrue(coordinator.shouldKeepAtBottom())
        scheduler.runNext(1_500)
        assertFalse(coordinator.shouldKeepAtBottom())
        assertTrue(scrolls.contains(false))
    }

    @Test
    fun leavingPageCancelsPendingScrollTicks() {
        val scheduler = FakeScrollScheduler()
        val scrolls = mutableListOf<Boolean>()
        val coordinator = coordinator(scheduler, scrolls)
        coordinator.onAppear()
        coordinator.onSendRequested()
        coordinator.onDisappear()

        scheduler.runAll()
        assertEquals(listOf(true), scrolls)
    }

    private fun coordinator(
        scheduler: FakeScrollScheduler,
        scrolls: MutableList<Boolean>,
    ) = ChatScrollCoordinator(
        state = ChatScrollState(),
        scheduler = scheduler,
        onScrollToBottom = scrolls::add,
        onResetFollowUps = {},
        onScheduleFollowUps = {},
    )
}

private class FakeScrollScheduler : ChatScrollScheduler {
    private data class Task(
        val delay: Int,
        val repeating: Boolean,
        val action: () -> Unit,
        var cancelled: Boolean = false,
    )

    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): ChatScrollScheduledTask {
        val entry = Task(delayMillis, repeating, task)
        tasks += entry
        return object : ChatScrollScheduledTask {
            override fun cancel() {
                entry.cancelled = true
            }
        }
    }

    fun runNext(delayMillis: Int) {
        val entry = tasks.firstOrNull { it.delay == delayMillis && !it.cancelled }
            ?: error("No live task for ${delayMillis}ms")
        if (!entry.repeating) entry.cancelled = true
        entry.action()
    }

    fun runAll() {
        tasks.filterNot { it.cancelled }.toList().forEach { entry ->
            if (!entry.repeating) entry.cancelled = true
            entry.action()
        }
    }
}
