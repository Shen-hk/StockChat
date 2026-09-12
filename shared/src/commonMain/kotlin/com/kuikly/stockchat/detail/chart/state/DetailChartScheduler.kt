package com.kuikly.stockchat.detail.chart.state

import com.tencent.kuikly.core.timer.Timer

internal fun interface DetailChartScheduledTask { fun cancel() }

internal fun interface DetailChartScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): DetailChartScheduledTask
}

internal class KuiklyDetailChartScheduler : DetailChartScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): DetailChartScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return DetailChartScheduledTask(timer::cancel)
    }
}
