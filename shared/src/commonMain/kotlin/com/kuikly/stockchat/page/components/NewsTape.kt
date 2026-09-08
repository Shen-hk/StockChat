package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.NewsItem
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 新闻弹幕带（对齐 doc 29 全页原型 .tape）：卡容器 + 头部（相关资讯 / 长按先览 · 点按落旗）
 * + 横向滚动胶囊条目流（情绪圆点 + 时间 + 标题，单行截断）+ 卡内 B2 摘要条（brandSoft）。
 * 取代 doc 26 §5.5 的单条飞过玻璃条（评审结论：不好看，且自动播放抓不住）。
 *
 * 交互：点按条目 = 落旗 + 展开摘要条，重复点按同一条目收旗收条（页侧 toggle）；
 * 长按 400ms 先览（B1，松手 700ms 后消失由页侧调度）；摘要条可「问问 AI」带入对话。
 * 选中态在 attr 闭包内读 selected()（R1）：切换条目时全部胶囊随 attr 重绘。
 */
internal fun ViewContainer<*, *>.NewsTape(
    theme: StockChatTheme,
    items: () -> List<NewsItem>,
    // 当前展开摘要的条目（null = 全收起）；页侧 newsSummary 驱动
    selected: () -> NewsItem?,
    // 情绪判定：true=利好(rise) / false=利空(fall) / null=中性不染色；页侧 scoreNewsSentiment
    sentimentOf: (NewsItem) -> Boolean?,
    onTapItem: (NewsItem) -> Unit,
    onLongPressItem: ((NewsItem) -> Unit)? = null,
    onLongPressRelease: (() -> Unit)? = null,
    onAskAi: ((NewsItem) -> Unit)? = null,
    onOpenUrl: ((NewsItem) -> Unit)? = null,
) {
    vif({ items().isNotEmpty() }) {
        View {
            attr {
                marginTop(12f)
                borderRadius(14f)
                backgroundColor(theme.surface)
                border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                boxShadow(BoxShadow(0f, 6f, 18f, theme.textPrimary.opacity(0.14f)))
                overflow(true)
                touchEnable(true)
            }
            // 头部：标题 + 交互提示（原型 .tape-head）
            View {
                attr {
                    paddingTop(8f); paddingLeft(12f); paddingRight(12f); paddingBottom(4f)
                    flexDirectionRow(); alignItemsCenter()
                }
                Text {
                    attr {
                        text("相关资讯")
                        flex(1f)
                        fontSize(theme.type.label)
                        fontWeightSemiBold()
                        color(theme.textSecondary)
                    }
                }
                Text {
                    attr {
                        text("长按先览 · 点按落旗")
                        fontSize(theme.type.meta)
                        color(theme.textTertiary)
                    }
                }
            }
            // 胶囊条目行：横向滚动（Kuikly Scroller 方向随 flexDirection）
            Scroller {
                attr {
                    flexDirectionRow()
                    paddingLeft(12f); paddingRight(12f); paddingBottom(10f)
                    showScrollerIndicator(false)
                }
                vbind({ items().size }) {
                    items().forEach { item ->
                        TapePill(theme, item, selected, sentimentOf, onTapItem, onLongPressItem, onLongPressRelease)
                    }
                }
            }
            // B2 摘要条（卡内展开，原型 .news-summary；非全屏 Sheet）
            vif({ selected() != null }) {
                vbind({ selected()?.id ?: "" }) {
                    val news = selected()
                    if (news != null) {
                        val sentiment = sentimentOf(news)
                        View {
                            attr {
                                backgroundColor(theme.brandSoft)
                                paddingLeft(12f); paddingRight(12f); paddingTop(9f); paddingBottom(10f)
                            }
                            Text {
                                attr {
                                    text("${formatTapeTime(news.time)} · ${sentimentLabel(sentiment)}")
                                    fontSize(theme.type.meta)
                                    fontWeightSemiBold()
                                    color(sentimentColor(theme, sentiment))
                                }
                            }
                            Text {
                                attr {
                                    text(news.title)
                                    marginTop(3f)
                                    fontSize(theme.type.label)
                                    fontWeightSemiBold()
                                    color(theme.textPrimary)
                                    lineHeight(16f)
                                }
                            }
                            vif({ news.summary.isNotEmpty() }) {
                                Text {
                                    attr {
                                        text(news.summary)
                                        marginTop(4f)
                                        fontSize(theme.type.meta)
                                        lineHeight(15f)
                                        color(theme.textSecondary)
                                    }
                                }
                            }
                            // 事实行 + 问 AI 出口（U1 预算内：摘要条本身不占 brand 常驻位）
                            View {
                                attr { marginTop(7f); flexDirectionRow(); alignItemsCenter() }
                                Text {
                                    attr {
                                        text("旗标已落在走势图对应位置 · 端侧规则")
                                        fontSize(theme.type.meta)
                                        color(theme.textTertiary)
                                        flex(1f)
                                    }
                                }
                                View {
                                    attr { touchEnable(true) }
                                    Text {
                                        attr {
                                            text("就这条新闻问问 AI ›")
                                            fontSize(theme.type.meta)
                                            fontWeightSemiBold()
                                            color(theme.brand)
                                        }
                                    }
                                    event { click { onAskAi?.invoke(news) } }
                                }
                            }
                            // 阅读原文 + 收起（收起 = 重复点按同义，走页侧 toggle）
                            View {
                                attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                                vif({ news.url.isNotEmpty() }) {
                                    View {
                                        attr {
                                            height(26f)
                                            paddingLeft(10f); paddingRight(10f)
                                            allCenter()
                                            borderRadius(13f)
                                            backgroundColor(theme.surface)
                                            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                                        }
                                        Text {
                                            attr {
                                                text("阅读原文 ↗")
                                                fontSize(theme.type.meta)
                                                fontWeightMedium()
                                                color(theme.textSecondary)
                                            }
                                        }
                                        event { click { onOpenUrl?.invoke(news) } }
                                    }
                                }
                                Text {
                                    attr {
                                        text("收起 ×")
                                        marginLeft(12f)
                                        fontSize(theme.type.meta)
                                        color(theme.textTertiary)
                                    }
                                    event { click { onTapItem(news) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 胶囊条目（原型 .tape-item）：情绪点 + 时间 + 标题，单行截断；选中态 brandSoft。 */
private fun ViewContainer<*, *>.TapePill(
    theme: StockChatTheme,
    item: NewsItem,
    selected: () -> NewsItem?,
    sentimentOf: (NewsItem) -> Boolean?,
    onTapItem: (NewsItem) -> Unit,
    onLongPressItem: ((NewsItem) -> Unit)?,
    onLongPressRelease: (() -> Unit)?,
) {
    View {
        attr {
            marginRight(8f)
            height(28f)
            borderRadius(9f)
            paddingLeft(10f); paddingRight(10f)
            flexDirectionRow(); alignItemsCenter()
            // 选中态在 attr 内读 selected()（R1）：换选条目时所有胶囊重绘
            val sel = selected()?.id == item.id
            backgroundColor(if (sel) theme.brandSoft else theme.surfaceMuted)
            if (sel) border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(0.6f)))
        }
        event {
            click { onTapItem(item) }
            // B1 长按先览（U5 400ms·8dp）：start 即回调，end/cancel 交页侧调度消失
            longPress { params ->
                when (params.state) {
                    "start" -> onLongPressItem?.invoke(item)
                    "end", "cancel" -> onLongPressRelease?.invoke()
                }
            }
        }
        vif({ sentimentOf(item) != null }) {
            View {
                attr {
                    width(6f); height(6f); borderRadius(3f)
                    marginRight(5f)
                    backgroundColor(sentimentColor(theme, sentimentOf(item)))
                }
            }
        }
        Text {
            attr {
                text(pillText(item))
                fontSize(10.5f)
                if (selected()?.id == item.id) fontWeightSemiBold()
                color(if (selected()?.id == item.id) theme.textPrimary else theme.textSecondary)
            }
        }
    }
}

private fun sentimentColor(theme: StockChatTheme, positive: Boolean?): Color = when (positive) {
    true -> theme.rise
    false -> theme.fall
    null -> theme.textTertiary
}

private fun sentimentLabel(positive: Boolean?): String = when (positive) {
    true -> "利好"
    false -> "利空"
    null -> "中性"
}

/**
 * 胶囊单行文本：时间 + 标题。Kuikly core 无 maxLines/textOverflow API（源码已核），
 * 按字符显示宽度手动截断（CJK=1 单位 / 拉丁=0.5，项目既有模式）。
 */
private fun pillText(item: NewsItem): String {
    val time = formatTapeTime(item.time)
    return "$time  " + truncateByWidth(item.title, PILL_TITLE_UNITS)
}

private fun truncateByWidth(text: String, maxUnits: Float): String {
    var w = 0f
    for ((i, ch) in text.withIndex()) {
        w += if (ch.code > 0x2E7F) 1f else 0.5f
        if (w > maxUnits) return text.substring(0, i) + "…"
    }
    return text
}

private fun formatTapeTime(raw: String): String {
    // `2026-09-07 10:23:42` → `09-07 10:23`
    return if (raw.length >= 16) raw.substring(5, 16) else raw
}

/** 胶囊标题显示宽度预算（单位 ≈ 一个 CJK 字符 @10.5f）；时间占 ~6.5。 */
private const val PILL_TITLE_UNITS = 11.5f
