package com.kuikly.stockchat.watchlist.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.kuikly.stockchat.watchlist.brief.state.WatchlistBriefCoordinator

/**
 * doc 30 小空间整合的简报组件（doc 47 B-4 组件层）：
 * - InboxPreviewRow：聚合头下的预警收件箱预览（未读 badge 即应用内消息提示入口）；
 * - BriefCard：今日速览卡（规则引擎事实句，条件触发，平静日不出现）。
 * 两块卡均为 R4 两拍入场（R5 恒注册、R2 驱动 key 最后读），reduceMotion 直出。
 */
internal fun ViewContainer<*, *>.WatchlistInboxPreviewRow(
    theme: StockChatTheme,
    brief: WatchlistBriefCoordinator,
    reduceMotion: Boolean,
    onOpenAlerts: () -> Unit,
) {
    val rowTheme = theme
    View {
        attr {
            marginTop(12f)
            padding(12f)
            borderRadius(14f)
            backgroundColor(rowTheme.surface)
            boxShadow(BoxShadow(0f, 1f, 3f, Color(0x182238, 0.06f)))
            flexDirectionRow()
            alignItemsCenter()
            // 入场两拍（R4/R5）：目标值由 inboxPreviewPresented 决定，animate 恒注册
            // 且是本 attr 最后一次 observable 读取（R2）。
            opacity(if (brief.inboxPreviewPresented) 1f else 0f)
            transform(translate = Translate(0f, 0f, offsetY = if (brief.inboxPreviewPresented) 0f else 12f))
            if (!reduceMotion) {
                animate(Animation.easeOut(0.28f), brief.inboxPreviewPresented)
            }
        }
        event { click { onOpenAlerts() } }
        View {
            attr {
                width(36f)
                height(36f)
                borderRadius(11f)
                backgroundColor(rowTheme.brandSoft)
                allCenter()
            }
            Text { attr { text("⚡"); fontSizeScaled(15f) } }
        }
        View {
            attr { flex(1f); marginLeft(11f); marginRight(8f) }
            Text {
                attr {
                    text(inboxTitle(brief))
                    fontSizeScaled(12.5f)
                    fontWeightSemiBold()
                    color(rowTheme.textPrimary)
                }
            }
            Text {
                attr {
                    // 在 attr 内读 observableList（R1）：消息重建时预览行即时刷新，
                    // 不能在构建闭包先取快照（那是首帧定格，R1 高危）。
                    text(brief.inboxMessages.firstOrNull()?.summary.orEmpty())
                    marginTop(3f)
                    fontSizeScaled(10.5f)
                    color(rowTheme.textSecondary)
                }
            }
        }
        // 未读角标（badge）：0 不画，与收件箱已读态同源（AlertInboxStore）。
        vif({ brief.inboxUnread > 0 }) {
            View {
                attr {
                    paddingLeft(7f)
                    paddingRight(7f)
                    height(18f)
                    allCenter()
                    borderRadius(9f)
                    backgroundColor(rowTheme.rise)
                    marginRight(6f)
                }
                Text {
                    attr {
                        text("${brief.inboxUnread}")
                        fontSizeScaled(10f)
                        fontWeightSemiBold()
                        color(Color(0xFFFFFFFF, 1f))
                    }
                }
            }
        }
        Text {
            attr {
                text("查看 ›")
                fontSizeScaled(11f)
                fontWeightSemiBold()
                color(rowTheme.brand)
            }
        }
    }
}

private fun inboxTitle(brief: WatchlistBriefCoordinator): String =
    if (brief.inboxUnread > 0) "预警收件箱 · ${brief.inboxUnread} 条未读" else "预警收件箱 · 暂无新消息"

/** 今日速览卡：徽标「速览」（规则引擎产出，不冒称 AI），点击展开事实行。 */
internal fun ViewContainer<*, *>.WatchlistBriefCard(
    theme: StockChatTheme,
    brief: WatchlistBriefCoordinator,
    reduceMotion: Boolean,
) {
    val cardTheme = theme
    View {
        attr {
            marginTop(14f)
            padding(14f)
            borderRadius(16f)
            backgroundColor(cardTheme.surface)
            boxShadow(BoxShadow(0f, 4f, 14f, Color(0x182238, 0.07f)))
            opacity(if (brief.briefPresented) 1f else 0f)
            transform(translate = Translate(0f, 0f, offsetY = if (brief.briefPresented) 0f else 12f))
            if (!reduceMotion) {
                animate(Animation.easeOut(0.28f), brief.briefPresented)
            }
        }
        event { click { brief.toggleBrief() } }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f)
                    borderRadius(6f)
                    backgroundColor(cardTheme.brand)
                }
                Text {
                    attr {
                        text("速览")
                        fontSizeScaled(9f)
                        fontWeightSemiBold()
                        color(Color(0xFFFFFFFF, 1f))
                    }
                }
            }
            Text {
                attr {
                    flex(1f)
                    text("今日速览 · 开盘前看完")
                    marginLeft(7f)
                    fontSizeScaled(12.5f)
                    fontWeightSemiBold()
                    color(cardTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(if (brief.briefOpen) "收起" else "展开")
                    fontSizeScaled(10f)
                    color(cardTheme.textTertiary)
                }
            }
        }
        // 展开区 vif 直出（与收件箱展开区同口径：不做高度动画，只做事实呈现）。
        vif({ brief.briefOpen }) {
            View {
                attr { marginTop(10f) }
                vfor({ brief.briefLines }) { line ->
                    Text {
                        attr {
                            text(line)
                            marginTop(6f)
                            fontSizeScaled(11.5f)
                            lineHeightScaled(17f)
                            color(cardTheme.textSecondary)
                        }
                    }
                }
                Text {
                    attr {
                        text("由规则引擎从行情与事件整理 · 非预测非建议")
                        marginTop(9f)
                        fontSizeScaled(9.5f)
                        color(cardTheme.textTertiary)
                    }
                }
            }
        }
    }
}
