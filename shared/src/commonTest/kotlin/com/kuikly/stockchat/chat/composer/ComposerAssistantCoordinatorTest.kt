package com.kuikly.stockchat.chat.composer

import com.kuikly.stockchat.chat.composer.state.AssistantPanel
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantCoordinator
import com.kuikly.stockchat.chat.composer.state.PlainComposerAssistantState
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
        }
        val coordinator = ComposerAssistantCoordinator(state)

        coordinator.closePanel()

        assertEquals(AssistantPanel.NONE, state.panel)
        assertEquals(0, state.atHighlight)
        assertEquals(0, state.slashHighlight)
        assertEquals("", state.slashUnknown)
        assertTrue(state.atCandidates.isEmpty())
        assertTrue(state.slashCandidates.isEmpty())
    }

    @Test
    fun renderKeyRebuildUsesSingleFrameToken() {
        val state = PlainComposerAssistantState()
        val coordinator = ComposerAssistantCoordinator(state)

        coordinator.bumpParamRenderKey()
        coordinator.bumpParamRenderKey()

        assertEquals(listOf(0), state.paramPanelRenderKey)
    }
}
