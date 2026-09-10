package com.kuikly.stockchat.data.provider

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.localtime
import platform.posix.strftime
import platform.posix.time
import platform.posix.time_tVar

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformCurrentTimeMillis(): Long = memScoped {
    val now = alloc<time_tVar>()
    time(now.ptr) * 1_000L
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformCurrentDate(compact: Boolean): String = memScoped {
    val buffer = allocArray<ByteVar>(11)
    val now = alloc<time_tVar>()
    val format = if (compact) "%Y%m%d" else "%Y-%m-%d"
    time(now.ptr)
    strftime(buffer, 11u, format, localtime(now.ptr))
    buffer.toKString()
}

internal actual fun platformCurrentHour(): Int = currentTimeParts().first

internal actual fun platformCurrentMinuteOfDay(): Int {
    val (hour, minute) = currentTimeParts()
    return hour * 60 + minute
}

@OptIn(ExperimentalForeignApi::class)
private fun currentTimeParts(): Pair<Int, Int> = memScoped {
    val now = alloc<time_tVar>()
    time(now.ptr)
    val parts = localtime(now.ptr)?.pointed ?: return@memScoped 0 to 0
    parts.tm_hour to parts.tm_min
}
