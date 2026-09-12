package com.kuikly.stockchat.risk.domain

import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.LimitUpStock
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.Quote
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 风险地图纯计算域（doc 47 B-3 第 1 步「把计算模型纯化」）：
 * 输入只包含 **自选行（含行情）/ 行业归属 / 指数行情 / 事件 / 涨停池**，
 * **不得读取任何 Store / Pager / Kuikly 类型**（门禁硬指标：`risk/domain` 零
 * Kuikly import）。输出全部是不可变数据，同输入恒同输出，可纯单测。
 *
 * 口径全部逐值搬运自原 `RiskMapPage`（2026-09-12 前的版本），禁止顺手改数值：
 * 波动窗口 [CORRELATION_WINDOW]、最少样本 [MIN_RETURN_DAYS]、单链判定阈值
 * (>50% / CR3>70%)、跑输阈值 -0.3pct、AI 触点阈值 (CR3>0.60 / 波动比>1.5)。
 */

/** 波动基准 = 沪深300（FR-R7，更接近「大盘组合」口径）。 */
const val RISK_INDEX_SYMBOL = "000300.SH"

/** 相关性 / 波动窗口（近 N 个交易日）。 */
const val CORRELATION_WINDOW = 60

/** 相关系数与组合标准差的最少样本天数。 */
const val MIN_RETURN_DAYS = 30

/** 快照日期粗粒度换算用毫秒/天。 */
const val DAY_MS = 24L * 60 * 60 * 1000

/** 风险地图的一只自选行（symbol 唯一；quote 在行情到达前为 null）。 */
data class RiskRow(
    val symbol: String,
    val name: String,
    val quote: Quote? = null,
)

/** 行业分组（等权 = 只数占比）。 */
data class IndustryStat(
    val name: String,
    val count: Int,
    val members: List<RiskRow>,
)

/** 单链集中判定结果。 */
data class ChainConcentration(
    val triggered: Boolean,
    val topName: String,
    val topCount: Int,
    val cr3: Double,
    val cr3Count: Int,
)

/** 行业分组（等权 = 只数占比）；行业未知的归「未分类」。 */
fun industryStats(rows: List<RiskRow>, industries: Map<String, String>): List<IndustryStat> {
    if (rows.isEmpty()) return emptyList()
    return rows.groupBy { industries[it.symbol]?.takeIf { name -> name.isNotBlank() } ?: "未分类" }
        .map { (name, members) -> IndustryStat(name, members.size, members) }
        .sortedByDescending { it.count }
}

/** 单链集中判定：单一行业 >50%，或前三大行业合计 >70%。 */
fun chainConcentration(
    rows: List<RiskRow>,
    industry: List<IndustryStat>,
): ChainConcentration {
    val total = rows.size
    if (total == 0 || industry.isEmpty()) return ChainConcentration(false, "", 0, 0.0, 0)
    val top = industry.first()
    val cr3Count = industry.take(3).sumOf { it.count }
    val cr3 = cr3Count.toDouble() / total
    val topWeight = top.count.toDouble() / total
    return ChainConcentration(
        triggered = topWeight > 0.50 || cr3 > 0.70,
        topName = top.name,
        topCount = top.count,
        cr3 = cr3,
        cr3Count = cr3Count,
    )
}

/** 日收益率序列（date → close/prevClose - 1），只取最近 [CORRELATION_WINDOW] 根。 */
fun dailyReturns(quote: Quote?): Map<String, Double>? {
    val bars = quote?.kLines.orEmpty()
    if (bars.size < 2) return null
    val window = bars.takeLast(CORRELATION_WINDOW + 1)
    return buildMap {
        for (i in 1 until window.size) {
            val prev = window[i - 1].close
            if (prev > 0.0) {
                put(window[i].date, window[i].close / prev - 1.0)
            }
        }
    }
}

