package com.kuikly.stockchat.chat.composer.state

import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer
import kotlin.math.PI

/** Reactive visual drivers for the composer; the page only renders these values. */
internal interface ComposerVisualStatePort {
    var chromePresented: Boolean
    var rimPhase: Float
}

internal class ComposerVisualState : ComposerVisualStatePort {
    override var chromePresented: Boolean by observable(false)
    override var rimPhase: Float by observable(0f)
}

internal class PlainComposerVisualState : ComposerVisualStatePort {
    override var chromePresented = false
    override var rimPhase = 0f
}

internal fun interface ComposerVisualTask { fun cancel() }
internal interface ComposerVisualScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): ComposerVisualTask
    fun repeat(intervalMillis: Int, task: () -> Unit): ComposerVisualTask
}

internal class KuiklyComposerVisualScheduler : ComposerVisualScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): ComposerVisualTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) { task(); timer.cancel() }
        return ComposerVisualTask(timer::cancel)
    }

    override fun repeat(intervalMillis: Int, task: () -> Unit): ComposerVisualTask {
        val timer = Timer()
        timer.schedule(intervalMillis, intervalMillis) { task() }
        return ComposerVisualTask(timer::cancel)
    }
}

/** Owns R4 chrome presentation, safety fallback, and the page-visible rim frame loop. */
internal class ComposerVisualCoordinator(
    val state: ComposerVisualStatePort,
    private val scheduler: ComposerVisualScheduler,
    private val log: (String) -> Unit = {},
) {
    private var presentationVersion = 0
    private var rimTask: ComposerVisualTask? = null
    private val tasks = mutableListOf<ComposerVisualTask>()

    fun presentChrome(target: Boolean, reducedMotion: Boolean) {
        val version = ++presentationVersion
        if (reducedMotion) {
            state.chromePresented = target
            return
        }
        schedule(32) { if (version == presentationVersion) state.chromePresented = target }
        schedule(600) {
            if (version == presentationVersion && state.chromePresented != target) {
                log("chromePresentationSafety target=$target")
                state.chromePresented = target
            }
        }
    }

    fun startRimFlow() {
        if (rimTask != null) return
        rimTask = scheduler.repeat(50) {
            state.rimPhase = (state.rimPhase + 0.063f) % (PI * 2f).toFloat()
        }
    }

    fun stopRimFlow() {
        rimTask?.cancel()
        rimTask = null
    }

    fun onDestroy() {
        ++presentationVersion
        stopRimFlow()
        tasks.forEach(ComposerVisualTask::cancel)
        tasks.clear()
    }

    private fun schedule(delay: Int, task: () -> Unit) {
        tasks += scheduler.schedule(delay, task)
    }
}
