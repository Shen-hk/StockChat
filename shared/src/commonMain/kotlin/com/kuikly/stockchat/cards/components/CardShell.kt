package com.kuikly.stockchat.cards.components

import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CardEvent
import com.kuikly.stockchat.cards.core.ExpandMode
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.CardRegistry
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.SkeletonCardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.core.UnknownCardModel
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.CollapsibleCard(model: CardModel, context: CardContext) {
    CardShell(model, context)
}

fun ViewContainer<*, *>.CardShell(model: CardModel, context: CardContext) {
    val theme = context.theme
    val focusEnabled = context.cardKey.isNotEmpty() && context.onFocusChanged != null
    val focused = focusEnabled && context.focusedCardKey == context.cardKey
    val dimmed = focusEnabled && context.focusedCardKey.isNotEmpty() && !focused
    val compareSelected = (model as? StockQuoteCardModel)?.quote?.symbol == context.compareCandidateSymbol
    var focusPanStartY = 0f
    val canToggleExpanded = context.density == CardDensity.COMPACT &&
        model.expandMode == ExpandMode.ACCORDION &&
        context.onToggleExpanded != null &&
        model !is SkeletonCardModel &&
        model !is UnknownCardModel
    val renderContext = if (canToggleExpanded && context.expanded) {
        context.copy(density = CardDensity.FULL)
    } else {
        context
    }
    View {
        attr {
            if (context.density == CardDensity.MINI) {
                padding(0f)
            } else {
                alignSelfStretch()
                marginTop(10f)
                padding(14f)
                backgroundColor(if (compareSelected) theme.brandSoft else theme.surface)
                borderRadius(theme.cardRadius)
            }
            opacity(if (dimmed) 0.35f else 1f)
            transform(scale = if (focused) Scale(1.04f, 1.04f) else Scale.DEFAULT)
            animate(Animation.easeOut(0.2f), focused)
            if (focused || compareSelected) border(Border(2f, BorderStyle.SOLID, theme.brand))
            if (focusEnabled) capture(CaptureRule.pan(CaptureRuleDirection.VERTICAL))
        }
        if (focusEnabled) {
            event {
                longPress { params ->
                    when (params.state) {
                        "start" -> {
                            context.onCardEvent?.invoke(context.cardKey, CardEvent.FocusStart)
                            (model as? StockQuoteCardModel)?.let { quoteModel ->
                                context.onCompareCandidate?.invoke(context.cardKey, quoteModel.quote.symbol)
                            }
                            context.onFocusChanged?.invoke(context.cardKey, true)
                        }
                        "end" -> {
                            context.onCardEvent?.invoke(context.cardKey, CardEvent.FocusEnd)
                            context.onFocusChanged?.invoke(context.cardKey, false)
                        }
                    }
                }
                pan { params ->
                    when (params.state) {
                        "start" -> focusPanStartY = params.y
                        "end" -> if (focused) {
                            val deltaY = params.y - focusPanStartY
                            context.onCardEvent?.invoke(context.cardKey, CardEvent.FocusPanEnd(deltaY))
                            when {
                                deltaY <= -28f -> {
                                    context.onCardEvent?.invoke(context.cardKey, CardEvent.RequestFullScreen)
                                    focusTargetSymbol(model)?.let(context.onOpenStock) ?: context.onOpenSheet?.invoke(model)
                                }
                                deltaY >= 28f && context.expanded -> {
                                    context.onCardEvent?.invoke(context.cardKey, CardEvent.Collapse)
                                    context.onToggleExpanded?.invoke()
                                }
                            }
                        }
                    }
                }
            }
        }
        when (model) {
            is SkeletonCardModel -> SkeletonCard(model.cardType, theme)
            is UnknownCardModel -> UnknownCard(model, theme)
            else -> CardRegistry.dispatch(model.cardType)?.render(this, model, renderContext)
                ?: UnknownCard(UnknownCardModel(model.cardType, ""), theme)
        }
        if (context.density == CardDensity.COMPACT && compareSelected) {
            Text {
                attr {
                    text("待对比：再点另一张行情卡")
                    marginTop(8f)
                    fontSize(10f)
                    color(theme.brand)
                }
            }
        }
        if (canToggleExpanded) {
            View {
                attr {
                    alignSelfFlexStart()
                    marginTop(10f)
                    paddingTop(3f)
                    paddingBottom(3f)
                    paddingLeft(2f)
                    paddingRight(2f)
                }
                Text {
                    attr {
                        text(if (context.expanded) "收起 ▲" else "查看完整内容 ▼")
                        fontSize(11f)
                        color(theme.brand)
                    }
                }
                event {
                    click {
                        context.onCardEvent?.invoke(context.cardKey, if (context.expanded) CardEvent.Collapse else CardEvent.Expand)
                        context.onToggleExpanded?.invoke()
                    }
                }
            }
        }
        if (context.density == CardDensity.COMPACT && model.expandMode == ExpandMode.BOTTOM_SHEET && context.onOpenSheet != null) {
            CardAction("查看完整内容 ▼", theme) {
                context.onCardEvent?.invoke(context.cardKey, CardEvent.RequestFullScreen)
                context.onOpenSheet.invoke(model)
            }
        }
        if (context.density == CardDensity.COMPACT && model.cardType == "stock-chart" && context.onOpenSheet != null) {
            CardAction("全屏查看 ▼", theme) {
                context.onCardEvent?.invoke(context.cardKey, CardEvent.RequestFullScreen)
                context.onOpenSheet.invoke(model)
            }
        }
        if (context.density == CardDensity.COMPACT && model.expandMode == ExpandMode.NESTED_CONVERSATION && context.onStartSubThread != null) {
            CardAction("深挖此点", theme) {
                context.onCardEvent?.invoke(context.cardKey, CardEvent.StartSubThread(model.cardId))
                context.onStartSubThread.invoke(model)
            }
        }
    }
}

