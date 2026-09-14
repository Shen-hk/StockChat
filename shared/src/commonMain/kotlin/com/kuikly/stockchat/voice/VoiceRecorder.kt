package com.kuikly.stockchat.voice

import com.kuikly.stockchat.base.BridgeModule
import com.tencent.kuikly.core.timer.Timer
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 语音输入状态机（规范 docs/11-语音输入交互与动效规范_v1.0.md §4.3）。
 * IDLE → RECORDING（按住）→ TRANSCRIBING（松手）→ IDLE（发送/取消）。
 */
enum class VoiceState { IDLE, RECORDING, TRANSCRIBING }

enum class VoiceError { PERMISSION_DENIED, MIC_OCCUPIED, NO_MATCH, UNAVAILABLE }

/**
 * 录音桥。Native 端负责真实语音识别；common 层只消费音量与最终文本。
 */
interface VoiceRecorder {
    /** 开始识别。onAmplitude 按约 60ms 节奏回调音量，onTranscript 在最终 ASR 结果到达时回调。 */
    fun start(onAmplitude: (Float) -> Unit, onTranscript: (String) -> Unit, onError: (VoiceError) -> Unit)
    /** 正常结束（松手），触发 native ASR 停止听写并等待最终 transcript 回调。 */
    fun stop()
    /** 取消（上滑/会话重置）。不产生任何文件与回调。 */
    fun cancel()
}

/**
 * Native bridge backed recorder. Android implements real SpeechRecognizer recognition in HRBridgeModule;
 * platforms without the bridge report UNAVAILABLE instead of falling through silently.
 */
internal class NativeBridgeVoiceRecorder(private val bridge: BridgeModule) : VoiceRecorder {
    private var pendingTranscript: String? = null
    private var transcriptCallback: ((String) -> Unit)? = null
    private var waitingForStop = false

    override fun start(onAmplitude: (Float) -> Unit, onTranscript: (String) -> Unit, onError: (VoiceError) -> Unit) {
        pendingTranscript = null
        transcriptCallback = onTranscript
        waitingForStop = false
        bridge.startVoiceRecording { data ->
            val type = data?.optString("type").orEmpty()
            when (type) {
                "ready" -> Unit
                "amplitude" -> onAmplitude(data?.optString("rms")?.toFloatOrNull() ?: 0f)
                "partial" -> pendingTranscript = data?.optString("text").orEmpty()
                "transcript" -> handleTranscript(data?.optString("text").orEmpty())
                "error" -> onError(data?.optString("error").orEmpty().toVoiceError())
                else -> if (data?.optString("code") == "-1") onError(VoiceError.UNAVAILABLE)
            }
        }
    }

    override fun stop() {
        waitingForStop = true
        bridge.stopVoiceRecording()
    }

    override fun cancel() {
        pendingTranscript = null
        transcriptCallback = null
        waitingForStop = false
        bridge.cancelVoiceRecording()
    }

    private fun handleTranscript(transcript: String) {
        if (waitingForStop) {
            transcriptCallback?.invoke(transcript)
            pendingTranscript = null
            transcriptCallback = null
        } else {
            pendingTranscript = transcript
        }
    }

    private fun String.toVoiceError(): VoiceError = when (this) {
        "PERMISSION_DENIED" -> VoiceError.PERMISSION_DENIED
        "MIC_OCCUPIED" -> VoiceError.MIC_OCCUPIED
        "NO_MATCH" -> VoiceError.NO_MATCH
        else -> VoiceError.UNAVAILABLE
    }
}

/**
 * 模拟录音桥：不产生音频，按 60ms 节奏（WorkBuddy 源码 SAMPLE_INTERVAL_MS=60）
 * 输出模拟说话时域 RMS——词组断续 × 音节抖动 × 随机抖动。
 */
class SimulatedVoiceRecorder : VoiceRecorder {
    private var timer: Timer? = null
    private var t = 0.0
    private var onTranscript: ((String) -> Unit)? = null

    override fun start(onAmplitude: (Float) -> Unit, onTranscript: (String) -> Unit, onError: (VoiceError) -> Unit) {
        t = 0.0
        this.onTranscript = onTranscript
        val timer = Timer()
        this.timer = timer
        timer.schedule(0, 60) {
            t += 0.06
            val phrase = maxOf(0.0, sin(t * 1.4)).pow(1.5)
            val syllable = 0.5 + 0.5 * sin(t * 9.0)
            val rms = (phrase * (0.3 + 0.5 * syllable) + Random.nextFloat() * 0.1) * 0.4
            onAmplitude(rms.toFloat().coerceIn(0f, 1f))
        }
    }

    override fun stop() {
        timer?.cancel()
        timer = null
        onTranscript?.invoke(MockVoiceTranscriber.next())
        onTranscript = null
    }

    override fun cancel() {
        timer?.cancel()
        timer = null
        onTranscript = null
    }
}

/** 模拟转写：按顺序从 mock 池取句，验证"松手 → 转写 → 直接发送"全链路。 */
object MockVoiceTranscriber {
    private val pool = listOf(
        "帮我看看贵州茅台最近的走势",
        "最近三天主力资金是流入还是流出",
        "什么是缩量十字星",
        "对比一下茅台和五粮液这半年的表现",
    )
    private var index = 0
    fun next(): String = pool[index++ % pool.size]
}
