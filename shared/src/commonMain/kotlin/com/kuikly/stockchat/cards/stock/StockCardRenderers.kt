package com.kuikly.stockchat.cards.stock

import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled

import com.kuikly.stockchat.cards.component.CardShell
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CompareCalculator
import com.kuikly.stockchat.cards.core.CardRegistry
import com.kuikly.stockchat.cards.core.CardRenderer
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.ExplanationDepth
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.NewsItem
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.chart.model.KLineCalculator
import com.kuikly.stockchat.chart.model.ChartViewportAction
import com.kuikly.stockchat.chart.model.ChartViewportCommand
import com.kuikly.stockchat.common.Format
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.TouchParams
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
import kotlin.math.sqrt

object StockCardRenderers {
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true
        CardRegistry.register(StockQuoteCardRenderer)
        CardRegistry.register(StockChartCardRenderer)
        CardRegistry.register(AttributionCardRenderer)
        CardRegistry.register(DefinitionCardRenderer)
        CardRegistry.register(InsightCardRenderer)
        CardRegistry.register(StockCompareCardRenderer)
        CardRegistry.register(NewsCardRenderer)
    }
}

object StockQuoteCardRenderer : CardRenderer {
    override val cardType: String = "stock-quote"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? StockQuoteCardModel ?: return
        val quote = model.quote
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text(quote.name); fontSizeScaled(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(quote.symbol); marginTop(1f); fontSizeScaled(10f); color(theme.textTertiary) } }
                }
                View {
                    attr { alignItemsFlexEnd() }
                    Text { attr { text(Format.price(quote.price)); fontSizeScaled(18f); fontWeightBold(); color(if (quote.rising) theme.rise else theme.fall) } }
                    Text { attr { text("${if (quote.rising) "▲" else "▼"} ${Format.percent(quote.changePercent)}"); marginTop(1f); fontSizeScaled(11f); color(if (quote.rising) theme.rise else theme.fall) } }
                }
            }
            MiniTimeline(container, model, context, height = 42f)
            container.Text { attr { text("${quote.source} · ${quote.timestamp}"); marginTop(4f); fontSizeScaled(9f); color(theme.textTertiary) } }
            if (context.cardClickable) {
                container.event { click { context.onOpenStock(quote.symbol) } }
            }
            return
        }
        container.View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr { flex(1f) }
                Text { attr { text(quote.name); fontSizeScaled(16f); fontWeightSemiBold(); color(theme.textPrimary) } }
                Text { attr { text(quote.symbol); marginTop(2f); fontSizeScaled(11f); color(theme.textTertiary) } }
            }
            View {
                attr { alignItemsFlexEnd() }
                Text {
                    attr {
                        text(Format.price(quote.price))
                        fontSizeScaled(28f)
                        fontWeightBold()
                        color(if (quote.rising) theme.rise else theme.fall)
                    }
                }
                Text {
                    attr {
                        text("${if (quote.rising) "▲" else "▼"} ${Format.percent(quote.changePercent)}")
                        marginTop(2f)
                        fontSizeScaled(12f)
                        color(if (quote.rising) theme.rise else theme.fall)
                    }
                }
            }
            event {
                click {
                    if (context.compareCandidateSymbol.isNotEmpty() && context.compareCandidateSymbol != quote.symbol) {
                        context.onCompareCandidate?.invoke(context.cardKey, quote.symbol)
                    } else {
                        context.onOpenStock(quote.symbol)
                    }
                }
            }
        }
        if (context.density == CardDensity.COMPACT) {
            container.View {
                attr { flexDirectionRow(); marginTop(10f) }
                Metric("今开", Format.price(quote.open), theme, this)
                Metric("最高", Format.price(quote.high), theme, this)
                Metric("最低", Format.price(quote.low), theme, this)
                Metric("成交量", Format.compactAmount(quote.volume), theme, this)
            }
            container.Text {
                attr {
                    text("展开后查看分时走势、最高最低和行情来源")
                    marginTop(8f)
                    fontSizeScaled(10f)
                    color(theme.textTertiary)
                }
            }
            return
        }
        container.Text {
            attr {
                text("分时走势 · 虚线为昨收基准与均价，红绿为量能")
                marginTop(12f)
                fontSizeScaled(10f)
                color(theme.textTertiary)
            }
        }
        KuiklyTimelineChart(container, model.quote, context, height = 128f)
        container.View {
            attr { flexDirectionRow(); marginTop(10f) }
            Metric("今开", Format.price(quote.open), theme, this)
            Metric("最高", Format.price(quote.high), theme, this)
            Metric("最低", Format.price(quote.low), theme, this)
            Metric("成交额", Format.compactAmount(quote.amount), theme, this)
        }
        container.Text {
            attr {
                text("行情来源：${quote.source} · 更新于 ${quote.timestamp}，数据可能存在延迟")
                marginTop(10f)
                fontSizeScaled(10f)
                color(theme.textTertiary)
            }
        }
    }
}

object StockChartCardRenderer : CardRenderer {
    override val cardType: String = "stock-chart"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? StockChartCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text(model.quote.name); fontSizeScaled(13f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(if (model.mode == StockChartMode.TIMELINE) "分时走势" else model.period.label); marginTop(1f); fontSizeScaled(9f); color(theme.textTertiary) } }
                }
                Text { attr { text(Format.percent(model.quote.changePercent)); fontSizeScaled(12f); color(if (model.quote.rising) theme.rise else theme.fall) } }
            }
            if (model.mode == StockChartMode.TIMELINE) MiniTimeline(container, StockQuoteCardModel(model.quote), context, height = 34f)
            if (context.cardClickable) {
                container.event { click { context.onOpenStock(model.quote.symbol) } }
            }
            return
        }
        container.View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr { flex(1f) }
                Text {
                    attr { text("${model.quote.name} ${if (model.mode == StockChartMode.TIMELINE) "分时走势" else "${model.period.label}走势"}"); fontSizeScaled(15f); fontWeightSemiBold(); color(theme.textPrimary) }
                }
                Text {
                    attr { text("${Format.price(model.quote.price)} · ${Format.percent(model.quote.changePercent)}"); marginTop(2f); fontSizeScaled(10f); color(if (model.quote.rising) theme.rise else theme.fall) }
                }
            }
            event { click { context.onOpenStock(model.quote.symbol) } }
        }
        if (context.density == CardDensity.COMPACT) {
            if (model.mode == StockChartMode.TIMELINE) MiniTimeline(container, StockQuoteCardModel(model.quote), context, height = 62f)
            container.Text {
                attr { text("展开后查看更大图表、基准说明和完整细节"); marginTop(6f); fontSizeScaled(10f); color(theme.textTertiary) }
            }
            return
        }
        if (model.mode == StockChartMode.TIMELINE) KuiklyTimelineChart(container, model.quote, context, height = 132f)
        else KLineChart(container, model, context)
        container.Text {
            attr { text(if (model.mode == StockChartMode.TIMELINE) "虚线为昨收基准与均价，红绿为量能" else "显示 MA5 / MA10 / MA20 与红绿量能，点按可查看某日数据"); marginTop(6f); fontSizeScaled(10f); color(theme.textTertiary) }
        }
    }
}

