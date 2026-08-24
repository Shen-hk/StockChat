package com.kuikly.stockchat.chat

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Persists completed and interrupted conversation turns on the current device only. */
class ChatSessionStore(override val pagerId: String) : PagerScope {
    private val preferences: SharedPreferencesModule
        get() = getPager().acquireModule(SharedPreferencesModule.MODULE_NAME)

    fun load(): List<StoredChatMessage> {
        val raw = preferences.getString(STORAGE_KEY)
        if (raw.isEmpty()) return emptyList()
        return try {
            val rows = JSONArray(raw)
            buildList {
                repeat(rows.length()) { index ->
                    val item = rows.optJSONObject(index) ?: return@repeat
                    val role = runCatching { MessageRole.valueOf(item.optString("role")) }.getOrNull() ?: return@repeat
                    val content = item.optString("content")
                    if (content.isNotBlank()) {
                        add(StoredChatMessage(item.optString("id"), role, content, item.optBoolean("failed"), item.optBoolean("cancelled")))
                    }
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun save(messages: List<ChatMessage>) {
        val rows = JSONArray()
        messages.takeLast(MAX_MESSAGES).filter { !it.streaming }.forEach { message ->
            rows.put(JSONObject().apply {
                put("id", message.id)
                put("role", message.role.name)
                put("content", message.content)
                put("failed", message.failed)
                put("cancelled", message.cancelled)
            })
        }
        preferences.setString(STORAGE_KEY, rows.toString())
    }

    companion object {
        private const val STORAGE_KEY = "stockchat_chat_session_v1"
        private const val MAX_MESSAGES = 40
    }
}

data class StoredChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val failed: Boolean,
    val cancelled: Boolean,
)
