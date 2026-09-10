package com.kuikly.stockchat.data.entity

data class Security(
    val symbol: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    /** 市场分类（沪深A股/港股/美股/指数），本地全量数据直接填充；远端搜索建议按返回原样填充 */
    val market: String = "",
    /** 标的种类（stock/index/board），远端搜索建议场景填充 */
    val kind: String = "",
)

object Securities {
    /** 全局搜索页「股票与指数」tab 栏目顺序（「全部」在页面侧拼装） */
    const val MARKET_CN_A = "沪深A股"
    const val MARKET_HK = "港股"
    const val MARKET_US = "美股"
    const val MARKET_INDEX = "指数"

    val categories = listOf(MARKET_CN_A, MARKET_HK, MARKET_US, MARKET_INDEX)

    val all = listOf(
        // 沪深A股
        Security("600519.SH", "贵州茅台", listOf("茅台", "600519", "GZMT", "GUIZHOUMOUTAI"), MARKET_CN_A, "stock"),
        Security("000858.SZ", "五粮液", listOf("000858", "WLY", "WULIANGYE"), MARKET_CN_A, "stock"),
        Security("601318.SH", "中国平安", listOf("平安", "601318", "ZGPA", "ZHONGGUOPINGAN"), MARKET_CN_A, "stock"),
        Security("600036.SH", "招商银行", listOf("招行", "600036", "ZSYH", "ZHAOSHANGYINHANG"), MARKET_CN_A, "stock"),
        Security("300750.SZ", "宁德时代", listOf("宁德", "300750", "NDSD", "NINGDESHIDAI"), MARKET_CN_A, "stock"),
        Security("002594.SZ", "比亚迪", listOf("BYD", "002594", "BIYADI"), MARKET_CN_A, "stock"),
        Security("688981.SH", "中芯国际", listOf("中芯", "688981", "ZXGJ", "ZHONGXINGUOJI"), MARKET_CN_A, "stock"),
        // “平安” is intentionally shared with 中国平安 so the chat UI can exercise contextual disambiguation.
        Security("000001.SZ", "平安银行", listOf("平安", "000001", "PAYH", "PINGANYINHANG"), MARKET_CN_A, "stock"),
        // 港股
        Security("00700.HK", "腾讯控股", listOf("腾讯", "00700", "TXKG", "TENCENT"), MARKET_HK, "stock"),
        Security("09988.HK", "阿里巴巴", listOf("阿里", "09988", "ALIBABA"), MARKET_HK, "stock"),
        Security("03690.HK", "美团", listOf("03690", "MEITUAN"), MARKET_HK, "stock"),
        Security("01810.HK", "小米集团", listOf("小米", "01810", "XIAOMI"), MARKET_HK, "stock"),
        // 美股
        Security("AAPL.US", "苹果", listOf("AAPL", "APPLE"), MARKET_US, "stock"),
        Security("TSLA.US", "特斯拉", listOf("TSLA", "TESLA"), MARKET_US, "stock"),
        Security("NVDA.US", "英伟达", listOf("NVDA", "NVIDIA"), MARKET_US, "stock"),
        // 指数
        Security("000001.SH", "上证指数", listOf("上证", "大盘", "SZZS", "SHANGZHENGZHISHU"), MARKET_INDEX, "index"),
        Security("399001.SZ", "深证成指", listOf("深证", "深成指", "399001", "SZCZ"), MARKET_INDEX, "index"),
        Security("399006.SZ", "创业板指", listOf("创业板", "399006", "CYBZ"), MARKET_INDEX, "index"),
        Security("HSI.HK", "恒生指数", listOf("恒指", "恒生", "HSI", "HENGSHENGZHISHU"), MARKET_INDEX, "index"),
    )

    // “平安” is intentionally shared with 中国平安 so the chat UI can exercise contextual disambiguation.

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
