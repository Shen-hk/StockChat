package com.kuikly.stockchat

import com.kuikly.stockchat.chat.ChatContext
import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.ChatQuoteContext
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.composer.MentionEntity
import com.kuikly.stockchat.composer.MentionType
import com.kuikly.stockchat.composer.SendPayload
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.KLineInterval
import com.kuikly.stockchat.data.provider.KLinePoint
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePoint
import com.kuikly.stockchat.data.provider.QuoteProvider
import com.kuikly.stockchat.data.provider.QuoteRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatQuoteContextTest {

    private fun quote(
        symbol: String = "600519.SH",
        name: String = "贵州茅台",
        price: Double = 1421.0,
        previousClose: Double = 1433.30,
    ) = Quote(
        symbol = symbol,
        name = name,
        price = price,
        previousClose = previousClose,
        open = 1430.0,
        high = 1436.0,
        low = 1418.0,
        volume = 2_860_000.0,
        amount = 4_060_000_000.0,
        turnoverRate = 0.228,
        peTtm = 26.4,
        pb = 8.1,
        marketCap = 1_790_000_000_000.0,
        timestamp = "2026-09-07 09:47",
        source = "腾讯行情",
    )

    @Test
    fun symbolsComeFromMentionsFirstThenPlainTextScan() {
        val payload = SendPayload(
            text = "贵州茅台和五粮液哪个市值更大",
            mentions = listOf(MentionEntity("600519.SH", "贵州茅台", MentionType.STOCK, "@贵州茅台")),
            command = null,
            renderedPrompt = null,
        )
        // @ 提及优先，纯文本扫描补上五粮液，去重后不重复
        assertEquals(listOf("600519.SH", "000858.SZ"), ChatQuoteContext.resolveSymbols(payload))
    }

    @Test
    fun textOnlyQuestionsResolveByAliasAndName() {
        val payload = SendPayload(text = "大盘今天为什么跌", mentions = emptyList(), command = null, renderedPrompt = null)
        assertEquals(listOf("000001.SH"), ChatQuoteContext.resolveSymbols(payload))
    }

    @Test
    fun questionsWithoutResolvableSymbolGetNoInjection() {
        val payload = SendPayload(text = "PE 是什么意思", mentions = emptyList(), command = null, renderedPrompt = null)
        assertTrue(ChatQuoteContext.resolveSymbols(payload).isEmpty())
    }

    @Test
    fun onlineSnapshotIsInjectedAsQuoteNote() {
        val repository = QuoteRepository(
            online = SingleQuoteProvider(quote()),
            offline = OfflineProvider(quote()),
        )
        val payload = SendPayload(text = "茅台现在多少钱", mentions = emptyList(), command = null, renderedPrompt = null)
        var note: String? = null
        ChatQuoteContext.resolve(payload, repository) { note = it }

        val injected = requireNotNull(note)
        assertTrue(injected.startsWith("【实时行情注入】"))
        assertTrue(injected.contains("600519.SH 贵州茅台"))
        assertTrue(injected.contains("1421.00"))
        assertTrue(injected.contains("-0.86%"))
        assertTrue(injected.contains("禁止回答「无法获取实时行情」"))
    }

    @Test
    fun offlineSnapshotIsNeverInjectedAsRealtimeData() {
        // 在线拉不到（返回 null）→ 仓库降级到离线演示数据，绝不能伪装成实时行情注入
        val repository = QuoteRepository(
            online = SingleQuoteProvider(null),
            offline = OfflineProvider(quote()),
        )
        val payload = SendPayload(text = "茅台现在多少钱", mentions = emptyList(), command = null, renderedPrompt = null)
        var note: String? = "sentinel"
        ChatQuoteContext.resolve(payload, repository) { note = it }
        assertNull(note)
    }

    @Test
    fun quoteNoteIsAppendedAsSystemMessageBeforeConversation() {
        val messages = listOf(
            ChatMessage("p", "m1", MessageRole.USER, "上一问"),
            ChatMessage("p", "m2", MessageRole.ASSISTANT, "上一答"),
            ChatMessage("p", "m3", MessageRole.USER, "茅台现在多少钱"),
        )
        val built = ChatContext.build(messages, systemNote = "提及注记", quoteNote = "【实时行情注入】- 行情行")

        // 2 条 system 注记 + 3 条历史对话
        assertEquals(5, built.size)
        assertEquals("system", built[0].role)
        assertEquals("提及注记", built[0].content)
        assertEquals("system", built[1].role)
        assertEquals("【实时行情注入】- 行情行", built[1].content)
        assertEquals("user", built[2].role)
        assertEquals("上一问", built[2].content)
        assertEquals("user", built[4].role)
        assertEquals("茅台现在多少钱", built[4].content)
    }

    @Test
    fun describeContainsQuoteFactsAndAttribution() {
        val line = ChatQuoteContext.describe(quote())
        assertTrue(line.contains("来源：腾讯行情"))
        assertTrue(line.contains("截至 2026-09-07 09:47"))
        assertTrue(line.contains("换手率 +0.23%"))
        assertTrue(line.contains("总市值 17900.00亿"))
    }
}

/** 单一快照桩：模拟「在线源正常返回 / 拉不到（null）」两种情形。 */
private class SingleQuoteProvider(private val quote: Quote?) : QuoteProvider {
    override val mode = DataMode.ONLINE
    override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) = onResult(quote)
    override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) = onResult(emptyList())
    override fun kLines(symbol: String, count: Int, interval: KLineInterval, onResult: (List<KLinePoint>) -> Unit) =
        onResult(emptyList())
}

private class OfflineProvider(private val quote: Quote) : QuoteProvider {
    override val mode = DataMode.OFFLINE
    override fun snapshot(symbol: String, onResult: (Quote?) -> Unit) = onResult(quote)
    override fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit) = onResult(emptyList())
    override fun kLines(symbol: String, count: Int, interval: KLineInterval, onResult: (List<KLinePoint>) -> Unit) =
        onResult(emptyList())
}
