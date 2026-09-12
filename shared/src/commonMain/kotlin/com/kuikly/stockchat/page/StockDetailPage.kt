package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.data.provider.AiProvider
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
import com.kuikly.stockchat.cards.stock.MarketCardRenderers
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.chart.model.KLineCalculator
import com.kuikly.stockchat.chart.model.ChartViewportAction
import com.kuikly.stockchat.chart.model.ChartViewportCommand
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openChatWithQuestion
import com.kuikly.stockchat.common.openUrl
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.MarketDataPrefs
import com.kuikly.stockchat.data.MarketDataSource
import com.kuikly.stockchat.data.provider.MarketTimelineSpec
import com.kuikly.stockchat.data.provider.QuotePrefetchStore
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.DisclosureKind
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteLoadResult
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.data.provider.RatingSpectrum
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.detail.quote.state.DetailDataCoordinator
import com.kuikly.stockchat.detail.quote.state.DetailDataEffect
import com.kuikly.stockchat.detail.quote.state.DetailDataState
import com.kuikly.stockchat.detail.quote.state.DetailInsightPort
import com.kuikly.stockchat.detail.quote.state.DetailNewsPort
import com.kuikly.stockchat.detail.quote.state.DetailQuotePort
import com.kuikly.stockchat.detail.quote.state.KuiklyDetailDataScheduler
import com.kuikly.stockchat.detail.chart.state.DetailChartEffect
import com.kuikly.stockchat.detail.chart.state.DetailChartHostPort
import com.kuikly.stockchat.detail.chart.state.DetailChartInteractionCoordinator
import com.kuikly.stockchat.detail.chart.state.DetailChartState
import com.kuikly.stockchat.detail.chart.state.KuiklyDetailChartScheduler
import com.kuikly.stockchat.detail.ai.state.DetailAiHostPort
import com.kuikly.stockchat.detail.ai.state.DetailAiInsightCoordinator
import com.kuikly.stockchat.detail.ai.state.DetailAiState
import com.kuikly.stockchat.detail.ai.state.KuiklyDetailAiScheduler
import com.kuikly.stockchat.detail.overlay.state.DetailOverlayCoordinator
import com.kuikly.stockchat.detail.overlay.state.DetailOverlayState
import com.kuikly.stockchat.detail.overlay.state.KuiklyDetailOverlayScheduler
import com.kuikly.stockchat.data.config.AiConfig
// doc 29 集成：共享基建 + 板块组件（事件回调经这些基建接线）
import com.kuikly.stockchat.page.detail.AnchorIndex
import com.kuikly.stockchat.page.detail.AnomalyPoint
import com.kuikly.stockchat.page.detail.CardFootnote
import com.kuikly.stockchat.page.detail.ContextChip
import com.kuikly.stockchat.page.detail.ContextChipStore
import com.kuikly.stockchat.page.detail.DetailCompanyProfile
import com.kuikly.stockchat.page.detail.DetailCompanyProfileCatalog
import com.kuikly.stockchat.page.detail.DetailChartHeaderInput
import com.kuikly.stockchat.page.detail.DetailChartHeaderResolver
import com.kuikly.stockchat.page.detail.DetailMetric
import com.kuikly.stockchat.page.detail.DetailOverlay
import com.kuikly.stockchat.page.detail.Materiality
import com.kuikly.stockchat.page.detail.RelevanceAnchor
import com.kuikly.stockchat.page.detail.detectAnomalies
import com.kuikly.stockchat.page.detail.materialityOf
import com.kuikly.stockchat.page.detail.pickPinnedCard
import com.kuikly.stockchat.page.detail.promptFragment
import com.kuikly.stockchat.page.detail.scoreNewsSentiment
import com.kuikly.stockchat.page.detail.shareholderFootnote
import com.kuikly.stockchat.page.detail.FactorSpec
import com.kuikly.stockchat.page.components.BalanceSegment
import com.kuikly.stockchat.page.components.BalanceSpectrumBlock
import com.kuikly.stockchat.page.components.BlockState
import com.kuikly.stockchat.page.components.ChartFlag
import com.kuikly.stockchat.page.components.FactorReplayBlock
import com.kuikly.stockchat.page.components.FeatureTile
import com.kuikly.stockchat.page.components.IndustryCompareOverlay
import com.kuikly.stockchat.page.components.MaterialityBadge
import com.kuikly.stockchat.page.components.QuickReasonChips
import com.kuikly.stockchat.page.components.detailTimelineSeries
import com.kuikly.stockchat.page.components.formatTapeTime
import com.kuikly.stockchat.page.components.truncateByWidth
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.page.components.DetailTimelineChart
import com.kuikly.stockchat.page.components.NewsMarquee
import com.kuikly.stockchat.page.components.NewsSummaryBar
import com.kuikly.stockchat.page.components.estimateNewsMarqueeLoopWidth
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.AppTopBarAction
import com.kuikly.stockchat.page.components.LineIconBookmark
import com.kuikly.stockchat.page.components.LineIconDots
import com.kuikly.stockchat.page.components.AtmosphereBackdrop
import com.kuikly.stockchat.page.components.DataModeBadge
import com.kuikly.stockchat.page.components.LineIconBarChart
import com.kuikly.stockchat.page.components.LineIconCopy
import com.kuikly.stockchat.page.components.LineIconPin
import com.kuikly.stockchat.page.components.LineIconPlus
import com.kuikly.stockchat.page.components.LineIconMinus
import com.kuikly.stockchat.page.components.LineIconArrowLeft
import com.kuikly.stockchat.page.components.LineIconArrowRight
import com.kuikly.stockchat.page.components.LineIconReset
import com.kuikly.stockchat.base.BridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.module.SharedPreferencesModule
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
    // Quote/Insight/News 加载域：唯一 owner 是 DetailDataCoordinator（Wave 2 第 1
    // 刀，见 docs/39 §9）。字段以只读 getter 转发到 detailDataState 的 observable，
    // 保证 DSL 闭包内的读取仍建立反应式依赖（同 Chat Island 模式，见
    // ChatPage.islandExpanded）。ticker 动效、声呐异动、AI 触发仍在本页处理，见
    // handleDetailDataEffect。
    private val detailDataState = DetailDataState()
    private val detailDataCoordinator by lazy {
        DetailDataCoordinator(
            state = detailDataState,
            quotePort = object : DetailQuotePort {
                override fun cachedOrOffline(symbol: String) = quoteRepository.cachedOrOffline(symbol)
                override fun load(symbol: String, onResult: (QuoteLoadResult) -> Unit) =
                    quoteRepository.load(symbol, onResult)
            },
            insightPort = object : DetailInsightPort {
                override fun cachedStock(symbol: String) = dependencies.insightRepository.cachedStock(symbol)
                override fun loadStock(symbol: String, onResult: (StockInsightBundle) -> Unit) =
                    dependencies.insightRepository.loadStock(symbol, onResult)
            },
            newsPort = object : DetailNewsPort {
                override fun stockNews(symbol: String, onResult: (List<NewsItem>) -> Unit) =
                    dependencies.stockNewsProvider.stockNews(symbol, onResult)
            },
            scheduler = KuiklyDetailDataScheduler(),
        ) { effect -> handleDetailDataEffect(effect) }
    }
    private val quote: Quote get() = detailDataState.quote
    private val dataModeLabel: String get() = detailDataState.dataModeLabel
    private val quoteLoading: Boolean get() = detailDataState.quoteLoading
    private val chartDataLoading: Boolean get() = detailDataState.chartDataLoading
    private val insight: StockInsightBundle get() = detailDataState.insight
    private val newsList: List<NewsItem> get() = detailDataState.newsList
    private var chartMode: StockChartMode by observable(StockChartMode.TIMELINE)
    private var chartPeriod: StockChartPeriod by observable(StockChartPeriod.DAY)
    private var watchlisted: Boolean by observable(false)
    private var watchlistHint: String by observable("")
    private var topProgress: Float by observable(0f)
    private var topCompactVisible: Boolean by observable(false)
    private var requestedMarketDataSource = MarketDataSource.REAL
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
    // AI 解读域（Wave 2 第 3 刀，见 docs/39 §9 / docs/43 D3）：唯一 owner 是
    // DetailAiInsightCoordinator（合并主 aiRemote* 与圈选 circleAi* 两套同构
    // 状态机为两个 AiInsightSession，同时收编 aiAwaitingFacts / aiRevealLimit /
    // aiRevealSource）。字段以只读 getter 转发到 detailAiState 的 observable，
    // DSL 闭包内读取仍建立反应式依赖（同 D1 quote 写法）。线程纪律（Provider
    // 回调 → TypewriterSmoother 节拍 + jumpToMain 跳回主线程）由 session 内部
    // 守住，不再依赖页面 setTimeout(0)。
    private val detailAiState = DetailAiState()
    private val detailAiCoordinator by lazy {
        DetailAiInsightCoordinator(
            state = detailAiState,
            host = object : DetailAiHostPort {
                override val pagerId = this@StockDetailPage.pagerId
                override fun quote(): Quote = detailDataState.quote
                override fun insight(): StockInsightBundle = detailDataState.insight
                override fun newsList(): List<NewsItem> = detailDataState.newsList
                override fun timelineSeries(): List<Double> = detailTimelineSeries(detailDataState.quote)
                override fun loadConfig(): AiConfig = aiChatDependencies.configStore.load()
                override fun configValidationError(config: AiConfig): String? = config.validationError()
                override fun createProvider(config: AiConfig): AiProvider = aiChatDependencies.aiProviderFactory(config)
                override fun jumpToMain(block: () -> Unit) { setTimeout(0) { block() } }
            },
            scheduler = KuiklyDetailAiScheduler(),
            reduceMotion = reduceMotion,
        )
    }
    private val aiRemoteState: Int get() = detailAiState.mainState
    private val aiRemoteText: String get() = detailAiState.mainText
    private val aiRemoteError: String get() = detailAiState.mainError
    private val aiRemoteModel: String get() = detailAiState.mainModel
    private val aiAwaitingFacts: Boolean get() = detailAiState.awaitingFacts
    private val aiRevealLimit: Int get() = detailAiState.revealLimit
    private val aiRevealSource: String get() = detailAiState.revealSource
    private val circleAiState: Int get() = detailAiState.circleState
    private val circleAiText: String get() = detailAiState.circleText
    private val circleAiError: String get() = detailAiState.circleError
    private val circleAiModel: String get() = detailAiState.circleModel
    private var expandedAttributionKey: String by observable("")
    private val selectedKLineIndex: Int get() = detailChartState.selectedKLineIndex
    private val chartViewportCommand: ChartViewportCommand get() = detailChartState.chartViewportCommand
    private var livePulseVersion = 0
    private var tickerLiftVersion = 0
    // ④ 声呐气泡横向漂移相位（0..1 循环，R1：draw 闭包内读取驱动 Canvas 重绘）。
    // 50 步 × 60ms ≈ 3s 一个往返周期；reduceMotion 恒 0（原地呼吸不漂移）。
    private var sonarDrift: Float by observable(0f)
    private var sonarDriftVersion = 0
    // ---- doc 26 新增状态：氛围/自绘分时/新闻弹幕 ----
    private val crosshairIndex: Int get() = detailChartState.crosshairIndex
    private var drawProgress: Float by observable(0f)
    private var drawVersion = 0
    // 长按十字线 scrub 期间锁外层 Scroller 滚动（DetailTimelineChart onScrubActive 驱动，
    // WatchlistPage 拖拽排序同款机制）；普通上下滚动不进 scrub、永不锁
    private val chartScrubLock: Boolean get() = detailChartState.chartScrubLock
    // ---- 弹幕 v2（对齐市场页）：页侧持有节拍——setTimeout 链 33ms 步进 offset
    // （≈30dp/s）。点按即停：摘要条展开（newsSummary）或长按先览（tapePreview）
    // 期间暂停步进，收起/松手后恢复；reduceMotion 不流动。交互口径不变。
    private var tapeOffset: Float by observable(0f)
    private var tapeTimerStarted = false
    private var companyInfoTab: Int by observable(0) // 0 = 公司介绍，1 = 公司数据
    // 公司数据切面每次挂载都从隐藏态开始，下一帧翻转为可见，供下方卡片消费入场动画。
    private var companyDataPresented: Boolean by observable(false)
    private val reduceMotion by lazy { platformPrefersReducedMotion() }
    private val theme: StockChatTheme get() = appTheme()

    // ---- doc 29 集成状态：基建 + 13 个交互（U1 全部经 detailOverlayCoordinator 仲裁） ----
    private val chipStore = ContextChipStore()
    // overlay 仲裁域（Wave 2 第 4 刀，见 docs/39 §9 / docs/43 D4）：唯一 owner 是
    // DetailOverlayCoordinator，承接原 OverlayArbiter 的 U1「开新的先关旧的、点
    // 空白全关」仲裁语义并收编新闻摘要 / 披露 peek / 弹幕先览 / 理由 chips 四类
    // 载荷。字段以只读 getter 转发到 detailOverlayState 的 observable，DSL
    // 闭包内读取仍建立反应式依赖。tapePreviewVersion 镜像由 Coordinator 私有
    // 持有。DetailOverlay 枚举仍在 page.detail 包（跨多处 vif 条件引用）。
    private val detailOverlayState = DetailOverlayState()
    private val detailOverlayCoordinator by lazy {
        DetailOverlayCoordinator(
            state = detailOverlayState,
            scheduler = KuiklyDetailOverlayScheduler(),
            reduceMotion = reduceMotion,
        )
    }
    private val newsSummary: NewsItem? get() = detailOverlayState.newsSummary
    private val tapePreview: NewsItem? get() = detailOverlayState.tapePreview
    private val tapePreviewAnchorX: Float get() = detailOverlayState.tapePreviewAnchorX
    private val tapePreviewAnchorY: Float get() = detailOverlayState.tapePreviewAnchorY
    private val disclosurePeek: DisclosureItem? get() = detailOverlayState.disclosurePeek
    private val disclosurePeekVisible: Boolean get() = detailOverlayState.disclosurePeekVisible
    private val reasonChipsVisible: Boolean get() = detailOverlayState.reasonChipsVisible
    private var hintVersion = 0                                                 // watchlistHint toast 计时 revision
    // 图表交互域（Wave 2 第 2 刀，见 docs/39 §9 / docs/43 D2）：唯一 owner 是
    // DetailChartInteractionCoordinator（十字线/圈选/声呐/气泡/视口/预填/旗标/
    // 区间带）。字段以只读 getter 转发到 detailChartState 的 observable，DSL
    // 闭包内读取仍建立反应式依赖（同 D1 quote 模式）。overlay 仲裁、圈选 AI
    // 流、selectedSentence 联动仍在本页处理，见 handleDetailChartEffect。
    private val detailChartState = DetailChartState()
    private val detailChartCoordinator by lazy {
        DetailChartInteractionCoordinator(
            state = detailChartState,
            host = DetailChartHostPort { detailTimelineSeries(detailDataState.quote) },
            scheduler = KuiklyDetailChartScheduler(),
        ) { effect -> handleDetailChartEffect(effect) }
    }
    private val sonarPoints: List<AnomalyPoint> get() = detailChartState.sonarPoints      // ④ 异动声呐
    private val selectedSonarIndex: Int get() = detailChartState.selectedSonarIndex       // ④ 选中声呐点
    private val chartBubble: String get() = detailChartState.chartBubble                  // ④/① 就地气泡文案
    private val chartBubblePresented: Boolean get() = detailChartState.chartBubblePresented // ④/① 气泡两帧入场（R4）
    private val circleSelecting: Boolean get() = detailChartState.circleSelecting         // ① 圈选态 hint
    private val circleHintPresented: Boolean get() = detailChartState.circleHintPresented // ① hint 两帧入场（R4）
    private val prefillQuestion: String get() = detailChartState.prefillQuestion          // ⑤ scrub 停顿预填
    private val chartFlags: List<ChartFlag> get() = detailChartState.chartFlags           // B2 图侧新闻旗标
    private val bandRange: Triple<Int, Int, Boolean>? get() = detailChartState.bandRange  // ②/B2 区间高亮带 (start,end,fromSentence)
    private var selectedSentence: Int by observable(-1)                                   // ② 选中的解读句
    private val aiChatDependencies by lazy { ChatDependencies.forPager(pagerId) }
    // ② 句图联动锚点（真实化，2026-09-08）：不再写死槽位。点句时从句子内容
    // 端侧推导——优先解析句内 HH:MM 映射分时索引（LLM 只负责引用时间，坐标
    // 由 AnchorIndex 计算），无时间词时按当日真实分时（最高/最低/开盘时刻）回退。
    // B1/B2 已落旗新闻（点按收起用）
    private var droppedNewsIds = mutableSetOf<String>()

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        MarketCardRenderers.ensureRegistered()
        symbol = pagerData.params.optString("symbol").ifEmpty { "600519.SH" }
        requestedMarketDataSource = selectedMarketDataSource()
        handoffFadeActive = pagerData.params.optString("krTransition") == "islandExpand"
        handoffPresented = !handoffFadeActive || reduceMotion
        watchlisted = watchlistStore.contains(symbol)
        // Quote（含预取命中/缓存兜底/真实加载/合并规则）、Insight、News 的加载统一
        // 由 DetailDataCoordinator 持有；下游 ticker/声呐/AI 触发经
        // handleDetailDataEffect 承接（见 detail/quote/state）。
        detailDataCoordinator.start(symbol, QuotePrefetchStore.peek(symbol))
        // AI 解读域：占位图等行情/资金事实就绪期间显示骨架；4s 兜底由 Coordinator 内 armAwaitingFactsFallback() 提供。
        detailAiCoordinator.armAwaitingFactsFallback()
        detailAiCoordinator.startReveal(buildInsightSummary())
        startTapeTimer()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        // 从设置页回来且来源发生变化时，不能沿用上一轮结果：先重新进入骨架态，
        // 真实模式会重新探测接口；Mock 模式直接给当前股票的固定交易日兜底数据。
        val selectedSource = selectedMarketDataSource()
        if (selectedSource != requestedMarketDataSource) {
            requestedMarketDataSource = selectedSource
            detailDataCoordinator.reload(symbol)
        }
        entranceVisible = reduceMotion
        if (!reduceMotion) {
            setTimeout(0) { entranceVisible = true }
            // R5 断链兜底：ref→setTimeout 丢链时 RevealBlock 全体（图表/指标板/AI 块）
            // 不得停在 opacity 0（同值写不通知，幂等安全；同 handoffPresented 做法）。
            setTimeout(600) { entranceVisible = true }
        }
        // 交接淡入 R4 两帧翻转：首帧 opacity 0 挂载（对齐全屏玻璃帧），
        // 下一帧翻转为可见触发淡入。500ms 兜底防 ref→setTimeout 链路丢失
        // 导致页面停在透明态（同值写入不通知，幂等安全）。
        if (handoffFadeActive) {
            setTimeout(0) { handoffPresented = true }
            setTimeout(500) { handoffPresented = true }
        }
        startLivePulse()
        startSonarDrift()
        detailAiCoordinator.startReveal(buildInsightSummary())
        detailAiCoordinator.maybeStartAiInsight()
        startDrawOn()
    }

    /** 公司数据切面两帧入场，保证下方事实卡每次切入都有完整淡入。 */
    private fun selectCompanyInfoTab(tab: Int) {
        val target = tab.coerceIn(0, 1)
        if (target == companyInfoTab) return
        if (target == 1) {
            companyDataPresented = reduceMotion
            companyInfoTab = target
            if (!reduceMotion) {
                setTimeout(0) { if (companyInfoTab == 1) companyDataPresented = true }
                setTimeout(600) { if (companyInfoTab == 1) companyDataPresented = true }
            }
        } else companyInfoTab = target
    }

    private fun selectedMarketDataSource(): MarketDataSource = MarketDataSource.fromId(
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .getString(MarketDataPrefs.KEY_SOURCE),
    )

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        livePulseVersion++
        sonarDriftVersion++
        drawVersion++
        // AI 解读域：中断进行中的两路流（generation 失效使残留回调全部 no-op），
        // 已显示文本保留（state=3 有文本 / state=0 无文本，与原语义一致）。
        detailAiCoordinator.onDisappear()
    }

    override fun pageWillDestroy() {
        detailDataCoordinator.onDestroy()
        detailChartCoordinator.onDestroy()
        detailAiCoordinator.onDestroy()
        detailOverlayCoordinator.onDestroy()
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val page = this
        val attribution = CardPayloadParser.parse("attribution", "{\"symbol\":\"${page.quote.symbol}\"}") as AttributionIntent
        val ctx = CardContext(page.theme, CardDensity.FULL, { }, glass = page.hostGlassRenderer)
        val aiSummary = page.buildInsightSummary()
        val wide = page.pagerData.pageViewWidth >= 768f
        // 注意：body() 的 builder 闭包只取首帧快照（AGENTS.md R1），insight/quote 的
        // 读取一律延迟到下方 vbind/attr/event 闭包内（businessCards 见 vbind({insight})，
        // 归因标题与 G1 重算用 lambda 传入）。RevealBlock 阶梯序号按满配 5 张业务卡取常量。
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
                // 氛围底（doc 26 §3）：整屏渐变垫层，必须声明在 Scroller 之前
                // （垫在滚动内容之下）；涨红/跌绿随行情 tick 在 draw 闭包内换色。
                AtmosphereBackdrop(
                    toneSoft = { page.toneSoftColor() },
                    pageColor = { page.theme.page },
                )
                Scroller {
                    attr {
                        flex(1f)
                        // 竖向 Scroller 水平 padding 会被双倍扣除，14/14 时右侧多出 28dp 留白；
                        // 右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                        paddingLeft(14f)
                        paddingRight(0f)
                        // 原型的顶栏是 46dp；内容从顶栏下 12dp 开始，不保留旧版氛围
                        // 头图的额外空白。
                        paddingTop(page.pagerData.statusBarHeight + 69f)
                        paddingBottom(100f)
                        // 十字线长按 scrub 期间锁定滚动（scrub 结束恢复）；R1：attr 闭包内
                        // 读 observable，scrub 进/出时本 attr 重跑
                        scrollEnable(!page.chartScrubLock)
                    }
                    event {
                        scroll { params ->
                            page.updateTopProgress(params.offsetX, params.contentHeight, params.viewHeight)
                        }
                        contentSizeChanged { _, contentHeight ->
                            page.updateTopProgress(0f, contentHeight, page.pagerData.pageViewHeight)
                        }
                    }

                    // ---- Hero 行情（卡外价格行，直接铺在氛围底上）----
                    // 2026-09-09 版式：价格 / 涨跌额 / 涨跌胶囊沿基线对齐（alignItemsFlexEnd），
                    // 胶囊宽度随文本自适应——不再是「大数字旁半悬空的定宽胶囊」。
                    View {
                        attr { marginTop(2f) }
                        View {
                            attr { flexDirectionRow(); alignItemsFlexEnd() }
                            TickerText(
                                text = { page.chartHeaderSnapshot().priceText },
                                previousText = { page.previousPriceText },
                                loading = { page.quoteLoading },
                                fontSize = page.theme.type.display,
                                width = { 142f },
                                color = { page.chartHeaderSnapshot().tone },
                                theme = page.theme,
                                lift = { page.tickerLift },
                                directionUp = { page.tickerDirectionUp },
                                reduceMotion = page.reduceMotion,
                            )
                            // 涨跌胶囊：宽度按文本长度自适应（lambda 传入，attr 闭包内随 quote 刷新，R1）
                            View {
                                attr {
                                    marginLeft(8f)
                                    marginBottom(4f)
                                    paddingTop(3f); paddingBottom(3f); paddingLeft(9f); paddingRight(9f)
                                    backgroundColor(page.toneColor())
                                    borderRadius(page.theme.inputRadius)
                                    alignItemsCenter(); justifyContentCenter()
                                }
                                TickerText(
                                    text = { Format.percent(page.chartHeaderSnapshot().changePercent) },
                                    previousText = { page.previousPercentText },
                                    loading = { page.quoteLoading },
                                    fontSize = page.theme.type.sm,
                                    width = { (Format.percent(page.chartHeaderSnapshot().changePercent).length * 6.6f + 6f).coerceAtLeast(50f) },
                                    color = { page.theme.onBrand },
                                    theme = page.theme,
                                    lift = { page.tickerLift },
                                    directionUp = { page.tickerDirectionUp },
                                    reduceMotion = page.reduceMotion,
                                )
                            }
                            // 涨跌额（元）：胶囊右侧弱一档的同行事实
                            Text {
                                attr {
                                    text(Format.signed(page.chartHeaderSnapshot().change))
                                    marginLeft(8f)
                                    marginBottom(6f)
                                    fontSize(page.theme.type.sm)
                                    fontWeightMedium()
                                    color(page.chartHeaderSnapshot().tone.opacity(0.85f))
                                }
                            }
                        }
                        // 选中十字线/蜡烛时，这里同步相应时点的交易数据；保持为干净的信息行，
                        // 不再用一格一块的玻璃底把 Hero 切碎。
                        vbind({ listOf(page.quote, page.chartMode, page.chartPeriod, page.crosshairIndex, page.selectedKLineIndex) }) {
                            val snapshot = page.chartHeaderSnapshot()
                            snapshot.metrics.chunked(4).forEachIndexed { rowIndex, row ->
                                View {
                                    attr {
                                        marginTop(if (rowIndex == 0) 10f else 7f)
                                        flexDirectionRow()
                                        alignItemsCenter()
                                        touchEnable(false)
                                    }
                                    row.forEachIndexed { columnIndex, metric ->
                                        if (columnIndex > 0) {
                                            View {
                                                attr {
                                                    width(0.5f); height(24f)
                                                    marginRight(7f)
                                                    backgroundColor(page.theme.divider)
                                                }
                                            }
                                        }
                                        View {
                                            attr { flex(1f) }
                                            Text {
                                                attr {
                                                    text(metric.label)
                                                    fontSizeScaled(10f)
                                                    color(page.theme.textTertiary)
                                                }
                                            }
                                            Text {
                                                attr {
                                                    text(metric.value)
                                                    marginTop(2f)
                                                    fontSizeScaled(13f)
                                                    fontWeightSemiBold()
                                                    color(metric.valueColor ?: page.theme.textSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                text(page.chartHeaderSnapshot().caption)
                                marginTop(4f)
                                fontSize(page.theme.type.meta)
                                color(page.theme.textTertiary)
                            }
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
                    }

                    // ---- 新闻弹幕 v2（对齐市场页）：无背板持续流动、屏幕边缘流出。
                    // 交互口径不变：点按 = 落旗 + 展开摘要条（摘要展开即暂停流动，
                    // 收起恢复）；长按 = 先览气泡（先览期间同样暂停）。
                    NewsMarquee(
                        theme = page.theme,
                        items = { page.newsList },
                        // B1 情绪点 + 摘要头「利好/利空」：端侧词典打分，涨红跌绿（U2）
                        sentimentOf = { item -> scoreNewsSentiment(item.title).isPositive },
                        offset = { page.tapeOffset },
                        loopWidth = { page.tapeLoopWidth() },
                        onTapItem = { page.onNewsTapped(it) },
                        selected = { page.newsSummary },
                        // B1 长按先览（U5 400ms）：TAPE_PREVIEW 层，松手 700ms 后消失（5s 兜底）；
                        // pageX/pageY = 触摸点在根 Page 坐标系，气泡锚定所按条目正下方
                        onLongPressItem = { item, pressX, pressY -> page.showTapePreview(item, pressX, pressY) },
                        onLongPressRelease = { page.scheduleTapePreviewDismiss() },
                    )

                    // B2 摘要条（原卡内展开改为弹幕下独立圆角块；交互不变）
                    vif({ page.newsSummary != null }) {
                        vbind({ page.newsSummary?.id ?: "" }) {
                            val news = page.newsSummary
                            if (news != null) {
                                View {
                                    attr { marginTop(8f); borderRadius(12f) }
                                    NewsSummaryBar(
                                        theme = page.theme,
                                        news = news,
                                        sentiment = scoreNewsSentiment(news.title).isPositive,
                                        flagged = page.isNewsFlagged(news),
                                        onToggle = { page.onNewsTapped(news) },
                                        onAskAi = { page.askAboutNews(it) },
                                        onOpenUrl = { target ->
                                            page.detailOverlayCoordinator.closeSummary()
                                            page.openUrl(target.url)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ---- 走势主卡（2026-09-08：去掉白色大框，图表直接浮在氛围底上）----
                    // 入场动效：欢迎引导卡同款上滑淡入（RevealBlock，index 0 起阶梯）
                    RevealBlock(0, { page.entranceVisible }, page.reduceMotion) {
                    View {
                        attr {
                            marginTop(-3f)
                        }
                        // 左侧切换周期，右侧操作视窗；中间保留弹性空白，互不拥挤。
                        View {
                            attr { flexDirectionRow(); alignItemsCenter();  marginBottom(7f) }
                            ChartSegment(page.theme, { page.chartMode }, { page.chartPeriod }, page.reduceMotion) { m, p ->
                                page.chartMode = m
                                page.chartPeriod = p
                                page.detailChartCoordinator.resetChartSelection()
                            }
                            View { attr { flex(1f) } }
                            ChartViewportControls(page.theme) { action -> page.detailChartCoordinator.issueViewportCommand(action) }
                        }
                        // 图例固定在工具栏下一行，不再漂浮压住高低点和价格曲线。
                        View {
                            attr { alignSelfFlexEnd(); marginBottom(6f); touchEnable(false) }
                            ChartLegend(page.theme, { page.toneColor() })
                        }
                        // 图表区加载骨架（chartDataLoading=分时尚未到位时的空白期治理，
                        // 2026-09-10）：波形占位随 livePulse 呼吸，替代 396dp 空白图表。
                        vif({ page.chartDataLoading }) {
                            ChartLoadingSkeleton(page.theme, { page.livePulse }, page.reduceMotion)
                        }
                        // 分时主体（自绘）与 K 线互斥切换；分段控件缩小后悬浮图左上（见下方 overlay）
                            vif({ page.chartMode == StockChartMode.TIMELINE && !page.chartDataLoading }) {
                                DetailTimelineChart(
                                    theme = page.theme,
                                    quote = { page.quote },
                                    crosshairIndex = { page.crosshairIndex },
                                    drawProgress = { page.drawProgress },
                                    motionPhase = { page.sonarDrift },
                                    reduceMotion = page.reduceMotion,
                                    // 图表不再被卡片内边距二次挤压：横向直接吃满详情页
                                    // 可用宽度，十字线信息也在绘图区内浮现。
                                    containerWidth = page.pagerData.pageViewWidth - 28f,
                                    // ⑤ 恢复 scrub 即清空预填（只预填不发送）
                                    onScrub = { page.detailChartCoordinator.onScrub(it) },
                                    // ④ 异动声呐
                                    sonarIndices = { page.sonarPoints.map { p -> p.index } },
                                    selectedSonarIndex = { page.selectedSonarIndex },
                                    onSonarTap = { page.detailChartCoordinator.tapSonar(it) },
                                    // B2 新闻旗标（图侧）
                                    flags = { page.chartFlags },
                                    // ② 句图联动 / B2 区间高亮带（同容器，后者覆盖前者）
                                    band = { page.bandRange },
                                    // ① 圈选即问
                                    onCircleSelect = { s, e -> page.detailChartCoordinator.onCircleSelected(s, e) },
                                    onSelectStateChange = { selecting ->
                                        page.detailChartCoordinator.setCircleSelecting(selecting)
                                    },
                                    // ⑤ 十字线停顿 600ms 预填
                                    onScrubPause = { page.detailChartCoordinator.onScrubPause(it) },
                                    // ⑤ 松手离开 scrub：2s 后清预填（chart 侧 revision 去抖）
                                    onScrubLeave = { page.detailChartCoordinator.clearPrefill() },
                                    // 长按进/出 scrub：锁/解锁外层 Scroller 滚动
                                    onScrubActive = { page.detailChartCoordinator.setInteractionActive(it) },
                                    // U1 点空白（非声呐轻点）：关闭图表气泡并清其区间带
                                    onBlankTap = { page.closeChartBubble() },
                                    viewportCommand = { page.chartViewportCommand },
                                )
                            }
                            vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.DAY && !page.chartDataLoading }) {
                                KLineChart(
                                    container = this,
                                    model = StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.DAY),
                                    context = ctx,
                                    selectedIndex = { page.selectedKLineIndex },
                                    onSelectIndex = { page.detailChartCoordinator.selectKLineIndex(it) },
                                    chartHeight = 396f,
                                    // 捏合缩放期间锁外层 Scroller（两指会被原生滚动接管），
                                    // 与分时图 scrub 锁共用同一开关
                                    onZoomActive = { page.detailChartCoordinator.setInteractionActive(it) },
                                    onCrosshairActive = { page.detailChartCoordinator.setInteractionActive(it) },
                                    viewportCommand = { page.chartViewportCommand },
                                )
                            }
                            vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.WEEK && !page.chartDataLoading }) {
                                KLineChart(
                                    container = this,
                                    model = StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.WEEK),
                                    context = ctx,
                                    selectedIndex = { page.selectedKLineIndex },
                                    onSelectIndex = { page.detailChartCoordinator.selectKLineIndex(it) },
                                    chartHeight = 396f,
                                    // 捏合缩放期间锁外层 Scroller（两指会被原生滚动接管），
                                    // 与分时图 scrub 锁共用同一开关
                                    onZoomActive = { page.detailChartCoordinator.setInteractionActive(it) },
                                    onCrosshairActive = { page.detailChartCoordinator.setInteractionActive(it) },
                                    viewportCommand = { page.chartViewportCommand },
                                )
                            }
                            vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.MONTH && !page.chartDataLoading }) {
                                KLineChart(
                                    container = this,
                                    model = StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.MONTH),
                                    context = ctx,
                                    selectedIndex = { page.selectedKLineIndex },
                                    onSelectIndex = { page.detailChartCoordinator.selectKLineIndex(it) },
                                    chartHeight = 396f,
                                    // 捏合缩放期间锁外层 Scroller（两指会被原生滚动接管），
                                    // 与分时图 scrub 锁共用同一开关
                                    onZoomActive = { page.detailChartCoordinator.setInteractionActive(it) },
                                    onCrosshairActive = { page.detailChartCoordinator.setInteractionActive(it) },
                                    viewportCommand = { page.chartViewportCommand },
                                )
                            }
                        // ① 圈选态 hint（vif + 两帧入场，R4）：进入圈选时出现、松手消失
                        vif({ page.circleSelecting && page.circleHintPresented }) {
                            Text {
                                attr {
                                    absolutePosition(left = 16f, top = 46f)
                                    text("圈选中：松手生成这段走势的解读")
                                    fontSizeScaled(10f)
                                    fontWeightMedium()
                                    color(page.theme.brand)
                                    opacity(if (page.circleHintPresented) 1f else 0f)
                                    if (!page.reduceMotion) {
                                        animate(Animation.easeOut(0.18f), page.circleHintPresented)
                                    }
                                    touchEnable(false)
                                }
                            }
                        }
                        // ④/① 就地气泡（doc 29 U1/U2/U3）：唯一就地回应容器——白底 brand
                        // 描边圆角 14，「AI · 端侧规则」来源标注，R4 两帧上浮淡入
                        vif({ page.detailOverlayCoordinator.active() == DetailOverlay.CHART_BUBBLE && page.chartBubble.isNotEmpty() }) {
                            View {
                                attr {
                                    absolutePosition(left = 16f, right = 16f, bottom = 12f)
                                    padding(12f)
                                    borderRadius(14f)
                                    backgroundColor(page.theme.surface)
                                    border(Border(1.2f, BorderStyle.SOLID, page.theme.brand.opacity(0.6f)))
                                    boxShadow(BoxShadow(0f, 6f, 18f, page.theme.brand.opacity(0.14f)))
                                    opacity(if (page.chartBubblePresented) 1f else 0f)
                                    if (!page.reduceMotion) {
                                        transform(Translate(0f, if (page.chartBubblePresented) 0f else 0.08f))
                                        animate(Animation.easeOut(0.22f), page.chartBubblePresented)
                                    }
                                }
                                View {
                                    attr { flexDirectionRow(); alignItemsCenter() }
                                    Text {
                                        attr {
                                            text(page.chartBubbleSourceLabel())
                                            fontSizeScaled(9f)
                                            fontWeightSemiBold()
                                            color(page.theme.brand)
                                            flex(1f)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text("×")
                                            fontSizeScaled(12f)
                                            color(page.theme.textTertiary)
                                        }
                                    }
                                    event { click { page.closeChartBubble() } }
                                }
                                Text {
                                    attr {
                                        text(page.chartBubble)
                                        marginTop(6f)
                                        fontSizeScaled(11.5f)
                                        lineHeightScaled(17f)
                                        color(page.theme.textPrimary)
                                    }
                                }
                                // ① 圈选 AI 解读：流式段落（打字机逐字），对照图就地阅读
                                vif({ page.circleAiState == 1 && page.circleAiText.isBlank() }) {
                                    Text {
                                        attr {
                                            text("正在生成区间解读…")
                                            marginTop(4f)
                                            fontSizeScaled(11f)
                                            color(page.theme.textTertiary)
                                        }
                                    }
                                }
                                vif({ page.circleAiText.isNotBlank() }) {
                                    Text {
                                        attr {
                                            text(page.circleAiText)
                                            marginTop(4f)
                                            fontSizeScaled(11.5f)
                                            lineHeightScaled(17f)
                                            color(page.theme.textPrimary)
                                        }
                                    }
                                }
                                vif({ page.circleAiState == 4 }) {
                                    Text {
                                        attr {
                                            text("AI 调用失败：${page.circleAiError} · 以上为端侧统计")
                                            marginTop(4f)
                                            fontSizeScaled(10f)
                                            lineHeightScaled(14f)
                                            color(page.theme.textTertiary)
                                        }
                                    }
                                }
                                View {
                                    attr {
                                        marginTop(8f)
                                        alignSelfFlexStart()
                                        height(28f)
                                        paddingLeft(12f)
                                        paddingRight(12f)
                                        allCenter()
                                        borderRadius(14f)
                                        backgroundColor(page.theme.brandSoft)
                                    }
                                    Text {
                                        attr {
                                            text("去对话深聊 ›")
                                            fontSizeScaled(11f)
                                            fontWeightSemiBold()
                                            color(page.theme.brand)
                                        }
                                    }
                                    event {
                                        click {
                                            page.openChatWithQuestion(
                                                page.chipStore.promptFragment() +
                                                    "「${page.chartBubble}」帮我从资金面和消息面深聊${page.quote.name}这段走势。",
                                                focusSymbol = page.symbol,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }

                    // ---- 次级指标：与顶部重复的换手率不再单独做胶囊；保留资金、估值与规模。 ----
                    RevealBlock(1, { page.entranceVisible }, page.reduceMotion) {
                    View {
                        attr {
                            marginTop(page.theme.spacing.md)
                            paddingTop(8f); paddingBottom(8f)
                            borderTop(Border(0.5f, BorderStyle.SOLID, page.theme.divider))
                            borderBottom(Border(0.5f, BorderStyle.SOLID, page.theme.divider))
                        }
                        // 主力资金取自 insight.fundFlow → 外层 vbind(insight)：insight 加载后整行重建（R1）。
                        // 涨红/跌绿遵循 A 股配色约定。
                        vbind({ page.insight }) {
                            val main = page.insight.fundFlow?.main
                            vbind({ page.quote }) {
                                SecondaryMetricRow(
                                    listOf(
                                        DetailMetric(
                                            "主力资金",
                                            if (main == null) "--" else Format.compactAmount(main),
                                            valueColor = when {
                                                main == null -> null
                                                main > 0 -> page.theme.rise
                                                else -> page.theme.fall
                                            },
                                        ),
                                        DetailMetric("成交额", Format.compactAmount(page.quote.amount)),
                                        DetailMetric("PE(TTM)", page.quote.peTtm.takeIf { it > 0.0 }?.let { Format.decimal(it, 1) } ?: "--"),
                                        DetailMetric("总市值", Format.compactAmount(page.quote.marketCap)),
                                    ),
                                    page.theme,
                                    onGrabCell = { page.grabMetric(it.label, it.value) },
                                )
                            }
                        }
                    }
                    }

                    // ---- AI 一行归因：全页唯一常驻 AI 触点（端侧模板，纯事实） ----
                    RevealBlock(2, { page.entranceVisible }, page.reduceMotion) {
                    vbind({ page.quote }) {
                        View {
                            attr {
                                marginTop(page.theme.spacing.md)
                                paddingLeft(10f)
                                paddingTop(7f); paddingBottom(7f)
                                backgroundColor(page.theme.brandSoft)
                                borderRadius(8f)
                            }
                            View {
                                attr {
                                    absolutePosition(left = 0f, top = 7f, bottom = 7f)
                                    width(3f)
                                    borderRadius(1.5f)
                                    backgroundColor(page.theme.brand)
                                }
                            }
                            Text {
                                attr {
                                    text(page.buildOneLineAttribution())
                                    fontSizeScaled(11.5f)
                                    lineHeightScaled(17f)
                                    color(page.theme.textSecondary)
                                }
                            }
                        }
                    }
                    }

                    // ---- AI 解读（原型叙事位：紧贴一行归因，先给解读再看业务数据）----
                    // 真实化（2026-09-08）：默认走真实 LLM 流式（与聊天页同一套
                    // API 配置与通路），未配置/失败回退端侧模板并如实标注来源。
                    // ② 句图联动：点句子 → 端侧按句内时间在走势图点亮真实区间
                    RevealBlock(3, { page.entranceVisible }, page.reduceMotion) {
                        AiInsightBlock(
                            state = { page.aiRemoteState },
                            remoteText = { page.aiRemoteText },
                            remoteError = { page.aiRemoteError },
                            remoteModel = { page.aiRemoteModel },
                            localSummary = { if (page.aiRevealSource.isEmpty()) aiSummary else page.aiRevealSource },
                            revealLimit = { page.aiRevealLimit },
                            actionLabel = { page.aiActionLabel() },
                            remoteSentences = { page.currentInsightSentences() },
                            preparing = { page.aiAwaitingFacts },
                            breath = { page.livePulse },
                            reduceMotion = page.reduceMotion,
                            theme = page.theme,
                            onAction = { page.toggleAiInsight() },
                            selectedSentence = { page.selectedSentence },
                            onPickSentence = { page.pickSentence(it) },
                        )
                    }

                    // 公司介绍 / 公司数据共享同一信息区；公司数据面承接下方事实卡。
                    RevealBlock(4, { page.entranceVisible }, page.reduceMotion) {
                        CompanyIndustryPanel(
                            selectedTab = { page.companyInfoTab },
                            profile = { DetailCompanyProfileCatalog.forSymbol(page.symbol) },
                            quote = { page.quote },
                            theme = page.theme,
                            reduceMotion = page.reduceMotion,
                            tabTrackWidth = (page.pagerData.pageViewWidth - 38f).coerceAtLeast(120f),
                            onSelectTab = { page.selectCompanyInfoTab(it) },
                        )
                    }

                    // ---- 公司数据：只在右侧「公司数据」切面挂载。 ----
                    vif({ page.companyInfoTab == 1 }) {
                    vbind({ page.insight }) {
                        val fundamentals = page.insight.fundamentals
                        // doc 29 E1 今日相关置顶：billboard 数据非空 → 该卡 pinnedToday（至多一张置顶）
                        val relevanceAnchors = listOf(
                            RelevanceAnchor("fund-flow", false, ""),
                            RelevanceAnchor("financial", false, ""),
                            RelevanceAnchor("shareholders", false, ""),
                            RelevanceAnchor("billboard", fundamentals?.billboard != null, "今日上龙虎榜"),
                            RelevanceAnchor("actions", false, ""),
                        )
                        val pinnedId = pickPinnedCard(relevanceAnchors, relevanceAnchors.map { it.cardId })
                        // doc 29 E2 注脚：仅用真实可得输入——股东户数的户均变化由户数环比在
                        // 「总股本不变」假设下推导（-h/(100+h)），不伪造行业分位等缺失数据，
                        // 无输入的注脚一律不显示。
                        val shareholderNote = fundamentals?.shareholder?.let { sh ->
                            val denom = 100.0 + sh.changePercent
                            val perHolder = if (denom != 0.0) -sh.changePercent / denom * 100.0 else 0.0
                            shareholderFootnote(sh.changePercent, perHolder)
                        }
                        val businessCards = listOfNotNull(
                            page.insight.fundFlow?.let { BusinessInsightItem("fund-flow", "资金流", FundFlowCardModel(it, "fund-flow:${page.symbol}")) },
                            fundamentals?.financial?.let { BusinessInsightItem("financial", "财务", FinancialCardModel(it, "financial:${page.symbol}")) },
                            fundamentals?.shareholder?.let {
                                BusinessInsightItem("shareholders", "股东户数", ShareholderCardModel(it, "shareholders:${page.symbol}"), footnote = shareholderNote)
                            },
                            fundamentals?.billboard?.let {
                                BusinessInsightItem("billboard", "龙虎榜", BillboardCardModel(it, "billboard:${page.symbol}"), pinned = "billboard" == pinnedId)
                            },
                            fundamentals?.actions?.takeIf { it.isNotEmpty() }?.let {
                                BusinessInsightItem("actions", "分红与解禁", CorporateActionCardModel(it, "actions:${page.symbol}"))
                            },
                            // E1：置顶卡移到第一位（无事件日原序）
                        ).let { cards -> if (pinnedId == null) cards else cards.sortedByDescending { it.pinned } }
                        SectionLabel("公司数据", page.theme, strong = true)
                        BusinessInsightGrid(
                            items = businessCards,
                            context = ctx,
                            theme = page.theme,
                            wide = wide,
                            baseIndex = 0,
                            entranceVisible = { page.companyDataPresented },
                            reduceMotion = page.reduceMotion,
                            // E2 注脚点击 → 展示判定依据（U3 两步溯源）
                            onFootnoteClick = { note -> page.toastHint("${note.rationale} · 端侧规则") },
                        )
                    }
                    }

                    RevealBlock(9, { page.entranceVisible }, page.reduceMotion) {
                        SectionLabel("公告与研报", page.theme, strong = true)
                        // F1+F3 同卡（原型 .ann-card）：公告要点与研报评级光谱是同一张卡
                        // 的上下两段，对外是一个视图，中间不再隔两张卡的白边。
                        View {
                            attr {
                                marginTop(8f)
                                padding(12f)
                                borderRadius(14f)
                                backgroundColor(page.theme.surface)
                                border(Border(0.5f, BorderStyle.SOLID, page.theme.divider))
                            }
                            // vbind({insight})：公告列表随 insight 加载重建（R1：builder 闭包不追踪 observable）
                            vbind({ page.insight }) {
                                // F1 公告要点 · 端侧评级（前 3 条，标题+徽章+日期，点击条目看判定依据）
                                DisclosureMaterialityBlock(
                                    items = page.insight.disclosures.take(3),
                                    theme = page.theme,
                                    inset = true,
                                    // 2026-09-09 修复：原为具名 onPeek + 尾随 lambda 混用（编译错误），
                                    // 改为全具名；尾随 lambda 原本意图即 onExplain
                                    onExplain = { title -> page.toastHint(materialityOf(title).rule) },
                                    onPeek = { item -> page.detailOverlayCoordinator.showDisclosurePeek(item) },
                                )
                            }

                            // ---- F3 多空观点光谱：并入公告与研报同一张卡（原型 .balance 在 ann-card 内） ----
                            // vbind({insight})：真实评级光谱（东财研报库近 90 天 emRatingName 聚合）
                            // 随 insight 加载重建（R1：builder 闭包不追踪 observable）；在线缺失时
                            // 回落演示段（明确标注 示例 · 演示数据），光谱不空转。
                            vbind({ page.insight }) {
                                BalanceSpectrumBlock(
                                    theme = page.theme,
                                    segments = balanceSegmentsFor(page.insight.ratingSpectrum, page.theme),
                                    initialIndex = 0,
                                    containerWidth = page.pagerData.pageViewWidth - 28f,
                                    reduceMotion = page.reduceMotion,
                                    inset = true,
                                )
                            }
                        }
                    }

                    // ---- 涨跌归因 × AI 走势推演：同一工作台内切换手动重放 / AI 推测过程。 ----
                    RevealBlock(10, { page.entranceVisible }, page.reduceMotion) {
                        SectionLabel("涨跌归因与走势推演", page.theme, strong = true)
                        AttributionForecastWorkbench(
                            theme = page.theme,
                            factors = listOf(
                                FactorSpec("资金面", -0.30),
                                FactorSpec("板块联动", -0.14),
                                FactorSpec("市场整体", 0.05),
                                FactorSpec("个股事件", -0.23),
                            ),
                            actualPct = { page.quote.changePercent },
                            quote = { page.quote },
                            mainFlow = { page.insight.fundFlow?.main },
                            containerWidth = page.pagerData.pageViewWidth - 28f,
                            reduceMotion = page.reduceMotion,
                        )
                    }

                }

                // title/subtitle 是 AppTopBar 的普通构建参数，必须由 vbind 在
                // 行情快照到达后重建；否则会永久停在页面初始占位的“贵州茅台”。
                vbind({ page.quote.name to page.quote.symbol }) {
                    AppTopBar(
                        title = page.quote.name,
                        subtitle = page.quote.symbol,
                        statusBarHeight = page.pagerData.statusBarHeight,
                        theme = page.theme,
                        renderer = page.hostGlassRenderer,
                        backLabel = "‹",
                        onBack = { page.closePage() },
                        compactLine = { "${Format.price(page.quote.price)}  ${Format.percent(page.quote.changePercent)}" },
                        compactLineColor = { page.toneColor() },
                        compactVisible = { page.topCompactVisible },
                        progress = { page.topProgress },
                        reduceMotion = page.reduceMotion,
                        actions = listOf(
                            // 书签替代 Unicode ☆/★，选中时以品牌色实心状态确认已自选。
                            AppTopBarAction(
                                icon = { color, size, selected -> LineIconBookmark(color, size, selected) },
                                onClick = { page.toggleWatchlist() },
                                selected = { page.watchlisted },
                            ),
                            AppTopBarAction(
                                icon = { color, size, _ -> LineIconDots(color, size) },
                                onClick = { page.detailOverlayCoordinator.request(DetailOverlay.MORE_MENU) },
                            ),
                        ),
                    )
                }
                // U1 点空白全关：REASON_CHIPS 层的透明遮罩（开新层自动被仲裁器切换）
                vif({ page.detailOverlayCoordinator.active() == DetailOverlay.REASON_CHIPS }) {
                    View {
                        attr { absolutePositionAllZero(); touchEnable(true) }
                        event { click { page.detailOverlayCoordinator.close() } }
                    }
                }
                // H1 快捷理由 chips：底栏上方浮出（U1 仲裁层）
                vif({ page.detailOverlayCoordinator.active() == DetailOverlay.REASON_CHIPS }) {
                    View {
                        attr {
                            absolutePosition(
                                left = 14f,
                                right = 14f,
                                bottom = 76f + page.pagerData.safeAreaInsets.bottom,
                            )
                        }
                        QuickReasonChips(
                            theme = page.theme,
                            reasons = listOf("等回调到位", "财报前布局", "跟热点板块"),
                            visible = { true },
                            onPick = { page.pickQuickReason(it) },
                            reduceMotion = page.reduceMotion,
                        )
                    }
                }
                // U1 点空白全关：MORE_MENU 层的透明遮罩（开新层自动被仲裁器切换）
                vif({ page.detailOverlayCoordinator.active() == DetailOverlay.MORE_MENU }) {
                    View {
                        attr { absolutePositionAllZero(); touchEnable(true) }
                        event { click { page.detailOverlayCoordinator.close() } }
                    }
                }
                // ⋯ 更多操作菜单（2026-09-10）：锚在顶栏 ⋯（右缘 12f + 57f 高的顶栏）
                // 下方的小卡片，U1 仲裁层互斥。条目改为公共 FeatureTile 磁贴横排
                // （与抽屉/输入栏媒体弹层同一视觉语言），只挂真实可用的动作。
                vif({ page.detailOverlayCoordinator.active() == DetailOverlay.MORE_MENU }) {
                    View {
                        attr {
                            absolutePosition(
                                top = page.pagerData.statusBarHeight + 62f,
                                // left 自算（右缘 12f 对齐 ⋯ 按钮触控区），不依赖 right 单边锚定
                                left = (page.pagerData.pageViewWidth - 250f - 12f).coerceAtLeast(12f),
                            )
                            width(250f)
                            paddingTop(12f)
                            paddingBottom(12f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            borderRadius(16f)
                            backgroundColor(page.theme.surface)
                            border(Border(0.5f, BorderStyle.SOLID, page.theme.divider))
                            boxShadow(BoxShadow(0f, 10f, 26f, page.theme.textPrimary.opacity(0.20f)))
                            touchEnable(true)
                        }
                        View {
                            attr { flexDirectionRow() }
                            FeatureTile(
                                label = "记当初理由",
                                theme = page.theme,
                                height = 66f,
                                icon = { LineIconPin(page.theme.textPrimary, 22f) },
                            ) {
                                // 仲裁器语义：请求 REASON_CHIPS 即自动关闭 MORE_MENU
                                page.detailOverlayCoordinator.request(DetailOverlay.REASON_CHIPS)
                            }
                            View { attr { width(8f) } }
                            FeatureTile(
                                label = "AI 解读",
                                theme = page.theme,
                                height = 66f,
                                icon = { LineIconBarChart(page.theme.textPrimary, 22f) },
                            ) { page.toggleAiInsight() }
                            View { attr { width(8f) } }
                            FeatureTile(
                                label = "复制代码",
                                theme = page.theme,
                                height = 66f,
                                icon = { LineIconCopy(page.theme.textPrimary, 22f) },
                            ) { page.copySymbolToPasteboard() }
                        }
                    }
                }
                // B1 长按先览小气泡（doc 29 原型 .preview：fixed 浮层、left=胶囊左缘钳右、
                // top=胶囊底+8——「像聊天气泡一样从所按消息上引出来」）。锚点取长按事件
                // 的 pageX/pageY（触摸点在根 Page 坐标系，Kuikly LongPressParams 原生提供，
                // 无需估算胶囊布局位置）；finger 在胶囊上（高 28），+20 ≈ 胶囊底+8。
                // 松手 700ms 消失由页侧计时调度；原型同为 position:fixed，显示期间不随页面滚动。
                vif({ page.detailOverlayCoordinator.active() == DetailOverlay.TAPE_PREVIEW && page.tapePreview != null }) {
                    vbind({ page.tapePreview?.id ?: "" }) {
                        val preview = page.tapePreview
                        if (preview != null) {
                            val sentiment = scoreNewsSentiment(preview.title).isPositive
                            val left = (page.tapePreviewAnchorX - 20f)
                                .coerceIn(12f, (page.pagerData.pageViewWidth - 248f).coerceAtLeast(12f))
                            View {
                                attr {
                                    absolutePosition(left = left, top = page.tapePreviewAnchorY + 20f)
                                    width(236f)
                                    padding(10f)
                                    borderRadius(12f)
                                    backgroundColor(page.theme.surface)
                                    border(Border(1f, BorderStyle.SOLID, page.theme.brand.opacity(0.5f)))
                                    boxShadow(BoxShadow(0f, 8f, 22f, page.theme.textPrimary.opacity(0.16f)))
                                    touchEnable(true)
                                }
                                Text {
                                    attr {
                                        text("${formatTapeTime(preview.time)} · ${if (sentiment == true) "利好" else if (sentiment == false) "利空" else "中性"}")
                                        fontSizeScaled(10f)
                                        fontWeightSemiBold()
                                        color(
                                            when (sentiment) {
                                                true -> page.theme.rise
                                                false -> page.theme.fall
                                                null -> page.theme.textTertiary
                                            }
                                        )
                                    }
                                }
                                Text {
                                    attr {
                                        text(truncateByWidth(preview.title, 42f))
                                        marginTop(4f)
                                        fontSizeScaled(10f)
                                        lineHeightScaled(14f)
                                        color(page.theme.textSecondary)
                                    }
                                }
                                Text {
                                    attr {
                                        text("点按展开与落旗 · 端侧规则")
                                        marginTop(5f)
                                        fontSizeScaled(9.5f)
                                        color(page.theme.brand)
                                    }
                                }
                                event { click { page.onNewsTapped(preview) } }
                            }
                        }
                    }
                }
                // F1 公告/研报长按预览（2026-09-09）：蒙层 + 底部浮卡，MarketPage peek 同构。
                vif({ page.disclosurePeek != null }) {
                    View {
                        attr {
                            absolutePositionAllZero()
                            zIndex(20, useOutline = false)
                            backgroundColor(page.theme.textPrimary.opacity(0.26f))
                            val shown = page.disclosurePeekVisible
                            opacity(if (shown) 1f else 0f)
                            touchEnable(shown)
                            if (!page.reduceMotion) animate(Animation.easeOut(0.20f), page.disclosurePeekVisible)
                        }
                        event { click { page.detailOverlayCoordinator.dismissDisclosurePeek() } }
                    }
                    View {
                        attr {
                            absolutePosition(
                                left = 14f,
                                right = 14f,
                                bottom = 76f + page.pagerData.safeAreaInsets.bottom,
                            )
                            zIndex(21, useOutline = false)
                            padding(16f)
                            borderRadius(18f)
                            backgroundColor(page.theme.surface)
                            border(Border(1f, BorderStyle.SOLID, page.theme.divider))
                            boxShadow(BoxShadow(0f, 14f, 30f, page.theme.textPrimary.opacity(0.18f)))
                            val shown = page.disclosurePeekVisible
                            opacity(if (shown) 1f else 0f)
                            touchEnable(shown)
                            if (!page.reduceMotion) {
                                transform(scale = Scale(if (shown) 1f else 0.96f, if (shown) 1f else 0.96f))
                                animate(Animation.easeOut(0.20f), page.disclosurePeekVisible)
                            }
                        }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter() }
                            Text {
                                attr {
                                    text(page.disclosurePeek?.title ?: "")
                                    flex(1f)
                                    fontSizeScaled(13f)
                                    fontWeightBold()
                                    color(page.theme.textPrimary)
                                    lineHeightScaled(18f)
                                }
                            }
                            Text {
                                attr { text("关闭"); fontSizeScaled(11f); color(page.theme.brand) }
                                event { click { page.detailOverlayCoordinator.dismissDisclosurePeek() } }
                            }
                        }
                        Text {
                            attr {
                                text(
                                    // 研报先给可读摘要；券商与日期降到来源行，避免元数据
                                    // 抢占长按预览的首屏内容。
                                    (page.disclosurePeek?.takeIf { it.kind != DisclosureKind.RESEARCH }
                                        ?.let { "${it.kind.label} · ${it.publisher} · ${it.date}" } ?: "")
                                )
                                marginTop(if (page.disclosurePeek?.kind == DisclosureKind.RESEARCH) 0f else 8f)
                                fontSizeScaled(10f)
                                color(page.theme.textTertiary)
                            }
                        }
                        // 重要度规则只适用于公告；研报展示机构标题提炼，不混入无关的公告评级。
                        vif({ page.disclosurePeek?.kind != DisclosureKind.RESEARCH }) {
                            Text {
                                attr {
                                    text(
                                        page.disclosurePeek?.let { item ->
                                            val verdict = when (materialityOf(item.title).level) {
                                                Materiality.HIGH -> "高重要度"
                                                Materiality.MID -> "中重要度"
                                                Materiality.LOW -> "低重要度"
                                            }
                                            "端侧评级：$verdict · ${materialityOf(item.title).rule}"
                                        } ?: ""
                                    )
                                    marginTop(8f)
                                    fontSizeScaled(11f)
                                    lineHeightScaled(16f)
                                    color(page.theme.textSecondary)
                                }
                            }
                        }
                        Text {
                            attr {
                                text(page.disclosurePeek?.summary?.takeIf { it.isNotBlank() } ?: "")
                                marginTop(if (page.disclosurePeek?.kind == DisclosureKind.RESEARCH) 8f else 6f)
                                fontSizeScaled(if (page.disclosurePeek?.kind == DisclosureKind.RESEARCH) 12f else 11f)
                                lineHeightScaled(if (page.disclosurePeek?.kind == DisclosureKind.RESEARCH) 18f else 16f)
                                color(page.theme.textSecondary)
                            }
                        }
                        Text {
                            attr {
                                text(page.disclosurePeek?.let { item ->
                                    if (item.kind == DisclosureKind.RESEARCH) {
                                        "来源：${item.publisher} · ${item.date} · 机构观点仅供参考"
                                    } else {
                                        item.stamp.source.takeIf { it.isNotBlank() }
                                            ?.let { "来源：$it · 只述事实，不构成建议" }
                                            ?: "只述事实，不构成建议"
                                    }
                                } ?: "只述事实，不构成建议")
                                marginTop(8f)
                                fontSizeScaled(9f)
                                color(page.theme.textTertiary)
                            }
                        }
                        vif({ page.disclosurePeek?.url?.isNotBlank() == true }) {
                            View {
                                attr {
                                    alignSelfFlexStart()
                                    marginTop(12f)
                                    paddingTop(7f); paddingBottom(7f)
                                    paddingLeft(10f); paddingRight(10f)
                                    borderRadius(9f)
                                    backgroundColor(page.theme.brandSoft)
                                }
                                Text {
                                    attr {
                                        text("查看公告原文  ↗")
                                        fontSizeScaled(11f)
                                        fontWeightSemiBold()
                                        color(page.theme.brand)
                                    }
                                }
                                event {
                                    click {
                                        val url = page.disclosurePeek?.url.orEmpty()
                                        if (url.isNotBlank()) page.openUrl(url)
                                    }
                                }
                            }
                        }
                    }
                }
                DetailBottomBar(
                    theme = page.theme,
                    renderer = page.hostGlassRenderer,
                    bottomInset = page.pagerData.safeAreaInsets.bottom,
                    watchlisted = { page.watchlisted },
                    feedback = { page.watchlistFeedback },
                    reduceMotion = page.reduceMotion,
                    onToggleWatchlist = { page.toggleWatchlist() },
                    onBackToChat = { page.closePage() },
                    // ③「问 AI」：问题 = 抓取上下文片段 +（停顿预填 或 默认解读问句），
                    // 并把当前标的作为结构化焦点传入（问题文本可能被用户改写）。
                    onAskAi = {
                        val base = page.prefillQuestion.ifEmpty { page.askAiQuestion() }
                        page.openChatWithQuestion(page.chipStore.promptFragment() + base, focusSymbol = page.symbol)
                    },
                    chips = { page.chipStore.chips },
                    onRemoveChip = { page.chipStore.remove(it.key) },
                    prefill = { page.prefillQuestion },
                )
            }
        }
    }

    /** watchlistHint 统一 toast 入口：2.8s 自动清除；新提示使旧清除计时失效。 */
    private fun toastHint(message: String) {
        watchlistHint = message
        val version = ++hintVersion
        setTimeout(2800) {
            if (version == hintVersion) watchlistHint = ""
        }
    }

    /** ⋯ 菜单「复制代码」：走 Bridge 剪贴板通道（与聊天页分享文案同源）。 */
    private fun copySymbolToPasteboard() {
        detailOverlayCoordinator.close()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).copyToPasteboard(symbol)
        toastHint("代码 $symbol 已复制")
    }

    // ───────────── F1 公告/研报长按预览（MarketPage peek 同构，已迁入 D4 Coordinator）─────────────

    private fun toggleWatchlist() {
        if (watchlisted) {
            watchlistStore.remove(symbol)
            watchlisted = false
            toastHint("已从自选移除")
            playWatchlistFeedback()
            return
        }
        when (watchlistStore.add(symbol, quote.name)) {
            WatchlistAddResult.ADDED -> {
                watchlisted = true
                toastHint("已加入自选")
                // FR-W2：详情页入口的来源即理由，可在自选长按改写
                watchlistStore.setReason(symbol, "详情页添加")
                // doc 29 A1：记录加自选当时价（回访卡 KPI）
                watchlistStore.setEntryPrice(symbol, quote.price)
                playWatchlistFeedback()
            }
            WatchlistAddResult.ALREADY_IN -> {
                watchlisted = true
                toastHint("已在自选中")
                playWatchlistFeedback()
            }
            WatchlistAddResult.FULL -> toastHint("自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧")
        }
    }

    // ───────────── doc 29 集成：交互回调与数据派生 ─────────────
    // 图表交互状态机（tapSonar/showChartBubble/onCircleSelected/makePrefill/
    // setChartInteractionActive/issueChartViewportCommand）已迁入
    // DetailChartInteractionCoordinator（detail/chart/state）；本页只保留跨域
    // 编排：overlay 仲裁、圈选 AI 流、selectedSentence 联动、toast。
    // AI 流状态机（requestAiInsight/requestCircleAi/startAiReveal/toggleAiInsight/
    // buildAiInsightPrompt/buildCircleAiPrompt）已迁入 DetailAiInsightCoordinator
    // （detail/ai/state）；本页只保留 buildInsightSummary（端侧兜底文案）
    // 与按句切分的纯文本辅助。

    /**
     * U1「点空白全关」：关闭图表气泡并清掉它挂的区间带（sonar/圈选带）。
     * ② 句图带若在气泡开启期间被覆盖，收起后重新点句即可恢复。
     */
    private fun closeChartBubble() {
        if (detailOverlayCoordinator.active() == DetailOverlay.CHART_BUBBLE) {
            detailOverlayCoordinator.close()
            detailChartCoordinator.clearBandRange()
            // 关气泡即中断圈选 AI 流（气泡已不可见，流完也无处展示）
            detailAiCoordinator.cancelCircleAi()
        }
    }

    /**
     * DetailChartInteractionCoordinator 的下游副作用（docs/43 D2）：overlay
     * 仲裁（D4 地盘）与圈选 AI 解读（D3 地盘）仍是本页地盘，本函数承接。
     */
    private fun handleDetailChartEffect(effect: DetailChartEffect) {
        when (effect) {
            is DetailChartEffect.ChartBubbleShown -> {
                // 新气泡内容一律重置上一段圈选 AI 流（generation 失效使旧回调全部 no-op）
                detailAiCoordinator.cancelCircleAi()
                detailOverlayCoordinator.request(DetailOverlay.CHART_BUBBLE)
            }
            is DetailChartEffect.CircleSelectionCommitted -> detailAiCoordinator.requestCircleAi(effect.lo, effect.hi)
            is DetailChartEffect.CircleSelectionRejected -> toastHint(effect.message)
        }
    }

    /** ① 气泡来源行：圈选 AI 流式期间如实标注状态（与 AI 解读块同一「真 AI/端侧」分界）。 */
    private fun chartBubbleSourceLabel(): String = when (circleAiState) {
        1, 2 -> "AI 生成中（$circleAiModel）"
        3 -> "AI 生成（$circleAiModel）· 仅供参考"
        4 -> "端侧统计 · AI 调用失败"
        else -> "端侧统计"   // 未请求 AI（声呐气泡/未配置）：不得挂「AI」名头
    }

    /** ③ 指标长按抓取：chip 直接入上下文（降级路径），去重由 chipStore 负责。 */
    private fun grabMetric(label: String, value: String) {
        val added = chipStore.add(ContextChip(label, label, value))
        toastHint(
            if (added) "已抓取「$label」，问 AI 时会一并带上"
            else "「$label」已在提问上下文中"
        )
    }

    /**
     * B2 旗标落点：新闻发布时间夹紧映射到当日分时索引（盘前→开盘 09:30、午休→早盘末、
     * 盘后→尾盘 15:00）。东财 Art_ShowTime 多为盘前/盘后发布，此前严格映射对这些
     * 一律返回 null，导致「点按落旗」永远落不上（2026-09-09 修复）；无时间字段时退关键词静态映射。
     */
    private fun newsFlagIndex(item: NewsItem): Int? {
        if (item.time.length >= 16) {
            AnchorIndex.timeStringToIndexClamped(item.time.substring(11, 16))?.let { return it }
        }
        return when {
            listOf("北向", "早盘").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("09:47")
            listOf("半年报", "中报").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("11:02")
            listOf("批价", "渠道").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("13:35")
            listOf("龙虎榜", "席位").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("14:06")
            else -> null
        }
    }

    /** B2 落旗：旗标 + 区间高亮带（12 点宽）。旗色/标签 = 发布后 1h 真实走势，只述事实不写因果。 */
    private fun dropNewsFlag(item: NewsItem) {
        val idx = newsFlagIndex(item)
        if (idx == null) {
            toastHint("该条资讯缺少可用时间锚点，未落旗")
            return
        }
        droppedNewsIds.add(item.id)
        val series = detailTimelineSeries(quote)
        var isPositive = quote.change >= 0.0
        var label = ""
        if (idx in series.indices) {
            val endIdx = minOf(series.lastIndex, idx + 60)
            val base = series[idx]
            if (base != 0.0) {
                val pct = (series[endIdx] - base) / base * 100.0
                isPositive = pct >= 0
                label = Format.percent(pct)
            }
        }
        detailChartCoordinator.applyNewsFlag(idx, isPositive, label)
    }

    /** B2 收旗：移除该条旗标与高亮带（未落过旗则无操作）。 */
    private fun removeNewsFlag(item: NewsItem) {
        if (item.id !in droppedNewsIds) return
        droppedNewsIds.remove(item.id)
        newsFlagIndex(item)?.let { idx -> detailChartCoordinator.removeFlagAt(idx) }
    }

    /** 摘要条事实行用：该条是否已落旗。 */
    fun isNewsFlagged(item: NewsItem): Boolean = item.id in droppedNewsIds

    // ------------------------------------------------------------------
    // 弹幕 v2 节拍（对齐市场页）：33ms 步进 1dp；暂停/空数据只跳过步进，不拆链条。
    // 点按即停 = 摘要条展开或长按先览期间不步进，收起/松手后自动恢复。
    // ------------------------------------------------------------------

    private fun startTapeTimer() {
        if (tapeTimerStarted) return
        tapeTimerStarted = true
        scheduleTapeTick()
    }

    private fun scheduleTapeTick() {
        setTimeout(33) {
            if (!tapeTimerStarted) return@setTimeout
            if (!reduceMotion && newsSummary == null && tapePreview == null) {
                val loop = tapeLoopWidth()
                if (loop > 0f) {
                    val next = tapeOffset + 1f
                    tapeOffset = if (next >= loop) next - loop else next
                }
            }
            scheduleTapeTick()
        }
    }

    /** 一圈估算宽度：委托共享估算器（详情页恒带情绪点位）。 */
    private fun tapeLoopWidth(): Float =
        estimateNewsMarqueeLoopWidth(newsList, withSentimentDot = true)

    /**
     * B1/B2 弹幕条目点按（doc §4.3「点按条目或旗落旗，重复点按收起」）：
     * 收起/再展开以摘要条状态为准，与旗标是否落成功解耦——此前 toggle 挂在
     * droppedNewsIds 上，未落旗条目永不入集合，「收起 ×」点了只会重新打开同一条
     * （2026-09-09 修复「点按后收起/再点起无效」）。
     */
    private fun onNewsTapped(item: NewsItem) {
        // 与旗标/摘要的状态机分两域编排：overlay 走 Coordinator、旗标走 Chart
        // Coordinator（D2）。本页只负责「点同一条收起/点不同展开」的语义拼接。
        if (newsSummary?.id == item.id) {
            detailOverlayCoordinator.closeSummary()
            removeNewsFlag(item)
            return
        }
        detailOverlayCoordinator.showSummary(item)
        dropNewsFlag(item)
    }

    // B1 长按先览（doc §4.2）：状态机与定时器已迁入 D4 Coordinator，本页只在 DSL 转发。
    private fun showTapePreview(item: NewsItem, pressX: Float, pressY: Float) {
        detailOverlayCoordinator.showTapePreview(item, pressX, pressY)
    }

    /** B1 松手 700ms 后收先览（doc §4.2「气泡消失：松手 700ms 后或点按条目」）。 */
    private fun scheduleTapePreviewDismiss() {
        detailOverlayCoordinator.scheduleTapePreviewDismiss()
    }

    /** B2 摘要条「问问 AI」：新闻标题作上下文带入对话（问法为事实型：是什么意思）。 */
    private fun askAboutNews(news: NewsItem) {
        detailOverlayCoordinator.closeSummary()
        openChatWithQuestion(chipStore.promptFragment() + "「${news.title}」这条新闻是什么意思？", focusSymbol = symbol)
    }

    /** H1 快捷理由：写入自选 + 理由 + 当时价。 */
    private fun pickQuickReason(reason: String) {
        detailOverlayCoordinator.close()
        val result = watchlistStore.add(symbol, quote.name)
        if (result == WatchlistAddResult.FULL) {
            toastHint("自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧")
            return
        }
        if (result == WatchlistAddResult.ADDED) watchlistStore.setEntryPrice(symbol, quote.price)
        watchlistStore.setReason(symbol, reason)
        watchlisted = true
        toastHint("已记入当初理由")
    }

    /**
     * ② 点解读句：端侧从句子内容推导真实锚点亮区间带；同句再点收起。
     * 2026-09-09 用户定案：句句要有区域对应——句内无时间词、也无「最高/最低/上午」
     * 等语义词时，按句子序号在当日时间轴上等分近似圈区间（如实提示是近似定位）。
     */
    private fun pickSentence(index: Int) {
        if (selectedSentence == index) {
            selectedSentence = -1
            detailChartCoordinator.clearBandRange()
            return
        }
        val sentences = currentInsightSentences()
        val sentence = sentences.getOrNull(index)
        if (sentence == null) {
            selectedSentence = -1
            detailChartCoordinator.clearBandRange()
            return
        }
        selectedSentence = index
        val anchor = computeSentenceAnchor(sentence)
        detailChartCoordinator.setBandRange(
            if (anchor != null) {
                Triple(anchor.first, anchor.second, true)
            } else {
                // 近似定位：该句占当日时间轴的 1/n 等分（n = 句子总数）
                val n = AnchorIndex.INDEX_COUNT
                val start = (index * n / sentences.size).coerceIn(0, n - 2)
                val end = ((index + 1) * n / sentences.size).coerceAtMost(n - 1).coerceAtLeast(start + 1)
                toastHint("这句没引用具体时间，按句子顺序近似圈出对应时段")
                Triple(start, end, true)
            },
        )
    }

    /** 当前解读文本（远程 AI 优先，未请求/失败回退端侧模板）按句切分。 */
    private fun currentInsightSentences(): List<String> {
        val source = if (aiRemoteText.isNotBlank()) sanitizeAiText(aiRemoteText) else buildInsightSummary()
        return splitInsightSentences(source)
    }

    /** 保险清理：防模型偶发卡片协议/markdown 行污染逐句渲染。 */
    private fun sanitizeAiText(raw: String): String = raw
        .lines()
        .filterNot { it.trimStart().startsWith("```") }
        .joinToString("\n")
        .trim()

    private fun splitInsightSentences(source: String): List<String> =
        source.split(Regex("[。，]")).map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * ② 句图联动锚点真实化：全部来自当日真实分时，端侧计算坐标。
     * 1) 句内 HH:MM（≥2 个取首尾，1 个取 ±8 分钟窗）经 AnchorIndex 映射；
     * 2) 无时间词时按语义回退到分时真实极值/开盘时刻；
     * 3) 都对不上返回 null（不高亮，如实提示）。
     */
    private fun computeSentenceAnchor(sentence: String): Pair<Int, Int>? {
        val times = Regex("\\d{1,2}:\\d{2}").findAll(sentence)
            .mapNotNull { AnchorIndex.timeStringToIndex(it.value) }
            .toList()
        if (times.isNotEmpty()) {
            val start = times.min()
            val end = if (times.size > 1) times.max() else start + 8
            return start to end.coerceAtMost(AnchorIndex.INDEX_COUNT - 1)
        }
        val series = detailTimelineSeries(quote)
        if (series.isEmpty()) return null
        val last = series.lastIndex
        val highIdx = series.indices.maxByOrNull { series[it] }
        val lowIdx = series.indices.minByOrNull { series[it] }
        return when {
            sentence.contains("最高") && highIdx != null -> centeredAnchor(highIdx, last)
            sentence.contains("最低") && lowIdx != null -> centeredAnchor(lowIdx, last)
            sentence.contains("开盘") -> 0 to 8.coerceAtMost(last)
            sentence.contains("上午") -> 0 to 119.coerceAtMost(last)
            sentence.contains("下午") -> 120.coerceAtMost(last) to last
            else -> null
        }
    }

    private fun centeredAnchor(index: Int, last: Int): Pair<Int, Int> =
        (index - 8).coerceAtLeast(0) to (index + 8).coerceAtMost(last)

    /** A1 回访状态词：端侧判定（现价≥加自选价→兑现中；跌破 8%→已破位）。 */
    private fun revisitStatus(entryPrice: Double): Pair<String, Boolean> = when {
        entryPrice <= 0.0 -> "未记录加自选价" to true
        quote.price >= entryPrice -> "回调到位 · 理由兑现中" to true
        quote.price < entryPrice * 0.92 -> "已破位 · 回顾当初理由" to false
        else -> "低于加自选价 · 持有观察" to false
    }

    /** A1 期间最大回撤：暂无多日历史序列，先按当日分时峰谷回撤计算（已知简化）。 */
    private fun periodMaxDrawdownPct(): Double {
        val series = detailTimelineSeries(quote)
        if (series.size < 2) return 0.0
        var peak = series.first()
        var maxDd = 0.0
        for (p in series) {
            if (p > peak) peak = p
            if (peak > 0.0) maxDd = maxOf(maxDd, (peak - p) / peak * 100.0)
        }
        return maxDd
    }

    /** A1 事件一句话：只述事实。 */
    private fun revisitEventsSummary(): String = when {
        insight.fundamentals?.billboard != null -> "今日登上龙虎榜"
        newsList.isNotEmpty() -> "近期动态：${newsList.first().title.take(24)}"
        else -> "暂无特别事件记录"
    }

    /** A1 加自选时间标签（millis → 「M月d日 HH:mm」，多平台安全换算）。 */
    private fun formatEntryTime(millis: Long): String {
        if (millis <= 0L) return "此前"
        val totalMin = millis / 60000L
        val days = totalMin / 1440L
        // civil_from_days（Howard Hinnant 算法）：epoch 天数 → y/m/d
        var z = days + 719468L
        val era = (if (z >= 0) z else z - 146096L) / 146097L
        val doe = z - era * 146097L
        val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val d = doy - (153L * mp + 2L) / 5L + 1L
        val m = if (mp < 10L) mp + 3L else mp - 9L
        @Suppress("UNUSED_VARIABLE") val y = yoe + era * 400L
        val hh = (totalMin % 1440L) / 60L
        val mm = totalMin % 60L
        return "${m}月${d}日 ${if (hh < 10L) "0" else ""}$hh:${if (mm < 10L) "0" else ""}$mm"
    }

    /** FR-W2：当前自选理由（未自选或未填返回空）。 */
    private fun watchlistReason(): String =
        watchlistStore.list().firstOrNull { it.symbol == symbol }?.reason.orEmpty()

    /**
     * DetailDataCoordinator 的下游副作用：quote 合并/insight 就位后，ticker 动效、
     * 声呐异动派生、AI 洞察触发仍是本页（及后续 Wave2 第 2/3 刀）的地盘，本函数
     * 原样承接原 `applyQuote()` 的这部分逻辑。
     */
    private fun handleDetailDataEffect(effect: DetailDataEffect) {
        when (effect) {
            is DetailDataEffect.QuoteApplied -> {
                val old = effect.previous
                val next = effect.next
                previousPriceText = Format.price(old.price)
                previousPercentText = Format.percent(old.changePercent)
                previousChangeText = "${Format.signed(old.change)}  ${Format.percent(old.changePercent)}"
                tickerDirectionUp = next.price >= old.price
                // doc 29 ④ 异动声呐：分时到达后跑一次端侧检测（成交量暂不参与确认，见已知简化）。
                // 同屏 ≤3 点（doc §4.4 呼吸预算，U4），超出的按 |涨跌幅| 降序舍弃。
                detailChartCoordinator.applySonarPoints(detectAnomalies(detailTimelineSeries(next), null).take(3))
                // 分时首次到达后择机启动真实 AI 解读（等 800ms 让资金流/财报尽量落位）；
                // 只在分时"新到"时调度一次，不再随快照/K线回调重复 setTimeout。
                if (effect.timelineJustArrived) setTimeout(800) { detailAiCoordinator.maybeStartAiInsight() }
                if (effect.changed) playTicker()
            }
            DetailDataEffect.InsightLoaded -> {
                detailAiCoordinator.startReveal(buildInsightSummary())
                // 行情+洞察就绪后启动真实 AI 解读（未配置/无分时数据则保持端侧模板）
                detailAiCoordinator.maybeStartAiInsight()
            }
            is DetailDataEffect.PrefetchApplied -> {
                detailChartCoordinator.applySonarPoints(detectAnomalies(detailTimelineSeries(effect.quote), null).take(3))
            }
        }
    }

    private fun playTicker() {
        if (reduceMotion) return
        tickerLift = true
        // 复位双保险（R5：setTimeout 链在 Android 上会丢回调）：180ms 正常复位 +
        // 600ms 断链兜底。同值写不通知，兜底触发时幂等；version 防旧 timer
        // 把新一轮 lift 提前拍死在半程（动画播一半顶部数据卡在半透明态）。
        val version = ++tickerLiftVersion
        setTimeout(180) { if (version == tickerLiftVersion) tickerLift = false }
        setTimeout(600) { if (version == tickerLiftVersion) tickerLift = false }
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

    /** 图表连续运动相位（实时点呼吸 + 声呐漂移）；reduceMotion 下恒 0。 */
    private fun startSonarDrift() {
        if (reduceMotion) {
            sonarDrift = 0f
            return
        }
        val version = ++sonarDriftVersion
        fun tick() {
            if (version != sonarDriftVersion) return
            // 手势期间十字线优先：不写 observable，避免底图与轻量十字线层争抢帧预算。
            if (!detailChartCoordinator.isInteractionActive()) sonarDrift = (sonarDrift + 0.00534f) % 1f
            setTimeout(16) { tick() }
        }
        setTimeout(0) { tick() }
    }

    /** 分时 draw-on 入场（doc 26 §4.5）：progress 0→1 步进驱动；断链兜底 1.2s 强制终帧。 */
    private fun startDrawOn() {
        val version = ++drawVersion
        if (reduceMotion) {
            drawProgress = 1f
            return
        }
        drawProgress = 0f
        fun tick() {
            if (version != drawVersion) return
            // 与手势/原生刷新对齐到帧级，维持约 360ms 的原入场节奏。
            drawProgress = (drawProgress + 0.045f).coerceAtMost(1f)
            if (drawProgress < 1f) setTimeout(16) { tick() }
        }
        setTimeout(0) { tick() }
        setTimeout(1200) { if (version == drawVersion) drawProgress = 1f }
    }


    /** 振幅（(高-低)/昨收），昨收缺失时 0。 */
    private fun amplitudePercent(): Double =
        if (quote.previousClose <= 0.0) 0.0 else (quote.high - quote.low) / quote.previousClose * 100.0

    /**
     * AI 一行归因（doc 26 §7）：端侧模板、纯事实槽位，不走 LLM、无「问 AI」按钮。
     * 例：「低开 0.45%，日内最高 1331.35（09:31）、最低 1324.00（09:30），现价 1323.65，位于平盘线下方。」
     */
    private fun buildOneLineAttribution(): String {
        val q = quote
        if (q.previousClose <= 0.0) return "行情到达后，这里会给出一句事实性复盘。"
        if (q.timeline.isEmpty()) return "分时数据到达后，这里会给出一句事实性复盘。"
        val openPct = (q.open - q.previousClose) / q.previousClose * 100.0
        val openDesc = when {
            openPct > 0.05 -> "高开 ${Format.decimal(openPct, 2)}%"
            openPct < -0.05 -> "低开 ${Format.decimal(-openPct, 2)}%"
            else -> "平开"
        }
        val highPoint = q.timeline.maxByOrNull { it.price }
        val lowPoint = q.timeline.minByOrNull { it.price }
        val position = if (q.price >= q.previousClose) "上方" else "下方"
        val toneDesc = when {
            q.change > 0.0 -> "现报 ${Format.price(q.price)}"
            q.change < 0.0 -> "现报 ${Format.price(q.price)}"
            else -> "现价 ${Format.price(q.price)}"
        }
        return "$openDesc，日内最高 ${Format.price(q.high)}（${highPoint?.time ?: "--"}）、" +
            "最低 ${Format.price(q.low)}（${lowPoint?.time ?: "--"}），$toneDesc，位于平盘线$position。"
    }

    // ── DSL 接线入口（doc 43 D3：AI 编排已迁入 Coordinator；本页只保留少量页侧转接）──
    /** AI 解读块的「问 AI」按钮文案（与原 aiActionLabel 等价）：1/2→停止、3→重新解读、4→重试、其余→生成。 */
    private fun aiActionLabel(): String = when (aiRemoteState) {
        1, 2 -> "停止"
        3 -> "重新解读"
        4 -> "重试"
        else -> "生成"
    }

    /** AI 解读块的「问 AI」按钮点击：流式中→停止；否则→请求一次。 */
    private fun toggleAiInsight() {
        detailAiCoordinator.toggleInsight()
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
        val time = quote.timestamp.ifEmpty { "等待刷新" }
        // 行情来源/演示模式不属于用户决策信息，且会和“实时同步”重复；详情头只保留
        // 必要的更新时间，离线状态由请求失败时的明确提示承载。
        return "更新于 $time"
    }

    /**
     * The Hero is the single source of chart inspection data: long-pressing a timeline point or
     * selecting a candle changes this snapshot instead of opening a chart-local tooltip.
     */
    private fun chartHeaderSnapshot() = DetailChartHeaderResolver.resolve(
        DetailChartHeaderInput(
            quote = quote,
            mode = chartMode,
            period = chartPeriod,
            crosshairIndex = crosshairIndex,
            selectedKLineIndex = selectedKLineIndex,
            theme = theme,
            fallbackTone = toneColor(),
            fallbackCaption = quoteSourceLine(),
            amplitudePercent = amplitudePercent(),
        ),
    )

    // doc 29 ③：「问 AI」默认问句（有停顿预填时优先用预填）
    private fun askAiQuestion(): String = "帮我解读一下${quote.name}今天的走势。"

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

private data class TrendOutlook(
    val title: String,
    val detail: String,
    val confidence: String,
    val color: Color,
)

private data class BusinessInsightItem(
    val id: String,
    val label: String,
    val model: CardModel,
    // doc 29 E1：今日相关置顶卡（至多一张，构建处经 pickPinnedCard 仲裁）
    val pinned: Boolean = false,
    // doc 29 E2：卡内 AI 注脚（无真实输入时为 null，不显示——不伪造数据）
    val footnote: CardFootnote? = null,
)

/**
 * 行业数据与公司介绍的共享卡。横向滑动仅在横向位移明显大于纵向位移时接管，
 * 所以正常纵向浏览仍交给详情页 Scroller；标签点击为无手势偏好的等价入口。
 */
private fun ViewContainer<*, *>.CompanyIndustryPanel(
    selectedTab: () -> Int,
    profile: () -> DetailCompanyProfile,
    quote: () -> Quote,
    theme: StockChatTheme,
    reduceMotion: Boolean,
    tabTrackWidth: Float,
    onSelectTab: (Int) -> Unit,
) {
    var downX = 0f
    var downY = 0f
    var axis = 0 // 0 未仲裁；1 横向切换；2 纵向滚动
    var gestureDone = false

    SectionLabel("公司介绍 / 公司数据", theme, strong = true)
    View {
        attr {
            marginTop(8f)
            padding(12f)
            borderRadius(14f)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            touchEnable(true)
        }
        View {
            attr {
                height(30f)
                padding(3f)
                flexDirectionRow()
                borderRadius(8f)
                backgroundColor(theme.surfaceMuted)
            }
            // 单一滑块置于标签下方；按 tab 驱动横向平移，而非两枚独立按钮各自变底色。
            View {
                attr {
                    val tab = selectedTab()
                    absolutePosition(top = 3f, left = 3f)
                    width(((tabTrackWidth - 6f) / 2f).coerceAtLeast(48f))
                    height(24f)
                    borderRadius(6f)
                    backgroundColor(theme.surface)
                    boxShadow(BoxShadow(0f, 1f, 4f, theme.textPrimary.opacity(0.08f)))
                    transform(Translate(if (tab == 0) 0f else ((tabTrackWidth - 6f) / 2f).coerceAtLeast(48f), 0f))
                    // R5：每轮都为下次 tab 改变登记滑块动画。
                    if (!reduceMotion) animate(Animation.easeOut(0.20f), tab)
                }
            }
            listOf("公司介绍", "公司数据").forEachIndexed { index, label ->
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        borderRadius(6f)
                        val active = selectedTab() == index
                        backgroundColor(Color.TRANSPARENT)
                        if (!reduceMotion) animate(Animation.easeOut(0.20f), active)
                    }
                    Text {
                        attr {
                            val active = selectedTab() == index
                            text(label)
                            fontSizeScaled(10f)
                            fontWeightSemiBold()
                            color(if (active) theme.brand else theme.textTertiary)
                            if (!reduceMotion) animate(Animation.easeOut(0.18f), active)
                        }
                    }
                    event { click { onSelectTab(index) } }
                }
            }
        }

        // vbind 负责卸载上一面、挂载下一面；内部两帧进入避免新挂载内容首帧跳变。
        vbind({ selectedTab() }) {
            if (selectedTab() == 1) {
                CompanyIndustryPanelEntrance(reduceMotion) {
                    View {
                        attr { marginTop(12f) }
                        Text {
                            attr {
                                text("公司数据")
                                fontSizeScaled(14f)
                                fontWeightSemiBold()
                                color(theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("财务、股东户数、分红与解禁、龙虎榜和资金流将在下方依次展开。")
                                marginTop(5f)
                                fontSizeScaled(11f)
                                lineHeightScaled(16f)
                                color(theme.textSecondary)
                            }
                        }
                    }
                }
            } else {
                CompanyIndustryPanelEntrance(reduceMotion) {
                    vbind({ profile() to quote() }) {
                        val company = profile()
                        val currentQuote = quote()
                        View {
                            attr { marginTop(12f) }
                            Text {
                                attr {
                                    text("${currentQuote.name} · 公司简介")
                                    fontSizeScaled(14f)
                                    fontWeightSemiBold()
                                    color(theme.textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    // 这里只保留一段摘要，详细事实仍以各业务数据卡与公告为准。
                                    text(company.summary ?: "公司简介暂未接入；可切到「公司数据」查看已接入的财务与公告相关数据。")
                                    marginTop(5f)
                                    fontSizeScaled(11f)
                                    lineHeightScaled(16f)
                                    color(theme.textSecondary)
                                }
                            }
                            if (company.tags.isNotEmpty()) {
                                View {
                                    attr { marginTop(9f); flexDirectionRow() }
                                    company.tags.forEach { tag ->
                                        View {
                                            attr {
                                                marginRight(6f)
                                                padding(4f)
                                                borderRadius(7f)
                                                backgroundColor(theme.brandSoft)
                                            }
                                            Text {
                                                attr {
                                                    text("# $tag")
                                                    fontSizeScaled(9.5f)
                                                    color(theme.brand)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            company.focus?.let { focus ->
                                Text {
                                    attr {
                                        text("阅读线索：$focus")
                                        marginTop(9f)
                                        fontSizeScaled(9.5f)
                                        lineHeightScaled(14f)
                                        color(theme.textTertiary)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text("切到「公司数据」：财务 · 股东户数 · 分红与解禁 · 龙虎榜")
                                    marginTop(8f)
                                    fontSizeScaled(9.5f)
                                    color(theme.brand)
                                }
                            }
                        }
                    }
                }
            }
        }

        event {
            touchDown { event ->
                downX = event.x
                downY = event.y
                axis = 0
                gestureDone = false
            }
            touchMove { event ->
                if (gestureDone || axis == 2) return@touchMove
                val dx = event.x - downX
                val dy = event.y - downY
                if (axis == 0) {
                    if (kotlin.math.abs(dx) < 10f && kotlin.math.abs(dy) < 10f) return@touchMove
                    axis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else 2
                }
            }
            touchUp { event ->
                if (gestureDone) return@touchUp
                gestureDone = true
                if (axis == 1 && kotlin.math.abs(event.x - downX) >= 28f) {
                    onSelectTab(if (event.x < downX) 1 else 0)
                }
            }
            touchCancel { _ -> gestureDone = true }
        }
    }
}

/** 新挂载的 tab 内容用两帧淡入上移；R5 中每帧都登记同一动画供下轮翻转消费。 */
private fun ViewContainer<*, *>.CompanyIndustryPanelEntrance(
    reduceMotion: Boolean,
    content: ViewContainer<*, *>.() -> Unit,
) {
    val presented = BlockState(reduceMotion)
    if (!reduceMotion) setTimeout(0) { presented.value = true }
    View {
        attr {
            val visible = presented.value
            opacity(if (visible) 1f else 0f)
            transform(Translate(if (visible) 0f else 0.08f, 0f))
            if (!reduceMotion) animate(Animation.easeOut(0.18f), visible)
        }
        content()
    }
}

private fun ViewContainer<*, *>.SectionLabel(
    text: String,
    theme: StockChatTheme,
    // true = 章节级节头（原型 .sec-head .t：12px/800/主文字色）；默认弱样式仅用于
    // bento 卡标题（原型 .bcard .k 10px/text3 的就近映射）。
    strong: Boolean = false,
    // true = AI 可交互节头（原型 .ai-head .t：brand 强调色，doc29 协调规则②）
    accent: Boolean = false,
) {
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text(text)
            when {
                accent -> {
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.brand)
                }
                strong -> {
                    fontSizeScaled(12f)
                    fontWeightBold()
                    color(theme.textPrimary)
                }
                else -> {
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.textTertiary)
                }
            }
        }
    }
}

/**
 * 业务数据节头（原型 .sec-head）：标题 + 右侧 brand 提示。
 * E1 为自动置顶（无 FLIP 重放，见 doc 29 §9 有意偏差），提示用陈述文案、不做假按钮。
 */
private fun ViewContainer<*, *>.TickerText(
    // 可变状态一律传 lambda：observable 读取延迟到 attr/vif 闭包内（R1），
    // 行情 tick 时文本/颜色随 attr 重跑刷新，lift 动画才有驱动 key（R2）。
    text: () -> String,
    previousText: () -> String,
    loading: () -> Boolean,
    fontSize: Float,
    // 宽度同样走 lambda：涨跌胶囊宽度随文本长度变化（attr 内读取，随 quote 刷新）
    width: () -> Float,
    color: () -> Color,
    theme: StockChatTheme,
    lift: () -> Boolean,
    directionUp: () -> Boolean,
    reduceMotion: Boolean,
) {
    vif({ loading() }) {
        View {
            attr {
                width(width())
                height(fontSize * 0.72f)
                borderRadius(6f)
                backgroundColor(theme.surfaceMuted)
            }
        }
    }
    vif({ !loading() }) {
    View {
        attr {
            width(width())
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

/** 走势卡头图例：价格（实色）/ 均价（虚线）/ 昨收（虚线弱化）。 */
private fun ViewContainer<*, *>.ChartLegend(theme: StockChatTheme, tone: () -> Color) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); touchEnable(false) }
        LegendItem(theme, tone, "价格", dashed = false)
        LegendItem(theme, { theme.textSecondary }, "均价", dashed = true)
        LegendItem(theme, { theme.textTertiary }, "昨收", dashed = true)
    }
}

private fun ViewContainer<*, *>.LegendItem(theme: StockChatTheme, color: () -> Color, label: String, dashed: Boolean) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); marginLeft(if (label == "价格") 0f else 8f) }
        View {
            attr {
                width(10f)
                if (dashed) {
                    height(0f)
                    borderBottom(Border(1.5f, BorderStyle.DASHED, color()))
                } else {
                    height(2.4f)
                    borderRadius(1.2f)
                    backgroundColor(color())
                }
                touchEnable(false)
            }
        }
        Text {
            attr {
                text(label)
                marginLeft(3f)
                fontSizeScaled(9f)
                color(theme.textTertiary)
            }
        }
    }
}

/** 次级指标行：无分隔线的轻量 label-value 列（doc 26 §6）。 */
private fun ViewContainer<*, *>.SecondaryMetricRow(
    items: List<DetailMetric>,
    theme: StockChatTheme,
    marginTop: Float = 0f,
    // doc 29 ③：单元格长按 400ms（U5）→ 抓取为上下文 chip
    onGrabCell: ((DetailMetric) -> Unit)? = null,
) {
    View {
        attr { flexDirectionRow(); marginTop(marginTop) }
        items.forEach { item ->
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(item.label)
                        fontSizeScaled(9.5f)
                        color(theme.textTertiary)
                    }
                }
                Text {
                    attr {
                        text(item.value)
                        marginTop(2f)
                        fontSizeScaled(12f)
                        fontWeightMedium()
                        color(item.valueColor ?: theme.textSecondary)
                    }
                }
                if (onGrabCell != null) {
                    event { longPress { onGrabCell.invoke(item) } }
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
    // 全页阶梯入场的起始序号：走势卡 0 / 指标板 1 / AI 归因行 2 之后接续，
    // 业务卡数量异步到达会变化，body 重跑时按当帧 size 顺延即可。
    baseIndex: Int,
    // 传 lambda 而非 Boolean：闭包实参是建视图时的首帧快照（R1），
    // observable 的读取必须延迟到 RevealBlock 的 attr 闭包内才建立依赖。
    entranceVisible: () -> Boolean,
    reduceMotion: Boolean,
    // doc 29 E2：注脚点击 → 页面展示判定依据（U3）
    onFootnoteClick: (CardFootnote) -> Unit = {},
) {
    if (items.isEmpty()) {
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
    // 数据卡的内容高度不一致（资金/财务/股东/龙虎榜/公司行为），固定行的 2×N
    // 网格会在较短卡下方留下突兀空白。改为按预估信息密度自动分配的双列瀑布流，
    // 每一列独立向下排布；仅单卡时保持满宽。
    if (wide || items.size > 1) {
        val columns = balancedBusinessColumns(items)
        View {
            attr {
                marginTop(theme.spacing.md)
                flexDirectionRow()
                alignItemsFlexStart()
            }
            columns.forEachIndexed { columnIndex, column ->
                View {
                    attr {
                        flex(1f)
                        if (columnIndex == 0) marginRight(6f) else marginLeft(6f)
                    }
                    column.forEach { item ->
                        val itemIndex = items.indexOf(item)
                        View {
                            attr { marginTop(if (item == column.first()) 0f else 12f) }
                            RevealBlock(baseIndex + itemIndex, entranceVisible, reduceMotion) {
                                BusinessCardSlot(item, theme, onFootnoteClick, label = item.label, card = {
                                    CardShell(item.model, context, pinnedRing = item.pinned, noTopMargin = true)
                                })
                            }
                        }
                    }
                }
            }
        }
        return
    }
    items.forEachIndexed { index, item ->
        RevealBlock(baseIndex + index, entranceVisible, reduceMotion) {
            BusinessCardSlot(item, theme, onFootnoteClick, label = item.label, card = {
                CardShell(item.model, context, pinnedRing = item.pinned, noTopMargin = true)
            })
        }
    }
}

/** Greedy two-column packing keeps variable-length business cards from reserving blank row space. */
private fun balancedBusinessColumns(items: List<BusinessInsightItem>): List<List<BusinessInsightItem>> {
    val columns = listOf(mutableListOf<BusinessInsightItem>(), mutableListOf<BusinessInsightItem>())
    val loads = floatArrayOf(0f, 0f)
    fun estimatedHeight(item: BusinessInsightItem) = when (item.model) {
        is FinancialCardModel -> 1.15f
        is ShareholderCardModel -> 1.10f
        is BillboardCardModel -> 1.30f
        is CorporateActionCardModel -> 1.45f
        is FundFlowCardModel -> 1.00f
        else -> 1.10f
    }
    items.forEach { item ->
        val target = if (loads[0] <= loads[1]) 0 else 1
        columns[target] += item
        loads[target] += estimatedHeight(item)
    }
    return columns
}

/**
 * doc 29 E1/E2/E3 业务卡槽：置顶卡由 CardShell(pinnedRing) 在卡自身边框上画 brand
 * 描边 + 「今日相关」角标（v1.0 描边画在外层 wrapper 上，与卡片之间隔着标签和
 * 边距，光圈外一圈留白、不贴合，已废弃）；有注脚的卡在卡底加 brand 小字（点击
 * 展示判定依据 + 「· 端侧规则」）；长按 400ms（U5）→ E3 行业对比覆盖层（只读），
 * 松手 2.2s 后弹回。
 */
private fun ViewContainer<*, *>.BusinessCardSlot(
    item: BusinessInsightItem,
    theme: StockChatTheme,
    onFootnoteClick: (CardFootnote) -> Unit,
    label: String,
    card: ViewContainer<*, *>.() -> Unit,
) {
    // E3 覆盖层状态（BlockState：observable 委托仅支持类成员，局部状态收敛）
    val compareShown = BlockState(false)
    var compareHideRevision = 0
    View {
        attr {
            alignSelfStretch()
        }
        // E3 长按行业比：start 挂载覆盖层；end/cancel 起算 2.2s 弹回。
        // 重按会使旧计时失效（revision），长按中不会中途消失。
        event {
            longPress { params ->
                when (params.state) {
                    "start" -> {
                        compareHideRevision++
                        compareShown.value = true
                    }
                    "end", "cancel" -> {
                        val revision = compareHideRevision
                        setTimeout(2200) {
                            if (revision == compareHideRevision) compareShown.value = false
                        }
                    }
                }
            }
        }
        SectionLabel(label, theme)
        // 卡片锚点容器：「今日相关」角标与 E3 覆盖层都以它为定位基准。
        // CardShell 以 noTopMargin/pinnedRing 关掉自身上边距，锚点上边距统一
        // 补回与标签的间距，于是锚点边界 = 卡片边界（置顶与否皆成立）——
        // 覆盖层 absolutePositionAllZero 即与正常卡片同宽、同高、同位。
        View {
            attr {
                alignSelfStretch()
                marginTop(10f)
            }
            card()
            // 「今日相关」角标必须画在 card() 之后（2026-09-09 修复「今日相被盖住」）：
            // 角标 absolutePosition(top=-8f) 骑在卡顶边上，若先于卡片挂载，后画的
            // 卡身背景会把角标下半截（含文字下半）盖住。后画者在上，角标才完整可见。
            vif({ item.pinned }) {
                View {
                    attr {
                        absolutePosition(top = -8f, right = 10f)
                        height(16f)
                        paddingLeft(8f)
                        paddingRight(8f)
                        allCenter()
                        borderRadius(8f)
                        backgroundColor(theme.brand)
                        touchEnable(false)
                    }
                    Text {
                        attr {
                            text("今日相关")
                            fontSizeScaled(9f)
                            fontWeightSemiBold()
                            color(theme.onBrand)
                        }
                    }
                }
            }
            vif({ compareShown.value }) {
                IndustryCompareOverlay(theme, label)
            }
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
            //
            // 节奏对齐 ChatScaffolding.QuestionStarterCard（欢迎语四张引导卡）：
            // 上滑 28% 自高 + 淡入，easeOut 0.375s，阶梯延迟 0.08s 起步、步长
            // 0.094s（下一张在前一张进行到 25% 时启动）。
            val shown = visible()
            opacity(if (shown) 1f else 0f)
            if (!reduceMotion) {
                transform(Translate(0f, if (shown) 0f else 0.28f))
                // 无条件注册 easeOut（含未呈现态）：flip 周期消费的正是上一周期
                // 注册的这份动画（R5）。此前 else 分支注册 linear(0)，入场被
                // 消费成 0 时长瞬移——与 CardSheet/ChatScaffolding 同一范式。
                animate(Animation.easeOut(0.375f).delay(0.08f + 0.094f * index), shown)
            }
        }
        content()
    }
}

// 图表导航：单独占一行，留出足够的触控面积，也不会遮住高低点与图例。
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
    // activeIndex 在闭包内实时派生（R1）：捕获计算结果会让点亮态全部冻结。
    fun activeIdx(): Int = when {
        chartMode() == StockChartMode.TIMELINE -> 0
        chartPeriod() == StockChartPeriod.WEEK -> 2
        chartPeriod() == StockChartPeriod.MONTH -> 3
        else -> 1
    }
    View {
        attr {
            marginBottom(10f)
            padding(4f)
            flexDirectionRow()
            alignSelfFlexStart()
            backgroundColor(theme.marketGlass)
            borderRadius(13f)
            border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
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
                    width(42f)
                    height(32f)
                    marginRight(if (index < 3) 4f else 0f)
                    allCenter()
                    borderRadius(10f)
                    // 条件属性全量赋值（attr 不设不清）：点亮/熄灭两态都显式给全
                    backgroundColor(if (active()) theme.brandSoft else theme.marketGlass)
                    border(Border(1f, BorderStyle.SOLID, if (active()) theme.brand.opacity(0.34f) else theme.marketGlassEdge))
                    boxShadow(BoxShadow(0f, 2f, 6f, if (active()) theme.brand.opacity(0.12f) else Color(0L)))
                    if (!reduceMotion) animate(Animation.easeOut(0.16f), active())
                }
                Text {
                    attr {
                        text(label)
                        fontSizeScaled(11f)
                        fontWeightSemiBold()
                        color(if (active()) theme.brand else theme.textSecondary)
                    }
                }
                event { click { onSelect(mode, period) } }
            }
        }
    }
}

/** Shared, visible viewport controls for 分时 / 日K / 周K / 月K. */
private fun ViewContainer<*, *>.ChartViewportControls(
    theme: StockChatTheme,
    onAction: (ChartViewportAction) -> Unit,
) {
    val controls = listOf(
        ChartViewportAction.ZOOM_IN,
        ChartViewportAction.ZOOM_OUT,
        ChartViewportAction.PAN_LEFT,
        ChartViewportAction.PAN_RIGHT,
        ChartViewportAction.RESET,
    )
    View {
        attr {
            padding(4f)
            flexDirectionRow()
            backgroundColor(theme.marketGlass.opacity(0.96f))
            borderRadius(12f)
            border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
            boxShadow(BoxShadow(0f, 3f, 10f, theme.textPrimary.opacity(0.10f)))
        }
        controls.forEachIndexed { index, action ->
            View {
                attr {
                    width(24f)
                    height(30f)
                    allCenter()
                    borderRadius(8f)
                    if (index > 0) marginLeft(2f)
                }
                when (action) {
                    ChartViewportAction.ZOOM_IN -> LineIconPlus(theme.textPrimary, 15f)
                    ChartViewportAction.ZOOM_OUT -> LineIconMinus(theme.textPrimary, 15f)
                    ChartViewportAction.PAN_LEFT -> LineIconArrowLeft(theme.textPrimary, 16f)
                    ChartViewportAction.PAN_RIGHT -> LineIconArrowRight(theme.textPrimary, 16f)
                    ChartViewportAction.RESET -> LineIconReset(theme.textPrimary, 15f)
                    ChartViewportAction.NONE -> Unit
                }
                event { click { onAction(action) } }
            }
        }
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
    // doc 29 ③：抓取上下文 chips（点击移除，去重由 ContextChipStore 负责）
    chips: () -> List<ContextChip> = { emptyList() },
    onRemoveChip: (ContextChip) -> Unit = {},
    // doc 29 ⑤：scrub 停顿预填（灰字展示，与用户手打区分；只预填不发送）
    prefill: () -> String = { "" },
) {
    View {
        attr {
            absolutePosition(
                bottom = 18f + bottomInset,
                left = 14f,
                right = 14f,
            )
        }
        // ③ 抓取 chips 行：vbind 按 chips 列表重建（vif 只判有无，1→2 枚不会重跑内容闭包，
        // 此前第二枚 chip 不显示、删除后残留旧 chip 的 bug 即源于此）
        vbind({ chips() }) {
            if (chips().isNotEmpty()) {
                View {
                    attr {
                        marginBottom(6f)
                        paddingLeft(4f)
                        paddingRight(4f)
                        flexDirectionRow()
                        flexWrapWrap()
                    }
                    chips().forEach { chip ->
                        View {
                            attr {
                                marginRight(6f)
                                marginBottom(4f)
                                paddingLeft(10f)
                                paddingRight(10f)
                                height(28f)
                                allCenter()
                                borderRadius(14f)
                                backgroundColor(theme.surface)
                                border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(0.6f)))
                            }
                            Text {
                                attr {
                                    text("${chip.label} ${chip.value} ×")
                                    fontSize(theme.type.meta)
                                    fontWeightMedium()
                                    color(theme.brand)
                                }
                            }
                            event { click { onRemoveChip(chip) } }
                        }
                    }
                }
            }
        }
        // ⑤ 预填灰字行（区别手打：surfaceMuted 底 + 三级灰字 + 「预填」前缀）
        vif({ prefill().isNotEmpty() }) {
            View {
                attr {
                    marginBottom(6f)
                    alignSelfFlexStart()
                    paddingLeft(10f)
                    paddingRight(10f)
                    paddingTop(5f)
                    paddingBottom(5f)
                    borderRadius(12f)
                    backgroundColor(theme.surfaceMuted)
                    border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                }
                Text {
                    attr {
                        text("预填 · ${prefill()}")
                        fontSize(theme.type.meta)
                        color(theme.textTertiary)
                    }
                }
            }
        }
        // 原型同款单一输入栏：自选由顶栏 + 管理，底栏只承担“带上下文问 AI”。
        // 旧版三枚操作按钮重复了页面已有入口，也把视觉重心从图表拉走。
        View {
            attr {
                height(52f)
                padding(4f)
                borderRadius(25f)
                flexDirectionRow()
                alignItemsCenter()
                backgroundColor(theme.surface)
                border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                // 2026-09-10 用户反馈：底栏输入框阴影太浅，加深（0.16→0.32，位移/模糊同步加大）。
                boxShadow(BoxShadow(0f, 12f, 32f, theme.textPrimary.opacity(0.32f)))
            }
            View {
                attr {
                    flex(1f)
                    height(44f)
                    paddingLeft(12f)
                    justifyContentCenter()
                }
                Text {
                    attr {
                        text(if (prefill().isNotEmpty()) prefill() else "问点什么…（图表上停顿试试）")
                        fontSize(theme.type.label)
                        fontWeightMedium()
                        color(if (prefill().isNotEmpty()) theme.brand else theme.textTertiary)
                    }
                }
                event { click { onAskAi() } }
            }
            View {
                attr {
                    size(42f, 42f)
                    allCenter()
                    borderRadius(21f)
                    backgroundColor(theme.brand)
                    boxShadow(BoxShadow(0f, 4f, 12f, theme.brand.opacity(0.35f)))
                }
                Text { attr { text("↑"); fontSizeScaled(18f); fontWeightSemiBold(); color(theme.onBrand) } }
                event { click { onAskAi() } }
            }
        }
    }
}

/** ⋯ 更多操作菜单条目（2026-09-10）：纯文字行，整行 44dp 触控。 */
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
    state: () -> Int,
    remoteText: () -> String,
    remoteError: () -> String,
    remoteModel: () -> String,
    localSummary: () -> String,
    revealLimit: () -> Int,
    actionLabel: () -> String,
    remoteSentences: () -> List<String>,
    theme: StockChatTheme,
    onAction: () -> Unit,
    // true = 行情/资金事实尚未就绪、AI 请求还没发出（占位骨架，防先闪端侧模板再跳骨架）
    preparing: () -> Boolean = { false },
    // 呼吸相位（复用页侧 livePulse 680ms 翻转）：占位骨架整体明暗呼吸，等待真实 AI
    // 返回期间的「活着」反馈（2026-09-09 用户定案：呼吸占位框，替代静态骨架）
    breath: () -> Boolean = { false },
    reduceMotion: Boolean = false,
    selectedSentence: () -> Int = { -1 },
    onPickSentence: (Int) -> Unit = {},
) {
    View {
        attr {
            marginTop(theme.spacing.lg)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            boxShadow(BoxShadow(0f, 6f, 18f, theme.textPrimary.opacity(0.06f)))
        }
        View { attr { height(4f); backgroundColor(theme.brand) } }
        View {
            attr { padding(theme.spacing.lg) }
            // 头部：标题 + 真实动作按钮（生成/停止/重新解读/重试，随状态机变化）
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
                Text {
                    attr {
                        text(actionLabel())
                        fontSize(theme.type.meta)
                        fontWeightSemiBold()
                        color(theme.brand)
                    }
                    event { click { onAction() } }
                }
            }
            // 内容层：状态机 + 文本共同驱动重建；打字机 reveal 属数据驱动（R1），
            // 无注册动画，重建不会丢动画状态。
            vbind({ state() to (remoteText() to revealLimit()) }) {
                val st = state()
                val remote = remoteText().trim()
                when {
                    // thinking：呼吸占位框 + 进度说明（等真实 LLM 返回，不落端侧模板）
                    st == 1 -> {
                        AiInsightPlaceholder(
                            theme = theme,
                            message = "正在读取行情事实并生成解读…",
                            breath = breath,
                        )
                    }
                    // 占位图：页面已打开但行情/资金事实还没就绪、AI 请求尚未发出。
                    // 此前这段时间显示端侧模板文字、随后再跳骨架，观感慢且割裂。
                    st == 0 && preparing() -> {
                        AiInsightPlaceholder(
                            theme = theme,
                            message = "行情与资金数据就绪后，会自动生成 AI 解读…",
                            breath = breath,
                            reduceMotion = reduceMotion,
                        )
                    }
                    // 远程流式/完成：逐句渲染 + 句图联动
                    remote.isNotEmpty() && st in 2..3 -> {
                        InsightSentences(
                            sentences = remoteSentences(),
                            streaming = st == 2,
                            theme = theme,
                            selectedSentence = selectedSentence,
                            onPickSentence = onPickSentence,
                        )
                    }
                    // 错误回退：红字如实报错；已有部分流式文本则保留，否则退端侧模板
                    st == 4 -> {
                        Text {
                            attr {
                                text("AI 调用失败：${remoteError()}")
                                marginTop(theme.spacing.md)
                                fontSize(theme.type.meta)
                                color(theme.fall)
                            }
                        }
                        if (remote.isNotEmpty()) {
                            InsightSentences(
                                sentences = remoteSentences(),
                                streaming = false,
                                theme = theme,
                                selectedSentence = selectedSentence,
                                onPickSentence = onPickSentence,
                            )
                        } else {
                            InsightSentences(
                                sentences = splitLocalReveal(localSummary(), revealLimit()),
                                streaming = false,
                                theme = theme,
                                selectedSentence = selectedSentence,
                                onPickSentence = onPickSentence,
                            )
                        }
                    }
                    // 本地（未配置/未请求）：端侧模板打字机揭示
                    else -> {
                        val sentences = splitLocalReveal(localSummary(), revealLimit())
                        if (sentences.isEmpty()) {
                            View {
                                attr {
                                    marginTop(theme.spacing.md)
                                    width(180f); height(18f); borderRadius(5f)
                                    backgroundColor(theme.surface.opacity(0.72f))
                                }
                            }
                            View {
                                attr {
                                    marginTop(theme.spacing.sm)
                                    width(240f); height(12f); borderRadius(4f)
                                    backgroundColor(theme.surface.opacity(0.62f))
                                }
                            }
                        } else {
                            InsightSentences(
                                sentences = sentences,
                                streaming = false,
                                theme = theme,
                                selectedSentence = selectedSentence,
                                onPickSentence = onPickSentence,
                            )
                        }
                    }
                }
            }
            // 底部来源行（如实标注内容来源——这是「真 AI」与「端侧规则」的分界线）
            vbind({ state() to (remoteModel() to remoteError()) }) {
                val label = when {
                    state() == 1 -> "正在调用 AI（${remoteModel()}）· 流式生成中"
                    state() == 0 && preparing() -> "等待行情与资金数据就绪 · 就绪后自动调用 AI"
                    state() == 2 || state() == 3 -> "AI 生成（${remoteModel()}）· 仅供参考，不构成投资建议"
                    state() == 4 -> if (remoteText().isNotBlank()) "AI 流中断，以上为已生成的部分内容" else "AI 调用失败，以上为端侧规则摘要（未调用 AI）"
                    else -> "端侧规则摘要 · 未调用 AI（配置 API 后点「生成」获得真实解读）"
                }
                Text {
                    attr {
                        text(label)
                        marginTop(theme.spacing.md)
                        fontSize(theme.type.meta)
                        color(theme.textTertiary)
                    }
                }
            }
            // ② 句图联动可发现性提示
            Text {
                attr {
                    text("点句子在走势图高亮对应区间 · 时间取自句内引用，未引用时间的句子按顺序近似定位")
                    marginTop(8f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

/** 端侧模板的打字机揭示：截断到 revealLimit 后按句切分。 */
private fun splitLocalReveal(summary: String, revealLimit: Int): List<String> {
    if (revealLimit <= 0) return emptyList()
    val revealed = summary.take(revealLimit.coerceAtMost(summary.length))
    return revealed.split(Regex("[。，]")).map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * AI 解读占位骨架（thinking / 等待事实就绪两态共用）：
 * 三行条 + 一行说明，整体随 breath 相位做明暗呼吸（等待真实 AI 的「活着」反馈；
 * breath 恒 false（reduceMotion）时静态显示）。
 */
private fun ViewContainer<*, *>.AiInsightPlaceholder(
    theme: StockChatTheme,
    message: String,
    breath: () -> Boolean = { false },
    reduceMotion: Boolean = false,
) {
    // 呼吸：容器 opacity 随相位翻转 0.45↔1.0（R1/R2：attr 内读相位、animate 收尾；
    // R5：每次翻转重跑 attr 都重新注册，下一拍消费上一拍注册的动画）。
    // reduceMotion：恒全亮静态，不注册动画。
    View {
        attr {
            marginTop(theme.spacing.md)
            opacity(if (reduceMotion || breath()) 1f else 0.45f)
            if (!reduceMotion) animate(Animation.easeOut(0.68f), breath())
        }
        View { attr { width(220f); height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.14f)) } }
        View { attr { width(180f); height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.10f)); marginTop(9f) } }
        View { attr { width(200f); height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.08f)); marginTop(9f) } }
        Text { attr { text(message); marginTop(10f); fontSizeScaled(10f); color(theme.brand) } }
    }
}

/**
 * 图表区加载骨架（quoteLoading 期间替代空白图表，2026-09-10）：396dp 圆角玻璃
 * 基座 + 三条占位条（上两行拟价格行位、底部一行拟量能带），整体随 breath 相位
 * 明暗呼吸（复用 AI 占位骨架范式：R2/R5 每次 attr 重跑重注册，下一拍消费）。
 * reduceMotion 恒全亮静态。
 */
private fun ViewContainer<*, *>.ChartLoadingSkeleton(
    theme: StockChatTheme,
    breath: () -> Boolean = { false },
    reduceMotion: Boolean = false,
) {
    View {
        attr {
            marginTop(theme.spacing.lg)
            height(396f)
            borderRadius(16f)
            backgroundColor(theme.marketGlass.opacity(0.6f))
            border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
            opacity(if (reduceMotion || breath()) 1f else 0.55f)
            if (!reduceMotion) animate(Animation.easeOut(0.68f), breath())
        }
        View {
            attr {
                absolutePosition(left = 16f, top = 56f)
                width(196f); height(11f); borderRadius(5f)
                backgroundColor(theme.textTertiary.opacity(0.32f))
            }
        }
        View {
            attr {
                absolutePosition(left = 16f, top = 80f)
                width(132f); height(11f); borderRadius(5f)
                backgroundColor(theme.textTertiary.opacity(0.20f))
            }
        }
        View {
            attr {
                absolutePosition(left = 16f, top = 344f)
                width(176f); height(8f); borderRadius(4f)
                backgroundColor(theme.textTertiary.opacity(0.16f))
            }
        }
    }
}

/** 逐句渲染（首句强调 + 后续圆点行），流式态末句尾随光标；点句回调带全局句序号。 */
private fun ViewContainer<*, *>.InsightSentences(
    sentences: List<String>,
    streaming: Boolean,
    theme: StockChatTheme,
    selectedSentence: () -> Int,
    onPickSentence: (Int) -> Unit,
) {
    if (sentences.isEmpty()) return
    fun displayText(s: String, isLast: Boolean): String =
        s + if (streaming && isLast) " ▍" else if (s.endsWith("。") || s.endsWith("，")) "" else "。"
    // 首句（句 0）
    View {
        attr { marginTop(theme.spacing.sm) }
        Text {
            attr {
                text(displayText(sentences.first(), sentences.size == 1))
                fontSize(theme.type.body)
                fontWeightSemiBold()
                color(if (selectedSentence() == 0) theme.brand else theme.textPrimary)
                lineHeightScaled(21f)
            }
        }
        event { click { onPickSentence(0) } }
    }
    sentences.drop(1).forEachIndexed { index, s ->
        View {
            attr { flexDirectionRow(); marginTop(theme.spacing.sm); alignItemsFlexStart() }
            View {
                attr {
                    width(6f); height(6f); borderRadius(3f)
                    backgroundColor(if (selectedSentence() == index + 1) theme.brand else theme.textTertiary)
                    marginTop(6f); marginRight(theme.spacing.sm)
                }
            }
            Text {
                attr {
                    flex(1f)
                    text(displayText(s, index == sentences.size - 2))
                    fontSize(theme.type.sm)
                    lineHeightScaled(19f)
                    color(if (selectedSentence() == index + 1) theme.brand else theme.textSecondary)
                }
            }
            // ② 句图联动：点句 → 页面端侧计算真实锚点点亮区间带
            event { click { onPickSentence(index + 1) } }
        }
    }
}

private fun ViewContainer<*, *>.AttributionBlock(
    model: AttributionCardModel,
    theme: StockChatTheme,
    expandedKey: () -> String,
    // 行情方向走 lambda：model 是首帧快照，标题需随 quote tick 在 attr 内实时刷新（R1）
    rising: () -> Boolean,
    reduceMotion: Boolean,
    onToggle: (String) -> Unit,
) {
    val factors = model.factors
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text("为什么${if (rising()) "涨" else "跌"}")
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
                    lineHeightScaled(16f)
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
                            lineHeightScaled(16f)
                            color(theme.textSecondary)
                        }
                    }
                }
            }
            event { click { onToggle(key) } }
        }
    }
}

