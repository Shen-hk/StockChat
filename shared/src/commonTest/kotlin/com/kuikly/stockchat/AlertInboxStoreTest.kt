package com.kuikly.stockchat

import com.kuikly.stockchat.data.AlertInboxStore
import com.kuikly.stockchat.data.AlertKind
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertInboxStoreTest {
    @Test
    fun deferredMessagePersistsAndLeavesUnreadCount() {
        val storage = InMemoryKeyValueStorage()
        val store = AlertInboxStore(storage, nowMillis = { 42L })
        val message = message("MOVE:600519.SH:3.0")

        assertEquals(1, store.unreadCount(listOf(message)))
        assertTrue(store.toggleDeferred(message.id))
        assertEquals(setOf(message.id), store.deferredIds())
        assertEquals(setOf(message.id), store.readIds())
        assertEquals(0, store.unreadCount(listOf(message)))

        val restored = AlertInboxStore(storage, nowMillis = { 43L })
        assertEquals(setOf(message.id), restored.deferredIds())
        assertFalse(restored.toggleDeferred(message.id))
        assertTrue(restored.deferredIds().isEmpty())
        assertEquals(setOf(message.id), restored.readIds())
    }

    @Test
    fun emptyIdCannotEnterDeferredQueue() {
        val store = AlertInboxStore(InMemoryKeyValueStorage())

        assertFalse(store.toggleDeferred(""))
        assertTrue(store.deferredIds().isEmpty())
    }

    @Test
    fun deleteMessageRemovesExtraAndPersistsDismissal() {
        val storage = InMemoryKeyValueStorage()
        val store = AlertInboxStore(storage, nowMillis = { 42L })
        val extra = message("MOCK_STOCK_ALERT:42")
        store.putExtraMessage(extra)
        assertEquals(listOf(extra), store.extraMessages())

        store.deleteMessage(extra.id)

        assertTrue(store.extraMessages().isEmpty())
        assertEquals(setOf(extra.id), store.dismissedIds())

        val restored = AlertInboxStore(storage, nowMillis = { 43L })
        assertTrue(restored.extraMessages().isEmpty())
        assertEquals(setOf(extra.id), restored.dismissedIds())
    }

    @Test
    fun emptyIdCannotBeDeleted() {
        val store = AlertInboxStore(InMemoryKeyValueStorage())

        store.deleteMessage("")

        assertTrue(store.dismissedIds().isEmpty())
    }

    private fun message(id: String) = AlertMessage(
        id = id,
        kind = AlertKind.MOVE,
        symbol = "600519.SH",
        name = "贵州茅台",
        title = "贵州茅台出现异动",
        summary = "日内涨跌幅达到预警阈值",
        facts = listOf("事实 1"),
        createdAtMillis = 1L,
        askQuestion = "发生了什么？",
    )
}