/**
 * 日/周/月 K 线图 —— Canvas 自绘（2026-09-09 对齐分时图视觉语言）+ 可视窗口缩放拖动
 * （2026-09-10 对齐主流行情 App 交互，用户要求）：
 *
 * 数据模型：全量 K 线进内存（腾讯日K 240 根 / 周K 120 / 月K 60），图表只画
 * [KLineChartState.windowStart, windowStart + windowCount) 窗口内的蜡烛。
 * 默认窗口 = 最近 60/52/48 根（与旧版 takeLast 截断观感一致），缩小（捏开）可见
 * 全量历史，放大（捏合）最少 K_MIN_VISIBLE 根；价量纵轴按窗口内极值自适应重定标。
 *
 * 手势（仅详情页交互态 onSelectIndex != null；聊天卡片态只保留点按选中）：
 *  - 双指捏合：两指间距驱动窗口根数，锚定捏合中点所在蜡烛不随缩放漂移；
 *    捏合期间回调 onZoomActive(true) 锁外层 Scroller（两指会被原生滚动接管，
 *    分时图 onScrubActive 同款机制），松一指/松手恢复；
 *  - 单指拖动：先以 6dp 阈值仲裁方向；横向意图立即锁定外层 Scroller 后十字线
 *    跟随手指，竖向意图交还页面。这样小幅纵向抖动不会打断正在看的十字线；
 *    拖出窗口边缘时窗口整体跟随平移（带图滚动）。
 *  - touch 流挂同尺寸手势覆盖层 View（Canvas 本体 touchEnable(false)，
 *    DetailTimelineChart 同款范式；多指按 TouchParams.touches/pointerId 同步）。
 *
 * 绘制：真蜡烛身（涨红/跌绿实心 + 影线）、红绿量能副图、MA5/10/20、轻网格、
 * 高/低价位角标（窗口极值）、十字线（竖虚线贯穿价量两区 + 收价水平虚线 +
 * 收价空心点 + 端边读数）与三行随值数据行，全部随十字线/窗口反应式刷新（R1）。
 */
