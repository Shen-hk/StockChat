package com.kuikly.stockchat.data.provider

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.os.Handler
import android.os.Looper

internal actual fun platformCurrentTimeMillis(): Long = System.currentTimeMillis()
internal actual fun platformCurrentDate(compact: Boolean): String =
    SimpleDateFormat(if (compact) "yyyyMMdd" else "yyyy-MM-dd", Locale.US).format(Date())
internal actual fun platformCurrentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
internal actual fun platformCurrentMinuteOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}
internal actual fun platformSchedule(delayMillis: Long, block: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed(block, delayMillis)
}
