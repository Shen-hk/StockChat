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
