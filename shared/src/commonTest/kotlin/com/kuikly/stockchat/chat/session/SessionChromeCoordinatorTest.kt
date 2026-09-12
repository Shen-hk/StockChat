package com.kuikly.stockchat.chat.session

import com.kuikly.stockchat.chat.session.state.PlainSessionChromeState
import com.kuikly.stockchat.chat.session.state.SessionChromeCoordinator
import com.kuikly.stockchat.foundation.design.GlassRenderingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionChromeCoordinatorTest {
    @Test
    fun hostModeDoesNotOverrideManualChoice() {
        val state = PlainSessionChromeState()
        val coordinator = SessionChromeCoordinator(state)

        coordinator.setGlassMode(GlassRenderingMode.REALTIME, manuallySelected = true)
        coordinator.syncHostGlassMode(GlassRenderingMode.SIMPLIFIED)

        assertEquals(GlassRenderingMode.REALTIME, state.glassMode)
        coordinator.clearManualGlassMode()
        coordinator.syncHostGlassMode(GlassRenderingMode.SIMPLIFIED)
        assertEquals(GlassRenderingMode.SIMPLIFIED, state.glassMode)
        assertFalse(state.glassModeManuallySelected)
    }

    @Test
    fun cycleAndHistoryResetAreOwnedBySessionChrome() {
        val state = PlainSessionChromeState().apply { historyQuery = "茅台" }
        val coordinator = SessionChromeCoordinator(state)

        coordinator.cycleGlassMode()
        coordinator.resetHistoryQuery()

        assertEquals(GlassRenderingMode.REALTIME, state.glassMode)
        assertTrue(state.glassModeManuallySelected)
        assertEquals("", state.historyQuery)
    }
}
