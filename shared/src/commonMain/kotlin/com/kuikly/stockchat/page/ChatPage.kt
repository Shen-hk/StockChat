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
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.SkeletonBlock
import com.kuikly.stockchat.protocol.SuggestionsIntent
import com.kuikly.stockchat.protocol.SymbolCardIntent
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.TextBlock
import com.kuikly.stockchat.richtext.EntityRichText
import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntitySpan
import com.kuikly.stockchat.richtext.EntityType
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
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
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

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
    private var ambiguousSymbols: ObservableList<String> by observableList()
    private var ambiguousEntityText: String by observable("")
    private var ambiguousAction: EntityAction by observable(EntityAction.PREVIEW)
    private var keyboardHeight: Float by observable(0f)
    private var drawerOpen: Boolean by observable(false)
    private var liveDataMode: Boolean by observable(true)
    // Page data is injected after construction; use the safe fallback until created().
    private var glassMode: GlassRenderingMode by observable(GlassRenderingMode.SIMPLIFIED)
    private var glassModeManuallySelected = false
    // The home navigation is an L3 Sheet surface at every scroll position.
    private var glassNav: Boolean by observable(true)
    private var inputPanel: InputPanel by observable(InputPanel.NONE)
    private var expandedCardKey: String by observable("")
    private var focusedCardKey: String by observable("")
    private var compareCandidateKey: String by observable("")
    private var compareCandidateSymbol: String by observable("")
    private var compareCard: StockCompareCardModel? by observable(null)
    private var sheetCard: CardModel? by observable(null)
    private var sheetPresented: Boolean by observable(false)
    private var sheetLevel: SheetLevel by observable(SheetLevel.HALF)
    private var sheetPanStartY = 0f
    // Do not mount a modal during the native long-press gesture itself: its
    // full-screen scrim would swallow that gesture's terminal touch event.
    private var longPressedStockTarget = ""
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
                glass = page.glassNav,
                liveData = page.liveDataMode,
                renderer = page.glassRenderer,
                contextTitle = if (page.drilledKeys.isNotEmpty()) "归因链 · 资金面 ›" else null,
                onMenu = { page.drawerOpen = !page.drawerOpen },
                onHistory = { page.drawerOpen = true },
                onNewChat = { page.viewModel.clear(); page.keepChatAtBottomTemporarily() },
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
                    // The composer is an overlay, not a flex sibling.  This is
                    // essential for a real backdrop: messages must remain
                    // behind this material while the conversation scrolls.
                    absolutePosition(bottom = 0f, left = 0f, right = 0f)
                    padding(12f)
                    paddingBottom(12f + page.pagerData.safeAreaInsets.bottom + page.keyboardHeight)
                }
                GlassBackdrop(page.theme.glass.sheet, page.glassRenderer)
                RecentSymbolRow(page.theme) { text -> page.injectQuestion(text) }
                vif({ page.inputPanel != InputPanel.NONE }) {
                    InputAssistantRow(page.inputPanel, page.theme) { value -> page.injectQuestion(value) }
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsFlexEnd()
                        marginTop(8f)
                    }
                    View {
                        attr {
                            flex(1f)
                            minHeight(44f)
                            paddingLeft(16f)
                            paddingRight(8f)
                            backgroundColor(page.theme.surfaceMuted)
                            borderRadius(page.theme.inputRadius)
                            justifyContentCenter()
                        }
                        TextArea {
                            ref { page.inputRef = it }
                            attr {
                                minHeight(40f)
                                maxHeight(80f)
                                fontSize(14f)
                                lineHeight(21f)
                                color(page.theme.textPrimary)
                                placeholder("问一只股票或一个术语")
                                placeholderColor(page.theme.textTertiary)
                                returnKeyTypeSend()
                            }
                            event {
                                textDidChange {
                                    page.viewModel.inputText = it.text
                                    page.updateInputPanel(it.text)
                                }
                                keyboardHeightChange {
                                    page.keyboardHeight = it.height
                                    page.scheduleScrollChatToBottom()
                                }
                                inputReturn {
                                    page.submitInput()
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            marginLeft(8f)
                            size(36f, 36f)
                            allCenter()
                            borderRadius(18f)
                            backgroundColor(if (page.viewModel.inputText.isBlank() && page.viewModel.streamState != StreamState.STREAMING) page.theme.divider else page.theme.brand)
                        }
                        Text {
                            attr {
                                text(if (page.viewModel.streamState == StreamState.STREAMING) "■" else "↑")
                                fontSize(17f)
                                fontWeightSemiBold()
                                color(page.theme.onBrand)
                            }
                        }
                        event {
                            click {
                                if (page.viewModel.streamState == StreamState.STREAMING) page.viewModel.stop()
                                else page.submitInput()
                            }
                        }
                    }
                }
                Text { attr { text("@ 标的联想 · / 指令面板 · 回车发送 · Shift+回车换行"); marginTop(7f); fontSize(10f); color(page.theme.textTertiary) } }
            }
            vif({ page.sheetCard != null }) {
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
                        onOpenStock = { page.openStockDetail(it) },
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
                    onClose = { page.drawerOpen = false },
                    onToggleDataMode = { page.toggleDataMode() },
                    onCycleVisualMode = { page.cycleGlassMode() },
                    onSettings = { page.drawerOpen = false; page.openPage(Routes.API_CONFIG) },
                )
            }
        }
    }

    private fun submitInput() {
        val value = viewModel.inputText
        viewModel.send(value)
        if (value.isNotBlank()) {
            inputRef.view?.setText("")
            keepChatAtBottomTemporarily()
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
        when (state) {
            "start" -> if (!cancelled) {
                suppressNextStockClickSymbol = entity.target
                longPressedStockTarget = entity.target
                handleStockEntity(entity, EntityAction.SHEET)
                longPressedStockTarget = ""
                setTimeout(250) {
                    if (suppressNextStockClickSymbol == entity.target) {
                        suppressNextStockClickSymbol = ""
                    }
                }
            }
            "end" -> {
                // Android's RichText gesture bridge may mark the terminal
                // callback cancelled after it has already recognised a valid
                // long-press.  The recorded start is the reliable signal.
                val shouldOpenSheet = longPressedStockTarget == entity.target
                longPressedStockTarget = ""
                if (shouldOpenSheet && pendingEntitySheetSymbol.isEmpty()) {
                    // RichText may dispatch click immediately after longPress
                    // end. Keep the guard through that dispatch, then release
                    // it in case the platform does not emit a click at all.
                    handleStockEntity(entity, EntityAction.SHEET)
                    setTimeout(250) {
                        if (suppressNextStockClickSymbol == entity.target) {
                            suppressNextStockClickSymbol = ""
                        }
                    }
                }
            }
            else -> if (cancelled) longPressedStockTarget = ""
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
        quoteFor(symbol)?.let { presentPendingEntitySheet(symbol, it) }
    }

    private fun presentPendingEntitySheet(symbol: String, quote: Quote?) {
        if (pendingEntitySheetSymbol != symbol || quote == null) return
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
        sheetCard = model
        sheetLevel = if (model.cardType == "stock-chart") SheetLevel.FULL else SheetLevel.HALF
        sheetPresented = false
        setTimeout(16) { sheetPresented = true }
    }

    private fun dismissCardSheet() {
        sheetPresented = false
        setTimeout(420) {
            if (!sheetPresented) sheetCard = null
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

private enum class InputPanel { NONE, SYMBOL, COMMAND }

private fun ViewContainer<*, *>.InputAssistantRow(panel: InputPanel, theme: StockChatTheme, onSelect: (String) -> Unit) {
    val items = when (panel) {
        InputPanel.SYMBOL -> listOf("@贵州茅台", "@五粮液", "@上证指数")
        InputPanel.COMMAND -> listOf("/复盘", "/对比", "/解读")
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
            if (user) alignItemsFlexEnd() else alignItemsFlexStart()
        }
        View {
            attr {
                if (user) marginLeft(58f) else marginRight(4f)
                if (!user) flexDirectionRow()
                if (user) { paddingTop(10f); paddingBottom(10f); paddingLeft(16f); paddingRight(16f); backgroundColor(theme.brand); borderRadius(20f) }
            }
            if (user) {
                Text { attr { text(message.content); fontSize(14f); lineHeight(21f); color(theme.onBrand) } }
            } else {
                View {
                    attr { size(36f, 36f); marginRight(10f); allCenter(); backgroundColor(theme.brand); borderRadius(10f) }
                    Text { attr { text("AI"); fontSize(12f); fontWeightBold(); color(theme.onBrand) } }
                }
                View {
                    attr { flex(1f) }
                    vif({ message.streaming }) {
                        View {
                        Text {
                            attr {
                                text(message.content)
                                fontSize(14f)
                                lineHeight(21f)
                                color(theme.textSecondary)
                            }
                        }
                        }
                    }
                    vif({ !message.streaming }) {
                        View {
                        try {
                            AssistantContent(message, theme, contextSymbols, suggestionsActive, onEntityStock, onEntityStockLongPress, onCardStock, onTerm, onSuggestion, onRetry, onQuoteNeeded, quoteFor, isCardExpanded, onToggleCardExpanded, onOpenCardSheet, drilledKeys, onToggleDrill, subThreads, onStartSubThread, onToggleSubThread, onUpdateSubThreadInput, onSendSubThread, focusedCardKey, onFocusChanged, compareCandidateSymbol, onCompareCandidate, onCardEvent)
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
            animate(Animation.easeOut(0.20f), presented)
        }
        event { click { onDismiss() } }
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
