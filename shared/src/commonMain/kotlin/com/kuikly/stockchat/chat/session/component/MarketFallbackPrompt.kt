package com.kuikly.stockchat.chat.session.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.MarketFallbackPrompt(
    theme: StockChatTheme,
    pageWidth: Float,
    visible: () -> Boolean,
    onDismiss: () -> Unit,
    onSwitchToMock: () -> Unit,
) {
    vif(visible) {
        View {
            attr {
                absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                zIndex(80, useOutline = false)
                backgroundColor(Color(0x88000000))
                allCenter()
                padding(24f)
            }
            event { click { onDismiss() } }
            View {
                attr {
                    width((pageWidth - 48f).coerceAtMost(360f))
                    padding(20f)
                    borderRadius(20f)
                    backgroundColor(theme.surface)
                }
                event { click { } }
                Text {
                    attr {
                        text("行情接口开小差了")
                        fontSizeScaled(18f)
                        fontWeightBold()
                        color(theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("暂时没有收到真实行情数据。要切换到本地 Mock 数据继续查看吗？")
                        marginTop(8f)
                        fontSizeScaled(13f)
                        lineHeightScaled(20f)
                        color(theme.textSecondary)
                    }
                }
                View {
                    attr { flexDirectionRow(); marginTop(20f); justifyContentFlexEnd() }
                    View {
                        attr { padding(10f) }
                        Text { attr { text("暂不切换"); fontSizeScaled(14f); fontWeightMedium(); color(theme.textSecondary) } }
                        event { click { onDismiss() } }
                    }
                    View {
                        attr { padding(10f); marginLeft(8f) }
                        Text { attr { text("切换到 Mock 数据"); fontSizeScaled(14f); fontWeightBold(); color(theme.brand) } }
                        event { click { onSwitchToMock() } }
                    }
                }
            }
        }
    }
}
