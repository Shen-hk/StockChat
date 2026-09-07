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
    // FR-W2 关注理由：明文、≤40 字、不外传。行上不展示（保持扫描效率），
    // 只在长按菜单 / 详情页 / 风险地图作为「当初理由 vs 当前事实」的对照物出现。
    val reason: String = "",
    // FR-W8 理由变更历史：最近 3 次修改，新的在前。可回看「我改主意了几次」。
    val reasonHistory: List<String> = emptyList(),
    // FR-W6 对话置顶：从对话（@ 候选 / 卡片「加自选」/ 灵动岛星标）加入的标的
    // 置顶并打 ★——「刚在对话里聊到的票」永远在列表最上面。
    val starred: Boolean = false,
    // FR-W9 停留时长「没看过」半边：最后一次从自选列表点进详情的时间。0 = 从未点开。
    val lastViewedAtMillis: Long = 0L,
    // doc 29 A1 回访卡：加自选当时的行情价。0 = 未记录（旧数据/非详情页入口），
    // 序列化兼容：optString 缺省 0.0，写回时仅在 >0 时落盘，旧 key 不迁移。
    val entryPrice: Double = 0.0,
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

    /**
     * @param pinned FR-W6：true 表示从对话入口加入 → 置顶（sortOrder = 最小值-1）并打 ★。
     */
    fun add(symbol: String, name: String, pinned: Boolean = false): WatchlistAddResult {
        val rows = readRows().toMutableList()
        if (rows.any { it.symbol == symbol }) return WatchlistAddResult.ALREADY_IN
        if (rows.size >= MAX_ITEMS) return WatchlistAddResult.FULL
        rows += WatchlistItem(
            symbol = symbol,
            name = name,
            addedAtMillis = nowMillis(),
            sortOrder = if (pinned) (rows.minOfOrNull { it.sortOrder } ?: 0) - 1 else rows.size,
            starred = pinned,
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

    fun setGroup(symbol: String, groupId: String) {
        writeRows(readRows().map { if (it.symbol == symbol) it.copy(groupId = groupId) else it })
    }

    /**
     * FR-W2 关注理由 + FR-W8 变更历史。
     * 旧理由非空时压入 history 头部并截断到 3 条；空→非空也算一次「改主意」。
     */
    fun setReason(symbol: String, reason: String) {
        val trimmed = reason.trim().take(MAX_REASON_LENGTH)
        writeRows(
            readRows().map { item ->
                if (item.symbol != symbol) {
                    item
                } else {
                    val history = if (item.reason.isNotBlank() && item.reason != trimmed) {
                        (listOf(item.reason) + item.reasonHistory).take(MAX_REASON_HISTORY)
                    } else {
                        item.reasonHistory
                    }
                    item.copy(reason = trimmed, reasonHistory = history)
                }
            },
        )
    }

    /**
     * 置顶：sortOrder 取当前最小值减一。readRows 按 sortOrder 升序返回，
     * 负数不会与 add() 产生的 0..n 冲突，也无需整列重排。
     */
    fun moveToTop(symbol: String) {
        val rows = readRows()
        if (rows.none { it.symbol == symbol }) return
        val top = (rows.minOfOrNull { it.sortOrder } ?: 0) - 1
        writeRows(rows.map { if (it.symbol == symbol) it.copy(sortOrder = top) else it })
    }

    /**
     * doc 29 A1：记录加自选当时的行情价（回访卡 KPI）。仅详情页入口调用；
     * 旧数据该字段缺省 0.0，页面侧据此显示「—」。
     */
    fun setEntryPrice(symbol: String, price: Double) {
        writeRows(readRows().map { if (it.symbol == symbol) it.copy(entryPrice = price) else it })
    }

    /**
     * FR-W7 手动排序：沿 sortOrder 整列重排（不做拖动手势——与左滑行/滚动手势
     * 叠加时误触率高，菜单步进是有意取舍）。delta = -1 上移一位，+1 下移一位。
     */
    fun moveBy(symbol: String, delta: Int) {
        if (delta == 0) return
        val rows = readRows()
        val index = rows.indexOfFirst { it.symbol == symbol }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, rows.lastIndex)
        if (target == index) return
        val reordered = rows.toMutableList()
        val item = reordered.removeAt(index)
        reordered.add(target, item)
        writeRows(reordered.mapIndexed { i, row -> row.copy(sortOrder = i) })
    }

    /** FR-W9：从自选列表点进详情时记一笔「看过」。 */
    fun markViewed(symbol: String) {
        writeRows(readRows().map { if (it.symbol == symbol) it.copy(lastViewedAtMillis = nowMillis()) else it })
    }

    fun setAlertRules(symbol: String, alertRules: String) {
        writeRows(readRows().map { if (it.symbol == symbol) it.copy(alertRules = alertRules) else it })
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
                                reason = item.optString("reason"),
                                reasonHistory = parseHistory(item.optString("reasonHistory")),
                                starred = item.optString("starred") == "1",
                                lastViewedAtMillis = item.optString("lastViewedAtMillis").toLongOrNull() ?: 0L,
                                entryPrice = item.optString("entryPrice").toDoubleOrNull() ?: 0.0, // 集成修复：A1 回访卡字段（兼容旧数据缺省 0.0）
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
                put("reason", item.reason)
                put("reasonHistory", JSONArray().apply { item.reasonHistory.forEach { put(it) } })
                if (item.starred) put("starred", "1")
                if (item.lastViewedAtMillis > 0L) put("lastViewedAtMillis", item.lastViewedAtMillis)
                if (item.entryPrice > 0.0) put("entryPrice", item.entryPrice) // 集成修复：A1 回访卡字段（向后兼容，仅在有值时落盘）
            })
        }
        preferences.setString(KEY, array.toString())
    }

    companion object {
        private const val KEY = "stockchat_watchlist_v1"
        const val MAX_ITEMS = 50
        const val MAX_REASON_LENGTH = 40
        const val MAX_REASON_HISTORY = 3

        /** 兼容式迁移：v1 数据没有 reason 字段，optString 返回空串即缺省值，无需换 key。 */
        private fun parseHistory(raw: String): List<String> = try {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val value = array.optString(index)
                    if (!value.isNullOrBlank()) add(value)
                }
            }.take(MAX_REASON_HISTORY)
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
