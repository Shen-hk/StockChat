package com.kuikly.stockchat.data.provider

import platform.Foundation.NSDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone

internal actual fun platformCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000).toLong()
internal actual fun platformCurrentDate(compact: Boolean): String = NSDateFormatter().run {
    dateFormat = if (compact) "yyyyMMdd" else "yyyy-MM-dd"
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    timeZone = NSTimeZone.localTimeZone
    stringFromDate(NSDate())
}
internal actual fun platformCurrentHour(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()
