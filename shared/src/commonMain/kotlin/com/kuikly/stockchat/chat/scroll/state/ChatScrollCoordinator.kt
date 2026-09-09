package com.kuikly.stockchat.chat.scroll.state

import com.tencent.kuikly.core.timer.Timer

internal interface ChatScrollScheduledTask {
    fun cancel()
}

internal fun interface ChatScrollScheduler {
    fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): ChatScrollScheduledTask
}

internal class KuiklyChatScrollScheduler : ChatScrollScheduler {
    override fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): ChatScrollScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            if (!repeating) timer.cancel()
        }
        return object : ChatScrollScheduledTask {
            override fun cancel() = timer.cancel()
        }
    }
}

/** Plain timer-owned state. UI observables and native Scroller references stay in ChatPage. */
internal class ChatScrollState {
    var pageActive = false
    var destroyed = false
    var streaming = false
    var following = true
    var keepBottomVersion = 0
    var streamFlushed = false
}

/**
 * Owns the chat's follow policy and all related timers. The Page supplies
 * already-observed stream snapshots and performs the resulting native scroll.
 */
internal class ChatScrollCoordinator(
    private val state: ChatScrollState,
    private val scheduler: ChatScrollScheduler,
    private val onScrollToBottom: (animated: Boolean) -> Unit,
    private val onResetFollowUps: () -> Unit,
    private val onScheduleFollowUps: () -> Unit,
    private val log: (String) -> Unit = {},
) {
    private var followLoop: ChatScrollScheduledTask? = null
    private var keepBottomClear: ChatScrollScheduledTask? = null
    private var lastStreaming = false
    private var sawStreaming = false
    private var preStreamTicks = 0
    private var idleTicks = 0

    fun onAppear() {
        state.pageActive = true
        if (state.streaming && state.following) startFollowLoop()
    }

    fun onDisappear() {
        state.pageActive = false
        stopFollowLoop()
        keepBottomClear?.cancel()
        keepBottomClear = null
    }

    fun onDestroy() {
        state.destroyed = true
        state.pageActive = false
        stopFollowLoop()
        keepBottomClear?.cancel()
        keepBottomClear = null
    }

    fun onNewChat() {
        ++state.keepBottomVersion
        state.following = true
        state.streamFlushed = false
        stopFollowLoop()
        keepBottomClear?.cancel()
        keepBottomClear = null
    }

    /** Called only from a vbind/event closure, never from a timer callback. */
    fun onStreamStateObserved(streaming: Boolean) {
        state.streaming = streaming
        if (streaming && state.pageActive && state.following) startFollowLoop()
    }

    fun onUserScroll(isAtBottom: Boolean) {
        state.following = isAtBottom
        // A deliberate drag away from the bottom is stronger than the send
        // window. Otherwise its pending 2.5s timer keeps issuing native scrolls
        // and visibly steals the reader back to the latest message.
        if (!isAtBottom) {
            state.keepBottomVersion = 0
            keepBottomClear?.cancel()
            keepBottomClear = null
        }
    }

    fun onSendRequested() {
        state.following = true
        val version = ++state.keepBottomVersion
        onScrollToBottom(true)
        keepBottomClear?.cancel()
        keepBottomClear = scheduler.schedule(SEND_WINDOW_MS, repeating = false) {
            if (state.keepBottomVersion == version) state.keepBottomVersion = 0
        }
        startFollowLoop()
    }

    fun onContentSizeGrew() {
        if (!isActive()) return
        if (state.streaming) {
            state.streamFlushed = true
            onResetFollowUps()
        } else if (state.streamFlushed) {
            state.streamFlushed = false
            if (state.following) keepAtBottomAfterStreamEnd()
            onScheduleFollowUps()
        }
        if (shouldKeepAtBottom()) onScrollToBottom(false)
    }

    fun shouldKeepAtBottom(): Boolean = state.keepBottomVersion > 0 || (state.streaming && state.following)

    private fun keepAtBottomAfterStreamEnd() {
        val version = ++state.keepBottomVersion
        onScrollToBottom(false)
        keepBottomClear?.cancel()
        keepBottomClear = scheduler.schedule(STREAM_END_WINDOW_MS, repeating = false) {
            if (state.keepBottomVersion == version) state.keepBottomVersion = 0
        }
    }

    private fun startFollowLoop() {
        followLoop?.cancel()
        lastStreaming = state.streaming
        sawStreaming = state.streaming
        preStreamTicks = 0
        idleTicks = 0
        val task = scheduler.schedule(FOLLOW_TICK_MS, repeating = true) {
            if (!isActive()) {
                stopFollowLoop()
                return@schedule
            }
            val streaming = state.streaming
            if (streaming) sawStreaming = true
            if (lastStreaming && !streaming && state.following) keepAtBottomAfterStreamEnd()
            lastStreaming = streaming
            idleTicks = if (streaming || state.keepBottomVersion > 0 || !sawStreaming) 0 else idleTicks + 1
            if (!sawStreaming && preStreamTicks++ > PRE_STREAM_MAX_TICKS) {
                log("followLoop preStream timeout")
                stopFollowLoop()
                return@schedule
            }
            if (idleTicks > FOLLOW_GRACE_TICKS) {
                stopFollowLoop()
                return@schedule
            }
            if (shouldKeepAtBottom()) onScrollToBottom(false)
        }
        followLoop = task
    }

    private fun stopFollowLoop() {
        followLoop?.cancel()
        followLoop = null
    }

    private fun isActive() = state.pageActive && !state.destroyed

    private companion object {
        const val FOLLOW_TICK_MS = 120
        const val STREAM_END_WINDOW_MS = 1_500
        const val SEND_WINDOW_MS = 2_500
        const val FOLLOW_GRACE_TICKS = 20
        const val PRE_STREAM_MAX_TICKS = 160
    }
}
