package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.page.TickerText
import com.kuikly.stockchat.page.detail.DetailChartHeaderSnapshot
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * Wave 2 D5：Hero 行情区（卡外价格行 + 十字线/K线选中指标）。从 StockDetailPage.body()
 * 搬出的视觉区块（迁移前 lines 464-582），保持「零行为变更」：仅物理搬迁，渲染口径、
 * 反应式依赖、间距数值与字号逐字对齐原实现。
 *
 * Props：观察/取值闭包（observable 通过闭包读取，attr 内建立反应式依赖）。Actions：本
 * 区块纯展示，无事件回调（自选提示统一在主 View 显示，不属于交互）。
 */
internal fun ViewContainer<*, *>.DetailHeroSection(
    theme: StockChatTheme,
    reduceMotion: Boolean,
    /**
     * 涨跌胶囊底色（涨=红、跌=绿，平盘=辅色），由 Page 端 toneColor() 提供，闭包读取让
     * attr 内随 quote tick 即时刷新。
     */
    toneColor: () -> com.tencent.kuikly.core.base.Color,
    tickerSnapshot: () -> DetailChartHeaderSnapshot,
    previousPriceText: () -> String,
    previousPercentText: () -> String,
    quoteLoading: () -> Boolean,
    tickerLift: () -> Boolean,
    tickerDirectionUp: () -> Boolean,
    /**
     * Hero 仅在选中分时十字线/K线蜡烛时显示对应时点的事实；提供 4 件只读 getter 让
     * 闭包能在 attr 内即时重建（quote/mode/period/index 都参与 vbind 依赖追踪）。
     */
    quote: () -> Quote,
    chartMode: () -> StockChartMode,
    chartPeriod: () -> StockChartPeriod,
    crosshairIndex: () -> Int,
    selectedKLineIndex: () -> Int,
    watchlistHint: () -> String,
) {
    View {
        attr { marginTop(2f) }
        View {
            attr { flexDirectionRow(); alignItemsFlexEnd() }
            TickerText(
                text = { tickerSnapshot().priceText },
                previousText = previousPriceText,
                loading = quoteLoading,
                fontSize = theme.type.display,
                width = { 142f },
                color = { tickerSnapshot().tone },
                theme = theme,
                lift = tickerLift,
                directionUp = tickerDirectionUp,
                reduceMotion = reduceMotion,
            )
            // 涨跌胶囊：宽度按文本长度自适应（lambda 传入，attr 闭包内随 quote 刷新，R1）
            View {
                attr {
                    marginLeft(8f)
                    marginBottom(4f)
                    paddingTop(3f); paddingBottom(3f); paddingLeft(9f); paddingRight(9f)
                    backgroundColor(toneColor())
                    borderRadius(theme.inputRadius)
                    alignItemsCenter(); justifyContentCenter()
                }
                TickerText(
                    text = { Format.percent(tickerSnapshot().changePercent) },
                    previousText = previousPercentText,
                    loading = quoteLoading,
                    fontSize = theme.type.sm,
                    width = { (Format.percent(tickerSnapshot().changePercent).length * 6.6f + 6f).coerceAtLeast(50f) },
                    color = { theme.onBrand },
                    theme = theme,
                    lift = tickerLift,
                    directionUp = tickerDirectionUp,
                    reduceMotion = reduceMotion,
                )
            }
            // 涨跌额（元）：胶囊右侧弱一档的同行事实
            Text {
                attr {
                    text(Format.signed(tickerSnapshot().change))
                    marginLeft(8f)
                    marginBottom(6f)
                    fontSize(theme.type.sm)
                    fontWeightMedium()
                    color(tickerSnapshot().tone.opacity(0.85f))
                }
            }
        }
        // 选中十字线/蜡烛时，这里同步相应时点的交易数据；保持为干净的信息行，
        // 不用一格一块的玻璃底把 Hero 切碎。
        vbind({ listOf(quote(), chartMode(), chartPeriod(), crosshairIndex(), selectedKLineIndex()) }) {
            val snapshot = tickerSnapshot()
            snapshot.metrics.chunked(4).forEachIndexed { rowIndex, row ->
                View {
                    attr {
                        marginTop(if (rowIndex == 0) 10f else 7f)
                        flexDirectionRow()
                        alignItemsCenter()
                        touchEnable(false)
                    }
                    row.forEachIndexed { columnIndex, metric ->
                        if (columnIndex > 0) {
                            View {
                                attr {
                                    width(0.5f); height(24f)
                                    marginRight(7f)
                                    backgroundColor(theme.divider)
                                }
                            }
                        }
                        View {
                            attr { flex(1f) }
                            Text {
                                attr {
                                    text(metric.label)
                                    fontSizeScaled(10f)
                                    color(theme.textTertiary)
                                }
                            }
                            Text {
                                attr {
                                    text(metric.value)
                                    marginTop(2f)
                                    fontSizeScaled(13f)
                                    fontWeightSemiBold()
                                    color(metric.valueColor ?: theme.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text {
            attr {
                text(tickerSnapshot().caption)
                marginTop(4f)
                fontSize(theme.type.meta)
                color(theme.textTertiary)
            }
        }
        vif({ watchlistHint().isNotEmpty() }) {
            Text {
                attr {
                    text(watchlistHint())
                    marginTop(6f)
                    fontSize(theme.type.meta)
                    color(theme.term)
                }
            }
        }
    }
}

