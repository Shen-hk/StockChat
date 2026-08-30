package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.stock.KLineChart
import com.kuikly.stockchat.cards.stock.MiniTimeline
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteRepositoryStore
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.DataModeBadge
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.STOCK_DETAIL, supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private var symbol = "600519.SH"
    private var quote: Quote by observable(QuoteRepositoryStore.shared(pagerId).cachedOrOffline("600519.SH")!!)
    private var dataModeLabel: String by observable("正在连接行情")
    private var chartMode: StockChartMode by observable(StockChartMode.TIMELINE)
    private var chartPeriod: StockChartPeriod by observable(StockChartPeriod.DAY)
    private var watchlisted: Boolean by observable(false)
    private var watchlistHint: String by observable("")
    private val quoteRepository by lazy { QuoteRepositoryStore.shared(pagerId) }
    private val watchlistStore by lazy { WatchlistStore(pagerId) }
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        symbol = pagerData.params.optString("symbol").ifEmpty { "600519.SH" }
        quote = quoteRepository.cachedOrOffline(symbol) ?: quote
        watchlisted = watchlistStore.contains(symbol)
        quoteRepository.load(symbol) { result ->
            result.quote?.let { quote = it }
            dataModeLabel = result.mode.quoteLabel()
        }
    }

    override fun body(): ViewBuilder {
        val page = this
        val attribution = CardPayloadParser.parse("attribution", "{\"symbol\":\"${page.quote.symbol}\"}") as AttributionIntent
        val ctx = CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer)
        val aiSummary = "短线价格偏弱，资金与板块联动影响较大。中期判断应继续核对现金流、渠道库存和公司公告。"
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(100f)
                }

                // ---- Hero 行情（去卡片，直接铺底） ----
                View {
                    attr { marginTop(page.theme.spacing.xl) }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter() }
                        View {
                            attr { flex(1f); flexDirectionRow(); alignItemsCenter(); flexWrapWrap() }
                            Text {
                                attr {
                                    text(Format.price(page.quote.price))
                                    fontSize(page.theme.type.display)
                                    fontWeightBold()
                                    color(if (page.quote.rising) page.theme.rise else page.theme.fall)
                                }
                            }
                            View {
                                attr {
                                    marginLeft(page.theme.spacing.sm)
                                    paddingTop(3f); paddingBottom(3f); paddingLeft(10f); paddingRight(10f)
                                    backgroundColor(if (page.quote.rising) page.theme.riseSoft else page.theme.fallSoft)
                                    borderRadius(page.theme.inputRadius)
                                    alignItemsCenter(); justifyContentCenter()
                                }
                                Text {
                                    attr {
                                        text(Format.percent(page.quote.changePercent))
                                        fontSize(page.theme.type.sm)
                                        fontWeightSemiBold()
                                        color(if (page.quote.rising) page.theme.rise else page.theme.fall)
                                    }
                                }
                            }
                        }
                        DataModeBadge(page.theme, page.dataModeLabel, page.hostGlassRenderer)
                    }
                    Text {
                        attr {
                            text("${Format.signed(page.quote.change)}  ${Format.percent(page.quote.changePercent)}")
                            marginTop(4f)
                            fontSize(page.theme.type.body)
                            color(if (page.quote.rising) page.theme.rise else page.theme.fall)
                        }
                    }
                    Text {
                        attr {
                            text("数据源：${page.quote.source} · 更新于 ${page.quote.timestamp}")
                            marginTop(page.theme.spacing.sm)
                            fontSize(page.theme.type.meta)
                            color(page.theme.textTertiary)
                        }
                    }
                    View {
                        attr {
                            marginTop(page.theme.spacing.md)
                            height(36f)
                            paddingLeft(14f)
                            paddingRight(14f)
                            alignSelfFlexStart()
                            allCenter()
                            borderRadius(page.theme.inputRadius)
                            backgroundColor(if (page.watchlisted) page.theme.surfaceMuted else page.theme.brandSoft)
                        }
                        Text {
                            attr {
                                text(if (page.watchlisted) "已自选" else "加自选")
                                fontSize(page.theme.type.label)
                                fontWeightSemiBold()
                                color(if (page.watchlisted) page.theme.textSecondary else page.theme.brand)
                            }
                        }
                        event { click { page.toggleWatchlist() } }
                    }
                    vif({ page.watchlistHint.isNotEmpty() }) {
                        Text {
                            attr {
                                text(page.watchlistHint)
                                marginTop(6f)
                                fontSize(page.theme.type.meta)
                                color(page.theme.term)
                            }
                        }
                    }
                }

                // ---- 行情指标：细分隔线网格（替代白卡） ----
                SectionLabel("行情数据", page.theme)
                View {
                    attr {
                        marginTop(page.theme.spacing.lg)
                        backgroundColor(page.theme.surfaceMuted)
                        borderRadius(page.theme.cardRadius)
                    }
                    MetricGrid(
                        listOf(
                            "今开" to Format.price(page.quote.open),
                            "最高" to Format.price(page.quote.high),
                            "最低" to Format.price(page.quote.low),
                            "昨收" to Format.price(page.quote.previousClose),
                            "成交量" to Format.compactAmount(page.quote.volume),
                            "成交额" to Format.compactAmount(page.quote.amount),
                            "换手率" to "${Format.decimal(page.quote.turnoverRate, 2)}%",
                            "总市值" to Format.compactAmount(page.quote.marketCap),
                        ),
                        page.theme,
                    )
                }

                // ---- 走势：去卡片，满宽绘制 + 分段控件 ----
                SectionLabel("走势", page.theme)
                ChartSegment(page.theme, page.chartMode, page.chartPeriod) { m, p ->
                    page.chartMode = m
                    page.chartPeriod = p
                }
                DetailChart(page.theme, page.chartMode, page.chartPeriod, page.quote, ctx)

                // ---- 关键指标：细分隔线网格 ----
                SectionLabel("关键指标", page.theme)
                View {
                    attr {
                        marginTop(page.theme.spacing.lg)
                        backgroundColor(page.theme.surfaceMuted)
                        borderRadius(page.theme.cardRadius)
                    }
                    MetricGrid(
                        listOf(
                            "PE(TTM)" to Format.decimal(page.quote.peTtm, 2),
                            "PB" to Format.decimal(page.quote.pb, 2),
                            "振幅" to Format.decimal((page.quote.high - page.quote.low) / page.quote.previousClose * 100, 2) + "%",
                        ),
                        page.theme,
                    )
                }
                Text {
                    attr {
                        text("指标要结合行业、增长与盈利质量一起看，单个数值不构成结论。")
                        marginTop(page.theme.spacing.md)
                        fontSize(page.theme.type.label)
                        lineHeight(17f)
                        color(page.theme.textTertiary)
                    }
                }

                // ---- AI 解读：要点卡片（方案 C） ----
                SectionLabel("AI 解读", page.theme)
                AiInsightBlock(aiSummary, page.theme)

                // ---- 涨跌归因：列表 + 分隔线 ----
                AttributionBlock(
                    AttributionCardModel(page.quote, attribution.direction, attribution.factors),
                    page.theme,
                )

                // ---- 相关资讯：去卡列表 ----
                NewsSection(page.theme)
            }

            AppTopBar(
                title = page.quote.name,
                subtitle = page.quote.symbol,
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
            View {
                attr {
                    absolutePosition(bottom = 18f + page.pagerData.safeAreaInsets.bottom, left = 72f, right = 72f)
                    height(46f)
                    allCenter()
                }
                GlassBackdrop(page.theme.glass.peek, page.hostGlassRenderer)
                Text { attr { text("回到对话"); fontSize(14f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                event { click { page.closePage() } }
            }
        }
    }

    private fun toggleWatchlist() {
        if (watchlisted) {
            watchlistStore.remove(symbol)
            watchlisted = false
            watchlistHint = "已从自选移除"
            return
        }
        when (watchlistStore.add(symbol, quote.name)) {
            WatchlistAddResult.ADDED -> {
                watchlisted = true
                watchlistHint = "已加入自选"
            }
            WatchlistAddResult.ALREADY_IN -> {
                watchlisted = true
                watchlistHint = "已在自选中"
            }
            WatchlistAddResult.FULL -> watchlistHint = "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
        }
    }
}

private fun ViewContainer<*, *>.SectionLabel(text: String, theme: StockChatTheme) {
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text(text)
            fontSize(theme.type.label)
            fontWeightSemiBold()
            color(theme.textTertiary)
        }
    }
}

