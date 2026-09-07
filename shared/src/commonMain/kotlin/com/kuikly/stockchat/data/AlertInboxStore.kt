package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 预警消息类型（doc 30）。
 *
 * MOVE = 价格阈值触发的行情异动（AlertStore 规则）；EVENT = 未来 30 天事件临近
 * （解禁/财报/分红，数据来自预约日历）；EXPOSURE = 风险地图周快照的暴露变化
 * （CR3/成员数对比）。三类共用一条收件箱时间线。
 */
enum class AlertKind(val label: String) {
    MOVE("行情异动"), EVENT("事件临近"), EXPOSURE("暴露变化");

    companion object {
        /** 磁盘存 label；未知 label 降级为 MOVE（最老类型，兼容旧数据）。 */
        fun fromLabel(label: String): AlertKind = entries.firstOrNull { it.label == label } ?: MOVE
    }
}

/**
 * 一条预警消息。**只承载已发生的事实**：title/summary/facts 全部由规则引擎组装，
 * 不含任何操作建议；askQuestion 是「问 AI」出口的预填问题，回答交给对话页
 * AttributionIntent 链路，页内零 AI 生成文本（doc 23 合规红线 + doc 30 §1）。
 *
 * @param id 稳定去重键（"MOVE:sym:阈值" / "EVENT:sym:date" / "EXPOSURE:快照时间"）
 * @param symbol 标的代码；EXPOSURE 类为空串
 * @param name 展示名；EXPOSURE 类为「组合」
 * @param termKey 可选术语出口（如 UNLOCK）；空 = 无
 * @param pinned true = RiskMapPage「转预警」手工写入并持久化；false = 每次构建派生
 */
data class AlertMessage(
    val id: String,
    val kind: AlertKind,
    val symbol: String,
    val name: String,
    val title: String,
    val summary: String,
    val facts: List<String>,
    val createdAtMillis: Long,
    val askQuestion: String,
    val termKey: String = "",
    val pinned: Boolean = false,
)

/**
 * 预警收件箱状态存储（doc 30）：
 * - 已读 id 集合：消息本身是每次构建派生的，落盘只存「已读过哪些 id」；
 * - 静默：按规则 symbol 静默价格异动、整类静默暴露变化（消息级一等动作，不藏设置里）；
 * - 免打扰：收盘后（15 点后）触发的异动合并进次日，不即时出卡；
 * - pinned 消息：风险地图「转预警」手工写入的事实消息，持久化且恒显示。
 *
 * 合规注释：本 store 不含任何远程推送、不含用户资产数据；消息只解释已发生的事
 * （doc 23 N-R6 维持：无推送）。
 *
 * 磁盘结构是内部实现细节，页面只允许走方法 API（单 key JSON，读写风格照抄
 * AlertStore / RiskSnapshotStore；解析失败一律降级为空值，不崩溃）。
 */
