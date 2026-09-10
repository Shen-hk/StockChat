package com.kuikly.stockchat.data.provider

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * Exchange-level intraday layout.  This is deliberately keyed by market, never
 * by an individual security: every symbol returned by remote search follows the
 * same parser and chart geometry.
 */
data class MarketTimelineSpec(
    val slotCount: Int,
    val labels: List<Pair<Int, String>>,
    private val includesTime: (String) -> Boolean,
) {
    fun contains(time: String): Boolean = includesTime(time)

    companion object {
        fun forSymbol(symbol: String): MarketTimelineSpec = when (symbol.substringAfterLast('.', "").uppercase()) {
            "HK" -> HONG_KONG
            "US" -> UNITED_STATES
            else -> MAINLAND_CHINA
        }

        private val MAINLAND_CHINA = MarketTimelineSpec(
            slotCount = 241,
            labels = listOf(0 to "09:30", 60 to "10:30", 120 to "11:30/13:00", 180 to "14:00", 240 to "15:00"),
        ) { time ->
            (time >= "09:30" && time <= "11:30") || (time >= "13:00" && time <= "15:00")
        }
        private val HONG_KONG = MarketTimelineSpec(
            slotCount = 332,
            labels = listOf(0 to "09:30", 90 to "11:00", 150 to "12:00/13:00", 240 to "14:30", 331 to "16:00"),
        ) { time ->
            (time >= "09:30" && time <= "12:00") || (time >= "13:00" && time <= "16:00")
        }
        private val UNITED_STATES = MarketTimelineSpec(
            slotCount = 391,
            labels = listOf(0 to "09:30", 90 to "11:00", 195 to "12:45", 300 to "14:30", 390 to "16:00"),
        ) { time -> time >= "09:30" && time <= "16:00" }
    }
}

object TencentQuoteParser {
    fun parseSnapshot(root: JSONObject, symbol: String): Quote? {
        val code = remoteCode(symbol)
        val marketData = root.optJSONObject("data")?.optJSONObject(code) ?: return null
        val quoteArray = marketData.optJSONObject("qt")?.optJSONArray(code) ?: return null
        if (quoteArray.length() < 6) return null
        val kLines = parseKLines(marketData.optJSONArray(KLineInterval.DAY.responseField) ?: marketData.optJSONArray(KLineInterval.DAY.fallbackField))
        val price = quoteArray.number(3)
        val previousClose = quoteArray.number(4)
        return Quote(
            symbol = symbol,
            name = quoteArray.text(1).ifEmpty { symbol },
            price = price,
            previousClose = previousClose,
            open = quoteArray.number(5),
            high = quoteArray.number(33),
            low = quoteArray.number(34),
            volume = quoteArray.number(6),
            amount = quoteArray.number(37) * 10_000.0,
            turnoverRate = quoteArray.number(38),
            peTtm = quoteArray.number(39),
            pb = quoteArray.number(46),
            marketCap = quoteArray.number(45) * 100_000_000.0,
            timestamp = formatTimestamp(quoteArray.text(30)),
            source = "腾讯证券行情",
            kLines = kLines,
        )
    }

