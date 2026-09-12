package com.kuikly.stockchat.chat.session.state

import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer

internal interface FollowUpStatePort {
    var mounted: Boolean
    var presented: Boolean
}

internal class FollowUpState : FollowUpStatePort {
    override var mounted: Boolean by observable(false)
    override var presented: Boolean by observable(false)
}

internal class PlainFollowUpState : FollowUpStatePort {
    override var mounted = false
    override var presented = false
}

internal fun interface FollowUpScheduledTask { fun cancel() }

internal fun interface FollowUpScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): FollowUpScheduledTask
}

internal class KuiklyFollowUpScheduler : FollowUpScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): FollowUpScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return FollowUpScheduledTask(timer::cancel)
    }
}

/** R4 two-frame answer-follow-up presentation with version-guarded delayed work. */
internal class FollowUpCoordinator(
    val state: FollowUpStatePort,
    private val scheduler: FollowUpScheduler,
) {
    private var version = 0
    private var destroyed = false
    private val tasks = mutableListOf<FollowUpScheduledTask>()

    fun reset() {
        ++version
        state.presented = false
        state.mounted = false
    }

    fun schedulePresentation() {
        val currentVersion = ++version
        state.presented = false
        state.mounted = false
        schedule(MOUNT_DELAY_MS) {
            if (destroyed || currentVersion != version) return@schedule
            state.mounted = true
            schedule(PRESENT_DELAY_MS) {
                if (!destroyed && currentVersion == version) state.presented = true
            }
        }
    }

    fun onDestroy() {
        destroyed = true
        ++version
        tasks.forEach(FollowUpScheduledTask::cancel)
        tasks.clear()
    }

    private fun schedule(delayMillis: Int, task: () -> Unit) {
        tasks += scheduler.schedule(delayMillis, task)
    }

    private companion object {
        const val MOUNT_DELAY_MS = 320
        const val PRESENT_DELAY_MS = 16
    }
}
