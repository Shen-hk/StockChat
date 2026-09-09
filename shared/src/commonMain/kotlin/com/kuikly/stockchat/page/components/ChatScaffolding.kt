package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View


internal fun ViewContainer<*, *>.DateDivider(theme: StockChatTheme) {
    View {
        attr { marginTop(14f); marginBottom(8f); flexDirectionRow(); alignItemsCenter() }
        View { attr { height(1f); flex(1f); backgroundColor(theme.divider) } }
        Text { attr { text("今天"); marginLeft(10f); marginRight(10f); fontSizeScaled(11f); color(theme.textTertiary) } }
        View { attr { height(1f); flex(1f); backgroundColor(theme.divider) } }
    }
}

internal fun ViewContainer<*, *>.RecentSymbolRow(theme: StockChatTheme, onSelect: (String) -> Unit) {
    Scroller {
        attr { height(28f); flexDirectionRow() }
        listOf("📍 贵州茅台", "五粮液", "上证指数", "+ 添加关注").forEach { label ->
            View {
                // 白色背景胶囊（2026-09-05），细描边保证落在玻璃胶囊上仍可辨。
                attr { height(26f); marginRight(7f); paddingLeft(10f); paddingRight(10f); justifyContentCenter(); backgroundColor(theme.surface); borderRadius(13f); border(Border(0.5f, BorderStyle.SOLID, theme.divider)) }
                Text { attr { text(label); fontSizeScaled(11f); color(if (label.startsWith("+")) theme.brand else theme.textSecondary) } }
                event { click { if (!label.startsWith("+")) onSelect(label.removePrefix("📍 ")) } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.RegressionQuestionRow(
    theme: StockChatTheme,
    onQuestion: (String) -> Unit,
) {
    val cases = listOf(
        "手风琴" to "回归：手风琴 贵州茅台最近怎么样",
        "资讯 Sheet" to "回归：资讯 Sheet 看贵州茅台资讯",
        "归因下钻" to "回归：归因下钻 为什么跌",
        "分支追问" to "回归：分支追问 贵州茅台最近怎么样",
        "焦点放大" to "回归：焦点放大 贵州茅台最近怎么样",
        "对比卡" to "回归：对比卡 贵州茅台和五粮液比较",
    )
    Scroller {
        attr {
            height(42f)
            paddingLeft(14f)
            paddingRight(14f)
            paddingBottom(6f)
            flexDirectionRow()
        }
        cases.forEach { item ->
            View {
                attr {
                    height(32f)
                    marginRight(7f)
                    paddingLeft(10f)
                    paddingRight(10f)
                    justifyContentCenter()
                    borderRadius(10f)
                    backgroundColor(theme.surfaceMuted)
                }
                Text {
                    attr {
                        text(item.first)
                        fontSizeScaled(11f)
                        fontWeightMedium()
                        color(theme.textSecondary)
                    }
                }
                event { click { onQuestion(item.second) } }
            }
        }
    }
}


internal fun ViewContainer<*, *>.ActiveComparePanel(
    model: StockCompareCardModel,
    theme: StockChatTheme,
    insightLoading: () -> Boolean = { false },
    insightText: () -> String = { "" },
    insightError: () -> String = { "" },
    onRetryInsight: () -> Unit = {},
    onOpenStock: (String) -> Unit,
    onClose: () -> Unit,
) {
    View {
        attr {
            marginLeft(12f)
            marginRight(12f)
            marginBottom(8f)
            padding(10f)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
            // 蒙层之上的浮层投影：让弹窗从压暗的背景里"浮"出来。
            boxShadow(BoxShadow(0f, 8f, 28f, Color(0x000000, 0.22f)))
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("对比视图")
                    fontSizeScaled(12f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                    flex(1f)
                }
            }
            Text { attr { text("退出"); fontSizeScaled(11f); color(theme.textSecondary) } }
            event { click { onClose() } }
        }
        CardShell(
            model,
            CardContext(
                theme = theme,
                density = CardDensity.FULL,
                onOpenStock = onOpenStock,
            ),
        )
        View {
            attr {
                marginTop(10f)
                padding(10f)
                borderRadius(10f)
                backgroundColor(theme.surfaceMuted)
            }
            Text {
                attr {
                    text("AI 解读")
                    fontSizeScaled(11f)
                    fontWeightSemiBold()
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    // 流式解读（2026-09-08）：LOADING 态有部分文本就直接展示并尾随
                    // 光标（MarketPage AI 复盘卡同款），文本逐字到达 → 面板高度连续
                    // 小步生长；此前整段文本要等 READY 才上屏，高度瞬间涨几行，
                    // 底部锚定的面板会被顶得"突然往上弹一下"。
                    val content = when {
                        insightError().isNotBlank() -> insightError()
                        insightText().isNotBlank() -> if (insightLoading()) "${insightText()} ▌" else insightText()
                        insightLoading() -> "正在读取两只股票的差异..."
                        else -> "等待第二只股票完成对比"
                    }
                    text(content)
                    marginTop(6f)
                    fontSizeScaled(11f)
                    lineHeightScaled(17f)
                    color(if (insightError().isNotBlank()) theme.fall else theme.textSecondary)
                }
            }
            View {
                attr {
                    val visible = insightError().isNotBlank()
                    height(if (visible) 24f else 0f)
                    marginTop(if (visible) 8f else 0f)
                    paddingLeft(9f)
                    paddingRight(9f)
                    alignSelfFlexStart()
                    allCenter()
                    borderRadius(8f)
                    backgroundColor(theme.brandSoft)
                    opacity(if (visible) 1f else 0f)
                    touchEnable(visible)
                }
                Text { attr { text("重试"); fontSizeScaled(10f); fontWeightMedium(); color(theme.brand) } }
                event { click { if (insightError().isNotBlank()) onRetryInsight() } }
            }
        }
    }
}

/**
 * 术语对比全屏面板（灵动岛术语对比的第二只落槽后弹出，对标 ActiveComparePanel）。
 * 两列并排：术语名 + 分类 + 人话解释 + A股例子；底部 AI 解读块复用股票对比的
 * insight 状态机（股票/术语对比会话互斥，状态可安全共用）。纯词典端侧内容 +
 * AI 事实性解读，不做任何「该选哪个」的价值判断（合规文案铁律）。
 */
internal fun ViewContainer<*, *>.TermComparePanel(
    left: GlossaryEntry,
    right: GlossaryEntry,
    theme: StockChatTheme,
    insightLoading: () -> Boolean = { false },
    insightText: () -> String = { "" },
    insightError: () -> String = { "" },
    onRetryInsight: () -> Unit = {},
    onClose: () -> Unit,
) {
    View {
        attr {
            marginLeft(12f)
            marginRight(12f)
            marginBottom(8f)
            padding(10f)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
            boxShadow(BoxShadow(0f, 8f, 28f, Color(0x000000, 0.22f)))
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("术语对比")
                    fontSizeScaled(12f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                    flex(1f)
                }
            }
            Text { attr { text("退出"); fontSizeScaled(11f); color(theme.textSecondary) } }
            event { click { onClose() } }
        }
        View {
            attr { marginTop(8f); flexDirectionRow() }
            TermCompareColumn(left, theme)
            View { attr { width(8f) } }
            TermCompareColumn(right, theme)
        }
        View {
            attr {
                marginTop(10f)
                padding(10f)
                borderRadius(10f)
                backgroundColor(theme.surfaceMuted)
            }
            Text {
                attr {
                    text("AI 解读")
                    fontSizeScaled(11f)
                    fontWeightSemiBold()
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    // 流式解读（2026-09-08）：同 ActiveComparePanel，LOADING 态
                    // 部分文本直接上屏 + 尾随光标，避免 onDone 整段顶高面板。
                    val content = when {
                        insightError().isNotBlank() -> insightError()
                        insightText().isNotBlank() -> if (insightLoading()) "${insightText()} ▌" else insightText()
                        insightLoading() -> "正在生成两个概念的区别与联系..."
                        else -> "等待第二个术语完成对比"
                    }
                    text(content)
                    marginTop(6f)
                    fontSizeScaled(11f)
                    lineHeightScaled(17f)
                    color(if (insightError().isNotBlank()) theme.fall else theme.textSecondary)
                }
            }
            View {
                attr {
                    val visible = insightError().isNotBlank()
                    height(if (visible) 24f else 0f)
                    marginTop(if (visible) 8f else 0f)
                    paddingLeft(9f)
                    paddingRight(9f)
                    alignSelfFlexStart()
                    allCenter()
                    borderRadius(8f)
                    backgroundColor(theme.brandSoft)
                    opacity(if (visible) 1f else 0f)
                    touchEnable(visible)
                }
                Text { attr { text("重试"); fontSizeScaled(10f); fontWeightMedium(); color(theme.brand) } }
                event { click { if (insightError().isNotBlank()) onRetryInsight() } }
            }
        }
    }
}

private fun ViewContainer<*, *>.TermCompareColumn(
    entry: GlossaryEntry,
    theme: StockChatTheme,
) {
    View {
        attr {
            flex(1f)
            padding(9f)
            borderRadius(10f)
            backgroundColor(theme.surfaceMuted)
        }
        Text { attr { text(entry.term); fontSizeScaled(13f); fontWeightBold(); color(theme.textPrimary) } }
        View {
            attr {
                marginTop(4f)
                paddingLeft(6f)
                paddingRight(6f)
                paddingTop(2f)
                paddingBottom(2f)
                alignSelfFlexStart()
                borderRadius(6f)
                backgroundColor(theme.brandSoft)
            }
            Text { attr { text(entry.category.label); fontSizeScaled(8.5f); fontWeightMedium(); color(theme.term) } }
        }
        Text {
            attr {
                text(entry.plain)
                marginTop(7f)
                fontSizeScaled(11f)
                lineHeightScaled(16f)
                color(theme.textPrimary)
            }
        }
        Text {
            attr {
                text("例 ${entry.example}")
                marginTop(6f)
                fontSizeScaled(9.5f)
                lineHeightScaled(14f)
                color(theme.textSecondary)
            }
        }
    }
}
