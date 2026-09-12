package com.kuikly.stockchat.chat.session.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.kuikly.stockchat.page.components.LineIconClose
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Stateless session overlays. Values that change after mount are supplied as closures (R1/R4). */
internal fun ViewContainer<*, *>.ImagePreviewOverlay(
    path: () -> String,
    pageWidth: Float,
    pageHeight: Float,
    statusBarHeight: Float,
    onClose: () -> Unit,
) {
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
            backgroundColor(Color(0xD9000000))
            allCenter()
            touchEnable(true)
        }
        event { click { onClose() } }
        Image {
            attr {
                src("file://" + path())
                width((pageWidth - 32f).coerceAtLeast(120f))
                height((pageHeight - statusBarHeight - 80f).coerceAtLeast(120f))
                resizeContain()
                borderRadius(12f)
            }
            event { click { } }
        }
        View {
            attr {
                absolutePosition(top = statusBarHeight + 12f, right = 16f)
                size(36f, 36f); allCenter(); borderRadius(18f)
                backgroundColor(Color(0x66000000))
            }
            LineIconClose(color = Color(0xFFFFFFFF), size = 16f)
            event { click { onClose() } }
        }
    }
}

internal fun ViewContainer<*, *>.MessageActionOverlay(
    theme: StockChatTheme,
    renderer: GlassRenderer,
    pageWidth: Float,
    pageHeight: Float,
    presented: () -> Boolean,
    pageX: () -> Float,
    pageY: () -> Float,
    followUpAllowed: () -> Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onFollowUp: () -> Unit,
) {
    View {
        attr { absolutePosition(left = 0f, top = 0f); size(pageWidth, pageHeight) }
        event { click { onDismiss() } }
    }
    View {
        attr {
            val followUp = followUpAllowed()
            val menuWidth = if (followUp) 174f else 96f
            val y = pageY()
            absolutePosition(
                left = (pageX() - 20f).coerceIn(12f, (pageWidth - menuWidth - 12f).coerceAtLeast(12f)),
                top = if (y + 150f > pageHeight - 160f) y - 96f else y + 14f,
            )
            height(44f); borderRadius(14f)
            boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
            border(Border(1f, BorderStyle.SOLID, Color(0xFFFFFF, 0.35f)))
            val shown = presented()
            opacity(if (shown) 1f else 0f)
            transform(scale = if (shown) Scale(1f, 1f) else Scale(0.9f, 0.9f))
            animate(Animation.easeOut(0.2f), presented())
        }
        GlassBackdrop(theme.glass.peek, renderer)
        View {
            attr { flexDirectionRow(); alignItemsCenter(); height(44f); paddingLeft(5f); paddingRight(5f) }
            View {
                attr { height(34f); paddingLeft(14f); paddingRight(14f); allCenter() }
                Text { attr { text("复制"); fontSizeScaled(13f); color(theme.textPrimary) } }
                event { click { onCopy() } }
            }
            if (followUpAllowed()) {
                View { attr { width(1f); height(18f); backgroundColor(theme.textTertiary.opacity(0.25f)) } }
                View {
                    attr { height(34f); paddingLeft(14f); paddingRight(14f); allCenter() }
                    Text { attr { text("追问"); fontSizeScaled(13f); fontWeightMedium(); color(theme.brand) } }
                    event { click { onFollowUp() } }
                }
            }
        }
    }
}
