package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.core.FundFlowCardModel
import com.kuikly.stockchat.cards.core.FinancialCardModel
import com.kuikly.stockchat.cards.core.ShareholderCardModel
import com.kuikly.stockchat.cards.core.BillboardCardModel
import com.kuikly.stockchat.cards.core.CorporateActionCardModel
import com.kuikly.stockchat.cards.core.DisclosureCardModel
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.stock.KLineChart
import com.kuikly.stockchat.cards.stock.KuiklyTimelineChart
import com.kuikly.stockchat.cards.stock.MarketCardRenderers
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openChatWithQuestion
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.DataModeBadge
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.STOCK_DETAIL, supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private var symbol = "600519.SH"
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val quoteRepository get() = dependencies.quoteRepository
    private val watchlistStore get() = dependencies.watchlistStore
    // Page instances are constructed before Kuikly assigns pagerId. Do not
    // touch page-scoped storage/repositories from a property initializer.
    private var quote: Quote by observable(Quote.placeholder("600519.SH", "贵州茅台"))
    private var dataModeLabel: String by observable("正在连接行情")
    private var chartMode: StockChartMode by observable(StockChartMode.TIMELINE)
    private var chartPeriod: StockChartPeriod by observable(StockChartPeriod.DAY)
    private var watchlisted: Boolean by observable(false)
    private var watchlistHint: String by observable("")
    private var topProgress: Float by observable(0f)
    private var topCompactVisible: Boolean by observable(false)
    private var quoteLoading: Boolean by observable(true)
    private var tickerLift: Boolean by observable(false)
    private var tickerDirectionUp: Boolean by observable(true)
    private var previousPriceText: String by observable("")
    private var previousChangeText: String by observable("")
    private var previousPercentText: String by observable("")
    private var watchlistFeedback: Boolean by observable(false)
    private var livePulse: Boolean by observable(false)
    private var entranceVisible: Boolean by observable(false)
    // 容器变换交接（灵动岛下拉 → 详情）：无动画 push 让本页原地接管全屏
    // 玻璃帧，随后整页内容淡入，读起来是"卡片长成了详情页"。
    private var handoffPresented: Boolean by observable(true)
    private var handoffFadeActive = false
    private var aiRevealLimit: Int by observable(0)
    private var aiRevealSource = ""
    private var aiRetryActive: Boolean by observable(false)
    private var expandedAttributionKey: String by observable("")
    private var selectedKLineIndex: Int by observable(-1)
    private var livePulseVersion = 0
    private var aiRevealVersion = 0
    private var insight: StockInsightBundle by observable(OfflineMarketInsightProvider().stock("600519.SH"))
    private val reduceMotion by lazy { platformPrefersReducedMotion() }
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        MarketCardRenderers.ensureRegistered()
        symbol = pagerData.params.optString("symbol").ifEmpty { "600519.SH" }
        handoffFadeActive = pagerData.params.optString("krTransition") == "islandExpand"
        handoffPresented = !handoffFadeActive || reduceMotion
        quoteRepository.cachedOrOffline(symbol)?.let {
            quote = it
            quoteLoading = false
        }
        watchlisted = watchlistStore.contains(symbol)
        quoteRepository.load(symbol) { result ->
            result.quote?.let { applyQuote(it) }
            quoteLoading = false
            dataModeLabel = result.mode.quoteLabel()
        }
        insight = dependencies.insightRepository.cachedStock(symbol)
        dependencies.insightRepository.loadStock(symbol) {
            insight = it
            startAiReveal()
        }
        startAiReveal()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        entranceVisible = reduceMotion
        if (!reduceMotion) {
            setTimeout(0) { entranceVisible = true }
        }
        // 交接淡入 R4 两帧翻转：首帧 opacity 0 挂载（对齐全屏玻璃帧），
        // 下一帧翻转为可见触发淡入。500ms 兜底防 ref→setTimeout 链路丢失
        // 导致页面停在透明态（同值写入不通知，幂等安全）。
        if (handoffFadeActive) {
            setTimeout(0) { handoffPresented = true }
            setTimeout(500) { handoffPresented = true }
        }
        startLivePulse()
        startAiReveal()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        livePulseVersion++
        aiRevealVersion++
    }

    override fun body(): ViewBuilder {
        val page = this
        val attribution = CardPayloadParser.parse("attribution", "{\"symbol\":\"${page.quote.symbol}\"}") as AttributionIntent
        val ctx = CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer)
        val aiSummary = page.buildInsightSummary()
        val wide = page.pagerData.pageViewWidth >= 768f
        val businessCards = listOfNotNull(
            page.insight.fundFlow?.let { BusinessInsightItem("资金流", FundFlowCardModel(it, "fund-flow:${page.symbol}")) },
            page.insight.fundamentals?.financial?.let { BusinessInsightItem("财务", FinancialCardModel(it, "financial:${page.symbol}")) },
            page.insight.fundamentals?.shareholder?.let { BusinessInsightItem("股东户数", ShareholderCardModel(it, "shareholders:${page.symbol}")) },
            page.insight.fundamentals?.billboard?.let { BusinessInsightItem("龙虎榜", BillboardCardModel(it, "billboard:${page.symbol}")) },
            page.insight.fundamentals?.actions?.takeIf { it.isNotEmpty() }?.let {
                BusinessInsightItem("分红与解禁", CorporateActionCardModel(it, "actions:${page.symbol}"))
            },
        )
        return {
            attr { backgroundColor(page.theme.page) }
            // 交接容器：灵动岛无动画 push 后原地接管全屏玻璃帧，
            // 整页内容（含顶栏/底栏）在容器上统一淡入（R4 两帧翻转）。
            View {
                attr {
                    flex(1f)
                    opacity(if (page.handoffPresented) 1f else 0f)
                    if (!page.reduceMotion) {
                        // 原生整页淡入（0.24s）已承载与形变的重叠渐显，
                        // 页内这层只负责平滑首帧内容落位，略短不拖总时长。
                        animate(Animation.easeOut(0.22f), "stock-detail-handoff")
                    }
                }
                Scroller {
                    attr {
                        flex(1f)
                        paddingLeft(14f)
                        paddingRight(14f)
                        paddingTop(page.pagerData.statusBarHeight + 73f)
                        paddingBottom(100f)
                    }
                    event {
                        scroll { params ->
                            page.updateTopProgress(params.offsetX, params.contentHeight, params.viewHeight)
                        }
                        contentSizeChanged { _, contentHeight ->
                            page.updateTopProgress(0f, contentHeight, page.pagerData.pageViewHeight)
                        }
                    }

                    // ---- Hero 行情（去卡片，直接铺底） ----
                    View {
                        attr { marginTop(page.theme.spacing.xl) }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter() }
                            View {
                                attr { flex(1f); flexDirectionRow(); alignItemsCenter(); flexWrapWrap() }
                                TickerText(
                                    text = { Format.price(page.quote.price) },
                                    previousText = { page.previousPriceText },
                                    loading = { page.quoteLoading },
                                    fontSize = page.theme.type.display,
                                    width = 142f,
                                    color = { page.toneColor() },
                                    theme = page.theme,
                                    lift = { page.tickerLift },
                                    directionUp = { page.tickerDirectionUp },
                                    reduceMotion = page.reduceMotion,
                                )
                                View {
                                    attr {
                                        marginLeft(page.theme.spacing.sm)
                                        paddingTop(3f); paddingBottom(3f); paddingLeft(10f); paddingRight(10f)
                                        backgroundColor(page.toneSoftColor())
                                        borderRadius(page.theme.inputRadius)
                                        alignItemsCenter(); justifyContentCenter()
                                    }
                                    TickerText(
                                        text = { Format.percent(page.quote.changePercent) },
                                        previousText = { page.previousPercentText },
                                        loading = { page.quoteLoading },
                                        fontSize = page.theme.type.sm,
                                        width = 70f,
                                        color = { page.toneColor() },
                                        theme = page.theme,
                                        lift = { page.tickerLift },
                                        directionUp = { page.tickerDirectionUp },
                                        reduceMotion = page.reduceMotion,
                                    )
                                }
                            }
                            // dataModeLabel 为 observable：vbind 包一层使其随标签变化重建
                            vbind({ page.dataModeLabel }) {
                                DataModeBadge(page.theme, page.dataModeLabel, page.hostGlassRenderer)
                            }
                        }
                        View {
                            attr { marginTop(4f) }
                            TickerText(
                                text = { "${Format.signed(page.quote.change)}  ${Format.percent(page.quote.changePercent)}" },
                                previousText = { page.previousChangeText },
                                loading = { page.quoteLoading },
                                fontSize = page.theme.type.body,
                                width = 168f,
                                color = { page.toneColor() },
                                theme = page.theme,
                                lift = { page.tickerLift },
                                directionUp = { page.tickerDirectionUp },
                                reduceMotion = page.reduceMotion,
                            )
                        }
                        Text {
                            attr {
                                text(page.quoteSourceLine())
                                marginTop(page.theme.spacing.sm)
                                fontSize(page.theme.type.meta)
                                color(page.theme.textTertiary)
                            }
                        }
                        LiveDot(page.theme, { page.livePulse }, page.reduceMotion)
                        View {
                            attr {
                                marginTop(page.theme.spacing.md)
                                height(36f)
                                paddingLeft(14f)
                                paddingRight(14f)
                                alignSelfFlexStart()
                                allCenter()
                                borderRadius(page.theme.inputRadius)
                                backgroundColor(if (page.watchlisted) page.theme.surfaceMuted else page.theme.brandSoft)
                                border(Border(1f, BorderStyle.SOLID, if (page.watchlistFeedback) page.theme.brand else page.theme.divider))
                                boxShadow(
                                    if (page.watchlistFeedback) BoxShadow(0f, 4f, 14f, page.theme.brand.opacity(0.18f))
                                    else BoxShadow(0f, 0f, 0f, page.theme.brand.opacity(0f))
                                )
                                transform(scale = if (page.watchlistFeedback) Scale(1.04f, 1.04f) else Scale.DEFAULT)
                                if (!page.reduceMotion) animate(Animation.easeOut(0.18f), page.watchlistFeedback)
                            }
                            Text {
                                attr {
                                    text(if (page.watchlisted) "已自选" else "加自选")
                                    fontSize(page.theme.type.label)
                                    fontWeightSemiBold()
                                    color(if (page.watchlisted) page.theme.textSecondary else page.theme.brand)
                                }
                            }
                            FocusHairline({ page.watchlistFeedback }, page.theme, page.reduceMotion)
                            event { click { page.toggleWatchlist() } }
                        }
                        vif({ page.watchlistHint.isNotEmpty() }) {
                            Text {
                                attr {
                                    text(page.watchlistHint)
                                    marginTop(6f)
                                    fontSize(page.theme.type.meta)
                                    color(page.theme.term)
                                }
                            }
                        }
                        // FR-W2 对照物：详情页是「当前事实」一侧，当初理由在此对照
                        vif({ page.watchlisted && page.watchlistReason().isNotEmpty() }) {
                            Text {
                                attr {
                                    text("当初理由：${page.watchlistReason()}")
                                    marginTop(4f)
                                    fontSize(page.theme.type.meta)
                                    lineHeight(16f)
                                    color(page.theme.textSecondary)
                                }
                            }
                        }
                    }

                    // ---- 行情指标：细分隔线网格（替代白卡） ----
                    SectionLabel("行情数据", page.theme)
                    View {
                        attr {
                            marginTop(page.theme.spacing.lg)
                            backgroundColor(page.theme.surfaceMuted)
                            borderRadius(page.theme.cardRadius)
                        }
                        // 指标值随行情刷新：数据驱动重建，vbind 范式（MarketPage 同款）
                        vbind({ page.quote }) {
                            MetricGrid(
                                listOf(
                                    DetailMetric("今开", Format.price(page.quote.open), page.marketColor(page.quote.open)),
                                    DetailMetric("最高", Format.price(page.quote.high), page.marketColor(page.quote.high)),
                                    DetailMetric("最低", Format.price(page.quote.low), page.marketColor(page.quote.low)),
                                    DetailMetric("昨收", Format.price(page.quote.previousClose), page.theme.textSecondary),
                                    DetailMetric("成交量", Format.compactAmount(page.quote.volume)),
                                    DetailMetric("成交额", Format.compactAmount(page.quote.amount)),
                                    DetailMetric("换手率", "${Format.decimal(page.quote.turnoverRate, 2)}%"),
                                    DetailMetric("总市值", Format.compactAmount(page.quote.marketCap)),
                                ),
                                page.theme,
                            )
                        }
                    }

                    // ---- 走势：去卡片，满宽绘制 + 分段控件 ----
                    SectionLabel("走势", page.theme)
                    ChartSegment(page.theme, { page.chartMode }, { page.chartPeriod }, page.reduceMotion) { m, p ->
                        page.chartMode = m
                        page.chartPeriod = p
                        page.selectedKLineIndex = -1
                    }
                    DetailChart(page.theme, { page.chartMode }, { page.chartPeriod }, page.quote, ctx, { page.selectedKLineIndex }) {
                        page.selectedKLineIndex = it
                    }

                    // ---- 关键指标：细分隔线网格 ----
                    SectionLabel("关键指标", page.theme)
                    View {
                        attr {
                            marginTop(page.theme.spacing.lg)
                            backgroundColor(page.theme.surfaceMuted)
                            borderRadius(page.theme.cardRadius)
                        }
                        MetricGrid(
                            listOf(
                                DetailMetric("PE(TTM)", Format.decimal(page.quote.peTtm, 2)),
                                DetailMetric("PB", Format.decimal(page.quote.pb, 2)),
                                DetailMetric("振幅", Format.decimal((page.quote.high - page.quote.low) / page.quote.previousClose * 100, 2) + "%"),
                            ),
                            page.theme,
                        )
                    }
                    Text {
                        attr {
                            text("指标要结合行业、增长与盈利质量一起看，单个数值不构成结论。")
                            marginTop(page.theme.spacing.md)
                            fontSize(page.theme.type.label)
                            lineHeight(17f)
                            color(page.theme.textTertiary)
                        }
                    }

                    // ---- 真实业务数据：宽屏进入两列 Bento，窄屏保持线性阅读 ----
                    BusinessInsightGrid(
                        items = businessCards,
                        context = ctx,
                        theme = page.theme,
                        wide = wide,
                        entranceVisible = { page.entranceVisible },
                        reduceMotion = page.reduceMotion,
                    )

                    SectionLabel("公告与研报", page.theme)
                    CardShell(DisclosureCardModel(page.insight.disclosures, "disclosures:${page.symbol}"), ctx)

                    // ---- AI 解读：要点卡片（方案 C） ----
                    SectionLabel("AI 解读", page.theme)
                    AiInsightBlock(
                        summary = { if (page.aiRevealSource.isEmpty()) aiSummary else page.aiRevealSource },
                        revealLimit = { page.aiRevealLimit },
                        retryActive = { page.aiRetryActive },
                        theme = page.theme,
                        reduceMotion = page.reduceMotion,
                        onRetry = { page.retryAiReveal() },
                    )

                    // ---- 涨跌归因：列表 + 分隔线 ----
                    AttributionBlock(
                        AttributionCardModel(page.quote, attribution.direction, attribution.factors),
                        page.theme,
                        expandedKey = { page.expandedAttributionKey },
                        reduceMotion = page.reduceMotion,
                    ) { key ->
                        page.expandedAttributionKey = if (page.expandedAttributionKey == key) "" else key
                    }

                }

                AppTopBar(
                    title = page.quote.name,
                    subtitle = page.quote.symbol,
                    statusBarHeight = page.pagerData.statusBarHeight,
                    theme = page.theme,
                    renderer = page.hostGlassRenderer,
                    backLabel = "返回",
                    onBack = { page.closePage() },
                    compactLine = { "${Format.price(page.quote.price)}  ${Format.percent(page.quote.changePercent)}" },
                    compactLineColor = { page.toneColor() },
                    compactVisible = { page.topCompactVisible },
                    progress = { page.topProgress },
                    reduceMotion = page.reduceMotion,
                    actions = listOf(
                        (if (page.watchlisted) "✓" else "+") to { page.toggleWatchlist() },
                        "⋯" to { page.watchlistHint = "更多操作稍后接入" },
                    ),
                )
                DetailBottomBar(
                    theme = page.theme,
                    renderer = page.hostGlassRenderer,
                    bottomInset = page.pagerData.safeAreaInsets.bottom,
                    watchlisted = { page.watchlisted },
                    feedback = { page.watchlistFeedback },
                    reduceMotion = page.reduceMotion,
                    onToggleWatchlist = { page.toggleWatchlist() },
                    onBackToChat = { page.closePage() },
                    onAskAi = {
                        page.openChatWithQuestion(page.askAiQuestion())
                    },
                )
            }
        }
    }

    private fun toggleWatchlist() {
        if (watchlisted) {
            watchlistStore.remove(symbol)
            watchlisted = false
            watchlistHint = "已从自选移除"
            playWatchlistFeedback()
            return
        }
        when (watchlistStore.add(symbol, quote.name)) {
            WatchlistAddResult.ADDED -> {
                watchlisted = true
                watchlistHint = "已加入自选"
                // FR-W2：详情页入口的来源即理由，可在自选长按改写
                watchlistStore.setReason(symbol, "详情页添加")
                playWatchlistFeedback()
            }
            WatchlistAddResult.ALREADY_IN -> {
                watchlisted = true
                watchlistHint = "已在自选中"
                playWatchlistFeedback()
            }
            WatchlistAddResult.FULL -> watchlistHint = "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
        }
    }

    /** FR-W2：当前自选理由（未自选或未填返回空）。 */
    private fun watchlistReason(): String =
        watchlistStore.list().firstOrNull { it.symbol == symbol }?.reason.orEmpty()

    private fun applyQuote(next: Quote) {
        val old = quote
        val changed = old.price != next.price || old.changePercent != next.changePercent
        previousPriceText = Format.price(old.price)
        previousPercentText = Format.percent(old.changePercent)
        previousChangeText = "${Format.signed(old.change)}  ${Format.percent(old.changePercent)}"
        tickerDirectionUp = next.price >= old.price
        quote = next
        if (changed) playTicker()
    }

    private fun playTicker() {
        if (reduceMotion) return
        tickerLift = true
        setTimeout(180) { tickerLift = false }
    }

    private fun playWatchlistFeedback() {
        watchlistFeedback = true
        setTimeout(260) { watchlistFeedback = false }
    }

    private fun startLivePulse() {
        if (reduceMotion) {
            livePulse = false
            return
        }
        val version = ++livePulseVersion
        fun tick() {
            if (version != livePulseVersion) return
            livePulse = !livePulse
            setTimeout(680) { tick() }
        }
        setTimeout(0) { tick() }
    }

    private fun startAiReveal() {
        val source = buildInsightSummary()
        if (source == aiRevealSource && aiRevealLimit >= source.length) return
        aiRevealSource = source
        val version = ++aiRevealVersion
        if (reduceMotion) {
            aiRevealLimit = source.length
            return
        }
        aiRevealLimit = 0
        fun tick() {
            if (version != aiRevealVersion) return
            aiRevealLimit = (aiRevealLimit + 3).coerceAtMost(source.length)
            if (aiRevealLimit < source.length) setTimeout(28) { tick() }
        }
        setTimeout(0) { tick() }
    }

    private fun retryAiReveal() {
        aiRetryActive = true
        aiRevealSource = ""
        startAiReveal()
        setTimeout(420) { aiRetryActive = false }
    }

    private fun buildInsightSummary(): String {
        val financial = insight.fundamentals?.financial
        val flow = insight.fundFlow
        val business = financial?.explanation ?: "财务数据仍在加载，先不要用单一估值指标下结论。"
        val trading = flow?.explanation ?: "资金流数据仍在加载。"
        return "$business $trading"
    }

    private fun toneColor() = when {
        quote.change > 0.0 -> theme.rise
        quote.change < 0.0 -> theme.fall
        else -> theme.textPrimary
    }

    private fun toneSoftColor() = when {
        quote.change > 0.0 -> theme.riseSoft
        quote.change < 0.0 -> theme.fallSoft
        else -> theme.surfaceMuted
    }

    private fun quoteSourceLine(): String {
        val source = quote.source.ifEmpty { "正在连接行情" }
        val time = quote.timestamp.ifEmpty { "等待刷新" }
        return "数据源：$source · 更新于 $time"
    }

    private fun askAiQuestion(): String = "为什么${quote.name}今天${if (quote.change >= 0.0) "涨" else "跌"}？结合资金流、估值和公告帮我拆一下。"

    private fun marketColor(value: Double) = when {
        quote.previousClose <= 0.0 -> theme.textSecondary
        value > quote.previousClose -> theme.rise
        value < quote.previousClose -> theme.fall
        else -> theme.textSecondary
    }

    private fun updateTopProgress(offsetY: Float, contentHeight: Float, viewHeight: Float) {
        val scrollable = (contentHeight - viewHeight).coerceAtLeast(1f)
        topProgress = (offsetY / scrollable).coerceIn(0f, 1f)
        topCompactVisible = offsetY > 60f
    }
}

