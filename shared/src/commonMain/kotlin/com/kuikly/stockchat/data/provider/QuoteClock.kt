package com.kuikly.stockchat.data.provider

internal expect fun platformCurrentTimeMillis(): Long
internal expect fun platformCurrentDate(compact: Boolean = false): String
internal expect fun platformCurrentHour(): Int
