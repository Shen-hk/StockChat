package com.kuikly.stockchat.foundation.ui

import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 磁贴（2026-09-10 全局统一动作入口组件）：白卡 + 0.5 发丝描边 + 浅投影，
 * 列布局居中，线条图标 22（textPrimary）/ 标签 10（textSecondary）。
 * 源自抽屉 DrawerTile；输入栏媒体弹层、详情页「⋯」菜单、空态引导等
 * 动作入口统一复用，新入口优先用它而不是再手写一份样式。
 *
 * 磁贴宽度交给父容器：横排时外层 flexDirectionRow + 各占 flex(1)，
 * 间距用 8f 的空 View 分隔（DrawerTile 行同款）。
 */
fun ViewContainer<*, *>.FeatureTile(
    label: String,
    theme: StockChatTheme,
    height: Float = 64f,
    icon: ViewContainer<*, *>.() -> Unit,
    onClick: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            height(height)
            flexDirectionColumn()
            allCenter()
            borderRadius(13f)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            boxShadow(BoxShadow(0f, 2f, 8f, Color(0x000000, 0.06f)))
            touchEnable(true)
        }
        icon()
        Text {
            attr {
                text(label)
                marginTop(5f)
                fontSizeScaled(10f)
                color(theme.textSecondary)
            }
        }
        event { click { onClick() } }
    }
}

/**
 * 一行等宽磁贴：自动铺 8f 间距（首尾不留白，外层容器负责边距）。
 * tiles 元素为 (标签, 图标绘制闭包, 点击回调)。
 */
fun ViewContainer<*, *>.FeatureTileRow(
    theme: StockChatTheme,
    height: Float = 64f,
    tiles: List<Triple<String, ViewContainer<*, *>.() -> Unit, () -> Unit>>,
) {
    View {
        attr { flexDirectionRow() }
        tiles.forEachIndexed { index, tile ->
            if (index > 0) {
                View { attr { width(8f) } }
            }
            FeatureTile(
                label = tile.first,
                theme = theme,
                height = height,
                icon = tile.second,
                onClick = tile.third,
            )
        }
    }
}
