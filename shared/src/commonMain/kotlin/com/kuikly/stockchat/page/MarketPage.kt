package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openChatWithQuestion
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.provider.HotspotSnapshot
import com.kuikly.stockchat.data.provider.MarketIndex
import com.kuikly.stockchat.data.provider.MarketOverview
import com.kuikly.stockchat.data.provider.MarketSnapshotFrame
import com.kuikly.stockchat.data.provider.MarketSnapshotStore
import com.kuikly.stockchat.data.provider.MarketDemoDaySynthesizer
import com.kuikly.stockchat.data.provider.MarketEvent
import com.kuikly.stockchat.data.provider.MarketEventDetector
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.SectorRank
import com.kuikly.stockchat.data.provider.SourceTier
import com.kuikly.stockchat.data.provider.platformCurrentHour
import com.kuikly.stockchat.data.provider.platformCurrentMinuteOfDay
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.data.provider.timeLabelOf
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.AppTopBarMetric
import com.kuikly.stockchat.page.components.MarketNarrativeAxis
import com.kuikly.stockchat.page.components.NewsTape
import com.kuikly.stockchat.page.components.SourceStampLine
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Anchor
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Skew
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 市场页 v3.0「纪录片结构」（doc 36）：从九区块信息流到「叙事层 / 数据切片层 /
 * 口径层」三层。核心交互 = 时间机器——拖动叙事轴（①②）整页回到那一刻，所有
 * 数字经 [displayOverview] 与快照帧一致（N1：scrub 中零补间直接切换）。
 *
 * ## Reactivity contract (this page previously violated all of it)
 *
 * `body()` runs exactly once (`ComposeView.didInit`). An observable read in the
 * builder scope is therefore a first-frame snapshot: it registers no dependency,
 * the surrounding `attr` never re-runs, and — because `Attr.animate()` keys its
 * animation off `ReactiveObserver.currentObservablePropertyKey`, i.e. the LAST
 * observable read in the block, not off its `value` argument — every animate()
 * call silently binds to nothing. Three rules follow:
 *
 * 1. Read observables only inside `attr {}`, `event {}`, or a directive's value
 *    closure (`vif({...})` / `vbind({...})`).
 * 2. `animate()` goes LAST in its attr block, with the driving observable read in
 *    its own argument expression. In particular `theme` is captured once in the
 *    builder scope on purpose: `BasePager.nightModel` is observable, so reading
 *    `page.theme` inside an attr would hijack the animation key.
 * 3. A view created by `vif`/`vbind` cannot animate on its own mount, so anything
 *    that must fade in uses mount-then-present two-tick staging (see [showPeek])
 *    — the `drawerMounted`/`drawerPresented` pattern.
 *
 * Sections carrying no animation are wrapped in `vbind({ displayOverview() })`
 * and rebuild per scrub frame (N1 直接切换); sections carrying animation (Hero
 * tick flash, ladder) read inside `attr` instead, so their views survive a data
 * update and keep their registered animations.
 */
