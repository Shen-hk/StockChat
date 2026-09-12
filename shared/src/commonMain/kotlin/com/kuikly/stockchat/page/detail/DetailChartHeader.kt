package com.kuikly.stockchat.page.detail

import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chart.model.KLineCalculator
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.MarketTimelineSpec
import com.kuikly.stockchat.data.provider.Quote
import com.tencent.kuikly.core.base.Color

/**
 * 图表检查 Hero 的声明式输入。它是页面状态的只读快照，不持有 View、Timer 或平台模块。
 *
 * 分时十字线与 K 线选中态共用这一契约，避免展示规则再次散落回 [StockDetailPage]。
 */
internal data class DetailChartHeaderInput(
    val quote: Quote,
    val mode: StockChartMode,
    val period: StockChartPeriod,
    val crosshairIndex: Int,
    val selectedKLineIndex: Int,
    val theme: StockChatTheme,
    val fallbackTone: Color,
    val fallbackCaption: String,
    val amplitudePercent: Double,
)

/** UI DSL 直接消费的只读展示模型；不包含导航、刷新或手势副作用。 */
internal data class DetailChartHeaderSnapshot(
    val priceText: String,
    val change: Double,
    val changePercent: Double,
    val tone: Color,
    val caption: String,
    val metrics: List<DetailMetric>,
)

/** 单个检查指标。颜色为可选，未指定时由 DSL 使用次级文字色。 */
internal data class DetailMetric(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
)

/**
 * 详情图表的纯展示规则。
 *
 * 这个对象故意不接收 Pager：页面负责把 observable 状态读成 [DetailChartHeaderInput]，
 * DSL 只渲染返回快照。因此选中图表时不会引入新的响应式依赖或动画驱动。
 */
internal object DetailChartHeaderResolver {
    fun resolve(input: DetailChartHeaderInput): DetailChartHeaderSnapshot {
        val quote = input.quote
        if (input.mode == StockChartMode.TIMELINE) {
            val point = quote.timeline.getOrNull(input.crosshairIndex)
            if (point != null && quote.previousClose > 0.0) {
                val change = point.price - quote.previousClose
                val changePercent = change / quote.previousClose * 100.0
                val averages = TimeLineCalculator.averagePrices(
                    quote.timeline,
                    quote.previousClose,
                    MarketTimelineSpec.forSymbol(quote.symbol).lotSize,
                )
                return DetailChartHeaderSnapshot(
                    priceText = Format.price(point.price),
                    change = change,
                    changePercent = changePercent,
                    tone = marketColor(quote, point.price, input.theme),
                    caption = "查看 ${point.time} 分时 · 均价 ${Format.price(averages[input.crosshairIndex])} · 量 ${Format.compactAmount(point.volume)}手",
                    metrics = quoteMetrics(
                        quote = quote,
                        theme = input.theme,
                        volume = point.volume,
                        amount = point.amount,
                        changePercent = changePercent,
                        amplitudePercent = input.amplitudePercent,
                    ),
                )
            }
        } else if (input.mode == StockChartMode.K_LINE) {
            val lines = when (input.period) {
                StockChartPeriod.DAY -> quote.kLines
                StockChartPeriod.WEEK -> quote.weekKLines.ifEmpty {
                    KLineCalculator.aggregate(quote.kLines, input.period.grouping)
                }
                StockChartPeriod.MONTH -> quote.monthKLines.ifEmpty {
                    KLineCalculator.aggregate(quote.kLines, input.period.grouping)
                }
            }
            val candle = lines.getOrNull(input.selectedKLineIndex)
            if (candle != null) {
                val previous = lines.getOrNull(input.selectedKLineIndex - 1)?.close ?: candle.open
                val change = candle.close - previous
                val changePercent = if (previous == 0.0) 0.0 else change / previous * 100.0
                return DetailChartHeaderSnapshot(
                    priceText = Format.price(candle.close),
                    change = change,
                    changePercent = changePercent,
                    tone = if (candle.close >= candle.open) input.theme.rise else input.theme.fall,
                    caption = "查看 ${candle.date} ${periodLabel(input.period)} · 收盘价与当根 OHLC 已同步",
                    metrics = listOf(
                        DetailMetric("开", Format.price(candle.open), marketColor(quote, candle.open, input.theme)),
                        DetailMetric("高", Format.price(candle.high), marketColor(quote, candle.high, input.theme)),
                        DetailMetric("低", Format.price(candle.low), marketColor(quote, candle.low, input.theme)),
                        DetailMetric("涨跌", Format.percent(changePercent), if (change >= 0.0) input.theme.rise else input.theme.fall),
                        DetailMetric("成交量", Format.compactAmount(candle.volume), input.theme.textSecondary),
                        DetailMetric("成交额", Format.compactAmount(quote.amount), input.theme.textSecondary),
                        DetailMetric("换手", Format.ratioOrDash(quote.turnoverRate), input.theme.textSecondary),
                        DetailMetric("振幅", Format.percent(if (previous == 0.0) 0.0 else (candle.high - candle.low) / previous * 100.0), input.theme.textSecondary),
                    ),
                )
            }
        }
        return DetailChartHeaderSnapshot(
            priceText = Format.price(quote.price),
            change = quote.change,
            changePercent = quote.changePercent,
            tone = input.fallbackTone,
            caption = input.fallbackCaption,
            metrics = quoteMetrics(
                quote = quote,
                theme = input.theme,
                amplitudePercent = input.amplitudePercent,
            ),
        )
    }

    private fun quoteMetrics(
        quote: Quote,
        theme: StockChatTheme,
        volume: Double = quote.volume,
        amount: Double = quote.amount,
        changePercent: Double = quote.changePercent,
        amplitudePercent: Double,
    ) = listOf(
        DetailMetric("今开", Format.price(quote.open), marketColor(quote, quote.open, theme)),
        DetailMetric("最高", Format.price(quote.high), marketColor(quote, quote.high, theme)),
        DetailMetric("最低", Format.price(quote.low), marketColor(quote, quote.low, theme)),
        DetailMetric("涨跌", Format.percent(changePercent), if (changePercent >= 0.0) theme.rise else theme.fall),
        DetailMetric("成交量", Format.compactAmount(volume), theme.textSecondary),
        DetailMetric("成交额", Format.compactAmount(amount), theme.textSecondary),
        DetailMetric("换手", Format.ratioOrDash(quote.turnoverRate), theme.textSecondary),
        DetailMetric("振幅", Format.percent(amplitudePercent), theme.textSecondary),
    )

    private fun marketColor(quote: Quote, value: Double, theme: StockChatTheme) = when {
        quote.previousClose <= 0.0 -> theme.textSecondary
        value > quote.previousClose -> theme.rise
        value < quote.previousClose -> theme.fall
        else -> theme.textSecondary
    }

    private fun periodLabel(period: StockChartPeriod) = when (period) {
        StockChartPeriod.DAY -> "日K"
        StockChartPeriod.WEEK -> "周K"
        StockChartPeriod.MONTH -> "月K"
    }
}
