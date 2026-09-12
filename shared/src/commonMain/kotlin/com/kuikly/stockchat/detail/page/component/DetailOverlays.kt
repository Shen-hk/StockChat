package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.page.components.FeatureTile
import com.kuikly.stockchat.foundation.ui.icon.LineIconBarChart
import com.kuikly.stockchat.foundation.ui.icon.LineIconCopy
import com.kuikly.stockchat.foundation.ui.icon.LineIconPin
import com.kuikly.stockchat.page.components.QuickReasonChips
import com.kuikly.stockchat.page.components.formatTapeTime
import com.kuikly.stockchat.page.components.truncateByWidth
import com.kuikly.stockchat.page.detail.DetailOverlay
import com.kuikly.stockchat.page.detail.Materiality
import com.kuikly.stockchat.page.detail.materialityOf
import com.kuikly.stockchat.page.detail.scoreNewsSentiment
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * Wave 2 D5 第七组件：U1 就地浮层（REASON_CHIPS / MORE_MENU / TAPE_PREVIEW /
 * DISCLOSURE_PEEK）。从 StockDetailPage.body() 搬出原 lines 692-849，零行为
 * 变更；四个浮层共享 detailOverlayCoordinator 仲裁状态，本组件只做装配 + 渲染，
 * 所有交互经回调回链到 StockDetailPage。
 */