private fun focusTargetSymbol(model: CardModel): String? = when (model) {
    is StockQuoteCardModel -> model.quote.symbol
    is StockChartCardModel -> model.quote.symbol
    is AttributionCardModel -> model.quote.symbol
    is InsightCardModel -> model.quote.symbol
    is NewsCardModel -> model.quote.symbol
    is StockCompareCardModel -> model.quotes.firstOrNull()?.symbol
    else -> null
}

private fun ViewContainer<*, *>.CardAction(label: String, theme: com.kuikly.stockchat.cards.theme.StockChatTheme, action: () -> Unit) {
    View {
        attr {
            alignSelfFlexStart()
            marginTop(10f)
            paddingTop(3f)
            paddingBottom(3f)
            paddingLeft(2f)
            paddingRight(2f)
        }
        Text { attr { text(label); fontSize(11f); color(theme.brand) } }
        event { click { action() } }
    }
}

private fun ViewContainer<*, *>.SkeletonCard(type: String, theme: com.kuikly.stockchat.cards.theme.StockChatTheme) {
    Text {
        attr {
            text("正在准备 ${type.ifEmpty { "结构化内容" }}")
            fontSize(13f)
            color(theme.textSecondary)
        }
    }
    View {
        attr {
            height(54f)
            marginTop(10f)
            backgroundColor(theme.surfaceMuted)
            borderRadius(10f)
        }
    }
}

private fun ViewContainer<*, *>.UnknownCard(model: UnknownCardModel, theme: com.kuikly.stockchat.cards.theme.StockChatTheme) {
    Text {
        attr {
            text("该内容类型暂不支持")
            fontSize(15f)
            fontWeightMedium()
            color(theme.textPrimary)
        }
    }
    Text {
        attr {
            text("类型：${model.cardType}")
            marginTop(6f)
            fontSize(12f)
            color(theme.textTertiary)
        }
    }
}
