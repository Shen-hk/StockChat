package com.kuikly.stockchat.chat

import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntityType
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Persists completed and interrupted conversation turns on the current device only. */
class ChatSessionStore(
    private val preferences: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    fun activeSessionId(): String {
        ensureMigrated()
        val activeId = preferences.getString(ACTIVE_SESSION_KEY)
        if (activeId.isNotBlank()) return activeId
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
                    // 每次读取都按完整会话重算，旧版本保存的“首问标题”也会立即升级。
                    title = titleFor(session.messages),
                    preview = session.preview,
                    updatedAtMillis = session.updatedAtMillis,
                    groupTitle = groupTitleFor(session.updatedAtMillis, now),
                )
            }
    }

    fun hasSessions(): Boolean {
        ensureMigrated()
        return readSessions().isNotEmpty()
    }

    /**
     * 只读聚合全部会话消息（不切换 activeSession——[load] 会写 ACTIVE_SESSION_KEY，
     * 跨页分析（如风险地图思维画像）不能用，否则会悄悄换掉聊天页的当前会话）。
     */
    fun peekAllMessages(): List<StoredChatMessage> {
        ensureMigrated()
        return readSessions().flatMap { it.messages }
    }

    fun save(sessionId: String, messages: List<ChatMessage>) {
        ensureMigrated()
        val resolvedSessionId = sessionId.ifBlank { newSessionId() }
        val storedMessages = messages
            .takeLast(MAX_MESSAGES)
            .filter { !it.streaming && (it.content.isNotBlank() || it.attachments.isNotEmpty()) }
            .map { message ->
                StoredChatMessage(
                    message.id,
                    message.role,
                    message.content,
                    message.failed,
                    message.cancelled,
                    message.attachments,
                )
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
        summarizeConversationTitle(messages)

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
    val attachments: List<MessageAttachment> = emptyList(),
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
                put("attachments", JSONArray().apply {
                    message.attachments.forEach { attachment ->
                        put(JSONObject().apply {
                            put("id", attachment.id)
                            put("path", attachment.path)
                            put("name", attachment.name)
                            put("isImage", attachment.isImage)
                        })
                    }
                })
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
        val attachments = item.optJSONArray("attachments")?.toMessageAttachments().orEmpty()
        if (content.isNotBlank() || attachments.isNotEmpty()) {
            add(StoredChatMessage(item.optString("id"), role, content, item.optBoolean("failed"), item.optBoolean("cancelled"), attachments))
        }
    }
}

private fun JSONArray.toMessageAttachments(): List<MessageAttachment> = buildList {
    repeat(length()) { index ->
        val item = optJSONObject(index) ?: return@repeat
        val path = item.optString("path")
        if (path.isBlank()) return@repeat
        add(
            MessageAttachment(
                id = item.optString("id").ifBlank { "attachment_$index" },
                path = path,
                name = item.optString("name"),
                isImage = item.optBoolean("isImage"),
            ),
        )
    }
}

private fun String.cleanForSummary(): String =
    replace(Regex("```card:[\\s\\S]*?```"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length > 18) "${it.take(18)}…" else it }

/**
 * 从整段会话提炼稳定的主题标题，不再把首个问题原样截断当标题。
 * 标题完全由本地实体与主题词生成：切换抽屉时即时可用，也不会为标题额外发起模型请求。
 */
private fun summarizeConversationTitle(messages: List<StoredChatMessage>): String {
    val userTexts = messages
        .asSequence()
        .filter { it.role == MessageRole.USER }
        .map { it.content.cleanConversationText() }
        .filter { it.isNotBlank() }
        .toList()
    if (userTexts.isEmpty()) return "新会话"

    val combined = userTexts.joinToString(" ")
    val stockNames = userTexts
        .flatMap { text ->
            EntityRecognizer.recognize(text)
                .filter { it.type == EntityType.STOCK }
                .mapNotNull { span -> Securities.all.firstOrNull { it.symbol == span.target }?.name }
        }
        .distinct()
    val topics = CONVERSATION_TITLE_TOPICS
        .filter { topic -> topic.keywords.any(combined::contains) }
        .map { it.label }

    val title = when {
        stockNames.size >= 2 && COMPARISON_WORDS.any(combined::contains) ->
            "${stockNames[0]}与${stockNames[1]}对比"
        stockNames.size >= 2 -> "${stockNames[0]}等${stockNames.size}只标的分析"
        stockNames.size == 1 && topics.size == 1 -> "${stockNames[0]}${topics[0]}"
        stockNames.size == 1 -> "${stockNames[0]}综合分析"
        topics.size >= 2 -> "${topics[0]}与${topics[1]}"
        topics.size == 1 -> topics[0]
        userTexts.size >= 2 -> "多轮投资问题总结"
        else -> "投资问题解读"
    }
    return title.take(18)
}

private data class ConversationTitleTopic(val label: String, val keywords: List<String>)

private val CONVERSATION_TITLE_TOPICS = listOf(
    ConversationTitleTopic("估值分析", listOf("估值", "市盈率", "市净率", "PE", "PB", "贵不贵")),
    ConversationTitleTopic("业绩解读", listOf("业绩", "财报", "营收", "利润", "盈利", "基本面")),
    ConversationTitleTopic("风险分析", listOf("风险", "回撤", "波动", "下跌", "亏损")),
    ConversationTitleTopic("技术面分析", listOf("技术面", "K线", "均线", "MACD", "RSI", "走势")),
    ConversationTitleTopic("资金面分析", listOf("资金", "主力", "北向", "流入", "流出", "换手")),
    ConversationTitleTopic("行情复盘", listOf("行情", "涨跌", "今天", "近期", "最近")),
    ConversationTitleTopic("公告与新闻", listOf("公告", "新闻", "消息", "事件")),
    ConversationTitleTopic("分红回报", listOf("分红", "股息", "回报")),
    ConversationTitleTopic("自选复盘", listOf("自选", "持仓", "组合")),
)

private val COMPARISON_WORDS = listOf("对比", "比较", "区别", "哪个好", "谁更", "相比")

private fun String.cleanConversationText(): String =
    replace(Regex("```card:[\\s\\S]*?```"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun JSONObject.long(key: String): Long = optString(key).toLongOrNull() ?: 0L
