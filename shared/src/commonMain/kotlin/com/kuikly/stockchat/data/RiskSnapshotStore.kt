package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 暴露快照（doc 23 FR-R10，二期）：风险地图每次打开时若距上次留存 ≥7 天，
 * 把当期的等权暴露画像存一条，可回看「暴露如何随时间变化」。
 *
 * 刻意只存**摘要**（成员数 / 最大行业 / CR3 / 波动比），不存逐票明细——
 * 快照的用途是回看趋势，不是重建历史页面；且明文存储越少越合规。
 */
data class RiskSnapshot(
    val capturedAtMillis: Long,
    val memberCount: Int,
    val topIndustry: String,
    val topIndustryCount: Int,
    val cr3Percent: Int,
    /** 组合日波动 ÷ 大盘日波动，×100 存整数（13 = 1.3×）；null = 当期不可算。 */
    val volRatioPercent: Int?,
) {
    fun volRatioLabel(): String = volRatioPercent?.let { "波动 ${(it / 100.0)}×大盘" } ?: "波动未可算"
}

class RiskSnapshotStore(
    private val preferences: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    fun all(): List<RiskSnapshot> = readAll()

    /**
     * 距上次快照 ≥7 天才落一条（每周留存一次）；快照上限 [MAX_SNAPSHOTS]，
     * 超出丢最旧的——趋势回看只需要近期。
     */
    fun maybeCapture(snapshot: RiskSnapshot) {
        val existing = readAll()
        val last = existing.lastOrNull()?.capturedAtMillis ?: 0L
        if (nowMillis() - last < WEEK_MS) return
        val next = (existing + snapshot).takeLast(MAX_SNAPSHOTS)
        writeAll(next)
    }

    private fun readAll(): List<RiskSnapshot> {
        val raw = preferences.getString(KEY)
        if (raw.isEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val capturedAt = item.optString("capturedAtMillis").toLongOrNull() ?: 0L
                    if (capturedAt > 0L) {
                        add(
                            RiskSnapshot(
                                capturedAtMillis = capturedAt,
                                memberCount = item.optString("memberCount").toIntOrNull() ?: 0,
                                topIndustry = item.optString("topIndustry"),
                                topIndustryCount = item.optString("topIndustryCount").toIntOrNull() ?: 0,
                                cr3Percent = item.optString("cr3Percent").toIntOrNull() ?: 0,
                                volRatioPercent = item.optString("volRatioPercent").toIntOrNull(),
                            ),
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writeAll(snapshots: List<RiskSnapshot>) {
        val array = JSONArray()
        snapshots.forEach { row ->
            array.put(JSONObject().apply {
                put("capturedAtMillis", row.capturedAtMillis)
                put("memberCount", row.memberCount)
                put("topIndustry", row.topIndustry)
                put("topIndustryCount", row.topIndustryCount)
                put("cr3Percent", row.cr3Percent)
                put("volRatioPercent", row.volRatioPercent)
            })
        }
        preferences.setString(KEY, array.toString())
    }

    private companion object {
        const val KEY = "stockchat_risk_snapshot_v1"
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_SNAPSHOTS = 12
    }
}