class AlertInboxStore(
    private val storage: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    fun readIds(): Set<String> = read().readIds

    fun markRead(id: String) {
        if (id.isEmpty()) return
        val state = read()
        if (id in state.readIds) return
        write(state.copy(readIds = (state.readIds + id).takeLast(MAX_READ_IDS).toSet()))
    }

    fun markAllRead(ids: List<String>) {
        if (ids.isEmpty()) return
        val state = read()
        write(state.copy(readIds = (state.readIds + ids).takeLast(MAX_READ_IDS).toSet()))
    }

    /** 未读数 = 消息里 id 不在已读集合中的条数（派生消息天然可能过期消失）。 */
    fun unreadCount(messages: List<AlertMessage>): Int {
        val read = readIds()
        return messages.count { it.id !in read }
    }

    fun mutedRuleSymbols(): Set<String> = read().mutedSymbols

    fun toggleMute(symbol: String) {
        if (symbol.isEmpty()) return
        val state = read()
        val next = if (symbol in state.mutedSymbols) state.mutedSymbols - symbol else state.mutedSymbols + symbol
        write(state.copy(mutedSymbols = next))
    }

    fun exposureMuted(): Boolean = read().exposureMuted

    fun setExposureMuted(muted: Boolean) {
        val state = read()
        if (state.exposureMuted == muted) return
        write(state.copy(exposureMuted = muted))
    }

    fun quietHoursEnabled(): Boolean = read().quietHours

    fun setQuietHours(enabled: Boolean) {
        val state = read()
        if (state.quietHours == enabled) return
        write(state.copy(quietHours = enabled))
    }

    /** 风险地图「转预警」写入的持久化消息（含 pinned EXPOSURE），按写入时间正序返回。 */
    fun extraMessages(): List<AlertMessage> = read().extra

    /** 按 id 去重写入；上限 [MAX_EXTRA] 条，超出丢最旧的。 */
    fun putExtraMessage(msg: AlertMessage) {
        if (msg.id.isEmpty()) return
        val state = read()
        if (state.extra.any { it.id == msg.id }) return
        write(state.copy(extra = (state.extra + msg).takeLast(MAX_EXTRA)))
    }

    // ── 磁盘（单 key，内部实现）──

    private data class InboxState(
        val readIds: Set<String> = emptySet(),
        val mutedSymbols: Set<String> = emptySet(),
        val exposureMuted: Boolean = false,
        val quietHours: Boolean = false,
        val extra: List<AlertMessage> = emptyList(),
    )

    private fun read(): InboxState {
        val raw = storage.getString(KEY)
        if (raw.isEmpty()) return InboxState()
        return runCatching {
            val obj = JSONObject(raw)
            InboxState(
                readIds = stringSet(obj.optJSONArray("readIds")),
                mutedSymbols = stringSet(obj.optJSONArray("mutedSymbols")),
                exposureMuted = obj.optBoolean("exposureMuted"),
                quietHours = obj.optBoolean("quietHours"),
                extra = parseExtra(obj.optJSONArray("extra")),
            )
        }.getOrElse { InboxState() }
    }

    private fun write(state: InboxState) {
        val obj = JSONObject()
        obj.put("readIds", JSONArray().apply { state.readIds.forEach { put(it) } })
        obj.put("mutedSymbols", JSONArray().apply { state.mutedSymbols.forEach { put(it) } })
        obj.put("exposureMuted", state.exposureMuted)
        obj.put("quietHours", state.quietHours)
        obj.put("extra", JSONArray().apply { state.extra.forEach { put(messageJson(it)) } })
        obj.put("savedAt", nowMillis())
        storage.setString(KEY, obj.toString())
    }

    private fun messageJson(msg: AlertMessage): JSONObject = JSONObject().apply {
        put("id", msg.id)
        put("kind", msg.kind.label)
        put("symbol", msg.symbol)
        put("name", msg.name)
        put("title", msg.title)
        put("summary", msg.summary)
        put("facts", JSONArray().apply { msg.facts.forEach { put(it) } })
        put("createdAtMillis", msg.createdAtMillis)
        put("askQuestion", msg.askQuestion)
        put("termKey", msg.termKey)
        put("pinned", msg.pinned)
    }

    private fun parseExtra(array: JSONArray?): List<AlertMessage> {
        if (array == null) return emptyList()
        return buildList {
            repeat(array.length()) { index ->
                val row = array.optJSONObject(index) ?: return@repeat
                val id = row.optString("id")
                if (id.isEmpty()) return@repeat
                val facts = buildList {
                    row.optJSONArray("facts")?.let { arr ->
                        repeat(arr.length()) { i -> arr.optString(i).takeIf { it.isNotEmpty() }?.let(::add) }
                    }
                }
                add(
                    AlertMessage(
                        id = id,
                        kind = AlertKind.fromLabel(row.optString("kind")),
                        symbol = row.optString("symbol"),
                        name = row.optString("name").ifEmpty { "组合" },
                        title = row.optString("title").ifEmpty { id },
                        summary = row.optString("summary"),
                        facts = facts,
                        createdAtMillis = row.optString("createdAtMillis").toLongOrNull() ?: 0L,
                        askQuestion = row.optString("askQuestion"),
                        termKey = row.optString("termKey"),
                        pinned = row.optBoolean("pinned"),
                    ),
                )
            }
        }
    }

    private fun stringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            repeat(array.length()) { index ->
                array.optString(index).takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private companion object {
        const val KEY = "stockchat_alert_inbox_v1"
        const val MAX_READ_IDS = 200
        const val MAX_EXTRA = 50
    }
}
