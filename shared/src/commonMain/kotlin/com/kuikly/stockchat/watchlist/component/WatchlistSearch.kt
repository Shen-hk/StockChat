package com.kuikly.stockchat.watchlist.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.watchlist.state.WatchlistCoordinator
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

/**
 * z5 搜索浮层（doc 47 B-4 组件层）：原顶部长驻搜索框下沉于此（S-1：首屏 200px
 * 让给结论）。布局/文案/参数逐字来自原 WatchlistPage。
 * 非受控铁律：TextArea 的 text 只作挂载种子，不绑定响应式文本。
 */
internal fun ViewContainer<*, *>.WatchlistSearchOverlay(
    theme: StockChatTheme,
    data: WatchlistCoordinator,
    statusBarHeight: Float,
    searchSeed: String,
) {
    val pageTheme = theme
    View {
        attr {
            absolutePosition()
            top(0f)
            left(0f)
            right(0f)
            bottom(0f)
            zIndex(50, useOutline = false)
            backgroundColor(Color(0x000000, 0.42f))
            paddingLeft(14f)
            paddingRight(14f)
            paddingTop(statusBarHeight + 66f)
        }
        event {
            click {
                data.closeSearch()
            }
        }
        View {
            attr {
                borderRadius(16f)
                backgroundColor(pageTheme.surface)
                padding(12f)
            }
            event { click { /* 吃掉点击，防止冒泡关掉浮层 */ } }
            View {
                attr {
                    height(38f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(11f)
                    paddingRight(11f)
                    borderRadius(10f)
                    backgroundColor(pageTheme.surfaceMuted)
                }
                Text { attr { text("＋"); fontSizeScaled(15f); color(pageTheme.textTertiary) } }
                TextArea {
                    attr {
                        flex(1f)
                        marginLeft(6f)
                        height(36f)
                        fontSizeScaled(13f)
                        color(pageTheme.textPrimary)
                        backgroundColor(Color(0xFFFFFFFF, 0f))
                        text(searchSeed)
                        placeholder("搜索股票加入自选：贵州茅台 / 600519")
                        placeholderColor(pageTheme.textTertiary)
                        tintColor(pageTheme.brand)
                        selectionColor(pageTheme.brand)
                    }
                    event {
                        textDidChange(isSyncEdit = true) { state -> data.search(state.text) }
                    }
                }
            }
            vif({ data.candidates.isNotEmpty() }) {
                View {
                    attr { marginTop(8f) }
                    vfor({ data.candidates }) { security ->
                        WatchlistCandidateRow(
                            security = security,
                            theme = pageTheme,
                            onAdd = { data.add(security) },
                            container = this,
                        )
                    }
                }
            }
        }
    }
}

internal fun WatchlistCandidateRow(
    security: com.kuikly.stockchat.data.entity.Security,
    theme: StockChatTheme,
    onAdd: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(6f)
            padding(12f)
            borderRadius(12f)
            backgroundColor(theme.surfaceMuted)
        }
        View {
            attr { flex(1f) }
            Text { attr { text(security.name); fontSizeScaled(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
            Text { attr { text(security.symbol); marginTop(2f); fontSizeScaled(10f); color(theme.textTertiary) } }
        }
        View {
            attr {
                paddingLeft(12f)
                paddingRight(12f)
                height(28f)
                allCenter()
                borderRadius(8f)
                backgroundColor(theme.brandSoft)
            }
            Text { attr { text("加自选"); fontSizeScaled(12f); fontWeightSemiBold(); color(theme.brand) } }
            event { click { onAdd() } }
        }
    }
}
