package com.kuikly.stockchat.data.mock

import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.KLinePoint
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePoint
import com.kuikly.stockchat.data.provider.QuoteProvider
import com.kuikly.stockchat.data.provider.StockNewsProvider
import kotlin.math.max
import kotlin.math.min

object MockDataBank {
    private data class SeedQuote(
        val price: Double,
        val previousClose: Double,
        val pe: Double,
        val pb: Double,
        val marketCap: Double,
    )

    private val seeds = mapOf(
        "600519.SH" to SeedQuote(1272.83, 1291.50, 19.54, 6.33, 1_591_141_000_000.0),
        "000858.SZ" to SeedQuote(128.46, 131.20, 18.72, 4.25, 498_600_000_000.0),
        "601318.SH" to SeedQuote(61.38, 60.92, 7.81, 1.18, 1_119_000_000_000.0),
        "000001.SH" to SeedQuote(3825.76, 3806.58, 0.0, 0.0, 0.0),
        "300750.SZ" to SeedQuote(312.90, 307.44, 24.31, 5.62, 1_376_000_000_000.0),
        "00700.HK" to SeedQuote(621.50, 614.00, 23.12, 4.87, 5_918_000_000_000.0),
        "000001.SZ" to SeedQuote(12.68, 12.54, 5.48, 0.62, 230_000_000_000.0),
    )

    fun quote(symbol: String): Quote? {
        val security = Securities.all.firstOrNull { it.symbol == symbol } ?: return null
        val seed = seeds[symbol] ?: return null
        val random = DeterministicRandom(symbol.fold(17) { acc, char -> acc * 31 + char.code })
        val timeline = buildTimeline(seed.previousClose, seed.price, random)
        val kLines = buildKLines(seed.previousClose, random)
        return Quote(
            symbol = symbol,
            name = security.name,
            price = seed.price,
            previousClose = seed.previousClose,
            open = timeline.firstOrNull()?.price ?: seed.previousClose,
            high = timeline.maxOfOrNull { it.price } ?: seed.price,
            low = timeline.minOfOrNull { it.price } ?: seed.price,
            volume = 33_472.0 + random.nextDouble() * 40_000.0,
            amount = seed.price * (33_472.0 + random.nextDouble() * 40_000.0) * 100,
            turnoverRate = 0.27 + random.nextDouble() * 1.8,
            peTtm = seed.pe,
            pb = seed.pb,
            marketCap = seed.marketCap,
            timestamp = "2026-08-24 15:00",
            source = "离线演示数据",
            timeline = timeline,
            kLines = kLines,
        )
    }

    /**
     * 演示分时：全天 241 点（A 股 1 分钟粒度，09:30–15:00，跳过 11:30/13:00 午休）。
     * 历史版本只有 48 点、5 分钟间隔，而分时图横轴按"列表索引 → 全天 240 槽位"
     * 映射，48 个索引只铺满左段（用户反馈"分时只能走一半"）；且 1 分钟粒度下
     * 索引≈槽位，与真实腾讯分时的对齐方式一致。确定性随机种子保持不变。
     */
    private fun buildTimeline(start: Double, target: Double, random: DeterministicRandom): List<QuotePoint> {
        val points = mutableListOf<QuotePoint>()
        var current = start
        val count = 241
        repeat(count) { index ->
            val progress = (index + 1).toDouble() / count
            // 241 步的收敛系数：0.98^241 ≈ 0.8%，全天缓步收敛到目标价（48 点版
            // 的 0.08 在 241 步下约 40 分钟就贴住目标，剩余时段变成一条平线）。
            val pull = (target - current) * (0.018 + progress * 0.012)
            val noise = (random.nextDouble() - 0.5) * start * 0.0012
            current = max(start * 0.94, min(start * 1.06, current + pull + noise))
            // 交易分钟 → 墙钟时间：09:30–11:30 为 index 0–120，午休 90 分钟后
            // 13:01–15:00 为 index 121–240（连续写 570+index 会把下午写成 13:30 收盘）。
            val totalMinutes = 9 * 60 + 30 + index + if (index >= 121) 90 else 0
            points += QuotePoint(
                time = "${(totalMinutes / 60).toString().padStart(2, '0')}:${(totalMinutes % 60).toString().padStart(2, '0')}",
                price = if (index == count - 1) target else current,
                volume = 400.0 + random.nextDouble() * 1600.0,
            )
        }
        return points
    }

