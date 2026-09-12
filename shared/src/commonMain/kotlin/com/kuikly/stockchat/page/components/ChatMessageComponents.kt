package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.foundation.ui.icon.LineIconCopy
import com.kuikly.stockchat.foundation.ui.icon.LineIconFileText
import com.kuikly.stockchat.foundation.ui.icon.LineIconRefresh
import com.kuikly.stockchat.foundation.ui.icon.LineIconShare
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.cards.component.CardShell
import com.kuikly.stockchat.cards.core.CardAssembler
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CardEvent
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.SkeletonCardModel
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.card.state.SubThreadState
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.BrokenCardBlock
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.SkeletonBlock
import com.kuikly.stockchat.protocol.SuggestionIntent
import com.kuikly.stockchat.protocol.SuggestionsIntent
import com.kuikly.stockchat.protocol.SymbolCardIntent
import com.kuikly.stockchat.protocol.TextBlock
import com.kuikly.stockchat.richtext.EntityRichText
import com.kuikly.stockchat.richtext.EntitySpan
import com.kuikly.stockchat.richtext.EntityStreamingMarkdown
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class ChatMessageRenderState(
    val repairingCardKey: String,
    val drilledKeys: Set<String>,
    val subThreads: List<SubThreadState>,
    val focusedCardKey: String,
    val compareCandidateSymbol: String,
)

internal class ChatMessageActions(
    val onEntityStock: (EntitySpan) -> Unit,
    val onEntityStockLongPress: (EntitySpan, LongPressParams) -> Unit,
    val onCardStock: (String) -> Unit,
    val onTerm: (String) -> Unit,
    // 长按术语高亮 = 术语灵动岛预览/拖拽起手（与股票实体同款手势体系）。
    val onTermLongPress: (EntitySpan, LongPressParams) -> Unit = { _, _ -> },
    val onSuggestion: (String) -> Unit,
    val onRetry: () -> Unit,
    val onRetryCard: (String, String, String, String) -> Unit,
    val onQuoteNeeded: (String) -> Unit,
    val quoteFor: (String) -> Quote?,
    val isCardExpanded: (String) -> Boolean,
    val onToggleCardExpanded: (String) -> Unit,
    val onOpenCardSheet: (CardModel) -> Unit,
    val onToggleDrill: (String) -> Unit,
    val onStartSubThread: (CardModel) -> Unit,
    val onToggleSubThread: (String) -> Unit,
    val onUpdateSubThreadInput: (String, String) -> Unit,
    val onSendSubThread: (String) -> Unit,
    val onFocusChanged: (String, Boolean) -> Unit,
    val onCompareCandidate: (String, String) -> Unit,
    val onCardEvent: (String, CardEvent) -> Unit,
    // ===== 正文文本选择（复制 / 追问）=====
    // 气泡容器开启 selectable 后：长按正文 → 页侧调 createSelection 起选
    // （渲染层显示系统选择手柄与放大镜）→ 拖手柄调整 → selectEnd 取词弹菜单。
    // 实体链接的 linkLongPress 在更内层的富文本视图消费、不冒泡到这里，
    // 因此「长按实体=行情预览岛」与「长按正文=选择文字」天然共存。
    // 每条消息的容器 ref 必须按 messageId 注册：vfor 下 page 级单 ref 会被
    // 最后挂载的消息覆盖，导致跨消息坐标错乱。
    val onSelectionContainerRef: (String, ViewRef<DivView>) -> Unit,
    val onTextSelectionLongPress: (messageId: String, x: Float, y: Float, pageX: Float, pageY: Float) -> Unit,
    val onTextSelectEnd: (String) -> Unit,
    val onTextSelectCancel: (String) -> Unit,
    // ===== 消息操作图标（复制 / 重试 / 分享，纯图标，挂在免责行右侧）=====
    val onCopyMessage: (String) -> Unit,
    val onShareMessage: (String) -> Unit,
    // 重试 = 从该条 AI 回复起截断重新生成（含其后消息）。
    val onRegenerate: (String) -> Unit,
    /** Opens a sent image in the page-owned preview overlay. */
    val onPreviewImage: (String) -> Unit,
    // ===== 完成后的引导语 chips =====
    // 取值闭包读 observable（messages 列表 / message.streaming），在 vif 闭包内
    // 调用建立响应依赖；mount/presented 由页侧双态机驱动（R4 两帧入场）。
    val followUpsVisible: (ChatMessage) -> Boolean,
    val followUpsChips: (ChatMessage) -> List<SuggestionIntent>,
    val followUpsMounted: () -> Boolean,
    val followUpsPresented: () -> Boolean,
)

