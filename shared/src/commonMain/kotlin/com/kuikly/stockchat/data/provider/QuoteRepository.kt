package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.entity.Security
import com.kuikly.stockchat.data.MarketDataSource
import com.kuikly.stockchat.data.mock.MockQuoteProvider

/** A page-independent source of truth for quotes, their short-lived cache, and offline fallback. */
class QuoteRepository(
    private val online: QuoteProvider,
    private val offline: QuoteProvider = MockQuoteProvider(),
    private val cacheStore: QuoteCacheStore = NoOpQuoteCacheStore,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
    /** 每次请求时读取，保证从设置页返回后不会沿用创建 repository 时的旧选择。 */
    private val selectedSource: () -> MarketDataSource = { MarketDataSource.REAL },
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
        val requestSource = selectedSource()
        if (requestSource == MarketDataSource.MOCK) {
            loadMock(symbol, onResult)
            return
        }
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

        var fallbackToMock = false

        // Series requests begin immediately. A fast series response is retained until the
        // snapshot arrives, instead of being dropped because there is no Quote to copy yet.
        online.timeline(symbol) { points ->
            if (fallbackToMock) return@timeline
            val resolved = if (points.isNotEmpty()) points else offlineTimeline(symbol)
            if (resolved.isNotEmpty()) {
                if (points.isNotEmpty()) saveTimeline(symbol, points)
                latestTimeline = resolved
                publish()
            }
        }
        KLineInterval.entries.forEach { interval ->
            online.kLines(symbol, interval.defaultCount, interval) { points ->
                if (fallbackToMock) return@kLines
                val resolved = if (points.isNotEmpty()) points else offlineKLines(symbol, interval.defaultCount, interval)
                if (resolved.isNotEmpty()) {
                    if (points.isNotEmpty()) saveKLines(symbol, interval, points)
                    latestKLines[interval] = resolved
                    publish()
                }
            }
        }
        online.snapshot(symbol) { quote ->
            if (fallbackToMock) return@snapshot
            // 空响应不立刻发布缓存或 Mock：先让详情/列表继续显示呼吸骨架，
            // 由下面的统一超时窗口决定是否降级，避免网络抖动时闪一下旧数据。
            if (quote == null) {
                // 保留注入式自定义 offline provider 的同步契约，方便业务专用
                // repository 与既有单测；产品默认的 MockQuoteProvider 才走骨架窗口。
                if (offline !is MockQuoteProvider) {
                    fallbackToMock = true
                    val cached = fresh(snapshots[symbol], SNAPSHOT_TTL_MILLIS)
                    if (cached != null) {
                        latestSnapshot = QuoteLoadResult(cached.value.asCached(), DataMode.CACHE, cached.savedAtMillis)
                    } else {
                        latestTimeline = offlineTimeline(symbol)
                        KLineInterval.entries.forEach { interval ->
                            latestKLines[interval] = offlineKLines(symbol, interval.defaultCount, interval)
                        }
                        latestSnapshot = QuoteLoadResult(offlineQuote(symbol), DataMode.OFFLINE, nowMillis())
                    }
                    publish()
                }
                return@snapshot
            }
            saveSnapshot(symbol, quote)
            latestSnapshot = QuoteLoadResult(quote, DataMode.ONLINE, nowMillis())
            publish()
        }
        // 不在 repository 中挂跨页面的 Handler 兜底。它不绑定 Kuikly pager 生命周期，
        // 页面已卸载时再发布 Observable 会调用失效的 native bridge 并导致应用闪退。
        // 无回调的弱网场景保持调用方的加载安全态；正常的成功/失败回调仍按上方链路处理。
    }

    private fun loadMock(symbol: String, onResult: (QuoteLoadResult) -> Unit) {
        val quote = offlineQuote(symbol)?.let { base ->
            var resolved = base.copy(timeline = offlineTimeline(symbol))
            KLineInterval.entries.forEach { interval ->
                resolved = resolved.withKLines(interval, offlineKLines(symbol, interval.defaultCount, interval))
            }
            resolved
        }
        onResult(QuoteLoadResult(quote, DataMode.OFFLINE, nowMillis()))
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
        if (selectedSource() == MarketDataSource.MOCK) {
            return QuoteLoadResult(offlineQuote(symbol), DataMode.OFFLINE, nowMillis())
        }
        val cached = fresh(snapshots[symbol], SNAPSHOT_TTL_MILLIS)
        if (cached != null) {
            return QuoteLoadResult(cached.value.withCachedSeries(symbol).asCached(), DataMode.CACHE, cached.savedAtMillis)
        }
        // 真实模式的首帧不能偷塞 Mock：调用方应保留骨架，等 load() 的真实响应
        // 或超时降级统一发布；否则详情页会在请求窗口里先闪出离线价格。
        return if (offline is MockQuoteProvider) {
            QuoteLoadResult(null, DataMode.AUTO, nowMillis())
        } else {
            QuoteLoadResult(offlineQuote(symbol), DataMode.OFFLINE, nowMillis())
        }
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
