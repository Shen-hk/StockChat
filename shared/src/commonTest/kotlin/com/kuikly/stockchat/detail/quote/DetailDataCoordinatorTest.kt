package com.kuikly.stockchat.detail.quote

import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteLoadResult
import com.kuikly.stockchat.data.provider.QuotePoint
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.detail.quote.state.DetailDataCoordinator
import com.kuikly.stockchat.detail.quote.state.DetailDataEffect
import com.kuikly.stockchat.detail.quote.state.DetailDataScheduledTask
import com.kuikly.stockchat.detail.quote.state.DetailDataScheduler
import com.kuikly.stockchat.detail.quote.state.DetailInsightPort
import com.kuikly.stockchat.detail.quote.state.DetailNewsPort
import com.kuikly.stockchat.detail.quote.state.DetailQuotePort
import com.kuikly.stockchat.detail.quote.state.PlainDetailDataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailDataCoordinatorTest {
    @Test
    fun prefetchHitSkipsLoadingAndEmitsPrefetchApplied() {
        val f = fixture()
        val prefetched = quoteWithTimeline("600519.SH", price = 10.0, previousClose = 9.0)

        f.coordinator.start("600519.SH", prefetched)

        assertFalse(f.state.quoteLoading)
        assertFalse(f.state.chartDataLoading)
        assertEquals("预加载行情（可能延迟）", f.state.dataModeLabel)
        assertEquals(prefetched, f.state.quote)
        assertEquals<List<DetailDataEffect>>(listOf(DetailDataEffect.PrefetchApplied(prefetched)), f.effects)
    }

    @Test
    fun cachedOrOfflineHitAppliesQuoteWithoutEffect() {
        val cached = quoteWithTimeline("600519.SH", price = 8.0, previousClose = 8.0)
        val f = fixture(cachedQuote = cached)

        f.coordinator.start("600519.SH", prefetched = null)

        assertEquals(cached, f.state.quote)
        assertFalse(f.state.quoteLoading)
        assertTrue(f.effects.isEmpty(), "cachedOrOffline hit must not emit an effect, got ${f.effects}")
    }

    @Test
    fun successfulLoadClearsLoadingAndEmitsQuoteApplied() {
        val f = fixture()
        f.coordinator.start("600519.SH", prefetched = null)

        val next = quoteWithTimeline("600519.SH", price = 12.0, previousClose = 10.0)
        f.quotePort.completeOldest(QuoteLoadResult(next, DataMode.ONLINE, 0L))

        assertFalse(f.state.quoteLoading)
        assertEquals(DataMode.ONLINE.quoteLabel(), f.state.dataModeLabel)
        val applied = f.effects.filterIsInstance<DetailDataEffect.QuoteApplied>().single()
        assertTrue(applied.changed)
        assertTrue(applied.timelineJustArrived)
        assertEquals(next, applied.next)
    }

    @Test
    fun emptyTimelineOnNewQuoteKeepsPreviousSeries() {
        val f = fixture()
        val first = quoteWithTimeline("600519.SH", price = 10.0, previousClose = 10.0)
        f.coordinator.start("600519.SH", prefetched = first)
        f.effects.clear()

        // A later snapshot-only reply carries an empty timeline; it must not blank the chart.
        val snapshotOnly = Quote.placeholder("600519.SH", "贵州茅台").copy(price = 11.0, previousClose = 10.0)
        f.quotePort.completeOldest(QuoteLoadResult(snapshotOnly, DataMode.ONLINE, 0L))

        assertEquals(first.timeline, f.state.quote.timeline)
        assertEquals(11.0, f.state.quote.price)
        val applied = f.effects.filterIsInstance<DetailDataEffect.QuoteApplied>().single()
        assertFalse(applied.timelineJustArrived, "timeline was already present, this is not a fresh arrival")
    }

    @Test
    fun sixSecondFallbackClearsLoadingWhenCallbackNeverArrives() {
        val f = fixture()
        f.coordinator.start("600519.SH", prefetched = null)
        assertTrue(f.state.quoteLoading)

        f.scheduler.run(6000)

        assertFalse(f.state.quoteLoading)
    }

    @Test
    fun reloadRevisionGuardDropsStaleCallback() {
        val f = fixture()
        f.coordinator.start("600519.SH", prefetched = null)
        f.quotePort.completeOldest(QuoteLoadResult(quoteWithTimeline("600519.SH", 10.0, 10.0), DataMode.ONLINE, 0L))
        f.effects.clear()

        f.coordinator.reload("600519.SH") // revision 1, still pending
        f.coordinator.reload("600519.SH") // revision 2 supersedes revision 1
        assertTrue(f.state.quoteLoading)

        // The stale (revision 1) callback resolves first; it must be ignored.
        val stale = quoteWithTimeline("600519.SH", price = 999.0, previousClose = 10.0)
        f.quotePort.completeOldest(QuoteLoadResult(stale, DataMode.ONLINE, 0L))
        assertTrue(f.state.quoteLoading, "stale reload callback must not clear loading or apply its quote")
        assertTrue(f.effects.isEmpty())

        val fresh = quoteWithTimeline("600519.SH", price = 20.0, previousClose = 10.0)
        f.quotePort.completeOldest(QuoteLoadResult(fresh, DataMode.ONLINE, 0L))
        assertFalse(f.state.quoteLoading)
        assertEquals(20.0, f.state.quote.price)
    }

    @Test
    fun insightLoadUpdatesStateAndEmitsInsightLoaded() {
        val f = fixture()
        f.coordinator.start("600519.SH", prefetched = null)
        f.effects.clear()

        val loaded = f.state.insight.copy(loading = false)
        f.insightPort.completeLoad(loaded)

        assertEquals(loaded, f.state.insight)
        assertEquals<List<DetailDataEffect>>(listOf(DetailDataEffect.InsightLoaded), f.effects)
    }

    @Test
    fun newsIsOnlyAcceptedWhileListIsEmpty() {
        val f = fixture()
        f.coordinator.start("600519.SH", prefetched = null)

        val first = listOf(newsItem("1"), newsItem("2"))
        f.newsPort.deliver(first)
        assertEquals(first, f.state.newsList)

        f.newsPort.deliver(listOf(newsItem("3")))
        assertEquals(first, f.state.newsList, "a later delivery must not override an already-populated list")
    }

    @Test
    fun onDestroyCancelsPendingFallbackTimer() {
        val f = fixture()
        f.coordinator.start("600519.SH", prefetched = null)

        f.coordinator.onDestroy()
        f.scheduler.run(6000)

        // Cancelled before firing: quoteLoading is untouched by the (now dead) fallback.
        assertTrue(f.state.quoteLoading)
    }

    private fun fixture(cachedQuote: Quote? = null): Fixture {
        val state = PlainDetailDataState()
        val quotePort = FakeQuotePort(cachedQuote)
        val insightPort = FakeInsightPort(state.insight)
        val newsPort = FakeNewsPort()
        val scheduler = FakeDetailDataScheduler()
        val effects = mutableListOf<DetailDataEffect>()
        val coordinator = DetailDataCoordinator(state, quotePort, insightPort, newsPort, scheduler, effects::add)
        return Fixture(state, quotePort, insightPort, newsPort, scheduler, effects, coordinator)
    }

    private fun quoteWithTimeline(symbol: String, price: Double, previousClose: Double): Quote =
        Quote.placeholder(symbol, symbol).copy(
            price = price,
            previousClose = previousClose,
            timeline = listOf(QuotePoint(time = "09:30", price = price)),
        )

    private fun newsItem(id: String): NewsItem =
        NewsItem(id = id, title = "title-$id", source = "source", time = "2026-09-12 10:00:00", url = "https://example.com/$id")

    private data class Fixture(
        val state: PlainDetailDataState,
        val quotePort: FakeQuotePort,
        val insightPort: FakeInsightPort,
        val newsPort: FakeNewsPort,
        val scheduler: FakeDetailDataScheduler,
        val effects: MutableList<DetailDataEffect>,
        val coordinator: DetailDataCoordinator,
    )
}

