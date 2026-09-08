package com.kuikly.stockchat.cards.stock

import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CompareCalculator
import com.kuikly.stockchat.cards.core.CardRegistry
import com.kuikly.stockchat.cards.core.CardRenderer
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.ExplanationDepth
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.NewsItem
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.chart.model.KLineCalculator
import com.kuikly.stockchat.common.Format
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

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

object StockQuoteCardRenderer : CardRenderer {
    override val cardType: String = "stock-quote"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? StockQuoteCardModel ?: return
        val quote = model.quote
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text(quote.name); fontSize(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(quote.symbol); marginTop(1f); fontSize(10f); color(theme.textTertiary) } }
                }
                View {
                    attr { alignItemsFlexEnd() }
                    Text { attr { text(Format.price(quote.price)); fontSize(18f); fontWeightBold(); color(if (quote.rising) theme.rise else theme.fall) } }
                    Text { attr { text("${if (quote.rising) "▲" else "▼"} ${Format.percent(quote.changePercent)}"); marginTop(1f); fontSize(11f); color(if (quote.rising) theme.rise else theme.fall) } }
                }
            }
            MiniTimeline(container, model, context, height = 42f)
            container.Text { attr { text("${quote.source} · ${quote.timestamp}"); marginTop(4f); fontSize(9f); color(theme.textTertiary) } }
            if (context.cardClickable) {
                container.event { click { context.onOpenStock(quote.symbol) } }
            }
            return
        }
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
                        fontSize(28f)
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
            event {
                click {
                    if (context.compareCandidateSymbol.isNotEmpty() && context.compareCandidateSymbol != quote.symbol) {
                        context.onCompareCandidate?.invoke(context.cardKey, quote.symbol)
                    } else {
                        context.onOpenStock(quote.symbol)
                    }
                }
            }
        }
        if (context.density == CardDensity.COMPACT) {
            container.View {
                attr { flexDirectionRow(); marginTop(10f) }
                Metric("今开", Format.price(quote.open), theme, this)
                Metric("最高", Format.price(quote.high), theme, this)
                Metric("最低", Format.price(quote.low), theme, this)
                Metric("成交量", Format.compactAmount(quote.volume), theme, this)
            }
            container.Text {
                attr {
                    text("展开后查看分时走势、最高最低和行情来源")
                    marginTop(8f)
                    fontSize(10f)
                    color(theme.textTertiary)
                }
            }
            return
        }
        container.Text {
            attr {
                text("分时走势 · 虚线为昨收基准与均价，红绿为量能")
                marginTop(12f)
                fontSize(10f)
                color(theme.textTertiary)
            }
        }
        KuiklyTimelineChart(container, model.quote, context, height = 128f)
        container.View {
            attr { flexDirectionRow(); marginTop(10f) }
            Metric("今开", Format.price(quote.open), theme, this)
            Metric("最高", Format.price(quote.high), theme, this)
            Metric("最低", Format.price(quote.low), theme, this)
            Metric("成交额", Format.compactAmount(quote.amount), theme, this)
        }
        container.Text {
            attr {
                text("行情来源：${quote.source} · 更新于 ${quote.timestamp}，数据可能存在延迟")
                marginTop(10f)
                fontSize(10f)
                color(theme.textTertiary)
            }
        }
    }
}

