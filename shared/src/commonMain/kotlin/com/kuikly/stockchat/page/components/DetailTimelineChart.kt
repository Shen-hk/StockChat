package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.data.fontSizeScaled

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
import kotlin.math.sin

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
 * - 十字光标：touch 流 + 短长按 280ms 进入 scrub（不用 pan——pan 在 Android DOWN 拍
 *   即 requestDisallowInterceptTouchEvent 锁死父级 Scroller，普通上下滑动全被图表
 *   吃掉），长按显示 / move 跟随 / end 松手保留。
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
    onScrubLeave: () -> Unit = {},                     // ⑤ 松手离开 scrub（页面 2s 后清预填）
    onScrubActive: (Boolean) -> Unit = {},             // 长按进/出 scrub（页面锁/解锁 Scroller 滚动）
    onBlankTap: () -> Unit = {},                       // U1 点空白（非声呐、非拖动的轻点）回调
    sonarDrift: () -> Float = { 0f },                  // ④ 声呐气泡横向漂移相位（0..1 循环，页面步进驱动）
) {
    // ── 新增交互的内部状态 ──
    // 响应式字段放进一个小类（与 StockDetailPage 的 `by observable(...)` 同来源/同形态，
    // 驱动 Canvas 重绘，R1）；其余为纯瞬态（事件闭包内读写，不驱动重绘）。
    val state = ChartInteractionState()
    // 非响应式手势瞬态（事件闭包内读写，不驱动重绘）
    var downX = 0f
    var downY = 0f
    var movedDist = 0f                                        // 按下到当前位移平方
    var scrollingIntent = false                               // 纵向滑动意图：一旦成立，整段手势永不抢十字线
    var gestureDone = false                                   // 收尾幂等：up/cancel 只生效一次
    var scrubbing = false                                     // 长按已进入十字线 scrub 态
    var lpGen = 0                                             // 长按计时无取消句柄 → 代际计数失效
    var pauseSlot = -1
    var pauseRevision = 0                                     // 槽位变化即自增，使旧停顿计时失效（免 clearTimeout）
    var scrubLeaveRevision = 0                                // 松手 revision：使旧的 2s 清预填计时失效
    var selectingCircle = false                               // ① 圈选态（瞬态；响应式镜像在 state.selecting）
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
        // Canvas 只负责绘制：touchEnable(false)——① Canvas 的基础 Event 没有
        // touchDown/touchMove/touchUp/touchCancel（它们在 GroupEvent 上）；
        // ② 内容子树挂触摸会吞整条触摸流（RowGestureLayer 实测），手势统一由
        // 上方同尺寸手势覆盖层承担。
        Canvas({
            attr {
                absolutePositionAllZero()
                height(CHART_HEIGHT)
                touchEnable(false)
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

            // ── 轻网格：不再常驻两侧价格刻度，留出横向空间给价格曲线。──
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
                    // 纵向 clamp：PRICE_TOP=0 后「高」的标注不能顶出画布，
                    // 「低」的标注不能压过量能带顶（VOL_TOP - 4）。
                    val y = (PRICE_TOP + geometry.points[index].y + if (isHigh) -10f else 16f)
                        .coerceIn(10f, VOL_TOP - 4f)
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
                    // 横线：过当前价位的水平虚线（此前缺失，十字只有竖线）
                    canvas.beginPath()
                    canvas.moveTo(AXIS_LEFT, priceY)
                    canvas.lineTo(AXIS_LEFT + plotW, priceY)
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
                    // 十字线两端读数：只在交互时出现，替代挤压绘图区的常驻双侧坐标轴。
                    val pct = (points[scrubIndex].price - q.previousClose) / q.previousClose * 100.0
                    canvas.font(9f)
                    canvas.fillStyle(tone)
                    canvas.textAlign(TextAlign.LEFT)
                    canvas.fillText(Format.price(points[scrubIndex].price), 3f, priceY + 3f)
                    canvas.textAlign(TextAlign.RIGHT)
                    canvas.fillText(axisPercent(pct), width - 3f, priceY + 3f)
                    canvas.textAlign(TextAlign.LEFT)
                }

                // ── ④ 异动声呐点：brand 实心点 + 呼吸圆环（复用 pulse 驱动；selected 白心蓝边）──
                // 漂移：整颗气泡（环+点）绕锚点沿 X 轴小幅往返流动（不同 index 相位错开），
                // 呼吸动效不变；reduceMotion 下相位恒 0，原地呼吸。
                val sonar = sonarIndices()
                if (sonar.isNotEmpty()) {
                    val selSonar = selectedSonarIndex()
                    val driftPhase = if (reduceMotion) 0f else sonarDrift()
                    sonar.forEach { idx ->
                        if (idx !in points.indices) return@forEach
                        val wobble = sin(driftPhase * 2f * PI.toFloat() + idx * 1.9f) * 12f
                        val sx = slotX(idx) + wobble
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

            // ── 只保留时间轴；价格/涨跌幅跟随十字线贴边显示。──
            // 网格已全幅铺满（AXIS_LEFT/RIGHT=0），两端标签改贴边对齐避免被裁半。
            if (geometry != null) {
                canvas.font(9f)
                canvas.fillStyle(theme.textTertiary)
                listOf("09:30", "10:30", "11:30/13:00", "14:00", "15:00").forEachIndexed { i, label ->
                    val x = slotX(i * 60)
                    when (i) {
                        0 -> {
                            canvas.textAlign(TextAlign.LEFT)
                            canvas.fillText(label, x + 2f, VOL_TOP + VOL_HEIGHT + 14f)
                        }
                        4 -> {
                            canvas.textAlign(TextAlign.RIGHT)
                            canvas.fillText(label, x - 2f, VOL_TOP + VOL_HEIGHT + 14f)
                        }
                        else -> {
                            canvas.textAlign(TextAlign.CENTER)
                            canvas.fillText(label, x, VOL_TOP + VOL_HEIGHT + 14f)
                        }
                    }
                }
                canvas.textAlign(TextAlign.LEFT)
            }
        }

        // ── 手势覆盖层（RowGestureLayer 同款 touch 范式，2026-09-09 重构）──
        // 旧方案 pan 直接挂 Canvas：Android 渲染层 KRCSSGestureDetector 对带 pan 的
        // view 在 DOWN 拍即 requestDisallowInterceptTouchEvent(true)（字节码证实），
        // 父级 Scroller 整段手势被锁死——用户上下滑动全变成十字线拖动。
        // touch 不做 disallow：未进 scrub 前纵向拖动被 Scroller 正常拦截（touchCancel
        // 收尾），页面照常滚动。用户定案：普通上下滑动=页面滚动、不点亮十字线；
        // 短长按 280ms 才进十字线 scrub；纵向移动超过 6dp 会立即作废该入口，进入瞬间回调 onScrubActive(true) 让页面
        // 锁 Scroller 滚动（WatchlistPage 拖拽排序同款已验证机制），松手/取消恢复。
        // 长按计时用代际计数失效（RiskSkyChart 同款）。
        // ① 圈选即问（2026-09-09 实装）：横向拖动（位移 >14dp 且明显占优于纵向）进入
        // 圈选态，拖动实时更新预览带，松手回调 onCircleSelect——与纵向滚动（最高
        // 优先级）、280ms 短长按十字线 scrub 三者互斥，互不抢占。
        View {
            attr {
                absolutePositionAllZero()
                height(CHART_HEIGHT)
                touchEnable(true)
            }
            event {
                // 槽位换算每次现读 quote（event 闭包只在初始化执行一次，不能缓存 q）
                fun slotAt(x: Float): Int {
                    val q = quote()
                    if (q.timeline.isEmpty()) return -1
                    val plotW = (containerWidth - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
                    return ((x - AXIS_LEFT) / plotW * 240f).roundToInt().coerceIn(0, q.timeline.size - 1)
                }

                touchDown { e ->
                    downX = e.x
                    downY = e.y
                    movedDist = 0f
                    scrollingIntent = false
                    gestureDone = false
                    scrubbing = false
                    selectingCircle = false
                    lpGen++
                    val myGen = lpGen
                    setTimeout(CROSSHAIR_HOLD_MS) {
                        // 轻按停留 280ms 且没有纵向滚动意图才进十字线。横向微抖允许到 12dp，
                        // 但纵向一旦超过 6dp 会在 touchMove 中立即作废，优先保证页面滚动。
                        if (myGen == lpGen && !scrollingIntent && movedDist <= CROSSHAIR_HOLD_SLOP_SQ && !gestureDone) {
                            val slot = slotAt(downX)
                            if (slot >= 0) {
                                scrubbing = true
                                onScrubActive(true)
                                onScrub(slot)
                                pauseSlot = slot
                                pauseRevision++
                                val myRev = pauseRevision
                                setTimeout(600) {
                                    if (myRev == pauseRevision) onScrubPause(pauseSlot)
                                }
                            }
                        }
                    }
                }

                touchMove { e ->
                    if (gestureDone) return@touchMove
                    val dx = e.x - downX
                    val dy = e.y - downY
                    val d2 = dx * dx + dy * dy
                    if (d2 > movedDist) movedDist = d2
                    if (scrubbing) {
                        // scrub 态：十字线跟随手指 x（页面滚动已由 onScrubActive 锁定）
                        val slot = slotAt(e.x)
                        if (slot >= 0) {
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
                    } else if (abs(dy) > SCROLL_INTENT_DY) {
                        // 页面向上/下滚是最高优先级：即使父 Scroller 尚未发来 cancel，
                        // 也立刻撤销短长按，并禁止 touchUp 被误当作空白点按。
                        scrollingIntent = true
                        lpGen++
                    } else if (movedDist > CROSSHAIR_HOLD_SLOP_SQ) {
                        lpGen++ // 明显横移/抖动：作废短长按计时
                        // ① 圈选即问（2026-09-09 实装）：横向拖动位移明显占优于纵向时
                        // 进入圈选（走到这里 dy 必 ≤ 6dp，纵向滚动意图已在前序分支排除）。
                        // 进入即锁 Scroller（onScrubActive），后续纵向漂移不再被外层
                        // 拦截，手势全程留在本层；松手/取消在 touchUp/touchCancel 收尾。
                        if (!selectingCircle &&
                            abs(dx) > CIRCLE_ENTER_DX &&
                            abs(dx) > abs(dy) * 1.5f
                        ) {
                            selectingCircle = true
                            state.selecting = true
                            onSelectStateChange(true)
                            onScrubActive(true)
                        }
                        if (selectingCircle) {
                            val s = slotAt(downX)
                            val e2 = slotAt(e.x)
                            if (s >= 0 && e2 >= 0) state.selectRange = Pair(s, e2)
                        }
                    }
                }

                touchUp { _ ->
                    if (gestureDone) return@touchUp
                    gestureDone = true
                    lpGen++
                    pauseRevision++ // 使任何待触发停顿/长按计时失效
                    if (scrubbing) {
                        scrubbing = false
                        onScrubActive(false)
                        // 松手保留十字线（KLineChart 选中同款语义）；
                        // ⑤ 离开 scrub：2s 后清预填（期间任何新松手都会使本计时失效）
                        scrubLeaveRevision++
                        val myLeaveRev = scrubLeaveRevision
                        setTimeout(2000) {
                            if (myLeaveRev == scrubLeaveRevision) onScrubLeave()
                        }
                    } else if (selectingCircle) {
                        // ① 圈选收尾：退出圈选态 + 解锁 Scroller，有效区间回调页面
                        //（区间有效性 [hi-lo≥3] 由页面侧校验，短区间 toast 提示）
                        selectingCircle = false
                        state.selecting = false
                        onSelectStateChange(false)
                        onScrubActive(false)
                        val s = state.selectRange.first
                        val e2 = state.selectRange.second
                        state.selectRange = Pair(-1, -1)
                        if (s >= 0 && e2 >= 0 && s != e2) onCircleSelect(s, e2)
                    } else if (!scrollingIntent && movedDist <= CROSSHAIR_HOLD_SLOP_SQ) {
                        // ④ 声呐点命中：落点距中心 ≤12dp，回调且不进 scrub；
                        // 未命中 = 空白轻点 → U1「点空白全关」入口
                        val hit = sonarHitTest(downX, downY)
                        if (hit >= 0) onSonarTap(hit) else onBlankTap()
                    }
                }

                touchCancel { _ ->
                    // 被外层 Scroller 拦截（未进 scrub 的纵向滚动）或系统打断：
                    // 只清理计时与 scrub 锁，不触发 tap/松手语义
                    if (gestureDone) return@touchCancel
                    gestureDone = true
                    lpGen++
                    pauseRevision++
                    if (scrubbing) {
                        scrubbing = false
                        onScrubActive(false)
                    }
                    if (selectingCircle) {
                        // 圈选中被拦截/打断：静默退出，不回调松手语义（幂等收尾）
                        selectingCircle = false
                        state.selecting = false
                        onSelectStateChange(false)
                        onScrubActive(false)
                        state.selectRange = Pair(-1, -1)
                    }
                }
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
                                fontSizeScaled(9f)
                                color(theme.textTertiary)
                            }
                        }
                        Text {
                            attr {
                                text("${Format.price(point.price)}  ${Format.percent(pct)}")
                                marginTop(2f)
                                fontSizeScaled(11f)
                                fontWeightSemiBold()
                                color(valueColor)
                            }
                        }
                        Text {
                            attr {
                                text("均价 ${Format.price(averages[idx])}")
                                marginTop(2f)
                                fontSizeScaled(9f)
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

// 2026-09-08 版式调整：网格全幅铺满（AXIS_LEFT/RIGHT=0、PRICE_TOP=0，不留内边距），
// 分段控件缩小悬浮到图左上后，卡头整行高度折给绘图区（PRICE 260→290、VOL 66→70）。
private const val CHART_HEIGHT = 396f
private const val CROSSHAIR_HOLD_MS = 280
private const val SCROLL_INTENT_DY = 6f
private const val CROSSHAIR_HOLD_SLOP_SQ = 144f // 12dp × 12dp
private const val CIRCLE_ENTER_DX = 14f         // ① 圈选进入阈值：横向位移须超过此值且明显占优于纵向
private const val PRICE_TOP = 0f
private const val PRICE_HEIGHT = 290f
private const val VOL_TOP = 296f
private const val VOL_HEIGHT = 70f
private const val AXIS_LEFT = 0f
private const val AXIS_RIGHT = 0f
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