    /**
     * 腾讯分时行格式：`HHMM 价 累计量(手) 累计额(元)`（2026-09-07 实测）。
     * 量额均为当日累计，逐分钟差分为单分钟值；缺额字段（旧缓存格式）时 amount 记 0，
     * 均价由消费方走近似口径（Σ price×volume×100）。
     */
    fun parseTimeline(root: JSONObject, symbol: String): List<QuotePoint> {
        val code = remoteCode(symbol)
        val rows = root.optJSONObject("data")
            ?.optJSONObject(code)
            ?.optJSONObject("data")
            ?.optJSONArray("data") ?: return emptyList()
        var prevVolume = 0.0
        var prevAmount = 0.0
        val timelineSpec = MarketTimelineSpec.forSymbol(symbol)
        return buildList {
            repeat(rows.length()) { index ->
                val fields = rows.text(index).trim().split(Regex("\\s+"))
                if (fields.size >= 3) {
                    val time = fields[0].let { raw ->
                        if (raw.length == 4) "${raw.take(2)}:${raw.takeLast(2)}" else raw
                    }
                    // 收盘后接口会追加冻结填充点；按市场的统一交易时段过滤。
                    if (!timelineSpec.contains(time)) return@repeat
                    val price = fields[1].toDoubleOrNull() ?: return@repeat
                    val cumVolume = fields[2].toDoubleOrNull() ?: 0.0
                    val cumAmount = fields.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                    val hasAmount = fields.size >= 4
                    val volume = (cumVolume - prevVolume).coerceAtLeast(0.0)
                    val amount = if (hasAmount) (cumAmount - prevAmount).coerceAtLeast(0.0) else 0.0
                    add(QuotePoint(time, price, volume, amount))
                    prevVolume = cumVolume
                    prevAmount = cumAmount
                }
            }
        }
    }

    fun parseKLines(root: JSONObject, symbol: String, interval: KLineInterval): List<KLinePoint> {
        val code = remoteCode(symbol)
        val marketData = root.optJSONObject("data")?.optJSONObject(code) ?: return emptyList()
        return parseKLines(
            marketData.optJSONArray(interval.responseField) ?: marketData.optJSONArray(interval.fallbackField),
        )
    }

    fun remoteCode(symbol: String): String {
        val parts = symbol.uppercase().split('.')
        val digits = parts.firstOrNull().orEmpty()
        val market = parts.getOrNull(1)
        return when (market) {
            "SH" -> "sh$digits"
            "SZ" -> "sz$digits"
            "HK" -> "hk$digits"
            "US" -> "us$digits"
            else -> digits.lowercase()
        }
    }

    private fun parseKLines(rows: JSONArray?): List<KLinePoint> {
        if (rows == null) return emptyList()
        return buildList {
            repeat(rows.length()) { index ->
                val row = rows.optJSONArray(index) ?: return@repeat
                if (row.length() >= 6) {
                    add(
                        KLinePoint(
                            date = row.text(0),
                            open = row.number(1),
                            close = row.number(2),
                            high = row.number(3),
                            low = row.number(4),
                            volume = row.number(5),
                        ),
                    )
                }
            }
        }
    }

    private fun formatTimestamp(raw: String): String = if (raw.length >= 12) {
        "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)} ${raw.substring(8, 10)}:${raw.substring(10, 12)}"
    } else raw

    private fun JSONArray.text(index: Int): String = optString(index, "").orEmpty()
    private fun JSONArray.number(index: Int): Double = text(index).toDoubleOrNull() ?: 0.0
}

class TencentQuoteProvider(override val pagerId: String) : QuoteProvider, PagerScope {
    override val mode: DataMode = DataMode.ONLINE
    private val network: NetworkModule get() = getPager().acquireModule(NetworkModule.MODULE_NAME)

    override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) {
        val code = TencentQuoteParser.remoteCode(symbol)
        val url = kLineUrl(code, KLineInterval.DAY, KLineInterval.DAY.defaultCount)
        network.requestGet(url, JSONObject()) { data, success, _, _ ->
            onResult(if (success) TencentQuoteParser.parseSnapshot(data, symbol) else null)
        }
    }

    override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) {
        val code = TencentQuoteParser.remoteCode(symbol)
        val url = "https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=$code"
        network.requestGet(url, JSONObject()) { data, success, _, _ ->
            onResult(if (success) TencentQuoteParser.parseTimeline(data, symbol) else emptyList())
        }
    }

    override fun kLines(symbol: String, count: Int, interval: KLineInterval, onResult: (List<KLinePoint>) -> Unit) {
        val code = TencentQuoteParser.remoteCode(symbol)
        network.requestGet(kLineUrl(code, interval, count), JSONObject()) { data, success, _, _ ->
            onResult(if (success) TencentQuoteParser.parseKLines(data, symbol, interval).takeLast(count) else emptyList())
        }
    }

    private fun kLineUrl(code: String, interval: KLineInterval, count: Int): String =
        "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$code,${interval.requestPeriod},,,$count,qfq"
}

