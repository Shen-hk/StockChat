package com.kuikly.stockchat.chat.composer

import com.kuikly.stockchat.chat.composer.state.PlainVoiceInputState
import com.kuikly.stockchat.chat.composer.state.VoiceInputCoordinator
import com.kuikly.stockchat.chat.composer.state.VoiceInputEffect
import com.kuikly.stockchat.chat.composer.state.VoiceInputHostPort
import com.kuikly.stockchat.chat.composer.state.VoiceInputScheduledTask
import com.kuikly.stockchat.chat.composer.state.VoiceInputScheduler
import com.kuikly.stockchat.voice.VoiceError
import com.kuikly.stockchat.voice.VoiceRecorder
import com.kuikly.stockchat.voice.VoiceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceInputCoordinatorTest {
    @Test
    fun completedTranscriptSendsAndRestoresTheCollapsedComposer() {
        val f = fixture()

        f.coordinator.startSession()
        assertEquals(VoiceState.RECORDING, f.state.state)
        f.recorder.emitAmplitude(0.8f)
        assertTrue(f.state.amplitudes.last() > 0f)

        f.coordinator.finishSession()
        assertEquals(VoiceState.TRANSCRIBING, f.state.state)
        assertEquals(1, f.recorder.stopCalls)
        f.recorder.emitTranscript("帮我看看宁德时代")

        assertEquals(VoiceState.IDLE, f.state.state)
        assertTrue(f.effects.contains(VoiceInputEffect.SendTranscript("帮我看看宁德时代")))
        assertTrue(f.effects.contains(VoiceInputEffect.KeepChatAtBottom))
        assertTrue(f.effects.contains(VoiceInputEffect.SetTextComposerExpanded(false, true)))
    }

    @Test
    fun upwardReleaseCancelsInsteadOfStartingTranscription() {
        val f = fixture()
        f.coordinator.toggleInputMode()
        f.coordinator.onTouchDown(pageY = 200f, composerExpanded = false)
        f.coordinator.onTouchMove(pageY = 130f)

        f.coordinator.onTouchUp()

        assertEquals(1, f.recorder.cancelCalls)
        assertEquals(VoiceState.IDLE, f.state.state)
        assertFalse(f.state.cancelArmed)
        assertFalse(f.effects.any { it is VoiceInputEffect.SendTranscript })
    }

    @Test
    fun shortHoldShowsFeedbackAndDoesNotStopForTranscription() {
        val f = fixture()
        f.coordinator.startSession()

        f.coordinator.onTouchUp()

        assertEquals(1, f.recorder.cancelCalls)
        assertEquals(0, f.recorder.stopCalls)
        assertTrue(f.effects.contains(VoiceInputEffect.Toast("说话时间太短")))
    }

    private fun fixture(): Fixture {
        val state = PlainVoiceInputState()
        val recorder = FakeVoiceRecorder()
        val scheduler = FakeVoiceScheduler()
        val effects = mutableListOf<VoiceInputEffect>()
        val coordinator = VoiceInputCoordinator(
            state = state,
            recorder = recorder,
            scheduler = scheduler,
            host = object : VoiceInputHostPort {
                override fun inputTextIsBlank() = true
                override fun isAnswerStreaming() = false
            },
            onEffect = effects::add,
        )
        return Fixture(state, recorder, scheduler, effects, coordinator)
    }

    private data class Fixture(
        val state: PlainVoiceInputState,
        val recorder: FakeVoiceRecorder,
        val scheduler: FakeVoiceScheduler,
        val effects: MutableList<VoiceInputEffect>,
        val coordinator: VoiceInputCoordinator,
    )
}

private class FakeVoiceRecorder : VoiceRecorder {
    private var amplitude: ((Float) -> Unit)? = null
    private var transcript: ((String) -> Unit)? = null
    private var error: ((VoiceError) -> Unit)? = null
    var stopCalls = 0
    var cancelCalls = 0

    override fun start(onAmplitude: (Float) -> Unit, onTranscript: (String) -> Unit, onError: (VoiceError) -> Unit) {
        amplitude = onAmplitude
        transcript = onTranscript
        error = onError
    }

    override fun stop() { stopCalls++ }
    override fun cancel() { cancelCalls++ }
    fun emitAmplitude(value: Float) = amplitude?.invoke(value)
    fun emitTranscript(value: String) = transcript?.invoke(value)
}

private class FakeVoiceScheduler : VoiceInputScheduler {
    override fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): VoiceInputScheduledTask =
        VoiceInputScheduledTask {}
}
