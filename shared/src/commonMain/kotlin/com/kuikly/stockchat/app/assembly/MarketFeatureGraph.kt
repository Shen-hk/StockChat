package com.kuikly.stockchat.app.assembly

import com.kuikly.stockchat.app.platform.KuiklyKeyValueStorage
import com.kuikly.stockchat.app.platform.KuiklyPlatformScheduler
import com.kuikly.stockchat.app.platform.KuiklyQuoteJsonClient
import com.kuikly.stockchat.data.AlertInboxStore
import com.kuikly.stockchat.data.AlertStore
import com.kuikly.stockchat.data.GlossaryStore
import com.kuikly.stockchat.data.MarketDataPrefs
import com.kuikly.stockchat.data.RiskSnapshotStore
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.provider.EastMoneyInsightProvider
import com.kuikly.stockchat.data.provider.FallbackMarketOverviewProvider
import com.kuikly.stockchat.data.provider.MarketInsightRepository
import com.kuikly.stockchat.data.provider.QuoteProvider
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.provider.SecuritySearchProvider
import com.kuikly.stockchat.data.provider.SharedPreferencesQuoteCacheStore
import com.kuikly.stockchat.data.provider.StockNewsProvider
import com.kuikly.stockchat.data.provider.TencentIndexOverviewProvider
import com.kuikly.stockchat.data.provider.TencentQuoteProvider
import com.kuikly.stockchat.data.storage.KeyValueStorage

/**
 * 行情 / 自选 / 风险三个 Feature 共享的服务图（App 层的**数据载体**）。
 *
 * 2026-09-12（doc 47 B-1）从 `data/MarketDependencies.kt` 搬来：它一直是 App 的
 * composition root，却住在业务数据包里。搬动后 `data/` 只负责数据，装配全部收敛到
 * `app/assembly`。类名保持 `MarketDependencies` 不变 —— B-1 的跨轨契约只允许改
 * import 行，改类名会强迫轨 A 的页面改非 import 代码。
 *
 * 实例只能由 [MarketFeatureGraph.forPager] 创建（composition root 唯一入口）。
 */
class MarketDependencies(
    val watchlistStore: WatchlistStore,
    val alertStore: AlertStore,
    val alertInboxStore: AlertInboxStore,
    val glossaryStore: GlossaryStore,
    val riskSnapshotStore: RiskSnapshotStore,
    val quoteRepository: QuoteRepository,
    val insightRepository: MarketInsightRepository,
    val securitySearchProvider: SecuritySearchProvider,
    val stockNewsProvider: StockNewsProvider,
) {
    /** 行情页最小依赖集（doc 47 §4(5)）。消费方：A-5 Market 四层纵切。 */
    val marketFeature: MarketFeatureDependencies by lazy {
        MarketFeatureDependencies(
            quoteRepository = quoteRepository,
            insightRepository = insightRepository,
            watchlistStore = watchlistStore,
            securitySearchProvider = securitySearchProvider,
            stockNewsProvider = stockNewsProvider,
        )
    }

    /** 详情页最小依赖集（doc 47 §4(5)）。消费方：A-4 Detail 反向依赖收口。 */
    val detailFeature: DetailFeatureDependencies by lazy {
        DetailFeatureDependencies(
            quoteRepository = quoteRepository,
            insightRepository = insightRepository,
            stockNewsProvider = stockNewsProvider,
        )
    }
}

/** App 装配根：pager 级 Feature 图的唯一创建入口（`forPager` 只允许出现在本包）。 */
object MarketFeatureGraph {
    fun forPager(
        pagerId: String,
        storage: KeyValueStorage = KuiklyKeyValueStorage(pagerId),
    ): MarketDependencies {
        val scheduler = KuiklyPlatformScheduler(pagerId)
        val quoteJson = KuiklyQuoteJsonClient(pagerId)
        val eastMoney = EastMoneyInsightProvider(scheduler)
        return MarketDependencies(
            watchlistStore = WatchlistStore(storage),
            alertStore = AlertStore(storage),
            alertInboxStore = AlertInboxStore(storage),
            glossaryStore = GlossaryStore(storage),
            riskSnapshotStore = RiskSnapshotStore(storage),
            quoteRepository = QuoteRepository(
                online = TencentQuoteProvider(quoteJson),
                selectedSource = { MarketDataPrefs.source(storage) },
                cacheStore = SharedPreferencesQuoteCacheStore(storage),
            ),
            insightRepository = MarketInsightRepository(
                onlineFundFlow = eastMoney,
                onlineFundamentals = eastMoney,
                onlineDisclosures = eastMoney,
                onlineMarket = FallbackMarketOverviewProvider(
                    primary = eastMoney,
                    fallback = TencentIndexOverviewProvider(quoteJson),
                ),
                onlineIndustry = eastMoney,
                onlineRatingSpectrum = eastMoney,
            ),
            securitySearchProvider = eastMoney,
            stockNewsProvider = eastMoney,
        )
    }

    /**
     * 页面级预取用的行情源（`QuotePrefetchStore.warm` 专用）。
     *
     * 存在的意义：预取只需要「快照 + 分时」两次请求，不该让页面为了拿一个
     * QuoteProvider 去构造 Provider（doc 47 硬指标：Page 不得直接构造 Provider）。
     * 它仍由装配根产出，页面只拿到接口。
     */
    fun prefetchTarget(pagerId: String): QuoteProvider =
        TencentQuoteProvider(KuiklyQuoteJsonClient(pagerId))
}
