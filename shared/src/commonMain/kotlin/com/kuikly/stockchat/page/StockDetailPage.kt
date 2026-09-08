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
import com.kuikly.stockchat.cards.stock.MarketCardRenderers
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
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
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.provider.quoteLabel
// doc 29 集成：共享基建 + 板块组件（事件回调经这些基建接线）
import com.kuikly.stockchat.page.detail.AnchorIndex
import com.kuikly.stockchat.page.detail.AnomalyPoint
import com.kuikly.stockchat.page.detail.CardFootnote
import com.kuikly.stockchat.page.detail.ContextChip
import com.kuikly.stockchat.page.detail.ContextChipStore
import com.kuikly.stockchat.page.detail.DetailOverlay
import com.kuikly.stockchat.page.detail.Materiality
import com.kuikly.stockchat.page.detail.OverlayArbiter
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
import com.kuikly.stockchat.page.components.IndustryCompareOverlay
import com.kuikly.stockchat.page.components.MaterialityBadge
import com.kuikly.stockchat.page.components.QuickReasonChips
import com.kuikly.stockchat.page.components.RevisitCard
import com.kuikly.stockchat.page.components.detailTimelineSeries
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.page.components.AtmosphereBackdrop
import com.kuikly.stockchat.page.components.DetailTimelineChart
import com.kuikly.stockchat.page.components.NewsTape
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
    // ---- doc 26 新增状态：氛围/自绘分时/新闻弹幕 ----
    private var crosshairIndex: Int by observable(-1)
    private var drawProgress: Float by observable(0f)
    private var drawVersion = 0
    private var newsList: List<NewsItem> by observable(emptyList())
    private var newsSummary: NewsItem? by observable(null)
    private var insight: StockInsightBundle by observable(OfflineMarketInsightProvider().stock("600519.SH"))
    private val reduceMotion by lazy { platformPrefersReducedMotion() }
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    // ---- doc 29 集成状态：基建 + 13 个交互（U1 全部经 overlayArbiter 仲裁） ----
    private val overlayArbiter = OverlayArbiter()
    private val chipStore = ContextChipStore()
    private var sonarPoints: List<AnomalyPoint> by observable(emptyList())      // ④ 异动声呐
    private var selectedSonarIndex: Int by observable(-1)                       // ④ 选中声呐点
    private var chartBubble: String by observable("")                           // ④/① 就地气泡文案
    private var chartBubblePresented: Boolean by observable(false)              // ④/① 气泡两帧入场（R4）
    private var circleSelecting: Boolean by observable(false)                   // ① 圈选态 hint
    private var circleHintPresented: Boolean by observable(false)               // ① hint 两帧入场（R4）
    private var prefillQuestion: String by observable("")                       // ⑤ scrub 停顿预填
    private var chartFlags: List<ChartFlag> by observable(emptyList())          // B2 图侧新闻旗标
    private var bandRange: Triple<Int, Int, Boolean>? by observable(null)       // ②/B2 区间高亮带 (start,end,fromSentence)
    private var selectedSentence: Int by observable(-1)                         // ② 选中的解读句
    private var tapePreview: NewsItem? by observable(null)                      // B1 长按先览
    private var tapePreviewVersion = 0                                          // B1 先览消失计时 revision
    private var revisitExpanded: Boolean by observable(false)                   // A1 回访卡展开
    private var watchlistEntryVersion: Int by observable(0)                     // A1 自选条目变更重建键
    private var reasonChipsVisible: Boolean by observable(false)                // H1 快捷理由 chips
    private var hintVersion = 0                                                 // watchlistHint toast 计时 revision
    // ② 句图联动固定锚点（端侧模板句槽位，不让 LLM 猜坐标）
    private val sentenceAnchors = listOf(14 to 60, 140 to 162, 170 to 196)
    // B1/B2 已落旗新闻（点按收起用）
    private var droppedNewsIds = mutableSetOf<String>()

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
        // 新闻弹幕带：东财个股资讯（失败回 Mock 由 Fallback 链外置；此处空列表=整条隐藏）
        dependencies.stockNewsProvider.stockNews(symbol) { items ->
            if (items.isNotEmpty() && newsList.isEmpty()) {
                newsList = items.take(12)
            }
        }
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
        startDrawOn()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        livePulseVersion++
        aiRevealVersion++
        drawVersion++
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
                // ---- 氛围底（doc 26 §3）：固定整屏层（原型 .atmo inset:0），垫在
                // Scroller 之下、不随内容滚动；满色段从屏幕最顶端开始（含顶栏背后），
                // 渐变按实测屏高在 58% 收束。切勿挪回 Scroller 内容内（负偏移挂法
                // 已被证明会整层失效，详见 AtmosphereBackdrop 注释）。
                AtmosphereBackdrop(
                    toneSoft = { page.toneSoftColor() },
                    pageColor = page.theme.page,
                )
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

                    // ---- Hero 行情（卡外价格行，直接铺在氛围底上） ----
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

                    // ---- A1 当初理由回访卡（doc 29 §4.1）：Hero 下、NewsTape 前 ----
                    // vbind 键含新闻头条与 insight：eventsSummary（今日上龙虎榜/近期动态）随数据刷新
                    vif({ page.watchlisted }) {
                        vbind({ page.watchlistEntryVersion to page.watchlisted to (page.newsList.firstOrNull()?.id ?: "") }) {
                            val entry = page.watchlistStore.list().firstOrNull { it.symbol == page.symbol }
                            RevisitCard(
                                theme = page.theme,
                                expanded = { page.revisitExpanded },
                                onToggle = { page.revisitExpanded = !page.revisitExpanded },
                                entryTimeLabel = page.formatEntryTime(entry?.addedAtMillis ?: 0L),
                                reason = entry?.reason.orEmpty(),
                                entryPrice = entry?.entryPrice ?: 0.0,
                                currentPrice = { page.quote.price },
                                maxDrawdownPct = { page.periodMaxDrawdownPct() },
                                eventsSummary = page.revisitEventsSummary(),
                                statusText = { page.revisitStatus(entry?.entryPrice ?: 0.0).first },
                                statusPositive = { page.revisitStatus(entry?.entryPrice ?: 0.0).second },
                                reduceMotion = page.reduceMotion,
                            )
                        }
                    }

                    // ---- 新闻弹幕带（原型 .tape 对齐）：卡容器 + 横滚胶囊流 + 卡内摘要条 ----
                    NewsTape(
                        theme = page.theme,
                        items = { page.newsList },
                        selected = { page.newsSummary },
                        // B1 情绪点 + 摘要头「利好/利空」：端侧词典打分，涨红跌绿（U2）
                        sentimentOf = { item -> scoreNewsSentiment(item.title).isPositive },
                        onTapItem = { page.onNewsTapped(it) },
                        // B1 长按先览（U5 400ms）：TAPE_PREVIEW 层，松手 700ms 后消失（5s 兜底）
                        onLongPressItem = { page.showTapePreview(it) },
                        onLongPressRelease = { page.scheduleTapePreviewDismiss() },
                        onAskAi = { page.askAboutNews(it) },
                        onOpenUrl = { news ->
                            page.newsSummary = null
                            page.overlayArbiter.close()
                            page.openUrl(news.url)
                        },
                    )

                    // ---- B1 先览小卡（弹幕带下方，U1 仲裁 + 2s 自动消失） ----
                    vif({ page.overlayArbiter.active == DetailOverlay.TAPE_PREVIEW && page.tapePreview != null }) {
                        vbind({ page.tapePreview?.id ?: "" }) {
                            val preview = page.tapePreview
                            if (preview != null) {
                                View {
                                    attr {
                                        marginTop(8f)
                                        padding(10f)
                                        borderRadius(14f)
                                        backgroundColor(page.theme.surface)
                                        border(Border(1f, BorderStyle.SOLID, page.theme.brand.opacity(0.5f)))
                                        boxShadow(BoxShadow(0f, 4f, 14f, page.theme.brand.opacity(0.12f)))
                                    }
                                    Text {
                                        attr {
                                            text(preview.title)
                                            fontSize(page.theme.type.label)
                                            fontWeightSemiBold()
                                            color(page.theme.textPrimary)
                                            lineHeight(16f)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text((preview.summary.ifEmpty { preview.title }).take(30))
                                            marginTop(4f)
                                            fontSize(page.theme.type.meta)
                                            color(page.theme.textSecondary)
                                            lineHeight(15f)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text("点按查看行情反应 · 端侧规则")
                                            marginTop(4f)
                                            fontSize(page.theme.type.meta)
                                            color(page.theme.brand)
                                        }
                                    }
                                    event { click { page.onNewsTapped(preview) } }
                                }
                            }
                        }
                    }

                    // ---- 走势主卡（玻璃，页面第一实体）：分时 340px 自绘 + 蒙层 chips ----
                    // 入场动效：欢迎引导卡同款上滑淡入（RevealBlock，index 0 起阶梯）
                    RevealBlock(0, { page.entranceVisible }, page.reduceMotion) {
                    View {
                        attr {
                            marginTop(page.theme.spacing.lg)
                            borderRadius(20f)
                            overflow(true)
                            boxShadow(BoxShadow(0f, 8f, 24f, page.theme.textPrimary.opacity(0.08f)))
                        }
                        GlassBackdrop(page.theme.glass.peek, page.hostGlassRenderer)
                        View {
                            attr { padding(12f) }
                            // 卡头：分段控件 + 图例（价格/均价/昨收）
                            View {
                                attr { flexDirectionRow(); alignItemsCenter() }
                                View {
                                    attr { flex(1f); marginRight(10f) }
                                    ChartSegment(page.theme, { page.chartMode }, { page.chartPeriod }, page.reduceMotion) { m, p ->
                                        page.chartMode = m
                                        page.chartPeriod = p
                                        page.selectedKLineIndex = -1
                                        page.crosshairIndex = -1
                                    }
                                }
                                ChartLegend(page.theme, { page.toneColor() })
                            }
                            // 分时主体（自绘）与 K 线互斥切换
                            vif({ page.chartMode == StockChartMode.TIMELINE }) {
                                DetailTimelineChart(
                                    theme = page.theme,
                                    quote = { page.quote },
                                    crosshairIndex = { page.crosshairIndex },
                                    drawProgress = { page.drawProgress },
                                    pulse = { page.livePulse },
                                    reduceMotion = page.reduceMotion,
                                    containerWidth = page.pagerData.pageViewWidth - 28f - 24f,
                                    // ⑤ 恢复 scrub 即清空预填（只预填不发送）
                                    onScrub = {
                                        page.crosshairIndex = it
                                        page.prefillQuestion = ""
                                    },
                                    // ④ 异动声呐
                                    sonarIndices = { page.sonarPoints.map { p -> p.index } },
                                    selectedSonarIndex = { page.selectedSonarIndex },
                                    onSonarTap = { page.tapSonar(it) },
                                    // B2 新闻旗标（图侧）
                                    flags = { page.chartFlags },
                                    // ② 句图联动 / B2 区间高亮带（同容器，后者覆盖前者）
                                    band = { page.bandRange },
                                    // ① 圈选即问
                                    onCircleSelect = { s, e -> page.onCircleSelected(s, e) },
                                    onSelectStateChange = { selecting ->
                                        page.circleSelecting = selecting
                                        if (selecting) {
                                            page.circleHintPresented = false
                                            setTimeout(0) { page.circleHintPresented = true }
                                        }
                                    },
                                    // ⑤ 十字线停顿 600ms 预填
                                    onScrubPause = { page.makePrefill(it) },
                                    // ⑤ 松手离开 scrub：2s 后清预填（chart 侧 revision 去抖）
                                    onScrubLeave = { page.prefillQuestion = "" },
                                    // U1 点空白（非声呐轻点）：关闭图表气泡并清其区间带
                                    onBlankTap = { page.closeChartBubble() },
                                )
                            }
                            vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.DAY }) {
                                KLineChart(this, StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.DAY), ctx, page.selectedKLineIndex) { page.selectedKLineIndex = it }
                            }
                            vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.WEEK }) {
                                KLineChart(this, StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.WEEK), ctx, page.selectedKLineIndex) { page.selectedKLineIndex = it }
                            }
                            vif({ page.chartMode == StockChartMode.K_LINE && page.chartPeriod == StockChartPeriod.MONTH }) {
                                KLineChart(this, StockChartCardModel(page.quote, StockChartMode.K_LINE, StockChartPeriod.MONTH), ctx, page.selectedKLineIndex) { page.selectedKLineIndex = it }
                            }
                        }
                        // 蒙层 chip 条：叠卡底（压量能带下沿），值按相对昨收看色
                        vif({ page.chartMode == StockChartMode.TIMELINE }) {
                            vbind({ page.quote }) {
                                View {
                                    attr {
                                        absolutePosition(left = 24f, right = 24f, bottom = 24f)
                                        height(30f)
                                        borderRadius(15f)
                                        backgroundColor(page.theme.marketGlass)
                                        border(Border(1f, BorderStyle.SOLID, page.theme.marketGlassEdge))
                                        flexDirectionRow()
                                        alignItemsCenter()
                                        touchEnable(false)
                                    }
                                    listOf(
                                        "今开" to Format.price(page.quote.open) to page.marketColor(page.quote.open),
                                        "最高" to Format.price(page.quote.high) to page.marketColor(page.quote.high),
                                        "最低" to Format.price(page.quote.low) to page.marketColor(page.quote.low),
                                        "换手" to "${Format.decimal(page.quote.turnoverRate, 2)}%" to page.theme.textSecondary,
                                    ).forEachIndexed { i, (pair, color) ->
                                        val (label, value) = pair
                                        View {
                                            attr {
                                                flex(1f)
                                                flexDirectionRow()
                                                allCenter()
                                            }
                                            Text {
                                                attr {
                                                    text(label)
                                                    fontSize(9f)
                                                    color(page.theme.textTertiary)
                                                }
                                            }
                                            Text {
                                                attr {
                                                    text(value)
                                                    marginLeft(3f)
                                                    fontSize(10f)
                                                    fontWeightMedium()
                                                    color(color)
                                                }
                                            }
                                        }
                                        if (i < 3) {
                                            View {
                                                attr {
                                                    width(0.5f)
                                                    height(14f)
                                                    backgroundColor(page.theme.divider)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // ① 圈选态 hint（vif + 两帧入场，R4）：进入圈选时出现、松手消失
                        vif({ page.circleSelecting && page.circleHintPresented }) {
                            Text {
                                attr {
                                    absolutePosition(left = 16f, top = 0f)
                                    text("圈选中：拖动选择区间，松手看统计")
                                    fontSize(10f)
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
                        vif({ page.overlayArbiter.active == DetailOverlay.CHART_BUBBLE && page.chartBubble.isNotEmpty() }) {
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
                                            text("AI · 端侧规则")
                                            fontSize(9f)
                                            fontWeightSemiBold()
                                            color(page.theme.brand)
                                            flex(1f)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text("×")
                                            fontSize(12f)
                                            color(page.theme.textTertiary)
                                        }
                                    }
                                    event { click { page.closeChartBubble() } }
                                }
                                Text {
                                    attr {
                                        text(page.chartBubble)
                                        marginTop(6f)
                                        fontSize(11.5f)
                                        lineHeight(17f)
                                        color(page.theme.textPrimary)
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
                                            fontSize(11f)
                                            fontWeightSemiBold()
                                            color(page.theme.brand)
                                        }
                                    }
                                    event {
                                        click {
                                            page.openChatWithQuestion(
                                                page.chipStore.promptFragment() +
                                                    "「${page.chartBubble}」帮我从资金面和消息面深聊这段走势。"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }

                    // ---- 次级指标：单行 4 项（原型对齐：换手率/主力资金/成交量/总市值） ----
                    // 悬浮数据板：白 surface + 主页同款浮起阴影（AppChrome 按钮
                    // 0/6/18 黑 14%）+ 0.5 极细描边，从页面底色上"浮"出来。
                    RevealBlock(1, { page.entranceVisible }, page.reduceMotion) {
                    View {
                        attr {
                            marginTop(page.theme.spacing.md)
                            backgroundColor(page.theme.surface)
                            borderRadius(page.theme.cardRadius)
                            border(Border(0.5f, BorderStyle.SOLID, Color(0x000000, 0.05f)))
                            boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.14f)))
                            paddingTop(10f); paddingBottom(10f)
                            paddingLeft(14f); paddingRight(14f)
                        }
                        // 主力资金取自 insight.fundFlow → 外层 vbind(insight)：insight 加载后整行重建（R1）。
                        // 涨红/跌绿遵循 A 股配色约定。
                        vbind({ page.insight }) {
                            val main = page.insight.fundFlow?.main
                            vbind({ page.quote }) {
                                SecondaryMetricRow(
                                    listOf(
                                        DetailMetric("换手率", Format.decimal(page.quote.turnoverRate, 2) + "%"),
                                        DetailMetric(
                                            "主力资金",
                                            if (main == null) "--" else Format.compactAmount(main),
                                            valueColor = when {
                                                main == null -> null
                                                main > 0 -> page.theme.rise
                                                else -> page.theme.fall
                                            },
                                        ),
                                        DetailMetric("成交量", Format.compactAmount(page.quote.volume)),
                                        DetailMetric("总市值", Format.compactAmount(page.quote.marketCap)),
                                    ),
                                    page.theme,
                                    onGrabCell = { page.grabMetric(it.label, it.value) },
                                )
                            }
                        }
                    }
                    }

                    // 指标合规小字：紧跟指标行（提示对象即指标，原型外补充的合规文案）
                    Text {
                        attr {
                            text("指标要结合行业、增长与盈利质量一起看，单个数值不构成结论。")
                            marginTop(page.theme.spacing.sm)
                            fontSize(page.theme.type.label)
                            lineHeight(17f)
                            color(page.theme.textTertiary)
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
                                    fontSize(11.5f)
                                    lineHeight(17f)
                                    color(page.theme.textSecondary)
                                }
                            }
                        }
                    }
                    }

                    // ---- AI 解读（原型叙事位：紧贴一行归因，先给解读再看业务数据）----
                    // ② 句图联动：点句子 → 走势图区间带高亮
                    RevealBlock(3, { page.entranceVisible }, page.reduceMotion) {
                        SectionLabel("AI 解读 · 点句子，图会亮", page.theme)
                        vbind({ page.aiRevealLimit to page.aiRevealSource }) {
                            AiInsightBlock(
                                summary = { if (page.aiRevealSource.isEmpty()) aiSummary else page.aiRevealSource },
                                revealLimit = { page.aiRevealLimit },
                                retryActive = { page.aiRetryActive },
                                theme = page.theme,
                                reduceMotion = page.reduceMotion,
                                onRetry = { page.retryAiReveal() },
                                selectedSentence = { page.selectedSentence },
                                onPickSentence = { page.pickSentence(it) },
                            )
                        }
                    }

                    // ---- 真实业务数据：宽屏进入两列 Bento，窄屏保持线性阅读 ----
                    // vbind({insight})：E1 置顶/E2 注脚/卡片模型都在闭包内实时重算，
                    // insight 加载完成（离线→真实）时整块重建，不再停留在首帧快照。
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
                        BusinessInsightGrid(
                            items = businessCards,
                            context = ctx,
                            theme = page.theme,
                            wide = wide,
                            baseIndex = 4,
                            entranceVisible = { page.entranceVisible },
                            reduceMotion = page.reduceMotion,
                            // E2 注脚点击 → 展示判定依据（U3 两步溯源）
                            onFootnoteClick = { note -> page.toastHint("${note.rationale} · 端侧规则") },
                        )
                    }

                    RevealBlock(9, { page.entranceVisible }, page.reduceMotion) {
                        SectionLabel("公告与研报", page.theme)
                        // vbind({insight})：公告列表随 insight 加载重建（R1：builder 闭包不追踪 observable）
                        vbind({ page.insight }) {
                            // F1 公告要点 · 端侧评级（前 3 条，标题+徽章+日期，点击条目看判定依据）
                            DisclosureMaterialityBlock(
                                items = page.insight.disclosures.take(3),
                                theme = page.theme,
                            ) { title ->
                                page.toastHint(materialityOf(title).rule)
                            }
                            CardShell(DisclosureCardModel(page.insight.disclosures, "disclosures:${page.symbol}"), ctx)
                        }

                        // ---- F3 多空观点光谱：并入公告与研报同节（原型 .balance 在 ann-card 内） ----
                        Text {
                            attr {
                                text("研报评级光谱 · 示例 · 演示数据")
                                marginTop(10f)
                                fontSize(page.theme.type.meta)
                                fontWeightSemiBold()
                                color(page.theme.textTertiary)
                            }
                        }
                        BalanceSpectrumBlock(
                            theme = page.theme,
                            segments = listOf(
                                BalanceSegment("买入", 4, page.theme.rise, "示例 · 演示数据：偏多观点的占位引用，仅用于展示评级光谱交互，不构成任何建议。"),
                                BalanceSegment("增持", 3, page.theme.rise.opacity(0.55f), "示例 · 演示数据：谨慎看多的占位引用，观点切换仅为形态演示。"),
                                BalanceSegment("中性", 2, page.theme.textTertiary, "示例 · 演示数据：中性观点的占位引用，等待更多数据验证。"),
                                BalanceSegment("减持", 1, page.theme.fall, "示例 · 演示数据：偏空观点的占位引用，仅展示光谱另一端。"),
                            ),
                            initialIndex = 0,
                            containerWidth = page.pagerData.pageViewWidth - 28f,
                            reduceMotion = page.reduceMotion,
                        )
                    }

                    // ---- 涨跌归因 · 因子权重重放（原型 G1：因子列表在上、重放卡在下） ----
                    RevealBlock(10, { page.entranceVisible }, page.reduceMotion) {
                        SectionLabel("涨跌归因 · 因子权重重放", page.theme)
                        AttributionBlock(
                            AttributionCardModel(page.quote, attribution.direction, attribution.factors),
                            page.theme,
                            expandedKey = { page.expandedAttributionKey },
                            // rising 走 lambda：行情方向翻转后标题随 attr 重跑刷新（builder 快照问题）
                            rising = { page.quote.rising },
                            reduceMotion = page.reduceMotion,
                        ) { key ->
                            page.expandedAttributionKey = if (page.expandedAttributionKey == key) "" else key
                        }

                        // 因子基准值为示例 · 演示数据（诚实标注：重算数学真实、输入是演示样本）
                        Text {
                            attr {
                                text("因子基准值为示例 · 演示数据，重算为真实数学，非预测")
                                marginTop(8f)
                                fontSize(page.theme.type.meta)
                                color(page.theme.textTertiary)
                            }
                        }
                        FactorReplayBlock(
                            theme = page.theme,
                            factors = listOf(
                                FactorSpec("资金面", -0.30),
                                FactorSpec("板块联动", -0.14),
                                FactorSpec("市场整体", 0.05),
                                FactorSpec("个股事件", -0.23),
                            ),
                            actualPct = { page.quote.changePercent },
                            containerWidth = page.pagerData.pageViewWidth - 28f,
                            reduceMotion = page.reduceMotion,
                        )
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
                        // H1：未自选「＋」→ 快捷理由 chips（不直接 add）；已自选「✓」行为不变（移除）
                        (if (page.watchlisted) "✓" else "+") to {
                            if (page.watchlisted) {
                                page.toggleWatchlist()
                            } else {
                                page.overlayArbiter.request(DetailOverlay.REASON_CHIPS)
                            }
                        },
                        "⋯" to { page.toastHint("更多操作稍后接入") },
                    ),
                )
                // U1 点空白全关：REASON_CHIPS 层的透明遮罩（开新层自动被仲裁器切换）
                vif({ page.overlayArbiter.active == DetailOverlay.REASON_CHIPS }) {
                    View {
                        attr { absolutePositionAllZero(); touchEnable(true) }
                        event { click { page.overlayArbiter.close() } }
                    }
                }
                // H1 快捷理由 chips：底栏上方浮出（U1 仲裁层）
                vif({ page.overlayArbiter.active == DetailOverlay.REASON_CHIPS }) {
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
                DetailBottomBar(
                    theme = page.theme,
                    renderer = page.hostGlassRenderer,
                    bottomInset = page.pagerData.safeAreaInsets.bottom,
                    watchlisted = { page.watchlisted },
                    feedback = { page.watchlistFeedback },
                    reduceMotion = page.reduceMotion,
                    onToggleWatchlist = { page.toggleWatchlist() },
                    onBackToChat = { page.closePage() },
                    // ③「问 AI」：问题 = 抓取上下文片段 +（停顿预填 或 默认解读问句）
                    onAskAi = {
                        val base = page.prefillQuestion.ifEmpty { page.askAiQuestion() }
                        page.openChatWithQuestion(page.chipStore.promptFragment() + base)
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

    private fun toggleWatchlist() {
        if (watchlisted) {
            watchlistStore.remove(symbol)
            watchlisted = false
            toastHint("已从自选移除")
            watchlistEntryVersion++
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
                watchlistEntryVersion++
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

    /** ④ 声呐点轻点：就地气泡（U1 经仲裁器）+ 选中态 + 相关区间高亮带（doc §4.4）。 */
    private fun tapSonar(index: Int) {
        val point = sonarPoints.firstOrNull { it.index == index } ?: return
        selectedSonarIndex = index
        bandRange = Triple(
            (index - 10).coerceAtLeast(0),
            (index + 10).coerceAtMost(AnchorIndex.INDEX_COUNT - 1),
            false,
        )
        showChartBubble(point.label)
    }

    /** ④/① 就地气泡统一入口：仲裁器切换 + R4 两帧入场翻转。 */
    private fun showChartBubble(text: String) {
        chartBubble = text
        overlayArbiter.request(DetailOverlay.CHART_BUBBLE)
        chartBubblePresented = false
        setTimeout(0) { chartBubblePresented = true }
    }

    /**
     * U1「点空白全关」：关闭图表气泡并清掉它挂的区间带（sonar/圈选带）。
     * ② 句图带若在气泡开启期间被覆盖，收起后重新点句即可恢复。
     */
    private fun closeChartBubble() {
        if (overlayArbiter.active == DetailOverlay.CHART_BUBBLE) {
            overlayArbiter.close()
            bandRange = null
        }
    }

    /** ① 圈选松手：区间起止价、涨跌幅、极值全部端侧统计（纯事实）。 */
    private fun onCircleSelected(start: Int, end: Int) {
        val series = detailTimelineSeries(quote)
        if (series.size < 2) return
        val lo = minOf(start, end).coerceIn(0, series.lastIndex)
        val hi = maxOf(start, end).coerceIn(0, series.lastIndex)
        if (hi - lo < 3) {
            toastHint("区间太短（不足 3 个点），松手前多拖一段")
            return
        }
        val p0 = series[lo]
        val p1 = series[hi]
        val pct = if (p0 != 0.0) (p1 - p0) / p0 * 100.0 else 0.0
        val seg = series.subList(lo, hi + 1)
        // ① 松手后保留区间带高亮（brand 12%），随气泡关闭一起清除
        bandRange = Triple(lo, hi, false)
        showChartBubble(
            "${AnchorIndex.indexToTimeLabel(lo)}–${AnchorIndex.indexToTimeLabel(hi)} " +
                "区间${if (pct >= 0) "上行" else "下行"} ${Format.percent(pct)}，" +
                "区间极值 ${Format.price(seg.min())}–${Format.price(seg.max())}",
        )
    }

    /** ⑤ scrub 停顿 600ms：只预填不发送；恢复滑动（onScrub）即清空；松手 2s 后清除。 */
    private fun makePrefill(index: Int) {
        val series = detailTimelineSeries(quote)
        if (index !in series.indices) return
        val base = series.getOrNull((index - 4).coerceAtLeast(0)) ?: return
        val pct = if (base != 0.0) (series[index] - base) / base * 100.0 else 0.0
        // 措辞按走势方向模板生成（doc §4.6 验收：涨/跌/横盘三模板），均为可陈述事实问法
        prefillQuestion = when {
            pct > 0.15 -> "${AnchorIndex.indexToTimeLabel(index)} 前后这波涨是怎么回事？"
            pct < -0.15 -> "${AnchorIndex.indexToTimeLabel(index)} 前后这波跌是怎么回事？"
            else -> "${AnchorIndex.indexToTimeLabel(index)} 前后这段横盘是怎么回事？"
        }
    }

    /** ③ 指标长按抓取：chip 直接入上下文（降级路径），去重由 chipStore 负责。 */
    private fun grabMetric(label: String, value: String) {
        val added = chipStore.add(ContextChip(label, label, value))
        toastHint(
            if (added) "已抓取「$label」，问 AI 时会一并带上"
            else "「$label」已在提问上下文中"
        )
    }

    /** B2 旗标落点：优先用新闻真实发布时间换算分时索引；无时间字段时退关键词静态映射。 */
    private fun newsFlagIndex(item: NewsItem): Int? {
        if (item.time.length >= 16) {
            AnchorIndex.timeStringToIndex(item.time.substring(11, 16))?.let { return it }
        }
        return when {
            listOf("北向", "早盘").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("09:47")
            listOf("半年报", "中报").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("11:02")
            listOf("批价", "渠道").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("13:35")
            listOf("龙虎榜", "席位").any { item.title.contains(it) } -> AnchorIndex.timeStringToIndex("14:06")
            else -> null
        }
    }

    /** B2 点按条目：落旗（重复点按收起）+ 区间高亮带（12 点宽，圈选/句图同容器互斥）。 */
    private fun toggleNewsFlag(item: NewsItem) {
        val idx = newsFlagIndex(item)
        if (idx == null) {
            toastHint("该条新闻不在今日分时时段内，未落旗")
            return
        }
        if (item.id in droppedNewsIds) {
            droppedNewsIds.remove(item.id)
            chartFlags = chartFlags.filterNot { it.index == idx }
            if (bandRange?.first == idx) bandRange = null
            return
        }
        droppedNewsIds.add(item.id)
        // 旗色/标签 = 发布后 1h 真实走势（序列不足 1h 则用至今），只述事实不写因果
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
        chartFlags = chartFlags.filterNot { it.index == idx } + ChartFlag(idx, isPositive, label, dropped = true)
        bandRange = Triple(idx, (idx + 12).coerceAtMost(AnchorIndex.INDEX_COUNT - 1), false)
    }

    /**
     * B1/B2 弹幕条目点按（doc §4.3「点按条目或旗落旗，重复点按收起」）：
     * 未落旗 → 摘要条（NEWS_SUMMARY，弹幕带下方）+ 落旗；已落旗 → 收旗收条。
     */
    private fun onNewsTapped(item: NewsItem) {
        val idx = newsFlagIndex(item)
        if (item.id in droppedNewsIds) {
            droppedNewsIds.remove(item.id)
            if (idx != null) {
                chartFlags = chartFlags.filterNot { it.index == idx }
                if (bandRange?.first == idx) bandRange = null
            }
            if (newsSummary?.id == item.id) {
                newsSummary = null
                overlayArbiter.close()
            }
            return
        }
        newsSummary = item
        overlayArbiter.request(DetailOverlay.NEWS_SUMMARY)
        toggleNewsFlag(item)
    }

    /**
     * B1 长按先览（doc §4.2）：TAPE_PREVIEW 层；气泡在「松手 700ms 后」消失
     * （由 onLongPressRelease 调度），另留 5s 兜底防松手回调丢失。
     */
    private fun showTapePreview(item: NewsItem) {
        tapePreview = item
        overlayArbiter.request(DetailOverlay.TAPE_PREVIEW)
        val version = ++tapePreviewVersion
        setTimeout(5000) {
            if (version == tapePreviewVersion && overlayArbiter.active == DetailOverlay.TAPE_PREVIEW) {
                overlayArbiter.close()
                tapePreview = null
            }
        }
    }

    /** B1 松手 700ms 后收先览（doc §4.2「气泡消失：松手 700ms 后或点按条目」）。 */
    private fun scheduleTapePreviewDismiss() {
        val version = ++tapePreviewVersion
        setTimeout(700) {
            if (version == tapePreviewVersion && overlayArbiter.active == DetailOverlay.TAPE_PREVIEW) {
                overlayArbiter.close()
                tapePreview = null
            }
        }
    }

    /** B2 摘要条「问问 AI」：新闻标题作上下文带入对话（问法为事实型：是什么意思）。 */
    private fun askAboutNews(news: NewsItem) {
        overlayArbiter.close()
        newsSummary = null
        openChatWithQuestion(chipStore.promptFragment() + "「${news.title}」这条新闻是什么意思？")
    }

    /** H1 快捷理由：写入自选 + 理由 + 当时价，展开 A1 回访卡（doc §4.13 验收链路）。 */
    private fun pickQuickReason(reason: String) {
        overlayArbiter.close()
        val result = watchlistStore.add(symbol, quote.name)
        if (result == WatchlistAddResult.FULL) {
            toastHint("自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧")
            return
        }
        if (result == WatchlistAddResult.ADDED) watchlistStore.setEntryPrice(symbol, quote.price)
        watchlistStore.setReason(symbol, reason)
        watchlisted = true
        watchlistEntryVersion++
        revisitExpanded = true
        toastHint("已记入当初理由")
    }

    /** ② 点解读句：固定锚点亮区间带；同句再点收起。 */
    private fun pickSentence(index: Int) {
        if (selectedSentence == index) {
            selectedSentence = -1
            bandRange = null
            return
        }
        val anchor = sentenceAnchors.getOrNull(index) ?: return
        selectedSentence = index
        bandRange = Triple(anchor.first, anchor.second, true)
    }

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

    private fun applyQuote(next: Quote) {
        val old = quote
        val changed = old.price != next.price || old.changePercent != next.changePercent
        previousPriceText = Format.price(old.price)
        previousPercentText = Format.percent(old.changePercent)
        previousChangeText = "${Format.signed(old.change)}  ${Format.percent(old.changePercent)}"
        tickerDirectionUp = next.price >= old.price
        quote = next
        // doc 29 ④ 异动声呐：分时到达后跑一次端侧检测（成交量暂不参与确认，见已知简化）。
        // 同屏 ≤3 点（doc §4.4 呼吸预算，U4），超出的按 |涨跌幅| 降序舍弃。
        sonarPoints = detectAnomalies(detailTimelineSeries(next), null).take(3)
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
            drawProgress = (drawProgress + 0.08f).coerceAtMost(1f)
            if (drawProgress < 1f) setTimeout(28) { tick() }
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

private data class DetailMetric(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
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

/**
 * 业务数据节头（原型 .sec-head）：标题 + 右侧 brand 提示。
 * E1 为自动置顶（无 FLIP 重放，见 doc 29 §9 有意偏差），提示用陈述文案、不做假按钮。
 */
private fun ViewContainer<*, *>.BusinessDataHeader(theme: StockChatTheme) {
    View {
        attr {
            marginTop(theme.spacing.x3)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text("业务数据")
                fontSize(theme.type.label)
                fontWeightSemiBold()
                color(theme.textTertiary)
                flex(1f)
            }
        }
        Text {
            attr {
                text("今日相关 · 自动置顶")
                fontSize(theme.type.meta)
                color(theme.brand)
            }
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
                fontSize(9f)
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
                        fontSize(9.5f)
                        color(theme.textTertiary)
                    }
                }
                Text {
                    attr {
                        text(item.value)
                        marginTop(2f)
                        fontSize(12f)
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
        BusinessDataHeader(theme)
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
        BusinessDataHeader(theme)
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
                        RevealBlock(baseIndex + rowIndex * 2 + columnIndex, entranceVisible, reduceMotion) {
                            BusinessCardSlot(item, theme, onFootnoteClick) {
                                SectionLabel(item.label, theme)
                                CardShell(item.model, context)
                            }
                        }
                    }
                }
                if (row.size == 1) View { attr { flex(1f); marginLeft(6f) } }
            }
        }
        return
    }
    BusinessDataHeader(theme)
    items.forEachIndexed { index, item ->
        RevealBlock(baseIndex + index, entranceVisible, reduceMotion) {
            BusinessCardSlot(item, theme, onFootnoteClick) {
                SectionLabel(item.label, theme)
                CardShell(item.model, context)
            }
        }
    }
}

/**
 * doc 29 E1/E2/E3 业务卡槽：置顶卡加 brand 描边 + 「今日相关」角标；
 * 有注脚的卡在卡底加 brand 小字（点击展示判定依据 + 「· 端侧规则」）；
 * 长按 400ms（U5）→ E3 行业对比覆盖层（只读），松手 2.2s 后弹回。
 */
private fun ViewContainer<*, *>.BusinessCardSlot(
    item: BusinessInsightItem,
    theme: StockChatTheme,
    onFootnoteClick: (CardFootnote) -> Unit,
    content: ViewContainer<*, *>.() -> Unit,
) {
    // E3 覆盖层状态（BlockState：observable 委托仅支持类成员，局部状态收敛）
    val compareShown = BlockState(false)
    var compareHideRevision = 0
    View {
        attr {
            if (item.pinned) {
                borderRadius(theme.cardRadius)
                border(Border(1.2f, BorderStyle.SOLID, theme.brand.opacity(0.5f)))
                padding(2f)
            }
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
        vif({ item.pinned }) {
            View {
                attr {
                    absolutePosition(top = -1f, right = 10f)
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
                        fontSize(9f)
                        fontWeightSemiBold()
                        color(theme.onBrand)
                    }
                }
            }
        }
        content()
        vif({ item.footnote != null }) {
            View {
                attr { marginTop(6f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("ⓘ ${item.footnote?.text} · 端侧规则")
                        fontSize(theme.type.meta)
                        color(theme.brand)
                    }
                }
                event { click { item.footnote?.let(onFootnoteClick) } }
            }
        }
        // E3 可发现性提示（静态样本标注与 F3/G1 同范式）
        Text {
            attr {
                marginTop(4f)
                text("长按看行业对比 · 示例 · 端侧静态样本")
                fontSize(theme.type.meta)
                color(theme.textTertiary)
            }
        }
        vif({ compareShown.value }) {
            IndustryCompareOverlay(theme, item.label)
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
        // 主栏（原 50f 玻璃条）
        View {
            attr {
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
    // doc 29 ② 句图联动：句序号与页面侧 sentenceAnchors 一一对应，点句点亮区间带
    selectedSentence: () -> Int = { -1 },
    onPickSentence: (Int) -> Unit = {},
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
                    // 首句（句 0）：此前无点击事件导致②句图联动整段不可达，补上
                    View {
                        attr { marginTop(theme.spacing.sm) }
                        Text {
                            attr {
                                text(sentences.first() + "。")
                                fontSize(theme.type.body)
                                fontWeightSemiBold()
                                color(if (selectedSentence() == 0) theme.brand else theme.textPrimary)
                                lineHeight(21f)
                            }
                        }
                        event { click { onPickSentence(0) } }
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
                                text(s + "。")
                                fontSize(theme.type.sm)
                                lineHeight(19f)
                                color(if (selectedSentence() == index + 1) theme.brand else theme.textSecondary)
                            }
                        }
                        // ② 句图联动：点句 → 页面点亮对应区间带（此前缺失，交互不可达）
                        event { click { onPickSentence(index + 1) } }
                    }
                }
            }
            // ② 句图联动可发现性提示（端侧规则，不占「问 AI」触点预算）
            Text {
                attr {
                    text("点句子可在走势图中高亮对应区间 · 端侧规则")
                    marginTop(8f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
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

/**
 * doc 29 F1 公告要点 · 端侧评级（公告与研报卡之前的摘要块）：
 * 前 3 条公告/研报（标题+徽章+日期），高重要度加粗；点击条目 toast 判定依据（U3）。
 * 徽章为三枚圆点：HIGH 三涨色 / MID 两橙 / LOW 一灰（DetailBoardBlocks.MaterialityBadge）。
 */
private fun ViewContainer<*, *>.DisclosureMaterialityBlock(
    items: List<DisclosureItem>,
    theme: StockChatTheme,
    onExplain: (String) -> Unit,
) {
    vif({ items.isNotEmpty() }) {
        View {
            attr {
                marginBottom(theme.spacing.md)
                padding(theme.spacing.md)
                borderRadius(theme.inputRadius)
                backgroundColor(theme.surfaceMuted)
                border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            }
            Text {
                attr {
                    text("公告要点 · 端侧评级")
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.textSecondary)
                }
            }
            items.forEach { item ->
                // 评级在 attr 闭包内实时求值（纯函数，无副作用）
                fun level() = materialityOf(item.title).level
                View {
                    attr {
                        marginTop(8f)
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
                            lineHeight(16f)
                        }
                    }
                    Text {
                        attr {
                            text("  ${item.date}")
                            fontSize(theme.type.meta)
                            color(theme.textTertiary)
                        }
                    }
                    event { click { onExplain(item.title) } }
                }
            }
            Text {
                attr {
                    text("评级为端侧关键词规则，点条目可看判定依据 · 端侧规则")
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
