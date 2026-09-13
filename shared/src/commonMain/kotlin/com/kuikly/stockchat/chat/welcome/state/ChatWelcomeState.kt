package com.kuikly.stockchat.chat.welcome.state

import com.tencent.kuikly.core.reactive.handler.observable

/**
 * UI-facing state for the chat welcome section. Observable values are read by
 * the component only from Kuikly reactive closures.
 */
internal interface ChatWelcomeStatePort {
    var rotatingKeyword: String
    var cursorVisible: Boolean
    var entranceVisible: Boolean
    var recommendationsPresented: Boolean
    var composerGuidePresented: Boolean
    var composerPresented: Boolean
    var marketTabSelected: Boolean
    var keywordStopped: Boolean
    var welcomeMounted: Boolean
    var sessionEmpty: Boolean
    var fullMode: Boolean
    var pageActive: Boolean
    var destroyed: Boolean
}

internal class ChatWelcomeState : ChatWelcomeStatePort {
    override var rotatingKeyword: String by observable(DEFAULT_KEYWORD)
    override var cursorVisible: Boolean by observable(false)
    override var entranceVisible: Boolean by observable(false)
    override var recommendationsPresented: Boolean by observable(false)
    override var composerGuidePresented: Boolean by observable(false)
    override var composerPresented: Boolean by observable(false)
    override var marketTabSelected: Boolean by observable(false)

    /** Plain lifecycle flags: timer callbacks must never read UI observables. */
    override var keywordStopped = false
    override var welcomeMounted = false
    override var sessionEmpty = true
    override var fullMode = true
    override var pageActive = false
    override var destroyed = false

    companion object {
        const val DEFAULT_KEYWORD = "行情"
    }
}

/** Non-reactive state implementation for deterministic coordinator tests. */
internal class PlainChatWelcomeState : ChatWelcomeStatePort {
    override var rotatingKeyword = ChatWelcomeState.DEFAULT_KEYWORD
    override var cursorVisible = false
    override var entranceVisible = false
    override var recommendationsPresented = false
    override var composerGuidePresented = false
    override var composerPresented = false
    override var marketTabSelected = false
    override var keywordStopped = false
    override var welcomeMounted = false
    override var sessionEmpty = true
    override var fullMode = true
    override var pageActive = false
    override var destroyed = false
}
