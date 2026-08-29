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
import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.AtCandidateProvider
import com.kuikly.stockchat.composer.CommandExecution
import com.kuikly.stockchat.composer.CommandInvocation
import com.kuikly.stockchat.composer.CommandRegistry
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
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
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
    // Symbol whose long press was recognised but whose gesture has not ended.
    // The sheet can render immediately, but stays non-interactive until release.
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
    // Dynamic island: the top title capsule morphs into a live quote card.
    private var islandExpanded: Boolean by observable(false)
    private val islandSymbol = "600519.SH"
    // Page data is injected after construction; use the safe fallback until created().
    private var glassMode: GlassRenderingMode by observable(GlassRenderingMode.SIMPLIFIED)
    private var glassModeManuallySelected = false
    private var inputPanel: InputPanel by observable(InputPanel.NONE)
    // ===== @ 提及与 / 指令状态机（规范见 docs/10-输入栏@提及与斜杠指令交互规范_v1.0.md） =====
    // 联想面板维度（与 MEDIA 面板正交）：@ 触发 / / 触发 / 命令参数槽位。
    private var assistantPanel: AssistantPanel by observable(AssistantPanel.NONE)
    // 活动触发会话；同一时刻最多一个（规范 §3.1）。
    private var triggerSession: TriggerSession? = null
    // 最近一次观测到的光标偏移。点左下角 @ / / 按钮时在光标处插入触发字符；
    // -1 表示还没有观测到（此时退化为追加到末尾）。
    private var composerCursor: Int = -1
    // 拼音组合态：面板冻结显示"输入中…"（规范 §3.5）。
    private var triggerComposing: Boolean by observable(false)
    private var atCandidates: ObservableList<AtCandidate> by observableList()
    private var atHighlight: Int by observable(0)
    private var slashCandidates: ObservableList<SlashCommand> by observableList()
    private var slashHighlight: Int by observable(0)
    // 未知命令提示（规范 §5.6）：非空时面板显示"没有找到 /xxx"。
    private var slashUnknown: String by observable("")
    // 固化提及注册表：只存实体顺序，激活态每次从文本扫描得出（规范 §4.6）。
    private val mentionEntities = mutableListOf<MentionEntity>()
    // 上下文标记（/深水区等），注入 system context（规范 §4.8）。
    private val deepContextNotes = mutableListOf<String>()
    // / 命令参数态（规范 §5.4）。参数值不落字段，每次从输入文本实时解析，
    // 保证面板显示与最终发送用的是同一套解析结果。
    private var paramCommand: SlashCommand? by observable(null)
    // 最近提及（S1 数据源，最多 5 条，新的在前）。
    private val recentMentions = mutableListOf<String>()
    // Composer state machine (规范见 docs/09-输入栏默认态与输入态转换规范_v1.0.md).
    // 默认态 → 输入态由点击/聚焦/开面板触发；输入态是"粘"的：收起键盘不再回退，
    // 只有"键盘已收起时点击非输入栏区域"这一次点击才回到默认态。
    private var composerExpanded: Boolean by observable(false)
    // 键盘当前是否在屏上，由 TextArea 的 keyboardHeightChange 上报。
    private var keyboardVisible: Boolean by observable(false)
    // 键盘被收起但输入栏仍留在输入态时置位，避免 TextArea 重新挂载时把键盘又拉起来。
    private var keyboardDismissed = false
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
        // Preload the island quote so the morph opens with data in place.
        requestQuote(islandSymbol)
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
                        onEntityStock = { entity ->
                            println("[STOCKCHAT_DBG] onEntityStock click target=${entity.target}")
                            page.handleStockEntityClick(entity)
                        },
                        onEntityStockLongPress = { entity, state, cancelled ->
                            if (state != "move" || cancelled) {
                                println("[STOCKCHAT_DBG] onEntityStockLongPress target=${entity.target} state=$state cancelled=$cancelled")
                            }
                            page.handleStockEntityLongPress(entity, state, cancelled)
                        },
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
                liveData = { page.liveDataMode },
                renderer = page.glassRenderer,
                contextTitle = if (page.drilledKeys.isNotEmpty()) "归因链 · 资金面 ›" else null,
                pageWidth = page.pagerData.pageViewWidth,
                islandExpanded = { page.islandExpanded },
                islandQuote = { page.quoteFor(page.islandSymbol) },
                onToggleIsland = { page.toggleIsland() },
                onOpenIslandDetail = { symbol -> page.islandExpanded = false; page.openStockDetail(symbol) },
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
                    }
                    GlassBackdrop(page.theme.glass.sheet, page.glassRenderer)
                        // 联想面板打开时收起"最近标的"横条：面板本身已含"最近"数据源候选，
                        // 两条叠着显示既重复又顶高输入栏。
                        vif({ page.isComposerExpanded() && page.assistantPanel == AssistantPanel.NONE }) {
                            RecentSymbolRow(page.theme) { text -> page.injectQuestion(text) }
                        }
                        vif({ page.isComposerExpanded() && page.assistantPanel != AssistantPanel.NONE }) {
                            page.renderAssistantPanel(this)
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
                                    page.renderComposerTextArea(this, compact = true)
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
                                // 联想面板打开时把输入框收紧到单行：面板与多行输入框叠起来太高，
                                // 此时注意力在候选上，草稿内容仍在，只是可视区收窄（内部可滚动）。
                                page.renderComposerTextArea(this, compact = page.assistantPanel != AssistantPanel.NONE)
                                // 已经在输入态时再点一下输入框：只负责把键盘叫回来。
                                event { click { page.expandComposer(requestFocus = true) } }
                            }
                            View {
                                attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
                                View {
                                    attr {
                                        size(40f, 40f)
                                        allCenter()
                                        borderRadius(20f)
                                        backgroundColor(if (page.assistantPanel == AssistantPanel.AT_MENTION) page.theme.brand else page.theme.brandSoft)
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
                        interactive = page.sheetInteractive,
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
        viewModel.send(payload)
        // 发送后清理输入期固化状态：mentions / 命令注册；最近提及列表保留供下次推荐。
        mentionEntities.clear()
        clearActiveCommand()
        closeAssistantPanel()
        setComposerText("")
        // 发送后不回默认态：键盘还在，用户接着问下一句更顺手。
        keepChatAtBottomTemporarily()
    }

    private fun toggleMediaPanel() {
        val willShow = inputPanel != InputPanel.MEDIA
        voiceActive = false
        if (willShow) {
            keyboardDismissed = true
            inputRef.view?.blur()
        }
        inputPanel = if (willShow) InputPanel.MEDIA else InputPanel.NONE
    }

    private fun handleMediaAction(action: ComposerMediaAction) {
        // 选完图片/拍照后整条输入栏回到默认态，不留在"半展开"的悬空状态。
        collapseComposer()
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
        collapseComposer()
        inputRef.view?.blur()
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
        composerExpanded = true
        voiceActive = false
        if (inputPanel == InputPanel.MEDIA) inputPanel = InputPanel.NONE
        if (requestFocus) {
            keyboardDismissed = false
            inputRef.view?.focus()
        }
    }

    /** 输入态 → 默认态。草稿会留在输入框里，只是收起辅助区。 */
    private fun collapseComposer() {
        composerExpanded = false
        keyboardDismissed = false
        inputPanel = InputPanel.NONE
        clearActiveCommand()
        closeAssistantPanel()
        voiceActive = false
    }

    /**
     * 点击输入栏以外的区域（Kimi 规则）：
     * - 键盘在屏上：这一次点击只收键盘，输入栏保持输入态；
     * - 键盘已收起：这一次点击才把输入栏收回默认态。
     */
    private fun handleOutsideTap() {
        if (keyboardVisible || keyboardHeight > 0f) {
            keyboardDismissed = true
            inputRef.view?.blur()
            return
        }
        collapseComposer()
        inputRef.view?.blur()
    }

    /** Reads observable state inside each vif predicate so Kuikly can re-render it. */
    private fun isComposerExpanded(): Boolean =
        composerExpanded || inputPanel != InputPanel.NONE || keyboardVisible || keyboardHeight > 0f

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

    private fun renderComposerTextArea(container: ViewContainer<*, *>, compact: Boolean = false) {
        container.TextArea {
            ref { this@ChatPage.inputRef = it }
            attr {
                minHeight(40f)
                // 默认态那一行是 44dp 定高，草稿文字不能撑破它。
                maxHeight(if (compact) 44f else 80f)
                fontSize(14f)
                lineHeight(21f)
                color(this@ChatPage.theme.textPrimary)
                backgroundColor(Color(0xFFFFFFFF, 0f))
                text(this@ChatPage.viewModel.inputText)
                placeholder("问一只股票或一个术语")
                placeholderColor(this@ChatPage.theme.textTertiary)
                returnKeyTypeSend()
                enablePinyinCallback(true)
                // 只有"输入态 + 键盘没有被用户主动收起"时才自动聚焦。收键盘后这里
                // 必须保持 false，否则任何一次 TextArea 重新挂载都会把键盘顶回来。
                autofocus(
                    this@ChatPage.inputPanel != InputPanel.MEDIA &&
                        this@ChatPage.composerExpanded &&
                        !this@ChatPage.keyboardDismissed
                )
            }
            event {
                inputFocus {
                    // 键盘自己弹起（系统输入法回调 / 原生点击）也算进入输入态。
                    this@ChatPage.keyboardDismissed = false
                    this@ChatPage.expandComposer()
                }
                inputBlur {
                    // 失焦本身绝不收起输入栏：默认态与输入态各挂一个 TextArea，
                    // 切换时会重新挂载并先抛一次 blur，此时键盘事件还没回来。
                }
                textDidChange(isSyncEdit = true) {
                    this@ChatPage.handleComposerTextChanged(it.text)
                }
                textInputStateChange { state ->
                    this@ChatPage.viewModel.inputText = state.text
                    val composing = state.compositionStart != TextInputState.NO_COMPOSITION
                    this@ChatPage.updateTriggerSession(state.text, state.selectionEnd, composing)
                }
                keyboardHeightChange {
                    this@ChatPage.keyboardHeight = it.height
                    this@ChatPage.keyboardVisible = it.height > 0f
                    this@ChatPage.scheduleScrollChatToBottom()
                }
                inputReturn {
                    // 面板开着：Enter 优先确认高亮候选；面板关着才走发送。
                    if (!this@ChatPage.confirmHighlightedCandidate()) this@ChatPage.submitInput()
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
        if (state != "move" || cancelled) {
            println("[STOCKCHAT_DBG] handleStockEntityLongPress target=${entity.target} state=$state cancelled=$cancelled pending=${pendingEntitySheetSymbol}")
        }
        // Once "start" has fired, the long press is already recognised. Some
        // Android bridges mark the terminal event cancelled when the parent
        // scroller wins the final touch arbitration, so cancellation must not
        // discard a gesture that has already started.
        if (state == "move") {
            if (cancelled && pendingLongPressSymbol == entity.target) {
                pendingLongPressSymbol = ""
                finishStockLongPress(entity.target)
            }
            return
        }

        if (state == "start") {
            if (cancelled || pendingLongPressSymbol == entity.target) return
            pendingLongPressSymbol = entity.target
            suppressNextStockClickSymbol = entity.target
            println("[STOCKCHAT_DBG] handleStockEntityLongPress recognised(start) -> dispatch SHEET")
            handleStockEntity(entity, EntityAction.SHEET)
            return
        }

        if (state == "end" || cancelled) {
            if (pendingLongPressSymbol == entity.target) {
                pendingLongPressSymbol = ""
                finishStockLongPress(entity.target)
            }
            return
        }

        println("[STOCKCHAT_DBG] handleStockEntityLongPress ignored(state=$state)")
    }

    private fun finishStockLongPress(symbol: String) {
        println("[STOCKCHAT_DBG] finishStockLongPress symbol=$symbol mounted=${sheetCard?.cardId}")
        // Mount after the terminal touch callback returns. Kuikly's vif block
        // captures presentation values when it mounts, so mounting hidden and
        // toggling the flag later leaves the native view permanently invisible.
        setTimeout(16) {
            if (sheetCard?.cardId == "entity-sheet:$symbol") {
                sheetPresented = true
                sheetInteractive = true
                sheetMounted = true
                println("[STOCKCHAT_DBG] entity sheet mounted after gesture end")
            }
        }
        setTimeout(400) {
            if (suppressNextStockClickSymbol == symbol) {
                suppressNextStockClickSymbol = ""
            }
        }
    }

    private fun handleStockEntity(entity: EntitySpan, action: EntityAction) {
        println("[STOCKCHAT_DBG] handleStockEntity target=${entity.target} candidates=${entity.candidates.size} action=$action")
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
        println("[STOCKCHAT_DBG] openEntityQuoteSheet symbol=$symbol pendingBefore=${pendingEntitySheetSymbol} quoteForCached=${quoteFor(symbol) != null}")
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
        println("[STOCKCHAT_DBG] presentPendingEntitySheet symbol=$symbol pending=${pendingEntitySheetSymbol} quoteNull=${quote == null}")
        if (pendingEntitySheetSymbol != symbol) return
        if (quote == null) {
            pendingEntitySheetSymbol = ""
            return
        }
        pendingEntitySheetSymbol = ""
        println("[STOCKCHAT_DBG] -> openCardSheet for $symbol")
        openCardSheet(
            StockQuoteCardModel(quote, "entity-sheet:$symbol"),
            deferInteraction = pendingLongPressSymbol == symbol,
        )
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
        val firstRequest = requestedSymbols.add(symbol)
        println("[STOCKCHAT_DBG] requestQuote symbol=$symbol firstRequest=$firstRequest")
        if (!firstRequest) { println("[STOCKCHAT_DBG] requestQuote early-return(dup) symbol=$symbol"); return }
        if (!liveDataMode) {
            mockQuoteProvider.snapshot(symbol) { quote ->
                println("[STOCKCHAT_DBG] requestQuote mock callback symbol=$symbol quoteNull=${quote == null}")
                val updated = ChatQuoteState(symbol, quote, DataMode.OFFLINE)
                val index = quoteStates.indexOfFirst { it.symbol == symbol }
                if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
                presentPendingEntitySheet(symbol, quote)
            }
            return
        }
        quoteRepository.load(symbol) { result ->
            println("[STOCKCHAT_DBG] requestQuote live callback symbol=$symbol quoteNull=${result.quote == null}")
            val updated = ChatQuoteState(symbol, result.quote, result.mode)
            val index = quoteStates.indexOfFirst { it.symbol == symbol }
            if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
            presentPendingEntitySheet(symbol, result.quote)
        }
    }

    private fun toggleCardExpanded(cardKey: String) {
        expandedCardKey = if (expandedCardKey == cardKey) "" else cardKey
    }

    private var islandAnimating = false
    private fun toggleIsland() {
        // A tap can be re-delivered to stacked layers while the morph
        // re-layouts; ignore toggles until the animation settles.
        if (islandAnimating) return
        islandAnimating = true
        islandExpanded = !islandExpanded
        if (islandExpanded) requestQuote(islandSymbol)
        setTimeout(400) { islandAnimating = false }
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
            inputRef.view?.focus()
            return
        }
        var text = viewModel.inputText
        var cursor = if (composerCursor in 0..text.length) composerCursor else text.length
        if (session != null) {
            val end = cursor.coerceAtLeast(session.triggerStart).coerceAtMost(text.length)
            if (session.triggerStart <= end) {
                text = text.substring(0, session.triggerStart) + text.substring(end)
                cursor = session.triggerStart
            }
        }
        val before = text.substring(0, cursor)
        val after = text.substring(cursor)
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val inserted = (if (needsSpace) " " else "") + trigger
        val newText = before + inserted + after
        val newCursor = cursor + inserted.length
        // 原子写入「文本 + 光标」，避免 setText 后光标被重置到末尾。
        setComposerText(newText, newCursor)
        inputRef.view?.focus()
        updateTriggerSession(newText, newCursor, false)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    private fun setComposerText(text: String, cursor: Int = text.length) {
        val fixedCursor = cursor.coerceIn(0, text.length)
        viewModel.inputText = text
        composerCursor = fixedCursor
        inputRef.view?.setTextInputState(TextInputState(text, fixedCursor, fixedCursor))
    }

    /**
     * 用户输入编辑的唯一文本入口。
     *
     * 规范：
     * - 文本增删以 `textDidChange(sync)` 为准，保证 Android 上退格/清空也能同步到状态；
     * - 光标优先由 `textInputStateChange` 校准；若删除只上报 textDidChange，则按新旧长度推算；
     * - 不再用 selectionChange 回写文本，避免旧选区状态把刚删除的字符写回来。
     */
    private fun handleComposerTextChanged(text: String) {
        val oldText = viewModel.inputText
        val oldCursor = if (composerCursor in 0..oldText.length) composerCursor else oldText.length
        val delta = text.length - oldText.length
        val estimatedCursor = when {
            delta < 0 -> oldCursor + delta
            delta > 0 -> oldCursor + delta
            else -> oldCursor
        }.coerceIn(0, text.length)
        viewModel.inputText = text
        updateTriggerSession(text, estimatedCursor, triggerComposing)
    }

    /**
     * 触发状态机主入口（规范 10 §3）。
     *
     * 每次 textDidChange / textInputStateChange 时调用：用光标词法判定识别活动触发会话，
     * 据此切换联想面板（@ 提及 / / 命令选择 / 命令参数槽位）。组合态期间冻结面板
     * 显示"输入中…"，避免拼音候选与 @ 候选互相抖动。
     */
    private fun updateTriggerSession(text: String, cursor: Int, composing: Boolean) {
        // 记住光标，供左下角 @ / / 按钮在光标处插入触发字符。
        composerCursor = cursor
        triggerComposing = composing
        // 组合态：保留旧 session，仅刷新 composing 标记让面板显示"输入中…"。
        val session = if (composing) triggerSession else TriggerDetector.detect(text, cursor)
        val prev = triggerSession
        val sameTrigger = session != null && prev != null &&
            session.type == prev.type && session.triggerStart == prev.triggerStart &&
            session.query == prev.query
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
        val list = AtCandidateProvider.rank(session.query, recentMentions)
        atCandidates.clear()
        atCandidates.addAll(list)
        atHighlight = 0
        assistantPanel = AssistantPanel.AT_MENTION
    }

    private fun loadSlashCandidates(session: TriggerSession) {
        if (inputPanel == InputPanel.MEDIA) inputPanel = InputPanel.NONE
        val q = session.query
        // 命令名定型（精确命中）→ 进入参数槽位态。
        val resolved = CommandRegistry.resolve(q.removePrefix("/"))
        if (resolved != null && q.isNotBlank() && !q.contains(' ')) {
            enterCommandParams(resolved)
            return
        }
        val list = CommandRegistry.filter(q.removePrefix("/"))
        slashCandidates.clear()
        slashCandidates.addAll(list)
        slashHighlight = 0
        slashUnknown = if (list.isEmpty() && q.isNotEmpty()) q else ""
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
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        closeAssistantPanel()
        // 命令参数态下固化完一个 @ 标的，回到参数面板继续填下一个槽位。
        if (paramCommand != null) assistantPanel = AssistantPanel.COMMAND_PARAMS
    }

    /** 选中一个 / 命令 → 定型进入参数态或直接执行（规范 §5.3）。 */
    private fun selectSlashCommand(command: SlashCommand) {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        when (command.execution) {
            CommandExecution.LOCAL_ACTION -> {
                // /清屏：立即执行本地动作，不进入发送管线。
                if (command.id == "clear") { closeAssistantPanel(); viewModel.clear(); setComposerText("") }
            }
            CommandExecution.CONTEXT_ONLY -> {
                // /深水区：切换上下文标记并提示，不直接发送。
                val note = "深水区模式"
                if (deepContextNotes.contains(note)) deepContextNotes.remove(note) else deepContextNotes.add(note)
                closeAssistantPanel()
                setComposerText("")
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
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("/")) return null
        val name = trimmed.removePrefix("/").substringBefore(' ').substringBefore('@')
        val command = CommandRegistry.resolve(name) ?: return null
        val remainder = trimmed.substringAfter("/$name").trim()
        // 剥离 @ 固化提及文本后的纯文本片段（按空白切分）。
        var stripped = remainder
        for (m in mentions) stripped = stripped.replace(m.mentionText, "\u0001")
        val fragments = stripped.split(Regex("\\s+|\u0001+")).filter { it.isNotBlank() }
        val secIterator = mentions.iterator()
        val fragIterator = fragments.iterator()
        val args = LinkedHashMap<String, String>()
        for (param in command.params) {
            when (param.type) {
                ParamType.SECURITY -> {
                    val m = if (secIterator.hasNext()) secIterator.next() else null
                    args[param.key] = m?.name ?: if (fragIterator.hasNext()) fragIterator.next() else ""
                }
                ParamType.TEXT, ParamType.ENUM -> {
                    args[param.key] = if (fragIterator.hasNext()) fragIterator.next() else ""
                }
            }
        }
        return CommandInvocation(command.id, command.name, args)
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

    /** 涨跌幅格式化：+2.8% / -4.1% / —（无涨跌）。手写取整避免 Float 直转的长尾小数。 */
    private fun formatChgPct(pct: Float?): String {
        if (pct == null) return "—"
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
            ASSISTANT_PANEL_EMPTY_HEIGHT
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
        // 槽位数量由命令 schema 决定、不会跳动，高度按条数算：
        // 标题 32f + 每槽 40f + 底部提示 36f，超出上限则在框内滚动。
        val wanted = 32f + command.requiredParams.size * 40f + 36f
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
            command.requiredParams.forEach { param ->
                val filled = args[param.key].orEmpty()
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(4f); padding(6f); backgroundColor(page.theme.surfaceMuted); borderRadius(8f) }
                    View { attr { flex(1f); flexDirectionColumn() }
                        Text { attr { text(param.label + if (param.required) " *" else ""); fontSize(11f); color(page.theme.textSecondary) } }
                        Text { attr { text(if (filled.isNotEmpty()) filled else param.placeholder); fontSize(12f); color(if (filled.isNotEmpty()) page.theme.textPrimary else page.theme.textTertiary) } }
                    }
                    Text { attr { text(when (param.type) { ParamType.SECURITY -> "@" ; ParamType.ENUM -> "选" ; else -> "文" }); fontSize(9f); color(page.theme.textTertiary) } }
                }
            }
            View {
                attr { marginTop(8f); alignItemsCenter(); justifyContentCenter(); height(28f) }
                Text { attr { text("继续输入 @标的 或文字填充参数，回车发送"); fontSize(10f); color(page.theme.textTertiary) } }
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
        compareCard = null
        compareCandidateKey = ""
        compareCandidateSymbol = ""
    }

    private fun openCardSheet(model: CardModel, deferInteraction: Boolean = false) {
        val version = ++sheetPresentationVersion
        sheetMounted = false
        sheetCard = model
        sheetLevel = if (model.cardType == "stock-chart") SheetLevel.FULL else SheetLevel.HALF
        sheetPresented = !deferInteraction
        sheetInteractive = !deferInteraction
        println("[STOCKCHAT_DBG] openCardSheet model=${model.cardType} deferInteraction=$deferInteraction")
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

    private fun updateSubThread(cardId: String, update: (SubThreadState) -> SubThreadState) {
        val index = subThreads.indexOfFirst { it.cardId == cardId }
        if (index >= 0) subThreads[index] = update(subThreads[index])
    }

    private fun quoteFor(symbol: String): Quote? {
        val q = quoteStates.firstOrNull { it.symbol == symbol }?.quote ?: quoteRepository.cachedOrOffline(symbol)
        println("[STOCKCHAT_DBG] quoteFor symbol=$symbol found=${q != null}")
        return q
    }

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
    interactive: Boolean = presented,
    viewportHeight: Float,
    bottomInset: Float,
    onDismiss: () -> Unit,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    onPan: (String, Float) -> Unit,
    onOpenStock: (String) -> Unit,
    onTerm: (String) -> Unit,
) {
    println("[STOCKCHAT_DBG] CardSheetHost render model=${model.cardType} level=$level presented=$presented interactive=$interactive")
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
            touchEnable(interactive)
            animate(Animation.easeOut(0.20f), presented)
        }
        event { click { if (interactive) onDismiss() } }
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
            touchEnable(interactive)
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
