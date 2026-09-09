package com.kuikly.stockchat.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.kuikly.stockchat.KRApplication
import com.kuikly.stockchat.KuiklyRenderActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

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

            "openUrl" -> {
                openUrlExternal(params)
            }

            "shareInterpretation" -> {
                shareInterpretation(params)
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

            // 页面注册「媒体选择结果」回调；host 挂在 Activity 上，onDestroy 清空。
            "registerComposerMediaResult" -> {
                (activity as? KuiklyRenderActivity)?.composerMediaResultHost = callback
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

            // 深色主题（含 App 内换肤覆盖）→ 状态栏图标切浅色；浅色恢复深色图标。
            // setupImmersiveMode 默认 LIGHT_STATUS_BAR（深色图标），这里按页面解析结果翻转。
            "setStatusBarIconsDark" -> {
                setStatusBarIconsDark(params)
            }

            // 页面注册「大且快右向横滑 → 抽屉展开」回调；host 挂在 Activity 上，
            // Activity 销毁时清空。keepCallback 由 Kuikly 侧 toNative(true,...) 控制。
            "registerDrawerFlingHost" -> {
                (activity as? KuiklyRenderActivity)?.drawerFlingHost = callback
            }

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

    /**
     * 页面解析出的最终主题明暗态（含 App 内「通用设置-主题」覆盖）→
     * 状态栏图标方向。深色页面图标用浅色（清除 LIGHT_STATUS_BAR），
     * 浅色页面恢复默认深色图标。桥调用可能不在主线程，统一 post。
     */
    private fun setStatusBarIconsDark(params: String?) {
        val currentActivity = activity ?: return
        val dark = params != null && JSONObject(params).optInt("dark", 0) == 1
        currentActivity.runOnUiThread {
            val decor = currentActivity.window?.decorView ?: return@runOnUiThread
            @Suppress("DEPRECATION")
            val flags = if (dark) {
                decor.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                decor.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            decor.systemUiVisibility = flags
        }
    }

    private fun startVoiceRecording(callback: KuiklyRenderCallback?) {
        val currentActivity = activity as? KuiklyRenderActivity
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
        val currentActivity = activity as? KuiklyRenderActivity
        if (currentActivity == null || callback == null) {
            callback?.invoke(mapOf("code" to -1, "message" to "页面不可用"))
            return
        }
        val source = JSONObject(params ?: "{}").optString("source")
        try {
            when (source) {
                "camera" -> launchCameraSource(currentActivity)
                "document" -> launchDocumentSource(currentActivity)
                else -> launchLibrarySource(currentActivity)
            }
        } catch (error: Exception) {
            Log.w("StockChatBridge", "openComposerMediaSource failed: $source", error)
            Toast.makeText(KRApplication.application, "打开失败，请稍后重试", Toast.LENGTH_SHORT).show()
            callback.invoke(mapOf("code" to -1, "message" to error.message.orEmpty()))
        }
    }

    /** 图库：系统相册选择器，单张图片。 */
    private fun launchLibrarySource(currentActivity: KuiklyRenderActivity) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        currentActivity.startActivityForResult(intent, RC_COMPOSER_LIBRARY)
    }

    /**
     * 拍照：输出直接写到缓存目录（FileProvider URI）。注意宿主**不声明**
     * CAMERA 权限——声明了反而要求运行时授权，而 ACTION_IMAGE_CAPTURE
     * 由相机应用持权拍摄，宿主无需该权限。
     */
    private fun launchCameraSource(currentActivity: KuiklyRenderActivity) {
        val dir = File(currentActivity.cacheDir, COMPOSER_MEDIA_DIR).apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(
            currentActivity,
            currentActivity.packageName + ".composer_file_provider",
            file,
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        currentActivity.startActivityForResult(intent, RC_COMPOSER_CAMERA)
    }

    /** 文档：系统文档选择器，任意可打开的文件类型。 */
    private fun launchDocumentSource(currentActivity: KuiklyRenderActivity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        currentActivity.startActivityForResult(intent, RC_COMPOSER_DOCUMENT)
    }

    /**
     * onActivityResult 统一入口（Activity 转发）：把选中内容复制到
     * cache/composer_media 下的宿主私有文件，再经 composerMediaResultHost
     * 回传 {type:"ok", kind, path, name, source}；取消回传 {type:"cancel"}。
     * 复制在后台线程执行，结果统一 post 回主线程（与 Kuikly 桥接约定一致）。
     */
    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        (context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.setPrimaryClip(ClipData.newPlainText(MODULE_NAME, paramJSON.optString("content")))
        }
    }

    /** 系统浏览器打开 http(s) 外链（新闻「阅读原文」等）。 */
    private fun openUrlExternal(params: String?) {
        if (params == null) {
            return
        }
        val url = JSONObject(params).optString("url")
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return
        }
        val currentActivity = activity
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (currentActivity != null) {
                currentActivity.startActivity(intent)
            } else {
                context?.startActivity(intent)
            }
        } catch (error: Throwable) {
            Log.w("StockChatBridge", "openUrl failed: $url", error)
            Toast.makeText(KRApplication.application, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareInterpretation(params: String?) {
        val currentActivity = activity ?: return
        val content = JSONObject(params ?: "{}").optString("content")
        if (content.isBlank()) return
        try {
            val width = 1080
            val side = 84
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(38, 42, 54)
                textSize = 38f
            }
            val layoutWidth = width - side * 2
            @Suppress("DEPRECATION")
            val textLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(content, 0, content.length, textPaint, layoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(8f, 1.05f)
                    .build()
            } else {
                StaticLayout(content, textPaint, layoutWidth, Layout.Alignment.ALIGN_NORMAL, 1.05f, 8f, false)
            }
            val height = (textLayout.height + 420).coerceAtLeast(1200)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.rgb(47, 107, 255)
            canvas.drawRoundRect(RectF(58f, 58f, 1022f, 238f), 42f, 42f, paint)
            paint.color = Color.WHITE
            paint.textSize = 60f
            paint.isFakeBoldText = true
            canvas.drawText("股问 StockChat", 92f, 142f, paint)
            paint.textSize = 28f
            paint.isFakeBoldText = false
            canvas.drawText("把数据解释成人话", 94f, 196f, paint)
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(58f, 272f, 1022f, (height - 92).toFloat()), 38f, 38f, paint)
            canvas.save()
            canvas.translate(side.toFloat(), 326f)
            textLayout.draw(canvas)
            canvas.restore()
            paint.color = Color.rgb(128, 134, 151)
            paint.textSize = 25f
            canvas.drawText("生成于股问 · 信息解释不构成投资建议", 84f, (height - 38).toFloat(), paint)

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "StockChat_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StockChat")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = currentActivity.contentResolver.insert(collection, values) ?: return
            currentActivity.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 96, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                currentActivity.contentResolver.update(uri, values, null, null)
            }
            bitmap.recycle()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            currentActivity.startActivity(Intent.createChooser(intent, "分享股问解读"))
        } catch (error: Throwable) {
            Log.w("StockChatShare", "share image failed", error)
            Toast.makeText(KRApplication.application, "长图生成失败，文案已复制", Toast.LENGTH_SHORT).show()
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

        // 输入栏媒体选择（onActivityResult 请求码，避开语音的 8301）。
        const val RC_COMPOSER_LIBRARY = 8311
        const val RC_COMPOSER_CAMERA = 8312
        const val RC_COMPOSER_DOCUMENT = 8313
        const val COMPOSER_MEDIA_DIR = "composer_media"

        /** 拍照输出文件（EXTRA_OUTPUT 指定的缓存路径，onActivityResult 时取走）。 */
        @Volatile
        internal var pendingCameraFile: File? = null

        /**
         * onActivityResult 统一入口（Activity 转发）：把选中内容复制到
         * cache/composer_media 下的宿主私有文件，再经 composerMediaResultHost
         * 回传 {type:"ok", kind, path, name, source}；取消回传 {type:"cancel"}。
         * 复制在后台线程执行，结果统一 post 回主线程（与 Kuikly 桥接约定一致）。
         */
        fun handleComposerMediaResult(activity: KuiklyRenderActivity, requestCode: Int, resultCode: Int, data: Intent?) {
            val host = activity.composerMediaResultHost
            if (host == null) {
                pendingCameraFile = null
                return
            }
            val source = when (requestCode) {
                RC_COMPOSER_LIBRARY -> "library"
                RC_COMPOSER_CAMERA -> "camera"
                RC_COMPOSER_DOCUMENT -> "document"
                else -> return
            }
            if (resultCode != android.app.Activity.RESULT_OK) {
                pendingCameraFile = null
                host.invoke(mapOf("type" to "cancel", "source" to source))
                return
            }
            when (requestCode) {
                RC_COMPOSER_CAMERA -> {
                    val file = pendingCameraFile
                    pendingCameraFile = null
                    if (file == null || !file.exists() || file.length() == 0L) {
                        host.invoke(mapOf("type" to "cancel", "source" to source))
                        return
                    }
                    deliverMediaResult(activity, host, source, file, "image", file.name)
                }
                RC_COMPOSER_LIBRARY, RC_COMPOSER_DOCUMENT -> {
                    val uri = data?.data
                    if (uri == null) {
                        host.invoke(mapOf("type" to "cancel", "source" to source))
                        return
                    }
                    copyPickedContent(activity, uri, source) { kind, file, name ->
                        if (kind == null || file == null) {
                            activity.runOnUiThread { host.invoke(mapOf("type" to "cancel", "source" to source)) }
                        } else {
                            deliverMediaResult(activity, host, source, file, kind, name)
                        }
                    }
                }
            }
        }

        /** 结果回传（统一 post 回主线程）。 */
        private fun deliverMediaResult(
            activity: KuiklyRenderActivity,
            host: KuiklyRenderCallback,
            source: String,
            file: File,
            kind: String,
            name: String,
        ) {
            activity.runOnUiThread {
                host.invoke(
                    mapOf(
                        "type" to "ok",
                        "kind" to kind,
                        "path" to file.absolutePath,
                        "name" to name,
                        "source" to source,
                    )
                )
            }
        }

        /**
         * 把 content:// 选中内容复制到宿主缓存。kind 按真实 MIME 判定：
         * image/ 通配类型回传图片（可缩略图预览），其余按文档回传。复制在后台线程。
         */
        private fun copyPickedContent(
            activity: KuiklyRenderActivity,
            uri: Uri,
            source: String,
            onResult: (kind: String?, file: File?, name: String) -> Unit,
        ) {
            Thread {
                var kind: String? = null
                var file: File? = null
                var name = ""
                try {
                    val resolver = activity.contentResolver
                    val mime = resolver.getType(uri).orEmpty()
                    name = queryDisplayName(resolver, uri)
                    val isImage = mime.startsWith("image/") || name.endsWith(".jpg") ||
                        name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") ||
                        name.endsWith(".gif")
                    val extension = when {
                        mime.contains("/") && !mime.endsWith("*") -> mime.substringAfter('/').substringBefore('+')
                        name.contains('.') -> name.substringAfterLast('.')
                        else -> if (isImage) "jpg" else "dat"
                    }
                    val dir = File(activity.cacheDir, COMPOSER_MEDIA_DIR).apply { mkdirs() }
                    val target = File(dir, "pick_${System.currentTimeMillis()}.$extension")
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (target.exists() && target.length() > 0L) {
                        kind = if (isImage) "image" else "file"
                        file = target
                        if (name.isBlank()) name = target.name
                    }
                } catch (error: Throwable) {
                    Log.w("StockChatBridge", "copyPickedContent failed: $source", error)
                }
                onResult(kind, file, name)
            }.start()
        }

        private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
            return try {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
                } ?: ""
            } catch (_: Throwable) {
                ""
            }
        }

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

    // 真实音量监听：onRmsChanged 依赖 OEM 语音服务实现（小米等大量机型不回调），
    // 波形会停在基线。这里独立开一路 AudioRecord（16kHz 单声道）按 25ms 块
    // 计算真实 PCM RMS，≥35ms 一拍上报（与页侧波形滚动节奏一致）；与 onRmsChanged 的取值做 max 合并，
    // 哪路报告了真实声音就用哪路。回调统一 post 回主线程（与 RecognitionListener
    // 一致），避免 Kuikly 桥接跨线程问题。
    @Volatile private var meterRunning = false
    @Volatile private var meterAvailable = false
    @Volatile private var recognizerRms = 0f
    private var meterThread: Thread? = null

    fun start(): Boolean {
        startAmplitudeMeter()
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
        recognizerRms = normalized
        if (!meterAvailable) {
            // AudioRecord 监听初始化失败的兜底：直接透传识别器的 RMS。
            callback.invoke(amplitudePayload(normalized))
        }
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
        stopAmplitudeMeter()
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

    private fun amplitudePayload(rms: Float): Map<String, Any> =
        mapOf("type" to "amplitude", "rms" to rms)

    /** 录音会话期间开启的真实麦克风音量监听（幂等）。 */
    private fun startAmplitudeMeter() {
        if (meterThread != null) return
        meterRunning = true
        val thread = Thread {
            var record: AudioRecord? = null
            try {
                val sampleRate = 16000
                // 25ms 一块；上报节流到 ≥35ms（与页侧波形滚动节奏一致，
                // 原为 60ms，用户要求加快滚动）。
                val chunk = ShortArray(sampleRate / 40)
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuf > 0) {
                    val r = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuf, chunk.size * 2),
                    )
                    if (r.state == AudioRecord.STATE_INITIALIZED) {
                        record = r
                        r.startRecording()
                        meterAvailable = true
                        var lastPost = 0L
                        while (meterRunning) {
                            val n = r.read(chunk, 0, chunk.size)
                            if (n <= 0) continue
                            var sum = 0.0
                            for (i in 0 until n) {
                                val s = chunk[i].toDouble()
                                sum += s * s
                            }
                            val rms = sqrt(sum / n)
                            // 线性归一：正常说话距离 PCM RMS 约 300–6000，
                            // 2500 作为满刻度偏保守，页侧还有 ×2.5 增益。
                            val own = (rms / 2500.0).coerceIn(0.0, 1.0).toFloat()
                            // 识别器 RMS 衰减合并：哪路报告了真实声音用哪路，
                            // 识别器静默后其旧值按 0.8/拍 衰减避免托底虚高。
                            recognizerRms *= 0.8f
                            val merged = maxOf(own, recognizerRms)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPost >= 35) {
                                lastPost = now
                                mainHandler.post {
                                    if (meterRunning) callback.invoke(amplitudePayload(merged))
                                }
                            }
                        }
                    } else {
                        r.release()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    record?.stop()
                } catch (_: Throwable) {
                }
                try {
                    record?.release()
                } catch (_: Throwable) {
                }
                meterAvailable = false
            }
        }
        thread.isDaemon = true
        meterThread = thread
        thread.start()
    }

    private fun stopAmplitudeMeter() {
        meterRunning = false
        meterThread = null
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
