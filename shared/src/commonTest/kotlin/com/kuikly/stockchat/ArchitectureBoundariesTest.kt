package com.kuikly.stockchat

import com.kuikly.stockchat.chat.ChatMessage
import com.kuikly.stockchat.chat.ChatSessionStore
import com.kuikly.stockchat.chat.MessageRole
import com.kuikly.stockchat.chat.MessageAttachment
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureBoundariesTest {

    @Test
    fun configStoreUsesTheStoragePortWithoutAPager() {
        val storage = InMemoryKeyValueStorage()
        val store = AiConfigStore(storage)

        store.save(AiConfig(" https://example.com/chat/ ", " model ", " secret "))

        assertEquals(AiConfig("https://example.com/chat", "model", "secret"), store.load())
        store.clear()
        assertEquals(AiConfig(), store.load())
    }

    @Test
    fun watchlistStorePersistsThroughAnInjectedStoragePort() {
        val storage = InMemoryKeyValueStorage()
        val writer = WatchlistStore(storage, nowMillis = { 42L })

        assertEquals(WatchlistAddResult.ADDED, writer.add("600519.SH", "贵州茅台"))
        assertEquals(WatchlistAddResult.ALREADY_IN, writer.add("600519.SH", "贵州茅台"))

        val reader = WatchlistStore(storage, nowMillis = { 99L })
        assertTrue(reader.contains("600519.SH"))
        assertEquals(42L, reader.list().single().addedAtMillis)
        reader.remove("600519.SH")
        assertFalse(writer.contains("600519.SH"))
    }

    @Test
    fun chatSessionsCanBeRestoredWithoutPlatformModules() {
        val storage = InMemoryKeyValueStorage()
        val writer = ChatSessionStore(storage, nowMillis = { 1_000L })
        assertFalse(writer.hasSessions())

        val sessionId = writer.startSession()
        writer.save(
            sessionId,
            listOf(
                ChatMessage("test", "m1", MessageRole.USER, "贵州茅台最近怎么样"),
                ChatMessage("test", "m2", MessageRole.ASSISTANT, "这里是解释"),
            ),
        )

        val reader = ChatSessionStore(storage, nowMillis = { 1_500L })
        val restored = reader.load(sessionId)

        assertTrue(reader.hasSessions())
        assertEquals(listOf("贵州茅台最近怎么样", "这里是解释"), restored.map { it.content })
        assertEquals("贵州茅台最近怎么样", reader.listSummaries().single().title)
    }

    @Test
    fun chatSessionRestoresAttachmentMetadata() {
        val storage = InMemoryKeyValueStorage()
        val writer = ChatSessionStore(storage, nowMillis = { 1_000L })
        val sessionId = writer.startSession()
        writer.save(
            sessionId,
            listOf(
                ChatMessage(
                    "test", "m1", MessageRole.USER, "请分析附件",
                    attachments = listOf(
                        MessageAttachment("a1", "/private/media/chart.png", "图表.png", true),
                        MessageAttachment("a2", "/private/media/report.pdf", "研报.pdf", false),
                    ),
                ),
            ),
        )

        val attachments = ChatSessionStore(storage, nowMillis = { 1_500L })
            .load(sessionId)
            .single()
            .attachments
        assertEquals(listOf("/private/media/chart.png", "/private/media/report.pdf"), attachments.map { it.path })
        assertEquals(listOf(true, false), attachments.map { it.isImage })
    }

    @Test
    fun emptyActiveSessionSurvivesRestartWhenHistoryExists() {
        val storage = InMemoryKeyValueStorage()
        val writer = ChatSessionStore(storage, nowMillis = { 1_000L })
        val historySessionId = writer.startSession()
        writer.save(
            historySessionId,
            listOf(ChatMessage("test", "m1", MessageRole.USER, "贵州茅台最近怎么样")),
        )

        val emptySessionId = writer.startSession()
        val reader = ChatSessionStore(storage, nowMillis = { 1_500L })

        assertEquals(emptySessionId, reader.activeSessionId())
        assertEquals(emptyList(), reader.load(reader.activeSessionId()))
        assertEquals(1, reader.listSummaries().size)
    }
}
