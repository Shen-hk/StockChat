package com.kuikly.stockchat

import com.kuikly.stockchat.chat.ChatContext
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.CardResponseFallback
import com.kuikly.stockchat.chat.WatchlistIntent
import com.kuikly.stockchat.chat.WatchlistSummaryBuilder
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.data.provider.SseEventParser
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.BrokenCardBlock
import com.kuikly.stockchat.richtext.EntityMarkdownAdapter
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
    fun watchlistIntentMatchesPortfolioQuestionsOnly() {
        listOf(
            "我的自选今天怎么样",
            "我持有的股票最近表现如何",
            "我的票涨跌情况",
            "关注的股票复盘一下",
            "自选股还好吗",
        ).forEach { question ->
            assertTrue(WatchlistIntent.matches(question), "应命中自选问句：$question")
        }
        assertTrue(!WatchlistIntent.matches("打开自选股"))
        assertTrue(!WatchlistIntent.matches("贵州茅台今天怎么样"))
    }

    @Test
    fun watchlistSummaryIsADeterministicDomainResponse() {
        val items = listOf(
            WatchlistItem("600519.SH", "贵州茅台"),
            WatchlistItem("000858.SZ", "五粮液"),
        )
        val summary = WatchlistSummaryBuilder.build(items, MockDataBank::quote)

        assertTrue(summary.contains("当前统计"))
        assertTrue(summary.contains("```card:stock-quote"))
        assertTrue(summary.contains("600519.SH"))
        assertTrue(summary.contains("000858.SZ"))
    }

    @Test
    fun incompleteFinalCardBlocksBecomeRetryableBrokenBlocks() {
        val blocks = AiResponseLexer.lex("正文\n```card:stock-quote\n{\"symbol\":\"600519.SH\"}", finished = true)
        assertEquals(2, blocks.size)
        assertTrue(blocks.last() is BrokenCardBlock)
        assertEquals("stock-quote", (blocks.last() as BrokenCardBlock).type)
    }

    @Test
    fun incompleteCardResponseIsNotChangedByFallback() {
        val response = "正文\n```card:stock-quote\n{\"symbol\":\"600519.SH\"}"
        val fixed = CardResponseFallback.appendMissingCard("贵州茅台怎么样", response)
        assertEquals(response, fixed)
    }

    @Test
    fun cardBlockCanBeReplacedInPlace() {
        val original = "正文\n```card:stock-quote\n{\"symbol\":\"BAD\"}"
        val broken = AiResponseLexer.lex(original, finished = true).last() as BrokenCardBlock
        val replacement = "```card:stock-quote\n{\"symbol\":\"600519.SH\"}\n```"
        assertEquals("正文\n$replacement", AiResponseLexer.replaceCardBlock(original, broken.id, replacement))
    }

    @Test
    fun sseParserReadsDeltaAndDoneEvents() {
        assertEquals("你好", SseEventParser.delta("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}"))
        assertEquals("", SseEventParser.delta("data: [DONE]"))
    }

    @Test
    fun entityMarkdownAdapterKeepsMarkdownAndAddsInternalEntityLinks() {
        val adapted = EntityMarkdownAdapter.withEntityLinks("## 结论\n\n- **贵州茅台**的 PE 偏高")

        assertEquals("## 结论\n\n- **[贵州茅台](stockchat-entity://0)**的 [PE](stockchat-entity://1) 偏高", adapted.content)
        assertEquals(listOf("贵州茅台", "PE"), adapted.entities.map { it.text })
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
