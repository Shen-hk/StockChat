package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 新闻弹幕带（doc 26 §5.5，形态：单条队列）：34f 玻璃细条，一条从右缘匀速飘出
 * （≈55dp/s，时长随标题长度自适应）→ 停 1.5s → 下一条，同屏最多 1 条。
 * 按住暂停/松开续播（步进驱动，进度天然保留）；点击弹玻璃摘要卡。
 * 内容仅标题 + 来源 + 时间，不做涨跌染色；空列表整条隐藏（组件不渲染）。
 *
 * 状态全部页侧持有（items/index/offset/paused observable + setTimeout 步进链），
 * 本组件无状态（SwipeActionRow/VoiceBar 同范式）；步进驱动而非 attr animate，
 * 规避 R5「复位动画被消费」与 commonMain 无时间源两项实测红线。
 */
internal fun ViewContainer<*, *>.NewsTape(
    theme: StockChatTheme,
    renderer: GlassRenderer,
    items: () -> List<NewsItem>,
    index: () -> Int,
    offset: () -> Float,
    paused: () -> Boolean,
    reduceMotion: Boolean,
    containerWidth: Float,
    onTapItem: (NewsItem) -> Unit,
    onPauseChange: (Boolean) -> Unit,
) {
    vif({ items().isNotEmpty() }) {
        View {
            attr {
                marginTop(10f)
                height(TAPE_HEIGHT)
                borderRadius(11f)
                overflow(true)
                touchEnable(true)
            }
            GlassBackdrop(theme.glass.peek, renderer)
            // 左右边缘渐隐（「过境」语义）：page 色 → 透明 横向渐变细条
            TapeEdgeFade(theme, alignRight = false)
            TapeEdgeFade(theme, alignRight = true)

            vbind({ index() to items().size }) {
                val list = items()
                if (list.isNotEmpty()) {
                    val item = list[index().mod(list.size)]
                    View {
                        attr {
                            absolutePosition(
                                left = if (reduceMotion) 12f else offset(),
                                top = 0f,
                            )
                            height(TAPE_HEIGHT)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingRight(12f)
                            touchEnable(true)
                        }
                        event {
                            click { onTapItem(item) }
                            pan { params ->
                                when (params.state) {
                                    "start" -> onPauseChange(true)
                                    "end" -> onPauseChange(false)
                                }
                            }
                        }
                        Text {
                            attr {
                                text(item.title)
                                fontSize(11f)
                                color(theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("  ${item.source} · ${formatTapeTime(item.time)}")
                                fontSize(9f)
                                color(theme.textTertiary)
                            }
                        }
                        vif({ paused() }) {
                            View {
                                attr {
                                    marginLeft(8f)
                                    width(26f)
                                    height(14f)
                                    borderRadius(7f)
                                    backgroundColor(theme.surfaceMuted)
                                    allCenter()
                                }
                                Text {
                                    attr {
                                        text("停")
                                        fontSize(9f)
                                        color(theme.textTertiary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 摘要卡：覆盖层 + 底部玻璃卡（复用 CardSheet 范式，轻量实现）。 */
internal fun ViewContainer<*, *>.NewsSummarySheet(
    item: () -> NewsItem?,
    theme: StockChatTheme,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    vif({ item() != null }) {
        val current = item()
        View {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x4D000000))
                touchEnable(true)
                if (current != null) opacity(1f) else opacity(0f)
            }
            event { click { onDismiss() } }
        }
        if (current != null) {
            View {
                attr {
                    absolutePosition(left = 16f, right = 16f, bottom = 0f)
                    paddingBottom(24f)
                }
                View {
                    attr {
                        padding(16f)
                        borderRadius(16f)
                        backgroundColor(theme.marketGlass)
                        border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
                        boxShadow(BoxShadow(0f, 8f, 28f, theme.textPrimary.opacity(0.16f)))
                        // 新挂载两帧淡入（R4：首帧不播动画，setTimeout 翻转由调用方 state 驱动）
                        opacity(1f)
                    }
                    Text {
                        attr {
                            text(current.title)
                            fontSize(14f)
                            fontWeightSemiBold()
                            color(theme.textPrimary)
                            lineHeight(20f)
                        }
                    }
                    Text {
                        attr {
                            text("${current.source} · ${current.time}")
                            marginTop(6f)
                            fontSize(10f)
                            color(theme.textTertiary)
                        }
                    }
                    vif({ current.summary.isNotEmpty() }) {
                        Text {
                            attr {
                                text(current.summary)
                                marginTop(10f)
                                fontSize(12f)
                                lineHeight(18f)
                                color(theme.textSecondary)
                            }
                        }
                    }
                    vif({ current.url.isNotEmpty() }) {
                        View {
                            attr {
                                marginTop(14f)
                                height(32f)
                                paddingLeft(14f)
                                paddingRight(14f)
                                alignSelfFlexStart()
                                allCenter()
                                borderRadius(16f)
                                backgroundColor(theme.brandSoft)
                            }
                            Text {
                                attr {
                                    text("阅读原文")
                                    fontSize(11f)
                                    fontWeightSemiBold()
                                    color(theme.brand)
                                }
                            }
                            event { click { onOpenUrl(current.url) } }
                        }
                    }
                    Text {
                        attr {
                            text("内容来自公开媒体，仅陈述事实，不构成任何建议")
                            marginTop(12f)
                            fontSize(9f)
                            color(theme.textTertiary)
                        }
                    }
                    event { click { onDismiss() } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.TapeEdgeFade(theme: StockChatTheme, alignRight: Boolean) {
    Canvas({
        attr {
            if (alignRight) {
                absolutePosition(right = 0f, top = 0f)
            } else {
                absolutePosition(left = 0f, top = 0f)
            }
            width(TAPE_EDGE)
            height(TAPE_HEIGHT)
            touchEnable(false)
        }
    }) { canvas, width, height ->
        val gradient = canvas.createLinearGradient(
            if (alignRight) width else 0f,
            0f,
            if (alignRight) 0f else width,
            0f,
        )
        gradient.addColorStop(0f, theme.page.opacity(0.0f))
        gradient.addColorStop(1f, theme.page.opacity(0.95f))
        canvas.fillStyle(gradient)
        canvas.beginPath()
        canvas.moveTo(0f, 0f)
        canvas.lineTo(width, 0f)
        canvas.lineTo(width, height)
        canvas.lineTo(0f, height)
        canvas.closePath()
        canvas.fill()
    }
}

/** 步进估算条宽：CJK ≈ 11f/字、拉丁 ≈ 6f/字 + 来源时间缀 + 余量。 */
internal fun estimateTapeWidth(item: NewsItem): Float {
    val titleWidth = item.title.sumOf { ch ->
        (if (ch.code > 0x2E7F) 11.0 else 6.0)
    }.toFloat()
    return titleWidth + 150f
}

private fun formatTapeTime(raw: String): String {
    // `2026-09-07 10:23:42` → `09-07 10:23`
    return if (raw.length >= 16) raw.substring(5, 16) else raw
}

private const val TAPE_HEIGHT = 34f
private const val TAPE_EDGE = 28f
