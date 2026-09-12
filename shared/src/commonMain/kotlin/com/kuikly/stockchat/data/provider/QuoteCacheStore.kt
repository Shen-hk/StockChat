package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.storage.KeyValueStorage
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
    init {
        migrateFromLargeSeriesCache()
    }

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
            // 分时和 K 线已有独立的会话内缓存。把它们再塞进快照会让一条行情
            // 被重复序列化四次；真实模式下多只股票同时刷新会阻塞 Kuikly UI 线程。
            put("quote", quote.toSnapshotJson())
        }
    }

    override fun saveTimeline(symbol: String, points: List<QuotePoint>, savedAtMillis: Long) {
        // 逐分钟序列最多数百点，频繁落盘会在网络回调线程制造明显卡顿。
        // QuoteRepository 已持有五分钟会话缓存；冷启动时重新请求即可。
    }

    override fun saveKLines(symbol: String, interval: KLineInterval, points: List<KLinePoint>, savedAtMillis: Long) {
        // 同上：K 线只保留会话缓存，避免每个 interval 都重写整份 SharedPreferences JSON。
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
        existing.takeLast(MAX_SNAPSHOT_ROWS - 1).forEach(rows::put)
        rows.put(JSONObject().apply {
            put("cacheId", id)
            build()
        })
        preferences.setString(key, rows.toString())
    }

    /**
     * v1 把整日分时、日/周/月 K 线同时写入三份 JSON，且快照中又嵌套了一份，
     * 造成 SharedPreferences 文件持续膨胀。迁移不读取旧内容，防止启动时 JSON
     * 反序列化旧大对象再次卡住；旧键清空后下一次启动即可恢复轻量状态。
     */
    private fun migrateFromLargeSeriesCache() {
        if (preferences.getString(CACHE_VERSION_KEY) == CACHE_VERSION) return
        preferences.setString(LEGACY_SNAPSHOT_KEY, "")
        preferences.setString(LEGACY_TIMELINE_KEY, "")
        preferences.setString(LEGACY_KLINE_KEY, "")
        preferences.setString(CACHE_VERSION_KEY, CACHE_VERSION)
    }

    companion object {
        private const val CACHE_VERSION_KEY = "stockchat_quote_cache_version"
        private const val CACHE_VERSION = "2"
        private const val LEGACY_SNAPSHOT_KEY = "stockchat_quote_snapshots_v1"
        private const val LEGACY_TIMELINE_KEY = "stockchat_quote_timelines_v1"
        private const val LEGACY_KLINE_KEY = "stockchat_quote_klines_v1"
        private const val SNAPSHOT_KEY = "stockchat_quote_snapshots_v2"
        private const val TIMELINE_KEY = "stockchat_quote_timelines_v2"
        private const val KLINE_KEY = "stockchat_quote_klines_v2"
        private const val MAX_SNAPSHOT_ROWS = 8
    }
}

private fun Quote.toSnapshotJson(): JSONObject = JSONObject().apply {
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
            if (point.amount > 0.0) put("amount", point.amount)
        })
    }
}

private fun JSONArray.toQuotePoints(): List<QuotePoint> = buildList {
    repeat(length()) { index ->
        val row = optJSONObject(index) ?: return@repeat
        add(QuotePoint(row.optString("time"), row.double("price"), row.double("volume"), row.double("amount")))
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
