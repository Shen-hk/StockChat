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
import com.kuikly.stockchat.glass.applyGlassSurfaceSkin
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.ChatViewModel
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.StreamState
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.openGlossary
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.LocalAlertProvider
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.config.DataSourceConfig
import com.kuikly.stockchat.data.mock.MockQuoteProvider
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.page.components.ChatDrawer
import com.kuikly.stockchat.page.components.CardSheetHost
import com.kuikly.stockchat.page.components.ActiveComparePanel
import com.kuikly.stockchat.page.components.TermComparePanel
import com.kuikly.stockchat.page.components.ChatMessageActions
import com.kuikly.stockchat.page.components.ChatMessageRenderState
import com.kuikly.stockchat.page.components.ChatMessageView
import com.kuikly.stockchat.page.components.DateDivider
import com.kuikly.stockchat.page.components.RecentSymbolRow
import com.kuikly.stockchat.page.components.RegressionQuestionRow
import com.kuikly.stockchat.page.components.SubThreadState
import com.kuikly.stockchat.page.components.WelcomeMode
import com.kuikly.stockchat.page.components.WelcomeSection
import com.kuikly.stockchat.page.components.WelcomeStarter
import com.kuikly.stockchat.page.components.defaultWelcomeStarters
import com.kuikly.stockchat.page.components.ChatTopNav
import com.kuikly.stockchat.page.components.DrawerGestureMotion
import com.kuikly.stockchat.page.components.DrawerGesturePhase
import com.kuikly.stockchat.page.components.IslandGestureMotion
import com.kuikly.stockchat.page.components.IslandGesturePhase
import com.kuikly.stockchat.page.components.ISLAND_ANIMATION_CLOSE
import com.kuikly.stockchat.page.components.ISLAND_ANIMATION_DETAIL
import com.kuikly.stockchat.page.components.ISLAND_ANIMATION_RETURN
import com.kuikly.stockchat.page.components.LineIconAudioLines
import com.kuikly.stockchat.page.components.LineIconKeyboard
import com.kuikly.stockchat.page.components.LineIconChevronUp
import com.kuikly.stockchat.page.components.LineIconPlus
import com.kuikly.stockchat.page.components.LineIconCamera
import com.kuikly.stockchat.page.components.LineIconPhoto
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
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.attr.AccessibilityRole
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
import com.tencent.kuikly.core.directives.vbind
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

