package com.kuikly.stockchat.risk.data

import com.kuikly.stockchat.data.provider.HotspotSnapshot
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.MarketInsightRepository
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteLoadResult
import com.kuikly.stockchat.data.provider.QuoteRepository

/**
 * Risk Feature 的数据 Port（doc 47 B-3）：把「行情三级降级链 + 行业/日历/热点的
 * 缓存降级」收口到一个入口，Page 与 Coordinator 不再直接认识两个 Repository 的
 * 全部方法；同时让 RiskDataCoordinator 可用 fake 仓库做纯单测。
 * 降级策略本身**逐字保留**在 `QuoteRepository` / `MarketInsightRepository`
 * —— 默认实现只做收口与转发，不改任何判定规则。
 */
internal interface RiskRepository {
    /** 离线/缓存行情先行（页面首帧可算），在线加载由 [loadQuote] 异步补齐。 */
    fun cachedOrOffline(symbol: String): Quote?

    /** 在线行情（QuoteRepository 内部三级降级：在线 → 缓存 → 空态）。 */
    fun loadQuote(symbol: String, onResult: (QuoteLoadResult) -> Unit)

    /** 批量行业归属：失败静默降级为空表（未知标的归「未分类」），不阻塞其他维度。 */
    fun loadIndustries(symbols: List<String>, onResult: (Map<String, String>) -> Unit)

    /** 全市场预约日历（「∩ 自选」过滤在 Coordinator，输入口径只有 watchlist）。 */
    fun loadCalendar(onResult: (List<MarketCalendarEvent>) -> Unit)

    /** 涨停池热点快照（情绪暴露维度）。 */
    fun loadHotspots(onResult: (HotspotSnapshot) -> Unit)
}

internal class DefaultRiskRepository(
    private val quoteRepository: QuoteRepository,
    private val insightRepository: MarketInsightRepository,
) : RiskRepository {
    override fun cachedOrOffline(symbol: String): Quote? = quoteRepository.cachedOrOffline(symbol)

    override fun loadQuote(symbol: String, onResult: (QuoteLoadResult) -> Unit) {
        quoteRepository.load(symbol, onResult)
    }

    override fun loadIndustries(symbols: List<String>, onResult: (Map<String, String>) -> Unit) {
        insightRepository.loadIndustries(symbols, onResult)
    }

    override fun loadCalendar(onResult: (List<MarketCalendarEvent>) -> Unit) {
        insightRepository.loadCalendar(onResult)
    }

    override fun loadHotspots(onResult: (HotspotSnapshot) -> Unit) {
        insightRepository.loadHotspots(onResult)
    }
}
