package com.kuikly.stockchat.chat

import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Persists completed and interrupted conversation turns on the current device only. */
class ChatSessionStore(
    private val preferences: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    constructor(
        pagerId: String,
        nowMillis: () -> Long = ::platformCurrentTimeMillis,
    ) : this(PagerKeyValueStorage(pagerId), nowMillis)

    fun activeSessionId(): String {
        ensureMigrated()
        val activeId = preferences.getString(ACTIVE_SESSION_KEY)
        if (activeId.isNotBlank() && readSessions().any { it.id == activeId }) return activeId
        return readSessions().firstOrNull()?.id ?: newSessionId()
    }

    fun startSession(): String =
        newSessionId().also { preferences.setString(ACTIVE_SESSION_KEY, it) }

    fun load(sessionId: String): List<StoredChatMessage> {
        ensureMigrated()
        preferences.setString(ACTIVE_SESSION_KEY, sessionId)
        return readSessions().firstOrNull { it.id == sessionId }?.messages.orEmpty()
    }

    fun listSummaries(): List<ChatSessionSummary> {
        ensureMigrated()
        val now = nowMillis()
        return readSessions()
            .sortedByDescending { it.updatedAtMillis }
            .map { session ->
                ChatSessionSummary(
                    id = session.id,
                    title = session.title,
                    preview = session.preview,
                    updatedAtMillis = session.updatedAtMillis,
                    groupTitle = groupTitleFor(session.updatedAtMillis, now),
                )
            }
    }

    fun save(sessionId: String, messages: List<ChatMessage>) {
        ensureMigrated()
        val resolvedSessionId = sessionId.ifBlank { newSessionId() }
        val storedMessages = messages
            .takeLast(MAX_MESSAGES)
            .filter { !it.streaming && it.content.isNotBlank() }
            .map { message ->
                StoredChatMessage(message.id, message.role, message.content, message.failed, message.cancelled)
            }
        val sessions = readSessions()
            .filterNot { it.id == resolvedSessionId }
            .toMutableList()
        if (storedMessages.isNotEmpty()) {
            val existing = readSessions().firstOrNull { it.id == resolvedSessionId }
            val updatedAtMillis = nowMillis()
            sessions.add(
                StoredChatSession(
                    id = resolvedSessionId,
                    title = titleFor(storedMessages),
                    preview = previewFor(storedMessages),
                    updatedAtMillis = updatedAtMillis.takeIf { it > 0L } ?: existing?.updatedAtMillis ?: 0L,
                    messages = storedMessages,
                ),
            )
        }
        writeSessions(sessions.sortedByDescending { it.updatedAtMillis }.take(MAX_SESSIONS))
        preferences.setString(ACTIVE_SESSION_KEY, resolvedSessionId)
    }

    private fun ensureMigrated() {
        if (preferences.getString(SESSIONS_KEY).isNotBlank()) return
        val legacy = readLegacyMessages()
        if (legacy.isEmpty()) return
        val id = "legacy"
        writeSessions(
            listOf(
                StoredChatSession(
                    id = id,
                    title = titleFor(legacy),
                    preview = previewFor(legacy),
                    updatedAtMillis = nowMillis().takeIf { it > 0L } ?: 0L,
                    messages = legacy,
                ),
            ),
        )
        preferences.setString(ACTIVE_SESSION_KEY, id)
    }

    private fun readLegacyMessages(): List<StoredChatMessage> {
        val raw = preferences.getString(LEGACY_STORAGE_KEY)
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

    private fun readSessions(): List<StoredChatSession> {
        val raw = preferences.getString(SESSIONS_KEY)
        if (raw.isEmpty()) return emptyList()
        return try {
            val rows = JSONArray(raw)
            buildList {
                repeat(rows.length()) { index ->
                    rows.optJSONObject(index)?.toStoredChatSession()?.let(::add)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writeSessions(sessions: List<StoredChatSession>) {
        val rows = JSONArray()
        sessions.forEach { rows.put(it.toJson()) }
        preferences.setString(SESSIONS_KEY, rows.toString())
    }

    private fun newSessionId(): String {
        val base = "s${nowMillis().takeIf { it > 0L } ?: 0L}"
        val existing = readSessions().map { it.id }.toSet()
        if (base !in existing) return base
        var suffix = 1
        while ("${base}_$suffix" in existing) suffix += 1
        return "${base}_$suffix"
    }

    private fun titleFor(messages: List<StoredChatMessage>): String =
        messages.firstOrNull { it.role == MessageRole.USER }?.content
            ?.cleanForSummary()
            ?.takeIf { it.isNotBlank() }
            ?: "新会话"

    private fun previewFor(messages: List<StoredChatMessage>): String =
        messages.lastOrNull { it.role != MessageRole.SYSTEM }?.content
            ?.cleanForSummary()
            ?.takeIf { it.isNotBlank() }
            ?: "暂无内容"

    private fun groupTitleFor(updatedAtMillis: Long, now: Long): String {
        if (updatedAtMillis <= 0L || now <= 0L) return "更早"
        val age = (now - updatedAtMillis).coerceAtLeast(0L)
        return when {
            age < ONE_DAY_MILLIS -> "今天"
            age < TWO_DAYS_MILLIS -> "昨天"
            else -> "更早"
        }
    }

    companion object {
        private const val LEGACY_STORAGE_KEY = "stockchat_chat_session_v1"
        private const val SESSIONS_KEY = "stockchat_chat_sessions_v2"
        private const val ACTIVE_SESSION_KEY = "stockchat_active_chat_session_v2"
        private const val MAX_MESSAGES = 40
        private const val MAX_SESSIONS = 30
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1_000L
        private const val TWO_DAYS_MILLIS = 2 * ONE_DAY_MILLIS
    }
}

data class StoredChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val failed: Boolean,
    val cancelled: Boolean,
)

private data class StoredChatSession(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
    val messages: List<StoredChatMessage>,
)

private fun StoredChatSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("preview", preview)
    put("updatedAtMillis", updatedAtMillis)
    put("messages", JSONArray().apply {
        messages.forEach { message ->
            put(JSONObject().apply {
                put("id", message.id)
                put("role", message.role.name)
                put("content", message.content)
                put("failed", message.failed)
                put("cancelled", message.cancelled)
            })
        }
    })
}

private fun JSONObject.toStoredChatSession(): StoredChatSession? {
    val id = optString("id")
    if (id.isBlank()) return null
    val messages = optJSONArray("messages")?.toStoredChatMessages().orEmpty()
    if (messages.isEmpty()) return null
    return StoredChatSession(
        id = id,
        title = optString("title").ifEmpty { "新会话" },
        preview = optString("preview").ifEmpty { "暂无内容" },
        updatedAtMillis = long("updatedAtMillis"),
        messages = messages,
    )
}

private fun JSONArray.toStoredChatMessages(): List<StoredChatMessage> = buildList {
    repeat(length()) { index ->
        val item = optJSONObject(index) ?: return@repeat
        val role = runCatching { MessageRole.valueOf(item.optString("role")) }.getOrNull() ?: return@repeat
        val content = item.optString("content")
        if (content.isNotBlank()) {
            add(StoredChatMessage(item.optString("id"), role, content, item.optBoolean("failed"), item.optBoolean("cancelled")))
        }
    }
}

private fun String.cleanForSummary(): String =
    replace(Regex("```card:[\\s\\S]*?```"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length > 18) "${it.take(18)}…" else it }

private fun JSONObject.long(key: String): Long = optString(key).toLongOrNull() ?: 0L