object StockChartCardRenderer : CardRenderer {
    override val cardType: String = "stock-chart"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? StockChartCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text(model.quote.name); fontSize(13f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(if (model.mode == StockChartMode.TIMELINE) "分时走势" else model.period.label); marginTop(1f); fontSize(9f); color(theme.textTertiary) } }
                }
                Text { attr { text(Format.percent(model.quote.changePercent)); fontSize(12f); color(if (model.quote.rising) theme.rise else theme.fall) } }
            }
            if (model.mode == StockChartMode.TIMELINE) MiniTimeline(container, StockQuoteCardModel(model.quote), context, height = 34f)
            if (context.cardClickable) {
                container.event { click { context.onOpenStock(model.quote.symbol) } }
            }
            return
        }
        container.View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr { flex(1f) }
                Text {
                    attr { text("${model.quote.name} ${if (model.mode == StockChartMode.TIMELINE) "分时走势" else "${model.period.label}走势"}"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) }
                }
                Text {
                    attr { text("${Format.price(model.quote.price)} · ${Format.percent(model.quote.changePercent)}"); marginTop(2f); fontSize(10f); color(if (model.quote.rising) theme.rise else theme.fall) }
                }
            }
            event { click { context.onOpenStock(model.quote.symbol) } }
        }
        if (context.density == CardDensity.COMPACT) {
            if (model.mode == StockChartMode.TIMELINE) MiniTimeline(container, StockQuoteCardModel(model.quote), context, height = 62f)
            container.Text {
                attr { text("展开后查看更大图表、基准说明和完整细节"); marginTop(6f); fontSize(10f); color(theme.textTertiary) }
            }
            return
        }
        if (model.mode == StockChartMode.TIMELINE) KuiklyTimelineChart(container, model.quote, context, height = 132f)
        else KLineChart(container, model, context)
        container.Text {
            attr { text(if (model.mode == StockChartMode.TIMELINE) "虚线为昨收基准与均价，红绿为量能" else "显示 MA5 / MA10 / MA20；日线数据可能存在延迟"); marginTop(6f); fontSize(10f); color(theme.textTertiary) }
        }
    }
}

