package com.kuikly.stockchat.common

internal actual fun platformOpenPage(page: String, params: Map<String, String>): Boolean = false
internal actual fun platformClosePage(): Boolean = false
// Android 无 Activity 上下文可直取，走原生桥 openUrl（KRBridgeModule）
internal actual fun platformOpenUrl(url: String): Boolean = false
