package com.kuikly.stockchat

import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.KLineInterval
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePoint
import com.kuikly.stockchat.data.provider.QuotePrefetchStore
import com.kuikly.stockchat.data.provider.QuoteProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QuotePrefetchStoreTest {
    @BeforeTest
    fun resetStore() {
        // 全局单例跨用例隔离：进程内所有页面共享 entries/inFlight。
        QuotePrefetchStore.clearForTest()
    }

    @Test
    fun warmMergesTimelineIntoSnapshotAndPeekReturnsFreshQuote() {
        var now = 1_000L
        val base = MockDataBank.quote("600519.SH")!!.copy(timeline = emptyList())
        val timeline = base.timeline.take(5)
        val provider = MutablePrefetchProvider(base, timeline)

        QuotePrefetchStore.warm(listOf("600519.SH"), provider, { now })
        val prefetched = QuotePrefetchStore.peek("600519.SH", { now })

        assertNotNull(prefetched)
        assertEquals(base.price, prefetched.price, 0.0)
        assertEquals(timeline, prefetched.timeline)
        assertNull(QuotePrefetchStore.peek("000858.SZ", { now }))
    }

    @Test
    fun peekMissesAfterFreshnessWindowAndWarmSkipsFreshSymbols() {
        var now = 1_000L
        val provider = MutablePrefetchProvider(MockDataBank.quote("600519.SH")!!, emptyList())

        QuotePrefetchStore.warm(listOf("600519.SH"), provider, { now })
        now += QuotePrefetchStore.FRESH_MILLIS + 1
        assertNull(QuotePrefetchStore.peek("600519.SH", { now }))

        // 过期后再次 warm 重新拉取；新鲜期内重复 warm 不产生新请求。
        now += 1_000L
        QuotePrefetchStore.warm(listOf("600519.SH"), provider, { now })
        val before = provider.snapshotRequests
        QuotePrefetchStore.warm(listOf("600519.SH"), provider, { now })
        assertEquals(before, provider.snapshotRequests)
        assertNotNull(QuotePrefetchStore.peek("600519.SH", { now }))
    }

    @Test
    fun warmIgnoresInvalidSnapshotAndBlankSymbols() {
        var now = 1_000L
        val provider = MutablePrefetchProvider(null, emptyList())

        QuotePrefetchStore.warm(listOf("", "600519.SH"), provider, { now })

        assertNull(QuotePrefetchStore.peek("600519.SH", { now }))
        // 快照无效（无数据）时不得入缓存，但请求发生过一次；数据恢复后可再次预热。
        assertEquals(1, provider.snapshotRequests)
    }

    private class MutablePrefetchProvider(
        var quote: Quote?,
        private val timeline: List<QuotePoint>,
    ) : QuoteProvider {
        override val mode: DataMode = DataMode.ONLINE
        var snapshotRequests = 0
            private set

        override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) {
            snapshotRequests++
            onResult(quote?.takeIf { it.symbol == symbol })
        }

        override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) =
            onResult(if (quote?.symbol == symbol) timeline else emptyList())

        override fun kLines(symbol: String, count: Int, interval: KLineInterval, onResult: (List<com.kuikly.stockchat.data.provider.KLinePoint>) -> Unit) =
            onResult(emptyList())
    }
}
