package com.kuikly.stockchat.chat.composer.state

import com.kuikly.stockchat.voice.VoiceError
import com.kuikly.stockchat.voice.VoiceRecorder
import com.kuikly.stockchat.voice.VoiceState
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer
import kotlin.math.pow

private const val VOICE_AMP_BARS = 56

internal interface VoiceInputStatePort {
    var state: VoiceState
    var cancelArmed: Boolean
    var elapsedSeconds: Float
    var amplitudes: FloatArray
    var micFill: Float
    var inputMode: Boolean
}

internal class VoiceInputState : VoiceInputStatePort {
    override var state: VoiceState by observable(VoiceState.IDLE)
    override var cancelArmed: Boolean by observable(false)
    override var elapsedSeconds: Float by observable(0f)
    override var amplitudes: FloatArray by observable(FloatArray(VOICE_AMP_BARS) { 0f })
    override var micFill: Float by observable(0f)
    override var inputMode: Boolean by observable(false)
}

internal class PlainVoiceInputState : VoiceInputStatePort {
    override var state = VoiceState.IDLE
    override var cancelArmed = false
    override var elapsedSeconds = 0f
    override var amplitudes = FloatArray(VOICE_AMP_BARS) { 0f }
    override var micFill = 0f
    override var inputMode = false
}

internal fun interface VoiceInputScheduledTask { fun cancel() }

internal fun interface VoiceInputScheduler {
    fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): VoiceInputScheduledTask
}

internal class KuiklyVoiceInputScheduler : VoiceInputScheduler {
    override fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): VoiceInputScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            if (!repeating) timer.cancel()
        }
        return VoiceInputScheduledTask(timer::cancel)
    }
}

internal interface VoiceInputHostPort {
    fun inputTextIsBlank(): Boolean
    fun isAnswerStreaming(): Boolean
}

internal sealed interface VoiceInputEffect {
    data object BlurComposer : VoiceInputEffect
    data object CloseAssistantPanel : VoiceInputEffect
    data object ClearCommandValidation : VoiceInputEffect
    data object CollapseTextComposer : VoiceInputEffect
    data class SetTextComposerExpanded(val expanded: Boolean, val scheduleChrome: Boolean) : VoiceInputEffect
    data class Toast(val message: String) : VoiceInputEffect
    data class SendTranscript(val transcript: String) : VoiceInputEffect
    data object KeepChatAtBottom : VoiceInputEffect
}

/**
 * Owns hold-to-record transitions, recorder callbacks, wave smoothing and
 * post-session layout restoration. Page-owned effects perform native UI and send work.
 */
