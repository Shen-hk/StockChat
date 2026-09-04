package com.kuikly.stockchat.cards.stock

/**
 * 分时走势图 —— 用 KuiklyChartKit DSL 重写（替代自研 MiniTimeline）。
 *
 * 数据来源：Quote.timeline: List<QuotePoint>(time, price, volume) + Quote.previousClose 基准。
 * K线（日/周/月蜡烛）仍走自研 KLineChart，本文件不触碰。
 *
 * 接入前提（见 docs/18）：
 *   1) 把 Shen-hk/KuiklyChartKit 以 submodule 源码接入，:shared 依赖 implementation(project(":chartkit"))
 *   2) chartkit 随本工程 Kuikly 2.25.0 一起编译（其 README 写的是 2.7.0，需重跑它的 44 个单测确认）
 *
 * 颜色用浅色版 StockChatTheme token（接入后改为读 context.theme，避免硬编码）。
 */

import com.kuikly.core.base.Color
import com.kuikly.core.base.ViewContainer
import com.kuikly.core.base.ViewRef
import com.kuikly.kuiklychartkit.chart.BarChart
import com.kuikly.kuiklychartkit.chart.Chart
import com.kuikly.kuiklychartkit.chart.ChartAnimationEasing
import com.kuikly.kuiklychartkit.chart.ChartView
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.data.provider.Quote

// 浅色主题 token（与 StockChatTheme 浅色版对齐：rise #D92E2E / fall #0C7A45 / grid #E2E8F0 / ink3 #69748F / page #F5F6F8）
private val RISE = Color(0xFFD92E2E)
private val FALL = Color(0xFF0C7A45)
private val GRID = Color(0xFFE2E8F0)
private val INK3 = Color(0xFF69748F)
private val PAGE = Color(0xFFF5F6F8)

// 主图 / 副图 ViewRef，用于视窗联动
private var timelineRef: ViewRef<ChartView>? = null
private var volumeRef: ViewRef<ChartView>? = null

internal fun KuiklyTimelineChart(
    container: ViewContainer<*, *>,
    quote: Quote,
    context: CardContext,
    height: Float = 132f,
    onPointSelected: (label: String, value: Float) -> Unit = { _, _ -> },
) {
    val pts = quote.timeline
    if (pts.isEmpty()) return

    val prices = pts.map { it.price.toFloat() }.toTypedArray()
    val times = pts.map { it.time }.toTypedArray()
    val vols = pts.map { it.volume.toFloat() }.toTypedArray()
    val baseline = quote.previousClose.toFloat()
    val flat = Array(pts.size) { baseline }                       // 昨收基准线（库只支持 includeZero，用恒定 line 系列模拟）
    val lineColor = if (quote.rising) RISE else FALL
    val barColors = pts.map { if (it.price >= baseline) RISE else FALL }.toTypedArray()

    // —— 主图：分时面积 + 昨收基准线（混合图 Chart，可同时放 area + line）——
    Chart {
        ref { timelineRef = it }
        attr { height(height); marginTop(10f); alignSelfStretch() }
        chart {
            labels(*times)
            area("价格", *prices) {
                color(lineColor)
                smooth()
                // 面积渐变填充：库支持，若 fillAlpha 未暴露可去掉（AreaChart 默认带填充）
                // fillAlpha(0.12f)
            }
            line("昨收", *flat) { color(GRID) }                    // 虚线基准：line 不支持 dashed，用浅灰实线表达
            axes {
                y { formatter = { "%.2f".format(it) } }            // x 时间轴交给 Tooltip，不堆网格
            }
            grid { horizontal = true; dashed(4f, 4f) }
            interaction {
                selectionEnabled = true                           // 十字线 + Tooltip（库自带，省去自研命中）
                panEnabled = true                                  // 单指横向拖动，不阻塞页面纵向滚动
                zoomEnabled = false                                // 分时不缩放，避免误触
            }
            animation { enabled = true; durationMillis = 600; easing = ChartAnimationEasing.EASE_IN_OUT }
            theme {
                backgroundColor = PAGE
                mutedTextColor = INK3
                gridColor = GRID
                contentPadding(12f)
            }
        }
        event {
            pointSelected { sel -> onPointSelected(sel.label, sel.value.toFloat()) }
            viewportChanged { vp -> volumeRef?.view?.setViewport(vp.startIndex, vp.endIndex) }  // 副图同步视窗
        }
    }

    // —— 成交量副图（与主图视窗联动）——
    BarChart {
        ref { volumeRef = it }
        attr { height(56f); marginTop(6f); alignSelfStretch() }
        chart {
            labels(*times)
            series("成交量", *vols) {
                barColors(*barColors)                              // 逐根染红/绿（=涨/跌）
                valueLabels()
            }
            axes { /* x / y 全隐藏，副图只需柱 */ }
            grid { horizontal = false }
            interaction { selectionEnabled = false; panEnabled = true; zoomEnabled = false }
            theme { backgroundColor = PAGE; contentPadding(12f) }
        }
    }
}
