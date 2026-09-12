package com.kuikly.stockchat.chat.composer.state

import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer

/** Reactive state consumed by the composer DSL. Native TextArea work stays behind effects. */
internal interface ComposerFocusStatePort {
    var expanded: Boolean
    var keyboardVisible: Boolean
    var keyboardHeight: Float
}

internal class ComposerFocusState : ComposerFocusStatePort {
    override var expanded: Boolean by observable(false)
    override var keyboardVisible: Boolean by observable(false)
    override var keyboardHeight: Float by observable(0f)
}

internal class PlainComposerFocusState : ComposerFocusStatePort {
    override var expanded = false
    override var keyboardVisible = false
    override var keyboardHeight = 0f
}

internal fun interface ComposerFocusScheduledTask { fun cancel() }

internal fun interface ComposerFocusScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): ComposerFocusScheduledTask
}

internal class KuiklyComposerFocusScheduler : ComposerFocusScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): ComposerFocusScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return ComposerFocusScheduledTask(timer::cancel)
    }
}

internal sealed interface ComposerFocusEffect {
    data object FocusInput : ComposerFocusEffect
    data object BlurInput : ComposerFocusEffect
    data object LeaveVoiceInputMode : ComposerFocusEffect
    data object CancelVoiceSession : ComposerFocusEffect
    data object ClearActiveCommand : ComposerFocusEffect
    data object CloseAssistantPanel : ComposerFocusEffect
    data class ScheduleChromePresentation(val expanded: Boolean) : ComposerFocusEffect
}

/**
 * Owns the text-composer transition/focus-recovery protocol. This isolates the
 * platform-sensitive focus timing from ChatPage while retaining ViewRef actions
 * as effects and preserving the existing R4 chrome presentation contract.
 */
internal class ComposerFocusCoordinator(
    val state: ComposerFocusStatePort,
    private val scheduler: ComposerFocusScheduler,
    private val onEffect: (ComposerFocusEffect) -> Unit,
    private val log: (String) -> Unit = {},
) {
    private var focusRequestVersion = 0
    private var keyboardLayoutVersion = 0
    private var focusRecoveryPending = false
    private var focusLocked = false
    private var unexpectedBlurVersion = 0
    private var blurRecoverAttempts = 0
    private val tasks = mutableListOf<ComposerFocusScheduledTask>()

    fun expand(requestFocus: Boolean, voiceBusy: Boolean) {
        val wasExpanded = state.expanded
        log("expand requestFocus=$requestFocus wasExpanded=$wasExpanded")
        if (!state.expanded) {
            state.expanded = true
            onEffect(ComposerFocusEffect.LeaveVoiceInputMode)
            onEffect(ComposerFocusEffect.ScheduleChromePresentation(expanded = true))
        }
        if (voiceBusy) onEffect(ComposerFocusEffect.CancelVoiceSession)
        if (requestFocus) {
            focusLocked = true
            if (wasExpanded) onEffect(ComposerFocusEffect.FocusInput) else scheduleFocusAfterExpansion()
        }
    }

    fun onInputFocus(voiceBusy: Boolean) {
        if (!state.expanded) expand(requestFocus = false, voiceBusy = voiceBusy)
    }

    fun onKeyboardHeightChanged(height: Float, durationSeconds: Float) {
        state.keyboardHeight = height
        state.keyboardVisible = height > 0f
        if (height > 0f) {
            blurRecoverAttempts = 0
            scheduleFocusAfterKeyboardLayout(durationSeconds)
        }
    }

    fun blur() {
        focusLocked = false
        ++unexpectedBlurVersion
        ++focusRequestVersion
        ++keyboardLayoutVersion
        focusRecoveryPending = false
        onEffect(ComposerFocusEffect.BlurInput)
    }

    fun collapse() {
        ++focusRequestVersion
        ++keyboardLayoutVersion
        focusRecoveryPending = false
        focusLocked = false
        ++unexpectedBlurVersion
        blurRecoverAttempts = 0
        state.expanded = false
        onEffect(ComposerFocusEffect.ScheduleChromePresentation(expanded = false))
        onEffect(ComposerFocusEffect.ClearActiveCommand)
        onEffect(ComposerFocusEffect.CloseAssistantPanel)
        onEffect(ComposerFocusEffect.CancelVoiceSession)
    }

    /** Voice remains a separate state machine; this is its narrow layout bridge. */
    fun setExpandedFromVoice(expanded: Boolean, scheduleChrome: Boolean = false) {
        state.expanded = expanded
        if (scheduleChrome) onEffect(ComposerFocusEffect.ScheduleChromePresentation(expanded))
    }

    fun isExpanded(voiceBusy: Boolean): Boolean =
        state.expanded || state.keyboardVisible || state.keyboardHeight > 0f || voiceBusy

    fun isVisuallyExpanded(): Boolean =
        state.expanded || state.keyboardVisible || state.keyboardHeight > 0f

    fun onDestroy() {
        ++focusRequestVersion
        ++keyboardLayoutVersion
        tasks.forEach(ComposerFocusScheduledTask::cancel)
        tasks.clear()
    }

    private fun scheduleFocusAfterExpansion() {
        val requestVersion = ++focusRequestVersion
        focusRecoveryPending = true
        log("scheduleFocusRecovery version=$requestVersion")
        schedule(FOCUS_FALLBACK_MS) { recoverFocus(requestVersion, "fallback") }
    }

    private fun scheduleFocusAfterKeyboardLayout(durationSeconds: Float) {
        if (!focusRecoveryPending || !state.expanded) return
        val requestVersion = focusRequestVersion
        val layoutVersion = ++keyboardLayoutVersion
        val delayMs = (durationSeconds * 1000f).toInt().coerceIn(0, 400) + KEYBOARD_LAYOUT_BUFFER_MS
        schedule(delayMs) {
            if (layoutVersion == keyboardLayoutVersion) recoverFocus(requestVersion, "keyboardLayout")
        }
    }

    private fun recoverFocus(requestVersion: Int, source: String) {
        if (requestVersion != focusRequestVersion || !focusRecoveryPending || !state.expanded) return
        focusRecoveryPending = false
        log("runFocusRecovery source=$source version=$requestVersion")
        onEffect(ComposerFocusEffect.FocusInput)
    }

    private fun schedule(delayMillis: Int, task: () -> Unit) {
        tasks += scheduler.schedule(delayMillis, task)
    }

    private companion object {
        const val FOCUS_FALLBACK_MS = 500
        const val KEYBOARD_LAYOUT_BUFFER_MS = 48
    }
}
