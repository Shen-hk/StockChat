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
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
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
 * 详情页分时主图上的「新闻旗标」数据（doc 29 §4.3 B2 / 图侧）。
 *
 * @param index   旗标锚定的分时 index（与 [Quote.timeline] 一一对应）
 * @param isPositive true=涨（红）/ false=跌（绿），着色沿用数据语义，不混用 brand
 * @param label   旗右侧小字（如发布后 1h 涨跌幅 "-1.12%"），纯事实文案
 * @param dropped false=悬于图外顶部（旗杆指向价位，待落）；true=已落到对应价位
 *                dropped 由 false→true 时由本组件驱动 550ms 弹性下落（见下方协调层）。
 */
data class ChartFlag(
    val index: Int,
    val isPositive: Boolean,
    val label: String,
    val dropped: Boolean,
)

/**
 * 公开顶层函数：「quote → 分时价格点位序列」。
 *
 * 语义：返回 [Quote.timeline] 中每个点的收盘价序列（[Double]），按时间升序，
 * 与分时图绘制所用的点位一一对应——index i 即 [Quote.timeline] 的第 i 个点，
 * 点数 = timeline.size（A 股全天至多 241 个槽位），**不是固定 240**。
 * 横轴 slot 用 `/240f` 作全天槽位对齐分母（见 [DetailTimelineChart] 内 slotX），
 * 与序列长度无关；本函数只负责「价格序列」本身，供页面/规则引擎（如异动检测、
 * 句图联动锚点换算）复用，不影响绘制结果。
 */
fun detailTimelineSeries(quote: Quote): List<Double> =
    quote.timeline.map { it.price }

