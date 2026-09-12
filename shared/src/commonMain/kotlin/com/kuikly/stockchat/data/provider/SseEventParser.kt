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
            val choice = data.optJSONArray("choices")?.optJSONObject(0) ?: return ""
            // Deliberately ignore reasoning_content: it is model scratch work,
            // not the answer we render. MiMo, DeepSeek and other reasoning
            // models may stream it before the normal content field.
            contentText(choice.optJSONObject("delta"))
                ?: contentText(choice.optJSONObject("message"))
                ?: ""
        } catch (_: Throwable) {
            null
        }
    }

    /** Supports the regular string form and OpenAI-compatible typed content arrays. */
    private fun contentText(message: JSONObject?): String? {
        if (message == null) return null
        // JSONObject.optString serializes an array as a JSON string. Inspect the
        // typed-content form first so a compatible provider cannot leak the raw
        // array into the chat or hide a card fence inside it.
        val parts = message.optJSONArray("content")
        if (parts != null) {
            return buildString {
                for (index in 0 until parts.length()) {
                    val part = parts.optJSONObject(index) ?: continue
                    part.optString("text")
                        .takeUnless { it.isEmpty() || it == "null" }
                        ?.let(::append)
                }
            }.takeIf { it.isNotEmpty() }
        }
        val string = message.optString("content")
            .takeUnless { it.isEmpty() || it == "null" }
        if (string != null) return string
        return null
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
