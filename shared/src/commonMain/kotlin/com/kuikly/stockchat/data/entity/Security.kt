package com.kuikly.stockchat.data.entity

data class Security(
    val symbol: String,
    val name: String,
    val aliases: List<String> = emptyList(),
)

object Securities {
    val all = listOf(
        Security("600519.SH", "贵州茅台", listOf("茅台", "600519")),
        Security("000858.SZ", "五粮液", listOf("000858")),
        Security("601318.SH", "中国平安", listOf("平安", "601318")),
        Security("000001.SH", "上证指数", listOf("上证", "大盘")),
        Security("300750.SZ", "宁德时代", listOf("宁德", "300750")),
        Security("00700.HK", "腾讯控股", listOf("腾讯", "00700")),
        // “平安” is intentionally shared with 中国平安 so the chat UI can exercise contextual disambiguation.
        Security("000001.SZ", "平安银行", listOf("平安", "000001")),
    )

    fun find(query: String): Security? {
        val normalized = query.trim().uppercase()
        return all.firstOrNull { security ->
            security.symbol.uppercase() == normalized ||
                security.name in query ||
                security.aliases.any { it.uppercase() == normalized || it in query }
        }
    }
}
