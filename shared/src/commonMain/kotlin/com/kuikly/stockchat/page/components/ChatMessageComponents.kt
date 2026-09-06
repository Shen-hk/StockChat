package com.kuikly.stockchat.page.components

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
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.MessageRole
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
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class SubThreadState(
    val cardId: String,
    val title: String,
    val input: String,
    val response: String,
    val streaming: Boolean = false,
    val collapsed: Boolean = false,
)

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
    val onShareInterpretation: (String) -> Unit,
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
                Text { attr { text(message.content); fontSize(16f); lineHeight(24f); color(theme.onBrand) } }
            } else {
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
    state: ChatMessageRenderState,
    actions: ChatMessageActions,
) {
    val blocks = AiResponseLexer.lex(message.content, finished = !message.streaming)
    blocks.forEach { block ->
        when (block) {
            is TextBlock -> {
                View {
                    attr { marginTop(4f) }
                    EntityRichText(block.content, theme, contextSymbols, actions.onEntityStock, actions.onEntityStockLongPress, actions.onTerm)
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
                } catch (_: Throwable) {
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
                    flex(1f); fontSize(9f); color(if (message.failed) theme.brand else theme.textTertiary)
                }
                if (message.failed) event { click { actions.onRetry() } }
            }
            if (!message.failed && !message.cancelled) {
                Text { attr { text("分享长图"); fontSize(9.5f); color(theme.brand) } }
                event { click { actions.onShareInterpretation(message.content) } }
            }
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
