package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

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
import com.kuikly.stockchat.glass.applyGlassSurfaceSkin
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.ChatViewModel
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.StreamState
import com.kuikly.stockchat.chat.card.state.CardInteractionCoordinator
import com.kuikly.stockchat.chat.card.state.CardInteractionEffect
import com.kuikly.stockchat.chat.card.state.CardInteractionState
import com.kuikly.stockchat.chat.compare.state.CompareInsightCoordinator
import com.kuikly.stockchat.chat.compare.state.CompareInsightEffect
import com.kuikly.stockchat.chat.compare.state.CompareInsightRequester
import com.kuikly.stockchat.chat.compare.state.CompareInsightState
import com.kuikly.stockchat.chat.compare.state.CompareInsightStateHolder
import com.kuikly.stockchat.chat.compare.state.PagerCompareTextRevealerFactory
import com.kuikly.stockchat.chat.scroll.state.ChatScrollCoordinator
import com.kuikly.stockchat.chat.scroll.state.ChatScrollState
import com.kuikly.stockchat.chat.scroll.state.KuiklyChatScrollScheduler
import com.kuikly.stockchat.chat.composer.state.ComposerAttachmentCoordinator
import com.kuikly.stockchat.chat.composer.state.ComposerAttachmentState
import com.kuikly.stockchat.chat.composer.state.AssistantPanel
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantCoordinator
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantEffect
import com.kuikly.stockchat.chat.composer.state.ComposerAssistantState
import com.kuikly.stockchat.chat.composer.state.KuiklyComposerAssistantScheduler
import com.kuikly.stockchat.chat.composer.state.ComposerFocusCoordinator
import com.kuikly.stockchat.chat.composer.state.ComposerFocusEffect
import com.kuikly.stockchat.chat.composer.state.ComposerFocusState
import com.kuikly.stockchat.chat.composer.state.KuiklyComposerFocusScheduler
import com.kuikly.stockchat.chat.composer.state.KuiklyVoiceInputScheduler
import com.kuikly.stockchat.chat.composer.state.VoiceInputCoordinator
import com.kuikly.stockchat.chat.composer.state.VoiceInputEffect
import com.kuikly.stockchat.chat.composer.state.VoiceInputHostPort
import com.kuikly.stockchat.chat.composer.state.VoiceInputState
import com.kuikly.stockchat.chat.composer.component.ComposerCandidateRows
import com.kuikly.stockchat.chat.composer.state.KuiklyMediaSheetScheduler
import com.kuikly.stockchat.chat.composer.state.MAX_COMPOSER_ATTACHMENTS
import com.kuikly.stockchat.chat.composer.state.MediaSheetCoordinator
import com.kuikly.stockchat.chat.drawer.state.ChatDrawerCoordinator
import com.kuikly.stockchat.chat.drawer.state.ChatDrawerEffect
import com.kuikly.stockchat.chat.drawer.state.ChatDrawerState
import com.kuikly.stockchat.chat.drawer.state.DrawerGestureMotion
import com.kuikly.stockchat.chat.drawer.state.DrawerGesturePhase
import com.kuikly.stockchat.chat.drawer.state.KuiklyDrawerScheduler
import com.kuikly.stockchat.chat.entity.state.EntityAction
import com.kuikly.stockchat.chat.entity.state.EntityEffect
import com.kuikly.stockchat.chat.entity.state.EntityHostPort
import com.kuikly.stockchat.chat.entity.state.EntityInteractionCoordinator
import com.kuikly.stockchat.chat.entity.state.EntityState
import com.kuikly.stockchat.chat.entity.state.KuiklyEntityScheduler
import com.kuikly.stockchat.chat.message.state.KuiklyMessageActionScheduler
import com.kuikly.stockchat.chat.message.state.MessageActionCoordinator
import com.kuikly.stockchat.chat.message.state.MessageActionEffect
import com.kuikly.stockchat.chat.message.state.MessageActionFallback
import com.kuikly.stockchat.chat.message.state.MessageActionState
import com.kuikly.stockchat.chat.sheet.state.CardSheetCoordinator
import com.kuikly.stockchat.chat.sheet.state.CardSheetState
import com.kuikly.stockchat.chat.sheet.state.ChatSheetLevel
import com.kuikly.stockchat.chat.sheet.state.KuiklyCardSheetScheduler
import com.kuikly.stockchat.chat.session.state.FollowUpCoordinator
import com.kuikly.stockchat.chat.session.state.FollowUpState
import com.kuikly.stockchat.chat.session.state.KuiklyFollowUpScheduler
import com.kuikly.stockchat.chat.session.state.BackToTopCoordinator
import com.kuikly.stockchat.chat.session.state.BackToTopState
import com.kuikly.stockchat.chat.session.state.KuiklyBackToTopScheduler
import com.kuikly.stockchat.chat.session.state.ImagePreviewCoordinator
import com.kuikly.stockchat.chat.session.state.ImagePreviewState
import com.kuikly.stockchat.chat.session.state.SessionChromeCoordinator
import com.kuikly.stockchat.chat.session.state.SessionChromeState
import com.kuikly.stockchat.chat.session.component.ImagePreviewOverlay
import com.kuikly.stockchat.chat.session.component.MessageActionOverlay
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.PlatformProfile
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.openGlossary
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.LocalAlertProvider
import com.kuikly.stockchat.data.MarketDataPrefs
import com.kuikly.stockchat.data.MarketDataSource
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.QuotePrefetchStore
import com.kuikly.stockchat.data.provider.TencentQuoteProvider
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.kuikly.stockchat.chat.welcome.data.WelcomeStarterStore
import com.kuikly.stockchat.chat.welcome.state.ChatWelcomeCoordinator
import com.kuikly.stockchat.chat.welcome.state.ChatWelcomeEffect
import com.kuikly.stockchat.chat.welcome.state.ChatWelcomeState
import com.kuikly.stockchat.chat.welcome.state.KuiklyWelcomeScheduler
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.page.components.ChatDrawer
import com.kuikly.stockchat.page.components.CardSheetHost
import com.kuikly.stockchat.page.components.FeatureTile
import com.kuikly.stockchat.page.components.ActiveComparePanel
import com.kuikly.stockchat.page.components.TermComparePanel
import com.kuikly.stockchat.page.components.ChatMessageActions
import com.kuikly.stockchat.page.components.ChatMessageRenderState
import com.kuikly.stockchat.page.components.ChatMessageView
import com.kuikly.stockchat.page.components.DateDivider
import com.kuikly.stockchat.page.components.ComposerGuideRow
import com.kuikly.stockchat.page.components.RegressionQuestionRow
import com.kuikly.stockchat.chat.welcome.component.WelcomeMode
import com.kuikly.stockchat.chat.welcome.component.WelcomeSection
import com.kuikly.stockchat.chat.welcome.component.WelcomeStarter
import com.kuikly.stockchat.chat.welcome.component.defaultWelcomeStarters
import com.kuikly.stockchat.chat.welcome.component.randomWelcomeStarters
import com.kuikly.stockchat.page.components.ChatTopNav
import com.kuikly.stockchat.chat.island.state.IslandEffect
import com.kuikly.stockchat.chat.island.state.IslandGestureMotion
import com.kuikly.stockchat.chat.island.state.IslandHostPort
import com.kuikly.stockchat.chat.island.state.IslandState
import com.kuikly.stockchat.chat.island.state.KuiklyIslandScheduler
import com.kuikly.stockchat.chat.island.state.QuoteIslandCoordinator
import com.kuikly.stockchat.page.components.LineIconAudioLines
import com.kuikly.stockchat.page.components.LineIconClose
import com.kuikly.stockchat.page.components.LineIconFileText
import com.kuikly.stockchat.page.components.LineIconChevronRight
import com.kuikly.stockchat.page.components.LineIconKeyboard
import com.kuikly.stockchat.page.components.LineIconChevronUp
import com.kuikly.stockchat.page.components.LineIconPlus
import com.kuikly.stockchat.page.components.LineIconCamera
import com.kuikly.stockchat.page.components.LineIconPhoto
import com.kuikly.stockchat.page.components.LineIconStop
import com.kuikly.stockchat.page.components.VoiceBar
import com.kuikly.stockchat.voice.NativeBridgeVoiceRecorder
import com.kuikly.stockchat.voice.VoiceState
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.BrokenCardBlock
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.SkeletonBlock
import com.kuikly.stockchat.protocol.SuggestionsIntent
import com.kuikly.stockchat.protocol.SuggestionIntent
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
import com.kuikly.stockchat.composer.CatalogEntry
import com.kuikly.stockchat.composer.ComposerCatalog
import com.kuikly.stockchat.composer.CommandExecution
import com.kuikly.stockchat.composer.CommandInvocationParser
import com.kuikly.stockchat.composer.CommandInvocation
import com.kuikly.stockchat.composer.CommandParam
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
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.attr.AccessibilityRole
import com.tencent.kuikly.core.base.Color
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
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.TextInputState
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.SelectionType
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.Timer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Compatibility alias for non-chat gallery previews; Chat state owns the implementation. */
internal typealias SheetLevel = ChatSheetLevel

@Page(Routes.CHAT, supportInLocal = true)
internal class ChatPage : BasePager() {
    // 输入栏诊断日志统一 tag。logcat 过滤：adb logcat -s KLog 或搜 "Composer"。
    private companion object {
        const val COMPOSER_LOG_TAG = "Composer"
        // 焦点隔离诊断开关；正常交付必须关闭。完整输入栏使用无 Blur 的静态
        // 玻璃表皮，避免原生 Blur 覆盖层遮住 EditText 光标。
        const val COMPOSER_ISOLATION_TEST = false
        // 意外 blur 自动恢复的连续尝试上限。
        // 贴底目标偏移量的安全余量：native 侧对超出 contentH-viewH 的
        // setContentOffset 请求会静默无效，减 1px 规避浮点精度导致的误判。
        const val CHAT_SCROLL_HAIR_WIDTH = 1f
    }
    private val dependencies by lazy { ChatDependencies.forPager(pagerId) }
    private val viewModel by lazy { ChatViewModel(pagerId, dependencies) }
    // Welcome is a self-contained vertical slice: storage/data, state/timing,
    // component and this page-level platform-effect adapter.
    private val welcomeStarterStore by lazy {
        WelcomeStarterStore(
            PagerKeyValueStorage(pagerId),
            defaultWelcomeStarters().map { it.kind.name }.toSet(),
        )
    }
    private val welcomeState by lazy { ChatWelcomeState() }
    private val welcomeReducedMotion by lazy { platformPrefersReducedMotion() }
    private val welcomeCoordinator by lazy {
        ChatWelcomeCoordinator(
            state = welcomeState,
            starterStore = welcomeStarterStore,
            scheduler = KuiklyWelcomeScheduler(),
            reducedMotion = welcomeReducedMotion,
            onEffect = { effect ->
            when (effect) {
                ChatWelcomeEffect.HAPTIC_IMPACT ->
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                ChatWelcomeEffect.OPEN_MARKET -> openPage(Routes.MARKET)
            }
            },
            onRefreshStarters = ::refreshWelcomeStarters,
        )
    }
    // 推荐文案是普通快照；ObservableList 仅作为 vfor 的完整重建信号（R7）。
    private var welcomeStarterRenderKey: ObservableList<Int> by observableList()
    private var welcomeStarterSelection: List<WelcomeStarter> = defaultWelcomeStarters()
    private var previousWelcomeStarterLengths: List<Int> = welcomeStarterSelection.map { it.question.length }
    // 全页唯一、持久挂载的 TextArea。ref 只在首次 body 挂载前为空。
    private var inputRef: ViewRef<TextAreaView>? = null
    private var chatScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    private var chatContentHeight = 0f
    private var lastLoggedScrollY = -1f
    private var lastLoggedContentH = -1f
    private val chatScrollState = ChatScrollState()
    private val chatScrollCoordinator by lazy {
        ChatScrollCoordinator(
            state = chatScrollState,
            scheduler = KuiklyChatScrollScheduler(),
            onScrollToBottom = { animated -> scheduleScrollChatToBottom(animated) },
            onResetFollowUps = ::resetFollowUps,
            onScheduleFollowUps = ::scheduleFollowUpsPresentation,
            log = { message -> KLog.i(COMPOSER_LOG_TAG, message) },
        )
    }
    // ===== 实体交互状态层（长按 / 拖拽 / 投放 / 二义实体 / 行情预览）=====
    // 页面只保留：拖拽浮层渲染、纯查询（isIslandFirstCompareDrop）、Effect 执行。
    private val entityState = EntityState()
    private val entityCoordinator by lazy {
        EntityInteractionCoordinator(
            entityState,
            object : EntityHostPort {
                override fun pageWidth() = pagerData.pageViewWidth
                override fun pageHeight() = pagerData.pageViewHeight
                override fun statusBarHeight() = pagerData.statusBarHeight
                override fun safeAreaBottom() = pagerData.safeAreaInsets.bottom
                override fun keyboardHeight() = this@ChatPage.keyboardHeight
                override fun isIslandExpanded(): Boolean =
                    islandExpanded ||
                        islandCompareLeftSymbol.isNotEmpty() ||
                        islandTermKey.isNotEmpty() ||
                        islandTermCompareLeftKey.isNotEmpty()
                override fun displayName(symbol: String, fallback: String) =
                    entityDisplayName(symbol, fallback)
                override fun termName(key: String) = Glossary.byKey(key)?.term
            },
            KuiklyEntityScheduler(),
            ::handleEntityEffect,
        )
    }
    // 已发送图片的全屏预览。路径非空即挂载，关闭时清空以释放 Image 子树。
    private val imagePreviewState = ImagePreviewState()
    private val imagePreviewCoordinator = ImagePreviewCoordinator(imagePreviewState)
    private val imagePreviewPath: String get() = imagePreviewState.path

