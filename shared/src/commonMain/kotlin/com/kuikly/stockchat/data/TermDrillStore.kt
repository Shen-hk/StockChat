package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 术语闪卡的作答态（doc 30 轮播原型 → 真机）。 */
object TermDrill {
    /** 没学过（无记录，不落盘）。 */
    const val STATE_NEW = 0
    /** 有点模糊：进「待巩固」，出牌时排在前面反复出现。 */
    const val STATE_FUZZY = 1
    /** 会了：本轮移出队列；与 [GlossaryStore] 的 KNOWN 双写保持地图一致。 */
    const val STATE_KNOWN = 2
    /** 没记住：追加到队尾，本轮内再过一遍。 */
    const val STATE_FORGOT = 3
}

/**
 * 卡片轮学的出牌记录（doc 24 §6.3 掌握状态的「练」侧补充）。
 *
 * 与 [GlossaryStore] 的分工：那里只回答「遇到过没有 / 读没读懂」（地图页吃这份），
 * 这里只回答「卡片轮学怎么出牌」——按 FORGOT → FUZZY → NEW 的顺序、组内最久没练
 * 的先出；答「会了」时由页面双写 [GlossaryStore.markKnown]，两个视图不脱节。
 * 「今日已学 N 张」是激励性计数，随作答次数累加，跨天自动归零。
 */
class TermDrillStore(
    private val preferences: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
    private val todayKey: () -> String = { platformCurrentDate(compact = true) },
) {
    constructor(
        pagerId: String,
        nowMillis: () -> Long = ::platformCurrentTimeMillis,
    ) : this(PagerKeyValueStorage(pagerId), nowMillis)

    data class DrillRecord(
        val key: String,
        val state: Int,
        val lastMillis: Long,
    )

    private val cache: MutableMap<String, DrillRecord> by lazy { readAll().toMutableMap() }

    fun state(key: String): Int = cache[key]?.state ?: TermDrill.STATE_NEW

    fun all(): Map<String, DrillRecord> = cache

    /** 记一次作答；同时给「今日已学」计数 +1。 */
    fun record(key: String, state: Int) {
        if (key.isBlank()) return
        cache[key] = DrillRecord(key = key, state = state, lastMillis = nowMillis())
        persist()
        bumpToday()
    }

    /**
     * 出牌顺序：FORGOT → FUZZY → NEW，组内按最久没练优先（稳定、可预期，
     * 不用随机——轮学的安全感来自「上次不会的这次一定还会见到」）。
     * [includeKnown] = true 时 KNOWN 沉底保留（「全部掌握！再过一遍」）。
     */
    fun deckOrder(keys: List<String>, includeKnown: Boolean = false): List<String> =
        keys.sortedWith(
            compareBy({ stateRank(it) }, { cache[it]?.lastMillis ?: 0L }, { it }),
        ).filter { includeKnown || state(it) != TermDrill.STATE_KNOWN }

    private fun stateRank(key: String): Int = when (state(key)) {
        TermDrill.STATE_FORGOT -> 0
        TermDrill.STATE_FUZZY -> 1
        TermDrill.STATE_NEW -> 2
        else -> 3
    }

    /** 今日已学张数（非今日记录视为 0，跨天自然归零）。 */
    fun todayLearned(): Int {
        val (day, count) = readToday()
        return if (day == todayKey()) count else 0
    }

    private fun bumpToday() {
        val today = todayKey()
        val (day, count) = readToday()
        val next = if (day == today) count + 1 else 1
        preferences.setString(
            TODAY_KEY,
            JSONObject().apply {
                put("d", today)
                put("n", next)
            }.toString(),
        )
    }

    private fun readToday(): Pair<String, Int> {
        val raw = preferences.getString(TODAY_KEY)
        if (raw.isEmpty()) return "" to 0
        return try {
            val obj = JSONObject(raw)
            obj.optString("d") to (obj.optString("n").toIntOrNull() ?: 0)
        } catch (_: Throwable) {
            "" to 0
        }
    }

    private fun persist() {
        val array = JSONArray()
        cache.values.sortedBy { it.key }.forEach { row ->
            array.put(JSONObject().apply {
                put("key", row.key)
                put("state", row.state)
                put("lastMillis", row.lastMillis)
            })
        }
        preferences.setString(KEY, array.toString())
    }

    private fun readAll(): Map<String, DrillRecord> {
        val raw = preferences.getString(KEY)
        if (raw.isEmpty()) return emptyMap()
        return try {
            val array = JSONArray(raw)
            buildMap {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val key = item.optString("key")
                    val state = item.optString("state").toIntOrNull() ?: TermDrill.STATE_NEW
                    if (key.isNotBlank() && state != TermDrill.STATE_NEW) {
                        put(
                            key,
                            DrillRecord(
                                key = key,
                                state = state,
                                lastMillis = item.optString("lastMillis").toLongOrNull() ?: 0L,
                            ),
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    companion object {
        private const val KEY = "stockchat_term_drill_v1"
        private const val TODAY_KEY = "stockchat_term_drill_today_v1"
    }
}
