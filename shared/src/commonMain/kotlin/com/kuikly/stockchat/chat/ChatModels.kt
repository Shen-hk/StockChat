package com.kuikly.stockchat.chat

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.handler.observable

enum class MessageRole { USER, ASSISTANT }

class ChatMessage(
    override val pagerId: String,
    val id: String,
    val role: MessageRole,
    content: String,
    streaming: Boolean = false,
    failed: Boolean = false,
) : PagerScope {
    var content: String by observable(content)
    var streaming: Boolean by observable(streaming)
    var failed: Boolean by observable(failed)
}

enum class StreamState { IDLE, STREAMING, STOPPED, ERROR }
