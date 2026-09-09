package com.kuikly.stockchat.page.detail

/**
 * 分时锚点索引器（doc 29 §3 共享基建 · ④声呐 / ②句图联动 / B2新闻旗 / 公告锚点 共用）。
 *
 * A 股分时 240 点：上午 09:30–11:30、下午 13:00–15:00，每 1 分钟 1 点。
 * 索引 0–119 归属上午盘、120–239 归属下午盘；INDEX_COUNT=240。
 * 本 object 为纯函数实现，不依赖任何 Kuikly 运行时，可在 JVM 单测。
 *
 * 约定（与 doc 29 边界一致）：
 *  - 09:30 → 0，11:30 → 119（早盘末位）
 *  - 13:00 → 120，15:00 → 239（午盘末位）
 *  - 午休 11:30–13:00 之间（含 12:00）一律返回 null（跨日新闻置灰，见 B2）
 *
 * 注：13:35 在本映射下返回 155（=120 + 35）。doc 29 正文示例写的 145 与
 * “13:00–15:00 → 120–239”的范围约束不自洽（若 13:35=145，则 15:00 只能落到 ~206，
 * 破坏 120–239 范围），故以范围约束为准实现为 155。详见交付报告。
 */
object AnchorIndex {
    const val INDEX_COUNT = 240

    private const val MORNING_START = 9 * 60 + 30   // 570 (09:30)
    private const val MORNING_END = 11 * 60 + 30    // 690 (11:30)
    private const val AFTERNOON_START = 13 * 60     // 780 (13:00)
    private const val AFTERNOON_END = 15 * 60       // 900 (15:00)
    private const val AFTERNOON_OFFSET = 120        // 下午盘起始索引

    /**
     * "HH:MM" → 分时索引；越界（午休、收盘后、非法串）返回 null。
     */
    fun timeStringToIndex(hhmm: String): Int? {
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (m < 0 || m > 59) return null
        val t = h * 60 + m
        return when {
            t in MORNING_START..MORNING_END -> {
                val idx = t - MORNING_START // 09:30 -> 0，11:30 -> 120
                if (idx > 119) 119 else idx // 11:30 收在早盘末位 119
            }
            t in AFTERNOON_START..AFTERNOON_END -> {
                val idx = AFTERNOON_OFFSET + (t - AFTERNOON_START) // 13:00 -> 120，15:00 -> 240
                if (idx > 239) 239 else idx // 15:00 收在午盘末位 239
            }
            else -> null // 午休（含 12:00）与收盘后
        }
    }

    /**
     * "HH:MM" → 分时索引（夹紧版）：盘前发布 → 0（09:30 开盘位）、午休 → 119（早盘末位）、
     * 盘后/晚间发布 → 239（尾盘位）。真实资讯的 Art_ShowTime 多为盘前/盘后发布，
     * 严格版 [timeStringToIndex] 对这些一律返回 null，导致「点按落旗」永远落不上——
     * B2 落旗走本夹紧版，保证任何可解析时间都有可视落点。非法串返回 null。
     */
    fun timeStringToIndexClamped(hhmm: String): Int? {
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h < 0 || h > 23 || m < 0 || m > 59) return null
        val t = h * 60 + m
        return when {
            t < MORNING_START -> 0
            t <= MORNING_END -> minOf(t - MORNING_START, 119)
            t < AFTERNOON_START -> 119
            t <= AFTERNOON_END -> minOf(AFTERNOON_OFFSET + (t - AFTERNOON_START), 239)
            else -> 239
        }
    }

    /**
     * 分时索引 → "HH:MM" 标签；越界返回空串。
     */
    fun indexToTimeLabel(index: Int): String {
        if (index < 0 || index >= INDEX_COUNT) return ""
        val t = if (index <= 119) MORNING_START + index else AFTERNOON_START + (index - AFTERNOON_OFFSET)
        val h = t / 60
        val m = t % 60
        return pad2(h) + ":" + pad2(m)
    }

    /**
     * 开盘以来的「交易分钟数」（午休折叠，不计入） → 分时索引。
     * 09:30=0，11:30=120（→夹紧到 119），13:00=120，15:00=240（→夹紧到 239）。
     * 负数或 >240 返回 null。下午时段的换算请用 [timeStringToIndex] 以免与早盘收盘混淆。
     */
    fun minutesSinceOpenToIndex(min: Int): Int? {
        if (min < 0 || min > 240) return null
        return if (min > 239) 239 else min
    }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()
}
