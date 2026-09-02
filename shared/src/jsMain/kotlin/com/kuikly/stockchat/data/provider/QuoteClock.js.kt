package com.kuikly.stockchat.data.provider

import kotlin.js.Date

internal actual fun platformCurrentTimeMillis(): Long = (js("Date.now()") as Double).toLong()
internal actual fun platformCurrentDate(compact: Boolean): String {
    val date = Date()
    val value = "${date.getFullYear().toInt()}-${(date.getMonth().toInt() + 1).toString().padStart(2, '0')}-${date.getDate().toInt().toString().padStart(2, '0')}"
    return if (compact) value.replace("-", "") else value
}
