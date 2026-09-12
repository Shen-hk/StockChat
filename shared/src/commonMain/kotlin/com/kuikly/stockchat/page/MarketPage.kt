package com.kuikly.stockchat.page

import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.app.assembly.ChatFeatureGraph
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openChatWithQuestion
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.common.openUrl
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.app.assembly.MarketDependencies
import com.kuikly.stockchat.app.assembly.MarketFeatureGraph
import com.kuikly.stockchat.data.provider.HotspotSnapshot
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.MarketIndex
import com.kuikly.stockchat.data.provider.MarketOverview
import com.kuikly.stockchat.data.provider.MarketSnapshotFrame
import com.kuikly.stockchat.data.provider.MarketSnapshotStore
import com.kuikly.stockchat.data.provider.MarketEvent
import com.kuikly.stockchat.data.provider.MarketEventDetector
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.SectorRank
import com.kuikly.stockchat.data.provider.SourceStamp
import com.kuikly.stockchat.data.provider.SourceTier
import com.kuikly.stockchat.data.provider.platformCurrentHour
import com.kuikly.stockchat.data.provider.platformCurrentMinuteOfDay
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.data.provider.timeLabelOf
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.AppTopBarAction
import com.kuikly.stockchat.foundation.ui.icon.LineIconCalendar
import com.kuikly.stockchat.foundation.ui.icon.LineIconRefresh
import com.kuikly.stockchat.page.components.AppTopBarMetric
import com.kuikly.stockchat.page.components.AtmosphereBackdrop
import com.kuikly.stockchat.page.components.MarketNarrativeAxis
import com.kuikly.stockchat.page.components.NewsMarquee
import com.kuikly.stockchat.foundation.ui.feedback.SourceStampLine
import com.kuikly.stockchat.page.components.formatTapeTime
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
    private val dependencies by lazy { MarketFeatureGraph.forPager(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }
    // 真实模式首帧先明确“连接中”，不能让演示数据短暂伪装成实时行情。
    private var overview: MarketOverview by observable(
        MarketOverview(
            indices = emptyList(), risingCount = 0, fallingCount = 0, flatCount = 0,
            limitUpCount = 0, limitDownCount = 0, sectors = emptyList(),
            stamp = SourceStamp("正在连接行情服务", "--", SourceTier.MARKET_DATA, DataMode.OFFLINE),
        ),
    )
    private var refreshing: Boolean by observable(false)
    /** 0 = no flash; 1/2/3 = the three phases of one tick sequence. */
    private var tickPhase: Int by observable(0)
    private var tickDirection: Double by observable(0.0)
    private var tickRevision = 0
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
    /** 市场页主区块按阶段淡入，避免首次进入时所有信息同时砸入视野。 */
    private var marketEntrancePhase: Int by observable(0)

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
    private var scrubTweenGeneration = 0
    private var liveRefreshGeneration = 0
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
    private val aiDependencies by lazy { ChatFeatureGraph.forPager(pagerId) }

    // ------------------------------------------------------------------
    // 新闻弹幕（J1 v2）：页侧持有节拍——setTimeout 链 33ms 步进 offset（≈30dp/s），
    // 暂停 = 停止步进（点条目开弹窗时暂停，弹窗关闭恢复），reduceMotion 不流动。
    // 弹窗详情 + AI 解读流式与 AI 复盘卡同一条程纪律（observable 只在主线程写）。
    // ------------------------------------------------------------------
    private var tapeOffset: Float by observable(0f)
    private var tapePaused: Boolean by observable(false)
    private var tapeTimerStarted = false

    private var newsPeek: NewsItem? by observable(null)
    private var newsPeekVisible: Boolean by observable(false)
    /** 弹窗卸载代数：快速关→开时使旧的延迟卸载回调失效。 */
    private var newsPeekGeneration = 0
    /** 0 idle / 1 thinking / 2 streaming / 3 done / 4 error / 5 未配置 AI。 */
    private var newsAiState: Int by observable(0)
    private var newsAiText: String by observable("")
    private var newsAiProvider: AiProvider? = null
    private var newsAiGeneration = 0
    private var newsAiTypewriter: TypewriterSmoother? = null

    /**
     * The page's single motion switch. Every transform/opacity timeline on this
     * page is gated on it, so there is exactly one place to disable motion.
     * scrub 本身不受它影响——用户主动手势且是信息本体（doc 36 N 系列兜底条款）。
     */
    private fun motionEnabled(): Boolean = !reduceMotion

    override fun created() {
        super.created()
        marketNews = OfflineMarketInsightProvider().marketNewsValue()
        startTapeTimer()
        // Entry motion is one-shot and decorative-free; it never repeats while prices update.
        if (motionEnabled()) {
            setTimeout(1) {
                heroEntered = true
                marketEntrancePhase = 1
            }
            setTimeout(75) { stripEntered = true; marketEntrancePhase = 2 }
            setTimeout(150) { marketEntrancePhase = 3 }
            setTimeout(225) { marketEntrancePhase = 4 }
            setTimeout(300) { breadthEntered = true; volumeEntered = true; marketEntrancePhase = 5 }
            setTimeout(375) { ladderEntered = true; marketEntrancePhase = 6 }
        }
        refreshOverview()
        scheduleLiveRefresh()
        dependencies.insightRepository.loadHotspots { hotspots = it }
    }

    /**
     * 快照入库：只记录在线行情的真实轮询帧。冷启动或数据源不可用时叙事轴保持空轨道，
     * 绝不合成演示日内曲线，也不以缓存/离线值伪造新的时间点。
     */
    private fun ingestOverview(data: MarketOverview) {
        if (data.stamp.tier != SourceTier.DEMO && data.stamp.mode == DataMode.ONLINE) {
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

    /** 仅返回真实采集点；绘制端按对应交易分钟定位，避免补出不存在的历史走势。 */
    private fun axisSeries(): List<Double> {
        return snapshotFrames.map(::metricValueAt)
    }

    private fun axisMinutes(): List<Int> = snapshotFrames.map { it.minute }

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
        // 只允许回放实际入库的帧；拖到未采集区间时停在该位置之前最近的真实快照。
        val sampledMinute = snapshotFrames.lastOrNull { it.minute <= minute }?.minute ?: -1
        scrubMinute = sampledMinute
        replayChipVisible = sampledMinute in 0..239
    }

    /** 交易时段每 15 秒拉取一次；以回调完成后再排下一轮，避免慢网请求重叠。 */
    private fun scheduleLiveRefresh() {
        val generation = ++liveRefreshGeneration
        fun next() {
            setTimeout(15_000) {
                if (generation != liveRefreshGeneration) return@setTimeout
                if (!refreshing && marketPhase().live) refreshOverview()
                next()
            }
        }
        next()
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

    // ------------------------------------------------------------------
    // 新闻弹幕 v2 页侧节拍与弹窗（J1 v2）：状态字段声明见类头部注释块。
    // ------------------------------------------------------------------

    /** setTimeout 链 33ms 步进 1f（≈30dp/s）；暂停 = 停止步进，reduceMotion 不流动。 */
    private fun startTapeTimer() {
        if (tapeTimerStarted) return
        tapeTimerStarted = true
        fun tick() {
            if (!tapePaused && motionEnabled()) tapeOffset += 1f
            setTimeout(33) { tick() }
        }
        tick()
    }

    /**
     * 一圈估算宽度（NewsMarquee 循环缝口径）：CJK 字符 ≈1×字号、拉丁 ≈0.5×字号，
     * 字号 11f（未含字体缩放），加每条 marginRight(26f)。与弹幕行渲染同源（filteredNews），
     * 回放 scrub 换内容时缝宽随之更新。
     */
    private fun tapeLoopWidth(): Float {
        val items = filteredNews()
        if (items.isEmpty()) return 0f
        var width = 0f
        items.forEach { item ->
            val text = "${formatTapeTime(item.time)}  ${item.title}"
            text.forEach { c -> width += if (c.code > 0x2E7F) 11f else 5.5f }
            width += 26f
        }
        return width
    }

    /** 点弹幕条目：暂停流动 + 两拍入场弹窗（R4：vif 挂载首帧不播动画）+ 启动 AI 解读。 */
    private fun openNewsPeek(item: NewsItem) {
        newsPeek = item
        newsPeekGeneration++
        newsPeekVisible = false
        tapePaused = true
        if (motionEnabled()) {
            setTimeout(0) { newsPeekVisible = true }
        } else {
            newsPeekVisible = true
        }
        startNewsAi(item)
    }

    /** 关闭弹窗：恢复流动，销毁流式会话（AI 触点条件触发不常驻），退场后卸载。 */
    private fun dismissNewsPeek() {
        if (newsPeek == null) return
        newsPeekVisible = false
        tapePaused = false
        newsAiProvider?.stop()
        newsAiGeneration++
        newsAiTypewriter?.flushNow()
        newsAiTypewriter?.cancel()
        newsAiTypewriter = null
        newsAiState = 0
        newsAiText = ""
        val generation = newsPeekGeneration
        setTimeout(240) {
            if (generation == newsPeekGeneration && !newsPeekVisible) newsPeek = null
        }
    }

    /** 弹窗内 AI 解读：与 AI 复盘卡同一条程纪律（observable 只在主线程写）。 */
    private fun startNewsAi(news: NewsItem) {
        val config = aiDependencies.configStore.load()
        if (config.validationError() != null) {
            newsAiState = 5
            return
        }
        newsAiProvider?.stop()
        newsAiTypewriter?.cancel()
        newsAiText = ""
        newsAiState = 1
        val provider = aiDependencies.aiProviderFactory(config)
        newsAiProvider = provider
        val generation = ++newsAiGeneration
        var content = ""
        val smoother = TypewriterSmoother(pagerId) { revealed ->
            if (generation != newsAiGeneration) return@TypewriterSmoother
            newsAiText = revealed
            if (newsAiState == 1 && revealed.isNotEmpty()) newsAiState = 2
        }
        newsAiTypewriter = smoother
        provider.ask(
            messages = listOf(AiChatMessage("user", buildNewsAiPrompt(news))),
            onDelta = { delta ->
                content += delta
                if (generation == newsAiGeneration) smoother.append(delta)
            },
            onDone = {
                if (generation != newsAiGeneration) return@ask
                smoother.complete {
                    setTimeout(0) {
                        if (generation != newsAiGeneration) return@setTimeout
                        if (newsAiState <= 2) newsAiState = 3
                    }
                }
            },
            onError = { message ->
                if (generation != newsAiGeneration) return@ask
                setTimeout(0) {
                    if (generation != newsAiGeneration) return@setTimeout
                    smoother.flushNow()
                    smoother.cancel()
                    if (newsAiState == 1 || newsAiState == 2) {
                        newsAiText = message
                        newsAiState = 4
                    }
                }
            },
        )
    }

    /** 弹幕新闻解读 prompt：只做事实与逻辑解读，项目铁律——不荐股、不给目标价。 */
    private fun buildNewsAiPrompt(news: NewsItem): String = buildString {
        append("请用 3 句话以内解读这条新闻的事实与影响逻辑：")
        append("「${news.title}」")
        if (news.summary.isNotBlank()) append("。摘要：${news.summary}")
        append("。说明与哪个板块/方向相关即可，不做买卖建议，不给出目标价。")
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
            // 氛围底（同详情页 AtmosphereBackdrop）：市场火热→红、冷淡→绿、均衡中性，
            // moodScore（含历史回放帧）在 Canvas draw 闭包内驱动换色；垫层声明在
            // Scroller 之前，垫在全部滚动内容之下。
            AtmosphereBackdrop(
                toneSoft = { page.marketToneSoft() },
                pageColor = { page.theme.page },
            )
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

                // J1 · 新闻弹幕（v2 全屏流动）：无背板、持续左移循环、屏幕边缘自然
                // 流出。点条目 = 暂停流动 + 底部小弹窗（详情 + AI 解读流式）；
                // 回放 scrub 时随帧换内容（vbind 条目签名 key）。
                NewsMarquee(
                    theme = theme,
                    items = { page.filteredNews() },
                    sentimentOf = { null },
                    offset = { page.tapeOffset },
                    loopWidth = { page.tapeLoopWidth() },
                    onTapItem = { item -> page.openNewsPeek(item) },
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

                // 标题、说明与板块涨跌数据收在同一容器，作为指数带的连续下一段。
                View {
                    attr {
                        marginTop(14f); padding(13f); borderRadius(16f); backgroundColor(theme.surface)
                        boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f)))
                        val entered = page.marketEntrancePhase >= 3 || !page.motionEnabled()
                        opacity(if (entered) 1f else 0f)
                        if (page.motionEnabled()) {
                            transform(translate = Translate(0f, 0f, 0f, if (entered) 0f else 12f))
                            animate(Animation.easeOut(0.28f), page.marketEntrancePhase)
                        }
                    }
                    Text { attr { text("板块行情"); fontSizeScaled(17f); fontWeightBold(); color(theme.textPrimary) } }
                    Text { attr { text("实时涨跌 · 上涨/下跌家数"); marginTop(3f); fontSizeScaled(10f); color(theme.textTertiary) } }
                    vbind({ page.displayOverview() }) {
                        val sectors = page.displayOverview().sectors.sortedByDescending { it.changePercent }.take(6)
                        if (sectors.isEmpty()) {
                            Text { attr { text("板块行情暂未接入"); marginTop(9f); fontSizeScaled(11f); color(theme.textTertiary) } }
                        } else {
                            View { attr { marginTop(10f); flexDirectionRow(); flexWrapWrap() }
                                sectors.forEachIndexed { index, sector ->
                                    View {
                                        attr {
                                            width((page.pagerData.pageViewWidth - 28f - 26f) / 2f - 4f); marginBottom(8f)
                                            if (index % 2 == 1) marginLeft(8f)
                                            padding(11f); borderRadius(13f); backgroundColor(theme.surfaceMuted)
                                            border(Border(1f, BorderStyle.SOLID, page.changeColor(sector.changePercent).opacity(0.18f)))
                                        }
                                        View { attr { flexDirectionRow(); alignItemsCenter() }
                                            Text { attr { text(sector.name); flex(1f); fontSizeScaled(11.5f); fontWeightSemiBold(); color(theme.textPrimary) } }
                                            Text { attr { text(Format.percent(sector.changePercent)); fontSizeScaled(11.5f); fontWeightBold(); color(page.changeColor(sector.changePercent)) } }
                                        }
                                        Text { attr { text("上涨 ${sector.risingCount} · 下跌 ${sector.fallingCount}"); marginTop(5f); fontSizeScaled(9.5f); color(theme.textTertiary) } }
                                        event { click { page.showSectorPeek(sector.code) } }
                                    }
                                }
                            }
                        }
                    }
                }

                // 数据切换前置到叙事轴上方，切换结果紧跟在时间轴后呈现。
                View { attr {
                    marginTop(18f); padding(3f); flexDirectionRow(); borderRadius(15f); backgroundColor(theme.surfaceMuted)
                    val entered = page.marketEntrancePhase >= 4 || !page.motionEnabled()
                    opacity(if (entered) 1f else 0f)
                    if (page.motionEnabled()) {
                        transform(translate = Translate(0f, 0f, 0f, if (entered) 0f else 10f))
                        animate(Animation.easeOut(0.24f), page.marketEntrancePhase)
                    }
                }
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

                // ① · 今日叙事轴：全页唯一主视觉 + 时间机器 scrub（②）。
                MarketSectionTitle("今日叙事轴", "多空宽度 · 事件钉 · 拖动回放整页", theme)
                View { attr {
                    padding(10f); paddingRight(12f); borderRadius(18f); backgroundColor(theme.marketGlass); border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge)); boxShadow(BoxShadow(0f, 9f, 22f, theme.textPrimary.opacity(0.08f)))
                    val entered = page.marketEntrancePhase >= 5 || !page.motionEnabled()
                    opacity(if (entered) 1f else 0f)
                    if (page.motionEnabled()) {
                        transform(translate = Translate(0f, 0f, 0f, if (entered) 0f else 12f))
                        animate(Animation.easeOut(0.28f), page.marketEntrancePhase)
                    }
                }
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
                        seriesMinutes = { page.axisMinutes() },
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
                            // key 只挂帧数：点亮态由各 attr 内读 scrubMinute 就地切换，
                            // 拖动中不重建行（lit 不能留在 builder 闭包——那是首帧快照）。
                            vbind({ page.snapshotFrames.size }) {
                                page.cachedEvents.take(3).forEach { event ->
                                    View { attr {
                                        val lit = page.scrubMinute >= event.segStart && page.scrubMinute <= event.segEnd
                                        marginTop(6f); paddingLeft(7f); borderLeft(Border(2f, BorderStyle.SOLID, if (lit) theme.brand else theme.divider)); backgroundColor(if (lit) theme.brandSoft else theme.brandSoft.opacity(0f))
                                    }
                                        Text { attr {
                                            val lit = page.scrubMinute >= event.segStart && page.scrubMinute <= event.segEnd
                                            text("${event.timeLabel} ${event.title}：${event.fact} ›"); fontSizeScaled(10.5f); lineHeightScaled(16f); color(if (lit) theme.textPrimary else theme.textSecondary)
                                        } }
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
                            // 四维统一素色（发丝线分格）：右侧两格不再填充警示底色——
                            // 填充态与相邻格视觉粘连（用户反馈 2026-09-10）。
                            (0..3).forEach { index ->
                                View {
                                    attr {
                                        flex(1f); paddingLeft(if (index == 0) 0f else 7f); paddingRight(7f)
                                        if (index < 3) borderRight(Border(0.5f, BorderStyle.SOLID, theme.divider))
                                    }
                                    Text { attr { text(page.shortTermMetric(index).first); fontSizeScaled(10f); color(theme.textTertiary) } }
                                    Text { attr { text(page.shortTermMetric(index).second); marginTop(7f); fontSizeScaled(17f); fontWeightBold(); color(if (index == 0) theme.rise else if (index == 1) theme.fall else theme.textPrimary) } }
                                    Text { attr { text(page.shortTermMetric(index).third); marginTop(4f); fontSizeScaled(8.5f); lineHeightScaled(12f); color(theme.textTertiary) } }
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
                                // 条上不写字：家数标注在条右侧（窄条时柱内数字会被裁切、
                                // 视觉贴字，用户反馈 2026-09-10）。轨道固定 180f，条宽仍编码家数。
                                View { attr { width(180f); height(20f) }
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
                                        event { click { page.toggleBoardLevel(level) }; longPress { params -> if (params.state == "start") page.showBoardPeek(level) } }
                                    }
                                }
                                Text { attr { text(page.ladderCount(level).let { if (it > 0) "$it 家" else "—" }); marginLeft(8f); fontSizeScaled(10f); fontWeightSemiBold(); color(if (page.ladderCount(level) > 0) theme.rise else theme.textTertiary) } }
                            }
                            vif({ page.expandedBoardLevel == level }) {
                                View { attr { marginLeft(34f); marginTop(5f); padding(8f); borderRadius(8f); backgroundColor(theme.riseSoft); opacity(0.96f) }
                                    Text { attr { text(page.boardSampleText(level)); fontSizeScaled(10f); lineHeightScaled(15f); color(theme.textSecondary) } }
                                }
                            }
                        }
                        Text { attr { text("条宽表示涨停池样本家数 · 家数标注在条右侧 · 点按展开样本 · 长按快速预览"); marginTop(10f); fontSizeScaled(10f); color(theme.textTertiary) } }
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

            // 新闻小弹窗（J1 v2）：点弹幕条目 → 暂停流动 + 详情 + AI 解读流式。
            // 与 peek 同款玻璃卡 + 两拍入场；关闭即销毁流式会话（AI 触点条件触发不常驻）。
            vif({ page.newsPeek != null }) {
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        zIndex(22, useOutline = false)
                        backgroundColor(page.theme.textPrimary.opacity(0.26f))
                        val shown = page.newsPeekVisible
                        opacity(if (shown) 1f else 0f)
                        touchEnable(shown)
                        if (page.motionEnabled()) animate(Animation.easeOut(0.20f), page.newsPeekVisible)
                    }
                    event { click { page.dismissNewsPeek() } }
                }
                View {
                    attr {
                        absolutePosition(left = 14f, right = 14f, bottom = 18f + page.pagerData.safeAreaInsets.bottom)
                        zIndex(23, useOutline = false)
                        padding(16f)
                        borderRadius(18f)
                        backgroundColor(page.theme.marketGlass)
                        border(Border(1f, BorderStyle.SOLID, page.theme.marketGlassEdge))
                        boxShadow(BoxShadow(0f, 14f, 30f, page.theme.textPrimary.opacity(0.18f)))
                        val shown = page.newsPeekVisible
                        opacity(if (shown) 1f else 0f)
                        touchEnable(shown)
                        if (page.motionEnabled()) {
                            transform(scale = Scale(if (shown) 1f else 0.96f, if (shown) 1f else 0.96f))
                            animate(Animation.easeOut(0.20f), page.newsPeekVisible)
                        }
                    }
                    View { attr { flexDirectionRow(); alignItemsCenter() }
                        Text { attr { text("要闻 · ${formatTapeTime(page.newsPeek?.time ?: "")}"); flex(1f); fontSizeScaled(11f); fontWeightSemiBold(); color(page.theme.textTertiary) } }
                        Text { attr { text("关闭 ×"); fontSizeScaled(11f); color(page.theme.brand) }; event { click { page.dismissNewsPeek() } } }
                    }
                    Text { attr { text(page.newsPeek?.title ?: ""); marginTop(8f); fontSizeScaled(14f); fontWeightBold(); lineHeightScaled(20f); color(page.theme.textPrimary) } }
                    vif({ page.newsPeek?.summary?.isNotEmpty() == true }) {
                        Text { attr { text(page.newsPeek?.summary ?: ""); marginTop(6f); fontSizeScaled(11.5f); lineHeightScaled(17f); color(page.theme.textSecondary) } }
                    }
                    View { attr { marginTop(12f); height(0.5f); backgroundColor(page.theme.divider) } }
                    // AI 解读位（R1：状态在 vif/attr 内读，流式文本随 typewriter 刷新）
                    View { attr { marginTop(10f) }
                        Text { attr { text("AI 解读"); fontSizeScaled(10.5f); fontWeightSemiBold(); color(page.theme.textTertiary) } }
                        vif({ page.newsAiState == 5 }) {
                            Text { attr { text("未配置 AI 服务，可到对话页继续追问这条新闻。"); marginTop(6f); fontSizeScaled(11.5f); lineHeightScaled(17f); color(page.theme.textSecondary) } }
                        }
                        vif({ page.newsAiState == 1 }) {
                            Text { attr { text("正在解读…"); marginTop(6f); fontSizeScaled(11.5f); color(page.theme.textTertiary) } }
                        }
                        vif({ page.newsAiState == 2 || page.newsAiState == 3 || page.newsAiState == 4 }) {
                            Text {
                                attr {
                                    text(
                                        page.newsAiText.ifBlank {
                                            if (page.newsAiState == 4) "解读失败。" else "…"
                                        }
                                    )
                                    marginTop(6f)
                                    fontSizeScaled(11.5f)
                                    lineHeightScaled(17f)
                                    color(if (page.newsAiState == 4) page.theme.fall else page.theme.textSecondary)
                                }
                            }
                        }
                        vif({ page.newsAiState == 4 }) {
                            View { attr { touchEnable(true); marginTop(6f) }
                                Text { attr { text("重试 ›"); fontSizeScaled(11.5f); fontWeightSemiBold(); color(page.theme.brand) } }
                                event {
                                    click {
                                        val news = page.newsPeek
                                        if (news != null) page.startNewsAi(news)
                                    }
                                }
                            }
                        }
                    }
                    View { attr { marginTop(12f); flexDirectionRow(); alignItemsCenter() }
                        View { attr { touchEnable(true) }
                            Text { attr { text("就这条问问 AI ›"); fontSizeScaled(11.5f); fontWeightSemiBold(); color(page.theme.brand) } }
                            event {
                                click {
                                    val news = page.newsPeek ?: return@click
                                    page.openChatWithQuestion(
                                        "「${news.title}」这条新闻是什么情况？对今天的市场有什么影响？",
                                        "来自市场页新闻弹幕",
                                    )
                                    page.dismissNewsPeek()
                                }
                            }
                        }
                        vif({ page.newsPeek?.url?.isNotEmpty() == true }) {
                            View { attr { touchEnable(true); marginLeft(16f) }
                                Text { attr { text("阅读原文 ↗"); fontSizeScaled(11.5f); fontWeightMedium(); color(page.theme.textSecondary) } }
                                event { click { page.newsPeek?.url?.let { page.openUrl(it) } } }
                            }
                        }
                    }
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
                actions = listOf(
                    AppTopBarAction(icon = { color, size, _ -> LineIconRefresh(color, size) }, onClick = { page.refreshOverview() }),
                    AppTopBarAction(icon = { color, size, _ -> LineIconCalendar(color, size) }, onClick = { page.openPage(Routes.CALENDAR) }),
                ),
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

    /**
     * 氛围底软色：火热（偏暖/较热）→ riseSoft 红、冷淡（偏弱/偏冷）→ fallSoft 绿、
     * 均衡 → surfaceMuted 中性（渐变自然退化为极浅灰）。阈值对齐 moodLabel 分档。
     * 读 displayOverview()（scrub 中跟随历史回放帧换色），供 Canvas draw 闭包调用。
     */
    private fun marketToneSoft(): Color = when (displayOverview().moodScore) {
        in 61..100 -> theme.riseSoft
        in 0..45 -> theme.fallSoft
        else -> theme.surfaceMuted
    }
    private fun threshold(change: Double?): String = when { change == null -> "关口待接入"; change <= -2 -> "关键关口承压"; change >= 2 -> "关键关口走强"; else -> "关口附近震荡" }
    private fun marketPhase(): MarketPhase = when (platformCurrentHour().coerceIn(0, 23)) { in 0..8 -> MarketPhase("盘前准备", false, "开盘前数据不代表成交结果"); 9 -> MarketPhase("集合竞价", true, "竞价阶段可能出现虚假大单，需以连续交易为准"); in 10..11 -> MarketPhase("早盘连续交易", true, null); 12 -> MarketPhase("午间休市", false, "休市期间行情静止，并非数据故障"); in 13..14 -> MarketPhase("午后连续交易", true, null); 15 -> MarketPhase("收盘集合 / 复盘", false, "收盘数据正在汇总"); in 16..18 -> MarketPhase("盘后静默期", false, "盘后数据可能陆续修订"); else -> MarketPhase("非交易时段", false, "显示最近一个交易日数据") }
    private data class MarketPhase(val label: String, val live: Boolean, val notice: String?)
    private data class MarketPeek(val title: String, val primary: String, val detail: String)
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
