package com.kuikly.stockchat.common

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

object Routes {
    const val CHAT = "ChatPage"
    const val STOCK_DETAIL = "StockDetailPage"
    const val CARD_GALLERY = "CardGallery"
    const val GLOSSARY = "GlossaryPage"
    const val WATCHLIST = "WatchlistPage"
    const val RISK = "RiskMapPage"
    const val API_CONFIG = "ApiConfigPage"
    const val MARKET = "MarketPage"
    const val HOTSPOTS = "HotspotPage"
    const val SEARCH = "GlobalSearchPage"
    const val CALENDAR = "MarketCalendarPage"
    const val ALERTS = "AlertCenterPage"
    const val LEGACY_ROUTER = "router"
}

fun PagerScope.openStockDetail(
    symbol: String,
    from: String = Routes.CHAT,
    islandExpand: Boolean = false,
) {
    getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        Routes.STOCK_DETAIL,
        JSONObject().apply {
            put("symbol", symbol)
            put("from", from)
            // 容器变换交接：灵动岛玻璃卡片刚好铺满全屏，原生侧对该路由做
            // 无动画 push（去掉系统右侧推入），详情页内容再就地淡入接管
            // 这一帧，读起来就是"卡片长成了详情页"。
            if (islandExpand) put("krTransition", "islandExpand")
        },
    )
}

fun PagerScope.openChatWithQuestion(question: String) {
    getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        Routes.CHAT,
        JSONObject().apply {
            put("question", question)
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
