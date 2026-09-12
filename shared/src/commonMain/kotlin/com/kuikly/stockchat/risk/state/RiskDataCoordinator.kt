package com.kuikly.stockchat.risk.state

import com.kuikly.stockchat.data.RiskSnapshot
import com.kuikly.stockchat.data.RiskSnapshotStore
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.provider.LimitUpStock
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.risk.data.RiskRepository
import com.kuikly.stockchat.risk.domain.RiskRow
import com.kuikly.stockchat.risk.domain.chainConcentration
import com.kuikly.stockchat.risk.domain.industryStats
import com.kuikly.stockchat.risk.domain.portfolioVolRatio
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

/**
 * 风险地图数据域唯一 owner（doc 47 B-3）：接管原 RiskMapPage 的
 * `rows / indexQuote / industries / events / limitUps / dataModeLabel` 六个
 * observable 与 `reload()` 装载链、FR-R10 暴露快照留存。
 *
 * 自选是风险唯一输入（doc 23 C-1）：本域不持有标的来源，[reload] 由页面从
 * WatchlistStore 读出列表后传入。行情是异步到达的；每批行情落地都会触发
 * [onQuoteArrived]（页面把它接到星图重算 + AI 事实就绪检查）。
 */
internal class RiskDataCoordinator(
    private val repository: RiskRepository,
    private val snapshotStore: RiskSnapshotStore,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
    private val today: () -> String = ::platformCurrentDate,
    /** 每次行情/指数到达后回调（重算相关系数、波动倍率、AI 事实）。 */
    private val onQuoteArrived: () -> Unit = {},
    /** 行业归属到达后回调（触发 FR-R10 快照采集判定）。 */
    private val onIndustriesArrived: () -> Unit = {},
) {
    /** 自选行（created() 内同步装满；行情加载只替换元素不改 size）。 */
    val rows: ObservableList<RiskRow> by observableList()

    /** 大盘基准（波动暴露的分母，FR-R7 = 沪深300）。 */
    var indexQuote: Quote? by observable(null)
        private set

    /** symbol → 行业名；离线/失败时为空表（未分类），不阻塞其他维度。 */
    var industries: Map<String, String> by observable(emptyMap())
        private set

    /** 事件时间轴：全市场预约日历 ∩ 自选代码，未来 6 条。 */
    var events: List<MarketCalendarEvent> by observable(emptyList())
        private set

    /** 涨停池 ∩ 自选（情绪暴露维度）。 */
    var limitUps: List<Pair<LimitUpStock, RiskRow>> by observable(emptyList())
        private set

    /** 数据模式角标文案（缓存/离线/实时）。 */
    var dataModeLabel: String by observable("")
        private set

    /** 是否已经装满自选行（构建期判定星图档位用，与原 rows.isEmpty() 等价）。 */
    val loaded: Boolean get() = rows.isNotEmpty()

    /**
     * 装载链（与原 RiskMapPage.reload 逐步等价）：
     * 离线/缓存行情先行可算一次 → 每只在线行情异步替换 → 指数 → 行业 → 日历 → 涨停池。
     */
    fun reload(watchlistItems: List<WatchlistItem>) {
        rows.clear()
        watchlistItems.forEach { item ->
            rows.add(RiskRow(item.symbol, item.name, repository.cachedOrOffline(item.symbol)))
            repository.loadQuote(item.symbol) { result ->
                val index = rows.indexOfFirst { it.symbol == item.symbol }
                val quote = result.quote
                if (index >= 0 && quote != null) {
                    rows[index] = rows[index].copy(quote = quote)
                }
                dataModeLabel = result.mode.quoteLabel()
                // 行情是异步到达的；相关系数/波动/AI 事实槽位必须随新行情重算。
                onQuoteArrived()
            }
        }
        if (watchlistItems.isEmpty()) return

        // 大盘基准（波动暴露的分母）。
        repository.loadQuote(INDEX_SYMBOL) { result ->
            result.quote?.let {
                indexQuote = it
                onQuoteArrived()
            }
        }

        // 行业归属：一次批量请求；失败降级为空表，不阻塞其他维度。
        repository.loadIndustries(watchlistItems.map { it.symbol }) { map ->
            industries = map
            onIndustriesArrived()
        }

        // 事件时间轴：全市场预约日历 ∩ 自选代码。
        repository.loadCalendar { all ->
            val codes = watchlistItems.map { it.symbol.substringBefore('.') }.toSet()
            val todayDate = today()
            events = all
                .filter { it.symbol.substringBefore('.') in codes || it.symbol in watchlistItems.map { item -> item.symbol } }
                .filter { it.date >= todayDate }
                .sortedBy { it.date }
                .take(6)
        }

        // 情绪暴露：涨停池 ∩ 自选。
        repository.loadHotspots { snapshot ->
            val bySymbol = rows.associateBy { it.symbol }
            limitUps = snapshot.limitUps.mapNotNull { limitUp ->
                bySymbol[limitUp.symbol]?.let { limitUp to it }
            }
        }
    }

    // ── FR-R10 暴露快照留存 ──

    /**
     * 暴露快照留存（每周一次）。行业数据到达后尝试采集，7 天内重复打开不重复
     * 存（判定在 RiskSnapshotStore.maybeCapture 内）。波动比取当期组合与大盘的比值。
     */
    fun captureSnapshotIfDue() {
        val industry = industryStats(rows.toList(), industries)
        if (industry.isEmpty()) return
        val chain = chainConcentration(rows.toList(), industry)
        val ratio = portfolioVolRatio(rows.toList(), indexQuote)
        snapshotStore.maybeCapture(
            RiskSnapshot(
                capturedAtMillis = nowMillis(),
                memberCount = rows.size,
                topIndustry = chain.topName,
                topIndustryCount = chain.topCount,
                cr3Percent = (chain.cr3 * 100).toInt(),
                volRatioPercent = ratio?.let { (it * 100).toInt() },
            ),
        )
    }

    /** 快照历史（构建期读一次；≥2 条才显示）。 */
    fun snapshots(): List<RiskSnapshot> = snapshotStore.all()

    private companion object {
        // FR-R7：波动基准 = 沪深300（更接近「大盘组合」口径；原用上证指数已改）。
        const val INDEX_SYMBOL = "000300.SH"
    }
}