/**
 * 把可手调的归因重放和 AI 情景推演收在一个工作台内。用户可随时回到手动归因，
 * AI 面则明确展示「读取快照 → 提取信号 → 合成情景」的推测链，而非黑盒结论。
 */
private fun ViewContainer<*, *>.AttributionForecastWorkbench(
    theme: StockChatTheme,
    factors: List<FactorSpec>,
    actualPct: () -> Double,
    quote: () -> Quote,
    mainFlow: () -> Double?,
    containerWidth: Float,
    reduceMotion: Boolean,
) {
    val mode = BlockState(0) // 0 = 用户手动归因，1 = AI 推测过程
    View {
        attr {
            marginTop(8f)
            padding(12f)
            borderRadius(14f)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
        }
        View {
            attr {
                height(30f)
                padding(3f)
                flexDirectionRow()
                borderRadius(8f)
                backgroundColor(theme.surfaceMuted)
            }
            listOf("手动归因", "AI 走势").forEachIndexed { index, label ->
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        borderRadius(6f)
                        val active = mode.value == index
                        backgroundColor(if (active) theme.brandSoft else Color.TRANSPARENT)
                        if (!reduceMotion) animate(Animation.easeOut(0.18f), active)
                    }
                    Text {
                        attr {
                            val active = mode.value == index
                            text(label)
                            fontSizeScaled(10f)
                            fontWeightSemiBold()
                            color(if (active) theme.brand else theme.textTertiary)
                            if (!reduceMotion) animate(Animation.easeOut(0.18f), active)
                        }
                    }
                    event { click { mode.value = index } }
                }
            }
        }
        vbind({ mode.value }) {
            if (mode.value == 0) {
                WorkbenchPaneEntrance(reduceMotion) {
                    FactorReplayBlock(
                        theme = theme,
                        factors = factors,
                        actualPct = actualPct,
                        containerWidth = containerWidth - 24f,
                        reduceMotion = reduceMotion,
                    )
                }
            } else {
                WorkbenchPaneEntrance(reduceMotion) {
                    AiInferenceProcessBlock(
                        theme = theme,
                        quote = quote,
                        mainFlow = mainFlow,
                        reduceMotion = reduceMotion,
                    )
                    AiTrendForecastBlock(theme, quote, mainFlow)
                }
            }
        }
    }
}