internal fun ViewContainer<*, *>.DetailOverlays(
    theme: StockChatTheme,
    reduceMotion: Boolean,
    statusBarHeight: Float,
    bottomInset: Float,
    pageViewWidth: Float,
    overlayActive: () -> DetailOverlay,
    tapePreview: () -> NewsItem?,
    tapePreviewAnchorX: () -> Float,
    tapePreviewAnchorY: () -> Float,
    disclosurePeek: () -> DisclosureItem?,
    disclosurePeekVisible: () -> Boolean,
    // ---- actions ----
    onCloseOverlay: () -> Unit,
    onPickQuickReason: (String) -> Unit,
    onRequestReasonChips: () -> Unit,
    onCopySymbolToPasteboard: () -> Unit,
    onToggleAiInsight: () -> Unit,
    onTapPreviewItem: (NewsItem) -> Unit,
    onDismissDisclosurePeek: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
) {
    // REASON_CHIPS 透明遮罩（U1 点空白全关）
    vif({ overlayActive() == DetailOverlay.REASON_CHIPS }) {
        View {
            attr { absolutePositionAllZero(); touchEnable(true) }
            event { click { onCloseOverlay() } }
        }
    }
    // H1 快捷理由 chips：底栏上方浮出
    vif({ overlayActive() == DetailOverlay.REASON_CHIPS }) {
        View {
            attr {
                absolutePosition(
                    left = 14f,
                    right = 14f,
                    bottom = 76f + bottomInset,
                )
            }
            QuickReasonChips(
                theme = theme,
                reasons = listOf("等回调到位", "财报前布局", "跟热点板块"),
                visible = { true },
                onPick = onPickQuickReason,
                reduceMotion = reduceMotion,
            )
        }
    }
    // MORE_MENU 透明遮罩
    vif({ overlayActive() == DetailOverlay.MORE_MENU }) {
        View {
            attr { absolutePositionAllZero(); touchEnable(true) }
            event { click { onCloseOverlay() } }
        }
    }
    // ⋯ 更多操作菜单
    vif({ overlayActive() == DetailOverlay.MORE_MENU }) {
        View {
            attr {
                absolutePosition(
                    top = statusBarHeight + 62f,
                    left = (pageViewWidth - 250f - 12f).coerceAtLeast(12f),
                )
                width(250f)
                paddingTop(12f)
                paddingBottom(12f)
                paddingLeft(12f)
                paddingRight(12f)
                borderRadius(16f)
                backgroundColor(theme.surface)
                border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                boxShadow(BoxShadow(0f, 10f, 26f, theme.textPrimary.opacity(0.20f)))
                touchEnable(true)
            }
            View {
                attr { flexDirectionRow() }
                FeatureTile(
                    label = "记当初理由",
                    theme = theme,
                    height = 66f,
                    icon = { LineIconPin(theme.textPrimary, 22f) },
                ) { onRequestReasonChips() }
                View { attr { width(8f) } }
                FeatureTile(
                    label = "AI 解读",
                    theme = theme,
                    height = 66f,
                    icon = { LineIconBarChart(theme.textPrimary, 22f) },
                ) { onToggleAiInsight() }
                View { attr { width(8f) } }
                FeatureTile(
                    label = "复制代码",
                    theme = theme,
                    height = 66f,
                    icon = { LineIconCopy(theme.textPrimary, 22f) },
                ) { onCopySymbolToPasteboard() }
            }
        }
    }
    // B1 长按先览小气泡
    vif({ overlayActive() == DetailOverlay.TAPE_PREVIEW && tapePreview() != null }) {
        vbind({ tapePreview()?.id ?: "" }) {
            val preview = tapePreview()
            if (preview != null) {
                val sentiment = scoreNewsSentiment(preview.title).isPositive
                val left = (tapePreviewAnchorX() - 20f)
                    .coerceIn(12f, (pageViewWidth - 248f).coerceAtLeast(12f))
                View {
                    attr {
                        absolutePosition(left = left, top = tapePreviewAnchorY() + 20f)
                        width(236f)
                        padding(10f)
                        borderRadius(12f)
                        backgroundColor(theme.surface)
                        border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(0.5f)))
                        boxShadow(BoxShadow(0f, 8f, 22f, theme.textPrimary.opacity(0.16f)))
                        touchEnable(true)
                    }
                    Text {
                        attr {
                            text("${formatTapeTime(preview.time)} · ${if (sentiment == true) "利好" else if (sentiment == false) "利空" else "中性"}")
                            fontSizeScaled(10f)
                            fontWeightSemiBold()
                            color(
                                when (sentiment) {
                                    true -> theme.rise
                                    false -> theme.fall
                                    null -> theme.textTertiary
                                },
                            )
                        }
                    }
                    Text {
                        attr {
                            text(truncateByWidth(preview.title, 42f))
                            marginTop(4f)
                            fontSizeScaled(10f)
                            lineHeightScaled(14f)
                            color(theme.textSecondary)
                        }
                    }
                    Text {
                        attr {
                            text("点按展开与落旗 · 端侧规则")
                            marginTop(5f)
                            fontSizeScaled(9.5f)
                            color(theme.brand)
                        }
                    }
                    event { click { onTapPreviewItem(preview) } }
                }
            }
        }
    }
    // F1 公告/研报长按预览（蒙层 + 底部浮卡）
    vif({ disclosurePeek() != null }) {
        View {
            attr {
                absolutePositionAllZero()
                zIndex(20, useOutline = false)
                backgroundColor(theme.textPrimary.opacity(0.26f))
                val shown = disclosurePeekVisible()
                opacity(if (shown) 1f else 0f)
                touchEnable(shown)
                if (!reduceMotion) animate(Animation.easeOut(0.20f), disclosurePeekVisible())
            }
            event { click { onDismissDisclosurePeek() } }
        }
        View {
            attr {
                absolutePosition(
                    left = 14f,
                    right = 14f,
                    bottom = 76f + bottomInset,
                )
                zIndex(21, useOutline = false)
                padding(16f)
                borderRadius(18f)
                backgroundColor(theme.surface)
                border(Border(1f, BorderStyle.SOLID, theme.divider))
                boxShadow(BoxShadow(0f, 14f, 30f, theme.textPrimary.opacity(0.18f)))
                val shown = disclosurePeekVisible()
                opacity(if (shown) 1f else 0f)
                touchEnable(shown)
                if (!reduceMotion) {
                    transform(scale = Scale(if (shown) 1f else 0.96f, if (shown) 1f else 0.96f))
                    animate(Animation.easeOut(0.20f), disclosurePeekVisible())
                }
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(disclosurePeek()?.title ?: "")
                        flex(1f)
                        fontSizeScaled(13f)
                        fontWeightBold()
                        color(theme.textPrimary)
                        lineHeightScaled(18f)
                    }
                }
                Text {
                    attr { text("关闭"); fontSizeScaled(11f); color(theme.brand) }
                    event { click { onDismissDisclosurePeek() } }
                }
            }
            Text {
                attr {
                    text(
                        // 研报先给可读摘要；券商与日期降到来源行
                        (disclosurePeek()?.takeIf { it.kind != com.kuikly.stockchat.data.provider.DisclosureKind.RESEARCH }
                            ?.let { "${it.kind.label} · ${it.publisher} · ${it.date}" } ?: ""),
                    )
                    marginTop(if (disclosurePeek()?.kind == com.kuikly.stockchat.data.provider.DisclosureKind.RESEARCH) 0f else 8f)
                    fontSizeScaled(10f)
                    color(theme.textTertiary)
                }
            }
            // 重要度规则只适用于公告；研报展示机构标题提炼
            vif({ disclosurePeek()?.kind != com.kuikly.stockchat.data.provider.DisclosureKind.RESEARCH }) {
                Text {
                    attr {
                        text(
                            disclosurePeek()?.let { item ->
                                val verdict = when (materialityOf(item.title).level) {
                                    Materiality.HIGH -> "高重要度"
                                    Materiality.MID -> "中重要度"
                                    Materiality.LOW -> "低重要度"
                                }
                                "端侧评级：$verdict · ${materialityOf(item.title).rule}"
                            } ?: "",
                        )
                        marginTop(8f)
                        fontSizeScaled(11f)
                        lineHeightScaled(16f)
                        color(theme.textSecondary)
                    }
                }
            }
            Text {
                attr {
                    text(disclosurePeek()?.summary?.takeIf { it.isNotBlank() } ?: "")
                    marginTop(if (disclosurePeek()?.kind == com.kuikly.stockchat.data.provider.DisclosureKind.RESEARCH) 8f else 6f)
                    fontSizeScaled(if (disclosurePeek()?.kind == com.kuikly.stockchat.data.provider.DisclosureKind.RESEARCH) 12f else 11f)
                    lineHeightScaled(if (disclosurePeek()?.kind == com.kuikly.stockchat.data.provider.DisclosureKind.RESEARCH) 18f else 16f)
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    text(
                        disclosurePeek()?.let { item ->
                            if (item.kind == com.kuikly.stockchat.data.provider.DisclosureKind.RESEARCH) {
                                "来源：${item.publisher} · ${item.date} · 机构观点仅供参考"
                            } else {
                                item.stamp.source.takeIf { it.isNotBlank() }
                                    ?.let { "来源：$it · 只述事实，不构成建议" }
                                    ?: "只述事实，不构成建议"
                            }
                        } ?: "只述事实，不构成建议",
                    )
                    marginTop(8f)
                    fontSizeScaled(9f)
                    color(theme.textTertiary)
                }
            }
            vif({ disclosurePeek()?.url?.isNotBlank() == true }) {
                View {
                    attr {
                        alignSelfFlexStart()
                        marginTop(12f)
                        paddingTop(7f); paddingBottom(7f)
                        paddingLeft(10f); paddingRight(10f)
                        borderRadius(9f)
                        backgroundColor(theme.brandSoft)
                    }
                    Text {
                        attr {
                            text("查看公告原文  ↗")
                            fontSizeScaled(11f)
                            fontWeightSemiBold()
                            color(theme.brand)
                        }
                    }
                    event {
                        click {
                            val url = disclosurePeek()?.url.orEmpty()
                            if (url.isNotBlank()) onOpenUrl(url)
                        }
                    }
                }
            }
        }
    }
}