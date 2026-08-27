package com.kuikly.stockchat.chat

import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.provider.DeepSeekAiProvider
import com.kuikly.stockchat.data.provider.MockAiProvider
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

class ChatViewModel(override val pagerId: String) : PagerScope {
    var messages: ObservableList<ChatMessage> by observableList()
    var inputText: String by observable("")
    var streamState: StreamState by observable(StreamState.IDLE)
    var apiConfigured: Boolean by observable(false)
        private set
    private val configStore = AiConfigStore(pagerId)
    private val sessionStore = ChatSessionStore(pagerId)
    private var aiProvider: AiProvider? = null
    private var subThreadProvider: AiProvider? = null
    private var nextId = 1

    init {
        refreshConfigStatus()
        val restored = sessionStore.load()
        restored.forEach { item ->
            messages.add(ChatMessage(pagerId, item.id.ifEmpty { newId() }, item.role, item.content, failed = item.failed, cancelled = item.cancelled))
        }
        nextId = maxOf(nextId, messages.size + 1)
    }

    fun send(question: String = inputText) {
        val value = question.trim()
        if (value.isEmpty() || streamState == StreamState.STREAMING) return
        inputText = ""
        messages.add(ChatMessage(pagerId, newId(), MessageRole.USER, value))
        persist()
        val assistantId = newId()
        if (value.startsWith("回归：")) {
            streamWithProvider(MockAiProvider(pagerId), assistantId, value)
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
        val provider = DeepSeekAiProvider(pagerId, config)
        aiProvider = provider
        streamWithProvider(provider, assistantId, value)
    }

    private fun streamWithProvider(provider: AiProvider, assistantId: String, question: String) {
        aiProvider = provider
        val assistantMessage = ChatMessage(pagerId, assistantId, MessageRole.ASSISTANT, "正在组织回答…", streaming = true)
        messages.add(assistantMessage)
        streamState = StreamState.STREAMING
        var content = ""
        provider.ask(
            messages = ChatContext.build(messages),
            onDelta = { delta ->
                content += delta
                assistantMessage.content = content.substringBefore("```card").trim().ifEmpty { "正在整理结构化信息…" }
            },
            onDone = {
                streamState = StreamState.IDLE
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

    fun stop() {
        aiProvider?.stop()
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

    fun askSubThread(prompt: String, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val config = configStore.load()
        val configError = config.validationError()
        if (configError != null) {
            onError(configError)
            return
        }
        val provider = DeepSeekAiProvider(pagerId, config)
        subThreadProvider = provider
        provider.ask(
            messages = listOf(AiChatMessage("user", "$prompt\n请只用简洁文字解释，不要输出卡片协议。")),
            onDelta = onDelta,
            onDone = onDone,
            onError = onError,
        )
    }

    private fun persist() = sessionStore.save(messages)

    private fun newId(): String = "m${nextId++}"

}
