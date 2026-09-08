package com.kuikly.stockchat.cards.stock

/**
 * 聊天卡片分时走势图 —— Canvas 自绘（复刻详情页 DetailTimelineChart 的视觉语言）。
 *
 * 历史：曾用 KuiklyChartKit DSL（AreaChart/BarChart），效果与详情页割裂且整库只为
 * 这一个使用点服务，已移除 ：chartkit 依赖；K线（日/周/月蜡烛）仍走自研 KLineChart。
 *
 * 与详情页同款的视觉要素（按卡片尺寸压缩）：
 *  - 对称涨跌幅几何（昨收恒居中线，TimeLineCalculator.calculateSymmetric，241 槽位对齐）；
 *  - 昨收 1f 虚线基准；
 *  - 价格面积闭合到昨收：上方涨跌语义色渐变、下方对侧语义色淡渐变；
 *  - 价格线 1.7f 圆角 + 均价 1.1f 虚线（真实 amount 口径，缺失走近似）；
 *  - 红绿量能副图（相对前一分钟，首根对今开）；
 *  - 高低锚点（小圆点 + 9f 标注，边缘 clamp）；
 *  - 精简时间轴（09:30 / 11:30/13:00 / 15:00）；
 *  - 盘中生长态 now 点（实心点，卡片内不做脉冲，避免卡片常驻动画）；
 *  - 点按（click，非 pan——pan 会锁死聊天 Scroller 纵向滚动）十字光标 + 端边读数。
 *
 * 入场：draw-on 生长动画由本组件自驱（progress 0→1 的 setTimeout 链 + 900ms 安全兜底
 * 强制 1f，防断链卡死）。重渲染时若 progress 已到 1f 不再重播；进行中则从当前值续走。
 * draw 闭包读 progress/selected observable 建立依赖（R1），无 attr animate，不涉及 R5。
 */

import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.Quote
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

