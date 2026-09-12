package com.kuikly.stockchat.detail.quote.state

import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteLoadResult
import com.kuikly.stockchat.data.provider.StockInsightBundle

/** [DetailDataCoordinator] 对 quoteRepository 的最小依赖面，供页面适配、测试可 fake。 */
internal interface DetailQuotePort {
    fun cachedOrOffline(symbol: String): Quote?
    fun load(symbol: String, onResult: (QuoteLoadResult) -> Unit)
}

/** 对 insightRepository 的最小依赖面。 */
internal interface DetailInsightPort {
    fun cachedStock(symbol: String): StockInsightBundle
    fun loadStock(symbol: String, onResult: (StockInsightBundle) -> Unit)
}

/** 对 stockNewsProvider 的最小依赖面。 */
internal interface DetailNewsPort {
    fun stockNews(symbol: String, onResult: (List<NewsItem>) -> Unit)
}

/**
 * Detail 数据加载域需要页面（或后续 Wave2 其他 Coordinator）执行的下游副作用。
 * ticker 动效、声呐异动派生、AI 洞察的自动触发仍是页面/其他域的地盘——本刀只
 * 负责在 quote 合并完成、insight 首次就位后发出通知，不接管这些下游行为。
 */
internal sealed interface DetailDataEffect {
    data class QuoteApplied(
        val previous: Quote,
        val next: Quote,
        val changed: Boolean,
        val timelineJustArrived: Boolean,
    ) : DetailDataEffect

    object InsightLoaded : DetailDataEffect

    /**
     * 转场预取命中（openStockDetail 携带的整页数据）。原实现只在此路径派生声呐
     * 异动点，不播 ticker、不写 previous*Text——不能并入 [QuoteApplied]，否则会
     * 把占位态的 previous*Text 也一并覆盖。
     */
    data class PrefetchApplied(val quote: Quote) : DetailDataEffect
}
