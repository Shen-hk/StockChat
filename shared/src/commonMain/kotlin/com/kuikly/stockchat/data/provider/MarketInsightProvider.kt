package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.entity.Security

/** Traceability carried by every non-quote conclusion shown to the user. */
data class SourceStamp(
    val source: String,
    val asOf: String,
    val tier: SourceTier,
    val mode: DataMode = DataMode.ONLINE,
)

enum class SourceTier(val label: String) {
    EXCHANGE("公告/交易所"),
    MARKET_DATA("市场数据"),
    RESEARCH("券商研报"),
    MEDIA("公开媒体"),
    DEMO("演示数据"),
}

/** Remote symbol lookup keeps global search useful beyond the bundled examples. */
interface SecuritySearchProvider {
    fun searchSecurities(query: String, onResult: (List<Security>) -> Unit)
}

data class FundFlow(
    val main: Double,
    val superLarge: Double,
    val large: Double,
    val medium: Double,
    val small: Double,
    val stamp: SourceStamp,
) {
    val explanation: String
        get() = when {
            main > 50_000_000 -> "主力资金明显净流入，说明当日大单承接较强；它只反映交易行为，不等同于后续上涨。"
            main < -50_000_000 -> "主力资金明显净流出，说明当日大单卖压偏强；还需结合板块与公告判断是否为个股因素。"
            else -> "主力资金流向接近平衡，单靠资金面不足以解释价格变化。"
        }
}

data class FinancialSummary(
    val reportDate: String,
    val revenue: Double,
    val netProfit: Double,
    val revenueYoY: Double,
    val profitYoY: Double,
    val eps: Double,
    val roe: Double,
    val grossMargin: Double,
    val stamp: SourceStamp,
) {
    val explanation: String
        get() = when {
            revenueYoY >= 10.0 && profitYoY >= 10.0 -> "收入与利润同步增长，当前增长质量较完整；仍需核对现金流和增长是否可持续。"
            revenueYoY >= 0.0 && profitYoY < 0.0 -> "收入增长但利润下滑，可能存在成本、费用或产品结构压力，值得继续看毛利率变化。"
            revenueYoY < 0.0 && profitYoY < 0.0 -> "收入与利润同时承压，基本面处在收缩阶段；这是已披露事实，不代表未来走势。"
            else -> "收入与利润方向不一致，需要结合毛利率和一次性损益进一步拆解。"
        }
}

data class ShareholderSnapshot(
    val holders: Long,
    val change: Long,
    val changePercent: Double,
    val period: String,
    val stamp: SourceStamp,
) {
    val explanation: String
        get() = if (change > 0) "股东户数增加，筹码趋于分散；这是一项滞后披露指标。" else "股东户数减少，筹码趋于集中；不能单独据此判断价格方向。"
}

data class BillboardRecord(
    val tradeDate: String,
    val reason: String,
    val buyAmount: Double,
    val sellAmount: Double,
    val netAmount: Double,
    val stamp: SourceStamp,
)

data class CorporateAction(
    val title: String,
    val date: String,
    val status: String,
    val stamp: SourceStamp,
)

enum class DisclosureKind(val label: String) { ANNOUNCEMENT("公告"), RESEARCH("研报") }

data class DisclosureItem(
    val id: String,
    val kind: DisclosureKind,
    val title: String,
    val publisher: String,
    val date: String,
    val summary: String,
    val riskLabel: String,
    val url: String,
    val stamp: SourceStamp,
)

data class FundamentalBundle(
    val financial: FinancialSummary?,
    val shareholder: ShareholderSnapshot?,
    val billboard: BillboardRecord?,
    val actions: List<CorporateAction>,
)

data class StockInsightBundle(
    val fundFlow: FundFlow? = null,
    val fundamentals: FundamentalBundle? = null,
    val disclosures: List<DisclosureItem> = emptyList(),
    val loading: Boolean = false,
)

