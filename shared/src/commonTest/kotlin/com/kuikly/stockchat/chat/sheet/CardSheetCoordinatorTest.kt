package com.kuikly.stockchat.chat.sheet

import com.kuikly.stockchat.cards.core.UnknownCardModel
import com.kuikly.stockchat.chat.sheet.state.CardSheetCoordinator
import com.kuikly.stockchat.chat.sheet.state.CardSheetScheduledTask
import com.kuikly.stockchat.chat.sheet.state.CardSheetScheduler
import com.kuikly.stockchat.chat.sheet.state.ChatSheetLevel
import com.kuikly.stockchat.chat.sheet.state.PlainCardSheetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardSheetCoordinatorTest {
    @Test
    fun openMountsOnNextTickAndDismissWaitsForExitAnimation() {
        val scheduler = FakeSheetScheduler()
        val state = PlainCardSheetState()
        val coordinator = CardSheetCoordinator(state, scheduler)

        coordinator.open(UnknownCardModel("news", "test"))
        assertFalse(state.mounted)
        assertTrue(state.presented)
        scheduler.runNext(0)
        assertTrue(state.mounted)
        coordinator.dismiss()
        assertFalse(state.interactive)
        assertFalse(state.presented)
        scheduler.runNext(420)
        assertFalse(state.mounted)
        assertNull(state.model)
    }

    @Test
    fun panMovesThroughLevelsAndDismissesFromPeek() {
        val scheduler = FakeSheetScheduler()
        val state = PlainCardSheetState()
        val coordinator = CardSheetCoordinator(state, scheduler)
        coordinator.open(UnknownCardModel("news", "test"))

        coordinator.lower()
        assertEquals(ChatSheetLevel.PEEK, state.level)
        coordinator.onPan("start", 100f)
        coordinator.onPan("end", 60f)
        assertEquals(ChatSheetLevel.HALF, state.level)
        coordinator.lower()
        coordinator.lower()
        assertFalse(state.presented)
    }
}

private class FakeSheetScheduler : CardSheetScheduler {
    private data class Task(val delay: Int, val action: () -> Unit, var cancelled: Boolean = false)
    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMillis: Int, task: () -> Unit): CardSheetScheduledTask {
        val entry = Task(delayMillis, task)
        tasks += entry
        return object : CardSheetScheduledTask { override fun cancel() { entry.cancelled = true } }
    }

    fun runNext(delay: Int) {
        val task = tasks.firstOrNull { it.delay == delay && !it.cancelled } ?: error("No task for $delay")
        task.cancelled = true
        task.action()
    }
}
