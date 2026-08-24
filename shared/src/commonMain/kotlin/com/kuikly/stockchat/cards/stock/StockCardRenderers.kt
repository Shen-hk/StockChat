package com.kuikly.stockchat.cards.stock

import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardRegistry
import com.kuikly.stockchat.cards.core.CardRenderer
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.common.Format
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

object StockCardRenderers {
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true
        CardRegistry.register(StockQuoteCardRenderer)
        CardRegistry.register(StockChartCardRenderer)
        CardRegistry.register(AttributionCardRenderer)
        CardRegistry.register(DefinitionCardRenderer)
        CardRegistry.register(InsightCardRenderer)
        CardRegistry.register(StockCompareCardRenderer)
        CardRegistry.register(NewsCardRenderer)
    }
}

object StockQuoteCardRenderer : CardRenderer<StockQuoteCardModel> {
    override val cardType: String = "stock-quote"

    override fun render(container: ViewContainer<*, *>, model: StockQuoteCardModel, context: CardContext) {
        val quote = model.quote
        val theme = context.theme
        container.View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr { flex(1f) }
                Text { attr { text(quote.name); fontSize(16f); fontWeightSemiBold(); color(theme.textPrimary) } }
                Text { attr { text(quote.symbol); marginTop(2f); fontSize(11f); color(theme.textTertiary) } }
            }
            View {
                attr { alignItemsFlexEnd() }
                Text {
                    attr {
                        text(Format.price(quote.price))
                        fontSize(23f)
                        fontWeightBold()
                        color(if (quote.rising) theme.rise else theme.fall)
                    }
                }
                Text {
                    attr {
                        text("${if (quote.rising) "▲" else "▼"} ${Format.percent(quote.changePercent)}")
                        marginTop(2f)
                        fontSize(12f)
                        color(if (quote.rising) theme.rise else theme.fall)
                    }
                }
            }
        }
        MiniTimeline(container, model, context)
        container.View {
            attr { flexDirectionRow(); marginTop(10f) }
            Metric("今开", Format.price(quote.open), theme, this)
            Metric("最高", Format.price(quote.high), theme, this)
            Metric("最低", Format.price(quote.low), theme, this)
            Metric("成交额", Format.compactAmount(quote.amount), theme, this)
        }
        container.Text {
            attr {
                text("行情来源：${quote.source}，数据可能存在延迟")
                marginTop(10f)
                fontSize(10f)
                color(theme.textTertiary)
            }
        }
        container.event {
            click { context.onOpenStock(quote.symbol) }
        }
    }
}

object StockChartCardRenderer : CardRenderer<StockChartCardModel> {
    override val cardType: String = "stock-chart"

    override fun render(container: ViewContainer<*, *>, model: StockChartCardModel, context: CardContext) {
        val theme = context.theme
        container.Text {
            attr { text("${model.quote.name} 分时走势"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) }
        }
        MiniTimeline(container, StockQuoteCardModel(model.quote), context, height = 132f)
        container.Text {
            attr { text("虚线为昨收基准"); marginTop(6f); fontSize(10f); color(theme.textTertiary) }
        }
        container.event { click { context.onOpenStock(model.quote.symbol) } }
    }
}

object AttributionCardRenderer : CardRenderer<AttributionCardModel> {
    override val cardType: String = "attribution"

    override fun render(container: ViewContainer<*, *>, model: AttributionCardModel, context: CardContext) {
        val theme = context.theme
        container.Text {
            attr { text("为什么${if (model.quote.rising) "涨" else "跌"}"); fontSize(16f); fontWeightSemiBold(); color(theme.textPrimary) }
        }
        model.factors.forEach { factor ->
            container.View {
                attr { marginTop(12f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(factor.name); fontSize(13f); fontWeightMedium(); color(theme.textPrimary); flex(1f) } }
                    Text { attr { text("${Format.decimal(factor.weight * 100, 0)}%"); fontSize(12f); color(theme.textSecondary) } }
                }
                View {
                    attr { marginTop(6f); height(3f); backgroundColor(theme.surfaceMuted); borderRadius(2f) }
                    View {
                        attr {
                            height(3f)
                            width((factor.weight.coerceIn(0.0, 1.0) * 260).toFloat())
                            backgroundColor(theme.brand)
                            borderRadius(2f)
                        }
                    }
                }
                Text { attr { text(factor.description); marginTop(5f); fontSize(11f); lineHeight(16f); color(theme.textSecondary) } }
                Text { attr { text("来源：${factor.source}"); marginTop(2f); fontSize(10f); color(theme.textTertiary) } }
            }
        }
    }
}

object DefinitionCardRenderer : CardRenderer<DefinitionCardModel> {
    override val cardType: String = "definition"