internal fun KLineChart(
    container: ViewContainer<*, *>,
    model: StockChartCardModel,
    context: CardContext,
    selectedIndex: () -> Int = { -1 },
    onSelectIndex: ((Int) -> Unit)? = null,
    chartHeight: Float = K_COMPACT_CHART_HEIGHT,
    onZoomActive: ((Boolean) -> Unit)? = null,
    onCrosshairActive: ((Boolean) -> Unit)? = null,
    viewportCommand: () -> ChartViewportCommand = { ChartViewportCommand() },
) {
    val sourceLines = when (model.period) {
        StockChartPeriod.DAY -> model.quote.kLines
        StockChartPeriod.WEEK -> model.quote.weekKLines.ifEmpty { KLineCalculator.aggregate(model.quote.kLines, model.period.grouping) }
        StockChartPeriod.MONTH -> model.quote.monthKLines.ifEmpty { KLineCalculator.aggregate(model.quote.kLines, model.period.grouping) }
    }
    // 默认可视根数（旧版 takeLast 截断档位，初始观感不变）
    val defaultVisible = when (model.period) {
        StockChartPeriod.DAY -> 60
        StockChartPeriod.WEEK -> 52
        StockChartPeriod.MONTH -> 48
    }
    val lines = sourceLines
    val allMovingAverages = listOf(5, 10, 20).associateWith { period ->
        // 全长 MA（与 lines 同下标对齐），窗口只裁绘不裁数据
        KLineCalculator.movingAverage(sourceLines, period)
    }
    val theme = context.theme
    if (lines.isEmpty()) {
        container.View {
            attr {
                height(chartHeight)
                marginTop(10f)
                alignSelfStretch()
                borderRadius(12f)
                backgroundColor(theme.surfaceMuted)
                allCenter()
            }
            Text {
                attr {
                    text("K线数据正在加载")
                    fontSizeScaled(11f)
                    color(theme.textTertiary)
                }
            }
        }
        return
    }

    // 选中态：外部传入（详情页，lambda 在反应式闭包内调用）优先；卡片态读内部 observable（R1）。
    val state = KLineChartState()
    state.windowCount = min(defaultVisible, lines.size)
    state.windowStart = (lines.size - state.windowCount).coerceAtLeast(0)

    fun effectiveSel(): Int =
        (if (onSelectIndex != null) state.dragSelected.takeIf { it >= 0 } ?: selectedIndex() else state.selected)
            .takeIf { it in lines.indices } ?: lines.lastIndex

    /** 对前一根收盘的涨跌幅（首根对自身开盘价），随值行与端边读数共用。 */
    fun pctOf(i: Int): Double {
        val prev = if (i > 0) lines[i - 1].close else lines[i].open
        return (lines[i].close - prev) / prev * 100.0
    }

    fun toneOf(i: Int) = if (lines[i].close >= lines[i].open) theme.rise else theme.fall

    var measuredWidth = 0f

    // ── 手势几何（仅详情页交互态；覆盖层 event 闭包内调用）──
    val pointers = HashMap<Long, Pair<Float, Float>>()
    var pinchBaseDist = 0f
    var pinchBaseCount = 0
    var pinchAnchorX = 0f
    var pinchAnchorIdx = 0
    var crosshairDragging = false
    var zooming = false
    var downX = 0f
    var downY = 0f
    /** 0 = wait, 1 = chart crosshair, 2 = vertical page scroll. */
    var singleFingerAxis = 0
    var gestureDone = false
    // 视觉十字线只写组件本地 observable；详情页头部/指标板的外部选中态至多每帧
    // 同步一次，避免高频 touchMove 让整页的 vbind 在同一帧内重复计算。
    var pendingSelection = -1
    var selectionDispatchScheduled = false
    var selectionDispatchVersion = 0
    var lastDeliveredSelection = -1

    fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    /** 多指同步：touches（全量当前触点）合并进表，再加本次回调主触点。 */
    fun mergePointers(e: TouchParams) {
        e.touches.forEach { t -> pointers[t.pointerId] = t.x to t.y }
        pointers[e.pointerId.toLong()] = e.x to e.y
    }

    fun beginPinch() {
        val pts = pointers.values.toList()
        if (pts.size < 2) return
        val w = measuredWidth.coerceAtLeast(1f)
        pinchBaseDist = dist(pts[0], pts[1]).coerceAtLeast(1f)
        pinchBaseCount = state.windowCount
        pinchAnchorX = (pts[0].first + pts[1].first) / 2f
        pinchAnchorIdx = state.windowStart +
            (pinchAnchorX / w * pinchBaseCount).roundToInt().coerceIn(0, state.windowCount - 1)
        zooming = true
        onZoomActive?.invoke(true)
    }

    /** 间距比 → 窗口根数；锚定蜡烛保持原 x 位置（newStart = anchorIdx - frac·newCount）。 */
    fun applyPinch() {
        if (!zooming || pinchBaseDist <= 0f) return
        val pts = pointers.values.toList()
        if (pts.size < 2) return
        val ratio = dist(pts[0], pts[1]) / pinchBaseDist
        val newCount = (pinchBaseCount / ratio).roundToInt()
            .coerceIn(min(K_MIN_VISIBLE, lines.size), lines.size)
        if (newCount == state.windowCount) return
        val w = measuredWidth.coerceAtLeast(1f)
        state.windowStart = (pinchAnchorIdx - pinchAnchorX / w * newCount)
            .roundToInt().coerceIn(0, lines.size - newCount)
        state.windowCount = newCount
    }

    fun endZoom() {
        if (zooming) {
            zooming = false
            onZoomActive?.invoke(false)
        }
        pinchBaseDist = 0f
    }

    fun dispatchSelection(index: Int, immediately: Boolean = false) {
        if (state.dragSelected != index) state.dragSelected = index
        if (onSelectIndex == null) return
        if (immediately) {
            selectionDispatchVersion++
            selectionDispatchScheduled = false
            pendingSelection = -1
            if (index != lastDeliveredSelection) {
                lastDeliveredSelection = index
                onSelectIndex.invoke(index)
            }
            return
        }
        pendingSelection = index
        if (selectionDispatchScheduled) return
        selectionDispatchScheduled = true
        val version = selectionDispatchVersion
        setTimeout(16) {
            if (version != selectionDispatchVersion) return@setTimeout
            selectionDispatchScheduled = false
            val next = pendingSelection
            pendingSelection = -1
            if (next >= 0 && next != lastDeliveredSelection) {
                lastDeliveredSelection = next
                onSelectIndex.invoke(next)
            }
        }
    }

    fun flushSelection() {
        val next = pendingSelection
        pendingSelection = -1
        selectionDispatchVersion++
        selectionDispatchScheduled = false
        if (next >= 0 && next != lastDeliveredSelection) {
            lastDeliveredSelection = next
            onSelectIndex?.invoke(next)
        }
    }

    /** 十字线跟随手指；拖出窗口边缘则窗口整体平移（带图滚动，主流 App 手感）。 */
    fun followCrosshair(x: Float) {
        val raw = (state.windowStart +
            (x / measuredWidth.coerceAtLeast(1f) * state.windowCount).toInt())
            .coerceIn(0, lines.lastIndex)
        val end = state.windowStart + state.windowCount - 1
        when {
            raw < state.windowStart -> state.windowStart = raw
            raw > end -> state.windowStart = (raw - state.windowCount + 1).coerceAtLeast(0)
        }
        dispatchSelection(raw)
    }

    fun applyViewportCommand(command: ChartViewportCommand) {
        if (command.revision == state.lastViewportCommand) return
        state.lastViewportCommand = command.revision
        val oldCount = state.windowCount.coerceIn(1, lines.size)
        val minCount = min(K_MIN_VISIBLE, lines.size)
        val newCount = when (command.action) {
            ChartViewportAction.ZOOM_IN -> (oldCount * 0.70f).roundToInt().coerceIn(minCount, lines.size)
            ChartViewportAction.ZOOM_OUT -> (oldCount / 0.70f).roundToInt().coerceIn(minCount, lines.size)
            ChartViewportAction.RESET -> min(defaultVisible, lines.size)
            else -> oldCount
        }
        val newStart = when (command.action) {
            ChartViewportAction.PAN_LEFT -> (state.windowStart - (oldCount * 0.22f).roundToInt()).coerceAtLeast(0)
            ChartViewportAction.PAN_RIGHT -> (state.windowStart + (oldCount * 0.22f).roundToInt())
                .coerceAtMost((lines.size - oldCount).coerceAtLeast(0))
            ChartViewportAction.ZOOM_IN, ChartViewportAction.ZOOM_OUT ->
                (state.windowStart + oldCount / 2 - newCount / 2).coerceIn(0, (lines.size - newCount).coerceAtLeast(0))
            ChartViewportAction.RESET -> (lines.size - newCount).coerceAtLeast(0)
            ChartViewportAction.NONE -> state.windowStart
        }
        state.windowStart = newStart
        state.windowCount = newCount
    }

    container.View {
        attr {
            height(chartHeight)
            marginTop(10f)
            alignSelfStretch()
        }
        // 详情页控制条通过 revisioned command 驱动这个私有视窗；普通卡片保持默认 NOOP。
        View {
            attr {
                width(0f); height(0f); opacity(0f); touchEnable(false)
                applyViewportCommand(viewportCommand())
            }
        }
        // Canvas 只负责绘制：touch 事件在 GroupEvent 上，Canvas 本体不吃触摸，
        // 手势统一由上方同尺寸覆盖层承担（DetailTimelineChart 同款范式）。
        Canvas({
            attr {
                absolutePositionAllZero()
                height(chartHeight)
                touchEnable(false)
            }
        }) { canvas, width, height ->
            measuredWidth = width
            if (lines.isEmpty() || width <= 0f) return@Canvas
            // 图表可在详情页扩展至与分时图同高；所有价量坐标按当前 Canvas 高度同比放大。
            val heightScale = height / K_COMPACT_CHART_HEIGHT
            val priceHeight = K_COMPACT_PRICE_H * heightScale
            val volumeTop = K_COMPACT_VOL_TOP * heightScale
            val volumeHeight = K_COMPACT_VOL_H * heightScale
            // 可视窗口（R1：windowStart/windowCount 是 observable，捏合/拖动改写即重绘）
            val count = state.windowCount.coerceIn(1, lines.size)
            val start = state.windowStart.coerceIn(0, lines.size - count)
            val endIdx = start + count - 1
            val step = width / count
            fun xOf(i: Int) = (i - start) * step + step / 2f
            val bodyW = (step * 0.66f).coerceIn(1.2f, 9f)
            // 价量纵轴按可视窗口内极值自适应（主流 App 口径：缩放后重定标）
            val window = lines.subList(start, endIdx + 1)
            val low = window.minOf { it.low }
            val high = window.maxOf { it.high }
            val range = (high - low).coerceAtLeast(0.0001)
            fun y(value: Double) = ((high - value) / range * (priceHeight - 8f * heightScale) + 4f * heightScale).toFloat()

            // ── 轻网格：价格区 25/50/75% 三条横线（0.5f，与分时图同款）──
            canvas.lineWidth(0.5f)
            canvas.strokeStyle(theme.divider)
            listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                val gy = priceHeight * ratio
                canvas.beginPath()
                canvas.moveTo(0f, gy)
                canvas.lineTo(width, gy)
                canvas.stroke()
            }

            // ── 蜡烛：影线 1f + 实心蜡烛身（涨红/跌绿；十字星以 1.2f 最小高度呈现）──
            for (index in start..endIdx) {
                val line = lines[index]
                val cx = xOf(index)
                val color = if (line.close >= line.open) theme.rise else theme.fall
                canvas.beginPath()
                canvas.moveTo(cx, y(line.high))
                canvas.lineTo(cx, y(line.low))
                canvas.strokeStyle(color)
                canvas.lineWidth(1f)
                canvas.stroke()
                val top = minOf(y(line.open), y(line.close))
                val bodyH = (maxOf(y(line.open), y(line.close)) - top).coerceAtLeast(1.2f)
                canvas.beginPath()
                canvas.moveTo(cx - bodyW / 2f, top)
                canvas.lineTo(cx + bodyW / 2f, top)
                canvas.lineTo(cx + bodyW / 2f, top + bodyH)
                canvas.lineTo(cx - bodyW / 2f, top + bodyH)
                canvas.closePath()
                canvas.fillStyle(color)
                canvas.fill()
            }

            // ── 量能副图：红绿量能条（收≥开红，否则绿）──
            val maxVol = window.maxOf { it.volume }.coerceAtLeast(1.0)
            for (index in start..endIdx) {
                val line = lines[index]
                val h = (line.volume / maxVol * volumeHeight).toFloat().coerceIn(1f, volumeHeight)
                val bx = xOf(index) - bodyW / 2f
                val by = volumeTop + volumeHeight - h
                canvas.beginPath()
                canvas.moveTo(bx, by)
                canvas.lineTo(bx + bodyW, by)
                canvas.lineTo(bx + bodyW, by + h)
                canvas.lineTo(bx, by + h)
                canvas.closePath()
                canvas.fillStyle(if (line.close >= line.open) theme.rise.opacity(0.62f) else theme.fall.opacity(0.62f))
                canvas.fill()
            }

            // ── MA5 / MA10 / MA20（全长序列，只绘窗口段）──
            listOf(5 to theme.brand, 10 to theme.textSecondary, 20 to theme.textTertiary).forEach { (period, color) ->
                val values = allMovingAverages.getValue(period)
                canvas.beginPath()
                var started = false
                for (index in start..endIdx) {
                    val value = values.getOrNull(index)
                    if (value != null) {
                        val mx = xOf(index)
                        if (!started) { canvas.moveTo(mx, y(value)); started = true } else canvas.lineTo(mx, y(value))
                    }
                }
                if (started) { canvas.strokeStyle(color); canvas.lineWidth(1.3f); canvas.stroke() }
            }

            // ── 高/低价位角标（可视窗口极值，看数据不点也能读）──
            canvas.font(9f)
            canvas.fillStyle(theme.textTertiary)
            canvas.fillText("高 ${Format.price(high)}", 2f, 11f * heightScale)
            canvas.fillText("低 ${Format.price(low)}", 2f, priceHeight - 5f * heightScale)

            // ── 十字线：竖虚线贯穿价量两区 + 收价水平虚线 + 收价空心点 + 右端读数 ──
            // 选中蜡烛被缩放移出窗口时不绘（随值行仍显示其数值）。
            val sel = effectiveSel()
            if (sel in start..endIdx) {
                val selLine = lines[sel]
                val cx = xOf(sel)
                val closeY = y(selLine.close)
                val tone = toneOf(sel)
                canvas.setLineDash(listOf(4f, 5f))
                canvas.beginPath()
                canvas.moveTo(cx, 0f)
                canvas.lineTo(cx, volumeTop + volumeHeight)
                canvas.strokeStyle(theme.textTertiary.opacity(0.5f))
                canvas.lineWidth(0.8f)
                canvas.stroke()
                canvas.beginPath()
                canvas.moveTo(0f, closeY)
                canvas.lineTo(width, closeY)
                canvas.strokeStyle(theme.textTertiary.opacity(0.35f))
                canvas.lineWidth(1f)
                canvas.stroke()
                canvas.setLineDash(emptyList())
                canvas.beginPath()
                canvas.arc(cx, closeY, 3f, 0f, (2 * PI).toFloat(), false)
                canvas.fillStyle(theme.marketGlass)
                canvas.fill()
                canvas.lineWidth(1.4f)
                canvas.strokeStyle(tone)
                canvas.stroke()
                // 端边读数：收价 + 对前收涨跌幅，贴右缘；贴近顶部时改放线下方防裁切
                canvas.font(9f)
                canvas.fillStyle(tone)
                canvas.textAlign(TextAlign.RIGHT)
                val readY = if (closeY < 20f) closeY + 13f else closeY - 5f
                canvas.fillText("${Format.price(selLine.close)} ${Format.percent(pctOf(sel))}", width - 3f, readY)
                canvas.textAlign(TextAlign.LEFT)
            }
        }
        // ── 手势覆盖层（详情页：捏合缩放 + 拖动十字线；卡片态：点按选中）──
        View {
            attr {
                absolutePositionAllZero()
                height(chartHeight)
                touchEnable(true)
            }
            event {
                if (onSelectIndex != null) {
                    touchDown { e ->
                        mergePointers(e)
                        gestureDone = false
                        selectionDispatchVersion++
                        selectionDispatchScheduled = false
                        pendingSelection = -1
                        if (pointers.size >= 2 && lines.size > K_MIN_VISIBLE) {
                            if (crosshairDragging) onCrosshairActive?.invoke(false)
                            crosshairDragging = false
                            singleFingerAxis = 1
                            beginPinch()
                        } else {
                            // 不在 DOWN 就抢父滚动：先等方向明确，普通页面上下滑不受影响。
                            downX = e.x
                            downY = e.y
                            singleFingerAxis = 0
                            crosshairDragging = false
                        }
                    }
                    touchMove { e ->
                        if (gestureDone) return@touchMove
                        mergePointers(e)
                        if (zooming) {
                            applyPinch()
                        } else if (pointers.size == 1) {
                            val point = pointers.values.first()
                            if (singleFingerAxis == 0) {
                                val dx = point.first - downX
                                val dy = point.second - downY
                                if (max(abs(dx), abs(dy)) <= K_GESTURE_AXIS_SLOP) return@touchMove
                                // 打平归竖向：页面的自然滚动优先，减少想滚页时点亮十字线。
                                singleFingerAxis = if (abs(dx) > abs(dy)) 1 else 2
                                if (singleFingerAxis == 1) {
                                    crosshairDragging = true
                                    onCrosshairActive?.invoke(true)
                                }
                            }
                            if (crosshairDragging) followCrosshair(point.first)
                        }
                    }
                    touchUp { _ ->
                        if (gestureDone) return@touchUp
                        gestureDone = true
                        // 保留原有轻点选中语义；仅方向明确的纵向手势交给页面。
                        if (!zooming && singleFingerAxis == 0) followCrosshair(downX)
                        flushSelection()
                        // 单指抬起即结束本轮仲裁；清表防止余指回调遗留为下一轮的幽灵指针。
                        pointers.clear()
                        endZoom()
                        if (crosshairDragging) onCrosshairActive?.invoke(false)
                        crosshairDragging = false
                        singleFingerAxis = 0
                    }
                    touchCancel { _ ->
                        if (gestureDone) return@touchCancel
                        gestureDone = true
                        pointers.clear()
                        flushSelection()
                        if (crosshairDragging) onCrosshairActive?.invoke(false)
                        crosshairDragging = false
                        singleFingerAxis = 0
                        endZoom()
                    }
                } else {
                    // 卡片态：点按选中，再点同根回到最新（-1 → effectiveSel 回落到 lastIndex）
                    click { params ->
                        val idx = (state.windowStart +
                            (params.x / measuredWidth.coerceAtLeast(1f) * state.windowCount).toInt())
                            .coerceIn(0, lines.lastIndex)
                        state.selected = if (state.selected == idx) -1 else idx
                    }
                }
            }
        }
    }
    // ── 首尾日期行（随可视窗口，attr 内读窗口 observable，R1）──
    container.View {
        attr { marginTop(5f); flexDirectionRow() }
        Text {
            attr {
                text(lines[state.windowStart.coerceIn(0, lines.lastIndex)].date)
                fontSizeScaled(9f)
                color(theme.textTertiary)
                flex(1f)
            }
        }
        Text {
            attr {
                text(lines[(state.windowStart + state.windowCount - 1).coerceIn(0, lines.lastIndex)].date)
                fontSizeScaled(9f)
                color(theme.textTertiary)
                textAlignRight()
            }
        }
    }
    // ── 只保留均线参考值；OHLC/涨跌/量额已统一显示在详情页顶部 ──
    container.View {
        attr { marginTop(4f); flexDirectionRow() }
        listOf(5 to theme.brand, 10 to theme.textSecondary, 20 to theme.textTertiary).forEach { (period, color) ->
            Text {
                attr {
                    val v = allMovingAverages.getValue(period)[effectiveSel()]
                    text("MA$period ${v?.let { Format.price(it) } ?: "--"}")
                    fontSizeScaled(9f)
                    color(color)
                    flex(1f)
                }
            }
        }
    }
}

