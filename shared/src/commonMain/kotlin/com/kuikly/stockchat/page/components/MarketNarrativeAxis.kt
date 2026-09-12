package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.MarketEvent
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.TextAlign
import com.tencent.kuikly.core.views.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/** 叙事轴绘制几何（宽度由 Canvas 实测传入，其余常量与 doc 36 原型一致）。 */
internal object NarrativeAxisLayout {
    const val PAD = 10f
    const val PLOT_TOP = 14f
    const val PLOT_BOT = 92f
    const val DENS_TOP = 102f
    const val DENS_BOT = 122f
    const val LBL_Y = 140f
    const val LUNCH_MINUTE = 120
    const val MINUTES = 240
    const val PIN_RADIUS = 7f
    const val PIN_HIT = 16f

    fun xFor(minute: Int, width: Float): Float {
        val w = (width - PAD * 2).coerceAtLeast(1f)
        return PAD + minute.toFloat() / MINUTES * w
    }

    fun minuteFor(x: Float, width: Float): Int {
        val w = (width - PAD * 2).coerceAtLeast(1f)
        return (((x - PAD) / w * MINUTES).roundToInt()).coerceIn(0, MINUTES)
    }
}

/**
 * 今日叙事轴（doc 36 ①②，原型 v3 同构）：多空发散面积 + 事件钉 + 分钟成交密度 +
 * 时间机器游标。这是页面唯一的 Canvas 主视觉，scrub 是页面级手势。
 *
 * 绘制纪律（R1）：draw 闭包只读各 lambda 参数——均经页侧 lambda 读取 observable。
 * Canvas 拆两层：静态层（轨道/密度/面积/主线/钉子/刻度）只读 series/density/
 * baseValue/events，scrub 拖动期间零重绘；游标层只读 scrubMinute，拖动每帧
 * 仅重画游标（N1：scrub 中零补间、数字与游标同步直切，与原型一致）。
 *
 * 手势同 RiskSkyChart / RiskSkyTimeBrush 范式：touch 系列挂外层 View（Canvas 的
 * Event 基类只有 click/pan；pan 会在 Android DOWN 即锁死外层纵向 Scroller），
 * Canvas 与 View 同原点，touch x/y 即轴坐标。纵向拖动被 Scroller 拦截时按取消收尾。
 */
