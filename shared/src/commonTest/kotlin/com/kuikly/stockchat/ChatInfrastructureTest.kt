package com.kuikly.stockchat

import com.kuikly.stockchat.chat.ChatContext
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.CardResponseSanitizer
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.chat.WatchlistIntent
import com.kuikly.stockchat.chat.WatchlistSummaryBuilder
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.data.provider.SseEventParser
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.BrokenCardBlock
import com.kuikly.stockchat.richtext.EntityMarkdownAdapter
import com.kuikly.stockchat.richtext.stripStreamingCardMarkup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatInfrastructureTest {
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
    fun cardBlockCanBeReplacedInPlace() {
        val original = "正文\n```card:stock-quote\n{\"symbol\":\"BAD\"}"
        val broken = AiResponseLexer.lex(original, finished = true).last() as BrokenCardBlock
        val replacement = "```card:stock-quote\n{\"symbol\":\"600519.SH\"}\n```"
        assertEquals("正文\n$replacement", AiResponseLexer.replaceCardBlock(original, broken.id, replacement))
    }

    @Test
    fun cardSanitizerRemovesCardsForSymbolsOutsideTheConversation() {
        val response = "正文\n\n```card:stock-quote\n{\"symbol\":\"600519.SH\"}\n```"

        val sanitized = CardResponseSanitizer.removeUnrelatedCards(
            question = "MACD 金叉是什么意思？",
            conversation = emptyList(),
            response = response,
        )

        assertEquals("正文", sanitized)
    }

    @Test
    fun cardSanitizerKeepsCardsForAnEstablishedSymbol() {
        val response = "正文\n\n```card:stock-quote\n{\"symbol\":\"600519.SH\"}\n```"

        val sanitized = CardResponseSanitizer.removeUnrelatedCards(
            question = "贵州茅台今天怎么样？",
            conversation = emptyList(),
            response = response,
        )

        assertEquals(response, sanitized)
    }

    @Test
    fun cardSanitizerKeepsMarketCardsForASymbolEstablishedInConversation() {
        val response = "结论\n\n```card:stock-chart\n{\"symbol\":\"600519.SH\"}\n```"

        val sanitized = CardResponseSanitizer.removeUnrelatedCards(
            question = "它接下来怎么看？",
            conversation = listOf(ChatMessage("test", "user-1", MessageRole.USER, "贵州茅台今天怎么样？")),
            response = response,
        )

        assertEquals(response, sanitized)
    }

    @Test
    fun cardSanitizerDoesNotAddOrKeepAnUnrequestedDefinitionCard() {
        val response = "PE 是市盈率。\n\n```card:definition\n{\"term\":\"市盈率 PE\"}\n```"

        val sanitized = CardResponseSanitizer.removeUnrelatedCards(
            question = "PE 是什么？",
            conversation = emptyList(),
            response = response,
        )

        assertEquals("PE 是市盈率。", sanitized)
    }

    @Test
    fun sseParserReadsDeltaAndDoneEvents() {
        assertEquals("你好", SseEventParser.delta("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}"))
        assertEquals("", SseEventParser.delta("data: [DONE]"))
    }

    @Test
    fun sseParserSkipsReasoningAndAcceptsTypedContent() {
        assertEquals("", SseEventParser.delta("data: {\"choices\":[{\"delta\":{\"content\":null,\"reasoning_content\":\"scratch work\"}}]}"))
        assertEquals("你好", SseEventParser.delta("data: {\"choices\":[{\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"你\"},{\"type\":\"text\",\"text\":\"好\"}]}}]}"))
    }

    @Test
    fun entityMarkdownAdapterKeepsMarkdownAndAddsInternalEntityLinks() {
        val adapted = EntityMarkdownAdapter.withEntityLinks("## 结论\n\n- **贵州茅台**的 PE 偏高")

        assertEquals("## 结论\n\n- **[贵州茅台](stockchat-entity://0)**的 [PE](stockchat-entity://1) 偏高", adapted.content)
        assertEquals(listOf("贵州茅台", "PE"), adapted.entities.map { it.text })
    }

    @Test
    fun entityMarkdownAdapterPreservesQuoteAndTableSyntaxForSharedRendering() {
        val markdown = "> 来源：样例记录\n\n| 字段甲 | 字段乙 |\n| --- | --- |\n| A | 12% |"

        assertEquals(markdown, EntityMarkdownAdapter.withEntityLinks(markdown).content)
    }

    @Test
    fun streamingCardMarkupIsHiddenUntilTheStructuredCardCanMount() {
        val openCard = "先给结论\n```card:stock-quote\n{\"symbol\":\"600519.SH\"}"
        val completedCard = "$openCard\n```\n补充说明"

        assertEquals("先给结论\n\n> 正在准备行情卡片…\n\n", stripStreamingCardMarkup(openCard))
        assertEquals("先给结论\n\n> 正在准备行情卡片…\n\n\n补充说明", stripStreamingCardMarkup(completedCard))
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

    @Test
    fun typewriterRevealCountTypesSlowlyAndCatchesUpWhenBacklogged() {
        // 小积压：一字一拍（打字机手感）
        assertEquals(1, TypewriterSmoother.revealCount(1))
        assertEquals(1, TypewriterSmoother.revealCount(2))
        // 大积压：按固定追平节奏加速释放
        assertTrue(TypewriterSmoother.revealCount(100) > 1)
        assertTrue(TypewriterSmoother.revealCount(100) <= TypewriterSmoother.MAX_CHARS_PER_TICK)
        assertTrue(TypewriterSmoother.revealCount(1000) == TypewriterSmoother.MAX_CHARS_PER_TICK)
        // 永不超过积压本身
        for (backlog in 0..32) assertTrue(TypewriterSmoother.revealCount(backlog) <= backlog)
    }

    @Test
    fun typewriterHidesCardProtocolIncludingPartialFence() {
        // 完整 fence：从头截断
        assertEquals("正文", TypewriterSmoother.hideCardProtocol("正文```card:stock-quote\n{}"))
        // 结尾半个 fence 前缀：扣住不显示
        assertEquals("正文", TypewriterSmoother.hideCardProtocol("正文`"))
        assertEquals("正文", TypewriterSmoother.hideCardProtocol("正文``"))
        assertEquals("正文", TypewriterSmoother.hideCardProtocol("正文```c"))
        // 普通反引号不在结尾时不误伤
        assertEquals("代`码`高亮", TypewriterSmoother.hideCardProtocol("代`码`高亮"))
    }
}