internal class VoiceInputCoordinator(
    val state: VoiceInputStatePort,
    private val recorder: VoiceRecorder,
    private val scheduler: VoiceInputScheduler,
    private val host: VoiceInputHostPort,
    private val onEffect: (VoiceInputEffect) -> Unit,
) {
    private var touchDownPageY = 0f
    private var sourceWasExpanded = false
    private var sessionVersion = 0
    private var clock: VoiceInputScheduledTask? = null

    fun toggleInputMode() {
        if (state.state != VoiceState.IDLE) return
        state.inputMode = !state.inputMode
        if (state.inputMode) onEffect(VoiceInputEffect.BlurComposer)
    }

    fun leaveInputModeForText() {
        state.inputMode = false
    }

    fun enterInputModeFromExpanded() {
        if (state.state != VoiceState.IDLE) return
        state.inputMode = true
        onEffect(VoiceInputEffect.BlurComposer)
        onEffect(VoiceInputEffect.CollapseTextComposer)
    }

    fun onTouchDown(pageY: Float, composerExpanded: Boolean) {
        if (state.state != VoiceState.IDLE) return
        sourceWasExpanded = composerExpanded
        touchDownPageY = pageY
        startSession()
    }

    fun onTouchMove(pageY: Float) {
        if (state.state == VoiceState.RECORDING) state.cancelArmed = (touchDownPageY - pageY) >= CANCEL_DISTANCE
    }

    fun onTouchUp() {
        if (state.state != VoiceState.RECORDING) return
        when {
            state.cancelArmed -> cancelSession()
            state.elapsedSeconds < MIN_SEND_DURATION_SECONDS -> abortTooShortSession()
            else -> finishSession()
        }
    }

    fun startSession() {
        val version = ++sessionVersion
        onEffect(VoiceInputEffect.BlurComposer)
        if (!state.inputMode) {
            onEffect(VoiceInputEffect.SetTextComposerExpanded(true, scheduleChrome = !sourceWasExpanded))
        }
        onEffect(VoiceInputEffect.CloseAssistantPanel)
        onEffect(VoiceInputEffect.ClearCommandValidation)
        state.cancelArmed = false
        state.elapsedSeconds = 0f
        state.amplitudes = FloatArray(VOICE_AMP_BARS) { 0f }
        state.micFill = 0.02f
        state.state = VoiceState.RECORDING
        startClock(version)
        recorder.start(
            onAmplitude = { rms -> if (sessionVersion == version && state.state == VoiceState.RECORDING) updateAmplitude(rms) },
            onTranscript = { transcript -> if (sessionVersion == version && state.state == VoiceState.TRANSCRIBING) onTranscriptReady(transcript) },
            onError = { error -> if (sessionVersion == version) onRecorderError(error) },
        )
    }

    fun finishSession() {
        if (state.state != VoiceState.RECORDING) return
        clock?.cancel()
        clock = null
        state.cancelArmed = false
        state.state = VoiceState.TRANSCRIBING
        state.amplitudes = FloatArray(VOICE_AMP_BARS) { 0f }
        state.micFill = 0f
        recorder.stop()
    }

    fun cancelSession() {
        if (state.state == VoiceState.IDLE) return
        recorder.cancel()
        restoreAfterSession()
    }

    fun onDestroy() {
        recorder.cancel()
        stopUi()
    }

    private fun startClock(version: Int) {
        clock?.cancel()
        clock = scheduler.schedule(CLOCK_TICK_MS, repeating = true) {
            if (sessionVersion != version || state.state != VoiceState.RECORDING) return@schedule
            val next = (state.elapsedSeconds + 0.1f).coerceAtMost(MAX_DURATION_SECONDS)
            state.elapsedSeconds = next
            if (next >= MAX_DURATION_SECONDS) finishSession()
        }
    }

    private fun updateAmplitude(rms: Float) {
        val shaped = (rms.coerceIn(0f, 1f) * 2.5f).coerceIn(0f, 1f).toDouble().pow(0.6).toFloat()
        val previousFill = state.micFill
        state.micFill = previousFill + (shaped - previousFill) * if (shaped > previousFill) 0.6f else 0.25f
        val previous = state.amplitudes
        val next = FloatArray(previous.size)
        for (index in 0 until next.size - 1) next[index] = previous[index + 1]
        val target = 28f * shaped
        val last = previous.getOrNull(next.size - 1) ?: 0f
        next[next.size - 1] = (last + (target - last) * 0.6f).coerceIn(0f, 26f)
        state.amplitudes = next
    }

    private fun onTranscriptReady(transcript: String) {
        if (transcript.isBlank()) {
            onEffect(VoiceInputEffect.Toast("没听清，请再说一次"))
            restoreAfterSession()
        } else if (host.isAnswerStreaming()) {
            onEffect(VoiceInputEffect.Toast("当前回答未结束，请稍后再试"))
            restoreAfterSession()
        } else {
            onEffect(VoiceInputEffect.SendTranscript(transcript))
            restoreAfterSession()
            onEffect(VoiceInputEffect.KeepChatAtBottom)
        }
    }

    private fun abortTooShortSession() {
        recorder.cancel()
        stopUi()
        onEffect(VoiceInputEffect.Toast("说话时间太短"))
        restoreAfterSession()
    }

    private fun onRecorderError(error: VoiceError) {
        stopUi()
        restoreAfterSession()
        onEffect(VoiceInputEffect.Toast(error.message()))
    }

    private fun restoreAfterSession() {
        stopUi()
        if (state.inputMode) {
            onEffect(VoiceInputEffect.SetTextComposerExpanded(false, scheduleChrome = false))
        } else if (!sourceWasExpanded && host.inputTextIsBlank()) {
            onEffect(VoiceInputEffect.SetTextComposerExpanded(false, scheduleChrome = true))
        } else {
            onEffect(VoiceInputEffect.SetTextComposerExpanded(true, scheduleChrome = false))
        }
        sourceWasExpanded = false
    }

    private fun stopUi() {
        ++sessionVersion
        clock?.cancel()
        clock = null
        state.state = VoiceState.IDLE
        state.cancelArmed = false
        state.elapsedSeconds = 0f
        state.amplitudes = FloatArray(VOICE_AMP_BARS) { 0f }
        state.micFill = 0f
    }

    private fun VoiceError.message(): String = when (this) {
        VoiceError.PERMISSION_DENIED -> "需要麦克风权限才能语音提问"
        VoiceError.MIC_OCCUPIED -> "麦克风被占用，请稍后再试"
        VoiceError.NO_MATCH -> "没听清，请再说一次"
        VoiceError.UNAVAILABLE -> "当前平台暂不支持语音提问"
    }

    private companion object {
        const val CLOCK_TICK_MS = 100
        const val MAX_DURATION_SECONDS = 60f
        const val MIN_SEND_DURATION_SECONDS = 0.8f
        const val CANCEL_DISTANCE = 60f
    }
}