data class MarketIndex(
    val code: String,
    val name: String,
    val price: Double,
    val changePercent: Double,
    val high: Double? = null,
    val low: Double? = null,
)

data class SectorRank(
    val code: String,
    val name: String,
    val changePercent: Double,
    val mainFlow: Double,
    val risingCount: Int,
    val fallingCount: Int,
)

data class LimitUpStock(
    val symbol: String,
    val name: String,
    val changePercent: Double,
    val sector: String,
    val consecutiveBoards: Int,
    val sealedAmount: Double,
    val openCount: Int,
)

data class MarketOverview(
    val indices: List<MarketIndex>,
    val risingCount: Int,
    val fallingCount: Int,
    val flatCount: Int,
    val limitUpCount: Int,
    val limitDownCount: Int,
    val sectors: List<SectorRank>,
    val stamp: SourceStamp,
    /** Optional market-wide figures. Each value is independently allowed to fall back. */
    val turnoverAmount: Double? = null,
    val yesterdayTurnoverAmount: Double? = null,
    val fiveDayAverageTurnoverAmount: Double? = null,
    val sealRate: Double? = null,
    val brokenBoardCount: Int? = null,
    val highestBoard: Int? = null,
    val yesterdayHighestBoard: Int? = null,
    val northboundFlow: Double? = null,
) {
    val moodScore: Int
        get() {
            val breadth = if (risingCount + fallingCount == 0) 50.0 else risingCount * 100.0 / (risingCount + fallingCount)
            return (breadth * 0.7 + (50 + (limitUpCount - limitDownCount).coerceIn(-50, 50)) * 0.3).toInt().coerceIn(0, 100)
        }
    val explanation: String
        get() = when (moodScore) {
            in 0..25 -> "市场情绪偏冷，多数个股承压。先区分指数下跌与持仓所在板块的差异，不宜只看单一指数。"
            in 26..45 -> "市场情绪偏弱，下跌家数相对更多。当前数据说明风险偏好回落，不预测下一交易日方向。"
            in 46..60 -> "市场多空相对均衡，结构分化比指数方向更重要。可继续查看领涨板块与涨停分布。"
            in 61..80 -> "市场情绪偏暖，上涨家数占优。热度主要集中在哪些板块，仍需结合资金流确认。"
            else -> "市场情绪较热，涨停与上涨家数集中。热度高不等同于追涨依据，应留意拥挤和分化。"
        }
}

data class HotspotSnapshot(
    val sectors: List<SectorRank>,
    val limitUps: List<LimitUpStock>,
    val stamp: SourceStamp,
) {
    val explanation: String
        get() {
            val lead = sectors.firstOrNull()?.name ?: "暂无明确主线"
            val clustered = limitUps.groupBy { it.sector }.maxByOrNull { it.value.size }
            return if (clustered != null) "$lead 领涨，涨停主要聚集在${clustered.key}（${clustered.value.size} 只）。这是当日热度归因，不是选股建议。"
            else "$lead 领涨，但涨停样本较少，热点持续性仍不确定。"
        }
}

enum class CalendarEventKind(val label: String) { EARNINGS("财报"), IPO("打新"), UNLOCK("解禁"), DIVIDEND("分红") }

data class MarketCalendarEvent(
    val date: String,
    val symbol: String,
    val name: String,
    val kind: CalendarEventKind,
    val title: String,
    val stamp: SourceStamp,
)

interface FundFlowProvider {
    fun fundFlow(symbol: String, onResult: (FundFlow?) -> Unit)
}

interface FundamentalProvider {
    fun fundamentals(symbol: String, onResult: (FundamentalBundle?) -> Unit)
    fun calendar(onResult: (List<MarketCalendarEvent>) -> Unit)
}

interface DisclosureProvider {
    fun disclosures(symbol: String, onResult: (List<DisclosureItem>) -> Unit)
}

interface MarketOverviewProvider {
    fun overview(onResult: (MarketOverview?) -> Unit)
    fun hotspots(onResult: (HotspotSnapshot?) -> Unit)
}

