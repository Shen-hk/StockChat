package com.kuikly.stockchat.cards.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.provider.SourceStamp
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Card-owned source disclosure; intentionally independent of pages and features. */
internal fun ViewContainer<*, *>.CardSourceStampLine(stamp: SourceStamp, theme: StockChatTheme) {
    View {
        attr { marginTop(9f); flexDirectionRow(); alignItemsCenter(); flexWrapWrap() }
        View {
            attr { paddingLeft(7f); paddingRight(7f); paddingTop(3f); paddingBottom(3f); borderRadius(7f); backgroundColor(if (stamp.mode.name == "OFFLINE") theme.surfaceMuted else Color(0xFF2F6BFF, 0.10f)) }
            Text { attr { text(stamp.tier.label); fontSizeScaled(9f); color(if (stamp.mode.name == "OFFLINE") theme.textTertiary else theme.brand) } }
        }
        Text { attr { text("${stamp.source} · 截至 ${stamp.asOf.ifEmpty { "待更新" }}"); marginLeft(7f); fontSizeScaled(9f); color(theme.textTertiary) } }
    }
}
