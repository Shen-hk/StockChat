package com.kuikly.stockchat.data.provider

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

object SseEventParser {
    fun delta(line: String): String? {
        val event = line.trim()
        if (!event.startsWith("data:")) return null
        val payload = event.removePrefix("data:").trim()
        if (payload == "[DONE]") return ""
        return try {
            val data = JSONObject(payload)
            data.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                ?: data.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?: ""
        } catch (_: Throwable) {
            null
        }
    }
}
