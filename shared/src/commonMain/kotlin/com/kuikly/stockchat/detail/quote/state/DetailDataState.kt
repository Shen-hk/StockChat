package com.kuikly.stockchat.detail.quote.state

import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * Detail 页 Quote/Insight/News 的可渲染状态端口（Wave 2 第 1 刀）。
 * Kuikly 实现用 `observable`，测试用 [PlainDetailDataState]。
 */
internal interface DetailDataStatePort {
    var quote: Quote
    var dataModeLabel: String
    var quoteLoading: Boolean
    var chartDataLoading: Boolean
    var insight: StockInsightBundle
    var newsList: List<NewsItem>
}

internal class DetailDataState : DetailDataStatePort {
    override var quote: Quote by observable(Quote.placeholder("600519.SH", "贵州茅台"))
    override var dataModeLabel: String by observable("正在连接行情")
    override var quoteLoading: Boolean by observable(true)
    override var chartDataLoading: Boolean by observable(true)
    override var insight: StockInsightBundle by observable(OfflineMarketInsightProvider().stock("600519.SH"))
    override var newsList: List<NewsItem> by observable(emptyList())
}

internal class PlainDetailDataState : DetailDataStatePort {
    override var quote = Quote.placeholder("600519.SH", "贵州茅台")
    override var dataModeLabel = "正在连接行情"
    override var quoteLoading = true
    override var chartDataLoading = true
    override var insight = OfflineMarketInsightProvider().stock("600519.SH")
    override var newsList: List<NewsItem> = emptyList()
}
