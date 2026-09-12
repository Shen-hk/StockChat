package com.kuikly.stockchat.watchlist.component

import com.kuikly.stockchat.foundation.ui.fontSizeScaled

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 底部撤销条（Snackbar）。
 *
 * 存在的理由：破坏性操作从「常驻可见按钮」换成左滑手势后，误触率必然上升——手指
 * 在滑动过程中很容易带出动作。而撤销入口如果还留在页面顶部的 hint 里，用户滑动时
 * 视线在行上，根本看不见。所以撤销必须跟着手指走，落在拇指可达的底部。
 *
 * 两点取舍：
 * - **不做入场动画**。Kuikly 的 animate / animation 两个接口都靠 observable 变化驱动
 *   （`Attr.animate` 取 `ReactiveObserver.currentObservablePropertyKey`），而本组件由
 *   `vif` 创建，text 在建视图前就已非空，没有可驱动的变化。与其伪造一个 observable
 *   换 200ms 淡入，不如直接出现——Snackbar 本身就是即时反馈，动效克制优先。
 * - **用深色底 + 白字**。撤销条是浮层，必须和页面内容明确分层；沿用 theme 既有
 *   token（textPrimary 底 / page 字 / onBrand 按钮），不新增颜色。
 *
 * @param text 以 lambda 传入，保证在 vif / attr 闭包**内**读取才建立响应式依赖。
 */
fun ViewContainer<*, *>.UndoBar(
    theme: StockChatTheme,
    text: () -> String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    vif({ text().isNotEmpty() }) {
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                left(14f)
                right(14f)
                bottom(24f)
                height(48f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(14f)
                paddingRight(10f)
                borderRadius(14f)
                backgroundColor(theme.textPrimary)
                zIndex(30, useOutline = false)
            }
            Text {
                attr {
                    flex(1f)
                    text(text())
                    fontSizeScaled(13f)
                    color(theme.page)
                }
            }
            View {
                attr {
                    paddingLeft(12f)
                    paddingRight(12f)
                    height(32f)
                    allCenter()
                    borderRadius(8f)
                    // 半透明白底，避免为浮层按钮新增一个饱和色。
                    backgroundColor(Color(0xFFFFFFFF, 0.16f))
                }
                Text {
                    attr {
                        text(actionLabel)
                        fontSizeScaled(13f)
                        fontWeightSemiBold()
                        color(theme.onBrand)
                    }
                }
                event { click { onAction() } }
            }
        }
    }
}
