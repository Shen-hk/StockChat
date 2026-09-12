package com.kuikly.stockchat.data.provider

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSDate
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent

internal actual fun platformCurrentTimeMillis(): Long =
    ((CFAbsoluteTimeGetCurrent() + 978307200.0) * 1_000).toLong()
internal actual fun platformCurrentDate(compact: Boolean): String = NSDateFormatter().run {
    dateFormat = if (compact) "yyyyMMdd" else "yyyy-MM-dd"
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    stringFromDate(NSDate())
}
internal actual fun platformCurrentHour(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()

internal actual fun platformCurrentMinuteOfDay(): Int {
    val cal = NSCalendar.currentCalendar
    val hour = cal.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()
    val minute = cal.component(NSCalendarUnitMinute, fromDate = NSDate()).toInt()
    return hour * 60 + minute
}
internal actual fun platformSchedule(delayMillis: Long, block: () -> Unit) {
    platform.Foundation.NSTimer.scheduledTimerWithTimeInterval(delayMillis / 1_000.0, false) { _ -> block() }
}