fun pearson(a: Map<String, Double>, b: Map<String, Double>): Double? {
    val common = a.keys.intersect(b.keys)
    if (common.size < MIN_RETURN_DAYS) return null
    val xs = common.map { a.getValue(it) }
    val ys = common.map { b.getValue(it) }
    val n = common.size.toDouble()
    val mx = xs.average()
    val my = ys.average()
    var cov = 0.0
    var vx = 0.0
    var vy = 0.0
    repeat(xs.size) { i ->
        val dx = xs[i] - mx
        val dy = ys[i] - my
        cov += dx * dy
        vx += dx * dx
        vy += dy * dy
    }
    if (vx <= 0.0 || vy <= 0.0) return null
    return (cov / sqrt(vx * vy)).coerceIn(-1.0, 1.0)
}

fun sameDirectionDays(a: Map<String, Double>, b: Map<String, Double>): Int =
    a.keys.intersect(b.keys).count { date ->
        (a.getValue(date) > 0 && b.getValue(date) > 0) || (a.getValue(date) < 0 && b.getValue(date) < 0)
    }

fun stdOf(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.average()
    val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
    return sqrt(variance)
}

/** 等权组合日收益 = 各成员当日收益的算术平均（按有数据的成员计）。 */
fun portfolioStd(rows: List<RiskRow>): Double? {
    val members = rows.mapNotNull { dailyReturns(it.quote) }
    if (members.size < 2) return null
    val dates = members.flatMap { it.keys }.toSet()
    val portfolio = dates.mapNotNull { date ->
        val values = members.mapNotNull { it[date] }
        if (values.isNotEmpty()) values.average() else null
    }
    if (portfolio.size < MIN_RETURN_DAYS) return null
    return stdOf(portfolio)
}

/** 组合日波动 ÷ 大盘日波动（与波动暴露面板 / headline 同口径）。 */
fun portfolioVolRatio(rows: List<RiskRow>, indexQuote: Quote?): Double? {
    val std = portfolioStd(rows) ?: return null
    val indexStd = dailyReturns(indexQuote)?.let { stdOf(it.values.toList()) } ?: return null
    return if (indexStd > 0.0) std / indexStd else null
}

/** 相关性里 |r| 最大的一对（key "A|B" → 名字对 + r）；无数据返回 null。 */
fun bestCorrelationPair(
    rows: List<RiskRow>,
    correlations: Map<String, Double>,
): Pair<Pair<String, String>, Double>? {
    val best = correlations.entries.maxByOrNull { abs(it.value) } ?: return null
    val (a, b) = best.key.split("|")
    val nameA = rows.firstOrNull { it.symbol == a }?.name ?: a
    val nameB = rows.firstOrNull { it.symbol == b }?.name ?: b
    return (nameA to nameB) to best.value
}

/** 重算两两相关系数（key "A|B"，A 在列表序在前）；星图连线数据源。 */
fun computeCorrelations(rows: List<RiskRow>): Map<String, Double> {
    val returns = rows.mapNotNull { row ->
        dailyReturns(row.quote)?.let { row.symbol to it }
    }
    val cors = HashMap<String, Double>()
    for (i in returns.indices) {
        for (j in i + 1 until returns.size) {
            pearson(returns[i].second, returns[j].second)?.let { r ->
                cors["${returns[i].first}|${returns[j].first}"] = r
            }
        }
    }
    return cors
}

/** 重算个股日波动 ÷ 沪深300 日波动（等权口径）；颠簸层光晕数据源。 */
fun computeVolRatios(rows: List<RiskRow>, indexQuote: Quote?): Map<String, Double> {
    val returns = rows.mapNotNull { row ->
        dailyReturns(row.quote)?.let { row.symbol to it }
    }
    val indexStd = dailyReturns(indexQuote)?.let { stdOf(it.values.toList()) }
    return if (indexStd != null && indexStd > 0.0) {
        returns.associate { (symbol, rets) -> symbol to stdOf(rets.values.toList()) / indexStd }
    } else {
        emptyMap()
    }
}

/** FR-R9 跑输大盘归因通路：组合等权涨跌幅 - 大盘涨跌幅 ≤ -0.3pct 才触发。 */
fun underperformGapPct(rows: List<RiskRow>, indexQuote: Quote?): Double? {
    val indexPct = indexQuote?.changePercent ?: return null
    val quotes = rows.mapNotNull { it.quote }
    if (quotes.isEmpty()) return null
    val gap = quotes.map { it.changePercent }.average() - indexPct
    return if (gap <= -0.3) gap else null
}

