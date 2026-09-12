package com.kuikly.stockchat.chat.session.state

import com.kuikly.stockchat.foundation.design.GlassRenderingMode
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * Small, session-scoped presentation state that is shared by the drawer and
 * the chat surface.  Keeping it here prevents the page from becoming the
 * owner of drawer search and glass-mode transitions.
 */
internal interface SessionChromeStatePort {
    var historyQuery: String
    var glassMode: GlassRenderingMode
    var glassModeManuallySelected: Boolean
}

internal class SessionChromeState : SessionChromeStatePort {
    override var historyQuery: String by observable("")
    override var glassMode: GlassRenderingMode by observable(GlassRenderingMode.SIMPLIFIED)
    override var glassModeManuallySelected = false
}

internal class PlainSessionChromeState : SessionChromeStatePort {
    override var historyQuery = ""
    override var glassMode = GlassRenderingMode.SIMPLIFIED
    override var glassModeManuallySelected = false
}

internal class SessionChromeCoordinator(private val state: SessionChromeStatePort) {
    fun resetHistoryQuery() {
        state.historyQuery = ""
    }

    fun syncHostGlassMode(mode: GlassRenderingMode) {
        if (!state.glassModeManuallySelected) state.glassMode = mode
    }

    fun setGlassMode(mode: GlassRenderingMode, manuallySelected: Boolean) {
        state.glassModeManuallySelected = manuallySelected
        state.glassMode = mode
    }

    fun clearManualGlassMode() {
        state.glassModeManuallySelected = false
    }

    fun cycleGlassMode() {
        state.glassModeManuallySelected = true
        state.glassMode = when (state.glassMode) {
            GlassRenderingMode.REALTIME -> GlassRenderingMode.SNAPSHOT
            GlassRenderingMode.SNAPSHOT -> GlassRenderingMode.SIMPLIFIED
            GlassRenderingMode.SIMPLIFIED -> GlassRenderingMode.REALTIME
        }
    }
}