private class FakeQuotePort(private val cached: Quote?) : DetailQuotePort {
    private val pending = mutableListOf<(QuoteLoadResult) -> Unit>()
    override fun cachedOrOffline(symbol: String): Quote? = cached
    override fun load(symbol: String, onResult: (QuoteLoadResult) -> Unit) {
        pending += onResult
    }

    fun completeOldest(result: QuoteLoadResult) {
        check(pending.isNotEmpty()) { "no pending load() to complete" }
        pending.removeAt(0).invoke(result)
    }
}

private class FakeInsightPort(private val initial: StockInsightBundle) : DetailInsightPort {
    private var onLoaded: ((StockInsightBundle) -> Unit)? = null
    override fun cachedStock(symbol: String): StockInsightBundle = initial
    override fun loadStock(symbol: String, onResult: (StockInsightBundle) -> Unit) {
        onLoaded = onResult
    }

    fun completeLoad(bundle: StockInsightBundle) {
        onLoaded?.invoke(bundle)
    }
}

private class FakeNewsPort : DetailNewsPort {
    private var onLoaded: ((List<NewsItem>) -> Unit)? = null
    override fun stockNews(symbol: String, onResult: (List<NewsItem>) -> Unit) {
        onLoaded = onResult
    }

    fun deliver(items: List<NewsItem>) {
        onLoaded?.invoke(items)
    }
}

private class FakeDetailDataScheduler : DetailDataScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): DetailDataScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return DetailDataScheduledTask { entry.cancelled = true }
    }

    fun run(delay: Int) {
        entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}