internal fun KLineChart(
    container: ViewContainer<*, *>,
    model: StockChartCardModel,
    context: CardContext,
    selectedIndex: Int = -1,
    onSelectIndex: ((Int) -> Unit)? = null,
) {
    val sourceLines = when (model.period) {
        StockChartPeriod.DAY -> model.quote.kLines
        StockChartPeriod.WEEK -> model.quote.weekKLines.ifEmpty { KLineCalculator.aggregate(model.quote.kLines, model.period.grouping) }
        StockChartPeriod.MONTH -> model.quote.monthKLines.ifEmpty { KLineCalculator.aggregate(model.quote.kLines, model.period.grouping) }
    }
    val maxVisible = when (model.period) {
        StockChartPeriod.DAY -> 60
        StockChartPeriod.WEEK -> 52
        StockChartPeriod.MONTH -> 48
    }
    val lines = sourceLines.takeLast(maxVisible)
    val allMovingAverages = listOf(5, 10, 20).associateWith { period ->
        KLineCalculator.movingAverage(sourceLines, period).takeLast(lines.size)
    }
    val theme = context.theme
    if (lines.isEmpty()) {
        container.View {
            attr {
                height(168f)
                marginTop(10f)
                alignSelfStretch()
                borderRadius(12f)
                backgroundColor(theme.surfaceMuted)
                allCenter()
            }
            Text {
                attr {
                    text("K线数据正在加载")
                    fontSize(11f)
                    color(theme.textTertiary)
                }
            }
        }
        return
    }
    var measuredWidth = 0f
    fun resolveIndex(x: Float): Int {
        val safeWidth = measuredWidth.coerceAtLeast(1f)
        val step = safeWidth / lines.size
        return (x / step).toInt().coerceIn(0, lines.lastIndex)
    }
    container.Canvas({
        attr { height(168f); marginTop(10f); alignSelfStretch() }
        if (onSelectIndex != null) {
            event {
                click { params -> onSelectIndex(resolveIndex(params.x)) }
                pan { params ->
                    if (!params.isEnd) onSelectIndex(resolveIndex(params.x))
                }
            }
        }
    }) { canvas, width, height ->
        measuredWidth = width
        if (lines.isEmpty() || width <= 0f) return@Canvas
        val low = lines.minOf { it.low }
        val high = lines.maxOf { it.high }
        val range = (high - low).coerceAtLeast(0.0001)
        fun y(value: Double) = ((high - value) / range * (height - 8f) + 4f).toFloat()
        val step = width / lines.size
        val selected = selectedIndex.takeIf { it in lines.indices } ?: lines.lastIndex
        lines.forEachIndexed { index, line ->
            val x = step * index + step / 2f
            val color = if (line.close >= line.open) theme.rise else theme.fall
            canvas.beginPath(); canvas.moveTo(x, y(line.high)); canvas.lineTo(x, y(line.low)); canvas.strokeStyle(color); canvas.lineWidth(1f); canvas.stroke()
            val bodyWidth = (step * 0.62f).coerceIn(1f, 7f)
            val bodyTop = minOf(y(line.open), y(line.close))
            val bodyBottom = maxOf(y(line.open), y(line.close))
            canvas.beginPath()
            canvas.moveTo(x - bodyWidth / 2f, (bodyTop + bodyBottom) / 2f)
            canvas.lineTo(x + bodyWidth / 2f, (bodyTop + bodyBottom) / 2f)
            canvas.strokeStyle(color)
            canvas.lineWidth((bodyBottom - bodyTop).coerceAtLeast(1.2f))
            canvas.stroke()
        }
        listOf(5 to theme.brand, 10 to theme.textSecondary, 20 to theme.textTertiary).forEach { (period, color) ->
            val values = allMovingAverages.getValue(period)
            canvas.beginPath()
            var started = false
            values.forEachIndexed { index, value ->
                if (value != null) {
                    val x = step * index + step / 2f
                    if (!started) { canvas.moveTo(x, y(value)); started = true } else canvas.lineTo(x, y(value))
                }
            }
            if (started) { canvas.strokeStyle(color); canvas.lineWidth(1.2f); canvas.stroke() }
        }
        lines.getOrNull(selected)?.let { latest ->
            val x = step * selected + step / 2f
            val closeY = y(latest.close)
            canvas.setLineDash(listOf(4f, 5f))
            canvas.beginPath()
            canvas.moveTo(x, 4f)
            canvas.lineTo(x, height - 4f)
            canvas.strokeStyle(theme.textTertiary.opacity(0.34f))
            canvas.lineWidth(1f)
            canvas.stroke()
            canvas.beginPath()
            canvas.moveTo(0f, closeY)
            canvas.lineTo(width, closeY)
            canvas.strokeStyle(theme.textTertiary.opacity(0.24f))
            canvas.lineWidth(1f)
            canvas.stroke()
            canvas.setLineDash(emptyList())
        }
    }
    container.View {
        attr { marginTop(5f); flexDirectionRow() }
        Text { attr { text(lines.first().date); fontSize(9f); color(theme.textTertiary); flex(1f) } }
        Text { attr { text(lines.last().date); fontSize(9f); color(theme.textTertiary); textAlignRight() } }
    }
    val selectedLine = lines.getOrNull(selectedIndex.takeIf { it in lines.indices } ?: lines.lastIndex)
    selectedLine?.let { latest ->
        container.View {
            attr {
                marginTop(6f)
                paddingTop(6f)
                borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
                flexDirectionRow()
            }
            Text { attr { text("定位 ${latest.date}"); fontSize(10f); color(theme.textTertiary); flex(1f) } }
            Text {
                attr {
                    text("收 ${Format.price(latest.close)}")
                    fontSize(10f)
                    fontWeightSemiBold()
                    color(if (latest.close >= latest.open) theme.rise else theme.fall)
                    textAlignRight()
                }
            }
        }
    }
}