internal fun ViewContainer<*, *>.MarketNarrativeAxis(
    theme: StockChatTheme,
    height: Float,
    // 触摸命中与绘制的共用宽度（页侧由 pagerData.pageViewWidth 减横向 padding 得出，
    // RiskSkyTimeBrush 同款——touch 参数里没有 viewWidth）
    containerWidth: Float,
    // 已真实采集的度量序列；空序列 = 冷启动无帧，只画空轨道
    series: () -> List<Double>,
    // 与 series 一一对应的交易分钟。禁止把稀疏真实帧补成虚构的整日分钟线。
    seriesMinutes: () -> List<Int>,
    // 分钟成交密度 0..1（累计成交额逐分钟差分归一化）
    density: () -> List<Float>,
    // 多空分界值（红盘率口径 = 50；指数口径 = 0 即昨收）
    baseValue: () -> Double,
    events: () -> List<MarketEvent>,
    // 当前游标帧：-1 = 「现在」（游标隐藏）
    scrubMinute: () -> Int,
    timeLabel: (Int) -> String,
    onScrub: (Int) -> Unit,
    onScrubEnd: (dragged: Boolean) -> Unit,
    onTapPin: (MarketEvent) -> Unit,
) {
    var downX = 0f
    var moved = false
    var scrubbing = false

    fun valueY(value: Double, d0: Double, d1: Double): Float {
        val span = (d1 - d0).coerceAtLeast(1e-6)
        return NarrativeAxisLayout.PLOT_BOT -
            ((value - d0) / span * (NarrativeAxisLayout.PLOT_BOT - NarrativeAxisLayout.PLOT_TOP)).toFloat()
    }

    fun domain(): Pair<Double, Double> {
        val s = series()
        val base = baseValue()
        if (s.isEmpty()) return base - 1.0 to base + 1.0
        return (s.min() - 1.5).coerceAtMost(base - 0.8) to (s.max() + 1.5).coerceAtLeast(base + 0.8)
    }

    View {
        attr {
            height(height)
            touchEnable(true)
        }
        event {
            touchDown { params ->
                downX = params.x
                moved = false
                scrubbing = true
                onScrub(NarrativeAxisLayout.minuteFor(params.x, containerWidth))
            }
            touchMove { params ->
                if (!scrubbing) return@touchMove
                if (abs(params.x - downX) > 4f) moved = true
                onScrub(NarrativeAxisLayout.minuteFor(params.x, containerWidth))
            }
            touchUp { params ->
                if (!scrubbing) return@touchUp
                scrubbing = false
                // 轻点（未拖动）优先命中事件钉：钉子是「直达时刻」的按钮。
                if (!moved) {
                    val s = series()
                    if (s.isNotEmpty()) {
                        val (d0, d1) = domain()
                        val hit = events().firstOrNull { event ->
                            val minute = event.minute.coerceIn(0, NarrativeAxisLayout.MINUTES)
                            val px = NarrativeAxisLayout.xFor(minute, containerWidth)
                            val sampleIndex = seriesMinutes().indexOf(minute)
                            val py = if (sampleIndex >= 0) valueY(s[sampleIndex], d0, d1) else (NarrativeAxisLayout.PLOT_TOP + NarrativeAxisLayout.PLOT_BOT) / 2f
                            val dx = params.x - px
                            val dy = params.y - py
                            dx * dx + dy * dy <= NarrativeAxisLayout.PIN_HIT * NarrativeAxisLayout.PIN_HIT
                        }
                        if (hit != null) {
                            onTapPin(hit)
                            return@touchUp
                        }
                    }
                }
                onScrubEnd(moved)
            }
            touchCancel { _ ->
                if (scrubbing) {
                    scrubbing = false
                    onScrubEnd(moved)
                }
            }
        }
        // 静态层：午休线、密度条、发散面积、主线、事件钉、刻度。只读
        // series/density/baseValue/events——scrub 拖动期间这些都不变，本层零重绘
        //（拆层前拖动每帧要重画 240 根密度条 + 全部钉子，是跟手性瓶颈）。
        Canvas({
            attr {
                absolutePositionAllZero()
                height(height)
            }
        }) { canvas, _, _ ->
            val s = series()
            val minutes = seriesMinutes()
            val dens = density()
            val base = baseValue()
            val (d0, d1) = domain()
            val layout = NarrativeAxisLayout

            // 午休分隔线
            val lunchX = layout.xFor(layout.LUNCH_MINUTE, containerWidth)
            canvas.beginPath()
            canvas.moveTo(lunchX, layout.PLOT_TOP - 4f)
            canvas.lineTo(lunchX, layout.DENS_BOT)
            canvas.lineWidth(0.8f)
            canvas.setLineDash(listOf(2f, 3f))
            canvas.strokeStyle(theme.textTertiary.opacity(0.5f))
            canvas.stroke()
            canvas.setLineDash(emptyList())
            canvas.font(7.5f)
            canvas.textAlign(TextAlign.CENTER)
            canvas.fillStyle(theme.textTertiary)
            canvas.fillText("午休", lunchX, layout.PLOT_TOP - 6f)

            // 分钟成交密度细条（B 站高能进度条的语言：密度本身是导航信息）
            if (dens.isNotEmpty()) {
                val barW = (containerWidth - layout.PAD * 2) / layout.MINUTES
                val maxD = dens.max().coerceAtLeast(1e-6f)
                for (t in dens.indices) {
                    if (t > layout.MINUTES) break
                    val h = dens[t] / maxD * (layout.DENS_BOT - layout.DENS_TOP)
                    canvas.beginPath()
                    canvas.moveTo(layout.xFor(t, containerWidth) - barW / 2f, layout.DENS_BOT)
                    canvas.lineTo(layout.xFor(t, containerWidth) - barW / 2f, layout.DENS_BOT - h)
                    canvas.lineTo(layout.xFor(t, containerWidth) + barW / 2f, layout.DENS_BOT - h)
                    canvas.lineTo(layout.xFor(t, containerWidth) + barW / 2f, layout.DENS_BOT)
                    canvas.closePath()
                    canvas.fillStyle(theme.textPrimary.opacity(0.20f))
                    canvas.fill()
                }
                canvas.textAlign(TextAlign.LEFT)
                canvas.font(7.5f)
                canvas.fillStyle(theme.textTertiary)
                canvas.fillText("分钟成交强度", layout.PAD, layout.DENS_TOP - 4f)
            }

            if (s.isNotEmpty()) {
                val baseY = valueY(base, d0, d1)
                // 多空分界虚线
                canvas.beginPath()
                canvas.moveTo(layout.PAD, baseY)
                canvas.lineTo(containerWidth - layout.PAD, baseY)
                canvas.lineWidth(0.8f)
                canvas.setLineDash(listOf(3f, 3f))
                canvas.strokeStyle(theme.textTertiary.opacity(0.6f))
                canvas.stroke()
                canvas.setLineDash(emptyList())

                // 分段发散面积：曲线在分界上方填 rise、下方填 fall（多空拉锯被看见）
                var i = 0
                while (i < s.size - 1) {
                    var j = i
                    while (j < s.size - 1 && (s[j] >= base) == (s[j + 1] >= base)) j++
                    canvas.beginPath()
                    canvas.moveTo(layout.xFor(minutes.getOrElse(i) { 0 }, containerWidth), baseY)
                    for (t in i..j.coerceAtMost(s.size - 1)) {
                        canvas.lineTo(layout.xFor(minutes.getOrElse(t) { 0 }, containerWidth), valueY(s[t], d0, d1))
                    }
                    canvas.lineTo(layout.xFor(minutes.getOrElse(j.coerceAtMost(s.size - 1)) { 0 }, containerWidth), baseY)
                    canvas.closePath()
                    canvas.fillStyle(if (s[i] >= base) theme.rise.opacity(0.20f) else theme.fall.opacity(0.20f))
                    canvas.fill()
                    i = j + 1
                }

                // 主线
                canvas.beginPath()
                for (t in s.indices) {
                    val x = layout.xFor(minutes.getOrElse(t) { 0 }.coerceIn(0, layout.MINUTES), containerWidth)
                    val y = valueY(s[t], d0, d1)
                    if (t == 0) canvas.moveTo(x, y) else canvas.lineTo(x, y)
                }
                canvas.lineWidth(1.4f)
                canvas.lineCapRound()
                canvas.strokeStyle(theme.textPrimary)
                canvas.stroke()
            }

            // 事件钉（L2 事件才上钉，端侧 EventDetector 输出）
            val full = series()
            events().forEach { event ->
                val minute = event.minute.coerceIn(0, layout.MINUTES)
                val px = layout.xFor(minute, containerWidth)
                canvas.beginPath()
                canvas.moveTo(px, layout.PLOT_TOP - 2f)
                canvas.lineTo(px, layout.DENS_BOT)
                canvas.lineWidth(0.8f)
                canvas.strokeStyle(theme.brand.opacity(0.28f))
                canvas.stroke()
                val sampleIndex = seriesMinutes().indexOf(minute)
                val py = if (sampleIndex >= 0) valueY(full[sampleIndex], d0, d1) else (layout.PLOT_TOP + layout.PLOT_BOT) / 2f
                canvas.beginPath()
                canvas.arc(px, py, layout.PIN_RADIUS, 0f, (2 * PI).toFloat(), false)
                canvas.fillStyle(theme.brand)
                canvas.fill()
                canvas.font(8f)
                canvas.textAlign(TextAlign.CENTER)
                canvas.fillStyle(theme.onBrand)
                canvas.fillText(event.title.take(1), px, py + 2.8f)
                canvas.textAlign(TextAlign.LEFT)
            }

            // 时间刻度
            canvas.font(8f)
            canvas.textAlign(TextAlign.CENTER)
            canvas.fillStyle(theme.textTertiary)
            listOf(0, 60, 120, 180, 240).forEach { t ->
                canvas.fillText(timeLabel(t), layout.xFor(t, containerWidth), layout.LBL_Y)
            }
            canvas.textAlign(TextAlign.LEFT)
        }

        // 游标层：唯一读 scrubMinute 的绘制面。拖动每帧只重画这条竖线 + 圆点 +
        // 时间胶囊（依赖的 series 拖动期间不变，不触发额外重绘），跟手性来源。
        Canvas({
            attr {
                absolutePositionAllZero()
                height(height)
            }
        }) { canvas, _, _ ->
            val layout = NarrativeAxisLayout
            val s = series()
            val (d0, d1) = domain()
            val scrub = scrubMinute()
            if (scrub in 0..layout.MINUTES && s.size > scrub) {
                val cx = layout.xFor(scrub, containerWidth)
                canvas.beginPath()
                canvas.moveTo(cx, layout.PLOT_TOP - 6f)
                canvas.lineTo(cx, layout.DENS_BOT)
                canvas.lineWidth(1.2f)
                canvas.strokeStyle(theme.textPrimary)
                canvas.stroke()
                canvas.beginPath()
                canvas.arc(cx, valueY(s[scrub], d0, d1), 3.5f, 0f, (2 * PI).toFloat(), false)
                canvas.fillStyle(theme.textPrimary)
                canvas.fill()
                // 帧时间胶囊
                val label = timeLabel(scrub)
                canvas.font(8.5f)
                val tw = label.length * 5.6f + 10f
                val bx = (cx - tw / 2f).coerceIn(layout.PAD, containerWidth - layout.PAD - tw)
                canvas.beginPath()
                canvas.moveTo(bx, layout.PLOT_TOP - 6f)
                canvas.lineTo(bx + tw, layout.PLOT_TOP - 6f)
                canvas.lineTo(bx + tw, layout.PLOT_TOP + 8f)
                canvas.lineTo(bx, layout.PLOT_TOP + 8f)
                canvas.closePath()
                canvas.fillStyle(theme.textPrimary)
                canvas.fill()
                canvas.textAlign(TextAlign.CENTER)
                canvas.fillStyle(theme.onBrand)
                canvas.fillText(label, bx + tw / 2f, layout.PLOT_TOP + 3.5f)
                canvas.textAlign(TextAlign.LEFT)
            }
        }
    }
}