/**
 * Real index-only market fallback. EastMoney remains the primary source for
 * breadth and sectors; when it is unavailable this provider prevents the
 * market page from degrading into a completely empty screen.
 */
class TencentIndexOverviewProvider(pagerId: String) : MarketOverviewProvider {
    private val quotes = TencentQuoteProvider(pagerId)

    override fun overview(onResult: (MarketOverview?) -> Unit) {
        val symbols = listOf(
            "000001.SH", "399001.SZ", "399006.SZ", "000688.SH", "899050.SZ",
            "000300.SH", "000016.SH", "000905.SH", "HSI.HK",
        )
        var pending = symbols.size
        val values = mutableListOf<MarketIndex>()
        symbols.forEach { symbol ->
            quotes.snapshot(symbol) { quote ->
                quote?.takeIf { it.price > 0.0 && it.previousClose > 0.0 }?.let {
                    values += MarketIndex(
                        code = symbol.substringBefore('.'),
                        name = it.name.ifBlank { symbol },
                        price = it.price,
                        changePercent = it.changePercent,
                        high = it.high.takeIf { value -> value > 0.0 },
                        low = it.low.takeIf { value -> value > 0.0 },
                    )
                }
                pending--
                if (pending == 0) {
                    onResult(
                        values.takeIf { it.isNotEmpty() }?.let { indices ->
                            MarketOverview(
                                indices = indices,
                                risingCount = 0,
                                fallingCount = 0,
                                flatCount = 0,
                                limitUpCount = 0,
                                limitDownCount = 0,
                                sectors = emptyList(),
                                stamp = SourceStamp(
                                    source = "腾讯证券公开行情（指数快照）",
                                    asOf = indices.firstOrNull()?.let { "实时" } ?: "",
                                    tier = SourceTier.MARKET_DATA,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun hotspots(onResult: (HotspotSnapshot?) -> Unit) = onResult(null)
}

/**
 * 行情降级链：在线 → 会话内缓存 → 终点。
 * 真实模式（DataSourceConfig.USE_REAL_MARKET_DATA = true）终点为空；模拟模式终点回落 MockQuoteProvider（原状态）。
 */
class FallbackQuoteProvider(pagerId: String) : QuoteProvider {
    private val online = TencentQuoteProvider(pagerId)
    private val offline: QuoteProvider? =
        if (com.kuikly.stockchat.data.config.DataSourceConfig.USE_REAL_MARKET_DATA) null else com.kuikly.stockchat.data.mock.MockQuoteProvider()
    private val cache = mutableMapOf<String, Quote>()
    private var currentMode = DataMode.AUTO
    override val mode: DataMode get() = currentMode

    override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) {
        online.snapshot(symbol) { quote ->
            if (quote != null) {
                currentMode = DataMode.ONLINE
                cache[symbol] = quote
                onResult(quote)
            } else {
                val cached = cache[symbol]
                currentMode = if (cached != null) DataMode.CACHE else DataMode.OFFLINE
                onResult(cached ?: run {
                    var fallback: Quote? = null
                    offline?.snapshot(symbol) { fallback = it }
                    fallback
                })
            }
        }
    }

    override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) {
        online.timeline(symbol) { points ->
            if (points.isNotEmpty()) onResult(points)
            else if (offline != null) offline.timeline(symbol, onResult)
            else onResult(emptyList())
        }
    }

    override fun kLines(symbol: String, count: Int, interval: KLineInterval, onResult: (List<KLinePoint>) -> Unit) {
        online.kLines(symbol, count, interval) { points ->
            if (points.isNotEmpty()) onResult(points)
            else if (offline != null) offline.kLines(symbol, count, interval, onResult)
            else onResult(emptyList())
        }
    }
}
