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
import com.kuikly.stockchat.cards.core.InsightCardModel
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
import com.kuikly.stockchat.chat.ChatViewModel
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.StreamState
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteRepositoryStore
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.mock.MockQuoteProvider
import com.kuikly.stockchat.page.components.ChatDrawer
import com.kuikly.stockchat.page.components.ChatTopNav
import com.kuikly.stockchat.page.components.LineIconPlus
import com.kuikly.stockchat.page.components.LineIconMic
import com.kuikly.stockchat.page.components.LineIconCamera
import com.kuikly.stockchat.page.components.LineIconPhoto
import com.kuikly.stockchat.page.components.LineIconSend
import com.kuikly.stockchat.page.components.LineIconStop
import com.kuikly.stockchat.page.components.LineIconRecordingDot
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
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.PI

@Page(Routes.CHAT, supportInLocal = true)
internal class ChatPage : BasePager() {
    private val viewModel by lazy { ChatViewModel(pagerId) }
    private lateinit var inputRef: ViewRef<TextAreaView>
    private var chatScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    private var chatContentHeight = 0f
    private var keepChatAtBottomVersion = 0
    private var peekSymbol: String by observable("")
    private var peekVisible: Boolean by observable(false)
    // A long-pressed stock opens its quote in the global glass sheet.  Keep the
    // symbol while a live quote is loading so the interaction never gets lost.
    private var pendingEntitySheetSymbol: String by observable("")
    // Symbol whose long press was recognised but whose gesture has not ended
    // yet. The sheet is only presented on "end" so the finger-up never lands
    // on the card that is about to mount.
    private var pendingLongPressSymbol: String = ""
    // The long-press gesture's terminal touch can bleed through into the freshly
    // mounted sheet and trigger its onOpenStock (jumping to the detail page).
    // Keep the sheet non-interactive for a short window after it opens so that
    // the releasing finger can never reach the sheet content.
    private var sheetInteractive: Boolean by observable(false)
    private var sheetPresentationVersion = 0
    private var ambiguousSymbols: ObservableList<String> by observableList()
    private var ambiguousEntityText: String by observable("")
    private var ambiguousAction: EntityAction by observable(EntityAction.PREVIEW)
    private var keyboardHeight: Float by observable(0f)
    private var drawerOpen: Boolean by observable(false)
    private var liveDataMode: Boolean by observable(true)
    // Page data is injected after construction; use the safe fallback until created().
    private var glassMode: GlassRenderingMode by observable(GlassRenderingMode.SIMPLIFIED)
    private var glassModeManuallySelected = false
    private var inputPanel: InputPanel by observable(InputPanel.NONE)
    private var inputFocused: Boolean by observable(false)
    // Composer extras: voice input mock state.
    private var voiceActive: Boolean by observable(false)
    private var expandedCardKey: String by observable("")
    private var focusedCardKey: String by observable("")
    private var repairingCardKey: String by observable("")
    private var compareCandidateKey: String by observable("")
    private var compareCandidateSymbol: String by observable("")
    private var compareCard: StockCompareCardModel? by observable(null)
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
    private val quoteRepository by lazy { QuoteRepositoryStore.shared(pagerId) }
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

