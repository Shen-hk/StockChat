package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** The compact, conversation-first chrome used by ChatHome. */
fun ViewContainer<*, *>.ChatTopNav(
    statusBarHeight: Float,
    theme: StockChatTheme,
    drawerOpen: Boolean,
    glass: Boolean,
    liveData: Boolean,
    renderer: GlassRenderer = GlassRenderer.Default,
    contextTitle: String? = null,
    onMenu: () -> Unit,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
) {
    View {
        attr {
            // This chrome must float above the scroller.  If it participates in
            // the flex column, the page content is laid out below it and there
            // is nothing for the backdrop blur to refract.
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(statusBarHeight + 44f)
            paddingTop(statusBarHeight)
            if (!(glass || drawerOpen || contextTitle != null)) backgroundColor(theme.page)
        }
        if (glass || drawerOpen || contextTitle != null) GlassBackdrop(theme.glass.sheet, renderer)
        View {
            attr {
                height(44f)
                paddingLeft(8f)
                paddingRight(8f)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr { size(40f, 40f); allCenter(); borderRadius(12f); backgroundColor(if (drawerOpen) theme.surfaceMuted else theme.page) }
                Text { attr { text(if (drawerOpen) "×" else "☰"); fontSize(22f); color(theme.textPrimary) } }
                event { click { onMenu() } }
            }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(contextTitle ?: "StockChat.")
                        fontSize(if (contextTitle == null) 17f else 15f)
                        fontWeightBold()
                        color(if (contextTitle == null) theme.textPrimary else theme.textSecondary)
                    }
                }
                View {
                    attr {
                        size(5f, 5f)
                        marginLeft(7f)
                        borderRadius(3f)
                        backgroundColor(if (liveData) Color(0xFF34C759) else theme.textTertiary)
                    }
                }
            }
            View {
                attr { size(40f, 40f); allCenter(); borderRadius(12f) }
                Text { attr { text("◷"); fontSize(21f); color(theme.textSecondary) } }
                event { click { onHistory() } }
            }
            View {
                attr { size(40f, 40f); allCenter(); borderRadius(12f) }
                Text { attr { text("＋"); fontSize(23f); color(theme.brand) } }
                event { click { onNewChat() } }
            }
        }
    }
}

fun ViewContainer<*, *>.ChatDrawer(
    statusBarHeight: Float,
    bottomInset: Float,
    theme: StockChatTheme,
    liveData: Boolean,
    renderer: GlassRenderer = GlassRenderer.Default,
    visualLabel: String = renderer.statusLabel(),
    onClose: () -> Unit,
    onToggleDataMode: () -> Unit,
    onCycleVisualMode: () -> Unit = {},
    onSettings: () -> Unit,
) {
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
            backgroundColor(Color(0x59000000))
        }
        event { click { onClose() } }
    }
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, bottom = 0f)
            width(280f)
            paddingTop(statusBarHeight + 12f)
            paddingLeft(12f)
            paddingRight(12f)
            paddingBottom(bottomInset + 12f)
        }
        GlassBackdrop(theme.glass.sheet, renderer)
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr { flex(1f); padding(12f); flexDirectionRow(); alignItemsCenter(); backgroundColor(theme.surface); borderRadius(14f) }
            View { attr { size(8f, 8f); borderRadius(4f); backgroundColor(if (liveData) Color(0xFF34C759) else theme.textTertiary); marginRight(8f) } }
            View {
                attr { flex(1f) }
                Text { attr { text(if (liveData) "实时数据" else "模拟数据"); fontSize(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
                Text { attr { text(if (liveData) "行情与信息均为真实数据" else "使用本地演示数据"); marginTop(2f); fontSize(10f); color(theme.textTertiary) } }
            }
            View {
                attr { width(40f); height(24f); borderRadius(12f); padding(3f); backgroundColor(if (liveData) theme.brand else theme.surfaceMuted) }
                Text { attr { text(if (liveData) "●" else "○"); fontSize(16f); color(theme.surface) } }
                event { click { onToggleDataMode() } }
            }
            }
            View {
                attr { size(34f, 34f); marginLeft(8f); allCenter(); borderRadius(10f); backgroundColor(theme.surfaceMuted) }
                Text { attr { text("×"); fontSize(20f); color(theme.textSecondary) } }
                event { click { onClose() } }
            }
        }
        View {
            attr {
                marginTop(8f)
                paddingLeft(12f)
                paddingRight(8f)
                height(34f)
                flexDirectionRow()
                alignItemsCenter()
                backgroundColor(theme.surface)
                borderRadius(10f)
            }
            Text { attr { text(visualLabel); fontSize(12f); color(theme.textSecondary); flex(1f) } }
            Text { attr { text("切换"); fontSize(11f); fontWeightMedium(); color(theme.brand) } }
            event { click { onCycleVisualMode() } }
        }
        DrawerGroupTitle("会话历史", theme)
        DrawerItem("贵州茅台为何大跌", "今天", theme, active = true)
        DrawerItem("和五粮液对比", "今天", theme)
        DrawerItem("半导体板块后市", "昨天", theme)
        View { attr { height(1f); marginTop(10f); marginBottom(10f); marginLeft(2f); marginRight(2f); backgroundColor(theme.divider) } }
        DrawerItem("☆  自选股", "", theme)
        DrawerItem("⌘  术语表", "", theme)
        View { attr { height(1f); marginTop(10f); marginBottom(10f); marginLeft(2f); marginRight(2f); backgroundColor(theme.divider) } }
        DrawerItem("⚙  设置", "", theme, onClick = onSettings)
    }
}

