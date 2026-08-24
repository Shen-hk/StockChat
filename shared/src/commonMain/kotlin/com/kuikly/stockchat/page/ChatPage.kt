package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardAssembler
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.ChatViewModel
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.StreamState
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.DataModeBadge
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.SkeletonBlock
import com.kuikly.stockchat.protocol.SuggestionsIntent
import com.kuikly.stockchat.protocol.TextBlock
import com.kuikly.stockchat.richtext.EntityRichText
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.CHAT, supportInLocal = true)
internal class ChatPage : BasePager() {
    private val viewModel by lazy { ChatViewModel(pagerId, pagerData.params.optString("deepSeekApiKey")) }
    private lateinit var inputRef: ViewRef<InputView>
    private var peekSymbol: String by observable("")
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
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
                DataModeBadge(page.theme)
                Text {
                    attr {
                        text("行情数字由数据层填充")
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
                        onEntityStock = { page.peekSymbol = it },
                        onCardStock = { page.openStockDetail(it) },
                        onTerm = { page.viewModel.send("$it 是什么意思") },
                        onSuggestion = { page.viewModel.send(it) },
                    )
                }
            }
            vif({ page.peekSymbol.isNotEmpty() }) {
                val quote = MockDataBank.quote(page.peekSymbol)
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
                    }
                    View {
                        attr { flex(1f) }
                        Text {
                            attr {
                                text(quote?.let { "${it.name}  ${it.symbol}" } ?: page.peekSymbol)
                                fontSize(12f)
                                fontWeightSemiBold()
                                color(page.theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(quote?.let { "${Format.price(it.price)}  ${Format.percent(it.changePercent)}" } ?: "暂时没有行情")
                                marginTop(4f)
                                fontSize(15f)
                                color(
                                    when {
                                        quote == null -> page.theme.textTertiary
                                        quote.rising -> page.theme.rise
                                        else -> page.theme.fall
                                    },
                                )
                            }
                        }
                    }
                    View {
                        attr { padding(9f); borderRadius(9f); backgroundColor(page.theme.surfaceMuted) }
                        Text { attr { text("收起"); fontSize(11f); color(page.theme.textSecondary) } }
                        event { click { page.peekSymbol = "" } }
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
                    paddingBottom(12f + page.pagerData.safeAreaInsets.bottom)
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
}

private fun ViewContainer<*, *>.ChatMessageView(
    message: ChatMessage,
    theme: StockChatTheme,
    onEntityStock: (String) -> Unit,
    onCardStock: (String) -> Unit,
    onTerm: (String) -> Unit,
    onSuggestion: (String) -> Unit,
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
                            AssistantContent(message, theme, onEntityStock, onCardStock, onTerm, onSuggestion)
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
    onEntityStock: (String) -> Unit,
    onCardStock: (String) -> Unit,
    onTerm: (String) -> Unit,
    onSuggestion: (String) -> Unit,
) {
    val blocks = AiResponseLexer.lex(message.content, finished = !message.streaming)
    blocks.forEach { block ->
        when (block) {
            is TextBlock -> {
                View {
                    attr { marginTop(4f) }
                    EntityRichText(block.content, theme, onEntityStock, onTerm)
                }
            }
            is CardBlock -> {
                val intent = CardPayloadParser.parse(block.type, block.payload)
                if (intent is SuggestionsIntent) SuggestionRow(intent, theme, onSuggestion)
                else CardShell(
                    CardAssembler.assemble(block),
                    CardContext(theme, CardDensity.COMPACT, onCardStock, onTerm),
                )
            }
            is SkeletonBlock -> CardShell(
                CardAssembler.assemble(block),
                CardContext(theme, CardDensity.COMPACT, onCardStock, onTerm),
            )
        }
    }
    if (message.id == "m1") {
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
                text(if (message.failed) "生成失败，请重试" else "AI 生成，仅供参考，不构成投资建议")
                marginTop(10f)
                fontSize(9f)
                color(theme.textTertiary)
            }
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
