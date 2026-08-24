package com.kuikly.stockchat

import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.KLinePoint
import com.kuikly.stockchat.data.provider.KLineInterval
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePoint
import com.kuikly.stockchat.data.provider.QuoteProvider
import com.kuikly.stockchat.data.provider.QuoteRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuoteRepositoryTest {
    @Test
    fun usesFreshCacheWhenOnlineSnapshotFails() {
        var now = 1_000L
        val online = MutableQuoteProvider(MockDataBank.quote("600519.SH"))
        val repository = QuoteRepository(online, MutableQuoteProvider(null), { now })

        assertEquals(DataMode.ONLINE, repository.loadOnce("600519.SH").mode)
        online.quote = null

        val cached = repository.loadOnce("600519.SH")
        assertEquals(DataMode.CACHE, cached.mode)
        assertTrue(cached.quote!!.source.startsWith("缓存行情"))
    }

    @Test
    fun expiresSnapshotCacheAndFallsBackToOfflineData() {
        var now = 1_000L
        val online = MutableQuoteProvider(MockDataBank.quote("600519.SH"))
        val offline = MutableQuoteProvider(MockDataBank.quote("000858.SZ"))
        val repository = QuoteRepository(online, offline, { now })

        repository.loadOnce("600519.SH")
        online.quote = null
        now += QuoteRepository.SNAPSHOT_TTL_MILLIS + 1

        val result = repository.loadOnce("600519.SH")
        assertEquals(DataMode.OFFLINE, result.mode)
        assertEquals("000858.SZ", result.quote?.symbol)
    }

    @Test
    fun mergesTimelineAndKLinesIntoOnlineSnapshot() {
        val onlineQuote = MockDataBank.quote("600519.SH")!!.copy(timeline = emptyList(), kLines = emptyList())
        val expectedTimeline = MockDataBank.quote("600519.SH")!!.timeline.take(3)
        val expectedKLines = MockDataBank.quote("600519.SH")!!.kLines.take(3)
        val online = MutableQuoteProvider(onlineQuote, expectedTimeline, expectedKLines)
        val repository = QuoteRepository(online, MutableQuoteProvider(null), { 1_000L })
        val results = mutableListOf<Quote>()

        repository.load("600519.SH") { it.quote?.let(results::add) }

        assertEquals(expectedTimeline, results.last().timeline)
        assertEquals(expectedKLines, results.last().kLines)
    }

    private fun QuoteRepository.loadOnce(symbol: String) =
        mutableListOf<com.kuikly.stockchat.data.provider.QuoteLoadResult>().also { results -> load(symbol, results::add) }.first()

    private class MutableQuoteProvider(
        var quote: Quote?,
        private val timeline: List<QuotePoint> = emptyList(),
        private val kLines: List<KLinePoint> = emptyList(),
    ) : QuoteProvider {
        override val mode: DataMode = DataMode.ONLINE

        override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) = onResult(quote)
        override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) = onResult(timeline)
        override fun kLines(symbol: String, count: Int, interval: KLineInterval, onResult: (List<KLinePoint>) -> Unit) = onResult(kLines)
    }
}
