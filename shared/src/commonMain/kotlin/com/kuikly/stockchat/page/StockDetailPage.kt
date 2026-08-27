package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteRepositoryStore
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.DataModeBadge
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
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
    private val quoteRepository by lazy { QuoteRepositoryStore.shared(pagerId) }
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        symbol = pagerData.params.optString("symbol").ifEmpty { "600519.SH" }
        quote = quoteRepository.cachedOrOffline(symbol) ?: quote
        quoteRepository.load(symbol) { result ->
            result.quote?.let { quote = it }
            dataModeLabel = result.mode.quoteLabel()
        }
    }

    override fun body(): ViewBuilder {
        val page = this
        val attribution = CardPayloadParser.parse("attribution", "{\"symbol\":\"${page.quote.symbol}\"}") as AttributionIntent
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
                View {
                    attr { padding(16f); backgroundColor(page.theme.surface); borderRadius(page.theme.cardRadius) }
                    View {
                        attr { flexDirectionRow(); alignItemsFlexStart() }
                        View {
                            attr { flex(1f) }
                            Text {
                                attr {
                                    text(Format.price(page.quote.price))
                                    fontSize(32f)
                                    fontWeightBold()
                                    color(if (page.quote.rising) page.theme.rise else page.theme.fall)
                                }
                            }
                            Text {
                                attr {
                                    text("${Format.signed(page.quote.change)}  ${Format.percent(page.quote.changePercent)}")
                                    marginTop(3f)
                                    fontSize(14f)
                                    color(if (page.quote.rising) page.theme.rise else page.theme.fall)
                                }
                            }
                        }
                        DataModeBadge(page.theme, page.dataModeLabel, page.hostGlassRenderer)
                    }
                    View {
                        attr { marginTop(16f); flexDirectionRow() }
                        DetailMetric("今开", Format.price(page.quote.open), page.theme, this)
                        DetailMetric("最高", Format.price(page.quote.high), page.theme, this)
                        DetailMetric("最低", Format.price(page.quote.low), page.theme, this)
                        DetailMetric("昨收", Format.price(page.quote.previousClose), page.theme, this)
                    }
                    View {
                        attr { marginTop(14f); flexDirectionRow() }
                        DetailMetric("成交量", Format.compactAmount(page.quote.volume), page.theme, this)
                        DetailMetric("成交额", Format.compactAmount(page.quote.amount), page.theme, this)
                        DetailMetric("换手率", "${Format.decimal(page.quote.turnoverRate, 2)}%", page.theme, this)
                        DetailMetric("总市值", Format.compactAmount(page.quote.marketCap), page.theme, this)
                    }
                    Text {
                        attr {
                            text("数据源：${page.quote.source} · 更新于 ${page.quote.timestamp}")
                            marginTop(14f)
                            fontSize(10f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
                View {
                    attr { marginTop(10f); flexDirectionRow() }
                    listOf(StockChartMode.TIMELINE to "分时", StockChartMode.K_LINE to "日 K", StockChartMode.K_LINE to "周 K", StockChartMode.K_LINE to "月 K").forEachIndexed { index, (mode, label) ->
                        View {
                            val period = when (index) { 2 -> StockChartPeriod.WEEK; 3 -> StockChartPeriod.MONTH; else -> StockChartPeriod.DAY }
                            attr { marginRight(6f); paddingLeft(10f); paddingRight(10f); height(32f); justifyContentCenter(); borderRadius(9f); backgroundColor(if (page.chartMode == mode && (mode == StockChartMode.TIMELINE || page.chartPeriod == period)) page.theme.brandSoft else page.theme.surfaceMuted) }
                            Text { attr { text(label); fontSize(12f); color(if (page.chartMode == mode && (mode == StockChartMode.TIMELINE || page.chartPeriod == period)) page.theme.brand else page.theme.textSecondary) } }
                            event { click { page.chartMode = mode; page.chartPeriod = period } }
                        }
                    }
                }
                vif({ page.chartMode == StockChartMode.TIMELINE }) {
                    CardShell(
                        StockChartCardModel(page.quote, StockChartMode.TIMELINE, StockChartPeriod.DAY),
                        CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer),
                    )
                }
                vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.DAY }) {
                    CardShell(
                        StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.DAY),
                        CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer),
                    )
                }
                vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.WEEK }) {
                    CardShell(
                        StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.WEEK),
                        CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer),
                    )
                }
                vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.MONTH }) {
                    CardShell(
                        StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.MONTH),
                        CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer),
                    )
                }
                View {
                    attr { marginTop(10f); padding(14f); backgroundColor(page.theme.surface); borderRadius(page.theme.cardRadius) }
                    Text { attr { text("关键指标"); fontSize(16f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                    View {
                        attr { marginTop(12f); flexDirectionRow() }
                        DetailMetric("PE(TTM)", Format.decimal(page.quote.peTtm, 2), page.theme, this)
                        DetailMetric("PB", Format.decimal(page.quote.pb, 2), page.theme, this)
                        DetailMetric("振幅", Format.decimal((page.quote.high - page.quote.low) / page.quote.previousClose * 100, 2) + "%", page.theme, this)
                    }
                    Text {
                        attr {
                            text("指标要结合行业、增长与盈利质量一起看，单个数值不构成结论。")
                            marginTop(12f)
                            fontSize(11f)
                            lineHeight(17f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
                CardShell(
                    InsightCardModel(page.quote, "短线价格偏弱，资金与板块联动影响较大。中期判断应继续核对现金流、渠道库存和公司公告。"),
                    CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer),
                )
                CardShell(
                    AttributionCardModel(page.quote, attribution.direction, attribution.factors),
                    CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer),
                )
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
}

private fun DetailMetric(
    label: String,
    value: String,
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { flex(1f) }
        Text { attr { text(label); fontSize(10f); color(theme.textTertiary) } }
        Text { attr { text(value); marginTop(4f); fontSize(12f); fontWeightMedium(); color(theme.textPrimary) } }
    }
}

private fun ViewContainer<*, *>.NewsSection(theme: StockChatTheme) {
    val items = listOf(
        Triple("公司发布近期经营情况说明", "公司公告", "2 小时前"),
        Triple("白酒板块盘中震荡，龙头股表现分化", "证券时报", "3 小时前"),
        Triple("机构关注消费复苏节奏与渠道库存", "公开研报摘要", "昨天"),
    )
    View {
        attr { marginTop(10f); padding(14f); backgroundColor(theme.surface); borderRadius(theme.cardRadius) }
        Text { attr { text("相关资讯"); fontSize(16f); fontWeightSemiBold(); color(theme.textPrimary) } }
        items.forEachIndexed { index, item ->
            View {
                attr {
                    paddingTop(12f)
                    paddingBottom(12f)
                    if (index > 0) borderTop(Border(1f, BorderStyle.SOLID, theme.divider))
                }
                Text { attr { text(item.first); fontSize(13f); lineHeight(19f); color(theme.textPrimary) } }
                Text { attr { text("${item.second}  ${item.third}"); marginTop(5f); fontSize(10f); color(theme.textTertiary) } }
            }
        }
    }
}
