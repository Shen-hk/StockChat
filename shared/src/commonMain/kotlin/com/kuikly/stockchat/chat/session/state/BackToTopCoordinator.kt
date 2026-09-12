package com.kuikly.stockchat.chat.session.state

import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer

internal interface BackToTopStatePort {
    var mounted: Boolean
    var presented: Boolean
    var shadowVisible: Boolean
}

internal class BackToTopState : BackToTopStatePort {
    override var mounted: Boolean by observable(false)
    override var presented: Boolean by observable(false)
    override var shadowVisible: Boolean by observable(false)
}

internal class PlainBackToTopState : BackToTopStatePort {
    override var mounted = false
    override var presented = false
    override var shadowVisible = false
}

internal fun interface BackToTopTask { fun cancel() }
internal fun interface BackToTopScheduler { fun schedule(delayMillis: Int, task: () -> Unit): BackToTopTask }

internal class KuiklyBackToTopScheduler : BackToTopScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): BackToTopTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) { task(); timer.cancel() }
        return BackToTopTask(timer::cancel)
    }
}

/** Owns R4 button presentation and the temporary programmatic-scroll visibility lock. */
internal class BackToTopCoordinator(
    val state: BackToTopStatePort,
    private val scheduler: BackToTopScheduler,
    private val onScrollToTop: () -> Unit,
) {
    private var version = 0
    private var scrollVersion = 0
    private val tasks = mutableListOf<BackToTopTask>()

    fun onScroll(offsetY: Float, viewportHeight: Float) {
        if (scrollVersion != 0) return
        val visible = offsetY > viewportHeight * 0.25f
        if (visible != state.mounted) setVisible(visible)
    }

    fun scrollToTopAnimated() {
        val current = ++scrollVersion
        setVisible(false)
        onScrollToTop()
        schedule(900) { if (scrollVersion == current) scrollVersion = 0 }
    }

    fun onDestroy() { ++version; ++scrollVersion; tasks.forEach(BackToTopTask::cancel); tasks.clear() }

    private fun setVisible(visible: Boolean) {
        val current = ++version
        if (visible) {
            state.shadowVisible = false
            state.mounted = true
            schedule(16) { if (version == current) state.presented = true }
            schedule(240) { if (version == current && state.presented) state.shadowVisible = true }
        } else {
            state.presented = false
            state.shadowVisible = false
            schedule(240) { if (version == current) state.mounted = false }
        }
    }

    private fun schedule(delay: Int, block: () -> Unit) { tasks += scheduler.schedule(delay, block) }
}
