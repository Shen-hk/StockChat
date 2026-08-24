package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.AppTopBar(
    title: String,
    subtitle: String,
    statusBarHeight: Float,
    theme: StockChatTheme,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    View {
        attr {
            paddingTop(statusBarHeight)
            backgroundColor(theme.surface)
        }
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

fun ViewContainer<*, *>.DataModeBadge(theme: StockChatTheme, text: String = "离线演示模式") {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            paddingTop(5f)
            paddingBottom(5f)
            paddingLeft(8f)
            paddingRight(8f)
            backgroundColor(theme.brandSoft)
            borderRadius(9f)
        }
        View { attr { size(6f, 6f); borderRadius(3f); backgroundColor(theme.brand); marginRight(5f) } }
        Text { attr { text(text); fontSize(10f); fontWeightMedium(); color(theme.brand) } }
    }
}
