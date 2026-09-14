package com.kuikly.stockchat.common

import kotlinx.browser.window

internal actual fun platformOpenPage(page: String, params: Map<String, String>): Boolean {
    val encode: (String) -> String = { value -> window.asDynamic().encodeURIComponent(value) as String }
    val query = buildList {
        add("page_name=${encode(page)}")
        params.forEach { (key, value) -> add("${encode(key)}=${encode(value)}") }
    }.joinToString("&")
    window.location.assign("${window.location.origin}${window.location.pathname}?$query")
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
