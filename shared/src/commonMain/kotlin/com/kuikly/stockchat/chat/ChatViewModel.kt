package com.kuikly.stockchat.chat

import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.provider.FallbackAiProvider
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

class ChatViewModel(override val pagerId: String, apiKey: String = "") : PagerScope {
    var messages: ObservableList<ChatMessage> by observableList()
    var inputText: String by observable("")
    var streamState: StreamState by observable(StreamState.IDLE)
    private val aiProvider: AiProvider = FallbackAiProvider(pagerId, apiKey)
    private var nextId = 1

    init {
        messages.add(
            ChatMessage(
                pagerId = pagerId,
                id = newId(),
                role = MessageRole.ASSISTANT,
                content = "你好，我是股问。可以问我一只股票为什么涨跌、当前走势，或一个金融术语是什么意思。",
            ),
        )
    }

    fun send(question: String = inputText) {
        val value = question.trim()
        if (value.isEmpty() || streamState == StreamState.STREAMING) return
        inputText = ""
        messages.add(ChatMessage(pagerId, newId(), MessageRole.USER, value))
        val assistantId = newId()
        val assistantMessage = ChatMessage(pagerId, assistantId, MessageRole.ASSISTANT, "正在组织回答…", streaming = true)
        messages.add(assistantMessage)
        streamState = StreamState.STREAMING
        var content = ""
        aiProvider.ask(
            question = value,
            onDelta = { delta ->
                content += delta
                assistantMessage.content = content.substringBefore("```card").trim().ifEmpty { "正在整理结构化信息…" }
            },
            onDone = {
                streamState = StreamState.IDLE
                assistantMessage.content = content
                assistantMessage.streaming = false
            },
            onError = { error ->
                streamState = StreamState.ERROR
                assistantMessage.content = error
                assistantMessage.streaming = false
                assistantMessage.failed = true
            },
        )
    }

    fun stop() {
        aiProvider.stop()
        streamState = StreamState.STOPPED
        val index = messages.lastIndex
        if (index >= 0 && messages[index].streaming) {
            messages[index].streaming = false
        }
    }

    fun clear() {
        stop()
        messages.clear()
        messages.add(ChatMessage(pagerId, newId(), MessageRole.ASSISTANT, "新会话已开始。想先看哪只股票？"))
        streamState = StreamState.IDLE
    }

    private fun newId(): String = "m${nextId++}"

}
