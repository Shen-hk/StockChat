package com.kuikly.stockchat.foundation.ui.feedback

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.data.provider.SourceStamp
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 数据来源披露行（tier 徽标 + 来源 + 截至时间）。
 *
 * 2026-09-12（doc 46 A-2）合并：此前 page/components/InsightComponents.kt 与
 * cards/components/SourceStampLine.kt 各有一份实现**逐字节相同**的拷贝
 * （SourceStampLine / CardSourceStampLine）。此处合并为唯一入口，Card 侧与
 * Insight 侧都调它；实现体照抄原样，对外零视觉差异。
 */
fun ViewContainer<*, *>.SourceStampLine(stamp: SourceStamp, theme: StockChatTheme) {
    View {
        attr { marginTop(9f); flexDirectionRow(); alignItemsCenter(); flexWrapWrap() }
        View {
            attr { paddingLeft(7f); paddingRight(7f); paddingTop(3f); paddingBottom(3f); borderRadius(7f); backgroundColor(if (stamp.mode.name == "OFFLINE") theme.surfaceMuted else Color(0xFF2F6BFF, 0.10f)) }
            Text { attr { text(stamp.tier.label); fontSizeScaled(9f); color(if (stamp.mode.name == "OFFLINE") theme.textTertiary else theme.brand) } }
        }
        Text { attr { text("${stamp.source} · 截至 ${stamp.asOf.ifEmpty { "待更新" }}"); marginLeft(7f); fontSizeScaled(9f); color(theme.textTertiary) } }
    }
}
