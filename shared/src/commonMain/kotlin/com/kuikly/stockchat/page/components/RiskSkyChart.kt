package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.page.risk.SkyGeometry
import com.kuikly.stockchat.page.risk.SkyLayer
import com.kuikly.stockchat.page.risk.StarLayout
import com.kuikly.stockchat.page.risk.TimeBrushLayout
import com.kuikly.stockchat.page.risk.clampF
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.TextAlign
import com.tencent.kuikly.core.views.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 风险星图 Canvas 自绘（doc 32 §6.1，doc 26 Canvas 范式）。
 *
 * 五投影同图：星星位置来自 [SkyGeometry]（切层不换位置，只变形高亮）：
 * - 团域虚线圈（抱团）：CLUSTER 层实显，其余层淡至 0.25；
 * - 连线 + r 值小字（牵连）：仅 LINK 层绘制，|r|≥0.5 才画，粗细/亮度=强度；
 * - 光晕（颠簸）：按波动倍率取半径，VOLATILITY 层呼吸 + 「N×大盘」标注；
 * - 连板环纹（热度）：一圈 = 一连板，HEAT 层提亮、非连板星退暗 0.28；
 * - 事件徽标（日程）：星旁「MM-dd 财/解/息」，SCHEDULE 层淡显；时间刷命中日 → brand 命中圈。
 *
 * 绘制纪律：draw 闭包只读各 lambda 参数（R1 建立响应式依赖，数据变化驱动重绘）；
 * 无常驻循环——光晕/引路星脉冲由页侧 beaconPhase 步进 observable 驱动，reduceMotion 恒 0。
 * ContextApi 无 globalAlpha：淡出统一用 Color.opacity 乘算（DetailTimelineChart 同口径）。
 *
 * 命中顺序（doc 32 §6.1）：星体 20dp → 引路星光环 → 团域 → 空白。
 * LINK 层还支持拖星牵引：移动超过 12dp 才起拖（8–12dp 死区抬起不算点按也不算拖）；
 * 长按 500ms（移动超过 8dp 取消）通过页侧 Context Bar 发问。
 *
 * 手势必须用 touch 而非 pan：Android 渲染层只要 view 挂 pan 事件，DOWN 时就会
 * requestDisallowInterceptTouchEvent，整段手势内外层纵向 Scroller 都无法接管——
 * 数百 dp 高的星图会成为「滚动死区」。touch 不 disallow：纵向拖动被 Scroller
 * 拦截后以 touchCancel/touchUp(action=cancel) 收尾，拖拽星弹回，页面照常滚动
 * （ChatPage 语音「按住说话」同款已验证范式）。横向拖星不受拦截，牵引可玩。
 */