private fun ViewContainer<*, *>.MetricGrid(items: List<Pair<String, String>>, theme: StockChatTheme) {
    val rows = items.chunked(4)
    rows.forEachIndexed { r, row ->
        View {
            attr { flexDirectionRow() }
            row.forEachIndexed { c, (label, value) ->
                View {
                    attr {
                        flex(1f)
                        paddingTop(theme.spacing.lg); paddingBottom(theme.spacing.lg)
                        paddingLeft(theme.spacing.md); paddingRight(theme.spacing.md)
                        if (c < row.lastIndex) borderRight(Border(0.5f, BorderStyle.SOLID, theme.divider))
                        if (r < rows.lastIndex) borderBottom(Border(0.5f, BorderStyle.SOLID, theme.divider))
                    }
                    Text { attr { text(label); fontSize(theme.type.label); color(theme.textTertiary) } }
                    Text { attr { text(value); marginTop(5f); fontSize(theme.type.body); fontWeightMedium(); color(theme.textPrimary) } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.ChartSegment(
    theme: StockChatTheme,
    chartMode: StockChartMode,
    chartPeriod: StockChartPeriod,
    onSelect: (StockChartMode, StockChartPeriod) -> Unit,
) {
    val tabs = listOf(
        StockChartMode.TIMELINE to "分时",
        StockChartMode.K_LINE to "日K",
        StockChartMode.K_LINE to "周K",
        StockChartMode.K_LINE to "月K",
    )
    View {
        attr { marginTop(theme.spacing.lg) }
        View {
            attr {
                flexDirectionRow(); padding(3f)
                backgroundColor(theme.surfaceMuted); borderRadius(theme.inputRadius)
            }
            tabs.forEachIndexed { index, (mode, label) ->
                val period = when (index) {
                    2 -> StockChartPeriod.WEEK
                    3 -> StockChartPeriod.MONTH
                    else -> StockChartPeriod.DAY
                }
                val active = chartMode == mode && (mode == StockChartMode.TIMELINE || chartPeriod == period)
                View {
                    attr {
                        paddingLeft(14f); paddingRight(14f); height(32f)
                        justifyContentCenter(); borderRadius(theme.inputRadius)
                        backgroundColor(if (active) theme.brandSoft else theme.surfaceMuted)
                    }
                    Text {
                        attr {
                            text(label)
                            fontSize(theme.type.label)
                            fontWeightSemiBold()
                            color(if (active) theme.brand else theme.textSecondary)
                        }
                    }
                    event { click { onSelect(mode, period) } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DetailChart(
    theme: StockChatTheme,
    chartMode: StockChartMode,
    chartPeriod: StockChartPeriod,
    quote: Quote,
    ctx: CardContext,
) {
    vif({ chartMode == StockChartMode.TIMELINE }) {
        MiniTimeline(this, StockQuoteCardModel(quote), ctx, height = 132f)
        Text {
            attr {
                text("虚线为昨收基准")
                marginTop(6f); fontSize(theme.type.meta); color(theme.textTertiary)
            }
        }
    }
    vif({ chartMode == StockChartMode.K_LINE && chartPeriod == StockChartPeriod.DAY }) {
        KLineChart(this, StockChartCardModel(quote, StockChartMode.K_LINE, StockChartPeriod.DAY), ctx)
    }
    vif({ chartMode == StockChartMode.K_LINE && chartPeriod == StockChartPeriod.WEEK }) {
        KLineChart(this, StockChartCardModel(quote, StockChartMode.K_LINE, StockChartPeriod.WEEK), ctx)
    }
    vif({ chartMode == StockChartMode.K_LINE && chartPeriod == StockChartPeriod.MONTH }) {
        KLineChart(this, StockChartCardModel(quote, StockChartMode.K_LINE, StockChartPeriod.MONTH), ctx)
    }
}

private fun ViewContainer<*, *>.AiInsightBlock(summary: String, theme: StockChatTheme) {
    val sentences = summary.split(Regex("[。，]")).map { it.trim() }.filter { it.isNotEmpty() }
    View {
        attr {
            marginTop(theme.spacing.lg)
            backgroundColor(theme.surfaceMuted)
            borderRadius(theme.cardRadius)
        }
        View { attr { height(4f); backgroundColor(theme.brand) } }
        View {
            attr { padding(theme.spacing.lg) }
            Text {
                attr {
                    text("AI 解读")
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.brand)
                }
            }
            if (sentences.isNotEmpty()) {
                Text {
                    attr {
                        text(sentences.first() + "。")
                        marginTop(theme.spacing.sm)
                        fontSize(theme.type.body)
                        fontWeightSemiBold()
                        color(theme.textPrimary)
                        lineHeight(21f)
                    }
                }
            }
            sentences.drop(1).forEach { s ->
                View {
                    attr { flexDirectionRow(); marginTop(theme.spacing.sm); alignItemsFlexStart() }
                    View {
                        attr {
                            width(6f); height(6f); borderRadius(3f)
                            backgroundColor(theme.brand)
                            marginTop(6f); marginRight(theme.spacing.sm)
                        }
                    }
                    Text {
                        attr {
                            flex(1f)
                            text(s + "。")
                            fontSize(theme.type.sm)
                            lineHeight(19f)
                            color(theme.textSecondary)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("AI 生成 · 仅供参考，不构成投资建议")
                    marginTop(theme.spacing.md)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AttributionBlock(model: AttributionCardModel, theme: StockChatTheme) {
    val factors = model.factors
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text("为什么${if (model.quote.rising) "涨" else "跌"}")
            fontSize(theme.type.title)
            fontWeightSemiBold()
            color(theme.textPrimary)
        }
    }
    factors.forEachIndexed { index, factor ->
        View {
            attr {
                marginTop(theme.spacing.md); paddingTop(theme.spacing.md)
                if (index > 0) borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(factor.name)
                        flex(1f)
                        fontSize(theme.type.sm)
                        fontWeightMedium()
                        color(theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("${Format.decimal(factor.weight * 100, 0)}%")
                        fontSize(theme.type.label)
                        color(theme.textSecondary)
                    }
                }
            }
            Text {
                attr {
                    text(factor.description)
                    marginTop(4f)
                    fontSize(theme.type.label)
                    lineHeight(16f)
                    color(theme.textSecondary)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.NewsSection(theme: StockChatTheme) {
    val items = listOf(
        Triple("公司发布近期经营情况说明", "公司公告", "2 小时前"),
        Triple("白酒板块盘中震荡，龙头股表现分化", "证券时报", "3 小时前"),
        Triple("机构关注消费复苏节奏与渠道库存", "公开研报摘要", "昨天"),
    )
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text("相关资讯")
            fontSize(theme.type.label)
            fontWeightSemiBold()
            color(theme.textTertiary)
        }
    }
    items.forEachIndexed { index, item ->
        View {
            attr {
                marginTop(theme.spacing.md); paddingTop(theme.spacing.md)
                if (index > 0) borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
            }
            Text {
                attr {
                    text(item.first)
                    fontSize(theme.type.sm)
                    lineHeight(19f)
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text("${item.second}  ${item.third}")
                    marginTop(5f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}