/**
 * K 线图交互状态（R1 响应式字段）：
 *  - selected：卡片态选中（-1 = 未选中，跟随最新一根）；
 *  - windowCount/windowStart：可视窗口（捏合/拖动改写 → draw 与随值行 attr 重跑）。
 */
private class KLineChartState {
    var selected by observable(-1)
    /** 详情页拖动中的本地十字线位置；使绘制不必等待页面级状态回写。 */
    var dragSelected by observable(-1)
    var windowCount by observable(0)
    var windowStart by observable(0)
    var lastViewportCommand by observable(0)
}

/** 捏合放大后最少可见蜡烛根数（再少单根过宽，失真）。 */
private const val K_MIN_VISIBLE = 20
private const val K_GESTURE_AXIS_SLOP = 6f

private const val K_COMPACT_CHART_HEIGHT = 168f
private const val K_COMPACT_PRICE_H = 112f
private const val K_COMPACT_VOL_TOP = 122f
private const val K_COMPACT_VOL_H = 38f

object AttributionCardRenderer : CardRenderer {
    override val cardType: String = "attribution"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? AttributionCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            val top = model.factors.maxByOrNull { it.weight }
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text("归因：${top?.name ?: "暂无"}"); fontSizeScaled(13f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(top?.description ?: "等待更多数据确认"); marginTop(2f); fontSizeScaled(10f); color(theme.textTertiary) } }
                }
                Text { attr { text(top?.let { "${Format.decimal(it.weight * 100, 0)}%" } ?: "--"); fontSizeScaled(12f); color(theme.brand) } }
            }
            return
        }
        container.Text {
            attr { text("为什么${if (model.quote.rising) "涨" else "跌"}"); fontSizeScaled(16f); fontWeightSemiBold(); color(theme.textPrimary) }
        }
        val factors = if (context.density == CardDensity.COMPACT) model.factors.take(2) else model.factors
        factors.forEach { factor ->
            val drillKey = "${model.cardId}:${factor.name}"
            val drilled = drillKey in context.drilledKeys
            container.View {
                attr { marginTop(12f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(factor.name); fontSizeScaled(13f); fontWeightMedium(); color(theme.textPrimary); flex(1f) } }
                    Text { attr { text("${Format.decimal(factor.weight * 100, 0)}%"); fontSizeScaled(12f); color(theme.textSecondary) } }
                }
                View {
                    attr { marginTop(6f); height(3f); flexDirectionRow(); backgroundColor(theme.surfaceMuted); borderRadius(2f) }
                    val weight = factor.weight.coerceIn(0.0, 1.0).toFloat()
                    View {
                        attr {
                            height(3f)
                            flex(weight.coerceAtLeast(0.01f))
                            backgroundColor(theme.brand)
                            borderRadius(2f)
                        }
                    }
                    View { attr { height(3f); flex((1f - weight).coerceAtLeast(0.01f)) } }
                }
                Text { attr { text(factor.description); marginTop(5f); fontSizeScaled(11f); lineHeightScaled(16f); color(theme.textSecondary) } }
                Text { attr { text("来源：${factor.source}"); marginTop(2f); fontSizeScaled(10f); color(theme.textTertiary) } }
                if (context.onToggleDrill != null) {
                    event {
                        click {
                            context.onCardEvent?.invoke(context.cardKey, com.kuikly.stockchat.cards.core.CardEvent.DrillInto(drillKey))
                            context.onToggleDrill.invoke(drillKey)
                        }
                    }
                }
            }
            if (drilled) {
                val sourceKey = "$drillKey:source"
                val sourceOpen = sourceKey in context.drilledKeys
                container.View {
                    attr {
                        marginTop(6f)
                        marginLeft(12f)
                        padding(10f)
                        backgroundColor(theme.surfaceMuted)
                        borderRadius(8f)
                    }
                    View { attr { height(2f); backgroundColor(theme.brand); borderRadius(1f) } }
                    Text { attr { text("${factor.name} 详情"); marginTop(8f); fontSizeScaled(12f); fontWeightMedium(); color(theme.textPrimary) } }
                    Text { attr { text("该因素当前权重为 ${Format.decimal(factor.weight * 100, 0)}%，可结合下方信源继续核对。 "); marginTop(4f); fontSizeScaled(11f); lineHeightScaled(16f); color(theme.textSecondary) } }
                    CardShell(
                        NewsCardModel(model.quote, listOf(NewsItem("${factor.source}：${factor.description}", factor.source, "当前"))),
                        context.copy(density = CardDensity.MINI, onOpenSheet = null),
                    )
                    View {
                        attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                        Text {
                            attr {
                                text(if (sourceOpen) "收起信源 ▲" else "查看信源 ▼")
                                fontSizeScaled(10f)
                                color(theme.brand)
                                flex(1f)
                            }
                        }
                        event { click { context.onToggleDrill?.invoke(sourceKey) } }
                    }
                    if (sourceOpen) {
                        View {
                            attr {
                                marginTop(8f)
                                marginLeft(12f)
                                padding(8f)
                                backgroundColor(theme.surface)
                                borderRadius(8f)
                            }
                            Text { attr { text("信源核对"); fontSizeScaled(11f); fontWeightMedium(); color(theme.textPrimary) } }
                            Text {
                                attr {
                                    text("${factor.source} 提供了“${factor.description}”这一条解释依据，可信度为 ${factor.confidence}。")
                                    marginTop(4f)
                                    fontSizeScaled(10f)
                                    lineHeightScaled(15f)
                                    color(theme.textSecondary)
                                }
                            }
                        }
                    }
                    View {
                        attr { alignSelfFlexStart(); marginTop(6f); paddingTop(4f); paddingBottom(4f) }
                        Text { attr { text("收起详情"); fontSizeScaled(10f); color(theme.brand) } }
                        event { click { context.onToggleDrill?.invoke(drillKey) } }
                    }
                }
            }
        }
    }
}