private data class DetailMetric(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
)

private data class BusinessInsightItem(
    val label: String,
    val model: CardModel,
)

private fun ViewContainer<*, *>.SectionLabel(text: String, theme: StockChatTheme) {
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text(text)
            fontSize(theme.type.label)
            fontWeightSemiBold()
            color(theme.textTertiary)
        }
    }
}

private fun ViewContainer<*, *>.TickerText(
    // 可变状态一律传 lambda：observable 读取延迟到 attr/vif 闭包内（R1），
    // 行情 tick 时文本/颜色随 attr 重跑刷新，lift 动画才有驱动 key（R2）。
    text: () -> String,
    previousText: () -> String,
    loading: () -> Boolean,
    fontSize: Float,
    width: Float,
    color: () -> Color,
    theme: StockChatTheme,
    lift: () -> Boolean,
    directionUp: () -> Boolean,
    reduceMotion: Boolean,
) {
    vif({ loading() }) {
        View {
            attr {
                width(width)
                height(fontSize * 0.72f)
                borderRadius(6f)
                backgroundColor(theme.surfaceMuted)
            }
        }
    }
    vif({ !loading() }) {
    View {
        attr {
            width(width)
            height(fontSize * 1.12f)
            overflow(true)
        }
        vif({ !reduceMotion && lift() && previousText().isNotEmpty() }) {
            Text {
                attr {
                    absolutePosition(top = 0f, left = 0f)
                    text(previousText())
                    fontSize(fontSize)
                    fontWeightBold()
                    color(color().opacity(0.72f))
                    transform(Translate(0f, if (directionUp()) -0.86f else 0.86f))
                    opacity(0f)
                    animate(Animation.springEaseOut(0.30f, 0.78f, 0.18f), lift())
                }
            }
        }
        Text {
            attr {
                absolutePosition(top = 0f, left = 0f)
                text(text())
                fontSize(fontSize)
                fontWeightBold()
                color(color())
                opacity(if (lift()) 0.18f else 1f)
                if (!reduceMotion) {
                    transform(Translate(0f, if (lift()) {
                        if (directionUp()) 0.72f else -0.72f
                    } else {
                        0f
                    }))
                    animate(Animation.springEaseOut(0.32f, 0.80f, 0.16f), lift())
                }
            }
        }
        vif({ !reduceMotion && lift() }) {
            View {
                attr {
                    absolutePosition(left = 0f, right = 0f, bottom = 0f)
                    height(1f)
                    backgroundColor(color().opacity(0.24f))
                    opacity(0.6f)
                    animate(Animation.easeOut(0.20f), lift())
                }
            }
        }
    }
    }
}

