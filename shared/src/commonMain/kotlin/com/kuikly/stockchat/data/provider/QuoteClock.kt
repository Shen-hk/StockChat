package com.kuikly.stockchat.data.provider

internal expect fun platformCurrentTimeMillis(): Long
internal expect fun platformCurrentDate(compact: Boolean = false): String
internal expect fun platformCurrentHour(): Int

/** 北京时区当前「时×60+分」，供 SnapshotStore 分钟节流入库（doc 36 §7）。 */
internal expect fun platformCurrentMinuteOfDay(): Int

/**
 * 北京时区（UTC+8）下 days 天前的 yyyy-MM-dd。纯 common 实现（epochDay → 民用日期），
 * 避免为东财研报接口的 beginTime 参数给每个平台再补 actual。窗口参数差一天不影响业务。
 */
internal fun platformDateDaysAgo(days: Long): String {
    val dayMillis = 24L * 3600 * 1000
    val epochDay = (platformCurrentTimeMillis() + 8L * 3600 * 1000) / dayMillis - days
    // Howard Hinnant civil_from_days 算法（Long 防溢出）
    var z = epochDay + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = mp + (if (mp < 10L) 3L else -9L)
    val year = y + (if (m <= 2L) 1L else 0L)
    fun pad(n: Long): String = if (n < 10L) "0$n" else n.toString()
    return "${pad(year)}-${pad(m)}-${pad(d)}"
}
