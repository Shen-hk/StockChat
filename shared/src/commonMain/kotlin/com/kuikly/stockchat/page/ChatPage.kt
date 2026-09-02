package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.BridgeModule
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardAssembler
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CardEvent
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.SkeletonCardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.kuikly.stockchat.glass.GlassRenderingMode
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.ChatViewModel
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.StreamState
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.LocalAlertProvider
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.mock.MockQuoteProvider
import com.kuikly.stockchat.page.components.ChatDrawer
import com.kuikly.stockchat.page.components.CardSheetHost
import com.kuikly.stockchat.page.components.ActiveComparePanel
import com.kuikly.stockchat.page.components.ChatMessageActions
import com.kuikly.stockchat.page.components.ChatMessageRenderState
import com.kuikly.stockchat.page.components.ChatMessageView
import com.kuikly.stockchat.page.components.DateDivider
import com.kuikly.stockchat.page.components.RecentSymbolRow
import com.kuikly.stockchat.page.components.RegressionQuestionRow
import com.kuikly.stockchat.page.components.SubThreadState
import com.kuikly.stockchat.page.components.WelcomeSection
import com.kuikly.stockchat.page.components.ChatTopNav
import com.kuikly.stockchat.page.components.IslandGestureMotion
import com.kuikly.stockchat.page.components.IslandGesturePhase
import com.kuikly.stockchat.page.components.ISLAND_ANIMATION_CLOSE
import com.kuikly.stockchat.page.components.ISLAND_ANIMATION_DETAIL
import com.kuikly.stockchat.page.components.ISLAND_ANIMATION_RETURN
import com.kuikly.stockchat.page.components.LineIconPlus
import com.kuikly.stockchat.page.components.LineIconMicWithFill
import com.kuikly.stockchat.page.components.LineIconCamera
import com.kuikly.stockchat.page.components.LineIconPhoto
import com.kuikly.stockchat.page.components.LineIconSend
import com.kuikly.stockchat.page.components.LineIconStop
import com.kuikly.stockchat.page.components.VoiceBar
import com.kuikly.stockchat.voice.NativeBridgeVoiceRecorder
import com.kuikly.stockchat.voice.VoiceError
import com.kuikly.stockchat.voice.VoiceRecorder
import com.kuikly.stockchat.voice.VoiceState
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.BrokenCardBlock
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.SkeletonBlock
import com.kuikly.stockchat.protocol.SuggestionsIntent
import com.kuikly.stockchat.protocol.SymbolCardIntent
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.TextBlock
import com.kuikly.stockchat.richtext.EntityRichText
import com.kuikly.stockchat.richtext.EntityStreamingMarkdown
import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntitySpan
import com.kuikly.stockchat.richtext.EntityType
import com.kuikly.stockchat.richtext.EntityDropResolver
import com.kuikly.stockchat.richtext.EntityDropTarget
import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.AtCandidateProvider
import com.kuikly.stockchat.composer.ComposerCatalog
import com.kuikly.stockchat.composer.CommandExecution
import com.kuikly.stockchat.composer.CommandInvocationParser
import com.kuikly.stockchat.composer.CommandInvocation
import com.kuikly.stockchat.composer.CommandRegistry
import com.kuikly.stockchat.composer.ComposerEditingReducer
import com.kuikly.stockchat.composer.ComposerTextOperations
import com.kuikly.stockchat.composer.MentionEntity
import com.kuikly.stockchat.composer.MentionType
import com.kuikly.stockchat.composer.ParamType
import com.kuikly.stockchat.composer.SendPayload
import com.kuikly.stockchat.composer.SlashCommand
import com.kuikly.stockchat.composer.SolidTokenRegistry
import com.kuikly.stockchat.composer.TriggerDetector
import com.kuikly.stockchat.composer.TriggerSession
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.TextInputState
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.Timer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

@Page(Routes.CHAT, supportInLocal = true)
internal class ChatPage : BasePager() {
    // 输入栏诊断日志统一 tag。logcat 过滤：adb logcat -s KLog 或搜 "Composer"。
    private companion object {
        const val COMPOSER_LOG_TAG = "Composer"
    }
    private val dependencies by lazy { ChatDependencies.forPager(pagerId) }
    private val viewModel by lazy { ChatViewModel(pagerId, dependencies) }
    // 全页唯一、持久挂载的 TextArea。ref 只在首次 body 挂载前为空。
    private var inputRef: ViewRef<TextAreaView>? = null
    private var chatScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    private var chatContentHeight = 0f
    private var keepChatAtBottomVersion = 0
    private var peekSymbol: String by observable("")
    private var peekVisible: Boolean by observable(false)
    // Symbol whose long press was recognised but whose gesture has not ended.
    // It also suppresses the click some bridges emit after the terminal touch.
    private var pendingLongPressSymbol: String = ""
    private var draggedEntity: EntitySpan? = null
    private var entityDragActive: Boolean by observable(false)
    private var entityDragX: Float by observable(0f)
    private var entityDragY: Float by observable(0f)
    private var entityDragStartX = 0f
    private var entityDragStartY = 0f
    private var entityDragName: String by observable("")
    private var entityDropTarget: EntityDropTarget by observable(EntityDropTarget.NONE)
    // Sheet interaction is gated independently so its fading layer cannot
    // accept late taps while it is being dismissed.
    private var sheetInteractive: Boolean by observable(false)
    private var sheetPresentationVersion = 0
    private var ambiguousSymbols: ObservableList<String> by observableList()
    private var ambiguousEntityText: String by observable("")
    private var ambiguousAction: EntityAction by observable(EntityAction.PREVIEW)
    private var keyboardHeight: Float by observable(0f)
    private var drawerOpen: Boolean by observable(false)
    private var liveDataMode: Boolean by observable(true)
    // Dynamic island: the top title capsule morphs into a live quote card.
    private var islandExpanded: Boolean by observable(false)
    private var islandSymbol: String by observable("600519.SH")
    private var islandGestureMotion: IslandGestureMotion by observable(IslandGestureMotion())
    private var islandGestureStartY = 0f
    private var islandMotionRevision = 0
    private var islandDetailRouteActive = false
    private var islandDetailRouteResetVersion = 0
    private var islandMounted: Boolean by observable(true)
    private var islandWatchlisted: Boolean by observable(false)
    private var islandCompareLeftSymbol: String by observable("")
    private var islandCompareRightSymbol: String by observable("")
    private var islandCompareVisible: Boolean by observable(false)
    // Page data is injected after construction; use the safe fallback until created().
    private var glassMode: GlassRenderingMode by observable(GlassRenderingMode.SIMPLIFIED)
    private var glassModeManuallySelected = false
    private var inputPanel: InputPanel by observable(InputPanel.NONE)
    // ===== @ 提及与 / 指令状态机（规范见 docs/10-输入栏@提及与斜杠指令交互规范_v1.0.md） =====
    // 联想面板维度（与 MEDIA 面板正交）：@ 触发 / / 触发 / 命令参数槽位。
    private var assistantPanel: AssistantPanel by observable(AssistantPanel.NONE)
    // 活动触发会话；同一时刻最多一个（规范 §3.1）。
    private var triggerSession: TriggerSession? = null
    // 文本、选区、组合区必须作为一个原子快照流转，不允许分散字段被
    // textDidChange / selectionChange 交叉覆盖。
    private var composerEditingState = TextInputState("")
    // 拼音组合态：面板冻结显示"输入中…"（规范 §3.5）。
    private var triggerComposing: Boolean by observable(false)
    private var atCandidates: ObservableList<AtCandidate> by observableList()
    private var atHighlight: Int by observable(0)
    private var slashCandidates: ObservableList<SlashCommand> by observableList()
    private var slashHighlight: Int by observable(0)
    // 未知命令提示（规范 §5.6）：非空时面板显示"没有找到 /xxx"。
    private var slashUnknown: String by observable("")
    private var lastTrackedTriggerKey = ""
    private var lastTrackedUnknownSlash = ""
    // 固化提及注册表：只存实体顺序，激活态每次从文本扫描得出（规范 §4.6）。
    private val mentionEntities = mutableListOf<MentionEntity>()
    // 上下文标记（/深水区等），注入 system context（规范 §4.8）。
    private val deepContextNotes = mutableListOf<String>()
    private var deepContextVersion: Int by observable(0)
    private var commandValidationMessage: String by observable("")
    // / 命令参数态（规范 §5.4）。参数值不落字段，每次从输入文本实时解析，
    // 保证面板显示与最终发送用的是同一套解析结果。
    private var paramCommand: SlashCommand? by observable(null)
    // 最近提及（S1 数据源，最多 5 条，新的在前）。
    private val recentMentions = mutableListOf<String>()
    // Composer state machine (规范见 docs/09-输入栏默认态与输入态转换规范_v1.0.md).
    // 默认态 → 输入态由点击/聚焦/开面板触发；输入态是"粘"的：收起键盘不再回退，
    // 只有"键盘已收起时点击非输入栏区域"这一次点击才回到默认态。
    private var composerExpanded: Boolean by observable(false)
    // Kuikly Android 在输入框首次聚焦所触发的 Composer 展开布局提交期间，会让
    // RecyclerView 内的 EditText 短暂 clearFocus。恢复请求带版本号：任何用户主动
    // blur/collapse 都会使旧请求失效，防止延迟回调把焦点从其他区域抢回来。
    private var composerFocusRequestVersion = 0
    private var composerKeyboardLayoutVersion = 0
    private var composerFocusRecoveryPending = false
    // 用户进入文字输入态后锁住真实原生焦点。只有业务明确调用 blurComposer 才解锁；
    // RecyclerView/键盘避让布局造成的 inputBlur 都视为意外失焦并自动恢复。
    private var composerFocusLocked = false
    private var composerUnexpectedBlurVersion = 0
    // 键盘当前是否在屏上，由 TextArea 的 keyboardHeightChange 上报。
    private var keyboardVisible: Boolean by observable(false)
    // Voice input is the composer's third state (docs/11): hold to record,
    // release to transcribe/send, slide up to cancel.
    private var voiceState: VoiceState by observable(VoiceState.IDLE)
    private var voiceCancelArmed: Boolean by observable(false)
    private var voiceElapsedSec: Float by observable(0f)
    private var voiceAmps: FloatArray by observable(FloatArray(24) { 4f })
    private var voiceMicFill: Float by observable(0f)
    private var voiceTouchDownPageY = 0f
    private var voiceSourceExpanded = false
    private var voiceClockTimer: Timer? = null
    private var voiceSessionVersion = 0
    private val voiceRecorder: VoiceRecorder by lazy {
        NativeBridgeVoiceRecorder(acquireModule(BridgeModule.MODULE_NAME))
    }
    private var expandedCardKey: String by observable("")
    private var focusedCardKey: String by observable("")
    private var repairingCardKey: String by observable("")
    private var compareCandidateKey: String by observable("")
    private var compareCandidateSymbol: String by observable("")
    private var compareCard: StockCompareCardModel? by observable(null)
    private var compareInsightState: CompareInsightState by observable(CompareInsightState.IDLE)
    private var compareInsightText: String by observable("")
    private var compareInsightError: String by observable("")
    private var compareInsightPairKey = ""
    private var compareInsightVersion = 0
    private var sheetCard: CardModel? by observable(null)
    private var sheetMounted: Boolean by observable(false)
    private var sheetPresented: Boolean by observable(false)
    private var sheetLevel: SheetLevel by observable(SheetLevel.HALF)
    private var sheetPanStartY = 0f
    // Do not mount a modal during the native long-press gesture itself: its
    // full-screen scrim would swallow that gesture's terminal touch event.
    private var drilledKeys: ObservableList<String> by observableList()
    private var subThreads: ObservableList<SubThreadState> by observableList()
    private var suppressNextStockClickSymbol = ""
    private var peekVersion = 0
    private val quoteRepository get() = dependencies.quoteRepository
    private val watchlistStore get() = dependencies.watchlistStore
    private val alertStore get() = dependencies.alertStore
    private var alertPollGeneration = 0
    private val deliveredAlertBuckets = mutableSetOf<String>()
    private val mockQuoteProvider = MockQuoteProvider()
    private var quoteStates: ObservableList<ChatQuoteState> by observableList()
    private val requestedSymbols = mutableSetOf<String>()
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    /** The document caps simultaneously visible real-time blur surfaces at two. */
    private val glassRenderer: GlassRenderer
        get() {
            val overBudget = activeRealtimeGlassSurfaces() > 2
            val effectiveMode = if (glassMode == GlassRenderingMode.REALTIME && overBudget) {
                GlassRenderingMode.SNAPSHOT
            } else {
                glassMode
            }
            return GlassRenderer(effectiveMode)
        }

    private fun activeRealtimeGlassSurfaces(): Int = 2 + if (
        peekSymbol.isNotEmpty() || drawerOpen || sheetCard != null
    ) 1 else 0

    override fun hostGlassModeDidChange(renderer: GlassRenderer) {
        if (!glassModeManuallySelected) glassMode = renderer.mode
    }

