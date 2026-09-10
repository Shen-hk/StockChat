package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.provider.NewsItem
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 全屏流式新闻弹幕（市场页 / 详情页共用，doc 36 J1 的 v2 形态）：无背板、无头部卡，
 * 条目以纯文本弹幕从右向左持续流动，在屏幕边缘自然流出/流入（唯一裁切边界
 * 是外层竖向 Scroller 的全屏宽 frame），双份内容 + offset % loopWidth 无缝循环。
 *
 * 状态与节拍全部由页侧持有（offset observable + 页侧 setTimeout 链步进），
 * 本组件纯渲染 + 点按/长按回调；暂停 = 页侧停止步进（市场页点条目开弹窗时、
 * 详情页摘要条展开或长按先览时停）。交互语义由页侧决定：市场页点按 = 弹窗，
 * 详情页点按 = 落旗 + 展开摘要条、长按 = 先览气泡（原 NewsTape 口径不变）。
 *
 * 为什么不用 animate() 驱动流动（R5）：一次 attr 注册只服务下一次驱动变化，
 * 而弹幕需要随时暂停/恢复、内容随数据重建——定时器直写 offset observable
 * 是最可控的通路，暂停 = 停止步进，恢复 = 继续步进，无需动画登记。
 *
 * 循环缝：core 无 post-measure 测量通路，一圈宽度用字符显示宽度估算
 * （见 [estimateNewsMarqueeLoopWidth]），attr 内 `offset % loopWidth` 自洽回绕；
 * 估差只影响接缝处 ±几 dp，可接受。
 */
internal fun ViewContainer<*, *>.NewsMarquee(
    theme: StockChatTheme,
    items: () -> List<NewsItem>,
    // 情绪判定：true=利好 / false=利空 / null=中性不染色
    sentimentOf: (NewsItem) -> Boolean?,
    // 页侧弹幕偏移（dp，随时间递增）
    offset: () -> Float,
    // 一圈估算宽度（dp），页侧按当前条目估算
    loopWidth: () -> Float,
    onTapItem: (NewsItem) -> Unit,
    // 当前选中条目（详情页摘要条展开态）：brand 色高亮，其余纯文本
    selected: () -> NewsItem? = { null },
    // B1 长按先览透传（详情页）：state=start 回调 item 与按压点 pageX/pageY
    onLongPressItem: ((NewsItem, Float, Float) -> Unit)? = null,
    onLongPressRelease: (() -> Unit)? = null,
) {
    vif({ items().isNotEmpty() }) {
        View {
            attr {
                marginTop(6f)
                height(34f)
                alignItemsCenter()
                // 不设 overflow(true)：弹幕越过自身 bounds 绘制，从屏幕两侧流出
            }
            // 双份内容拼接；vbind 以条目签名做 key——数据换内容时整行重建，
            // 重建帧直接落到当前 offset（无动画直设，无跳动感）。
            vbind({ items().joinToString("|") { it.id } }) {
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        height(34f)
                        val loop = loopWidth()
                        transform(Translate(0f, 0f, offsetX = if (loop > 0f) -(offset() % loop) else 0f))
                    }
                    repeat(2) {
                        items().forEach { item ->
                            TapeBullet(
                                theme, item, sentimentOf(item), selected(),
                                onTapItem, onLongPressItem, onLongPressRelease,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 弹幕条目（无背板）：情绪点 + 时间 + 标题整句，纯文本弹幕；选中条目 brand
 * 色加粗高亮（不改变宽度估算口径，避免循环缝漂移）。
 */
private fun ViewContainer<*, *>.TapeBullet(
    theme: StockChatTheme,
    item: NewsItem,
    sentiment: Boolean?,
    selected: NewsItem?,
    onTapItem: (NewsItem) -> Unit,
    onLongPressItem: ((NewsItem, Float, Float) -> Unit)?,
    onLongPressRelease: (() -> Unit)?,
) {
    val isSelected = selected?.id == item.id
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginRight(26f)
        }
        event {
            click { onTapItem(item) }
            if (onLongPressItem != null) {
                longPress { params ->
                    when (params.state) {
                        "start" -> onLongPressItem?.invoke(item, params.pageX, params.pageY)
                        "end", "cancel" -> onLongPressRelease?.invoke()
                    }
                }
            }
        }
        vif({ sentiment != null }) {
            View {
                attr {
                    width(5f); height(5f); borderRadius(2.5f)
                    marginRight(5f)
                    backgroundColor(bulletSentimentColor(theme, sentiment))
                }
            }
        }
        Text {
            attr {
                text("${formatTapeTime(item.time)}  ${item.title}")
                fontSizeScaled(11f)
                if (isSelected) fontWeightSemiBold()
                color(if (isSelected) theme.brand else theme.textSecondary)
            }
        }
    }
}

private fun bulletSentimentColor(theme: StockChatTheme, positive: Boolean?): Color = when (positive) {
    true -> theme.rise
    false -> theme.fall
    null -> theme.textTertiary
}

/**
 * 一圈估算宽度（dp）：字符显示宽度（CJK=1 / 拉丁=0.5）× 11f 字号 + 固定开销
 * （间距 26 + 情绪点位）。与渲染口径一致；估差只影响循环接缝 ±几 dp。
 * 两页 loopWidth() 直接委托本函数，避免口径漂移。
 */
internal fun estimateNewsMarqueeLoopWidth(items: List<NewsItem>, withSentimentDot: Boolean): Float {
    if (items.isEmpty()) return 0f
    return items.sumOf { item ->
        val text = "${formatTapeTime(item.time)}  ${item.title}"
        val units = text.sumOf { ch -> if (ch.code > 0x2E7F) 1.0 else 0.5 }
        units * 11.0 + 26.0 + if (withSentimentDot) 10.0 else 5.0
    }.toFloat()
}