object DefinitionCardRenderer : CardRenderer {
    override val cardType: String = "definition"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? DefinitionCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text { attr { text(model.term); fontSizeScaled(13f); fontWeightSemiBold(); color(theme.brand); width(84f) } }
                Text { attr { text(model.plainText); fontSizeScaled(11f); color(theme.textSecondary); flex(1f) } }
            }
        } else {
            if (model.category.isNotBlank()) {
                container.Text { attr { text(model.category); fontSizeScaled(10f); color(theme.textTertiary) } }
            }
            container.Text { attr { text(model.term); marginTop(if (model.category.isBlank()) 0f else 2f); fontSizeScaled(if (context.density == CardDensity.COMPACT) 16f else 18f); fontWeightBold(); color(theme.brand) } }
            container.Text { attr { text(model.plainText); marginTop(8f); fontSizeScaled(14f); lineHeightScaled(21f); color(theme.textPrimary) } }
            if (model.depth == ExplanationDepth.PARAGRAPH && context.density != CardDensity.FULL && model.advanced.isNotBlank()) {
                container.Text { attr { text(model.advanced.take(150)); marginTop(7f); fontSizeScaled(11.5f); lineHeightScaled(18f); color(theme.textSecondary) } }
            }
            if (model.depth == ExplanationDepth.EXAMPLE_AND_DATA || context.density == CardDensity.FULL) {
                container.View {
                    attr { marginTop(10f); padding(10f); backgroundColor(theme.surfaceMuted); borderRadius(10f) }
                    Text { attr { text(model.example); fontSizeScaled(12f); lineHeightScaled(18f); color(theme.textSecondary) } }
                }
                if (model.advanced.isNotBlank()) {
                    container.Text { attr { text("进阶解释"); marginTop(12f); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.textTertiary) } }
                    container.Text { attr { text(model.advanced); marginTop(4f); fontSizeScaled(12f); lineHeightScaled(19f); color(theme.textSecondary) } }
                }
            } else if (model.depth != ExplanationDepth.ONE_SENTENCE && model.advanced.isNotBlank()) {
                container.Text { attr { text("展开后查看例子与进阶解释"); marginTop(8f); fontSizeScaled(10f); color(theme.textTertiary) } }
            }
        }
    }
}

