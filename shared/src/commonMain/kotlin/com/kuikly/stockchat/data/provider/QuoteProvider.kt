package com.kuikly.stockchat.data.provider

enum class DataMode { AUTO, ONLINE, CACHE, OFFLINE }

fun DataMode.quoteLabel(): String = when (this) {
    DataMode.AUTO -> "自动数据模式"
    DataMode.ONLINE -> "实时行情"
    DataMode.CACHE -> "缓存行情"
    DataMode.OFFLINE -> "离线演示模式"
}

/** The provider-native K-line series. WEEK and MONTH are not derived from daily rows. */
enum class KLineInterval(
    val requestPeriod: String,
    val responseField: String,
    val fallbackField: String,
    val defaultCount: Int,
) {
    DAY("day", "qfqday", "day", 240),
    WEEK("week", "qfqweek", "week", 120),
    MONTH("month", "qfqmonth", "month", 60),
}

/**
 * 分时单点。[volume] 为该分钟成交量（手）、[amount] 为该分钟成交额（元），
 * 由腾讯分时接口的累计量额逐分钟差分得到；旧缓存/Mock 无 amount 时为 0，均价走近似口径。
 */
data class QuotePoint(
    val time: String,
    val price: Double,
    val volume: Double = 0.0,
    val amount: Double = 0.0,
)

/** 新闻弹幕带的单条快讯（东财个股资讯）。[time] 形如 `2026-09-07 10:23:42`。 */
data class NewsItem(
    val id: String,
    val title: String,
    val source: String,
    val time: String,
    val url: String,
    val summary: String = "",
)

data class KLinePoint(
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
)

data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val previousClose: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val amount: Double,
    val turnoverRate: Double,
    val peTtm: Double,
    val pb: Double,
    val marketCap: Double,
    val timestamp: String,
    val source: String,
    val timeline: List<QuotePoint> = emptyList(),
    val kLines: List<KLinePoint> = emptyList(),
    val weekKLines: List<KLinePoint> = emptyList(),
    val monthKLines: List<KLinePoint> = emptyList(),
) {
    val change: Double get() = price - previousClose
    val changePercent: Double get() = if (previousClose == 0.0) 0.0 else change / previousClose * 100.0
    val rising: Boolean get() = change >= 0.0

    companion object {
        /** Safe construction-time value for pages whose scoped repository is not available yet. */
        fun placeholder(symbol: String, name: String = symbol): Quote = Quote(
            symbol = symbol,
            name = name,
            price = 0.0,
            previousClose = 0.0,
            open = 0.0,
            high = 0.0,
            low = 0.0,
            volume = 0.0,
            amount = 0.0,
            turnoverRate = 0.0,
            peTtm = 0.0,
            pb = 0.0,
            marketCap = 0.0,
            timestamp = "",
            source = "",
        )
    }
}

interface QuoteProvider {
    val mode: DataMode
    fun snapshot(symbol: String, onResult: (Quote?) -> Unit)
    fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit)
    fun kLines(
        symbol: String,
        count: Int = KLineInterval.DAY.defaultCount,
        interval: KLineInterval = KLineInterval.DAY,
        onResult: (List<KLinePoint>) -> Unit,
    )
}

/** 详情页新闻弹幕带的数据源（东财个股资讯；空列表 = 无可展示内容，UI 整条隐藏）。 */
interface StockNewsProvider {
    fun stockNews(symbol: String, onResult: (List<NewsItem>) -> Unit)
}
