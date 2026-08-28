package com.kuikly.stockchat.chat

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.handler.observable

enum class MessageRole { SYSTEM, USER, ASSISTANT }

class ChatMessage(
    override val pagerId: String,
    val id: String,
    val role: MessageRole,
    content: String,
    streaming: Boolean = false,
    failed: Boolean = false,
    cancelled: Boolean = false,
) : PagerScope {
    var content: String by observable(content)
    var streaming: Boolean by observable(streaming)
    var failed: Boolean by observable(failed)
    var cancelled: Boolean by observable(cancelled)
}

enum class StreamState { IDLE, STREAMING, STOPPED, ERROR }

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
    val groupTitle: String,
)