/** L1 结论：纯规则，中性措辞，不预判方向。 */
fun headline(
    rows: List<RiskRow>,
    industry: List<IndustryStat>,
    chain: ChainConcentration,
    indexQuote: Quote?,
): String {
    val total = rows.size
    val ratio = portfolioVolRatio(rows, indexQuote)
    return when {
        chain.triggered && chain.topName != "未分类" ->
            "你关注的 $total 只，有 ${chain.topCount} 只押在同一条链上（${chain.topName}）"
        ratio != null && ratio > 1.5 ->
            "$total 只自选的等权波动是大盘的 ${Format.price(ratio)} 倍"
        else -> "$total 只自选的共同暴露画像：单链集中不明显"
    }
}

/** 展示辅助：只数占比 → "37%"。 */
fun weightLabel(count: Int, total: Int): String =
    if (total <= 0) "0%" else "${(count * 100.0 / total).toInt()}%"

/** 展示辅助：份额 → "37%"。 */
fun weightLabel(share: Double): String = "${(share * 100).toInt()}%"

/** 展示辅助：相关系数带符号；null = 样本不足。 */
fun coefficientLabel(r: Double?): String =
    if (r == null) "样本不足" else "${if (r >= 0) "+" else ""}${Format.price(r)}"

// ── 星图纯映射（焦点注释 / 成员摘要 / AI 事实槽位的唯一事实来源） ──

/** 星图事件匹配：代码主串一致即视为同一标的的事件。 */
fun skyEventsOf(symbol: String, events: List<MarketCalendarEvent>): List<MarketCalendarEvent> {
    val code = symbol.substringBefore('.')
    return events.filter { it.symbol == symbol || it.symbol.substringBefore('.') == code }
}

/** 焦点注释（Spotlight）：全端侧模板，数字来自 Provider，零 LLM。 */
fun skyFocusNote(
    symbol: String,
    rows: List<RiskRow>,
    correlations: Map<String, Double>,
    volRatios: Map<String, Double>,
    limitUps: List<Pair<LimitUpStock, RiskRow>>,
    events: List<MarketCalendarEvent>,
): String {
    val row = rows.firstOrNull { it.symbol == symbol } ?: return ""
    val strong = rows.count { other ->
        other.symbol != symbol &&
            StarLayout.lookupCorrelation(correlations, symbol, other.symbol)
                ?.let { abs(it) > 0.6 } == true
    }
    val parts = mutableListOf<String>()
    parts += if (strong > 0) {
        "与 $strong 只相关系数>0.6"
    } else {
        "与谁都不连（近 $CORRELATION_WINDOW 日）"
    }
    volRatios[symbol]?.let { parts += "波动 ${Format.decimal(it, 1)}×大盘（等权估算）" }
    val boards = limitUps.firstOrNull { it.second.symbol == symbol }?.first?.consecutiveBoards ?: 0
    if (boards > 0) parts += "今日 $boards 连板"
    skyEventsOf(symbol, events).firstOrNull()?.let {
        parts += "${it.date.substring(5)} ${it.kind.label}已预约"
    }
    return "「${row.name}」：${parts.joinToString(" · ")}"
}

/** 成员抽屉摘要行：行业 + 事件 + 连板 + 波动（有啥写啥，全部事实）。 */
fun skyStarSub(
    row: RiskRow,
    industries: Map<String, String>,
    volRatios: Map<String, Double>,
    limitUps: List<Pair<LimitUpStock, RiskRow>>,
    events: List<MarketCalendarEvent>,
): String {
    val parts = mutableListOf<String>()
    parts += industries[row.symbol]?.takeIf { n -> n.isNotBlank() } ?: "未分类"
    skyEventsOf(row.symbol, events).firstOrNull()?.let {
        parts += "${it.date.substring(5)} ${it.kind.label}"
    }
    val boards = limitUps.firstOrNull { it.second.symbol == row.symbol }?.first?.consecutiveBoards ?: 0
    if (boards > 0) parts += "$boards 连板"
    volRatios[row.symbol]?.let { parts += "波动 ${Format.decimal(it, 1)}×大盘" }
    return parts.joinToString(" · ")
}

