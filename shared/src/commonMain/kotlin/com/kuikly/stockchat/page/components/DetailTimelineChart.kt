package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.Quote
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextAlign
import com.tencent.kuikly.core.views.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 详情页自绘分时主体（doc 26 §4）：弃 ChartKit（本页），Canvas 全量绘制。
 * 241 固定槽位（盘中生长态）、对称涨跌幅双轴（昨收恒居中线）、均价虚线、
 * 昨收虚线基准、每分钟红绿量能、高低锚点、now 脉冲、十字光标随值卡。
 * 轴标注 / 高低锚点文字 / 时间轴均由 Canvas fillText 承担（ContextApi 实测支持，
 * 无 fillRect/globalAlpha——量能条走路径填充，淡出用 Color.opacity）。
 *
 * 动画纪律（AGENTS.md）：
 * - R1：draw 闭包内读 quote/crosshair/drawProgress/pulse observable 建立依赖，数据变化驱动重绘；
 * - R4：入场 draw-on 由页侧 drawProgress 0→1 驱动（setTimeout 链，断链兜底在页侧）；
 * - 十字光标：横向 pan 捕获（不抢 Scroller 纵向滚动），start 显示 / move 跟随 / end 清除。
 */
internal fun ViewContainer<*, *>.DetailTimelineChart(
    theme: StockChatTheme,
    quote: () -> Quote,
    crosshairIndex: () -> Int,
    drawProgress: () -> Float,
    pulse: () -> Boolean,
    reduceMotion: Boolean,
    containerWidth: Float,
    onScrub: (Int) -> Unit,
) {
    View {
        attr {
            height(CHART_HEIGHT)
        }
        Canvas({
            attr {
                absolutePositionAllZero()
                height(CHART_HEIGHT)
                touchEnable(true)
            }
            // 十字光标：pan 直接挂 Canvas（KLineChart 同款实测范式，无 capture——
            // 独立覆盖层 + capture(HORIZONTAL) 在 Scroller 内收不到事件）。
            // event 必须与 attr 同级挂在视图初始化作用域上，不能写进 attr 闭包。
                event {
                    pan { params ->
                        val q = quote()
                        if (q.timeline.isEmpty()) return@pan
                        val plotW = (containerWidth - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
                        // 松手保留光标（KLineChart 选中同款语义）：随值卡停在该槽供阅读，
                        // 再次触摸更新位置；切到 K 线或离开页面时随组件状态清除。
                        if (!params.isEnd || params.state == "start") {
                            val slot = ((params.x - AXIS_LEFT) / plotW * 240f).roundToInt()
                                .coerceIn(0, q.timeline.size - 1)
                            onScrub(slot)
                        }
                    }
                }
        }) { canvas, width, _ ->
            val q = quote()
            val progress = drawProgress()
            val scrubIndex = crosshairIndex()
            val points = q.timeline
            val plotW = (width - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
            val geometry = if (points.isEmpty() || q.previousClose <= 0.0) null
            else TimeLineCalculator.calculateSymmetric(points, plotW, PRICE_HEIGHT, q.previousClose)
            val slotX: (Int) -> Float = { AXIS_LEFT + it / 240f * plotW }

            // ── 网格：横 4 条（昨收 ±d、±d/2）+ 竖 5 条 ──
            if (geometry != null) {
                val d = geometry.upper - q.previousClose
                canvas.lineWidth(0.5f)
                canvas.strokeStyle(theme.divider)
                listOf(1.0, 0.5).forEach { ratio ->
                    listOf(q.previousClose + d * ratio, q.previousClose - d * ratio).forEach { level ->
                        val y = PRICE_TOP + geometry.yFor(level)
                        canvas.beginPath()
                        canvas.moveTo(AXIS_LEFT, y)
                        canvas.lineTo(AXIS_LEFT + plotW, y)
                        canvas.stroke()
                    }
                }
                listOf(0, 60, 120, 180, 240).forEach { slot ->
                    val x = slotX(slot)
                    canvas.beginPath()
                    canvas.moveTo(x, PRICE_TOP)
                    canvas.lineTo(x, PRICE_TOP + PRICE_HEIGHT)
                    canvas.stroke()
                }
            }

            // ── 昨收基准：1f 虚线（无数据时是唯一内容） ──
            val fallbackBaselineY = PRICE_TOP + PRICE_HEIGHT / 2f
            val baselineY = if (geometry != null) PRICE_TOP + geometry.baselineY else fallbackBaselineY
            canvas.beginPath()
            canvas.moveTo(AXIS_LEFT, baselineY)
            canvas.lineTo(AXIS_LEFT + plotW, baselineY)
            canvas.setLineDash(listOf(4f, 3f))
            canvas.lineWidth(1f)
            canvas.strokeStyle(theme.textTertiary.opacity(0.75f))
            canvas.stroke()
            canvas.setLineDash(emptyList())

            if (geometry != null) {
                val tone = if (q.rising) theme.rise else theme.fall
                val opposite = if (q.rising) theme.fall else theme.rise
                val visible = if (progress >= 1f) points.size else max(2, (points.size * progress).roundToInt())

                // ── 价格面积：闭合到昨收基线；上方 tone 渐变、下方对侧语义色 ──
                if (visible >= 2) {
                    val lastX = slotX(visible - 1)
                    val above = canvas.createLinearGradient(0f, PRICE_TOP, 0f, baselineY)
                    above.addColorStop(0f, tone.opacity(0.16f))
                    above.addColorStop(1f, tone.opacity(0.02f))
                    canvas.beginPath()
                    canvas.moveTo(AXIS_LEFT, baselineY)
                    for (i in 0 until visible) canvas.lineTo(slotX(i), PRICE_TOP + geometry.points[i].y)
                    canvas.lineTo(lastX, baselineY)
                    canvas.closePath()
                    canvas.fillStyle(above)
                    canvas.fill()
                    if (points.take(visible).any { it.price < q.previousClose }) {
                        val below = canvas.createLinearGradient(0f, baselineY, 0f, PRICE_TOP + PRICE_HEIGHT)
                        below.addColorStop(0f, opposite.opacity(0.10f))
                        below.addColorStop(1f, opposite.opacity(0.02f))
                        canvas.beginPath()
                        canvas.moveTo(AXIS_LEFT, baselineY)
                        for (i in 0 until visible) canvas.lineTo(slotX(i), PRICE_TOP + geometry.points[i].y)
                        canvas.lineTo(lastX, baselineY)
                        canvas.closePath()
                        canvas.fillStyle(below)
                        canvas.fill()
                    }
                }

                // ── 价格线：1.7f 圆角 ──
                canvas.beginPath()
                for (i in 0 until visible) {
                    val x = slotX(i)
                    val y = PRICE_TOP + geometry.points[i].y
                    if (i == 0) canvas.moveTo(x, y) else canvas.lineTo(x, y)
                }
                canvas.strokeStyle(tone)
                canvas.lineWidth(1.7f)
                canvas.lineCapRound()
                canvas.stroke()

                // ── 均价虚线：真实 amount 口径（缺失走近似），1.1f dash 3/3 ──
                val averages = TimeLineCalculator.averagePrices(points, q.previousClose)
                canvas.beginPath()
                for (i in 0 until visible) {
                    val x = slotX(i)
                    val y = PRICE_TOP + geometry.yFor(averages[i])
                    if (i == 0) canvas.moveTo(x, y) else canvas.lineTo(x, y)
                }
                canvas.setLineDash(listOf(3f, 3f))
                canvas.lineWidth(1.1f)
                canvas.strokeStyle(theme.textSecondary.opacity(0.8f))
                canvas.stroke()
                canvas.setLineDash(emptyList())

                // ── 量能条：每分钟一根，颜色按相对前一分钟（首根对今开） ──
                val flags = TimeLineCalculator.volumeRisingFlags(points, q.open)
                val maxVolume = points.maxOf { it.volume }.coerceAtLeast(1.0)
                val barW = plotW / 240f * 0.62f
                for (i in 0 until visible) {
                    val vol = points[i].volume
                    if (vol <= 0.0) continue
                    val h = (vol / maxVolume * VOL_HEIGHT).toFloat().coerceIn(1f, VOL_HEIGHT)
                    val x = slotX(i) - barW / 2f
                    val y = VOL_TOP + VOL_HEIGHT - h
                    val barColor = when (flags[i]) {
                        true -> theme.rise.opacity(0.62f)
                        false -> theme.fall.opacity(0.62f)
                        null -> theme.flat.opacity(0.5f)
                    }
                    canvas.beginPath()
                    canvas.moveTo(x, y)
                    canvas.lineTo(x + barW, y)
                    canvas.lineTo(x + barW, y + h)
                    canvas.lineTo(x, y + h)
                    canvas.closePath()
                    canvas.fillStyle(barColor)
                    canvas.fill()
                }

                // ── 高低锚点：2.6f 圆点 + 8.5sp 标注（x 向内 clamp 26f） ──
                val highIndex = points.indices.maxBy { points[it].price }
                val lowIndex = points.indices.minBy { points[it].price }
                canvas.font(9f)
                listOf(
                    Triple(highIndex, "高 ${Format.price(points[highIndex].price)}", true),
                    Triple(lowIndex, "低 ${Format.price(points[lowIndex].price)}", false),
                ).forEach { (index, label, isHigh) ->
                    val x = slotX(index)
                    val y = PRICE_TOP + geometry.points[index].y + if (isHigh) -10f else 16f
                    val anchorX = x.coerceIn(AXIS_LEFT + 26f, AXIS_LEFT + plotW - 26f)
                    canvas.textAlign(
                        when {
                            anchorX > x -> TextAlign.RIGHT
                            anchorX < x -> TextAlign.LEFT
                            else -> TextAlign.CENTER
                        },
                    )
                    canvas.fillStyle(tone)
                    canvas.fillText(label, anchorX, y)
                }
                canvas.textAlign(TextAlign.LEFT)

                // ── now 点（盘中生长态）：外圈脉冲 + 实心点 ──
                if (points.size < 241) {
                    val lastX = slotX(points.size - 1)
                    val lastY = PRICE_TOP + geometry.points.last().y
                    if (!reduceMotion && pulse()) {
                        canvas.beginPath()
                        canvas.arc(lastX, lastY, 8f, 0f, (2 * PI).toFloat(), false)
                        canvas.lineWidth(1.8f)
                        canvas.strokeStyle(tone.opacity(0.28f))
                        canvas.stroke()
                    }
                    canvas.beginPath()
                    canvas.arc(lastX, lastY, 2.8f, 0f, (2 * PI).toFloat(), false)
                    canvas.fillStyle(tone)
                    canvas.fill()
                }

                // ── 十字光标：竖虚线 + 价格/均价空心点 ──
                if (scrubIndex in points.indices) {
                    val x = slotX(scrubIndex)
                    val priceY = PRICE_TOP + geometry.points[scrubIndex].y
                    val avgY = PRICE_TOP + geometry.yFor(averages[scrubIndex])
                    canvas.beginPath()
                    canvas.moveTo(x, PRICE_TOP)
                    canvas.lineTo(x, VOL_TOP + VOL_HEIGHT)
                    canvas.setLineDash(listOf(3f, 3f))
                    canvas.lineWidth(0.8f)
                    canvas.strokeStyle(theme.textTertiary)
                    canvas.stroke()
                    canvas.setLineDash(emptyList())
                    listOf(priceY, avgY).forEach { y ->
                        canvas.beginPath()
                        canvas.arc(x, y, 3.2f, 0f, (2 * PI).toFloat(), false)
                        canvas.fillStyle(theme.marketGlass)
                        canvas.fill()
                        canvas.lineWidth(1.4f)
                        canvas.strokeStyle(tone)
                        canvas.stroke()
                    }
                }
            }

            // ── 左右轴刻度 + 时间轴标注（Canvas fillText） ──
            if (geometry != null) {
                val d = geometry.upper - q.previousClose
                canvas.font(9f)
                canvas.fillStyle(theme.textTertiary)
                listOf(1.0, 0.5, 0.0, -0.5, -1.0).forEach { ratio ->
                    val level = q.previousClose + d * ratio
                    val y = PRICE_TOP + geometry.yFor(level) + 3f
                    canvas.textAlign(TextAlign.LEFT)
                    canvas.fillText(Format.price(level), 2f, y)
                    canvas.textAlign(TextAlign.RIGHT)
                    canvas.fillText(axisPercent(d * ratio / q.previousClose * 100.0), width - 2f, y)
                }
                canvas.textAlign(TextAlign.CENTER)
                listOf("09:30", "10:30", "11:30/13:00", "14:00", "15:00").forEachIndexed { i, label ->
                    canvas.fillText(label, slotX(i * 60), VOL_TOP + VOL_HEIGHT + 14f)
                }
                canvas.textAlign(TextAlign.LEFT)
            }
        }

        // ── 十字光标随值卡（玻璃小卡，z 在 Canvas 上，数据驱动重建） ──
        vif({ crosshairIndex() >= 0 }) {
            vbind({ crosshairIndex() to quote().timeline.size }) {
                val q = quote()
                val idx = crosshairIndex()
                if (q.timeline.isNotEmpty() && idx in q.timeline.indices && q.previousClose > 0.0) {
                    val point = q.timeline[idx]
                    val plotW = (containerWidth - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
                    val averages = TimeLineCalculator.averagePrices(q.timeline, q.previousClose)
                    val rawX = AXIS_LEFT + idx / 240f * plotW
                    val cardX = min(max(rawX - TOOLTIP_HALF, 12f), (containerWidth - TOOLTIP_WIDTH - 12f).coerceAtLeast(12f))
                    val pct = (point.price - q.previousClose) / q.previousClose * 100.0
                    val valueColor = when {
                        point.price > q.previousClose -> theme.rise
                        point.price < q.previousClose -> theme.fall
                        else -> theme.textSecondary
                    }
                    View {
                        attr {
                            absolutePosition(left = cardX, top = 18f)
                            width(TOOLTIP_WIDTH)
                            padding(8f)
                            borderRadius(10f)
                            backgroundColor(theme.marketGlass)
                            border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
                            boxShadow(BoxShadow(0f, 4f, 14f, theme.textPrimary.opacity(0.10f)))
                            touchEnable(false)
                        }
                        Text {
                            attr {
                                text("${point.time} · 量 ${point.volume.roundToInt()}手")
                                fontSize(9f)
                                color(theme.textTertiary)
                            }
                        }
                        Text {
                            attr {
                                text("${Format.price(point.price)}  ${Format.percent(pct)}")
                                marginTop(2f)
                                fontSize(11f)
                                fontWeightSemiBold()
                                color(valueColor)
                            }
                        }
                        Text {
                            attr {
                                text("均价 ${Format.price(averages[idx])}")
                                marginTop(2f)
                                fontSize(9f)
                                color(theme.textSecondary)
                            }
                        }
                    }
                }
            }
        }

    }
}

private fun axisPercent(pct: Double): String =
    if (abs(pct) < 0.005) "0.00%"
    else (if (pct > 0) "+" else "-") + Format.decimal(abs(pct), 2) + "%"

private const val CHART_HEIGHT = 340f
private const val PRICE_TOP = 12f
private const val PRICE_HEIGHT = 220f
private const val VOL_TOP = 246f
private const val VOL_HEIGHT = 60f
private const val AXIS_LEFT = 38f
private const val AXIS_RIGHT = 40f
private const val TOOLTIP_WIDTH = 148f
private const val TOOLTIP_HALF = TOOLTIP_WIDTH / 2f