object InsightCardRenderer : CardRenderer {
    override val cardType: String = "insight"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? InsightCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("AI"); fontSizeScaled(12f); fontWeightBold(); color(theme.brand); width(34f) } }
                Text { attr { text(model.summary); fontSizeScaled(11f); color(theme.textSecondary); flex(1f) } }
            }
        } else {
            container.Text { attr { text("AI 解读"); fontSizeScaled(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
            container.Text { attr { text(model.summary); marginTop(8f); fontSizeScaled(14f); lineHeightScaled(21f); color(theme.textSecondary) } }
            container.Text {
                attr {
                    text("AI 生成 · 仅供参考，不构成投资建议")
                    marginTop(10f)
                    fontSizeScaled(10f)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

object StockCompareCardRenderer : CardRenderer {
    override val cardType: String = "stock-compare"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? StockCompareCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                model.quotes.take(2).forEach { quote ->
                    View {
                        attr { flex(1f); marginRight(8f) }
                        Text { attr { text(quote.name); fontSizeScaled(12f); fontWeightSemiBold(); color(theme.textPrimary) } }
                        Text { attr { text(Format.percent(quote.changePercent)); marginTop(2f); fontSizeScaled(11f); color(if (quote.rising) theme.rise else theme.fall) } }
                        event { click { context.onOpenStock(quote.symbol) } }
                    }
                }
            }
            return
        }
        container.Text { attr { text("股票对比"); fontSizeScaled(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        val quotes = model.quotes.take(2)
        container.View {
            attr { marginTop(12f); flexDirectionRow(); alignItemsStretch() }
            quotes.forEachIndexed { index, quote ->
                if (index > 0) View { attr { width(1f); marginLeft(10f); marginRight(10f); backgroundColor(theme.divider) } }
                View {
                    attr { flex(1f) }
                    Text { attr { text(quote.name); fontSizeScaled(13f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(quote.symbol); marginTop(2f); fontSizeScaled(9f); color(theme.textTertiary) } }
                    Text { attr { text(Format.price(quote.price)); marginTop(10f); fontSizeScaled(17f); fontWeightBold(); color(theme.textPrimary) } }
                    Text { attr { text(Format.percent(quote.changePercent)); marginTop(3f); fontSizeScaled(12f); color(if (quote.rising) theme.rise else theme.fall) } }
                    event { click { context.onOpenStock(quote.symbol) } }
                }
            }
        }
        if (quotes.size == 2) {
            val delta = CompareCalculator.delta(quotes[0], quotes[1])
            val gapColor = if (delta.changePercentDifference >= 0) theme.rise else theme.fall
            container.View {
                attr { marginTop(14f) }
                Text { attr { text("涨跌幅差 ${Format.percent(delta.changePercentDifference)}"); fontSizeScaled(10f); color(theme.textSecondary) } }
                View {
                    attr {
                        height(4f)
                        marginTop(6f)
                        flexDirectionRow()
                        backgroundColor(theme.surfaceMuted)
                        borderRadius(2f)
                    }
                    val gap = delta.normalizedGap.toFloat().coerceIn(0f, 1f)
                    View { attr { height(4f); flex(gap.coerceAtLeast(0.01f)); backgroundColor(gapColor); borderRadius(2f) } }
                    View { attr { height(4f); flex((1f - gap).coerceAtLeast(0.01f)) } }
                }
            }
        }
        container.Text {
            attr { text("价格与涨跌幅仅作横向观察，不代表投资优劣"); marginTop(10f); fontSizeScaled(10f); color(theme.textTertiary) }
        }
    }
}

object NewsCardRenderer : CardRenderer {
    override val cardType: String = "news"

    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        model as? NewsCardModel ?: return
        val theme = context.theme
        if (context.density == CardDensity.MINI) {
            val item = model.items.firstOrNull()
            container.View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("资讯"); fontSizeScaled(12f); fontWeightSemiBold(); color(theme.brand); width(42f) } }
                Text { attr { text(item?.title ?: "${model.quote.name} 暂无新资讯"); fontSizeScaled(11f); color(theme.textSecondary); flex(1f) } }
            }
            return
        }
        container.Text { attr { text("${model.quote.name} 相关资讯"); fontSizeScaled(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        val items = if (context.density == CardDensity.COMPACT) model.items.take(2) else model.items
        items.forEach { item ->
            container.View {
                attr { marginTop(11f) }
                Text { attr { text(item.title); fontSizeScaled(12f); lineHeightScaled(18f); color(theme.textPrimary) } }
                Text { attr { text("${item.source}  ${item.time}"); marginTop(3f); fontSizeScaled(9f); color(theme.textTertiary) } }
            }
        }
        container.Text { attr { text("资讯为公开信息摘要，请以原始来源为准"); marginTop(10f); fontSizeScaled(10f); color(theme.textTertiary) } }
    }
}

/**
 * 迷你分时走势（自选卡 MINI、图表卡 MINI/COMPACT 共用）。
 *
 * 缩放走 [TimeLineCalculator.calculate] 自适应 min/max：迷你图只表意当日形态，
 * 波动有多大就铺多满（2026-09-09 用户反馈"全是平线"后收紧了 padding 下限）。
 *
 * 入场/刷新为 draw-on 生长动画：progress 0→1 的 setTimeout 链 + 900ms 安全兜底
 * 强制 1f（防断链卡死），draw 闭包读 progress observable 建立依赖（R1），无 attr
 * animate、不涉及 R5。同签名数据（分时点数 + 最新价）的重建不重播——QuoteRepository
 * 一次 load 会连发 snapshot/timeline/klines 多个回调触发整页重建，不加防抖的话
 * 动画会被反复打断；数据真的变了（点数或现价更新）才重新从左往右生长。
 */
internal fun MiniTimeline(
    container: ViewContainer<*, *>,
    model: StockQuoteCardModel,
    context: CardContext,
    height: Float = 72f,
) {
    val quote = model.quote
    val theme = context.theme
    val pts = quote.timeline
    val state = MiniChartState()
    val signature = pts.size to (pts.lastOrNull()?.price ?: 0.0)
    val trackerKey = "${model.cardId}#$height"
    if (pts.isEmpty() || miniDrawOnTracker[trackerKey] == signature) {
        state.progress = 1f
    } else {
        miniDrawOnTracker[trackerKey] = signature
        if (miniDrawOnTracker.size > 128) miniDrawOnTracker.clear()
        var i = 0
        fun tick() {
            i++
            state.progress = (i.toFloat() / MINI_DRAW_ON_STEPS).coerceIn(0f, 1f)
            if (state.progress < 1f) setTimeout(MINI_DRAW_ON_STEP_MS) { tick() }
        }
        tick()
        setTimeout(MINI_DRAW_ON_SAFETY_MS) { state.progress = 1f }
    }

    container.Canvas({
        attr { height(height); marginTop(10f); alignSelfStretch() }
    }) { canvas, width, canvasHeight ->
        val geometry = TimeLineCalculator.calculate(quote.timeline, width, canvasHeight, quote.previousClose)
        if (geometry.points.isEmpty()) return@Canvas
        val progress = state.progress
        // A newly returned timeline can contain a single point. Keep the
        // draw-on's two-point minimum for normal series, but never let it
        // exceed the actual data count: the old value of 2 indexed past a
        // one-point series and crashed the Watchlist page as quotes arrived.
        val visible = if (progress >= 1f) geometry.points.size
        else max(2, (geometry.points.size * progress).roundToInt())
            .coerceAtMost(geometry.points.size)

        // 昨收基准虚线（结构层，直接全量呈现，不参与生长）
        canvas.beginPath()
        canvas.moveTo(0f, geometry.baselineY)
        canvas.lineTo(width, geometry.baselineY)
        canvas.setLineDash(listOf(3f, 4f))
        canvas.strokeStyle(theme.divider)
        canvas.lineWidth(1f)
        canvas.stroke()
        canvas.setLineDash(emptyList())

        val tone = if (quote.rising) theme.rise else theme.fall

        // 面积淡渐变：闭合到昨收基准，随线头一同生长（迷你图只做单侧轻渐变）
        if (visible >= 2) {
            val fill = canvas.createLinearGradient(0f, 0f, 0f, canvasHeight)
            fill.addColorStop(0f, tone.opacity(0.14f))
            fill.addColorStop(1f, tone.opacity(0.02f))
            canvas.beginPath()
            canvas.moveTo(geometry.points[0].x, geometry.baselineY)
            for (i in 0 until visible) canvas.lineTo(geometry.points[i].x, geometry.points[i].y)
            canvas.lineTo(geometry.points[visible - 1].x, geometry.baselineY)
            canvas.closePath()
            canvas.fillStyle(fill)
            canvas.fill()
        }

        // 价格折线：只画前 visible 个点 → 线头从左向右推进
        canvas.beginPath()
        for (i in 0 until visible) {
            val point = geometry.points[i]
            if (i == 0) canvas.moveTo(point.x, point.y) else canvas.lineTo(point.x, point.y)
        }
        canvas.strokeStyle(tone)
        canvas.lineWidth(2f)
        canvas.lineCapRound()
        canvas.stroke()

        // 生长头/now 点：动画中是线头，走完后落在现价位置
        val tip = geometry.points[visible - 1]
        canvas.beginPath()
        canvas.arc(tip.x, tip.y, 2.2f, 0f, (2 * PI).toFloat(), false)
        canvas.fillStyle(tone)
        canvas.fill()
    }
}

/** 迷你分时 draw-on 参数：18 步 × 20ms ≈ 360ms；900ms 兜底强制走完。 */
private const val MINI_DRAW_ON_STEPS = 18
private const val MINI_DRAW_ON_STEP_MS = 20
private const val MINI_DRAW_ON_SAFETY_MS = 900

/** draw-on 进度状态（R1：draw 闭包读 progress observable 驱动逐帧重绘）。 */
private class MiniChartState {
    var progress by observable(0f)
}

/** 已播动画签名表：key = cardId#height，value = (分时点数, 最新价)，数据变了才重播。 */
private val miniDrawOnTracker = mutableMapOf<String, Pair<Int, Double>>()

private fun Metric(
    label: String,
    value: String,
    theme: com.kuikly.stockchat.cards.theme.StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { flex(1f) }
        Text { attr { text(label); fontSizeScaled(10f); color(theme.textTertiary) } }
        Text { attr { text(value); marginTop(3f); fontSizeScaled(12f); fontWeightMedium(); color(theme.textPrimary) } }
    }
}
