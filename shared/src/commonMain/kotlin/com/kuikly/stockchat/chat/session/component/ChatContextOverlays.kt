package com.kuikly.stockchat.chat.session.component

import com.kuikly.stockchat.shared.cards.component.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.foundation.ui.surface.GlassBackdrop
import com.kuikly.stockchat.foundation.design.GlassRenderer
import com.kuikly.stockchat.foundation.ui.icon.LineIconChevronUp
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.AccessibilityRole
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal object ChatContextOverlays {
    fun renderAmbiguity(
        container: ViewContainer<*, *>,
        theme: StockChatTheme,
        symbols: () -> List<String>,
        entityText: () -> String,
        displayName: (String) -> String,
        onSelect: (String) -> Unit,
    ) {
        container.vif({ symbols().isNotEmpty() }) {
            View {
                attr {
                    marginLeft(12f); marginRight(12f); marginBottom(8f); padding(10f)
                    backgroundColor(theme.brandSoft); borderRadius(12f)
                }
                Text {
                    attr {
                        text("“${entityText()}”可能指以下标的")
                        fontSizeScaled(12f); fontWeightMedium(); color(theme.textPrimary)
                    }
                }
                Scroller {
                    attr { flexDirectionRow(); height(36f); marginTop(7f) }
                    symbols().forEach { symbol ->
                        View {
                            attr {
                                height(32f); marginRight(7f); paddingLeft(10f); paddingRight(10f)
                                justifyContentCenter(); borderRadius(9f); backgroundColor(theme.surface)
                            }
                            Text { attr { text(displayName(symbol)); fontSizeScaled(12f); color(theme.brand) } }
                            event { click { onSelect(symbol) } }
                        }
                    }
                }
            }
        }
    }

    fun renderPeek(
        container: ViewContainer<*, *>,
        theme: StockChatTheme,
        renderer: GlassRenderer,
        symbol: () -> String,
        visible: () -> Boolean,
        quoteFor: (String) -> Quote?,
        onDismiss: () -> Unit,
        onOpenStock: (String) -> Unit,
    ) {
        container.vif({ symbol().isNotEmpty() }) {
            val currentSymbol = symbol()
            val quote = quoteFor(currentSymbol)
            View {
                attr {
                    marginLeft(12f); marginRight(12f); marginBottom(8f); padding(12f)
                    flexDirectionRow(); alignItemsCenter()
                    opacity(if (visible()) 1f else 0f)
                    transform(Translate(0f, if (visible()) 0f else 0.16f))
                    animate(Animation.easeOut(0.24f), visible())
                }
                GlassBackdrop(theme.glass.peek, renderer)
                View {
                    attr { flex(1f) }
                    if (quote == null) {
                        Text { attr { text("正在获取 $currentSymbol 的行情…"); fontSizeScaled(12f); color(theme.textTertiary) } }
                    } else {
                        CardShell(
                            StockQuoteCardModel(quote),
                            CardContext(theme, CardDensity.MINI, onOpenStock, glass = renderer),
                        )
                    }
                }
                View {
                    attr { padding(9f); borderRadius(9f); backgroundColor(theme.surfaceMuted) }
                    Text { attr { text("收起"); fontSizeScaled(11f); color(theme.textSecondary) } }
                    event { click { onDismiss() } }
                }
                View {
                    attr { marginLeft(7f); padding(9f); borderRadius(9f); backgroundColor(theme.brand) }
                    Text { attr { text("看详情"); fontSizeScaled(11f); fontWeightMedium(); color(theme.onBrand) } }
                    event { click { onOpenStock(currentSymbol) } }
                }
            }
        }
    }

    fun renderBackToTop(
        container: ViewContainer<*, *>,
        theme: StockChatTheme,
        renderer: GlassRenderer,
        bottomInset: Float,
        shouldMount: () -> Boolean,
        presented: () -> Boolean,
        shadowVisible: () -> Boolean,
        onClick: () -> Unit,
    ) {
        container.vif(shouldMount) {
            View {
                attr {
                    absolutePosition(right = 14f, bottom = 130f + bottomInset)
                    size(40f, 40f); allCenter(); borderRadius(20f)
                    border(Border(1f, BorderStyle.SOLID, Color(0xFFFFFF, 0.35f)))
                    boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, if (shadowVisible()) 0.16f else 0f)))
                    accessibility("回到顶部")
                    accessibilityRole(AccessibilityRole.BUTTON)
                    accessibilityInfo(clickable = true, longClickable = false)
                    val isPresented = presented()
                    opacity(if (isPresented) 1f else 0f)
                    transform(scale = if (isPresented) Scale(1f, 1f) else Scale(0.5f, 0.5f))
                    animate(Animation.easeOut(0.22f), presented())
                }
                GlassBackdrop(theme.glass.peek, renderer)
                LineIconChevronUp(color = theme.textSecondary, size = 18f)
                event { click { onClick() } }
            }
        }
    }
}
