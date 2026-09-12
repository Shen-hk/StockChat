package com.kuikly.stockchat.chat.composer.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.session.component.ComposerGuideRow
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.View

internal data class ComposerChromeProps(
    val theme: StockChatTheme,
    val emptySession: () -> Boolean,
    val welcomePresented: () -> Boolean,
    val expanded: () -> Boolean,
    val keyboardHeight: () -> Float,
    val bottomInset: Float,
    val composerDropActive: () -> Boolean,
    val onGuideSelected: (String) -> Unit,
    val renderRim: (ViewContainer<*, *>) -> Unit,
)

internal fun ViewContainer<*, *>.ComposerChrome(
    props: ComposerChromeProps,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            val visible = !props.emptySession() || props.welcomePresented()
            opacity(if (visible) 1f else 0f)
            transform(Translate(0f, if (visible) 0f else 18f))
            animate(Animation.easeOut(0.36f).delay(0.10f), props.welcomePresented())
        }
        View {
            attr {
                absolutePosition(bottom = 0f, left = 0f, right = 0f)
                height(10f + props.bottomInset + props.keyboardHeight())
                backgroundColor(props.theme.page)
                touchEnable(false)
            }
        }
        View {
            attr {
                val expanded = props.expanded()
                height(if (expanded) 0f else GUIDE_HEIGHT)
                opacity(if (expanded) 0f else 1f)
                touchEnable(!expanded)
                animate(Animation.easeOut(LAYOUT_DURATION), expanded)
            }
            View {
                attr { marginBottom(8f); paddingTop(8f); paddingBottom(8f); alignSelfFlexStart() }
                ComposerGuideRow(props.theme, props.onGuideSelected)
            }
        }
        View {
            attr {
                borderRadius(26f); paddingTop(9f); paddingBottom(9f); paddingLeft(10f); paddingRight(10f)
                boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
                backgroundColor(if (props.composerDropActive()) props.theme.brandSoft else props.theme.surface)
                transform(scale = if (props.composerDropActive()) Scale(1.015f, 1.015f) else Scale.DEFAULT)
                animate(Animation.easeOut(0.16f), props.composerDropActive())
            }
            content()
            props.renderRim(this)
        }
    }
}

private const val GUIDE_HEIGHT = 42f
private const val LAYOUT_DURATION = 0.28f
