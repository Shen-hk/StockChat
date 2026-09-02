package module

import com.tencent.kuikly.core.render.web.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.web.ktx.KuiklyRenderCallback
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONException
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONObject
import utils.Ui
import kotlin.js.Date

/**
 * Bridge interface module used by business side
 */
class KRBridgeModule : KuiklyRenderBaseModule() {
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "toast" -> {
                toast(params)
            }

            "copyToPasteboard" -> copyToPasteboard(params)
            "shareInterpretation" -> shareInterpretation(params)

            "log" -> {
                console.log(params)
            }

            "currentTimestamp" -> {
                currentTimestamp(params)
            }

            "dateFormatter" -> {
                dateFormatter(params)
            }

            else -> {
                callback?.invoke(
                    mapOf(
                        "code" to -1,
                        "message" to "Method does not exist"
                    )
                )
            }
        }
    }

    /**
     * Show toast message on page
     */
    private fun toast(params: String?) {
        if (params != null) {
            try {
                val message = JSONObject(params)
                Ui.showToast(message)
            } catch (e: JSONException) {
                // JSON parsing failed
                console.error("toast json parse error", e)
            }
        }
    }

    private fun currentTimestamp(params: String?): String = Date.now().toString()

    private fun copyToPasteboard(params: String?) {
        val content = JSONObject(params ?: "{}").optString("content")
        js("navigator.clipboard && navigator.clipboard.writeText(content)")
    }

    private fun shareInterpretation(params: String?) {
        val content = JSONObject(params ?: "{}").optString("content")
        if (content.isBlank()) return
        js("""
            (function(text) {
                var canvas = document.createElement('canvas');
                var width = 1080, side = 84, lineHeight = 52;
                var measure = canvas.getContext('2d');
                measure.font = '34px sans-serif';
                var lines = [];
                String(text).split('\n').forEach(function(paragraph) {
                    if (!paragraph) { lines.push(''); return; }
                    var line = '';
                    Array.from(paragraph).forEach(function(ch) {
                        var next = line + ch;
                        if (measure.measureText(next).width > width - side * 2 && line) {
                            lines.push(line); line = ch;
                        } else { line = next; }
                    });
                    lines.push(line);
                });
                canvas.width = width;
                canvas.height = Math.min(24000, Math.max(720, 330 + lines.length * lineHeight));
                var ctx = canvas.getContext('2d');
                ctx.fillStyle = '#F4F6FA'; ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.fillStyle = '#FFFFFF'; ctx.fillRect(42, 42, width - 84, canvas.height - 84);
                ctx.fillStyle = '#1A5CD9'; ctx.font = 'bold 48px sans-serif'; ctx.fillText('股问 StockChat', side, 130);
                ctx.fillStyle = '#262A36'; ctx.font = '34px sans-serif';
                lines.forEach(function(line, index) {
                    var y = 220 + index * lineHeight;
                    if (y < canvas.height - 130) ctx.fillText(line, side, y);
                });
                ctx.fillStyle = '#737986'; ctx.font = '24px sans-serif';
                ctx.fillText('信息解释，不构成投资建议 · 数据以原始信源为准', side, canvas.height - 78);
                canvas.toBlob(function(blob) {
                    if (!blob) return;
                    var file = new File([blob], 'StockChat-share.png', {type: 'image/png'});
                    if (navigator.share && navigator.canShare && navigator.canShare({files: [file]})) {
                        navigator.share({title: '股问 StockChat', files: [file]}).catch(function() {});
                    } else {
                        var link = document.createElement('a');
                        link.download = file.name; link.href = URL.createObjectURL(blob); link.click();
                        setTimeout(function() { URL.revokeObjectURL(link.href); }, 1000);
                    }
                }, 'image/png');
            })(content)
        """)
    }

    private fun formatDate(date: Date, format: String): String {
        fun pad(num: Int) = num.toString().padStart(2, '0')
        val replacements = mapOf(
            "yyyy" to date.getFullYear().toString(),
            "MM" to pad(date.getMonth() + 1),
            "dd" to pad(date.getDate()),
            "HH" to pad(date.getHours()),
            "mm" to pad(date.getMinutes()),
            "ss" to pad(date.getSeconds())
        )
        var result = format
        for ((k, v) in replacements) {
            result = result.replace(k, v)
        }
        return result
    }

    private fun dateFormatter(params: String?): String {
        val paramJSONObject = JSONObject(params ?: "{}")
        val date = Date(paramJSONObject.optLong("timeStamp"))
        return formatDate(date, paramJSONObject.optString("format"))
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
    }
}