    override fun pageDidAppear() {
        super.pageDidAppear()
        viewModel.refreshConfigStatus()
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
                        onEntityStock = { page.handleStockEntityClick(it) },
                        onEntityStockLongPress = { entity, state, cancelled -> page.handleStockEntityLongPress(entity, state, cancelled) },
                        onCardStock = { page.openStockDetail(it) },
                        onTerm = { page.viewModel.send("$it 是什么意思") },
                        onSuggestion = { page.viewModel.send(it) },
                        onRetry = { page.viewModel.retryLast() },
                        repairingCardKey = page.repairingCardKey,
                        onRetryCard = { messageId, blockId, cardType, rawCard -> page.retryCard(messageId, blockId, cardType, rawCard) },
                        onQuoteNeeded = { page.requestQuote(it) },
                        quoteFor = { page.quoteFor(it) },
                        isCardExpanded = { page.expandedCardKey == it },
                        onToggleCardExpanded = { page.toggleCardExpanded(it) },
                        onOpenCardSheet = { page.openCardSheet(it) },
                        drilledKeys = page.drilledKeys.toSet(),
                        onToggleDrill = { page.toggleDrill(it) },
                        subThreads = page.subThreads.toList(),
                        onStartSubThread = { page.startSubThread(it) },
                        onToggleSubThread = { page.toggleSubThread(it) },
                        onUpdateSubThreadInput = { id, input -> page.updateSubThreadInput(id, input) },
                        onSendSubThread = { page.sendSubThread(it) },
                        focusedCardKey = page.focusedCardKey,
                        onFocusChanged = { key, focused -> page.setFocusedCard(key, focused) },
                        compareCandidateSymbol = page.compareCandidateSymbol,
                        onCompareCandidate = { key, symbol -> page.handleCompareCandidate(key, symbol) },
                        onCardEvent = { key, event -> page.handleCardEvent(key, event) },
                    )
                }
            }
            // Declare the overlay after its backdrop source so Android paints
            // the glass above the moving conversation rather than beneath it.
            ChatTopNav(
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                drawerOpen = page.drawerOpen,
                liveData = page.liveDataMode,
                renderer = page.glassRenderer,
                contextTitle = if (page.drilledKeys.isNotEmpty()) "归因链 · 资金面 ›" else null,
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
                    ActiveComparePanel(compareModel, page.theme, { page.openStockDetail(it) }) { page.clearCompare() }
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
                    }
                    GlassBackdrop(page.theme.glass.sheet, page.glassRenderer)
                        vif({ page.isComposerExpanded() }) {
                            RecentSymbolRow(page.theme) { text -> page.injectQuestion(text) }
                        }
                        vif({ page.isComposerExpanded() && (page.inputPanel == InputPanel.SYMBOL || page.inputPanel == InputPanel.COMMAND) }) {
                            InputAssistantRow(page.inputPanel, page.theme) { value -> page.injectQuestion(value) }
                        }
                        vif({ page.isComposerExpanded() && page.inputPanel == InputPanel.MEDIA }) {
                            MediaInputRow(page.theme) { action -> page.handleMediaAction(action) }
                        }
                        vif({ !page.isComposerExpanded() }) {
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    marginTop(8f)
                                }
                                View {
                                    attr {
                                        size(32f, 32f)
                                        marginRight(7f)
                                        allCenter()
                                        borderRadius(16f)
                                        backgroundColor(page.theme.surfaceMuted)
                                    }
                                    LineIconPlus(color = page.theme.textSecondary, size = 16f)
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        height(44f)
                                        paddingLeft(12f)
                                        paddingRight(12f)
                                        justifyContentCenter()
                                        borderRadius(16f)
                                        backgroundColor(Color(0xFFFFFFFF, 0f))
                                    }
                                    page.renderComposerTextArea(this)
                                    event { click { page.expandComposer(requestFocus = true) } }
                                }
                                View {
                                    attr {
                                        size(32f, 32f)
                                        marginLeft(7f)
                                        allCenter()
                                        borderRadius(16f)
                                        backgroundColor(if (page.voiceActive) page.theme.rise else page.theme.surfaceMuted)
                                    }
                                    vif({ page.voiceActive }) {
                                        LineIconRecordingDot(color = Color(0xFFFFFFFF), size = 11f)
                                    }
                                    vif({ !page.voiceActive }) {
                                        LineIconMic(color = page.theme.textSecondary, size = 17f)
                                    }
                                    event { click { page.voiceActive = !page.voiceActive } }
                                }
                                View {
                                    attr {
                                        size(32f, 32f)
                                        marginLeft(7f)
                                        allCenter()
                                        borderRadius(16f)
                                        backgroundColor(if (page.inputPanel == InputPanel.MEDIA) page.theme.brandSoft else page.theme.surfaceMuted)
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
                                attr {
                                    minHeight(44f)
                                    paddingLeft(12f)
                                    paddingRight(12f)
                                    justifyContentCenter()
                                    borderRadius(16f)
                                    backgroundColor(Color(0xFFFFFFFF, 0f))
                                }
                                page.renderComposerTextArea(this)
                                event { click { page.expandComposer(requestFocus = true) } }
                            }
                            View {
                                attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(page.theme.brandSoft)
                                    }
                                    Text { attr { text("@"); fontSize(16f); fontWeightSemiBold(); color(page.theme.brand) } }
                                }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        marginLeft(8f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(page.theme.brandSoft)
                                    }
                                    Text { attr { text("/"); fontSize(16f); fontWeightSemiBold(); color(page.theme.brand) } }
                                }
                                View { attr { flex(1f) } }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        marginRight(6f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(if (page.voiceActive) page.theme.rise else page.theme.surfaceMuted)
                                    }
                                    vif({ page.voiceActive }) {
                                        LineIconRecordingDot(color = Color(0xFFFFFFFF), size = 12f)
                                    }
                                    vif({ !page.voiceActive }) {
                                        LineIconMic(color = page.theme.textSecondary, size = 19f)
                                    }
                                    event { click { page.voiceActive = !page.voiceActive } }
                                }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        marginRight(4f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(if (page.inputPanel == InputPanel.MEDIA) page.theme.brandSoft else page.theme.surfaceMuted)
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
                                        backgroundColor(if (page.viewModel.streamState == StreamState.STREAMING) page.theme.divider else page.theme.brand)
                                        boxShadow(BoxShadow(0f, 3f, 8f, Color(0x000000, 0.18f)))
                                    }
                                    vif({ page.viewModel.streamState == StreamState.STREAMING }) {
                                        LineIconStop(color = page.theme.onBrand, size = 18f)
                                    }
                                    vif({ page.viewModel.streamState != StreamState.STREAMING }) {
                                        LineIconSend(color = page.theme.onBrand, size = 22f)
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
            vif({ page.sheetMounted }) {
                page.sheetCard?.let { model ->
                    CardSheetHost(
                        model = model,
                        level = page.sheetLevel,
                        theme = page.theme,
                        renderer = page.glassRenderer,
                        presented = page.sheetPresented,
                        viewportHeight = page.pagerData.pageViewHeight,
                        bottomInset = page.pagerData.safeAreaInsets.bottom,
                        onDismiss = { page.dismissCardSheet() },
                        onLower = { page.lowerCardSheet() },
                        onRaise = { page.raiseCardSheet() },
                        onPan = { state, y -> page.handleSheetPan(state, y) },
                        onOpenStock = { if (page.sheetInteractive) page.openStockDetail(it) },
                        onTerm = { page.viewModel.send("$it 是什么意思") },
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
                    onSettings = { page.drawerOpen = false; page.openPage(Routes.API_CONFIG) },
                )
            }
        }
    }

    private fun startNewChat() {
        viewModel.startNewChat()
        resetSessionUiState()
        inputRef.view?.setText("")
        keepChatAtBottomTemporarily()
    }

    private fun openHistorySession(sessionId: String) {
        viewModel.openSession(sessionId)
        resetSessionUiState()
        reloadQuotesForCurrentSession()
        inputRef.view?.setText("")
        keepChatAtBottomTemporarily()
    }

    private fun submitInput() {
        val value = viewModel.inputText
        viewModel.send(value)
        if (value.isNotBlank()) {
            inputRef.view?.setText("")
            inputFocused = false
            keepChatAtBottomTemporarily()
        }
    }

    private fun toggleMediaPanel() {
        val willShow = inputPanel != InputPanel.MEDIA
        voiceActive = false
        inputFocused = false
        if (willShow) inputRef.view?.blur()
        inputPanel = if (willShow) InputPanel.MEDIA else InputPanel.NONE
    }

    private fun handleMediaAction(action: ComposerMediaAction) {
        inputPanel = InputPanel.NONE
        inputFocused = false
        inputRef.view?.blur()
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridge.hapticImpact()
        bridge.openComposerMediaSource(action.source)
    }

    private fun resetSessionUiState() {
        ambiguousSymbols.clear()
        ambiguousEntityText = ""
        pendingEntitySheetSymbol = ""
        pendingLongPressSymbol = ""
        peekSymbol = ""
        peekVisible = false
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
        drilledKeys.clear()
        subThreads.clear()
        suppressNextStockClickSymbol = ""
        requestedSymbols.clear()
        quoteStates.clear()
        inputFocused = false
        voiceActive = false
        inputPanel = InputPanel.NONE
    }

    private fun reloadQuotesForCurrentSession() {
        viewModel.messages
            .flatMap { EntityRecognizer.recognize(it.content) }
            .filter { it.type == EntityType.STOCK }
            .map { it.target }
            .distinct()
            .forEach(::requestQuote)
    }

    private fun expandComposer(requestFocus: Boolean = false) {
        inputFocused = true
        voiceActive = false
        if (inputPanel == InputPanel.MEDIA) inputPanel = InputPanel.NONE
        if (requestFocus) inputRef.view?.focus()
    }

    /** Reads observable state inside each vif predicate so Kuikly can re-render it. */
    private fun isComposerExpanded(): Boolean =
        inputFocused || inputPanel != InputPanel.NONE || viewModel.inputText.isNotBlank() || keyboardHeight > 0f

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
        container.TextArea {
            ref { this@ChatPage.inputRef = it }
            attr {
                minHeight(40f)
                maxHeight(80f)
                fontSize(14f)
                lineHeight(21f)
                color(this@ChatPage.theme.textPrimary)
                backgroundColor(Color(0xFFFFFFFF, 0f))
                text(this@ChatPage.viewModel.inputText)
                placeholder("问一只股票或一个术语")
                placeholderColor(this@ChatPage.theme.textTertiary)
                returnKeyTypeSend()
                autofocus(
                    this@ChatPage.inputPanel != InputPanel.MEDIA &&
                        (this@ChatPage.inputFocused || this@ChatPage.keyboardHeight > 0f)
                )
            }
            event {
                inputFocus {
                    this@ChatPage.expandComposer()
                }
                inputBlur {
                    // Do not collapse on blur alone: switching from the
                    // collapsed input to the expanded input remounts TextArea
                    // and may emit a transient blur before the keyboard event.
                }
                textDidChange {
                    this@ChatPage.viewModel.inputText = it.text
                    this@ChatPage.updateInputPanel(it.text)
                }
                keyboardHeightChange {
                    this@ChatPage.keyboardHeight = it.height
                    this@ChatPage.inputFocused = it.height > 0f
                    this@ChatPage.scheduleScrollChatToBottom()
                }
                inputReturn {
                    this@ChatPage.submitInput()
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

    private fun handleStockEntityClick(entity: EntitySpan) {
        if (suppressNextStockClickSymbol == entity.target) {
            suppressNextStockClickSymbol = ""
            return
        }
        handleStockEntity(entity, EntityAction.DETAIL)
    }

    private fun handleStockEntityLongPress(entity: EntitySpan, state: String, cancelled: Boolean) {
        // A cancelled gesture must never open the sheet.
        if (cancelled) {
            if (pendingLongPressSymbol == entity.target) pendingLongPressSymbol = ""
            return
        }
        // The long-press gesture bridge reports recognition through different
        // states across platforms: "start" (finger-down recognition) and/or
        // "end" (finger-up). "move" must be ignored. Never re-open an entity
        // that already has a pending sheet; this also guards against duplicate
        // events while the quote is loading.
        if (state == "move") return
        if (pendingEntitySheetSymbol == entity.target) return
        if (sheetCard?.cardId == "entity-sheet:${entity.target}") {
            return
        }

        // "start" fires while the finger is still down. Presenting the sheet
        // there means the eventual finger-up lands on the freshly mounted card
        // and bleeds through into its stock header, jumping straight to the
        // detail page. Always wait for the gesture to actually finish.
        if (state == "start") {
            pendingLongPressSymbol = entity.target
            return
        }
        if (state != "end") return
        pendingLongPressSymbol = ""
        openSheetOnGestureEnd(entity)
    }

    private fun openSheetOnGestureEnd(entity: EntitySpan) {
        suppressNextStockClickSymbol = entity.target
        handleStockEntity(entity, EntityAction.SHEET)
        setTimeout(250) {
            if (suppressNextStockClickSymbol == entity.target) {
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
            EntityAction.SHEET -> openEntityQuoteSheet(symbol)
        }
    }

    private fun openEntityQuoteSheet(symbol: String) {
        pendingEntitySheetSymbol = symbol
        requestQuote(symbol)
        val cached = quoteFor(symbol)
        if (cached != null) {
            presentPendingEntitySheet(symbol, cached)
        } else if (quoteStates.any { it.symbol == symbol }) {
            pendingEntitySheetSymbol = ""
        }
    }

    private fun presentPendingEntitySheet(symbol: String, quote: Quote?) {
        if (pendingEntitySheetSymbol != symbol) return
        if (quote == null) {
            pendingEntitySheetSymbol = ""
            return
        }
        pendingEntitySheetSymbol = ""
        openCardSheet(StockQuoteCardModel(quote, "entity-sheet:$symbol"))
    }

    private fun contextSymbolsBefore(messageId: String): List<String> =
        viewModel.messages
            .takeWhile { it.id != messageId }
            .flatMap { EntityRecognizer.recognize(it.content) }
            .filter { it.type == EntityType.STOCK }
            .map { it.target }

    private fun suggestionsAreActive(message: ChatMessage): Boolean {
        val latestAssistant = viewModel.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        return latestAssistant?.id == message.id && !message.streaming && !message.failed && !message.cancelled
    }

    private fun requestQuote(symbol: String) {
        if (!requestedSymbols.add(symbol)) return
        if (!liveDataMode) {
            mockQuoteProvider.snapshot(symbol) { quote ->
                val updated = ChatQuoteState(symbol, quote, DataMode.OFFLINE)
                val index = quoteStates.indexOfFirst { it.symbol == symbol }
                if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
                presentPendingEntitySheet(symbol, quote)
            }
            return
        }
        quoteRepository.load(symbol) { result ->
            val updated = ChatQuoteState(symbol, result.quote, result.mode)
            val index = quoteStates.indexOfFirst { it.symbol == symbol }
            if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
            presentPendingEntitySheet(symbol, result.quote)
        }
    }

    private fun toggleCardExpanded(cardKey: String) {
        expandedCardKey = if (expandedCardKey == cardKey) "" else cardKey
    }

    private fun toggleDataMode() {
        liveDataMode = !liveDataMode
        requestedSymbols.clear()
        quoteStates.clear()
        (viewModel.messages.flatMap { EntityRecognizer.recognize(it.content) }
            .filter { it.type == EntityType.STOCK }
            .map { it.target } + listOfNotNull(peekSymbol.takeIf { it.isNotEmpty() }))
            .distinct()
            .forEach(::requestQuote)
    }

    private fun updateInputPanel(text: String) {
        inputPanel = when {
            text.trimStart().startsWith("@") -> InputPanel.SYMBOL
            text.trimStart().startsWith("/") -> InputPanel.COMMAND
            else -> InputPanel.NONE
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
        viewModel.inputText = question
        inputRef.view?.setText(question)
        inputRef.view?.focus()
        inputPanel = InputPanel.NONE
    }

    private fun clearCompare() {
        compareCard = null
        compareCandidateKey = ""
        compareCandidateSymbol = ""
    }

    private fun openCardSheet(model: CardModel) {
        val version = ++sheetPresentationVersion
        sheetCard = model
        sheetMounted = true
        sheetLevel = if (model.cardType == "stock-chart") SheetLevel.FULL else SheetLevel.HALF
        sheetPresented = false
        sheetInteractive = false
        setTimeout(16) {
            if (sheetPresentationVersion == version) sheetPresented = true
        }
        // Re-enable interaction only after the long-press gesture has fully ended.
        setTimeout(250) {
            if (sheetPresentationVersion != version || sheetCard?.cardId != model.cardId) return@setTimeout
            sheetInteractive = true
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

    private fun updateSubThread(cardId: String, update: (SubThreadState) -> SubThreadState) {
        val index = subThreads.indexOfFirst { it.cardId == cardId }
        if (index >= 0) subThreads[index] = update(subThreads[index])
    }

    private fun quoteFor(symbol: String): Quote? =
        quoteStates.firstOrNull { it.symbol == symbol }?.quote ?: quoteRepository.cachedOrOffline(symbol)

}

private enum class EntityAction { DETAIL, PREVIEW, SHEET }

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

private data class SubThreadState(
    val cardId: String,
    val title: String,
    val input: String,
    val response: String,
    val streaming: Boolean = false,
    val collapsed: Boolean = false,
)

private fun ViewContainer<*, *>.WelcomeSection(theme: StockChatTheme, onChoose: (String) -> Unit) {
    View {
        attr { paddingTop(52f); paddingLeft(20f); paddingRight(20f); alignItemsCenter() }
        View {
            attr { size(56f, 56f); allCenter(); borderRadius(16f); backgroundColor(theme.brand) }
            Text { attr { text("↗"); fontSize(29f); fontWeightBold(); color(theme.onBrand) } }
        }
        Text { attr { text("你好，我是股问 AI"); marginTop(14f); fontSize(17f); fontWeightSemiBold(); color(theme.textPrimary) } }
        Text { attr { text("做股民的解释器，不做荐股机"); marginTop(5f); fontSize(12f); color(theme.textTertiary) } }
        WelcomeChip("📊", "贵州茅台今天为什么跌？", theme.riseSoft, theme.rise, theme, onChoose)
        WelcomeChip("📖", "MACD 金叉是什么意思？", theme.brandSoft, theme.term, theme, onChoose)
        WelcomeChip("⚖", "茅台和五粮液怎么选？", theme.brandSoft, theme.brand, theme, onChoose)
    }
}

private fun ViewContainer<*, *>.WelcomeChip(
    icon: String,
    text: String,
    iconBackground: Color,
    iconColor: Color,
    theme: StockChatTheme,
    onChoose: (String) -> Unit,
) {
    View {
        attr { alignSelfStretch(); height(54f); marginTop(12f); paddingLeft(12f); paddingRight(12f); flexDirectionRow(); alignItemsCenter(); backgroundColor(theme.surface); borderRadius(14f) }
        View { attr { size(30f, 30f); allCenter(); borderRadius(9f); backgroundColor(iconBackground); marginRight(10f) }; Text { attr { text(icon); fontSize(15f); color(iconColor) } } }
        Text { attr { text(text); fontSize(14f); color(theme.textPrimary); flex(1f) } }
        Text { attr { text("›"); fontSize(22f); color(theme.textTertiary) } }
        event { click { onChoose(text) } }
    }
}

private fun ViewContainer<*, *>.DateDivider(theme: StockChatTheme) {
    View {
        attr { marginTop(14f); marginBottom(8f); flexDirectionRow(); alignItemsCenter() }
        View { attr { height(1f); flex(1f); backgroundColor(theme.divider) } }
        Text { attr { text("今天"); marginLeft(10f); marginRight(10f); fontSize(11f); color(theme.textTertiary) } }
        View { attr { height(1f); flex(1f); backgroundColor(theme.divider) } }
    }
}

private fun ViewContainer<*, *>.RecentSymbolRow(theme: StockChatTheme, onSelect: (String) -> Unit) {
    Scroller {
        attr { height(28f); flexDirectionRow() }
        listOf("📍 贵州茅台", "五粮液", "上证指数", "+ 添加关注").forEach { label ->
            View {
                attr { height(26f); marginRight(7f); paddingLeft(10f); paddingRight(10f); justifyContentCenter(); backgroundColor(theme.surfaceMuted); borderRadius(13f) }
                Text { attr { text(label); fontSize(11f); color(if (label.startsWith("+")) theme.brand else theme.textSecondary) } }
                event { click { if (!label.startsWith("+")) onSelect(label.removePrefix("📍 ")) } }
            }
        }
    }
}

private enum class InputPanel { NONE, SYMBOL, COMMAND, MEDIA }

private enum class ComposerMediaAction(val source: String, val label: String) {
    PHOTO_LIBRARY("library", "选照片"),
    CAMERA("camera", "拍照"),
}

private fun ViewContainer<*, *>.InputAssistantRow(panel: InputPanel, theme: StockChatTheme, onSelect: (String) -> Unit) {
    val items = when (panel) {
        InputPanel.SYMBOL -> listOf("@贵州茅台", "@五粮液", "@上证指数")
        InputPanel.COMMAND -> listOf("/复盘", "/对比", "/解读")
        InputPanel.MEDIA -> emptyList()
        InputPanel.NONE -> emptyList()
    }
    View {
        attr { marginTop(8f); padding(8f); flexDirectionRow(); backgroundColor(theme.surface); borderRadius(12f) }
        items.forEach { item ->
            View {
                attr { height(30f); marginRight(6f); paddingLeft(9f); paddingRight(9f); allCenter(); backgroundColor(theme.brandSoft); borderRadius(8f) }
                Text { attr { text(item); fontSize(11f); color(theme.brand) } }
                event { click { onSelect(item) } }
            }
        }
    }
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

private fun ViewContainer<*, *>.ChatMessageView(
    message: ChatMessage,
    theme: StockChatTheme,
    contextSymbols: List<String>,
    suggestionsActive: Boolean,
    onEntityStock: (EntitySpan) -> Unit,
    onEntityStockLongPress: (EntitySpan, String, Boolean) -> Unit,
    onCardStock: (String) -> Unit,
    onTerm: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onRetry: () -> Unit,
    repairingCardKey: String,
    onRetryCard: (String, String, String, String) -> Unit,
    onQuoteNeeded: (String) -> Unit,
    quoteFor: (String) -> Quote?,
    isCardExpanded: (String) -> Boolean,
    onToggleCardExpanded: (String) -> Unit,
    onOpenCardSheet: (CardModel) -> Unit,
    drilledKeys: Set<String>,
    onToggleDrill: (String) -> Unit,
    subThreads: List<SubThreadState>,
    onStartSubThread: (CardModel) -> Unit,
    onToggleSubThread: (String) -> Unit,
    onUpdateSubThreadInput: (String, String) -> Unit,
    onSendSubThread: (String) -> Unit,
    focusedCardKey: String,
    onFocusChanged: (String, Boolean) -> Unit,
    compareCandidateSymbol: String,
    onCompareCandidate: (String, String) -> Unit,
    onCardEvent: (String, CardEvent) -> Unit,
) {
    val user = message.role == MessageRole.USER
    View {
        attr {
            marginTop(20f)
            if (user) alignItemsFlexEnd()
        }
        View {
            attr {
                if (user) {
                    marginLeft(58f)
                    marginRight(2f)
                    paddingTop(10f)
                    paddingBottom(10f)
                    paddingLeft(16f)
                    paddingRight(16f)
                    backgroundColor(theme.brand)
                    borderRadius(20f)
                } else {
                    // No avatar: the AI message spans the row with symmetric
                    // margins so the left and right insets always match.
                    marginLeft(6f)
                    marginRight(6f)
                }
            }
            if (user) {
                Text { attr { text(message.content); fontSize(14f); lineHeight(21f); color(theme.onBrand) } }
            } else {
                vif({ message.streaming }) {
                    View {
                        EntityStreamingMarkdown(message.content, theme, contextSymbols, onEntityStock, onEntityStockLongPress, onTerm)
                    }
                }
                vif({ !message.streaming }) {
                    View {
                    try {
                        AssistantContent(message, theme, contextSymbols, suggestionsActive, onEntityStock, onEntityStockLongPress, onCardStock, onTerm, onSuggestion, onRetry, repairingCardKey, onRetryCard, onQuoteNeeded, quoteFor, isCardExpanded, onToggleCardExpanded, onOpenCardSheet, drilledKeys, onToggleDrill, subThreads, onStartSubThread, onToggleSubThread, onUpdateSubThreadInput, onSendSubThread, focusedCardKey, onFocusChanged, compareCandidateSymbol, onCompareCandidate, onCardEvent)
                    } catch (error: Throwable) {
                        Text {
                            attr {
                                text("结构化内容暂时无法展示：${error.message.orEmpty()}")
                                fontSize(12f)
                                lineHeight(18f)
                                color(theme.textSecondary)
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AssistantContent(
    message: ChatMessage,
    theme: StockChatTheme,
    contextSymbols: List<String>,
    suggestionsActive: Boolean,
    onEntityStock: (EntitySpan) -> Unit,
    onEntityStockLongPress: (EntitySpan, String, Boolean) -> Unit,
    onCardStock: (String) -> Unit,
    onTerm: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onRetry: () -> Unit,
    repairingCardKey: String,
    onRetryCard: (String, String, String, String) -> Unit,
    onQuoteNeeded: (String) -> Unit,
    quoteFor: (String) -> Quote?,
    isCardExpanded: (String) -> Boolean,
    onToggleCardExpanded: (String) -> Unit,
    onOpenCardSheet: (CardModel) -> Unit,
    drilledKeys: Set<String>,
    onToggleDrill: (String) -> Unit,
    subThreads: List<SubThreadState>,
    onStartSubThread: (CardModel) -> Unit,
    onToggleSubThread: (String) -> Unit,
    onUpdateSubThreadInput: (String, String) -> Unit,
    onSendSubThread: (String) -> Unit,
    focusedCardKey: String,
    onFocusChanged: (String, Boolean) -> Unit,
    compareCandidateSymbol: String,
    onCompareCandidate: (String, String) -> Unit,
    onCardEvent: (String, CardEvent) -> Unit,
) {
    val blocks = AiResponseLexer.lex(message.content, finished = !message.streaming)
    blocks.forEach { block ->
        when (block) {
            is TextBlock -> {
                View {
                    attr { marginTop(4f) }
                    EntityRichText(block.content, theme, contextSymbols, onEntityStock, onEntityStockLongPress, onTerm)
                }
            }
            is CardBlock -> {
                try {
                    val cardKey = "${message.id}:${block.id}"
                    val intent = CardPayloadParser.parse(block.type, block.payload)
                    if (intent is SuggestionsIntent) {
                        if (suggestionsActive) SuggestionRow(intent, theme, onSuggestion)
                    } else {
                        when (intent) {
                            is SymbolCardIntent -> onQuoteNeeded(intent.symbol)
                            is AttributionIntent -> onQuoteNeeded(intent.symbol)
                            else -> Unit
                        }
                        if (intent is SymbolCardIntent && intent.type == "stock-chart") {
                            ChatStockChartCard(block, intent, theme, onCardStock, onTerm, quoteFor, isCardExpanded, onToggleCardExpanded, onOpenCardSheet, cardKey, focusedCardKey, onFocusChanged, compareCandidateSymbol, onCompareCandidate, onCardEvent)
                        } else {
                            val model = CardAssembler.assemble(block, quoteFor)
                            ReactiveCardShell(
                                model,
                                CardContext(
                                    theme = theme,
                                    density = CardDensity.COMPACT,
                                    onOpenStock = onCardStock,
                                    onExplainTerm = onTerm,
                                    expanded = false,
                                    onToggleExpanded = { onToggleCardExpanded(cardKey) },
                                    onOpenSheet = onOpenCardSheet,
                                    drilledKeys = drilledKeys,
                                    onToggleDrill = onToggleDrill,
                                    onStartSubThread = onStartSubThread,
                                    cardKey = cardKey,
                                    focusedCardKey = focusedCardKey,
                                    onFocusChanged = onFocusChanged,
                                    compareCandidateSymbol = compareCandidateSymbol,
                                    onCompareCandidate = onCompareCandidate,
                                    onCardEvent = onCardEvent,
                                ),
                                cardKey,
                                isCardExpanded,
                            )
                            if (model is InsightCardModel) {
                                subThreads.firstOrNull { it.cardId == model.cardId }?.let { thread ->
                                    NestedConversation(thread, theme, onToggleSubThread, onUpdateSubThreadInput, onSendSubThread)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    val rawCard = "```card:${block.type}\n${block.payload}\n```"
                    val cardKey = "${message.id}:${block.id}"
                    StructuredContentUnavailable(
                        type = block.type,
                        theme = theme,
                        retrying = repairingCardKey == cardKey,
                        onRetry = { onRetryCard(message.id, block.id, block.type, rawCard) },
                    )
                }
            }
            is BrokenCardBlock -> {
                val cardKey = "${message.id}:${block.id}"
                StructuredContentUnavailable(
                    type = block.type,
                    theme = theme,
                    retrying = repairingCardKey == cardKey,
                    onRetry = { onRetryCard(message.id, block.id, block.type, block.raw) },
                )
            }
            is SkeletonBlock -> CardShell(
                CardAssembler.assemble(block),
                CardContext(theme, CardDensity.COMPACT, onCardStock, onTerm),
            )
        }
    }
    if (message.id == "m1" && suggestionsActive) {
        SuggestionRow(
            SuggestionsIntent(
                listOf(
                    com.kuikly.stockchat.protocol.SuggestionIntent("贵州茅台最近怎么样"),
                    com.kuikly.stockchat.protocol.SuggestionIntent("为什么跌"),
                    com.kuikly.stockchat.protocol.SuggestionIntent("PE 是什么", "diverge"),
                ),
            ),
            theme,
            onSuggestion,
        )
    }
    if (!message.streaming) {
        Text {
            attr {
                text(
                    when {
                        message.failed -> "生成失败，点击重试"
                        message.cancelled -> "已停止生成"
                        else -> "AI 生成，仅供参考，不构成投资建议"
                    },
                )
                marginTop(10f)
                fontSize(9f)
                color(if (message.failed) theme.brand else theme.textTertiary)
            }
            if (message.failed) event { click { onRetry() } }
        }
    }
}

private fun ViewContainer<*, *>.StructuredContentUnavailable(
    type: String,
    theme: StockChatTheme,
    retrying: Boolean,
    onRetry: () -> Unit,
) {
    View {
        attr {
            alignSelfStretch()
            marginTop(10f)
            padding(12f)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
            flexDirectionRow()
            alignItemsCenter()
        }
        View {
            attr {
                width(52f)
                height(52f)
                marginRight(12f)
                allCenter()
                backgroundColor(theme.surfaceMuted)
                borderRadius(8f)
            }
            Text {
                attr {
                    text("CARD")
                    fontSize(10f)
                    fontWeightMedium()
                    color(theme.textTertiary)
                }
            }
        }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text("${type.ifEmpty { "结构化内容" }} 加载失败")
                    fontSize(13f)
                    fontWeightMedium()
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    text("可单独重试这张卡片")
                    marginTop(3f)
                    fontSize(10f)
                    color(theme.textTertiary)
                }
            }
        }
        View {
            attr {
                height(30f)
                paddingLeft(12f)
                paddingRight(12f)
                allCenter()
                backgroundColor(if (retrying) theme.surfaceMuted else theme.brandSoft)
                borderRadius(8f)
            }
            Text {
                attr {
                    text(if (retrying) "重试中" else "重试")
                    fontSize(11f)
                    fontWeightMedium()
                    color(if (retrying) theme.textTertiary else theme.brand)
                }
            }
            if (!retrying) event { click { onRetry() } }
        }
    }
}

private fun ViewContainer<*, *>.ChatStockChartCard(
    block: CardBlock,
    intent: SymbolCardIntent,
    theme: StockChatTheme,
    onCardStock: (String) -> Unit,
    onTerm: (String) -> Unit,
    quoteFor: (String) -> Quote?,
    isCardExpanded: (String) -> Boolean,
    onToggleCardExpanded: (String) -> Unit,
    onOpenCardSheet: (CardModel) -> Unit,
    cardKey: String,
    focusedCardKey: String,
    onFocusChanged: (String, Boolean) -> Unit,
    compareCandidateSymbol: String,
    onCompareCandidate: (String, String) -> Unit,
    onCardEvent: (String, CardEvent) -> Unit,
) {
    val context = CardContext(
        theme = theme,
        density = CardDensity.COMPACT,
        onOpenStock = onCardStock,
        onExplainTerm = onTerm,
        expanded = false,
        onToggleExpanded = { onToggleCardExpanded(cardKey) },
        onOpenSheet = onOpenCardSheet,
        cardKey = cardKey,
        focusedCardKey = focusedCardKey,
        onFocusChanged = onFocusChanged,
        compareCandidateSymbol = compareCandidateSymbol,
        onCompareCandidate = onCompareCandidate,
        onCardEvent = onCardEvent,
    )
    vif({ quoteFor(intent.symbol)?.timeline?.isNotEmpty() == true }) {
        quoteFor(intent.symbol)?.let { quote ->
            ReactiveCardShell(StockChartCardModel(quote, cardId = block.id), context, cardKey, isCardExpanded)
        }
    }
    vif({ quoteFor(intent.symbol)?.let { it.timeline.isEmpty() && it.kLines.isNotEmpty() } == true }) {
        quoteFor(intent.symbol)?.let { quote ->
            ReactiveCardShell(StockChartCardModel(quote, StockChartMode.K_LINE, cardId = block.id), context, cardKey, isCardExpanded)
        }
    }
    vif({ quoteFor(intent.symbol)?.let { it.timeline.isEmpty() && it.kLines.isEmpty() } != false }) {
        CardShell(SkeletonCardModel("stock-chart", block.id), context)
    }
}

private fun ViewContainer<*, *>.ReactiveCardShell(
    model: CardModel,
    context: CardContext,
    cardKey: String,
    isCardExpanded: (String) -> Boolean,
) {
    vif({ isCardExpanded(cardKey) }) {
        CardShell(model, context.copy(expanded = true))
    }
    vif({ !isCardExpanded(cardKey) }) {
        CardShell(model, context.copy(expanded = false))
    }
}

private fun ViewContainer<*, *>.SuggestionRow(
    intent: SuggestionsIntent,
    theme: StockChatTheme,
    onSuggestion: (String) -> Unit,
) {
    Scroller {
        attr {
            flexDirectionRow()
            height(38f)
            marginTop(10f)
        }
        intent.chips.forEach { suggestion ->
            View {
                attr {
                    height(34f)
                    marginRight(7f)
                    paddingLeft(11f)
                    paddingRight(11f)
                    justifyContentCenter()
                    borderRadius(11f)
                    backgroundColor(if (suggestion.type == "drill") theme.brandSoft else theme.surfaceMuted)
                }
                Text {
                    attr {
                        text(suggestion.text)
                        fontSize(12f)
                        color(if (suggestion.type == "drill") theme.brand else theme.textSecondary)
                    }
                }
                event { click { onSuggestion(suggestion.text) } }
            }
        }
    }
}

private fun ViewContainer<*, *>.RegressionQuestionRow(
    theme: StockChatTheme,
    onQuestion: (String) -> Unit,
) {
    val cases = listOf(
        "手风琴" to "回归：手风琴 贵州茅台最近怎么样",
        "资讯 Sheet" to "回归：资讯 Sheet 看贵州茅台资讯",
        "归因下钻" to "回归：归因下钻 为什么跌",
        "分支追问" to "回归：分支追问 贵州茅台最近怎么样",
        "焦点放大" to "回归：焦点放大 贵州茅台最近怎么样",
        "对比卡" to "回归：对比卡 贵州茅台和五粮液比较",
    )
    Scroller {
        attr {
            height(42f)
            paddingLeft(14f)
            paddingRight(14f)
            paddingBottom(6f)
            flexDirectionRow()
        }
        cases.forEach { item ->
            View {
                attr {
                    height(32f)
                    marginRight(7f)
                    paddingLeft(10f)
                    paddingRight(10f)
                    justifyContentCenter()
                    borderRadius(10f)
                    backgroundColor(theme.surfaceMuted)
                }
                Text {
                    attr {
                        text(item.first)
                        fontSize(11f)
                        fontWeightMedium()
                        color(theme.textSecondary)
                    }
                }
                event { click { onQuestion(item.second) } }
            }
        }
    }
}

private fun ViewContainer<*, *>.NestedConversation(
    state: SubThreadState,
    theme: StockChatTheme,
    onToggle: (String) -> Unit,
    onInput: (String, String) -> Unit,
    onSend: (String) -> Unit,
) {
    View {
        attr {
            marginTop(8f)
            marginLeft(16f)
            padding(10f)
            backgroundColor(theme.brandSoft)
            borderRadius(8f)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View { attr { width(2f); height(18f); marginRight(7f); backgroundColor(theme.brand); borderRadius(1f) } }
            Text { attr { text(state.title); fontSize(11f); fontWeightMedium(); color(theme.brand); flex(1f) } }
            Text { attr { text(if (state.collapsed) "展开" else "收起"); fontSize(11f); color(theme.brand) } }
            event { click { onToggle(state.cardId) } }
        }
        if (!state.collapsed) {
            Text { attr { text(state.response); marginTop(8f); fontSize(12f); lineHeight(18f); color(theme.textSecondary) } }
            View {
                attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f); height(34f); paddingLeft(9f); paddingRight(9f); backgroundColor(theme.surface); borderRadius(8f); justifyContentCenter() }
                    Input {
                        attr { height(32f); fontSize(12f); color(theme.textPrimary); placeholder("继续追问"); placeholderColor(theme.textTertiary) }
                        event { textDidChange { onInput(state.cardId, it.text) } }
                    }
                }
                View {
                    attr { marginLeft(6f); height(34f); paddingLeft(10f); paddingRight(10f); allCenter(); backgroundColor(theme.brand); borderRadius(8f) }
                    Text { attr { text(if (state.streaming) "…" else "发送"); fontSize(11f); color(theme.onBrand) } }
                    if (!state.streaming) event { click { onSend(state.cardId) } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.ActiveComparePanel(
    model: StockCompareCardModel,
    theme: StockChatTheme,
    onOpenStock: (String) -> Unit,
    onClose: () -> Unit,
) {
    View {
        attr {
            marginLeft(12f)
            marginRight(12f)
            marginBottom(8f)
            padding(10f)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("对比视图")
                    fontSize(12f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                    flex(1f)
                }
            }
            Text { attr { text("退出"); fontSize(11f); color(theme.textSecondary) } }
            event { click { onClose() } }
        }
        CardShell(
            model,
            CardContext(
                theme = theme,
                density = CardDensity.FULL,
                onOpenStock = onOpenStock,
            ),
        )
    }
}

internal fun ViewContainer<*, *>.CardSheetHost(
    model: CardModel,
    level: SheetLevel,
    theme: StockChatTheme,
    renderer: GlassRenderer = GlassRenderer.Default,
    presented: Boolean = true,
    viewportHeight: Float,
    bottomInset: Float,
    onDismiss: () -> Unit,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    onPan: (String, Float) -> Unit,
    onOpenStock: (String) -> Unit,
    onTerm: (String) -> Unit,
) {
    val availableHeight = (viewportHeight - bottomInset).coerceAtLeast(520f)
    val sheetHeight = (availableHeight * level.ratio).coerceAtLeast(180f)
    val footerHeight = if (level == SheetLevel.FULL) 0f else 52f
    val overlayHeight = (viewportHeight - sheetHeight - bottomInset).coerceAtLeast(0f)
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(overlayHeight)
            backgroundColor(Color(0x4D000000))
            opacity(if (presented) 1f else 0f)
            // While hidden the scrim must not swallow touches, otherwise the
            // page underneath (including the fresh sheet's own content) is
            // left unresponsive for the whole presentation window.
            touchEnable(presented)
            animate(Animation.easeOut(0.20f), presented)
        }
        event { click { if (presented) onDismiss() } }
    }
    View {
        attr {
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            height(sheetHeight + bottomInset)
            paddingLeft(16f)
            paddingRight(16f)
            paddingBottom(12f + bottomInset)
            opacity(if (presented) 1f else 0f)
            transform(scale = if (presented) Scale.DEFAULT else Scale(0.98f, 0.98f))
            touchEnable(presented)
            animate(Animation.easeOut(if (renderer.mode == GlassRenderingMode.SIMPLIFIED) 0.20f else 0.40f), presented)
            animate(Animation.easeOut(0.26f), level)
        }
        // The transition material fades its blur toward removal before the sheet unmounts.
        GlassBackdrop(
            if (presented) theme.glass.sheet else theme.glass.dissolve,
            renderer,
            blurVisible = presented,
        )
        View {
            attr {
                width(56f)
                height(36f)
                alignSelfCenter()
                allCenter()
                capture(CaptureRule.pan(CaptureRuleDirection.VERTICAL))
            }
            View {
                attr {
                    width(36f)
                    height(4f)
                    backgroundColor(theme.divider)
                    borderRadius(2f)
                }
            }
            event { pan { onPan(it.state, it.y) } }
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text { attr { text("完整内容"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary); flex(1f) } }
            Text { attr { text("收起"); fontSize(12f); color(theme.textSecondary) } }
            event { click { onLower() } }
        }
        Scroller {
            attr { height((sheetHeight - 82f - footerHeight).coerceAtLeast(64f)); marginTop(6f) }
            CardShell(
                model,
                CardContext(
                    theme = theme,
                    density = level.density,
                    onOpenStock = onOpenStock,
                    onExplainTerm = onTerm,
                    glass = renderer,
                ),
            )
        }
        if (level != SheetLevel.FULL) {
            View {
                attr {
                    alignSelfStretch()
                    height(38f)
                    marginTop(8f)
                    allCenter()
                    backgroundColor(theme.brandSoft)
                    borderRadius(10f)
                }
                Text { attr { text("展开更多"); fontSize(12f); fontWeightMedium(); color(theme.brand) } }
                event { click { onRaise() } }
            }
        }
    }
}
