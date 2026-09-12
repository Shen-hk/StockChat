package com.kuikly.stockchat.watchlist.state

/**
 * Watchlist Feature 统一的调度抽象（与 risk / detail-ai 同范式）：
 * Coordinator 内不直接持有 Kuikly Timer，便于单测用 fake scheduler 驱动。
 * Kuikly 实现用 `com.tencent.kuikly.core.timer.Timer`（核心线程触发，满足 iOS 线程铁律 A）。
 */
internal fun interface WatchlistScheduledTask {
    fun cancel()
}

internal fun interface WatchlistScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): WatchlistScheduledTask
}

internal class KuiklyWatchlistScheduler : WatchlistScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): WatchlistScheduledTask {
        val timer = com.tencent.kuikly.core.timer.Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return WatchlistScheduledTask(timer::cancel)
    }
}
