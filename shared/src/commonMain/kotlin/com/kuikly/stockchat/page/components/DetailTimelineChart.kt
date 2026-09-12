package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.foundation.ui.fontSizeScaled

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.chart.model.ChartViewportAction
import com.kuikly.stockchat.chart.model.ChartViewportCommand
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.PlatformProfile
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePoint
import com.kuikly.stockchat.data.provider.MarketTimelineSpec
import com.tencent.kuikly.core.base.ViewContainer
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
import com.kuikly.stockchat.risk.sky.component.RiskSkyChart

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
 * 点数 = timeline.size，横轴槽位由证券所属市场决定，而非固定 A 股 241 点。
 * 本函数只负责「价格序列」本身，供页面/规则引擎（如异动检测、
 * 句图联动锚点换算）复用，不影响绘制结果。
 */
fun detailTimelineSeries(quote: Quote): List<Double> =
    quote.timeline.map { it.price }

/**
 * 详情页自绘分时主体（doc 26 §4）：弃 ChartKit（本页），Canvas 全量绘制。
 * 按市场的全天槽位（盘中生长态）、对称涨跌幅双轴（昨收恒居中线）、均价虚线、
 * 昨收虚线基准、每分钟红绿量能、高低锚点、now 脉冲、十字光标随值卡。
 * 轴标注 / 高低锚点文字 / 时间轴均由 Canvas fillText 承担（ContextApi 实测支持，
 * 无 fillRect/globalAlpha——量能条 / 旗标底走路径填充，淡出用 Color.opacity）。
 *
 * 动画纪律（AGENTS.md）：
 * - R1：draw 闭包内读 quote/crosshair/drawProgress/motionPhase/state.selecting/state.selectRange/
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
    /** 0..1 continuous visual phase; keeps chart breathing fluid without layout animation. */
    motionPhase: () -> Float,
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
    viewportCommand: () -> ChartViewportCommand = { ChartViewportCommand() },
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
    // 高频 touchMove 合并：native 可在一帧内送来多次 move，crosshair 只需消费最终槽位。
    var pendingScrubIndex = -1
    var scrubDispatchScheduled = false
    var scrubDispatchVersion = 0
    var lastScrubIndex = -1
    // 分时数据通常在本次手势期间不变；几何/均价不应随十字线位置重复计算。
    var cachedTimeline: List<QuotePoint>? = null
    var cachedBaseline = Double.NaN
    var cachedPlotWidth = -1f
    var cachedSlots = 0
    var cachedGeometry: com.kuikly.stockchat.chart.model.SymmetricGeometry? = null
    var cachedAverageTimeline: List<QuotePoint>? = null
    var cachedAverageBaseline = Double.NaN
    var cachedAverageSymbol = ""
    var cachedAverages = emptyList<Double>()
    // 量能色与最大量只由分时序列决定；此前每次十字线移动、声呐呼吸帧都会
    // 全量扫描一次，240 个点的图在拖动时会额外制造 GC 和掉帧。
    var cachedVolumeTimeline: List<QuotePoint>? = null
    var cachedVolumeOpen = Double.NaN
    var cachedVolumeFlags = emptyList<Boolean?>()
    var cachedMaxVolume = 1.0

    fun viewportFor(q: Quote): Pair<Int, Int> {
        val maxSpan = (MarketTimelineSpec.forSymbol(q.symbol).slotCount - 1).coerceAtLeast(1)
        val span = state.viewportSpan.takeIf { it > 0 }?.coerceIn(TIMELINE_MIN_SPAN, maxSpan) ?: maxSpan
        val start = state.viewportStart.coerceIn(0, (maxSpan - span).coerceAtLeast(0))
        return start to span
    }

    fun applyViewportCommand(command: ChartViewportCommand, q: Quote) {
        if (command.revision == state.lastViewportCommand) return
        state.lastViewportCommand = command.revision
        val maxSpan = (MarketTimelineSpec.forSymbol(q.symbol).slotCount - 1).coerceAtLeast(1)
        val (oldStart, oldSpan) = viewportFor(q)
        val newSpan = when (command.action) {
            ChartViewportAction.ZOOM_IN -> (oldSpan * 0.70f).roundToInt().coerceIn(TIMELINE_MIN_SPAN, maxSpan)
            ChartViewportAction.ZOOM_OUT -> (oldSpan / 0.70f).roundToInt().coerceIn(TIMELINE_MIN_SPAN, maxSpan)
            ChartViewportAction.RESET -> maxSpan
            else -> oldSpan
        }
        val newStart = when (command.action) {
            ChartViewportAction.PAN_LEFT -> (oldStart - (oldSpan * 0.22f).roundToInt()).coerceAtLeast(0)
            ChartViewportAction.PAN_RIGHT -> (oldStart + (oldSpan * 0.22f).roundToInt())
                .coerceAtMost((maxSpan - oldSpan).coerceAtLeast(0))
            ChartViewportAction.ZOOM_IN, ChartViewportAction.ZOOM_OUT ->
                (oldStart + oldSpan / 2 - newSpan / 2).coerceIn(0, (maxSpan - newSpan).coerceAtLeast(0))
            ChartViewportAction.RESET -> 0
            ChartViewportAction.NONE -> oldStart
        }
        state.viewportStart = newStart
        state.viewportSpan = newSpan
    }

    fun averagesFor(q: Quote): List<Double> {
        if (
            cachedAverageTimeline !== q.timeline || cachedAverageBaseline != q.previousClose ||
            cachedAverageSymbol != q.symbol
        ) {
            cachedAverageTimeline = q.timeline
            cachedAverageBaseline = q.previousClose
            cachedAverageSymbol = q.symbol
            cachedAverages = TimeLineCalculator.averagePrices(
                q.timeline,
                q.previousClose,
                MarketTimelineSpec.forSymbol(q.symbol).lotSize,
            )
        }
        return cachedAverages
    }

    fun volumeMetricsFor(q: Quote): Pair<List<Boolean?>, Double> {
        if (cachedVolumeTimeline !== q.timeline || cachedVolumeOpen != q.open) {
            cachedVolumeTimeline = q.timeline
            cachedVolumeOpen = q.open
            cachedVolumeFlags = TimeLineCalculator.volumeRisingFlags(q.timeline, q.open)
            cachedMaxVolume = q.timeline.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 1.0
        }
        return cachedVolumeFlags to cachedMaxVolume
    }

    fun dispatchScrub(index: Int, immediately: Boolean = false) {
        if (index < 0) return
        // 视觉层先消费本地位置，业务回调仍可按帧合并，避免快划时十字线落后手指。
        if (state.scrubIndex != index) state.scrubIndex = index
        if (immediately) {
            pendingScrubIndex = -1
            if (index != lastScrubIndex) {
                lastScrubIndex = index
                onScrub(index)
            }
            return
        }
        pendingScrubIndex = index
        if (scrubDispatchScheduled) return
        scrubDispatchScheduled = true
        val version = scrubDispatchVersion
        setTimeout(16) {
            if (version != scrubDispatchVersion) return@setTimeout
            scrubDispatchScheduled = false
            val next = pendingScrubIndex
            pendingScrubIndex = -1
            if (next >= 0 && next != lastScrubIndex) {
                lastScrubIndex = next
                onScrub(next)
            }
        }
    }

    fun flushScrub() {
        val next = pendingScrubIndex
        pendingScrubIndex = -1
        scrubDispatchVersion++
        scrubDispatchScheduled = false
        if (next >= 0 && next != lastScrubIndex) {
            lastScrubIndex = next
            onScrub(next)
        }
    }

    // ④ 声呐点命中测试：落点距某声呐中心 ≤12dp（平方 144）返回其 index，否则 -1
    fun sonarHitTest(x: Float, y: Float): Int {
        val q = quote()
        if (q.timeline.isEmpty() || q.previousClose <= 0.0) return -1
        val pw = (containerWidth - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
        val slots = MarketTimelineSpec.forSymbol(q.symbol).slotCount
        val (viewportStart, viewportSpan) = viewportFor(q)
        val g = TimeLineCalculator.calculateSymmetric(
            q.timeline,
            pw,
            PRICE_HEIGHT,
            q.previousClose,
            slots = slots,
            lotSize = MarketTimelineSpec.forSymbol(q.symbol).lotSize,
        )
        sonarIndices().forEach { idx ->
            if (idx !in q.timeline.indices) return@forEach
            val sx = AXIS_LEFT + (idx - viewportStart) / viewportSpan.toFloat() * pw
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
        // 详情页控制条只广播意图。这个零尺寸响应式节点负责把意图施加到组件私有视窗，
        // 连续点击同一个按钮由 command.revision 区分（R1：读取只发生在 attr 内）。
        View {
            attr {
                width(0f); height(0f); opacity(0f); touchEnable(false)
                val command = viewportCommand()
                val currentQuote = quote()
                applyViewportCommand(command, currentQuote)
            }
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
            val phase = if (reduceMotion) 0f else motionPhase()
            val points = q.timeline
            val timelineSpec = MarketTimelineSpec.forSymbol(q.symbol)
            val slotCount = timelineSpec.slotCount
            val (viewportStart, viewportSpan) = viewportFor(q)
            val plotW = (width - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
            if (
                cachedTimeline !== points || cachedBaseline != q.previousClose ||
                cachedPlotWidth != plotW || cachedSlots != slotCount
            ) {
                cachedTimeline = points
                cachedBaseline = q.previousClose
                cachedPlotWidth = plotW
                cachedSlots = slotCount
                cachedGeometry = if (points.isEmpty() || q.previousClose <= 0.0) null
                else TimeLineCalculator.calculateSymmetric(
                    points,
                    plotW,
                    PRICE_HEIGHT,
                    q.previousClose,
                    slots = slotCount,
                    lotSize = timelineSpec.lotSize,
                )
            }
            val geometry = cachedGeometry
            val slotX: (Int) -> Float = { AXIS_LEFT + (it - viewportStart) / viewportSpan.toFloat() * plotW }

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
                // 竖网格线跟随该市场的时段标签（2026-09-11 修）：此前写死 A 股
                // 0/60/120/180/240，港股（0/90/150/240/330）网格与时间标签完全错位。
                // 只在 PlatformProfile.marketFixes（当前 iOS）生效，其余平台保持写死 A 股槽位。
                val gridSlots = if (PlatformProfile.marketFixes) {
                    timelineSpec.labels.map { it.first }.ifEmpty { listOf(0, 60, 120, 180, 240) }
                } else {
                    listOf(0, 60, 120, 180, 240)
                }
                gridSlots.forEach { slot ->
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

            // ── 昨收基准：仅在有真实分时点时绘制；空数据必须明确提示，不能伪装成横线。 ──
            val baselineY = PRICE_TOP + (geometry?.baselineY ?: PRICE_HEIGHT / 2f)
            if (geometry != null) {
                canvas.beginPath()
                canvas.moveTo(AXIS_LEFT, baselineY)
                canvas.lineTo(AXIS_LEFT + plotW, baselineY)
                canvas.setLineDash(listOf(4f, 3f))
                canvas.lineWidth(1f)
                canvas.strokeStyle(theme.textTertiary.opacity(0.75f))
                canvas.stroke()
                canvas.setLineDash(emptyList())
            } else {
                canvas.font(13f)
                canvas.fillStyle(theme.textTertiary)
                canvas.textAlign(TextAlign.CENTER)
                canvas.fillText("暂无分时数据", width / 2f, PRICE_TOP + PRICE_HEIGHT / 2f)
                canvas.textAlign(TextAlign.LEFT)
            }

            if (geometry != null) {
                val tone = if (q.rising) theme.rise else theme.fall
                val opposite = if (q.rising) theme.fall else theme.rise
                // 美股开盘早期或接口刚刷新时可能只有一个真实点。此前强制至少 2
                // 点会在 geometry.points[1] 越界，造成详情页卡退；单点应画为当前点，
                // 待下一条分时到达后自然连成线。
                val visible = if (progress >= 1f) points.size
                else (points.size * progress).roundToInt().coerceIn(1, points.size)

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
                val averages = averagesFor(q)
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
                val (flagsVol, maxVolume) = volumeMetricsFor(q)
                val barW = plotW / viewportSpan * 0.62f
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
                    // 对齐方向必须让文字留在画布内（2026-09-11 修）：此前按「锚点相对 x」
                    // 取反，导致贴左边缘的「低 xxx」用 RIGHT 对齐、半个字被裁掉
                    // （A 股 "1264.00"、港股 "419.40" 都实测被裁）。
                    // 只在 PlatformProfile.marketFixes（当前 iOS）生效，其余平台保留原对齐规则。
                    canvas.textAlign(
                        if (PlatformProfile.marketFixes) {
                            when {
                                anchorX <= AXIS_LEFT + 26f -> TextAlign.LEFT
                                anchorX >= AXIS_LEFT + plotW - 26f -> TextAlign.RIGHT
                                else -> TextAlign.CENTER
                            }
                        } else {
                            when {
                                anchorX > x -> TextAlign.RIGHT
                                anchorX < x -> TextAlign.LEFT
                                else -> TextAlign.CENTER
                            }
                        },
                    )
                    canvas.fillStyle(tone)
                    canvas.fillText(label, anchorX, y)
                }
                canvas.textAlign(TextAlign.LEFT)

                // ── now 点（盘中生长态）：外圈脉冲 + 实心点 ──
                if (points.size < slotCount) {
                    val lastX = slotX(points.size - 1)
                    val lastY = PRICE_TOP + geometry.points.last().y
                    if (!reduceMotion) {
                        val breath = (sin(phase * 2f * PI.toFloat()) + 1f) * 0.5f
                        canvas.beginPath()
                        canvas.arc(lastX, lastY, 5.5f + breath * 3f, 0f, (2 * PI).toFloat(), false)
                        canvas.lineWidth(1.8f)
                        canvas.strokeStyle(tone.opacity(0.14f + breath * 0.20f))
                        canvas.stroke()
                    }
                    canvas.beginPath()
                    canvas.arc(lastX, lastY, 2.8f, 0f, (2 * PI).toFloat(), false)
                    canvas.fillStyle(tone)
                    canvas.fill()
                }

                // ── ④ 异动声呐点：brand 实心点 + 呼吸圆环（复用 pulse 驱动；selected 白心蓝边）──
                // 漂移：整颗气泡（环+点）绕锚点沿 X 轴小幅往返流动（不同 index 相位错开），
                // 呼吸动效不变；reduceMotion 下相位恒 0，原地呼吸。
                val sonar = sonarIndices()
                if (sonar.isNotEmpty()) {
                    val selSonar = selectedSonarIndex()
                    val driftPhase = phase
                    sonar.forEach { idx ->
                        if (idx !in points.indices) return@forEach
                        val wobble = sin(driftPhase * 2f * PI.toFloat() + idx * 1.9f) * 12f
                        val sx = slotX(idx) + wobble
                        val sy = PRICE_TOP + geometry.points[idx].y
                        if (!reduceMotion) {
                            val breath = (sin(driftPhase * 2f * PI.toFloat() + idx * 1.9f) + 1f) * 0.5f
                            canvas.beginPath()
                            canvas.arc(sx, sy, 6f + breath * 3.5f, 0f, (2 * PI).toFloat(), false)
                            canvas.lineWidth(1.6f)
                            canvas.strokeStyle(theme.brand.opacity(0.16f + breath * 0.25f))
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
                timelineSpec.labels.forEachIndexed { i, (slot, label) ->
                    val x = slotX(slot)
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

        // 十字线单独成层：拖动时只清绘这一层，不重复绘制约 240 个价格点、量能柱、
        // 均价线与旗标。视觉位置直接读组件本地状态，保持与 touchMove 同拍。
        Canvas({
            attr {
                absolutePositionAllZero()
                height(CHART_HEIGHT)
                touchEnable(false)
            }
        }) { canvas, width, _ ->
            val q = quote()
            val scrubIndex = state.scrubIndex
            val selecting = state.selecting
            val selectRange = state.selectRange
            val points = q.timeline
            if (scrubIndex !in points.indices || q.previousClose <= 0.0 ||
                (selecting && selectRange.first != selectRange.second)
            ) return@Canvas

            val timelineSpec = MarketTimelineSpec.forSymbol(q.symbol)
            val plotW = (width - AXIS_LEFT - AXIS_RIGHT).coerceAtLeast(1f)
            val geometry = cachedGeometry ?: TimeLineCalculator.calculateSymmetric(
                points,
                plotW,
                PRICE_HEIGHT,
                q.previousClose,
                slots = timelineSpec.slotCount,
                lotSize = timelineSpec.lotSize,
            )
            val (viewportStart, viewportSpan) = viewportFor(q)
            val x = AXIS_LEFT + (scrubIndex - viewportStart) / viewportSpan.toFloat() * plotW
            val priceY = PRICE_TOP + geometry.points[scrubIndex].y
            val avgY = PRICE_TOP + geometry.yFor(averagesFor(q)[scrubIndex])
            val tone = if (q.rising) theme.rise else theme.fall

            canvas.beginPath()
            canvas.moveTo(x, PRICE_TOP)
            canvas.lineTo(x, VOL_TOP + VOL_HEIGHT)
            canvas.setLineDash(listOf(3f, 3f))
            canvas.lineWidth(0.8f)
            canvas.strokeStyle(theme.textTertiary)
            canvas.stroke()
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
            val pct = (points[scrubIndex].price - q.previousClose) / q.previousClose * 100.0
            canvas.font(9f)
            canvas.fillStyle(tone)
            canvas.textAlign(TextAlign.LEFT)
            canvas.fillText(Format.price(points[scrubIndex].price), 3f, priceY + 3f)
            canvas.textAlign(TextAlign.RIGHT)
            canvas.fillText(axisPercent(pct), width - 3f, priceY + 3f)
            canvas.textAlign(TextAlign.LEFT)
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
                    val (viewportStart, viewportSpan) = viewportFor(q)
                    return (viewportStart + (x - AXIS_LEFT) / plotW * viewportSpan)
                        .roundToInt().coerceIn(0, q.timeline.size - 1)
                }

                touchDown { e ->
                    downX = e.x
                    downY = e.y
                    movedDist = 0f
                    scrollingIntent = false
                    gestureDone = false
                    scrubbing = false
                    selectingCircle = false
                    // 新手势废弃上一次尚未执行的帧合并回调；首个十字线位置必须
                    // 立即同步，确保重新长按同一槽位也会清掉旧预填。
                    scrubDispatchVersion++
                    scrubDispatchScheduled = false
                    pendingScrubIndex = -1
                    lastScrubIndex = -1
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
                                dispatchScrub(slot, immediately = true)
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
                    // ① 圈选可靠性（2026-09-10）：横向主导的位移一旦出现（哪怕未到
                    // 12dp slop）立即作废 280ms 短长按计时——否则慢启动的圈选拖动会在
                    // 280ms 被定时器收编成十字线 scrub（此后横向跟随手指 x），
                    // 圈选永远进不去，表现为「不是每次都能圈出解读」。
                    // 互斥语义不变：纵向滚动意图仍由下方分支最高优先处理。
                    if (!scrubbing && !selectingCircle &&
                        abs(dx) > CIRCLE_INTENT_DX && abs(dx) > abs(dy) * 1.5f
                    ) {
                        lpGen++
                    }
                    if (scrubbing) {
                        // scrub 态：十字线跟随手指 x（页面滚动已由 onScrubActive 锁定）
                        val slot = slotAt(e.x)
                        if (slot >= 0) {
                            dispatchScrub(slot)
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
                        flushScrub()
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
                        state.scrubIndex = -1
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
                        flushScrub()
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
private const val CIRCLE_INTENT_DX = 4f         // ① 横向意图早判：未到圈选阈值但横向主导即作废短长按（防 scrub 收编圈选）
private const val PRICE_TOP = 0f
private const val PRICE_HEIGHT = 290f
private const val VOL_TOP = 296f
private const val VOL_HEIGHT = 70f
private const val AXIS_LEFT = 0f
private const val AXIS_RIGHT = 0f
private const val TIMELINE_MIN_SPAN = 36

/**
 * DetailTimelineChart 内部交互状态容器：把需要驱动 Canvas 重绘的响应式字段
 * 收敛到一个小类里（与 StockDetailPage 的 `by observable(...)` 同来源、同形态，
 * 确保编译期与运行时表现一致）。十字线视觉位置、①圈选态/预览带、B2 旗标下落进度需要响应式。
 */
private class ChartInteractionState {
    var scrubIndex by observable(-1)                         // 十字线视觉位置：事件到达即更新
    var selecting by observable(false)                       // ① 是否处于圈选态
    var selectRange by observable(Pair(-1, -1))              // ① 圈选起止 index（预览带）
    var flagDropProgress by observable(emptyMap<Int, Float>()) // B2 旗标下落进度（index→0..1）
    var viewportStart by observable(0)
    var viewportSpan by observable(0)
    var lastViewportCommand by observable(0)
}
