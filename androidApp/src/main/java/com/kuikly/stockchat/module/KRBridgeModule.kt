package com.kuikly.stockchat.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
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
import android.os.Build
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
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

            "postTestNotification", "postMockStockAlert" -> {
                postMockStockAlert(params)
            }

            "openComposerMediaSource" -> {
                openComposerMediaSource(params, callback)
            }

            // 页面注册「媒体选择结果」回调；host 挂在 Activity 上，onDestroy 清空。
            "registerComposerMediaResult" -> {
                (activity as? KuiklyRenderActivity)?.composerMediaResultHost = callback
            }

            "prepareAiMedia" -> {
                prepareAiMedia(params, callback)
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

    private fun postMockStockAlert(params: String?) {
        val currentActivity = activity ?: return
        val vibrate = params?.let { JSONObject(it).optBoolean("vibrate", true) } ?: true
        val delayMillis = params?.let { JSONObject(it).optLong("delayMillis", 0L) } ?: 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingMockNotification = PendingMockNotification(vibrate, delayMillis)
            ActivityCompat.requestPermissions(currentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
            Toast.makeText(currentActivity, "允许通知后会自动试播", Toast.LENGTH_SHORT).show()
            return
        }
        if (!NotificationManagerCompat.from(currentActivity).areNotificationsEnabled()) {
            Toast.makeText(currentActivity, "系统已关闭本应用通知，请在系统设置中开启后再试播", Toast.LENGTH_SHORT).show()
            return
        }
        MockStockAlertNotification.schedule(currentActivity, vibrate, delayMillis)
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
        Log.i("StockChatBridge", "openComposerMediaSource source=$params")
        val currentActivity = activity as? KuiklyRenderActivity
        // callback 只用于错误上报，可为空；不能因它缺失而放弃拉起选择器。
        if (currentActivity == null) {
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
            callback?.invoke(mapOf("code" to -1, "message" to error.message.orEmpty()))
        }
    }

    /**
     * Build a bounded, one-request-only multimodal payload from the app-private
     * composer cache. Images become data URLs; text-like documents become text.
     * Binary office/PDF files are deliberately not uploaded blindly: users get a
     * clear model-side note instead of corrupted text or an oversized request.
     */
    private fun prepareAiMedia(params: String?, callback: KuiklyRenderCallback?) {
        Thread {
            val output = mutableListOf<Map<String, String>>()
            try {
                val items = JSONObject(params ?: "{}").optJSONArray("items") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val file = File(item.optString("path"))
                    if (!file.isFile) continue
                    val name = item.optString("name").ifBlank { file.name }
                    val image = item.optString("kind") == "image"
                    if (image) {
                        // Keep a multi-image prompt under a practical mobile request budget.
                        if (file.length() > MAX_AI_IMAGE_BYTES) {
                            output += mapOf("name" to name, "documentText" to "图片过大，未随请求上传（请控制在 6 MB 内）。")
                        } else {
                            val mime = mimeFor(file.name, "image/jpeg")
                            val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                            output += mapOf("name" to name, "imageDataUrl" to "data:$mime;base64,$encoded")
                        }
                    } else {
                        if (file.extension.equals("pdf", ignoreCase = true)) {
                            pdfPreviewDataUrl(file)?.let { dataUrl ->
                                output += mapOf("name" to "$name（第 1 页）", "imageDataUrl" to dataUrl)
                            } ?: run {
                                output += mapOf("name" to name, "documentText" to "PDF 预览生成失败，请上传页面截图或复制关键段落。")
                            }
                        } else {
                            output += mapOf("name" to name, "documentText" to readTextDocument(file))
                        }
                    }
                }
            } catch (error: Throwable) {
                Log.w("StockChatBridge", "prepareAiMedia failed", error)
            }
            activity?.runOnUiThread { callback?.invoke(mapOf("items" to output)) }
        }.start()
    }

    private fun readTextDocument(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        if (extension !in TEXT_DOCUMENT_EXTENSIONS) {
            return "该文件为 .$extension 格式，当前会话无法安全提取正文。请上传 PDF 页面截图或复制关键段落，我可以继续解读。"
        }
        if (file.length() > MAX_AI_DOCUMENT_BYTES) {
            return "文档超过 256 KB，仅支持上传更小的文本文件或复制关键段落。"
        }
        return try {
            file.readText(Charsets.UTF_8).take(MAX_AI_DOCUMENT_CHARS)
        } catch (_: Throwable) {
            "文档无法按 UTF-8 文本读取，请复制关键段落后重试。"
        }
    }

    /** Render the first PDF page to a compact PNG so a vision model can read charts/tables. */
    private fun pdfPreviewDataUrl(file: File): String? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = (1440f / page.width.coerceAtLeast(1)).coerceIn(1f, 2f)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val bytes = java.io.ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        stream.toByteArray()
                    }
                    bitmap.recycle()
                    if (bytes.size > MAX_AI_IMAGE_BYTES) null
                    else "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
        }
    } catch (error: Throwable) {
        Log.w("StockChatBridge", "pdfPreviewDataUrl failed", error)
        null
    }

    private fun mimeFor(name: String, fallback: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> fallback
    }

    /**
     * 图库：系统选择器，支持多选图片（EXTRA_ALLOW_MULTIPLE）。用
     * ACTION_GET_CONTENT 而非 ACTION_PICK——前者的多选约定由系统统一保证，
     * 后者是否支持多选取决于各厂商相册实现。结果经 clipData（多张）或
     * data（单张）回传。
     */
    private fun launchLibrarySource(currentActivity: KuiklyRenderActivity) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        currentActivity.startActivityForResult(intent, RC_COMPOSER_LIBRARY)
    }

    /**
     * 拍照：输出直接写到应用私有持久目录（FileProvider URI）。注意宿主**不声明**
     * CAMERA 权限——声明了反而要求运行时授权，而 ACTION_IMAGE_CAPTURE
     * 由相机应用持权拍摄，宿主无需该权限。
     */
    private fun launchCameraSource(currentActivity: KuiklyRenderActivity) {
        val dir = File(currentActivity.filesDir, COMPOSER_MEDIA_DIR).apply { mkdirs() }
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
     * files/composer_media 下的宿主私有文件，再经 composerMediaResultHost
     * 回传 {type:"ok", source, items:[{kind, path, name}...]}；取消回传 {type:"cancel"}。
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
        private const val REQUEST_NOTIFICATION_PERMISSION = 8302
        private data class PendingMockNotification(val vibrate: Boolean, val delayMillis: Long)
        @Volatile
        private var pendingMockNotification: PendingMockNotification? = null

        fun handleNotificationPermissionResult(
            activity: KuiklyRenderActivity,
            requestCode: Int,
            grantResults: IntArray,
        ) {
            if (requestCode != REQUEST_NOTIFICATION_PERMISSION) return
            val pending = pendingMockNotification ?: return
            pendingMockNotification = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                MockStockAlertNotification.schedule(activity, pending.vibrate, pending.delayMillis)
            } else {
                Toast.makeText(activity, "通知权限未开启，无法试播系统提醒", Toast.LENGTH_SHORT).show()
            }
        }

        // 输入栏媒体选择（onActivityResult 请求码，避开语音的 8301）。
        const val RC_COMPOSER_LIBRARY = 8311
        const val RC_COMPOSER_CAMERA = 8312
        const val RC_COMPOSER_DOCUMENT = 8313
        const val COMPOSER_MEDIA_DIR = "composer_media"
        private const val MAX_AI_IMAGE_BYTES = 6L * 1024L * 1024L
        private const val MAX_AI_DOCUMENT_BYTES = 256L * 1024L
        private const val MAX_AI_DOCUMENT_CHARS = 24_000
        private val TEXT_DOCUMENT_EXTENSIONS = setOf("txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "log")

        /** 拍照输出文件（EXTRA_OUTPUT 指定的缓存路径，onActivityResult 时取走）。 */
        @Volatile
        internal var pendingCameraFile: File? = null

        /**
         * onActivityResult 统一入口（Activity 转发）：把选中内容复制到
         * files/composer_media 下的宿主私有文件，再经 composerMediaResultHost
         * 回传 {type:"ok", source, items:[{kind, path, name}...]}（图库多选时
         * items 为多条，拍照/文档恒为单条）；取消回传 {type:"cancel"}。
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
                    deliverMediaItems(activity, host, source, listOf(PickedMedia(file, "image", file.name)))
                }
                RC_COMPOSER_LIBRARY, RC_COMPOSER_DOCUMENT -> {
                    // 多选经 clipData 回传（每项一个 Uri）；旧实现/单选只有 data。
                    val uris = mutableListOf<Uri>()
                    data?.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) {
                            clip.getItemAt(i).uri?.let { uris.add(it) }
                        }
                    }
                    if (uris.isEmpty()) {
                        data?.data?.let { uris.add(it) }
                    }
                    if (uris.isEmpty()) {
                        host.invoke(mapOf("type" to "cancel", "source" to source))
                        return
                    }
                    copyPickedContents(activity, uris, source) { picked ->
                        if (picked.isEmpty()) {
                            activity.runOnUiThread { host.invoke(mapOf("type" to "cancel", "source" to source)) }
                        } else {
                            deliverMediaItems(activity, host, source, picked)
                        }
                    }
                }
            }
        }

        /** 单个已复制到宿主缓存的选中内容。 */
        private class PickedMedia(val file: File, val kind: String, val name: String)

        /**
         * 批量结果回传（统一 post 回主线程）。注意 items 必须用 List<Map> 而
         * 不是 org.json.JSONArray：渲染桥 toJSONObject 只递归处理 List/Map/基本
         * 类型，JSONArray 不在支持列表里会被静默丢弃（2026-09-10 踩坑）。
         */
        private fun deliverMediaItems(
            activity: KuiklyRenderActivity,
            host: KuiklyRenderCallback,
            source: String,
            picked: List<PickedMedia>,
        ) {
            val items = picked.map { media ->
                mapOf(
                    "kind" to media.kind,
                    "path" to media.file.absolutePath,
                    "name" to media.name,
                )
            }
            activity.runOnUiThread {
                host.invoke(
                    mapOf(
                        "type" to "ok",
                        "source" to source,
                        "items" to items,
                    )
                )
            }
        }

        /**
         * 把 content:// 选中内容批量复制到宿主缓存。kind 按真实 MIME 判定：
         * image/ 通配类型回传图片（可缩略图预览），其余按文档回传。任一失败
         * 只跳过该项，不拖垮整批。复制在后台线程，整批完成后一次性回调。
         */
        private fun copyPickedContents(
            activity: KuiklyRenderActivity,
            uris: List<Uri>,
            source: String,
            onResult: (List<PickedMedia>) -> Unit,
        ) {
            Thread {
                val picked = mutableListOf<PickedMedia>()
                try {
                    val resolver = activity.contentResolver
                    for (uri in uris) {
                        try {
                            val mime = resolver.getType(uri).orEmpty()
                            var name = queryDisplayName(resolver, uri)
                            val isImage = mime.startsWith("image/") || name.endsWith(".jpg") ||
                                name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") ||
                                name.endsWith(".gif")
                            val extension = when {
                                mime.contains("/") && !mime.endsWith("*") -> mime.substringAfter('/').substringBefore('+')
                                name.contains('.') -> name.substringAfterLast('.')
                                else -> if (isImage) "jpg" else "dat"
                            }
                            val dir = File(activity.filesDir, COMPOSER_MEDIA_DIR).apply { mkdirs() }
                            val target = File(dir, "pick_${System.currentTimeMillis()}_${picked.size}.$extension")
                            resolver.openInputStream(uri)?.use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                            if (target.exists() && target.length() > 0L) {
                                if (name.isBlank()) name = target.name
                                picked.add(PickedMedia(target, if (isImage) "image" else "file", name))
                            }
                        } catch (error: Throwable) {
                            Log.w("StockChatBridge", "copyPickedContents item failed: $source", error)
                        }
                    }
                } catch (error: Throwable) {
                    Log.w("StockChatBridge", "copyPickedContents failed: $source", error)
                }
                onResult(picked)
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

    // SpeechRecognizer 与第二个 AudioRecord 同时抢用 MIC 会在部分 ROM 上造成
    // "声波不动 + 识别不到"。声波直接使用识别服务提供的 RMS，保证二者读取同一条音频流。
    private val resultTimeout = Runnable {
        if (stopping) deliverTranscript(latestPartial)
    }

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
            try {
                // 只有明确停止听写，系统服务才会输出最终 onResults；此前直接销毁
                // recognizer 会丢掉用户刚说完、尚未来得及发 partial 的文本。
                recognizer?.stopListening()
                mainHandler.postDelayed(resultTimeout, RESULT_TIMEOUT_MS)
            } catch (_: Throwable) {
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
        // 部分识别服务不实现 onRmsChanged，但会稳定派发语音开始事件；先让波形
        // 脱离静止基线，后续 RMS/音频缓冲会覆盖成真实幅度。
        callback.invoke(amplitudePayload(0.18f))
    }

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        callback.invoke(amplitudePayload(normalized))
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // 这是识别服务已经持有的音频缓冲，读取它不会再申请一条 AudioRecord。
        // 厂商若提供该回调，即使遗漏 RMS 也能驱动真实声波。
        val bytes = buffer ?: return
        if (bytes.size < 2) return
        var sum = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < bytes.size) {
            val low = bytes[index].toInt() and 0xFF
            val high = bytes[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt() / 32768.0
            sum += sample * sample
            samples++
            index += 2
        }
        if (samples > 0) {
            callback.invoke(amplitudePayload((kotlin.math.sqrt(sum / samples) * 6.5).coerceIn(0.02, 1.0).toFloat()))
        }
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
        mainHandler.removeCallbacks(resultTimeout)
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

    private companion object {
        const val RESULT_TIMEOUT_MS = 2_000L
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
