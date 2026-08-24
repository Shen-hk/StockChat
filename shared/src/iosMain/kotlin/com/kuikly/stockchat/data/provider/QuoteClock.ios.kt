package com.kuikly.stockchat.data.provider

import platform.Foundation.NSDate

internal actual fun platformCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000).toLong()
