package com.kuikly.stockchat.detail.overlay.state

import com.tencent.kuikly.core.timer.Timer

internal fun interface DetailOverlayScheduledTask { fun cancel() }

internal fun interface DetailOverlayScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): DetailOverlayScheduledTask
}

internal class KuiklyDetailOverlayScheduler : DetailOverlayScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): DetailOverlayScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return DetailOverlayScheduledTask(timer::cancel)
    }
}