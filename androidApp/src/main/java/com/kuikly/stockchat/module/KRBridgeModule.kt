package com.kuikly.stockchat.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.kuikly.stockchat.KRApplication
import com.kuikly.stockchat.KuiklyRenderActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KRBridgeModule : KuiklyRenderBaseModule() {

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "ssoRequest" -> {
                ssoRequest(params, callback)
            }

            "showAlert" -> {
                showAlert(params, callback)
            }

            "closePage" -> {
                closePage(params)
            }

            "openPage" -> {
                openPage(params)
            }

            "copyToPasteboard" -> {
                copyToPasteboard(params)
            }

            "toast" -> {
                toast(params)
            }

            "hapticImpact" -> {
                hapticImpact()
            }

            "openComposerMediaSource" -> {
                openComposerMediaSource(params, callback)
            }

            "startVoiceRecording" -> {
                startVoiceRecording(callback)
            }

            "stopVoiceRecording" -> {
                stopVoiceRecording()
            }

            "cancelVoiceRecording" -> {
                cancelVoiceRecording()
            }

            "getGlassMode" -> (activity as? KuiklyRenderActivity)?.currentGlassMode() ?: "simplified"

            "log" -> {
                log(params)
            }

            "reportDT" -> {
                reportDT(params)
            }

            "reportRealtime" -> {
                reportRealtime(params)
            }

            "qqLiveSSORequest" -> {
                qqLiveSSORequest(params, callback)
            }

            "localServeTime" -> {
                localServeTime(params, callback)
            }

            "currentTimestamp" -> {
                currentTimestamp(params)
            }

            "dateFormatter" -> {
                dateFormatter(params)
            }

            else -> callback?.invoke(
                mapOf(
                    "code" to -1,
                    "message" to "方法不存在"
                )
            )
        }
    }

    private fun reportRealtime(params: String?) {
    }

    private fun reportDT(params: String?) {
    }

    private fun log(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        Log.i("KuiklyRender", paramJSON.optString("content"))
    }

    private fun toast(params: String?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        Toast.makeText(
            KRApplication.application,
            paramJSON.optString("content"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun hapticImpact() {
        activity?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun startVoiceRecording(callback: KuiklyRenderCallback?) {
        val currentActivity = activity
        if (currentActivity == null || callback == null) {
            callback?.invoke(errorPayload("UNAVAILABLE", "页面不可用"))
            return
        }
        if (ContextCompat.checkSelfPermission(currentActivity, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(currentActivity, arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            callback.invoke(errorPayload("PERMISSION_DENIED", "需要麦克风权限"))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(currentActivity)) {
            callback.invoke(errorPayload("UNAVAILABLE", "当前设备没有可用语音识别服务"))
            return
        }
        val session = AndroidSpeechRecognitionSession(currentActivity, callback) { finished ->
            synchronized(VOICE_LOCK) {
                if (activeVoiceSession === finished) activeVoiceSession = null
            }
        }
        synchronized(VOICE_LOCK) {
            activeVoiceSession?.cancel()
            activeVoiceSession = session
        }
        val started = session.start()
        if (!started) {
            synchronized(VOICE_LOCK) {
                if (activeVoiceSession === session) activeVoiceSession = null
            }
            callback.invoke(errorPayload("MIC_OCCUPIED", "麦克风被占用"))
        }
    }

    private fun stopVoiceRecording(): String {
        val session = synchronized(VOICE_LOCK) {
            activeVoiceSession
        } ?: return JSONObject().put("path", "").toString()
        session.stop()
        return JSONObject().put("path", "").toString()
    }

    private fun cancelVoiceRecording() {
        cancelActiveVoiceRecording()
    }

    private fun errorPayload(error: String, message: String): Map<String, Any> =
        mapOf("type" to "error", "error" to error, "message" to message)

    private fun openComposerMediaSource(params: String?, callback: KuiklyRenderCallback?) {
        val source = JSONObject(params ?: "{}").optString("source")
        val intent = when (source) {
            "camera" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            else -> Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }
        }
        val currentActivity = activity
        if (currentActivity == null) {
            callback?.invoke(mapOf("code" to -1, "message" to "页面不可用"))
            return
        }
        if (intent.resolveActivity(currentActivity.packageManager) == null) {
            val label = if (source == "camera") "相机" else "相册"
            Toast.makeText(KRApplication.application, "未找到可用$label", Toast.LENGTH_SHORT).show()
            callback?.invoke(mapOf("code" to -1, "message" to "未找到可用$label"))
            return
        }
        try {
            currentActivity.startActivity(intent)
            callback?.invoke(mapOf("code" to 0, "source" to source))
        } catch (error: Exception) {
            Toast.makeText(KRApplication.application, "打开失败，请稍后重试", Toast.LENGTH_SHORT).show()
            callback?.invoke(mapOf("code" to -1, "message" to error.message.orEmpty()))
        }
    }

    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        (context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.setPrimaryClip(ClipData.newPlainText(MODULE_NAME, paramJSON.optString("content")))
        }
    }

    private fun openPage(params: String?) {
        if (params == null) {
            return
        }
        val ctx = context ?: return
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
    }

    private fun closePage(params: String?) {
        activity?.finish()
    }

    private fun showAlert(params: String?, callback: KuiklyRenderCallback?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        val titleText = paramJSON.optString("title")
        val message = paramJSON.optString("message")
        val buttons = paramJSON.optJSONArray("buttons") ?: JSONArray()
    }

    private fun ssoRequest(params: String?, callback: KuiklyRenderCallback?) {}

    private fun qqLiveSSORequest(params: String?, callback: KuiklyRenderCallback?) {
    }

    private fun localServeTime(params: String?, callback: KuiklyRenderCallback?) {
        val time = (System.currentTimeMillis() / 1000.0)
        callback?.invoke(
            mapOf(
                "time" to time
            )
        )
    }

    private fun currentTimestamp(params: String?): String {
        return (System.currentTimeMillis()).toString()
    }

    private fun dateFormatter(params: String?): String {
        val paramJSONObject = JSONObject(params ?: "{}")
        val data = Date(paramJSONObject.optLong("timeStamp"))
        val format = SimpleDateFormat(paramJSONObject.optString("format"))
        return format.format(data)
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        private const val REQUEST_RECORD_AUDIO = 8301
        private val VOICE_LOCK = Any()
        private var activeVoiceSession: AndroidSpeechRecognitionSession? = null

        fun cancelActiveVoiceRecording() {
            val session = synchronized(VOICE_LOCK) {
                val active = activeVoiceSession
                activeVoiceSession = null
                active
            }
            session?.cancel()
        }
    }
}

private class AndroidSpeechRecognitionSession(
    private val context: Context,
    private val callback: KuiklyRenderCallback,
    private val onFinish: (AndroidSpeechRecognitionSession) -> Unit,
) : RecognitionListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var started = false
    private var stopping = false
    private var deliveredResult = false
    private var latestPartial = ""
    private var finished = false

    fun start(): Boolean {
        mainHandler.post {
            try {
                val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = speechRecognizer
                speechRecognizer.setRecognitionListener(this)
                speechRecognizer.cancel()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                started = true
                speechRecognizer.startListening(intent)
                callback.invoke(mapOf("type" to "ready"))
            } catch (error: Throwable) {
                Log.w("StockChatVoice", "start speech recognition failed", error)
                callback.invoke(errorPayload("UNAVAILABLE", error.message.orEmpty()))
                destroyRecognizer()
            }
        }
        return true
    }

    fun stop() {
        mainHandler.post {
            if (!started || stopping) return@post
            stopping = true
            if (latestPartial.isNotBlank()) {
                deliverTranscript(latestPartial)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                recognizer?.cancel()
            } catch (_: Throwable) {
            }
            destroyRecognizer()
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        callback.invoke(mapOf("type" to "amplitude", "rms" to 0.02f))
    }

    override fun onBeginningOfSpeech() {
    }

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        callback.invoke(mapOf("type" to "amplitude", "rms" to normalized))
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
    }

    override fun onError(error: Int) {
        Log.w("StockChatVoice", "speech recognition error: $error ${speechErrorMessage(error)} stopping=$stopping")
        if (stopping && latestPartial.isNotBlank()) {
            deliverTranscript(latestPartial)
            return
        }
        val mapped = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "MIC_OCCUPIED"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSION_DENIED"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "MIC_OCCUPIED"
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                if (stopping) {
                    deliverTranscript("")
                    return
                } else {
                    "NO_MATCH"
                }
            }
            SpeechRecognizer.ERROR_CLIENT -> {
                if (stopping) {
                    deliverTranscript("")
                    return
                } else {
                    "UNAVAILABLE"
                }
            }
            else -> "UNAVAILABLE"
        }
        callback.invoke(errorPayload(mapped, speechErrorMessage(error)))
        destroyRecognizer()
    }

    override fun onResults(results: Bundle?) {
        deliverTranscript(bestTranscript(results))
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = bestTranscript(partialResults)
        if (text.isNotBlank()) {
            latestPartial = text
            callback.invoke(mapOf("type" to "partial", "text" to text))
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }

    private fun deliverTranscript(text: String) {
        if (deliveredResult) return
        deliveredResult = true
        callback.invoke(mapOf("type" to "transcript", "text" to text.trim()))
        destroyRecognizer()
    }

    private fun bestTranscript(results: Bundle?): String {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        return matches.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun destroyRecognizer() {
        if (!finished) {
            finished = true
            onFinish(this)
        }
        started = false
        stopping = false
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    private fun errorPayload(error: String, message: String): Map<String, Any> =
        mapOf("type" to "error", "error" to error, "message" to message)

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "麦克风被占用或录音失败"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务正忙，请稍后再试"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> "语音识别服务暂不可用"
        else -> "语音识别失败"
    }
}
private fun JSONObject.toMap(): Map<Any, Any> {
    val map = mutableMapOf<Any, Any>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val v = opt(key)) {
            is JSONObject -> {
                map[key] = v.toMap()
            }

            else -> {
                v?.also {
                    map[key] = it
                }
            }
        }
    }
    return map
}
