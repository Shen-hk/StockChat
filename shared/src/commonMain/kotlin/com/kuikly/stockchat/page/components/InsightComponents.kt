package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.SourceStamp
import com.kuikly.stockchat.foundation.ui.feedback.SourceStampLine
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.InsightSectionTitle(title: String, subtitle: String, theme: StockChatTheme) {
    View {
        attr { marginTop(22f); marginBottom(9f); flexDirectionRow(); alignItemsFlexEnd() }
        Text { attr { text(title); fontSizeScaled(17f); fontWeightBold(); color(theme.textPrimary) } }
        Text { attr { text(subtitle); marginLeft(8f); marginBottom(1f); fontSizeScaled(10f); color(theme.textTertiary) } }
    }
}

fun ViewContainer<*, *>.ExplanationCard(text: String, stamp: SourceStamp, theme: StockChatTheme) {
    View {
        attr {
            padding(14f)
            borderRadius(14f)
            backgroundColor(theme.brandSoft)
        }
        Text { attr { text("股问解读"); fontSizeScaled(10f); fontWeightSemiBold(); color(theme.brand) } }
        Text { attr { text(text); marginTop(7f); fontSizeScaled(13f); lineHeightScaled(20f); color(theme.textPrimary) } }
        SourceStampLine(stamp, theme)
    }
}

fun ViewContainer<*, *>.MetricTile(label: String, value: String, note: String, theme: StockChatTheme, accent: Color = theme.textPrimary) {
    View {
        attr { flex(1f); minWidth(94f); padding(12f); marginRight(7f); marginBottom(7f); borderRadius(12f); backgroundColor(theme.surface) }
        Text { attr { text(label); fontSizeScaled(10f); color(theme.textTertiary) } }
        Text { attr { text(value); marginTop(5f); fontSizeScaled(16f); fontWeightBold(); color(accent) } }
        Text { attr { text(note); marginTop(3f); fontSizeScaled(9f); color(theme.textSecondary) } }
    }
}