/** Batched per-security industry lookup (EastMoney f100). Keys are watchlist symbols. */
interface IndustryProvider {
    fun industries(symbols: List<String>, onResult: (Map<String, String>) -> Unit)
}

/** Page-scoped real -> memory cache -> deterministic demo fallback. */
class MarketInsightRepository(
    private val onlineFundFlow: FundFlowProvider,
    private val onlineFundamentals: FundamentalProvider,
    private val onlineDisclosures: DisclosureProvider,
    private val onlineMarket: MarketOverviewProvider,
    private val onlineIndustry: IndustryProvider? = null,
    private val fallback: OfflineMarketInsightProvider = OfflineMarketInsightProvider(),
) {
    private val stockCache = mutableMapOf<String, StockInsightBundle>()
    private var overviewCache: MarketOverview? = null
    private var hotspotCache: HotspotSnapshot? = null
    private var calendarCache: List<MarketCalendarEvent>? = null

    fun cachedStock(symbol: String): StockInsightBundle = stockCache[symbol] ?: fallback.stock(symbol)

    fun loadStock(symbol: String, onResult: (StockInsightBundle) -> Unit) {
        var state = cachedStock(symbol).copy(loading = true)
        onResult(state)
        onlineFundFlow.fundFlow(symbol) { value ->
            val resolved = value ?: state.fundFlow ?: fallback.stock(symbol).fundFlow
            state = state.copy(fundFlow = resolved)
            stockCache[symbol] = state
            onResult(state)
        }
        onlineFundamentals.fundamentals(symbol) { value ->
            val resolved = value ?: state.fundamentals ?: fallback.stock(symbol).fundamentals
            state = state.copy(fundamentals = resolved)
            stockCache[symbol] = state
            onResult(state)
        }
        onlineDisclosures.disclosures(symbol) { value ->
            val resolved = value.ifEmpty { state.disclosures.ifEmpty { fallback.stock(symbol).disclosures } }
            state = state.copy(disclosures = resolved, loading = false)
            stockCache[symbol] = state
            onResult(state)
        }
    }

    fun loadOverview(onResult: (MarketOverview) -> Unit) {
        // A memory hit is intentionally marked as cache: the UI can keep old
        // figures visible without implying that a network refresh succeeded.
        overviewCache?.let { cached -> onResult(cached.copy(stamp = cached.stamp.copy(mode = DataMode.CACHE))) }
        onlineMarket.overview { value ->
            val resolved = value ?: overviewCache ?: fallback.overviewValue()
            overviewCache = resolved
            onResult(resolved)
        }
    }

    fun loadHotspots(onResult: (HotspotSnapshot) -> Unit) {
        hotspotCache?.let(onResult)
        onlineMarket.hotspots { value ->
            val resolved = value ?: hotspotCache ?: fallback.hotspotValue()
            hotspotCache = resolved
            onResult(resolved)
        }
    }

    fun loadCalendar(onResult: (List<MarketCalendarEvent>) -> Unit) {
        calendarCache?.let(onResult)
        onlineFundamentals.calendar { value ->
            val resolved = value.ifEmpty { calendarCache ?: fallback.calendarValue() }
            calendarCache = resolved
            onResult(resolved)
        }
    }

    private var industryCache: Map<String, String>? = null

    /**
     * 批量行业归属。离线/失败时返回上一次缓存，再不行返回空表——
     * 风险地图的行业维度对未知标的归入「未分类」，不阻塞其他维度。
     */
    fun loadIndustries(symbols: List<String>, onResult: (Map<String, String>) -> Unit) {
        val provider = onlineIndustry
        if (provider == null || symbols.isEmpty()) {
            onResult(industryCache.orEmpty())
            return
        }
        industryCache?.let(onResult)
        provider.industries(symbols) { value ->
            val resolved = if (value.isEmpty()) industryCache.orEmpty() else value
            industryCache = resolved
            onResult(resolved)
        }
    }
}
