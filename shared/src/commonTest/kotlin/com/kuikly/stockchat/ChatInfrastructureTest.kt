package com.kuikly.stockchat

import com.kuikly.stockchat.chat.ChatContext
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.CardResponseFallback
import com.kuikly.stockchat.data.provider.SseEventParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatInfrastructureTest {
    @Test
    fun plainModelResponsesReceiveTheCardRequiredByQuestionIntent() {
        assertTrue(CardResponseFallback.appendMissingCard("贵州茅台最近有什么资讯", "正文").contains("```card:news"))
        assertTrue(CardResponseFallback.appendMissingCard("贵州茅台为什么涨", "正文").contains("```card:attribution"))
        assertTrue(CardResponseFallback.appendMissingCard("PE 是什么", "正文").contains("```card:definition"))
    }

    @Test
    fun modelCardResponseIsNotChangedByFallback() {
        val response = "正文\n```card:stock-quote\n{\"symbol\":\"600519.SH\"}\n```"
        assertEquals(response, CardResponseFallback.appendMissingCard("贵州茅台怎么样", response))
    }

    @Test
    fun sseParserReadsDeltaAndDoneEvents() {
        assertEquals("你好", SseEventParser.delta("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}"))
        assertEquals("", SseEventParser.delta("data: [DONE]"))
    }

    @Test
    fun contextKeepsLatestCompletedTurnsWithinBudget() {
        val messages = List(16) { index ->
            ChatMessage("test", "m$index", if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT, "消息$index")
        }
        val context = ChatContext.build(messages)
        assertEquals(ChatContext.MAX_MESSAGES, context.size)
        assertEquals("消息4", context.first().content)
        assertEquals("消息15", context.last().content)
        assertTrue(context.zipWithNext().all { it.first.role != it.second.role })
    }
}
