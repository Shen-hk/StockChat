package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.provider.SharedPreferencesQuoteCacheStore
import com.kuikly.stockchat.data.provider.TencentQuoteProvider
import com.kuikly.stockchat.data.provider.EastMoneyInsightProvider
import com.kuikly.stockchat.data.provider.MarketInsightRepository
import com.kuikly.stockchat.data.provider.SecuritySearchProvider
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage

/** Page-scoped market feature graph shared by quote and watchlist screens. */
class MarketDependencies(
    val watchlistStore: WatchlistStore,
    val alertStore: AlertStore,
    val glossaryStore: GlossaryStore,
    val riskSnapshotStore: RiskSnapshotStore,
    val quoteRepository: QuoteRepository,
    val insightRepository: MarketInsightRepository,
    val securitySearchProvider: SecuritySearchProvider,
) {
    companion object {
        fun forPager(
            pagerId: String,
            storage: KeyValueStorage = PagerKeyValueStorage(pagerId),
        ): MarketDependencies {
            val eastMoney = EastMoneyInsightProvider(pagerId)
            return MarketDependencies(
                watchlistStore = WatchlistStore(storage),
                alertStore = AlertStore(storage),
                glossaryStore = GlossaryStore(storage),
                riskSnapshotStore = RiskSnapshotStore(storage),
                quoteRepository = QuoteRepository(
                    online = TencentQuoteProvider(pagerId),
                    cacheStore = SharedPreferencesQuoteCacheStore(storage),
                ),
                insightRepository = MarketInsightRepository(
                    onlineFundFlow = eastMoney,
                    onlineFundamentals = eastMoney,
                    onlineDisclosures = eastMoney,
                    onlineMarket = eastMoney,
                    onlineIndustry = eastMoney,
                ),
                securitySearchProvider = eastMoney,
            )
        }
    }
}