    override fun created() {
        super.created()
        glassMode = hostGlassRenderer.mode
        StockCardRenderers.ensureRegistered()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        alertPollGeneration++
        // If the stock detail route is covering this page, JS state may
        // already read as idle while the native view is still waiting for the
        // collapsed-frame write. Force the write unconditionally.
        if (islandDetailRouteActive) {
            islandMounted = false
            forceIslandCollapsedForDetailRoute()
        }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        if (islandDetailRouteActive) scheduleIslandDetailReturnReset()
        viewModel.refreshConfigStatus()
        islandWatchlisted = watchlistStore.contains(islandSymbol)
        // Preload the island quote so the morph opens with data in place.
        requestQuote(islandSymbol)
        startAlertPolling()
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                ref { page.chatScrollerRef = it }
                attr {
                    flex(1f)
                    // Reserve resting space for the floating chrome while
                    // allowing content to travel underneath it as it scrolls.
                    paddingTop(page.pagerData.statusBarHeight + 52f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    paddingBottom(190f + page.pagerData.safeAreaInsets.bottom)
                }
                event {
                    contentSizeChanged { _, height ->
                        page.chatContentHeight = height
                        if (page.shouldKeepChatAtBottom()) page.scheduleScrollChatToBottom()
                    }
                    // 输入栏以外的任何点击都走这里（气泡/卡片上有自己的点击处理，
                    // 会把事件消费掉，不会冒泡上来）：这是输入态 → 默认态的唯一出口。
                    click { page.handleOutsideTap() }
                }
                vif({ page.viewModel.messages.isEmpty() }) {
                    WelcomeSection(page.theme) { question -> page.injectQuestion(question) }
                }
                vif({ page.viewModel.messages.isNotEmpty() }) {
                    DateDivider(page.theme)
                }
                vfor({ page.viewModel.messages }) { message ->
                    ChatMessageView(
                        message = message,
                        theme = page.theme,
                        contextSymbols = page.contextSymbolsBefore(message.id),
                        suggestionsActive = page.suggestionsAreActive(message),
                        understoodQuery = page.questionBefore(message.id),
                        state = ChatMessageRenderState(
                            repairingCardKey = page.repairingCardKey,
                            drilledKeys = page.drilledKeys.toSet(),
                            subThreads = page.subThreads.toList(),
                            focusedCardKey = page.focusedCardKey,
                            compareCandidateSymbol = page.compareCandidateSymbol,
                        ),
                        actions = ChatMessageActions(
                            onEntityStock = page::handleStockEntityClick,
                            onEntityStockLongPress = page::handleStockEntityLongPress,
                            onCardStock = page::openStockDetail,
                            onTerm = { page.viewModel.send("$it 是什么意思") },
                            onSuggestion = page.viewModel::send,
                            onRetry = page.viewModel::retryLast,
                            onRetryCard = page::retryCard,
                            onQuoteNeeded = page::requestQuote,
                            quoteFor = page::quoteFor,
                            isCardExpanded = { page.expandedCardKey == it },
                            onToggleCardExpanded = page::toggleCardExpanded,
                            onOpenCardSheet = page::openCardSheet,
                            onToggleDrill = page::toggleDrill,
                            onStartSubThread = page::startSubThread,
                            onToggleSubThread = page::toggleSubThread,
                            onUpdateSubThreadInput = page::updateSubThreadInput,
                            onSendSubThread = page::sendSubThread,
                            onFocusChanged = page::setFocusedCard,
                            onCompareCandidate = page::handleCompareCandidate,
                            onCardEvent = page::handleCardEvent,
                            onCorrectUnderstanding = page::beginUnderstandingCorrection,
                            onShareInterpretation = page::copyShareCard,
                        ),
                    )
                }
            }
            // Declare the overlay after its backdrop source so Android paints
            // the glass above the moving conversation rather than beneath it.
            ChatTopNav(
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                drawerOpen = page.drawerOpen,
                liveData = { page.liveDataMode },
                renderer = page.glassRenderer,
                contextTitle = if (page.drilledKeys.isNotEmpty()) "归因链 · 资金面 ›" else null,
                pageWidth = page.pagerData.pageViewWidth,
                pageHeight = page.pagerData.pageViewHeight,
                islandExpanded = { page.islandExpanded },
                islandMounted = { page.islandMounted },
                islandQuote = { page.quoteFor(page.islandSymbol) },
                islandGestureMotion = { page.islandGestureMotion },
                islandWatchlisted = { page.islandWatchlisted },
                islandDropActive = {
                    page.entityDragActive && page.entityDropTarget == EntityDropTarget.ISLAND
                },
                islandFirstCompareDrop = {
                    page.entityDragActive &&
                        page.entityDropTarget == EntityDropTarget.ISLAND &&
                        page.isIslandFirstCompareDrop()
                },
                islandCompareLeftSymbol = { page.islandCompareLeftSymbol },
                islandCompareRightSymbol = { page.islandCompareRightSymbol },
                islandCompareLeftQuote = { page.quoteFor(page.islandCompareLeftSymbol) },
                islandCompareRightQuote = { page.quoteFor(page.islandCompareRightSymbol) },
                islandCompareVisible = { page.isIslandCompareLobbyVisible() },
                islandTextOnly = { false },
                islandCompareInsightLoading = { page.compareInsightState == CompareInsightState.LOADING },
                islandCompareInsightAvailable = { page.compareInsightState == CompareInsightState.READY },
                onToggleIsland = { page.toggleIsland() },
                onIslandGesture = { state, y -> page.handleIslandGesture(state, y) },
                onIslandMotionComplete = { key -> page.completeIslandMotion(key) },
                onToggleIslandWatchlist = { symbol -> page.toggleIslandWatchlist(symbol) },
                onOpenIslandCompare = { page.openIslandComparePanel() },
                onClearIslandCompare = { page.clearIslandCompare() },
                onMenu = { page.drawerOpen = !page.drawerOpen },
                onNewChat = { page.startNewChat() },
            )
            vif({ page.ambiguousSymbols.isNotEmpty() }) {
                View {
                    attr {
                        marginLeft(12f)
                        marginRight(12f)
                        marginBottom(8f)
                        padding(10f)
                        backgroundColor(page.theme.brandSoft)
                        borderRadius(12f)
                    }
                    Text {
                        attr {
                            text("“${page.ambiguousEntityText}”可能指以下标的")
                            fontSize(12f)
                            fontWeightMedium()
                            color(page.theme.textPrimary)
                        }
                    }
                    Scroller {
                        attr { flexDirectionRow(); height(36f); marginTop(7f) }
                        page.ambiguousSymbols.forEach { symbol ->
                            val security = Securities.all.firstOrNull { it.symbol == symbol }
                            View {
                                attr {
                                    height(32f)
                                    marginRight(7f)
                                    paddingLeft(10f)
                                    paddingRight(10f)
                                    justifyContentCenter()
                                    borderRadius(9f)
                                    backgroundColor(page.theme.surface)
                                }
                                Text { attr { text(security?.name ?: symbol); fontSize(12f); color(page.theme.brand) } }
                                event { click { page.chooseAmbiguousSymbol(symbol) } }
                            }
                        }
                    }
                }
            }
            vif({ page.peekSymbol.isNotEmpty() }) {
                val quote = page.quoteFor(page.peekSymbol)
                View {
                    attr {
                        marginLeft(12f)
                        marginRight(12f)
                        marginBottom(8f)
                        padding(12f)
                        flexDirectionRow()
                        alignItemsCenter()
                        opacity(if (page.peekVisible) 1f else 0f)
                        transform(Translate(0f, if (page.peekVisible) 0f else 0.16f))
                        animate(Animation.easeOut(0.24f), page.peekVisible)
                    }
                    GlassBackdrop(page.theme.glass.peek, page.glassRenderer)
                    View {
                        attr { flex(1f) }
                        if (quote == null) Text { attr { text("正在获取 ${page.peekSymbol} 的行情…"); fontSize(12f); color(page.theme.textTertiary) } }
                        else CardShell(
                            StockQuoteCardModel(quote),
                            CardContext(page.theme, CardDensity.MINI, { page.openStockDetail(it) }, glass = page.glassRenderer),
                        )
                    }
                    View {
                        attr { padding(9f); borderRadius(9f); backgroundColor(page.theme.surfaceMuted) }
                        Text { attr { text("收起"); fontSize(11f); color(page.theme.textSecondary) } }
                        event { click { page.dismissPeek() } }
                    }
                    View {
                        attr { marginLeft(7f); padding(9f); borderRadius(9f); backgroundColor(page.theme.brand) }
                        Text { attr { text("看详情"); fontSize(11f); fontWeightMedium(); color(page.theme.onBrand) } }
                        event { click { page.openStockDetail(page.peekSymbol) } }
                    }
                }
            }
            vif({ page.compareCard != null }) {
                page.compareCard?.let { compareModel ->
                    ActiveComparePanel(
                        model = compareModel,
                        theme = page.theme,
                        insightLoading = { page.compareInsightState == CompareInsightState.LOADING },
                        insightText = { page.compareInsightText },
                        insightError = { page.compareInsightError },
                        onRetryInsight = { page.retryCompareInsight() },
                        onOpenStock = { page.openStockDetail(it) },
                        onClose = { page.clearCompare() },
                    )
                }
            }
            View {
                attr {
                    // Floating capsule composer on a solid page-coloured base:
                    // the blank area around/below the capsule no longer shows
                    // scrolled content through.  The extra 3dp top padding
                    // hosts a feather strip that softens the junction,
                    // mirroring the top chrome.
                    absolutePosition(bottom = 0f, left = 0f, right = 0f)
                    paddingTop(3f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    paddingBottom(10f + page.pagerData.safeAreaInsets.bottom + page.keyboardHeight)
                }
                // 吞掉落在输入栏自身范围内的点击（羽毛条/左右留白/底部留白），
                // 否则它们会穿透到聊天列表，被误判成"点击非输入栏区域"而收起输入栏。
                event { click { } }
                // 3dp feather at the top junction: content scrolling in from
                // above fades out instead of hitting a hard edge.
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f)
                        height(3f)
                        touchEnable(false)
                        backgroundLinearGradient(
                            Direction.TO_BOTTOM,
                            ColorStop(page.theme.page.opacity(0f), 0f),
                            ColorStop(page.theme.page, 1f),
                        )
                    }
                }
                // Solid base covering only the blank strip below the capsule,
                // down to the screen bottom. The capsule itself keeps its
                // glass backdrop.
                View {
                    attr {
                        absolutePosition(bottom = 0f, left = 0f, right = 0f)
                        height(10f + page.pagerData.safeAreaInsets.bottom + page.keyboardHeight)
                        backgroundColor(page.theme.page)
                        touchEnable(false)
                    }
                }
                // Two states: a short collapsed bar (＋ / input / 语音 / 拍照, no
                // send) and the expanded composing bar from the HTML prototype.
                // The glass remains transparent. Its gradient rim is painted
                // above it, rather than used as a coloured background beneath it.
                View {
                    attr {
                        borderRadius(26f)
                        paddingTop(9f)
                        paddingBottom(9f)
                        paddingLeft(10f)
                        paddingRight(10f)
                        backgroundColor(
                            if (page.entityDragActive && page.entityDropTarget == EntityDropTarget.COMPOSER) page.theme.brandSoft
                            else Color(0xFFFFFFFF, 0f)
                        )
                        transform(
                            scale = if (page.entityDragActive && page.entityDropTarget == EntityDropTarget.COMPOSER) {
                                Scale(1.015f, 1.015f)
                            } else {
                                Scale.DEFAULT
                            }
                        )
                        animate(
                            Animation.easeOut(0.16f),
                            page.entityDragActive && page.entityDropTarget == EntityDropTarget.COMPOSER,
                        )
                    }
                    GlassBackdrop(page.theme.glass.sheet, page.glassRenderer)
                        // 联想面板打开时收起"最近标的"横条：面板本身已含"最近"数据源候选，
                        // 两条叠着显示既重复又顶高输入栏。
                        vif({ page.isComposerExpanded() && page.assistantPanel == AssistantPanel.NONE }) {
                            RecentSymbolRow(page.theme) { text -> page.injectQuestion(text) }
                        }
                        vif({ page.isComposerExpanded() && page.deepContextVersion >= 0 && page.deepContextNotes.isNotEmpty() }) {
                            page.renderContextNoteBar(this)
                        }
                        vif({ page.isComposerExpanded() && page.commandValidationMessage.isNotEmpty() }) {
                            page.renderCommandValidationBar(this)
                        }
                        vif({ page.isComposerExpanded() && page.assistantPanel != AssistantPanel.NONE }) {
                            page.renderAssistantPanel(this)
                        }
                        vif({ page.isComposerExpanded() && page.inputPanel == InputPanel.MEDIA }) {
                            MediaInputRow(page.theme) { action -> page.handleMediaAction(action) }
                        }
                        // TextArea 必须永远挂在同一个父节点下。折叠/展开只改布局和
                        // 周边操作区，不再用 vif 替换输入组件，避免聚焦期间原生
                        // EditText 被移除或在尚未 attach 时调用 autofocus。
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                marginTop(if (page.isComposerExpanded()) 0f else 8f)
                            }
                            vif({ !page.isComposerExpanded() }) {
                                View {
                                    attr {
                                        size(32f, 32f)
                                        marginRight(7f)
                                        allCenter()
                                        borderRadius(16f)
                                        backgroundColor(page.theme.surfaceMuted)
                                        opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                        touchEnable(!page.isVoiceBusy())
                                    }
                                    LineIconPlus(color = page.theme.textSecondary, size = 16f)
                                }
                            }
                            View {
                                attr {
                                    flex(1f)
                                    minHeight(44f)
                                    paddingLeft(12f)
                                    paddingRight(12f)
                                    justifyContentCenter()
                                    borderRadius(16f)
                                    backgroundColor(Color(0xFFFFFFFF, 0f))
                                }
                                page.renderComposerTextArea(this)
                                vif({ page.voiceState != VoiceState.IDLE }) {
                                    VoiceBar(
                                        theme = page.theme,
                                        transcribing = { page.voiceState == VoiceState.TRANSCRIBING },
                                        cancelArmed = { page.voiceCancelArmed },
                                        elapsedSec = { page.voiceElapsedSec },
                                        amps = { page.voiceAmps },
                                    )
                                }
                            }
                            vif({ !page.isComposerExpanded() }) {
                                View {
                                    attr {
                                        size(32f, 32f)
                                        marginLeft(7f)
                                        allCenter()
                                        borderRadius(16f)
                                        backgroundColor(page.theme.surfaceMuted)
                                        transform(scale = if (page.voiceState == VoiceState.RECORDING) Scale(1.12f, 1.12f) else Scale.DEFAULT)
                                        animate(Animation.easeOut(0.12f), page.voiceState == VoiceState.RECORDING)
                                    }
                                    LineIconMicWithFill(
                                        color = page.theme.textSecondary,
                                        fillColor = if (page.voiceCancelArmed) page.theme.textTertiary else page.theme.rise,
                                        size = 17f,
                                        fill01 = { page.voiceMicFill },
                                    )
                                    event {
                                        touchDown { e -> page.handleVoiceTouchDown(e.pageY) }
                                        touchMove { e -> page.handleVoiceTouchMove(e.pageY) }
                                        touchUp { page.handleVoiceTouchUp() }
                                    }
                                }
                                View {
                                    attr {
                                        size(32f, 32f)
                                        marginLeft(7f)
                                        allCenter()
                                        borderRadius(16f)
                                        backgroundColor(if (page.inputPanel == InputPanel.MEDIA) page.theme.brandSoft else page.theme.surfaceMuted)
                                        opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                        touchEnable(!page.isVoiceBusy())
                                    }
                                    LineIconCamera(
                                        color = if (page.inputPanel == InputPanel.MEDIA) page.theme.brand else page.theme.textSecondary,
                                        size = 17f,
                                    )
                                    event { click { page.toggleMediaPanel() } }
                                }
                            }
                        }
                        vif({ page.isComposerExpanded() }) {
                            View {
                                attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(if (page.assistantPanel == AssistantPanel.AT_MENTION) page.theme.brand else page.theme.brandSoft)
                                        opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                        touchEnable(!page.isVoiceBusy())
                                    }
                                    Text {
                                        attr {
                                            text("@")
                                            fontSize(16f)
                                            fontWeightSemiBold()
                                            color(if (page.assistantPanel == AssistantPanel.AT_MENTION) page.theme.onBrand else page.theme.brand)
                                        }
                                    }
                                    event { click { page.onTriggerButtonTapped('@') } }
                                }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        marginLeft(8f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(if (page.assistantPanel == AssistantPanel.SLASH || page.assistantPanel == AssistantPanel.COMMAND_PARAMS) page.theme.brand else page.theme.brandSoft)
                                        opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                        touchEnable(!page.isVoiceBusy())
                                    }
                                    Text {
                                        attr {
                                            text("/")
                                            fontSize(16f)
                                            fontWeightSemiBold()
                                            color(if (page.assistantPanel == AssistantPanel.SLASH || page.assistantPanel == AssistantPanel.COMMAND_PARAMS) page.theme.onBrand else page.theme.brand)
                                        }
                                    }
                                    event { click { page.onTriggerButtonTapped('/') } }
                                }
                                View { attr { flex(1f) } }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        marginRight(6f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(page.theme.surfaceMuted)
                                        transform(scale = if (page.voiceState == VoiceState.RECORDING) Scale(1.12f, 1.12f) else Scale.DEFAULT)
                                        animate(Animation.easeOut(0.12f), page.voiceState == VoiceState.RECORDING)
                                    }
                                    LineIconMicWithFill(
                                        color = page.theme.textSecondary,
                                        fillColor = if (page.voiceCancelArmed) page.theme.textTertiary else page.theme.rise,
                                        size = 19f,
                                        fill01 = { page.voiceMicFill },
                                    )
                                    event {
                                        touchDown { e -> page.handleVoiceTouchDown(e.pageY) }
                                        touchMove { e -> page.handleVoiceTouchMove(e.pageY) }
                                        touchUp { page.handleVoiceTouchUp() }
                                    }
                                }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        marginRight(4f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(if (page.inputPanel == InputPanel.MEDIA) page.theme.brandSoft else page.theme.surfaceMuted)
                                        opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                        touchEnable(!page.isVoiceBusy())
                                    }
                                    LineIconCamera(
                                        color = if (page.inputPanel == InputPanel.MEDIA) page.theme.brand else page.theme.textSecondary,
                                        size = 19f,
                                    )
                                    event { click { page.toggleMediaPanel() } }
                                }
                                View {
                                    attr {
                                        size(44f, 44f)
                                        allCenter()
                                        borderRadius(22f)
                                        backgroundColor(
                                            when {
                                                page.viewModel.streamState == StreamState.STREAMING -> page.theme.divider
                                                page.isCommandSendBlocked() -> page.theme.surfaceMuted
                                                else -> page.theme.brand
                                            }
                                        )
                                        boxShadow(BoxShadow(0f, 3f, 8f, Color(0x000000, 0.18f)))
                                        opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                        touchEnable(!page.isVoiceBusy())
                                    }
                                    vif({ page.viewModel.streamState == StreamState.STREAMING }) {
                                        LineIconStop(color = page.theme.onBrand, size = 18f)
                                    }
                                    vif({ page.viewModel.streamState != StreamState.STREAMING }) {
                                        LineIconSend(
                                            color = if (page.isCommandSendBlocked()) page.theme.textTertiary else page.theme.onBrand,
                                            size = 22f,
                                        )
                                    }
                                    event {
                                        click {
                                            if (page.viewModel.streamState == StreamState.STREAMING) page.viewModel.stop()
                                            else page.submitInput()
                                        }
                                    }
                                }
                            }
                        }
                    page.renderComposerGradientRim(this)
                }
            }
            vif({ page.entityDragActive }) {
                page.renderEntityDragOverlay(this)
            }
            vif({ page.sheetMounted }) {
                page.sheetCard?.let { model ->
                    CardSheetHost(
                        model = model,
                        level = page.sheetLevel,
                        theme = page.theme,
                        renderer = page.glassRenderer,
                        presented = page.sheetPresented,
                        interactive = page.sheetInteractive,
                        viewportHeight = page.pagerData.pageViewHeight,
                        bottomInset = page.pagerData.safeAreaInsets.bottom,
                        onDismiss = { page.dismissCardSheet() },
                        onLower = { page.lowerCardSheet() },
                        onRaise = { page.raiseCardSheet() },
                        onPan = { state, y -> page.handleSheetPan(state, y) },
                        onOpenStock = { if (page.sheetInteractive) page.openStockDetail(it) },
                        onTerm = { page.viewModel.send("$it 是什么意思") },
                        primaryActionLabel = (model as? StockQuoteCardModel)?.quote?.symbol?.let { symbol ->
                            if (page.watchlistStore.contains(symbol)) "已自选" else "加自选"
                        },
                        onPrimaryAction = { symbol -> page.addWatchlistFromEntity(symbol) },
                    )
                }
            }
            vif({ page.drawerOpen }) {
                ChatDrawer(
                    statusBarHeight = page.pagerData.statusBarHeight,
                    bottomInset = page.pagerData.safeAreaInsets.bottom,
                    theme = page.theme,
                    liveData = page.liveDataMode,
                    renderer = page.glassRenderer,
                    visualLabel = page.glassRenderer.statusLabel(),
                    sessions = page.viewModel.sessionSummaries.toList(),
                    activeSessionId = page.viewModel.activeSessionId,
                    onClose = { page.drawerOpen = false },
                    onToggleDataMode = { page.toggleDataMode() },
                    onCycleVisualMode = { page.cycleGlassMode() },
                    onNewChat = { page.startNewChat() },
                    onOpenSession = { page.openHistorySession(it) },
                    onOpenGallery = { page.openPage(Routes.CARD_GALLERY) },
                    onOpenGlossary = { page.openPage(Routes.GLOSSARY) },
                    onOpenWatchlist = { page.openPage(Routes.WATCHLIST) },
                    onOpenMarket = { page.openPage(Routes.MARKET) },
                    onOpenSearch = { page.openPage(Routes.SEARCH) },
                    onOpenAlerts = { page.openPage(Routes.ALERTS) },
                    onToggleIsland = { page.toggleIsland() },
                    onSettings = { page.drawerOpen = false; page.openPage(Routes.API_CONFIG) },
                )
            }
        }
    }

    private fun startNewChat() {
        viewModel.startNewChat()
        resetSessionUiState()
        setComposerText("")
        keepChatAtBottomTemporarily()
    }

    private fun openHistorySession(sessionId: String) {
        viewModel.openSession(sessionId)
        resetSessionUiState()
        reloadQuotesForCurrentSession()
        setComposerText("")
        keepChatAtBottomTemporarily()
    }

    private fun submitInput() {
        val payload = buildSendPayload()
        if (payload.displayText.isEmpty() || viewModel.streamState == StreamState.STREAMING) return
        val command = payload.command?.let { commandById(it.commandId) }
        if (command != null) {
            val missing = missingRequiredParams(command, payload.command.args)
            if (missing.isNotEmpty()) {
                commandValidationMessage = "还需要填写：${missing.joinToString("、") { it.label }}"
                assistantPanel = AssistantPanel.COMMAND_PARAMS
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(commandValidationMessage)
                return
            }
            if (handleCommandSideEffect(command, payload)) return
        }
        if (payload.command == null && viewModel.inputText.trimStart().startsWith("/")) {
            trackComposerEvent("slash_unknown", "rawInput" to viewModel.inputText.trim(), "didSend" to true)
        }
        if (payload.mentions.isNotEmpty()) {
            trackComposerEvent("at_send_with_mentions", "mentionCount" to payload.mentions.size)
        }
        payload.command?.let {
            trackComposerEvent("slash_cmd_send", "commandId" to it.commandId, "argCount" to it.args.count { entry -> entry.value.isNotBlank() })
        }
        viewModel.send(payload)
        // 发送后清理输入期固化状态：mentions / 命令注册；最近提及列表保留供下次推荐。
        mentionEntities.clear()
        clearActiveCommand()
        commandValidationMessage = ""
        closeAssistantPanel()
        setComposerText("")
        // 发送后不回默认态：键盘还在，用户接着问下一句更顺手。
        keepChatAtBottomTemporarily()
    }

    private fun isCommandSendBlocked(): Boolean {
        if (viewModel.streamState == StreamState.STREAMING) return false
        val command = buildSendPayload().command ?: return false
        val definition = commandById(command.commandId) ?: return false
        return missingRequiredParams(definition, command.args).isNotEmpty()
    }

    private fun commandById(commandId: String): SlashCommand? =
        CommandRegistry.all.firstOrNull { it.id == commandId }

    private fun missingRequiredParams(command: SlashCommand, args: Map<String, String>): List<com.kuikly.stockchat.composer.CommandParam> =
        CommandInvocationParser.missingRequiredParams(command, args)

    /**
     * LOCAL_ACTION / CONTEXT_ONLY 不进入模型发送管线。
     * 若用户写成 "/深水区 茅台怎么看"，则只剥掉命令头，保留正文并带上下文标记发送。
     */
    private fun handleCommandSideEffect(command: SlashCommand, payload: SendPayload): Boolean {
        return when (command.execution) {
            CommandExecution.LOCAL_ACTION -> {
                when (command.id) {
                    "clear" -> {
                        viewModel.clear()
                        val rest = CommandInvocationParser.remainder(payload.text, command.name)
                        finishCommandSideEffect(rest)
                        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("已清屏")
                    }
                    "monitor" -> {
                        finishCommandSideEffect("")
                        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("已建立盯盘：${payload.command?.args?.values?.filter { it.isNotBlank() }?.joinToString(" · ").orEmpty()}")
                    }
                    else -> finishCommandSideEffect("")
                }
                true
            }
            CommandExecution.CONTEXT_ONLY -> {
                toggleContextNote("深水区模式")
                val rest = CommandInvocationParser.remainder(payload.text, command.name)
                if (rest.isBlank()) {
                    finishCommandSideEffect("")
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(
                        if (deepContextNotes.contains("深水区模式")) "已开启深水区模式" else "已关闭深水区模式"
                    )
                    true
                } else {
                    val forwarded = SendPayload(
                        text = rest,
                        mentions = payload.mentions,
                        command = null,
                        renderedPrompt = null,
                        contextNotes = deepContextNotes.toList(),
                    )
                    viewModel.send(forwarded)
                    mentionEntities.clear()
                    clearActiveCommand()
                    commandValidationMessage = ""
                    closeAssistantPanel()
                    setComposerText("")
                    keepChatAtBottomTemporarily()
                    true
                }
            }
            CommandExecution.PROMPT_TEMPLATE -> false
        }
    }

    private fun finishCommandSideEffect(remainingText: String) {
        mentionEntities.clear()
        clearActiveCommand()
        commandValidationMessage = ""
        closeAssistantPanel()
        setComposerText(remainingText.trimStart())
        keepChatAtBottomTemporarily()
    }

    private fun toggleContextNote(note: String) {
        if (deepContextNotes.contains(note)) {
            deepContextNotes.remove(note)
            glassModeManuallySelected = false
        } else {
            deepContextNotes.add(note)
            glassModeManuallySelected = true
            glassMode = GlassRenderingMode.REALTIME
        }
        deepContextVersion++
    }

    private fun trackComposerEvent(eventCode: String, vararg fields: Pair<String, Any?>) {
        val data = JSONObject()
        fields.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is Int -> data.put(key, value)
                is Long -> data.put(key, value)
                is Float -> data.put(key, value)
                is Double -> data.put(key, value)
                is Boolean -> data.put(key, value)
                else -> data.put(key, value.toString())
            }
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).reportDT(eventCode, data)
    }

    private fun toggleMediaPanel() {
        val willShow = inputPanel != InputPanel.MEDIA
        cancelVoiceSession()
        if (willShow) {
            blurComposer()
        }
        inputPanel = if (willShow) InputPanel.MEDIA else InputPanel.NONE
    }

    private fun handleMediaAction(action: ComposerMediaAction) {
        // 选完图片/拍照后整条输入栏回到默认态，不留在"半展开"的悬空状态。
        collapseComposer()
        blurComposer()
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridge.hapticImpact()
        bridge.openComposerMediaSource(action.source)
    }

    private fun resetSessionUiState() {
        ambiguousSymbols.clear()
        ambiguousEntityText = ""
        pendingLongPressSymbol = ""
        draggedEntity = null
        entityDragActive = false
        entityDropTarget = EntityDropTarget.NONE
        entityDragName = ""
        peekSymbol = ""
        peekVisible = false
        islandExpanded = false
        resetIslandMotion()
        islandCompareLeftSymbol = ""
        islandCompareRightSymbol = ""
        islandCompareVisible = false
        sheetCard = null
        sheetMounted = false
        sheetPresented = false
        sheetLevel = SheetLevel.HALF
        sheetInteractive = false
        sheetPresentationVersion++
        expandedCardKey = ""
        focusedCardKey = ""
        repairingCardKey = ""
        compareCandidateKey = ""
        compareCandidateSymbol = ""
        compareCard = null
        resetCompareInsight()
        drilledKeys.clear()
        subThreads.clear()
        suppressNextStockClickSymbol = ""
        requestedSymbols.clear()
        quoteStates.clear()
        deepContextNotes.clear()
        deepContextVersion++
        commandValidationMessage = ""
        collapseComposer()
        blurComposer()
    }

    private fun reloadQuotesForCurrentSession() {
        viewModel.messages
            .flatMap { EntityRecognizer.recognize(it.content) }
            .filter { it.type == EntityType.STOCK }
            .map { it.target }
            .distinct()
            .forEach(::requestQuote)
    }

    /** 默认态 → 输入态。requestFocus 为真时同时把键盘拉起来。 */
    private fun expandComposer(requestFocus: Boolean = false) {
        val wasExpanded = composerExpanded
        KLog.i(COMPOSER_LOG_TAG, "expandComposer requestFocus=$requestFocus wasExpanded=$wasExpanded ref=${inputRef?.view != null}")
        // Kuikly observable 的同值赋值仍可能触发一次 render/layout commit；而
        // EditText 的 focus 回调会再次进入这里。状态转换必须幂等，否则恢复焦点
        // 后的第二次同值提交仍会把原生焦点清掉。
        if (!composerExpanded) composerExpanded = true
        if (voiceState != VoiceState.IDLE) cancelVoiceSession()
        if (inputPanel == InputPanel.MEDIA) inputPanel = InputPanel.NONE
        if (requestFocus) {
            composerFocusLocked = true
            if (wasExpanded) inputRef?.view?.focus() else scheduleComposerFocusAfterExpansion()
        }
    }

    /**
     * Android 首次聚焦会同时触发输入栏展开和键盘避让布局。真正会 clearFocus 的
     * 是 keyboardHeight 写入后的那次 RecyclerView commit，固定等 64ms 仍可能早于
     * 键盘动画结束。这里先登记一次性恢复请求；正常路径由 keyboardHeightChange
     * 在最终布局后执行，500ms 仅作为不回调该事件的平台兜底。
     */
    private fun scheduleComposerFocusAfterExpansion() {
        val requestVersion = ++composerFocusRequestVersion
        composerFocusRecoveryPending = true
        KLog.i(COMPOSER_LOG_TAG, "scheduleFocusRecovery version=$requestVersion")
        setTimeout(500) {
            recoverComposerFocus(requestVersion, "fallback")
        }
    }

    private fun scheduleComposerFocusAfterKeyboardLayout(duration: Float) {
        if (!composerFocusRecoveryPending || !composerExpanded) return
        val requestVersion = composerFocusRequestVersion
        val layoutVersion = ++composerKeyboardLayoutVersion
        val delayMs = (duration * 1000f).toInt().coerceIn(0, 400) + 48
        setTimeout(delayMs) {
            if (layoutVersion != composerKeyboardLayoutVersion) return@setTimeout
            recoverComposerFocus(requestVersion, "keyboardLayout")
        }
    }

    private fun recoverComposerFocus(requestVersion: Int, source: String) {
        if (
            requestVersion != composerFocusRequestVersion ||
            !composerFocusRecoveryPending ||
            !composerExpanded
        ) return
        composerFocusRecoveryPending = false
        KLog.i(COMPOSER_LOG_TAG, "runFocusRecovery source=$source version=$requestVersion ref=${inputRef?.view != null}")
        inputRef?.view?.focus()
    }

    private fun recoverUnexpectedComposerBlur() {
        if (
            !composerFocusLocked ||
            !composerExpanded ||
            voiceState != VoiceState.IDLE ||
            inputPanel == InputPanel.MEDIA
        ) return
        val blurVersion = ++composerUnexpectedBlurVersion
        val requestVersion = composerFocusRequestVersion
        setTimeout(96) {
            if (
                blurVersion != composerUnexpectedBlurVersion ||
                requestVersion != composerFocusRequestVersion ||
                !composerFocusLocked ||
                !composerExpanded ||
                voiceState != VoiceState.IDLE ||
                inputPanel == InputPanel.MEDIA
            ) return@setTimeout
            KLog.i(COMPOSER_LOG_TAG, "recoverUnexpectedBlur version=$blurVersion")
            inputRef?.view?.focus()
        }
    }

    private fun blurComposer() {
        composerFocusLocked = false
        composerUnexpectedBlurVersion++
        composerFocusRequestVersion++
        composerKeyboardLayoutVersion++
        composerFocusRecoveryPending = false
        inputRef?.view?.blur()
    }

    /** 输入态 → 默认态。草稿会留在输入框里，只是收起辅助区。 */
    private fun collapseComposer() {
        KLog.i(COMPOSER_LOG_TAG, "collapseComposer draftLen=${viewModel.inputText.length}")
        composerFocusRequestVersion++
        composerKeyboardLayoutVersion++
        composerFocusRecoveryPending = false
        composerFocusLocked = false
        composerUnexpectedBlurVersion++
        composerExpanded = false
        inputPanel = InputPanel.NONE
        clearActiveCommand()
        closeAssistantPanel()
        cancelVoiceSession()
    }

    /**
     * 点击输入栏以外的区域（Kimi 规则）：
     * - 键盘在屏上：这一次点击只收键盘，输入栏保持输入态；
     * - 键盘已收起：这一次点击才把输入栏收回默认态。
     */
    private fun handleOutsideTap() {
        KLog.i(COMPOSER_LOG_TAG, "outsideTap keyboardVisible=$keyboardVisible keyboardHeight=$keyboardHeight")
        if (voiceState != VoiceState.IDLE) return
        if (keyboardVisible || keyboardHeight > 0f) {
            blurComposer()
            return
        }
        collapseComposer()
        blurComposer()
    }

    /** Reads observable state inside each vif predicate so Kuikly can re-render it. */
    private fun isComposerExpanded(): Boolean =
        composerExpanded || inputPanel != InputPanel.NONE || keyboardVisible || keyboardHeight > 0f || voiceState != VoiceState.IDLE

    private fun isVoiceBusy(): Boolean = voiceState != VoiceState.IDLE

    private fun handleVoiceTouchDown(pageY: Float) {
        if (voiceState != VoiceState.IDLE) return
        voiceSourceExpanded = isComposerExpanded()
        voiceTouchDownPageY = pageY
        startVoiceSession()
    }

    private fun handleVoiceTouchMove(pageY: Float) {
        if (voiceState != VoiceState.RECORDING) return
        voiceCancelArmed = (voiceTouchDownPageY - pageY) >= 60f
    }

    private fun handleVoiceTouchUp() {
        if (voiceState != VoiceState.RECORDING) return
        when {
            voiceCancelArmed -> cancelVoiceSession()
            voiceElapsedSec < 0.8f -> abortTooShortVoiceSession()
            else -> finishVoiceSession()
        }
    }

    private fun startVoiceSession() {
        val version = ++voiceSessionVersion
        blurComposer()
        composerExpanded = true
        inputPanel = InputPanel.NONE
        closeAssistantPanel()
        commandValidationMessage = ""
        voiceCancelArmed = false
        voiceElapsedSec = 0f
        voiceAmps = FloatArray(24) { 4f }
        voiceMicFill = 0.02f
        voiceState = VoiceState.RECORDING
        startVoiceClock(version)
        voiceRecorder.start(
            onAmplitude = { rms ->
                if (voiceSessionVersion == version && voiceState == VoiceState.RECORDING) {
                    updateVoiceAmplitude(rms)
                }
            },
            onTranscript = { transcript ->
                if (voiceSessionVersion == version && voiceState == VoiceState.TRANSCRIBING) {
                    onTranscriptReady(transcript)
                }
            },
            onError = { error ->
                if (voiceSessionVersion == version) handleVoiceRecorderError(error)
            },
        )
    }

    private fun startVoiceClock(version: Int) {
        voiceClockTimer?.cancel()
        val timer = Timer()
        voiceClockTimer = timer
        timer.schedule(100, 100) {
            if (voiceSessionVersion != version || voiceState != VoiceState.RECORDING) return@schedule
            val next = (voiceElapsedSec + 0.1f).coerceAtMost(60f)
            voiceElapsedSec = next
            if (next >= 60f) finishVoiceSession()
        }
    }

    private fun updateVoiceAmplitude(rms: Float) {
        val amp = (rms.coerceIn(0f, 1f) * 2.5f).coerceIn(0f, 1f)
        val shaped = amp.toDouble().pow(0.6).toFloat()
        val prevFill = voiceMicFill
        val fillRate = if (shaped > prevFill) 0.6f else 0.25f
        voiceMicFill = prevFill + (shaped - prevFill) * fillRate

        val previous = voiceAmps
        val next = FloatArray(24)
        val mid = (next.size - 1) / 2f
        for (i in next.indices) {
            val envelope = 0.55f + 0.45f * abs(cos((i - mid) / next.size * PI.toFloat()))
            val target = 4f + 24f * shaped * envelope
            val prev = previous.getOrNull(i) ?: 4f
            val rate = if (target > prev) 0.6f else 0.25f
            next[i] = (prev + (target - prev) * rate).coerceIn(4f, 28f)
        }
        voiceAmps = next
    }

    private fun finishVoiceSession() {
        if (voiceState != VoiceState.RECORDING) return
        voiceClockTimer?.cancel()
        voiceClockTimer = null
        voiceCancelArmed = false
        voiceState = VoiceState.TRANSCRIBING
        voiceAmps = FloatArray(24) { 4f }
        voiceMicFill = 0f
        voiceRecorder.stop()
    }

    private fun onTranscriptReady(transcript: String) {
        if (voiceState != VoiceState.TRANSCRIBING) return
        if (transcript.isBlank()) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("没听清，请再说一次")
            restoreAfterVoiceSession()
            return
        }
        if (viewModel.streamState == StreamState.STREAMING) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("当前回答未结束，请稍后再试")
            restoreAfterVoiceSession()
            return
        }
        viewModel.send(transcript)
        restoreAfterVoiceSession()
        keepChatAtBottomTemporarily()
    }

    private fun abortTooShortVoiceSession() {
        voiceRecorder.cancel()
        stopVoiceUi()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("说话时间太短")
        restoreAfterVoiceSession()
    }

    private fun cancelVoiceSession() {
        if (voiceState == VoiceState.IDLE) return
        voiceRecorder.cancel()
        stopVoiceUi()
        restoreAfterVoiceSession()
    }

    private fun handleVoiceRecorderError(error: VoiceError) {
        stopVoiceUi()
        restoreAfterVoiceSession()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(
            when (error) {
                VoiceError.PERMISSION_DENIED -> "需要麦克风权限才能语音提问"
                VoiceError.MIC_OCCUPIED -> "麦克风被占用，请稍后再试"
                VoiceError.NO_MATCH -> "没听清，请再说一次"
                VoiceError.UNAVAILABLE -> "当前平台暂不支持语音提问"
            },
        )
    }

    private fun stopVoiceUi() {
        voiceSessionVersion++
        voiceClockTimer?.cancel()
        voiceClockTimer = null
        voiceState = VoiceState.IDLE
        voiceCancelArmed = false
        voiceElapsedSec = 0f
        voiceAmps = FloatArray(24) { 4f }
        voiceMicFill = 0f
    }

    private fun restoreAfterVoiceSession() {
        stopVoiceUi()
        if (!voiceSourceExpanded && viewModel.inputText.isBlank()) {
            composerExpanded = false
        } else {
            composerExpanded = true
        }
        voiceSourceExpanded = false
    }

    private fun renderComposerGradientRim(container: ViewContainer<*, *>) {
        container.Canvas(
            init = {
                attr {
                    absolutePositionAllZero()
                    touchEnable(false)
                    zIndex(10, useOutline = false)
                }
            },
            draw = { context, width, height ->
                val gradient = context.createLinearGradient(0f, 0f, width, 0f)
                gradient.addColorStop(0f, Color(0xFF2563EB, 0.72f))
                gradient.addColorStop(0.45f, Color(0xFF7C3AED, 0.50f))
                gradient.addColorStop(1f, Color(0xFF2DD4BF, 0.68f))

                // Fill only the difference between two concentric rounded
                // rectangles. A single clipped ring gives straight edges and
                // corners exactly the same 1dp thickness.
                context.save()
                drawComposerRoundedRect(context, width, height, inset = 0f, cornerRadius = 26f)
                context.clipPathIntersect()
                drawComposerRoundedRect(context, width, height, inset = 1f, cornerRadius = 25f)
                context.clipPathDifference()
                drawComposerRoundedRect(context, width, height, inset = 0f, cornerRadius = 26f)
                context.fillStyle(gradient)
                context.fill()
                context.restore()
            },
        )
    }

    private fun drawComposerRoundedRect(
        context: CanvasContext,
        width: Float,
        height: Float,
        inset: Float,
        cornerRadius: Float,
    ) {
        val radius = minOf(cornerRadius, (width / 2f - inset).coerceAtLeast(0f), (height / 2f - inset).coerceAtLeast(0f))
        context.beginPath()
        context.moveTo(inset + radius, inset)
        context.lineTo(width - inset - radius, inset)
        context.arc(width - inset - radius, inset + radius, radius, (-PI / 2).toFloat(), 0f, false)
        context.lineTo(width - inset, height - inset - radius)
        context.arc(width - inset - radius, height - inset - radius, radius, 0f, (PI / 2).toFloat(), false)
        context.lineTo(inset + radius, height - inset)
        context.arc(inset + radius, height - inset - radius, radius, (PI / 2).toFloat(), PI.toFloat(), false)
        context.lineTo(inset, inset + radius)
        context.arc(inset + radius, inset + radius, radius, PI.toFloat(), (PI * 1.5f).toFloat(), false)
        context.closePath()
    }

    private fun renderComposerTextArea(container: ViewContainer<*, *>) {
        // 非受控铁律（光标消失/退格错乱的根因是回声循环）：
        // 原生上报 textDidChange → 业务把 observable 草稿 + 估算光标 + 清空组合态
        // 写回 → textInputState 响应式绑定重放 setProp → 原生在 IME 组合态进行中
        // 被回写打断 → 组合会话被杀 → 光标消失、退格删错。
        // 且该回声拦不住：textDidChange 注册路径不置 isProcessingNativeEvent，
        // 业务估算态与原生真实态永远 hasSameEditingState=false。
        // 因此：attr 中完全不能出现 text/textInputState。Kuikly 的 attr 是整块
        // 响应式执行的，高度/主题/语音态任一变化都会把同一块里的 text 再下发；
        // 即便参数是固定的 mountSeed，也会打断正在进行的拼音组合与退格。
        // 首次挂载种子在 ref 后命令式写入；后续回写也只走 setComposerText →
        // view.setTextInputState（callMethod，不经过 Props）。
        val mountSeed = this.viewModel.inputText
        composerEditingState = ComposerEditingReducer.programmatic(mountSeed)
        KLog.i(COMPOSER_LOG_TAG, "render seedLen=${mountSeed.length}")
        container.TextArea {
            ref {
                this@ChatPage.inputRef = it
                KLog.i(COMPOSER_LOG_TAG, "ref nativeRef=${it.nativeRef} view=${it.view?.viewName()}")
                // ref 回调发生在组件加入 Pager 映射之前，此时 view 可能尚不可用。
                // 延后一拍只初始化这一个新挂载的原生输入框，绝不参与后续响应式刷新。
                this@ChatPage.setTimeout(0) {
                    if (this@ChatPage.inputRef?.nativeRef != it.nativeRef) return@setTimeout
                    it.view?.setTextInputState(this@ChatPage.composerEditingState)
                }
            }
            // 输入法相关配置必须放在无 observable 依赖的静态 attr 中。
            // returnKeyType 在 Android 会调用 InputMethodManager.restartInput；如果它
            // 跟 maxHeight 等动态属性一起重放，会在展开布局时重启刚建立的连接，
            // 形成“键盘有反应、正文无光标”的僵尸 InputConnection。
            attr {
                minHeight(40f)
                fontSize(14f)
                lineHeight(21f)
                backgroundColor(Color(0xFFFFFFFF, 0f))
                placeholder("问一只股票或一个术语")
                returnKeyTypeSend()
                enablePinyinCallback(true)
                // Android 端由外部属性处理器把这个标记映射成 EditText 的
                // isCursorVisible。焦点锁在意外 blur 后恢复，原生光标标记也不会
                // 因布局刷新被关闭。
                "stockChatKeepCursorVisible" with 1
            }
            attr {
                // 折叠态和联想面板展开时收紧到单行。这里只更新尺寸 prop，
                // 持久 TextArea 本身不移除、不重建。
                maxHeight(
                    if (!this@ChatPage.isComposerExpanded() || this@ChatPage.assistantPanel != AssistantPanel.NONE) 44f
                    else 80f
                )
                color(this@ChatPage.theme.textPrimary)
                opacity(if (this@ChatPage.voiceState == VoiceState.IDLE) 1f else 0f)
                touchEnable(this@ChatPage.voiceState == VoiceState.IDLE)
                placeholderColor(this@ChatPage.theme.textTertiary)
                tintColor(this@ChatPage.theme.brand)
                selectionColor(this@ChatPage.theme.brand)
            }
            event {
                inputFocus {
                    // 键盘自己弹起（系统输入法回调 / 原生点击）也算进入输入态。
                    KLog.i(COMPOSER_LOG_TAG, "EV inputFocus len=${it.text.length}")
                    this@ChatPage.composerFocusLocked = true
                    // 已展开后的恢复 focus 必须是纯事件：不要再次进入任何布局函数。
                    // 即使函数内部最终没有改值，Kuikly 的事件/依赖追踪也可能安排
                    // 一次提交，并在 RecyclerView 中再次 clearFocus。
                    if (!this@ChatPage.composerExpanded) {
                        this@ChatPage.expandComposer()
                        this@ChatPage.scheduleComposerFocusAfterExpansion()
                    }
                }
                inputBlur {
                    // 失焦本身绝不收起输入栏：输入态是"粘"的，只有"键盘已收起时点击
                    // 非输入栏区域"这一次点击才回默认态（见 handleOutsideTap）。
                    KLog.i(COMPOSER_LOG_TAG, "EV inputBlur len=${it.text.length}")
                    this@ChatPage.recoverUnexpectedComposerBlur()
                }
                textDidChange(isSyncEdit = true) {
                    KLog.d(COMPOSER_LOG_TAG, "EV textDidChange len=${it.text.length}")
                    this@ChatPage.handleComposerTextChanged(it.text)
                }
                textInputStateChange { state ->
                    KLog.d(
                        COMPOSER_LOG_TAG,
                        "EV textInputStateChange len=${state.text.length}" +
                            " sel=${state.selectionStart}..${state.selectionEnd}" +
                            " comp=${state.compositionStart}..${state.compositionEnd}"
                    )
                    this@ChatPage.updateComposerEditingState(state)
                    val composing = state.compositionStart != TextInputState.NO_COMPOSITION
                    this@ChatPage.updateTriggerSession(state.text, state.selectionEnd, composing)
                }
                selectionChange { state ->
                    KLog.d(
                        COMPOSER_LOG_TAG,
                        "EV selectionChange len=${state.text.length}" +
                            " sel=${state.selectionStart}..${state.selectionEnd}"
                    )
                    this@ChatPage.updateComposerSelectionState(state)
                }
                keyboardHeightChange {
                    KLog.i(COMPOSER_LOG_TAG, "EV keyboardHeightChange h=${it.height} dur=${it.duration}")
                    this@ChatPage.keyboardHeight = it.height
                    this@ChatPage.keyboardVisible = it.height > 0f
                    if (it.height > 0f) {
                        this@ChatPage.scheduleComposerFocusAfterKeyboardLayout(it.duration)
                    }
                    this@ChatPage.scheduleScrollChatToBottom()
                }
                inputReturn {
                    KLog.i(COMPOSER_LOG_TAG, "EV inputReturn len=${it.text.length}")
                    // 面板开着：Enter 优先确认高亮候选；面板关着才走发送。
                    if (!this@ChatPage.confirmHighlightedCandidate()) this@ChatPage.submitInput()
                }
            }
        }
    }

    private fun renderContextNoteBar(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                height(30f)
                marginTop(8f)
                paddingLeft(10f)
                paddingRight(8f)
                flexDirectionRow()
                alignItemsCenter()
                backgroundColor(page.theme.brandSoft)
                borderRadius(9f)
            }
            Text {
                attr {
                    text(page.deepContextNotes.joinToString("、"))
                    fontSize(11f)
                    fontWeightMedium()
                    color(page.theme.brand)
                    flex(1f)
                }
            }
            Text {
                attr { text("取消"); fontSize(11f); color(page.theme.textSecondary) }
                event {
                    click {
                        page.deepContextNotes.clear()
                        page.deepContextVersion++
                    }
                }
            }
        }
    }

    private fun renderCommandValidationBar(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                height(28f)
                marginTop(8f)
                paddingLeft(10f)
                paddingRight(10f)
                justifyContentCenter()
                backgroundColor(page.theme.riseSoft)
                borderRadius(9f)
            }
            Text {
                attr {
                    text(page.commandValidationMessage)
                    fontSize(11f)
                    color(page.theme.rise)
                }
            }
        }
    }

    private fun shouldKeepChatAtBottom(): Boolean =
        keepChatAtBottomVersion > 0 || viewModel.streamState == StreamState.STREAMING

    private fun keepChatAtBottomTemporarily() {
        val version = ++keepChatAtBottomVersion
        scheduleScrollChatToBottom()
        setTimeout(1200) {
            if (keepChatAtBottomVersion == version) keepChatAtBottomVersion = 0
        }
    }

    private fun scheduleScrollChatToBottom(animated: Boolean = true) {
        setTimeout(16) {
            chatScrollerRef?.view?.setContentOffset(0f, chatContentHeight, animated)
        }
    }

    private fun showQuote(symbol: String) {
        val version = ++peekVersion
        ambiguousSymbols.clear()
        ambiguousEntityText = ""
        peekSymbol = symbol
        peekVisible = false
        requestQuote(symbol)
        setTimeout(16) {
            if (peekVersion == version && peekSymbol == symbol) peekVisible = true
        }
    }

    private fun dismissPeek() {
        val version = ++peekVersion
        peekVisible = false
        setTimeout(180) {
            if (peekVersion == version && !peekVisible) peekSymbol = ""
        }
    }

    private fun schedulePeekDismissal() {
        val version = peekVersion
        setTimeout(1500) {
            if (peekVersion == version && peekVisible) dismissPeek()
        }
    }

    private fun renderEntityDragOverlay(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                touchEnable(false)
            }
            View {
                attr {
                    val width = 166f
                    val left = (page.entityDragX - width / 2f)
                        .coerceIn(8f, (page.pagerData.pageViewWidth - width - 8f).coerceAtLeast(8f))
                    val top = (page.entityDragY - 64f)
                        .coerceIn(page.pagerData.statusBarHeight + 4f, page.pagerData.pageViewHeight - 62f)
                    absolutePosition(top = top, left = left)
                    width(width)
                    height(50f)
                    paddingLeft(13f)
                    paddingRight(13f)
                    justifyContentCenter()
                    borderRadius(18f)
                    backgroundColor(page.theme.brand)
                    boxShadow(BoxShadow(0f, 7f, 18f, page.theme.brand.opacity(0.28f)))
                    transform(scale = Scale(1.03f, 1.03f))
                }
                Text {
                    attr {
                        text(page.entityDragName)
                        fontSize(13f)
                        fontWeightSemiBold()
                        color(page.theme.onBrand)
                    }
                }
                Text {
                    attr {
                        text(
                            when (page.entityDropTarget) {
                                EntityDropTarget.ISLAND -> "松手加入股票对比"
                                EntityDropTarget.COMPOSER -> "松手插入 @ 提及"
                                EntityDropTarget.NONE -> "拖到输入框或灵动岛"
                            }
                        )
                        marginTop(2f)
                        fontSize(9f)
                        color(page.theme.onBrand.opacity(0.78f))
                    }
                }
            }
        }
    }

    private fun handleStockEntityClick(entity: EntitySpan) {
        if (suppressNextStockClickSymbol == entity.target) {
            suppressNextStockClickSymbol = ""
            return
        }
        handleStockEntity(entity, EntityAction.DETAIL)
    }

    private fun handleStockEntityLongPress(entity: EntitySpan, params: LongPressParams) {
        when (params.state) {
            "start" -> {
                if (params.isCancel || pendingLongPressSymbol == entity.target) return
                pendingLongPressSymbol = entity.target
                suppressNextStockClickSymbol = entity.target
                draggedEntity = entity
                entityDragName = entityDisplayName(entity.target, entity.text)
                entityDragStartX = params.pageX
                entityDragStartY = params.pageY
                entityDragX = params.pageX
                entityDragY = params.pageY
                entityDragActive = false
                entityDropTarget = EntityDropTarget.NONE
                // A stationary long press remains the quote-preview gesture.
                openEntityQuoteIsland(entity.target)
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                trackComposerEvent("entity_hold_preview", "symbol" to entity.target)
                return
            }
            "move" -> {
                if (pendingLongPressSymbol != entity.target) return
                if (!entityDragActive && EntityDropResolver.hasExceededDragThreshold(
                        entityDragStartX,
                        entityDragStartY,
                        params.pageX,
                        params.pageY,
                    )
                ) {
                    entityDragActive = true
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                    trackComposerEvent("entity_drag_start", "symbol" to entity.target)
                }
                if (entityDragActive) updateEntityDragPosition(params.pageX, params.pageY)
                if (params.isCancel) {
                    if (entityDragActive) finishEntityDrag() else finishEntityHold(entity.target)
                }
                return
            }
            "end" -> {
                if (pendingLongPressSymbol != entity.target) return
                if (entityDragActive) {
                    updateEntityDragPosition(params.pageX, params.pageY)
                    finishEntityDrag()
                } else {
                    finishEntityHold(entity.target)
                }
                return
            }
            else -> if (params.isCancel && pendingLongPressSymbol == entity.target) {
                if (entityDragActive) finishEntityDrag() else finishEntityHold(entity.target)
                return
            }
        }
    }

    private fun finishEntityHold(symbol: String) {
        draggedEntity = null
        entityDragActive = false
        entityDropTarget = EntityDropTarget.NONE
        entityDragName = ""
        pendingLongPressSymbol = ""
        finishStockLongPress(symbol)
    }

    private fun updateEntityDragPosition(pageX: Float, pageY: Float) {
        entityDragX = pageX
        entityDragY = pageY
        entityDropTarget = EntityDropResolver.resolve(
            pageX = pageX,
            pageY = pageY,
            pageWidth = pagerData.pageViewWidth,
            pageHeight = pagerData.pageViewHeight,
            statusBarHeight = pagerData.statusBarHeight,
            safeAreaBottom = pagerData.safeAreaInsets.bottom,
            keyboardHeight = keyboardHeight,
            islandExpanded = islandExpanded || islandCompareLeftSymbol.isNotEmpty(),
        )
    }

    private fun isIslandFirstCompareDrop(): Boolean =
        islandCompareLeftSymbol.isEmpty()

    private fun isIslandCompareLobbyVisible(): Boolean =
        islandCompareVisible &&
            islandCompareLeftSymbol.isNotEmpty()

    private fun finishEntityDrag() {
        val entity = draggedEntity
        val target = entityDropTarget
        val symbol = entity?.target ?: pendingLongPressSymbol
        draggedEntity = null
        entityDragActive = false
        entityDropTarget = EntityDropTarget.NONE
        entityDragName = ""
        pendingLongPressSymbol = ""

        if (entity != null) {
            when (target) {
                EntityDropTarget.COMPOSER -> handleStockEntity(entity, EntityAction.MENTION)
                EntityDropTarget.ISLAND -> handleStockEntity(entity, EntityAction.COMPARE)
                EntityDropTarget.NONE -> Unit
            }
            if (target != EntityDropTarget.NONE) {
                trackComposerEvent("entity_drag_drop", "symbol" to entity.target, "target" to target.name.lowercase())
            }
        }
        finishStockLongPress(symbol)

    }

    private fun finishStockLongPress(symbol: String) {
        // The island is outside the releasing finger's hit area, so no deferred
        // mount is needed. Keep only the short click-suppression guard.
        setTimeout(400) {
            if (suppressNextStockClickSymbol == symbol) {
                suppressNextStockClickSymbol = ""
            }
        }
    }

    private fun handleStockEntity(entity: EntitySpan, action: EntityAction) {
        if (entity.candidates.size == 1) performEntityAction(entity.target, action)
        else {
            ambiguousEntityText = entity.text
            ambiguousAction = action
            ambiguousSymbols.clear()
            ambiguousSymbols.addAll(entity.candidates)
        }
    }

    private fun chooseAmbiguousSymbol(symbol: String) = performEntityAction(symbol, ambiguousAction)

    private fun performEntityAction(symbol: String, action: EntityAction) {
        when (action) {
            EntityAction.DETAIL -> openStockDetail(symbol)
            EntityAction.PREVIEW -> showQuote(symbol)
            EntityAction.ISLAND -> openEntityQuoteIsland(symbol)
            EntityAction.MENTION -> insertDraggedMention(symbol)
            EntityAction.COMPARE -> addDraggedStockToIsland(symbol)
        }
    }

    private fun entityDisplayName(symbol: String, fallback: String = symbol): String =
        quoteFor(symbol)?.name ?: ComposerCatalog.find(symbol)?.name ?:
        Securities.all.firstOrNull { it.symbol == symbol }?.name ?: fallback

    private fun insertDraggedMention(symbol: String) {
        val entry = ComposerCatalog.find(symbol)
        val name = entry?.name ?: entityDisplayName(symbol)
        val mention = entry?.let(MentionEntity::of)
            ?: MentionEntity(symbol, name, MentionType.STOCK, "@$name")
        val edit = ComposerTextOperations.insertMention(
            text = viewModel.inputText,
            selectionStart = composerEditingState.selectionStart,
            selectionEnd = composerEditingState.selectionEnd,
            mentionText = mention.mentionText,
        )
        setComposerText(edit.text, edit.cursor)
        mentionEntities.removeAll { it.symbol == symbol }
        mentionEntities.add(mention)
        recentMentions.remove(symbol)
        recentMentions.add(0, symbol)
        if (recentMentions.size > 8) recentMentions.subList(8, recentMentions.size).clear()
        closeAssistantPanel()
        if (paramCommand != null) assistantPanel = AssistantPanel.COMMAND_PARAMS
        expandComposer(requestFocus = true)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    private fun addDraggedStockToIsland(symbol: String) {
        resetIslandMotion()
        compareCandidateKey = ""
        compareCandidateSymbol = ""
        if (islandCompareLeftSymbol.isEmpty()) {
            islandCompareLeftSymbol = symbol
            islandCompareRightSymbol = ""
            compareCard = null
            resetCompareInsight()
        } else if (islandCompareLeftSymbol == symbol || islandCompareRightSymbol == symbol) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("请选择另一只股票进行对比")
            islandExpanded = true
            return
        } else if (islandCompareRightSymbol.isNotEmpty()) {
            islandCompareLeftSymbol = islandCompareRightSymbol
            islandCompareRightSymbol = symbol
            compareCard = null
            resetCompareInsight()
        } else {
            islandCompareRightSymbol = symbol
        }
        requestQuote(symbol)
        requestQuote(islandCompareLeftSymbol)
        // The island owns the comparison session from the first drop until the
        // user explicitly exits it.  Keep the completed two-stock summary open
        // while the full comparison panel is visible below.
        islandCompareVisible = true
        islandExpanded = true
        syncIslandCompareCard()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    private fun syncIslandCompareCard() {
        val leftSymbol = islandCompareLeftSymbol
        val rightSymbol = islandCompareRightSymbol
        if (leftSymbol.isEmpty() || rightSymbol.isEmpty()) return
        val left = quoteFor(leftSymbol) ?: return
        val right = quoteFor(rightSymbol) ?: return
        compareCard = StockCompareCardModel(
            listOf(left, right),
            "island-compare:${left.symbol}:${right.symbol}",
        )
        requestCompareInsightIfNeeded(left, right)
    }

    private fun clearIslandCompare() {
        clearCompareExperience()
    }

    private fun clearCompareExperience() {
        resetIslandMotion()
        // Closing comparison is also a hard interaction boundary.  A terminal
        // long-press event can be lost when the comparison panel mounts under
        // the releasing finger, so explicitly invalidate every drag field.
        pendingLongPressSymbol = ""
        draggedEntity = null
        entityDragActive = false
        entityDropTarget = EntityDropTarget.NONE
        entityDragName = ""
        islandCompareLeftSymbol = ""
        islandCompareRightSymbol = ""
        islandCompareVisible = false
        compareCard = null
        compareCandidateKey = ""
        compareCandidateSymbol = ""
        resetCompareInsight()
        islandExpanded = false
    }

    private fun openIslandComparePanel() {
        if (compareCard == null) return
        resetIslandMotion()
        islandCompareVisible = true
        islandExpanded = true
    }

    private fun addWatchlistFromEntity(symbol: String) {
        val quote = quoteFor(symbol)
        val security = Securities.all.firstOrNull { it.symbol == symbol }
        val name = quote?.name ?: security?.name ?: symbol
        val message = when (watchlistStore.add(symbol, name)) {
            WatchlistAddResult.ADDED -> "已加入自选：$name"
            WatchlistAddResult.ALREADY_IN -> "$name 已在自选中"
            WatchlistAddResult.FULL -> "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(message)
    }

    private fun openEntityQuoteIsland(symbol: String) {
        resetIslandMotion()
        islandSymbol = symbol
        islandWatchlisted = watchlistStore.contains(symbol)
        islandCompareVisible = false
        requestQuote(symbol)
        islandExpanded = true
    }

    private fun toggleIslandWatchlist(symbol: String) {
        val quote = quoteFor(symbol)
        val security = Securities.all.firstOrNull { it.symbol == symbol }
        val name = quote?.name ?: security?.name ?: symbol
        val message = if (watchlistStore.contains(symbol)) {
            watchlistStore.remove(symbol)
            "已从自选移除：$name"
        } else {
            when (watchlistStore.add(symbol, name)) {
                WatchlistAddResult.ADDED -> "已加入自选：$name"
                WatchlistAddResult.ALREADY_IN -> "$name 已在自选中"
                WatchlistAddResult.FULL -> "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
            }
        }
        islandWatchlisted = watchlistStore.contains(symbol)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(message)
    }

    private fun contextSymbolsBefore(messageId: String): List<String> =
        viewModel.messages
            .takeWhile { it.id != messageId }
            .flatMap { EntityRecognizer.recognize(it.content) }
            .filter { it.type == EntityType.STOCK }
            .map { it.target }

    private fun startAlertPolling() {
        val generation = ++alertPollGeneration
        pollAlerts(generation)
    }

    private fun pollAlerts(generation: Int) {
        if (generation != alertPollGeneration) return
        alertStore.list().filter { it.enabled }.forEach { rule ->
            quoteRepository.load(rule.symbol) { result ->
                val quote = result.quote ?: return@load
                val trigger = LocalAlertProvider.evaluate(rule, quote) ?: return@load
                val bucket = "${rule.symbol}:${quote.timestamp.take(10)}:${quote.changePercent.toInt()}"
                if (deliveredAlertBuckets.add(bucket)) {
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                        .toast("${trigger.title}：${Format.percent(quote.changePercent)}，已生成归因提示")
                }
            }
        }
        setTimeout(60_000) { pollAlerts(generation) }
    }

    private fun questionBefore(messageId: String): String =
        viewModel.messages
            .takeWhile { it.id != messageId }
            .lastOrNull { it.role == MessageRole.USER }
            ?.content
            .orEmpty()

    private fun beginUnderstandingCorrection(question: String) {
        val text = "请纠正你对这个问题的理解：$question。我的真实意思是："
        setComposerText(text)
        inputRef?.view?.focus()
        keepChatAtBottomTemporarily()
    }

    private fun copyShareCard(content: String) {
        val plain = AiResponseLexer.lex(content, finished = true)
            .filterIsInstance<TextBlock>()
            .joinToString("\n") { it.content.trim() }
            .ifBlank { content.substringBefore("```card").trim() }
        val share = "股问 StockChat\n\n$plain\n\n数据与 AI 解读仅供理解信息，不构成投资建议。"
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).copyToPasteboard(share)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).shareInterpretation(share)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("分享长图已生成，文案也已复制")
    }

    private fun suggestionsAreActive(message: ChatMessage): Boolean {
        val latestAssistant = viewModel.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        return latestAssistant?.id == message.id && !message.streaming && !message.failed && !message.cancelled
    }

    private fun requestQuote(symbol: String) {
        val firstRequest = requestedSymbols.add(symbol)
        if (!firstRequest) return
        if (!liveDataMode) {
            mockQuoteProvider.snapshot(symbol) { quote ->
                val updated = ChatQuoteState(symbol, quote, DataMode.OFFLINE)
                val index = quoteStates.indexOfFirst { it.symbol == symbol }
                if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
                syncIslandCompareCard()
            }
            return
        }
        quoteRepository.load(symbol) { result ->
            val updated = ChatQuoteState(symbol, result.quote, result.mode)
            val index = quoteStates.indexOfFirst { it.symbol == symbol }
            if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
            syncIslandCompareCard()
        }
    }

    private fun toggleCardExpanded(cardKey: String) {
        expandedCardKey = if (expandedCardKey == cardKey) "" else cardKey
    }

    private var islandAnimating = false

    private fun resetIslandMotion(snap: Boolean = false) {
        islandGestureMotion = IslandGestureMotion(revision = ++islandMotionRevision, snap = snap)
        islandAnimating = false
    }

    private fun forceIslandCollapsedForDetailRoute() {
        islandExpanded = false
        resetIslandMotion(snap = true)
    }

    private fun remountIslandCollapsedForDetailRoute() {
        islandMounted = false
        forceIslandCollapsedForDetailRoute()
        setTimeout(16) {
            islandMounted = true
            forceIslandCollapsedForDetailRoute()
        }
    }

    private fun scheduleIslandDetailReturnReset() {
        val resetVersion = ++islandDetailRouteResetVersion
        remountIslandCollapsedForDetailRoute()
        val writeCollapsedFrame: () -> Unit = {
            if (islandDetailRouteActive && resetVersion == islandDetailRouteResetVersion) {
                forceIslandCollapsedForDetailRoute()
            }
        }
        writeCollapsedFrame()
        intArrayOf(16, 80, 180, 360).forEach { delay ->
            setTimeout(delay) { writeCollapsedFrame() }
        }
        setTimeout(520) {
            if (resetVersion == islandDetailRouteResetVersion) {
                forceIslandCollapsedForDetailRoute()
                islandMounted = true
                islandDetailRouteActive = false
            }
        }
    }

    private fun cancelIslandDetailRouteReset() {
        if (!islandDetailRouteActive) return
        islandDetailRouteActive = false
        islandDetailRouteResetVersion++
        islandMounted = true
    }

    private fun toggleIsland() {
        // A tap can be re-delivered to stacked layers while the morph
        // re-layouts; ignore toggles until the animation settles.
        if (islandAnimating || islandGestureMotion.phase != IslandGesturePhase.IDLE) return
        cancelIslandDetailRouteReset()
        islandAnimating = true
        islandExpanded = !islandExpanded
        if (islandExpanded && islandCompareLeftSymbol.isNotEmpty() && islandCompareRightSymbol.isEmpty() && compareCard == null) {
            islandCompareVisible = true
        }
        if (islandExpanded) requestQuote(islandSymbol)
        setTimeout(400) { islandAnimating = false }
    }

    private fun handleIslandGesture(state: String, y: Float) {
        when (state) {
            "start" -> {
                if (
                    !islandExpanded ||
                    islandAnimating ||
                    islandGestureMotion.phase != IslandGesturePhase.IDLE ||
                    isIslandCompareLobbyVisible()
                ) return
                islandGestureStartY = y
                islandGestureMotion = islandGestureMotion.copy(
                    phase = IslandGesturePhase.DRAGGING,
                    offsetY = 0f,
                )
            }
            "move" -> if (islandGestureMotion.phase == IslandGesturePhase.DRAGGING) {
                val maxDown = (pagerData.pageViewHeight * 0.42f).coerceAtLeast(180f)
                islandGestureMotion = islandGestureMotion.copy(
                    phase = IslandGesturePhase.DRAGGING,
                    offsetY = (y - islandGestureStartY).coerceIn(-104f, maxDown),
                )
            }
            "end", "cancel" -> {
                if (islandGestureMotion.phase != IslandGesturePhase.DRAGGING) return
                val deltaY = (y - islandGestureStartY).coerceIn(
                    -104f,
                    (pagerData.pageViewHeight * 0.42f).coerceAtLeast(180f),
                )
                when {
                    state == "end" && deltaY <= -24f -> settleIslandClosedFromGesture()
                    state == "end" && deltaY >= 28f -> openIslandDetailFromGesture(islandSymbol)
                    else -> settleIslandGestureBack()
                }
            }
        }
    }

    private fun settleIslandGestureBack() {
        islandGestureMotion = islandGestureMotion.copy(
            phase = IslandGesturePhase.RETURNING,
            offsetY = 0f,
        )
        // Completion event is authoritative. The timeout is only a renderer
        // fallback and is phase-gated, so stale callbacks cannot move the card.
        setTimeout(280) { completeIslandMotion(ISLAND_ANIMATION_RETURN) }
    }

    private fun settleIslandClosedFromGesture() {
        islandAnimating = true
        islandGestureMotion = islandGestureMotion.copy(
            phase = IslandGesturePhase.CLOSING,
            offsetY = -104f,
        )
        setTimeout(280) { completeIslandMotion(ISLAND_ANIMATION_CLOSE) }
    }

    private fun openIslandDetailFromGesture(symbol: String) {
        if (
            !islandExpanded ||
            islandGestureMotion.phase != IslandGesturePhase.DRAGGING ||
            symbol.isEmpty()
        ) return
        islandGestureMotion = islandGestureMotion.copy(
            phase = IslandGesturePhase.OPENING_DETAIL,
            offsetY = 0f,
        )
        islandAnimating = true

        // The native route starts only after the glass has actually covered
        // the viewport. This timeout is a phase-gated renderer fallback.
        setTimeout(280) { completeIslandMotion(ISLAND_ANIMATION_DETAIL) }
    }

    private fun completeIslandMotion(animationKey: String) {
        when {
            animationKey == ISLAND_ANIMATION_RETURN &&
                islandGestureMotion.phase == IslandGesturePhase.RETURNING -> {
                resetIslandMotion()
            }
            animationKey == ISLAND_ANIMATION_CLOSE &&
                islandGestureMotion.phase == IslandGesturePhase.CLOSING -> {
                islandExpanded = false
                resetIslandMotion()
            }
            animationKey == ISLAND_ANIMATION_DETAIL &&
                islandGestureMotion.phase == IslandGesturePhase.OPENING_DETAIL -> {
                val symbol = islandSymbol
                islandExpanded = false
                islandDetailRouteActive = true
                islandDetailRouteResetVersion++
                openStockDetail(symbol)
                // openPage is synchronous. Reset the covered ChatPage now so
                // the full-screen transform cannot survive until navigation
                // returns; pageDidAppear repeats this as a lifecycle fallback.
                // Snap (no tween): this reset can end up committing right as
                // the page is covered or uncovered, and animating from the
                // full-screen frame at that moment is what reads as the
                // island jumping to the top of the screen.
                remountIslandCollapsedForDetailRoute()
            }
        }
    }

    private fun toggleDataMode() {
        liveDataMode = !liveDataMode
        requestedSymbols.clear()
        quoteStates.clear()
        (viewModel.messages.flatMap { EntityRecognizer.recognize(it.content) }
            .filter { it.type == EntityType.STOCK }
            .map { it.target } + listOfNotNull(
                peekSymbol.takeIf { it.isNotEmpty() },
                islandSymbol.takeIf { it.isNotEmpty() },
                islandCompareLeftSymbol.takeIf { it.isNotEmpty() },
                islandCompareRightSymbol.takeIf { it.isNotEmpty() },
            ))
            .distinct()
            .forEach(::requestQuote)
    }

    /**
     * 左下角 @ / / 按钮（规范 §3.1）：在光标处插入触发字符并唤起对应联想面板。
     *
     * - 触发字符前不是空白时自动补一个空格，否则过不了词法判定（规范 §3.2）；
     * - 当前是另一个触发的半成品（如 "@茅"）时先摘掉它，再插入新触发字符，
     *   避免在文本里留下 "…@茅/" 这种脏片段；
     * - 再点一次同一个字符则收起面板（toggle）。
     */
    private fun onTriggerButtonTapped(trigger: Char) {
        val session = triggerSession
        if (session != null && session.type == trigger) {
            // 命令参数态下再点 / 是"收起命令"，要把命令态一起结束，
            // 否则会留下 paramCommand != null 但面板已关的不一致状态。
            if (assistantPanel == AssistantPanel.COMMAND_PARAMS) clearActiveCommand()
            closeAssistantPanel()
            inputRef?.view?.focus()
            return
        }
        val text = viewModel.inputText
        val cursor = composerEditingState.selectionEnd.takeIf { it in 0..text.length } ?: text.length
        val edit = ComposerTextOperations.insertTrigger(text, cursor, session, trigger)
        // 原子写入「文本 + 光标」，避免 setText 后光标被重置到末尾。
        setComposerText(edit.text, edit.cursor)
        inputRef?.view?.focus()
        updateTriggerSession(edit.text, edit.cursor, false)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    private fun setComposerText(text: String, cursor: Int = text.length) {
        val state = ComposerEditingReducer.programmatic(text, cursor)
        KLog.i(COMPOSER_LOG_TAG, "setComposerText len=${text.length} cursor=${state.selectionEnd} → 命令式写回原生")
        viewModel.inputText = text
        composerEditingState = state
        inputRef?.view?.setTextInputState(state)
    }

    private fun updateComposerEditingState(state: TextInputState) {
        val next = ComposerEditingReducer.nativeState(state)
        KLog.d(COMPOSER_LOG_TAG, "updateEditingState sel=${next.selectionStart}..${next.selectionEnd} comp=${next.compositionStart}..${next.compositionEnd}")
        composerEditingState = next
        viewModel.inputText = next.text
    }

    private fun updateComposerSelectionState(state: TextInputState) {
        if (state.text != composerEditingState.text) {
            KLog.d(COMPOSER_LOG_TAG, "ignore stale selection eventLen=${state.text.length} currentLen=${composerEditingState.text.length}")
            return
        }
        val next = ComposerEditingReducer.nativeSelection(composerEditingState, state)
        composerEditingState = next
        KLog.d(COMPOSER_LOG_TAG, "updateSelection cursor=${next.selectionEnd} sel=${next.selectionStart}..${next.selectionEnd}")
    }

    /**
     * 用户输入编辑的唯一文本入口。
     *
     * 规范：
     * - 文本增删以 `textDidChange(sync)` 为准，保证 Android 上退格/清空也能同步到状态；
     * - Android 固定先抛完整编辑态、再抛文本。重复文本事件必须幂等，
     *   否则会把刚收到的拼音 composition 清空；
     * - 估算分支只在「无 textInputStateChange 先行」的平台/路径上生效；
     * - selectionChange 只记录光标/选区，不回写文本，避免旧选区状态把刚删除的字符写回来。
     */
    private fun handleComposerTextChanged(text: String) {
        commandValidationMessage = ""
        val previous = composerEditingState
        val next = ComposerEditingReducer.nativeText(previous, text)
        KLog.d(
            COMPOSER_LOG_TAG,
            "handleTextChanged oldLen=${previous.text.length} newLen=${text.length}" +
                " oldCursor=${previous.selectionEnd} nextCursor=${next.selectionEnd}"
        )
        if (next == previous) return
        composerEditingState = next
        viewModel.inputText = next.text
        val composing = next.compositionStart != TextInputState.NO_COMPOSITION
        updateTriggerSession(next.text, next.selectionEnd, composing)
    }

    /**
     * 触发状态机主入口（规范 10 §3）。
     *
     * 每次 textDidChange / textInputStateChange 时调用：用光标词法判定识别活动触发会话，
     * 据此切换联想面板（@ 提及 / / 命令选择 / 命令参数槽位）。组合态期间冻结面板
     * 显示"输入中…"，避免拼音候选与 @ 候选互相抖动。
     */
    private fun updateTriggerSession(text: String, cursor: Int, composing: Boolean) {
        triggerComposing = composing
        // 组合态：保留旧 session，仅刷新 composing 标记让面板显示"输入中…"。
        val session = if (composing) triggerSession else TriggerDetector.detect(text, cursor)
        val prev = triggerSession
        val sameTrigger = session != null && prev != null &&
            session.type == prev.type && session.triggerStart == prev.triggerStart &&
            session.query == prev.query
        if (session != null && !sameTrigger) {
            val key = "${session.type}:${session.triggerStart}"
            if (key != lastTrackedTriggerKey) {
                trackComposerEvent(
                    if (session.type == '@') "at_trigger_start" else "slash_trigger_start",
                    "cursor" to session.cursor,
                )
                lastTrackedTriggerKey = key
            }
            if (session.type == '@' && session.query.isNotEmpty()) {
                trackComposerEvent("at_query_len", "len" to session.query.length)
            }
        } else if (prev != null && session == null && !composing) {
            trackComposerEvent(
                if (prev.type == '@') "at_abort" else "slash_abort",
                "reason" to ComposerTextOperations.triggerAbortReason(text, cursor, prev),
                "query" to prev.query,
            )
            lastTrackedTriggerKey = ""
        }
        triggerSession = session
        if (sameTrigger && composing) return
        when (session?.type) {
            '@' -> loadAtCandidates(session)
            '/' -> loadSlashCandidates(session)
            null -> resumeCommandParamsOrClose(text)
        }
    }

    /**
     * 无活动触发会话时的兜底（规范 §5.4）：若 / 命令名仍在文本开头，说明用户正在
     * 填参数（打空格、打文字都不会产生 @ / 触发会话），此时保持参数槽位面板；
     * 命令名被删掉才结束命令态并收起面板。
     */
    private fun resumeCommandParamsOrClose(text: String) {
        val cmd = paramCommand
        if (cmd != null && text.trimStart().startsWith("/${cmd.name}")) {
            assistantPanel = AssistantPanel.COMMAND_PARAMS
            return
        }
        if (cmd != null) clearActiveCommand()
        closeAssistantPanel()
    }

    private fun loadAtCandidates(session: TriggerSession) {
        // 进入 @ 面板时确保 MEDIA 面板关闭（两面板正交，但同屏只展示一个联想区）。
        if (inputPanel == InputPanel.MEDIA) inputPanel = InputPanel.NONE
        val list = AtCandidateProvider.rank(session.query, recentMentions, watchlistStore.symbols())
        atCandidates.clear()
        atCandidates.addAll(list)
        atHighlight = 0
        assistantPanel = AssistantPanel.AT_MENTION
        trackComposerEvent(
            "at_panel_show_src",
            "src" to "local",
            "query_len" to session.query.length,
            "count" to list.size,
        )
    }

    private fun loadSlashCandidates(session: TriggerSession) {
        if (inputPanel == InputPanel.MEDIA) inputPanel = InputPanel.NONE
        val q = session.query
        // 命令名定型（精确命中）→ 进入参数槽位态。
        val resolved = CommandRegistry.resolve(q)
        if (resolved != null && q.isNotBlank() && !q.contains(' ')) {
            if (resolved.hasParams) {
                enterCommandParams(resolved)
            } else {
                slashCandidates.clear()
                slashCandidates.add(resolved)
                slashHighlight = 0
                slashUnknown = ""
                assistantPanel = AssistantPanel.SLASH
            }
            return
        }
        val list = CommandRegistry.filter(q)
        slashCandidates.clear()
        slashCandidates.addAll(list)
        slashHighlight = 0
        slashUnknown = if (list.isEmpty() && q.isNotEmpty()) q else ""
        if (slashUnknown.isNotEmpty() && slashUnknown != lastTrackedUnknownSlash) {
            trackComposerEvent("slash_unknown", "rawInput" to slashUnknown, "didSend" to false)
            lastTrackedUnknownSlash = slashUnknown
        }
        assistantPanel = AssistantPanel.SLASH
    }

    /** 命令定型后的参数态（规范 §5.4）。简化 P1：展示槽位提示，参数由用户继续输入。 */
    private fun enterCommandParams(command: SlashCommand) {
        paramCommand = command
        // 把输入框里的 "/q" 规范为 "/命令名 "（带尾空格，光标移到末尾继续填参数）。
        val text = viewModel.inputText
        val session = triggerSession
        if (session != null && session.type == '/') {
            val before = text.substring(0, session.triggerStart)
            val replacement = "/${command.name} "
            val after = text.substring(session.cursor)
            val newText = before + replacement + after
            val newCursor = before.length + replacement.length
            setComposerText(newText, newCursor)
            // 重置 session：命令名已固化，后续字符进入参数区，不再走 / 触发词法。
            triggerSession = TriggerSession('/', session.triggerStart, command.name, newCursor)
        }
        slashCandidates.clear()
        slashUnknown = ""
        assistantPanel = AssistantPanel.COMMAND_PARAMS
    }

    /**
     * 收起联想面板。
     *
     * 注意：这里不清 [paramCommand]——/ 命令的参数态生命周期由「命令名是否还在文本里」
     * 决定（见 [updateTriggerSession]），否则用户一打空格/文字填参数面板就会消失。
     * 真正清命令的时机是：命令名被删掉、发送完成、注入新问句、收起输入栏。
     */
    private fun closeAssistantPanel() {
        if (assistantPanel == AssistantPanel.NONE && triggerSession == null) return
        assistantPanel = AssistantPanel.NONE
        triggerSession = null
        atCandidates.clear()
        slashCandidates.clear()
        slashUnknown = ""
        atHighlight = 0
        slashHighlight = 0
    }

    /** 结束 / 命令参数态。 */
    private fun clearActiveCommand() {
        paramCommand = null
    }

    /** 选中一个 @ 候选 → 固化 token（规范 §4.6）。 */
    private fun selectAtCandidate(candidate: AtCandidate) {
        val session = triggerSession ?: return
        if (session.type != '@') return
        val entity = MentionEntity.of(candidate.entry)
        val text = viewModel.inputText
        val before = text.substring(0, session.triggerStart)
        val after = text.substring(minOf(session.cursor, text.length))
        val newText = before + entity.mentionText + after
        setComposerText(newText, before.length + entity.mentionText.length)
        mentionEntities.add(entity)
        // 最近提及：提到最前，去重，截断 8 条。
        recentMentions.remove(entity.symbol)
        recentMentions.add(0, entity.symbol)
        if (recentMentions.size > 8) recentMentions.subList(8, recentMentions.size).clear()
        trackComposerEvent(
            "at_select",
            "rank" to atCandidates.indexOf(candidate).takeIf { it >= 0 }?.plus(1),
            "via" to "tap",
            "symbol" to entity.symbol,
            "type" to entity.type.name,
        )
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        closeAssistantPanel()
        // 命令参数态下固化完一个 @ 标的，回到参数面板继续填下一个槽位。
        if (paramCommand != null) assistantPanel = AssistantPanel.COMMAND_PARAMS
    }

    /** 参数态 SECURITY 槽候选：不需要用户手敲 @，点候选后把当前槽查询词替换为 @token。 */
    private fun selectParamSecurityCandidate(candidate: AtCandidate) {
        val command = paramCommand ?: return
        val entity = MentionEntity.of(candidate.entry)
        val text = viewModel.inputText
        val commandPrefix = "/${command.name}"
        val commandStart = text.indexOf(commandPrefix)
        if (commandStart < 0) return
        val commandEnd = commandStart + commandPrefix.length
        val tailStart = (commandEnd + 1).coerceAtMost(text.length)
        val replaceStart = ComposerTextOperations.lastParameterTokenStart(text, tailStart)
        val before = text.substring(0, replaceStart)
        val separator = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val newText = before + separator + entity.mentionText + " "
        setComposerText(newText, newText.length)
        mentionEntities.add(entity)
        recentMentions.remove(entity.symbol)
        recentMentions.add(0, entity.symbol)
        if (recentMentions.size > 8) recentMentions.subList(8, recentMentions.size).clear()
        commandValidationMessage = ""
        assistantPanel = AssistantPanel.COMMAND_PARAMS
        trackComposerEvent(
            "slash_param_complete",
            "commandId" to command.id,
            "paramType" to "SECURITY",
            "symbol" to entity.symbol,
        )
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    private fun selectParamEnumOption(option: String) {
        if (paramCommand == null) return
        val text = viewModel.inputText
        val suffix = if (text.isNotEmpty() && !text.last().isWhitespace()) " $option" else option
        val newText = text + suffix + " "
        setComposerText(newText, newText.length)
        commandValidationMessage = ""
        assistantPanel = AssistantPanel.COMMAND_PARAMS
        trackComposerEvent(
            "slash_param_complete",
            "commandId" to paramCommand?.id,
            "paramType" to "ENUM",
            "value" to option,
        )
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    /** 选中一个 / 命令 → 定型进入参数态或直接执行（规范 §5.3）。 */
    private fun selectSlashCommand(command: SlashCommand) {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        trackComposerEvent("slash_cmd_select", "commandId" to command.id, "via" to "tap")
        when (command.execution) {
            CommandExecution.LOCAL_ACTION -> {
                if (command.hasParams) enterCommandParams(command)
                else handleCommandSideEffect(
                    command,
                    SendPayload("/${command.name}", emptyList(), CommandInvocation(command.id, command.name, emptyMap()), null, deepContextNotes.toList()),
                )
            }
            CommandExecution.CONTEXT_ONLY -> {
                // /深水区：切换上下文标记并提示，不直接发送。
                handleCommandSideEffect(
                    command,
                    SendPayload("/${command.name}", emptyList(), CommandInvocation(command.id, command.name, emptyMap()), null, deepContextNotes.toList()),
                )
            }
            CommandExecution.PROMPT_TEMPLATE -> {
                if (command.hasParams) enterCommandParams(command)
                else sendCommandImmediately(command, emptyMap())
            }
        }
    }

    /** 无参 PROMPT_TEMPLATE 命令：直接渲染并发送。 */
    private fun sendCommandImmediately(command: SlashCommand, args: Map<String, String>) {
        val prompt = CommandRegistry.renderPrompt(command, args)
        val invocation = CommandInvocation(command.id, command.name, args)
        closeAssistantPanel()
        setComposerText("")
        viewModel.send(SendPayload(prompt, mentionEntities.toList(), invocation, prompt, deepContextNotes.toList()))
        keepChatAtBottomTemporarily()
    }

    /**
     * Enter 确认高亮项（规范 §3.6）。
     *
     * 注：规范里的 ↑↓ 移动高亮与 Esc 收起依赖键盘按键事件，而 Kuikly TextArea
     * 只暴露 textDidChange / selectionChange / inputReturn / focus / blur 等输入事件，
     * 无 keyDown 一类按键回调，且移动端本身没有方向键，故这两项暂未接入。
     * 候选选择走点击，收起面板由「删掉触发字符 / 光标移出 / 空格中止」自动完成。
     */
    private fun confirmHighlightedCandidate(): Boolean {
        return when (assistantPanel) {
            AssistantPanel.AT_MENTION -> {
                val c = atCandidates.getOrNull(atHighlight) ?: return false
                selectAtCandidate(c); true
            }
            AssistantPanel.SLASH -> {
                val c = slashCandidates.getOrNull(slashHighlight) ?: return false
                selectSlashCommand(c); true
            }
            else -> false
        }
    }

    /**
     * 构造本次输入的结构化发送载荷（规范 §4.8）。
     *
     * 发送前三道校验交给 [SolidTokenRegistry.verify]：被改过的提及会被剔除。
     * 命令调用从输入文本解析：命中的命令名 + @ 固化提及填 SECURITY 槽 + 文本片段填 TEXT/ENUM 槽。
     */
    private fun buildSendPayload(): SendPayload {
        val rawText = viewModel.inputText
        val verifiedMentions = SolidTokenRegistry.verify(mentionEntities, rawText)
        val command = resolveCommandFromText(rawText, verifiedMentions)
        val payload = if (command != null) {
            val rendered = CommandRegistry.renderPrompt(command.commandId.let { id ->
                CommandRegistry.all.firstOrNull { it.id == id } ?: CommandRegistry.all.first()
            }, command.args)
            SendPayload(rawText, verifiedMentions, command, rendered, deepContextNotes.toList())
        } else {
            SendPayload(rawText, verifiedMentions, null, null, deepContextNotes.toList())
        }
        return payload
    }

    /**
     * 从输入文本解析命令调用（规范 §5.5 P1 简化版）。
     * "/对比 @贵州茅台 @五粮液 估值" → compare, {left:贵州茅台, right:五粮液, dim:估值}
     * SECURITY 槽按 @ 固化提及顺序填，TEXT/ENUM 槽按剩余文本片段填。
     */
    private fun resolveCommandFromText(text: String, mentions: List<MentionEntity>): CommandInvocation? {
        return CommandInvocationParser.parse(text, mentions, ::exactSecurityFragment)
    }

    private fun exactSecurityFragment(fragment: String): Boolean {
        val q = fragment.removePrefix("@")
        return AtCandidateProvider.rank(q, recentMentions, watchlistStore.symbols()).any { candidate ->
            candidate.entry.name == q || candidate.entry.symbol.equals(q, ignoreCase = true)
        }
    }

    private fun currentParamQuery(command: SlashCommand): String {
        return CommandInvocationParser.currentParameterQuery(viewModel.inputText, command.name)
    }

    /**
     * 联想面板渲染（规范 10 §3.4）：按 [assistantPanel] 维度分发到
     * @ 候选 / / 命令 / 命令参数槽位三个子面板。
     */
    private fun renderAssistantPanel(container: ViewContainer<*, *>) {
        when (assistantPanel) {
            AssistantPanel.AT_MENTION -> renderAtCandidateRows(container)
            AssistantPanel.SLASH -> renderSlashCommandRows(container)
            AssistantPanel.COMMAND_PARAMS -> renderCommandParams(container)
            AssistantPanel.NONE -> Unit
        }
    }

    private fun renderAtCandidateRows(container: ViewContainer<*, *>) {
        val page = this
        val query = triggerSession?.query.orEmpty()
        // 面板高度按候选条数算：少了贴合内容（不留空白框），多了截断到上限在框内滚动。
        val panelHeight = if (page.triggerComposing) {
            ASSISTANT_PANEL_EMPTY_HEIGHT
        } else {
            assistantPanelHeight(page.atCandidates.size, CANDIDATE_ROW_HEIGHT)
        }
        container.View {
            attr {
                height(panelHeight)
                marginTop(8f)
                backgroundColor(page.theme.surface)
                borderRadius(12f)
                overflow(true)
            }
            if (page.triggerComposing) {
                View {
                    attr { height(44f); alignItemsCenter(); justifyContentCenter() }
                    Text { attr { text("输入中…"); fontSize(12f); color(page.theme.textTertiary) } }
                }
            } else if (page.atCandidates.isEmpty()) {
                View {
                    attr { height(56f); alignItemsCenter(); justifyContentCenter() }
                    Text {
                        attr {
                            text(if (query.isEmpty()) "没有可推荐的标的" else "没有匹配“$query”的标的")
                            fontSize(12f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            } else {
                Scroller {
                    attr {
                        height(panelHeight)
                        flexDirectionColumn()
                        padding(4f)
                    }
                    page.atCandidates.forEachIndexed { index, candidate ->
                        View {
                            attr {
                                height(CANDIDATE_ROW_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                paddingLeft(12f)
                                paddingRight(12f)
                                backgroundColor(if (index == page.atHighlight) page.theme.brandSoft else Color(0x00000000L, 0f))
                            }
                            event { click { page.selectAtCandidate(candidate) } }
                            if (candidate.entry.kind == MentionType.BOARD) {
                                page.renderBoardCandidateRow(this, candidate, query)
                            } else {
                                page.renderSecurityCandidateRow(this, candidate, query)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 个股/指数候选行：名称（匹配段高亮）· 代码 · 市场 · 涨跌幅 · 来源。
     * 单行横排而非两行堆叠，信息密度对齐 HTML 原型，也避免行内出现大片空白。
     */
    private fun renderSecurityCandidateRow(row: ViewContainer<*, *>, candidate: AtCandidate, query: String) {
        val page = this
        val entry = candidate.entry
        row.View {
            attr { flex(1f); flexDirectionRow(); alignItemsCenter(); marginRight(8f) }
            val (pre, hit, suf) = page.splitHighlight(entry.name, query)
            if (pre.isNotEmpty()) {
                row.Text { attr { text(pre); fontSize(14f); color(page.theme.textPrimary) } }
            }
            if (hit.isNotEmpty()) {
                row.Text { attr { text(hit); fontSize(14f); fontWeightBold(); color(page.theme.brand) } }
            }
            if (suf.isNotEmpty()) {
                row.Text { attr { text(suf); fontSize(14f); color(page.theme.textPrimary) } }
            }
        }
        row.Text {
            attr {
                text(entry.symbol)
                fontSize(11f)
                color(page.theme.textTertiary)
                width(74f)
                textAlignRight()
                marginRight(6f)
            }
        }
        row.View {
            attr {
                paddingLeft(5f); paddingRight(5f); height(16f); marginRight(6f)
                alignItemsCenter(); justifyContentCenter()
                backgroundColor(page.theme.surfaceMuted); borderRadius(4f)
            }
            row.Text { attr { text(entry.market); fontSize(9f); color(page.theme.textSecondary) } }
        }
        // 涨跌幅右对齐定宽：红涨绿跌（中国习惯），无涨跌的板块走另一分支。
        row.Text {
            attr {
                text(page.formatChgPct(entry.chgPct))
                fontSize(12f)
                fontWeightSemiBold()
                color(page.chgColor(entry.chgPct))
                width(48f)
                textAlignRight()
                marginRight(6f)
            }
        }
        row.View {
            attr {
                paddingLeft(5f); paddingRight(5f); height(16f)
                alignItemsCenter(); justifyContentCenter()
                backgroundColor(page.theme.surfaceMuted); borderRadius(4f)
            }
            row.Text { attr { text(candidate.source); fontSize(9f); color(page.theme.textSecondary) } }
        }
    }

    /** 板块聚合行：板块无涨跌，用成分数量补位，避免右端空一大块。 */
    private fun renderBoardCandidateRow(row: ViewContainer<*, *>, candidate: AtCandidate, query: String) {
        val page = this
        val entry = candidate.entry
        row.View {
            attr { flex(1f); flexDirectionRow(); alignItemsCenter(); marginRight(8f) }
            val (pre, hit, suf) = page.splitHighlight(entry.name, query)
            if (pre.isNotEmpty()) {
                row.Text { attr { text(pre); fontSize(14f); color(page.theme.brand) } }
            }
            if (hit.isNotEmpty()) {
                row.Text { attr { text(hit); fontSize(14f); fontWeightBold(); color(page.theme.brand) } }
            }
            if (suf.isNotEmpty()) {
                row.Text { attr { text(suf); fontSize(14f); color(page.theme.brand) } }
            }
        }
        row.Text {
            attr {
                text("共 ${entry.boardCount} 只")
                fontSize(11f)
                color(page.theme.textTertiary)
                marginRight(6f)
            }
        }
        row.View {
            attr {
                paddingLeft(5f); paddingRight(5f); height(16f); marginRight(6f)
                alignItemsCenter(); justifyContentCenter()
                backgroundColor(page.theme.brandSoft); borderRadius(4f)
            }
            row.Text { attr { text("板块"); fontSize(9f); color(page.theme.brand) } }
        }
        row.View {
            attr {
                paddingLeft(5f); paddingRight(5f); height(16f)
                alignItemsCenter(); justifyContentCenter()
                backgroundColor(page.theme.surfaceMuted); borderRadius(4f)
            }
            row.Text { attr { text(candidate.source); fontSize(9f); color(page.theme.textSecondary) } }
        }
    }

    /** 把名称按 query 切成「前缀 / 命中段 / 后缀」，命中段单独着色加粗。 */
    private fun splitHighlight(name: String, query: String): Triple<String, String, String> {
        if (query.isEmpty()) return Triple(name, "", "")
        val i = name.indexOf(query, ignoreCase = true)
        if (i < 0) return Triple(name, "", "")
        return Triple(
            name.substring(0, i),
            name.substring(i, i + query.length),
            name.substring(i + query.length),
        )
    }

    /** 涨跌幅格式化：+2.8% / -4.1% / --（无涨跌）。手写取整避免 Float 直转的长尾小数。 */
    private fun formatChgPct(pct: Float?): String {
        if (pct == null) return "--"
        val sign = if (pct >= 0f) "+" else "−"
        val abs = if (pct >= 0f) pct else -pct
        val int = abs.toInt()
        val dec = ((abs - int) * 10).toInt()
        return "$sign$int.$dec%"
    }

    private fun chgColor(pct: Float?): Color = when {
        pct == null -> theme.textTertiary
        pct > 0f -> theme.rise
        pct < 0f -> theme.fall
        else -> theme.textSecondary
    }

    private fun renderSlashCommandRows(container: ViewContainer<*, *>) {
        val page = this
        // 与 @ 面板同理：按条数算高度，命令只有 8 条但全展开太长，截断到上限在框内滚动。
        val panelHeight = if (page.slashUnknown.isNotEmpty()) {
            UNKNOWN_COMMAND_PANEL_HEIGHT
        } else {
            assistantPanelHeight(page.slashCandidates.size, COMMAND_ROW_HEIGHT)
        }
        container.View {
            attr {
                height(panelHeight)
                marginTop(8f)
                backgroundColor(page.theme.surface)
                borderRadius(12f)
                overflow(true)
            }
            if (page.slashUnknown.isNotEmpty()) {
                View {
                    attr { padding(10f); flexDirectionColumn() }
                    Text { attr { text("未识别命令：/${page.slashUnknown}"); fontSize(12f); color(page.theme.textSecondary) } }
                    Text { attr { text("将作为普通文本发送"); fontSize(10f); color(page.theme.textTertiary) } }
                    val suggestions = CommandRegistry.suggest(page.slashUnknown)
                    if (suggestions.isNotEmpty()) {
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
                            Text { attr { text("你是不是想用"); fontSize(10f); color(page.theme.textTertiary); marginRight(6f) } }
                            suggestions.forEach { command ->
                                View {
                                    attr {
                                        height(24f)
                                        marginRight(6f)
                                        paddingLeft(8f)
                                        paddingRight(8f)
                                        allCenter()
                                        backgroundColor(page.theme.brandSoft)
                                        borderRadius(7f)
                                    }
                                    Text { attr { text("/${command.name}"); fontSize(11f); color(page.theme.brand) } }
                                    event { click { page.selectSlashCommand(command) } }
                                }
                            }
                        }
                    }
                }
            } else if (page.slashCandidates.isEmpty()) {
                View {
                    attr { height(36f); alignItemsCenter(); justifyContentCenter() }
                    Text { attr { text("输入 / 唤起指令"); fontSize(12f); color(page.theme.textTertiary) } }
                }
            } else {
                Scroller {
                    attr {
                        height(panelHeight)
                        flexDirectionColumn()
                        padding(4f)
                    }
                    page.slashCandidates.forEachIndexed { index, command ->
                        View {
                            attr {
                                height(COMMAND_ROW_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f)
                                backgroundColor(if (index == page.slashHighlight) page.theme.brandSoft else Color(0x00000000L, 0f))
                            }
                            event { click { page.selectSlashCommand(command) } }
                            View {
                                attr { width(28f); height(28f); marginRight(10f); alignItemsCenter(); justifyContentCenter(); backgroundColor(page.theme.brandSoft); borderRadius(8f) }
                                Text { attr { text(command.icon); fontSize(14f); color(page.theme.brand) } }
                            }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text { attr { text("/${command.name}"); fontSize(13f); color(page.theme.textPrimary) } }
                                Text { attr { text(command.desc); fontSize(10f); color(page.theme.textTertiary) } }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderCommandParams(container: ViewContainer<*, *>) {
        val command = paramCommand ?: return
        val page = this
        // 参数值实时解析：与发送时同一套 resolveCommandFromText，面板所见即所发。
        val args = resolveCommandFromText(
            viewModel.inputText,
            SolidTokenRegistry.verify(mentionEntities, viewModel.inputText),
        )?.args.orEmpty()
        val missing = missingRequiredParams(command, args)
        val currentKey = missing.firstOrNull()?.key ?: command.params.firstOrNull { args[it.key].isNullOrBlank() }?.key
        val currentParam = command.params.firstOrNull { it.key == currentKey }
        // 槽位数量由命令 schema 决定、不会跳动，高度按条数算：
        // 标题 32f + 每槽 40f + 底部提示 36f，超出上限则在框内滚动。
        val wanted = 32f + command.params.size * 40f + 36f +
            if (currentParam?.type == ParamType.SECURITY) {
                18f + ASSISTANT_PANEL_MAX_ROWS * (CANDIDATE_ROW_HEIGHT + 4f)
            } else {
                0f
            }
        container.Scroller {
            attr {
                height(minOf(wanted, ASSISTANT_PANEL_MAX_ROWS * COMMAND_ROW_HEIGHT + ASSISTANT_PANEL_PADDING))
                marginTop(8f)
                flexDirectionColumn()
                backgroundColor(page.theme.surface)
                borderRadius(12f)
                padding(10f)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter(); marginBottom(8f) }
                View {
                    attr { width(24f); height(24f); marginRight(8f); alignItemsCenter(); justifyContentCenter(); backgroundColor(page.theme.brandSoft); borderRadius(6f) }
                    Text { attr { text(command.icon); fontSize(12f); color(page.theme.brand) } }
                }
                Text { attr { text("/${command.name} · 参数"); fontSize(13f); color(page.theme.textPrimary) } }
            }
            command.params.forEach { param ->
                val filled = args[param.key].orEmpty()
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(4f)
                        padding(6f)
                        backgroundColor(
                            when {
                                filled.isNotEmpty() -> page.theme.brandSoft
                                param.key == currentKey -> page.theme.surface
                                else -> page.theme.surfaceMuted
                            }
                        )
                        borderRadius(8f)
                    }
                    View { attr { flex(1f); flexDirectionColumn() }
                        Text { attr { text(param.label + if (param.required) " *" else "（可选）"); fontSize(11f); color(if (param.key == currentKey) page.theme.brand else page.theme.textSecondary) } }
                        Text { attr { text(if (filled.isNotEmpty()) filled else param.placeholder); fontSize(12f); color(if (filled.isNotEmpty()) page.theme.textPrimary else page.theme.textTertiary) } }
                    }
                    Text { attr { text(when (param.type) { ParamType.SECURITY -> "@" ; ParamType.ENUM -> "选" ; else -> "文" }); fontSize(9f); color(page.theme.textTertiary) } }
                }
            }
            if (currentParam?.type == ParamType.SECURITY) {
                val query = page.currentParamQuery(command)
                val candidates = AtCandidateProvider.rank(query, page.recentMentions, page.watchlistStore.symbols()).take(ASSISTANT_PANEL_MAX_ROWS)
                Text {
                    attr {
                        text(if (query.isEmpty()) "选择${currentParam.label}" else "匹配「$query」")
                        marginTop(10f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
                candidates.forEachIndexed { index, candidate ->
                    View {
                        attr {
                            height(CANDIDATE_ROW_HEIGHT)
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(4f)
                            paddingLeft(10f)
                            paddingRight(10f)
                            backgroundColor(if (index == 0) page.theme.brandSoft else page.theme.surfaceMuted)
                            borderRadius(8f)
                        }
                        event { click { page.selectParamSecurityCandidate(candidate) } }
                        if (candidate.entry.kind == MentionType.BOARD) {
                            page.renderBoardCandidateRow(this, candidate, query)
                        } else {
                            page.renderSecurityCandidateRow(this, candidate, query)
                        }
                    }
                }
            } else if (currentParam?.type == ParamType.ENUM) {
                Text {
                    attr {
                        text("选择${currentParam.label}")
                        marginTop(10f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(6f) }
                    currentParam.enumOptions.forEach { option ->
                        View {
                            attr {
                                height(28f)
                                marginRight(7f)
                                paddingLeft(11f)
                                paddingRight(11f)
                                allCenter()
                                backgroundColor(page.theme.brandSoft)
                                borderRadius(8f)
                            }
                            Text { attr { text(option); fontSize(12f); fontWeightMedium(); color(page.theme.brand) } }
                            event { click { page.selectParamEnumOption(option) } }
                        }
                    }
                }
            }
            View {
                attr { marginTop(8f); alignItemsCenter(); justifyContentCenter(); height(28f) }
                Text {
                    attr {
                        text(if (missing.isEmpty()) "必填已完成，可继续补可选参数或发送" else "继续输入 @标的 或文字填充必填参数")
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
            }
        }
    }

    private fun cycleGlassMode() {
        glassModeManuallySelected = true
        glassMode = when (glassMode) {
            GlassRenderingMode.REALTIME -> GlassRenderingMode.SNAPSHOT
            GlassRenderingMode.SNAPSHOT -> GlassRenderingMode.SIMPLIFIED
            GlassRenderingMode.SIMPLIFIED -> GlassRenderingMode.REALTIME
        }
    }

    private fun setFocusedCard(cardKey: String, focused: Boolean) {
        focusedCardKey = if (focused) cardKey else ""
    }

    private fun handleCompareCandidate(cardKey: String, symbol: String) {
        requestQuote(symbol)
        if (compareCandidateSymbol.isEmpty() || compareCandidateSymbol == symbol) {
            compareCandidateKey = cardKey
            compareCandidateSymbol = symbol
            compareCard = null
            return
        }
        val left = quoteFor(compareCandidateSymbol)
        val right = quoteFor(symbol)
        if (left != null && right != null) {
            compareCard = StockCompareCardModel(listOf(left, right), "active-compare:${left.symbol}:${right.symbol}")
            compareCandidateKey = ""
            compareCandidateSymbol = ""
            focusedCardKey = ""
            requestCompareInsightIfNeeded(left, right)
        } else {
            requestQuote(compareCandidateSymbol)
            requestQuote(symbol)
        }
    }

    private fun handleCardEvent(cardKey: String, event: CardEvent) {
        when (event) {
            is CardEvent.FocusStart -> acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
            is CardEvent.FocusEnd -> if (focusedCardKey == cardKey) focusedCardKey = ""
            else -> Unit
        }
    }

    private fun injectQuestion(question: String) {
        setComposerText(question)
        // 注入新文本后旧固化提及与命令态失效，统一清空输入期状态。
        mentionEntities.clear()
        clearActiveCommand()
        closeAssistantPanel()
        inputPanel = InputPanel.NONE
        // 推荐问句/联想词/历史标的：统一从 expandComposer 进入，输入态与键盘一起到位。
        expandComposer(requestFocus = true)
    }

    private fun clearCompare() {
        clearCompareExperience()
    }

    private fun openCardSheet(model: CardModel, deferInteraction: Boolean = false) {
        val version = ++sheetPresentationVersion
        sheetMounted = false
        sheetCard = model
        sheetLevel = if (model.cardType == "stock-chart") SheetLevel.FULL else SheetLevel.HALF
        sheetPresented = !deferInteraction
        sheetInteractive = !deferInteraction
        if (!deferInteraction) {
            setTimeout(0) {
                if (sheetPresentationVersion == version) sheetMounted = true
            }
        }
    }

    private fun dismissCardSheet() {
        val version = ++sheetPresentationVersion
        sheetInteractive = false
        sheetPresented = false
        setTimeout(420) {
            if (sheetPresentationVersion == version && !sheetPresented) {
                sheetMounted = false
                sheetCard = null
            }
        }
    }

    private fun raiseCardSheet() {
        sheetLevel = when (sheetLevel) {
            SheetLevel.PEEK -> SheetLevel.HALF
            SheetLevel.HALF -> SheetLevel.FULL
            SheetLevel.FULL -> SheetLevel.FULL
        }
    }

    private fun lowerCardSheet() {
        when (sheetLevel) {
            SheetLevel.FULL -> sheetLevel = SheetLevel.HALF
            SheetLevel.HALF -> sheetLevel = SheetLevel.PEEK
            SheetLevel.PEEK -> dismissCardSheet()
        }
    }

    private fun handleSheetPan(state: String, y: Float) {
        when (state) {
            "start" -> sheetPanStartY = y
            "end" -> when {
                y - sheetPanStartY <= -28f -> raiseCardSheet()
                y - sheetPanStartY >= 28f -> lowerCardSheet()
            }
        }
    }

    private fun toggleDrill(drillKey: String) {
        val index = drilledKeys.indexOf(drillKey)
        if (index >= 0) drilledKeys.removeAt(index) else drilledKeys.add(drillKey)
    }

    private fun startSubThread(model: CardModel) {
        val insight = model as? InsightCardModel ?: return
        val existing = subThreads.indexOfFirst { it.cardId == insight.cardId }
        if (existing >= 0) {
            subThreads[existing] = subThreads[existing].copy(collapsed = false)
            return
        }
        val state = SubThreadState(insight.cardId, "分支：AI 解读深挖", "", "正在生成深入解读…", streaming = true)
        subThreads.add(state)
        requestSubThread(insight.cardId, "请围绕以下解读继续深入说明：${insight.summary}")
    }

    private fun toggleSubThread(cardId: String) {
        val index = subThreads.indexOfFirst { it.cardId == cardId }
        if (index >= 0) subThreads[index] = subThreads[index].copy(collapsed = !subThreads[index].collapsed)
    }

    private fun updateSubThreadInput(cardId: String, input: String) {
        val index = subThreads.indexOfFirst { it.cardId == cardId }
        if (index >= 0) subThreads[index] = subThreads[index].copy(input = input)
    }

    private fun sendSubThread(cardId: String) {
        val state = subThreads.firstOrNull { it.cardId == cardId } ?: return
        if (state.streaming || state.input.isBlank()) return
        val index = subThreads.indexOfFirst { it.cardId == cardId }
        subThreads[index] = state.copy(response = "正在生成深入解读…", input = "", streaming = true, collapsed = false)
        requestSubThread(cardId, state.input)
    }

    private fun retryCard(messageId: String, blockId: String, cardType: String, rawCard: String) {
        val key = "$messageId:$blockId"
        if (repairingCardKey.isNotEmpty()) return
        repairingCardKey = key
        viewModel.retryCard(
            messageId = messageId,
            blockId = blockId,
            cardType = cardType.ifBlank { "stock-quote" },
            rawCard = rawCard,
            onDone = {
                repairingCardKey = ""
                keepChatAtBottomTemporarily()
            },
            onError = { error ->
                repairingCardKey = ""
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(error)
            },
        )
    }

    private fun requestSubThread(cardId: String, prompt: String) {
        var response = ""
        viewModel.askSubThread(
            prompt = prompt,
            onDelta = { delta ->
                response += delta
                updateSubThread(cardId) { it.copy(response = response, streaming = true) }
            },
            onDone = { updateSubThread(cardId) { it.copy(response = response.ifBlank { "暂未生成内容" }, streaming = false) } },
            onError = { error -> updateSubThread(cardId) { it.copy(response = error, streaming = false) } },
        )
    }

    private fun retryCompareInsight() {
        val cardQuotes = compareCard?.quotes.orEmpty()
        val left = cardQuotes.getOrNull(0) ?: quoteFor(islandCompareLeftSymbol) ?: return
        val right = cardQuotes.getOrNull(1) ?: quoteFor(islandCompareRightSymbol) ?: return
        compareInsightPairKey = ""
        requestCompareInsightIfNeeded(left, right)
    }

    private fun resetCompareInsight() {
        compareInsightVersion += 1
        compareInsightPairKey = ""
        compareInsightState = CompareInsightState.IDLE
        compareInsightText = ""
        compareInsightError = ""
    }

    private fun requestCompareInsightIfNeeded(left: Quote, right: Quote) {
        val pairKey = "${left.symbol}:${right.symbol}"
        if (compareInsightPairKey == pairKey && compareInsightState != CompareInsightState.ERROR) return
        compareInsightPairKey = pairKey
        compareInsightState = CompareInsightState.LOADING
        compareInsightText = ""
        compareInsightError = ""
        val requestVersion = ++compareInsightVersion
        var response = ""
        viewModel.askSubThread(
            prompt = buildCompareInsightPrompt(left, right),
            onDelta = { delta ->
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@askSubThread
                response += delta
                compareInsightText = response
            },
            onDone = {
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@askSubThread
                compareInsightText = response.ifBlank { "暂未生成对比解读" }
                compareInsightState = CompareInsightState.READY
            },
            onError = { error ->
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@askSubThread
                compareInsightError = error
                compareInsightState = CompareInsightState.ERROR
            },
        )
    }

    private fun buildCompareInsightPrompt(left: Quote, right: Quote): String {
        return """
            请基于以下两只股票的即时行情做一个简洁对比解读。
            要求：
            1. 只解释差异和可能关注点，不给买卖建议。
            2. 用 3 到 5 句中文，适合显示在手机卡片里。
            3. 明确说明价格、涨跌幅、日内高低点、成交额、换手率里的关键差异。

            股票 A：${left.name} ${left.symbol}
            价格：${Format.price(left.price)}
            涨跌幅：${Format.percent(left.changePercent)}
            日内高低：${Format.price(left.high)} / ${Format.price(left.low)}
            成交额：${Format.compactAmount(left.amount)}
            换手率：${Format.decimal(left.turnoverRate, 2)}%

            股票 B：${right.name} ${right.symbol}
            价格：${Format.price(right.price)}
            涨跌幅：${Format.percent(right.changePercent)}
            日内高低：${Format.price(right.high)} / ${Format.price(right.low)}
            成交额：${Format.compactAmount(right.amount)}
            换手率：${Format.decimal(right.turnoverRate, 2)}%
        """.trimIndent()
    }

    private fun updateSubThread(cardId: String, update: (SubThreadState) -> SubThreadState) {
        val index = subThreads.indexOfFirst { it.cardId == cardId }
        if (index >= 0) subThreads[index] = update(subThreads[index])
    }

    private fun quoteFor(symbol: String): Quote? {
        return quoteStates.firstOrNull { it.symbol == symbol }?.quote ?: quoteRepository.cachedOrOffline(symbol)
    }

}

private enum class EntityAction { DETAIL, PREVIEW, ISLAND, MENTION, COMPARE }

private enum class CompareInsightState { IDLE, LOADING, READY, ERROR }

internal enum class SheetLevel(val ratio: Float, val density: CardDensity) {
    PEEK(0.25f, CardDensity.MINI),
    HALF(0.50f, CardDensity.COMPACT),
    FULL(0.90f, CardDensity.FULL),
}

private data class ChatQuoteState(
    val symbol: String,
    val quote: Quote?,
    val mode: DataMode,
)


/** MEDIA（相册/拍照面板）维度；@ / / 联想面板走 [AssistantPanel]，两者正交。 */
private enum class InputPanel { NONE, MEDIA }

/**
 * 联想面板最多可见行数：候选条数超过这个行数后，面板定高、超出部分在框内滚动。
 * 固定为 3 行——既避免面板把输入栏顶得太高，也能覆盖绝大多数"输入几个字即命中"的场景。
 *
 * 注意不能用 `maxHeight`：Kuikly 的 Scroller contentView 是绝对定位、高度由内容决定，
 * 实测 `maxHeight` 压不住，面板会被撑到完整高度。所以这里按候选条数**算出实际高度**
 * 再用 `height()` 定死——候选少时贴合内容（不留空白框），多到 3 行就截断滚动。
 */
private const val ASSISTANT_PANEL_MAX_ROWS = 3

/** 候选行高：单行横排 5 项信息（名称/代码/市场/涨跌/来源）。 */
private const val CANDIDATE_ROW_HEIGHT = 42f

/** 面板上下 padding 之和（attr 里 padding(4f) 上下各 4f）。 */
private const val ASSISTANT_PANEL_PADDING = 8f

/** 命令面板行高：图标 + 命令名/描述两行文字，比候选行高一些。 */
private const val COMMAND_ROW_HEIGHT = 52f

/** 空态/组合态的面板高度：比一整行候选略高，避免只有一行文字却占满屏。 */
private const val ASSISTANT_PANEL_EMPTY_HEIGHT = 52f

/** 未知命令态需要容纳解释文字和近似建议。 */
private const val UNKNOWN_COMMAND_PANEL_HEIGHT = 88f

/** 按候选条数算面板高度：少了贴合，多到 [ASSISTANT_PANEL_MAX_ROWS] 行截断滚动。 */
private fun assistantPanelHeight(rowCount: Int, rowHeight: Float): Float {
    if (rowCount <= 0) return ASSISTANT_PANEL_EMPTY_HEIGHT
    val wanted = rowCount * rowHeight + ASSISTANT_PANEL_PADDING
    val capped = ASSISTANT_PANEL_MAX_ROWS * rowHeight + ASSISTANT_PANEL_PADDING
    return minOf(wanted, capped)
}

/**
 * 联想面板维度（规范 10 §2）：与 [InputPanel]（控制 MEDIA/默认态）正交。
 * @ 提及、/ 命令选择、/ 命令参数槽位三态在此维度切换。
 */
private enum class AssistantPanel { NONE, AT_MENTION, SLASH, COMMAND_PARAMS }

private enum class ComposerMediaAction(val source: String, val label: String) {
    PHOTO_LIBRARY("library", "选照片"),
    CAMERA("camera", "拍照"),
}

private fun ViewContainer<*, *>.MediaInputRow(theme: StockChatTheme, onSelect: (ComposerMediaAction) -> Unit) {
    View {
        attr { marginTop(8f); padding(8f); flexDirectionRow(); backgroundColor(theme.surface); borderRadius(12f) }
        listOf(ComposerMediaAction.PHOTO_LIBRARY, ComposerMediaAction.CAMERA).forEachIndexed { index, action ->
            View {
                attr {
                    flex(1f)
                    height(42f)
                    if (index == 0) marginRight(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    backgroundColor(theme.brandSoft)
                    borderRadius(10f)
                }
                View {
                    attr {
                        size(26f, 26f)
                        marginRight(8f)
                        allCenter()
                        backgroundColor(theme.surface)
                        borderRadius(13f)
                    }
                    if (action == ComposerMediaAction.PHOTO_LIBRARY) {
                        LineIconPhoto(color = theme.brand, size = 15f)
                    } else {
                        LineIconCamera(color = theme.brand, size = 15f)
                    }
                }
                Text { attr { text(action.label); fontSize(13f); fontWeightMedium(); color(theme.brand) } }
                event { click { onSelect(action) } }
            }
        }
    }
}
