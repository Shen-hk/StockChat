package com.kuikly.stockchat.protocol

data class SuggestionIntent(val text: String, val type: String = "drill")

data class FactorIntent(
    val name: String,
    val weight: Double,
    val confidence: String,
    val description: String,
    val source: String,
)

sealed interface CardIntent { val type: String }
data class SymbolCardIntent(override val type: String, val symbol: String) : CardIntent
data class DefinitionIntent(val term: String, val plainText: String, val example: String) : CardIntent {
    override val type: String = "definition"
}
data class AttributionIntent(
    val symbol: String,
    val direction: String,
    val factors: List<FactorIntent>,
) : CardIntent { override val type: String = "attribution" }
data class SuggestionsIntent(val chips: List<SuggestionIntent>) : CardIntent {
    override val type: String = "suggestions"
}
data class UnknownIntent(override val type: String, val rawPayload: String) : CardIntent

object CardPayloadParser {
    fun parse(type: String, payload: String): CardIntent = when (type) {
        "stock-quote", "stock-chart", "insight", "news", "stock-compare" ->
            SymbolCardIntent(type, stringField(payload, "symbol") ?: "600519.SH")
        "definition" -> DefinitionIntent(
            term = stringField(payload, "term") ?: "市盈率 PE",
            plainText = stringField(payload, "plainText") ?: "股价相对于每股收益的倍数，用来观察估值水平。",
            example = stringField(payload, "example") ?: "PE 需要结合行业、增速和盈利稳定性一起看。",
        )
        "attribution" -> AttributionIntent(
            symbol = stringField(payload, "symbol") ?: "600519.SH",
            direction = stringField(payload, "direction") ?: "fall",
            factors = parseFactors(payload).ifEmpty { defaultFactors() },
        )
        "suggestions" -> SuggestionsIntent(parseSuggestions(payload))
        else -> UnknownIntent(type, payload)
    }

    private fun parseSuggestions(payload: String): List<SuggestionIntent> {
        val objectRegex = Regex("\\{[^{}]*\\}")
        return objectRegex.findAll(arrayBody(payload, "chips") ?: "").mapNotNull { match ->
            val text = stringField(match.value, "text") ?: return@mapNotNull null
            SuggestionIntent(text, stringField(match.value, "type") ?: "drill")
        }.toList()
    }

    private fun parseFactors(payload: String): List<FactorIntent> {
        val objectRegex = Regex("\\{[^{}]*\\}")
        return objectRegex.findAll(arrayBody(payload, "factors") ?: "").mapNotNull { match ->
            val name = stringField(match.value, "name") ?: return@mapNotNull null
            FactorIntent(
                name = name,
                weight = numberField(match.value, "weight") ?: 0.25,
                confidence = stringField(match.value, "confidence") ?: "medium",
                description = stringField(match.value, "desc") ?: "暂无更多说明",
                source = stringField(match.value, "source") ?: "行情数据推断",
            )
        }.toList()
    }

    private fun arrayBody(payload: String, field: String): String? {
        val start = Regex("\"$field\"\\s*:\\s*\\[").find(payload)?.range?.last?.plus(1) ?: return null
        var depth = 1
        for (index in start until payload.length) {
            when (payload[index]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return payload.substring(start, index)
                }
            }
        }
        return null
    }

    private fun stringField(payload: String, field: String): String? =
        Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(payload)?.groupValues?.get(1)

    private fun numberField(payload: String, field: String): Double? =
        Regex("\"$field\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").find(payload)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun defaultFactors() = listOf(
        FactorIntent("资金面", 0.42, "high", "成交放大且价格承压，主动卖出力量偏强。", "行情数据推断"),
        FactorIntent("板块联动", 0.28, "medium", "同板块标的同步走弱，存在联动拖累。", "板块行情"),
        FactorIntent("消息面", 0.18, "medium", "暂未发现足以单独解释波动的重大公告。", "公开信息"),
        FactorIntent("情绪面", 0.12, "low", "短线情绪偏谨慎，对波动有放大作用。", "市场宽度"),
    )
}
