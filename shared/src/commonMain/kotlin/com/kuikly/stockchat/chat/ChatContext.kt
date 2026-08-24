package com.kuikly.stockchat.chat

data class AiChatMessage(val role: String, val content: String)

object ChatContext {
    const val MAX_MESSAGES = 12
    const val MAX_CHARACTERS = 9_000

    fun build(messages: List<ChatMessage>): List<AiChatMessage> {
        val candidates = messages.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .filter { !it.failed && !it.cancelled && it.content.isNotBlank() }
            .takeLast(MAX_MESSAGES)
        val selected = mutableListOf<AiChatMessage>()
        var remaining = MAX_CHARACTERS
        candidates.asReversed().forEach { message ->
            if (remaining <= 0) return@forEach
            val content = message.content.takeLast(remaining)
            selected += AiChatMessage(if (message.role == MessageRole.USER) "user" else "assistant", content)
            remaining -= content.length
        }
        return selected.asReversed()
    }
}
