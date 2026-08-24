package com.kuikly.stockchat.cards.components

import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.CardRegistry
import com.kuikly.stockchat.cards.core.SkeletonCardModel
import com.kuikly.stockchat.cards.core.UnknownCardModel
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.CardShell(model: CardModel, context: CardContext) {
    val theme = context.theme
    View {
        attr {
            if (context.density == com.kuikly.stockchat.cards.core.CardDensity.MINI) {
                padding(0f)
            } else {
                marginTop(10f)
                padding(14f)
                backgroundColor(theme.surface)
                borderRadius(theme.cardRadius)
            }
        }
        when (model) {
            is SkeletonCardModel -> SkeletonCard(model.cardType, theme)
            is UnknownCardModel -> UnknownCard(model, theme)
            else -> CardRegistry.dispatch(model.cardType)?.render(this, model, context)
                ?: UnknownCard(UnknownCardModel(model.cardType, ""), theme)
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
