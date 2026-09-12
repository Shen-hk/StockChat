package com.kuikly.stockchat.chat.message

import com.kuikly.stockchat.chat.message.state.MessageActionCoordinator
import com.kuikly.stockchat.chat.message.state.MessageActionEffect
import com.kuikly.stockchat.chat.message.state.MessageActionFallback
import com.kuikly.stockchat.chat.message.state.MessageActionScheduledTask
import com.kuikly.stockchat.chat.message.state.MessageActionScheduler
import com.kuikly.stockchat.chat.message.state.PlainMessageActionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageActionCoordinatorTest {
    @Test
    fun newlyMountedMenuPresentsOnTheSecondFrame() {
        val f = fixture()

        f.coordinator.show("选中文本", allowFollowUp = true, pageX = 20f, pageY = 30f)

        assertTrue(f.state.mounted)
        assertFalse(f.state.presented)
        f.scheduler.run(0)
        assertTrue(f.state.presented)
        assertTrue(f.state.followUpAllowed)
    }

    @Test
    fun dismissClearsNativeSelectionThenUnmountsAfterExit() {
        val f = fixture()
        f.coordinator.beginSelection("m1", 10f, 20f, MessageActionFallback("fallback", true))
        f.coordinator.show("选中文本", allowFollowUp = true, pageX = 10f, pageY = 20f)
        f.scheduler.run(0)

        f.coordinator.dismiss()

        assertFalse(f.state.presented)
        assertTrue(f.effects.contains(MessageActionEffect.ClearSelection("m1")))
        f.scheduler.run(220)
        assertFalse(f.state.mounted)
    }

    @Test
    fun openingMenuInvalidatesThePendingSelectionFallback() {
        val f = fixture()
        f.coordinator.beginSelection("m1", 10f, 20f, MessageActionFallback("fallback", true))
        f.coordinator.show("即时菜单", allowFollowUp = false, pageX = 10f, pageY = 20f)

        f.scheduler.run(500)

        assertFalse(f.effects.any { it is MessageActionEffect.CollectSelection })
    }

    private fun fixture(): Fixture {
        val state = PlainMessageActionState()
        val scheduler = FakeMessageActionScheduler()
        val effects = mutableListOf<MessageActionEffect>()
        return Fixture(
            state,
            scheduler,
            effects,
            MessageActionCoordinator(state, scheduler, effects::add),
        )
    }

    private data class Fixture(
        val state: PlainMessageActionState,
        val scheduler: FakeMessageActionScheduler,
        val effects: MutableList<MessageActionEffect>,
        val coordinator: MessageActionCoordinator,
    )
}

private class FakeMessageActionScheduler : MessageActionScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): MessageActionScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return MessageActionScheduledTask { entry.cancelled = true }
    }

    fun run(delayMillis: Int) {
        entries.filter { it.delay == delayMillis && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}