object AttributionCardRenderer : CardRenderer {
    override val cardType: String = "attribution"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? AttributionCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            val top = model.factors.maxByOrNull { it.weight }
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text("归因：${top?.name ?: "暂无"}"); fontSize(13f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(top?.description ?: "等待更多数据确认"); marginTop(2f); fontSize(10f); color(theme.textTertiary) } }
                }
                Text { attr { text(top?.let { "${Format.decimal(it.weight * 100, 0)}%" } ?: "--"); fontSize(12f); color(theme.brand) } }
            }
            return
        }
        container.Text {
            attr { text("为什么${if (model.quote.rising) "涨" else "跌"}"); fontSize(16f); fontWeightSemiBold(); color(theme.textPrimary) }
        }
        val factors = if (context.density == CardDensity.COMPACT) model.factors.take(2) else model.factors
        factors.forEach { factor ->
            val drillKey = "${model.cardId}:${factor.name}"
            val drilled = drillKey in context.drilledKeys
            container.View {
                attr { marginTop(12f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(factor.name); fontSize(13f); fontWeightMedium(); color(theme.textPrimary); flex(1f) } }
                    Text { attr { text("${Format.decimal(factor.weight * 100, 0)}%"); fontSize(12f); color(theme.textSecondary) } }
                }
                View {
                    attr { marginTop(6f); height(3f); flexDirectionRow(); backgroundColor(theme.surfaceMuted); borderRadius(2f) }
                    val weight = factor.weight.coerceIn(0.0, 1.0).toFloat()
                    View {
                        attr {
                            height(3f)
                            flex(weight.coerceAtLeast(0.01f))
                            backgroundColor(theme.brand)
                            borderRadius(2f)
                        }
                    }
                    View { attr { height(3f); flex((1f - weight).coerceAtLeast(0.01f)) } }
                }
                Text { attr { text(factor.description); marginTop(5f); fontSize(11f); lineHeight(16f); color(theme.textSecondary) } }
                Text { attr { text("来源：${factor.source}"); marginTop(2f); fontSize(10f); color(theme.textTertiary) } }
                if (context.onToggleDrill != null) {
                    event {
                        click {
                            context.onCardEvent?.invoke(context.cardKey, com.kuikly.stockchat.cards.core.CardEvent.DrillInto(drillKey))
                            context.onToggleDrill.invoke(drillKey)
                        }
                    }
                }
            }
            if (drilled) {
                val sourceKey = "$drillKey:source"
                val sourceOpen = sourceKey in context.drilledKeys
                container.View {
                    attr {
                        marginTop(6f)
                        marginLeft(12f)
                        padding(10f)
                        backgroundColor(theme.surfaceMuted)
                        borderRadius(8f)
                    }
                    View { attr { height(2f); backgroundColor(theme.brand); borderRadius(1f) } }
                    Text { attr { text("${factor.name} 详情"); marginTop(8f); fontSize(12f); fontWeightMedium(); color(theme.textPrimary) } }
                    Text { attr { text("该因素当前权重为 ${Format.decimal(factor.weight * 100, 0)}%，可结合下方信源继续核对。 "); marginTop(4f); fontSize(11f); lineHeight(16f); color(theme.textSecondary) } }
                    CardShell(
                        NewsCardModel(model.quote, listOf(NewsItem("${factor.source}：${factor.description}", factor.source, "当前"))),
                        context.copy(density = CardDensity.MINI, onOpenSheet = null),
                    )
                    View {
                        attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                        Text {
                            attr {
                                text(if (sourceOpen) "收起信源 ▲" else "查看信源 ▼")
                                fontSize(10f)
                                color(theme.brand)
                                flex(1f)
                            }
                        }
                        event { click { context.onToggleDrill?.invoke(sourceKey) } }
                    }
                    if (sourceOpen) {
                        View {
                            attr {
                                marginTop(8f)
                                marginLeft(12f)
                                padding(8f)
                                backgroundColor(theme.surface)
                                borderRadius(8f)
                            }
                            Text { attr { text("信源核对"); fontSize(11f); fontWeightMedium(); color(theme.textPrimary) } }
                            Text {
                                attr {
                                    text("${factor.source} 提供了“${factor.description}”这一条解释依据，可信度为 ${factor.confidence}。")
                                    marginTop(4f)
                                    fontSize(10f)
                                    lineHeight(15f)
                                    color(theme.textSecondary)
                                }
                            }
                        }
                    }
                    View {
                        attr { alignSelfFlexStart(); marginTop(6f); paddingTop(4f); paddingBottom(4f) }
                        Text { attr { text("收起详情"); fontSize(10f); color(theme.brand) } }
                        event { click { context.onToggleDrill?.invoke(drillKey) } }
                    }
                }
            }
        }
    }
}

