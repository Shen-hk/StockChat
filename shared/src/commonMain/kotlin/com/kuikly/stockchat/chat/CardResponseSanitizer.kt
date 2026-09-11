package com.kuikly.stockchat.chat

import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.SymbolCardIntent
import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntityType

/**
 * A model may still emit a card despite the system prompt. Keep structured cards
 * tied to an entity already established by the current turn or conversation;
 * this prevents a fallback/demo symbol from appearing in a generic answer.
 */
internal object CardResponseSanitizer {
    fun removeUnrelatedCards(
        question: String,
        conversation: List<ChatMessage>,
        response: String,
    ): String {
        val allowedSymbols = EntityRecognizer.recognize(
            buildString {
                append(question)
                conversation
                    .takeLast(CONTEXT_MESSAGE_LIMIT)
                    .filter { it.role != MessageRole.SYSTEM }
                    .forEach { append('\n').append(it.content) }
            },
        )
            .filter { it.type == EntityType.STOCK }
            .map { it.target }
            .toSet()

        var sanitized = response
        AiResponseLexer.lex(response).filterIsInstance<CardBlock>().forEach { block ->
            if (!isAllowed(block, allowedSymbols, question)) {
                sanitized = AiResponseLexer.replaceCardBlock(sanitized, block.id, "")
            }
        }
        return sanitized.trim()
    }

    private fun isAllowed(block: CardBlock, allowedSymbols: Set<String>, question: String): Boolean {
        // Suggestions are a card protocol too. They should never be generated as
        // a generic tail decoration; the prompt only allows them on an explicit ask.
        if (block.type == "suggestions") return false

        return when (val intent = CardPayloadParser.parse(block.type, block.payload)) {
            is SymbolCardIntent -> intent.symbol in allowedSymbols
            is AttributionIntent -> intent.symbol in allowedSymbols
            // A definition card has no stock symbol and is meaningful only when
            // the user explicitly asks for content in card form.
            else -> block.type == "definition" && asksForCard(question)
        }
    }

    private fun asksForCard(question: String): Boolean =
        question.contains("卡片") || question.contains("术语卡")

    private const val CONTEXT_MESSAGE_LIMIT = 8
}
