package com.kuikly.stockchat

import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.data.provider.SharedPreferencesQuoteCacheStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPreferencesQuoteCacheStoreTest {
    @Test
    fun migratesLargeV1CacheWithoutParsingItAndPersistsOnlySnapshotFields() {
        val storage = InMemoryKeyValueStorage(
            mapOf(
                "stockchat_quote_snapshots_v1" to "[not valid JSON]",
                "stockchat_quote_timelines_v1" to "[not valid JSON]",
                "stockchat_quote_klines_v1" to "[not valid JSON]",
            ),
        )
        val cache = SharedPreferencesQuoteCacheStore(storage)
        val quote = MockDataBank.quote("600519.SH")!!

        cache.saveSnapshot(quote.symbol, quote, 1_000L)

        val values = storage.snapshot()
        assertEquals("", values["stockchat_quote_snapshots_v1"])
        assertEquals("2", values["stockchat_quote_cache_version"])
        assertFalse(values.getValue("stockchat_quote_snapshots_v2").contains("timeline"))
        val restored = cache.loadSnapshots().single().value
        assertEquals(quote.symbol, restored.symbol)
        assertTrue(restored.timeline.isEmpty())
        assertTrue(restored.kLines.isEmpty())
    }

}
