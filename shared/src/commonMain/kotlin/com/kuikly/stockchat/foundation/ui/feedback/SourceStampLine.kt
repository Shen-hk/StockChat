package com.kuikly.stockchat.foundation.ui.feedback

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.provider.SourceStamp
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 统一的信息来源披露行。
 *
 * 本文件是 `page.components.SourceStampLine` 与 `cards.components.CardSourceStampLine`
 * 的**合并结果**（docs/46 A-2）。两套实现的渲染逻辑逐字相同，仅可见性不同，
 * 因此合并后对外只保留一个 [SourceStampLine]，Card 与 Insight 都调它。
 *
 * 行为与合并前完全一致：OFFLINE 档位用 muted 底色、tier 标签转 tertiary 色，
 * 其余档位用品牌蓝 10% 底色；`asOf` 为空时回落为「待更新」。
 */
fun ViewContainer<*, *>.SourceStampLine(stamp: SourceStamp, theme: StockChatTheme) {
    View {
        attr { marginTop(9f); flexDirectionRow(); alignItemsCenter(); flexWrapWrap() }
        View {
            attr {
                paddingLeft(7f); paddingRight(7f); paddingTop(3f); paddingBottom(3f)
                borderRadius(7f)
                backgroundColor(if (stamp.mode.name == "OFFLINE") theme.surfaceMuted else Color(0xFF2F6BFF, 0.10f))
            }
            Text { attr { text(stamp.tier.label); fontSizeScaled(9f); color(if (stamp.mode.name == "OFFLINE") theme.textTertiary else theme.brand) } }
        }
        Text { attr { text("${stamp.source} · 截至 ${stamp.asOf.ifEmpty { "待更新" }}"); marginLeft(7f); fontSizeScaled(9f); color(theme.textTertiary) } }
    }
}