    override fun render(container: ViewContainer<*, *>, model: DefinitionCardModel, context: CardContext) {
        val theme = context.theme
        container.Text { attr { text(model.term); fontSize(18f); fontWeightBold(); color(theme.brand) } }
        container.Text { attr { text(model.plainText); marginTop(8f); fontSize(14f); lineHeight(21f); color(theme.textPrimary) } }
        container.View {
            attr { marginTop(10f); padding(10f); backgroundColor(theme.surfaceMuted); borderRadius(10f) }
            Text { attr { text(model.example); fontSize(12f); lineHeight(18f); color(theme.textSecondary) } }
        }
    }
}

object InsightCardRenderer : CardRenderer<InsightCardModel> {
    override val cardType: String = "insight"

    override fun render(container: ViewContainer<*, *>, model: InsightCardModel, context: CardContext) {
        val theme = context.theme
        container.Text { attr { text("AI 解读"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        container.Text { attr { text(model.summary); marginTop(8f); fontSize(14f); lineHeight(21f); color(theme.textSecondary) } }
        container.Text {
            attr {
                text("AI 生成 · 仅供参考，不构成投资建议")
                marginTop(10f)
                fontSize(10f)
                color(theme.textTertiary)
            }
        }
    }
}

object StockCompareCardRenderer : CardRenderer<StockCompareCardModel> {
    override val cardType: String = "stock-compare"

    override fun render(container: ViewContainer<*, *>, model: StockCompareCardModel, context: CardContext) {
        val theme = context.theme
        container.Text { attr { text("股票对比"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        model.quotes.forEach { quote ->
            container.View {
                attr { marginTop(11f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text(quote.name); fontSize(13f); fontWeightMedium(); color(theme.textPrimary) } }
                    Text { attr { text(quote.symbol); marginTop(2f); fontSize(9f); color(theme.textTertiary) } }
                }
                Text { attr { text(Format.price(quote.price)); fontSize(14f); color(theme.textPrimary) } }
                Text {
                    attr {
                        text(Format.percent(quote.changePercent))
                        width(72f)
                        textAlignRight()
                        fontSize(12f)
                        color(if (quote.rising) theme.rise else theme.fall)
                    }
                }
                event { click { context.onOpenStock(quote.symbol) } }
            }
        }
        container.Text {
            attr { text("价格与涨跌幅仅作横向观察，不代表投资优劣"); marginTop(10f); fontSize(10f); color(theme.textTertiary) }
        }
    }
}

object NewsCardRenderer : CardRenderer<NewsCardModel> {
    override val cardType: String = "news"

    override fun render(container: ViewContainer<*, *>, model: NewsCardModel, context: CardContext) {
        val theme = context.theme
        container.Text { attr { text("${model.quote.name} 相关资讯"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        model.items.forEach { item ->
            container.View {
                attr { marginTop(11f) }
                Text { attr { text(item.title); fontSize(12f); lineHeight(18f); color(theme.textPrimary) } }
                Text { attr { text("${item.source}  ${item.time}"); marginTop(3f); fontSize(9f); color(theme.textTertiary) } }
            }
        }
        container.Text { attr { text("资讯为公开信息摘要，请以原始来源为准"); marginTop(10f); fontSize(10f); color(theme.textTertiary) } }
    }
}

private fun MiniTimeline(
    container: ViewContainer<*, *>,
    model: StockQuoteCardModel,
    context: CardContext,
    height: Float = 72f,
) {
    val quote = model.quote
    val theme = context.theme
    container.Canvas({
        attr { height(height); marginTop(10f) }
    }) { canvas, width, canvasHeight ->
        val geometry = TimeLineCalculator.calculate(quote.timeline, width, canvasHeight, quote.previousClose)
        if (geometry.points.isNotEmpty()) {
            canvas.beginPath()
            canvas.moveTo(0f, geometry.baselineY)
            canvas.lineTo(width, geometry.baselineY)
            canvas.setLineDash(listOf(3f, 4f))
            canvas.strokeStyle(theme.divider)
            canvas.lineWidth(1f)
            canvas.stroke()
            canvas.setLineDash(emptyList())
            canvas.beginPath()
            geometry.points.forEachIndexed { index, point ->
                if (index == 0) canvas.moveTo(point.x, point.y) else canvas.lineTo(point.x, point.y)
            }
            canvas.strokeStyle(if (quote.rising) theme.rise else theme.fall)
            canvas.lineWidth(2f)
            canvas.lineCapRound()
            canvas.stroke()
        }
    }
}

private fun Metric(
    label: String,
    value: String,
    theme: com.kuikly.stockchat.cards.theme.StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { flex(1f) }
        Text { attr { text(label); fontSize(10f); color(theme.textTertiary) } }
        Text { attr { text(value); marginTop(3f); fontSize(12f); fontWeightMedium(); color(theme.textPrimary) } }
    }
}
