package com.kuikly.stockchat.chat

import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.SymbolCardIntent
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntityType

/**
 * Keep structured cards tied to an entity already established by the current
 * turn or conversation; this prevents a fallback/demo symbol from appearing in
 * a generic answer while allowing the prompt to add useful market cards by
 * default for a known stock.
 */
internal object CardResponseSanitizer {
    fun removeUnrelatedCards(
        question: String,
        conversation: List<ChatMessage>,
        response: String,
        preferredSymbols: List<String> = emptyList(),
    ): String {
        val allowedSymbols = establishedSymbols(question, conversation, preferredSymbols)

        var sanitized = response
        // Work backwards: card ids are positional, so replacing a later fence cannot
        // invalidate the ids of fences that still need processing before it.
        AiResponseLexer.lex(response).filterIsInstance<CardBlock>().asReversed().forEach { block ->
            val canonical = canonicalBlock(block)
            if (canonical == null || !isAllowed(canonical, allowedSymbols.toSet(), question)) {
                sanitized = AiResponseLexer.replaceCardBlock(sanitized, block.id, "")
            } else if (canonical != block) {
                sanitized = AiResponseLexer.replaceCardBlock(
                    sanitized,
                    block.id,
                    "```card:${canonical.type}\n${canonical.payload}\n```",
                )
            }
        }
        return appendFallbackQuoteCard(sanitized.trim(), question, conversation, preferredSymbols)
    }

    /**
     * Providers often emit valid but non-canonical exchange prefixes (SH600519,
     * 600519.SS). Rendering needs the application symbol so quote lookup succeeds.
     */
    private fun canonicalBlock(block: CardBlock): CardBlock? {
        val intent = CardPayloadParser.parse(block.type, block.payload)
        val rawSymbol = when (intent) {
            is SymbolCardIntent -> intent.symbol
            is AttributionIntent -> intent.symbol
            else -> return block
        }
        val symbol = canonicalSymbol(rawSymbol) ?: return null
        if (symbol == rawSymbol) return block
        return block.copy(payload = block.payload.replaceSymbol(rawSymbol, symbol))
    }

    private fun String.replaceSymbol(oldSymbol: String, newSymbol: String): String =
        replace(Regex("(\\\"symbol\\\"\\s*:\\s*\\\")${Regex.escape(oldSymbol)}(\\\")", RegexOption.IGNORE_CASE)) {
            it.groupValues[1] + newSymbol + it.groupValues[2]
        }

    private fun establishedSymbols(
        question: String,
        conversation: List<ChatMessage>,
        preferredSymbols: List<String>,
    ): List<String> = buildList {
        preferredSymbols.mapNotNullTo(this, ::canonicalSymbol)
        EntityRecognizer.recognize(
            buildString {
                append(question)
                conversation
                    .takeLast(CONTEXT_MESSAGE_LIMIT)
                    .filter { it.role != MessageRole.SYSTEM }
                    .forEach { append('\n').append(it.content) }
            },
        )
            .filter { it.type == EntityType.STOCK }
            .mapNotNullTo(this) { span -> canonicalSymbol(span.target) }
    }.distinct()

    /** A card fallback is safe only when the current turn has one unambiguous focus. */
    private fun appendFallbackQuoteCard(
        response: String,
        question: String,
        conversation: List<ChatMessage>,
        preferredSymbols: List<String>,
    ): String {
        if (AiResponseLexer.lex(response).filterIsInstance<CardBlock>().any(::isMarketCard)) return response
        val symbol = fallbackSymbol(question, conversation, preferredSymbols) ?: return response
        val card = "```card:stock-quote\n{\"symbol\":\"$symbol\"}\n```"
        return if (response.isBlank()) card else "$response\n\n$card"
    }

    private fun isMarketCard(block: CardBlock): Boolean = when (CardPayloadParser.parse(block.type, block.payload)) {
        is SymbolCardIntent, is AttributionIntent -> true
        else -> false
    }

    private fun fallbackSymbol(
        question: String,
        conversation: List<ChatMessage>,
        preferredSymbols: List<String>,
    ): String? {
        preferredSymbols.mapNotNull(::canonicalSymbol).distinct().singleOrNull()?.let { return it }
        unambiguousSymbols(question).singleOrNull()?.let { return it }
        return conversation.asReversed()
            .firstOrNull { it.role == MessageRole.USER }
            ?.content
            ?.let(::unambiguousSymbols)
            ?.singleOrNull()
    }

    private fun unambiguousSymbols(text: String): List<String> = EntityRecognizer.recognize(text)
        .filter { it.type == EntityType.STOCK && it.candidates.size == 1 }
        .mapNotNull { canonicalSymbol(it.target) }
        .distinct()

    private fun canonicalSymbol(value: String): String? {
        val normalized = value.trim().uppercase().replace(" ", "")
            .replace(Regex("^SH(\\d{6})$"), "\$1.SH")
            .replace(Regex("^SZ(\\d{6})$"), "\$1.SZ")
            .replace(Regex("^(\\d{6})\\.(SS|XSHG)$"), "\$1.SH")
            .replace(Regex("^(\\d{6})\\.(XSHE)$"), "\$1.SZ")
        return Securities.all.firstOrNull { security ->
            security.symbol.equals(normalized, ignoreCase = true) ||
                security.symbol.substringBefore('.').equals(normalized, ignoreCase = true)
        }?.symbol
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
