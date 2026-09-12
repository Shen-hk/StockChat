package com.kuikly.stockchat.page

import com.kuikly.stockchat.detail.page.component.RevealBlock
import com.kuikly.stockchat.detail.page.component.SecondaryMetricRow
import com.kuikly.stockchat.detail.page.component.DetailBottomBar

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.stock.MarketCardRenderers
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chart.model.ChartViewportCommand
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
import com.kuikly.stockchat.data.provider.QuotePrefetchStore
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteLoadResult
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.StockInsightBundle
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
import com.kuikly.stockchat.detail.page.component.DetailAiInsightBlock
import com.kuikly.stockchat.detail.page.component.DetailAttributionBoard
import com.kuikly.stockchat.detail.page.component.DetailChartCard
import com.kuikly.stockchat.detail.page.component.DetailCompanyInfoSection
import com.kuikly.stockchat.detail.page.component.DetailHeroSection
import com.kuikly.stockchat.detail.page.component.DetailNewsTicker
import com.kuikly.stockchat.detail.page.component.DetailOverlays
import com.kuikly.stockchat.detail.ai.state.DetailAiHostPort
import com.kuikly.stockchat.detail.ai.state.DetailAiInsightCoordinator
import com.kuikly.stockchat.detail.ai.state.DetailAiState
import com.kuikly.stockchat.detail.ai.state.KuiklyDetailAiScheduler
import com.kuikly.stockchat.detail.overlay.state.DetailOverlayCoordinator
import com.kuikly.stockchat.detail.overlay.state.DetailOverlayState
import com.kuikly.stockchat.detail.overlay.state.KuiklyDetailOverlayScheduler
import com.kuikly.stockchat.data.config.AiConfig
// doc 29 集成：共享基建 + 板块组件（事件回调经这些基建接线）
import com.kuikly.stockchat.detail.domain.AnchorIndex
import com.kuikly.stockchat.detail.domain.AnomalyPoint
import com.kuikly.stockchat.detail.domain.ContextChip
import com.kuikly.stockchat.detail.domain.ContextChipStore
import com.kuikly.stockchat.detail.domain.DetailChartHeaderInput
import com.kuikly.stockchat.detail.domain.DetailChartHeaderResolver
import com.kuikly.stockchat.detail.domain.DetailMetric
import com.kuikly.stockchat.detail.domain.DetailOverlay
import com.kuikly.stockchat.detail.domain.detectAnomalies
import com.kuikly.stockchat.detail.domain.promptFragment
import com.kuikly.stockchat.detail.page.component.ChartFlag
import com.kuikly.stockchat.detail.page.component.detailTimelineSeries
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.detail.page.component.DetailTimelineChart
import com.kuikly.stockchat.page.components.estimateNewsMarqueeLoopWidth
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.AppTopBarAction
import com.kuikly.stockchat.foundation.ui.icon.LineIconBookmark
import com.kuikly.stockchat.foundation.ui.icon.LineIconDots
import com.kuikly.stockchat.page.components.AtmosphereBackdrop
import com.kuikly.stockchat.base.BridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
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
                    DetailHeroSection(
                        theme = page.theme,
                        reduceMotion = page.reduceMotion,
                        toneColor = { page.toneColor() },
                        tickerSnapshot = { page.chartHeaderSnapshot() },
                        previousPriceText = { page.previousPriceText },
                        previousPercentText = { page.previousPercentText },
                        quoteLoading = { page.quoteLoading },
                        tickerLift = { page.tickerLift },
                        tickerDirectionUp = { page.tickerDirectionUp },
                        quote = { page.quote },
                        chartMode = { page.chartMode },
                        chartPeriod = { page.chartPeriod },
                        crosshairIndex = { page.crosshairIndex },
                        selectedKLineIndex = { page.selectedKLineIndex },
                        watchlistHint = { page.watchlistHint },
                    )

                    // ---- 新闻弹幕 v2 + 摘要条 + 先览 + 快捷理由 chips（docs/43 D5 第二组件）----
                    DetailNewsTicker(
                        theme = page.theme,
                        reduceMotion = page.reduceMotion,
                        newsList = { page.newsList },
                        tapeOffset = { page.tapeOffset },
                        tapeLoopWidth = { page.tapeLoopWidth() },
                        newsSummary = { page.newsSummary },
                        onTapItem = { page.onNewsTapped(it) },
                        onLongPressItem = { item, pressX, pressY -> page.showTapePreview(item, pressX, pressY) },
                        onLongPressRelease = { page.scheduleTapePreviewDismiss() },
                        onAskAi = { page.askAboutNews(it) },
                        onOpenUrl = { target ->
                            page.detailOverlayCoordinator.closeSummary()
                            page.openUrl(target.url)
                        },
                        isNewsFlagged = { page.isNewsFlagged(it) },
                    )

                    // ---- 走势主卡（K线/分时 + 圈选 hint + 图表气泡）----
                    DetailChartCard(
                        theme = page.theme,
                        reduceMotion = page.reduceMotion,
                        entranceVisible = { page.entranceVisible },
                        revealIndex = 0,
                        ctx = ctx,
                        containerWidth = page.pagerData.pageViewWidth - 28f,
                        chartMode = { page.chartMode },
                        chartPeriod = { page.chartPeriod },
                        quote = { page.quote },
                        crosshairIndex = { page.crosshairIndex },
                        drawProgress = { page.drawProgress },
                        sonarDrift = { page.sonarDrift },
                        chartDataLoading = { page.chartDataLoading },
                        livePulse = { page.livePulse },
                        chartViewportCommand = { page.chartViewportCommand },
                        selectedKLineIndex = { page.selectedKLineIndex },
                        sonarPoints = { page.sonarPoints },
                        selectedSonarIndex = { page.selectedSonarIndex },
                        chartFlags = { page.chartFlags },
                        bandRange = { page.bandRange },
                        circleSelecting = { page.circleSelecting },
                        circleHintPresented = { page.circleHintPresented },
                        chartBubble = { page.chartBubble },
                        chartBubblePresented = { page.chartBubblePresented },
                        circleAiState = { page.circleAiState },
                        circleAiText = { page.circleAiText },
                        circleAiError = { page.circleAiError },
                        chartBubbleSourceLabel = { page.chartBubbleSourceLabel() },
                        bubbleOverlayActive = { page.detailOverlayCoordinator.active() == DetailOverlay.CHART_BUBBLE },
                        onChangeModePeriod = { m, p ->
                            page.chartMode = m
                            page.chartPeriod = p
                            page.detailChartCoordinator.resetChartSelection()
                        },
                        onIssueViewportCommand = { page.detailChartCoordinator.issueViewportCommand(it) },
                        onSelectKLineIndex = { page.detailChartCoordinator.selectKLineIndex(it) },
                        onScrub = { page.detailChartCoordinator.onScrub(it) },
                        onScrubPause = { page.detailChartCoordinator.onScrubPause(it) },
                        onScrubLeave = { page.detailChartCoordinator.clearPrefill() },
                        onScrubActive = { page.detailChartCoordinator.setInteractionActive(it) },
                        onZoomActive = { page.detailChartCoordinator.setInteractionActive(it) },
                        onCrosshairActive = { page.detailChartCoordinator.setInteractionActive(it) },
                        onTapSonar = { page.detailChartCoordinator.tapSonar(it) },
                        onCircleSelect = { s, e -> page.detailChartCoordinator.onCircleSelected(s, e) },
                        onSelectStateChange = { page.detailChartCoordinator.setCircleSelecting(it) },
                        onBlankTap = { page.closeChartBubble() },
                        onCloseChartBubble = { page.closeChartBubble() },
                        onOpenChatFromBubble = {
                            page.openChatWithQuestion(
                                page.chipStore.promptFragment() +
                                    "「${page.chartBubble}」帮我从资金面和消息面深聊${page.quote.name}这段走势。",
                                focusSymbol = page.symbol,
                            )
                        },
                        toneColor = { page.toneColor() },
                    )

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

                    // ---- AI 一行归因 + AI 解读块 + 句图联动（docs/43 D5 第四组件）----
                    DetailAiInsightBlock(
                        theme = page.theme,
                        reduceMotion = page.reduceMotion,
                        entranceVisible = { page.entranceVisible },
                        oneLineAttributionRevealIndex = 2,
                        insightBlockRevealIndex = 3,
                        quote = { page.quote },
                        aiRemoteState = { page.aiRemoteState },
                        aiRemoteText = { page.aiRemoteText },
                        aiRemoteError = { page.aiRemoteError },
                        aiRemoteModel = { page.aiRemoteModel },
                        aiRevealSource = { if (page.aiRevealSource.isEmpty()) aiSummary else page.aiRevealSource },
                        aiRevealLimit = { page.aiRevealLimit },
                        aiAwaitingFacts = { page.aiAwaitingFacts },
                        livePulse = { page.livePulse },
                        aiActionLabel = { page.aiActionLabel() },
                        insightSentences = { page.currentInsightSentences() },
                        selectedSentence = { page.selectedSentence },
                        oneLineAttributionText = { page.buildOneLineAttribution() },
                        onToggleAiInsight = { page.toggleAiInsight() },
                        onPickSentence = { page.pickSentence(it) },
                    )

                    // 公司介绍 / 公司数据 + 业务卡 + 公告研报（docs/43 D5 第五组件）----
                    DetailCompanyInfoSection(
                        theme = page.theme,
                        reduceMotion = page.reduceMotion,
                        entranceVisible = { page.entranceVisible },
                        tabPanelRevealIndex = 4,
                        disclosureRevealIndex = 9,
                        symbol = { page.symbol },
                        quote = { page.quote },
                        insight = { page.insight },
                        companyInfoTab = { page.companyInfoTab },
                        companyDataPresented = { page.companyDataPresented },
                        tabTrackWidth = (page.pagerData.pageViewWidth - 38f).coerceAtLeast(120f),
                        containerWidth = page.pagerData.pageViewWidth - 28f,
                        wide = wide,
                        ctx = ctx,
                        onSelectCompanyTab = { page.selectCompanyInfoTab(it) },
                        onToastHint = { page.toastHint(it) },
                        onShowDisclosurePeek = { page.detailOverlayCoordinator.showDisclosurePeek(it) },
                    )

                    // ---- 涨跌归因 × AI 走势推演（docs/43 D5 第六组件）----
                    DetailAttributionBoard(
                        theme = page.theme,
                        reduceMotion = page.reduceMotion,
                        entranceVisible = { page.entranceVisible },
                        revealIndex = 10,
                        quote = { page.quote },
                        mainFlow = { page.insight.fundFlow?.main },
                        containerWidth = page.pagerData.pageViewWidth - 28f,
                    )

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
                // U1 就地浮层（REASON_CHIPS + MORE_MENU + TAPE_PREVIEW + DISCLOSURE_PEEK）----
                DetailOverlays(
                    theme = page.theme,
                    reduceMotion = page.reduceMotion,
                    statusBarHeight = page.pagerData.statusBarHeight,
                    bottomInset = page.pagerData.safeAreaInsets.bottom,
                    pageViewWidth = page.pagerData.pageViewWidth,
                    overlayActive = { page.detailOverlayCoordinator.active() },
                    tapePreview = { page.tapePreview },
                    tapePreviewAnchorX = { page.tapePreviewAnchorX },
                    tapePreviewAnchorY = { page.tapePreviewAnchorY },
                    disclosurePeek = { page.disclosurePeek },
                    disclosurePeekVisible = { page.disclosurePeekVisible },
                    onCloseOverlay = { page.detailOverlayCoordinator.close() },
                    onPickQuickReason = { page.pickQuickReason(it) },
                    onRequestReasonChips = { page.detailOverlayCoordinator.request(DetailOverlay.REASON_CHIPS) },
                    onCopySymbolToPasteboard = { page.copySymbolToPasteboard() },
                    onToggleAiInsight = { page.toggleAiInsight() },
                    onTapPreviewItem = { page.onNewsTapped(it) },
                    onDismissDisclosurePeek = { page.detailOverlayCoordinator.dismissDisclosurePeek() },
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