    private fun buildKLines(anchor: Double, random: DeterministicRandom): List<KLinePoint> {
        var close = anchor * 0.94
        return List(30) { index ->
            val open = close * (0.992 + random.nextDouble() * 0.016)
            close = open * (0.986 + random.nextDouble() * 0.028)
            val high = max(open, close) * (1.002 + random.nextDouble() * 0.012)
            val low = min(open, close) * (0.998 - random.nextDouble() * 0.012)
            KLinePoint(
                date = "08-${(index + 1).toString().padStart(2, '0')}",
                open = open,
                close = close,
                high = high,
                low = low,
                volume = 20_000.0 + random.nextDouble() * 60_000.0,
            )
        }
    }
}

class MockQuoteProvider : QuoteProvider, StockNewsProvider {
    override val mode: DataMode = DataMode.OFFLINE

    override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) = onResult(MockDataBank.quote(symbol))

    override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) =
        onResult(MockDataBank.quote(symbol)?.timeline.orEmpty())

    override fun stockNews(symbol: String, onResult: (List<NewsItem>) -> Unit) =
        onResult(news(symbol))

    override fun kLines(symbol: String, count: Int, interval: com.kuikly.stockchat.data.provider.KLineInterval, onResult: (List<KLinePoint>) -> Unit) =
        onResult(
            MockDataBank.quote(symbol)?.kLines
                ?.let { lines ->
                    when (interval) {
                        com.kuikly.stockchat.data.provider.KLineInterval.DAY -> lines
                        com.kuikly.stockchat.data.provider.KLineInterval.WEEK -> aggregate(lines, 5)
                        com.kuikly.stockchat.data.provider.KLineInterval.MONTH -> aggregate(lines, 20)
                    }
                }
                ?.takeLast(count)
                .orEmpty(),
        )

    private fun aggregate(lines: List<KLinePoint>, grouping: Int): List<KLinePoint> = lines.chunked(grouping).map { group ->
        KLinePoint(
            date = "${group.first().date}~${group.last().date}",
            open = group.first().open,
            close = group.last().close,
            high = group.maxOf { it.high },
            low = group.minOf { it.low },
            volume = group.sumOf { it.volume },
        )
    }
    /** 离线演示用的个股快讯：事实性标题，不带任何建议话术。 */
    fun news(symbol: String): List<NewsItem> {
        val security = Securities.all.firstOrNull { it.symbol == symbol } ?: return emptyList()
        val code = symbol.substringBefore('.')
        return listOf(
            NewsItem(
                id = "${code}-demo-1",
                title = "${security.name}：公司披露近期生产经营正常，无应披露而未披露事项",
                source = "离线演示数据",
                time = "2026-09-07 09:45:00",
                url = "",
                summary = "演示模式下展示的示例快讯，用于呈现新闻弹幕带的版式与节奏。",
            ),
            NewsItem(
                id = "${code}-demo-2",
                title = "${security.name}所在行业今日开盘表现与板块资金动向（示例）",
                source = "离线演示数据",
                time = "2026-09-07 10:12:00",
                url = "",
                summary = "演示模式下展示的示例快讯，仅用于演示，不代表任何真实信息。",
            ),
            NewsItem(
                id = "${code}-demo-3",
                title = "机构调研纪要显示${security.name}产能利用率保持稳定（示例）",
                source = "离线演示数据",
                time = "2026-09-06 16:30:00",
                url = "",
                summary = "演示模式下展示的示例快讯，仅用于演示，不代表任何真实信息。",
            ),
        )
    }
}

internal class DeterministicRandom(seed: Int) {
    private var state: Long = (seed.toLong() and 0x7fffffffL).coerceAtLeast(1L)

    fun nextDouble(): Double {
        state = (state * 1103515245L + 12345L) and 0x7fffffffL
        return state.toDouble() / 0x7fffffffL.toDouble()
    }
}
