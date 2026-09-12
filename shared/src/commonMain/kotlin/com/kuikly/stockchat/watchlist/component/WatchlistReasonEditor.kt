package com.kuikly.stockchat.watchlist.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.watchlist.state.WatchlistCoordinator
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

/**
 * z5 行操作浮层（doc 47 B-4 组件层）：长按菜单（分组/理由/置顶/步进排序/移除）
 * 与 FR-W2 理由编辑浮层（一行输入 + 3 个常用理由 chip，可跳过不强制）。
 * 布局/文案/参数逐字来自原 WatchlistPage。
 */
internal fun ViewContainer<*, *>.WatchlistMenuOverlay(
    theme: StockChatTheme,
    data: WatchlistCoordinator,
    safeAreaBottom: Float,
) {
    val pageTheme = theme
    View {
        attr {
            absolutePosition()
            top(0f)
            left(0f)
            right(0f)
            bottom(0f)
            zIndex(40, useOutline = false)
            backgroundColor(Color(0x000000, 0.42f))
            justifyContentFlexEnd()
            paddingBottom(safeAreaBottom)
        }
        event { click { data.closeMenu() } }
        View {
            attr {
                paddingLeft(16f)
                paddingRight(16f)
                paddingBottom(28f)
            }
            event { click { /* 吃掉点击 */ } }
            View {
                attr {
                    borderRadius(18f)
                    backgroundColor(pageTheme.surface)
                    paddingTop(6f)
                    paddingBottom(6f)
                }
                Text {
                    attr {
                        text(data.menuTitle())
                        marginTop(10f)
                        marginLeft(16f)
                        fontSizeScaled(11f)
                        color(pageTheme.textTertiary)
                    }
                }
                // FR-W2：菜单里回看理由（行上不展示，保持扫描效率）
                vif({ data.currentReason().isNotEmpty() }) {
                    Text {
                        attr {
                            text("当初理由：${data.currentReason()}")
                            marginTop(3f)
                            marginLeft(16f)
                            marginRight(16f)
                            fontSizeScaled(11.5f)
                            lineHeightScaled(16f)
                            color(pageTheme.textSecondary)
                        }
                    }
                }
                // FR-W8：理由变更历史回看「我改主意了几次」
                vif({ data.currentReasonHistory().isNotEmpty() }) {
                    Text {
                        attr {
                            text("之前：${data.currentReasonHistory().joinToString(" ← ")}")
                            marginTop(3f)
                            marginLeft(16f)
                            marginRight(16f)
                            fontSizeScaled(10.5f)
                            lineHeightScaled(15f)
                            color(pageTheme.textTertiary)
                        }
                    }
                }
                listOf(
                    "core" to "设为核心观察",
                    "research" to "设为待研究",
                    "" to "清除分组",
                ).forEach { (groupId, label) ->
                    WatchlistMenuRow(label = label, destructive = false, theme = pageTheme) {
                        data.setGroupTo(data.menuSymbol, groupId)
                        data.closeMenu()
                    }
                }
                WatchlistMenuRow(
                    label = if (data.currentReason().isEmpty()) "设置关注理由" else "修改关注理由",
                    destructive = false,
                    theme = pageTheme,
                ) {
                    data.beginReasonEdit()
                }
                WatchlistMenuRow(label = "置顶", destructive = false, theme = pageTheme) {
                    data.pinToTop(data.menuSymbol)
                    data.closeMenu()
                }
                // FR-W7 手动排序：菜单步进（上移/下移一位）。与拖拽排序并存，
                // 能力等价（可到任意位），给不开拖拽习惯的用户一个显式入口。
                WatchlistMenuRow(label = "上移一位", destructive = false, theme = pageTheme) {
                    data.moveRow(data.menuSymbol, -1)
                    data.closeMenu()
                }
                WatchlistMenuRow(label = "下移一位", destructive = false, theme = pageTheme) {
                    data.moveRow(data.menuSymbol, +1)
                    data.closeMenu()
                }
                WatchlistMenuRow(label = "移除", destructive = true, theme = pageTheme) {
                    data.remove(data.menuSymbol)
                    data.closeMenu()
                }
            }
            View {
                attr {
                    marginTop(8f)
                    height(50f)
                    allCenter()
                    borderRadius(18f)
                    backgroundColor(pageTheme.surface)
                }
                Text {
                    attr { text("取消"); fontSizeScaled(14f); fontWeightMedium(); color(pageTheme.textSecondary) }
                }
                event { click { data.closeMenu() } }
            }
        }
    }
}