object DefinitionCardRenderer : CardRenderer {
    override val cardType: String = "definition"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? DefinitionCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text { attr { text(model.term); fontSize(13f); fontWeightSemiBold(); color(theme.brand); width(84f) } }
                Text { attr { text(model.plainText); fontSize(11f); color(theme.textSecondary); flex(1f) } }
            }
        } else {
            if (model.category.isNotBlank()) {
                container.Text { attr { text(model.category); fontSize(10f); color(theme.textTertiary) } }
            }
            container.Text { attr { text(model.term); marginTop(if (model.category.isBlank()) 0f else 2f); fontSize(if (context.density == CardDensity.COMPACT) 16f else 18f); fontWeightBold(); color(theme.brand) } }
            container.Text { attr { text(model.plainText); marginTop(8f); fontSize(14f); lineHeight(21f); color(theme.textPrimary) } }
            if (model.depth == ExplanationDepth.PARAGRAPH && context.density != CardDensity.FULL && model.advanced.isNotBlank()) {
                container.Text { attr { text(model.advanced.take(150)); marginTop(7f); fontSize(11.5f); lineHeight(18f); color(theme.textSecondary) } }
            }
            if (model.depth == ExplanationDepth.EXAMPLE_AND_DATA || context.density == CardDensity.FULL) {
                container.View {
                    attr { marginTop(10f); padding(10f); backgroundColor(theme.surfaceMuted); borderRadius(10f) }
                    Text { attr { text(model.example); fontSize(12f); lineHeight(18f); color(theme.textSecondary) } }
                }
                if (model.advanced.isNotBlank()) {
                    container.Text { attr { text("进阶解释"); marginTop(12f); fontSize(11f); fontWeightSemiBold(); color(theme.textTertiary) } }
                    container.Text { attr { text(model.advanced); marginTop(4f); fontSize(12f); lineHeight(19f); color(theme.textSecondary) } }
                }
            } else if (model.depth != ExplanationDepth.ONE_SENTENCE && model.advanced.isNotBlank()) {
                container.Text { attr { text("展开后查看例子与进阶解释"); marginTop(8f); fontSize(10f); color(theme.textTertiary) } }
            }
        }
    }
}

object InsightCardRenderer : CardRenderer {
    override val cardType: String = "insight"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? InsightCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("AI"); fontSize(12f); fontWeightBold(); color(theme.brand); width(34f) } }
                Text { attr { text(model.summary); fontSize(11f); color(theme.textSecondary); flex(1f) } }
            }
        } else {
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
}