private fun ViewContainer<*, *>.DrawerGroupTitle(text: String, theme: StockChatTheme) {
    Text { attr { text(text); marginTop(22f); marginBottom(8f); fontSize(11f); fontWeightSemiBold(); color(theme.textTertiary) } }
}

private fun ViewContainer<*, *>.DrawerItem(label: String, time: String, theme: StockChatTheme, active: Boolean = false, onClick: () -> Unit = {}) {
    View {
        attr { height(42f); paddingLeft(10f); paddingRight(10f); flexDirectionRow(); alignItemsCenter(); borderRadius(10f); backgroundColor(if (active) theme.brandSoft else theme.page) }
        Text { attr { text(label); fontSize(14f); color(if (active) theme.brand else theme.textPrimary); flex(1f) } }
        if (time.isNotEmpty()) Text { attr { text(time); fontSize(11f); color(theme.textTertiary) } }
        event { click { onClick() } }
    }
}

fun ViewContainer<*, *>.AppTopBar(
    title: String,
    subtitle: String,
    statusBarHeight: Float,
    theme: StockChatTheme,
    renderer: GlassRenderer = GlassRenderer.Default,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    View {
        attr {
            // All app chrome is a floating material; the page scroller is its
            // backdrop source and must remain visible underneath it.
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(statusBarHeight + 57f)
            paddingTop(statusBarHeight)
        }
        GlassBackdrop(theme.glass.sheet, renderer)
        View {
            attr {
                height(56f)
                paddingLeft(16f)
                paddingRight(12f)
                flexDirectionRow()
                alignItemsCenter()
            }
            if (backLabel != null) {
                View {
                    attr { paddingRight(12f); height(44f); justifyContentCenter() }
                    Text { attr { text(backLabel); fontSize(14f); fontWeightMedium(); color(theme.brand) } }
                    event { click { onBack() } }
                }
            }
            View {
                attr { flex(1f) }
                Text { attr { text(title); fontSize(18f); fontWeightBold(); color(theme.textPrimary) } }
                Text { attr { text(subtitle); marginTop(1f); fontSize(10f); color(theme.textTertiary) } }
            }
            actions.forEach { (label, action) ->
                View {
                    attr { marginLeft(6f); padding(8f); borderRadius(9f); backgroundColor(theme.surfaceMuted) }
                    Text { attr { text(label); fontSize(12f); color(theme.textSecondary) } }
                    event { click { action() } }
                }
            }
        }
        View { attr { height(1f); backgroundColor(theme.divider) } }
    }
}

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
        Text { attr { text(text); fontSize(10f); fontWeightMedium(); color(theme.brand) } }
    }
}
