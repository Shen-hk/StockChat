package com.kuikly.stockchat.common

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

object Routes {
    const val CHAT = "ChatPage"
    const val STOCK_DETAIL = "StockDetailPage"
    const val CARD_GALLERY = "CardGallery"
    const val API_CONFIG = "ApiConfigPage"
    const val LEGACY_ROUTER = "router"
}

fun PagerScope.openStockDetail(symbol: String, from: String = Routes.CHAT) {
    getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        Routes.STOCK_DETAIL,
        JSONObject().apply {
            put("symbol", symbol)
            put("from", from)
        },
    )
}

fun PagerScope.openPage(page: String) {
    if (!platformOpenPage(page)) {
        getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(page, JSONObject())
    }
}

fun PagerScope.closePage() {
    if (!platformClosePage()) {
        getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }
}

internal expect fun platformOpenPage(page: String): Boolean
internal expect fun platformClosePage(): Boolean
