package com.kuikly.stockchat.detail.quote.state

import com.tencent.kuikly.core.timer.Timer

internal fun interface DetailDataScheduledTask { fun cancel() }

internal fun interface DetailDataScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): DetailDataScheduledTask
}

internal class KuiklyDetailDataScheduler : DetailDataScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): DetailDataScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return DetailDataScheduledTask(timer::cancel)
    }
}
