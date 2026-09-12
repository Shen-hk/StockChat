package com.kuikly.stockchat.detail.ai.state

import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.MarketTimelineSpec
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.detail.domain.AnchorIndex
import com.kuikly.stockchat.detail.page.component.detailTimelineSeries

/**
 * AI 解读 prompt 的端侧事实槽位算法（docs/43 D3：产品不可变行为，逐字不能改）。
 * `buildAiInsightPrompt` 与 `buildCircleAiPrompt` 是与原
 * StockDetailPage 中同名私有函数**逐字对齐**的提取版，仅把 `quote` / `insight`
 * / `newsList` 等页面字段改作函数参数；模板、硬性要求段落、事实条目组装顺序
 * 均原样保留。
 */
internal fun buildAiInsightPrompt(
    quote: Quote,
    insight: StockInsightBundle,
    newsList: List<NewsItem>,
    timelineSeries: List<Double>,
): String {
    val timeline = quote.timeline
    val highPoint = timeline.maxByOrNull { it.price }
    val lowPoint = timeline.minByOrNull { it.price }
    val morningClose = timelineSeries.getOrNull(119)
    val openVsPrev = if (quote.previousClose > 0.0) {
        Format.percent((quote.open - quote.previousClose) / quote.previousClose * 100.0)
    } else "--"
    val facts = buildList {
        add("现价 ${Format.price(quote.price)}（${Format.percent(quote.changePercent)}），昨收 ${Format.price(quote.previousClose)}，开盘 ${Format.price(quote.open)}（较昨收 $openVsPrev）")
        if (highPoint != null) add("日内最高 ${Format.price(quote.high)}（出现于 ${highPoint.time}）")
        if (lowPoint != null) add("日内最低 ${Format.price(quote.low)}（出现于 ${lowPoint.time}）")
        if (morningClose != null && morningClose != 0.0 && timelineSeries.size > 120) {
            add("上午收盘（11:30）${Format.price(morningClose)}，午后至今 ${Format.percent((quote.price - morningClose) / morningClose * 100.0)}")
        }
        insight.fundFlow?.let {
            add("今日主力资金净${if (it.main >= 0) "流入" else "流出"} ${Format.compactAmount(kotlin.math.abs(it.main))}")
        }
        insight.fundamentals?.financial?.let {
            add("最新财报（${it.reportDate}）：营收同比 ${Format.percent(it.revenueYoY)}，净利润同比 ${Format.percent(it.profitYoY)}")
        }
        if (newsList.isNotEmpty()) {
            add("近期资讯标题：${newsList.take(3).joinToString("；") { it.title }}")
        }
    }
    return buildString {
        appendLine("你是 A 股个股解读助手。请基于下面的今日真实数据，用 3-4 句简体中文解读 ${quote.name}（${quote.symbol}）今天的盘面走势。")
        appendLine()
        appendLine("硬性要求：")
        appendLine("1. 每句话必须至少引用一个具体分时时间点（HH:MM，仅限 09:30-11:30 或 13:00-15:00），端侧会按句内时间在分时图上定位高亮区间；没有时间依据的句子不要写。")
        appendLine("2. 只陈述与解释以上数据体现的事实，不预测后续涨跌，不给出买卖、仓位建议。")
        appendLine("3. 直接输出句子，每句以句号结尾；不要小标题、序号、加粗、markdown 或任何卡片协议。")
        appendLine()
        appendLine("今日数据（唯一事实来源，禁止编造未提供的数字）：")
        facts.forEach { appendLine("- $it") }
    }
}

