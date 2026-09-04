package com.kuikly.stockchat.cards.stock

/**
 * 分时走势图 —— 用 KuiklyChartKit DSL 重写（替代自研 MiniTimeline）。
 *
 * 数据来源：Quote.timeline: List<QuotePoint>(time, price, volume) + Quote.previousClose 基准。
 * K线（日/周/月蜡烛）仍走自研 KLineChart，本文件不触碰。
 *
 * 接入前提：KuiklyChartKit 以 submodule 源码接入（settings.gradle.kts 的 :chartkit），
 *           :shared 依赖 implementation(project(":chartkit"))。
 *
 * 真实 DSL 注意点（README 与实现不符，已对照源码核对）：
 *  - 入口是 AreaChart/BarChart/LineChart 等 ComposeView 扩展，lambda 接收者为对应 View；
 *    init 扩展挂在 ViewContainer<*,*> 上，本文件用 container.AreaChart { } / container.BarChart { } 调用。
 *  - AreaChart 会为每条 series 填到 0，因此不能用「第二条恒定 series」画基准（会填出一整块）。
 *    改为把 yAxis 范围对称到 previousClose 上下，使昨收落在视觉中线；并用 area { fillColors }
 *    设置与折线同色的半透明填充。
 *  - BarChart 数据方法是 data(vararg ChartSeries<BarEntry>)，没有 barData（barData 仅 MixedChart 有）。
 *  - 成交量副图与主图各自独立渲染（库未暴露公开命令式 setViewport），不做视窗联动。
 *  - interaction.maxRenderPointCount 校验要求 >= 4，分时点数可能不足，故不设置（走自动预算）。
 *  - 颜色读取 CardContext.theme，避免图表与页面主题出现两份真值。
 */

import com.tencent.kuikly.core.base.ViewContainer
import com.kuikly.kuiklychartkit.chart.AreaChart
import com.kuikly.kuiklychartkit.chart.BarChart
import com.kuikly.kuiklychartkit.chart.BarEntry
import com.kuikly.kuiklychartkit.chart.ChartPoint
import com.kuikly.kuiklychartkit.chart.ChartSeries
import com.kuikly.kuiklychartkit.chart.ChartTheme
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.data.provider.Quote

internal fun KuiklyTimelineChart(
    container: ViewContainer<*, *>,
    quote: Quote,
    context: CardContext,
    height: Float = 132f,
    onPointSelected: (label: String, value: Float) -> Unit = { _, _ -> },
) {
    val pts = quote.timeline
    if (pts.isEmpty()) return

    val appTheme = context.theme
    val isRising = quote.rising
    val lineColor = if (isRising) appTheme.rise else appTheme.fall
    val fillColor = lineColor.opacity(0.20f)
    val prices = pts.map { it.price.toFloat() }
    val baseline = quote.previousClose.toFloat()

    // yAxis 范围对称到 previousClose 上下，使昨收落在视觉中线（替代自研的虚线基准）
    val lo = minOf(prices.minOrNull() ?: baseline, baseline)
    val hi = maxOf(prices.maxOrNull() ?: baseline, baseline)
    val pad = ((hi - lo) * 0.12f).coerceAtLeast(0.01f)

    val pointSeries = ChartSeries(
        name = "价格",
        items = pts.mapIndexed { i, p -> ChartPoint(x = i.toFloat(), y = p.price.toFloat(), label = p.time) },
        color = lineColor,
    )
    val timelineTheme = ChartTheme.light().copy(
        backgroundColor = appTheme.page,
        axisColor = appTheme.divider,
        gridColor = appTheme.divider,
        labelColor = appTheme.textTertiary,
        selectionColor = lineColor,
    )

    // —— 主图：分时面积图（带 Tooltip / 数据过渡动画）——
    container.AreaChart {
        attr {
            height(height)
            marginTop(10f)
            alignSelfStretch()
            theme = timelineTheme
            data(pointSeries)
            xAxis { visible = false }
            yAxis {
                visible = true
                includeZero = false
                min = lo - pad
                max = hi + pad
                tickCount = 4
            }
            line { smooth = true; showPoints = false; lineWidth = 2f }
            area { fillColors = listOf(fillColor) }
            legend { visible = false }
            grid { visible = true }
            tooltip { enabled = true }
            dataTransition { enabled = true; durationMs = 420 }
        }
        event {
            onItemSelected { sel -> onPointSelected(sel.item.label, sel.item.y) }
        }
    }

    // —— 成交量副图（独立渲染，单一色）——
    val barSeries = ChartSeries(
        name = "成交量",
        items = pts.map { BarEntry(it.time, it.volume.toFloat()) },
        color = appTheme.textTertiary.opacity(0.38f),
    )
    container.BarChart {
        attr {
            height(56f)
            marginTop(6f)
            alignSelfStretch()
            theme = timelineTheme
            data(barSeries)
            xAxis { visible = false }
            yAxis { visible = false }
            legend { visible = false }
            bars { showValueLabels = false; cornerRadius = 2f }
            tooltip { enabled = true }
            dataTransition { enabled = true; durationMs = 420 }
        }
    }
}
