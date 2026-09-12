package com.kuikly.stockchat.foundation.ui.feedback

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.design.GlassRenderer
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.surface.GlassBackdrop
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 数据模式角标（离线演示模式等）。只接收展示文案与主题，不读数据源。
 *
 * 2026-09-12（doc 47 B-2）从 page/components/AppChrome.kt 原样拆出。
 */
fun ViewContainer<*, *>.DataModeBadge(
    theme: StockChatTheme,
    text: String = "离线演示模式",
    renderer: GlassRenderer = GlassRenderer.Default,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            paddingTop(5f)
            paddingBottom(5f)
            paddingLeft(8f)
            paddingRight(8f)
        }
        GlassBackdrop(theme.glass.peek, renderer)
        View { attr { size(6f, 6f); borderRadius(3f); backgroundColor(theme.brand); marginRight(5f) } }
        Text { attr { text(text); fontSizeScaled(10f); fontWeightMedium(); color(theme.brand) } }
    }
}
