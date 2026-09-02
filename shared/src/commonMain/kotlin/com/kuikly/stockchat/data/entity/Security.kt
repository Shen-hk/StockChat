package com.kuikly.stockchat.data.entity

data class Security(
    val symbol: String,
    val name: String,
    val aliases: List<String> = emptyList(),
)

object Securities {
    val all = listOf(
        Security("600519.SH", "贵州茅台", listOf("茅台", "600519", "GZMT", "GUIZHOUMOUTAI")),
        Security("000858.SZ", "五粮液", listOf("000858", "WLY", "WULIANGYE")),
        Security("601318.SH", "中国平安", listOf("平安", "601318", "ZGPA", "ZHONGGUOPINGAN")),
        Security("000001.SH", "上证指数", listOf("上证", "大盘", "SZZS", "SHANGZHENGZHISHU")),
        Security("300750.SZ", "宁德时代", listOf("宁德", "300750", "NDSD", "NINGDESHIDAI")),
        Security("00700.HK", "腾讯控股", listOf("腾讯", "00700", "TXKG", "TENCENT")),
        // “平安” is intentionally shared with 中国平安 so the chat UI can exercise contextual disambiguation.
        Security("000001.SZ", "平安银行", listOf("平安", "000001", "PAYH", "PINGANYINHANG")),
    )

    fun find(query: String): Security? {
        val normalized = query.trim().uppercase()
        return all.firstOrNull { security ->
            security.symbol.uppercase() == normalized ||
                security.name in query ||
                security.aliases.any { it.uppercase() == normalized || it in query }
        }
    }

    fun search(query: String, limit: Int = 20): List<Security> {
        val q = query.trim()
        if (q.isEmpty()) return all.take(limit)
        val qUpper = q.uppercase()
        return all.mapNotNull { security ->
            val symbol = security.symbol.uppercase()
            val aliases = security.aliases.map { it.uppercase() }
            val score = when {
                symbol == qUpper -> 100
                security.name == q -> 95
                aliases.any { it == qUpper } -> 90
                symbol.startsWith(qUpper) -> 80
                security.name.startsWith(q) -> 75
                aliases.any { it.startsWith(qUpper) } -> 70
                security.name.contains(q) -> 60
                symbol.contains(qUpper) -> 50
                aliases.any { it.contains(qUpper) } -> 45
                else -> return@mapNotNull null
            }
            score to security
        }
            .sortedWith(compareByDescending<Pair<Int, Security>> { it.first }.thenBy { it.second.symbol })
            .map { it.second }
            .take(limit)
    }
}
