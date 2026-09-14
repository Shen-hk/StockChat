package com.kuikly.stockchat.common

internal actual fun platformOpenPage(page: String, params: Map<String, String>): Boolean = false
internal actual fun platformClosePage(): Boolean = false
// iOS 待接原生桥后启用；当前回落 openUrl 桥方法
internal actual fun platformOpenUrl(url: String): Boolean = false