private fun ViewContainer<*, *>.WatchlistMenuRow(
    label: String,
    destructive: Boolean,
    theme: StockChatTheme,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(46f)
            paddingLeft(16f)
            paddingRight(16f)
            justifyContentCenter()
        }
        Text {
            attr {
                text(label)
                fontSizeScaled(14.5f)
                color(if (destructive) theme.rise else theme.textPrimary)
            }
        }
        event { click { onClick() } }
    }
}

/** FR-W2 理由编辑浮层：三入口（搜索添加 / 长按菜单 / 详情页）共用。 */
internal fun ViewContainer<*, *>.WatchlistReasonEditorOverlay(
    theme: StockChatTheme,
    data: WatchlistCoordinator,
    safeAreaBottom: Float,
) {
    val pageTheme = theme
    View {
        attr {
            absolutePosition()
            top(0f)
            left(0f)
            right(0f)
            bottom(0f)
            zIndex(45, useOutline = false)
            backgroundColor(Color(0x000000, 0.42f))
            justifyContentFlexEnd()
            paddingBottom(safeAreaBottom)
        }
        event { click { data.closeReasonEdit() } }
        View {
            attr {
                marginLeft(16f)
                marginRight(16f)
                marginBottom(28f)
                borderRadius(18f)
                backgroundColor(pageTheme.surface)
                padding(16f)
            }
            event { click { /* 吃掉点击 */ } }
            Text {
                attr {
                    text("为什么关注 ${data.rows.firstOrNull { it.symbol == data.reasonEditSymbol }?.name.orEmpty()}？")
                    fontSizeScaled(14f)
                    fontWeightMedium()
                    color(pageTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("记下当初的理由，之后在风险地图对照「当初理由 vs 当前事实」。留空可跳过。")
                    marginTop(4f)
                    fontSizeScaled(11f)
                    lineHeightScaled(16f)
                    color(pageTheme.textTertiary)
                }
            }
            View {
                attr {
                    marginTop(12f)
                    height(38f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(11f)
                    paddingRight(11f)
                    borderRadius(10f)
                    backgroundColor(pageTheme.surfaceMuted)
                }
                TextArea {
                    attr {
                        flex(1f)
                        height(36f)
                        fontSizeScaled(13f)
                        color(pageTheme.textPrimary)
                        backgroundColor(Color(0xFFFFFFFF, 0f))
                        text("")
                        placeholder("业绩好转 / 前景看好 / 观察一下…（≤40 字）")
                        placeholderColor(pageTheme.textTertiary)
                        tintColor(pageTheme.brand)
                        selectionColor(pageTheme.brand)
                    }
                    event {
                        textDidChange(isSyncEdit = true) { state ->
                            data.onReasonTyped(state.text)
                        }
                    }
                }
            }
            View {
                attr { marginTop(10f); flexDirectionRow() }
                listOf("业绩", "政策", "技术面").forEach { chip ->
                    View {
                        attr {
                            marginRight(8f)
                            paddingTop(6f)
                            paddingBottom(6f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            borderRadius(14f)
                            backgroundColor(
                                if (data.reasonChip == chip) pageTheme.brand else pageTheme.surfaceMuted,
                            )
                        }
                        event { click { data.toggleReasonChip(chip) } }
                        Text {
                            attr {
                                text(chip)
                                fontSizeScaled(12f)
                                color(if (data.reasonChip == chip) Color(0xFFFFFFFF, 1f) else pageTheme.textSecondary)
                            }
                        }
                    }
                }
            }
            View {
                attr { marginTop(14f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        flex(1f)
                        height(42f)
                        allCenter()
                        borderRadius(12f)
                        backgroundColor(pageTheme.surfaceMuted)
                    }
                    event { click { data.closeReasonEdit() } }
                    Text { attr { text("跳过"); fontSizeScaled(13.5f); color(pageTheme.textSecondary) } }
                }
                View {
                    attr {
                        flex(1f)
                        marginLeft(10f)
                        height(42f)
                        allCenter()
                        borderRadius(12f)
                        backgroundColor(pageTheme.brand)
                    }
                    event { click { data.saveReason() } }
                    Text { attr { text("保存"); fontSizeScaled(13.5f); fontWeightMedium(); color(Color(0xFFFFFFFF, 1f)) } }
                }
            }
        }
    }
}
