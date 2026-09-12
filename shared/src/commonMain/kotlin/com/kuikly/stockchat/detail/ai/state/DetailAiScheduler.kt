package com.kuikly.stockchat.detail.ai.state

import com.tencent.kuikly.core.timer.Timer

internal fun interface DetailAiScheduledTask { fun cancel() }

internal fun interface DetailAiScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): DetailAiScheduledTask
}

internal class KuiklyDetailAiScheduler : DetailAiScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): DetailAiScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return DetailAiScheduledTask(timer::cancel)
    }
}