@Page(Routes.MARKET, supportInLocal = true)
internal class MarketPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }
    private var overview: MarketOverview by observable(OfflineMarketInsightProvider().overviewValue())
    private var refreshing: Boolean by observable(false)
    /** 0 = no flash; 1/2/3 = the three phases of one tick sequence. */
    private var tickPhase: Int by observable(0)
    private var tickDirection: Double by observable(0.0)
    private var tickRevision = 0
    private var emotionPulse: Boolean by observable(false)
    private var emotionPulseRevision = 0
    private var hotspots: HotspotSnapshot by observable(OfflineMarketInsightProvider().hotspotValue())
    private var expandedBoardLevel: Int by observable(-1)
    private var peek: MarketPeek? by observable(null)
    private var peekVisible: Boolean by observable(false)
    private var heroEntered: Boolean by observable(false)
    private var ladderEntered: Boolean by observable(false)
    private var stickyIndexVisible: Boolean by observable(false)
    private var pullDistance: Float by observable(0f)
    private var pullReady: Boolean by observable(false)
    private var refreshResultVisible: Boolean by observable(false)
    /** 指数带次级卡片与 AI 卡的入场开关（spec 22 §5.2 渐进加载的入场层）。 */
    private var stripEntered: Boolean by observable(false)
    /** 宽度条中心双向生长（spec 22 §3.3）。 */
    private var breadthEntered: Boolean by observable(false)
    /** 量能三柱从基线生长（spec 22 §3.2）。 */
    private var volumeEntered: Boolean by observable(false)

    // ------------------------------------------------------------------
    // 时间机器（doc 36 ①②）：快照环 + scrub 状态 + 事件（③④共用）
    // ------------------------------------------------------------------
    private val snapshotStore = MarketSnapshotStore()
    private var snapshotFrames: List<MarketSnapshotFrame> by observable(emptyList())
    /** -1 = 「现在」；0..240 = 正在回放该分钟帧。 */
    private var scrubMinute: Int by observable(-1)
    private var replayChipVisible: Boolean by observable(false)
    /** 0 = 红盘率；1 = 上证；2 = 创业板；3 = 北证50。 */
    private var axisMetric: Int by observable(0)
    /** 0 宽度情绪 / 1 量能资金 / 2 板块竞速 / 3 连板梯队。 */
    private var activeSlice: Int by observable(0)
    private var tempDetailOpen: Boolean by observable(false)
    private var scrubTweenGeneration = 0
    /** 端侧事件检测缓存（ingest 时重算，draw/卡片共用，避免每帧重跑 O(n²)）。 */
    private var cachedEvents: List<MarketEvent> = emptyList()
    private var marketNews: List<NewsItem> = emptyList()

    private var aiState: Int by observable(0)
    private var aiText: String by observable("")
    /** AI 思考态骨架的有限呼吸脉冲（2 次，非循环，可中断）。 */
    private var aiPulse: Boolean by observable(false)
    private var aiPulseRevision = 0
    private var aiProvider: AiProvider? = null
    private var aiGeneration = 0
    // 流式平滑器（复用聊天页）：网络回调在后台线程触发，observable 只能主线程写，
    // 与 StockDetailPage 同一处修复（后台直写曾造成闪退与流式内容不刷新）。
    private var activeAiTypewriter: TypewriterSmoother? = null
    private val aiDependencies by lazy { ChatDependencies.forPager(pagerId) }

    /**
     * The page's single motion switch. Every transform/opacity timeline on this
     * page is gated on it, so there is exactly one place to disable motion.
     * scrub 本身不受它影响——用户主动手势且是信息本体（doc 36 N 系列兜底条款）。
     */
    private fun motionEnabled(): Boolean = !reduceMotion

    override fun created() {
        super.created()
        marketNews = OfflineMarketInsightProvider().marketNewsValue()
        // Entry motion is one-shot and decorative-free; it never repeats while prices update.
        if (motionEnabled()) {
            setTimeout(1) {
                heroEntered = true
                ladderEntered = true
                stripEntered = true
                breadthEntered = true
                volumeEntered = true
            }
        }
        ingestOverview(overview)
        refreshOverview()
        dependencies.insightRepository.loadHotspots { hotspots = it }
    }

    /**
     * 快照入库（doc 36 §7 降级链）：真实模式逐分钟节流记录真实轮询帧；
     * DEMO 模式用演示快照锚定合成整日 241 帧，让时间机器在演示态完整可玩
     * （真实模式绝不合成——不冒充）。冷启动无帧时叙事轴退化为空轨道。
     */
    private fun ingestOverview(data: MarketOverview) {
        if (data.stamp.tier == SourceTier.DEMO) {
            if (snapshotStore.all().size < MarketDemoDaySynthesizer.FRAMES) {
                snapshotStore.clear()
                MarketDemoDaySynthesizer.synthesize(data).forEach(snapshotStore::put)
            }
        } else {
            snapshotStore.record(data, platformCurrentMinuteOfDay())
        }
        snapshotFrames = snapshotStore.all()
        cachedEvents = MarketEventDetector.detect(snapshotFrames)
    }

    /**
     * 全页数据出口：scrub 中返回该帧快照，否则返回实时 overview。
     * 时间机器的作用面 = 本函数——所有分区读它即随帧同步（vbind 的 value 闭包
     * 内调用会同时注册 scrubMinute/overview 两个依赖）。
     */
    private fun displayOverview(): MarketOverview {
        val frame = if (scrubMinute in 0..240) snapshotStore.frameAt(scrubMinute) else null
        return frame?.overview ?: overview
    }

    private fun axisMetricsCode(): String? = when (axisMetric) {
        1 -> "000001"
        2 -> "399006"
        3 -> "899050"
        else -> null
    }

    private fun metricValueAt(frame: MarketSnapshotFrame): Double {
        val code = axisMetricsCode() ?: return frame.redPct
        return frame.overview.indices.firstOrNull { it.code == code }?.changePercent
            ?: frame.overview.indices.firstOrNull()?.changePercent
            ?: frame.redPct
    }

    /** 度量序列补全为 0..240 稠密数组（帧稀疏时按 last-known 填充）。 */
    private fun axisSeries(): List<Double> {
        val frames = snapshotFrames
        if (frames.isEmpty()) return emptyList()
        val out = DoubleArray(241)
        var fi = 0
        var current = metricValueAt(frames.first())
        for (m in 0..240) {
            while (fi < frames.size && frames[fi].minute <= m) {
                current = metricValueAt(frames[fi])
                fi++
            }
            out[m] = current
        }
        return out.toList()
    }

    /** 分钟成交密度：累计成交额逐分钟差分 → 归一化（B 站高能进度条的语言）。 */
    private fun axisDensity(): List<Float> {
        val frames = snapshotFrames
        if (frames.size < 2) return emptyList()
        val cumulative = DoubleArray(241)
        var fi = 0
        var current = 0.0
        for (m in 0..240) {
            while (fi < frames.size && frames[fi].minute <= m) {
                current = frames[fi].overview.turnoverAmount ?: current
                fi++
            }
            cumulative[m] = current
        }
        val diff = DoubleArray(241)
        for (m in 1..240) diff[m] = (cumulative[m] - cumulative[m - 1]).coerceAtLeast(0.0)
        val maxDiff = diff.max().takeIf { it > 0.0 } ?: return emptyList()
        return diff.map { (it / maxDiff).toFloat() }
    }

    /** 温度日内轨迹（moodScore 与市场温度同源同公式）。 */
    private fun temperatureSeries(): List<Double> {
        val frames = snapshotFrames
        if (frames.isEmpty()) return emptyList()
        val out = DoubleArray(241)
        var fi = 0
        var current = frames.first().overview.moodScore.toDouble()
        for (m in 0..240) {
            while (fi < frames.size && frames[fi].minute <= m) {
                current = frames[fi].overview.moodScore.toDouble()
                fi++
            }
            out[m] = current
        }
        return out.toList()
    }

    private fun axisBaseValue(): Double = if (axisMetric == 0) 50.0 else 0.0

    private fun axisMetricLabel(): String = when (axisMetric) {
        0 -> "红盘率 · 多空分界 50%"
        1 -> "上证指数 · 分界 0%（昨收）"
        2 -> "创业板指 · 分界 0%（昨收）"
        else -> "北证50 · 分界 0%（昨收）"
    }

    /** scrub 跳帧（N2）：520ms 补间，单一 scrubTweenGeneration 防串。 */
    private fun scrubTo(target: Int) {
        val clamped = target.coerceIn(0, 240)
        scrubTweenGeneration++
        val generation = scrubTweenGeneration
        if (!motionEnabled()) {
            applyScrub(clamped)
            return
        }
        val from = if (scrubMinute in 0..240) scrubMinute else 240
        val steps = 8
        for (i in 1..steps) {
            val p = i.toDouble() / steps
            val eased = 1 - (1 - p) * (1 - p) * (1 - p)
            val value = (from + (clamped - from) * eased).roundToInt()
            setTimeout((520 / steps * i)) {
                if (generation == scrubTweenGeneration) applyScrub(value)
            }
        }
    }

    private fun applyScrub(minute: Int) {
        scrubTweenGeneration++
        scrubMinute = minute.coerceIn(0, 240)
        replayChipVisible = scrubMinute in 0..239
    }

    private fun backToNow() {
        scrubTweenGeneration++
        scrubMinute = -1
        replayChipVisible = false
    }

    /** 拖动中直接切帧（N1）：拖动手势会打断进行中的跳帧补间。 */
    private fun onAxisScrub(minute: Int) {
        scrubTweenGeneration++
        scrubMinute = minute
        replayChipVisible = minute in 0..239
    }

    /** 板块竞速（⑤）：Top5 + 相对 30 分钟前的位次变动（只在回放态显箭头）。 */
    private fun raceRows(): List<SectorRaceRow> {
        val data = displayOverview()
        val ranked = data.sectors.sortedByDescending { it.changePercent }.take(5)
        if (ranked.isEmpty()) return emptyList()
        val refFrame = if (scrubMinute >= 30) snapshotStore.frameAt(scrubMinute - 30) else null
        val refRanked = (refFrame?.overview ?: data).sectors.sortedByDescending { it.changePercent }
        return ranked.mapIndexed { index, sector ->
            val refRank = refRanked.indexOfFirst { it.code == sector.code }.takeIf { it >= 0 } ?: index
            SectorRaceRow(sector, index + 1, refRank - (index + 1))
        }
    }

    private data class SectorRaceRow(val sector: SectorRank, val rank: Int, val delta: Int)

    /** 指数下钻（J2）：跳转 App 既有详情页，市场页不新建界面。 */
    private fun indexSymbol(index: MarketIndex): String? = when (index.name) {
        "上证指数" -> "000001.SH"
        "深证成指" -> "399001.SZ"
        "创业板指" -> "399006.SZ"
        "科创50" -> "000688.SH"
        "沪深300" -> "000300.SH"
        "上证50" -> "000016.SH"
        "中证500" -> "000905.SH"
        else -> null
    }

    private fun openIndexDetail(index: MarketIndex?) {
        val symbol = index?.let(::indexSymbol) ?: return
        openStockDetail(symbol, from = Routes.MARKET)
    }

    /** 新闻弹幕带随帧过滤（doc 36 §9.1）：回放到 HH:MM 只显示当时已发布的新闻。 */
    private fun filteredNews(): List<NewsItem> {
        if (marketNews.isEmpty()) return emptyList()
        if (scrubMinute < 0) return marketNews
        val limit = timeLabelOf(scrubMinute)
        return marketNews.filter { it.time.length >= 16 && it.time.substring(11, 16) <= limit }
    }

    /**
     * A changed quote uses one restartable L2 sequence (80ms / 220ms / 300ms).
     * The revision guard matters when a manual refresh arrives while a previous
     * timer is still finishing: stale timers must never clear the newer tick.
     */
    private fun refreshOverview(fromPull: Boolean = false) {
        val previous = overview
        refreshing = true
        dependencies.insightRepository.loadOverview { updated ->
            val before = previous.indices.firstOrNull()?.price
            val after = updated.indices.firstOrNull()?.price
            overview = updated
            ingestOverview(updated)
            refreshing = false
            if (fromPull) {
                refreshResultVisible = true
                setTimeout(1600) { refreshResultVisible = false }
            }
            if (before != null && after != null && before != after) {
                triggerTickFlash(after - before)
            }
            if (shouldPulseShortTermAlert(previous, updated)) {
                triggerEmotionPulse()
            }
        }
    }

    /**
     * One observable drives the whole sequence. Splitting it across a `tickFlash`
     * boolean plus a phase counter meant two properties changed in the same tick,
     * and animate() can only own one key — the second write then applied
     * instantly and swallowed the flash.
     */
    private fun triggerTickFlash(direction: Double) {
        tickRevision += 1
        val revision = tickRevision
        tickDirection = direction
        tickPhase = 1
        if (!motionEnabled()) {
            setTimeout(600) { if (tickRevision == revision) tickPhase = 0 }
            return
        }
        setTimeout(80) { if (tickRevision == revision) tickPhase = 2 }
        setTimeout(300) { if (tickRevision == revision) tickPhase = 3 }
        setTimeout(600) { if (tickRevision == revision) tickPhase = 0 }
    }

    /** Two finite pulses only when a real short-term warning newly appears or deepens. */
    private fun shouldPulseShortTermAlert(before: MarketOverview, after: MarketOverview): Boolean {
        val sealWorsened = (after.sealRate ?: 1.0) < 0.80 && (before.sealRate ?: 1.0) >= 0.80
        val boardDropped = before.highestBoard != null && after.highestBoard != null && after.highestBoard <= before.highestBoard - 2
        return sealWorsened || boardDropped
    }

    private fun triggerEmotionPulse() {
        if (!motionEnabled()) return
        emotionPulseRevision += 1
        val revision = emotionPulseRevision
        emotionPulse = true
        setTimeout(300) { if (emotionPulseRevision == revision) emotionPulse = false }
        setTimeout(600) { if (emotionPulseRevision == revision) emotionPulse = true }
        setTimeout(900) { if (emotionPulseRevision == revision) emotionPulse = false }
    }

    private fun toggleBoardLevel(level: Int) {
        expandedBoardLevel = if (expandedBoardLevel == level) -1 else level
    }

    /** The compact strip is toggled only at the threshold, not on every scroll callback. */
    private fun updateStickyIndex(offsetY: Float) {
        val next = offsetY > 120f
        if (stickyIndexVisible != next) stickyIndexVisible = next
        if (offsetY >= 0f && pullReady) completePullRefresh()
        val distance = (-offsetY).coerceAtLeast(0f).coerceAtMost(84f)
        if (abs(pullDistance - distance) >= 2f) pullDistance = distance
        val ready = distance >= 72f
        if (pullReady != ready) pullReady = ready
    }

    private fun completePullRefresh() {
        if (pullReady && !refreshing) refreshOverview(fromPull = true)
        pullDistance = 0f
        pullReady = false
    }

    private fun showPeek(title: String, primary: String, detail: String) {
        peek = MarketPeek(title, primary, detail)
        if (motionEnabled()) setTimeout(1) { if (peek != null) peekVisible = true } else peekVisible = true
    }

    private fun dismissPeek() {
        if (!peekVisible) return
        peekVisible = false
        if (!motionEnabled()) {
            peek = null
        } else {
            setTimeout(200) { if (!peekVisible) peek = null }
        }
    }

    private fun showIndexPeek() {
        val index = displayOverview().indices.firstOrNull()
        showPeek(
            title = index?.name ?: "上证指数",
            primary = index?.let { "${Format.price(it.price)}  ${Format.percent(it.changePercent)}" } ?: "--",
            detail = "长按预览 · ${displayOverview().stamp.mode.quoteLabel()} · ${displayOverview().stamp.asOf}",
        )
    }

    private fun showSectorPeek(code: String) {
        val sector = displayOverview().sectors.firstOrNull { it.code == code } ?: return
        showPeek(
            title = sector.name,
            primary = Format.percent(sector.changePercent),
            detail = "上涨 ${sector.risingCount} · 下跌 ${sector.fallingCount} · 主力资金 ${Format.compactAmount(sector.mainFlow)}",
        )
    }

    private fun showBoardPeek(level: Int) {
        val samples = hotspots.limitUps.filter { it.consecutiveBoards == level }
        val headline = if (level == 1) "首板" else "${level}板"
        val names = samples.take(3).joinToString("、") { it.name }.ifBlank { "当前数据源暂无该层级股票样本" }
        showPeek(headline, "${samples.size} 只样本", names)
    }

    private fun showShortTermMetricPeek(label: String, primary: String, detail: String) {
        showPeek(label, primary, "$detail · 涨停池数据源：${displayOverview().stamp.source}")
    }

    private fun showFundMethodology(label: String, primary: String, detail: String) {
        showPeek(label, primary, "$detail · 数据源：${displayOverview().stamp.source}。不同平台统计范围和时间点可能不同。")
    }

    // ------------------------------------------------------------------
    // AI 复盘卡（doc 31 §1 → doc 36 ④ 可导航版）：锚点由端侧事件检测生成，
    // LLM 只把事件串成话。未配置 AI 时降级为深链对话页。
    // ------------------------------------------------------------------

    private fun toggleAiBriefing() {
        if (aiState == 1 || aiState == 2) stopAiBriefing() else startAiBriefing()
    }

    private fun aiActionLabel(): String = when (aiState) {
        1, 2 -> "停止"
        3 -> "重新生成"
        4 -> "重试"
        else -> "生成"
    }

    private fun startAiBriefing() {
        val config = aiDependencies.configStore.load()
        val configError = config.validationError()
        if (configError != null) {
            openChatWithQuestion("帮我复盘一下今天的市场：${moodHeadline(overview)}")
            return
        }
        aiProvider?.stop()
        activeAiTypewriter?.cancel()
        aiText = ""
        aiState = 1
        triggerAiPulse()
        val provider = aiDependencies.aiProviderFactory(config)
        aiProvider = provider
        // 线程纪律：provider 回调来自后台线程，observable 写入只在主线程发生
        //（onPublish 为节拍器主线程回调；onDone/onError 经 setTimeout(0) 跳回）。
        val generation = ++aiGeneration
        var content = ""
        val smoother = TypewriterSmoother(pagerId) { revealed ->
            if (generation != aiGeneration) return@TypewriterSmoother
            aiText = revealed
            if (aiState == 1 && revealed.isNotEmpty()) aiState = 2
        }
        activeAiTypewriter = smoother
        provider.ask(
            messages = listOf(AiChatMessage("user", buildAiPrompt())),
            onDelta = { delta ->
                content += delta
                if (generation == aiGeneration) smoother.append(delta)
            },
            onDone = {
                if (generation != aiGeneration) return@ask
                val fullContent = content
                smoother.complete {
                    setTimeout(0) {
                        if (generation != aiGeneration) return@setTimeout
                        if (aiState <= 2) aiState = if (fullContent.isBlank()) 0 else 3
                    }
                }
            },
            onError = { message ->
                if (generation != aiGeneration) return@ask
                setTimeout(0) {
                    if (generation != aiGeneration) return@setTimeout
                    smoother.flushNow()
                    smoother.cancel()
                    if (aiState == 1 || aiState == 2) {
                        aiText = message
                        aiState = 4
                    }
                }
            },
        )
    }

    /** 流式中的再点击 = 中断：保留已生成的部分文本（落为 done），否则回到 idle。 */
    private fun stopAiBriefing() {
        aiProvider?.stop()
        aiGeneration++
        activeAiTypewriter?.flushNow()
        activeAiTypewriter?.cancel()
        activeAiTypewriter = null
        aiPulseRevision += 1
        aiState = if (aiText.isNotBlank()) 3 else 0
    }

    /** 骨架呼吸是有限脉冲（2 次），流式一旦开始即被内容反馈取代。 */
    private fun triggerAiPulse() {
        aiPulseRevision += 1
        val revision = aiPulseRevision
        aiPulse = true
        setTimeout(350) { if (aiPulseRevision == revision && aiState == 1) aiPulse = false }
        setTimeout(700) { if (aiPulseRevision == revision && aiState == 1) aiPulse = true }
        setTimeout(1050) { if (aiPulseRevision == revision && aiState == 1) aiPulse = false }
    }

    /** 只喂本页已展示的数据；明确禁止卡片协议与荐股措辞（doc 31 §1.2）。 */
    private fun buildAiPrompt(): String {
        val data = overview
        val indexText = data.indices.take(3)
            .joinToString("；") { "${it.name} ${Format.price(it.price)}（${Format.percent(it.changePercent)}）" }
        val topSectors = data.sectors.sortedByDescending { it.changePercent }
            .take(3)
            .joinToString("、") { "${it.name} ${Format.percent(it.changePercent)}" }
        return buildString {
            append("你是 A 股市场解读助手。基于以下今日行情快照，用不超过 150 字的简体中文口语化复盘今天市场结构：")
            append("先讲指数与量能，再讲宽度与情绪，最后一句给中性观察。")
            append("只输出正文，不要列表、不要卡片协议、不要任何个股买卖建议。\n")
            append("指数：").append(indexText.ifBlank { "待接入" }).append("。\n")
            append("宽度：上涨 ").append(data.risingCount).append(" / 平盘 ").append(data.flatCount)
                .append(" / 下跌 ").append(data.fallingCount).append("。\n")
            append("量能：").append(data.turnoverAmount?.let(::turnoverText) ?: "待接入")
            volumeDeviation()?.let { append("（较5日均 ").append(Format.percent(it)).append("）") }
            append("。\n")
            append("情绪：恐贪 ").append(data.moodScore)
                .append("；封板率 ").append(data.sealRate?.let { Format.percent(it * 100) } ?: "--")
                .append("；最高板 ").append(data.highestBoard ?: "--")
                .append(" 板（昨 ").append(data.yesterdayHighestBoard ?: "--").append(" 板）。\n")
            append("领涨板块：").append(topSectors.ifBlank { "待接入" }).append("。\n")
            append("数据源：").append(data.stamp.source).append("，截至 ").append(data.stamp.asOf).append("。")
        }
    }

    private fun aiFollowUpQuestion(): String {
        val leader = displayOverview().sectors.maxByOrNull { it.changePercent }
        return buildString {
            append("接着刚才今天市场的复盘，帮我展开讲讲")
            if (leader != null) append("领涨板块 ${leader.name}（${Format.percent(leader.changePercent)}）") else append("板块轮动")
            append("背后的资金和情绪逻辑。")
        }
    }

    // ------------------------------------------------------------------
    // body：叙事层 → 数据切片层 → 口径层（doc 36 §3 纪录片三层结构）
    // ------------------------------------------------------------------

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                // 竖向 Scroller 水平 padding 会被双倍扣除，14/14 时右侧多出 28dp 留白；
                // 右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                attr { flex(1f); paddingLeft(14f); paddingRight(0f); paddingTop(page.pagerData.statusBarHeight + 68f); paddingBottom(78f) }
                event { scroll { params -> page.updateStickyIndex(params.offsetX) } }
                // Captured once, deliberately: both are non-observable snapshots
                // here (see rule 2 in the class doc — reading `page.theme` inside
                // an attr would steal every animation key on this page).
                val theme = page.theme
                val phase = page.marketPhase()

                // J1 · 新闻弹幕带（随帧过滤）：点条目 = 以该新闻为上下文追问 AI。
                NewsTape(
                    theme = theme,
                    items = { page.filteredNews() },
                    selected = { null },
                    sentimentOf = { null },
                    headerTitle = "今日要闻 · 弹幕带",
                    headerHint = "点按追问 AI · 回放时只显示当时的新闻",
                    onTapItem = { item ->
                        page.openChatWithQuestion("「${item.title}」这条新闻是什么情况？对今天的市场有什么影响？")
                    },
                    onAskAi = { item ->
                        page.openChatWithQuestion("「${item.title}」是什么情况？", "来自市场页新闻弹幕带")
                    },
                )

                // A · market session state：回放态由「历史回放 · HH:MM」接棒。
                View {
                    attr { paddingTop(10f); paddingBottom(17f); paddingLeft(4f); paddingRight(4f); opacity(if (phase.live || page.scrubMinute >= 0) 1f else 0.55f) }
                    View {
                        attr { height(27f); flexDirectionRow(); alignItemsCenter(); paddingLeft(9f); paddingRight(9f); borderRadius(14f); backgroundColor(theme.surface.opacity(0.72f)) }
                        View {
                            attr {
                                size(8f, 8f)
                                borderRadius(5f)
                                backgroundColor(if (page.scrubMinute >= 0) theme.brand else if (phase.live) theme.rise else theme.flat)
                            }
                        }
                        Text {
                            attr {
                                // R1：attr 内读 scrubMinute，回放态标签随帧更新
                                text(if (page.scrubMinute >= 0) "历史回放 · ${timeLabelOf(page.scrubMinute)}" else phase.label)
                                marginLeft(6f); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.textPrimary)
                            }
                        }
                        Text { attr { text(page.refreshStatusText()); flex(1f); textAlignRight(); fontSizeScaled(10f); color(theme.textTertiary) } }
                    }
                    vif({ page.scrubMinute < 0 }) {
                        phase.notice?.let { Text { attr { text(it); marginTop(6f); fontSizeScaled(10.5f); color(theme.flat) } } }
                    }

                    // B · Hero sits on the atmosphere, not in another generic card.
                    // 回放帧经 displayOverview 随帧切换（vbind value 闭包读 scrubMinute）。
                    vbind({ page.displayOverview() }) {
                        val data = page.displayOverview()
                        Text { attr { text(page.moodHeadline(data)); marginTop(18f); fontSizeScaled(30f); lineHeightScaled(38f); fontWeightBold(); color(theme.textPrimary) } }
                        View { attr { marginTop(11f); flexDirectionRow(); flexWrapWrap() }
                            MarketPill("情绪周期 · ${page.moodLabel(data.moodScore)}", page.moodColor(data.moodScore))
                            MarketPill("恐贪 ${data.moodScore}", theme.brand)
                            MarketPill(page.threshold(data.indices.firstOrNull()?.changePercent), theme.flat)
                        }
                        View { attr { marginTop(12f); paddingLeft(9f); borderLeft(Border(2f, BorderStyle.SOLID, theme.brand)) }
                            Text { attr { text(data.explanation); fontSizeScaled(12f); lineHeightScaled(18f); color(theme.textSecondary) } }
                        }
                    }
                }

                // ⑥ · 透明温度计：徽章 + 日内轨迹 + 分量拆解（公式全透明可点开）。
                View { attr { marginTop(2f); marginLeft(4f); marginRight(10f); flexDirectionRow(); alignItemsCenter(); paddingLeft(11f); paddingRight(11f); paddingTop(9f); paddingBottom(9f); borderRadius(13f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                    View {
                        attr {
                            width(44f); height(44f); borderRadius(12f); allCenter()
                            backgroundColor(page.tempBadgeBackground())
                            // animate last：驱动键 = scrubMinute（回放帧直切徽章配色）
                            animate(Animation.easeOut(0.18f), page.scrubMinute)
                        }
                        Text { attr { text("${page.displayOverview().moodScore}°"); fontSizeScaled(17f); lineHeightScaled(19f); fontWeightBold(); color(page.tempBadgeForeground()) } }
                        Text { attr { text("市场温度"); fontSizeScaled(7.5f); color(page.tempBadgeForeground().opacity(0.72f)) } }
                    }
                    View { attr { flex(1f); marginLeft(10f) }
                        Text { attr { text(page.temperatureSummary()); fontSizeScaled(10.5f); lineHeightScaled(15f); color(theme.textSecondary) } }
                        Text { attr { text(if (page.tempDetailOpen) "收起构成 ∧" else "点开看构成 ∨"); marginTop(3f); fontSizeScaled(9.5f); fontWeightSemiBold(); color(theme.brand) } }
                    }
                    TemperatureSpark(theme, series = { page.temperatureSeries() }, scrubMinute = { page.scrubMinute })
                    event { click { page.tempDetailOpen = !page.tempDetailOpen } }
                }
                vif({ page.tempDetailOpen }) {
                    View { attr { marginTop(8f); marginLeft(4f); marginRight(10f); padding(10f); borderRadius(9f); backgroundColor(theme.surfaceMuted) }
                        Text { attr { text(page.temperatureFormulaText()); fontSizeScaled(10f); lineHeightScaled(17f); color(theme.textSecondary) } }
                        Text { attr { text("只描述「今天热不热」，不预测「明天涨不涨」。公式端侧透明，与黑箱温度计划界。"); marginTop(5f); fontSizeScaled(9.5f); color(theme.textTertiary) } }
                    }
                }

                // C · z1 secondary index cards under a z3 main glass card（J2：点按跳既有详情页）。
                Text { attr { text("指数带"); marginTop(14f); fontSizeScaled(17f); fontWeightBold(); color(theme.textPrimary) } }
                Text { attr { text("点位、方向与当日区间 · 点按进入大盘详情页"); marginTop(3f); fontSizeScaled(10f); color(theme.textTertiary) } }
                View { attr { marginTop(9f); marginLeft(8f); marginRight(8f); flexDirectionRow(); opacity(0.90f) }
                    vbind({ page.displayOverview() }) {
                        page.displayOverview().indices.drop(1).take(2).forEachIndexed { index, item ->
                            View {
                                attr {
                                    flex(1f); height(108f); padding(13f); borderRadius(17f); backgroundColor(theme.surfaceMuted); if (index > 0) marginLeft(8f)
                                    opacity(if (page.stripEntered || !page.motionEnabled()) 1f else 0f)
                                    if (page.motionEnabled()) {
                                        val entered = page.stripEntered
                                        transform(
                                            rotate = Rotate.DEFAULT,
                                            scale = Scale.DEFAULT,
                                            translate = Translate(0f, 0f, 0f, if (entered) 0f else 12f),
                                            anchor = Anchor.DEFAULT,
                                            skew = Skew.DEFAULT,
                                        )
                                        animate(Animation.easeOut(0.26f).delay(0.06f * index), page.stripEntered)
                                    }
                                }
                                Text { attr { text(item.name); fontSizeScaled(11f); color(theme.textSecondary) } }
                                Text { attr { text(Format.price(item.price)); marginTop(8f); fontSizeScaled(19f); fontWeightBold(); color(theme.textPrimary) } }
                                Text { attr { text(Format.percent(item.changePercent)); marginTop(3f); fontSizeScaled(12f); fontWeightSemiBold(); color(page.changeColor(item.changePercent)) } }
                                event { click { page.openIndexDetail(item) } }
                            }
                        }
                    }
                }
                // The Hero glass card is never wrapped in a vbind: it carries the
                // tick flash, so its views must survive a data update or the
                // rebuild eats the animation registration. Frame sync happens via
                // attr reads of displayOverview()（注册 scrubMinute 依赖，就地更新）。
                View {
                    attr { marginTop(-22f); padding(17f); borderRadius(20f); backgroundColor(theme.marketGlass); border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge)); boxShadow(BoxShadow(0f, 10f, 24f, theme.textPrimary.opacity(0.10f))) }
                    event {
                        click { page.openIndexDetail(page.displayOverview().indices.firstOrNull()) }
                        longPress { params -> if (params.state == "start") page.showIndexPeek() }
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            opacity(if (page.heroEntered || !page.motionEnabled()) 1f else 0f)
                            if (page.motionEnabled()) {
                                val entered = page.heroEntered
                                transform(scale = Scale(if (entered) 1f else 0.94f, if (entered) 1f else 0.94f))
                                animate(Animation.springEaseOut(0.24f, 0.82f, 0.16f), page.heroEntered)
                            }
                        }
                        View { attr { flex(1f) }
                            Text { attr { text(page.displayOverview().indices.firstOrNull()?.name ?: "行情接入中"); fontSizeScaled(12f); color(theme.textSecondary) } }
                            View {
                                attr {
                                    marginTop(4f); paddingLeft(4f); paddingRight(4f); borderRadius(4f)
                                    backgroundColor(page.tickBackground(theme))
                                    // animate last so the key is tickPhase, not the
                                    // tickDirection read inside tickBackground().
                                    if (page.motionEnabled()) animate(Animation.easeOut(0.30f), page.tickPhase)
                                }
                                Text {
                                    attr {
                                        text(page.displayOverview().indices.firstOrNull()?.let { Format.price(it.price) } ?: "--")
                                        fontSizeScaled(38f); lineHeightScaled(43f); fontWeightBold()
                                        color(if (page.tickPhase in 1..2) page.changeColor(page.tickDirection) else theme.textPrimary)
                                    }
                                }
                            }
                        }
                        View {
                            attr { padding(7f); borderRadius(7f); backgroundColor(page.changeSoft(page.displayOverview().indices.firstOrNull()?.changePercent ?: 0.0)) }
                            Text { attr { text(Format.percent(page.displayOverview().indices.firstOrNull()?.changePercent ?: 0.0)); fontSizeScaled(15f); fontWeightBold(); color(page.changeColor(page.displayOverview().indices.firstOrNull()?.changePercent ?: 0.0)) } }
                        }
                    }
                    View { attr { marginTop(11f); height(34f); flexDirectionRow(); alignItemsFlexEnd(); opacity(if (page.refreshing) 0.72f else 1f) }
                        listOf(11f, 16f, 13f, 22f, 18f, 26f, 21f, 30f, 24f, 33f, 28f, 34f).forEachIndexed { index, height ->
                            View { attr { flex(1f); marginRight(if (index == 11) 0f else 3f); height(height); borderRadius(2f); backgroundColor(page.changeColor(page.displayOverview().indices.firstOrNull()?.changePercent ?: 0.0).opacity(if (index == 11) 0.92f else 0.25f)) } }
                        }
                    }
                    Text { attr { text(if (phase.live) "分时走势 · 最新点持续更新" else "收盘走势 · 非交易时段已静止"); marginTop(5f); fontSizeScaled(9.5f); color(theme.textTertiary) } }
                    View { attr { marginTop(10f); flexDirectionRow() }
                        MarketIndexFact("成交", theme) { page.displayOverview().turnoverAmount?.let(page::turnoverText) ?: "待接入" }
                        MarketIndexFact("最高", theme) { page.displayOverview().indices.firstOrNull()?.high?.let(Format::price) ?: "待接入" }
                        MarketIndexFact("最低", theme) { page.displayOverview().indices.firstOrNull()?.low?.let(Format::price) ?: "待接入" }
                    }
                }
                View { attr { marginTop(10f); flexDirectionRow(); flexWrapWrap() }
                    vbind({ page.displayOverview() }) {
                        val data = page.displayOverview()
                        data.indices.drop(3).take(6).forEach { item ->
                            val name = item.name
                            val change = item.changePercent
                            View { attr { height(28f); marginRight(6f); marginBottom(6f); paddingLeft(9f); paddingRight(9f); allCenter(); borderRadius(14f); backgroundColor(theme.surface); border(Border(1f, BorderStyle.SOLID, if (abs(change) > 2.0) page.changeColor(change).opacity(0.52f) else theme.divider)) }
                                Text { attr { text("$name ${Format.percent(change)}"); fontSizeScaled(10.5f); color(page.changeColor(change)) } }
                                event { click { page.openIndexDetail(item) } }
                            }
                        }
                        if (data.indices.size <= 3) Text { attr { text("更多指数数据接入中"); marginTop(5f); fontSizeScaled(10f); color(theme.textTertiary) } }
                    }
                }

                // ① · 今日叙事轴：全页唯一主视觉 + 时间机器 scrub（②）。
                MarketSectionTitle("今日叙事轴", "多空宽度 · 事件钉 · 拖动回放整页", theme)
                View { attr { padding(10f); paddingRight(12f); borderRadius(18f); backgroundColor(theme.marketGlass); border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge)); boxShadow(BoxShadow(0f, 9f, 22f, theme.textPrimary.opacity(0.08f))) }
                    View { attr { paddingLeft(4f); paddingBottom(2f); flexDirectionRow(); alignItemsCenter() }
                        Text { attr { text(page.axisMetricLabel()); fontSizeScaled(11.5f); fontWeightBold(); color(theme.textPrimary); flex(1f) } }
                        Text { attr { text("拖动 = 整页回放"); fontSizeScaled(9.5f); fontWeightSemiBold(); color(theme.brand) } }
                    }
                    View { attr { marginTop(6f); flexDirectionRow() }
                        listOf("红盘率", "上证", "创业板", "北证50").forEachIndexed { index, label ->
                            View { attr { height(24f); marginRight(6f); paddingLeft(10f); paddingRight(10f); allCenter(); borderRadius(12f); backgroundColor(if (page.axisMetric == index) theme.brand else theme.surfaceMuted) }
                                Text { attr { text(label); fontSizeScaled(10f); fontWeightSemiBold(); color(if (page.axisMetric == index) theme.onBrand else theme.textSecondary) } }
                                event { click { page.axisMetric = index } }
                            }
                        }
                    }
                    MarketNarrativeAxis(
                        theme = theme,
                        height = 150f,
                        containerWidth = page.pagerData.pageViewWidth - 28f - 24f,
                        series = { page.axisSeries() },
                        density = { page.axisDensity() },
                        baseValue = { page.axisBaseValue() },
                        events = { page.cachedEvents },
                        scrubMinute = { page.scrubMinute },
                        timeLabel = { minute -> timeLabelOf(minute) },
                        onScrub = { minute -> page.onAxisScrub(minute) },
                        onScrubEnd = { _ -> /* 松手停在该帧，「回到现在」chip 收口 */ },
                        onTapPin = { event -> page.scrubTo(event.minute) },
                    )
                    Text { attr { text(if (page.scrubMinute >= 0) "正在看 ${timeLabelOf(page.scrubMinute)} 的市场 · 松手停在该帧 · 点钉子直达" else "拖动任意位置，整页回放到那一刻"); marginTop(4f); fontSizeScaled(10f); color(theme.brand) } }
                }

                // ③ · 关键时刻：端侧事件检测（L2 事件才上钉），点卡跳帧。
                vif({ page.cachedEvents.isNotEmpty() }) {
                    MarketSectionTitle("关键时刻", "端侧事件检测 · 点卡跳帧", theme)
                        Scroller {
                        attr { flexDirectionRow(); height(96f); showScrollerIndicator(false) }
                        vbind({ page.snapshotFrames.size to page.scrubMinute }) {
                            page.cachedEvents.forEach { event ->
                                View {
                                    attr {
                                        width(172f); marginRight(8f); padding(11f); paddingTop(9f); paddingBottom(9f); borderRadius(14f)
                                        backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f)))
                                        border(Border(if (abs(page.scrubMinute - event.minute) <= 6) 1.5f else 0f, BorderStyle.SOLID, theme.brand))
                                    }
                                    Text { attr { text("${event.timeLabel} · ${event.title}"); fontSizeScaled(11f); fontWeightBold(); color(theme.brand) } }
                                    Text { attr { text(event.fact); marginTop(5f); fontSizeScaled(10f); lineHeightScaled(15f); color(theme.textSecondary) } }
                                    event { click { page.scrubTo(event.minute) } }
                                }
                            }
                        }
                    }
                }

                // B · AI 复盘卡（doc 36 ④ 可导航版）：时间锚点由端侧事件检测生成，
                // 点句子跳帧；scrub 经过区间时句子点亮（双向）。流式主体沿用 doc 31。
                View {
                    attr {
                        marginTop(22f); padding(15f); borderRadius(16f); backgroundColor(theme.brandSoft); border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(0.14f)))
                        opacity(if (page.stripEntered || !page.motionEnabled()) 1f else 0f)
                        if (page.motionEnabled()) {
                            val entered = page.stripEntered
                            transform(
                                rotate = Rotate.DEFAULT,
                                scale = Scale(if (entered) 1f else 0.98f, if (entered) 1f else 0.98f),
                                translate = Translate(0f, 0f, 0f, if (entered) 0f else 10f),
                                anchor = Anchor.DEFAULT,
                                skew = Skew.DEFAULT,
                            )
                            animate(Animation.easeOut(0.28f).delay(0.18f), page.stripEntered)
                        }
                    }
                    View { attr { flexDirectionRow(); alignItemsCenter() }
                        View { attr { width(22f); height(22f); borderRadius(7f); backgroundColor(theme.brand); allCenter() }
                            Text { attr { text("✦"); fontSizeScaled(12f); fontWeightBold(); color(theme.onBrand) } }
                        }
                        Text { attr { text("让 AI 讲讲今天 · 可导航复盘"); marginLeft(8f); flex(1f); fontSizeScaled(14f); fontWeightBold(); color(theme.textPrimary) } }
                        Text { attr { text(page.aiActionLabel()); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.brand) } }
                        event { click { page.toggleAiBriefing() } }
                    }
                    vif({ page.cachedEvents.isNotEmpty() }) {
                        View { attr { marginTop(8f); padding(9f); borderRadius(10f); backgroundColor(theme.surface) }
                            Text { attr { text("每句话都是一个时间锚点，点了就走过去"); fontSizeScaled(9.5f); color(theme.textTertiary) } }
                            vbind({ page.snapshotFrames.size to page.scrubMinute }) {
                                page.cachedEvents.take(3).forEach { event ->
                                    val lit = page.scrubMinute >= event.segStart && page.scrubMinute <= event.segEnd
                                    View { attr { marginTop(6f); paddingLeft(7f); borderLeft(Border(2f, BorderStyle.SOLID, if (lit) theme.brand else theme.divider)); backgroundColor(if (lit) theme.brandSoft else theme.brandSoft.opacity(0f)) }
                                        Text { attr { text("${event.timeLabel} ${event.title}：${event.fact} ›"); fontSizeScaled(10.5f); lineHeightScaled(16f); color(if (lit) theme.textPrimary else theme.textSecondary) } }
                                        event { click { page.scrubTo(event.segStart) } }
                                    }
                                }
                            }
                        }
                    }
                    vif({ page.aiState == 0 }) {
                        Text { attr { text("基于本页已展示的指数、宽度、量能与情绪数据，让 AI 用一段话讲清今天市场的结构。"); marginTop(10f); fontSizeScaled(11f); lineHeightScaled(16f); color(theme.textSecondary) } }
                    }
                    vif({ page.aiState == 1 }) {
                        View { attr { marginTop(12f) }
                            View {
                                attr {
                                    height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.16f))
                                    opacity(if (!page.motionEnabled()) 0.6f else if (page.aiPulse) 1f else 0.45f)
                                    if (page.motionEnabled()) {
                                        val pulsing = page.aiPulse
                                        animate(Animation.easeOut(0.5f), page.aiPulse)
                                    }
                                }
                            }
                            View {
                                attr {
                                    marginTop(8f); marginRight(110f); height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.16f))
                                    opacity(if (!page.motionEnabled()) 0.6f else if (page.aiPulse) 1f else 0.45f)
                                    if (page.motionEnabled()) {
                                        val pulsing = page.aiPulse
                                        animate(Animation.easeOut(0.5f), page.aiPulse)
                                    }
                                }
                            }
                            Text { attr { text("正在汇总页面数据（指数 · 宽度 · 量能 · 情绪）…"); marginTop(10f); fontSizeScaled(10f); color(theme.brand) } }
                        }
                    }
                    vif({ page.aiState == 2 || page.aiState == 3 || page.aiState == 4 }) {
                        Text {
                            attr {
                                text(page.aiText + if (page.aiState == 2) " ▍" else "")
                                marginTop(10f); fontSizeScaled(12f); lineHeightScaled(20f)
                                color(if (page.aiState == 4) theme.fall else theme.textPrimary)
                            }
                        }
                    }
                    vif({ page.aiState == 3 }) {
                        View { attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
                            Text { attr { text("AI 生成 · 仅供参考，不构成投资建议 · 锚点由端侧规则生成"); flex(1f); fontSizeScaled(9.5f); color(theme.textTertiary) } }
                            Text { attr { text("追问 ›"); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.brand) }
                                event { click { page.openChatWithQuestion(page.aiFollowUpQuestion()) } } }
                        }
                    }
                }

                // ---------- 第二层 · 数据切片层（4 选 1，替代九区块纵向流） ----------
                View { attr { marginTop(22f); padding(3f); flexDirectionRow(); borderRadius(15f); backgroundColor(theme.surfaceMuted) }
                    listOf("宽度情绪", "量能资金", "板块竞速", "连板梯队").forEachIndexed { index, label ->
                        View {
                            attr {
                                flex(1f); height(30f); allCenter(); borderRadius(12f)
                                val selected = page.activeSlice == index
                                backgroundColor(if (selected) theme.surface else theme.surfaceMuted)
                                if (page.motionEnabled()) {
                                    transform(scale = Scale(if (selected) 1f else 0.96f, if (selected) 1f else 0.96f))
                                    animate(Animation.easeOut(0.18f), page.activeSlice)
                                }
                            }
                            Text { attr { text(label); fontSizeScaled(10.5f); fontWeightMedium(); color(if (page.activeSlice == index) theme.textPrimary else theme.textTertiary) } }
                            event { click { page.activeSlice = index } }
                        }
                    }
                }

                // 切片 0 · 宽度情绪（原 D/E 区块合并，全部随帧更新）
                vif({ page.activeSlice == 0 }) {
                    View { attr { marginTop(10f); padding(15f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                        vbind({ page.displayOverview() }) {
                            val data = page.displayOverview()
                            val total = max(1, data.risingCount + data.fallingCount + data.flatCount)
                            val risingFraction = data.risingCount.toFloat() / total
                            val flatFraction = data.flatCount.toFloat() / total
                            val fallingFraction = data.fallingCount.toFloat() / total
                            View { attr { flexDirectionRow(); alignItemsFlexEnd() }
                                View { attr { flex(1f) }; Text { attr { text(data.risingCount.toString()); fontSizeScaled(25f); fontWeightBold(); color(theme.rise) } }; Text { attr { text("上涨"); marginTop(2f); fontSizeScaled(10f); color(theme.textTertiary) } } }
                                View { attr { width(82f); allCenter() }
                                    Text {
                                        attr {
                                            text("红盘 ${Format.decimal(risingFraction.toDouble() * 100, 1)}%")
                                            fontSizeScaled(11f); fontWeightSemiBold(); color(theme.textSecondary)
                                            opacity(if (page.breadthEntered || !page.motionEnabled()) 1f else 0f)
                                            if (page.motionEnabled()) {
                                                val entered = page.breadthEntered
                                                animate(Animation.easeOut(0.30f).delay(0.36f), page.breadthEntered)
                                            }
                                        }
                                    }
                                }
                                View { attr { flex(1f) }; Text { attr { text(data.fallingCount.toString()); textAlignRight(); fontSizeScaled(25f); fontWeightBold(); color(theme.fall) } }; Text { attr { text("下跌"); marginTop(2f); textAlignRight(); fontSizeScaled(10f); color(theme.textTertiary) } } }
                            }
                            View {
                                attr {
                                    marginTop(13f); height(10f); flexDirectionRow(); borderRadius(5f); overflow(true); backgroundColor(theme.surfaceMuted)
                                    if (page.motionEnabled()) {
                                        val entered = page.breadthEntered
                                        transform(
                                            rotate = Rotate.DEFAULT,
                                            scale = Scale(if (entered) 1f else 0.02f, 1f),
                                            translate = Translate.DEFAULT,
                                            anchor = Anchor.DEFAULT,
                                            skew = Skew.DEFAULT,
                                        )
                                        animate(Animation.easeOut(0.52f), page.breadthEntered)
                                    }
                                }
                                View { attr { flex(risingFraction); backgroundColor(theme.rise) } }
                                View { attr { flex(flatFraction); backgroundColor(theme.flat) } }
                                View { attr { flex(fallingFraction); backgroundColor(theme.fall) } }
                            }
                            Text { attr { text("涨跌差 ${data.risingCount - data.fallingCount} · 平盘 ${data.flatCount}"); marginTop(8f); fontSizeScaled(10f); color(theme.textTertiary) } }
                        }
                        // 短线情绪四维：单块玻璃面板 + 发丝竖线（不是四张卡）
                        View { attr { marginTop(13f); paddingTop(12f); borderTop(Border(1f, BorderStyle.SOLID, theme.divider)); flexDirectionRow() }
                            (0..3).forEach { index ->
                                View {
                                    attr {
                                        flex(1f); paddingLeft(if (index == 0) 0f else 7f); paddingRight(7f)
                                        if (index < 3) borderRight(Border(0.5f, BorderStyle.SOLID, theme.divider))
                                        if (page.shortTermAlert() && index >= 2) {
                                            borderRadius(8f)
                                            backgroundColor(theme.fall)
                                            if (page.motionEnabled()) {
                                                val pulsing = page.emotionPulse
                                                transform(scale = Scale(if (pulsing) 1.03f else 1f, if (pulsing) 1.03f else 1f))
                                                animate(Animation.easeOut(0.60f), page.emotionPulse)
                                            }
                                        }
                                    }
                                    Text { attr { text(page.shortTermMetric(index).first); fontSizeScaled(10f); color(if (page.shortTermAlert() && index >= 2) theme.onBrand.opacity(0.78f) else theme.textTertiary) } }
                                    Text { attr { text(page.shortTermMetric(index).second); marginTop(7f); fontSizeScaled(17f); fontWeightBold(); color(if (page.shortTermAlert() && index >= 2) theme.onBrand else if (index == 0) theme.rise else if (index == 1) theme.fall else theme.textPrimary) } }
                                    Text { attr { text(page.shortTermMetric(index).third); marginTop(4f); fontSizeScaled(8.5f); lineHeightScaled(12f); color(if (page.shortTermAlert() && index >= 2) theme.onBrand.opacity(0.76f) else theme.textTertiary) } }
                                    event {
                                        click {
                                            val value = page.shortTermMetric(index).second
                                            when (index) {
                                                0 -> page.showShortTermMetricPeek("涨停", value, "样本为当日收盘封住涨停的证券；一字板数量暂未单列")
                                                1 -> page.showShortTermMetricPeek("跌停", value, "样本为当日收盘封住跌停的证券；昨日对照暂未单列")
                                                2 -> page.showShortTermMetricPeek("封板率", value, "未开板涨停样本 ÷ 涨停样本；炸板次数取涨停池 zbc 字段")
                                                else -> page.showShortTermMetricPeek("最高连板", value, "取涨停池 lbc 的最大值；昨日高度用于观察高度变化")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 切片 1 · 量能资金（原 D/I 区块合并）
                vif({ page.activeSlice == 1 }) {
                    View { attr { marginTop(10f); padding(15f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                        vbind({ page.displayOverview() }) {
                            val data = page.displayOverview()
                            val volume = data.turnoverAmount
                            val volumeDeviation = page.volumeDeviation()
                            View { attr { flexDirectionRow(); alignItemsFlexEnd() }
                                Text { attr { text(volume?.let(page::turnoverText) ?: "--"); fontSizeScaled(27f); lineHeightScaled(31f); fontWeightBold(); color(theme.textPrimary) } }
                                Text { attr { text(volumeDeviation?.let { "较5日均 ${Format.percent(it)}" } ?: "成交额待接入"); marginLeft(8f); marginBottom(3f); fontSizeScaled(10.5f); color(volumeDeviation?.let(page::changeColor) ?: theme.flat) } }
                            }
                            listOf("今日" to volume, "昨日" to data.yesterdayTurnoverAmount, "5日均" to data.fiveDayAverageTurnoverAmount).forEachIndexed { index, (label, value) ->
                                val width = if (value != null && volume != null && volume > 0) (154f * (value / volume).toFloat()).coerceIn(14f, 154f) else 26f
                                View { attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
                                    Text { attr { text(label); width(34f); fontSizeScaled(10f); color(theme.textTertiary) } }
                                    View { attr { width(160f); height(6f); borderRadius(3f); backgroundColor(theme.surfaceMuted) }
                                        View {
                                            attr {
                                                width(if (!page.motionEnabled() || page.volumeEntered) width else 0.02f)
                                                height(6f); borderRadius(3f); backgroundColor(if (index == 0) theme.brand else theme.textTertiary.opacity(0.38f))
                                                if (page.motionEnabled()) {
                                                    val entered = page.volumeEntered
                                                    animate(Animation.easeOut(0.32f).delay(0.04f * index), page.volumeEntered)
                                                }
                                            }
                                        }
                                    }
                                    Text { attr { text(value?.let(page::turnoverText) ?: "--"); flex(1f); marginLeft(8f); textAlignRight(); fontSizeScaled(10f); color(theme.textSecondary) } }
                                }
                            }
                            listOf(
                                Triple("北向资金", data.northboundFlow?.let(Format::compactAmount) ?: "待接入", "沪深股通拆分与占成交比待接入"),
                                Triple("行业主力", data.sectors.firstOrNull()?.name ?: "待接入", "主力净流入排名"),
                            ).forEachIndexed { index, row ->
                                View { attr { marginTop(if (index == 0) 13f else 0f); paddingTop(10f); paddingBottom(10f); flexDirectionRow(); alignItemsCenter(); borderTop(Border(1f, BorderStyle.SOLID, theme.divider.opacity(0.72f))) }
                                    View { attr { flex(1f) }; Text { attr { text(row.first); fontSizeScaled(12f); fontWeightSemiBold(); color(theme.textPrimary) } }; Text { attr { text(row.third); marginTop(3f); fontSizeScaled(9.5f); color(theme.textTertiary) } } }
                                    Text { attr { text(row.second); width(84f); textAlignRight(); fontSizeScaled(12f); fontWeightSemiBold(); color(if (row.first == "北向资金") page.changeColor(data.northboundFlow ?: 0.0) else theme.textPrimary) } }
                                    event {
                                        click {
                                            val detail = if (index == 0) {
                                                "当前展示净流向；沪股通与深股通拆分、占成交比尚未纳入，不能与其他平台口径直接相减"
                                            } else {
                                                "按板块主力净流入排序；当前第一名为 ${data.sectors.firstOrNull()?.name ?: "--"}"
                                            }
                                            page.showFundMethodology(row.first, row.second, detail)
                                        }
                                    }
                                }
                            }
                        }
                        Text { attr { text("北向资金存在不同统计口径，展示前需核对来源与时间范围。"); marginTop(8f); fontSizeScaled(10f); lineHeightScaled(15f); color(theme.flat) } }
                    }
                }

                // 切片 2 · 板块竞速场（⑤）：位次相对 30 分钟前，回放态才显箭头。
                vif({ page.activeSlice == 2 }) {
                    View { attr { marginTop(10f); padding(15f); paddingBottom(16f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                        vbind({ page.displayOverview() to page.scrubMinute }) {
                            val rows = page.raceRows()
                            if (rows.isEmpty()) {
                                Text { attr { text("板块数据接入中，暂无可竞速的样本"); fontSizeScaled(11f); color(theme.textTertiary) } }
                            }
                            rows.forEach { row ->
                                View { attr { marginTop(if (row.rank == 1) 0f else 9f); flexDirectionRow(); alignItemsCenter() }
                                    Text { attr { text("${row.rank}"); width(22f); fontSizeScaled(11f); fontWeightBold(); color(theme.textTertiary) } }
                                    Text { attr { text(row.sector.name); width(86f); fontSizeScaled(12.5f); fontWeightSemiBold(); color(theme.textPrimary) } }
                                    View { attr { flex(1f); height(14f); borderRadius(4f); backgroundColor(theme.surfaceMuted); overflow(true) }
                                        View { attr { width((180f * (abs(row.sector.changePercent) / 7.0).toFloat()).coerceIn(8f, 180f)); height(14f); borderRadius(4f); backgroundColor(page.changeColor(row.sector.changePercent).opacity(0.30f)) } }
                                    }
                                    Text { attr { text(Format.percent(row.sector.changePercent)); width(56f); textAlignRight(); fontSizeScaled(12f); fontWeightBold(); color(page.changeColor(row.sector.changePercent)) } }
                                    Text { attr { text(if (page.scrubMinute < 0) "—" else when { row.delta > 0 -> "↑${row.delta}"; row.delta < 0 -> "↓${-row.delta}"; else -> "—" }); width(32f); textAlignRight(); fontSizeScaled(9.5f); fontWeightBold(); color(if (row.delta > 0 && page.scrubMinute >= 0) theme.brand else theme.textTertiary) } }
                                    event { click { page.showSectorPeek(row.sector.code) } }
                                }
                            }
                        }
                        Text { attr { text("位次相对 30 分钟前（F1 Timing Tower 式超车）· 实时态箭头静默 · 拖动叙事轴时按帧重排"); marginTop(10f); fontSizeScaled(9.5f); lineHeightScaled(14f); color(theme.textTertiary) } }
                    }
                }

                // 切片 3 · 连板梯队（原 G 区块）：条宽编码家数，昨日高度虚线残影。
                vif({ page.activeSlice == 3 }) {
                    View { attr { marginTop(10f); padding(15f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                        vbind({ page.displayOverview() }) {
                            val yesterdayBoard = page.displayOverview().yesterdayHighestBoard ?: 0
                            val highestBoard = page.displayOverview().highestBoard ?: 0
                            if (yesterdayBoard > highestBoard) {
                                Text { attr { text("┄┄ 昨日最高 $yesterdayBoard 板 · 高度坍塌残影"); fontSizeScaled(10f); color(theme.flat) } }
                            }
                        }
                        (1..4).reversed().forEachIndexed { index, level ->
                            View {
                                attr {
                                    marginTop(if (index == 0) 9f else 7f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    opacity(if (page.ladderEntered || !page.motionEnabled()) 1f else 0f)
                                    if (page.motionEnabled()) {
                                        val entered = page.ladderEntered
                                        transform(
                                            rotate = Rotate.DEFAULT,
                                            scale = Scale.DEFAULT,
                                            translate = Translate(0f, 0f, 0f, if (entered) 0f else 12f),
                                            anchor = Anchor.DEFAULT,
                                            skew = Skew.DEFAULT,
                                        )
                                        animate(Animation.easeOut(0.28f).delay(0.06f * (level - 1)), page.ladderEntered)
                                    }
                                }
                                Text { attr { text(if (level == 1) "首板" else "${level}板"); width(34f); fontSizeScaled(10f); color(theme.textSecondary) } }
                                View {
                                    attr {
                                        val count = page.ladderCount(level)
                                        width(if (count > 0) (180f * count / page.ladderMax()).coerceAtLeast(15f) else 15f)
                                        height(20f)
                                        borderRadius(5f)
                                        backgroundColor(if (count > 0) theme.rise.opacity(0.25f + index * 0.12f) else theme.surfaceMuted)
                                        if (page.motionEnabled()) {
                                            val expanded = page.expandedBoardLevel == level
                                            transform(scale = Scale(if (expanded) 1.03f else 1f, if (expanded) 1.03f else 1f))
                                            animate(Animation.easeOut(0.18f), page.expandedBoardLevel)
                                        }
                                    }
                                    Text { attr { text(page.ladderCount(level).let { if (it > 0) " $it" else " —" }); fontSizeScaled(10f); fontWeightSemiBold(); color(if (page.ladderCount(level) > 0) theme.rise else theme.textTertiary) } }
                                    event { click { page.toggleBoardLevel(level) }; longPress { params -> if (params.state == "start") page.showBoardPeek(level) } }
                                }
                            }
                            vif({ page.expandedBoardLevel == level }) {
                                View { attr { marginLeft(34f); marginTop(5f); padding(8f); borderRadius(8f); backgroundColor(theme.riseSoft); opacity(0.96f) }
                                    Text { attr { text(page.boardSampleText(level)); fontSizeScaled(10f); lineHeightScaled(15f); color(theme.textSecondary) } }
                                }
                            }
                        }
                        Text { attr { text("条宽表示涨停池样本家数 · 点按展开样本 · 长按快速预览"); marginTop(10f); fontSizeScaled(10f); color(theme.textTertiary) } }
                    }
                }

                // ---------- 第三层 · 口径收口 ----------
                vbind({ page.displayOverview() }) {
                    val samples = page.displayOverview().breadthSamples
                    if (samples.isNotEmpty()) {
                        val uncoveredTotal = samples.sumOf { it.uncovered }
                        Text {
                            attr {
                                text(
                                    "情绪算法样本（按大类）：" + samples.joinToString(" · ") { sample ->
                                        "${sample.name} ${sample.counted}/${sample.total}"
                                    } + if (uncoveredTotal > 0) "；停牌等未计入 ${uncoveredTotal} 只" else "，全部计入"
                                )
                                marginTop(14f)
                                fontSizeScaled(10f)
                                lineHeightScaled(15f)
                                color(theme.textTertiary)
                            }
                        }
                    }
                }
                vbind({ page.overview.stamp }) {
                    SourceStampLine(page.overview.stamp, theme)
                }
                Text { attr { text("数据仅供信息参考，不构成投资建议。行情数据可能延迟或存在不同统计口径。回放态数字均为历史事实，不构成任何预测。"); marginTop(10f); fontSizeScaled(10f); lineHeightScaled(15f); color(theme.textTertiary) } }
            }

            // ② 回放水印 chip + 一键回现在（三重防误读之一；挂载不动画为 R4 已知限制，可接受）。
            vif({ page.replayChipVisible }) {
                View {
                    attr {
                        absolutePosition(top = page.pagerData.statusBarHeight + 58f, left = 64f, right = 50f)
                        zIndex(15, useOutline = false)
                        height(30f); paddingLeft(12f); paddingRight(6f)
                        flexDirectionRow(); alignItemsCenter()
                        borderRadius(15f)
                        backgroundColor(page.theme.brand)
                        boxShadow(BoxShadow(0f, 6f, 18f, page.theme.brand.opacity(0.35f)))
                    }
                    Text { attr { text("正在看 ${timeLabelOf(page.scrubMinute)} 的市场 · 历史回放"); fontSizeScaled(10.5f); fontWeightSemiBold(); color(page.theme.onBrand); flex(1f) } }
                    View { attr { height(22f); paddingLeft(9f); paddingRight(9f); allCenter(); borderRadius(11f); backgroundColor(page.theme.onBrand.opacity(0.22f)) }
                        Text { attr { text("回到现在 ›"); fontSizeScaled(10f); fontWeightSemiBold(); color(page.theme.onBrand) } }
                        event { click { page.backToNow() } }
                    }
                }
            }

            vif({ page.peek != null }) {
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        zIndex(20, useOutline = false)
                        backgroundColor(page.theme.textPrimary.opacity(0.26f))
                        val shown = page.peekVisible
                        opacity(if (shown) 1f else 0f)
                        touchEnable(shown)
                        if (page.motionEnabled()) animate(Animation.easeOut(0.20f), page.peekVisible)
                    }
                    event { click { page.dismissPeek() } }
                }
                View {
                    attr {
                        absolutePosition(left = 14f, right = 14f, bottom = 18f + page.pagerData.safeAreaInsets.bottom)
                        zIndex(21, useOutline = false)
                        padding(16f)
                        borderRadius(18f)
                        backgroundColor(page.theme.marketGlass)
                        border(Border(1f, BorderStyle.SOLID, page.theme.marketGlassEdge))
                        boxShadow(BoxShadow(0f, 14f, 30f, page.theme.textPrimary.opacity(0.18f)))
                        val shown = page.peekVisible
                        opacity(if (shown) 1f else 0f)
                        touchEnable(shown)
                        if (page.motionEnabled()) {
                            transform(scale = Scale(if (shown) 1f else 0.96f, if (shown) 1f else 0.96f))
                            animate(Animation.easeOut(0.20f), page.peekVisible)
                        }
                    }
                    View { attr { flexDirectionRow(); alignItemsCenter() }
                        Text { attr { text(page.peek?.title ?: ""); flex(1f); fontSizeScaled(13f); fontWeightBold(); color(page.theme.textPrimary) } }
                        Text { attr { text("关闭"); fontSizeScaled(11f); color(page.theme.brand) }; event { click { page.dismissPeek() } } }
                    }
                    Text { attr { text(page.peek?.primary ?: ""); marginTop(8f); fontSizeScaled(23f); fontWeightBold(); color(page.theme.textPrimary) } }
                    Text { attr { text(page.peek?.detail ?: ""); marginTop(6f); fontSizeScaled(11f); lineHeightScaled(16f); color(page.theme.textSecondary) } }
                }
            }
            AppTopBar(
                title = "市场",
                subtitle = "数字之后，解释今天发生了什么",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
                compactVisible = { page.stickyIndexVisible },
                reduceMotion = page.reduceMotion,
                compactMetrics = {
                    page.displayOverview().indices.take(3).mapIndexed { index, item ->
                        val flash = page.tickPhase in 1..2 && index == 0
                        AppTopBarMetric(
                            label = item.name,
                            value = Format.price(item.price),
                            change = Format.percent(item.changePercent),
                            changeColor = page.changeColor(if (flash) page.tickDirection else item.changePercent),
                            flash = flash,
                        )
                    }
                },
                actions = listOf("刷新" to { page.refreshOverview() }, "日历" to { page.openPage(Routes.CALENDAR) }),
            )
        }
    }

    // ------------------------------------------------------------------
    // 派生文案与口径
    // ------------------------------------------------------------------

    private fun changeColor(value: Double): Color = if (value > 0) theme.rise else if (value < 0) theme.fall else theme.flat
    private fun changeSoft(value: Double): Color = if (value > 0) theme.riseSoft else if (value < 0) theme.fallSoft else theme.surfaceMuted
    private fun tickBackground(theme: StockChatTheme): Color = when {
        !motionEnabled() || tickPhase == 0 || tickPhase >= 3 -> theme.marketGlass.opacity(0f)
        tickPhase == 1 -> changeSoft(tickDirection)
        else -> changeSoft(tickDirection).opacity(0.42f)
    }

    private fun refreshStatusText(): String = when {
        refreshResultVisible -> "已更新"
        refreshing -> "正在刷新…"
        pullReady -> "松手刷新"
        pullDistance > 0f -> "上次更新 ${overview.stamp.asOf}"
        else -> "${overview.stamp.mode.quoteLabel()} · ${overview.stamp.asOf}"
    }

    private fun volumeDeviation(): Double? {
        val volume = overview.turnoverAmount ?: return null
        val average = overview.fiveDayAverageTurnoverAmount ?: return null
        if (average <= 0) return null
        return (volume / average - 1) * 100
    }

    /** L2 significance gate: the seal rate falling under 80% is a real warning. */
    private fun shortTermAlert(): Boolean = (displayOverview().sealRate ?: 1.0) < 0.80

    private fun shortTermMetric(index: Int): Triple<String, String, String> {
        val data = displayOverview()
        return when (index) {
            0 -> Triple("涨停", data.limitUpCount.toString(), "一字待接入")
            1 -> Triple("跌停", data.limitDownCount.toString(), "昨日待接入")
            2 -> Triple("封板率", data.sealRate?.let { Format.percent(it * 100) } ?: "--", "炸板 ${data.brokenBoardCount ?: "--"} 只")
            else -> Triple("最高板", data.highestBoard?.let { "${it}板" } ?: "--", "昨 ${data.yesterdayHighestBoard ?: "--"} 板")
        }
    }

    private fun ladderCount(level: Int): Int = hotspots.limitUps.count { it.consecutiveBoards == level }
    private fun ladderMax(): Int = (1..4).maxOf { ladderCount(it) }.coerceAtLeast(1)
    private fun boardSampleText(level: Int): String = hotspots.limitUps
        .filter { it.consecutiveBoards == level }
        .take(4)
        .joinToString(" · ") { it.name }
        .ifBlank { "当前数据源暂无该层级股票样本" }

    private fun turnoverText(value: Double): String = "${Format.decimal(value / 1_000_000_000_000.0, 2)}万亿"
    private fun moodLabel(score: Int): String = when (score) { in 0..25 -> "偏冷"; in 26..45 -> "偏弱"; in 46..60 -> "均衡"; in 61..80 -> "偏暖"; else -> "较热" }
    private fun moodHeadline(data: MarketOverview): String = when {
        data.indices.isEmpty() && data.risingCount == 0 && data.fallingCount == 0 -> "行情接入中，数据马上就位"
        data.moodScore in 0..25 -> "情绪走弱，个股承压"
        data.moodScore in 26..45 -> "指数与情绪同步偏弱"
        data.moodScore in 46..60 -> "多空均衡，结构分化"
        data.moodScore in 61..80 -> "上涨占优，热度回升"
        else -> "市场热度较高，分化仍在"
    }
    private fun moodColor(score: Int): Color = if (score >= 50) theme.rise else theme.fall
    private fun threshold(change: Double?): String = when { change == null -> "关口待接入"; change <= -2 -> "关键关口承压"; change >= 2 -> "关键关口走强"; else -> "关口附近震荡" }
    private fun marketPhase(): MarketPhase = when (platformCurrentHour().coerceIn(0, 23)) { in 0..8 -> MarketPhase("盘前准备", false, "开盘前数据不代表成交结果"); 9 -> MarketPhase("集合竞价", true, "竞价阶段可能出现虚假大单，需以连续交易为准"); in 10..11 -> MarketPhase("早盘连续交易", true, null); 12 -> MarketPhase("午间休市", false, "休市期间行情静止，并非数据故障"); in 13..14 -> MarketPhase("午后连续交易", true, null); 15 -> MarketPhase("收盘集合 / 复盘", false, "收盘数据正在汇总"); in 16..18 -> MarketPhase("盘后静默期", false, "盘后数据可能陆续修订"); else -> MarketPhase("非交易时段", false, "显示最近一个交易日数据") }
    private data class MarketPhase(val label: String, val live: Boolean, val notice: String?)
    private data class MarketPeek(val title: String, val primary: String, val detail: String)

    // ⑥ 透明温度计：徽章配色与分量拆解文案（公式与 moodScore 同源）。
    private fun tempBadgeBackground(): Color {
        val score = displayOverview().moodScore
        return when {
            score < 42 -> theme.fallSoft
            score >= 55 -> theme.riseSoft
            else -> theme.surfaceMuted
        }
    }

    private fun tempBadgeForeground(): Color {
        val score = displayOverview().moodScore
        return when {
            score < 42 -> theme.fall
            score >= 55 -> theme.rise
            else -> theme.textSecondary
        }
    }

    private fun temperatureSummary(): String {
        val data = displayOverview()
        return "市场温度 ${data.moodScore}° · ${moodLabel(data.moodScore)}，${temperatureDriver(data)}"
    }

    private fun temperatureDriver(data: MarketOverview): String {
        val breadth = if (data.risingCount + data.fallingCount == 0) 50.0 else data.risingCount * 100.0 / (data.risingCount + data.fallingCount)
        return when {
            breadth >= 55 -> "宽度偏暖贡献为正"
            breadth <= 45 -> "宽度偏冷拖累为主"
            else -> "宽度中性，涨跌结构分化"
        }
    }

    private fun temperatureFormulaText(): String {
        val data = displayOverview()
        val total = data.risingCount + data.fallingCount
        val breadth = if (total == 0) 50.0 else data.risingCount * 100.0 / total
        val net = (data.limitUpCount - data.limitDownCount).coerceIn(-50, 50)
        return "温度 = 红盘率 × 0.7 + 涨停净热度 × 0.3。" +
            "当前：红盘率 ${Format.decimal(breadth, 1)}% → ${Format.decimal(breadth * 0.7, 1)}；" +
            "涨停净热度 $net → ${Format.decimal((50 + net) * 0.3, 1)}；" +
            "合计 ${data.moodScore}°（帧 ${displayOverview().stamp.asOf}）。"
    }
}

/** 温度日内轨迹 mini 曲线（⑥）：纯 Canvas 折线 + 游标，随帧同步。 */
private fun ViewContainer<*, *>.TemperatureSpark(
    theme: StockChatTheme,
    series: () -> List<Double>,
    scrubMinute: () -> Int,
) {
    val w = 88f
    val h = 30f
    Canvas({
        attr {
            width(w)
            height(h)
            marginLeft(8f)
        }
    }) { canvas, _, _ ->
        val s = series()
        if (s.size < 2) return@Canvas
        val d0 = (s.min() - 2).coerceAtMost(0.0)
        val d1 = (s.max() + 2).coerceAtLeast(100.0)
        val span = (d1 - d0).coerceAtLeast(1e-6)
        fun y(v: Double): Float = (h - 4f - ((v - d0) / span * (h - 10f)).toFloat())
        canvas.beginPath()
        for (t in s.indices) {
            val x = 4f + t.toFloat() / 240f * (w - 8f)
            if (t == 0) canvas.moveTo(x, y(s[t])) else canvas.lineTo(x, y(s[t]))
        }
        canvas.lineWidth(1.2f)
        canvas.lineCapRound()
        canvas.strokeStyle(theme.textTertiary)
        canvas.stroke()
        val scrub = scrubMinute()
        if (scrub in 0..240 && s.size > scrub) {
            val cx = 4f + scrub.toFloat() / 240f * (w - 8f)
            canvas.beginPath()
            canvas.arc(cx, y(s[scrub]), 2.6f, 0f, (2 * kotlin.math.PI).toFloat(), false)
            canvas.fillStyle(theme.brand)
            canvas.fill()
        }
    }
}

private fun ViewContainer<*, *>.MarketPill(text: String, color: Color) {
    View { attr { height(24f); marginRight(6f); marginBottom(5f); paddingLeft(8f); paddingRight(8f); allCenter(); borderRadius(12f); backgroundColor(color.opacity(0.11f)) }
        Text { attr { text(text); fontSizeScaled(10f); fontWeightMedium(); color(color) } }
    }
}

/** `value` is a closure so the figure is read inside attr and stays live. */
private fun ViewContainer<*, *>.MarketIndexFact(label: String, theme: StockChatTheme, value: () -> String) {
    View { attr { flex(1f) }
        Text { attr { text(label); fontSizeScaled(9.5f); color(theme.textTertiary) } }
        Text { attr { text(value()); marginTop(3f); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.textPrimary) } }
    }
}

private fun ViewContainer<*, *>.MarketSectionTitle(
    title: String,
    subtitle: String,
    theme: StockChatTheme,
    onTap: (() -> Unit)? = null,
) {
    View { attr { marginTop(23f); marginBottom(9f); flexDirectionRow(); alignItemsFlexEnd() }
        Text { attr { text(title); fontSizeScaled(17f); fontWeightBold(); color(theme.textPrimary) } }
        Text { attr { text(subtitle); marginLeft(7f); marginBottom(2f); fontSizeScaled(10f); color(theme.textTertiary) } }
        if (onTap != null) event { click { onTap() } }
    }
}
