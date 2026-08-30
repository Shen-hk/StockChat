package com.kuikly.stockchat.richtext

import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.Securities

enum class EntityType { STOCK, TERM }

data class EntitySpan(
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val type: EntityType,
    val target: String,
    /** All securities matching this text. More than one item requires a user choice. */
    val candidates: List<String> = listOf(target),
)

object EntityRecognizer {
    private data class Entry(val type: EntityType, val target: String)

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        val entries = mutableListOf<Entry>()
    }

    private val dictionary: TrieNode by lazy {
        TrieNode().also { root ->
            Securities.all.forEach { security ->
                (listOf(security.name, security.symbol.substringBefore('.')) + security.aliases)
                    .filter { it.length >= 2 }
                    .distinct()
                    .forEach { insert(root, it, Entry(EntityType.STOCK, security.symbol)) }
            }
            Glossary.matchTokens().forEach { (token, key) -> insert(root, token, Entry(EntityType.TERM, key)) }
        }
    }

    /**
     * Finds non-overlapping entities in one pass. For aliases shared by more than one
     * security, a symbol already mentioned in [contextSymbols] wins; otherwise the
     * first dictionary security is used as the visible default and all choices remain
     * in [EntitySpan.candidates] for the UI.
     */
    fun recognize(text: String, contextSymbols: List<String> = emptyList()): List<EntitySpan> {
        val result = mutableListOf<EntitySpan>()
        var start = 0
        while (start < text.length) {
            var node = dictionary
            var cursor = start
            var longestEntries: List<Entry>? = null
            var endExclusive = start
            while (cursor < text.length) {
                node = node.children[normalized(text[cursor])] ?: break
                cursor += 1
                if (node.entries.isNotEmpty()) {
                    longestEntries = node.entries
                    endExclusive = cursor
                }
            }
            if (longestEntries == null || !isAcceptableMatch(text, start, endExclusive)) {
                start += 1
                continue
            }
            val stockCandidates = longestEntries.filter { it.type == EntityType.STOCK }
                .map { it.target }
                .distinct()
            val type = if (stockCandidates.isNotEmpty()) EntityType.STOCK else EntityType.TERM
            val candidates = if (type == EntityType.STOCK) stockCandidates else longestEntries.map { it.target }.distinct()
            val target = contextSymbols.asReversed().firstOrNull { it in candidates } ?: candidates.first()
            result += EntitySpan(start, endExclusive, text.substring(start, endExclusive), type, target, candidates)
            start = endExclusive
        }
        return result
    }

    private fun insert(root: TrieNode, token: String, entry: Entry) {
        var node = root
        token.forEach { char -> node = node.children.getOrPut(normalized(char)) { TrieNode() } }
        if (entry !in node.entries) node.entries += entry
    }

    /**
     * 英文缩写（PE / MA / RSI）必须作为独立词出现，否则 "Market"、"People" 这类
     * 普通英文单词会被截出 PE、MA 造成误标——术语误标比漏标更伤信任。
     */
    private fun isAcceptableMatch(text: String, start: Int, endExclusive: Int): Boolean {
        if (text.substring(start, endExclusive).any { it.code >= 128 }) return true
        return !isAsciiWordChar(text.getOrNull(start - 1)) && !isAsciiWordChar(text.getOrNull(endExclusive))
    }

    private fun isAsciiWordChar(char: Char?): Boolean =
        char != null && char.code < 128 && (char.isLetterOrDigit() || char == '_')

    private fun normalized(char: Char): Char = if (char.code < 128) char.lowercaseChar() else char
}
