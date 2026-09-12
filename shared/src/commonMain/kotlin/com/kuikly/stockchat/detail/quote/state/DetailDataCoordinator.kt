package com.kuikly.stockchat.detail.quote.state

import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.quoteLabel

/**
 * Detail 页 Quote/Insight/News 加载域的唯一 owner（Wave 2 第 1 刀，见
 * `docs/39-项目级目标架构与演进蓝图_v1.0.md` §9）：缓存/预取读取、真实加载、
 * 分时/K 线的"空序列不覆盖旧值"合并规则、6s loading 兜底、数据源切换重载的
 * revision 守卫。ticker 动效、声呐异动派生、AI 洞察触发仍是页面（及后续
 * Wave2 第 2/3 刀）的地盘——本类只在 quote 合并、insight 首次就位后发出
 * [DetailDataEffect] 通知，不直接执行这些下游副作用。
 */
internal class DetailDataCoordinator(
    private val state: DetailDataStatePort,
    private val quotePort: DetailQuotePort,
    private val insightPort: DetailInsightPort,
    private val newsPort: DetailNewsPort,
    private val scheduler: DetailDataScheduler,
    private val onEffect: (DetailDataEffect) -> Unit,
) {
    private var dataRevision = 0
    private val tasks = mutableListOf<DetailDataScheduledTask>()

    /** 对齐原 `created()`：预取/缓存兜底 → 真实加载 → insight/news 加载。 */
    fun start(symbol: String, prefetched: Quote?) {
        // 页面对象的默认占位是茅台，但路由参数在页面 created() 才可读取。必须立刻
        // 用当前 symbol 覆盖，网络尚未回调或请求失败时也不能把标题显示成茅台。
        state.quote = Quote.placeholder(symbol, symbol)
        if (prefetched != null) {
            state.quote = prefetched
            state.quoteLoading = false
            state.chartDataLoading = prefetched.timeline.isEmpty()
            state.dataModeLabel = "预加载行情（可能延迟）"
            onEffect(DetailDataEffect.PrefetchApplied(prefetched))
        } else {
            quotePort.cachedOrOffline(symbol)?.let {
                state.quote = it
                state.quoteLoading = false
                state.chartDataLoading = it.timeline.isEmpty()
            }
        }
        quotePort.load(symbol) { result ->
            result.quote?.let { applyQuote(it) }
            state.quoteLoading = false
            state.chartDataLoading = state.quote.timeline.isEmpty()
            state.dataModeLabel = result.mode.quoteLabel()
        }
        // 兜底：行情回调整体丢失（极端弱网/断链）时不让顶部价格永远停在骨架空态。
        schedule(6000) { if (state.quoteLoading) state.quoteLoading = false }

        state.insight = insightPort.cachedStock(symbol)
        insightPort.loadStock(symbol) {
            state.insight = it
            onEffect(DetailDataEffect.InsightLoaded)
        }

        newsPort.stockNews(symbol) { items ->
            if (items.isNotEmpty() && state.newsList.isEmpty()) {
                state.newsList = items.take(12)
            }
        }
    }

    /** 对齐原 `reloadQuoteForCurrentSource()`：数据源切换后的重载，revision 守卫过期回调。 */
    fun reload(symbol: String) {
        state.quoteLoading = true
        state.chartDataLoading = true
        val revision = ++dataRevision
        quotePort.load(symbol) { result ->
            if (revision != dataRevision) return@load
            result.quote?.let { applyQuote(it) }
            state.quoteLoading = false
            state.chartDataLoading = state.quote.timeline.isEmpty()
            state.dataModeLabel = result.mode.quoteLabel()
        }
    }

    /** 对齐原 `applyQuote()` 的合并规则；发 [DetailDataEffect.QuoteApplied] 而不直接播 ticker。 */
    private fun applyQuote(input: Quote) {
        val old = state.quote
        // 序列只增不减：快照先行到达时 timeline/kLines 为空，绝不能用空序列覆盖
        // 已有分时——否则图表塌成一条昨收基线横线、K线页签空白。真实序列到达
        // （非空）时才整体替换。
        var next = input
        if (next.timeline.isEmpty() && old.timeline.isNotEmpty()) next = next.copy(timeline = old.timeline)
        if (next.kLines.isEmpty() && old.kLines.isNotEmpty()) next = next.copy(kLines = old.kLines)
        if (next.weekKLines.isEmpty() && old.weekKLines.isNotEmpty()) next = next.copy(weekKLines = old.weekKLines)
        if (next.monthKLines.isEmpty() && old.monthKLines.isNotEmpty()) next = next.copy(monthKLines = old.monthKLines)
        val changed = old.price != next.price || old.changePercent != next.changePercent
        val timelineJustArrived = next.timeline.isNotEmpty() && old.timeline.isEmpty()
        state.quote = next
        onEffect(DetailDataEffect.QuoteApplied(old, next, changed, timelineJustArrived))
    }

    fun onDestroy() {
        dataRevision++
        tasks.forEach(DetailDataScheduledTask::cancel)
        tasks.clear()
    }

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }
}