/**
 * 详情页自绘分时主体（doc 26 §4）：弃 ChartKit（本页），Canvas 全量绘制。
 * 241 固定槽位（盘中生长态）、对称涨跌幅双轴（昨收恒居中线）、均价虚线、
 * 昨收虚线基准、每分钟红绿量能、高低锚点、now 脉冲、十字光标随值卡。
 * 轴标注 / 高低锚点文字 / 时间轴均由 Canvas fillText 承担（ContextApi 实测支持，
 * 无 fillRect/globalAlpha——量能条 / 旗标底走路径填充，淡出用 Color.opacity）。
 *
 * 动画纪律（AGENTS.md）：
 * - R1：draw 闭包内读 quote/crosshair/drawProgress/pulse/state.selecting/state.selectRange/
 *   flags/state.flagDropProgress/band/sonarIndices 等 observable 建立依赖，数据变化驱动重绘；
 * - R4：入场 draw-on 由页侧 drawProgress 0→1 驱动（setTimeout 链，断链兜底在页侧）；
 * - 十字光标：横向 pan 捕获（不抢 Scroller 纵向滚动），start 显示 / move 跟随 / end 清除。
 *
 * 本次扩展（doc 29）：④ 异动声呐、① 圈选即问、⑤ 十字线停顿预填、B2 新闻旗标（图侧）、
 * ② 句图联动区间带。全部以「新增可选参数 + 默认值」方式扩展，默认参数下绘制与交互
 * 与改前完全一致（声呐/旗标/区间带为空、圈选/停顿回调为空操作）。
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
    // ── 以下为本次新增可选参数（默认值保证现有调用点不加改动、行为不变）──
    sonarIndices: () -> List<Int> = { emptyList() },   // ④ 异动点 index 列表
    selectedSonarIndex: () -> Int = { -1 },            // ④ 当前选中声呐点
    onSonarTap: (Int) -> Unit = {},                    // ④ 轻点声呐点回调
    flags: () -> List<ChartFlag> = { emptyList() },    // B2 图侧新闻旗标列表
    band: () -> Triple<Int, Int, Boolean>? = { null }, // ② 句图联动区间带 (start,end,fromSentence)
    onCircleSelect: (Int, Int) -> Unit = { _, _ -> },  // ① 圈选松手回调（起止 index）
    onSelectStateChange: (Boolean) -> Unit = {},       // ① 进入/退出圈选态（页面显隐 hint）
    onScrubPause: (Int) -> Unit = {},                  // ⑤ scrub 停顿 600ms 回调
) {
    // ── 新增交互的内部状态 ──
    // 响应式字段放进一个小类（与 StockDetailPage 的 `by observable(...)` 同来源/同形态，
    // 驱动 Canvas 重绘，R1）；其余为纯瞬态（事件闭包内读写，不驱动重绘）。
    val state = ChartInteractionState()
    // 非响应式手势瞬态（事件闭包内读写，不驱动重绘）
    var downX = 0f
    var downY = 0f
    var movedDist = 0f                                        // 按下到当前位移平方（≤64 即 ≤8dp）
    var longPressFired = false
    var longPressCancelled = false                            // 移动 >8dp 置真，使待触发长按失效
    var pauseSlot = -1
    var pauseRevision = 0                                     // 槽位变化即自增，使旧停顿计时失效（免 clearTimeout）
    var droppedKnown = emptySet<Int>()                        // 已启动下落动画的旗标 index

    // ④ 声呐点命中测试：落点距某声呐中心 ≤12dp（平方 144）返回其 index，否则 -1
    fun sonarHitTest(x: Float, y: Float): Int {
        val q = quote()
        if (q.timeline.isEmpty() || q.previousClose <= 0.0) return -1
        val pw = (containerWidth - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
        val g = TimeLineCalculator.calculateSymmetric(q.timeline, pw, PRICE_HEIGHT, q.previousClose)
        sonarIndices().forEach { idx ->
            if (idx !in q.timeline.indices) return@forEach
            val sx = AXIS_LEFT + idx / 240f * pw
            val sy = PRICE_TOP + g.points[idx].y
            val dx = x - sx
            val dy = y - sy
            if (dx * dx + dy * dy <= 144f) return idx
        }
        return -1
    }

    // B2 旗标 550ms 弹性下落（easeOutBack 过冲模拟弹性）；reduceMotion 直接落位。
    // 说明：本组件以 setTimeout 步进驱动（与 NewsTape 同范式，commonMain 无连续时间源），
    // 不走 attr animate，故不存在 R5「动画预注册滞后」问题。
    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val p = t - 1f
        return 1f + c3 * p * p * p + c1 * p * p
    }

    fun setFlagProgress(idx: Int, v: Float) {
        val m = HashMap(state.flagDropProgress)
        m[idx] = v
        state.flagDropProgress = m
    }

    fun startFlagDrop(idx: Int, rm: Boolean) {
        if (rm) {
            setFlagProgress(idx, 1f) // reduceMotion：直接落位，无动画
            return
        }
        val steps = 20
        val stepMs = 28 // 20×28 ≈ 560ms，接近 550ms
        var i = 0
        fun tick() {
            i++
            val raw = (i.toFloat() / steps).coerceIn(0f, 1f)
            setFlagProgress(idx, easeOutBack(raw))
            if (i < steps) setTimeout(stepMs) { tick() }
        }
        tick()
    }

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
            // 本次在 pan 内扩展：① 长按 400ms 进圈选（与 scrub 互斥）、④ 轻点声呐、
            // ⑤ scrub 停顿 600ms 预填。长按计时用 setTimeout 实现（U5：400ms/8dp）。
            event {
                pan { params ->
                    val q = quote()
                    if (q.timeline.isEmpty()) return@pan
                    val n = q.timeline.size
                    val plotW = (containerWidth - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
                    val slotOf: (Float) -> Int = { x ->
                        ((x - AXIS_LEFT) / plotW * 240f).roundToInt().coerceIn(0, n - 1)
                    }

                    // 按下：记录起点，启动 400ms 长按计时（① 圈选入口）
                    if (params.state == "start") {
                        downX = params.x
                        downY = params.y
                        movedDist = 0f
                        longPressFired = false
                        longPressCancelled = false
                        setTimeout(400) {
                            // 移动 >8dp 或已释放则失效；与 scrub 互斥
                            if (!longPressFired && !longPressCancelled && movedDist <= 64f && !state.selecting) {
                                longPressFired = true
                                val idx = slotOf(downX)
                                state.selecting = true
                                state.selectRange = Pair(idx, idx)
                                onSelectStateChange(true)
                            }
                        }
                    }

                    if (state.selecting) {
                        // ① 圈选拖动态：实时更新预览带，不进 scrub（与 scrub 互斥）
                        if (!params.isEnd) {
                            val idx = slotOf(params.x)
                            state.selectRange = Pair(state.selectRange.first, idx)
                        }
                    } else {
                        // 原有 scrub：松手保留光标（KLineChart 选中同款语义）
                        if (!params.isEnd || params.state == "start") {
                            val slot = slotOf(params.x)
                            onScrub(slot)
                            // ⑤ scrub 停顿 600ms：位置变化则重置计时，一次停顿只回调一次
                            if (slot != pauseSlot) {
                                pauseSlot = slot
                                pauseRevision++
                                val myRev = pauseRevision
                                setTimeout(600) {
                                    if (myRev == pauseRevision) onScrubPause(pauseSlot)
                                }
                            }
                        }
                        // 移动 >8dp 取消长按计时；累计位移供松手判定 tap / 圈选
                        if (params.state == "move") {
                            val dx = params.x - downX
                            val dy = params.y - downY
                            val d2 = dx * dx + dy * dy
                            if (d2 > movedDist) movedDist = d2
                            if (movedDist > 64f && !longPressFired) longPressCancelled = true
                        }
                    }

                    // 松手：清理计时；圈选则判定回调，否则判定声呐点命中
                    if (params.isEnd) {
                        longPressCancelled = true
                        pauseRevision++ // 使任何待触发停顿计时失效
                        if (state.selecting) {
                            val (s, e) = state.selectRange
                            if (abs(e - s) >= 3) onCircleSelect(min(s, e), max(s, e))
                            state.selecting = false
                            state.selectRange = Pair(-1, -1)
                            onSelectStateChange(false)
                        } else if (movedDist <= 64f) {
                            // ④ 声呐点命中：落点距中心 ≤12dp，回调且不进 scrub
                            val hit = sonarHitTest(downX, downY)
                            if (hit >= 0) onSonarTap(hit)
                        }
                        longPressFired = false
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

                // ── 区间高亮带（① 圈选预览 / ② 句图联动，绘制层级在价格线之下）──
                val drawBand: (Int, Int, Float) -> Unit = { s, e, a ->
                    val x0 = slotX(s.coerceIn(0, points.size - 1))
                    val x1 = slotX(e.coerceIn(0, points.size - 1))
                    val left = min(x0, x1)
                    val right = max(x0, x1)
                    canvas.beginPath()
                    canvas.moveTo(left, PRICE_TOP)
                    canvas.lineTo(right, PRICE_TOP)
                    canvas.lineTo(right, VOL_TOP + VOL_HEIGHT)
                    canvas.lineTo(left, VOL_TOP + VOL_HEIGHT)
                    canvas.closePath()
                    canvas.fillStyle(theme.brand.opacity(a))
                    canvas.fill()
                }
                // ① 圈选预览带（仅拖动态，起止差 >0 才画）
                val sel = state.selectRange
                if (state.selecting && sel.first >= 0 && sel.second >= 0 && sel.first != sel.second) {
                    drawBand(sel.first, sel.second, 0.12f)
                }
                // ② 句图联动区间带（fromSentence=true 用 16%、false 用 12%）
                val b = band()
                if (b != null) {
                    drawBand(b.first, b.second, if (b.third) 0.16f else 0.12f)
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
                val flagsVol = TimeLineCalculator.volumeRisingFlags(points, q.open)
                val maxVolume = points.maxOf { it.volume }.coerceAtLeast(1.0)
                val barW = plotW / 240f * 0.62f
                for (i in 0 until visible) {
                    val vol = points[i].volume
                    if (vol <= 0.0) continue
                    val h = (vol / maxVolume * VOL_HEIGHT).toFloat().coerceIn(1f, VOL_HEIGHT)
                    val x = slotX(i) - barW / 2f
                    val y = VOL_TOP + VOL_HEIGHT - h
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

                // ── 十字光标：竖虚线 + 价格/均价空心点（圈选拖动态隐藏）──
                if (scrubIndex in points.indices &&
                    !(state.selecting && state.selectRange.first != state.selectRange.second)
                ) {
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

                // ── ④ 异动声呐点：brand 实心点 + 呼吸圆环（复用 pulse 驱动；selected 白心蓝边）──
                val sonar = sonarIndices()
                if (sonar.isNotEmpty()) {
                    val selSonar = selectedSonarIndex()
                    sonar.forEach { idx ->
                        if (idx !in points.indices) return@forEach
                        val sx = slotX(idx)
                        val sy = PRICE_TOP + geometry.points[idx].y
                        if (!reduceMotion && pulse()) {
                            canvas.beginPath()
                            canvas.arc(sx, sy, 9f, 0f, (2 * PI).toFloat(), false)
                            canvas.lineWidth(1.6f)
                            canvas.strokeStyle(theme.brand.opacity(0.35f))
                            canvas.stroke()
                        }
                        canvas.beginPath()
                        canvas.arc(sx, sy, 3f, 0f, (2 * PI).toFloat(), false)
                        canvas.fillStyle(theme.brand)
                        canvas.fill()
                        if (idx == selSonar) {
                            canvas.beginPath()
                            canvas.arc(sx, sy, 1.6f, 0f, (2 * PI).toFloat(), false)
                            canvas.fillStyle(theme.marketGlass)
                            canvas.fill()
                            canvas.beginPath()
                            canvas.arc(sx, sy, 3.4f, 0f, (2 * PI).toFloat(), false)
                            canvas.lineWidth(1.4f)
                            canvas.strokeStyle(theme.brand)
                            canvas.stroke()
                        }
                    }
                }

                // ── B2 新闻旗标（图侧）：旗杆 1.5dp + 三角旗；dropped 时 550ms 弹性落到目标价 ──
                val flagList = flags()
                if (flagList.isNotEmpty()) {
                    val drop = state.flagDropProgress
                    flagList.forEach { fl ->
                        if (fl.index !in points.indices) return@forEach
                        val targetY = PRICE_TOP + geometry.points[fl.index].y
                        val topY = PRICE_TOP + 2f // 未落时悬于图顶
                        val t = drop[fl.index] ?: 0f // 0=图顶，1=目标价
                        val y = topY + (targetY - topY) * t
                        val x = slotX(fl.index)
                        val tone = if (fl.isPositive) theme.rise else theme.fall
                        val poleTop = y - 12f
                        // 旗杆
                        canvas.beginPath()
                        canvas.moveTo(x, targetY)
                        canvas.lineTo(x, poleTop)
                        canvas.lineWidth(1.5f)
                        canvas.strokeStyle(tone)
                        canvas.stroke()
                        // 三角旗（右指，涨红跌绿）
                        canvas.beginPath()
                        canvas.moveTo(x, poleTop)
                        canvas.lineTo(x, poleTop + 9f)
                        canvas.lineTo(x + 11f, poleTop + 4.5f)
                        canvas.closePath()
                        canvas.fillStyle(tone)
                        canvas.fill()
                        // 旗右侧 label 小字（半透明底 + 涨红跌绿）
                        canvas.font(9f)
                        canvas.textAlign(TextAlign.LEFT)
                        val tw = fl.label.length * 5.4f + 4f
                        canvas.beginPath()
                        canvas.moveTo(x + 13f, poleTop - 1f)
                        canvas.lineTo(x + 13f + tw, poleTop - 1f)
                        canvas.lineTo(x + 13f + tw, poleTop + 10f)
                        canvas.lineTo(x + 13f, poleTop + 10f)
                        canvas.closePath()
                        canvas.fillStyle(tone.opacity(0.18f))
                        canvas.fill()
                        canvas.fillStyle(tone)
                        canvas.fillText(fl.label, x + 15f, poleTop + 7f)
                    }
                    canvas.textAlign(TextAlign.LEFT)
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

        // ── B2 旗标下落协调层：检测新落旗并启动 550ms 弹性下落（尺寸 0、不可见、不接收事件）。
        //    flags() 在 attr 闭包内读取建立响应式依赖（R1）；reduceMotion 直接落位。──
        View {
            attr {
                width(0f)
                height(0f)
                opacity(0f)
                touchEnable(false)
                val list = flags()
                val rm = reduceMotion
                val currentDropped = list.filter { it.dropped }.map { it.index }.toSet()
                currentDropped.forEach { idx ->
                    if (idx !in droppedKnown) startFlagDrop(idx, rm)
                }
                droppedKnown = currentDropped
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

/**
 * DetailTimelineChart 内部交互状态容器：把需要驱动 Canvas 重绘的响应式字段
 * 收敛到一个小类里（与 StockDetailPage 的 `by observable(...)` 同来源、同形态，
 * 确保编译期与运行时表现一致）。仅①圈选态/预览带、B2 旗标下落进度需要响应式。
 */
private class ChartInteractionState {
    var selecting by observable(false)                       // ① 是否处于圈选态
    var selectRange by observable(Pair(-1, -1))              // ① 圈选起止 index（预览带）
    var flagDropProgress by observable(emptyMap<Int, Float>()) // B2 旗标下落进度（index→0..1）
}
