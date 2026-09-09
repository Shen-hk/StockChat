package com.kuikly.stockchat.chat.composer.state

import com.tencent.kuikly.core.timer.Timer

internal interface MediaSheetScheduledTask { fun cancel() }

internal fun interface MediaSheetScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): MediaSheetScheduledTask
}

internal class KuiklyMediaSheetScheduler : MediaSheetScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): MediaSheetScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return object : MediaSheetScheduledTask { override fun cancel() = timer.cancel() }
    }
}

/**
 * Preserves the two-frame sheet entrance and delayed unmount while keeping stale
 * callbacks out of the Page. Page-owned focus, bridge and haptic effects remain outside.
 */
internal class MediaSheetCoordinator(
    private val state: ComposerAttachmentStatePort,
    private val scheduler: MediaSheetScheduler,
) {
    private var version = 0
    private var presented = false
    private var pendingTask: MediaSheetScheduledTask? = null

    fun open() {
        val current = ++version
        pendingTask?.cancel()
        setPresented(false)
        state.mediaSheetMounted = true
        pendingTask = scheduler.schedule(ENTRANCE_DELAY_MS) {
            if (version == current) setPresented(true)
        }
    }

    fun dismiss() {
        val current = ++version
        pendingTask?.cancel()
        setPresented(false)
        pendingTask = scheduler.schedule(EXIT_DURATION_MS) {
            if (version == current && !presented) state.mediaSheetMounted = false
        }
    }

    fun reset() {
        ++version
        pendingTask?.cancel()
        pendingTask = null
        state.mediaSheetMounted = false
        setPresented(false)
    }

    private fun setPresented(value: Boolean) {
        presented = value
        state.mediaSheetPresented = value
    }

    private companion object {
        const val ENTRANCE_DELAY_MS = 16
        const val EXIT_DURATION_MS = 260
    }
}
