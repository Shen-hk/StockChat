package com.kuikly.stockchat.risk.state

/**
 * Risk Feature 统一的调度抽象（与 detail/ai 的 DetailAiScheduler 同范式）：
 * Coordinator 内不直接持有 Kuikly Timer，便于单测用 fake scheduler 驱动。
 * Kuikly 实现用 `com.tencent.kuikly.core.timer.Timer`（与 Detail 同款，
 * 在核心线程上触发，满足 iOS 线程铁律 A）。
 */
internal fun interface RiskScheduledTask {
    fun cancel()
}

internal fun interface RiskScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): RiskScheduledTask
}

internal class KuiklyRiskScheduler : RiskScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): RiskScheduledTask {
        val timer = com.tencent.kuikly.core.timer.Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return RiskScheduledTask(timer::cancel)
    }
}