internal fun buildCircleAiPrompt(
    quote: Quote,
    timelineSeries: List<Double>,
    lo: Int,
    hi: Int,
): String {
    val timeline = quote.timeline
    val seg = timeline.subList(lo, (hi + 1).coerceAtMost(timeline.size))
    val p0 = timelineSeries[lo]
    val p1 = timelineSeries[hi]
    val pct = if (p0 != 0.0) (p1 - p0) / p0 * 100.0 else 0.0
    val highPt = seg.maxByOrNull { it.price }
    val lowPt = seg.minByOrNull { it.price }
    // 区间终点与均价线关系（均价 = 真实 amount 口径，与图上虚线一致）
    val averages = TimeLineCalculator.averagePrices(
        timeline,
        quote.previousClose,
        MarketTimelineSpec.forSymbol(quote.symbol).lotSize,
    )
    val avgEnd = averages.getOrNull(hi)
    val vsAvg = if (avgEnd != null && avgEnd > 0.0) {
        "区间终点${if (p1 >= avgEnd) "高于" else "低于"}均价线（${Format.price(avgEnd)}）"
    } else null
    // 区间量能 vs 全天每分钟均量（放量/缩量，只述倍数事实）
    val dayAvgVol = timeline.map { it.volume }.average().takeIf { !it.isNaN() } ?: 0.0
    val segAvgVol = seg.map { it.volume }.average().takeIf { !it.isNaN() } ?: 0.0
    val volDesc = if (dayAvgVol > 0.0) {
        val ratio = segAvgVol / dayAvgVol
        when {
            ratio >= 1.5 -> "区间量能明显放大（约为全天每分钟均量的 ${Format.decimal(ratio, 1)} 倍）"
            ratio <= 0.6 -> "区间量能收缩（约为全天每分钟均量的 ${Format.decimal(ratio, 1)} 倍）"
            else -> "区间量能与全天每分钟均量相当"
        }
    } else null
    val vsPrev = if (quote.previousClose > 0.0) {
        val a = (p0 - quote.previousClose) / quote.previousClose * 100.0
        val b = (p1 - quote.previousClose) / quote.previousClose * 100.0
        "区间起点较昨收 ${Format.percent(a)}，终点较昨收 ${Format.percent(b)}"
    } else null
    val facts = buildList {
        add("圈选区间：${AnchorIndex.indexToTimeLabel(lo)} 至 ${AnchorIndex.indexToTimeLabel(hi)}")
        add("区间起点 ${Format.price(p0)}，终点 ${Format.price(p1)}，区间涨跌 ${Format.percent(pct)}")
        if (highPt != null) add("区间最高 ${Format.price(highPt.price)}（出现于 ${highPt.time}）")
        if (lowPt != null) add("区间最低 ${Format.price(lowPt.price)}（出现于 ${lowPt.time}）")
        vsAvg?.let { add(it) }
        vsPrev?.let { add(it) }
        volDesc?.let { add(it) }
    }
    return buildString {
        appendLine("你是 A 股个股解读助手。用户刚在 ${quote.name}（${quote.symbol}）当日分时图上圈选了一段区间，请基于下面的圈选区间真实数据，用 2-3 句简体中文解读这段走势（用户正对照分时图阅读，请紧扣区间内事实）。")
        appendLine()
        appendLine("硬性要求：")
        appendLine("1. 每句话必须至少引用一个具体分时时间点（HH:MM，仅限 09:30-11:30 或 13:00-15:00，且必须落在圈选区间内）；没有时间依据的句子不要写。")
        appendLine("2. 只陈述与解释以上数据体现的事实，不预测后续涨跌，不给出买卖、仓位建议。")
        appendLine("3. 直接输出句子，每句以句号结尾；不要小标题、序号、加粗、markdown 或任何卡片协议。")
        appendLine()
        appendLine("圈选区间数据（唯一事实来源，禁止编造未提供的数字）：")
        facts.forEach { appendLine("- $it") }
    }
}

/** 保险清理：防模型偶发卡片协议/markdown 行污染逐句渲染。 */
internal fun sanitizeAiText(raw: String): String = raw
    .lines()
    .filterNot { it.trimStart().startsWith("```") }
    .joinToString("\n")
    .trim()

internal fun splitInsightSentences(source: String): List<String> =
    source.split(Regex("[。，]")).map { it.trim() }.filter { it.isNotEmpty() }