private fun ViewContainer<*, *>.FocusHairline(
    visible: () -> Boolean,
    theme: StockChatTheme,
    reduceMotion: Boolean,
) {
    View {
        attr {
            absolutePositionAllZero()
            borderRadius(theme.inputRadius)
            border(Border(1.5f, BorderStyle.SOLID, if (visible()) theme.brand.opacity(0.50f) else theme.brand.opacity(0f)))
            touchEnable(false)
            if (!reduceMotion) {
                opacity(if (visible()) 1f else 0f)
                animate(Animation.easeOut(0.20f), visible())
            }
        }
    }
}

private fun ViewContainer<*, *>.MetricGrid(items: List<DetailMetric>, theme: StockChatTheme) {
    val rows = items.chunked(4)
    rows.forEachIndexed { r, row ->
        View {
            attr { flexDirectionRow() }
            row.forEachIndexed { c, item ->
                View {
                    attr {
                        flex(1f)
                        paddingTop(theme.spacing.lg); paddingBottom(theme.spacing.lg)
                        paddingLeft(theme.spacing.md); paddingRight(theme.spacing.md)
                        if (c < row.lastIndex) borderRight(Border(0.5f, BorderStyle.SOLID, theme.divider))
                        if (r < rows.lastIndex) borderBottom(Border(0.5f, BorderStyle.SOLID, theme.divider))
                    }
                    Text { attr { text(item.label); fontSize(theme.type.label); color(theme.textTertiary) } }
                    Text {
                        attr {
                            text(item.value)
                            marginTop(5f)
                            fontSize(theme.type.body)
                            fontWeightMedium()
                            color(item.valueColor ?: theme.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.BusinessInsightGrid(
    items: List<BusinessInsightItem>,
    context: CardContext,
    theme: StockChatTheme,
    wide: Boolean,
    // 传 lambda 而非 Boolean：闭包实参是建视图时的首帧快照（R1），
    // observable 的读取必须延迟到 RevealBlock 的 attr 闭包内才建立依赖。
    entranceVisible: () -> Boolean,
    reduceMotion: Boolean,
) {
    if (items.isEmpty()) {
        SectionLabel("业务数据", theme)
        View {
            attr {
                marginTop(theme.spacing.lg)
                height(74f)
                borderRadius(theme.cardRadius)
                backgroundColor(theme.surfaceMuted)
                allCenter()
            }
            Text {
                attr {
                    text("业务数据正在加载")
                    fontSize(theme.type.label)
                    color(theme.textTertiary)
                }
            }
        }
        return
    }
    if (wide) {
        SectionLabel("业务数据", theme)
        items.chunked(2).forEachIndexed { rowIndex, row ->
            View {
                attr {
                    marginTop(if (rowIndex == 0) theme.spacing.md else 0f)
                    flexDirectionRow()
                    alignItemsStretch()
                }
                row.forEachIndexed { columnIndex, item ->
                    View {
                        attr {
                            flex(1f)
                            if (columnIndex == 0) marginRight(6f) else marginLeft(6f)
                        }
                        RevealBlock(rowIndex * 2 + columnIndex, entranceVisible, reduceMotion) {
                            SectionLabel(item.label, theme)
                            CardShell(item.model, context)
                        }
                    }
                }
                if (row.size == 1) View { attr { flex(1f); marginLeft(6f) } }
            }
        }
        return
    }
    items.forEachIndexed { index, item ->
        RevealBlock(index, entranceVisible, reduceMotion) {
            SectionLabel(item.label, theme)
            CardShell(item.model, context)
        }
    }
}

private fun ViewContainer<*, *>.RevealBlock(
    index: Int,
    visible: () -> Boolean,
    reduceMotion: Boolean,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            // 驱动 observable 必须在 attr 闭包内读取（R1），并置于其他读取之后、
            // 紧邻 animate()（R2）。此前以普通 Boolean 快照传入：attr 不重跑、
            // animate() 绑定不到 key，入场链路整体失效。
            val shown = visible()
            opacity(if (shown) 1f else 0f)
            if (!reduceMotion) {
                transform(Translate(0f, if (shown) 0f else 0.16f))
                // 无条件注册 easeOut（含未呈现态）：flip 周期消费的正是上一周期
                // 注册的这份动画（R5）。此前 else 分支注册 linear(0)，入场被
                // 消费成 0 时长瞬移——与 CardSheet/ChatScaffolding 同一范式。
                animate(Animation.easeOut(0.28f).delay(0.04f * index), shown)
            }
        }
        content()
    }
}

private fun ViewContainer<*, *>.ChartSegment(
    theme: StockChatTheme,
    chartMode: () -> StockChartMode,
    chartPeriod: () -> StockChartPeriod,
    reduceMotion: Boolean,
    onSelect: (StockChartMode, StockChartPeriod) -> Unit,
) {
    val tabs = listOf(
        StockChartMode.TIMELINE to "分时",
        StockChartMode.K_LINE to "日K",
        StockChartMode.K_LINE to "周K",
        StockChartMode.K_LINE to "月K",
    )
    // activeIndex 在闭包内实时派生（R1）：捕获计算结果会让 tab 高亮/滑块全部冻结。
    fun activeIdx(): Int = when {
        chartMode() == StockChartMode.TIMELINE -> 0
        chartPeriod() == StockChartPeriod.WEEK -> 2
        chartPeriod() == StockChartPeriod.MONTH -> 3
        else -> 1
    }
    View {
        attr { marginTop(theme.spacing.lg) }
        View {
            attr {
                height(38f)
                padding(3f)
                backgroundColor(theme.surfaceMuted)
                borderRadius(theme.inputRadius)
                border(Border(1f, BorderStyle.SOLID, theme.divider))
                overflow(true)
            }
            // 滑块位置由占位 view 数量决定（数量型变化）：vbind 随 activeIndex 重建，
            // 重建即瞬移到位（R4：新挂载首帧不播动画）。原 spring 滑动动画绑定的
            // 是捕获的 Int、从未生效，属死代码，已随本次修正移除；若要滑动过渡
            // 需改为 transform 驱动的单滑块方案（独立任务）。
            vbind({ activeIdx() }) {
                View {
                    attr {
                        absolutePositionAllZero()
                        padding(3f)
                        flexDirectionRow()
                        touchEnable(false)
                    }
                    repeat(activeIdx()) { View { attr { flex(1f) } } }
                    View {
                        attr {
                            flex(1f)
                            height(32f)
                            borderRadius(theme.inputRadius)
                            backgroundColor(theme.brandSoft)
                            border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(0.24f)))
                            boxShadow(BoxShadow(0f, 2f, 8f, theme.brand.opacity(0.08f)))
                        }
                    }
                    repeat(3 - activeIdx()) { View { attr { flex(1f) } } }
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    height(32f)
                }
                tabs.forEachIndexed { index, (mode, label) ->
                    val period = when (index) {
                        2 -> StockChartPeriod.WEEK
                        3 -> StockChartPeriod.MONTH
                        else -> StockChartPeriod.DAY
                    }
                    fun active(): Boolean = index == activeIdx()
                    View {
                        attr {
                            flex(1f)
                            height(32f)
                            allCenter()
                            borderRadius(theme.inputRadius)
                            if (!reduceMotion) animate(Animation.easeOut(0.16f), active())
                        }
                        Text {
                            attr {
                                text(label)
                                fontSize(theme.type.label)
                                fontWeightSemiBold()
                                color(if (active()) theme.brand else theme.textSecondary)
                            }
                        }
                        event { click { onSelect(mode, period) } }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DetailChart(
    theme: StockChatTheme,
    chartMode: () -> StockChartMode,
    chartPeriod: () -> StockChartPeriod,
    quote: Quote,
    ctx: CardContext,
    selectedKLineIndex: () -> Int,
    onSelectKLine: (Int) -> Unit,
) {
    vif({ chartMode() == StockChartMode.TIMELINE }) {
        KuiklyTimelineChart(this, quote, ctx, height = 132f)
        Text {
            attr {
                text("虚线为昨收基准")
                marginTop(6f); fontSize(theme.type.meta); color(theme.textTertiary)
            }
        }
    }
    vif({ chartMode() == StockChartMode.K_LINE && chartPeriod() == StockChartPeriod.DAY }) {
        KLineChart(this, StockChartCardModel(quote, StockChartMode.K_LINE, StockChartPeriod.DAY), ctx, selectedKLineIndex(), onSelectKLine)
    }
    vif({ chartMode() == StockChartMode.K_LINE && chartPeriod() == StockChartPeriod.WEEK }) {
        KLineChart(this, StockChartCardModel(quote, StockChartMode.K_LINE, StockChartPeriod.WEEK), ctx, selectedKLineIndex(), onSelectKLine)
    }
    vif({ chartMode() == StockChartMode.K_LINE && chartPeriod() == StockChartPeriod.MONTH }) {
        KLineChart(this, StockChartCardModel(quote, StockChartMode.K_LINE, StockChartPeriod.MONTH), ctx, selectedKLineIndex(), onSelectKLine)
    }
}

private fun ViewContainer<*, *>.LiveDot(theme: StockChatTheme, pulse: () -> Boolean, reduceMotion: Boolean) {
    View {
        attr {
            marginTop(theme.spacing.sm)
            flexDirectionRow()
            alignItemsCenter()
        }
        View {
            attr {
                size(18f, 18f)
                allCenter()
                marginLeft(-4f)
                marginRight(3f)
            }
            View {
                attr {
                    absolutePosition(top = 1f, left = 1f)
                    val p = pulse()
                    size(if (p) 16f else 10f, if (p) 16f else 10f)
                    borderRadius(if (p) 8f else 5f)
                    backgroundColor(theme.brand.opacity(if (p) 0f else 0.18f))
                    border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(if (p) 0f else 0.28f)))
                    if (!reduceMotion) animate(Animation.linear(0.68f), pulse())
                    touchEnable(false)
                }
            }
            View {
                attr {
                    size(8f, 8f)
                    borderRadius(4f)
                    backgroundColor(theme.brand)
                    opacity(if (pulse()) 0.82f else 1f)
                    boxShadow(BoxShadow(0f, 0f, 8f, theme.brand.opacity(0.24f)))
                    if (!reduceMotion) {
                        transform(scale = if (pulse()) Scale(1.08f, 1.08f) else Scale.DEFAULT)
                        animate(Animation.easeOut(0.34f), pulse())
                    }
                }
            }
        }
        Text {
            attr {
                text("实时同步")
                marginLeft(7f)
                fontSize(theme.type.meta)
                color(theme.textTertiary)
            }
        }
    }
}

private fun ViewContainer<*, *>.DetailBottomBar(
    theme: StockChatTheme,
    renderer: GlassRenderer,
    bottomInset: Float,
    watchlisted: () -> Boolean,
    feedback: () -> Boolean,
    reduceMotion: Boolean,
    onToggleWatchlist: () -> Unit,
    onBackToChat: () -> Unit,
    onAskAi: () -> Unit,
) {
    View {
        attr {
            absolutePosition(
                bottom = 18f + bottomInset,
                left = 14f,
                right = 14f,
            )
            height(50f)
            padding(4f)
            borderRadius(25f)
            flexDirectionRow()
            alignItemsCenter()
        }
        GlassBackdrop(theme.glass.peek, renderer)
        DetailBottomAction(
            label = { if (watchlisted()) "已自选" else "加自选" },
            primary = false,
            theme = theme,
            feedback = feedback,
            reduceMotion = reduceMotion,
        ) {
            onToggleWatchlist()
        }
        DetailBottomAction(
            label = { "回到对话" },
            primary = true,
            theme = theme,
            feedback = { false },
            reduceMotion = reduceMotion,
        ) {
            onBackToChat()
        }
        DetailBottomAction(
            label = { "问 AI" },
            primary = false,
            theme = theme,
            feedback = { false },
            reduceMotion = reduceMotion,
        ) {
            onAskAi()
        }
    }
}

private fun ViewContainer<*, *>.DetailBottomAction(
    label: () -> String,
    primary: Boolean,
    theme: StockChatTheme,
    feedback: () -> Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            height(42f)
            marginLeft(2f)
            marginRight(2f)
            allCenter()
            borderRadius(21f)
            backgroundColor(if (primary) theme.brand else theme.surface.opacity(0.52f))
            border(Border(1f, BorderStyle.SOLID, if (feedback()) theme.brand else theme.divider.opacity(if (primary) 0f else 0.55f)))
            boxShadow(
                if (feedback()) BoxShadow(0f, 3f, 12f, theme.brand.opacity(0.18f))
                else BoxShadow(0f, 0f, 0f, theme.brand.opacity(0f))
            )
            if (!reduceMotion) {
                transform(scale = if (feedback()) Scale(1.03f, 1.03f) else Scale.DEFAULT)
                animate(Animation.easeOut(0.18f), feedback())
            }
        }
        Text {
            attr {
                text(label())
                fontSize(theme.type.sm)
                fontWeightSemiBold()
                color(if (primary) theme.onBrand else theme.textPrimary)
            }
        }
        FocusHairline({ feedback() || primary }, theme, reduceMotion)
        event { click { onClick() } }
    }
}

private fun ViewContainer<*, *>.AiInsightBlock(
    summary: () -> String,
    revealLimit: () -> Int,
    retryActive: () -> Boolean,
    theme: StockChatTheme,
    reduceMotion: Boolean,
    onRetry: () -> Unit,
) {
    View {
        attr {
            marginTop(theme.spacing.lg)
            backgroundColor(theme.surfaceMuted)
            borderRadius(theme.cardRadius)
            border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(0.10f)))
        }
        View { attr { height(4f); backgroundColor(theme.brand) } }
        View {
            attr { padding(theme.spacing.lg) }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("AI 解读")
                        flex(1f)
                        fontSize(theme.type.label)
                        fontWeightSemiBold()
                        color(theme.brand)
                    }
                }
                View {
                    attr {
                        height(28f)
                        paddingLeft(10f)
                        paddingRight(10f)
                        allCenter()
                        borderRadius(14f)
                        backgroundColor(theme.surface)
                        border(Border(1f, BorderStyle.SOLID, theme.divider))
                        if (!reduceMotion) {
                            transform(Rotate(if (retryActive()) 360f else 0f, 0f, 0f))
                            animate(Animation.easeOut(0.60f), retryActive())
                        }
                    }
                    Text {
                        attr {
                            text("↻ 重新解读")
                            fontSize(theme.type.meta)
                            fontWeightSemiBold()
                            color(theme.brand)
                        }
                    }
                    event { click { onRetry() } }
                }
            }
            // 逐句揭示：内容随 revealLimit 逐字增长，属数据驱动重建（MarketPage
            // vbind 同款范式），无注册动画，重建不会丢动画状态。
            vbind({ revealLimit() }) {
                val revealed = if (revealLimit() <= 0) "" else summary().take(revealLimit().coerceAtMost(summary().length))
                val sentences = revealed.split(Regex("[。，]")).map { it.trim() }.filter { it.isNotEmpty() }
                if (sentences.isNotEmpty()) {
                    Text {
                        attr {
                            text(sentences.first() + "。")
                            marginTop(theme.spacing.sm)
                            fontSize(theme.type.body)
                            fontWeightSemiBold()
                            color(theme.textPrimary)
                            lineHeight(21f)
                        }
                    }
                }
                if (sentences.isEmpty()) {
                    View {
                        attr {
                            marginTop(theme.spacing.md)
                            width(180f)
                            height(18f)
                            borderRadius(5f)
                            backgroundColor(theme.surface.opacity(0.72f))
                        }
                    }
                    View {
                        attr {
                            marginTop(theme.spacing.sm)
                            width(240f)
                            height(12f)
                            borderRadius(4f)
                            backgroundColor(theme.surface.opacity(0.62f))
                        }
                    }
                }
                sentences.drop(1).forEach { s ->
                    View {
                        attr { flexDirectionRow(); marginTop(theme.spacing.sm); alignItemsFlexStart() }
                        View {
                            attr {
                                width(6f); height(6f); borderRadius(3f)
                                backgroundColor(theme.brand)
                                marginTop(6f); marginRight(theme.spacing.sm)
                            }
                        }
                        Text {
                            attr {
                                flex(1f)
                                text(s + "。")
                                fontSize(theme.type.sm)
                                lineHeight(19f)
                                color(theme.textSecondary)
                            }
                        }
                    }
                }
            }
            Text {
                attr {
                    text("AI 生成 · 仅供参考，不构成投资建议")
                    marginTop(theme.spacing.md)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AttributionBlock(
    model: AttributionCardModel,
    theme: StockChatTheme,
    expandedKey: () -> String,
    reduceMotion: Boolean,
    onToggle: (String) -> Unit,
) {
    val factors = model.factors
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text("为什么${if (model.quote.rising) "涨" else "跌"}")
            fontSize(theme.type.title)
            fontWeightSemiBold()
            color(theme.textPrimary)
        }
    }
    factors.forEachIndexed { index, factor ->
        val key = "$index:${factor.name}"
        // expanded 必须在闭包内实时求值：expandedKey() 读 observable（R1），
        // 捕获成 Boolean 会让 vif 永不重跑、animate 绑不到 key。
        fun expanded(): Boolean = expandedKey() == key
        View {
            attr {
                marginTop(theme.spacing.md); paddingTop(theme.spacing.md)
                if (index > 0) borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
                if (!reduceMotion) animate(Animation.easeOut(0.18f), expanded())
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(factor.name)
                        flex(1f)
                        fontSize(theme.type.sm)
                        fontWeightMedium()
                        color(theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("${Format.decimal(factor.weight * 100, 0)}%")
                        fontSize(theme.type.label)
                        color(theme.textSecondary)
                    }
                }
                Text {
                    attr {
                        text(if (expanded()) "⌄" else "›")
                        marginLeft(8f)
                        fontSize(theme.type.body)
                        color(theme.brand)
                    }
                }
            }
            Text {
                attr {
                    text(factor.description)
                    marginTop(4f)
                    fontSize(theme.type.label)
                    lineHeight(16f)
                    color(theme.textSecondary)
                }
            }
            View {
                attr {
                    marginTop(8f)
                    height(4f)
                    borderRadius(2f)
                    flexDirectionRow()
                    backgroundColor(theme.surfaceMuted)
                }
                View {
                    attr {
                        flex(factor.weight.toFloat().coerceIn(0.02f, 1f))
                        borderRadius(2f)
                        backgroundColor(theme.brand)
                    }
                }
                View { attr { flex((1f - factor.weight.toFloat()).coerceAtLeast(0.001f)) } }
            }
            vif({ expanded() }) {
                View {
                    attr {
                        marginTop(theme.spacing.sm)
                        padding(theme.spacing.md)
                        borderRadius(theme.inputRadius)
                        backgroundColor(theme.surfaceMuted)
                        border(Border(1f, BorderStyle.SOLID, theme.divider))
                        opacity(if (expanded()) 1f else 0f)
                        if (!reduceMotion) {
                            transform(Translate(0f, if (expanded()) 0f else 0.10f))
                            animate(Animation.easeOut(0.20f), expanded())
                        }
                    }
                    Text {
                        attr {
                            text("依据：${factor.source.ifEmpty { model.source.ifEmpty { "行情与公开资料" } }} · ${model.asOf.ifEmpty { "当前快照" }}")
                            fontSize(theme.type.meta)
                            fontWeightSemiBold()
                            color(theme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text("这项归因主要说明它对当日涨跌的相对影响强弱，需要和其它原因一起看。")
                            marginTop(5f)
                            fontSize(theme.type.label)
                            lineHeight(16f)
                            color(theme.textSecondary)
                        }
                    }
                }
            }
            event { click { onToggle(key) } }
        }
    }
}

private fun ViewContainer<*, *>.NewsSection(theme: StockChatTheme) {
    val items = listOf(
        Triple("公司发布近期经营情况说明", "公司公告", "2 小时前"),
        Triple("白酒板块盘中震荡，龙头股表现分化", "证券时报", "3 小时前"),
        Triple("机构关注消费复苏节奏与渠道库存", "公开研报摘要", "昨天"),
    )
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text("相关资讯")
            fontSize(theme.type.label)
            fontWeightSemiBold()
            color(theme.textTertiary)
        }
    }
    items.forEachIndexed { index, item ->
        View {
            attr {
                marginTop(theme.spacing.md); paddingTop(theme.spacing.md)
                if (index > 0) borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
            }
            Text {
                attr {
                    text(item.first)
                    fontSize(theme.type.sm)
                    lineHeight(19f)
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text("${item.second}  ${item.third}")
                    marginTop(5f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}
