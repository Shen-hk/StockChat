package com.kuikly.stockchat.common

internal actual fun platformOpenPage(page: String, params: Map<String, String>): Boolean = false
internal actual fun platformClosePage(): Boolean = false
internal actual fun platformOpenUrl(url: String): Boolean = false
