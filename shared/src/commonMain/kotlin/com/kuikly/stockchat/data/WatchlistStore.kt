package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

data class WatchlistItem(
    val symbol: String,
    val name: String,
    val addedAtMillis: Long = 0L,
    val sortOrder: Int = 0,
    // 二期（P2）预留：分组与提醒规则。v1 就写进 schema，避免迁移成本。
    val groupId: String = "",
    val alertRules: String = "",
)

enum class WatchlistAddResult { ADDED, ALREADY_IN, FULL }

/**
 * 自选股本设备存储（12 号需求文档 FR-W1）。
 *
 * 股问是「无记忆的单次问答」：会话内标的消歧只记忆当次会话，用户每次都要重新输入标的名。
 * 自选让「我的票今天怎么样」这句最自然的问题有数据支撑。Demo 阶段无账号体系，
 * 因此只在本地持久化、不云端同步，容量上限 50 只。
 */
class WatchlistStore(
    private val preferences: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    constructor(
        pagerId: String,
        nowMillis: () -> Long = ::platformCurrentTimeMillis,
    ) : this(PagerKeyValueStorage(pagerId), nowMillis)

    fun list(): List<WatchlistItem> = readRows()

    fun symbols(): List<String> = readRows().map { it.symbol }

    fun contains(symbol: String): Boolean = readRows().any { it.symbol == symbol }

    fun add(symbol: String, name: String): WatchlistAddResult {
        val rows = readRows().toMutableList()
        if (rows.any { it.symbol == symbol }) return WatchlistAddResult.ALREADY_IN
        if (rows.size >= MAX_ITEMS) return WatchlistAddResult.FULL
        rows += WatchlistItem(
            symbol = symbol,
            name = name,
            addedAtMillis = nowMillis(),
            sortOrder = rows.size,
        )
        writeRows(rows)
        return WatchlistAddResult.ADDED
    }

    fun remove(symbol: String) {
        writeRows(readRows().filterNot { it.symbol == symbol })
    }

    fun restore(item: WatchlistItem): WatchlistAddResult {
        val rows = readRows().toMutableList()
        if (rows.any { it.symbol == item.symbol }) return WatchlistAddResult.ALREADY_IN
        if (rows.size >= MAX_ITEMS) return WatchlistAddResult.FULL
        rows += item
        writeRows(rows)
        return WatchlistAddResult.ADDED
    }

    private fun readRows(): List<WatchlistItem> {
        val raw = preferences.getString(KEY)
        if (raw.isEmpty()) return emptyList()
        return try {
            val rows = JSONArray(raw)
            buildList {
                repeat(rows.length()) { index ->
                    val item = rows.optJSONObject(index) ?: return@repeat
                    val symbol = item.optString("symbol")
                    if (symbol.isNotBlank()) {
                        add(
                            WatchlistItem(
                                symbol = symbol,
                                name = item.optString("name"),
                                addedAtMillis = item.optString("addedAtMillis").toLongOrNull() ?: 0L,
                                sortOrder = item.optString("sortOrder").toIntOrNull() ?: index,
                                groupId = item.optString("groupId"),
                                alertRules = item.optString("alertRules"),
                            ),
                        )
                    }
                }
            }.sortedBy { it.sortOrder }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writeRows(rows: List<WatchlistItem>) {
        val array = JSONArray()
        rows.take(MAX_ITEMS).forEach { item ->
            array.put(JSONObject().apply {
                put("symbol", item.symbol)
                put("name", item.name)
                put("addedAtMillis", item.addedAtMillis)
                put("sortOrder", item.sortOrder)
                put("groupId", item.groupId)
                put("alertRules", item.alertRules)
            })
        }
        preferences.setString(KEY, array.toString())
    }

    companion object {
        private const val KEY = "stockchat_watchlist_v1"
        const val MAX_ITEMS = 50
    }
}
