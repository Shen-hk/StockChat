package com.kuikly.stockchat.data.provider

internal actual fun platformCurrentTimeMillis(): Long = (js("Date.now()") as Double).toLong()
