package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardAssembler
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
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
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.DataModeBadge
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
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.CHAT, supportInLocal = true)
internal class ChatPage : BasePager() {
    private val viewModel by lazy { ChatViewModel(pagerId) }
    private lateinit var inputRef: ViewRef<InputView>
    private var peekSymbol: String by observable("")
    private var peekVisible: Boolean by observable(false)
    private var ambiguousSymbols: ObservableList<String> by observableList()
    private var ambiguousEntityText: String by observable("")
    private var ambiguousAction: EntityAction by observable(EntityAction.PREVIEW)
    private var keyboardHeight: Float by observable(0f)
    private var suppressNextStockClickSymbol = ""
    private var peekVersion = 0
    private val quoteRepository by lazy { QuoteRepositoryStore.shared(pagerId) }
    private var quoteStates: ObservableList<ChatQuoteState> by observableList()
    private val requestedSymbols = mutableSetOf<String>()
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    override fun created() {
        super.created()
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
            AppTopBar(
                title = "股问",
                subtitle = "解释行情，不做荐股",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                actions = listOf(
                    "API 设置" to { page.openPage(Routes.API_CONFIG) },
                    "组件" to { page.openPage(Routes.CARD_GALLERY) },
                    "新会话" to { page.viewModel.clear() },
                ),
            )
            View {
                attr {
                    paddingLeft(16f)
                    paddingRight(16f)
                    paddingTop(8f)
                    paddingBottom(6f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                DataModeBadge(
                    page.theme,
                    if (page.viewModel.apiConfigured) "AI API 已配置" else "AI API 未配置",
                )
                Text {
                    attr {
                        text(if (page.viewModel.apiConfigured) "回答将调用已配置的真实模型" else "请先在右上角完成 API 设置")
                        marginLeft(8f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
            }
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    paddingBottom(18f)
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
                    )
                }
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
                        backgroundColor(page.theme.surface)
                        borderRadius(page.theme.cardRadius)
                        opacity(if (page.peekVisible) 1f else 0f)
                        transform(Translate(0f, if (page.peekVisible) 0f else 0.16f))
                        animate(Animation.easeOut(0.18f), page.peekVisible)
                    }
                    View {
                        attr { flex(1f) }
                        if (quote == null) Text { attr { text("正在获取 ${page.peekSymbol} 的行情…"); fontSize(12f); color(page.theme.textTertiary) } }
                        else CardShell(
                            StockQuoteCardModel(quote),
                            CardContext(page.theme, CardDensity.MINI, { page.openStockDetail(it) }),
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
            View {
                attr {
                    padding(12f)
                    paddingBottom(12f + page.pagerData.safeAreaInsets.bottom + page.keyboardHeight)
                    backgroundColor(page.theme.surface)
                    flexDirectionRow()
                    alignItemsFlexEnd()
                }
                View {
                    attr {
                        flex(1f)
                        minHeight(44f)
                        paddingLeft(12f)
                        paddingRight(12f)
                        backgroundColor(page.theme.surfaceMuted)
                        borderRadius(page.theme.inputRadius)
                        justifyContentCenter()
                    }
                    Input {
                        ref { page.inputRef = it }
                        attr {
                            height(40f)
                            fontSize(14f)
                            color(page.theme.textPrimary)
                            placeholder("问一只股票或一个术语")
                            placeholderColor(page.theme.textTertiary)
                            returnKeyTypeSend()
                        }
                        event {
                            textDidChange { page.viewModel.inputText = it.text }
                            keyboardHeightChange { page.keyboardHeight = it.height }
                            inputReturn {
                                page.submitInput()
                            }
                        }
                    }
                }
                View {
                    attr {
                        marginLeft(8f)
                        size(54f, 44f)
                        allCenter()
                        borderRadius(page.theme.inputRadius)
                        backgroundColor(page.theme.brand)
                    }
                    Text {
                        attr {
                            text(if (page.viewModel.streamState == StreamState.STREAMING) "停止" else "发送")
                            fontSize(13f)
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
        }
    }

    private fun submitInput() {
        val value = viewModel.inputText
        viewModel.send(value)
        if (value.isNotBlank()) inputRef.view?.setText("")
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
                setTimeout(500) {
                    if (suppressNextStockClickSymbol == entity.target) suppressNextStockClickSymbol = ""
                }
                handleStockEntity(entity, EntityAction.PREVIEW)
            }
            "end" -> if (!cancelled && peekSymbol.isNotEmpty()) schedulePeekDismissal()
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
        if (action == EntityAction.DETAIL) openStockDetail(symbol) else showQuote(symbol)
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
        quoteRepository.load(symbol) { result ->
            val updated = ChatQuoteState(symbol, result.quote, result.mode)
            val index = quoteStates.indexOfFirst { it.symbol == symbol }
            if (index >= 0) quoteStates[index] = updated else quoteStates.add(updated)
        }
    }

    private fun quoteFor(symbol: String): Quote? =
        quoteStates.firstOrNull { it.symbol == symbol }?.quote ?: quoteRepository.cachedOrOffline(symbol)

}

private enum class EntityAction { DETAIL, PREVIEW }

private data class ChatQuoteState(
    val symbol: String,
    val quote: Quote?,
    val mode: DataMode,
)

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
) {
    val user = message.role == MessageRole.USER
    View {
        attr {
            marginTop(10f)
            if (user) alignItemsFlexEnd() else alignItemsFlexStart()
        }
        View {
            attr {
                if (user) marginLeft(58f) else marginRight(24f)
                padding(12f)
                backgroundColor(if (user) theme.brand else theme.surface)
                borderRadius(14f)
            }
            if (user) {
                Text { attr { text(message.content); fontSize(14f); lineHeight(21f); color(theme.onBrand) } }
            } else {
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
                            AssistantContent(message, theme, contextSymbols, suggestionsActive, onEntityStock, onEntityStockLongPress, onCardStock, onTerm, onSuggestion, onRetry, onQuoteNeeded, quoteFor)
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
    onQuoteNeeded: (String) -> Unit,
    quoteFor: (String) -> Quote?,
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
                val intent = CardPayloadParser.parse(block.type, block.payload)
                if (intent is SuggestionsIntent && suggestionsActive) SuggestionRow(intent, theme, onSuggestion)
                else {
                    when (intent) {
                        is SymbolCardIntent -> onQuoteNeeded(intent.symbol)
                        is AttributionIntent -> onQuoteNeeded(intent.symbol)
                        else -> Unit
                    }
                    CardShell(
                        CardAssembler.assemble(block, quoteFor),
                        CardContext(theme, CardDensity.COMPACT, onCardStock, onTerm),
                    )
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
