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

    /** Some native transports deliver the complete response instead of individual SSE lines. */
    fun deltas(body: String): List<String> {
        if (body.isBlank()) return emptyList()
        val lines = body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .toList()
        if (lines.isNotEmpty()) {
            return lines.mapNotNull { delta(it) }.filter { it.isNotEmpty() }
        }
        return delta("data: ${body.trim()}")?.takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty()
    }
}
