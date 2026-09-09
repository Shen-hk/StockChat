package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.data.fontSizeScaled

import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.kuikly.stockchat.glass.GlassRenderingMode
import com.kuikly.stockchat.chat.sheet.state.ChatSheetLevel
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Reusable glass sheet host for full-size card presentation. */
internal fun ViewContainer<*, *>.CardSheetHost(
    model: CardModel,
    level: ChatSheetLevel,
    theme: StockChatTheme,
    renderer: GlassRenderer = GlassRenderer.Default,
    presented: Boolean = true,
    interactive: Boolean = presented,
    viewportHeight: Float,
    bottomInset: Float,
    onDismiss: () -> Unit,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    onPan: (String, Float) -> Unit,
    onOpenStock: (String) -> Unit,
    onTerm: (String) -> Unit,
    primaryActionLabel: String? = null,
    onPrimaryAction: (String) -> Unit = {},
) {
    val availableHeight = (viewportHeight - bottomInset).coerceAtLeast(520f)
    val sheetHeight = (availableHeight * level.ratio).coerceAtLeast(180f)
    val footerHeight = if (level == ChatSheetLevel.FULL) 0f else 52f
    val overlayHeight = (viewportHeight - sheetHeight - bottomInset).coerceAtLeast(0f)
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(overlayHeight)
            backgroundColor(Color(0x4D000000))
            opacity(if (presented) 1f else 0f)
            touchEnable(interactive)
            animate(Animation.easeOut(0.20f), presented)
        }
        event { click { if (interactive) onDismiss() } }
    }
    View {
        attr {
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            height(sheetHeight + bottomInset)
            paddingLeft(16f)
            paddingRight(16f)
            paddingBottom(12f + bottomInset)
            opacity(if (presented) 1f else 0f)
            transform(scale = if (presented) Scale.DEFAULT else Scale(0.98f, 0.98f))
            touchEnable(interactive)
            animate(Animation.easeOut(if (renderer.mode == GlassRenderingMode.SIMPLIFIED) 0.20f else 0.40f), presented)
            animate(Animation.easeOut(0.26f), level)
        }
        GlassBackdrop(
            if (presented) theme.glass.sheet else theme.glass.dissolve,
            renderer,
            blurVisible = presented,
        )
        View {
            attr {
                width(56f)
                height(36f)
                alignSelfCenter()
                allCenter()
                capture(CaptureRule.pan(CaptureRuleDirection.VERTICAL))
            }
            View {
                attr {
                    width(36f)
                    height(4f)
                    backgroundColor(theme.divider)
                    borderRadius(2f)
                }
            }
            event { pan { onPan(it.state, it.y) } }
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text { attr { text("完整内容"); fontSizeScaled(15f); fontWeightSemiBold(); color(theme.textPrimary); flex(1f) } }
            if (primaryActionLabel != null) {
                Text {
                    attr {
                        text(primaryActionLabel)
                        marginRight(14f)
                        fontSizeScaled(12f)
                        fontWeightSemiBold()
                        color(if (primaryActionLabel == "已自选") theme.textTertiary else theme.brand)
                    }
                    event {
                        click {
                            if (interactive) cardPrimarySymbol(model)?.let(onPrimaryAction)
                        }
                    }
                }
            }
            Text { attr { text("收起"); fontSizeScaled(12f); color(theme.textSecondary) } }
            event { click { onLower() } }
        }
        Scroller {
            attr { height((sheetHeight - 82f - footerHeight).coerceAtLeast(64f)); marginTop(6f) }
            CardShell(
                model,
                CardContext(
                    theme = theme,
                    density = level.density,
                    onOpenStock = onOpenStock,
                    onExplainTerm = onTerm,
                    glass = renderer,
                ),
            )
        }
        if (level != ChatSheetLevel.FULL) {
            View {
                attr {
                    alignSelfStretch()
                    height(38f)
                    marginTop(8f)
                    allCenter()
                    backgroundColor(theme.brandSoft)
                    borderRadius(10f)
                }
                Text { attr { text("展开更多"); fontSizeScaled(12f); fontWeightMedium(); color(theme.brand) } }
                event { click { onRaise() } }
            }
        }
    }
}

private fun cardPrimarySymbol(model: CardModel): String? = when (model) {
    is StockQuoteCardModel -> model.quote.symbol
    is StockChartCardModel -> model.quote.symbol
    is AttributionCardModel -> model.quote.symbol
    is InsightCardModel -> model.quote.symbol
    is NewsCardModel -> model.quote.symbol
    is StockCompareCardModel -> model.quotes.firstOrNull()?.symbol
    else -> null
}
