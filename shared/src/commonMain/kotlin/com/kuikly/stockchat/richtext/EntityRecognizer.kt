package com.kuikly.stockchat.richtext

import com.kuikly.stockchat.data.entity.Securities

enum class EntityType { STOCK, TERM }

data class EntitySpan(
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val type: EntityType,
    val target: String,
)

object EntityRecognizer {
    private val terms = mapOf(
        "MACD" to "MACD",
        "市盈率" to "PE",
        "PE" to "PE",
        "PB" to "PB",
        "换手率" to "TURNOVER",
        "北向资金" to "NORTHBOUND",
        "量比" to "VOLUME_RATIO",
        "前复权" to "QFQ",
    )

    fun recognize(text: String): List<EntitySpan> {
        val candidates = mutableListOf<EntitySpan>()
        Securities.all.forEach { security ->
            (listOf(security.name, security.symbol.substringBefore('.')) + security.aliases)
                .filter { it.length >= 2 }
                .distinct()
                .forEach { alias -> addOccurrences(candidates, text, alias, EntityType.STOCK, security.symbol) }
        }
        terms.forEach { (term, target) -> addOccurrences(candidates, text, term, EntityType.TERM, target) }
        return candidates
            .sortedWith(compareBy<EntitySpan> { it.start }.thenByDescending { it.endExclusive - it.start })
            .fold(mutableListOf()) { accepted, candidate ->
                if (accepted.none { candidate.start < it.endExclusive && candidate.endExclusive > it.start }) accepted += candidate
                accepted
            }
    }

    private fun addOccurrences(
        output: MutableList<EntitySpan>,
        text: String,
        token: String,
        type: EntityType,
        target: String,
    ) {
        var from = 0
        while (from < text.length) {
            val index = text.indexOf(token, from, ignoreCase = token.all { it.code < 128 })
            if (index < 0) break
            output += EntitySpan(index, index + token.length, text.substring(index, index + token.length), type, target)
            from = index + token.length
        }
    }
}