internal fun ViewContainer<*, *>.ChatMessageView(
    message: ChatMessage,
    theme: StockChatTheme,
    contextSymbols: List<String>,
    suggestionsActive: Boolean,
    state: ChatMessageRenderState,
    actions: ChatMessageActions,
) {
    val user = message.role == MessageRole.USER
    View {
        attr {
            marginTop(20f)
            if (user) alignItemsFlexEnd()
        }
        if (user) {
            // 图片附件：独立于气泡单独成条发送——单排固定正方形缩略图
            // （与输入栏预览同款 56x56 / 12 圆角），靠右与气泡右缘对齐，
            // 图片在上、文字气泡在下。附件快照进 ChatMessage 后不再变化（直接读）。
            vif({ message.attachments.any { it.isImage } }) {
                View {
                    attr {
                        flexDirectionRow()
                        marginRight(2f)
                        marginBottom(
                            if (message.content.isNotEmpty() || message.attachments.any { !it.isImage }) 8f else 0f
                        )
                    }
                    val images = message.attachments.filter { it.isImage }
                    images.forEachIndexed { index, att ->
                        Image {
                            attr {
                                src("file://" + att.path)
                                size(56f, 56f)
                                borderRadius(12f)
                                if (index < images.size - 1) marginRight(8f)
                                backgroundColor(Color(0x11000000))
                            }
                            event { click { actions.onPreviewImage(att.path) } }
                        }
                    }
                }
            }
            // 文字/文档气泡：仅当有正文或文档附件时才出现（纯图片消息无气泡）。
            vif({ message.content.isNotEmpty() || message.attachments.any { !it.isImage } }) {
                View {
                    attr {
                        marginLeft(58f)
                        marginRight(2f)
                        paddingTop(10f)
                        paddingBottom(10f)
                        paddingLeft(16f)
                        paddingRight(16f)
                        backgroundColor(theme.brand)
                        borderRadius(20f)
                        // 子树内所有 Text/富文本可选（渲染层以本容器为根收集可选文本，
                        // selectEnd 事件与选区手柄均挂在此容器上）。
                        selectable(SelectableOption.ENABLE)
                        selectionColor(theme.brand)
                    }
                    ref { actions.onSelectionContainerRef(message.id, it) }
                    event {
                        longPress { params ->
                            if (params.isCancel || params.state != "start") return@longPress
                            actions.onTextSelectionLongPress(message.id, params.x, params.y, params.pageX, params.pageY)
                        }
                        selectEnd { actions.onTextSelectEnd(message.id) }
                        selectCancel { actions.onTextSelectCancel(message.id) }
                    }
                    message.attachments.filter { !it.isImage }.forEach { att ->
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                height(36f)
                                marginBottom(6f)
                                paddingLeft(12f)
                                paddingRight(12f)
                                backgroundColor(Color(0x33FFFFFF))
                                borderRadius(10f)
                            }
                            LineIconFileText(theme.onBrand, 16f)
                            Text {
                                attr {
                                    text(att.displayName)
                                    fontSizeScaled(12f)
                                    color(theme.onBrand)
                                    marginLeft(6f)
                                }
                            }
                        }
                    }
                    vif({ message.content.isNotEmpty() }) {
                        Text { attr { text(message.content); fontSizeScaled(16f); lineHeightScaled(24f); fontWeightMedium(); color(theme.onBrand) } }
                    }
                }
            }
        } else {
            View {
                attr {
                    // No avatar: the AI message spans the row with symmetric
                    // margins so the left and right insets always match.
                    marginLeft(6f)
                    marginRight(6f)
                    // 子树内所有 Text/富文本可选（渲染层以本容器为根收集可选文本，
                    // selectEnd 事件与选区手柄均挂在此容器上）。
                    selectable(SelectableOption.ENABLE)
                    selectionColor(theme.brand)
                }
                ref { actions.onSelectionContainerRef(message.id, it) }
                event {
                    longPress { params ->
                        if (params.isCancel || params.state != "start") return@longPress
                        actions.onTextSelectionLongPress(message.id, params.x, params.y, params.pageX, params.pageY)
                    }
                    selectEnd { actions.onTextSelectEnd(message.id) }
                    selectCancel { actions.onTextSelectCancel(message.id) }
                }
                vif({ message.streaming }) {
                    View {
                        // 流式正文：持久 MarkdownStreamingState + 100ms 定时 flush +
                        // 块级 diffUpdate（详见 EntityStreamingMarkdown 注释）。
                        // 不能用 vbind({ message.content }) 整树重挂载——高频 delta
                        // 下原生层会崩（shadow must not null / duplicate createFlexNode）。
                        EntityStreamingMarkdown(
                            textProvider = { message.content },
                            isStreaming = { message.streaming },
                            timerScope = message,
                            theme = theme,
                            contextSymbols = contextSymbols,
                            onStockClick = actions.onEntityStock,
                            onStockLongPress = actions.onEntityStockLongPress,
                            onTermClick = actions.onTerm,
                            onTermLongPress = actions.onTermLongPress,
                        )
                    }
                }
                vif({ !message.streaming }) {
                    View {
                    try {
                        AssistantContent(message, theme, contextSymbols, suggestionsActive, state, actions)
                    } catch (error: Throwable) {
                        Text {
                            attr {
                                text("结构化内容暂时无法展示：${error.message.orEmpty()}")
                                fontSizeScaled(12f)
                                lineHeightScaled(18f)
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
    state: ChatMessageRenderState,
    actions: ChatMessageActions,
) {
    val blocks = AiResponseLexer.lex(message.content, finished = !message.streaming)
    blocks.forEach { block ->
        when (block) {
            is TextBlock -> {
                View {
                    attr { marginTop(4f) }
                    EntityRichText(
                        block.content,
                        theme,
                        contextSymbols,
                        actions.onEntityStock,
                        actions.onEntityStockLongPress,
                        actions.onTerm,
                        actions.onTermLongPress,
                    )
                }
            }
            is CardBlock -> {
                try {
                        val cardKey = "${message.id}:${block.id}"
                        val intent = CardPayloadParser.parse(block.type, block.payload)
                        if (intent is SuggestionsIntent) {
                            if (suggestionsActive) SuggestionRow(intent, theme, actions.onSuggestion)
                        } else {
                            when (intent) {
                                is SymbolCardIntent -> actions.onQuoteNeeded(intent.symbol)
                                is AttributionIntent -> actions.onQuoteNeeded(intent.symbol)
                                else -> Unit
                            }
                            if (intent is SymbolCardIntent && intent.type == "stock-chart") {
                                ChatStockChartCard(block, intent, theme, actions.onCardStock, actions.onTerm, actions.quoteFor, actions.isCardExpanded, actions.onToggleCardExpanded, actions.onOpenCardSheet, cardKey, state.focusedCardKey, actions.onFocusChanged, state.compareCandidateSymbol, actions.onCompareCandidate, actions.onCardEvent)
                            } else {
                                val model = CardAssembler.assemble(block, actions.quoteFor)
                                ReactiveCardShell(
                                    model,
                                    CardContext(
                                        theme = theme,
                                        density = CardDensity.COMPACT,
                                        onOpenStock = actions.onCardStock,
                                        onExplainTerm = actions.onTerm,
                                        expanded = false,
                                        onToggleExpanded = { actions.onToggleCardExpanded(cardKey) },
                                        onOpenSheet = actions.onOpenCardSheet,
                                        drilledKeys = state.drilledKeys,
                                        onToggleDrill = actions.onToggleDrill,
                                        onStartSubThread = actions.onStartSubThread,
                                        cardKey = cardKey,
                                        focusedCardKey = state.focusedCardKey,
                                        onFocusChanged = actions.onFocusChanged,
                                        compareCandidateSymbol = state.compareCandidateSymbol,
                                        onCompareCandidate = actions.onCompareCandidate,
                                        onCardEvent = actions.onCardEvent,
                                    ),
                                    cardKey,
                                    actions.isCardExpanded,
                                )
                                if (model is InsightCardModel) {
                                    state.subThreads.firstOrNull { it.cardId == model.cardId }?.let { thread ->
                                        NestedConversation(thread, theme, actions.onToggleSubThread, actions.onUpdateSubThreadInput, actions.onSendSubThread)
                                    }
                                }
                            }
                        }
                    }
                catch (_: Throwable) {
                    val rawCard = "```card:${block.type}\n${block.payload}\n```"
                    val cardKey = "${message.id}:${block.id}"
                    StructuredContentUnavailable(
                        type = block.type,
                        theme = theme,
                        retrying = state.repairingCardKey == cardKey,
                        onRetry = { actions.onRetryCard(message.id, block.id, block.type, rawCard) },
                    )
                }
            }
            is BrokenCardBlock -> {
                val cardKey = "${message.id}:${block.id}"
                StructuredContentUnavailable(
                    type = block.type,
                    theme = theme,
                    retrying = state.repairingCardKey == cardKey,
                    onRetry = { actions.onRetryCard(message.id, block.id, block.type, block.raw) },
                )
            }
            is SkeletonBlock -> CardShell(
                CardAssembler.assemble(block),
                CardContext(theme, CardDensity.COMPACT, actions.onCardStock, actions.onTerm),
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
            actions.onSuggestion,
        )
    }
    if (!message.streaming) {
        // 免责/状态行：左侧文案，右侧 三个纯图标动作（复制 / 重试 / 分享）。
        // 失败态整行可点重试，图标不展示。
        View {
            attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text(
                        when {
                            message.failed -> "生成失败，点击重试"
                            message.cancelled -> "已停止生成"
                            else -> "AI 生成，仅供参考，不构成投资建议"
                        },
                    )
                    flex(1f)
                    fontSizeScaled(9f)
                    color(if (message.failed) theme.brand else theme.textTertiary)
                }
            }
            if (message.failed) event { click { actions.onRetry() } }
            vif({ !message.failed }) {
                footerIconButton(theme, icon = { c, s -> LineIconCopy(c, s) }) {
                    actions.onCopyMessage(message.id)
                }
                footerIconButton(theme, icon = { c, s -> LineIconRefresh(c, s) }) {
                    actions.onRegenerate(message.id)
                }
                footerIconButton(theme, icon = { c, s -> LineIconShare(c, s) }) {
                    actions.onShareMessage(message.content)
                }
            }
        }
        // 引导语：回答完成后弹出几条追问 chips（页侧两帧入场，R4）。
        // 模型协议里已带 suggestions 卡片时由上方 SuggestionRow 渲染，不重复弹。
        vif({ actions.followUpsMounted() && actions.followUpsVisible(message) }) {
            View {
                attr {
                    opacity(if (actions.followUpsPresented()) 1f else 0f)
                    transform(Translate(0f, if (actions.followUpsPresented()) 0f else 10f))
                    // R2/R5：presented 是本 attr 最后读的 observable，animate 以其为
                    // 驱动 key；挂载周期注册、下一帧翻转时消费播放。
                    animate(Animation.easeOut(0.26f).delay(0.04f), actions.followUpsPresented())
                }
                SuggestionRow(
                    SuggestionsIntent(actions.followUpsChips(message)),
                    theme,
                    actions.onSuggestion,
                )
            }
        }
    }
}

/**
 * 免责行右侧的纯图标动作按钮（无文字）：17f 图标 + 4f 四向留白做点击热区。
 */
private fun ViewContainer<*, *>.footerIconButton(
    theme: StockChatTheme,
    icon: ViewContainer<*, *>.(Color, Float) -> Unit,
    onTap: () -> Unit,
) {
    View {
        attr {
            marginLeft(8f)
            paddingTop(4f)
            paddingBottom(4f)
            paddingLeft(4f)
            paddingRight(4f)
        }
        icon(theme.textTertiary, 17f)
        event { click { onTap() } }
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
                    fontSizeScaled(10f)
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
                    fontSizeScaled(13f)
                    fontWeightMedium()
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    text("可单独重试这张卡片")
                    marginTop(3f)
                    fontSizeScaled(10f)
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
                    fontSizeScaled(11f)
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
                        fontSizeScaled(12f)
                        color(if (suggestion.type == "drill") theme.brand else theme.textSecondary)
                    }
                }
                event { click { onSuggestion(suggestion.text) } }
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
            Text { attr { text(state.title); fontSizeScaled(11f); fontWeightMedium(); color(theme.brand); flex(1f) } }
            Text { attr { text(if (state.collapsed) "展开" else "收起"); fontSizeScaled(11f); color(theme.brand) } }
            event { click { onToggle(state.cardId) } }
        }
        if (!state.collapsed) {
            Text { attr { text(state.response); marginTop(8f); fontSizeScaled(12f); lineHeightScaled(18f); color(theme.textSecondary) } }
            View {
                attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f); height(34f); paddingLeft(9f); paddingRight(9f); backgroundColor(theme.surface); borderRadius(8f); justifyContentCenter() }
                    Input {
                        attr { height(32f); fontSizeScaled(12f); color(theme.textPrimary); placeholder("继续追问"); placeholderColor(theme.textTertiary) }
                        event { textDidChange { onInput(state.cardId, it.text) } }
                    }
                }
                View {
                    attr { marginLeft(6f); height(34f); paddingLeft(10f); paddingRight(10f); allCenter(); backgroundColor(theme.brand); borderRadius(8f) }
                    Text { attr { text(if (state.streaming) "…" else "发送"); fontSizeScaled(11f); color(theme.onBrand) } }
                    if (!state.streaming) event { click { onSend(state.cardId) } }
                }
            }
        }
    }
}
