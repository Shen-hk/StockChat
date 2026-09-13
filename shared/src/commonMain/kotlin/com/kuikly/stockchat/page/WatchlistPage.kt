package com.kuikly.stockchat.page

import com.kuikly.stockchat.app.assembly.MarketFeatureGraph
import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.provider.QuotePrefetchStore
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBar
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBarAction
import com.kuikly.stockchat.foundation.ui.icon.LineIconBellRinging
import com.kuikly.stockchat.foundation.ui.icon.LineIconRadar
import com.kuikly.stockchat.foundation.ui.icon.LineIconSearch
import com.kuikly.stockchat.watchlist.component.UndoBar
import com.kuikly.stockchat.watchlist.brief.state.WatchlistBriefCoordinator
import com.kuikly.stockchat.watchlist.component.WatchlistMenuOverlay
import com.kuikly.stockchat.watchlist.component.WatchlistReasonEditorOverlay
import com.kuikly.stockchat.watchlist.component.WatchlistScrollContent
import com.kuikly.stockchat.watchlist.component.WatchlistSearchOverlay
import com.kuikly.stockchat.watchlist.drag.state.WatchlistDragCoordinator
import com.kuikly.stockchat.watchlist.state.KuiklyWatchlistScheduler
import com.kuikly.stockchat.watchlist.state.WatchlistCoordinator
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.kuikly.stockchat.foundation.ui.fontSizeScaled

/**
 * 自选股列表页 v2（doc 24 §6.1 重构规格；doc 47 B-4 装配层化）。
 *
 * 层次结构（z 轴从后到前）：
 * - z0 聚合头：等权涨跌幅主数字 + 规则引擎结论 + 中心分界比例条。
 * - z1 筛选：状态筛选（全部/异动 N）在上，分组弱化到第二行。
 * - z2 行：RowGestureLayer（点击进详情 / 长按拿起拖拽排序 / 长按原地松手开菜单）。
 * - z3 异动上浮：同屏涨幅最异常的一行垫玻璃底浮起；行情平静时无 z3。
 * - z5 浮层：搜索与长按菜单、理由编辑。
 *
 * doc 30 小空间整合：本页升级为「我的小空间」首页——收件箱预览 + 今日速览。
 *
 * 装配层职责（doc 47 B-4）：页面只持有三个状态域与装配逻辑——
 * - [WatchlistCoordinator]：列表/筛选/搜索/菜单/理由编辑/Undo 与 Store Intent；
 * - [WatchlistDragCoordinator]：长按拖拽排序状态机（R5：取消不重置 dragFrom/dragTo）；
 * - [WatchlistBriefCoordinator]：收件箱预览 + 今日速览（AlertInboxBuilder 派生链）。
 * 组件与视觉细节在 watchlist/component；页面不再直接构造 Provider、不持有业务 Timer。
 */
