package com.kuikly.stockchat.chat.composer

import com.kuikly.stockchat.chat.composer.state.AssistantPanel
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantCoordinator
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantEffect
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantScheduler
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantTask
import com.kuikly.stockchat.chat.composer.state.PlainComposerAssistantState
import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.CatalogEntry
import com.kuikly.stockchat.composer.TriggerSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposerAssistantCoordinatorTest {
    @Test
    fun closePanelClearsOnlyTransientPanelState() {
        val state = PlainComposerAssistantState().apply {
            panel = AssistantPanel.AT_MENTION
            atHighlight = 2
            slashHighlight = 1
            slashUnknown = "unknown"
            paramCommand = null
            recentMentions += "600519.SH"
        }
        val coordinator = ComposerAssistantCoordinator(state)

        coordinator.closePanel()

        assertEquals(AssistantPanel.NONE, state.panel)
        assertEquals(0, state.atHighlight)
        assertEquals(0, state.slashHighlight)
        assertEquals("", state.slashUnknown)
        assertTrue(state.atCandidates.isEmpty())
        assertTrue(state.slashCandidates.isEmpty())
        assertEquals(listOf("600519.SH"), state.recentMentions)
    }

    @Test
    fun renderKeyRebuildUsesSingleFrameToken() {
        val state = PlainComposerAssistantState()
        val coordinator = ComposerAssistantCoordinator(state)

        coordinator.bumpParamRenderKey()
        coordinator.bumpParamRenderKey()

        assertEquals(listOf(0), state.paramPanelRenderKey)
    }

    @Test
    fun remotePoolDeduplicatesAndQuoteFillKeepsCandidateHighlight() {
        val state = PlainComposerAssistantState()
        val coordinator = ComposerAssistantCoordinator(state)
        val entry = CatalogEntry("000001.SZ", "平安银行", "深A")
        state.atCandidates += AtCandidate(entry, 1f, 1f, "搜索")
        state.atHighlight = 1

        assertTrue(coordinator.mergeRemoteEntries(listOf(entry, entry), limit = 20) { false })
        assertEquals(1, state.remoteEntries.size)
        assertTrue(coordinator.applyChgPct(entry.symbol, 2.3f))

        assertEquals(2.3f, state.atCandidates.single().entry.chgPct)
        assertEquals(2.3f, state.remoteEntries.single().chgPct)
        assertEquals(1, state.atHighlight)
    }

    @Test
    fun remoteSearchDebounceAndLateResponseAreCoordinatorOwned() {
        val state = PlainComposerAssistantState().apply {
            panel = AssistantPanel.AT_MENTION
            triggerSession = TriggerSession('@', 0, "茅台", 3)
        }
        val scheduler = FakeScheduler()
        val effects = mutableListOf<ComposerAssistantEffect>()
        val coordinator = ComposerAssistantCoordinator(state, scheduler, effects::add)

        coordinator.requestRemoteSearch("茅台")
        scheduler.runAll()
        val request = effects.single() as ComposerAssistantEffect.SearchSecurities

        coordinator.closePanel()
        coordinator.acceptRemoteSearchResults(
            request.generation,
            request.query,
            listOf(CatalogEntry("600519.SH", "贵州茅台", "沪A")),
            limit = 20,
            isLocalSymbol = { false },
        )
        scheduler.runAll()

        assertTrue(state.remoteEntries.isEmpty())
        assertEquals(1, effects.size)
    }

    private class FakeScheduler : ComposerAssistantScheduler {
        private val tasks = mutableListOf<() -> Unit>()
        override fun schedule(delayMillis: Int, task: () -> Unit): ComposerAssistantTask {
            tasks += task
            return ComposerAssistantTask { tasks.remove(task) }
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeAt(0).invoke()
        }
    }
}