/**
 * 卡底 AI 详细解读的端侧事实槽位（唯一事实来源，模型只解读不编数字）。
 * 与 prompt / 端侧速览共用同一批事实，口径与原 RiskMapPage 逐句一致。
 */
data class RiskAiFacts(
    val total: Int,
    val industrySummary: String?,
    val bestPair: String?,
    val volRatio: String?,
    val upcomingEvents: List<String>,
    val strongestLimitUp: String?,
    val todayMoves: List<String>,
    /** 端侧速览口径的原始槽位（不经过 prompt 文案反解析）。 */
    val topChainName: String = "",
    val topChainCount: Int = 0,
    val topChainCr3Label: String = "",
    val bestPairNames: Pair<String, String>? = null,
    val bestPairLabel: String = "",
    val volRatioValue: Double? = null,
)

fun buildRiskAiFacts(
    rows: List<RiskRow>,
    industries: Map<String, String>,
    correlations: Map<String, Double>,
    indexQuote: Quote?,
    events: List<MarketCalendarEvent>,
    limitUps: List<Pair<LimitUpStock, RiskRow>>,
): RiskAiFacts {
    val total = rows.size
    val industry = industryStats(rows, industries)
    val chain = chainConcentration(rows, industry)
    return RiskAiFacts(
        total = total,
        industrySummary = if (industry.isEmpty()) {
            null
        } else {
            "最挤的链：${chain.topName} ${chain.topCount} 只，前三大行业合计 ${weightLabel(chain.cr3Count, total)}"
        },
        bestPair = bestCorrelationPair(rows, correlations)?.let { (names, r) ->
            "最相关的一对：${names.first} 与 ${names.second}，相关系数 ${coefficientLabel(r)}（近 $CORRELATION_WINDOW 日）"
        },
        volRatio = portfolioVolRatio(rows, indexQuote)?.let { ratio ->
            "组合日波动约为沪深300 的 ${Format.price(ratio)} 倍（等权）"
        },
        upcomingEvents = events.take(3).map { event ->
            "已预约事件：${event.date} ${event.name} ${event.kind.label}"
        },
        strongestLimitUp = limitUps.maxByOrNull { it.first.consecutiveBoards }?.let { (limitUp, row) ->
            "今日连板最强：${row.name} ${limitUp.consecutiveBoards} 连板"
        },
        todayMoves = rows.take(6).mapNotNull { row ->
            row.quote?.changePercent?.let { pct -> "今日 ${row.name} ${Format.percent(pct)}" }
        },
    )
}

/** prompt 用事实行（原 buildSkyAiPrompt 的 facts 列表，逐句一致）。 */
fun RiskAiFacts.toFactLines(): List<String> = buildList {
    add("自选共 $total 只（等权视角，非真实仓位）")
    industrySummary?.let(::add)
    bestPair?.let(::add)
    volRatio?.let(::add)
    addAll(upcomingEvents)
    strongestLimitUp?.let(::add)
    addAll(todayMoves)
}

/** 端侧速览（未配置/失败时的兜底内容，与 prompt 共用同一批事实；原 skyAiLocalSummary 逐句一致）。 */
fun riskAiLocalSummary(facts: RiskAiFacts, firstEvent: MarketCalendarEvent?): String {
    val parts = mutableListOf<String>()
    if (facts.topChainName.isNotEmpty()) {
        parts += "最挤的链是${facts.topChainName}（${facts.topChainCount} 只，前三大合计 ${facts.topChainCr3Label}）"
    }
    facts.bestPairNames?.let { pair ->
        if (facts.bestPairLabel.isNotEmpty()) parts += "最相关的一对是${pair.first}和${pair.second}（${facts.bestPairLabel}）"
    }
    facts.volRatioValue?.let { parts += "组合日波动约为大盘 ${Format.price(it)} 倍" }
    firstEvent?.let {
        parts += "最近的事件是${it.date.substring(5)}${it.name}${it.kind.label}"
    }
    if (parts.isEmpty()) parts += "行情数据还在路上，稍等片刻再生成"
    return "端侧速览：" + parts.joinToString("；") + "。"
}
