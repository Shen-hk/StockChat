package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.entity.Security

/** A page-independent source of truth for quotes, their short-lived cache, and offline fallback. */
class QuoteRepository(
    private val online: QuoteProvider,
    // 数据源开关：真实模式降级终点 = NullQuoteProvider（空态）；模拟模式 = MockQuoteProvider（原状态）。
    private val offline: QuoteProvider = if (com.kuikly.stockchat.data.config.DataSourceConfig.USE_REAL_MARKET_DATA) {
        NullQuoteProvider
    } else {
        com.kuikly.stockchat.data.mock.MockQuoteProvider()
    },
    private val cacheStore: QuoteCacheStore = NoOpQuoteCacheStore,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    companion object {
        const val SNAPSHOT_TTL_MILLIS = 60_000L
        const val SERIES_TTL_MILLIS = 5 * 60_000L
    }

    private data class CacheEntry<T>(val value: T, val savedAtMillis: Long)

    private val snapshots = mutableMapOf<String, CacheEntry<Quote>>()
    private val timelines = mutableMapOf<String, CacheEntry<List<QuotePoint>>>()
    private data class SeriesKey(val symbol: String, val interval: KLineInterval)
    private val kLines = mutableMapOf<SeriesKey, CacheEntry<List<KLinePoint>>>()

    init {
        restoreCache()
    }

    /**
     * Starts snapshot, timeline and all K-line requests together.  The prior implementation
     * waited for the snapshot callback before issuing the four series requests, which added a
     * full network round trip to detail-page chart rendering.  Results are accumulated by
     * resource type and published whenever the snapshot and any newer series are available.
     */
    fun load(symbol: String, onResult: (QuoteLoadResult) -> Unit) {
        var latestSnapshot: QuoteLoadResult? = null
        var latestTimeline = fresh(timelines[symbol], SERIES_TTL_MILLIS)?.value.orEmpty()
        val latestKLines = KLineInterval.entries.associateWithTo(mutableMapOf()) { interval ->
            fresh(kLines[SeriesKey(symbol, interval)], SERIES_TTL_MILLIS)?.value.orEmpty()
        }

        fun publish() {
            val snapshot = latestSnapshot ?: return
            val base = snapshot.quote ?: return
            var quote = base
            if (latestTimeline.isNotEmpty()) quote = quote.copy(timeline = latestTimeline)
            KLineInterval.entries.forEach { interval ->
                val points = latestKLines[interval].orEmpty()
                if (points.isNotEmpty()) quote = quote.withKLines(interval, points)
            }
            onResult(snapshot.copy(quote = quote))
        }

        // Series requests begin immediately. A fast series response is retained until the
        // snapshot arrives, instead of being dropped because there is no Quote to copy yet.
        online.timeline(symbol) { points ->
            val resolved = if (points.isNotEmpty()) points else offlineTimeline(symbol)
            if (resolved.isNotEmpty()) {
                if (points.isNotEmpty()) saveTimeline(symbol, points)
                latestTimeline = resolved
                publish()
            }
        }
        KLineInterval.entries.forEach { interval ->
            online.kLines(symbol, interval.defaultCount, interval) { points ->
                val resolved = if (points.isNotEmpty()) points else offlineKLines(symbol, interval.defaultCount, interval)
                if (resolved.isNotEmpty()) {
                    if (points.isNotEmpty()) saveKLines(symbol, interval, points)
                    latestKLines[interval] = resolved
                    publish()
                }
            }
        }
        snapshot(symbol) { result ->
            latestSnapshot = result
            publish()
        }
    }

    private fun offlineTimeline(symbol: String): List<QuotePoint> {
        var points: List<QuotePoint> = emptyList()
        offline.timeline(symbol) { points = it }
        return points
    }

    private fun offlineKLines(symbol: String, count: Int, interval: KLineInterval): List<KLinePoint> {
        var points: List<KLinePoint> = emptyList()
        offline.kLines(symbol, count, interval) { points = it }
        return points
    }

    /** Uses a fresh memory value when available, otherwise exposes clearly-labelled offline demo data. */
    fun cachedOrOffline(symbol: String): Quote? =
        cachedOrOfflineResult(symbol).quote

    /**
     * 最新快照（在线→缓存→离线降级，保证回调一次），供对话上下文注入等轻量场景。
     * 与 [load] 不同：不触发分时/K线刷新；快照结果照常入缓存供后续复用。
     */
    fun snapshotForContext(symbol: String, onResult: (QuoteLoadResult) -> Unit) {
        snapshot(symbol, onResult)
    }

    fun search(query: String, limit: Int = 20): List<Security> =
        Securities.search(query, limit)

    fun cachedOrOfflineResult(symbol: String): QuoteLoadResult {
        val cached = fresh(snapshots[symbol], SNAPSHOT_TTL_MILLIS)
        if (cached != null) {
            return QuoteLoadResult(cached.value.withCachedSeries(symbol).asCached(), DataMode.CACHE, cached.savedAtMillis)
        }
        return QuoteLoadResult(offlineQuote(symbol), DataMode.OFFLINE, nowMillis())
    }

    private fun snapshot(symbol: String, onResult: (QuoteLoadResult) -> Unit) {
        online.snapshot(symbol) { quote ->
            if (quote != null) {
                saveSnapshot(symbol, quote)
                onResult(QuoteLoadResult(quote, DataMode.ONLINE, nowMillis()))
                return@snapshot
            }
            val cached = fresh(snapshots[symbol], SNAPSHOT_TTL_MILLIS)
            if (cached != null) {
                onResult(QuoteLoadResult(cached.value.asCached(), DataMode.CACHE, cached.savedAtMillis))
            } else {
                val fallback = offlineQuote(symbol)
                onResult(QuoteLoadResult(fallback, DataMode.OFFLINE, nowMillis()))
            }
        }
    }

    private fun Quote.withCachedSeries(symbol: String): Quote = copy(
        timeline = fresh(this@QuoteRepository.timelines[symbol], SERIES_TTL_MILLIS)?.value ?: timeline,
        kLines = fresh(this@QuoteRepository.kLines[SeriesKey(symbol, KLineInterval.DAY)], SERIES_TTL_MILLIS)?.value ?: kLines,
        weekKLines = fresh(this@QuoteRepository.kLines[SeriesKey(symbol, KLineInterval.WEEK)], SERIES_TTL_MILLIS)?.value ?: weekKLines,
        monthKLines = fresh(this@QuoteRepository.kLines[SeriesKey(symbol, KLineInterval.MONTH)], SERIES_TTL_MILLIS)?.value ?: monthKLines,
    )

    private fun Quote.withKLines(interval: KLineInterval, points: List<KLinePoint>): Quote = when (interval) {
        KLineInterval.DAY -> copy(kLines = points)
        KLineInterval.WEEK -> copy(weekKLines = points)
        KLineInterval.MONTH -> copy(monthKLines = points)
    }

    private fun offlineQuote(symbol: String): Quote? {
        var quote: Quote? = null
        offline.snapshot(symbol) { quote = it }
        return quote
    }

    private fun Quote.asCached(): Quote = copy(source = "缓存行情（$source）")

    private fun <T> fresh(entry: CacheEntry<T>?, ttlMillis: Long): CacheEntry<T>? =
        entry?.takeIf { nowMillis() - it.savedAtMillis <= ttlMillis }

    private fun saveSnapshot(symbol: String, quote: Quote) {
        val savedAtMillis = nowMillis()
        snapshots[symbol] = CacheEntry(quote, savedAtMillis)
        cacheStore.saveSnapshot(symbol, quote, savedAtMillis)
    }

    private fun saveTimeline(symbol: String, points: List<QuotePoint>) {
        val savedAtMillis = nowMillis()
        timelines[symbol] = CacheEntry(points, savedAtMillis)
        cacheStore.saveTimeline(symbol, points, savedAtMillis)
    }

    private fun saveKLines(symbol: String, interval: KLineInterval, points: List<KLinePoint>) {
        val savedAtMillis = nowMillis()
        kLines[SeriesKey(symbol, interval)] = CacheEntry(points, savedAtMillis)
        cacheStore.saveKLines(symbol, interval, points, savedAtMillis)
    }

    private fun restoreCache() {
        cacheStore.loadSnapshots().forEach { entry ->
            snapshots[entry.symbol] = CacheEntry(entry.value, entry.savedAtMillis)
        }
        cacheStore.loadTimelines().forEach { entry ->
            timelines[entry.symbol] = CacheEntry(entry.value, entry.savedAtMillis)
        }
        cacheStore.loadKLines().forEach { entry ->
            kLines[SeriesKey(entry.symbol, entry.interval)] = CacheEntry(entry.value, entry.savedAtMillis)
        }
    }
}

data class QuoteLoadResult(
    val quote: Quote?,
    val mode: DataMode,
    val updatedAtMillis: Long,
)
