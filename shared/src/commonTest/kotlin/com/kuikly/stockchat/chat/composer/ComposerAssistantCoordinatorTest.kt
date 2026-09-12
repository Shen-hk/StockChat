package com.kuikly.stockchat.chat.composer

import com.kuikly.stockchat.chat.composer.state.AssistantPanel
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantCoordinator
import com.kuikly.stockchat.chat.composer.state.PlainComposerAssistantState
import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.CatalogEntry
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
}
