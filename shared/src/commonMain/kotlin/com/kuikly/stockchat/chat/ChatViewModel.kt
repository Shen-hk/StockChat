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
import com.tencent.kuikly.core.timer.setTimeout

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
    var hasSessionHistory: Boolean by observable(false)
        private set
    private val configStore = dependencies.configStore
    private val sessionStore = dependencies.sessionStore
    private val watchlistStore = dependencies.watchlistStore
    private val quoteRepository = dependencies.quoteRepository
    private var aiProvider: AiProvider? = null
    private var subThreadProvider: AiProvider? = null
    private var cardRepairProvider: AiProvider? = null
    private var activeTypewriter: TypewriterSmoother? = null
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
        messages.add(ChatMessage(pagerId, newId(), MessageRole.USER, value, attachments = payload.attachments))
        persist()
        respondTo(value, payload)
    }

    /**
     * 回复分发：send 在追加用户气泡后调用；重新生成（regenerateAt）与失败重试
     * （retryLast）复用同一管线但不重复插入用户消息。
     */
    private fun respondTo(value: String, payload: SendPayload) {
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

    /**
     * 重新生成某条 AI 回复：从该条起截断（含其后所有消息），用上一条用户提问
     * 重新走回复管线——不重复插入用户气泡。操作栏（复制/重试/分享）的「重试」
     * 即此能力；流式进行中拒绝重入。
     */
    fun regenerateAt(messageId: String): Boolean {
        if (streamState == StreamState.STREAMING) return false
        val index = messages.indexOfFirst { it.id == messageId && it.role == MessageRole.ASSISTANT }
        if (index < 0) return false
        val question = messages.take(index).lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        if (question.isBlank()) return false
        while (messages.size > index) messages.removeAt(messages.size - 1)
        persist()
        respondTo(
            question,
            SendPayload(text = question, mentions = emptyList(), command = null, renderedPrompt = null),
        )
        return true
    }

    private fun streamWithProvider(provider: AiProvider, assistantId: String, payload: SendPayload) {
        aiProvider = provider
        val assistantMessage = ChatMessage(pagerId, assistantId, MessageRole.ASSISTANT, "正在组织回答…", streaming = true)
        messages.add(assistantMessage)
        streamState = StreamState.STREAMING
        // 先解析行情上下文（端侧拉最新快照注入给模型，正文与卡片数字同源），再流式请求。
        // 快照链保证回调，但网络层极端挂起时由 watchdog 兜底：3s 未返回则按无行情发送。
        var launched = false
        fun launch(note: String?) {
            if (launched) return
            launched = true
            var content = ""
            // 打字机平滑：delta 全量进缓冲，显示端按节拍逐字释放（见 TypewriterSmoother）。
            // 网络一次吐一大段时不再是"整行蹦出"，而是匀速逐字打出；积压越大吐字越快。
            val typewriter = TypewriterSmoother(pagerId) { revealed ->
                assistantMessage.content = TypewriterSmoother
                    .hideCardProtocol(revealed)
                    .trim()
                    .ifEmpty { "正在整理结构化信息…" }
            }
            activeTypewriter = typewriter
            provider.ask(
                messages = ChatContext.build(messages, payload.systemNote(), note),
                onDelta = { delta ->
                    content += delta
                    typewriter.append(delta)
                },
                onDone = {
                    streamState = StreamState.IDLE
                    val question = payload.renderedPrompt ?: payload.text
                    // 收尾等显示端把已收到的文本打完再落定，避免最后一截被整段顶上来。
                    typewriter.complete {
                        assistantMessage.content = CardResponseFallback.appendMissingCard(question, content)
                        assistantMessage.streaming = false
                        persist()
                    }
                },
                onError = { error ->
                    streamState = StreamState.ERROR
                    typewriter.cancel()
                    assistantMessage.content = error
                    assistantMessage.streaming = false
                    assistantMessage.failed = true
                    persist()
                },
            )
        }
        ChatQuoteContext.resolve(payload, quoteRepository, ::launch)
        this.setTimeout(3000) { launch(null) }
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
        // 停止生成：先把打字机缓冲里已收到的文本一次性显示（之后终止节拍），
        // 再掐断网络流——与旧行为一致，已收到的半截回答保留可见。
        activeTypewriter?.let { it.flushNow(); it.cancel() }
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
        // 每次点击「新建」都必须换一个会话身份，即使当前会话恰好是空的。
        // 除了语义正确，这也给视图层一个稳定的重建键，避免空列表的同值 clear()
        // 被原生列表复用路径吞掉后留在空白画面。
        activeSessionId = sessionStore.startSession()
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
        // respondTo 而非 send：不重复插入用户气泡，直接重新生成回复。
        if (question.isNotBlank()) {
            respondTo(
                question,
                SendPayload(text = question, mentions = emptyList(), command = null, renderedPrompt = null),
            )
        }
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
        hasSessionHistory = sessionStore.hasSessions()
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