@Page(Routes.WATCHLIST, supportInLocal = true)
internal class WatchlistPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketFeatureGraph.forPager(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    private val data: WatchlistCoordinator by lazy {
        WatchlistCoordinator(
            host = object : WatchlistCoordinator.WatchlistHost {
                override fun watchlistStore() = dependencies.watchlistStore

                override fun quoteRepository() = dependencies.quoteRepository

                override fun insightRepository() = dependencies.insightRepository

                override fun warmPrefetch(symbols: List<String>) {
                    // 详情页预取（2026-09-10 空白期治理）：自选标的行情预热进全局预取缓存，
                    // 点进详情页 created() 直接命中整页秒开（60s 新鲜窗口内不重复请求；
                    // warm 内部自带去重与单次 8 标的上限，防请求风暴）。
                    QuotePrefetchStore.warm(symbols, MarketFeatureGraph.prefetchTarget(pagerId))
                }
            },
            scheduler = KuiklyWatchlistScheduler(),
        )
    }

    private val drag: WatchlistDragCoordinator by lazy {
        WatchlistDragCoordinator(
            host = object : WatchlistDragCoordinator.DragHost {
                override fun displayList() = data.displayList

                override fun rows() = data.rows

                override fun store(): WatchlistStore = dependencies.watchlistStore

                override fun canDragDirectly(): Boolean =
                    data.statusFilter.isEmpty() && data.activeGroup == "all"

                override fun openMenu(symbol: String) = data.openMenuFor(symbol)

                override fun refreshDisplay() = data.refreshDisplay()
            },
        )
    }

    private val brief: WatchlistBriefCoordinator by lazy {
        WatchlistBriefCoordinator(
            host = object : WatchlistBriefCoordinator.BriefHost {
                override fun watchlist() = dependencies.watchlistStore.list()

                override fun alertRules() = dependencies.alertStore.list()

                override fun snapshots() = dependencies.riskSnapshotStore.all()

                override fun mutedRuleSymbols() = dependencies.alertInboxStore.mutedRuleSymbols()

                override fun exposureMuted() = dependencies.alertInboxStore.exposureMuted()

                override fun quietHoursEnabled() = dependencies.alertInboxStore.quietHoursEnabled()

                override fun extraMessages() = dependencies.alertInboxStore.extraMessages()

                override fun unreadCount(messages: List<com.kuikly.stockchat.data.AlertMessage>) =
                    dependencies.alertInboxStore.unreadCount(messages)

                override fun reduceMotion(): Boolean = this@WatchlistPage.reduceMotion

                override fun aggregate() = data.aggregate()

                override fun rowsSnapshot() = data.rows.toList()
            },
            scheduler = KuiklyWatchlistScheduler(),
        )
    }

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        // 跨域接线：拖拽会话门控 displayList 重建；displayList/日历变化驱动简报重建。
        data.isDragging = { drag.dragSymbol.isNotEmpty() }
        data.onDragPendingRefresh = { drag.markRefreshPending() }
        data.onDisplayRebuilt = { brief.rebuildInbox() }
        data.onEventsLoaded = { brief.updateEvents(it) }
        // R4 两拍：预览条首帧隐藏，下一帧翻入（reduceMotion 直出）。
        brief.armInboxEntrance()
        data.reload()
        data.warmPrefetch()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        // Android 在路由/系统手势打断长按时可能不向行分发 touchCancel；若留下
        // dragSymbol，Scroller 会一直认为自己在拖拽会话中而拒绝拦截滚动。
        drag.cancelActiveSession()
    }

    override fun body(): ViewBuilder {
        val page = this
        // 非受控铁律：TextArea 的 text 只作挂载种子，不绑定响应式文本。
        val searchSeed = ""
        return {
            attr { backgroundColor(page.theme.page) }
            // 物理上只有这一个 Scroller，内容子树用 vbind 作为 Scroller 的子项；
            // 换肤/字号变更只重建内容，滚动位置与拖拽状态保留。
            Scroller {
                attr {
                    flex(1f)
                    // 竖向 Scroller 水平 padding 双倍扣除（同 ChatPage/MarketPage）：
                    // 左 14 右 0，实测左右各约 14dp 对齐；卡片阴影也留有绘制空间。
                    paddingLeft(14f)
                    paddingRight(0f)
                    // 顶栏实际占用约 statusBar + 44dp；此前再加 73dp 会让首卡
                    // 与标题栏之间多出近 30dp 的空洞，空自选时尤为明显。
                    paddingTop(page.pagerData.statusBarHeight + 44f)
                    paddingBottom(60f)
                    // 只有长按已拿起卡片的那一小段手势把移动事件留给行做排序；
                    // 平常一律由 Scroller 拦截纵向滑动。pageDidAppear 会兜底清理
                    // 被系统打断的会话，避免旧架构中 dragSymbol 残留而永久禁滚。
                    scrollEnable(page.drag.dragSymbol.isEmpty())
                }
                vbind({ page.themeRebuildKey() }) {
                    // 必须以当前 vbind 为扩展接收者。若写成
                    // page.WatchlistScrollContent(...)，全部物理子节点会挂到页面根节点，
                    // 绕过 ScrollerContentView：卡片虽然可见，但滚动内容高度不含它们。
                    WatchlistScrollContent(
                        theme = page.theme,
                        data = page.data,
                        drag = page.drag,
                        brief = page.brief,
                        reduceMotion = page.reduceMotion,
                        onOpenDetail = page::openRowDetail,
                        onOpenSearchPage = { page.openPage(Routes.SEARCH) },
                        onOpenMarketPage = { page.openPage(Routes.MARKET) },
                        onOpenRiskPage = { page.openPage(Routes.RISK) },
                        onOpenAlertsPage = { page.openPage(Routes.ALERTS) },
                    )
                }
            }

            AppTopBar(
                title = "我的小空间",
                subtitle = "自选 · 预警 · 风险 · 速览，都在这里",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
                actions = listOf(
                    AppTopBarAction(icon = { color, size, _ -> LineIconBellRinging(color, size) }, onClick = { page.openPage(Routes.ALERTS) }),
                    AppTopBarAction(icon = { color, size, _ -> LineIconRadar(color, size) }, onClick = { page.openPage(Routes.RISK) }),
                    AppTopBarAction(icon = { color, size, _ -> LineIconSearch(color, size) }, onClick = { page.data.openSearch() }),
                ),
            )

            // ── z5 搜索浮层：原顶部长驻搜索框下沉于此（S-1：首屏 200px 让给结论） ──
            vif({ page.data.searchOpen }) {
                page.WatchlistSearchOverlay(
                    theme = page.theme,
                    data = page.data,
                    statusBarHeight = page.pagerData.statusBarHeight,
                    searchSeed = searchSeed,
                )
            }

            // ── z5 长按菜单：分组从「三态循环」改为显式选项列表（D 黑名单 #7） ──
            vif({ page.data.menuSymbol.isNotEmpty() }) {
                page.WatchlistMenuOverlay(
                    theme = page.theme,
                    data = page.data,
                    safeAreaBottom = page.pagerData.safeAreaInsets.bottom,
                )
            }

            // ── z5 理由浮层（FR-W2）：一行输入 + 3 个常用理由 chip，可跳过不强制 ──
            vif({ page.data.reasonEditSymbol.isNotEmpty() }) {
                page.WatchlistReasonEditorOverlay(
                    theme = page.theme,
                    data = page.data,
                    safeAreaBottom = page.pagerData.safeAreaInsets.bottom,
                )
            }

            // 撤销条压在最上层：移除从长按菜单触发，撤销入口落在拇指可达的底部，
            // 沿用顶部 hint 等于没有撤销。
            // UndoBar 以参数捕获 theme：同样挂重建键，换肤时随整树刷新。
            vbind({ page.themeRebuildKey() }) {
                UndoBar(
                    theme = page.theme,
                    text = { page.data.undoText },
                    actionLabel = "撤销",
                    onAction = { page.data.undoRemove() },
                )
            }
        }
    }

    /** FR-W9：点进详情 = 「看过」一笔。埋点先行，失败不影响跳转。 */
    private fun openRowDetail(symbol: String) {
        data.markViewed(symbol)
        openStockDetail(symbol, Routes.WATCHLIST)
    }
}
