package com.kuikly.stockchat.common

import kotlinx.browser.window

internal actual fun platformOpenPage(page: String): Boolean {
    window.location.assign("${window.location.origin}${window.location.pathname}?page_name=$page")
    return true
}

internal actual fun platformClosePage(): Boolean {
    window.history.back()
    return true
}

// 仅处理 http(s) 外链；其它 scheme 交回桥接层
internal actual fun platformOpenUrl(url: String): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    window.open(url, "_blank")
    return true
}