internal fun KuiklyTimelineChart(
    container: ViewContainer<*, *>,
    quote: Quote,
    context: CardContext,
    height: Float = 132f,
    onPointSelected: (label: String, value: Float) -> Unit = { _, _ -> },
) {
    val pts = quote.timeline
    if (pts.isEmpty()) return

    val theme = context.theme
    val state = CardChartState()

    // ── 布局：price 区 + 6f 间距 + 量能副图 + 14f 时间轴，全部按 height 比例压缩 ──
    val axisSide = 4f
    val timeAxisH = 14f
    val volH = (height * 0.20f).coerceIn(18f, 26f)
    val volGap = 6f
    val priceTop = 4f
    val priceH = (height - timeAxisH - volH - volGap - priceTop).coerceAtLeast(40f)
    val volTop = priceTop + priceH + volGap
    val plotW: (Float) -> Float = { w -> (w - axisSide * 2f).coerceAtLeast(1f) }

    // ── 入场生长动画：setTimeout 链推进 progress，900ms 安全兜底强制 1f ──
    fun startDrawOn() {
        if (state.progress >= 1f) return // 已走完不重播；进行中则续走
        var i = (state.progress * DRAW_ON_STEPS).roundToInt()
        fun tick() {
            i++
            state.progress = (i.toFloat() / DRAW_ON_STEPS).coerceIn(0f, 1f)
            if (state.progress < 1f) setTimeout(DRAW_ON_STEP_MS) { tick() }
        }
        tick()
        setTimeout(DRAW_ON_SAFETY_MS) { state.progress = 1f }
    }
    startDrawOn()

    container.Canvas({
        attr {
            height(height)
            marginTop(10f)
            alignSelfStretch()
            touchEnable(true)
        }
        event {
            // 点按十字光标（非 pan：pan 挂在聊天 Scroller 子视图上会锁死纵向滚动）。
            // 命中已选点附近 → 清除；否则选中最近槽位。
            click { params ->
                val plot = plotW(params.x * 0f + params.x.let { 0f }.let { 0f }) // 占位，下一行真实计算
                // 真实 plot 宽用画布宽度近似：卡片恒为全宽拉伸，这里用父宽回推不可行，
                // 改为记录 draw 时的宽度（drawState.measuredW）。
                val w = state.measuredW
                if (w <= 0f) return@click
                val n = pts.size
                val pw = plotW(w)
                val idx = (((params.x - axisSide) / pw * 240f).roundToInt())
                    .coerceIn(0, n - 1)
                val next = if (state.selected == idx) -1 else idx
                state.selected = next
                if (next >= 0) {
                    val p = pts[next]
                    onPointSelected(p.time, p.price.toFloat())
                }
            }
        }
    }) { canvas, width, _ ->
        state.measuredW = width
        val progress = state.progress
        val selected = state.selected
        val pw = plotW(width)
        val slotX: (Int) -> Float = { axisSide + it / 240f * pw }
        val geometry = TimeLineCalculator.calculateSymmetric(pts, pw, priceH, quote.previousClose)

        // ── 昨收基准虚线（无数据时也是唯一内容）──
        val baselineY = priceTop + geometry.baselineY
        canvas.beginPath()
        canvas.moveTo(axisSide, baselineY)
        canvas.lineTo(axisSide + pw, baselineY)
        canvas.setLineDash(listOf(4f, 3f))
        canvas.lineWidth(1f)
        canvas.strokeStyle(theme.textTertiary.opacity(0.75f))
        canvas.stroke()
        canvas.setLineDash(emptyList())

        // ── 轻网格：上下 ±50%、±100% 两条横线 ──
        val dev = geometry.upper - quote.previousClose
        canvas.lineWidth(0.5f)
        canvas.strokeStyle(theme.divider)
        listOf(1.0, 0.5).forEach { ratio ->
            listOf(quote.previousClose + dev * ratio, quote.previousClose - dev * ratio).forEach { level ->
                val y = priceTop + geometry.yFor(level)
                canvas.beginPath()
                canvas.moveTo(axisSide, y)
                canvas.lineTo(axisSide + pw, y)
                canvas.stroke()
            }
        }

        val tone = if (quote.rising) theme.rise else theme.fall
        val opposite = if (quote.rising) theme.fall else theme.rise
        val visible = if (progress >= 1f) pts.size else max(2, (pts.size * progress).roundToInt())

        // ── 价格面积：闭合到昨收基线；上方 tone 渐变、下方对侧语义色 ──
        if (visible >= 2) {
            val lastX = slotX(visible - 1)
            val above = canvas.createLinearGradient(0f, priceTop, 0f, baselineY)
            above.addColorStop(0f, tone.opacity(0.16f))
            above.addColorStop(1f, tone.opacity(0.02f))
            canvas.beginPath()
            canvas.moveTo(axisSide, baselineY)
            for (i in 0 until visible) canvas.lineTo(slotX(i), priceTop + geometry.points[i].y)
            canvas.lineTo(lastX, baselineY)
            canvas.closePath()
            canvas.fillStyle(above)
            canvas.fill()
            if (pts.take(visible).any { it.price < quote.previousClose }) {
                val below = canvas.createLinearGradient(0f, baselineY, 0f, priceTop + priceH)
                below.addColorStop(0f, opposite.opacity(0.10f))
                below.addColorStop(1f, opposite.opacity(0.02f))
                canvas.beginPath()
                canvas.moveTo(axisSide, baselineY)
                for (i in 0 until visible) canvas.lineTo(slotX(i), priceTop + geometry.points[i].y)
                canvas.lineTo(lastX, baselineY)
                canvas.closePath()
                canvas.fillStyle(below)
                canvas.fill()
            }
        }

        // ── 价格线 1.7f 圆角 ──
        canvas.beginPath()
        for (i in 0 until visible) {
            val x = slotX(i)
            val y = priceTop + geometry.points[i].y
            if (i == 0) canvas.moveTo(x, y) else canvas.lineTo(x, y)
        }
        canvas.strokeStyle(tone)
        canvas.lineWidth(1.7f)
        canvas.lineCapRound()
        canvas.stroke()

        // ── 均价虚线（真实 amount 口径，缺失走近似）──
        val averages = TimeLineCalculator.averagePrices(pts, quote.previousClose)
        canvas.beginPath()
        for (i in 0 until visible) {
            val x = slotX(i)
            val y = priceTop + geometry.yFor(averages[i])
            if (i == 0) canvas.moveTo(x, y) else canvas.lineTo(x, y)
        }
        canvas.setLineDash(listOf(3f, 3f))
        canvas.lineWidth(1.1f)
        canvas.strokeStyle(theme.textSecondary.opacity(0.8f))
        canvas.stroke()
        canvas.setLineDash(emptyList())

        // ── 量能副图：红绿量能条（相对前一分钟，首根对今开）──
        val flagsVol = TimeLineCalculator.volumeRisingFlags(pts, quote.open)
        val maxVolume = pts.maxOf { it.volume }.coerceAtLeast(1.0)
        val barW = (pw / 240f * 0.62f).coerceIn(1f, 4f)
        for (i in 0 until visible) {
            val vol = pts[i].volume
            if (vol <= 0.0) continue
            val h = (vol / maxVolume * volH).toFloat().coerceIn(1f, volH)
            val x = slotX(i) - barW / 2f
            val y = volTop + volH - h
            val barColor = when (flagsVol[i]) {
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

        // ── 高低锚点：实心点 + 9f 标注（边缘 clamp 22f）──
        val highIndex = pts.indices.maxBy { pts[it].price }
        val lowIndex = pts.indices.minBy { pts[it].price }
        canvas.font(9f)
        listOf(
            Triple(highIndex, "高 ${Format.price(pts[highIndex].price)}", true),
            Triple(lowIndex, "低 ${Format.price(pts[lowIndex].price)}", false),
        ).forEach { (index, label, isHigh) ->
            val x = slotX(index)
            val y = priceTop + geometry.points[index].y + if (isHigh) -9f else 14f
            val anchorX = x.coerceIn(axisSide + 22f, axisSide + pw - 22f)
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

        // ── 盘中生长态 now 点（实心，不做脉冲）──
        if (pts.size < 241) {
            val lastX = slotX(pts.size - 1)
            val lastY = priceTop + geometry.points.last().y
            canvas.beginPath()
            canvas.arc(lastX, lastY, 2.6f, 0f, (2 * PI).toFloat(), false)
            canvas.fillStyle(tone)
            canvas.fill()
        }

        // ── 十字光标：竖虚线 + 空心点 + 端边读数（点按选中态）──
        if (selected in pts.indices) {
            val x = slotX(selected)
            val priceY = priceTop + geometry.points[selected].y
            canvas.beginPath()
            canvas.moveTo(x, priceTop)
            canvas.lineTo(x, volTop + volH)
            canvas.setLineDash(listOf(3f, 3f))
            canvas.lineWidth(0.8f)
            canvas.strokeStyle(theme.textTertiary)
            canvas.stroke()
            canvas.setLineDash(emptyList())
            canvas.beginPath()
            canvas.arc(x, priceY, 3f, 0f, (2 * PI).toFloat(), false)
            canvas.fillStyle(theme.marketGlass)
            canvas.fill()
            canvas.lineWidth(1.4f)
            canvas.strokeStyle(tone)
            canvas.stroke()
            // 读数：左侧贴左边、右侧贴右边（与详情页端边读数同款）
            val p = pts[selected]
            val pct = (p.price - quote.previousClose) / quote.previousClose * 100.0
            canvas.font(9f)
            canvas.fillStyle(tone)
            val readLeft = x < axisSide + pw / 2f
            canvas.textAlign(if (readLeft) TextAlign.LEFT else TextAlign.RIGHT)
            val textX = if (readLeft) axisSide + 2f else axisSide + pw - 2f
            canvas.fillText("${p.time} ${Format.price(p.price)} ${Format.percent(pct)}", textX, priceTop + 9f)
            canvas.textAlign(TextAlign.LEFT)
        }

        // ── 精简时间轴 ──
        canvas.font(8.5f)
        canvas.fillStyle(theme.textTertiary)
        canvas.textAlign(TextAlign.CENTER)
        val labelY = volTop + volH + 10f
        listOf(
            Triple("09:30", 0, TextAlign.LEFT),
            Triple("11:30/13:00", 120, TextAlign.CENTER),
            Triple("15:00", 240, TextAlign.RIGHT),
        ).forEach { (label, slot, align) ->
            canvas.textAlign(align)
            canvas.fillText(label, slotX(slot), labelY)
        }
        canvas.textAlign(TextAlign.LEFT)
    }
}

/** 入场生长动画参数：20 步 × 24ms ≈ 480ms；900ms 兜底强制走完。 */
private const val DRAW_ON_STEPS = 20
private const val DRAW_ON_STEP_MS = 24
private const val DRAW_ON_SAFETY_MS = 900

/**
 * 卡片分时图交互状态：progress 驱动入场生长重绘、selected 驱动十字光标重绘（R1）；
 * measuredW 为 draw 时实测画布宽（click 事件里反算槽位用，非响应式）。
 */
private class CardChartState {
    var progress by observable(0f)
    var selected by observable(-1)
    var measuredW: Float = 0f
}
