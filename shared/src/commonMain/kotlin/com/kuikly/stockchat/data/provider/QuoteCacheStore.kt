package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

interface QuoteCacheStore {
    fun loadSnapshots(): List<StoredQuote<Quote>> = emptyList()
    fun loadTimelines(): List<StoredQuote<List<QuotePoint>>> = emptyList()
    fun loadKLines(): List<StoredKLines> = emptyList()
    fun saveSnapshot(symbol: String, quote: Quote, savedAtMillis: Long) {}
    fun saveTimeline(symbol: String, points: List<QuotePoint>, savedAtMillis: Long) {}
    fun saveKLines(symbol: String, interval: KLineInterval, points: List<KLinePoint>, savedAtMillis: Long) {}
}

object NoOpQuoteCacheStore : QuoteCacheStore

data class StoredQuote<T>(
    val symbol: String,
    val value: T,
    val savedAtMillis: Long,
)

data class StoredKLines(
    val symbol: String,
    val interval: KLineInterval,
    val value: List<KLinePoint>,
    val savedAtMillis: Long,
)

class SharedPreferencesQuoteCacheStore(
    private val preferences: KeyValueStorage,
) : QuoteCacheStore {
    constructor(pagerId: String) : this(PagerKeyValueStorage(pagerId))

    override fun loadSnapshots(): List<StoredQuote<Quote>> =
        readRows(SNAPSHOT_KEY).mapNotNull { row ->
            val symbol = row.optString("symbol")
            val quote = row.optJSONObject("quote")?.toQuote()
            if (symbol.isNotBlank() && quote != null) StoredQuote(symbol, quote, row.long("savedAtMillis")) else null
        }

    override fun loadTimelines(): List<StoredQuote<List<QuotePoint>>> =
        readRows(TIMELINE_KEY).mapNotNull { row ->
            val symbol = row.optString("symbol")
            val points = row.optJSONArray("points")?.toQuotePoints().orEmpty()
            if (symbol.isNotBlank() && points.isNotEmpty()) StoredQuote(symbol, points, row.long("savedAtMillis")) else null
        }

    override fun loadKLines(): List<StoredKLines> =
        readRows(KLINE_KEY).mapNotNull { row ->
            val symbol = row.optString("symbol")
            val interval = runCatching { KLineInterval.valueOf(row.optString("interval")) }.getOrNull()
            val points = row.optJSONArray("points")?.toKLinePoints().orEmpty()
            if (symbol.isNotBlank() && interval != null && points.isNotEmpty()) {
                StoredKLines(symbol, interval, points, row.long("savedAtMillis"))
            } else {
                null
            }
        }

    override fun saveSnapshot(symbol: String, quote: Quote, savedAtMillis: Long) {
        upsert(SNAPSHOT_KEY, symbol) {
            put("symbol", symbol)
            put("savedAtMillis", savedAtMillis)
            put("quote", quote.toJson())
        }
    }

    override fun saveTimeline(symbol: String, points: List<QuotePoint>, savedAtMillis: Long) {
        upsert(TIMELINE_KEY, symbol) {
            put("symbol", symbol)
            put("savedAtMillis", savedAtMillis)
            put("points", points.toQuotePointArray())
        }
    }

    override fun saveKLines(symbol: String, interval: KLineInterval, points: List<KLinePoint>, savedAtMillis: Long) {
        upsert(KLINE_KEY, "$symbol:${interval.name}") {
            put("symbol", symbol)
            put("interval", interval.name)
            put("savedAtMillis", savedAtMillis)
            put("points", points.toKLinePointArray())
        }
    }

    private fun readRows(key: String): List<JSONObject> {
        val raw = preferences.getString(key)
        if (raw.isEmpty()) return emptyList()
        return try {
            val rows = JSONArray(raw)
            buildList {
                repeat(rows.length()) { index ->
                    rows.optJSONObject(index)?.let(::add)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun upsert(key: String, id: String, build: JSONObject.() -> Unit) {
        val rows = JSONArray()
        val existing = readRows(key).filterNot { it.optString("cacheId") == id }
        existing.takeLast(MAX_ROWS - 1).forEach(rows::put)
        rows.put(JSONObject().apply {
            put("cacheId", id)
            build()
        })
        preferences.setString(key, rows.toString())
    }

    companion object {
        private const val SNAPSHOT_KEY = "stockchat_quote_snapshots_v1"
        private const val TIMELINE_KEY = "stockchat_quote_timelines_v1"
        private const val KLINE_KEY = "stockchat_quote_klines_v1"
        private const val MAX_ROWS = 24
    }
}

private fun Quote.toJson(): JSONObject = JSONObject().apply {
    put("symbol", symbol)
    put("name", name)
    put("price", price)
    put("previousClose", previousClose)
    put("open", open)
    put("high", high)
    put("low", low)
    put("volume", volume)
    put("amount", amount)
    put("turnoverRate", turnoverRate)
    put("peTtm", peTtm)
    put("pb", pb)
    put("marketCap", marketCap)
    put("timestamp", timestamp)
    put("source", source)
    put("timeline", timeline.toQuotePointArray())
    put("kLines", kLines.toKLinePointArray())
    put("weekKLines", weekKLines.toKLinePointArray())
    put("monthKLines", monthKLines.toKLinePointArray())
}

private fun JSONObject.toQuote(): Quote? {
    val symbol = optString("symbol")
    if (symbol.isBlank()) return null
    return Quote(
        symbol = symbol,
        name = optString("name").ifEmpty { symbol },
        price = double("price"),
        previousClose = double("previousClose"),
        open = double("open"),
        high = double("high"),
        low = double("low"),
        volume = double("volume"),
        amount = double("amount"),
        turnoverRate = double("turnoverRate"),
        peTtm = double("peTtm"),
        pb = double("pb"),
        marketCap = double("marketCap"),
        timestamp = optString("timestamp"),
        source = optString("source").ifEmpty { "缓存行情" },
        timeline = optJSONArray("timeline")?.toQuotePoints().orEmpty(),
        kLines = optJSONArray("kLines")?.toKLinePoints().orEmpty(),
        weekKLines = optJSONArray("weekKLines")?.toKLinePoints().orEmpty(),
        monthKLines = optJSONArray("monthKLines")?.toKLinePoints().orEmpty(),
    )
}

private fun List<QuotePoint>.toQuotePointArray(): JSONArray = JSONArray().apply {
    forEach { point ->
        put(JSONObject().apply {
            put("time", point.time)
            put("price", point.price)
            put("volume", point.volume)
        })
    }
}

private fun JSONArray.toQuotePoints(): List<QuotePoint> = buildList {
    repeat(length()) { index ->
        val row = optJSONObject(index) ?: return@repeat
        add(QuotePoint(row.optString("time"), row.double("price"), row.double("volume")))
    }
}

private fun List<KLinePoint>.toKLinePointArray(): JSONArray = JSONArray().apply {
    forEach { point ->
        put(JSONObject().apply {
            put("date", point.date)
            put("open", point.open)
            put("close", point.close)
            put("high", point.high)
            put("low", point.low)
            put("volume", point.volume)
        })
    }
}

private fun JSONArray.toKLinePoints(): List<KLinePoint> = buildList {
    repeat(length()) { index ->
        val row = optJSONObject(index) ?: return@repeat
        add(
            KLinePoint(
                date = row.optString("date"),
                open = row.double("open"),
                close = row.double("close"),
                high = row.double("high"),
                low = row.double("low"),
                volume = row.double("volume"),
            ),
        )
    }
}

private fun JSONObject.double(key: String): Double = optString(key).toDoubleOrNull() ?: 0.0
private fun JSONObject.long(key: String): Long = optString(key).toLongOrNull() ?: 0L