internal fun ViewContainer<*, *>.RiskSkyChart(
    theme: StockChatTheme,
    geometry: () -> SkyGeometry,
    correlations: () -> Map<String, Double>,
    layer: () -> SkyLayer,
    selectedSymbol: () -> String,
    beaconClusterIndex: () -> Int,
    beaconPhase: () -> Float,
    volRatioOf: (String) -> Double?,
    boardsOf: (String) -> Int,
    changePercentOf: (String) -> Double?,
    eventsOf: (String) -> List<MarketCalendarEvent>,
    calendarDayLabel: () -> String,
    dragOffsets: () -> Map<String, Pair<Float, Float>>,
    canvasHeight: () -> Float,
    reduceMotion: Boolean,
    onTapStar: (String) -> Unit,
    onTapBeacon: () -> Unit,
    onTapCluster: (String) -> Unit,
    onTapBlank: () -> Unit,
    onDragStar: (symbol: String, dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onLongPressStar: (String) -> Unit,
) {
    // 轻点瞬态（事件闭包内读写，非响应式）
    var downX = 0f
    var downY = 0f
    var movedDist = 0f
    var armedSymbol = ""
    var dragging = false
    var longPressShown = false
    var gestureGeneration = 0

    // 手势收尾（touchUp / touchCancel 共用，幂等——两路都可能触发，只生效一次）。
    // 取消（被外层 Scroller 拦截等）：拖拽中则弹回，不派发点按；
    // 正常抬起：拖拽中弹回；否则 ≤8dp 且未长按 → 按命中顺序派发点按。
    fun handleGestureEnd(cancelled: Boolean) {
        val wasDragging = dragging
        dragging = false
        armedSymbol = ""
        gestureGeneration++
        if (wasDragging) {
            onDragEnd()
            return
        }
        if (cancelled || longPressShown || movedDist > 64f) return
        val g = geometry()
        if (g.stars.isEmpty()) return
        val x = downX
        val y = downY
        // ① 星体 20dp
        val star = g.stars.firstOrNull { s ->
            val dx = x - s.x
            val dy = y - s.y
            dx * dx + dy * dy <= StarLayout.STAR_HIT_DP * StarLayout.STAR_HIT_DP
        }
        if (star != null) {
            onTapStar(star.symbol)
            return
        }
        // ② 引路星光环（团域外圈 4~22dp 环带）
        val bi = beaconClusterIndex()
        if (bi in g.clusters.indices) {
            val c = g.clusters[bi]
            val d = dist(x, y, c.cx, c.cy)
            if (d >= c.radius + 4f && d <= c.radius + 22f) {
                onTapBeacon()
                return
            }
        }
        // ③ 团域内部
        val cluster = g.clusters.firstOrNull { dist(x, y, it.cx, it.cy) <= it.radius }
        if (cluster != null) {
            onTapCluster(cluster.name)
            return
        }
        // ④ 空白
        onTapBlank()
    }

    View {
        attr {
            height(canvasHeight())
            touchEnable(true)
        }
        // touch 事件挂在外层 View（GroupEvent 才有 touch 系列；Canvas 的 Event
        // 基类只有 click/pan）。Canvas 与 View 同原点同尺寸，touch 的 x/y 即星图坐标。
        event {
            touchDown { params ->
                val g = geometry()
                if (g.stars.isEmpty()) return@touchDown
                downX = params.x
                downY = params.y
                movedDist = 0f
                dragging = false
                longPressShown = false
                armedSymbol = g.stars.firstOrNull { s ->
                    val dx = params.x - s.x
                    val dy = params.y - s.y
                    dx * dx + dy * dy <= StarLayout.STAR_HIT_DP * StarLayout.STAR_HIT_DP
                }?.symbol.orEmpty()
                val generation = ++gestureGeneration
                if (armedSymbol.isNotEmpty()) {
                    setTimeout(500) {
                        if (generation == gestureGeneration && !dragging && armedSymbol.isNotEmpty()) {
                            onLongPressStar(armedSymbol)
                            armedSymbol = ""
                            longPressShown = true
                        }
                    }
                }
            }
            touchMove { params ->
                if (armedSymbol.isEmpty()) return@touchMove
                val dx = params.x - downX
                val dy = params.y - downY
                val d2 = dx * dx + dy * dy
                if (d2 > movedDist) movedDist = d2
                if (movedDist > 64f) gestureGeneration++ // 8dp：取消长按
                if (layer() == SkyLayer.LINK && movedDist > 144f) {
                    dragging = true
                    onDragStar(armedSymbol, dx, dy)
                }
            }
            touchUp { params -> handleGestureEnd(params.action == "cancel") }
            touchCancel { _ -> handleGestureEnd(cancelled = true) }
        }
        Canvas({
            attr {
                absolutePositionAllZero()
                height(canvasHeight())
            }
        }) { canvas, _, _ ->
            val g = geometry()
            if (g.stars.isEmpty()) return@Canvas
            val ly = layer()
            val selected = selectedSymbol()
            val bi = beaconClusterIndex()
            val phase = if (reduceMotion) 0f else beaconPhase()
            val dayLabel = calendarDayLabel()
            val cors = correlations()
            val offsets = dragOffsets()
            fun x(s: com.kuikly.stockchat.page.risk.SkyStar): Float = s.x + (offsets[s.symbol]?.first ?: 0f)
            fun y(s: com.kuikly.stockchat.page.risk.SkyStar): Float = s.y + (offsets[s.symbol]?.second ?: 0f)

            // LINK 层孤立星（无任何 |r|≥0.5 连线）退暗
            val linkedSymbols = if (ly == SkyLayer.LINK) {
                g.stars.filter { s ->
                    g.stars.any { o ->
                        o.symbol != s.symbol &&
                            StarLayout.lookupCorrelation(cors, s.symbol, o.symbol)?.let { abs(it) >= StarLayout.LINK_MIN_R } == true
                    }
                }.map { it.symbol }.toSet()
            } else {
                emptySet()
            }

            fun starEvents(symbol: String): List<MarketCalendarEvent> = eventsOf(symbol)
            fun starLit(symbol: String): Boolean =
                dayLabel.isNotEmpty() && starEvents(symbol).any { it.date.substring(5) == dayLabel }

            fun starAlpha(symbol: String): Float = when {
                ly == SkyLayer.LINK && symbol !in linkedSymbols -> 0.28f
                ly == SkyLayer.HEAT && boardsOf(symbol) == 0 -> 0.28f
                dayLabel.isNotEmpty() && !starLit(symbol) -> 0.55f
                else -> 1f
            }

            // ── 1. 团域虚线圈（CLUSTER 实显，其余 0.25）──
            val clusterAlpha = if (ly == SkyLayer.CLUSTER) 1f else 0.25f
            g.clusters.forEach { c ->
                canvas.beginPath()
                canvas.arc(c.cx, c.cy, c.radius, 0f, (2 * PI).toFloat(), false)
                canvas.lineWidth(1f)
                canvas.setLineDash(listOf(3f, 4f))
                canvas.strokeStyle(theme.brand.opacity(0.20f * clusterAlpha))
                canvas.stroke()
                canvas.fillStyle(theme.brand.opacity(0.05f * clusterAlpha))
                canvas.fill()
                canvas.setLineDash(emptyList())
                if (clusterAlpha > 0.5f) {
                    canvas.font(10f)
                    canvas.textAlign(TextAlign.CENTER)
                    canvas.fillStyle(theme.textSecondary.opacity(0.9f * clusterAlpha))
                    canvas.fillText("${c.name} · ${c.memberCount} 只", c.cx, c.cy - c.radius - 7f)
                    canvas.textAlign(TextAlign.LEFT)
                }
            }

            // ── 2. 牵连连线 + r 值（仅 LINK 层）──
            if (ly == SkyLayer.LINK) {
                canvas.font(8.5f)
                for (i in g.stars.indices) {
                    val a = g.stars[i]
                    for (j in i + 1 until g.stars.size) {
                        val b = g.stars[j]
                        val r = StarLayout.lookupCorrelation(cors, a.symbol, b.symbol) ?: continue
                        val width = StarLayout.linkWidthDp(r)
                        if (width <= 0f) continue
                        canvas.beginPath()
                        canvas.moveTo(x(a), y(a))
                        canvas.lineTo(x(b), y(b))
                        canvas.lineWidth(width)
                        canvas.lineCapRound()
                        canvas.strokeStyle(theme.brand.opacity(0.22f + (abs(r).toFloat() - 0.5f) * 0.5f))
                        canvas.stroke()
                        // r 值小字（线中点上方），负相关带负号
                        canvas.textAlign(TextAlign.CENTER)
                        canvas.fillStyle(theme.textTertiary)
                        val label = (if (r < 0) "-" else "") + Format.decimal(abs(r), 2)
                        canvas.fillText(label, (x(a) + x(b)) / 2f, (y(a) + y(b)) / 2f - 5f)
                        canvas.textAlign(TextAlign.LEFT)
                    }
                }
            }

            // ── 3~8. 逐星：光晕 / 环纹 / 徽标 / 命中圈 / 星体 / 文字 ──
            g.stars.forEach { s ->
                val a = starAlpha(s.symbol)
                if (a <= 0.01f) return@forEach

                // 光晕（波动倍率；VOLATILITY 层呼吸 + 标注）
                val halo = StarLayout.haloRadiusDp(volRatioOf(s.symbol))
                if (halo > 0f) {
                    val breathe = if (ly == SkyLayer.VOLATILITY && !reduceMotion) {
                        1f + 0.07f * sin(phase * 2.0 * PI).toFloat()
                    } else {
                        1f
                    }
                    canvas.beginPath()
                    canvas.arc(x(s), y(s), halo * breathe, 0f, (2 * PI).toFloat(), false)
                    canvas.fillStyle(theme.flat.opacity(0.10f * a))
                    canvas.fill()
                    if (ly == SkyLayer.VOLATILITY) {
                        val vr = volRatioOf(s.symbol)
                        if (vr != null) {
                            canvas.font(8f)
                            canvas.textAlign(TextAlign.CENTER)
                            canvas.fillStyle(theme.textTertiary.opacity(0.9f * a))
                            canvas.fillText("${Format.decimal(vr, 1)}×大盘", x(s), y(s) + 37f)
                            canvas.textAlign(TextAlign.LEFT)
                        }
                    }
                }

                // 连板环纹：一圈 = 一连板（最外圈实线，内圈虚线）
                val boards = boardsOf(s.symbol)
                if (boards > 0) {
                    val ringAlpha = if (ly == SkyLayer.HEAT) 1f else 0.5f
                    for (i in 1..boards.coerceAtMost(4)) {
                        canvas.beginPath()
                        canvas.arc(x(s), y(s), 16f + i * 5f, 0f, (2 * PI).toFloat(), false)
                        canvas.lineWidth(1.4f)
                        if (i < boards) canvas.setLineDash(listOf(40f, 10f))
                        canvas.strokeStyle(theme.flat.opacity(0.45f * a * ringAlpha))
                        canvas.stroke()
                        if (i < boards) canvas.setLineDash(emptyList())
                    }
                }

                // 事件徽标（首个未来事件）：星旁「MM-dd 财/解/息」
                val firstEvent = starEvents(s.symbol).firstOrNull()
                if (firstEvent != null) {
                    val lit = starLit(s.symbol)
                    val badgeAlpha = when {
                        lit -> 1f
                        ly == SkyLayer.SCHEDULE -> 0.85f
                        else -> 0.6f
                    }
                    val bx = x(s) + 9f
                    val by = y(s) - 30f
                    canvas.beginPath()
                    canvas.moveTo(bx, by)
                    canvas.lineTo(bx + 36f, by)
                    canvas.lineTo(bx + 36f, by + 14f)
                    canvas.lineTo(bx, by + 14f)
                    canvas.closePath()
                    canvas.fillStyle(if (lit) theme.brandSoft else theme.surfaceMuted)
                    canvas.fill()
                    canvas.font(8f)
                    canvas.textAlign(TextAlign.CENTER)
                    canvas.fillStyle(if (lit) theme.brand else theme.textTertiary)
                    canvas.fillText("${firstEvent.date.substring(5)} ${eventMark(firstEvent)}", bx + 18f, by + 10f)
                    canvas.textAlign(TextAlign.LEFT)
                }

                // 日程命中圈（brand）
                if (starLit(s.symbol)) {
                    canvas.beginPath()
                    canvas.arc(x(s), y(s), 17f, 0f, (2 * PI).toFloat(), false)
                    canvas.lineWidth(1.6f)
                    canvas.strokeStyle(theme.brand.opacity(a))
                    canvas.stroke()
                }

                // 星体
                canvas.beginPath()
                canvas.arc(x(s), y(s), StarLayout.STAR_RADIUS, 0f, (2 * PI).toFloat(), false)
                canvas.fillStyle(theme.surface)
                canvas.fill()
                canvas.lineWidth(1f)
                canvas.strokeStyle(theme.divider.opacity(a))
                canvas.stroke()
                // 名字首两字（星心）
                canvas.font(8.5f)
                canvas.textAlign(TextAlign.CENTER)
                canvas.fillStyle(theme.textSecondary.opacity(a))
                canvas.fillText(s.name.take(2), x(s), y(s) + 3.5f)
                // 星名（上方）
                canvas.font(9.5f)
                canvas.fillStyle(theme.textSecondary.opacity(0.95f * a))
                canvas.fillText(s.name, x(s), y(s) - 21f)
                // 今日涨跌幅（红涨绿跌沿用既有 token，星图本体不用涨跌色、仅此处文字）
                val pct = changePercentOf(s.symbol)
                if (pct != null) {
                    canvas.font(8.5f)
                    canvas.fillStyle(if (pct >= 0) theme.rise.opacity(a) else theme.fall.opacity(a))
                    canvas.fillText(Format.percent(pct), x(s), y(s) + 26f)
                }
                canvas.textAlign(TextAlign.LEFT)
            }

            // ── 9. 引路星：最挤一团外圈脉冲光环（CLUSTER 层强调）──
            if (bi in g.clusters.indices) {
                val c = g.clusters[bi]
                val ringR = c.radius + 12f
                val beaconAlpha = if (ly == SkyLayer.CLUSTER) 1f else 0.35f
                canvas.beginPath()
                canvas.arc(c.cx, c.cy, ringR, 0f, (2 * PI).toFloat(), false)
                canvas.lineWidth(1.6f)
                canvas.setLineDash(listOf(6f, 6f))
                canvas.strokeStyle(theme.brand.opacity(0.5f * beaconAlpha))
                canvas.stroke()
                canvas.setLineDash(emptyList())
                if (!reduceMotion) {
                    canvas.beginPath()
                    canvas.arc(c.cx, c.cy, ringR + phase * 8f, 0f, (2 * PI).toFloat(), false)
                    canvas.lineWidth(2f)
                    canvas.strokeStyle(theme.brand.opacity(0.55f * (1f - phase) * beaconAlpha))
                    canvas.stroke()
                }
                canvas.font(9.5f)
                canvas.textAlign(TextAlign.CENTER)
                canvas.fillStyle(theme.brand.opacity(beaconAlpha))
                canvas.fillText("最挤的一团 · 点我看 3 行事实", c.cx, c.cy + ringR + 14f)
                canvas.textAlign(TextAlign.LEFT)
            }

            // ── 10. 选中环 ──
            val sel = g.stars.firstOrNull { it.symbol == selected }
            if (sel != null) {
                canvas.beginPath()
                canvas.arc(x(sel), y(sel), 19f, 0f, (2 * PI).toFloat(), false)
                canvas.lineWidth(1.8f)
                canvas.strokeStyle(theme.brand)
                canvas.stroke()
            }
        }
    }
}

/**
 * 日程层时间刷（doc 32 §6.1 三续落地）：Canvas 轨道 + 事件日刻度 + knob 跟手。
 * 映射全走 [TimeBrushLayout]（均匀线性 + PAD=26 绘制命中共用）：
 * - 按下/拖动：knob 跟手，实时回调 fraction（页侧换算最近事件日点亮星图）；
 * - 松手：onRelease(dragged=true, index) 吸附最近事件日；
 * - 轻点：onRelease(dragged=false, index)，页侧「已选日再点 = 取消命中」。
 *
 * 手势同 [RiskSkyChart] 用 touch 而非 pan（Android 上 pan 会锁死外层纵向
 * Scroller）；纵向拖动被拦截时按取消收尾——视为松手吸附，状态不悬挂。
 */
internal fun ViewContainer<*, *>.RiskSkyTimeBrush(
    theme: StockChatTheme,
    dayLabels: () -> List<String>,
    knobFraction: () -> Float,
    selectedIndex: () -> Int,
    containerWidth: Float,
    onScrubFraction: (Float) -> Unit,
    onRelease: (dragged: Boolean, index: Int) -> Unit,
) {
    var downX = 0f
    var moved = false
    var brushActive = false

    fun applyX(x: Float) {
        val frac = clampF(
            (x - TimeBrushLayout.PAD) /
                (containerWidth - TimeBrushLayout.PAD * 2).coerceAtLeast(1f),
            0f,
            1f,
        )
        onScrubFraction(frac)
    }

    // touchUp / touchCancel 共用（幂等）；取消也按松手吸附收尾，knob 不悬挂半路。
    fun releaseBrush() {
        if (!brushActive) return
        brushActive = false
        val n = dayLabels().size
        if (n <= 0) return
        val idx = TimeBrushLayout.nearestIndexForFraction(knobFraction(), n)
        onRelease(moved, idx)
    }

    View {
        attr {
            height(64f)
            touchEnable(true)
        }
        // 同星图主画布：touch 挂外层 View（Canvas 的 Event 基类无 touch 系列），
        // Canvas 与 View 同原点，touch x/y 即轨道坐标。
        event {
            touchDown { params ->
                val n = dayLabels().size
                if (n <= 0) return@touchDown
                downX = params.x
                moved = false
                brushActive = true
                applyX(params.x)
            }
            touchMove { params ->
                if (!brushActive) return@touchMove
                if (abs(params.x - downX) > 8f) moved = true
                applyX(params.x)
            }
            touchUp { _ -> releaseBrush() }
            touchCancel { _ -> releaseBrush() }
        }
        Canvas({
            attr {
                absolutePositionAllZero()
                height(64f)
            }
        }) { canvas, width, _ ->
            val labels = dayLabels()
            if (labels.isEmpty()) return@Canvas
            val frac = knobFraction()
            val selIdx = selectedIndex()
            val ticks = TimeBrushLayout.tickXs(labels.size, width)
            val trackY = 30f

            // 轨道
            canvas.beginPath()
            canvas.moveTo(TimeBrushLayout.PAD, trackY)
            canvas.lineTo(width - TimeBrushLayout.PAD, trackY)
            canvas.lineWidth(2f)
            canvas.strokeStyle(theme.divider)
            canvas.stroke()

            // 事件日刻度 + 日期标签
            canvas.font(8.5f)
            ticks.forEachIndexed { i, tx ->
                val isSel = i == selIdx
                canvas.beginPath()
                canvas.arc(tx, trackY, 3f, 0f, (2 * PI).toFloat(), false)
                canvas.fillStyle(if (isSel) theme.brand else theme.textTertiary)
                canvas.fill()
                if (isSel) {
                    canvas.beginPath()
                    canvas.arc(tx, trackY, 6f, 0f, (2 * PI).toFloat(), false)
                    canvas.lineWidth(1.2f)
                    canvas.strokeStyle(theme.brand.opacity(0.5f))
                    canvas.stroke()
                }
                canvas.textAlign(TextAlign.CENTER)
                canvas.fillStyle(if (isSel) theme.brand else theme.textTertiary)
                canvas.fillText(labels[i], tx, trackY + 16f)
                canvas.textAlign(TextAlign.LEFT)
            }

            // knob
            val kx = TimeBrushLayout.xForFraction(frac, width)
            canvas.beginPath()
            canvas.arc(kx, trackY, 9f, 0f, (2 * PI).toFloat(), false)
            canvas.fillStyle(theme.brand)
            canvas.fill()
            canvas.beginPath()
            canvas.arc(kx, trackY, 3f, 0f, (2 * PI).toFloat(), false)
            canvas.fillStyle(theme.onBrand)
            canvas.fill()
        }
    }
}

private fun dist(x: Float, y: Float, cx: Float, cy: Float): Float {
    val dx = x - cx
    val dy = y - cy
    return sqrt(dx * dx + dy * dy)
}

/** 事件徽标单字：财报→财 / 解禁→解 / 其余→息。 */
internal fun eventMark(event: MarketCalendarEvent): String = when (event.kind) {
    com.kuikly.stockchat.data.provider.CalendarEventKind.EARNINGS -> "财"
    com.kuikly.stockchat.data.provider.CalendarEventKind.UNLOCK -> "解"
    else -> "息"
}

/** 供 [Color.opacity] 之外的隐式约束占位：保持导入收敛。 */
private val unusedThemeRef: (StockChatTheme) -> StockChatTheme = { it }
