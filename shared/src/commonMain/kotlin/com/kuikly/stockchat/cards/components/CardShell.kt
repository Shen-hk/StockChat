package com.kuikly.stockchat.cards.components

import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.SkeletonCardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.core.UnknownCardModel
import com.kuikly.stockchat.cards.stock.AttributionCardRenderer
import com.kuikly.stockchat.cards.stock.DefinitionCardRenderer
import com.kuikly.stockchat.cards.stock.InsightCardRenderer
import com.kuikly.stockchat.cards.stock.NewsCardRenderer
import com.kuikly.stockchat.cards.stock.StockChartCardRenderer
import com.kuikly.stockchat.cards.stock.StockQuoteCardRenderer
import com.kuikly.stockchat.cards.stock.StockCompareCardRenderer
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.CardShell(model: CardModel, context: CardContext) {
    val theme = context.theme
    View {
        attr {
            marginTop(10f)
            padding(14f)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
        }
        when (model) {
            is StockQuoteCardModel -> StockQuoteCardRenderer.render(this, model, context)
            is StockChartCardModel -> StockChartCardRenderer.render(this, model, context)
            is AttributionCardModel -> AttributionCardRenderer.render(this, model, context)
            is DefinitionCardModel -> DefinitionCardRenderer.render(this, model, context)
            is InsightCardModel -> InsightCardRenderer.render(this, model, context)
            is StockCompareCardModel -> StockCompareCardRenderer.render(this, model, context)
            is NewsCardModel -> NewsCardRenderer.render(this, model, context)
            is SkeletonCardModel -> SkeletonCard(model.cardType, theme)
            is UnknownCardModel -> UnknownCard(model, theme)
        }
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
