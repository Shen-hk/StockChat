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
    softColor: () -> Color,
    onTapItem: (NewsItem) -> Unit,
    onPauseChange: (Boolean) -> Unit,
    // ↓↓↓ 以下为板块组件开发新增（doc 28 §B1 长按先览 / 情绪点；向后兼容，仅追加可选参数） ↓↓↓
    // 长按 400ms（移动 ≤8dp，由 Kuikly 内置 longPress 手势保证）回调；已有 onTapItem 行为不变。
    // 长按期间复用既有 pan 的暂停逻辑（longPress start 亦置 paused=true，end/cancel 复位）。
    onLongPressItem: ((NewsItem) -> Unit)? = null,
    // 长按松手/取消回调（doc 29 §4.2：先览气泡在松手 700ms 后消失，由页侧调度）。
    onLongPressRelease: (() -> Unit)? = null,
    // 条目前 6dp 情绪圆点颜色；返回 null 不画。颜色由页面用 DetailRules.scoreNewsSentiment 结果传入。
    itemDotColor: ((NewsItem) -> Color?)? = null,
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
            // 左右边缘渐隐（「过境」语义）：遮罩色 = 氛围 wash 在弹幕行处的复合色，
            // 而非 page 原色——弹幕带位于 wash 渐变（toneSoft→page）收束之前，
            // 若遮罩画 page 原色，两端会出现与中段不一致的浅色块（高亮感）。
            TapeEdgeFade(theme.page, softColor, alignRight = false)
            TapeEdgeFade(theme.page, softColor, alignRight = true)

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
                            paddingLeft(10f)
                            paddingRight(12f)
                            touchEnable(true)
                        }
                        event {
                            click { onTapItem(item) }
                            // 长按先览（doc 28 §B1 / U5 400ms·8dp）：longPress start 即回调，
                            // 同时暂停滚动（与 pan 暂停逻辑一致）；松手/取消复位，避免长按态下滚动卡住。
                            longPress { params ->
                                when (params.state) {
                                    "start" -> {
                                        onPauseChange(true)
                                        onLongPressItem?.invoke(item)
                                    }
                                    "end", "cancel" -> {
                                        onPauseChange(false)
                                        onLongPressRelease?.invoke()
                                    }
                                }
                            }
                            pan { params ->
                                when (params.state) {
                                    "start" -> onPauseChange(true)
                                    "end" -> onPauseChange(false)
                                }
                            }
                        }
                        // 条目前情绪圆点（doc 28 §B1）：6dp，颜色由页面经 scoreNewsSentiment 映射后传入；
                        // 返回 null 不画。读色置于 vif 闭包内，确保情绪状态变化可驱动重绘（R1）。
                        vif({ itemDotColor?.invoke(item) != null }) {
                            val dot = itemDotColor!!.invoke(item)!!
                            View {
                                attr {
                                    width(6f)
                                    height(6f)
                                    borderRadius(3f)
                                    backgroundColor(dot)
                                    marginRight(6f)
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

/**
 * 摘要卡：覆盖层 + 底部玻璃卡（复用 CardSheet 范式，轻量实现）。
 * doc 29 集成后详情页已改用弹幕带下方摘要条（StockDetailPage 内联 B2 strip，
 * §4.3 要求「非全屏 Sheet」）；本组件保留作为 doc 26 形态回退，当前无调用方。
 */
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

private fun ViewContainer<*, *>.TapeEdgeFade(
    page: Color,
    softColor: () -> Color,
    alignRight: Boolean,
) {
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
        // draw 闭包内读 softColor() 建立响应式依赖（与 AtmosphereBackdrop 同款重绘机制）
        val fadeColor = washColorAtTape(softColor(), page)
        val gradient = canvas.createLinearGradient(
            if (alignRight) width else 0f,
            0f,
            if (alignRight) 0f else width,
            0f,
        )
        gradient.addColorStop(0f, fadeColor.opacity(0.0f))
        gradient.addColorStop(1f, fadeColor.opacity(0.95f))
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

/**
 * 氛围 wash（AtmosphereBackdrop：soft 0% → soft@0.45 18% → page 58%）在弹幕行
 * （实测屏高 ~38%，即线性段中点）处的复合色 ≈ 0.36·toneSoft + 0.64·page。
 * 弹幕行位置随 Hero 内容 ±20dp 漂移对应 ~6% 混色误差，肉眼不可辨。
 */
private fun washColorAtTape(soft: Color, page: Color): Color {
    val softW = 0.36f
    fun channel(shift: Int): Int {
        val s = ((soft.hexColor shr shift) and 0xFFL).toInt()
        val p = ((page.hexColor shr shift) and 0xFFL).toInt()
        return (s * softW + p * (1f - softW)).toInt().coerceIn(0, 255)
    }
    return Color(channel(16), channel(8), channel(0), 1f)
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