/** 新切面固定走两帧挂载，使手动归因与 AI 推测的内容切换都有可见过渡。 */
private fun ViewContainer<*, *>.WorkbenchPaneEntrance(
    reduceMotion: Boolean,
    content: ViewContainer<*, *>.() -> Unit,
) {
    val presented = BlockState(reduceMotion)
    if (!reduceMotion) setTimeout(0) { presented.value = true }
    View {
        attr {
            val visible = presented.value
            opacity(if (visible) 1f else 0f)
            transform(Translate(0f, if (visible) 0f else 0.10f))
            if (!reduceMotion) animate(Animation.easeOut(0.20f), visible)
        }
        content()
    }
}

/** AI 推测的可见中间步骤；其结论仍由下方明确标注为情景判断的卡片给出。 */
private fun ViewContainer<*, *>.AiInferenceProcessBlock(
    theme: StockChatTheme,
    quote: () -> Quote,
    mainFlow: () -> Double?,
    reduceMotion: Boolean,
) {
    val phase = BlockState(if (reduceMotion) 3 else 0)
    if (!reduceMotion) {
        setTimeout(0) { phase.value = 1 }
        setTimeout(150) { phase.value = 2 }
        setTimeout(300) { phase.value = 3 }
    }
    vbind({ quote() to mainFlow() }) {
        val currentQuote = quote()
        val flow = mainFlow()
        val outlook = trendOutlook(currentQuote, flow, theme)
        val steps = listOf(
            "读取市场快照" to "涨跌 ${Format.percent(currentQuote.changePercent)} · 振幅 ${Format.percent(if (currentQuote.previousClose == 0.0) 0.0 else (currentQuote.high - currentQuote.low) / currentQuote.previousClose * 100.0)}",
            "提取归因信号" to (flow?.let { "主力资金${if (it >= 0) "净流入" else "净流出"}${Format.compactAmount(kotlin.math.abs(it))}" } ?: "资金数据暂未返回"),
            "合成下一阶段情景" to outlook.title,
        )
        View {
            attr {
                marginTop(12f)
                padding(10f)
                borderRadius(10f)
                backgroundColor(theme.brandSoft.opacity(0.60f))
            }
            Text {
                attr {
                    text("AI 推测过程")
                    fontSizeScaled(11f)
                    fontWeightSemiBold()
                    color(theme.brand)
                }
            }
            steps.forEachIndexed { index, (title, detail) ->
                View {
                    attr {
                        val shown = phase.value >= index + 1
                        marginTop(9f)
                        flexDirectionRow()
                        opacity(if (shown) 1f else 0f)
                        transform(Translate(if (shown) 0f else 0.08f, 0f))
                        if (!reduceMotion) animate(Animation.easeOut(0.18f), phase.value)
                    }
                    View {
                        attr {
                            width(16f); height(16f); borderRadius(8f)
                            allCenter()
                            backgroundColor(if (phase.value >= index + 1) theme.brand else theme.surfaceMuted)
                        }
                        Text { attr { text("${index + 1}"); fontSizeScaled(8.5f); fontWeightSemiBold(); color(if (phase.value >= index + 1) theme.onBrand else theme.textTertiary) } }
                    }
                    View {
                        attr { flex(1f); marginLeft(7f) }
                        Text { attr { text(title); fontSizeScaled(10f); fontWeightSemiBold(); color(theme.textPrimary) } }
                        Text { attr { text(detail); marginTop(2f); fontSizeScaled(9.5f); lineHeightScaled(14f); color(theme.textSecondary) } }
                    }
                }
            }
        }
    }
}

