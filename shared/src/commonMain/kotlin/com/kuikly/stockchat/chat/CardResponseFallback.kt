package com.kuikly.stockchat.chat

import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntityType

/** Keeps the card interaction demo reachable when a model replies with plain text. */
internal object CardResponseFallback {
    fun appendMissingCard(question: String, response: String): String {
        if (response.contains("```card:")) return response
        val symbol = EntityRecognizer.recognize(question)
            .firstOrNull { it.type == EntityType.STOCK }
            ?.target ?: "600519.SH"
        val normalized = question.lowercase()
        val card = when {
            "对比" in question || "比较" in question -> "stock-compare" to "{\"symbol\":\"$symbol\"}"
            "资讯" in question || "新闻" in question || "公告" in question -> "news" to "{\"symbol\":\"$symbol\"}"
            "为什么" in question || "原因" in question || "涨" in question || "跌" in question ->
                "attribution" to "{\"symbol\":\"$symbol\",\"direction\":\"${if ("涨" in question) "rise" else "fall"}\"}"
            "pe" in normalized || "市盈率" in question || "pb" in normalized || "macd" in normalized ->
                "definition" to "{\"term\":\"${if ("macd" in normalized) "MACD" else "市盈率 PE"}\"}"
            "走势" in question || "k线" in normalized || "分时" in question -> "stock-chart" to "{\"symbol\":\"$symbol\"}"
            else -> "stock-quote" to "{\"symbol\":\"$symbol\"}"
        }
        return response.trimEnd() + "\n\n```card:${card.first}\n${card.second}\n```"
    }
}
