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
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.provider.HotspotSnapshot
import com.kuikly.stockchat.data.provider.MarketOverview
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.SectorRank
import com.kuikly.stockchat.data.provider.platformCurrentHour
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.AppTopBarMetric
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
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Market overview: relationships first; Hotspots and Calendar provide the drill-down.
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
 *    that must fade in uses mount-then-present two-tick staging (see [showPeek],
 *    [selectSectorTab]) — the `drawerMounted`/`drawerPresented` pattern.
 *
 * Sections carrying no animation are wrapped in `vbind({ overview })` and simply
 * rebuild on refresh. Sections carrying animation (Hero tick flash, ladder,
 * sector tabs, heatmap) read inside `attr` instead, so their views survive a data
 * update and keep their registered animations.
 */
@Page(Routes.MARKET, supportInLocal = true)
internal class MarketPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }
    private var overview: MarketOverview by observable(OfflineMarketInsightProvider().overviewValue())
    private var sectorTab: Int by observable(0)
    private var refreshing: Boolean by observable(false)
    /** 0 = no flash; 1/2/3 = the three phases of one tick sequence. */
    private var tickPhase: Int by observable(0)
    private var tickDirection: Double by observable(0.0)
    private var tickRevision = 0
    private var emotionPulse: Boolean by observable(false)
    private var emotionPulseRevision = 0
    private var hotspots: HotspotSnapshot by observable(OfflineMarketInsightProvider().hotspotValue())
    private var expandedBoardLevel: Int by observable(-1)
    private var selectedSectorCode: String? by observable(null)
    private var peek: MarketPeek? by observable(null)
    private var peekVisible: Boolean by observable(false)
    private var heroEntered: Boolean by observable(false)
    private var ladderEntered: Boolean by observable(false)
    private var heatmapEntered: Boolean by observable(false)
    private var stickyIndexVisible: Boolean by observable(false)
    private var pullDistance: Float by observable(0f)
    private var pullReady: Boolean by observable(false)
    private var refreshResultVisible: Boolean by observable(false)
    private var debugScroll: String by observable("scroll:none")
    /** 指数带次级卡片与 AI 卡的入场开关（spec 22 §5.2 渐进加载的入场层）。 */
    private var stripEntered: Boolean by observable(false)
    /** 宽度条中心双向生长（spec 22 §3.3）。 */
    private var breadthEntered: Boolean by observable(false)
    /** 量能三柱从基线生长（spec 22 §3.2）。 */
    private var volumeEntered: Boolean by observable(false)
    /** AI 复盘卡状态机：0 idle / 1 thinking / 2 streaming / 3 done / 4 error。 */
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
     * Currently driven only by the platform "reduce motion" setting; trading
     * phase deliberately does NOT gate it — all eleven timelines here are finite
     * one-shots, and spec 22 §0.4 stops *looping* motion outside trading hours,
     * of which this page has none.
     */
    private fun motionEnabled(): Boolean = !reduceMotion

    override fun created() {
        super.created()
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
        refreshOverview()
        dependencies.insightRepository.loadHotspots { hotspots = it }
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

    private fun selectSectorTab(tab: Int) {
        // heatmapEntered must fall BEFORE sectorTab flips. The vif builds the grid
        // in response to the sectorTab change, and a freshly mounted view cannot
        // animate itself, so the cells have to be created already-collapsed and
        // then raised one tick later.
        if (tab == 2 && motionEnabled()) heatmapEntered = false
        sectorTab = tab
        if (tab == 2 && motionEnabled()) setTimeout(1) { heatmapEntered = true }
    }

    /** The compact strip is toggled only at the threshold, not on every scroll callback. */
    private fun updateStickyIndex(offsetY: Float) {
        // This matches the detail-page compact-header convention. The Hero is
        // intentionally allowed to overlap the 56dp handoff zone, so the same
        // AppTopBar center can be replaced before the large index card leaves.
        val next = offsetY > 120f
        if (stickyIndexVisible != next) stickyIndexVisible = next
        // Kuikly's Scroller reports the elastic rebound, but does not expose a
        // touch-up callback. Returning to the top is therefore the release edge.
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
        // `peek` is the mount flag (vif); `peekVisible` is the transition. They
        // must not flip in the same tick or the overlay appears without a fade.
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
        val index = overview.indices.firstOrNull()
        showPeek(
            title = index?.name ?: "上证指数",
            primary = index?.let { "${Format.price(it.price)}  ${Format.percent(it.changePercent)}" } ?: "--",
            detail = "长按预览 · ${overview.stamp.mode.quoteLabel()} · ${overview.stamp.asOf}",
        )
    }

    private fun showSectorPeek(code: String) {
        val sector = overview.sectors.firstOrNull { it.code == code } ?: return
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
        showPeek(label, primary, "$detail · 涨停池数据源：${overview.stamp.source}")
    }

    private fun showFundMethodology(label: String, primary: String, detail: String) {
        showPeek(label, primary, "$detail · 数据源：${overview.stamp.source}。不同平台统计范围和时间点可能不同。")
    }

    // ------------------------------------------------------------------
    // AI 复盘卡（doc 31 §1）：内联流式生成，未配置 AI 时降级为深链对话页。
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
            // 无可用配置时不弹错误：直接带上下文跳对话页，由对话页做配置引导
            //（与 RiskMapPage / StockDetailPage 的深链范式一致）。
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
        // （onPublish 为节拍器主线程回调；onDone/onError 经 setTimeout(0) 跳回）。
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
        val leader = overview.sectors.maxByOrNull { it.changePercent }
        return buildString {
            append("接着刚才今天市场的复盘，帮我展开讲讲")
            if (leader != null) append("领涨板块 ${leader.name}（${Format.percent(leader.changePercent)}）") else append("板块轮动")
            append("背后的资金和情绪逻辑。")
        }
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                // 竖向 Scroller 水平 padding 会被双倍扣除，14/14 时右侧多出 28dp 留白；
                // 右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                attr { flex(1f); paddingLeft(14f); paddingRight(0f); paddingTop(page.pagerData.statusBarHeight + 68f); paddingBottom(78f) }
                // Kuikly names the vertical content offset `offsetX`; `offsetY`
                // is horizontal. Reading the latter kept this state at zero on
                // real Android devices even while the page visibly scrolled.
                event { scroll { params -> page.debugScroll = "x=${params.offsetX.toInt()} y=${params.offsetY.toInt()} ch=${params.contentHeight.toInt()} vh=${params.viewHeight.toInt()}"; page.updateStickyIndex(params.offsetX) } }
                // Captured once, deliberately: both are non-observable snapshots
                // here (see rule 2 in the class doc — reading `page.theme` inside
                // an attr would steal every animation key on this page).
                val theme = page.theme
                val phase = page.marketPhase()

                // A · market session state: non-trading hours use the official flat grey semantic.
                View {
                    attr { paddingTop(10f); paddingBottom(17f); paddingLeft(4f); paddingRight(4f); opacity(if (phase.live) 1f else 0.55f) }
                    View {
                        attr { height(27f); flexDirectionRow(); alignItemsCenter(); paddingLeft(9f); paddingRight(9f); borderRadius(14f); backgroundColor(theme.surface.opacity(0.72f)) }
                        View {
                            attr {
                                size(if (phase.live) 8f else 7f, if (phase.live) 8f else 7f)
                                borderRadius(5f)
                                backgroundColor(if (phase.live) theme.rise else theme.flat)
                                if (page.motionEnabled()) {
                                    val busy = page.refreshing
                                    transform(scale = Scale(if (busy) 1.35f else 1f, if (busy) 1.35f else 1f))
                                    animate(Animation.easeOut(0.22f), page.refreshing)
                                }
                            }
                        }
                        Text { attr { text(phase.label); marginLeft(6f); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.textPrimary) } }
                        Text { attr { text(page.refreshStatusText()); flex(1f); textAlignRight(); fontSizeScaled(10f); color(theme.textTertiary) } }
                    }
                    phase.notice?.let { Text { attr { text(it); marginTop(6f); fontSizeScaled(10.5f); color(theme.flat) } } }

                    // B · Hero sits on the atmosphere, not in another generic card.
                    // No motion here, so a rebuild-on-refresh is the simplest correct binding.
                    vbind({ page.overview }) {
                        val data = page.overview
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

                // C · z1 secondary index cards under a z3 main glass card.
                Text { attr { text("指数带"); marginTop(4f); fontSizeScaled(17f); fontWeightBold(); color(theme.textPrimary) } }
                Text { attr { text("点位、方向与当日区间"); marginTop(3f); fontSizeScaled(10f); color(theme.textTertiary) } }
                View { attr { marginTop(9f); marginLeft(8f); marginRight(8f); flexDirectionRow(); opacity(0.90f) }
                    vbind({ page.overview }) {
                        page.overview.indices.drop(1).take(2).forEachIndexed { index, item ->
                            View {
                                attr {
                                    flex(1f); height(108f); padding(13f); borderRadius(17f); backgroundColor(theme.surfaceMuted); if (index > 0) marginLeft(8f)
                                    // A1 入场 stagger（doc 31 §2）：上移淡入，scale 与 translate 必须
                                    // 走全参 transform——便捷重载会把其余分量重置为 DEFAULT。
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
                            }
                        }
                    }
                }
                // The Hero glass card is never wrapped in a vbind: it carries the
                // tick flash, so its views must survive a data update or the
                // rebuild eats the animation registration.
                View {
                    attr { marginTop(-22f); padding(17f); borderRadius(20f); backgroundColor(theme.marketGlass); border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge)); boxShadow(BoxShadow(0f, 10f, 24f, theme.textPrimary.opacity(0.10f))) }
                    event { longPress { params -> if (params.state == "start") page.showIndexPeek() } }
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
                            Text { attr { text(page.overview.indices.firstOrNull()?.name ?: "行情接入中"); fontSizeScaled(12f); color(theme.textSecondary) } }
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
                                        text(page.overview.indices.firstOrNull()?.let { Format.price(it.price) } ?: "--")
                                        fontSizeScaled(38f); lineHeightScaled(43f); fontWeightBold()
                                        color(if (page.tickPhase in 1..2) page.changeColor(page.tickDirection) else theme.textPrimary)
                                    }
                                }
                            }
                        }
                        View {
                            attr { padding(7f); borderRadius(7f); backgroundColor(page.changeSoft(page.overview.indices.firstOrNull()?.changePercent ?: 0.0)) }
                            Text { attr { text(Format.percent(page.overview.indices.firstOrNull()?.changePercent ?: 0.0)); fontSizeScaled(15f); fontWeightBold(); color(page.changeColor(page.overview.indices.firstOrNull()?.changePercent ?: 0.0)) } }
                        }
                    }
                    // A calm mini trend, not a fake price prediction chart.
                    View { attr { marginTop(11f); height(34f); flexDirectionRow(); alignItemsFlexEnd(); opacity(if (page.refreshing) 0.72f else 1f) }
                        listOf(11f, 16f, 13f, 22f, 18f, 26f, 21f, 30f, 24f, 33f, 28f, 34f).forEachIndexed { index, height ->
                            View { attr { flex(1f); marginRight(if (index == 11) 0f else 3f); height(height); borderRadius(2f); backgroundColor(page.changeColor(page.overview.indices.firstOrNull()?.changePercent ?: 0.0).opacity(if (index == 11) 0.92f else 0.25f)) } }
                        }
                    }
                    Text { attr { text(if (phase.live) "分时走势 · 最新点持续更新" else "收盘走势 · 非交易时段已静止"); marginTop(5f); fontSizeScaled(9.5f); color(theme.textTertiary) } }
                    View { attr { marginTop(10f); flexDirectionRow() }
                        MarketIndexFact("成交", theme) { page.overview.turnoverAmount?.let(page::turnoverText) ?: "待接入" }
                        MarketIndexFact("最高", theme) { page.overview.indices.firstOrNull()?.high?.let(Format::price) ?: "待接入" }
                        MarketIndexFact("最低", theme) { page.overview.indices.firstOrNull()?.low?.let(Format::price) ?: "待接入" }
                    }
                }
                View { attr { marginTop(10f); flexDirectionRow(); flexWrapWrap() }
                    vbind({ page.overview }) {
                        val data = page.overview
                        data.indices.drop(3).take(6).forEach { item ->
                            val name = item.name
                            val change = item.changePercent
                            View { attr { height(28f); marginRight(6f); marginBottom(6f); paddingLeft(9f); paddingRight(9f); allCenter(); borderRadius(14f); backgroundColor(theme.surface); border(Border(1f, BorderStyle.SOLID, if (abs(change) > 2.0) page.changeColor(change).opacity(0.52f) else theme.divider)) }
                                Text { attr { text("$name ${Format.percent(change)}"); fontSizeScaled(10.5f); color(page.changeColor(change)) } }
                            }
                        }
                        if (data.indices.size <= 3) Text { attr { text("更多指数数据接入中"); marginTop(5f); fontSizeScaled(10f); color(theme.textTertiary) } }
                    }
                }

                // D · volume relationship, one solid today bar and two reference bars.
                MarketSectionTitle("量能", "成交额与近期均值对照", theme)
                View { attr { padding(15f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                    vbind({ page.overview }) {
                        val data = page.overview
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
                                    // A3 柱体从基线生长（spec 22 §3.2）：320ms + 40ms stagger。
                                    // width 是普通 attr，animate 会补间它的变化；vbind 重建后
                                    // volumeEntered 恒真，刷新不重播。
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
                    }
                }

                // E · market breadth is shown as a centered relationship, never a tile grid.
                MarketSectionTitle("市场宽度", "上涨、平盘与下跌的结构", theme)
                View { attr { padding(15f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                    vbind({ page.overview }) {
                        val data = page.overview
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
                                        // A2：数字在条生长到约 70% 时淡入（delay ≈ 0.52s × 0.7）。
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
                        // A2 宽度条从中心分界线向两侧生长（spec 22 §3.3）：整条 scaleX 0→1
                        // 520ms，中心 origin 下两段同时伸展——它们是同一个事实的两面。
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
                        Text { attr { text("涨跌差 ${data.risingCount - data.fallingCount} · 涨幅>5% / 跌幅>5% 待接入 · 平盘 ${data.flatCount}"); marginTop(8f); fontSizeScaled(10f); color(theme.textTertiary) } }
                    }
                }

                // F · four figures share one glass plane and dividers; they are not four cards.
                // Carries the L2 alert pulse, so the columns read inside attr rather
                // than rebuilding: the pulse fires immediately after the data lands.
                MarketSectionTitle("短线情绪", "涨停池口径 ⓘ", theme) {
                    page.showPeek("涨停池口径", "封板率 = 未开板样本 / 涨停样本", "炸板次数来自涨停池 zbc 字段；连板高度来自 lbc 字段。数据源：${page.overview.stamp.source}。")
                }
                View { attr { padding(15f); borderRadius(20f); backgroundColor(theme.marketGlass); border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge)); boxShadow(BoxShadow(0f, 9f, 22f, theme.textPrimary.opacity(0.08f))); flexDirectionRow() }
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

                // G · a ladder encodes family size by bar width and preserves yesterday's height as a ghost line.
                vbind({ page.overview }) {
                    val yesterdayBoard = page.overview.yesterdayHighestBoard ?: 0
                    val highestBoard = page.overview.highestBoard ?: 0
                    MarketSectionTitle("连板梯队", if (yesterdayBoard > highestBoard) "昨日 $yesterdayBoard 板 · 今日高度回落 ${yesterdayBoard - highestBoard} 板" else "按连板高度查看结构", theme)
                }
                View { attr { padding(15f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                    vif({ (page.overview.yesterdayHighestBoard ?: 0) > (page.overview.highestBoard ?: 0) }) {
                        Text { attr { text("┄┄ 昨日最高 ${page.overview.yesterdayHighestBoard ?: 0} 板"); fontSizeScaled(10f); color(theme.flat) } }
                    }
                    (1..4).reversed().forEachIndexed { index, level ->
                        View {
                            attr {
                                marginTop(if (index == 0) 9f else 7f)
                                flexDirectionRow()
                                alignItemsCenter()
                                opacity(if (page.ladderEntered || !page.motionEnabled()) 1f else 0f)
                                // A4 逐层升起（spec 22 §4.6 / doc 31 §2）：Y 12px 上移 + 淡入
                                // 280ms，delay 按首板→高板 60ms 递增（与梯队高度的空间隐喻一致）。
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

                // H · sector lead is the raised card; #2–6 remain in a quieter ranking layer.
                MarketSectionTitle("板块", "领涨、领跌与热力图", theme)
                View { attr { padding(3f); flexDirectionRow(); borderRadius(15f); backgroundColor(theme.surfaceMuted) }
                    listOf("领涨", "领跌", "热力图").forEachIndexed { index, label ->
                        View {
                            attr {
                                flex(1f); height(30f); allCenter(); borderRadius(12f)
                                val selected = page.sectorTab == index
                                backgroundColor(if (selected) theme.surface else theme.surfaceMuted)
                                if (page.motionEnabled()) {
                                    transform(scale = Scale(if (selected) 1f else 0.96f, if (selected) 1f else 0.96f))
                                    animate(Animation.easeOut(0.18f), page.sectorTab)
                                }
                            }
                            Text { attr { text(label); fontSizeScaled(10.5f); fontWeightMedium(); color(if (page.sectorTab == index) theme.textPrimary else theme.textTertiary) } }
                            event { click { page.selectSectorTab(index) } }
                        }
                    }
                }
                vif({ page.sectorTab == 2 }) {
                    View { attr { marginTop(10f) }
                        vbind({ page.heatmapSectors() }) {
                            // Kuikly has no percentage width, so a fixed-column grid is
                            // built from chunked rows (the MetricGrid idiom) rather than
                            // flexWrapWrap: 16 flex(1) children on one wrap line just
                            // shrink into a single overflowing row.
                            val rows = page.heatmapSectors().chunked(4)
                            rows.forEachIndexed { rowIndex, row ->
                                View { attr { flexDirectionRow() }
                                    row.forEachIndexed { columnIndex, sector ->
                                        val index = rowIndex * 4 + columnIndex
                                        View { attr { flex(1f); paddingRight(if (columnIndex == 3) 0f else 6f); paddingBottom(6f) }
                                            // Two timelines, two layers: entrance belongs to
                                            // heatmapEntered, selection to selectedSectorCode.
                                            // Sharing one attr would make them fight over the
                                            // same transform and only one key could win.
                                            View {
                                                attr {
                                                    opacity(if (page.heatmapEntered || !page.motionEnabled()) 1f else 0f)
                                                    if (page.motionEnabled()) {
                                                        val entered = page.heatmapEntered
                                                        transform(scale = Scale(if (entered) 1f else 0.96f, if (entered) 1f else 0.96f))
                                                        animate(Animation.easeOut(0.20f).delay(0.014f * index), page.heatmapEntered)
                                                    }
                                                }
                                                View {
                                                    attr {
                                                        val color = page.changeColor(sector.changePercent)
                                                        height(54f); padding(8f); borderRadius(10f)
                                                        backgroundColor(color.opacity((0.12f + (abs(sector.changePercent) / 10.0).toFloat()).coerceAtMost(0.45f)))
                                                        val selected = page.selectedSectorCode == sector.code
                                                        border(Border(if (selected) 1.5f else 0f, BorderStyle.SOLID, color.opacity(0.7f)))
                                                        if (page.motionEnabled()) {
                                                            transform(scale = Scale(if (selected) 1.04f else 1f, if (selected) 1.04f else 1f))
                                                            // orEmpty() only shapes the value; the getter read above it
                                                            // is what registers selectedSectorCode as the animation key.
                                                            animate(Animation.easeOut(0.20f), page.selectedSectorCode.orEmpty())
                                                        }
                                                    }
                                                    Text { attr { text(sector.name); fontSizeScaled(9f); color(theme.textPrimary) } }
                                                    Text { attr { text(Format.percent(sector.changePercent)); marginTop(5f); fontSizeScaled(10f); fontWeightSemiBold(); color(page.changeColor(sector.changePercent)) } }
                                                    event {
                                                        click { page.selectedSectorCode = if (page.selectedSectorCode == sector.code) null else sector.code }
                                                        longPress { params -> if (params.state == "start") page.showSectorPeek(sector.code) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text { attr { text("色阶仅表示涨跌强度"); marginTop(2f); fontSizeScaled(10f); color(theme.textTertiary) } }
                    vif({ page.selectedHeatmapSector() != null }) {
                        View {
                            attr {
                                marginTop(8f)
                                padding(13f)
                                borderRadius(14f)
                                backgroundColor(theme.marketGlass)
                                border(Border(1f, BorderStyle.SOLID, page.changeColor(page.selectedHeatmapSector()?.changePercent ?: 0.0).opacity(0.34f)))
                            }
                            View { attr { flexDirectionRow(); alignItemsCenter() }
                                Text { attr { text(page.selectedHeatmapSector()?.name ?: ""); flex(1f); fontSizeScaled(15f); fontWeightBold(); color(theme.textPrimary) } }
                                Text { attr { text(Format.percent(page.selectedHeatmapSector()?.changePercent ?: 0.0)); fontSizeScaled(18f); fontWeightBold(); color(page.changeColor(page.selectedHeatmapSector()?.changePercent ?: 0.0)) } }
                            }
                            Text {
                                attr {
                                    val sector = page.selectedHeatmapSector()
                                    text(if (sector == null) "" else "主力 ${Format.compactAmount(sector.mainFlow)} · 上涨 ${sector.risingCount} · 下跌 ${sector.fallingCount}")
                                    marginTop(6f); fontSizeScaled(10f); color(theme.textSecondary)
                                }
                            }
                            Text { attr { text("点按查看口径与板块样本"); marginTop(7f); fontSizeScaled(10f); color(theme.brand) } }
                            event { click { page.selectedHeatmapSector()?.let { page.showSectorPeek(it.code) } } }
                        }
                    }
                }
                vif({ page.sectorTab != 2 }) {
                    // Ordering depends on both the tab and the data, so both are
                    // part of the rebuild key.
                    vbind({ page.sectorTab to page.overview }) {
                        val ranked = page.rankedSectors()
                        val leader = ranked.firstOrNull()
                        View { attr { marginTop(10f); padding(16f); borderRadius(20f); backgroundColor(theme.marketGlass); border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge)); boxShadow(BoxShadow(0f, 9f, 22f, theme.textPrimary.opacity(0.08f))) }
                            View { attr { flexDirectionRow(); alignItemsCenter() }
                                View { attr { flex(1f) }; Text { attr { text(leader?.name ?: "暂无板块数据"); fontSizeScaled(17f); fontWeightBold(); color(theme.textPrimary) } }; Text { attr { text(leader?.let { "主力 ${Format.compactAmount(it.mainFlow)} · 上涨 ${it.risingCount} / 下跌 ${it.fallingCount}" } ?: "板块明细接入中"); marginTop(4f); fontSizeScaled(10f); color(theme.textTertiary) } } }
                                Text { attr { text(leader?.let { Format.percent(it.changePercent) } ?: "--"); fontSizeScaled(22f); fontWeightBold(); color(page.changeColor(leader?.changePercent ?: 0.0)) } }
                            }
                            event { click { leader?.let { page.showSectorPeek(it.code) } } }
                        }
                        View { attr { marginTop(8f); padding(10f); borderRadius(15f); backgroundColor(theme.surfaceMuted); opacity(0.90f) }
                            ranked.drop(1).take(5).forEachIndexed { rank, sector ->
                                View { attr { marginTop(if (rank == 0) 0f else 9f); flexDirectionRow(); alignItemsCenter() }
                                    Text { attr { text("${rank + 2}"); width(22f); fontSizeScaled(10f); color(theme.textTertiary) } }
                                    Text { attr { text(sector.name); width(76f); fontSizeScaled(11f); color(theme.textPrimary) } }
                                    View { attr { flex(1f); height(5f); borderRadius(3f); backgroundColor(theme.surface) }
                                        View { attr { width((112f * (abs(sector.changePercent) / 6.0).toFloat()).coerceIn(8f, 112f)); height(5f); borderRadius(3f); backgroundColor(page.changeColor(sector.changePercent).opacity(0.58f)) } }
                                    }
                                    Text { attr { text(Format.percent(sector.changePercent)); width(50f); marginLeft(7f); textAlignRight(); fontSizeScaled(10f); color(page.changeColor(sector.changePercent)) } }
                                    event { click { page.showSectorPeek(sector.code) } }
                                }
                            }
                        }
                    }
                }

                // I · three money rows, with the known northbound methodology conflict called out.
                MarketSectionTitle("资金", "口径 ⓘ", theme) {
                    page.showFundMethodology("资金口径", "不同来源可能不同", "北向资金、行业主力与龙虎榜的统计对象和时间范围并不相同")
                }
                View { attr { padding(15f); borderRadius(16f); backgroundColor(theme.surface); boxShadow(BoxShadow(0f, 2f, 8f, theme.textPrimary.opacity(0.05f))) }
                    vbind({ page.overview }) {
                        val data = page.overview
                        listOf(
                            Triple("北向资金", data.northboundFlow?.let(Format::compactAmount) ?: "待接入", "沪深股通拆分与占成交比待接入"),
                            Triple("行业主力", data.sectors.firstOrNull()?.name ?: "待接入", "主力净流入排名"),
                            Triple("龙虎榜", "机构 vs 游资", "交易席位数据待接入"),
                        ).forEachIndexed { index, row ->
                            View { attr { paddingTop(10f); paddingBottom(10f); flexDirectionRow(); alignItemsCenter(); borderBottom(Border(1f, BorderStyle.SOLID, theme.divider.opacity(0.72f))) }
                                View { attr { flex(1f) }; Text { attr { text(row.first); fontSizeScaled(12f); fontWeightSemiBold(); color(theme.textPrimary) } }; Text { attr { text(row.third); marginTop(3f); fontSizeScaled(9.5f); color(theme.textTertiary) } } }
                                Text { attr { text(row.second); width(84f); textAlignRight(); fontSizeScaled(12f); fontWeightSemiBold(); color(if (row.first == "北向资金") page.changeColor(data.northboundFlow ?: 0.0) else theme.textPrimary) } }
                                event {
                                    click {
                                        val detail = when (index) {
                                            0 -> "当前展示净流向；沪股通与深股通拆分、占成交比尚未纳入，不能与其他平台口径直接相减"
                                            1 -> "按板块主力净流入排序；当前第一名为 ${page.overview.sectors.firstOrNull()?.name ?: "--"}"
                                            else -> "龙虎榜席位结构属于单独披露数据，当前页面只保留入口说明"
                                        }
                                        page.showFundMethodology(row.first, row.second, detail)
                                    }
                                }
                            }
                        }
                    }
                    Text { attr { text("北向资金存在不同统计口径，展示前需核对来源与时间范围。"); marginTop(11f); fontSizeScaled(10f); lineHeightScaled(15f); color(theme.flat) } }
                }

                // B · AI 复盘卡（doc 31 §1）：idle → thinking（骨架呼吸）→ streaming（打字机）
                // → done（追问深链）。未配置 AI 时点击直接带上下文跳对话页。
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
                    View {
                        attr { flexDirectionRow(); alignItemsCenter() }
                        View { attr { width(22f); height(22f); borderRadius(7f); backgroundColor(theme.brand); allCenter() }
                            Text { attr { text("✦"); fontSizeScaled(12f); fontWeightBold(); color(theme.onBrand) } }
                        }
                        Text { attr { text("让 AI 讲讲今天"); marginLeft(8f); flex(1f); fontSizeScaled(14f); fontWeightBold(); color(theme.textPrimary) } }
                        Text { attr { text(page.aiActionLabel()); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.brand) } }
                        event { click { page.toggleAiBriefing() } }
                    }
                    vif({ page.aiState == 0 }) {
                        Text { attr { text("基于本页已展示的指数、宽度、量能与情绪数据，让 AI 用一段话讲清今天市场的结构。"); marginTop(8f); fontSizeScaled(11f); lineHeightScaled(16f); color(theme.textSecondary) } }
                    }
                    vif({ page.aiState == 1 }) {
                        // 思考态骨架与输出文本同构（spec 22 §5.1）；呼吸是 aiPulse 驱动的
                        // 有限脉冲，流式开始即被内容反馈取代，不引入循环 shimmer。
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
                                // 打字机：attr 内读 aiText，每个 delta 重跑本 attr 更新文本，
                                // 视图不重挂（R1）；流式态尾随一个光标字符。
                                text(page.aiText + if (page.aiState == 2) " ▍" else "")
                                marginTop(10f); fontSizeScaled(12f); lineHeightScaled(20f)
                                color(if (page.aiState == 4) theme.fall else theme.textPrimary)
                            }
                        }
                    }
                    vif({ page.aiState == 3 }) {
                        View { attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
                            Text { attr { text("AI 生成 · 仅供参考，不构成投资建议"); flex(1f); fontSizeScaled(9.5f); color(theme.textTertiary) } }
                            Text { attr { text("追问 ›"); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.brand) }
                                event { click { page.openChatWithQuestion(page.aiFollowUpQuestion()) } } }
                        }
                    }
                }
                // 2026-09-08 情绪算法样本标注：宽度样本按市场大类拆分后，"搜不到"
                // 的证券（停牌、无涨跌幅数据等）按大类在页底标注，不静默丢失。
                vbind({ page.overview }) {
                    val samples = page.overview.breadthSamples
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
                Text { attr { text("数据仅供信息参考，不构成投资建议。行情数据可能延迟或存在不同统计口径。"); marginTop(10f); fontSizeScaled(10f); lineHeightScaled(15f); color(theme.textTertiary) } }
            }
            // `peek` mounts the overlay, `peekVisible` animates it (see showPeek).
            Text { attr { absolutePosition(top = 260f, left = 20f); zIndex(99, useOutline = false); text(page.debugScroll + " | sticky=" + page.stickyIndexVisible); fontSizeScaled(11f); color(com.tencent.kuikly.core.base.Color.RED) } }
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
                    page.overview.indices.take(3).mapIndexed { index, item ->
                        // The compact top bar intentionally downgrades the Hero's
                        // L2 flash to L1: a transient value-colour change only.
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
    private fun shortTermAlert(): Boolean = (overview.sealRate ?: 1.0) < 0.80

    private fun shortTermMetric(index: Int): Triple<String, String, String> {
        val data = overview
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

    private fun heatmapSectors(): List<SectorRank> = overview.sectors.sortedByDescending { abs(it.changePercent) }.take(16)
    private fun selectedHeatmapSector(): SectorRank? = overview.sectors.firstOrNull { it.code == selectedSectorCode }
    private fun rankedSectors(): List<SectorRank> =
        if (sectorTab == 1) overview.sectors.sortedBy { it.changePercent } else overview.sectors.sortedByDescending { it.changePercent }

    private fun turnoverText(value: Double): String = "${Format.decimal(value / 1_000_000_000_000.0, 2)}万亿"
    private fun moodLabel(score: Int): String = when (score) { in 0..25 -> "偏冷"; in 26..45 -> "偏弱"; in 46..60 -> "均衡"; in 61..80 -> "偏暖"; else -> "较热" }
    private fun moodHeadline(data: MarketOverview): String = when {
        // 空数据（初始/离线空态）不产出"情绪走弱"这类假结论。
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
