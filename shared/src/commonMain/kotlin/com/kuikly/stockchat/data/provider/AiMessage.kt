package com.kuikly.stockchat.data.provider

/** Provider-neutral AI request model; feature packages adapt their state into it. */
data class AiMediaPart(val name: String, val imageDataUrl: String? = null, val documentText: String? = null)
data class AiChatMessage(val role: String, val content: String, val media: List<AiMediaPart> = emptyList())
