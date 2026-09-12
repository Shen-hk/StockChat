package com.kuikly.stockchat.chat.entity.state

import com.tencent.kuikly.core.timer.Timer

internal fun interface EntityScheduledTask { fun cancel() }

internal fun interface EntityScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): EntityScheduledTask
}

internal class KuiklyEntityScheduler : EntityScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): EntityScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return EntityScheduledTask(timer::cancel)
    }
}
