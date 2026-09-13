package com.kuikly.stockchat.chat.composer.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.session.component.ComposerGuideRow
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.View

internal data class ComposerChromeProps(
    val theme: StockChatTheme,
    /** Theme closures keep persistent composer layers live across appearance changes. */
    val pageColor: () -> Color,
    val surfaceColor: () -> Color,
    val brandSoftColor: () -> Color,
    val emptySession: () -> Boolean,
    val welcomeGuidePresented: () -> Boolean,
    val welcomeComposerPresented: () -> Boolean,
    val expanded: () -> Boolean,
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
        View {
            attr {
                absolutePosition(bottom = 0f, left = 0f, right = 0f)
                // 不给胶囊以下再铺一层实色底；安全区由页面自身的背景承接。
                // 保留透明视图以清除已挂载实例上的旧背景属性。
                height(0f)
                backgroundColor(props.pageColor().opacity(0f))
                touchEnable(false)
            }
        }
        View {
            attr {
                val emptySession = props.emptySession()
                val presented = props.welcomeGuidePresented()
                opacity(if (!emptySession || presented) 1f else 0f)
                // 引导语是欢迎入场的第三拍；读驱动必须在其它 observable 之后。
                animate(Animation.easeOut(0.24f), props.welcomeGuidePresented())
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
        }
        View {
            attr {
                val emptySession = props.emptySession()
                val presented = props.welcomeComposerPresented()
                opacity(if (!emptySession || presented) 1f else 0f)
                // 输入框永远单独作为最后一拍淡入，不再和引导语共用状态。
                animate(Animation.easeOut(0.28f), props.welcomeComposerPresented())
            }
            View {
                attr {
                    borderRadius(26f); paddingTop(9f); paddingBottom(9f); paddingLeft(10f); paddingRight(10f)
                    boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
                    backgroundColor(if (props.composerDropActive()) props.brandSoftColor() else props.surfaceColor())
                    transform(scale = if (props.composerDropActive()) Scale(1.015f, 1.015f) else Scale.DEFAULT)
                    animate(Animation.easeOut(0.16f), props.composerDropActive())
                }
                content()
                props.renderRim(this)
            }
        }
    }
}

private const val GUIDE_HEIGHT = 42f
private const val LAYOUT_DURATION = 0.28f
