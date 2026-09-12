package com.kuikly.stockchat.common

import com.kuikly.stockchat.app.assembly.MarketFeatureGraph
import com.kuikly.stockchat.base.BridgeModule
import com.kuikly.stockchat.data.provider.QuotePrefetchStore
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
    const val SETTINGS = "SettingsPage"
    const val LEGACY_ROUTER = "router"
}

fun PagerScope.openStockDetail(
    symbol: String,
    from: String = Routes.CHAT,
    islandExpand: Boolean = false,
) {
    // 点按即预取（2026-09-10 空白期治理）：本函数是全部详情页入口的汇聚点，
    // 转场动画（约 300ms）期间并行拉快照+分时进全局预取缓存，详情页 created()
    // peek 命中即整页秒开，不再出现骨架空白期。runCatching：预取任何异常
    // 都不阻塞跳转；60s 新鲜窗口内重复点击不会重复请求。
    if (symbol.isNotBlank()) {
        runCatching { QuotePrefetchStore.warm(listOf(symbol), MarketFeatureGraph.prefetchTarget(pagerId)) }
    }
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

/**
 * Opens the single chat surface with an optional, user-visible fact note.
 * The note is deliberately separate from the natural-language question: it is
 * preserved in SendPayload context instead of teaching every caller to prefix
 * its question with implementation details.
 *
 * [focusSymbol]：结构化焦点标的（如 "600519.SH"）。带标的的场景（详情页、
 * 预警卡、风险星图）务必传入——问题文本里不含股票名时，聊天页靠它把标的
 * 固化为发送侧提及，AI 才知道"问的是哪只股票"，行情上下文也会随之注入。
 */
fun PagerScope.openChatWithQuestion(question: String, focusNote: String = "", focusSymbol: String = "") {
    getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        Routes.CHAT,
        JSONObject().apply {
            put("question", question)
            if (focusNote.isNotBlank()) put("focusNote", focusNote)
            if (focusSymbol.isNotBlank()) put("focusSymbol", focusSymbol)
            // 「问AI」跳转标记：宿主路由据此收拢导航栈。没有它的话，
            // 「详情 ⇄ 对话」反复横跳会把历史页一层层压在栈里，
            // 返回键要逐页退完所有旧页才能回到最初的对话页。
            put("openedViaAskAi", "1")
        },
    )
}

fun PagerScope.openGlossary(islandExpand: Boolean = false) {
    getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        Routes.GLOSSARY,
        JSONObject().apply {
            // 术语岛下滑 → 术语表与股票岛下滑 → 详情页共用同一条容器变换
            // 交接：原生无动画 push，页面内容就地淡入接管玻璃帧。
            if (islandExpand) put("krTransition", "islandExpand")
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

// 外部链接（新闻原文等）。平台通道优先（H5 直接 window.open），
// 未接平台通道的端走原生桥 openUrl（Android ACTION_VIEW）。
fun PagerScope.openUrl(url: String) {
    if (url.isBlank()) return
    if (!platformOpenUrl(url)) {
        getPager().acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).openUrl(url)
    }
}

internal expect fun platformOpenPage(page: String): Boolean
internal expect fun platformClosePage(): Boolean
internal expect fun platformOpenUrl(url: String): Boolean
