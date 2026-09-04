package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 词条掌握状态（doc 24 §6.3）。持久化只存三态：
 * - [UNSEEN]：没遇到过（无记录，不落盘）
 * - [SEEN]：点开过 / 在对话中触发过
 * - [KNOWN]：已理解（用户显式确认）
 * 「常见」（触发 ≥3 次）由 [hitCount] 派生，不单独存——存储态与展示态分离，
 * 展示态四段 = UNSEEN / SEEN(hits<3) / SEEN(hits>=3) / KNOWN。
 */
enum class GlossaryMastery {
    SEEN,
    KNOWN,
}

data class GlossaryEncounter(
    val key: String,
    val hitCount: Int = 0,
    val lastTriggeredMillis: Long = 0L,
    val mastery: GlossaryMastery = GlossaryMastery.SEEN,
) {
    val isKnown: Boolean get() = mastery == GlossaryMastery.KNOWN

    /** 四段展示态：未遇到(0) / 见过(1) / 常见(2) / 已读(3)。 */
    fun displayStage(): Int = when {
        isKnown -> 3
        hitCount >= COMMON_THRESHOLD -> 2
        else -> 1
    }

    companion object {
        const val COMMON_THRESHOLD = 3
        const val STAGE_UNSEEN = 0
        const val STAGE_SEEN = 1
        const val STAGE_COMMON = 2
        const val STAGE_KNOWN = 3
    }
}

/**
 * 术语「遇到」追踪（doc 24 §6.3）。
 *
 * 知识库地图的推荐引擎（下一步该懂什么）与四段状态条都吃这份数据。
 * 「遇到」的定义保持克制：只有用户真实撞上术语才算——聊天里点术语高亮、
 * 在词表里展开词条。AI 回答里被动扫过不算（渲染层重复渲染会让计数爆炸，
 * 且「页面里有」不等于「用户卡住了」）。
 */
class GlossaryStore(
    private val preferences: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    constructor(
        pagerId: String,
        nowMillis: () -> Long = ::platformCurrentTimeMillis,
    ) : this(PagerKeyValueStorage(pagerId), nowMillis)

    private val cache: MutableMap<String, GlossaryEncounter> by lazy { readAll().toMutableMap() }

    fun get(key: String): GlossaryEncounter? = cache[key]

    fun all(): Map<String, GlossaryEncounter> = cache

    /** 记录一次真实遇到：hitCount+1，时间刷新，未记录过的升级为 SEEN。 */
    fun encounter(key: String) {
        if (key.isBlank()) return
        val current = cache[key]
        cache[key] = if (current == null) {
            GlossaryEncounter(key = key, hitCount = 1, lastTriggeredMillis = nowMillis())
        } else {
            current.copy(hitCount = current.hitCount + 1, lastTriggeredMillis = nowMillis())
        }
        persist()
    }

    /** 用户显式确认已理解。 */
    fun markKnown(key: String) {
        if (key.isBlank()) return
        val current = cache[key]
        cache[key] = (current ?: GlossaryEncounter(key = key)).copy(mastery = GlossaryMastery.KNOWN)
        persist()
    }

    /** 按最近触发时间倒序的遇到记录（最近遇到 chips 用）。 */
    fun recent(limit: Int = 5): List<GlossaryEncounter> =
        cache.values.filter { it.hitCount > 0 }.sortedByDescending { it.lastTriggeredMillis }.take(limit)

    private fun persist() {
        val array = JSONArray()
        cache.values.sortedBy { it.key }.forEach { row ->
            array.put(JSONObject().apply {
                put("key", row.key)
                put("hitCount", row.hitCount)
                put("lastTriggeredMillis", row.lastTriggeredMillis)
                put("mastery", if (row.isKnown) "known" else "seen")
            })
        }
        preferences.setString(KEY, array.toString())
    }

    private fun readAll(): Map<String, GlossaryEncounter> {
        val raw = preferences.getString(KEY)
        if (raw.isEmpty()) return emptyMap()
        return try {
            val array = JSONArray(raw)
            buildMap {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val key = item.optString("key")
                    if (key.isNotBlank()) {
                        put(
                            key,
                            GlossaryEncounter(
                                key = key,
                                hitCount = item.optString("hitCount").toIntOrNull() ?: 0,
                                lastTriggeredMillis = item.optString("lastTriggeredMillis").toLongOrNull() ?: 0L,
                                mastery = if (item.optString("mastery") == "known") {
                                    GlossaryMastery.KNOWN
                                } else {
                                    GlossaryMastery.SEEN
                                },
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
        private const val KEY = "stockchat_glossary_v1"
    }
}
