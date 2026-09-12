package com.kuikly.stockchat.chat.island.state

import com.tencent.kuikly.core.timer.Timer

internal fun interface IslandScheduledTask { fun cancel() }

internal fun interface IslandScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): IslandScheduledTask
}

internal class KuiklyIslandScheduler : IslandScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): IslandScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return IslandScheduledTask(timer::cancel)
    }
}
