package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.mock.MockQuoteProvider
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

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

    fun parseTimeline(root: JSONObject, symbol: String): List<QuotePoint> {
        val code = remoteCode(symbol)
        val rows = root.optJSONObject("data")
            ?.optJSONObject(code)
            ?.optJSONObject("data")
            ?.optJSONArray("data") ?: return emptyList()
        return buildList {
            repeat(rows.length()) { index ->
                val fields = rows.text(index).trim().split(Regex("\\s+"))
                if (fields.size >= 3) {
                    val time = fields[0].let { raw ->
                        if (raw.length == 4) "${raw.take(2)}:${raw.takeLast(2)}" else raw
                    }
                    val price = fields[1].toDoubleOrNull() ?: return@repeat
                    add(QuotePoint(time, price, fields[2].toDoubleOrNull() ?: 0.0))
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

class FallbackQuoteProvider(pagerId: String) : QuoteProvider {
    private val online = TencentQuoteProvider(pagerId)
    private val offline = MockQuoteProvider()
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
                    offline.snapshot(symbol) { fallback = it }
                    fallback
                })
            }
        }
    }

    override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) {
        online.timeline(symbol) { points ->
            if (points.isNotEmpty()) onResult(points)
            else offline.timeline(symbol, onResult)
        }
    }

    override fun kLines(symbol: String, count: Int, interval: KLineInterval, onResult: (List<KLinePoint>) -> Unit) {
        online.kLines(symbol, count, interval) { points ->
            if (points.isNotEmpty()) onResult(points)
            else offline.kLines(symbol, count, interval, onResult)
        }
    }
}