@Page(Routes.CHAT, supportInLocal = true)
internal class ChatPage : BasePager() {
    // 输入栏诊断日志统一 tag。logcat 过滤：adb logcat -s KLog 或搜 "Composer"。
    private companion object {
        const val COMPOSER_LOG_TAG = "Composer"
        const val WELCOME_USED_KIND_KEY = "stockchat_welcome_used_starter_kinds_v1"
        // 焦点隔离诊断开关；正常交付必须关闭。完整输入栏使用无 Blur 的静态
        // 玻璃表皮，避免原生 Blur 覆盖层遮住 EditText 光标。
        const val COMPOSER_ISOLATION_TEST = false
        // 意外 blur 自动恢复的连续尝试上限。
        const val COMPOSER_BLUR_RECOVER_MAX_ATTEMPTS = 3
        // 滚动声波条数：新采样从右缘进入、历史整体左移（60ms/格），
        // 40 条 × (3+3) ≈ 240dp，铺满中段波形区。
        const val VOICE_AMP_BARS = 56
        // 流式贴底循环在「非流式且不在贴底窗口」后的宽限拍数（120ms/拍 ≈ 2.4s），
        // 覆盖流结束后的收尾长高（卡片解析、追问 chips、卡片行情异步到账）。
        const val CHAT_FOLLOW_GRACE_TICKS = 20
        // 流式尚未开始（首包未到）时贴底循环的最长存活拍数（120ms/拍 ≈ 19s）：
        // 行情上下文解析 watchdog 最长 3s + LLM 首包延迟，宽限计数必须等见过
        // STREAMING 才启动，否则循环会在流开始前退出、跟随彻底失去驱动。
        // 同时兜底防「流永远不来」（网络挂死）时循环无限存活。
        const val CHAT_FOLLOW_PRE_STREAM_MAX_TICKS = 160
        // 贴底目标偏移量的安全余量：native 侧对超出 contentH-viewH 的
        // setContentOffset 请求会静默无效，减 1px 规避浮点精度导致的误判。
        const val CHAT_SCROLL_HAIR_WIDTH = 1f
    }
    private val dependencies by lazy { ChatDependencies.forPager(pagerId) }
    private val viewModel by lazy { ChatViewModel(pagerId, dependencies) }
    private val welcomeStorage by lazy { PagerKeyValueStorage(pagerId) }
    private val welcomeKeywords = listOf("行情", "术语", "财报", "公告")
    // 全页唯一、持久挂载的 TextArea。ref 只在首次 body 挂载前为空。
    private var inputRef: ViewRef<TextAreaView>? = null
    private var chatScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    private var chatContentHeight = 0f
    private var lastLoggedScrollY = -1f
    private var lastLoggedContentH = -1f
    private var keepChatAtBottomVersion = 0
    // 流式跟随开关：内容增长时自动贴底。用户上滑/按住（isDragging 且不在底部）
    // 即暂停，拖回底部或重新发消息时恢复。
    private var chatFollowStream = true
    // 流式期间确实 flush 长高过：用于识别「流刚结束」的最后一次收尾长高。
    private var chatStreamFlushed = false
    // 流式贴底轮询循环（发送时启动，流结束+宽限后自灭）。
    private var chatFollowLoop: Timer? = null
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
    // ===== 消息长按操作菜单（复制 / 追问）=====
    // 双态机（mounted → presented 一拍后翻转，R4/R5），与 Drawer/CardSheet 同款。
    private var messageActionMounted: Boolean by observable(false)
    private var messageActionPresented: Boolean by observable(false)
    private var messageActionX: Float by observable(0f)
    private var messageActionY: Float by observable(0f)
    // 用户消息只提供复制；AI 消息追加「追问」。
    private var messageActionFollowUp: Boolean by observable(false)
    // 菜单内容不进 observable：只在长按事件里赋值、由点击动作直接读取。
    private var messageActionText = ""
    private var messageActionQuote = ""
    private var messageActionVersion = 0
    // 每条消息气泡的 selectable 容器 ref（vfor 下必须按 messageId 分键，
    // 单 ref 会被最后挂载的消息覆盖）。生命周期与页面一致，条目级泄漏可忽略。
    private val messageSelectionRefs = mutableMapOf<String, ViewRef<DivView>>()
    // 当前选择会话的落点（页面绝对坐标），selectEnd 弹菜单时复用定位。
    private var selectionMessageId = ""
    private var selectionPageX = 0f
    private var selectionPageY = 0f
    // ===== 回答完成后的引导语 chips 双态机（R4 两帧入场）=====
    // 流结束 → 挂载一拍后 presented 翻转；重新流式/换会话即重置。
    private var followUpsMounted: Boolean by observable(false)
    private var followUpsPresented: Boolean by observable(false)
    private var followUpsVersion = 0
    private var keyboardHeight: Float by observable(0f)
    private var drawerOpen: Boolean by observable(false)
    // Drawer double-state machine (mirrors sheetMounted/sheetPresented): vif
    // binds to drawerMounted, the transition animates on drawerPresented.
    // A vif-created view cannot animate on its own mount, so presented flips
    // one tick after mount via updateDrawerOpen (animate-binding rule 2026-09-03).
    private var drawerMounted: Boolean by observable(false)
    private var drawerPresented: Boolean by observable(false)
    private var drawerPresentationVersion = 0
    // 侧边栏横滑手势：phase 与 offsetX 同值原子化，跟手阶段直接落位，
    // 归位一次原子写入播放收敛动画（灵动岛手势同款模式）。
    private var drawerGesture: DrawerGestureMotion by observable(DrawerGestureMotion())
    private var drawerGestureStartX = 0f
    // 最近一次 move 的单步位移（非 observable）：快速短划（flick）判定的速度代理。
    private var drawerGestureLastDX = 0f
    // 原生 fling 侦察（大且快右向横滑 → 开抽屉）回调是否已注册：created 里
    // 注册一次即可（keepCallback），页面可见性由 pageVisible 守卫。
    private var drawerFlingHostRegistered = false
    private var pageVisible = false
    // 2026-09-08：行情数据模式只有"实时"一档（模拟分支已整体摘除），旧
    // liveDataMode 开关随之移除；顶部岛上的"实时"角标为常显。
    // 抽屉历史会话搜索词：drawer 的 Input 不受控，页面侧只存词 + 供 vbind 过滤；
    // 打开抽屉时重置，避免上次输入残留下次仍过滤。
    private var historySearchQuery: String by observable("")
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
    // ===== 术语灵动岛（与股票行情岛同体系，内容层分流、对比会话互斥）=====
    // 非空 = 岛当前承载该术语的讲解卡；长按蓝色术语高亮进入，与行情卡共用
    // 同一条形变/手势管线（AppChrome.StockIsland 的 termEntry 分支）。
    private var islandTermKey: String by observable("")
    private var islandTermCompareLeftKey: String by observable("")
    private var islandTermCompareRightKey: String by observable("")
    private var islandTermCompareVisible: Boolean by observable(false)
    // 长按术语的进行中 key（与 pendingLongPressSymbol 平行，互不串扰），
    // 以及长按后部分 bridge 会补发的 click 的抑制词形。
    private var pendingLongPressTermKey: String = ""
    private var suppressNextTermClick: String = ""
    // 对比会话代数：退出/重建对比时自增，使在途的收起兜底定时器失效。
    private var compareExperienceVersion = 0
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
    // 意外 blur 自动恢复的预算机制。真机（HyperOS）上存在「refocus 成功 →
    // showSoftInput 被客户端取消（ImeTracker: onCancelled at
    // PHASE_CLIENT_APPLY_ANIMATION）→ 约 2ms 后焦点又被清掉」的场景，若无限制
    // 地 96ms 重试会形成约 5Hz 的 focus/blur 死循环：键盘收起即弹出、光标跳闪、
    // InputConnection 被反复打断导致退格失效。预算只在拿到「焦点真正稳定」的
    // 证据时清零：一次 grant 后在稳定窗口内未被 blur 打断（见 inputFocus），
    // 或键盘真实弹出，或用户开始输入。
    private var composerBlurRecoverAttempts = 0
    // 键盘当前是否在屏上，由 TextArea 的 keyboardHeightChange 上报。
    private var keyboardVisible: Boolean by observable(false)
    // Voice input is the composer's third state (docs/11): hold to record,
    // release to transcribe/send, slide up to cancel.
    private var voiceState: VoiceState by observable(VoiceState.IDLE)
    private var voiceCancelArmed: Boolean by observable(false)
    private var voiceElapsedSec: Float by observable(0f)
    // 滚动声波历史：初始全 0（空白），录音开始后新采样从右缘进入、
    // 向左生长——视觉上"声波从后面长出来"，而不是一开始就有基线条。
    private var voiceAmps: FloatArray by observable(FloatArray(VOICE_AMP_BARS) { 0f })
    private var voiceMicFill: Float by observable(0f)
    // 语音模式（豆包式交互，2026-09-06）：折叠栏右侧图标在"声波/键盘"间切换；
    // 开启后中间文本区整体变成"按住说话"按钮，录音不再强制展开输入栏。
    private var voiceInputMode: Boolean by observable(false)
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
    // 回到顶部悬浮按钮：mounted/presented 双态机（同 drawer 模式，R4——vif 挂载
    // 的视图首帧不播动画，挂载后一拍再翻 presented）；version 使过期回调失效。
    private var chatBackToTopMounted: Boolean by observable(false)
    private var chatBackToTopPresented: Boolean by observable(false)
    private var chatBackToTopVersion = 0
    // 程序化动画回顶进行中：暂停按钮显隐判定，避免动画过程中的中间 scroll
    // 事件（offsetY 仍很大）把按钮又弹出来。
    private var chatTopScrollAnimationVersion = 0
    private val quoteRepository get() = dependencies.quoteRepository
    private val watchlistStore get() = dependencies.watchlistStore
    private val alertStore get() = dependencies.alertStore
    private val glossaryStore get() = dependencies.glossaryStore
    private var alertPollGeneration = 0
    private val deliveredAlertBuckets = mutableSetOf<String>()
    // 数据源开关：模拟模式下行情回落 MockDataBank（原状态）；真实模式此字段不参与请求。
    private val mockQuoteProvider = MockQuoteProvider()
    private var quoteStates: ObservableList<ChatQuoteState> by observableList()
    private val requestedSymbols = mutableSetOf<String>()
    private var welcomeKeywordText: String by observable("行情")
    private var welcomeKeywordStopped: Boolean by observable(false)
    private var welcomeKeywordVersion = 0
    private var welcomeKeywordTimer: Timer? = null
    private var welcomeCursorTimer: Timer? = null
    // 轮播关键词尾部的 2px 光标：轮播存活期 550ms 翻转（1.1s step-end），停止即隐藏
    private var welcomeCursorVisible: Boolean by observable(false)
    // 入场动效只控制示例卡，不控制欢迎区本身是否存在。新会话必须在清空消息的
    // 同一帧就能画出欢迎内容；把挂载再交给一个 Timer 会让抽屉收起/页面重排与
    // 会话切换碰撞时留下白屏窗口。
    private var welcomeEntranceVisible: Boolean by observable(false)
    private var welcomeEntranceArmed = false
    // 定时回调只能检查普通版本号，不能在回调中读取 observable。
    private var welcomeEntranceVersion = 0
    // 与欢迎词轮播共用 Kuikly Timer 调度路径；每次只保留一个入场阶段任务。
    private var welcomeEntranceTimer: Timer? = null
    // 入场兜底（独立于 welcomeEntranceTimer）：ref/Timer 链路任一环丢失时，
    // 示例卡不得停留在 opacity 0 的空首页。600ms 一次性，版本号守卫。
    private var welcomeEntranceSafetyTimer: Timer? = null
    private val welcomeReducedMotion by lazy { platformPrefersReducedMotion() }
    private var pendingRouteQuestion: String = ""
    private var pendingRouteFocusNote: String = ""
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
        pendingRouteQuestion = pagerData.params.optString("question")
        pendingRouteFocusNote = pagerData.params.optString("focusNote")
        StockCardRenderers.ensureRegistered()
        registerDrawerFlingHostIfNeeded()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        pageVisible = false
        alertPollGeneration++
        // 只暂停、不上锁：lock=true 会把 welcomeKeywordStopped 永久置真，
        // 于是从详情页返回或应用回到前台后 startWelcomeKeywordLoopIfNeeded()
        // 会被首行的 stopped 判断直接挡掉，轮播再也不会恢复。
        // 上锁只留给「用户已经开始对话」的路径（提交输入、选示例卡、打开历史会话）。
        stopWelcomeKeywordLoop(lock = false)
        stopComposerRimFlow()
        // If the stock detail route is covering this page, JS state may
        // already read as idle while the native view is still waiting for the
        // collapsed-frame write. Force the write unconditionally.
        // 交接遮罩期间例外：详情页整页淡入还没完成，全屏玻璃帧是它的底，
        // 提前归位会在淡入的前半段透出聊天页。
        if (islandDetailRouteActive && !islandHandoffMaskActive) {
            islandMounted = false
            forceIslandCollapsedForDetailRoute()
        }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        pageVisible = true
        if (islandDetailRouteActive) scheduleIslandDetailReturnReset()
        viewModel.refreshConfigStatus()
        islandWatchlisted = watchlistStore.contains(islandSymbol)
        // Preload the island quote so the morph opens with data in place.
        requestQuote(islandSymbol)
        startAlertPolling()
        startWelcomeKeywordLoopIfNeeded()
        startComposerRimFlow()
        scheduleWelcomeEntranceSafety()
        consumeRouteQuestionIfNeeded()
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
                        paddingBottom(190f + page.pagerData.safeAreaInsets.bottom)
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
                        longPress { page.chatFollowStream = false }
                        // 点击列表非输入栏区域：展开态先收键盘，键盘已收起才回到默认态
                        click { page.handleOutsideTap() }
                    }
                    // activeSessionId 是会话树的重建键。新建对话时先替换整棵会话
                    // 内容树，再由内部 vif/vfor 画空态或消息；这是应用内等价于用户
                    // 手动退出、重新进入页面的恢复动作，规避原生列表复用偶发残留。
                    vbind({ page.viewModel.activeSessionId }) {
                        // 仅由消息是否为空决定欢迎区的存在。不要把这里再绑定到动效的
                        // mounted 状态：内容可见性必须独立于任何异步动画调度。
                        vif({ page.viewModel.messages.isEmpty() }) {
                            WelcomeSection(
                            theme = page.theme,
                            // 取值闭包，不能在这里直接读 observable：vif 体只执行一次，
                            // 读到的快照不会建立依赖，attr 不重跑、animate() 也拿不到
                            // observablePropertyKey，整块动效会静默失效。
                            rotatingKeyword = { page.welcomeKeywordText },
                            cursorVisible = { page.welcomeCursorVisible },
                            entranceVisible = { page.welcomeEntranceVisible },
                            onMounted = page::welcomeDidMount,
                            marketTabSelected = { page.welcomeMarketTabSelected },
                            onOpenMarket = { page.handleWelcomeMarketTap() },
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
                                    if (it == page.suppressNextTermClick) {
                                        page.suppressNextTermClick = ""
                                    } else {
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
                    onToggleIslandWatchlist = { symbol -> page.toggleIslandWatchlist(symbol) },
                    onOpenIslandCompare = { page.openIslandComparePanel() },
                    onClearIslandCompare = { page.clearIslandCompare() },
                    onMenu = { page.updateDrawerOpen(!page.drawerOpen) },
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
                                bottom = 86f + page.pagerData.safeAreaInsets.bottom,
                            )
                            size(40f, 40f)
                            allCenter()
                            // 玻璃高光描边：GlassBackdrop 不带描边，细 rim 由容器补
                            // （与 peek 胶囊 resolved.stroke 对齐）。
                            border(Border(1f, BorderStyle.SOLID, Color(0xFFFFFF, 0.35f)))
                            // 与输入栏胶囊同款悬浮投影，让按钮浮在列表上方。
                            boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
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
                    View {
                        attr {
                            absolutePosition(left = 0f, top = 0f)
                            size(page.pagerData.pageViewWidth, page.pagerData.pageViewHeight)
                        }
                        event { click { page.dismissMessageActionMenu() } }
                    }
                    View {
                        attr {
                            val menuWidth = if (page.messageActionFollowUp) 174f else 96f
                            absolutePosition(
                                left = (page.messageActionX - 20f).coerceIn(
                                    12f,
                                    (page.pagerData.pageViewWidth - menuWidth - 12f).coerceAtLeast(12f),
                                ),
                                // 近底部翻到手指上方，避免被输入栏遮住。
                                top = if (page.messageActionY + 150f > page.pagerData.pageViewHeight - 160f) {
                                    page.messageActionY - 96f
                                } else {
                                    page.messageActionY + 14f
                                },
                            )
                            height(44f)
                            borderRadius(14f)
                            boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
                            border(Border(1f, BorderStyle.SOLID, Color(0xFFFFFF, 0.35f)))
                            opacity(if (page.messageActionPresented) 1f else 0f)
                            transform(scale = if (page.messageActionPresented) Scale(1f, 1f) else Scale(0.9f, 0.9f))
                            animate(Animation.easeOut(0.2f), page.messageActionPresented)
                        }
                        GlassBackdrop(page.theme.glass.peek, page.glassRenderer)
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                height(44f)
                                paddingLeft(5f)
                                paddingRight(5f)
                            }
                            View {
                                attr { height(34f); paddingLeft(14f); paddingRight(14f); allCenter() }
                                Text { attr { text("复制"); fontSize(13f); color(page.theme.textPrimary) } }
                                event { click { page.copyMessageToPasteboard() } }
                            }
                            if (page.messageActionFollowUp) {
                                View {
                                    attr { width(1f); height(18f); backgroundColor(page.theme.textTertiary.opacity(0.25f)) }
                                }
                                View {
                                    attr { height(34f); paddingLeft(14f); paddingRight(14f); allCenter() }
                                    Text {
                                        attr { text("追问"); fontSize(13f); fontWeightMedium(); color(page.theme.brand) }
                                    }
                                    event { click { page.quoteMessageIntoComposer() } }
                                }
                            }
                        }
                    }
                }
                val composerSheetMaterial = page.theme.glass.sheet
                val composerGlassRenderer = page.glassRenderer
                val composerDragBackground = page.theme.brandSoft
                View {
                    attr {
                        // Floating capsule composer on a solid page-coloured base:
                        // the blank area around/below the capsule no longer shows
                        // scrolled content through.  The extra 3dp top padding
                        // hosts a feather strip that softens the junction,
                        // mirroring the top chrome.
                        absolutePosition(bottom = 0f, left = 0f, right = 0f)
                        paddingTop(if (COMPOSER_ISOLATION_TEST) 0f else 3f)
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
                            // 悬浮感：与顶部灵动岛胶囊同款阴影（AppChrome 灵动岛
                            // 0/8/22/0.16），让输入栏像浮在列表上方而不是贴底。
                            boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
                            // 对比试验：输入栏使用纯白背景，不叠加玻璃表皮或 Blur。
                            // entityDragActive 仍是本 attr 的唯一动画驱动。
                            backgroundColor(
                                if (page.entityDragActive && page.entityDropTarget == EntityDropTarget.COMPOSER) composerDragBackground
                                else Color(0xFFFFFFFF)
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
                            // 联想面板打开时收起"最近标的"横条：面板本身已含"最近"数据源候选，
                            // 两条叠着显示既重复又顶高输入栏。
                            vif({ page.isComposerVisuallyExpanded() && page.assistantPanel == AssistantPanel.NONE }) {
                                RecentSymbolRow(page.theme) { text -> page.injectQuestion(text) }
                            }
                            vif({ page.isComposerVisuallyExpanded() && page.deepContextVersion >= 0 && page.deepContextNotes.isNotEmpty() }) {
                                page.renderContextNoteBar(this)
                            }
                            vif({ page.isComposerVisuallyExpanded() && page.commandValidationMessage.isNotEmpty() }) {
                                page.renderCommandValidationBar(this)
                            }
                            vif({ page.isComposerVisuallyExpanded() && page.assistantPanel != AssistantPanel.NONE }) {
                                page.renderAssistantPanel(this)
                            }
                            vif({ page.isComposerVisuallyExpanded() && page.inputPanel == InputPanel.MEDIA }) {
                                MediaInputRow(page.theme) { action -> page.handleMediaAction(action) }
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
                                            LineIconPlus(color = Color(0xFF000000), size = 24f)
                                        }
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
                                                backgroundColor(Color(0xFFFFFFFF))
                                            }
                                            vif({ page.voiceState == VoiceState.IDLE }) {
                                                Text {
                                                    attr {
                                                        text("按住 说话")
                                                        fontSize(15f)
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
                                                LineIconAudioLines(color = Color(0xFF000000), size = 27f)
                                            }
                                            vif({ page.voiceInputMode }) {
                                                LineIconKeyboard(color = Color(0xFF000000), size = 27f)
                                            }
                                            event { click { page.toggleVoiceInputMode() } }
                                        }
                                    }
                                }
                            }
                            vif({ page.isComposerVisuallyExpanded() }) {
                                View { attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
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
                                                    fontSize(18f)
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
                                                    fontSize(18f)
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
                                            // audio-lines），白底输入栏下同折叠态用黑色。
                                            LineIconAudioLines(color = Color(0xFF000000), size = 26f)
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
                                                color = if (page.inputPanel == InputPanel.MEDIA) page.theme.brand else page.theme.textSecondary,
                                                size = 22f,
                                            )
                                            event { click { page.toggleMediaPanel() } }
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
                                                        fontSize(18f)
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
                    event { click { } }
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
                    event { click { } }
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
                            !page.sheetMounted && !page.islandExpanded && !page.entityDragActive
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
                    onSettings = { page.updateDrawerOpen(false); page.openPage(Routes.API_CONFIG) },
                )
            }
        }
    }

    /**
     * Drawer open/close with the CardSheet presentation pattern:
     * open  = mount now, flip presented next tick so the entrance animates;
     * close = un-present now (plays the exit), unmount after 280ms guarded by
     * a version counter so rapid toggles never leave a stale timer behind.
     */
    private fun updateDrawerOpen(open: Boolean) {
        // 展开抽屉前必须先 blur 收键盘：原生键盘 z 序压过所有 Kuikly 视图，
        // 不收会浮在抽屉面板之上。blurComposer 解除焦点锁，避免意外 blur
        // 自动恢复把键盘又抢回来。
        if (open) blurComposer()
        // 程序化开合终结任何进行中的手势态，避免 SETTLING 分支抢走 transform 控制权
        // （已是 IDLE 缺省值时等值写入不触发通知，无副作用）。
        drawerGesture = DrawerGestureMotion()
        val wasOpen = drawerOpen
        val version = ++drawerPresentationVersion
        drawerOpen = open
        if (open) {
            drawerMounted = true
            drawerPresented = false
            // 重置历史会话搜索词（抽屉 Input 不受控，词存这里）。
            historySearchQuery = ""
            setTimeout(0) {
                if (drawerPresentationVersion == version) drawerPresented = true
            }
            // 点按展开：震动在**完全展开那一刻**（easeOut 0.375s + buffer），
            // version + 状态双守卫，被抢占时静默退出。
            if (!wasOpen) {
                setTimeout(395) {
                    if (version == drawerPresentationVersion && drawerOpen && drawerPresented) {
                        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                    }
                }
            }
        } else {
            drawerPresented = false
            // 卸载定时随动画时长 +25%：0.28 → 0.35s。
            setTimeout(350) {
                if (drawerPresentationVersion == version && !drawerPresented) {
                    drawerMounted = false
                }
            }
            // 点按收起：震动在**完全收起那一刻**（easeIn 0.275s + buffer）。
            if (wasOpen) {
                setTimeout(300) {
                    if (version == drawerPresentationVersion && !drawerOpen && !drawerPresented) {
                        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                    }
                }
            }
        }
    }

    /**
     * 侧边栏横滑手势：开→左拖跟手收起，关→左缘右拖跟手展开。
     * pageX 为页面坐标——面板自身随 transform 平移，local x 会抵消手指位移
     * （灵动岛 handle 注释同款坑）。
     */
    private fun handleDrawerPan(state: String, x: Float) {
        when (state) {
            "start" -> {
                if (drawerGesture.phase != DrawerGesturePhase.IDLE) return
                drawerGestureStartX = x
                drawerGestureLastDX = 0f
                val base = if (drawerOpen) 0f else -292f
                if (!drawerOpen) {
                    // 关→开：先挂载，让面板从指下跟手滑出；同时收键盘
                    //（原生键盘 z 序压过抽屉，手势展开同样要 blur）。
                    blurComposer()
                    drawerMounted = true
                }
                drawerGesture = DrawerGestureMotion(DrawerGesturePhase.DRAGGING, base)
            }
            "move" -> {
                if (drawerGesture.phase != DrawerGesturePhase.DRAGGING) return
                val raw =
                    if (drawerOpen) x - drawerGestureStartX else -292f + (x - drawerGestureStartX)
                val next = raw.coerceIn(-292f, 0f)
                // 单步位移作速度代理：pan 不带 velocity，move 事件帧间隔近似恒定，
                // 最后一步位移量大 = 手指正在快速滑动（RowGestureLayer 同款约束下的替代方案）。
                drawerGestureLastDX = next - drawerGesture.offsetX
                drawerGesture = DrawerGestureMotion(DrawerGesturePhase.DRAGGING, next)
            }
            "end", "cancel" -> {
                if (drawerGesture.phase != DrawerGesturePhase.DRAGGING) return
                val base = if (drawerOpen) 0f else -292f
                val travel = drawerGesture.offsetX - base
                // 快速短划判定（展开方向保留 9dp 速度代理）。收起方向（用户
                // 决策 2026-09-08）：不再要求速度/半程阈值——只要检测到向左的
                // 趋势（最后一步向左，或整体位置越过起点向左）松手即直接收回，
                // 不再出现"没到阈值回弹"的情况。
                val flickTowardClose = drawerOpen &&
                    (drawerGestureLastDX < 0f || drawerGesture.offsetX < 0f)
                val flickTowardOpen = !drawerOpen && drawerGestureLastDX >= 9f
                when {
                    // 手势被打断：回原位也要归位（有动画 + 震感）。
                    state == "cancel" -> settleDrawerGesture(open = drawerOpen)
                    // 纯误触（完全没往任何方向位移）静默还原，不播动画不震动；
                    // 收起方向因 flickTowardClose 放宽，任何向左趋势都到不了这里。
                    kotlin.math.abs(travel) < 8f && !flickTowardClose && !flickTowardOpen ->
                        cancelDrawerGesture()
                    else -> {
                        val progress = (drawerGesture.offsetX + 292f) / 292f
                        val settleOpen = if (drawerOpen) {
                            progress >= 0.5f && !flickTowardClose
                        } else {
                            progress >= 0.5f || flickTowardOpen
                        }
                        settleDrawerGesture(open = settleOpen)
                    }
                }
            }
        }
    }

    /** 误触还原：不播收敛动画、不震动。 */
    private fun cancelDrawerGesture() {
        drawerGesture = DrawerGestureMotion()
        if (!drawerOpen) drawerMounted = false // 关态下手势只临时挂载了面板，直接卸载
    }

    /**
     * 手势归位：一次原子写入（SETTLING + 目标偏移）让面板从手指最后一帧动画到端点。
     * 展开先快后慢（easeOut 0.375s），收起先慢后快（easeIn 0.30s）——时长 +25%
     * （用户决策 2026-09-05）。震动在**松手瞬间**触发（动画终点触发收起时震感太晚）。
     */
    private fun settleDrawerGesture(open: Boolean) {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        drawerGesture = DrawerGestureMotion(
            DrawerGesturePhase.SETTLING,
            if (open) 0f else -292f,
        )
        val version = ++drawerPresentationVersion
        drawerOpen = open
        drawerMounted = true
        // 面板挂载发生在手势 start，这里无需 setTimeout(0) 预挂载延迟。
        drawerPresented = open
        if (!open) {
            // 卸载定时随动画时长 +25%：0.30 → 0.375s。
            setTimeout(375) {
                if (version == drawerPresentationVersion && !drawerPresented) {
                    drawerMounted = false
                }
            }
        }
        // 收敛动画结束后回到常规态，后续菜单按钮开合走原 presented 路径。
        // phase 守卫保证被程序化开合（updateDrawerOpen 清手势态）抢占时静默退出。
        setTimeout(440) {
            if (drawerGesture.phase == DrawerGesturePhase.SETTLING) {
                drawerGesture = DrawerGestureMotion()
            }
        }
    }

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
     * 原生 fling 侦察命中：整页任意位置的「大且快」右向横滑。原生层只观测
     * 不消费事件，做不了跟手预览，因此直接程序化展开（走 updateDrawerOpen
     * 的双态入场 + blur 收键盘）。抽屉/卡片弹层/实体拖拽/灵动岛展开任一
     * 活跃时忽略，避免手势叠加。
     */
    private fun handleNativeDrawerFling() {
        if (!pageVisible) return
        if (drawerOpen || drawerMounted || sheetMounted || entityDragActive || islandExpanded) return
        updateDrawerOpen(true)
    }

    private fun startNewChat() {
        updateDrawerOpen(false)
        stopWelcomeKeywordLoop(lock = false)
        keepChatAtBottomVersion = 0
        chatContentHeight = 0f
        chatFollowStream = true
        chatStreamFlushed = false
        chatFollowLoop?.cancel()
        chatFollowLoop = null
        resetSessionUiState()
        setComposerText("")
        // 先把欢迎页置为可见终态，再清空消息触发 vif 挂载。这样即使 ref、动画帧或
        // 抽屉收起的回调丢失，新会话首帧也不会是空白。
        resetWelcomeForEmptySession()
        viewModel.startNewChat()
        startWelcomeKeywordLoopIfNeeded()
        resetChatScrollToTop()
    }

    // 2026-09-03 产品决策：全量走 FULL 档，BRIEF 档（老用户简版欢迎语）暂不启用。
    // 恢复 BRIEF 只需把该开关改回 true，原有降级逻辑全部保留。
    private val welcomeBriefEnabled = false

    private fun welcomeMode(): WelcomeMode =
        if (welcomeBriefEnabled && viewModel.hasSessionHistory) WelcomeMode.BRIEF else WelcomeMode.FULL

    private fun chooseWelcomeStarter(starter: WelcomeStarter) {
        markWelcomeStarterUsed(starter)
        stopWelcomeKeywordLoop(lock = true)
        injectQuestion(starter.question)
    }

    private fun readWelcomeUsedStarterKinds(): Set<String> =
        welcomeStorage.getString(WELCOME_USED_KIND_KEY)
            .split('|')
            .filter { it.isNotBlank() }
            .toSet()

    private fun markWelcomeStarterUsed(starter: WelcomeStarter) {
        val allKinds = defaultWelcomeStarters().map { it.kind.name }.toSet()
        val current = readWelcomeUsedStarterKinds()
        val next = if (current.containsAll(allKinds)) {
            mutableSetOf()
        } else {
            current.toMutableSet()
        }
        next.add(starter.kind.name)
        welcomeStorage.setString(WELCOME_USED_KIND_KEY, next.joinToString("|"))
    }

    private fun startWelcomeKeywordLoopIfNeeded() {
        if (welcomeKeywordStopped || welcomeMode() != WelcomeMode.FULL || viewModel.messages.isNotEmpty()) return
        if (welcomeReducedMotion) {
            // 「减弱动态效果」开启：不轮播、不闪烁，静态显示默认词。
            welcomeKeywordVersion++
            welcomeKeywordText = welcomeKeywords.first()
            welcomeCursorVisible = false
            return
        }
        val version = ++welcomeKeywordVersion
        welcomeKeywordText = welcomeKeywords.first()
        welcomeCursorVisible = true
        scheduleWelcomeKeywordTyping(version, wordIndex = 0, length = welcomeKeywords.first().length)
        scheduleWelcomeCursorBlink(version)
    }

    private fun stopWelcomeKeywordLoop(lock: Boolean = false) {
        if (lock) welcomeKeywordStopped = true
        welcomeKeywordVersion++
        welcomeKeywordTimer?.cancel()
        welcomeKeywordTimer = null
        welcomeCursorTimer?.cancel()
        welcomeCursorTimer = null
        welcomeKeywordText = welcomeKeywords.first()
        welcomeCursorVisible = false
    }

    private fun welcomeKeywordShouldRun(version: Int): Boolean =
        version == welcomeKeywordVersion &&
            !welcomeKeywordStopped &&
            !isWillDestroy() &&
            welcomeMode() == WelcomeMode.FULL &&
            viewModel.messages.isEmpty()

    private fun scheduleWelcomeKeywordTyping(version: Int, wordIndex: Int, length: Int) {
        if (!welcomeKeywordShouldRun(version)) return
        val word = welcomeKeywords[wordIndex % welcomeKeywords.size]
        welcomeKeywordText = word.take(length)
        if (length < word.length) {
            scheduleWelcomeKeywordStep(version, 200) {
                scheduleWelcomeKeywordTyping(version, wordIndex, length + 1)
            }
        } else {
            scheduleWelcomeKeywordStep(version, 1200) {
                scheduleWelcomeKeywordDeleting(version, wordIndex, word.length - 1)
            }
        }
    }

    private fun scheduleWelcomeKeywordDeleting(version: Int, wordIndex: Int, length: Int) {
        if (!welcomeKeywordShouldRun(version)) return
        val word = welcomeKeywords[wordIndex % welcomeKeywords.size]
        welcomeKeywordText = word.take(length)
        if (length > 0) {
            scheduleWelcomeKeywordStep(version, 120) {
                scheduleWelcomeKeywordDeleting(version, wordIndex, length - 1)
            }
        } else {
            scheduleWelcomeKeywordStep(version, 300) {
                scheduleWelcomeKeywordTyping(version, wordIndex + 1, 1)
            }
        }
    }

    private fun scheduleWelcomeKeywordStep(version: Int, delay: Int, block: () -> Unit) {
        welcomeKeywordTimer?.cancel()
        val timer = Timer()
        welcomeKeywordTimer = timer
        timer.schedule(delay, delay.coerceAtLeast(16)) {
            timer.cancel()
            if (welcomeKeywordTimer === timer) welcomeKeywordTimer = null
            if (welcomeKeywordShouldRun(version)) block()
        }
    }

    /** 光标 1.1s step-end 闪烁：每 550ms 翻转一次，与轮播共用版本号，随轮播一起停止。 */
    private fun scheduleWelcomeCursorBlink(version: Int) {
        if (!welcomeKeywordShouldRun(version)) return
        welcomeCursorTimer?.cancel()
        val timer = Timer()
        welcomeCursorTimer = timer
        timer.schedule(550, 550) {
            if (!welcomeKeywordShouldRun(version)) {
                timer.cancel()
                if (welcomeCursorTimer === timer) welcomeCursorTimer = null
                return@schedule
            }
            welcomeCursorVisible = !welcomeCursorVisible
        }
    }

    /**
     * 首屏入场：示例卡 94ms 阶梯上滑（时长 0.375s，下一张在前一张 25% 进度时启动）。
     * 每个空会话实例只播一次；从详情页返回不重播；减弱动态时直接落到终态。
     */
    private fun welcomeDidMount() {
        if (welcomeEntranceArmed) return
        welcomeEntranceArmed = true
        if (welcomeReducedMotion) {
            welcomeEntranceVisible = true
            return
        }
        // 该状态只在欢迎区已挂载后翻转；attr 内会读它并将它绑定为动画驱动。
        welcomeEntranceVisible = false
        val version = ++welcomeEntranceVersion
        // 轮播已验证的 Timer 调度路径：约两帧后再呈现，保证 vif 新建卡片的初态
        // 已实际下发给原生视图，随后由 attr 中的 visible 读取驱动 animate()。
        scheduleWelcomeEntranceStep(version, 32) {
            if (welcomeEntranceArmed) {
                welcomeEntranceVisible = true
            }
        }
        // ref 回调已递增版本号，reset 时挂的兜底按旧版本失效了；这里按新版本重挂，
        // 补上「ref 已回调但 32ms 呈现 Timer 丢失」的窗口，否则 visible 永远停在
        // false、示例卡全部停在 opacity 0（聊天中新建会话偶发无欢迎语的根因之一）。
        scheduleWelcomeEntranceSafety()
    }

    // 欢迎区「看市场」胶囊：点击后滑块滑到右半格，动画结束震动再跳转市场页
    // （R4/R5：滑块常驻挂载，attr 内无条件注册 easeOut，翻转周期消费上轮注册）。
    private var welcomeMarketTabSelected: Boolean by observable(false)

    private fun handleWelcomeMarketTap() {
        if (welcomeMarketTabSelected) return // 动画/跳转期间防重复触发
        welcomeMarketTabSelected = true
        // 滑动 220ms → 到位震动 → 市场页才淡入；跳转后复位滑块供返回时显示默认态。
        setTimeout(240) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
            openPage(Routes.MARKET)
        }
        setTimeout(420) {
            welcomeMarketTabSelected = false
        }
    }

    private fun resetWelcomeForEmptySession() {
        welcomeKeywordStopped = false
        // 新建会话优先保证内容可见，欢迎卡片不必为了入场动画经历一帧 opacity 0。
        // armed 同时阻止新挂载的 ref 把已经可见的内容又重置为隐藏态。
        welcomeEntranceArmed = true
        ++welcomeEntranceVersion
        welcomeEntranceTimer?.cancel()
        welcomeEntranceTimer = null
        welcomeEntranceVisible = true
        scheduleWelcomeEntranceSafety()
    }

    /**
     * 入场兜底：600ms 后欢迎区仍未呈现则直接落终态。
     * 覆盖冷启动与新会话两条路径下断链的任意一环：
     * - ref 未回调 / 32ms 呈现 Timer 丢失 → visible 停在 false，这里翻转
     *   呈现；此时卡片 attr 已注册 easeOut，翻转仍走正常入场动画。
     * 正常链路（ref → 32ms 呈现）先完成时，版本号已前移，本回调静默退出。
     */
    private fun scheduleWelcomeEntranceSafety() {
        welcomeEntranceSafetyTimer?.cancel()
        val version = welcomeEntranceVersion
        val timer = Timer()
        welcomeEntranceSafetyTimer = timer
        timer.schedule(600, 600) {
            timer.cancel()
            if (welcomeEntranceSafetyTimer === timer) welcomeEntranceSafetyTimer = null
            if (version == welcomeEntranceVersion && !isWillDestroy() &&
                viewModel.messages.isEmpty() && !welcomeEntranceVisible
            ) {
                // armed 防 ref 迟到后把 visible 打回 false；欢迎区的挂载只由
                // messages.isEmpty() 管理，visible 仅负责卡片终态。
                welcomeEntranceArmed = true
                welcomeEntranceVisible = true
            }
        }
    }

    /** 与欢迎词轮播相同的单次 Timer 调度；回调不读取任何 observable。 */
    private fun scheduleWelcomeEntranceStep(version: Int, delay: Int, block: () -> Unit) {
        welcomeEntranceTimer?.cancel()
        val timer = Timer()
        welcomeEntranceTimer = timer
        timer.schedule(delay, delay.coerceAtLeast(16)) {
            timer.cancel()
            if (welcomeEntranceTimer === timer) welcomeEntranceTimer = null
            if (version == welcomeEntranceVersion && !isWillDestroy()) block()
        }
    }

    private fun openHistorySession(sessionId: String) {
        stopWelcomeKeywordLoop(lock = true)
        viewModel.openSession(sessionId)
        resetSessionUiState()
        reloadQuotesForCurrentSession()
        setComposerText("")
        keepChatAtBottomTemporarily()
    }

    private fun submitInput() {
        stopWelcomeKeywordLoop(lock = true)
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
                        resetWelcomeForEmptySession()
                        startWelcomeKeywordLoopIfNeeded()
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

    /** 默认态 → 输入态。requestFocus 为真时同时把键盘拉起来。 */
    private fun expandComposer(requestFocus: Boolean = false) {
        val wasExpanded = composerExpanded
        KLog.i(COMPOSER_LOG_TAG, "expandComposer requestFocus=$requestFocus wasExpanded=$wasExpanded ref=${inputRef?.view != null}")
        // Kuikly observable 的同值赋值仍可能触发一次 render/layout commit；而
        // EditText 的 focus 回调会再次进入这里。状态转换必须幂等，否则恢复焦点
        // 后的第二次同值提交仍会把原生焦点清掉。
        if (!composerExpanded) {
            composerExpanded = true
            // 进入文字输入即退出语音模式：键盘图标切回声波图标。
            voiceInputMode = false
            // 展开态图标经两帧翻转错峰入场（R4）。
            scheduleComposerChromePresentation(true)
        }
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
        // 预算检查：连续恢复次数用尽前不再自动 refocus，防止 focus/blur 死循环。
        // 预算由一次未被 blur 打断的稳定焦点会话重置（见 inputFocus），不能用
        // 墙钟时间判断：commonMain 在各运行端没有统一、可靠的单调时钟。
        if (composerBlurRecoverAttempts >= COMPOSER_BLUR_RECOVER_MAX_ATTEMPTS) {
            KLog.i(COMPOSER_LOG_TAG, "blurRecoverBudgetExhausted attempts=$composerBlurRecoverAttempts")
            return
        }
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
            composerBlurRecoverAttempts++
            KLog.i(COMPOSER_LOG_TAG, "recoverUnexpectedBlur version=$blurVersion attempts=$composerBlurRecoverAttempts")
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
        composerBlurRecoverAttempts = 0
        composerExpanded = false
        // 折叠态图标此刻挂载：先保持 presented=true 让它们落到隐藏态并注册
        // 入场动画，翻转回 false 后对称回放（R4/R5）。
        scheduleComposerChromePresentation(false)
        inputPanel = InputPanel.NONE
        clearActiveCommand()
        closeAssistantPanel()
        cancelVoiceSession()
    }

    /** Reads observable state inside each vif predicate so Kuikly can re-render it. */
    private fun isComposerExpanded(): Boolean =
        composerExpanded || inputPanel != InputPanel.NONE || keyboardVisible || keyboardHeight > 0f || voiceState != VoiceState.IDLE

    /**
     * 输入栏的"视觉展开"态：与 [isComposerExpanded] 的唯一区别是排除录音态。
     * 语音模式下按住说话发生在折叠栏上（豆包式），录音期间折叠图标必须保持
     * 挂载、展开态图标行不得插入，否则中段布局会在按住瞬间跳高。
     */
    private fun isComposerVisuallyExpanded(): Boolean =
        composerExpanded || inputPanel != InputPanel.NONE || keyboardVisible || keyboardHeight > 0f

    /**
     * 点击非输入栏区域的两段式收起（用户要求恢复的原逻辑）：
     * - 键盘在屏：这一次点击只 blur 收起键盘，输入栏保持展开；
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

    private fun isVoiceBusy(): Boolean = voiceState != VoiceState.IDLE

    /**
     * 折叠栏右侧语音/键盘切换（豆包式）。开启后中间文本区变成"按住说话"；
     * 只在折叠态可达（图标仅折叠态挂载），录音中不可切。
     */
    private fun toggleVoiceInputMode() {
        if (voiceState != VoiceState.IDLE) return
        voiceInputMode = !voiceInputMode
        KLog.i(COMPOSER_LOG_TAG, "toggleVoiceInputMode -> $voiceInputMode")
        if (voiceInputMode) blurComposer()
    }

    /**
     * 展开态点击语音图标：直接折叠并进入语音模式，一步呈现"按住说话"样式。
     * collapseComposer 内部会补 presented false 翻转（R4），折叠图标正常入场。
     */
    private fun enterVoiceModeFromExpanded() {
        if (voiceState != VoiceState.IDLE) return
        voiceInputMode = true
        KLog.i(COMPOSER_LOG_TAG, "enterVoiceModeFromExpanded")
        // 先 blur 收键盘再折叠：否则键盘仍挂着（keyboardHeight>0），
        // isComposerVisuallyExpanded 保持 true，输入栏看起来没有收起。
        blurComposer()
        collapseComposer()
    }

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
        if (!voiceInputMode) {
            // 语音模式下录音发生在折叠栏，不再强制展开（豆包式）。此分支仅为
            // 兜底保留：录音入口现全部位于语音模式中段按钮上。
            composerExpanded = true
            // 从折叠态长按语音时展开态图标为新挂载，同样走两帧入场（R4）。
            if (!voiceSourceExpanded) scheduleComposerChromePresentation(true)
        }
        inputPanel = InputPanel.NONE
        closeAssistantPanel()
        commandValidationMessage = ""
        voiceCancelArmed = false
        voiceElapsedSec = 0f
        voiceAmps = FloatArray(VOICE_AMP_BARS) { 0f }
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

        // 滚动声波（豆包式）：新采样从右缘进入，历史样本整体左移一格
        // （节奏由桥接层上报频率决定，安卓侧 ~35ms/格），绘制上即"声波
        // 从右向左走过"。无 4f 基线：静音样本高度为 0（空白），只有真实
        // 声音才长出条目；新入条目做非对称平滑防抖。
        val previous = voiceAmps
        val next = FloatArray(previous.size)
        for (i in 0 until next.size - 1) next[i] = previous[i + 1]
        val target = 28f * shaped
        val last = previous.getOrNull(next.size - 1) ?: 0f
        next[next.size - 1] = (last + (target - last) * 0.6f).coerceIn(0f, 26f)
        voiceAmps = next
    }

    private fun finishVoiceSession() {
        if (voiceState != VoiceState.RECORDING) return
        voiceClockTimer?.cancel()
        voiceClockTimer = null
        voiceCancelArmed = false
        voiceState = VoiceState.TRANSCRIBING
        voiceAmps = FloatArray(VOICE_AMP_BARS) { 0f }
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
        voiceAmps = FloatArray(VOICE_AMP_BARS) { 0f }
        voiceMicFill = 0f
    }

    private fun restoreAfterVoiceSession() {
        stopVoiceUi()
        if (voiceInputMode) {
            // 语音模式：录音全程发生在折叠栏，收尾保持折叠 + 语音模式即可
            // （松手发送后停留在"按住说话"，与豆包一致）。presented 本就是
            // false，无需 chrome 翻转。
            composerExpanded = false
            voiceSourceExpanded = false
            return
        }
        if (!voiceSourceExpanded && viewModel.inputText.isBlank()) {
            composerExpanded = false
            // 语音收起路径不走 collapseComposer，必须在这里补两帧翻转：此刻
            // presented 仍是录音展开时的 true，折叠态图标以隐藏态挂载并注册了
            // 入场动画，缺了这次翻转它们会永远停在 opacity 0（R4/R5）。
            scheduleComposerChromePresentation(false)
        } else {
            composerExpanded = true
        }
        voiceSourceExpanded = false
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

    private fun renderComposerTextArea(container: ViewContainer<*, *>, isolated: Boolean = false) {
        if (isolated) {
            // 与 GlobalSearchPage 保持同一类原生 TextArea 配置：不持有 ref、
            // 不命令式回写编辑态、不监听焦点/选区/键盘高度，也不修改父级布局。
            // 垂直居中同 GlobalSearchPage：盒子收缩到单行内容高度，minHeight 兜空态。
            container.TextArea {
                attr {
                    flex(1f)
                    fontSize(14f)
                    lineHeight(21f)
                    minHeight(21f)
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
                fontSize(14f)
                lineHeight(21f)
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
                // 但折叠态必须保留单行高度下限（21f = lineHeight）：空文本时原生
                // 内容高度为 0，盒子归零会导致占位符不可见、点击完全落不到
                // EditText 上（冒泡到输入栏根节点被吞）。有文字时 intrinsic 高度
                // 优先，minHeight 只兜空态，单行视觉不变。
                minHeight(
                    when {
                        // 语音模式：压到 0 高给"按住说话"让位（opacity 0 已隐藏，
                        // 原生 EditText z 序在 Kuikly 视图之上，白底覆盖层盖不住，
                        // 只有整个盒子退出布局流才能让文字位完全让出来）。
                        this@ChatPage.voiceInputMode -> 0f
                        !this@ChatPage.isComposerExpanded() -> 21f
                        this@ChatPage.assistantPanel != AssistantPanel.NONE -> 0f
                        else -> 40f
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
                    if (!this@ChatPage.composerExpanded) this@ChatPage.expandComposer()
                }
                textDidChange(isSyncEdit = true) {
                    KLog.d(COMPOSER_LOG_TAG, "EV textDidChange len=${it.text.length}")
                    this@ChatPage.handleComposerTextChanged(it.text)
                }
                keyboardHeightChange {
                    KLog.i(COMPOSER_LOG_TAG, "EV keyboardHeightChange h=${it.height} dur=${it.duration}")
                    this@ChatPage.keyboardHeight = it.height
                    this@ChatPage.keyboardVisible = it.height > 0f
                    if (it.height > 0f) {
                        // 键盘真实弹出 = 焦点会话稳定，重置意外 blur 恢复预算。
                        this@ChatPage.composerBlurRecoverAttempts = 0
                        this@ChatPage.scheduleComposerFocusAfterKeyboardLayout(it.duration)
                    }
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

    private fun shouldKeepChatAtBottom(): Boolean =
        keepChatAtBottomVersion > 0 ||
            (viewModel.streamState == StreamState.STREAMING && chatFollowStream)

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
        if (viewModel.streamState == StreamState.STREAMING) {
            chatStreamFlushed = true
            resetFollowUps()
        } else if (chatStreamFlushed) {
            chatStreamFlushed = false
            // 用户流式中已上滑离开（chatFollowStream=false）则不打扰。
            if (chatFollowStream) keepChatAtBottomAfterStreamEnd()
            // 流刚结束的第一拍：延迟弹出引导语 chips，避免与贴底滚动/卡片挂载抢帧。
            scheduleFollowUpsPresentation()
        }
        if (shouldKeepChatAtBottom()) scheduleScrollChatToBottom(animated = false)
    }

    /** 引导语双态机：立即重置（新一轮流式/清屏时调用）。 */
    private fun resetFollowUps() {
        followUpsVersion++
        followUpsPresented = false
        followUpsMounted = false
    }

    /**
     * 引导语入场（R4）：回答收尾一拍后先挂载（opacity 0），再翻 presented 播
     * 淡入+上移。version 使重置/重复触发时过期回调失效。
     */
    private fun scheduleFollowUpsPresentation() {
        val version = ++followUpsVersion
        followUpsPresented = false
        followUpsMounted = false
        setTimeout(320) {
            if (version != followUpsVersion || isWillDestroy()) return@setTimeout
            followUpsMounted = true
            setTimeout(16) {
                if (version != followUpsVersion || isWillDestroy()) return@setTimeout
                followUpsPresented = true
            }
        }
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

    /** 流结束宽限贴底：只延长 keepChatAtBottomVersion 窗口，不改 chatFollowStream。 */
    private fun keepChatAtBottomAfterStreamEnd() {
        val version = ++keepChatAtBottomVersion
        scheduleScrollChatToBottom(animated = false)
        setTimeout(1500) {
            if (keepChatAtBottomVersion == version) keepChatAtBottomVersion = 0
        }
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
        // 回到顶部按钮显隐：所有 scroll 事件都判定（含惯性滚动），仅程序化
        // 动画回顶期间挂起，避免动画中间帧（offsetY 仍很大）把按钮弹回来。
        if (chatTopScrollAnimationVersion == 0) {
            val away = params.offsetY > params.viewHeight * 0.25f
            if (away != chatBackToTopMounted) setChatBackToTopVisible(away)
        }
        if (!params.isDragging) return
        chatFollowStream = params.offsetY >= params.contentHeight - params.viewHeight - 80f
    }

    /**
     * 回到顶部按钮双态机：进入 = 先挂载、一拍后呈现（vif 挂载的首帧不播动画，
     * R4）；退出 = 先收起、动画结束后卸载。version 使过期回调失效。
     */
    private fun setChatBackToTopVisible(visible: Boolean) {
        val version = ++chatBackToTopVersion
        if (visible) {
            chatBackToTopMounted = true
            setTimeout(16) {
                if (chatBackToTopVersion == version) chatBackToTopPresented = true
            }
        } else {
            chatBackToTopPresented = false
            setTimeout(240) {
                if (chatBackToTopVersion == version) chatBackToTopMounted = false
            }
        }
    }

    /**
     * 平滑滚回顶部：animated=true 走系统滚动动画（区别于 resetChatScrollToTop
     * 的 animated=false 闪现）。动画期间暂停按钮显隐判定，结束后兜底解锁。
     */
    private fun scrollChatToTopAnimated() {
        val version = ++chatTopScrollAnimationVersion
        setChatBackToTopVisible(false)
        chatScrollerRef?.view?.setContentOffset(0f, 0f, true)
        setTimeout(900) {
            if (chatTopScrollAnimationVersion == version) chatTopScrollAnimationVersion = 0
        }
    }

    private fun keepChatAtBottomTemporarily() {
        chatFollowStream = true
        val version = ++keepChatAtBottomVersion
        // 入口这一跳走动画：用户从上方发消息/触发追问时，要看到从当前位置
        // 滚到底部的过程，而不是硬跳。后续 follow loop / 内容长高的追加贴底
        // 仍一律非动画（那些是流式期间的高频小步追平，animated 会互相打断，
        // 且 native 侧 offset 状态在动画未完成时不同步，content 一长高就被
        // 重新布局拉回旧位置，实测表现为「滚了又弹回」）。
        scheduleScrollChatToBottom(animated = true)
        // 2.5s：覆盖行情上下文解析的 3s watchdog——期间用户消息/占位气泡
        // 已挂载长高，窗口内 follow loop 的贴底才生效（流开始后由 STREAMING 接管）。
        setTimeout(2500) {
            if (keepChatAtBottomVersion == version) keepChatAtBottomVersion = 0
        }
        startChatFollowLoop()
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

    /**
     * 流式跟随循环：每 120ms 贴一次底，退出条件 = 非流式且不在贴底窗口后
     * 再宽限约 1.6s（13 拍）。不依赖任何布局事件驱动——发送时启动，流式
     * 期间内容每次长高都会被下一次 tick 追平，流结束收尾（卡片解析、
     * 追问 chips 挂载）由「结束即续 1.5s 版本窗口」覆盖。
     */
    private fun startChatFollowLoop() {
        chatFollowLoop?.cancel()
        val timer = Timer()
        chatFollowLoop = timer
        var lastStreaming = false
        // 是否已见过 STREAMING：宽限计数只在流真正开始过之后才允许累积。
        // 发送 → 流开始之间隔着行情上下文解析（watchdog 最长 3s）+ LLM 首包
        // 延迟，若在此期间按「空闲」计数，循环会在流开始前退出，而
        // contentSizeChanged 事件实测不可靠，流式跟随将彻底失去驱动。
        var sawStreaming = false
        var preStreamTicks = 0
        var idleTicks = 0
        timer.schedule(120, 120) {
            if (isWillDestroy()) {
                timer.cancel()
                if (chatFollowLoop === timer) chatFollowLoop = null
                return@schedule
            }
            val streaming = viewModel.streamState == StreamState.STREAMING
            if (streaming) sawStreaming = true
            KLog.i(COMPOSER_LOG_TAG, "followLoop streaming=$streaming ver=$keepChatAtBottomVersion idle=$idleTicks follow=$chatFollowStream")
            // 流式刚结束：补一段贴底窗口，让收尾长高也被跟随（尊重用户上滑）。
            if (lastStreaming && !streaming && chatFollowStream) {
                keepChatAtBottomAfterStreamEnd()
            }
            lastStreaming = streaming
            idleTicks = if (streaming || keepChatAtBottomVersion > 0 || !sawStreaming) 0 else idleTicks + 1
            // 流一直没来（网络挂死等）：上限拍数后自灭，防止循环无限存活。
            if (!sawStreaming && preStreamTicks++ > CHAT_FOLLOW_PRE_STREAM_MAX_TICKS) {
                KLog.i(COMPOSER_LOG_TAG, "followLoop preStream timeout")
                timer.cancel()
                if (chatFollowLoop === timer) chatFollowLoop = null
                return@schedule
            }
            if (idleTicks > CHAT_FOLLOW_GRACE_TICKS) {
                timer.cancel()
                if (chatFollowLoop === timer) chatFollowLoop = null
                return@schedule
            }
            if (shouldKeepChatAtBottom()) scheduleScrollChatToBottom(animated = false)
        }
    }

    private fun resetChatScrollToTop() {
        intArrayOf(0, 16, 80).forEach { delay ->
            setTimeout(delay) {
                chatScrollerRef?.view?.setContentOffset(0f, 0f, false)
            }
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
                                EntityDropTarget.ISLAND ->
                                    if (page.draggedEntity?.type == EntityType.TERM) "松手加入术语对比" else "松手加入股票对比"
                                EntityDropTarget.COMPOSER ->
                                    // 术语没有 @ 提及形态：落到输入框是填入提问。
                                    if (page.draggedEntity?.type == EntityType.TERM) "松手填入提问" else "松手插入 @ 提及"
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
            islandExpanded = islandExpanded ||
                islandCompareLeftSymbol.isNotEmpty() ||
                islandTermKey.isNotEmpty() ||
                islandTermCompareLeftKey.isNotEmpty(),
        )
    }

    private fun isIslandFirstCompareDrop(): Boolean =
        // 首槽判定跟随被拖实体类型，而非槽位残留态：术语对比面板开着时再拖
        // 一只股票进岛，应按股票槽位判定而不是被旧术语槽架空（反之亦然）。
        if (draggedEntity?.type == EntityType.TERM) {
            islandTermCompareLeftKey.isEmpty()
        } else {
            islandCompareLeftSymbol.isEmpty()
        }

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
            if (entity.type == EntityType.TERM) {
                when (target) {
                    // 术语没有 @ 提及形态：拖到输入框 = 注入「X 是什么意思」问句。
                    EntityDropTarget.COMPOSER -> injectTermQuestion(entity)
                    EntityDropTarget.ISLAND -> addDraggedTermToIsland(entity.target)
                    EntityDropTarget.NONE -> Unit
                }
            } else {
                when (target) {
                    EntityDropTarget.COMPOSER -> handleStockEntity(entity, EntityAction.MENTION)
                    EntityDropTarget.ISLAND -> handleStockEntity(entity, EntityAction.COMPARE)
                    EntityDropTarget.NONE -> Unit
                }
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

    // ===== 消息长按操作菜单（复制 / 追问）=====

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
        selectionMessageId = messageId
        selectionPageX = pageX
        selectionPageY = pageY
        val ref = messageSelectionRefs[messageId]
        if (message.streaming || ref == null) {
            showMessageActionMenu(messagePlainText(message), message.role != MessageRole.USER, pageX, pageY)
            return
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
        ref.view?.createSelection(x, y, SelectionType.WORD)
        // 长按后不拖手柄的场景：留一拍给渲染层落词，已选上就直接弹菜单；
        // 若长按点没有文本（空白），回退整条消息菜单。
        val version = messageActionVersion
        setTimeout(500) {
            if (version != messageActionVersion) return@setTimeout
            collectSelectionAndShowMenu(pageX, pageY, fallbackMessage = message)
        }
    }

    /** selectEnd：手柄拖动结束（或起选定时器到达），取选中文本弹「复制/追问」。 */
    private fun handleTextSelectEnd(messageId: String) {
        if (messageId != selectionMessageId) return
        collectSelectionAndShowMenu(selectionPageX, selectionPageY, fallbackMessage = null)
    }

    private fun handleTextSelectCancel(messageId: String) {
        // 点按其他区域退出选择模式：同步收起菜单，保持界面状态一致。
        if (messageId == selectionMessageId && messageActionPresented) {
            dismissMessageActionMenu()
        }
    }

    private fun collectSelectionAndShowMenu(pageX: Float, pageY: Float, fallbackMessage: ChatMessage?) {
        val ref = messageSelectionRefs[selectionMessageId]
        if (ref == null) {
            fallbackMessage?.let {
                showMessageActionMenu(messagePlainText(it), it.role != MessageRole.USER, pageX, pageY)
            }
            return
        }
        ref.view?.getSelection { result ->
            // content 按 Text 视图阅读顺序给出选中文本，直接相连即原文。
            val selected = result.joinToString("").trim()
            when {
                selected.isNotEmpty() ->
                    showMessageActionMenu(selected, allowFollowUp = true, pageX = pageX, pageY = pageY)
                fallbackMessage != null ->
                    showMessageActionMenu(
                        messagePlainText(fallbackMessage),
                        fallbackMessage.role != MessageRole.USER,
                        pageX,
                        pageY,
                    )
                else -> Unit
            }
        }
    }

    private fun showMessageActionMenu(text: String, allowFollowUp: Boolean, pageX: Float, pageY: Float) {
        if (text.isBlank()) return
        messageActionText = text
        // 追问预填引文：压缩空白并截断，避免长回复撑爆输入栏。
        messageActionQuote = text.replace(Regex("\\s+"), " ").trim().let {
            if (it.length > 60) "${it.take(60)}…" else it
        }
        messageActionFollowUp = allowFollowUp
        messageActionVersion++
        messageActionX = pageX
        messageActionY = pageY
        if (messageActionMounted) {
            messageActionPresented = true
        } else {
            messageActionMounted = true
            val version = messageActionVersion
            // vif 新挂载视图首帧不播动画（R4）：挂载一拍后翻 presented。
            setTimeout(0) {
                if (version == messageActionVersion && messageActionMounted) messageActionPresented = true
            }
        }
    }

    private fun dismissMessageActionMenu() {
        if (!messageActionMounted) return
        clearActiveTextSelection()
        messageActionVersion++
        messageActionPresented = false
        val version = messageActionVersion
        setTimeout(220) {
            // version 已变化 = 期间重新长按打开了菜单，不能卸载。
            if (version == messageActionVersion && !messageActionPresented) messageActionMounted = false
        }
    }

    /** 清除当前消息上残留的选择手柄与高亮。 */
    private fun clearActiveTextSelection() {
        messageSelectionRefs[selectionMessageId]?.view?.clearSelection()
    }

    private fun copyMessageToPasteboard() {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridge.copyToPasteboard(messageActionText)
        bridge.toast("已复制")
        dismissMessageActionMenu()
    }

    /** 追问：把引文预填进输入栏并聚焦，问句由用户补全（不预填价值判断类文案）。 */
    private fun quoteMessageIntoComposer() {
        val quote = messageActionQuote
        dismissMessageActionMenu()
        if (quote.isEmpty()) return
        val draft = viewModel.inputText.trimEnd()
        setComposerText(if (draft.isEmpty()) "「$quote」" else "$draft\n「$quote」")
        expandComposer(requestFocus = true)
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

    /** 术语拖到输入框：注入「X 是什么意思」问句（术语没有 @ 提及形态）。 */
    private fun injectTermQuestion(entity: EntitySpan) {
        val name = Glossary.byKey(entity.target)?.term ?: entity.text
        injectQuestion("$name 是什么意思")
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

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
        // 新对比会话开始：使上一次退出对比留下的收起兜底定时器失效。
        compareExperienceVersion++
        compareCandidateKey = ""
        compareCandidateSymbol = ""
        // 对比会话互斥：开始股票对比即结束术语对比（槽位、面板与岛内术语态）。
        islandTermKey = ""
        islandTermCompareLeftKey = ""
        islandTermCompareRightKey = ""
        islandTermCompareVisible = false
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
        if (islandTermCompareLeftKey.isNotEmpty()) {
            clearIslandTermCompare()
        } else {
            clearCompareExperience()
        }
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
        // 对比 × 详情竞态仲裁（用户决策 2026-09-05）：
        // 1) 使在途的详情路由复位定时器失效，防止退出对比后旧复位帧与收起
        //    动画交错，把对比几何重新写回原生层（残留对比样式的根源）；
        // 2) 收起兜底：若收起写入被动画注册竞争/页面覆盖吃掉，360ms 后以
        //    snap 强制贴回收起几何（R5 version-guarded fallback 模式）。
        islandDetailRouteResetVersion++
        islandDetailRouteActive = false
        val version = ++compareExperienceVersion
        setTimeout(360) {
            if (version == compareExperienceVersion && !islandExpanded && !isIslandCompareLobbyVisible()) {
                forceIslandCollapsedForDetailRoute()
            }
        }
    }

    private fun openIslandComparePanel() {
        // 术语对比的「查看对比」：面板由双槽位驱动，无需额外状态。
        if (islandTermCompareLeftKey.isNotEmpty() && islandTermCompareRightKey.isNotEmpty()) {
            resetIslandMotion()
            islandExpanded = true
            return
        }
        if (compareCard == null) return
        resetIslandMotion()
        islandCompareVisible = true
        islandExpanded = true
    }

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

    private fun openEntityQuoteIsland(symbol: String) {
        resetIslandMotion()
        islandSymbol = symbol
        islandWatchlisted = watchlistStore.contains(symbol)
        islandCompareVisible = false
        // 术语讲解卡与行情卡互斥：打开股票岛时收起术语卡（对比面板不在此清理，
        // 与股票对比面板在行情卡打开时保留的策略一致）。
        islandTermKey = ""
        requestQuote(symbol)
        islandExpanded = true
    }

    // ===== 术语灵动岛（与股票行情岛同一手势/形变体系，2026-09-07）=====

    /** 长按蓝色术语高亮：原地展开术语讲解卡；拖拽跟手与股票实体共用一套字段。 */
    private fun handleTermEntityLongPress(entity: EntitySpan, params: LongPressParams) {
        when (params.state) {
            "start" -> {
                if (params.isCancel || pendingLongPressTermKey == entity.target) return
                pendingLongPressTermKey = entity.target
                suppressNextTermClick = entity.text
                draggedEntity = entity
                entityDragName = Glossary.byKey(entity.target)?.term ?: entity.text
                entityDragStartX = params.pageX
                entityDragStartY = params.pageY
                entityDragX = params.pageX
                entityDragY = params.pageY
                entityDragActive = false
                entityDropTarget = EntityDropTarget.NONE
                // 静止长按 = 术语讲解预览。长按展开讲解与点击高亮一样算一次
                // 真实「遇到」（doc 24 §6.3：用户真实撞上术语才算）。
                openEntityTermIsland(entity.target)
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                trackComposerEvent("term_hold_preview", "term" to entity.target)
                return
            }
            "move" -> {
                if (pendingLongPressTermKey != entity.target) return
                if (!entityDragActive && EntityDropResolver.hasExceededDragThreshold(
                        entityDragStartX,
                        entityDragStartY,
                        params.pageX,
                        params.pageY,
                    )
                ) {
                    entityDragActive = true
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
                    trackComposerEvent("term_drag_start", "term" to entity.target)
                }
                if (entityDragActive) updateEntityDragPosition(params.pageX, params.pageY)
                if (params.isCancel) {
                    if (entityDragActive) finishEntityDrag() else finishTermHold(entity.target)
                }
                return
            }
            "end" -> {
                if (pendingLongPressTermKey != entity.target) return
                if (entityDragActive) {
                    updateEntityDragPosition(params.pageX, params.pageY)
                    finishEntityDrag()
                } else {
                    finishTermHold(entity.target)
                }
                return
            }
            else -> if (params.isCancel && pendingLongPressTermKey == entity.target) {
                if (entityDragActive) finishEntityDrag() else finishTermHold(entity.target)
                return
            }
        }
    }

    private fun finishTermHold(key: String) {
        draggedEntity = null
        entityDragActive = false
        entityDropTarget = EntityDropTarget.NONE
        entityDragName = ""
        pendingLongPressTermKey = ""
        // 与 finishStockLongPress 同款：岛在释放手指的命中区之外，只需清理
        // 长按补发 click 的抑制词形。
        val suppressed = suppressNextTermClick
        setTimeout(400) {
            if (suppressNextTermClick == suppressed) {
                suppressNextTermClick = ""
            }
        }
    }

    private fun openEntityTermIsland(key: String) {
        resetIslandMotion()
        // 股票对比 lobby 隐藏（对比面板/槽位不销毁，与行情卡打开时同策略）。
        islandCompareVisible = false
        islandTermKey = key
        glossaryStore.encounter(key)
        islandExpanded = true
    }

    /** 术语拖入灵动岛：第一只占左槽，第二只占右槽并弹出术语对比面板。 */
    private fun addDraggedTermToIsland(key: String) {
        resetIslandMotion()
        // 对比会话互斥：开始术语对比即结束股票对比（反之亦然）。
        compareExperienceVersion++
        islandCompareLeftSymbol = ""
        islandCompareRightSymbol = ""
        islandCompareVisible = false
        compareCard = null
        resetCompareInsight()
        compareCandidateKey = ""
        compareCandidateSymbol = ""
        if (islandTermCompareLeftKey.isEmpty()) {
            islandTermCompareLeftKey = key
            islandTermCompareRightKey = ""
        } else if (islandTermCompareLeftKey == key || islandTermCompareRightKey == key) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("请拖入另一个术语进行对比")
            islandExpanded = true
            return
        } else if (islandTermCompareRightKey.isNotEmpty()) {
            islandTermCompareLeftKey = islandTermCompareRightKey
            islandTermCompareRightKey = key
        } else {
            islandTermCompareRightKey = key
        }
        islandTermCompareVisible = true
        islandExpanded = true
        syncTermComparePanel()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).hapticImpact()
    }

    private fun syncTermComparePanel() {
        if (!islandTermCompareVisible) return
        val left = Glossary.byKey(islandTermCompareLeftKey) ?: return
        val right = Glossary.byKey(islandTermCompareRightKey) ?: return
        requestTermCompareInsightIfNeeded(left, right)
    }

    private fun isIslandTermCompareLobbyVisible(): Boolean =
        islandTermCompareVisible && islandTermCompareLeftKey.isNotEmpty()

    private fun clearIslandTermCompare() {
        clearTermCompareExperience()
    }

    private fun clearTermCompareExperience() {
        resetIslandMotion()
        // 退出对比是硬交互边界：失效全部拖拽字段，防止终态长按事件丢失残留。
        pendingLongPressTermKey = ""
        draggedEntity = null
        entityDragActive = false
        entityDropTarget = EntityDropTarget.NONE
        entityDragName = ""
        islandTermCompareLeftKey = ""
        islandTermCompareRightKey = ""
        islandTermCompareVisible = false
        islandTermKey = ""
        islandExpanded = false
        resetCompareInsight()
        val version = ++compareExperienceVersion
        setTimeout(360) {
            if (version == compareExperienceVersion && !islandExpanded && !isIslandTermCompareLobbyVisible()) {
                forceIslandCollapsedForDetailRoute()
            }
        }
    }

    private fun toggleIslandWatchlist(symbol: String) {
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
        if (!DataSourceConfig.USE_REAL_MARKET_DATA) {
            // 模拟模式（原状态）：行情回落 MockDataBank，行为与数据源真实化之前一致。
            mockQuoteProvider.snapshot(symbol) { quote ->
                val updated = ChatQuoteState(symbol, quote, DataMode.OFFLINE)
                val index = quoteStates.indexOfFirst { it.symbol == symbol }
                if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
                syncIslandCompareCard()
            }
            return
        }
        // 真实模式：腾讯行情 → 缓存 → 空态，不再有任何模拟数值。
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
    // 容器变换交接：路由触发（160ms 主触发 + 420ms 兜底 + 动画完成事件）
    // 三路竞争，用幂等门保证只跑一次。
    private var islandDetailHandoffDone = false
    // 交接遮罩：原生整页淡入期间 push 会立即触发本页 pageDidDisappear，
    // 此时全屏玻璃帧还要作淡入的底，不能提前归位。
    private var islandHandoffMaskActive = false

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
        // 交接早已结束（详情页盖住期间淡入必已完成），清掉可能因渲染暂停
        // 而延迟的遮罩，避免 pageDidDisappear 的兜底归位被误拦。
        islandHandoffMaskActive = false
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
        islandHandoffMaskActive = false
        islandMounted = true
    }

    private fun toggleIsland() {
        // A tap can be re-delivered to stacked layers while the morph
        // re-layouts; ignore toggles until the animation settles.
        if (islandAnimating || islandGestureMotion.phase != IslandGesturePhase.IDLE) return
        // 对比 lobby 在场时 expanded() 恒为真，单纯翻转 islandExpanded 会被
        // compareVisible 架空（点了没反应，还把两个状态拧成不一致）。
        // 用户决策 2026-09-05：此时点击 = 退出整个对比体验。
        if (isIslandCompareLobbyVisible()) {
            clearCompareExperience()
            return
        }
        if (isIslandTermCompareLobbyVisible()) {
            clearTermCompareExperience()
            return
        }
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
                    isIslandCompareLobbyVisible() ||
                    isIslandTermCompareLobbyVisible()
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
                    // Trigger thresholds kept low so a short flick is enough
                    // (16dp close / 20dp detail); the pan already streams raw
                    // pageY so lowering them costs nothing in tracking.
                    state == "end" && deltaY <= -16f -> settleIslandClosedFromGesture()
                    state == "end" && deltaY >= 20f -> openIslandDetailFromGesture(islandSymbol)
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
            // 术语岛没有 islandSymbol（只有 islandTermKey），下滑去术语表走
            // 同一条 OPENING_DETAIL 管线——空 symbol 不能提前 return，否则
            // motion 永远停在 DRAGGING，卡片停留在被拉高的形变态（卡死）。
            (symbol.isEmpty() && islandTermKey.isEmpty())
        ) return
        islandDetailHandoffDone = false
        islandGestureMotion = islandGestureMotion.copy(
            phase = IslandGesturePhase.OPENING_DETAIL,
            offsetY = 0f,
        )
        islandAnimating = true

        // 容器变换交接：形变进行到 ~90%（0.18s 形变的 160ms 处）就启动路由，
        // 原生无动画 push + 整页淡入与剩余形变重叠，详情页在卡片收尾时就已
        // 开始渐显，消除"全屏白幕等页面启动"的停顿。420ms 是渲染器兜底
        //（与 160ms 主触发都走 phase-gated + islandDetailHandoffDone 幂等门）。
        setTimeout(160) { completeIslandMotion(ISLAND_ANIMATION_DETAIL) }
        setTimeout(420) { completeIslandMotion(ISLAND_ANIMATION_DETAIL) }
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
                islandGestureMotion.phase == IslandGesturePhase.OPENING_DETAIL &&
                !islandDetailHandoffDone -> {
                islandDetailHandoffDone = true
                val symbol = islandSymbol
                islandDetailRouteActive = true
                islandDetailRouteResetVersion++
                // 详情与对比互斥（用户决策 2026-09-05）：进详情路由时清掉灵动岛
                // 对比会话，避免返回后对比 lobby 借着 compareVisible 复活。
                // （清态随归位一起延后到交接淡入结束，期间玻璃帧是淡入的底。）
                // 交接遮罩：无动画 push 会立即触发本页 pageDidDisappear（其中
                // 会强制归位灵动岛），但全屏玻璃帧还要作原生整页淡入的底，
                // 先捂住归位，等详情页完全不透明后再原地 snap 归位（用户不可见）。
                islandHandoffMaskActive = true
                if (islandTermKey.isNotEmpty()) {
                    // 术语岛下滑 = 进入术语表：与股票岛进详情页同一条容器变换
                    // 交接（无动画 push + 页面就地淡入接管玻璃帧）。
                    openGlossary(islandExpand = true)
                } else {
                    openStockDetail(symbol, islandExpand = true)
                }
                setTimeout(550) {
                    islandHandoffMaskActive = false
                    islandExpanded = false
                    islandCompareVisible = false
                    islandCompareLeftSymbol = ""
                    islandCompareRightSymbol = ""
                    islandTermKey = ""
                    islandTermCompareLeftKey = ""
                    islandTermCompareRightKey = ""
                    islandTermCompareVisible = false
                    remountIslandCollapsedForDetailRoute()
                }
            }
        }
    }

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

    private fun consumeRouteQuestionIfNeeded() {
        val question = pendingRouteQuestion.trim()
        if (question.isEmpty()) return
        pendingRouteQuestion = ""
        val focusNote = pendingRouteFocusNote.trim()
        pendingRouteFocusNote = ""
        setTimeout(0) {
            if (focusNote.isNotEmpty()) attachContextNote(focusNote)
            injectQuestion(question)
        }
    }

    /** Route-provided context must not toggle the deep-water visual mode. */
    private fun attachContextNote(note: String) {
        if (note !in deepContextNotes) deepContextNotes.add(note)
        deepContextVersion++
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
        // 术语对比的解读重试不能走股票路径（pairKey/quotes 都不同），按会话派发。
        if (islandTermCompareLeftKey.isNotEmpty()) {
            retryTermCompareInsight()
            return
        }
        val cardQuotes = compareCard?.quotes.orEmpty()
        val left = cardQuotes.getOrNull(0) ?: quoteFor(islandCompareLeftSymbol) ?: return
        val right = cardQuotes.getOrNull(1) ?: quoteFor(islandCompareRightSymbol) ?: return
        compareInsightPairKey = ""
        requestCompareInsightIfNeeded(left, right)
    }

    private fun retryTermCompareInsight() {
        val left = Glossary.byKey(islandTermCompareLeftKey) ?: return
        val right = Glossary.byKey(islandTermCompareRightKey) ?: return
        compareInsightPairKey = ""
        requestTermCompareInsightIfNeeded(left, right)
    }

    private fun resetCompareInsight() {
        compareInsightVersion += 1
        compareInsightPairKey = ""
        compareInsightState = CompareInsightState.IDLE
        compareInsightText = ""
        compareInsightError = ""
    }

    /**
     * 术语对比 AI 解读：与股票对比共用 insight 状态机（会话互斥），
     * 只做两个概念的区别与联系的事实性解释（合规文案铁律：不做价值判断）。
     */
    private fun requestTermCompareInsightIfNeeded(left: GlossaryEntry, right: GlossaryEntry) {
        val pairKey = "term:${left.key}:${right.key}"
        if (compareInsightPairKey == pairKey && compareInsightState != CompareInsightState.ERROR) return
        compareInsightPairKey = pairKey
        compareInsightState = CompareInsightState.LOADING
        compareInsightText = ""
        compareInsightError = ""
        val requestVersion = ++compareInsightVersion
        // 流式打字机（与主聊天流同款，见 TypewriterSmoother）：delta 全量进缓冲、
        // 按节拍逐字释放到面板。面板高度因此随文本连续小步生长，而不是 onDone
        // 时整段顶上来把卡片"弹"一下。
        val typewriter = TypewriterSmoother(pagerId) { revealed ->
            if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@TypewriterSmoother
            compareInsightText = revealed
        }
        viewModel.askSubThread(
            prompt = "用不超过 120 字向 A 股新手解释金融术语「${left.term}」和「${right.term}」的区别与联系，" +
                "各举一个它们分别适用的小场景。只做事实性解释，不要给任何买卖建议或倾向性结论。",
            onDelta = { delta ->
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@askSubThread
                typewriter.append(delta)
            },
            onDone = {
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) {
                    typewriter.cancel()
                    return@askSubThread
                }
                // 收尾等显示端把缓冲打完再落 READY，避免最后一截整段蹦出。
                typewriter.complete {
                    if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@complete
                    compareInsightText = compareInsightText.ifBlank { "暂未生成对比解读" }
                    compareInsightState = CompareInsightState.READY
                }
            },
            onError = { error ->
                typewriter.cancel()
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@askSubThread
                compareInsightError = error
                compareInsightState = CompareInsightState.ERROR
            },
        )
    }

    private fun requestCompareInsightIfNeeded(left: Quote, right: Quote) {
        val pairKey = "${left.symbol}:${right.symbol}"
        if (compareInsightPairKey == pairKey && compareInsightState != CompareInsightState.ERROR) return
        compareInsightPairKey = pairKey
        compareInsightState = CompareInsightState.LOADING
        compareInsightText = ""
        compareInsightError = ""
        val requestVersion = ++compareInsightVersion
        // 流式打字机（同 requestTermCompareInsightIfNeeded）：逐字释放，
        // 面板高度连续生长，onDone 不再整段顶高卡片。
        val typewriter = TypewriterSmoother(pagerId) { revealed ->
            if (requestVersion == compareInsightVersion && compareInsightPairKey == pairKey) {
                compareInsightText = revealed
            }
        }
        viewModel.askSubThread(
            prompt = buildCompareInsightPrompt(left, right),
            onDelta = { delta ->
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@askSubThread
                typewriter.append(delta)
            },
            onDone = {
                if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) {
                    typewriter.cancel()
                    return@askSubThread
                }
                typewriter.complete {
                    if (requestVersion != compareInsightVersion || compareInsightPairKey != pairKey) return@complete
                    compareInsightText = compareInsightText.ifBlank { "暂未生成对比解读" }
                    compareInsightState = CompareInsightState.READY
                }
            },
            onError = { error ->
                typewriter.cancel()
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