    private val peekSymbol: String get() = entityState.peekSymbol
    private val peekVisible: Boolean get() = entityState.peekVisible
    private val draggedEntity: EntitySpan? get() = entityState.draggedEntity
    private val entityDragActive: Boolean get() = entityState.dragActive
    private val entityDragX: Float get() = entityState.dragX
    private val entityDragY: Float get() = entityState.dragY
    private val entityDragName: String get() = entityState.dragName
    private val entityDropTarget: EntityDropTarget get() = entityState.dropTarget
    // Sheet interaction is gated independently so its fading layer cannot
    // accept late taps while it is being dismissed.
    private val cardSheetState = CardSheetState()
    private val cardSheetCoordinator by lazy {
        CardSheetCoordinator(cardSheetState, KuiklyCardSheetScheduler())
    }
    private val ambiguousSymbols: List<String> get() = entityState.ambiguousSymbols
    private val ambiguousEntityText: String get() = entityState.ambiguousText
    private val ambiguousAction: EntityAction get() = entityState.ambiguousAction
    // ===== 消息长按操作菜单（复制 / 追问）=====
    private val messageActionState = MessageActionState()
    private val messageActionCoordinator by lazy {
        MessageActionCoordinator(messageActionState, KuiklyMessageActionScheduler(), ::handleMessageActionEffect)
    }
    private val messageActionMounted: Boolean get() = messageActionState.mounted
    private val messageActionPresented: Boolean get() = messageActionState.presented
    private val messageActionX: Float get() = messageActionState.pageX
    private val messageActionY: Float get() = messageActionState.pageY
    private val messageActionFollowUp: Boolean get() = messageActionState.followUpAllowed
    // 每条消息气泡的 selectable 容器 ref（vfor 下必须按 messageId 分键，
    // 单 ref 会被最后挂载的消息覆盖）。生命周期与页面一致，条目级泄漏可忽略。
    private val messageSelectionRefs = mutableMapOf<String, ViewRef<DivView>>()
    // ===== 回答完成后的引导语 chips 双态机（R4 两帧入场）=====
    // 流结束 → 挂载一拍后 presented 翻转；重新流式/换会话即重置。
    private val followUpState = FollowUpState()
    private val followUpCoordinator = FollowUpCoordinator(followUpState, KuiklyFollowUpScheduler())
    private val followUpsMounted: Boolean get() = followUpState.mounted
    private val followUpsPresented: Boolean get() = followUpState.presented
    // ===== 文字输入态（展开 / 聚焦恢复 / 键盘避让）=====
    private val composerFocusState = ComposerFocusState()
    private val composerFocusCoordinator by lazy {
        ComposerFocusCoordinator(
            state = composerFocusState,
            scheduler = KuiklyComposerFocusScheduler(),
            onEffect = ::handleComposerFocusEffect,
            log = { message -> KLog.i(COMPOSER_LOG_TAG, message) },
        )
    }
    private val composerExpanded: Boolean get() = composerFocusState.expanded
    private val keyboardHeight: Float get() = composerFocusState.keyboardHeight
    private val keyboardVisible: Boolean get() = composerFocusState.keyboardVisible
    private val drawerState = ChatDrawerState()
    private val drawerCoordinator by lazy {
        ChatDrawerCoordinator(drawerState, KuiklyDrawerScheduler()) { effect ->
            when (effect) {
                ChatDrawerEffect.BLUR_COMPOSER -> blurComposer()
                ChatDrawerEffect.HAPTIC_IMPACT ->
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                ChatDrawerEffect.RESET_HISTORY_QUERY -> sessionChromeCoordinator.resetHistoryQuery()
            }
        }
    }
    private val drawerOpen: Boolean get() = drawerState.open
    private val drawerMounted: Boolean get() = drawerState.mounted
    private val drawerPresented: Boolean get() = drawerState.presented
    private val drawerGesture: DrawerGestureMotion get() = drawerState.motion
    // 原生 fling 侦察（大且快右向横滑 → 开抽屉）回调是否已注册：created 里
    // 注册一次即可（keepCallback），页面可见性由 pageVisible 守卫。
    private var drawerFlingHostRegistered = false
    private var pageVisible = false
    // 真实行情在页面级等待窗口内没有任何回调时，征询用户是否切换到本地 Mock。
    // 定时器由 PagerScope.setTimeout 调度，离页后 pageVisible 守卫保证不写 UI。
    private var marketFallbackPromptSymbol: String by observable("")
    private val pendingRealQuoteSymbols = mutableSetOf<String>()
    // 2026-09-08：行情数据模式只有"实时"一档（模拟分支已整体摘除），旧
    // liveDataMode 开关随之移除；顶部岛上的"实时"角标为常显。
    // 抽屉历史会话搜索词：drawer 的 Input 不受控，页面侧只存词 + 供 vbind 过滤；
    // 打开抽屉时重置，避免上次输入残留下次仍过滤。
    private val sessionChromeState = SessionChromeState()
    private val sessionChromeCoordinator = SessionChromeCoordinator(sessionChromeState)
    private var historySearchQuery: String
        get() = sessionChromeState.historyQuery
        set(value) { sessionChromeState.historyQuery = value }
    // ===== 对比会话（候选 / 卡片 / AI 流式解读）=====
    private val compareState = CompareInsightStateHolder()
    private val compareCoordinator by lazy {
        CompareInsightCoordinator(
            state = compareState,
            requester = object : CompareInsightRequester {
                override fun request(
                    prompt: String,
                    onDelta: (String) -> Unit,
                    onDone: () -> Unit,
                    onError: (String) -> Unit,
                ) = viewModel.askSubThread(prompt, onDelta, onDone, onError)
            },
            revealerFactory = PagerCompareTextRevealerFactory(pagerId),
            onEffect = ::handleCompareEffect,
        )
    }
    private val compareCandidateKey: String get() = compareState.candidateCardKey
    private val compareCandidateSymbol: String get() = compareState.candidateSymbol
    private val compareCard: StockCompareCardModel? get() = compareState.card
    private val compareInsightState: CompareInsightState get() = compareState.insightState
    private val compareInsightText: String get() = compareState.insightText
    private val compareInsightError: String get() = compareState.insightError
    // Dynamic island: the top title capsule morphs into a live quote card.
    // 状态机（展开/收敛/自动收起/详情交接/返回复位/对比 lobby）由
    // QuoteIslandCoordinator 独占；页面只保留自选态、实体拖拽字段与 Effect
    // 执行（见 chat/island/state）。字段以只读 getter 转发到 islandState 的
    // observable，保证 DSL 闭包内的读取仍建立反应式依赖（同 Drawer 模式）。
    private val islandState = IslandState()
    private val islandCoordinator by lazy {
        QuoteIslandCoordinator(
            state = islandState,
            host = object : IslandHostPort {
                override fun isPageVisible() = pageVisible
                override fun pageHeight() = pagerData.pageViewHeight
                override fun hasCompareCard() = compareCoordinator.hasCard()
            },
            scheduler = KuiklyIslandScheduler(),
        ) { effect -> handleIslandEffect(effect) }
    }
    private val islandExpanded: Boolean get() = islandState.expanded
    private val islandSymbol: String get() = islandState.symbol
    private val islandGestureMotion: IslandGestureMotion get() = islandState.motion
    private val islandMounted: Boolean get() = islandState.mounted
    private val islandCompareLeftSymbol: String get() = islandState.compareLeftSymbol
    private val islandCompareRightSymbol: String get() = islandState.compareRightSymbol
    private val islandCompareVisible: Boolean get() = islandState.compareVisible
    private val islandTermKey: String get() = islandState.termKey
    private val islandTermCompareLeftKey: String get() = islandState.termCompareLeftKey
    private val islandTermCompareRightKey: String get() = islandState.termCompareRightKey
    private val islandTermCompareVisible: Boolean get() = islandState.termCompareVisible
    private var islandWatchlisted: Boolean by observable(false)
    // Page data is injected after construction; use the safe fallback until created().
    private var glassMode: GlassRenderingMode
        get() = sessionChromeState.glassMode
        set(value) { sessionChromeState.glassMode = value }
    private var glassModeManuallySelected: Boolean
        get() = sessionChromeState.glassModeManuallySelected
        set(value) { sessionChromeState.glassModeManuallySelected = value }
    // 输入栏媒体面板与附件数据均由独立 Coordinator 管理；Page 仅处理原生 Effect。
    private val composerAttachmentState = ComposerAttachmentState()
    private val composerAttachmentCoordinator = ComposerAttachmentCoordinator(composerAttachmentState)
    private val mediaSheetCoordinator by lazy {
        MediaSheetCoordinator(composerAttachmentState, KuiklyMediaSheetScheduler())
    }
    private var composerMediaHostRegistered = false
    // ===== @ 提及与 / 指令状态机（规范见 docs/10-输入栏@提及与斜杠指令交互规范_v1.0.md） =====
    // 联想面板维度（与 MEDIA 面板正交）：@ 触发 / / 触发 / 命令参数槽位。
    private val composerAssistantState = ComposerAssistantState()
    private val composerAssistantCoordinator = ComposerAssistantCoordinator(
        state = composerAssistantState,
        scheduler = KuiklyComposerAssistantScheduler(),
        onEffect = ::handleComposerAssistantEffect,
    )
    private var assistantPanel: AssistantPanel
        get() = composerAssistantState.panel
        set(value) { composerAssistantState.panel = value }
    // 活动触发会话；同一时刻最多一个（规范 §3.1）。
    private var triggerSession: TriggerSession?
        get() = composerAssistantState.triggerSession
        set(value) { composerAssistantState.triggerSession = value }
    // 文本、选区、组合区必须作为一个原子快照流转，不允许分散字段被
    // textDidChange / selectionChange 交叉覆盖。
    private var composerEditingState = TextInputState("")
    // 拼音组合态：面板冻结显示"输入中…"（规范 §3.5）。
    private var triggerComposing: Boolean
        get() = composerAssistantState.composing
        set(value) { composerAssistantState.composing = value }
    private val atCandidates: ObservableList<AtCandidate> get() = composerAssistantState.atCandidates as ObservableList<AtCandidate>
    private var atHighlight: Int
        get() = composerAssistantState.atHighlight
        set(value) { composerAssistantState.atHighlight = value }
    private val slashCandidates: ObservableList<SlashCommand> get() = composerAssistantState.slashCandidates as ObservableList<SlashCommand>
    private var slashHighlight: Int
        get() = composerAssistantState.slashHighlight
        set(value) { composerAssistantState.slashHighlight = value }
    // 未知命令提示（规范 §5.6）：非空时面板显示"没有找到 /xxx"。
    private var slashUnknown: String
        get() = composerAssistantState.slashUnknown
        set(value) { composerAssistantState.slashUnknown = value }
    private var lastTrackedTriggerKey: String
        get() = composerAssistantState.lastTrackedTriggerKey
        set(value) { composerAssistantState.lastTrackedTriggerKey = value }
    private var lastTrackedUnknownSlash: String
        get() = composerAssistantState.lastTrackedUnknownSlash
        set(value) { composerAssistantState.lastTrackedUnknownSlash = value }
    // 固化提及注册表：只存实体顺序，激活态每次从文本扫描得出（规范 §4.6）。
    private val mentionEntities: MutableList<MentionEntity> get() = composerAssistantState.mentionEntities
    // 上下文标记（/深水区等），注入 system context（规范 §4.8）。
    private val deepContextNotes: MutableList<String> get() = composerAssistantState.deepContextNotes
    private var deepContextVersion: Int
        get() = composerAssistantState.deepContextVersion
        set(value) { composerAssistantState.deepContextVersion = value }
    // 路由带来的焦点标的（详情页/预警/星图等场景经 openChatWithQuestion 传入）。
    // 不进 mentionEntities（那里靠文本对账，问题文本里没有 @名称 会被剔除），
    // 而是在 buildSendPayload 组包时无条件并入 mentions——入口显式给了标的，
    // 信任之；与文本扫描结果由 ChatQuoteContext 按 symbol 去重。
    private var routeFocusMention: MentionEntity?
        get() = composerAssistantState.routeFocusMention
        set(value) { composerAssistantState.routeFocusMention = value }
    private var commandValidationMessage: String
        get() = composerAssistantState.validationMessage
        set(value) { composerAssistantState.validationMessage = value }
    // / 命令参数态（规范 §5.4）。参数值不落字段，每次从输入文本实时解析，
    // 保证面板显示与最终发送用的是同一套解析结果。
    private var paramCommand: SlashCommand?
        get() = composerAssistantState.paramCommand
        set(value) { composerAssistantState.paramCommand = value }
    // 最近提及（S1 数据源，最多 5 条，新的在前）。
    private val recentMentions: MutableList<String> get() = composerAssistantState.recentMentions
    // ===== @ 候选实时化（规范 10 §4.2 S5 / P5）=====
    // 远端搜索建议池：会话内累积，rank 时并入打分；Observable 以驱动候选行重渲染。
    private val remoteEntries: ObservableList<CatalogEntry> get() = composerAssistantState.remoteEntries as ObservableList<CatalogEntry>
    // 候选涨跌回填：面板活跃代数 + 已请求 symbol 去重（行情快照走 QuoteRepository 缓存）。
    // 参数面板重渲染 key（R1）：ConditionView 的 creator 只在条件翻转时构建一次内容，
    // 参数态下打字/点选导致的 args 变化必须靠 vfor 的 collection 操作整帧重建面板
    // （2026-09-10 真机复现：参数面板在打字期间完全冻结）。bump = clear+add 产生
    // REMOVE/ADD 操作对，vfor 逐项重建。
    private val paramPanelRenderKey: ObservableList<Int> get() = composerAssistantState.paramPanelRenderKey as ObservableList<Int>
    // Composer state machine (规范见 docs/09-输入栏默认态与输入态转换规范_v1.0.md).
    // 默认态 → 输入态由点击/聚焦/开面板触发；输入态是"粘"的：收起键盘不再回退，
    // 只有"键盘已收起时点击非输入栏区域"这一次点击才回到默认态。
    // Voice input is the composer's third state (docs/11): hold to record,
    // release to transcribe/send, slide up to cancel. Its timing and recorder
    // callbacks live in VoiceInputCoordinator; this page exposes reactive getters to DSL.
    private val voiceInputState = VoiceInputState()
    private var voiceInputCoordinatorInstance: VoiceInputCoordinator? = null
    private val voiceInputCoordinator: VoiceInputCoordinator
        get() = voiceInputCoordinatorInstance ?: VoiceInputCoordinator(
            state = voiceInputState,
            recorder = NativeBridgeVoiceRecorder(acquireModule(BridgeModule.MODULE_NAME)),
            scheduler = KuiklyVoiceInputScheduler(),
            host = object : VoiceInputHostPort {
                override fun inputTextIsBlank() = viewModel.inputText.isBlank()
                override fun isAnswerStreaming() = viewModel.streamState == StreamState.STREAMING
            },
            onEffect = ::handleVoiceInputEffect,
        ).also { voiceInputCoordinatorInstance = it }
    private val voiceState: VoiceState get() = voiceInputState.state
    private val voiceCancelArmed: Boolean get() = voiceInputState.cancelArmed
    private val voiceElapsedSec: Float get() = voiceInputState.elapsedSeconds
    private val voiceAmps: FloatArray get() = voiceInputState.amplitudes
    private val voiceMicFill: Float get() = voiceInputState.micFill
    private val voiceInputMode: Boolean get() = voiceInputState.inputMode
    // Card sheets themselves have a dedicated coordinator; this owner covers
    // card-local expansion, focus, repair and deep-dive threads.
    private val cardInteractionState = CardInteractionState()
    private val cardInteractionCoordinator by lazy {
        CardInteractionCoordinator(cardInteractionState, ::handleCardInteractionEffect)
    }
    private val expandedCardKey: String get() = cardInteractionState.expandedCardKey
    private val focusedCardKey: String get() = cardInteractionState.focusedCardKey
    private val repairingCardKey: String get() = cardInteractionState.repairingCardKey
    private val drilledKeys: MutableList<String> get() = cardInteractionState.drilledKeys
    private val subThreads get() = cardInteractionState.subThreads
    // 回到顶部悬浮按钮：mounted/presented 双态机（同 drawer 模式，R4——vif 挂载
    // 的视图首帧不播动画，挂载后一拍再翻 presented）；version 使过期回调失效。
    private val backToTopState = BackToTopState()
    private val backToTopCoordinator = BackToTopCoordinator(
        backToTopState,
        KuiklyBackToTopScheduler(),
    ) { chatScrollerRef?.view?.setContentOffset(0f, 0f, true) }
    private val chatBackToTopMounted: Boolean get() = backToTopState.mounted
    private val chatBackToTopPresented: Boolean get() = backToTopState.presented
    // 入场缩放结束后才落投影，避免原生在缩放首帧按未裁切矩形绘制阴影。
    private val chatBackToTopShadowVisible: Boolean get() = backToTopState.shadowVisible
    // 程序化动画回顶进行中：暂停按钮显隐判定，避免动画过程中的中间 scroll
    // 事件（offsetY 仍很大）把按钮又弹出来。
    private val quoteRepository get() = dependencies.quoteRepository
    private val watchlistStore get() = dependencies.watchlistStore
    private val alertStore get() = dependencies.alertStore
    private val glossaryStore get() = dependencies.glossaryStore
    private var alertPollGeneration = 0
    private val deliveredAlertBuckets = mutableSetOf<String>()
    private var quoteStates: ObservableList<ChatQuoteState> by observableList()
    private val requestedSymbols = mutableSetOf<String>()
    private var pendingRouteQuestion: String = ""
    /** 冒烟钩子：params.autoAsk 非空时，路由问句注入后自动发送（本机无辅助功能权限，无法驱动真实点击）。 */
    private var pendingAutoAsk: Boolean = false
    /** 冒烟钩子：自动发送相对 pageDidAppear 的延迟（ms），默认 600。 */
    private var pendingAutoAskDelay: Int = 600
    /** 冒烟钩子：params.smokeMedia = library|camera|document，页面出现后自动走一次媒体入口（验证 iOS 桥）。 */
    private var pendingSmokeMediaSource: String = ""
    /** 冒烟钩子：params.smokeVoice = 1，页面出现后自动开一段录音并在 2.5s 后松手（验证 iOS 语音桥）。 */
    private var pendingSmokeVoice: Boolean = false
    private var pendingRouteFocusNote: String = ""
    private var pendingRouteFocusSymbol: String = ""
    // 输入框渐变描边流动相位（0..2π）：composerRimFlowTimer 以 20fps 推进，
    // renderComposerGradientRim 的 Canvas draw 闭包内读取本值驱动重绘
    // （VoiceBar 同款 ReactiveObserver 范式）。页面不可见即停，省电。
    private var composerRimPhase: Float by observable(0f)
    private var composerRimFlowTimer: Timer? = null
    // 输入栏折叠/展开的图标入场（R4）：vif 新挂载的视图首帧不播动画，展开态与
    // 折叠态图标的显隐经 mounted→presented 两帧翻转驱动（drawer 同款范式）。
    // 展开态图标以 presented 为入场驱动；折叠态图标以 !presented 为入场驱动，
    // 收起时对称回放。同值赋值不通知，翻转失败时也不会留下中间态。
    private var composerChromePresented: Boolean by observable(false)
    private var composerChromeVersion = 0
    private var composerChromeTimer: Timer? = null
    private val composerReducedMotion by lazy { platformPrefersReducedMotion() }
    private val theme: StockChatTheme get() = appTheme()
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
        peekSymbol.isNotEmpty() || drawerOpen || cardSheetState.model != null
    ) 1 else 0

    override fun hostGlassModeDidChange(renderer: GlassRenderer) {
        sessionChromeCoordinator.syncHostGlassMode(renderer.mode)
    }

    override fun created() {
        super.created()
        glassMode = hostGlassRenderer.mode
        pendingRouteQuestion = pagerData.params.optString("question")
        pendingAutoAsk = pagerData.params.optString("autoAsk").isNotEmpty()
        pendingAutoAskDelay = pagerData.params.optString("autoAskDelay").toIntOrNull() ?: 600
        pendingRouteFocusNote = pagerData.params.optString("focusNote")
        pendingRouteFocusSymbol = pagerData.params.optString("focusSymbol")
        StockCardRenderers.ensureRegistered()
        registerDrawerFlingHostIfNeeded()
        registerComposerMediaResultHostIfNeeded()
        refreshWelcomeStarters()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        pageVisible = false
        alertPollGeneration++
        chatScrollCoordinator.onDisappear()
        mediaSheetCoordinator.reset()
        // Coordinator cancels and version-guards every welcome callback here;
        // leaving a page must never let a stale timer mutate its observables.
        welcomeCoordinator.onDisappear()
        stopComposerRimFlow()
        // If the stock detail route is covering this page, JS state may
        // already read as idle while the native view is still waiting for the
        // collapsed-frame write. Force the write unconditionally.
        // 交接遮罩期间例外：详情页整页淡入还没完成，全屏玻璃帧是它的底，
        // 提前归位会在淡入的前半段透出聊天页。
        islandCoordinator.onPageDisappear()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        pageVisible = true
        islandCoordinator.onPageAppear()
        viewModel.refreshConfigStatus()
        islandWatchlisted = watchlistStore.contains(islandSymbol)
        // Preload the island quote so the morph opens with data in place.
        requestQuote(islandSymbol)
        // 详情页预取（2026-09-10 空白期治理）：进应用后把自选标的行情预热进全局
        // 预取缓存，点进详情页时 created() 直接命中整页秒开（60s 新鲜窗口内不重复
        // 请求；点按瞬间 openStockDetail 还有一次单标的兜底预热）。
        QuotePrefetchStore.warm(watchlistStore.symbols(), TencentQuoteProvider(pagerId))
        startAlertPolling()
        chatScrollCoordinator.onAppear()
        welcomeCoordinator.onAppear(
            sessionEmpty = viewModel.messages.isEmpty(),
            fullMode = welcomeMode() == WelcomeMode.FULL,
        )
        startComposerRimFlow()
        consumeRouteQuestionIfNeeded()
        runSmokeBridgeHooksIfNeeded()
    }

    /**
     * 冒烟钩子（simctl 专用）：只有 iOS 宿主经 env 注入 params 才会触发，
     * Android/鸿蒙永远收不到这两个参数。媒体入口复用 handleMediaAction
     * 真实路径；语音复用 startVoiceSession/finishVoiceSession 真实状态机。
     */
    private fun runSmokeBridgeHooksIfNeeded() {
        if (pendingSmokeMediaSource.isNotEmpty()) {
            val action = ComposerMediaAction.entries.firstOrNull { it.source == pendingSmokeMediaSource }
                ?: ComposerMediaAction.entries.first()
            pendingSmokeMediaSource = ""
            KLog.i(COMPOSER_LOG_TAG, "smoke media action ${action.source}")
            setTimeout(1200) {
                if (!isWillDestroy()) handleMediaAction(action)
            }
        }
        if (pendingSmokeVoice) {
            pendingSmokeVoice = false
            setTimeout(1500) {
                if (isWillDestroy()) return@setTimeout
                KLog.i(COMPOSER_LOG_TAG, "smoke voice start")
                startVoiceSession()
                setTimeout(2500) {
                    if (!isWillDestroy() && voiceState == VoiceState.RECORDING) {
                        KLog.i(COMPOSER_LOG_TAG, "smoke voice finish")
                        finishVoiceSession()
                    }
                }
            }
        }
    }

    override fun pageWillDestroy() {
        followUpCoordinator.onDestroy()
        backToTopCoordinator.onDestroy()
        voiceInputCoordinatorInstance?.onDestroy()
        composerFocusCoordinator.onDestroy()
        drawerCoordinator.onDestroy()
        islandCoordinator.onDestroy()
        entityCoordinator.onDestroy()
        compareCoordinator.onDestroy()
        composerAssistantCoordinator.onDestroy()
        messageActionCoordinator.onDestroy()
        welcomeCoordinator.onDestroy()
        chatScrollCoordinator.onDestroy()
        mediaSheetCoordinator.reset()
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            // 主内容平移层（用户决策 2026-09-08）：抽屉展开时整页内容（聊天
            // 列表 + 顶栏 + 输入栏）像被抽屉迎面"推"开一样整体右移，收起时
            // 整体左移回位。手势跟手阶段与面板同速落位；归位/程序化开合与
            // ChatDrawer 面板逐分支同构（同驱动、同曲线、同时长），保证两层
            // 消费的动画注册一致、严格同步。模态层（对比弹窗/CardSheet/抽屉
            // 本体）留在根层级不平移。
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    val motion = page.drawerGesture
                    val shown = page.drawerPresented
                    // 面板位移 [-292, 0] → 主内容位移 [0, 292]，与面板互为镜像。
                    val panelX =
                        if (motion.phase == DrawerGesturePhase.IDLE) {
                            if (shown) 0f else -292f
                        } else {
                            motion.offsetX
                        }
                    transform(translate = Translate(0f, 0f, offsetX = panelX + 292f))
                    // R2/R3：分支互斥、每分支恰一次 animate；实参位置现场再读
                    // observable（RowGestureLayer 范式），与 ChatDrawer 面板一致。
                    when (motion.phase) {
                        DrawerGesturePhase.IDLE ->
                            animate(
                                if (shown) Animation.easeOut(0.375f) else Animation.easeIn(0.275f),
                                page.drawerPresented,
                            )
                        DrawerGesturePhase.DRAGGING -> Unit // 跟手：直接落位，不注册动画
                        DrawerGesturePhase.SETTLING ->
                            animate(
                                if (motion.offsetX > -146f) Animation.easeOut(0.375f) else Animation.easeIn(0.30f),
                                page.drawerGesture,
                            )
                    }
                }
                Scroller {
                    ref { page.chatScrollerRef = it }
                    attr {
                        flex(1f)
                        // Reserve resting space for the floating chrome while
                        // allowing content to travel underneath it as it scrolls.
                        paddingTop(page.pagerData.statusBarHeight + 52f)
                        paddingLeft(14f)
                        // Kuikly 竖向 Scroller 测量子项宽 = 视宽 - 2×(左+右 padding)，
                        // 右 padding 被双倍扣除：14/14 时聊天内容右侧实测多出 28dp 留白
                        // （真机 1440px 宽，卡片右缘 1271px ≈ 预测的 w-2×28 位置）。
                        // 右侧留 0，留白交由子项自身 margin 补齐，实测左右各 20dp 对齐。
                        paddingRight(0f)
                        // 空欢迎页不再为聊天气泡预留 190dp 尾部；推荐卡后没有空白区。
                        paddingBottom(
                            if (page.viewModel.messages.isEmpty()) 0f
                            else 190f + page.pagerData.safeAreaInsets.bottom,
                        )
                    }
                    event {
                        contentSizeChanged { _, height ->
                            page.chatContentHeight = height
                            page.handleChatContentSizeGrew()
                        }
                        scroll { params ->
                            page.handleChatStreamScroll(params)
                        }
                        // 按住非交互区域（气泡/卡片自己的长按会被消费、不冒泡上来）
                        // 也视为用户接管列表，暂停跟随
                        longPress { page.chatScrollCoordinator.onUserScroll(isAtBottom = false) }
                        // 点击列表非输入栏区域：展开态先收键盘，键盘已收起才回到默认态
                        click { page.handleOutsideTap() }
                    }
                    // activeSessionId 是会话树的重建键。新建对话时先替换整棵会话
                    // 内容树，再由内部 vif/vfor 画空态或消息；这是应用内等价于用户
                    // 手动退出、重新进入页面的恢复动作，规避原生列表复用偶发残留。
                    // 前缀拼 themeRebuildKey：欢迎区/气泡/卡片都以参数捕获 theme，
                    // 换肤（明暗/字号档）时靠键翻转整树重建拿到新配色。
                    vbind({ page.themeRebuildKey() + "|" + page.viewModel.activeSessionId }) {
                        vbind({ page.viewModel.streamState }) {
                            page.chatScrollCoordinator.onStreamStateObserved(
                                page.viewModel.streamState == StreamState.STREAMING,
                            )
                            View { attr { height(0f); touchEnable(false) } }
                        }
                        // 仅由消息是否为空决定欢迎区的存在。不要把这里再绑定到动效的
                        // mounted 状态：内容可见性必须独立于任何异步动画调度。
                        vif({ page.viewModel.messages.isEmpty() }) {
                            WelcomeSection(
                            theme = page.theme,
                            // 取值闭包，不能在这里直接读 observable：vif 体只执行一次，
                            // 读到的快照不会建立依赖，attr 不重跑、animate() 也拿不到
                            // observablePropertyKey，整块动效会静默失效。
                            rotatingKeyword = { page.welcomeState.rotatingKeyword },
                            cursorVisible = { page.welcomeState.cursorVisible },
                            entranceVisible = { page.welcomeState.entranceVisible },
                            starterRenderKeys = page.welcomeStarterRenderKey,
                            starters = { page.welcomeStarterSelection },
                            onMounted = page.welcomeCoordinator::onWelcomeMounted,
                            marketTabSelected = { page.welcomeState.marketTabSelected },
                            onOpenMarket = page.welcomeCoordinator::onOpenMarketRequested,
                            reduceMotion = page.welcomeReducedMotion,
                            ) { starter ->
                                page.chooseWelcomeStarter(starter)
                            }
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
                                onTerm = {
                                    // 长按术语后部分 bridge 会补发 click，抑制之。
                                    if (!page.consumeTermClickSuppression(it)) {
                                        // 用户卡在术语上主动点高亮 = 一次真实「遇到」（doc 24 §6.3）。
                                        Glossary.keyForToken(it)?.let(page.glossaryStore::encounter)
                                        page.viewModel.send("$it 是什么意思")
                                    }
                                },
                                onTermLongPress = page::handleTermEntityLongPress,
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
                            onSelectionContainerRef = page::handleSelectionContainerRef,
                            onTextSelectionLongPress = page::handleTextSelectionLongPress,
                            onTextSelectEnd = page::handleTextSelectEnd,
                            onTextSelectCancel = page::handleTextSelectCancel,
                            // 消息操作图标：复制 / 重试（截断重生成）/ 分享。
                            onCopyMessage = page::copyMessageContent,
                            onShareMessage = page::copyShareCard,
                            onRegenerate = page::regenerateMessage,
                            onPreviewImage = page::openImagePreview,
                            // 引导语：完成后的追问 chips（闭包在 vif 内调用，读 observable
                            // 建立依赖；页侧双态机驱动两帧入场）。
                            followUpsVisible = page::isFollowUpsVisible,
                            followUpsChips = page::followUpChipsFor,
                            followUpsMounted = { page.followUpsMounted },
                            followUpsPresented = { page.followUpsPresented },
                        ),
                            )
                        }
                    }
                }
                // Declare the overlay after its backdrop source so Android paints
                // the glass above the moving conversation rather than beneath it.
                // ChatTopNav 以参数捕获 theme（body 只跑一次的首帧快照），attr 内
                // 读到的都是旧值；必须靠 vbind 键翻转整树重建，换肤时状态栏底色
                // 渐变层与导航行两层才会跟随新主题。
                vbind({ page.themeRebuildKey() }) {
                ChatTopNav(
                    statusBarHeight = page.pagerData.statusBarHeight,
                    theme = page.theme,
                    drawerOpen = { page.drawerOpen },
                    liveData = { true },
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
                    // 股票对比与术语对比共用同一套 lobby 几何，内容层按会话分流。
                    islandCompareVisible = {
                        page.isIslandCompareLobbyVisible() || page.isIslandTermCompareLobbyVisible()
                    },
                    islandTermEntry = { Glossary.byKey(page.islandTermKey) },
                    islandCompareIsTerm = { page.islandTermCompareLeftKey.isNotEmpty() },
                    islandTermCompareLeft = { Glossary.byKey(page.islandTermCompareLeftKey) },
                    islandTermCompareRight = { Glossary.byKey(page.islandTermCompareRightKey) },
                    islandTextOnly = { false },
                    islandCompareInsightLoading = { page.compareInsightState == CompareInsightState.LOADING },
                    islandCompareInsightAvailable = { page.compareInsightState == CompareInsightState.READY },
                    onToggleIsland = { page.toggleIsland() },
                    onIslandGesture = { state, y -> page.handleIslandGesture(state, y) },
                    onIslandMotionComplete = { key -> page.completeIslandMotion(key) },
                    onIslandInteraction = { page.noteIslandInteraction() },
                    onToggleIslandWatchlist = { symbol -> page.toggleIslandWatchlist(symbol) },
                    onOpenIslandCompare = { page.openIslandComparePanel() },
                    onClearIslandCompare = { page.clearIslandCompare() },
                    onMenu = { page.updateDrawerOpen(!page.drawerOpen) },
                    onNewChat = { page.startNewChat() },
                    onSearch = { page.openPage(Routes.SEARCH) },
                )
                }
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
                                fontSizeScaled(12f)
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
                                    Text { attr { text(security?.name ?: symbol); fontSizeScaled(12f); color(page.theme.brand) } }
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
                            if (quote == null) Text { attr { text("正在获取 ${page.peekSymbol} 的行情…"); fontSizeScaled(12f); color(page.theme.textTertiary) } }
                            else CardShell(
                                StockQuoteCardModel(quote),
                                CardContext(page.theme, CardDensity.MINI, { page.openStockDetail(it) }, glass = page.glassRenderer),
                            )
                        }
                        View {
                            attr { padding(9f); borderRadius(9f); backgroundColor(page.theme.surfaceMuted) }
                            Text { attr { text("收起"); fontSizeScaled(11f); color(page.theme.textSecondary) } }
                            event { click { page.dismissPeek() } }
                        }
                        View {
                            attr { marginLeft(7f); padding(9f); borderRadius(9f); backgroundColor(page.theme.brand) }
                            Text { attr { text("看详情"); fontSizeScaled(11f); fontWeightMedium(); color(page.theme.onBrand) } }
                            event { click { page.openStockDetail(page.peekSymbol) } }
                        }
                    }
                }
                // 回到顶部悬浮按钮：会话不在顶部时浮现于输入栏右上方，液态玻璃表皮
                // + ^ 图标；点击平滑滚回顶部（animated，而非闪现）。
                vif({
                    page.chatBackToTopMounted &&
                        page.viewModel.messages.isNotEmpty() &&
                        page.keyboardHeight == 0f &&
                        !page.isComposerExpanded()
                }) {
                    View {
                        attr {
                            absolutePosition(
                                right = 14f,
                                // 130f = 输入栏(10+66) + 引导语胶囊块(≈48) 再留 6dp
                                // 间隙：按钮底边须完全让开输入栏上方的引导语胶囊
                                // （2026-09-10 用户反馈：二者重叠）。
                                bottom = 130f + page.pagerData.safeAreaInsets.bottom,
                            )
                            size(40f, 40f)
                            allCenter()
                            // 圆形轮廓：玻璃 peek 圆角 20f 只作用在 GlassBackdrop 的
                            // Blur 内层，容器自身不设 borderRadius 时描边与 boxShadow
                            // 都按方角渲染——阴影呈正方形雏形（2026-09-10 用户反馈）。
                            borderRadius(20f)
                            // 玻璃高光描边：GlassBackdrop 不带描边，细 rim 由容器补
                            // （与 peek 胶囊 resolved.stroke 对齐）。
                            border(Border(1f, BorderStyle.SOLID, Color(0xFFFFFF, 0.35f)))
                            // 缩放入场时原生阴影会短暂按矩形边界绘制。落稳后才给
                            // 阴影；透明值用于主动清除上一次挂载留下的 native shadow。
                            val shadowVisible = page.chatBackToTopShadowVisible
                            boxShadow(
                                BoxShadow(
                                    0f,
                                    8f,
                                    22f,
                                    Color(0x000000, if (shadowVisible) 0.16f else 0f),
                                ),
                            )
                            // 无障碍属性属于 attr 方法，必须写在 attr 块内（写在
                            // builder 作用域会因解析不到而编译失败）。
                            accessibility("回到顶部")
                            accessibilityRole(AccessibilityRole.BUTTON)
                            accessibilityInfo(clickable = true, longClickable = false)
                            // R4/R5：mount 周期无条件注册，presented 翻转周期消费。
                            val presented = page.chatBackToTopPresented
                            opacity(if (presented) 1f else 0f)
                            transform(scale = if (presented) Scale(1f, 1f) else Scale(0.5f, 0.5f))
                            animate(Animation.easeOut(0.22f), page.chatBackToTopPresented)
                        }
                        GlassBackdrop(page.theme.glass.peek, page.glassRenderer)
                        LineIconChevronUp(color = page.theme.textSecondary, size = 18f)
                        event { click { page.scrollChatToTopAnimated() } }
                    }
                }
                // ===== 长按消息操作菜单（复制 / 追问）=====
                // 全屏透明手势层（点按任意处关闭）+ 长按落点附近的玻璃胶囊菜单。
                // 入场与回到顶部按钮同款双态机：mount 周期注册动画，presented
                // 翻转周期消费（R4/R5）。菜单内容（followUp 分支）在挂载帧读取
                // 一次即可，不参与挂载后的响应式变化。
                vif({ page.messageActionMounted }) {
                    MessageActionOverlay(page.theme, page.glassRenderer, page.pagerData.pageViewWidth, page.pagerData.pageViewHeight,
                        presented = { page.messageActionPresented }, pageX = { page.messageActionX }, pageY = { page.messageActionY },
                        followUpAllowed = { page.messageActionFollowUp }, onDismiss = page::dismissMessageActionMenu,
                        onCopy = page::copyMessageToPasteboard, onFollowUp = page::quoteMessageIntoComposer)
                }
                val composerSheetMaterial = page.theme.glass.sheet
                val composerGlassRenderer = page.glassRenderer
                View {
                    attr {
                        // Floating capsule composer on a solid page-coloured base:
                        // the blank area around/below the capsule no longer shows
                        // scrolled content through.
                        absolutePosition(bottom = 0f, left = 0f, right = 0f)
                        paddingTop(0f)
                        paddingLeft(if (COMPOSER_ISOLATION_TEST) 0f else 12f)
                        paddingRight(if (COMPOSER_ISOLATION_TEST) 0f else 12f)
                        paddingBottom(
                            if (COMPOSER_ISOLATION_TEST) 0f
                            else 10f + page.pagerData.safeAreaInsets.bottom + page.keyboardHeight,
                        )
                    }
                    if (COMPOSER_ISOLATION_TEST) {
                        // 使用玻璃的静态表皮（色彩、描边、阴影），不能调用
                        // GlassBackdrop：其 Blur 原生覆盖层会盖住 EditText 光标。
                        View {
                            attr {
                                height(42f)
                                paddingLeft(12f)
                                paddingRight(12f)
                                applyGlassSurfaceSkin(composerSheetMaterial, composerGlassRenderer)
                            }
                            page.renderComposerTextArea(this, isolated = true)
                        }
                    } else {
                    // 吞掉落在输入栏自身范围内的点击（羽毛条/左右留白/底部留白），
                    // 否则它们会穿透到聊天列表，被误判成"点击非输入栏区域"而收起输入栏。
                    event { click { } }
                    // Solid base covering only the blank strip below the capsule,
                    // down to the screen bottom. The capsule itself keeps its
                    // glass backdrop.
                    // 原生 TextArea 不可靠地继承绝对定位父层的 alpha；把欢迎入场
                    // 直接挂在胶囊内容层，确保文字、边框和操作按钮同步淡入上浮。
                    View {
                        attr {
                            val emptySession = page.viewModel.messages.isEmpty()
                            val welcomePresented = page.welcomeState.entranceVisible
                            val visible = !emptySession || welcomePresented
                            opacity(if (visible) 1f else 0f)
                            transform(Translate(0f, if (visible) 0f else 18f))
                            animate(Animation.easeOut(0.36f).delay(0.10f), page.welcomeState.entranceVisible)
                        }
                        View {
                        attr {
                            absolutePosition(bottom = 0f, left = 0f, right = 0f)
                            height(10f + page.pagerData.safeAreaInsets.bottom + page.keyboardHeight)
                            backgroundColor(page.theme.page)
                            touchEnable(false)
                        }
                    }
                    // 输入框上方引导语气泡（2026-09-12 用户反馈：仿豆包升级
                    // chip 可见性 + 去掉本页内「可以这样问」label ——「为你推荐」已在
                    // WelcomeSection 顶部出现；点按 chip = injectQuestion。
                    //
                    // 过渡不变量：引导语不随展开态卸载。它用固定基线高度退场，
                    // 让底部胶囊的锚点在同一帧里连续移动，而不是先删掉内容再撑高输入栏。
                    View {
                        attr {
                            val expanded = page.composerExpanded
                            height(if (expanded) 0f else COMPOSER_GUIDE_HEIGHT)
                            opacity(if (expanded) 0f else 1f)
                            touchEnable(!expanded)
                            animate(Animation.easeOut(COMPOSER_LAYOUT_DURATION), expanded)
                        }
                        View {
                            attr {
                                marginBottom(8f)
                                paddingTop(8f)
                                paddingBottom(8f)
                                alignSelfFlexStart()
                            }
                            ComposerGuideRow(page.theme) { text -> page.injectQuestion(text) }
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
                            // 悬浮感：与顶部灵动岛胶囊同款阴影（AppChrome 灵动岛
                            // 0/8/22/0.16），让输入栏像浮在列表上方而不是贴底。
                            boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
                            // 表皮跟随主题：浅色 = 纯白，深色 = surface 深灰。attr 内
                            // 读 page.theme 注册依赖，换肤时本块随 observable 重放
                            // （animate 的键取自实参里的 entityDrag 读，不受影响）。
                            backgroundColor(
                                if (page.entityDragActive && page.entityDropTarget == EntityDropTarget.COMPOSER) page.theme.brandSoft
                                else page.theme.surface
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
                            // 上下文备注条（@提及/命令带出的深上下文）：保持在胶囊内、
                            // 与输入语义强绑定，不随「最近标的」气泡外移。
                            vif({ page.isComposerVisuallyExpanded() && page.deepContextVersion >= 0 && page.deepContextNotes.isNotEmpty() }) {
                                page.renderContextNoteBar(this)
                            }
                            vif({ page.isComposerVisuallyExpanded() && page.commandValidationMessage.isNotEmpty() }) {
                                page.renderCommandValidationBar(this)
                            }
                            vif({ page.isComposerVisuallyExpanded() && page.assistantPanel != AssistantPanel.NONE }) {
                                page.renderAssistantPanel(this)
                            }
                            // 已选附件预览：折叠/展开两态都显示，位于文字上方；
                            // 图片是缩略图 + 右上角删除叉，文档是名称胶囊。
                            vif({ page.composerAttachmentState.attachments.isNotEmpty() }) {
                                page.renderComposerAttachmentRow(this)
                            }
                            // TextArea 必须永远挂在同一个父节点下。折叠/展开只改布局和
                            // 周边操作区，不再用 vif 替换输入组件，避免聚焦期间原生
                            // EditText 被移除或在尚未 attach 时调用 autofocus。
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                }
                                // 折叠态只保留左侧 + 与右侧语音两个操作位：纯黑线条、
                                // 透明背景（2026-09-05 设计调整）。2026-09-06 放大 50%：
                                // 32→48（图标 16→24 / 18→27），与文本区同高垂直居中。
                                vif({ !page.isComposerVisuallyExpanded() }) {
                                    // 收起回放壳（用户反馈 2026-09-08：外表圆圈大小不动）：
                                    // 48dp 触控区固定不缩放，缩放/淡入只作用在加号本体
                                    // 内层（驱动仍为 !presented，收起翻转时由挂载周期
                                    // 注册的 easeOut 消费，R2/R5）。
                                    View {
                                        attr {
                                            size(48f, 48f)
                                            marginRight(7f)
                                            allCenter()
                                            borderRadius(24f)
                                        }
                                        View {
                                            attr {
                                                opacity(if (!page.composerChromePresented) 1f else 0f)
                                                transform(scale = if (!page.composerChromePresented) Scale.DEFAULT else Scale(0.6f, 0.6f))
                                                animate(Animation.easeOut(0.2f), !page.composerChromePresented)
                                            }
                                            // 图标色原为硬编码纯黑（2026-09-05 设计）：
                                            // 深色表皮下改读 textPrimary 随主题反转；
                                            // Canvas 图标的颜色在挂载帧捕获，需 vbind
                                            // 键翻转重建才能换肤。
                                            vbind({ page.themeRebuildKey() }) {
                                                LineIconPlus(color = page.theme.textPrimary, size = 24f)
                                            }
                                        }
                                        // 折叠态「+」：与展开态同款底部媒体来源弹层。
                                        event { click { page.openMediaSheet() } }
                                    }
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        minHeight(if (page.isComposerVisuallyExpanded()) 44f else 48f)
                                        paddingLeft(12f)
                                        paddingRight(12f)
                                        justifyContentCenter()
                                        borderRadius(16f)
                                        backgroundColor(Color(0xFFFFFFFF, 0f))
                                    }
                                    // 点击兜底：空文本时原生 TextArea 可能收缩到极小高，
                                    // 点击落不到 EditText 上。整条中段可点，任何测量
                                    // 异常下都能进入输入态；已展开/语音模式下守卫直接跳过
                                    // （语音模式下中段是"按住说话"，触摸由覆盖层处理）。
                                    event {
                                        click {
                                            if (!page.voiceInputMode && !page.isComposerExpanded() && page.voiceState == VoiceState.IDLE) {
                                                KLog.i(COMPOSER_LOG_TAG, "composerMiddleTap expand")
                                                page.expandComposer(requestFocus = true)
                                            }
                                        }
                                    }
                                    page.renderComposerTextArea(this)
                                    // 语音模式（豆包式）："按住说话"是普通 flex 子项，
                                    // 恰好替代文字位置（语音模式下 TextArea 被压到 0 高，
                                    // 见 renderComposerTextArea）。不用 absolutePosition——
                                    // 实测在中段（纵向 column + justifyContentCenter）里
                                    // 定位偏下被遮挡。白底；录音期间保持挂载（vif 卸载
                                    // 会丢 touchUp，录音卡到 60s 超时），"按住说话"文案仅
                                    // 在 IDLE 显示，录音 UI 由其上层的 VoiceBar 呈现。
                                    // 2026-09-06 定稿：不做任何光晕（全屏圆顶与按钮级
                                    // boxShadow 均已移除），视觉反馈只有滚动声波与变红提示。
                                    vif({ page.voiceInputMode }) {
                                        View {
                                            attr {
                                                height(48f)
                                                allCenter()
                                                borderRadius(16f)
                                                backgroundColor(page.theme.surface)
                                            }
                                            vif({ page.voiceState == VoiceState.IDLE }) {
                                                Text {
                                                    attr {
                                                        text("按住 说话")
                                                        fontSizeScaled(15f)
                                                        color(page.theme.textSecondary)
                                                    }
                                                }
                                            }
                                            event {
                                                touchDown { e -> page.handleVoiceTouchDown(e.pageY) }
                                                touchMove { e -> page.handleVoiceTouchMove(e.pageY) }
                                                touchUp { page.handleVoiceTouchUp() }
                                            }
                                        }
                                    }
                                    vif({ page.voiceState != VoiceState.IDLE }) {
                                        VoiceBar(
                                            theme = page.theme,
                                            transcribing = { page.voiceState == VoiceState.TRANSCRIBING },
                                            cancelArmed = { page.voiceCancelArmed },
                                            amps = { page.voiceAmps },
                                        )
                                    }
                                }
                                vif({ !page.isComposerVisuallyExpanded() }) {
                                    // 外层：收起回放入场（驱动 presented）；
                                    // 内层：录音缩放（驱动 voiceState）。双驱动必须拆
                                    // 父子视图，同 attr 双 animate 违反 R3。
                                    View {
                                        attr {
                                            opacity(if (!page.composerChromePresented) 1f else 0f)
                                            transform(scale = if (!page.composerChromePresented) Scale.DEFAULT else Scale(0.6f, 0.6f))
                                            animate(Animation.easeOut(0.2f), !page.composerChromePresented)
                                        }
                                        View {
                                            attr {
                                                size(48f, 48f)
                                                marginLeft(7f)
                                                allCenter()
                                                borderRadius(24f)
                                            }
                                            // 语音模式开关（豆包式）：文字态显示声波线条
                                            // （Lucide audio-lines 对齐），语音模式显示键盘
                                            // （Lucide keyboard 对齐），点击互相切换；按住
                                            // 说话手势已移至中段覆盖层。
                                            vif({ !page.voiceInputMode }) {
                                                vbind({ page.themeRebuildKey() }) {
                                                    LineIconAudioLines(color = page.theme.textPrimary, size = 27f)
                                                }
                                            }
                                            vif({ page.voiceInputMode }) {
                                                vbind({ page.themeRebuildKey() }) {
                                                    LineIconKeyboard(color = page.theme.textPrimary, size = 27f)
                                                }
                                            }
                                            event { click { page.toggleVoiceInputMode() } }
                                        }
                                    }
                                }
                            }
                            // 过渡不变量：操作行始终保留在树上，只动画它占用的布局高度；
                            // 这样 @、/、语音、附件和发送按钮不会在首帧突然把胶囊顶高。
                            View {
                                attr {
                                    val expanded = page.composerExpanded
                                    height(if (expanded) COMPOSER_ACTION_ROW_HEIGHT else 0f)
                                    marginTop(if (expanded) COMPOSER_ACTION_ROW_GAP else 0f)
                                    opacity(if (expanded) 1f else 0f)
                                    touchEnable(expanded)
                                    animate(Animation.easeOut(COMPOSER_LAYOUT_DURATION), expanded)
                                }
                                View { attr { flexDirectionRow(); alignItemsCenter() }
                                    // 展开态图标入场壳（R4：vif 挂载首帧不播动画，由
                                    // presented 两帧翻转驱动；R2：attr 内读 observable、
                                    // animate 最后注册）。按左→右 30ms 错峰浮入。
                                    View {
                                        attr {
                                            opacity(if (page.composerChromePresented) 1f else 0f)
                                            transform(Translate(0f, if (page.composerChromePresented) 0f else 8f))
                                            animate(Animation.easeOut(0.24f), page.composerChromePresented)
                                        }
                                        View {
                                            attr {
                                                size(46f, 46f)
                                                allCenter()
                                                borderRadius(23f)
                                                opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                                touchEnable(!page.isVoiceBusy())
                                            }
                                            Text {
                                                attr {
                                                    text("@")
                                                    fontSizeScaled(18f)
                                                    fontWeightSemiBold()
                                                    color(page.theme.brand)
                                }
                            }
                                            event { click { page.onTriggerButtonTapped('@') } }
                                        }
                                    }
                                    View {
                                        attr {
                                            opacity(if (page.composerChromePresented) 1f else 0f)
                                            transform(Translate(0f, if (page.composerChromePresented) 0f else 8f))
                                            animate(Animation.easeOut(0.24f).delay(0.03f), page.composerChromePresented)
                                        }
                                        View {
                                            attr {
                                                size(46f, 46f)
                                                marginLeft(8f)
                                                allCenter()
                                                borderRadius(23f)
                                                opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                                touchEnable(!page.isVoiceBusy())
                                            }
                                            Text {
                                                attr {
                                                    text("/")
                                                    fontSizeScaled(18f)
                                                    fontWeightSemiBold()
                                                    color(page.theme.brand)
                                                }
                                            }
                                            event { click { page.onTriggerButtonTapped('/') } }
                                        }
                                    }
                                    View { attr { flex(1f) } }
                                    View {
                                        attr {
                                            opacity(if (page.composerChromePresented) 1f else 0f)
                                            transform(Translate(0f, if (page.composerChromePresented) 0f else 8f))
                                            animate(Animation.easeOut(0.24f).delay(0.06f), page.composerChromePresented)
                                        }
                                        View {
                                            attr {
                                                size(46f, 46f)
                                                marginRight(6f)
                                                allCenter()
                                                borderRadius(23f)
                                            }
                                            // 与折叠态语音开关同款声波图标（Lucide
                                            // audio-lines），颜色随主题表皮反转。
                                            vbind({ page.themeRebuildKey() }) {
                                                LineIconAudioLines(color = page.theme.textPrimary, size = 26f)
                                            }
                                            // 展开态点击语音（豆包式）：直接折叠并进入语音
                                            // 模式，一步呈现折叠态"按住说话"样式；不再在
                                            // 展开态按住录音。
                                            event { click { page.enterVoiceModeFromExpanded() } }
                                        }
                                    }
                                    // 展开态右侧媒体入口：拍照图标改为 + 号（2026-09-05）。
                                    View {
                                        attr {
                                            opacity(if (page.composerChromePresented) 1f else 0f)
                                            transform(Translate(0f, if (page.composerChromePresented) 0f else 8f))
                                            animate(Animation.easeOut(0.24f).delay(0.09f), page.composerChromePresented)
                                        }
                                        View {
                                            attr {
                                                size(46f, 46f)
                                                marginRight(4f)
                                                allCenter()
                                                borderRadius(23f)
                                                opacity(if (page.isVoiceBusy()) 0.4f else 1f)
                                                touchEnable(!page.isVoiceBusy())
                                            }
                                            LineIconPlus(
                                                color = page.theme.textSecondary,
                                                size = 22f,
                                            )
                                            event { click { page.openMediaSheet() } }
                                        }
                                    }
                                    View {
                                        attr {
                                            opacity(if (page.composerChromePresented) 1f else 0f)
                                            transform(Translate(0f, if (page.composerChromePresented) 0f else 8f))
                                            animate(Animation.easeOut(0.24f).delay(0.12f), page.composerChromePresented)
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
                                                Text {
                                                    attr {
                                                        text("↑")
                                                        fontSizeScaled(18f)
                                                        fontWeightSemiBold()
                                                        color(if (page.isCommandSendBlocked()) page.theme.textTertiary else page.theme.onBrand)
                                                    }
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
                                }
                            }
                        page.renderComposerGradientRim(this)
                    }
                    }
                    }
                }
            }
            // 已发送图片预览：置于主内容之后，因此会压住列表和输入栏；点击遮罩或右上角
            // 关闭均只清理预览状态，不会影响消息和附件数据。
            vif({ page.imagePreviewPath.isNotEmpty() }) {
                ImagePreviewOverlay({ page.imagePreviewPath }, page.pagerData.pageViewWidth, page.pagerData.pageViewHeight,
                    page.pagerData.statusBarHeight, page::closeImagePreview)
            }
            vif({ page.compareCard != null }) {
                // 对比结果弹窗 + 蒙层（用户决策 2026-09-05，二轮修正）：整个块
                // 声明在输入栏之后——蒙层必须绘制在输入栏（含渐变高光边）之上，
                // 否则输入栏高光会从压暗的背景里"漏"出来。聊天区、灵动岛、
                // 输入栏一起被压暗并吞掉点击；只有弹窗面板本身（块内后绘制）
                // 和更上层的拖拽 overlay/CardSheet/抽屉不受影响。
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        backgroundColor(Color(0x59000000))
                        touchEnable(true)
                        animate(Animation.easeOut(0.2f), page.compareCard != null)
                    }
                    // 蒙层即收回（2026-09-09）：点面板外任意区域 = 退出对比，
                    // 与面板内「退出」同走 clearCompare（含灵动岛几何复位）。
                    event { click { page.clearCompare() } }
                }
                // 底部锚定容器（2026-09-09）：此前面板是根容器的流式子节点，
                // 而根容器的兄弟节点全部 absolutePosition（主内容层/顶栏/输入栏），
                // 流式位置落在 y=0 → 面板被顶到屏幕最上面。改为全屏容器 +
                // justifyContentFlexEnd 锚底，AI 解读流式输出逐字到达、面板高度
                // 连续小步生长时，顶缘随之向上拉长，底缘不动。
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        justifyContentFlexEnd()
                        paddingBottom(page.pagerData.safeAreaInsets.bottom)
                    }
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
            }
            // 术语对比弹窗：双槽位填满即弹出（与股票 compareCard 同款触发），
            // 蒙层 + 面板绘制在输入栏之上，压暗聊天区/灵动岛/输入栏。
            vif({
                page.islandTermCompareVisible &&
                    page.islandTermCompareLeftKey.isNotEmpty() &&
                    page.islandTermCompareRightKey.isNotEmpty()
            }) {
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        backgroundColor(Color(0x59000000))
                        touchEnable(true)
                        animate(Animation.easeOut(0.2f), page.islandTermCompareVisible)
                    }
                    // 蒙层即收回（2026-09-09）：同股票对比蒙层，点击 = 退出术语对比。
                    event { click { page.clearIslandCompare() } }
                }
                // 底部锚定容器：同股票对比弹窗（2026-09-09），锚底 + 流式向上生长。
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        justifyContentFlexEnd()
                        paddingBottom(page.pagerData.safeAreaInsets.bottom)
                    }
                    Glossary.byKey(page.islandTermCompareLeftKey)?.let { leftEntry ->
                        Glossary.byKey(page.islandTermCompareRightKey)?.let { rightEntry ->
                            TermComparePanel(
                                left = leftEntry,
                                right = rightEntry,
                                theme = page.theme,
                                insightLoading = { page.compareInsightState == CompareInsightState.LOADING },
                                insightText = { page.compareInsightText },
                                insightError = { page.compareInsightError },
                                onRetryInsight = { page.retryCompareInsight() },
                                onClose = { page.clearIslandCompare() },
                            )
                        }
                    }
                }
            }
            vif({ page.entityDragActive }) {
                page.renderEntityDragOverlay(this)
            }
            vif({ page.cardSheetState.mounted }) {
                page.cardSheetState.model?.let { model ->
                    CardSheetHost(
                        model = model,
                        level = page.cardSheetState.level,
                        theme = page.theme,
                        renderer = page.glassRenderer,
                        presented = page.cardSheetState.presented,
                        interactive = page.cardSheetState.interactive,
                        viewportHeight = page.pagerData.pageViewHeight,
                        bottomInset = page.pagerData.safeAreaInsets.bottom,
                        onDismiss = { page.dismissCardSheet() },
                        onLower = { page.lowerCardSheet() },
                        onRaise = { page.raiseCardSheet() },
                        onPan = { state, y -> page.handleSheetPan(state, y) },
                        onOpenStock = { if (page.cardSheetState.interactive) page.openStockDetail(it) },
                        onTerm = {
                            // 卡片底部「问术语」同样记一次「遇到」。
                            Glossary.keyForToken(it)?.let(page.glossaryStore::encounter)
                            page.sendFromChat("$it 是什么意思")
                        },
                        primaryActionLabel = (model as? StockQuoteCardModel)?.quote?.symbol?.let { symbol ->
                            if (page.watchlistStore.contains(symbol)) "已自选" else "加自选"
                        },
                        onPrimaryAction = { symbol -> page.addWatchlistFromEntity(symbol) },
                    )
                }
            }
            // 输入栏「+」媒体来源弹层：全屏蒙层 + 底部卡片（图库/拍照/文档）。
            // 放在 CardSheetHost 之后，压住卡片与输入栏；抽屉在其上不受影响。
            vif({ page.composerAttachmentState.mediaSheetMounted }) {
                MediaActionSheetHost(
                    theme = page.theme,
                    // presented 必须传取值闭包：vif 体只执行一次，直接读
                    // observable 只会拿到挂载帧快照（false），两帧入场的
                    // presented 翻转永远到不了 attr（R1）。同 ChatDrawer。
                    presented = { page.composerAttachmentState.mediaSheetPresented },
                    bottomInset = page.pagerData.safeAreaInsets.bottom,
                    onDismiss = { page.dismissMediaSheet() },
                    onAction = { page.handleMediaAction(it) },
                )
            }
            // 左缘手势条：抽屉关闭时贴左缘右滑可跟手展开。放在 CardSheetHost 之后、
            // ChatDrawer 之前——卡片与抽屉呈现时自然被上层视图盖住；灵动岛展开卡
            // （左缘 14dp 起）与实体拖拽期间让位，避免抢走横向手势。
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, bottom = 0f)
                    width(20f)
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                    touchEnable(
                        !page.drawerOpen && !page.drawerMounted &&
                            !page.cardSheetState.mounted && !page.islandExpanded && !page.entityDragActive
                    )
                }
                event {
                    pan { params -> page.handleDrawerPan(params.state, params.pageX) }
                }
            }
            vif({ page.drawerMounted }) {
                ChatDrawer(
                    statusBarHeight = page.pagerData.statusBarHeight,
                    bottomInset = page.pagerData.safeAreaInsets.bottom,
                    theme = page.theme,
                    renderer = page.glassRenderer,
                    visualLabel = page.glassRenderer.statusLabel(),
                    sessions = page.viewModel.sessionSummaries.toList(),
                    historyQuery = { page.historySearchQuery },
                    onHistoryQuery = { page.historySearchQuery = it },
                    presented = { page.drawerPresented },
                    interactive = { page.drawerOpen },
                    gestureMotion = { page.drawerGesture },
                    onPan = { state, x -> page.handleDrawerPan(state, x) },
                    onClose = { page.updateDrawerOpen(false) },
                    onCycleVisualMode = { page.cycleGlassMode() },
                    onNewChat = { page.startNewChat() },
                    onOpenSession = { page.openHistorySession(it) },
                    onOpenGallery = { page.openPage(Routes.CARD_GALLERY) },
                    onOpenGlossary = { page.openPage(Routes.GLOSSARY) },
                    onOpenWatchlist = { page.openPage(Routes.WATCHLIST) },
                    onOpenRiskMap = { page.openPage(Routes.RISK) },
                    onOpenMarket = { page.openPage(Routes.MARKET) },
                    onOpenSearch = { page.openPage(Routes.SEARCH) },
                    onOpenAlerts = { page.openPage(Routes.ALERTS) },
                    onToggleIsland = { page.toggleIsland() },
                    onSettings = { page.updateDrawerOpen(false); page.openPage(Routes.SETTINGS) },
                )
            }
            // 行情接口不可用时的显式降级确认。页面级定时器受可见性守卫，避免
            // repository 全局 Handler 在 native bridge 已解绑时更新响应式视图。
            vif({ page.marketFallbackPromptSymbol.isNotEmpty() }) {
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        zIndex(80, useOutline = false)
                        backgroundColor(Color(0x88000000))
                        allCenter()
                        padding(24f)
                    }
                    event { click { page.dismissMarketFallbackPrompt() } }
                    View {
                        attr {
                            width((page.pagerData.pageViewWidth - 48f).coerceAtMost(360f))
                            padding(20f)
                            borderRadius(20f)
                            backgroundColor(page.theme.surface)
                        }
                        event { click { } }
                        Text {
                            attr {
                                text("行情接口开小差了")
                                fontSizeScaled(18f)
                                fontWeightBold()
                                color(page.theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("暂时没有收到真实行情数据。要切换到本地 Mock 数据继续查看吗？")
                                marginTop(8f)
                                fontSizeScaled(13f)
                                lineHeightScaled(20f)
                                color(page.theme.textSecondary)
                            }
                        }
                        View {
                            attr { flexDirectionRow(); marginTop(20f); justifyContentFlexEnd() }
                            View {
                                attr { padding(10f) }
                                Text { attr { text("暂不切换"); fontSizeScaled(14f); fontWeightMedium(); color(page.theme.textSecondary) } }
                                event { click { page.dismissMarketFallbackPrompt() } }
                            }
                            View {
                                attr { padding(10f); marginLeft(8f) }
                                Text { attr { text("切换到 Mock 数据"); fontSizeScaled(14f); fontWeightBold(); color(page.theme.brand) } }
                                event { click { page.switchPromptedQuoteToMock() } }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateDrawerOpen(open: Boolean) = drawerCoordinator.setOpen(open)

    private fun handleDrawerPan(state: String, x: Float) = drawerCoordinator.onPan(state, x)

    /**
     * 注册原生「大且快右向横滑」侦察回调（Android 宿主专有通道，见
     * DrawerFlingDetector）：created 里注册一次（keepCallback 多次触发），
     * 页面不可见时靠 pageVisible 守卫静默忽略。
     */
    private fun registerDrawerFlingHostIfNeeded() {
        if (drawerFlingHostRegistered) return
        drawerFlingHostRegistered = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
            .registerDrawerFlingHost { _ -> handleNativeDrawerFling() }
    }

    /**
     * 注册原生媒体选择结果回调（Android 宿主专有通道）：图库/拍照/文档选择
     * 完成后，宿主把内容复制到缓存文件，再经 keepCallback 回传路径与名称。
     * created 里注册一次；回调跨进程往返后可能不在渲染线程，页侧只做
     * observable 赋值（赋值本身线程安全，重渲染由渲染管线调度）。
     */
    private fun registerComposerMediaResultHostIfNeeded() {
        if (composerMediaHostRegistered) return
        composerMediaHostRegistered = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
            .registerComposerMediaResult { data -> handleComposerMediaResult(data) }
    }

    private fun handleComposerMediaResult(data: JSONObject?) {
        if (isWillDestroy()) return
        if (data == null || data.optString("type") != "ok") return
        // 多选图库走 items 批量回传；兼容旧版单条 {kind, path, name} 字段。
        val items = mutableListOf<Triple<String, String, Boolean>>() // (path, name, isImage)
        val batch = data.optJSONArray("items")
        if (batch != null) {
            for (i in 0 until batch.length()) {
                val item = batch.optJSONObject(i) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) {
                    items.add(Triple(path, item.optString("name"), item.optString("kind") == "image"))
                }
            }
        } else {
            val path = data.optString("path")
            if (path.isNotBlank()) {
                items.add(Triple(path, data.optString("name"), data.optString("kind") == "image"))
            }
        }
        if (items.isEmpty()) return
        var addedCount = 0
        for ((path, name, isImage) in items) {
            val attachment = composerAttachmentCoordinator.add(
                path = path,
                name = name,
                isImage = isImage,
            )
            if (attachment == null) {
                continue
            }
            addedCount++
            KLog.i(COMPOSER_LOG_TAG, "mediaResult kind=${attachment.kind} name=${attachment.displayName}")
        }
        if (addedCount < items.size) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("最多添加${MAX_COMPOSER_ATTACHMENTS}个附件")
        }
        if (addedCount > 0) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        }
    }

    /** 输入框附件预览的删除叉（显式点击，无需撤销条）。 */
    private fun removeComposerAttachment(id: String) {
        composerAttachmentCoordinator.remove(id)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    private fun clearComposerAttachments() {
        composerAttachmentCoordinator.clear()
    }

    /** 只带附件无文字时的兜底引导语，保证发送管线有正文。 */
    private fun defaultAttachmentPrompt(): String {
        return composerAttachmentCoordinator.defaultPrompt()
    }

    /**
     * 原生 fling 侦察命中：整页任意位置的「大且快」右向横滑。原生层只观测
     * 不消费事件，做不了跟手预览，因此直接程序化展开（走 updateDrawerOpen
     * 的双态入场 + blur 收键盘）。抽屉/卡片弹层/实体拖拽/灵动岛展开任一
     * 活跃时忽略，避免手势叠加。
     */
    private fun handleNativeDrawerFling() {
        if (!pageVisible) return
        if (drawerOpen || drawerMounted || cardSheetState.mounted || entityDragActive || islandExpanded) return
        updateDrawerOpen(true)
    }

    private fun startNewChat() {
        updateDrawerOpen(false)
        welcomeCoordinator.onNewEmptySession()
        chatScrollCoordinator.onNewChat()
        chatContentHeight = 0f
        resetSessionUiState()
        setComposerText("")
        viewModel.startNewChat()
        resetChatScrollToTop()
    }

    /** Picks a different short-to-long set before emitting the vfor rebuild key. */
    private fun refreshWelcomeStarters() {
        var next = randomWelcomeStarters()
        repeat(4) {
            if (next.map { it.question.length } != previousWelcomeStarterLengths) return@repeat
            next = randomWelcomeStarters()
        }
        welcomeStarterSelection = next
        previousWelcomeStarterLengths = next.map { it.question.length }
        welcomeStarterRenderKey.clear()
        welcomeStarterRenderKey.add(0)
    }

    // 2026-09-03 产品决策：全量走 FULL 档，BRIEF 档（老用户简版欢迎语）暂不启用。
    // 恢复 BRIEF 只需把该开关改回 true，原有降级逻辑全部保留。
    private val welcomeBriefEnabled = false

    private fun welcomeMode(): WelcomeMode =
        if (welcomeBriefEnabled && viewModel.hasSessionHistory) WelcomeMode.BRIEF else WelcomeMode.FULL

    private fun chooseWelcomeStarter(starter: WelcomeStarter) {
        welcomeCoordinator.onStarterChosen(starter.kind.name)
        injectQuestion(starter.question)
    }

    private fun openHistorySession(sessionId: String) {
        viewModel.openSession(sessionId)
        welcomeCoordinator.onSessionOpened(
            sessionEmpty = viewModel.messages.isEmpty(),
            fullMode = welcomeMode() == WelcomeMode.FULL,
        )
        resetSessionUiState()
        reloadQuotesForCurrentSession()
        setComposerText("")
        keepChatAtBottomTemporarily()
    }

    private fun submitInput() {
        welcomeCoordinator.onConversationStarted()
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
        sendWithPreparedMedia(payload)
    }

    /** Prepare local files on the native side before handing this turn to the provider. */
    private fun sendWithPreparedMedia(payload: SendPayload) {
        if (payload.attachments.isEmpty()) {
            finishSubmittedPayload(payload)
            return
        }
        val items = com.tencent.kuikly.core.nvi.serialization.json.JSONArray().apply {
            payload.attachments.forEach { attachment ->
                put(com.tencent.kuikly.core.nvi.serialization.json.JSONObject().apply {
                    put("path", attachment.path)
                    put("name", attachment.name)
                    put("kind", attachment.kind)
                })
            }
        }
        // iOS/H5 hosts that have not implemented this optional bridge must not leave
        // the composer stuck. Android normally answers well before this fallback.
        var delivered = false
        fun deliver(media: List<com.kuikly.stockchat.chat.AiMediaPart>) {
            if (delivered || isWillDestroy()) return
            delivered = true
            finishSubmittedPayload(payload.copy(media = media))
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).prepareAiMedia(items) { result ->
            val prepared = result?.optJSONArray("items")
            val media = buildList {
                if (prepared != null) for (i in 0 until prepared.length()) {
                    val item = prepared.optJSONObject(i) ?: continue
                    add(com.kuikly.stockchat.chat.AiMediaPart(
                        name = item.optString("name").ifBlank { "附件" },
                        imageDataUrl = item.optString("imageDataUrl").takeIf { it.isNotBlank() },
                        documentText = item.optString("documentText").takeIf { it.isNotBlank() },
                    ))
                }
            }
            deliver(media)
        }
        setTimeout(5_000) { deliver(emptyList()) }
    }

    private fun finishSubmittedPayload(payload: SendPayload) {
        viewModel.send(payload)
        // 发送后清理输入期固化状态：mentions / 命令注册 / 附件；最近提及列表保留供下次推荐。
        mentionEntities.clear()
        clearComposerAttachments()
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
                        welcomeCoordinator.onNewEmptySession()
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

    /**
     * 「+」（折叠/展开两态共用）唤起底部媒体来源卡片。先收输入态（键盘落下、
     * 输入栏回默认态，草稿保留），再挂载弹层并两帧翻 presented 播入场（R4）。
     */
    private fun openMediaSheet() {
        KLog.i(COMPOSER_LOG_TAG, "openMediaSheet")
        // The expanded composer and the media sheet both change the bottom
        // layout.  Mounting them in the same commit makes keyboard avoidance,
        // composer re-measurement and the sheet entrance compete for one frame
        // (most visible as a hitch on the first tap).  Let the composer settle
        // for one frame after blur/collapse, then mount the sheet.
        if (isComposerVisuallyExpanded()) {
            blurComposer()
            collapseComposer()
            setTimeout(32) {
                if (!isWillDestroy()) mediaSheetCoordinator.open()
            }
            return
        }
        mediaSheetCoordinator.open()
    }

    /** 蒙层点击 / 取消：presented 先归位，收尾动画播完再卸载。 */
    private fun dismissMediaSheet() {
        mediaSheetCoordinator.dismiss()
    }

    private fun handleMediaAction(action: ComposerMediaAction) {
        KLog.i(COMPOSER_LOG_TAG, "mediaAction ${action.source}")
        // 选完入口即收弹层，再交原生拉起图库/相机/文档选择器。
        dismissMediaSheet()
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridge.hapticImpact()
        bridge.openComposerMediaSource(action.source) { data ->
            if (data != null && data.optString("code") == "-1") {
                KLog.i(COMPOSER_LOG_TAG, "mediaAction failed ${data.optString("message")}")
            }
        }
        trackComposerEvent("composer_media_open", "source" to action.source)
    }

    private fun resetSessionUiState() {
        entityCoordinator.resetForNewSession()
        islandCoordinator.resetForNewSession()
        cardSheetCoordinator.reset()
        cardInteractionCoordinator.resetForNewSession()
        compareCoordinator.resetForNewSession()
        requestedSymbols.clear()
        quoteStates.clear()
        deepContextNotes.clear()
        routeFocusMention = null
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

    /**
     * 输入栏图标入场的两帧调度（R4/R5）：挂载周期先把 presented 落在与目标相反
     * 的值（新挂载的图标渲染为隐藏态并注册 easeOut），32ms 后翻转到目标态，
     * 翻转周期消费上一轮注册的动画。各图标的错峰由自带 delay 实现。
     * 版本号守卫保证快速开合时旧回调静默退出；600ms 兜底确保调度链路任一环
     * 丢失时图标不会停留在 opacity 0（scheduleWelcomeEntranceSafety 同款思路）。
     */
    private fun scheduleComposerChromePresentation(target: Boolean) {
        val version = ++composerChromeVersion
        composerChromeTimer?.cancel()
        composerChromeTimer = null
        if (composerReducedMotion) {
            composerChromePresented = target
            return
        }
        val timer = Timer()
        composerChromeTimer = timer
        timer.schedule(32, 32) {
            timer.cancel()
            if (composerChromeTimer === timer) composerChromeTimer = null
            if (version == composerChromeVersion && !isWillDestroy()) {
                composerChromePresented = target
            }
        }
        val safetyVersion = version
        val safety = Timer()
        safety.schedule(600, 600) {
            safety.cancel()
            if (safetyVersion == composerChromeVersion && !isWillDestroy() && composerChromePresented != target) {
                KLog.i(COMPOSER_LOG_TAG, "chromePresentationSafety target=$target")
                composerChromePresented = target
            }
        }
    }

    private fun handleComposerFocusEffect(effect: ComposerFocusEffect) {
        when (effect) {
            ComposerFocusEffect.FocusInput -> inputRef?.view?.focus()
            ComposerFocusEffect.BlurInput -> inputRef?.view?.blur()
            ComposerFocusEffect.LeaveVoiceInputMode -> voiceInputCoordinator.leaveInputModeForText()
            ComposerFocusEffect.CancelVoiceSession -> cancelVoiceSession()
            ComposerFocusEffect.ClearActiveCommand -> clearActiveCommand()
            ComposerFocusEffect.CloseAssistantPanel -> closeAssistantPanel()
            is ComposerFocusEffect.ScheduleChromePresentation ->
                scheduleComposerChromePresentation(effect.expanded)
        }
    }

    /** 默认态 → 输入态。requestFocus 为真时同时把键盘拉起来。 */
    private fun expandComposer(requestFocus: Boolean = false) {
        composerFocusCoordinator.expand(requestFocus, voiceState != VoiceState.IDLE)
    }

    private fun blurComposer() = composerFocusCoordinator.blur()

    /** 输入态 → 默认态。草稿会留在输入框里，只是收起辅助区。 */
    private fun collapseComposer() {
        KLog.i(COMPOSER_LOG_TAG, "collapseComposer draftLen=${viewModel.inputText.length}")
        composerFocusCoordinator.collapse()
    }

    /** Reads observable state inside each vif predicate so Kuikly can re-render it. */
    private fun isComposerExpanded(): Boolean = composerFocusCoordinator.isExpanded(voiceState != VoiceState.IDLE)

    /**
     * 输入栏的"视觉展开"态：与 [isComposerExpanded] 的唯一区别是排除录音态。
     * 语音模式下按住说话发生在折叠栏上（豆包式），录音期间折叠图标必须保持
     * 挂载、展开态图标行不得插入，否则中段布局会在按住瞬间跳高。
     */
    private fun isComposerVisuallyExpanded(): Boolean = composerFocusCoordinator.isVisuallyExpanded()

    /**
     * 点击非输入栏区域收起输入栏。草稿保留，但不应阻止回到折叠态。
     *
     * 之前这里在键盘可见时只做 blur，把 composer 留在展开态；有文字草稿时
     * 用户通常会先收键盘再点一次，但第二次点击可能被布局/原生输入框的焦点
     * 回调吃掉，结果看起来就像“有字不能收回”。一次点击同时完成 blur 和收起，
     * 让收回动作不依赖输入内容或键盘回调的时序。
     */
    private fun handleOutsideTap() {
        KLog.i(COMPOSER_LOG_TAG, "outsideTap keyboardVisible=$keyboardVisible keyboardHeight=$keyboardHeight")
        if (voiceState != VoiceState.IDLE) return
        blurComposer()
        if (composerExpanded || keyboardVisible || keyboardHeight > 0f) {
            collapseComposer()
        }
    }

    private fun isVoiceBusy(): Boolean = voiceState != VoiceState.IDLE

    /**
     * 折叠栏右侧语音/键盘切换（豆包式）。开启后中间文本区变成"按住说话"；
     * 只在折叠态可达（图标仅折叠态挂载），录音中不可切。
     */
    private fun toggleVoiceInputMode() {
        voiceInputCoordinator.toggleInputMode()
        KLog.i(COMPOSER_LOG_TAG, "toggleVoiceInputMode -> $voiceInputMode")
    }

    /**
     * 展开态点击语音图标：直接折叠并进入语音模式，一步呈现"按住说话"样式。
     * collapseComposer 内部会补 presented false 翻转（R4），折叠图标正常入场。
     */
    private fun enterVoiceModeFromExpanded() {
        KLog.i(COMPOSER_LOG_TAG, "enterVoiceModeFromExpanded")
        voiceInputCoordinator.enterInputModeFromExpanded()
    }

    private fun handleVoiceTouchDown(pageY: Float) {
        voiceInputCoordinator.onTouchDown(pageY, composerExpanded = isComposerExpanded())
    }

    private fun handleVoiceTouchMove(pageY: Float) = voiceInputCoordinator.onTouchMove(pageY)

    private fun handleVoiceTouchUp() = voiceInputCoordinator.onTouchUp()

    private fun startVoiceSession() = voiceInputCoordinator.startSession()

    private fun finishVoiceSession() = voiceInputCoordinator.finishSession()

    private fun cancelVoiceSession() = voiceInputCoordinator.cancelSession()

    private fun handleVoiceInputEffect(effect: VoiceInputEffect) {
        when (effect) {
            VoiceInputEffect.BlurComposer -> blurComposer()
            VoiceInputEffect.CloseAssistantPanel -> closeAssistantPanel()
            VoiceInputEffect.ClearCommandValidation -> commandValidationMessage = ""
            VoiceInputEffect.CollapseTextComposer -> collapseComposer()
            is VoiceInputEffect.SetTextComposerExpanded -> composerFocusCoordinator.setExpandedFromVoice(
                expanded = effect.expanded,
                scheduleChrome = effect.scheduleChrome,
            )
            is VoiceInputEffect.Toast -> acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(effect.message)
            is VoiceInputEffect.SendTranscript -> viewModel.send(effect.transcript)
            VoiceInputEffect.KeepChatAtBottom -> keepChatAtBottomTemporarily()
        }
    }

    /** 渐变描边流动：20fps 推进相位，一圈约 5s；幂等，页面出现时启动。 */
    private fun startComposerRimFlow() {
        if (composerRimFlowTimer != null) return
        val timer = Timer()
        composerRimFlowTimer = timer
        timer.schedule(50, 50) {
            if (composerRimFlowTimer !== timer) return@schedule
            composerRimPhase = (composerRimPhase + 0.063f) % (PI * 2f).toFloat()
        }
    }

    private fun stopComposerRimFlow() {
        composerRimFlowTimer?.cancel()
        composerRimFlowTimer = null
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
                // 流动渐变：渐变轴绕中心旋转（相位由页侧定时器推进，draw 闭包内
                // 读取 observable，ReactiveObserver 驱动重绘）。轴长取对角线，
                // 任意角度下渐变都完整覆盖画布，不会露边角断色。
                context.batchDraw = true
                val phase = composerRimPhase
                val cx = width / 2f
                val cy = height / 2f
                val half = sqrt(width * width + height * height) / 2f
                val dx = cos(phase) * half
                val dy = sin(phase) * half
                val gradient = context.createLinearGradient(cx - dx, cy - dy, cx + dx, cy + dy)
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

    /**
     * 输入栏附件预览行：位于文字上方，折叠/展开两态都显示（挂在胶囊内、
     * 输入行之前）。图片显示缩略图 + 右上角删除叉；文档显示图标 + 名称胶囊。
     */
    private fun renderComposerAttachmentRow(container: ViewContainer<*, *>) {
        container.View {
            attr { flexDirectionRow(); marginTop(10f); paddingLeft(2f) }
            // `attachments` is a value observable rather than ObservableList: rebuild this
            // compact preview row inside vbind so every add/remove is reactive (R1).
            vbind({ this@ChatPage.composerAttachmentState.attachments }) {
                this@ChatPage.composerAttachmentState.attachments.forEach { attachment ->
                if (attachment.isImage) {
                    View {
                        attr {
                            size(56f, 56f)
                            marginRight(8f)
                            borderRadius(12f)
                        }
                        Image {
                            attr {
                                src("file://" + attachment.path)
                                size(56f, 56f)
                                borderRadius(12f)
                                backgroundColor(Color(0x11000000))
                            }
                        }
                        // 删除叉：显式点击目标，不需要撤销条。
                        View {
                            attr {
                                absolutePosition(top = 3f, right = 3f)
                                size(16f, 16f)
                                allCenter()
                                borderRadius(8f)
                                backgroundColor(Color(0x8C000000))
                            }
                            LineIconClose(color = Color(0xFFFFFFFF), size = 9f)
                            event { click { this@ChatPage.removeComposerAttachment(attachment.id) } }
                        }
                    }
                } else {
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            height(56f)
                            marginRight(8f)
                            paddingLeft(12f)
                            paddingRight(10f)
                            backgroundColor(this@ChatPage.theme.surfaceMuted)
                            borderRadius(12f)
                        }
                        LineIconFileText(this@ChatPage.theme.textSecondary, 18f)
                        Text {
                            attr {
                                text(attachment.displayName)
                                fontSizeScaled(12f)
                                color(this@ChatPage.theme.textSecondary)
                                marginLeft(6f)
                                marginRight(4f)
                            }
                        }
                        View {
                            attr {
                                size(18f, 18f)
                                allCenter()
                                borderRadius(9f)
                                backgroundColor(Color(0x1A000000))
                            }
                            LineIconClose(color = this@ChatPage.theme.textSecondary, size = 9f)
                            event { click { this@ChatPage.removeComposerAttachment(attachment.id) } }
                        }
                    }
                }
                }
            }
        }
    }

    private fun renderComposerTextArea(container: ViewContainer<*, *>, isolated: Boolean = false) {
        if (isolated) {
            // 与 GlobalSearchPage 保持同一类原生 TextArea 配置：不持有 ref、
            // 不命令式回写编辑态、不监听焦点/选区/键盘高度，也不修改父级布局。
            // 垂直居中同 GlobalSearchPage：盒子收缩到单行内容高度，minHeight 兜空态。
            container.TextArea {
                attr {
                    flex(1f)
                    fontSizeScaled(17f)
                    lineHeightScaled(25f)
                    fontWeightMedium()
                    minHeight(25f)
                    maxHeight(40f)
                    color(this@ChatPage.theme.textPrimary)
                    backgroundColor(Color(0xFFFFFFFF, 0f))
                    placeholder("问一只股票或一个术语")
                    placeholderColor(this@ChatPage.theme.textTertiary)
                    tintColor(this@ChatPage.theme.brand)
                    selectionColor(this@ChatPage.theme.brand)
                }
                event {
                    textDidChange(isSyncEdit = true) {
                        this@ChatPage.handleComposerTextChanged(it.text)
                    }
                    inputReturn { this@ChatPage.submitInput() }
                }
            }
            return
        }
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
                fontSizeScaled(17f)
                lineHeightScaled(25f)
                fontWeightMedium()
                backgroundColor(Color(0xFFFFFFFF, 0f))
                placeholder("问一只股票或一个术语")
                returnKeyTypeSend()
                enablePinyinCallback(true)
            }
            attr {
                // 折叠态和联想面板展开时收紧到单行。这里只更新尺寸 prop，
                // 持久 TextArea 本身不移除、不重建。
                // 垂直居中关键：原生 TextArea 是 TOP|START 顶对齐（KRTextAreaView
                // setGravity(51)，DSL 无垂直对齐属性）。折叠态不能让 minHeight 把
                // 盒子撑到 40——那样文本贴在 40 高盒子的顶部，视觉上比旁边按钮偏上。
                // 折叠态让盒子收缩到单行内容高度，顶对齐即等于居中，再由外层
                // justifyContentCenter 把盒子放进 48 高的行里。
                // 但折叠态必须保留单行高度下限（25f = lineHeight）：空文本时原生
                // 内容高度为 0，盒子归零会导致占位符不可见、点击完全落不到
                // EditText 上（冒泡到输入栏根节点被吞）。有文字时 intrinsic 高度
                // 优先，minHeight 只兜空态，单行视觉不变。
                minHeight(
                    when {
                        // 语音模式：压到 0 高给"按住说话"让位（opacity 0 已隐藏，
                        // 原生 EditText z 序在 Kuikly 视图之上，白底覆盖层盖不住，
                        // 只有整个盒子退出布局流才能让文字位完全让出来）。
                        this@ChatPage.voiceInputMode -> 0f
                        !this@ChatPage.isComposerExpanded() -> 25f
                        this@ChatPage.assistantPanel != AssistantPanel.NONE -> 0f
                        else -> 40f
                    }
                )
                // 展开常规态文字整体下移 ~10dp（用户反馈 2026-09-10：文字偏上，
                // 下移后与下方 @/语音/＋/发送 按钮行视觉居中）。与 minHeight 同款
                // 门控，其余态恒 0（条件性 attr 必须无条件全量赋值，勿用 if）。
                marginTop(
                    when {
                        this@ChatPage.voiceInputMode -> 0f
                        !this@ChatPage.isComposerExpanded() -> 0f
                        this@ChatPage.assistantPanel != AssistantPanel.NONE -> 0f
                        else -> 10f
                    }
                )
                maxHeight(
                    when {
                        this@ChatPage.voiceInputMode -> 0f
                        !this@ChatPage.isComposerExpanded() || this@ChatPage.assistantPanel != AssistantPanel.NONE -> 44f
                        else -> 80f
                    }
                )
                color(this@ChatPage.theme.textPrimary)
                // 语音模式整个周期（含待机"按住说话"）隐藏：安卓原生 EditText
                // 的 z 序在 Kuikly 视图之上，白底覆盖层盖不住它，只有 opacity 0
                // 才干净；touch 一并关掉，避免按住说话时被 EditText 截走手势。
                opacity(if (this@ChatPage.voiceState == VoiceState.IDLE && !this@ChatPage.voiceInputMode) 1f else 0f)
                touchEnable(this@ChatPage.voiceState == VoiceState.IDLE && !this@ChatPage.voiceInputMode)
                placeholderColor(this@ChatPage.theme.textTertiary)
                tintColor(this@ChatPage.theme.brand)
                selectionColor(this@ChatPage.theme.brand)
            }
            event {
                // 只恢复输入态布局；不再触发 focus()/blur() 或焦点恢复定时器。
                // 原生 TextArea 因而持续拥有系统管理的正常输入光标。
                inputFocus {
                    this@ChatPage.composerFocusCoordinator.onInputFocus(
                        voiceBusy = this@ChatPage.voiceState != VoiceState.IDLE,
                    )
                }
                textDidChange(isSyncEdit = true) {
                    KLog.d(COMPOSER_LOG_TAG, "EV textDidChange len=${it.text.length}")
                    this@ChatPage.handleComposerTextChanged(it.text)
                }
                keyboardHeightChange {
                    KLog.i(COMPOSER_LOG_TAG, "EV keyboardHeightChange h=${it.height} dur=${it.duration}")
                    this@ChatPage.composerFocusCoordinator.onKeyboardHeightChanged(it.height, it.duration)
                    this@ChatPage.scheduleScrollChatToBottom(animated = false)
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
                    fontSizeScaled(11f)
                    fontWeightMedium()
                    color(page.theme.brand)
                    flex(1f)
                }
            }
            Text {
                attr { text("取消"); fontSizeScaled(11f); color(page.theme.textSecondary) }
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
                    fontSizeScaled(11f)
                    color(page.theme.rise)
                }
            }
        }
    }

    /**
     * 聊天内非输入栏发送入口（追问 chips / 术语高亮 / 建议问题）统一走这里：
     * 发送即视为用户要回到新消息处，与 submitInput 同待遇——即使此刻正
     * 上滑翻阅历史，也要自动滚到新消息再跟随流式输出。
     */
    private fun sendFromChat(payload: SendPayload) {
        viewModel.send(payload)
        keepChatAtBottomTemporarily()
    }

    private fun sendFromChat(question: String) {
        sendFromChat(
            SendPayload(
                text = question,
                mentions = emptyList(),
                command = null,
                renderedPrompt = null,
            ),
        )
    }

    /**
     * 内容长高统一入口。流式期间内容每 100ms flush 长高一次：非动画贴底最顺滑，
     * animated 连续重启动画会互相打断造成抖动。
     *
     * 关键缺口修补：ViewModel 在 onDone 里先把 streamState 置回 IDLE、再写入
     * 最终内容（完整卡片解析、追问 chips 挂载），此刻 shouldKeepChatAtBottom
     * 已不满足——若不补宽限，收尾长高后用户看到的不是最底部。此处识别
     * 「流中 flush 过、现已非 STREAMING」的第一拍，补一段宽限贴底窗口。
     */
    private fun handleChatContentSizeGrew() {
        chatScrollCoordinator.onContentSizeGrew()
    }

    /** 引导语双态机：立即重置（新一轮流式/清屏时调用）。 */
    private fun resetFollowUps() {
        followUpCoordinator.reset()
    }

    /**
     * 引导语入场（R4）：回答收尾一拍后先挂载（opacity 0），再翻 presented 播
     * 淡入+上移。version 使重置/重复触发时过期回调失效。
     */
    private fun scheduleFollowUpsPresentation() {
        followUpCoordinator.schedulePresentation()
    }

    /**
     * 引导语可见性：仅最新一条正常完成的 AI 回复，且模型协议未自带 suggestions
     * 卡片（自带时由消息内 SuggestionRow 渲染，不重复弹）。在组件 vif 闭包内
     * 调用：messages 列表与 message.streaming 的 observable 读在此建立依赖。
     */
    private fun isFollowUpsVisible(message: ChatMessage): Boolean {
        val latest = viewModel.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (latest?.id != message.id) return false
        if (message.streaming || message.failed || message.cancelled) return false
        return runCatching {
            AiResponseLexer.lex(message.content, finished = true)
                .none { it is CardBlock && it.type == "suggestions" }
        }.getOrDefault(true)
    }

    /** 引导语 chips：正文提到股票则围绕该标的追问，否则通用追问。 */
    private fun followUpChipsFor(message: ChatMessage): List<SuggestionIntent> {
        val stockName = runCatching {
            EntityRecognizer.recognize(message.content)
                .firstOrNull { it.type == EntityType.STOCK }?.target
        }.getOrNull()?.let { symbol ->
            Securities.all.firstOrNull { it.symbol == symbol }?.name ?: symbol
        }
        return if (stockName != null) {
            listOf(
                SuggestionIntent("${stockName}后市怎么看"),
                SuggestionIntent("${stockName}现在估值高吗"),
                SuggestionIntent("对比同行业其他股票"),
            )
        } else {
            listOf(
                SuggestionIntent("能再详细讲讲吗"),
                SuggestionIntent("结合最新行情再分析一下"),
                SuggestionIntent("总结一下要点"),
            )
        }
    }

    /** 操作栏「复制」：抽取正文纯文本入剪贴板（卡片协议块剔除）。 */
    private fun copyMessageContent(messageId: String) {
        val message = viewModel.messages.firstOrNull { it.id == messageId } ?: return
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridge.copyToPasteboard(messagePlainText(message))
        bridge.toast("已复制")
    }

    /** 操作栏「重试」：从该条 AI 回复起截断，用上一条用户提问重新生成。 */
    private fun regenerateMessage(messageId: String) {
        if (viewModel.regenerateAt(messageId)) keepChatAtBottomTemporarily()
    }

    /**
     * 流式跟随手势仲裁：用户手指拖拽中（isDragging）且不在底部 → 暂停跟随；
     * 拖回底部 → 自动恢复。程序化 setContentOffset 的 isDragging 为 false，
     * 不会误判为用户接管。
     */
    private fun handleChatStreamScroll(params: ScrollParams) {
        // 诊断日志：native 实际滚动位置（scroll 事件是 native 真实位移的地面真相，
        // 与 scrollBottom apply 的目标值对照即可判断「发了指令但没滚到位」）。
        if (kotlin.math.abs(params.offsetY - lastLoggedScrollY) > 8f ||
            kotlin.math.abs(params.contentHeight - lastLoggedContentH) > 1f
        ) {
            lastLoggedScrollY = params.offsetY
            lastLoggedContentH = params.contentHeight
            KLog.i(
                COMPOSER_LOG_TAG,
                "scroll actual y=${params.offsetY} ch=${params.contentHeight} vh=${params.viewHeight} drag=${params.isDragging}",
            )
        }
        // 长按操作菜单随列表滚动即消失，避免浮层与内容错位。
        if (messageActionPresented) dismissMessageActionMenu()
        // 展开但尚未输入时，拖动会话即明确表示用户要浏览内容；收回输入栏和
        // 键盘，避免空白展开态持续遮挡视野。有草稿时保持原样，避免误收起编辑。
        if (
            params.isDragging &&
            voiceState == VoiceState.IDLE &&
            isComposerVisuallyExpanded() &&
            viewModel.inputText.trim().isEmpty()
        ) {
            blurComposer()
            collapseComposer()
        }
        // 回到顶部按钮显隐：所有 scroll 事件都判定（含惯性滚动），仅程序化
        // 动画回顶期间挂起，避免动画中间帧（offsetY 仍很大）把按钮弹回来。
        backToTopCoordinator.onScroll(params.offsetY, params.viewHeight)
        if (!params.isDragging) return
        chatScrollCoordinator.onUserScroll(
            isAtBottom = params.offsetY >= params.contentHeight - params.viewHeight - 80f,
        )
    }

    /**
     * 回到顶部按钮双态机：进入 = 先挂载、一拍后呈现（vif 挂载的首帧不播动画，
     * R4）；退出 = 先收起、动画结束后卸载。version 使过期回调失效。
     */
    /**
     * 平滑滚回顶部：animated=true 走系统滚动动画（区别于 resetChatScrollToTop
     * 的 animated=false 闪现）。动画期间暂停按钮显隐判定，结束后兜底解锁。
     */
    private fun scrollChatToTopAnimated() {
        backToTopCoordinator.scrollToTopAnimated()
    }

    private fun keepChatAtBottomTemporarily() {
        chatScrollCoordinator.onSendRequested()
    }

    /**
     * 贴底执行体：直接读 contentView 的实时布局高度，不依赖
     * contentSizeChanged 事件（该事件挂在 ScrollerContentView 的
     * layoutFrameDidChanged 回调链上，实测不触发时缓存的
     * chatContentHeight 恒为 0，setContentOffset(0, 0) 会把列表钉在顶部）。
     * 内容不足一屏（或高度未知）时不滚，杜绝误跳顶。
     */
    private fun scheduleScrollChatToBottom(animated: Boolean = true) {
        setTimeout(16) {
            val scroller = chatScrollerRef?.view
            if (scroller == null) {
                KLog.i(COMPOSER_LOG_TAG, "scrollBottom ref=null")
                return@setTimeout
            }
            val contentH = scroller.contentView?.frame?.height ?: 0f
            if (contentH > 0f) chatContentHeight = contentH
            val viewH = scroller.frame.height
            if (contentH <= viewH + 1f) {
                KLog.i(COMPOSER_LOG_TAG, "scrollBottom skip contentH=$contentH viewH=$viewH")
                return@setTimeout
            }
            // 目标偏移量必须是 contentH - viewH（而非 contentH 本身），否则超出可滚动
            // 范围，native 侧会静默忽略该次 setContentOffset（Kuikly 自身 List 实现
            // 滚到底也是这个减法，见 LazyLoopDirectivesView 的 maxScrollOffset 计算）。
            val maxOffset = (contentH - viewH - CHAT_SCROLL_HAIR_WIDTH).coerceAtLeast(0f)
            scroller.setContentOffset(0f, maxOffset, animated)
            KLog.i(COMPOSER_LOG_TAG, "scrollBottom apply offset=$maxOffset contentH=$contentH viewH=$viewH animated=$animated")
        }
    }

    private fun resetChatScrollToTop() {
        intArrayOf(0, 16, 80).forEach { delay ->
            setTimeout(delay) {
                chatScrollerRef?.view?.setContentOffset(0f, 0f, false)
            }
        }
    }

    private fun showQuote(symbol: String) = entityCoordinator.showQuote(symbol)

    private fun dismissPeek() = entityCoordinator.dismissPeek()

    private fun schedulePeekDismissal() = entityCoordinator.schedulePeekDismissal()

    /** 长按术语后 bridge 补发的 click：命中抑制词形则消耗并返回 true（不再触发提问）。 */
    private fun consumeTermClickSuppression(token: String) =
        entityCoordinator.consumeTermClickSuppression(token)

    // ===== 实体交互 Effect 执行（页面侧：路由 / 行情 / 岛协作 / 输入栏注入 / 触感 / 埋点）=====

    private fun handleEntityEffect(effect: EntityEffect) {
        when (effect) {
            EntityEffect.Haptic -> acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
            is EntityEffect.Track -> trackComposerEvent(effect.event, *effect.params.toList().toTypedArray())
            is EntityEffect.RequestQuote -> requestQuote(effect.symbol)
            is EntityEffect.OpenStockDetail -> openStockDetail(effect.symbol)
            is EntityEffect.OpenStockIsland -> openEntityQuoteIsland(effect.symbol)
            is EntityEffect.OpenTermIsland -> openEntityTermIsland(effect.key)
            is EntityEffect.AddStockToIsland -> addDraggedStockToIsland(effect.symbol)
            is EntityEffect.AddTermToIsland -> addDraggedTermToIsland(effect.key)
            is EntityEffect.InjectStockMention -> insertDraggedMention(effect.symbol)
            is EntityEffect.InjectQuestion -> injectQuestion(effect.text)
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
                        fontSizeScaled(13f)
                        fontWeightSemiBold()
                        color(page.theme.onBrand)
                    }
                }
                Text {
                    attr {
                        text(
                            when (page.entityDropTarget) {
                                EntityDropTarget.ISLAND ->
                                    if (page.draggedEntity?.type == EntityType.TERM) "松手加入术语对比" else "松手加入股票对比"
                                EntityDropTarget.COMPOSER ->
                                    // 术语没有 @ 提及形态：落到输入框是填入提问。
                                    if (page.draggedEntity?.type == EntityType.TERM) "松手填入提问" else "松手插入 @ 提及"
                                EntityDropTarget.NONE -> "拖到输入框或灵动岛"
                            }
                        )
                        marginTop(2f)
                        fontSizeScaled(9f)
                        color(page.theme.onBrand.opacity(0.78f))
                    }
                }
            }
        }
    }

    private fun handleStockEntityClick(entity: EntitySpan) = entityCoordinator.onStockClick(entity)

    private fun handleStockEntityLongPress(entity: EntitySpan, params: LongPressParams) =
        entityCoordinator.onStockLongPress(
            entity = entity,
            phase = params.state,
            isCancel = params.isCancel,
            pageX = params.pageX,
            pageY = params.pageY,
        )

    private fun isIslandFirstCompareDrop(): Boolean =
        // 首槽判定跟随被拖实体类型，而非槽位残留态：术语对比面板开着时再拖
        // 一只股票进岛，应按股票槽位判定而不是被旧术语槽架空（反之亦然）。
        // 保留在页面：它同时读岛状态 observable（islandCompareLeftSymbol /
        // islandTermCompareLeftKey），DSL 会在 attr 内调用，必须保持反应式读取。
        if (entityState.draggedEntity?.type == EntityType.TERM) {
            islandTermCompareLeftKey.isEmpty()
        } else {
            islandCompareLeftSymbol.isEmpty()
        }

    private fun isIslandCompareLobbyVisible(): Boolean = islandCoordinator.isCompareLobbyVisible()

    // 拖拽收敛（finishEntityDrag / finishStockLongPress / finishEntityHold）已在
    // EntityInteractionCoordinator 内实现：页面不再持有 pendingLongPressSymbol 等守卫字段。

    // ===== 消息长按操作菜单（复制 / 追问）=====

    private fun handleMessageActionEffect(effect: MessageActionEffect) {
        when (effect) {
            is MessageActionEffect.CollectSelection ->
                collectSelectionAndShowMenu(effect.pageX, effect.pageY, effect.fallback)
            is MessageActionEffect.ClearSelection -> clearActiveTextSelection(effect.messageId)
        }
    }

    /** 提取可复制的纯文本：AI 回复剔除卡片代码块只保留正文，解析失败兜底原文。 */
    private fun messagePlainText(message: ChatMessage): String {
        if (message.role == MessageRole.USER) return message.content
        return runCatching {
            AiResponseLexer.lex(message.content, finished = true)
                .filterIsInstance<TextBlock>()
                .joinToString("\n") { it.content.trim() }
                .trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: message.content
    }

    private fun handleSelectionContainerRef(messageId: String, ref: ViewRef<DivView>) {
        messageSelectionRefs[messageId] = ref
    }

    /**
     * 长按消息：优先在落点处起选（WORD 粒度，渲染层显示选择手柄）；起选落空
     * （长按在空白/卡片非文本区）或流式期间，回退为整条消息的复制/追问菜单。
     */
    private fun handleTextSelectionLongPress(
        messageId: String,
        x: Float,
        y: Float,
        pageX: Float,
        pageY: Float,
    ) {
        val message = viewModel.messages.firstOrNull { it.id == messageId } ?: return
        messageActionCoordinator.beginSelection(
            messageId = messageId,
            pageX = pageX,
            pageY = pageY,
            fallback = MessageActionFallback(
                text = messagePlainText(message),
                allowFollowUp = message.role != MessageRole.USER,
            ),
        )
        val ref = messageSelectionRefs[messageId]
        if (message.streaming || ref == null) {
            showMessageActionMenu(messagePlainText(message), message.role != MessageRole.USER, pageX, pageY)
            return
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        ref.view?.createSelection(x, y, SelectionType.WORD)
    }

    /** selectEnd：手柄拖动结束（或起选定时器到达），取选中文本弹「复制/追问」。 */
    private fun handleTextSelectEnd(messageId: String) {
        val (pageX, pageY) = messageActionCoordinator.selectionAnchorFor(messageId) ?: return
        collectSelectionAndShowMenu(pageX, pageY, fallback = null)
    }

    private fun handleTextSelectCancel(messageId: String) {
        // 点按其他区域退出选择模式：同步收起菜单，保持界面状态一致。
        if (messageActionCoordinator.isCurrentSelection(messageId) && messageActionPresented) {
            dismissMessageActionMenu()
        }
    }

    private fun collectSelectionAndShowMenu(pageX: Float, pageY: Float, fallback: MessageActionFallback?) {
        val ref = messageSelectionRefs[messageActionCoordinator.selectedMessageId()]
        if (ref == null) {
            fallback?.let { showMessageActionMenu(it.text, it.allowFollowUp, pageX, pageY) }
            return
        }
        ref.view?.getSelection { result ->
            // content 按 Text 视图阅读顺序给出选中文本，直接相连即原文。
            val selected = result.joinToString("").trim()
            when {
                selected.isNotEmpty() ->
                    showMessageActionMenu(selected, allowFollowUp = true, pageX = pageX, pageY = pageY)
                fallback != null -> showMessageActionMenu(fallback.text, fallback.allowFollowUp, pageX, pageY)
                else -> Unit
            }
        }
    }

    private fun showMessageActionMenu(text: String, allowFollowUp: Boolean, pageX: Float, pageY: Float) {
        messageActionCoordinator.show(text, allowFollowUp, pageX, pageY)
    }

    private fun dismissMessageActionMenu() {
        messageActionCoordinator.dismiss()
    }

    /** 清除当前消息上残留的选择手柄与高亮。 */
    private fun clearActiveTextSelection(messageId: String) = messageSelectionRefs[messageId]?.view?.clearSelection()

    private fun copyMessageToPasteboard() {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridge.copyToPasteboard(messageActionCoordinator.actionText())
        bridge.toast("已复制")
        dismissMessageActionMenu()
    }

    /** 追问：把引文预填进输入栏并聚焦，问句由用户补全（不预填价值判断类文案）。 */
    private fun quoteMessageIntoComposer() {
        val quote = messageActionCoordinator.actionQuote()
        dismissMessageActionMenu()
        if (quote.isEmpty()) return
        val draft = viewModel.inputText.trimEnd()
        setComposerText(if (draft.isEmpty()) "「$quote」" else "$draft\n「$quote」")
        expandComposer(requestFocus = true)
    }

    private fun chooseAmbiguousSymbol(symbol: String) = entityCoordinator.chooseAmbiguous(symbol)

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

    private fun addDraggedStockToIsland(symbol: String) = islandCoordinator.addDraggedStock(symbol)

    private fun syncIslandCompareCard() {
        val leftSymbol = islandCompareLeftSymbol
        val rightSymbol = islandCompareRightSymbol
        if (leftSymbol.isEmpty() || rightSymbol.isEmpty()) return
        compareCoordinator.syncIslandCard(quoteFor(leftSymbol), quoteFor(rightSymbol))
    }

    private fun clearIslandCompare() = islandCoordinator.clearCompare()

    private fun openIslandComparePanel() = islandCoordinator.openComparePanel(compareCard != null)

    private fun addWatchlistFromEntity(symbol: String) {
        val quote = quoteFor(symbol)
        val security = Securities.all.firstOrNull { it.symbol == symbol }
        val name = quote?.name ?: security?.name ?: symbol
        val message = when (watchlistStore.add(symbol, name, pinned = true)) {
            WatchlistAddResult.ADDED -> {
                // FR-W2：对话入口的来源即理由（后续可在自选长按改写，留痕在 reasonHistory）
                // FR-W6：对话入口加入 → 置顶 + ★，「刚聊过的票」在自选列表最上面
                watchlistStore.setReason(symbol, "对话中添加")
                "已加入自选：$name（长按自选可补记理由）"
            }
            WatchlistAddResult.ALREADY_IN -> "$name 已在自选中"
            WatchlistAddResult.FULL -> "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(message)
    }

    private fun openEntityQuoteIsland(symbol: String) = islandCoordinator.openQuoteIsland(symbol)

    // ===== 术语灵动岛（与股票行情岛同一手势/形变体系，2026-09-07）=====

    /** 长按蓝色术语高亮：原地展开术语讲解卡；拖拽跟手与股票实体共用一套字段。 */
    private fun handleTermEntityLongPress(entity: EntitySpan, params: LongPressParams) =
        entityCoordinator.onTermLongPress(
            entity = entity,
            phase = params.state,
            isCancel = params.isCancel,
            pageX = params.pageX,
            pageY = params.pageY,
        )

    private fun openEntityTermIsland(key: String) = islandCoordinator.openTermIsland(key)

    /** 术语拖入灵动岛：第一只占左槽，第二只占右槽并弹出术语对比面板。 */
    private fun addDraggedTermToIsland(key: String) = islandCoordinator.addDraggedTerm(key)

    private fun syncTermComparePanel() {
        if (!islandTermCompareVisible) return
        val left = Glossary.byKey(islandTermCompareLeftKey) ?: return
        val right = Glossary.byKey(islandTermCompareRightKey) ?: return
        compareCoordinator.requestTermInsight(left, right)
    }

    private fun isIslandTermCompareLobbyVisible(): Boolean = islandCoordinator.isTermLobbyVisible()

    private fun clearIslandTermCompare() = islandCoordinator.clearTermCompare()

    private fun toggleIslandWatchlist(symbol: String) {
        noteIslandInteraction()
        val quote = quoteFor(symbol)
        val security = Securities.all.firstOrNull { it.symbol == symbol }
        val name = quote?.name ?: security?.name ?: symbol
        val message = if (watchlistStore.contains(symbol)) {
            watchlistStore.remove(symbol)
            "已从自选移除：$name"
        } else {
            when (watchlistStore.add(symbol, name, pinned = true)) {
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
        val isRealSource = selectedMarketDataSource() == MarketDataSource.REAL
        if (isRealSource) {
            pendingRealQuoteSymbols += symbol
            setTimeout(1_800) {
                if (
                    !pageVisible ||
                    isWillDestroy() ||
                    symbol !in pendingRealQuoteSymbols ||
                    marketFallbackPromptSymbol.isNotEmpty()
                ) return@setTimeout
                marketFallbackPromptSymbol = symbol
            }
        }
        // 与详情页同一条 QuoteRepository 链路（在线→缓存→离线）：不再按数据源开关
        // 短路到 MockQuoteProvider——那会让聊天卡片永远拿 48 点演示分时（索引对齐
        // 240 槽位后只画满左段，用户反馈"分时只能走一半"），而详情页同一时刻拿到
        // 的却是腾讯整日分时。开关只控制离线降级终点（mock 模式=MockDataBank，
        // 真实模式=空态），在线优先与两个页面保持一致。
        quoteRepository.load(symbol) { result ->
            pendingRealQuoteSymbols.remove(symbol)
            if (marketFallbackPromptSymbol == symbol) marketFallbackPromptSymbol = ""
            // 行情的超时兜底可在页面已被 push 覆盖或销毁后才回调。此时再触发
            // ObservableList 重渲染会调用已解绑的 native bridge，Android 会直接
            // 抛出 AssertionError。丢弃本轮结果，并允许下次 pageDidAppear 重试。
            if (!pageVisible || isWillDestroy()) {
                requestedSymbols.remove(symbol)
                return@load
            }
            val updated = ChatQuoteState(symbol, result.quote, result.mode)
            val index = quoteStates.indexOfFirst { it.symbol == symbol }
            if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
            syncIslandCompareCard()
        }
    }

    private fun selectedMarketDataSource(): MarketDataSource = MarketDataSource.fromId(
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .getString(MarketDataPrefs.KEY_SOURCE),
    )

    private fun dismissMarketFallbackPrompt() {
        pendingRealQuoteSymbols.remove(marketFallbackPromptSymbol)
        marketFallbackPromptSymbol = ""
    }

    private fun switchPromptedQuoteToMock() {
        val symbol = marketFallbackPromptSymbol
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .setString(MarketDataPrefs.KEY_SOURCE, MarketDataSource.MOCK.id)
        pendingRealQuoteSymbols.remove(symbol)
        marketFallbackPromptSymbol = ""
        requestedSymbols.remove(symbol)
        if (symbol.isNotEmpty()) requestQuote(symbol)
    }

    private fun toggleCardExpanded(cardKey: String) {
        cardInteractionCoordinator.toggleExpanded(cardKey)
    }

    private fun handleCompareEffect(effect: CompareInsightEffect) {
        when (effect) {
            is CompareInsightEffect.RequestQuote -> requestQuote(effect.symbol)
        }
    }

    // ===== 灵动岛（状态机在 QuoteIslandCoordinator，见 chat/island/state）=====
    // Page 只做 Effect adapter：路由、行情、Glossary、触感、Toast，以及
    // CompareInsight（尚未迁出的域）与实体拖拽残留（转发 EntityInteractionCoordinator）的清理。

    private fun handleIslandEffect(effect: IslandEffect) {
        when (effect) {
            IslandEffect.Haptic ->
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
            is IslandEffect.RequestQuote -> requestQuote(effect.symbol)
            is IslandEffect.OpenStockDetail -> openStockDetail(effect.symbol, islandExpand = true)
            IslandEffect.OpenGlossary -> openGlossary(islandExpand = true)
            is IslandEffect.Toast ->
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(effect.message)
            IslandEffect.RefreshWatchlisted ->
                islandWatchlisted = watchlistStore.contains(islandSymbol)
            is IslandEffect.EncounterTerm -> glossaryStore.encounter(effect.key)
            IslandEffect.SyncCompareCard -> syncIslandCompareCard()
            IslandEffect.SyncTermComparePanel -> syncTermComparePanel()
            IslandEffect.ResetCompareInsight -> compareCoordinator.reset()
            IslandEffect.ClearCompareCard -> compareCoordinator.clearCard()
            IslandEffect.ClearCompareCandidate -> compareCoordinator.clearCandidate()
            IslandEffect.ClearStockDrag -> entityCoordinator.clearStockDragResidue()
            IslandEffect.ClearTermDrag -> entityCoordinator.clearTermDragResidue()
        }
    }

    private fun toggleIsland() = islandCoordinator.toggle()
    private fun handleIslandGesture(state: String, y: Float) = islandCoordinator.onPan(state, y)
    private fun completeIslandMotion(animationKey: String) = islandCoordinator.onMotionComplete(animationKey)
    private fun noteIslandInteraction() = islandCoordinator.noteInteraction()

    private fun toggleDataMode() {
        // 2026-09-08：数据模式只剩"实时"一档（模拟分支已摘除），此开关保留为空操作
        // 以兼容历史调用点。
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
            // 二次点同一触发键是撤销本次输入，不只是藏起候选框。这样 @茅、/复盘
            // 等半成品不会残留在输入框；已固化的 @ 标的并不属于 triggerSession，保持不动。
            val edit = ComposerTextOperations.removeTriggerFragment(
                viewModel.inputText,
                session.cursor,
                session,
            )
            closeAssistantPanel()
            setComposerText(edit.text, edit.cursor)
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
        // 程序化改文本不经过 handleComposerTextChanged，参数态下也要重建面板。
        if (paramCommand != null) bumpParamPanelRenderKey()
    }

    /** 重建参数面板内容（见 paramPanelRenderKey 注释）。 */
    private fun bumpParamPanelRenderKey() {
        composerAssistantCoordinator.bumpParamRenderKey()
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
            // 参数态 SECURITY 槽复用 @ 的候选管线（规范 §5.4）：远端搜索与行情回填同样生效。
            val query = currentParamQuery(cmd)
            if (query.length >= 2) {
                val candidates = rankAtCandidates(query)
                scheduleRemoteSearch(query)
                fetchQuotesForPanel(
                    query,
                    candidates = candidates,
                    isPanelActive = { triggerSession == null && paramCommand === cmd },
                )
            }
            return
        }
        if (cmd != null) clearActiveCommand()
        closeAssistantPanel()
    }

    private fun loadAtCandidates(session: TriggerSession) {
        assistantPanel = AssistantPanel.AT_MENTION
        refreshAtCandidates(session.query)
        scheduleRemoteSearch(session.query)
        fetchQuotesForPanel(session.query)
        trackComposerEvent(
            "at_panel_show_src",
            "src" to "local",
            "query_len" to session.query.length,
            "count" to atCandidates.size,
        )
    }

    /** 本地目录 + 远端池统一打分并刷新 @ 候选列表。 */
    private fun refreshAtCandidates(query: String) {
        composerAssistantCoordinator.replaceAtCandidates(rankAtCandidates(query))
    }

    private fun rankAtCandidates(query: String): List<AtCandidate> =
        AtCandidateProvider.rank(query, recentMentions, watchlistStore.symbols(), remoteEntries.toList())

    /**
     * 远端搜索建议（规范 10 §4.2 S5 / P5）：query ≥ 2 字符时防抖请求东财 suggest，
     * 结果并入 remoteEntries 池后重排候选。到达时会话已终结（query 变化/面板关闭）则丢弃。
     * @ / 命令参数态共用：参数态下 triggerSession == null 且 paramCommand != null 视为活跃。
     */
    private fun scheduleRemoteSearch(query: String) {
        composerAssistantCoordinator.requestRemoteSearch(query)
    }

    private fun handleComposerAssistantEffect(effect: ComposerAssistantEffect) {
        when (effect) {
            is ComposerAssistantEffect.SearchSecurities ->
                dependencies.securitySearchProvider.searchSecurities(effect.query) { securities ->
                    composerAssistantCoordinator.acceptRemoteSearchResults(
                        generation = effect.generation,
                        query = effect.query,
                        entries = securities.map { it.toCatalogEntry() },
                        limit = REMOTE_ENTRY_POOL_LIMIT,
                        isLocalSymbol = { ComposerCatalog.find(it) != null },
                    )
                }
            is ComposerAssistantEffect.RemoteEntriesMerged -> {
                if (effect.parameterPanelActive) {
                    bumpParamPanelRenderKey()
                } else {
                    refreshAtCandidates(effect.query)
                    fetchQuotesForPanel(effect.query)
                }
                trackComposerEvent(
                    "at_panel_show_src",
                    "src" to "remote",
                    "query_len" to effect.query.length,
                    "count" to atCandidates.size,
                )
            }
        }
    }

    /** 远端建议 Security → 候选目录条目（PinYin 字段是首字母串，作 pinyinAbbr）。 */
    private fun com.kuikly.stockchat.data.entity.Security.toCatalogEntry(): CatalogEntry =
        CatalogEntry(
            symbol = symbol,
            name = name,
            market = market,
            pinyinFull = "",
            pinyinAbbr = aliases.firstOrNull { it.isNotEmpty() && !it[0].isDigit() }?.lowercase().orEmpty(),
            kind = when (kind) {
                "index" -> MentionType.INDEX
                "board" -> MentionType.BOARD
                else -> MentionType.STOCK
            },
            hot = 0.5f,
        )

    /**
     * 候选涨跌回填：对当前候选前几只发轻量快照请求（QuoteRepository 快照缓存去重），
     * 到达后原地更新涨跌幅。板块无涨跌跳过；面板已切换 query 则丢弃迟到结果。
     */
    private fun fetchQuotesForPanel(
        query: String,
        candidates: List<AtCandidate> = atCandidates.toList(),
        isPanelActive: () -> Boolean = { triggerSession?.query == query },
    ) {
        val generation = composerAssistantCoordinator.nextQuoteGeneration()
        candidates.take(PANEL_QUOTE_FETCH_LIMIT).forEach { candidate ->
            val entry = candidate.entry
            if (entry.kind == MentionType.BOARD || entry.chgPct != null) return@forEach
            if (!composerAssistantCoordinator.markQuoteRequested(entry.symbol)) return@forEach
            quoteRepository.snapshotForContext(entry.symbol) { result ->
                val quote = result.quote ?: return@snapshotForContext
                val pct = if (quote.price > 0.0 && quote.previousClose > 0.0) {
                    ((quote.price - quote.previousClose) / quote.previousClose * 100.0).toFloat()
                } else {
                    null
                }
                setTimeout(0) {
                    if (!composerAssistantCoordinator.isCurrentQuoteGeneration(generation)) return@setTimeout
                    if (!isPanelActive()) return@setTimeout
                    applyChgPct(entry.symbol, pct)
                }
            }
        }
    }

    /** 把涨跌写回候选列表与远端池（列表整体重建以触发 Observable 重渲染）。 */
    private fun applyChgPct(symbol: String, pct: Float?) {
        if (!composerAssistantCoordinator.applyChgPct(symbol, pct)) return
        // 参数态候选行由面板整帧重建渲染，行情到达后 bump 一次让涨跌立即可见。
        if (triggerSession == null && paramCommand != null) bumpParamPanelRenderKey()
    }

    private fun loadSlashCandidates(session: TriggerSession) {
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
        composerAssistantCoordinator.closePanel()
    }

    private fun openImagePreview(path: String) {
        imagePreviewCoordinator.open(path)
    }

    private fun closeImagePreview() {
        imagePreviewCoordinator.close()
    }

    /** 结束 / 命令参数态。 */
    private fun clearActiveCommand() {
        paramCommand = null
    }

    /**
     * 参数面板右上角「取消」：摘掉命令 token 退出命令态，正文与 @ 提及保留
     * （提及仍在文本里，发送对账照常生效）。
     */
    private fun cancelActiveCommand() {
        val command = paramCommand ?: return
        val text = viewModel.inputText
        val prefix = "/${command.name}"
        val remaining = if (text.trimStart().startsWith(prefix)) {
            text.trimStart().removePrefix(prefix).trimStart()
        } else {
            text
        }
        clearActiveCommand()
        closeAssistantPanel()
        setComposerText(remaining)
        inputRef?.view?.focus()
        trackComposerEvent("at_token_edit", "type" to "command_cancel", "command" to command.id)
    }

    /**
     * 点已填槽位 → 清空该槽重新填写（规范 §5.4 闭环编辑）。
     * SECURITY 槽移除 @token 及其固化提及；ENUM 槽移除选项词。
     */
    private fun clearFilledParamSlot(command: SlashCommand, param: CommandParam) {
        val args = resolveCommandFromText(
            viewModel.inputText,
            SolidTokenRegistry.verify(mentionEntities, viewModel.inputText),
        )?.args.orEmpty()
        val value = args[param.key].orEmpty().trim()
        if (value.isEmpty()) return
        val token = if (param.type == ParamType.SECURITY) "@$value" else value
        val text = viewModel.inputText
        val idx = text.indexOf(token)
        if (idx < 0) return
        val newText = (text.substring(0, idx) + " " + text.substring(idx + token.length))
            .replace(Regex(" {2,}"), " ")
            .trimEnd() + " "
        if (param.type == ParamType.SECURITY) {
            mentionEntities.removeAll { it.mentionText == token }
        }
        setComposerText(newText, idx)
        commandValidationMessage = ""
        trackComposerEvent("at_token_edit", "type" to "slot_clear", "param" to param.key)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
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
        // 附件快照：随 payload 进用户气泡回显；发送后由 submitInput 清输入栏。
        val attachments = composerAttachmentState.attachments.map {
            com.kuikly.stockchat.chat.MessageAttachment(it.id, it.path, it.name, it.isImage)
        }
        // 纯附件无文字：用附件兜底引导语保证发送管线有正文。
        val rawText = viewModel.inputText.ifBlank {
            if (attachments.isNotEmpty()) defaultAttachmentPrompt() else ""
        }
        val verified = SolidTokenRegistry.verify(mentionEntities, rawText)
        // 路由焦点标的绕过文本对账并入提及（去重：与输入期提及同 symbol 时以输入侧为准）。
        // 注意：合并列表只进 SendPayload（systemNote / 行情上下文注入消费）；
        // 命令解析仍用 verified——SECURITY 槽按提及顺序填、不看文本里有没有 @，
        // 焦点提及排第一会把 `/对比 五粮液 估值` 这类命令的第一个槽错填成入口标的。
        val verifiedMentions = routeFocusMention
            ?.takeIf { verified.none { m -> m.symbol == it.symbol } }
            ?.let { listOf(it) + verified }
            ?: verified
        val command = resolveCommandFromText(rawText, verified)
        val payload = if (command != null) {
            val rendered = CommandRegistry.renderPrompt(command.commandId.let { id ->
                CommandRegistry.all.firstOrNull { it.id == id } ?: CommandRegistry.all.first()
            }, command.args)
            SendPayload(rawText, verifiedMentions, command, rendered, deepContextNotes.toList(), attachments)
        } else {
            SendPayload(rawText, verifiedMentions, null, null, deepContextNotes.toList(), attachments)
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
        return AtCandidateProvider.rank(q, recentMentions, watchlistStore.symbols(), remoteEntries.toList()).any { candidate ->
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
        container.View {
            attr {
                marginTop(8f)
                backgroundColor(page.theme.surface)
                borderRadius(12f)
                overflow(true)
            }
            // R1：面板内容依赖 triggerComposing / atCandidates 两个 observable，
            // 分支判定必须放进 vif、行列表必须走 vfor——普通 builder 闭包只取首帧
            // 快照，面板挂载后的列表更新不会重渲染（2026-09-10 真机复现：
            // @ 面板永远停在「没有可推荐的标的」空态）。
            vif({ page.triggerComposing }) {
                View {
                    attr {
                        height(ASSISTANT_PANEL_EMPTY_HEIGHT)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Text { attr { text("输入中…"); fontSizeScaled(12f); color(page.theme.textTertiary) } }
                }
            }
            vif({ !page.triggerComposing && page.atCandidates.isEmpty() }) {
                View {
                    attr { height(56f); alignItemsCenter(); justifyContentCenter() }
                    Text {
                        attr {
                            // query 在面板打开期间持续变化，文案放 attr 响应式读取。
                            val q = page.triggerSession?.query.orEmpty()
                            text(if (q.isEmpty()) "没有可推荐的标的" else "没有匹配“$q”的标的")
                            fontSizeScaled(12f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            }
            vif({ !page.triggerComposing && page.atCandidates.isNotEmpty() }) {
                Scroller {
                    attr {
                        height(assistantPanelHeight(page.atCandidates.size, CANDIDATE_ROW_HEIGHT))
                        flexDirectionColumn()
                        padding(4f)
                    }
                    vfor({ page.atCandidates }) { candidate ->
                        val q = page.triggerSession?.query.orEmpty()
                        View {
                            attr {
                                height(CANDIDATE_ROW_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                paddingLeft(12f)
                                paddingRight(12f)
                                backgroundColor(
                                    if (page.atCandidates.indexOf(candidate) == page.atHighlight) page.theme.brandSoft
                                    else Color(0x00000000L, 0f)
                                )
                            }
                            event { click { page.selectAtCandidate(candidate) } }
                            if (candidate.entry.kind == MentionType.BOARD) {
                                ComposerCandidateRows.renderBoard(this, candidate, q, page.theme)
                            } else {
                                ComposerCandidateRows.renderSecurity(this, candidate, q, page.theme)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderSlashCommandRows(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                marginTop(8f)
                backgroundColor(page.theme.surface)
                borderRadius(12f)
                overflow(true)
            }
            // R1：与 @ 面板同理——分支判定进 vif、命令行走 vfor；普通 builder 闭包
            // 只取首帧快照，输入 / 命令名过程中的候选更新不会重渲染。
            vif({ page.slashUnknown.isNotEmpty() }) {
                View {
                    attr { padding(10f); flexDirectionColumn() }
                    Text { attr { text("未识别命令：/${page.slashUnknown}"); fontSizeScaled(12f); color(page.theme.textSecondary) } }
                    Text { attr { text("将作为普通文本发送"); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                    val suggestions = CommandRegistry.suggest(page.slashUnknown)
                    if (suggestions.isNotEmpty()) {
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
                            Text { attr { text("你是不是想用"); fontSizeScaled(10f); color(page.theme.textTertiary); marginRight(6f) } }
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
                                    Text { attr { text("/${command.name}"); fontSizeScaled(11f); color(page.theme.brand) } }
                                    event { click { page.selectSlashCommand(command) } }
                                }
                            }
                        }
                    }
                }
            }
            vif({ page.slashUnknown.isEmpty() && page.slashCandidates.isEmpty() }) {
                View {
                    attr { height(36f); alignItemsCenter(); justifyContentCenter() }
                    Text { attr { text("输入 / 唤起指令"); fontSizeScaled(12f); color(page.theme.textTertiary) } }
                }
            }
            vif({ page.slashUnknown.isEmpty() && page.slashCandidates.isNotEmpty() }) {
                Scroller {
                    attr {
                        height(assistantPanelHeight(page.slashCandidates.size, COMMAND_ROW_HEIGHT))
                        flexDirectionColumn()
                        padding(4f)
                    }
                    vfor({ page.slashCandidates }) { command ->
                        View {
                            attr {
                                height(COMMAND_ROW_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f)
                                backgroundColor(
                                    if (page.slashCandidates.indexOf(command) == page.slashHighlight) page.theme.brandSoft
                                    else Color(0x00000000L, 0f)
                                )
                            }
                            event { click { page.selectSlashCommand(command) } }
                            View {
                                attr { width(28f); height(28f); marginRight(10f); alignItemsCenter(); justifyContentCenter(); backgroundColor(page.theme.brandSoft); borderRadius(8f) }
                                Text { attr { text(command.icon); fontSizeScaled(14f); color(page.theme.brand) } }
                            }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text { attr { text("/${command.name}"); fontSizeScaled(13f); color(page.theme.textPrimary) } }
                                Text { attr { text(command.desc); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderCommandParams(container: ViewContainer<*, *>) {
        val page = this
        // R1：vif creator 的内容只构建一次（ConditionView.didCreated 守卫），参数态下
        // 打字/点选/远端到达都要靠 paramPanelRenderKey 的 collection 操作触发本 vfor
        // 整帧重建，下面的 args/missing/currentKey 才能读到最新值（所见即所发）。
        vfor({ page.paramPanelRenderKey }) { _ ->
        val command = page.paramCommand ?: return@vfor
        // 参数值实时解析：与发送时同一套 resolveCommandFromText，面板所见即所发。
        val args = page.resolveCommandFromText(
            page.viewModel.inputText,
            SolidTokenRegistry.verify(page.mentionEntities, page.viewModel.inputText),
        )?.args.orEmpty()
        val missing = page.missingRequiredParams(command, args)
        // 当前槽位判定：必填缺口优先；否则若末尾输入恰好命中某可选 ENUM 的选项
        //（如 /复盘 直接打"周"），跳到该 ENUM 槽，不被前面的可选 SECURITY 槽拦住。
        val trailing = page.currentParamQuery(command)
        val currentKey = missing.firstOrNull()?.key
            ?: command.params.firstOrNull { p ->
                args[p.key].isNullOrBlank() && p.type == ParamType.ENUM && trailing in p.enumOptions
            }?.key
            ?: command.params.firstOrNull { args[it.key].isNullOrBlank() }?.key
        val currentParam = command.params.firstOrNull { it.key == currentKey }
        val requiredTotal = command.params.count { it.required }
        // 槽位数量由命令 schema 决定、不会跳动，高度按条数算：
        // 标题 32f + 每槽 40f + 底部提示 36f，超出上限则在框内滚动。
        val wanted = 32f + command.params.size * 40f + 36f +
            if (currentParam?.type == ParamType.SECURITY) {
                18f + ASSISTANT_PANEL_MAX_ROWS * (CANDIDATE_ROW_HEIGHT + 4f)
            } else {
                0f
            }
        Scroller {
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
                    Text { attr { text(command.icon); fontSizeScaled(12f); color(page.theme.brand) } }
                }
                Text { attr { text("/${command.name} · 参数"); fontSizeScaled(13f); color(page.theme.textPrimary) } }
                View { attr { flex(1f) } }
                if (requiredTotal > 0) {
                    Text {
                        attr {
                            text("必填 ${requiredTotal - missing.size}/$requiredTotal")
                            fontSizeScaled(10f)
                            color(if (missing.isEmpty()) page.theme.brand else page.theme.textSecondary)
                            marginRight(8f)
                        }
                    }
                }
                View {
                    attr {
                        height(22f); paddingLeft(8f); paddingRight(8f)
                        alignItemsCenter(); justifyContentCenter()
                        backgroundColor(page.theme.surfaceMuted); borderRadius(7f)
                    }
                    event { click { page.cancelActiveCommand() } }
                    Text { attr { text("✕ 取消"); fontSizeScaled(10f); color(page.theme.textSecondary) } }
                }
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
                    // 已填槽位可点击重填（闭环编辑）：SECURITY 槽移除 @token，ENUM 槽移除选项词。
                    if (filled.isNotEmpty()) {
                        event { click { page.clearFilledParamSlot(command, param) } }
                    }
                    View { attr { flex(1f); flexDirectionColumn() }
                        Text { attr { text(param.label + if (param.required) " *" else "（可选）"); fontSizeScaled(11f); color(if (param.key == currentKey) page.theme.brand else page.theme.textSecondary) } }
                        Text { attr { text(if (filled.isNotEmpty()) filled else param.placeholder); fontSizeScaled(12f); color(if (filled.isNotEmpty()) page.theme.textPrimary else page.theme.textTertiary) } }
                    }
                    Text {
                        attr {
                            text(if (filled.isNotEmpty()) "重填" else when (param.type) { ParamType.SECURITY -> "@" ; ParamType.ENUM -> "选" ; else -> "文" })
                            fontSizeScaled(9f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            }
            if (currentParam?.type == ParamType.SECURITY) {
                val query = page.currentParamQuery(command)
                val candidates = page.rankAtCandidates(query).take(ASSISTANT_PANEL_MAX_ROWS)
                Text {
                    attr {
                        text(
                            when {
                                query.isEmpty() -> "选择${currentParam.label}"
                                candidates.isEmpty() -> "没有匹配「$query」的标的 · 可输入完整名称或代码后发送"
                                else -> "匹配「$query」"
                            }
                        )
                        marginTop(10f)
                        fontSizeScaled(10f)
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
                            ComposerCandidateRows.renderBoard(this, candidate, query, page.theme)
                        } else {
                            ComposerCandidateRows.renderSecurity(this, candidate, query, page.theme)
                        }
                    }
                }
            } else if (currentParam?.type == ParamType.ENUM) {
                Text {
                    attr {
                        text("选择${currentParam.label}")
                        marginTop(10f)
                        fontSizeScaled(10f)
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
                            Text { attr { text(option); fontSizeScaled(12f); fontWeightMedium(); color(page.theme.brand) } }
                            event { click { page.selectParamEnumOption(option) } }
                        }
                    }
                }
            }
            View {
                attr { marginTop(8f); alignItemsCenter(); justifyContentCenter(); height(28f) }
                Text {
                    attr {
                        text(
                            when {
                                missing.isNotEmpty() -> "还差必填：${missing.joinToString("、") { it.label }} · 点下方候选或直接输入"
                                command.params.any { args[it.key].isNullOrBlank() } -> "必填已齐 · 可点选可选参数或直接发送"
                                else -> "参数已齐 · 点击发送键发送"
                            }
                        )
                        fontSizeScaled(10f)
                        color(page.theme.textTertiary)
                    }
                }
            }
        }
        } // vfor paramPanelRenderKey
    }

    private fun cycleGlassMode() {
        sessionChromeCoordinator.cycleGlassMode()
    }

    private fun setFocusedCard(cardKey: String, focused: Boolean) {
        cardInteractionCoordinator.setFocused(cardKey, focused)
    }

    private fun handleCompareCandidate(cardKey: String, symbol: String) {
        compareCoordinator.selectCardCandidate(cardKey, symbol, ::quoteFor)
        if (compareCard != null) cardInteractionCoordinator.clearFocused()
    }

    private fun handleCardEvent(cardKey: String, event: CardEvent) {
        cardInteractionCoordinator.onCardEvent(cardKey, event)
    }

    private fun injectQuestion(question: String) {
        setComposerText(question)
        // 注入新文本后旧固化提及与命令态失效，统一清空输入期状态。
        mentionEntities.clear()
        clearActiveCommand()
        closeAssistantPanel()
        // 推荐问句/联想词/历史标的：统一从 expandComposer 进入，输入态与键盘一起到位。
        expandComposer(requestFocus = true)
    }

    private fun consumeRouteQuestionIfNeeded() {
        val question = pendingRouteQuestion.trim()
        if (question.isEmpty()) return
        pendingRouteQuestion = ""
        val focusNote = pendingRouteFocusNote.trim()
        pendingRouteFocusNote = ""
        val focusSymbol = pendingRouteFocusSymbol.trim()
        pendingRouteFocusSymbol = ""
        setTimeout(0) {
            if (focusNote.isNotEmpty()) attachContextNote(focusNote)
            // 结构化焦点标的：问题文本不含股票名时（如"「14:32 冲高回落」帮我
            // 深聊这段走势"），AI 原本无从得知问的是哪只。把标的固化为发送侧
            // 提及（buildSendPayload 组包时并入，绕过文本对账），systemNote 里
            // 即出现"请优先围绕这些标的回答"，ChatQuoteContext 也会随之注入
            // 真实行情；另挂一条可移除的上下文标注让用户可见。
            if (focusSymbol.isNotEmpty()) {
                Securities.all.firstOrNull { it.symbol == focusSymbol }?.let { security ->
                    routeFocusMention = MentionEntity(
                        symbol = security.symbol,
                        name = security.name,
                        type = MentionType.STOCK,
                        mentionText = "@${security.name}",
                    )
                    attachContextNote("标的：${security.name}（${security.symbol}）")
                }
            }
            injectQuestion(question)
            if (pendingAutoAsk) {
                pendingAutoAsk = false
                // 等输入栏展开与欢迎入场收尾后再发，避免与入场动效抢同一批状态。
                setTimeout(pendingAutoAskDelay) {
                    if (viewModel.streamState != StreamState.STREAMING) submitInput()
                }
            }
        }
    }

    /** Route-provided context must not toggle the deep-water visual mode. */
    private fun attachContextNote(note: String) {
        if (note !in deepContextNotes) deepContextNotes.add(note)
        deepContextVersion++
    }

    private fun clearCompare() = islandCoordinator.clearCompare()

    private fun openCardSheet(model: CardModel, deferInteraction: Boolean = false) {
        cardSheetCoordinator.open(model, deferInteraction)
    }

    private fun dismissCardSheet() {
        cardSheetCoordinator.dismiss()
    }

    private fun raiseCardSheet() = cardSheetCoordinator.raise()

    private fun lowerCardSheet() = cardSheetCoordinator.lower()

    private fun handleSheetPan(state: String, y: Float) = cardSheetCoordinator.onPan(state, y)

    private fun toggleDrill(drillKey: String) {
        cardInteractionCoordinator.toggleDrill(drillKey)
    }

    private fun startSubThread(model: CardModel) {
        cardInteractionCoordinator.startSubThread(model)
    }

    private fun toggleSubThread(cardId: String) {
        cardInteractionCoordinator.toggleSubThread(cardId)
    }

    private fun updateSubThreadInput(cardId: String, input: String) {
        cardInteractionCoordinator.updateSubThreadInput(cardId, input)
    }

    private fun sendSubThread(cardId: String) {
        cardInteractionCoordinator.sendSubThread(cardId)
    }

    private fun retryCard(messageId: String, blockId: String, cardType: String, rawCard: String) {
        cardInteractionCoordinator.retryCard(messageId, blockId, cardType, rawCard)
    }

    private fun handleCardInteractionEffect(effect: CardInteractionEffect) {
        when (effect) {
            CardInteractionEffect.HapticImpact ->
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
            is CardInteractionEffect.RequestSubThread -> requestSubThread(effect.cardId, effect.prompt)
            is CardInteractionEffect.RetryCard -> viewModel.retryCard(
                messageId = effect.messageId,
                blockId = effect.blockId,
                cardType = effect.cardType,
                rawCard = effect.rawCard,
                onDone = {
                    cardInteractionCoordinator.onRetryDone()
                    keepChatAtBottomTemporarily()
                },
                onError = { error ->
                    cardInteractionCoordinator.onRetryError()
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(error)
                },
            )
        }
    }

    private fun requestSubThread(cardId: String, prompt: String) {
        var response = ""
        viewModel.askSubThread(
            prompt = prompt,
            onDelta = { delta ->
                response += delta
                cardInteractionCoordinator.onSubThreadDelta(cardId, response)
            },
            onDone = { cardInteractionCoordinator.onSubThreadDone(cardId, response) },
            onError = { error -> cardInteractionCoordinator.onSubThreadError(cardId, error) },
        )
    }

    private fun retryCompareInsight() {
        // 术语对比的解读重试不能走股票路径（pairKey/quotes 都不同），按会话派发。
        if (islandTermCompareLeftKey.isNotEmpty()) {
            retryTermCompareInsight()
            return
        }
        val cardQuotes = compareCard?.quotes.orEmpty()
        val left = cardQuotes.getOrNull(0) ?: quoteFor(islandCompareLeftSymbol) ?: return
        val right = cardQuotes.getOrNull(1) ?: quoteFor(islandCompareRightSymbol) ?: return
        compareCoordinator.retryStockInsight(left, right)
    }

    private fun retryTermCompareInsight() {
        val left = Glossary.byKey(islandTermCompareLeftKey) ?: return
        val right = Glossary.byKey(islandTermCompareRightKey) ?: return
        compareCoordinator.retryTermInsight(left, right)
    }


    private fun quoteFor(symbol: String): Quote? {
        return quoteStates.firstOrNull { it.symbol == symbol }?.quote ?: quoteRepository.cachedOrOffline(symbol)
    }

}

private data class ChatQuoteState(
    val symbol: String,
    val quote: Quote?,
    val mode: DataMode,
)


/**
 * 联想面板最多可见行数：候选条数超过这个行数后，面板定高、超出部分在框内滚动。
 * 固定为 3 行——既避免面板把输入栏顶得太高，也能覆盖绝大多数"输入几个字即命中"的场景。
 *
 * 注意不能用 `maxHeight`：Kuikly 的 Scroller contentView 是绝对定位、高度由内容决定，
 * 实测 `maxHeight` 压不住，面板会被撑到完整高度。所以这里按候选条数**算出实际高度**
 * 再用 `height()` 定死——候选少时贴合内容（不留空白框），多到 3 行就截断滚动。
 */
private const val ASSISTANT_PANEL_MAX_ROWS = 3

/** 远端搜索建议防抖：停止输入 250ms 后才发请求（规范 §6.2 防抖 80ms 的宽松版，省配额）。 */
private const val REMOTE_SEARCH_DEBOUNCE_MS = 250

/** 远端建议池上限：超限整体清空（历史命中已随 query 演进自然退场）。 */
private const val REMOTE_ENTRY_POOL_LIMIT = 200

/** 每轮面板刷新最多回填涨跌的候选数（快照请求逐只发，控量）。 */
private const val PANEL_QUOTE_FETCH_LIMIT = 6

/** Composer motion invariants: both layout rows keep a stable baseline while toggling. */
private const val COMPOSER_GUIDE_HEIGHT = 42f
private const val COMPOSER_ACTION_ROW_HEIGHT = 46f
private const val COMPOSER_ACTION_ROW_GAP = 8f
private const val COMPOSER_LAYOUT_DURATION = 0.28f

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
 * 联想面板维度（规范 10 §2）：@ 提及、/ 命令选择、/ 命令参数槽位三态在此维度切换。
 */

/** 输入栏「+」可选的媒体来源（底部弹层磁贴入口，样式对齐抽屉 DrawerTile）。 */
private enum class ComposerMediaAction(val source: String, val label: String) {
    PHOTO_LIBRARY("library", "上传图库"),
    CAMERA("camera", "拍照"),
    DOCUMENT("document", "手机文档"),
}

/**
 * 底部媒体来源弹层：全屏蒙层 + 底部圆角卡片。蒙层或「取消」关闭；三个入口为
 * 抽屉同款磁贴（2026-09-10 重设计：去掉原「图标圆盘+标题/说明+箭头」行式布局，
 * 改为白卡 + 发丝描边 + 浅投影的 3 列磁贴，图标 22 / 标签 10，与 DrawerTile 一致）。
 * R4 两帧入场由页侧 openMediaSheet / dismissMediaSheet 驱动（mounted 隐藏挂载 →
 * presented 翻转播动画）。
 */
private fun ViewContainer<*, *>.MediaActionSheetHost(
    theme: StockChatTheme,
    presented: () -> Boolean,
    bottomInset: Float,
    onDismiss: () -> Unit,
    onAction: (ComposerMediaAction) -> Unit,
) {
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
            backgroundColor(Color(0x59000000))
            opacity(if (presented()) 1f else 0f)
            animate(Animation.easeOut(0.20f), presented())
        }
        event { click { onDismiss() } }
    }
    View {
        attr {
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            paddingLeft(16f)
            paddingRight(16f)
            paddingBottom(bottomInset + 12f)
            touchEnable(true)
            opacity(if (presented()) 1f else 0f)
            transform(Translate(0f, if (presented()) 0f else 48f))
            animate(Animation.easeOut(0.26f), presented())
        }
        View {
            attr {
                borderRadius(20f)
                backgroundColor(theme.surface)
                paddingTop(14f)
                paddingLeft(14f)
                paddingRight(14f)
                paddingBottom(14f)
                boxShadow(BoxShadow(0f, 10f, 30f, Color(0x000000, 0.18f)))
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(4f); paddingRight(4f) }
                Text {
                    attr {
                        text("添加内容")
                        flex(1f)
                        fontSizeScaled(15f)
                        fontWeightSemiBold()
                        color(theme.textPrimary)
                    }
                }
                Text {
                    attr { text("取消"); fontSizeScaled(13f); color(theme.textSecondary) }
                    event { click { onDismiss() } }
                }
            }
            // 入口磁贴行：公共 FeatureTile（抽屉 DrawerTile 同款磁贴，2026-09-10 统一）。
            View {
                attr { flexDirectionRow(); marginTop(12f) }
                ComposerMediaAction.values().forEachIndexed { index, action ->
                    if (index > 0) {
                        View { attr { width(8f) } }
                    }
                    FeatureTile(
                        label = action.label,
                        theme = theme,
                        height = 68f,
                        icon = {
                            when (action) {
                                ComposerMediaAction.PHOTO_LIBRARY -> LineIconPhoto(theme.textPrimary, 22f)
                                ComposerMediaAction.CAMERA -> LineIconCamera(theme.textPrimary, 22f)
                                ComposerMediaAction.DOCUMENT -> LineIconFileText(theme.textPrimary, 22f)
                            }
                        },
                        onClick = { KLog.i("Composer", "mediaSheetTileTap ${action.source}"); onAction(action) },
                    )
                }
            }
        }
    }
}
