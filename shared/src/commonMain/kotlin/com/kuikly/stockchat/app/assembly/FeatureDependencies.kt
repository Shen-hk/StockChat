package com.kuikly.stockchat.app.assembly

import com.kuikly.stockchat.data.provider.MarketInsightRepository
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.provider.SecuritySearchProvider
import com.kuikly.stockchat.data.provider.StockNewsProvider
import com.kuikly.stockchat.data.WatchlistStore

/**
 * 页面级最小依赖集（doc 47 §4(5)）：取代「万能 MarketDependencies（9 个服务）」。
 *
 * 目的不是省内存，而是让页面拿不到它不需要的服务 —— 拿不到就不会顺手用，
 * 四层分离才不会在下一个迭代里被重新糊回去。
 *
 * 这些切片由 [MarketFeatureGraph] 产出，消费方按工作包陆续接入：
 * - [MarketFeatureDependencies] → A-5（P1-Market 四层纵切）
 * - [DetailFeatureDependencies] → A-4（P1-Detail 收口）
 * - [ChatFeatureDependencies] → B-2 / P1-Chat 收口
 */
class MarketFeatureDependencies(
    val quoteRepository: QuoteRepository,
    val insightRepository: MarketInsightRepository,
    val watchlistStore: WatchlistStore,
    val securitySearchProvider: SecuritySearchProvider,
    val stockNewsProvider: StockNewsProvider,
)

class DetailFeatureDependencies(
    val quoteRepository: QuoteRepository,
    val insightRepository: MarketInsightRepository,
    val stockNewsProvider: StockNewsProvider,
)
