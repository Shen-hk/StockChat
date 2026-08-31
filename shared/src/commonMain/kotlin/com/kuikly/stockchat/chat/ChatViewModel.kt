package com.kuikly.stockchat.chat

import com.kuikly.stockchat.composer.SendPayload
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.provider.MockAiProvider
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.CardBlock
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

class ChatViewModel(
    override val pagerId: String,
    private val dependencies: ChatDependencies = ChatDependencies.forPager(pagerId),
) : PagerScope {
    var messages: ObservableList<ChatMessage> by observableList()
    var sessionSummaries: ObservableList<ChatSessionSummary> by observableList()
    // Composer draft mirror. ChatPage is the only writer so a native TextArea update and
    // this observable can be committed together; sending/session operations must not
    // mutate it behind the editor's back.
    var inputText: String by observable("")
    var streamState: StreamState by observable(StreamState.IDLE)
    var apiConfigured: Boolean by observable(false)
        private set
    private val configStore = dependencies.configStore
    private val sessionStore = dependencies.sessionStore
    private val watchlistStore = dependencies.watchlistStore
    private val quoteRepository = dependencies.quoteRepository
    private var aiProvider: AiProvider? = null
    private var subThreadProvider: AiProvider? = null
    private var cardRepairProvider: AiProvider? = null
    var activeSessionId: String by observable("")
        private set
    private var nextId = 1

    init {
        refreshConfigStatus()
        activeSessionId = sessionStore.activeSessionId()
        restoreMessages(sessionStore.load(activeSessionId))
        refreshSessionSummaries()
    }

    fun send(question: String = inputText) {
        send(
            SendPayload(
                text = question,
                mentions = emptyList(),
                command = null,
                renderedPrompt = null,
            )
        )
    }

    /**
     * 结构化发送（规范 10 §4.8）：输入期固化的 mentions / command 随消息一起进入管线。
     * 用户气泡显示 displayText（人话形式），模型侧收到 renderedPrompt + system 注记。
     */
    fun send(payload: SendPayload) {
        val value = payload.displayText
        if (value.isEmpty() || streamState == StreamState.STREAMING) return
        messages.add(ChatMessage(pagerId, newId(), MessageRole.USER, value))
        persist()
        val assistantId = newId()
        if (WatchlistIntent.matches(value)) {
            replyWithWatchlistSummary(assistantId)
            return
        }
        if (payload.text.startsWith("回归：")) {
            streamWithProvider(MockAiProvider(pagerId), assistantId, payload)
            return
        }
        val config = configStore.load()
        val configError = config.validationError()
        if (configError != null) {
            apiConfigured = false
            messages.add(
                ChatMessage(
                    pagerId,
                    assistantId,
                    MessageRole.ASSISTANT,
                    "$configError。请先打开右上角“API 设置”完成配置。",
                    failed = true,
                ),
            )
            streamState = StreamState.ERROR
            persist()
            return
        }
        apiConfigured = true
        val provider = dependencies.aiProviderFactory(config)
        aiProvider = provider
        streamWithProvider(provider, assistantId, payload)
    }

    private fun streamWithProvider(provider: AiProvider, assistantId: String, payload: SendPayload) {
        aiProvider = provider
        val assistantMessage = ChatMessage(pagerId, assistantId, MessageRole.ASSISTANT, "正在组织回答…", streaming = true)
        messages.add(assistantMessage)
        streamState = StreamState.STREAMING
        var content = ""
        provider.ask(
            messages = ChatContext.build(messages, payload.systemNote()),
            onDelta = { delta ->
                content += delta
                assistantMessage.content = content.substringBefore("```card").trim().ifEmpty { "正在整理结构化信息…" }
            },
            onDone = {
                streamState = StreamState.IDLE
                val question = payload.renderedPrompt ?: payload.text
                assistantMessage.content = CardResponseFallback.appendMissingCard(question, content)
                assistantMessage.streaming = false
                persist()
            },
            onError = { error ->
                streamState = StreamState.ERROR
                assistantMessage.content = error
                assistantMessage.streaming = false
                assistantMessage.failed = true
                persist()
            },
        )
    }

    private fun replyWithWatchlistSummary(assistantId: String) {
        val items = watchlistStore.list()
        messages.add(
            ChatMessage(
                pagerId,
                assistantId,
                MessageRole.ASSISTANT,
                WatchlistSummaryBuilder.build(items, quoteRepository::cachedOrOffline),
            ),
        )
        streamState = StreamState.IDLE
        persist()
    }

    fun stop() {
        aiProvider?.stop()
        cardRepairProvider?.stop()
        streamState = StreamState.STOPPED
        val index = messages.lastIndex
        if (index >= 0 && messages[index].streaming) {
            messages[index].streaming = false
            messages[index].cancelled = true
        }
        persist()
    }

    fun clear() {
        stop()
        messages.clear()
        streamState = StreamState.IDLE
        persist()
    }

    fun startNewChat() {
        stop()
        if (messages.isNotEmpty()) {
            activeSessionId = sessionStore.startSession()
        }
        messages.clear()
        streamState = StreamState.IDLE
        nextId = 1
        refreshSessionSummaries()
    }

    fun openSession(sessionId: String) {
        if (sessionId.isBlank() || sessionId == activeSessionId) return
        stop()
        activeSessionId = sessionId
        messages.clear()
        restoreMessages(sessionStore.load(sessionId))
        streamState = StreamState.IDLE
        refreshSessionSummaries()
    }

    fun refreshConfigStatus() {
        apiConfigured = configStore.load().validationError() == null
    }

    fun retryLast() {
        val failed = messages.lastOrNull { it.role == MessageRole.ASSISTANT && it.failed } ?: return
        messages.remove(failed)
        val question = messages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        persist()
        if (question.isNotBlank()) send(question)
    }

    fun retryCard(
        messageId: String,
        blockId: String,
        cardType: String,
        rawCard: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex < 0) {
            onError("原消息不存在")
            return
        }
        val question = previousUserQuestion(messageIndex)
        val provider = if (question.startsWith("回归：")) {
            MockAiProvider(pagerId)
        } else {
            val config = configStore.load()
            val configError = config.validationError()
            if (configError != null) {
                onError(configError)
                return
            }
            dependencies.aiProviderFactory(config)
        }
        cardRepairProvider = provider
        var response = ""
        provider.ask(
            messages = listOf(AiChatMessage("user", buildCardRetryPrompt(messageIndex, cardType, rawCard))),
            onDelta = { response += it },
            onDone = {
                val replacement = normalizeRepairedCard(cardType, response)
                if (replacement == null) {
                    onError("模型未返回可用卡片")
                    return@ask
                }
                messages.getOrNull(messageIndex)?.let { message ->
                    message.content = AiResponseLexer.replaceCardBlock(message.content, blockId, replacement)
                    persist()
                }
                onDone()
            },
            onError = onError,
        )
    }

    fun askSubThread(prompt: String, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val config = configStore.load()
        val configError = config.validationError()
        if (configError != null) {
            onError(configError)
            return
        }
        val provider = dependencies.aiProviderFactory(config)
        subThreadProvider = provider
        provider.ask(
            messages = listOf(AiChatMessage("user", "$prompt\n请只用简洁文字解释，不要输出卡片协议。")),
            onDelta = onDelta,
            onDone = onDone,
            onError = onError,
        )
    }

    private fun persist() {
        sessionStore.save(activeSessionId, messages)
        refreshSessionSummaries()
    }

    private fun refreshSessionSummaries() {
        sessionSummaries.clear()
        sessionSummaries.addAll(sessionStore.listSummaries())
    }

    private fun restoreMessages(restored: List<StoredChatMessage>) {
        restored.forEach { item ->
            messages.add(ChatMessage(pagerId, item.id.ifEmpty { newId() }, item.role, item.content, failed = item.failed, cancelled = item.cancelled))
        }
        nextId = nextMessageId(messages)
    }

    private fun buildCardRetryPrompt(messageIndex: Int, cardType: String, rawCard: String): String {
        val question = previousUserQuestion(messageIndex)
        return """
            上一轮用户问题：
            $question

            下面这张结构化卡片加载失败，请只重新生成这一张卡片。
            卡片类型必须是：$cardType
            原始卡片内容：
            $rawCard

            只输出一个完整卡片块，不要解释，不要输出正文：
            ```card:$cardType
            ${cardRetryPayloadExample(cardType)}
            ```
            JSON 只描述卡片意图，不要编造实时价格；实时行情由客户端填充。
        """.trimIndent()
    }

    private fun cardRetryPayloadExample(cardType: String): String = when (cardType) {
        "definition" -> "{\"term\":\"市盈率 PE\",\"plainText\":\"用一句话解释概念\",\"example\":\"给出一个简短例子\"}"
        "attribution" -> "{\"symbol\":\"600519.SH\",\"direction\":\"fall\",\"factors\":[{\"name\":\"资金面\",\"weight\":0.4,\"confidence\":\"medium\",\"desc\":\"简短说明\",\"source\":\"行情数据推断\"}]}"
        "suggestions" -> "{\"chips\":[{\"text\":\"继续追问\",\"type\":\"drill\"}]}"
        else -> "{\"symbol\":\"600519.SH\"}"
    }

    private fun normalizeRepairedCard(cardType: String, response: String): String? {
        val trimmed = response.trim()
        val card = AiResponseLexer.lex(trimmed, finished = true)
            .firstOrNull { it is CardBlock && (cardType.isBlank() || it.type == cardType) } as? CardBlock
        if (card != null) return "```card:${card.type}\n${card.payload}\n```"
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && cardType.isNotBlank()) {
            return "```card:$cardType\n$trimmed\n```"
        }
        return null
    }

    private fun previousUserQuestion(messageIndex: Int): String =
        messages.take(messageIndex).lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()

    private fun newId(): String = "m${nextId++}"

    private fun nextMessageId(messages: List<ChatMessage>): Int =
        (messages.mapNotNull { it.id.removePrefix("m").toIntOrNull() }.maxOrNull() ?: 0) + 1

}