/**
 * A compact, explicitly uncertain next-session scenario derived from the same quote and fund-flow
 * inputs as the attribution block. It is labelled as a modelled outlook, never a trade signal.
 */
private fun ViewContainer<*, *>.AiTrendForecastBlock(
    theme: StockChatTheme,
    quote: () -> Quote,
    mainFlow: () -> Double?,
) {
    vbind({ quote() to mainFlow() }) {
        val outlook = trendOutlook(quote(), mainFlow(), theme)
        View {
            attr {
                marginTop(12f)
                paddingTop(10f); paddingBottom(10f)
                paddingLeft(12f); paddingRight(12f)
                borderLeft(Border(3f, BorderStyle.SOLID, outlook.color))
                backgroundColor(theme.surfaceMuted.opacity(0.68f))
                borderRadius(10f)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("AI 走势推演")
                        fontSizeScaled(12f)
                        fontWeightSemiBold()
                        color(theme.textPrimary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text("情景判断 · ${outlook.confidence}")
                        fontSizeScaled(10f)
                        color(outlook.color)
                    }
                }
            }
            Text {
                attr {
                    text(outlook.title)
                    marginTop(5f)
                    fontSizeScaled(14f)
                    fontWeightSemiBold()
                    color(outlook.color)
                }
            }
            Text {
                attr {
                    text(outlook.detail)
                    marginTop(3f)
                    fontSizeScaled(11f)
                    lineHeightScaled(16f)
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    text("仅基于当前价量与资金快照推演，不构成买卖建议。")
                    marginTop(6f)
                    fontSizeScaled(9.5f)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

private fun trendOutlook(q: Quote, mainFlow: Double?, theme: StockChatTheme): TrendOutlook {
    if (q.previousClose <= 0.0) {
        return TrendOutlook("等待行情数据", "需等待昨收、涨跌和成交数据完整后再生成情景。", "低", theme.textSecondary)
    }
    val flowSignal = when {
        mainFlow == null -> 0
        mainFlow > 0.0 -> 1
        mainFlow < 0.0 -> -1
        else -> 0
    }
    val priceSignal = when {
        q.changePercent > 0.8 -> 2
        q.changePercent > 0.15 -> 1
        q.changePercent < -0.8 -> -2
        q.changePercent < -0.15 -> -1
        else -> 0
    }
    val amplitude = (q.high - q.low) / q.previousClose * 100.0
    val score = priceSignal + flowSignal
    val flowText = mainFlow?.let { "主力资金${if (it >= 0.0) "净流入" else "净流出"}${Format.compactAmount(kotlin.math.abs(it))}" } ?: "主力资金尚未返回"
    return when {
        score >= 2 -> TrendOutlook(
            "偏强延续情景",
            "涨跌 ${Format.percent(q.changePercent)}，$flowText；若量能不明显回落，短线可能维持偏强节奏。振幅 ${Format.percent(amplitude)}，仍需防高波动回撤。",
            "中", theme.rise,
        )
        score <= -2 -> TrendOutlook(
            "偏弱修复情景",
            "涨跌 ${Format.percent(q.changePercent)}，$flowText；若后续没有资金回流，弱势可能延续。振幅 ${Format.percent(amplitude)}，留意波动进一步放大。",
            "中", theme.fall,
        )
        else -> TrendOutlook(
            "区间震荡情景",
            "涨跌 ${Format.percent(q.changePercent)}，$flowText；价格与资金信号尚未同向，下一阶段更可能围绕当日区间反复确认。",
            "低", theme.textSecondary,
        )
    }
}

/**
 * doc 29 F1 公告要点 · 端侧评级（公告与研报卡之前的摘要块）：
 * 前 3 条公告/研报（标题+徽章+日期），高重要度加粗；点击条目 toast 判定依据（U3）。
 * 徽章为三枚圆点：HIGH 三涨色 / MID 两橙 / LOW 一灰（DetailBoardBlocks.MaterialityBadge）。
 *
 * [inset] = 并入「公告与研报」大卡（原型 .ann-card：公告行直接落在卡面上，行间细分割线）；
 * false = 独立灰底子卡（旧形态，保留兼容）。
 */
private fun ViewContainer<*, *>.DisclosureMaterialityBlock(
    items: List<DisclosureItem>,
    theme: StockChatTheme,
    inset: Boolean = false,
    onExplain: (String) -> Unit,
    // 长按条目 → 页级预览浮层（MarketPage peek 同款，2026-09-09 新增）：
    // toast 稍纵即逝看不完详情，长按浮卡可停留细看（松手不消失，点蒙层/关闭收回）。
    onPeek: (DisclosureItem) -> Unit,
) {
    vif({ items.isNotEmpty() }) {
        View {
            attr {
                if (!inset) {
                    marginBottom(theme.spacing.md)
                    padding(theme.spacing.md)
                    borderRadius(theme.inputRadius)
                    backgroundColor(theme.surfaceMuted)
                    border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                }
            }
            Text {
                attr {
                    text("公告要点 · 端侧评级")
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.textSecondary)
                }
            }
            items.forEachIndexed { index, item ->
                // 评级在 attr 闭包内实时求值（纯函数，无副作用）
                fun level() = materialityOf(item.title).level
                View {
                    attr {
                        marginTop(8f)
                        if (inset && index > 0) {
                            // 并卡模式：公告行之间对齐原型的 0.5px 细分割线
                            paddingTop(8f)
                            borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
                        }
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    MaterialityBadge(theme, { level() })
                    Text {
                        attr {
                            text(item.title)
                            fontSize(theme.type.label)
                            // F1：高重要度整行加粗
                            if (level() == Materiality.HIGH) fontWeightBold() else fontWeightMedium()
                            color(theme.textPrimary)
                            lineHeightScaled(16f)
                        }
                    }
                    Text {
                        attr {
                            text("  ${item.date}")
                            fontSize(theme.type.meta)
                            color(theme.textTertiary)
                        }
                    }
                    event {
                        click { onExplain(item.title) }
                        longPress { params ->
                            if (params.state == "start") onPeek(item)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("长按条目看详情 · 点按看判定依据 · 端侧规则")
                    marginTop(8f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
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
                    lineHeightScaled(19f)
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

// ───────────────────────── F3 研报评级光谱（真实数据 + 演示兜底） ─────────────────────────

/**
 * 真实评级光谱（东财研报库近 90 天 emRatingName 聚合）→ BalanceSegment；
 * 在线缺失/无覆盖时回落演示段（维持原占位形态与「示例 · 演示数据」标注），光谱不空转。
 */
private fun balanceSegmentsFor(spectrum: RatingSpectrum?, theme: StockChatTheme): List<BalanceSegment> {
    val real = spectrum?.segments
        ?.takeIf { it.isNotEmpty() }
        ?.map { seg -> BalanceSegment(seg.label, seg.count, ratingSpectrumColor(seg.label, theme), seg.quote) }
    return real ?: listOf(
        BalanceSegment("买入", 4, theme.rise, "示例 · 演示数据：偏多观点的占位引用，仅用于展示评级光谱交互，不构成任何建议。"),
        BalanceSegment("增持", 3, theme.rise.opacity(0.55f), "示例 · 演示数据：谨慎看多的占位引用，观点切换仅为形态演示。"),
        BalanceSegment("中性", 2, theme.textTertiary, "示例 · 演示数据：中性观点的占位引用，等待更多数据验证。"),
        BalanceSegment("减持", 1, theme.fall, "示例 · 演示数据：偏空观点的占位引用，仅展示光谱另一端。"),
    )
}

/** 档位配色：多端看涨红/看跌绿，中性走次要文本色。 */
private fun ratingSpectrumColor(label: String, theme: StockChatTheme) = when (label) {
    "买入" -> theme.rise
    "增持" -> theme.rise.opacity(0.55f)
    "减持" -> theme.fall
    else -> theme.textTertiary
}