object StockCompareCardRenderer : CardRenderer {
    override val cardType: String = "stock-compare"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? StockCompareCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                model.quotes.take(2).forEach { quote ->
                    View {
                        attr { flex(1f); marginRight(8f) }
                        Text { attr { text(quote.name); fontSize(12f); fontWeightSemiBold(); color(theme.textPrimary) } }
                        Text { attr { text(Format.percent(quote.changePercent)); marginTop(2f); fontSize(11f); color(if (quote.rising) theme.rise else theme.fall) } }
                        event { click { context.onOpenStock(quote.symbol) } }
                    }
                }
            }
            return
        }
        container.Text { attr { text("股票对比"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        val quotes = model.quotes.take(2)
        container.View {
            attr { marginTop(12f); flexDirectionRow(); alignItemsStretch() }
            quotes.forEachIndexed { index, quote ->
                if (index > 0) View { attr { width(1f); marginLeft(10f); marginRight(10f); backgroundColor(theme.divider) } }
                View {
                    attr { flex(1f) }
                    Text { attr { text(quote.name); fontSize(13f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(quote.symbol); marginTop(2f); fontSize(9f); color(theme.textTertiary) } }
                    Text { attr { text(Format.price(quote.price)); marginTop(10f); fontSize(17f); fontWeightBold(); color(theme.textPrimary) } }
                    Text { attr { text(Format.percent(quote.changePercent)); marginTop(3f); fontSize(12f); color(if (quote.rising) theme.rise else theme.fall) } }
                    event { click { context.onOpenStock(quote.symbol) } }
                }
            }
        }
        if (quotes.size == 2) {
            val delta = CompareCalculator.delta(quotes[0], quotes[1])
            val gapColor = if (delta.changePercentDifference >= 0) theme.rise else theme.fall
            container.View {
                attr { marginTop(14f) }
                Text { attr { text("涨跌幅差 ${Format.percent(delta.changePercentDifference)}"); fontSize(10f); color(theme.textSecondary) } }
                View {
                    attr {
                        height(4f)
                        marginTop(6f)
                        flexDirectionRow()
                        backgroundColor(theme.surfaceMuted)
                        borderRadius(2f)
                    }
                    val gap = delta.normalizedGap.toFloat().coerceIn(0f, 1f)
                    View { attr { height(4f); flex(gap.coerceAtLeast(0.01f)); backgroundColor(gapColor); borderRadius(2f) } }
                    View { attr { height(4f); flex((1f - gap).coerceAtLeast(0.01f)) } }
                }
            }
        }
        container.Text {
            attr { text("价格与涨跌幅仅作横向观察，不代表投资优劣"); marginTop(10f); fontSize(10f); color(theme.textTertiary) }
        }
    }
}

object NewsCardRenderer : CardRenderer {
    override val cardType: String = "news"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? NewsCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            val item = model.items.firstOrNull()
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("资讯"); fontSize(12f); fontWeightSemiBold(); color(theme.brand); width(42f) } }
                Text { attr { text(item?.title ?: "${model.quote.name} 暂无新资讯"); fontSize(11f); color(theme.textSecondary); flex(1f) } }
            }
            return
        }
        container.Text { attr { text("${model.quote.name} 相关资讯"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        val items = if (context.density == CardDensity.COMPACT) model.items.take(2) else model.items
        items.forEach { item ->
            container.View {
                attr { marginTop(11f) }
                Text { attr { text(item.title); fontSize(12f); lineHeight(18f); color(theme.textPrimary) } }
                Text { attr { text("${item.source}  ${item.time}"); marginTop(3f); fontSize(9f); color(theme.textTertiary) } }
            }
        }
        container.Text { attr { text("资讯为公开信息摘要，请以原始来源为准"); marginTop(10f); fontSize(10f); color(theme.textTertiary) } }
    }
}

/**
 * 迷你分时走势（自选卡 MINI、图表卡 MINI/COMPACT 共用）。
 *
 * 缩放走 [TimeLineCalculator.calculate] 自适应 min/max：迷你图只表意当日形态，
 * 波动有多大就铺多满（2026-09-09 用户反馈"全是平线"后收紧了 padding 下限）。
 *
 * 入场/刷新为 draw-on 生长动画：progress 0→1 的 setTimeout 链 + 900ms 安全兜底
 * 强制 1f（防断链卡死），draw 闭包读 progress observable 建立依赖（R1），无 attr
 * animate、不涉及 R5。同签名数据（分时点数 + 最新价）的重建不重播——QuoteRepository
 * 一次 load 会连发 snapshot/timeline/klines 多个回调触发整页重建，不加防抖的话
 * 动画会被反复打断；数据真的变了（点数或现价更新）才重新从左往右生长。
 */
internal fun MiniTimeline(
    container: ViewContainer<*, *>,
    model: StockQuoteCardModel,
    context: CardContext,
    height: Float = 72f,
) {
    val quote = model.quote
    val theme = context.theme
    val pts = quote.timeline
    val state = MiniChartState()
    val signature = pts.size to (pts.lastOrNull()?.price ?: 0.0)
    val trackerKey = "${model.cardId}#$height"
    if (pts.isEmpty() || miniDrawOnTracker[trackerKey] == signature) {
        state.progress = 1f
    } else {
        miniDrawOnTracker[trackerKey] = signature
        if (miniDrawOnTracker.size > 128) miniDrawOnTracker.clear()
        var i = 0
        fun tick() {
            i++
            state.progress = (i.toFloat() / MINI_DRAW_ON_STEPS).coerceIn(0f, 1f)
            if (state.progress < 1f) setTimeout(MINI_DRAW_ON_STEP_MS) { tick() }
        }
        tick()
        setTimeout(MINI_DRAW_ON_SAFETY_MS) { state.progress = 1f }
    }

    container.Canvas({
        attr { height(height); marginTop(10f); alignSelfStretch() }
    }) { canvas, width, canvasHeight ->
        val geometry = TimeLineCalculator.calculate(quote.timeline, width, canvasHeight, quote.previousClose)
        if (geometry.points.isEmpty()) return@Canvas
        val progress = state.progress
        val visible = if (progress >= 1f) geometry.points.size
        else max(2, (geometry.points.size * progress).roundToInt())

        // 昨收基准虚线（结构层，直接全量呈现，不参与生长）
        canvas.beginPath()
        canvas.moveTo(0f, geometry.baselineY)
        canvas.lineTo(width, geometry.baselineY)
        canvas.setLineDash(listOf(3f, 4f))
        canvas.strokeStyle(theme.divider)
        canvas.lineWidth(1f)
        canvas.stroke()
        canvas.setLineDash(emptyList())

        val tone = if (quote.rising) theme.rise else theme.fall

        // 面积淡渐变：闭合到昨收基准，随线头一同生长（迷你图只做单侧轻渐变）
        if (visible >= 2) {
            val fill = canvas.createLinearGradient(0f, 0f, 0f, canvasHeight)
            fill.addColorStop(0f, tone.opacity(0.14f))
            fill.addColorStop(1f, tone.opacity(0.02f))
            canvas.beginPath()
            canvas.moveTo(geometry.points[0].x, geometry.baselineY)
            for (i in 0 until visible) canvas.lineTo(geometry.points[i].x, geometry.points[i].y)
            canvas.lineTo(geometry.points[visible - 1].x, geometry.baselineY)
            canvas.closePath()
            canvas.fillStyle(fill)
            canvas.fill()
        }

        // 价格折线：只画前 visible 个点 → 线头从左向右推进
        canvas.beginPath()
        for (i in 0 until visible) {
            val point = geometry.points[i]
            if (i == 0) canvas.moveTo(point.x, point.y) else canvas.lineTo(point.x, point.y)
        }
        canvas.strokeStyle(tone)
        canvas.lineWidth(2f)
        canvas.lineCapRound()
        canvas.stroke()

        // 生长头/now 点：动画中是线头，走完后落在现价位置
        val tip = geometry.points[visible - 1]
        canvas.beginPath()
        canvas.arc(tip.x, tip.y, 2.2f, 0f, (2 * PI).toFloat(), false)
        canvas.fillStyle(tone)
        canvas.fill()
    }
}

/** 迷你分时 draw-on 参数：18 步 × 20ms ≈ 360ms；900ms 兜底强制走完。 */
private const val MINI_DRAW_ON_STEPS = 18
private const val MINI_DRAW_ON_STEP_MS = 20
private const val MINI_DRAW_ON_SAFETY_MS = 900

/** draw-on 进度状态（R1：draw 闭包读 progress observable 驱动逐帧重绘）。 */
private class MiniChartState {
    var progress by observable(0f)
}

/** 已播动画签名表：key = cardId#height，value = (分时点数, 最新价)，数据变了才重播。 */
private val miniDrawOnTracker = mutableMapOf<String, Pair<Int, Double>>()

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
