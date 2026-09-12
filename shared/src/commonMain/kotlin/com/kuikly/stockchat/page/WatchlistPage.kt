package com.kuikly.stockchat.page

import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.shared.cards.component.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.data.AlertInboxBuilder
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.app.assembly.MarketDependencies
import com.kuikly.stockchat.app.assembly.MarketFeatureGraph
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.entity.Security
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePrefetchStore
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBar
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBarAction
import com.kuikly.stockchat.page.components.DivergingBar
import com.kuikly.stockchat.foundation.ui.FeatureTile
import com.kuikly.stockchat.foundation.ui.icon.LineIconBarChart
import com.kuikly.stockchat.foundation.ui.icon.LineIconBellRinging
import com.kuikly.stockchat.foundation.ui.icon.LineIconRadar
import com.kuikly.stockchat.foundation.ui.icon.LineIconSearch
import com.kuikly.stockchat.page.components.RowGestureLayer
import com.kuikly.stockchat.page.components.UndoBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 自选股列表页 v2（doc 24 §6.1 重构规格）。
 *
 * 层次结构（z 轴从后到前）：
 * - z0 聚合头：等权涨跌幅主数字 + 规则引擎结论 + 中心分界比例条（涨跌家数的关系
 *   用图形表达，不靠文案罗列）。规则引擎纯本地计算，不调 LLM——聚合结论是算术，
 *   不是观点。
 * - z1 筛选：状态筛选（全部/异动 N）在上，分组弱化到第二行。
 * - z2 行：RowGestureLayer（点击进详情 / 长按拿起拖拽排序 / 长按原地松手开菜单）；
 *   手势入口在列表上方有常驻弱标注。
 * - z3 异动上浮：同屏涨幅最异常的一行垫玻璃底浮起；行情平静时无 z3。
 * - z5 浮层：搜索（原顶部长驻搜索框下沉于此）与长按菜单。
 *
 * 设计取向遵循「聊看一体」：入口放在抽屉而非独立 Tab，列表行复用 MINI 行情渲染器，
 * 点击直接进详情页；行情走三级降级链并诚实标注数据模式，不拿陈旧价格冒充实时。
 *
 * doc 30 小空间整合：本页升级为「我的小空间」首页——
 * - InboxPreviewRow：聚合头下的预警收件箱预览（未读 badge 即应用内消息提示入口）；
 * - 今日速览卡：规则引擎事实句（聚合结论/最异常行/最近事件），条件触发，平静日不出现；
 * - 消息由 AlertInboxBuilder 从行情/规则/日历/快照派生（纯函数，C-1 自选唯一输入源）。
 * 动效：两块新卡均为 R4 两拍入场（inboxPreviewPresented / briefPresented），
 * animate 恒注册（R5）、驱动 key 最后读取（R2），reduceMotion 直出。
 */
@Page(Routes.WATCHLIST, supportInLocal = true)
internal class WatchlistPage : BasePager() {
    private data class DragMotion(val dy: Float, val target: Int)

    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketFeatureGraph.forPager(pagerId) }
    private val watchlistStore get() = dependencies.watchlistStore
    private val quoteRepository get() = dependencies.quoteRepository
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    private var rows: ObservableList<WatchlistRow> by observableList()
    /** 渲染层过滤结果。vfor 只接受 ObservableList，所以过滤结果要落到这份拷贝上。 */
    private var displayList: ObservableList<WatchlistRow> by observableList()
    private var candidates: ObservableList<Security> by observableList()
    private var hint: String by observable("")
    private var lastRemoved: WatchlistItem? = null
    private var dataModeLabel: String by observable("")
    /** 分组 Tab：all = 全部，空串 = 未分组，其余为具体分组。 */
    private var activeGroup: String by observable("all")

    /** 状态筛选："" = 全部，"movers" = 仅异动行。分组筛选在 [activeGroup]。 */
    private var statusFilter: String by observable("")

    /** 搜索浮层开关：常驻搜索框下沉到 z5 后，这是唯一入口。 */
    private var searchOpen: Boolean by observable(false)

    /** 长按菜单正在操作的行；空 = 菜单关闭。 */
    private var menuSymbol: String by observable("")

    // ── FR-W2 关注理由浮层：三入口（搜索添加 / 长按菜单 / 详情页）共用同一编辑浮层 ──
    /** 理由浮层正在编辑的行；空 = 关闭。 */
    private var reasonEditSymbol: String by observable("")
    /** 快捷理由 chip 当前选中项；"" = 未选（用自定义输入）。 */
    private var reasonChip: String by observable("")
    /** 自定义输入的实时同步值（TextArea isSyncEdit）。选中 chip 后再打字则 chip 让位。 */
    private var reasonTyped: String by observable("")

    // ── 长按拖拽排序（lift + 落位）：长按拿起 → 跟手 → 让位 → 松手直接落位 ──
    // 机制：长按拍把 Scroller scrollEnable 关掉（KRRecyclerView.onInterceptTouchEvent
    // 首查 scrollEnabled，false 即不拦截），后续 move 留在本行 touch 上，跟手无需 pan
    // （pan 会 disallow 父级拦截、锁死列表滚动）。拖拽期间不动数据，只对让位行施加
    // ±槽距 translate（恒注册 spring，首个让位也有动画，R5）；松手**同帧落数据**
    // （applyDragOrder）：数据重排、displayList diff 与 transform 清除落在同一次
    // 渲染批里——让位行「布局移位 + transform 归零」互相抵消，被拖行 diff 为
    // Delete+Insert 直接重挂在新槽位。松手后没有任何收尾动画，卡片跟手到哪就
    // 落在哪（2026-09-09 去掉 settle 回弹两拍：松手后再播一段位移动画被实测
    // 感知为「从原位置移到落点」的闪现）。
    // dragFrom 保留为会话起点；dragMotion 携带当前目标槽位，避免位移与让位状态
    // 分两次通知，原生列表不会在两个中间态之间来回合成。
    // （左滑动作行 2026-09-09 移除：实测不实用，置顶/移除归口长按菜单。）
    /** 拖拽会话中的行；空 = 无会话。 */
    private var dragSymbol: String by observable("")
    /** 跟手位移与目标槽位的原子快照，避免一次 move 触发两轮不一致的渲染。 */
    private var dragMotion: DragMotion by observable(DragMotion(0f, 0))
    /**
     * 拿起时/当前目标槽位（displayList 索引，仅无过滤会话可用）。
     * 会话结束后故意不复位（R5 陷阱见上），由 beginDragLift 重播种。
     */
    private var dragFrom: Int by observable(0)
    /** 拖拽会话期间行情到达被挂起的 displayList 重建（vfor 重建会换视图丢 touchUp）。 */
    private var dragRefreshPending: Boolean = false

    // ── 撤销条：移除是破坏性操作，且从「常驻可见按钮」改成手势后误触率上升，
    //    撤销入口必须落在手指附近（底部），不能沿用顶部 hint。 ──
    private var undoText: String by observable("")
    private var undoTimerRef: String = ""

    // ── doc 30 小空间整合：预警收件箱预览 + 今日速览 ──
    /** 收件箱当前消息（AlertInboxBuilder 派生 + pinned 合并；vfor/badge 从这里读）。 */
    private var inboxMessages: ObservableList<AlertMessage> by observableList()
    /** 未读数（AlertInboxStore 口径），驱动预览条标题与角标。 */
    private var inboxUnread: Int by observable(0)
    /** 预览条入场两拍（R4）。 */
    private var inboxPreviewPresented: Boolean by observable(false)
    /** 速览卡展开态。 */
    private var briefOpen: Boolean by observable(false)
    /** 速览卡入场两拍：首条事实到达时翻转（R4），此后恒 true 不再重复播。 */
    private var briefPresented: Boolean by observable(false)
    /** 速览卡是否该出现（有异动或 30 天内事件）。 */
    private var hasBrief: Boolean by observable(false)
    /** 速览卡事实行（规则引擎产出，每行一句）。 */
    private var briefLines: ObservableList<String> by observableList()

    /** 预约日历 ∩ 自选的未来事件缓存（非 observable；到达即 rebuildInbox）。 */
    private var latestEvents: List<com.kuikly.stockchat.data.provider.MarketCalendarEvent> = emptyList()

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        // R4 两拍：预览条首帧隐藏，下一帧翻入（reduceMotion 直出）。
        if (reduceMotion) {
            inboxPreviewPresented = true
        } else {
            setTimeout(0) { inboxPreviewPresented = true }
        }
        reload()
        // 详情页预取（2026-09-10 空白期治理）：自选标的行情预热进全局预取缓存，
        // 点进详情页 created() 直接命中整页秒开（60s 新鲜窗口内不重复请求；
        // warm 内部自带去重与单次 8 标的上限，防请求风暴）。
        QuotePrefetchStore.warm(watchlistStore.list().map { it.symbol }, MarketFeatureGraph.prefetchTarget(pagerId))
    }

    override fun body(): ViewBuilder {
        val page = this
        // 非受控铁律：TextArea 的 text 只作挂载种子，不绑定响应式文本。
        val searchSeed = ""
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    // 竖向 Scroller 水平 padding 会被双倍扣除，14/14 时右侧多出 28dp 留白；
                    // 右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                    paddingLeft(14f)
                    paddingRight(0f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(60f)
                    // 拖拽排序会话期间锁滚动：长按拍生效（KRRecyclerView.onInterceptTouchEvent
                    // 首查 scrollEnabled，false 即不拦截），后续 move 留在被拿起的行上。
                    scrollEnable(page.dragSymbol.isEmpty())
                }

                // 换肤重建键（同 ChatPage/SettingsPage 约定）：自选页子树以
                // 参数捕获 theme（body 只跑一次，R1），从通用设置改主题/字号
                // 返回后 pageDidAppear 只重读 observable，参数捕获的旧快照
                // 不会刷新——靠 vbind 键翻转整树重建；Scroller 不重建，滚动
                // 位置与拖拽状态不受影响。
                vbind({ page.themeRebuildKey() }) {

                // ── z0 聚合头：回答「我的自选今天整体怎么样」 ──
                vif({ page.rows.isNotEmpty() }) {
                    page.renderAggregateHeader(this)
                }

                // ── doc 30：预警收件箱预览（消息提示的首页入口，badge 即未读数） ──
                vif({ page.inboxMessages.isNotEmpty() }) {
                    page.renderInboxPreview(this)
                }

                // ── z1 筛选：状态在上，分组弱化到第二行 ──
                View {
                    attr { marginTop(12f); flexDirectionRow(); alignItemsCenter() }
                    WatchlistFilterChip(
                        label = "全部 ${page.rows.size}",
                        selected = { page.statusFilter.isEmpty() && page.activeGroup == "all" },
                        theme = page.theme,
                        compact = false,
                    ) {
                        page.statusFilter = ""
                        page.activeGroup = "all"
                        page.refreshDisplay()
                    }
                    vif({ page.aggregate().moverCount > 0 }) {
                        WatchlistFilterChip(
                            label = "异动 ${page.aggregate().moverCount}",
                            selected = { page.statusFilter == FILTER_MOVERS },
                            theme = page.theme,
                            compact = false,
                        ) {
                            page.statusFilter = FILTER_MOVERS
                            page.refreshDisplay()
                        }
                    }
                }
                View {
                    attr { marginTop(7f); flexDirectionRow(); alignItemsCenter() }
                    listOf("core" to "核心观察", "research" to "待研究", "" to "未分组").forEach { (id, label) ->
                        WatchlistFilterChip(
                            label = label,
                            selected = { page.activeGroup == id },
                            theme = page.theme,
                            compact = true,
                        ) {
                            page.activeGroup = id
                            page.reload()
                        }
                    }                }

                // ── 手势标注：操作收进手势后必须有可见的入口说明，否则发现不了。
                // 一行弱提示常驻（textTertiary 10.5f），不与筛选 chip 抢视觉重量。 ──
                vif({ page.rows.isNotEmpty() }) {
                    Text {
                        attr {
                            text("长按卡片拖动可排序 · 长按原地松手看更多操作")
                            marginTop(9f)
                            fontSizeScaled(10.5f)
                            color(page.theme.textTertiary)
                        }
                    }
                }

                // ── FR-W9「没看过」半边：久未点开的一句话提示（事实陈述，不劝删） ──
                vif({ page.staleRows().isNotEmpty() }) {
                    Text {
                        attr {
                            text(page.staleLabel())
                            marginTop(10f)
                            fontSizeScaled(11.5f)
                            lineHeightScaled(17f)
                            color(page.theme.textTertiary)
                        }
                    }
                }

                vif({ page.hint.isNotEmpty() }) {
                    Text {
                        attr {
                            text(page.hint)
                            marginTop(10f)
                            fontSizeScaled(12f)
                            color(page.theme.term)
                        }
                    }
                }

                vif({ page.displayList.isEmpty() && page.rows.isNotEmpty() }) {
                    Text {
                        attr {
                            text("当前筛选下没有标的")
                            marginTop(24f)
                            fontSizeScaled(12.5f)
                            color(page.theme.textTertiary)
                        }
                    }
                }

                vif({ page.rows.isEmpty() }) {
                    WatchlistEmptyState(
                        theme = page.theme,
                        container = this,
                        // 空态的首要任务是找到第一只股票；直接进入完整搜索页，避免
                        // 仅唤起本页 z5 输入浮层而让用户误以为页面没有跳转。
                        onSearch = { page.openPage(Routes.SEARCH) },
                        onOpenMarket = { page.openPage(Routes.MARKET) },
                    )
                }

                vfor({ page.displayList }) { row ->
                    // 主题捕获在构建作用域：theme 读取的是 observable(nightModel)（见
                    // BasePager.isNightMode），若在 attr 里读到会覆盖动画 key（R2 高危
                    // 陷阱，MarketPage 同款处理）。
                    val rowTheme = page.theme
                    // ── 外层：拖拽会话层。跟手位移 / 让位位移 / 层级与投影。
                    // 与内层动画分视图隔离，避免多驱动共键（R3）。──
                    View {
                        attr {
                            marginTop(10f)
                            if (page.dragSymbol == row.symbol) {
                                zIndex(30, useOutline = false)
                                // 阴影和大比例缩放会放大原生合成器的脏矩形；拖拽每帧
                                // 更新 transform 时容易看到上一帧残影，层级已经足够表达抬起。
                                boxShadow(BoxShadow(0f, 4f, 10f, Color(0x000000, 0.10f)))
                                // 跟手位移直出，不注册动画（每帧写会跟动画互相拖拽）；
                                // 松手由 applyDragOrder 同帧落数据并清 transform，无收尾动画。
                                transform(translate = Translate(0f, 0f, offsetY = page.dragMotion.dy))
                            } else {
                                // attr 是增量应用：不显式复位会保留拿起拍的 zIndex/阴影。
                                // 向上重排时该 View 通常被 vfor 复用，于是看起来卡片仍悬浮。
                                zIndex(0, useOutline = false)
                                boxShadow(BoxShadow(0f, 0f, 0f, Color(0x000000, 0f)))
                                // 让位行索引必须在 attr 内实时读取。vfor 对未变行会复用
                                // 原 View；在构建闭包缓存 index 会让上一次排序后的索引残留。
                                val currentIndex = page.displayList.indexOfFirst { it.symbol == row.symbol }
                                transform(
                                    translate = Translate(0f, 0f, offsetY = page.rowSlotShift(currentIndex)),
                                )
                            }
                        }
                        // ── 中层：拿起缩放（「松动」手感）。拖拽态结束时必须直接归零，
                        // 不能注册 dragSymbol spring；否则松手落位后还会延迟播放回落动画，
                        // 视觉上像股票悬浮在落点上方。──
                        View {
                            attr {
                                val lifted = page.dragSymbol == row.symbol
                                val liftScale = if (lifted) 1.02f else 1f
                                transform(scale = Scale(liftScale, liftScale))
                            }
                            View {
                                RowGestureLayer(
                                    onTapContent = { page.openRowDetail(row.symbol) },
                                    onLongPressContent = { page.beginDragLift(row.symbol) },
                                    dragActive = { page.dragSymbol == row.symbol },
                                    onDragMove = { dy -> page.dragMove(dy) },
                                    onDragEnd = { dy, cancelled -> page.dragEnd(dy, cancelled) },
                                ) {
                                    View {
                                        attr {
                                            flexDirectionRow()
                                            // 原SwipeActionRow内容层承担的底色/圆角移到内容根节点
                                            // （动作层摘除后手势层不再管样式）。
                                            backgroundColor(rowTheme.surface)
                                            borderRadius(rowTheme.cardRadius)
                                        }
                                        // 分组从「常驻按钮」收进手势后，状态不能跟着一起消失：
                                        // 用一条 3dp 色带把分组留在行内（LDRS-R：关系→图形映射），
                                        // 视觉成本近乎为零，但「这行属于哪一组」始终可见。
                                        View {
                                            attr {
                                                width(3f)
                                                backgroundColor(groupTint(row.groupId, page.theme))
                                            }
                                        }
                                        View {
                                            attr { flex(1f); padding(12f) }
                                            val quote = row.quote
                                            if (quote != null) {
                                                CardShell(
                                                    StockQuoteCardModel(quote, cardId = "watchlist:${row.symbol}"),
                                                    CardContext(
                                                        theme = page.theme,
                                                        density = CardDensity.MINI,
                                                        onOpenStock = { page.openRowDetail(row.symbol) },
                                                        cardKey = "watchlist:${row.symbol}",
                                                        cardClickable = false,
                                                    ),
                                                )
                                            } else {
                                                WatchlistPendingRow(name = row.name, symbol = row.symbol, theme = page.theme, container = this)
                                            }
                                            vif({ row.quote != null && abs(row.quote!!.changePercent) >= 3.0 }) {
                                                Text {
                                                    val pct = row.quote?.changePercent ?: 0.0
                                                    attr {
                                                        text(if (pct > 0) "异动 ↑" else "异动 ↓")
                                                        marginTop(4f)
                                                        fontSizeScaled(9.5f)
                                                        color(if (pct > 0) page.theme.rise else page.theme.fall)
                                                    }
                                                }
                                            }
                                            // FR-W6：对话加入的标的带 ★ 来源标记（置顶由 sortOrder 保证）
                                            vif({ row.starred }) {
                                                Text {
                                                    attr {
                                                        text("★ 对话加入")
                                                        marginTop(4f)
                                                        fontSizeScaled(9.5f)
                                                        color(page.theme.brand)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── doc 30：今日速览（规则引擎事实句，条件触发；平静日不出现） ──
                vif({ page.hasBrief }) {
                    page.renderBriefCard(this)
                }

                // ── FR-W5 底部通路：扫描完列表，下一问是「我押注了什么」 ──
                vif({ page.rows.isNotEmpty() }) {
                    View {
                        attr {
                            marginTop(14f)
                            height(44f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(14f)
                            paddingRight(14f)
                            borderRadius(12f)
                            backgroundColor(page.theme.surfaceMuted)
                        }
                        event { click { page.openPage(Routes.RISK) } }
                        Text {
                            attr {
                                flex(1f)
                                text("我押注了什么？看共同暴露 ›")
                                fontSizeScaled(12.5f)
                                color(page.theme.textSecondary)
                            }
                        }
                        Text {
                            attr {
                                text("风险地图")
                                fontSizeScaled(11.5f)
                                color(page.theme.brand)
                            }
                        }
                    }
                }

                vif({ page.dataModeLabel.isNotEmpty() }) {
                    Text {
                        attr {
                            text(page.dataModeLabel)
                            marginTop(16f)
                            fontSizeScaled(11f)
                            lineHeightScaled(17f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
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
                    AppTopBarAction(icon = { color, size, _ -> LineIconSearch(color, size) }, onClick = { page.searchOpen = true }),
                ),
            )

            // ── z5 搜索浮层：原顶部长驻搜索框下沉于此（S-1：首屏 200px 让给结论） ──
            vif({ page.searchOpen }) {
                View {
                    attr {
                        absolutePosition()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        zIndex(50, useOutline = false)
                        backgroundColor(Color(0x000000, 0.42f))
                        paddingLeft(14f)
                        paddingRight(14f)
                        paddingTop(page.pagerData.statusBarHeight + 66f)
                    }
                    event {
                        click {
                            page.searchOpen = false
                            page.candidates.clear()
                        }
                    }
                    View {
                        attr {
                            borderRadius(16f)
                            backgroundColor(page.theme.surface)
                            padding(12f)
                        }
                        event { click { /* 吃掉点击，防止冒泡关掉浮层 */ } }
                        View {
                            attr {
                                height(38f)
                                flexDirectionRow()
                                alignItemsCenter()
                                paddingLeft(11f)
                                paddingRight(11f)
                                borderRadius(10f)
                                backgroundColor(page.theme.surfaceMuted)
                            }
                            Text { attr { text("＋"); fontSizeScaled(15f); color(page.theme.textTertiary) } }
                            TextArea {
                                attr {
                                    flex(1f)
                                    marginLeft(6f)
                                    height(36f)
                                    fontSizeScaled(13f)
                                    color(page.theme.textPrimary)
                                    backgroundColor(Color(0xFFFFFFFF, 0f))
                                    text(searchSeed)
                                    placeholder("搜索股票加入自选：贵州茅台 / 600519")
                                    placeholderColor(page.theme.textTertiary)
                                    tintColor(page.theme.brand)
                                    selectionColor(page.theme.brand)
                                }
                                event {
                                    textDidChange(isSyncEdit = true) { state -> page.search(state.text) }
                                }
                            }
                        }
                        vif({ page.candidates.isNotEmpty() }) {
                            View {
                                attr { marginTop(8f) }
                                vfor({ page.candidates }) { security ->
                                    WatchlistCandidateRow(
                                        security = security,
                                        theme = page.theme,
                                        onAdd = { page.add(security) },
                                        container = this,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── z5 长按菜单：分组从「三态循环」改为显式选项列表（D 黑名单 #7） ──
            vif({ page.menuSymbol.isNotEmpty() }) {
                View {
                    attr {
                        absolutePosition()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        zIndex(40, useOutline = false)
                        backgroundColor(Color(0x000000, 0.42f))
                        justifyContentFlexEnd()
                        paddingBottom(page.pagerData.safeAreaInsets.bottom)
                    }
                    event { click { page.menuSymbol = "" } }
                    View {
                        attr {
                            paddingLeft(16f)
                            paddingRight(16f)
                            paddingBottom(28f)
                        }
                        event { click { /* 吃掉点击 */ } }
                        View {
                            attr {
                                borderRadius(18f)
                                backgroundColor(page.theme.surface)
                                paddingTop(6f)
                                paddingBottom(6f)
                            }
                            Text {
                                attr {
                                    text(page.menuTitle())
                                    marginTop(10f)
                                    marginLeft(16f)
                                    fontSizeScaled(11f)
                                    color(page.theme.textTertiary)
                                }
                            }
                            // FR-W2：菜单里回看理由（行上不展示，保持扫描效率）
                            vif({ page.currentReason().isNotEmpty() }) {
                                Text {
                                    attr {
                                        text("当初理由：${page.currentReason()}")
                                        marginTop(3f)
                                        marginLeft(16f)
                                        marginRight(16f)
                                        fontSizeScaled(11.5f)
                                        lineHeightScaled(16f)
                                        color(page.theme.textSecondary)
                                    }
                                }
                            }
                            // FR-W8：理由变更历史回看「我改主意了几次」
                            vif({ page.currentReasonHistory().isNotEmpty() }) {
                                Text {
                                    attr {
                                        text("之前：${page.currentReasonHistory().joinToString(" ← ")}")
                                        marginTop(3f)
                                        marginLeft(16f)
                                        marginRight(16f)
                                        fontSizeScaled(10.5f)
                                        lineHeightScaled(15f)
                                        color(page.theme.textTertiary)
                                    }
                                }
                            }
                            listOf(
                                "core" to "设为核心观察",
                                "research" to "设为待研究",
                                "" to "清除分组",
                            ).forEach { (groupId, label) ->
                                WatchlistMenuRow(label = label, destructive = false, theme = page.theme) {
                                    page.setGroupTo(page.menuSymbol, groupId)
                                    page.menuSymbol = ""
                                }
                            }
                            WatchlistMenuRow(label = if (page.currentReason().isEmpty()) "设置关注理由" else "修改关注理由", destructive = false, theme = page.theme) {
                                page.reasonChip = ""
                                page.reasonTyped = ""
                                page.reasonEditSymbol = page.menuSymbol
                                page.menuSymbol = ""
                            }
                            WatchlistMenuRow(label = "置顶", destructive = false, theme = page.theme) {
                                page.pinToTop(page.menuSymbol)
                                page.menuSymbol = ""
                            }
                            // FR-W7 手动排序：菜单步进（上移/下移一位）。与拖拽排序并存，
                            // 能力等价（可到任意位），给不开拖拽习惯的用户一个显式入口。
                            WatchlistMenuRow(label = "上移一位", destructive = false, theme = page.theme) {
                                page.moveRow(page.menuSymbol, -1)
                                page.menuSymbol = ""
                            }
                            WatchlistMenuRow(label = "下移一位", destructive = false, theme = page.theme) {
                                page.moveRow(page.menuSymbol, +1)
                                page.menuSymbol = ""
                            }
                            WatchlistMenuRow(label = "移除", destructive = true, theme = page.theme) {
                                page.remove(page.menuSymbol)
                                page.menuSymbol = ""
                            }
                        }
                        View {
                            attr {
                                marginTop(8f)
                                height(50f)
                                allCenter()
                                borderRadius(18f)
                                backgroundColor(page.theme.surface)
                            }
                            Text {
                                attr { text("取消"); fontSizeScaled(14f); fontWeightMedium(); color(page.theme.textSecondary) }
                            }
                            event { click { page.menuSymbol = "" } }
                        }
                    }
                }
            }

            // ── z5 理由浮层（FR-W2）：一行输入 + 3 个常用理由 chip，可跳过不强制 ──
            vif({ page.reasonEditSymbol.isNotEmpty() }) {
                View {
                    attr {
                        absolutePosition()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        zIndex(45, useOutline = false)
                        backgroundColor(Color(0x000000, 0.42f))
                        justifyContentFlexEnd()
                        paddingBottom(page.pagerData.safeAreaInsets.bottom)
                    }
                    event { click { page.reasonEditSymbol = "" } }
                    View {
                        attr {
                            marginLeft(16f)
                            marginRight(16f)
                            marginBottom(28f)
                            borderRadius(18f)
                            backgroundColor(page.theme.surface)
                            padding(16f)
                        }
                        event { click { /* 吃掉点击 */ } }
                        Text {
                            attr {
                                text("为什么关注 ${page.rows.firstOrNull { it.symbol == page.reasonEditSymbol }?.name.orEmpty()}？")
                                fontSizeScaled(14f)
                                fontWeightMedium()
                                color(page.theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("记下当初的理由，之后在风险地图对照「当初理由 vs 当前事实」。留空可跳过。")
                                marginTop(4f)
                                fontSizeScaled(11f)
                                lineHeightScaled(16f)
                                color(page.theme.textTertiary)
                            }
                        }
                        View {
                            attr {
                                marginTop(12f)
                                height(38f)
                                flexDirectionRow()
                                alignItemsCenter()
                                paddingLeft(11f)
                                paddingRight(11f)
                                borderRadius(10f)
                                backgroundColor(page.theme.surfaceMuted)
                            }
                            TextArea {
                                attr {
                                    flex(1f)
                                    height(36f)
                                    fontSizeScaled(13f)
                                    color(page.theme.textPrimary)
                                    backgroundColor(Color(0xFFFFFFFF, 0f))
                                    text("")
                                    placeholder("业绩好转 / 前景看好 / 观察一下…（≤40 字）")
                                    placeholderColor(page.theme.textTertiary)
                                    tintColor(page.theme.brand)
                                    selectionColor(page.theme.brand)
                                }
                                event {
                                    textDidChange(isSyncEdit = true) { state ->
                                        page.reasonTyped = state.text
                                        if (state.text.isNotEmpty()) {
                                            page.reasonChip = ""
                                        }
                                    }
                                }
                            }
                        }
                        View {
                            attr { marginTop(10f); flexDirectionRow() }
                            listOf("业绩", "政策", "技术面").forEach { chip ->
                                View {
                                    attr {
                                        marginRight(8f)
                                        paddingTop(6f)
                                        paddingBottom(6f)
                                        paddingLeft(12f)
                                        paddingRight(12f)
                                        borderRadius(14f)
                                        backgroundColor(
                                            if (page.reasonChip == chip) page.theme.brand else page.theme.surfaceMuted,
                                        )
                                    }
                                    event { click { page.reasonChip = if (page.reasonChip == chip) "" else chip } }
                                    Text {
                                        attr {
                                            text(chip)
                                            fontSizeScaled(12f)
                                            color(if (page.reasonChip == chip) Color(0xFFFFFFFF, 1f) else page.theme.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                        View {
                            attr { marginTop(14f); flexDirectionRow(); alignItemsCenter() }
                            View {
                                attr {
                                    flex(1f)
                                    height(42f)
                                    allCenter()
                                    borderRadius(12f)
                                    backgroundColor(page.theme.surfaceMuted)
                                }
                                event { click { page.reasonEditSymbol = "" } }
                                Text { attr { text("跳过"); fontSizeScaled(13.5f); color(page.theme.textSecondary) } }
                            }
                            View {
                                attr {
                                    flex(1f)
                                    marginLeft(10f)
                                    height(42f)
                                    allCenter()
                                    borderRadius(12f)
                                    backgroundColor(page.theme.brand)
                                }
                                event { click { page.saveReason() } }
                                Text { attr { text("保存"); fontSizeScaled(13.5f); fontWeightMedium(); color(Color(0xFFFFFFFF, 1f)) } }
                            }
                        }
                    }
                }
                }

            // 撤销条压在最上层：移除从长按菜单触发，撤销入口落在拇指可达的底部，
            // 沿用顶部 hint 等于没有撤销。
            // UndoBar 以参数捕获 theme：同样挂重建键，换肤时随整树刷新。
            vbind({ page.themeRebuildKey() }) {
            UndoBar(
                theme = page.theme,
                text = { page.undoText },
                actionLabel = "撤销",
                onAction = { page.undoRemove() },
            )
            }
        }
    }

    // ── z0 聚合头 ──

    /**
     * 聚合数据。**结论是算术不是观点**（规则引擎，§6.1）：等权涨跌幅回答「整体」，
     * 红绿盘比回答「结构」，两者组合成一句 ≤16 字的事实性描述，不做任何预测。
     */
    private fun aggregate(): Aggregate {
        val quotes = rows.mapNotNull { it.quote }
        if (quotes.isEmpty()) {
            return Aggregate(rows.size, 0, 0.0, 0, 0, 0, "行情尚未就绪", 0)
        }
        val avg = quotes.map { it.changePercent }.average()
        var rising = 0
        var flat = 0
        var falling = 0
        quotes.forEach { q ->
            when {
                q.changePercent > 0.005 -> rising++
                q.changePercent < -0.005 -> falling++
                else -> flat++
            }
        }
        val movers = quotes.count { abs(it.changePercent) >= 3.0 }
        val conclusion = when {
            avg >= 2.0 && rising >= falling * 2 -> "多数上涨，自选强于大盘"
            avg >= 2.0 -> "上涨，但内部分化明显"
            avg <= -2.0 && falling >= rising * 2 -> "多数回落，与大盘同步"
            avg <= -2.0 -> "回落，但跌势集中在少数标的"
            else -> "整体平稳，波动集中在个别标的"
        }
        return Aggregate(rows.size, quotes.size, avg, rising, flat, falling, conclusion, movers)
    }

    private fun renderAggregateHeader(container: ViewContainer<*, *>) {
        val page = this@WatchlistPage
        val agg = page.aggregate()
        container.View {
            attr {
                padding(18f)
                paddingTop(20f)
                borderRadius(18f)
                backgroundColor(page.aggregateTint(agg.avgPct))
            }
            // FR-W5：聚合头即风险地图入口——「整体怎么样」的下一问永远是「我押注了什么」
            event { click { page.openPage(Routes.RISK) } }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("${agg.total} 只自选 · 等权")
                        fontSizeScaled(11f)
                        color(page.theme.textTertiary)
                    }
                }
                vif({ agg.quoted < agg.total }) {
                    Text {
                        attr {
                            text(" · ${agg.total - agg.quoted} 只无报价")
                            fontSizeScaled(11f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            }
            Text {
                attr {
                    text(Format.percent(agg.avgPct))
                    marginTop(6f)
                    fontSizeScaled(34f)
                    fontWeightBold()
                    color(page.aggregateColor(agg.avgPct))
                }
            }
            Text {
                attr {
                    text(agg.conclusion)
                    marginTop(5f)
                    fontSizeScaled(12.5f)
                    color(page.theme.textSecondary)
                }
            }
            View {
                attr { marginTop(16f) }
                DivergingBar(theme = page.theme, rising = agg.rising, flat = agg.flat, falling = agg.falling)
            }
            Text {
                attr {
                    text("涨 ${agg.rising} · 平 ${agg.flat} · 跌 ${agg.falling}")
                    marginTop(7f)
                    fontSizeScaled(10f)
                    color(page.theme.textTertiary)
                }
            }
        }
    }

    /** 氛围底取色：随整体方向取 riseSoft/fallSoft，平静取中性。只做氛围不做强调。 */
    private fun aggregateTint(avgPct: Double): Color = when {
        avgPct > 0.05 -> theme.riseSoft
        avgPct < -0.05 -> theme.fallSoft
        else -> theme.surfaceMuted
    }

    private fun aggregateColor(avgPct: Double): Color = when {
        avgPct > 0.005 -> theme.rise
        avgPct < -0.005 -> theme.fall
        else -> theme.flat
    }

    // ── doc 30 小空间整合：预警预览条 + 今日速览 ──

    /** 预警收件箱预览条：未读数即 badge，点击进收件箱（应用内消息提示，无远程推送）。 */
    private fun renderInboxPreview(container: ViewContainer<*, *>) {
        val page = this@WatchlistPage
        val rowTheme = theme
        container.View {
            attr {
                marginTop(12f)
                padding(12f)
                borderRadius(14f)
                backgroundColor(rowTheme.surface)
                boxShadow(BoxShadow(0f, 1f, 3f, Color(0x182238, 0.06f)))
                flexDirectionRow()
                alignItemsCenter()
                // 入场两拍（R4/R5）：目标值由 inboxPreviewPresented 决定，animate 恒注册
                // 且是本 attr 最后一次 observable 读取（R2）。
                opacity(if (page.inboxPreviewPresented) 1f else 0f)
                transform(translate = Translate(0f, 0f, offsetY = if (page.inboxPreviewPresented) 0f else 12f))
                if (!page.reduceMotion) {
                    animate(Animation.easeOut(0.28f), page.inboxPreviewPresented)
                }
            }
            event { click { page.openPage(Routes.ALERTS) } }
            View {
                attr {
                    width(36f)
                    height(36f)
                    borderRadius(11f)
                    backgroundColor(rowTheme.brandSoft)
                    allCenter()
                }
                Text { attr { text("⚡"); fontSizeScaled(15f) } }
            }
            View {
                attr { flex(1f); marginLeft(11f); marginRight(8f) }
                Text {
                    attr {
                        text(page.inboxTitle())
                        fontSizeScaled(12.5f)
                        fontWeightSemiBold()
                        color(rowTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        // 在 attr 内读 observableList（R1）：消息重建时预览行即时刷新，
                        // 不能在构建闭包先取快照（那是首帧定格，R1 高危）。
                        text(page.inboxMessages.firstOrNull()?.summary.orEmpty())
                        marginTop(3f)
                        fontSizeScaled(10.5f)
                        color(rowTheme.textSecondary)
                    }
                }
            }
            // 未读角标（badge）：0 不画，与收件箱已读态同源（AlertInboxStore）。
            vif({ page.inboxUnread > 0 }) {
                View {
                    attr {
                        paddingLeft(7f)
                        paddingRight(7f)
                        height(18f)
                        allCenter()
                        borderRadius(9f)
                        backgroundColor(rowTheme.rise)
                        marginRight(6f)
                    }
                    Text {
                        attr {
                            text("${page.inboxUnread}")
                            fontSizeScaled(10f)
                            fontWeightSemiBold()
                            color(Color(0xFFFFFFFF, 1f))
                        }
                    }
                }
            }
            Text {
                attr {
                    text("查看 ›")
                    fontSizeScaled(11f)
                    fontWeightSemiBold()
                    color(rowTheme.brand)
                }
            }
        }
    }

    private fun inboxTitle(): String =
        if (inboxUnread > 0) "预警收件箱 · $inboxUnread 条未读" else "预警收件箱 · 暂无新消息"

    /** 今日速览卡：徽标「速览」（规则引擎产出，不冒称 AI），点击展开事实行。 */
    private fun renderBriefCard(container: ViewContainer<*, *>) {
        val page = this@WatchlistPage
        val cardTheme = theme
        container.View {
            attr {
                marginTop(14f)
                padding(14f)
                borderRadius(16f)
                backgroundColor(cardTheme.surface)
                boxShadow(BoxShadow(0f, 4f, 14f, Color(0x182238, 0.07f)))
                opacity(if (page.briefPresented) 1f else 0f)
                transform(translate = Translate(0f, 0f, offsetY = if (page.briefPresented) 0f else 12f))
                if (!page.reduceMotion) {
                    animate(Animation.easeOut(0.28f), page.briefPresented)
                }
            }
            event { click { page.briefOpen = !page.briefOpen } }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f)
                        borderRadius(6f)
                        backgroundColor(cardTheme.brand)
                    }
                    Text {
                        attr {
                            text("速览")
                            fontSizeScaled(9f)
                            fontWeightSemiBold()
                            color(Color(0xFFFFFFFF, 1f))
                        }
                    }
                }
                Text {
                    attr {
                        flex(1f)
                        text("今日速览 · 开盘前看完")
                        marginLeft(7f)
                        fontSizeScaled(12.5f)
                        fontWeightSemiBold()
                        color(cardTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(if (page.briefOpen) "收起" else "展开")
                        fontSizeScaled(10f)
                        color(cardTheme.textTertiary)
                    }
                }
            }
            // 展开区 vif 直出（与收件箱展开区同口径：不做高度动画，只做事实呈现）。
            vif({ page.briefOpen }) {
                View {
                    attr { marginTop(10f) }
                    vfor({ page.briefLines }) { line ->
                        Text {
                            attr {
                                text(line)
                                marginTop(6f)
                                fontSizeScaled(11.5f)
                                lineHeightScaled(17f)
                                color(cardTheme.textSecondary)
                            }
                        }
                    }
                    Text {
                        attr {
                            text("由规则引擎从行情与事件整理 · 非预测非建议")
                            marginTop(9f)
                            fontSizeScaled(9.5f)
                            color(cardTheme.textTertiary)
                        }
                    }
                }
            }
        }
    }

    /** 收件箱消息重建：builder 纯函数 + pinned 合并去重（契约见 doc 30 §2.4）。 */
    private fun rebuildInbox() {
        val store = dependencies.alertInboxStore
        val watchlist = watchlistStore.list()
        val quotes = rows.associate { it.symbol to it.quote }
        val derived = AlertInboxBuilder.build(
            watchlist = watchlist,
            rules = dependencies.alertStore.list(),
            quotes = quotes,
            events = latestEvents,
            snapshots = dependencies.riskSnapshotStore.all(),
            mutedSymbols = store.mutedRuleSymbols(),
            exposureMuted = store.exposureMuted(),
            quietHours = store.quietHoursEnabled(),
            nowMillis = platformCurrentTimeMillis(),
            today = platformCurrentDate(),
        )
        val merged = (derived + store.extraMessages())
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtMillis }
        inboxMessages.clear()
        merged.forEach(inboxMessages::add)
        inboxUnread = store.unreadCount(merged)
        rebuildBrief()
    }

    /** 速览事实行重建：聚合结论 + 最异常行 + 最近事件，缺数据的行不写（口径诚实）。 */
    private fun rebuildBrief() {
        val agg = aggregate()
        val lines = mutableListOf<String>()
        if (agg.quoted > 0) {
            lines.add("你的 ${agg.total} 只自选等权 ${Format.percent(agg.avgPct)}，${agg.conclusion}。")
        }
        val topMover = rows
            .mapNotNull { row -> row.quote?.let { row to abs(it.changePercent) } }
            .filter { it.second >= 3.0 }
            .maxByOrNull { it.second }
        if (topMover != null) {
            val quote = topMover.first.quote
            if (quote != null) {
                lines.add("「${topMover.first.name} ${Format.percent(quote.changePercent)}」是当前最异常的一行，先看板块再看公告。")
            }
        }
        latestEvents.firstOrNull()?.let { event ->
            lines.add("「${event.name}的${event.kind.label}」安排在 ${event.date}（预约口径，非预测）。")
        }
        briefLines.clear()
        lines.forEach(briefLines::add)
        val nowHas = lines.isNotEmpty()
        // 首条事实到达时播一次入场两拍（R4）；此后恒 true，不重复打扰。
        if (nowHas && !hasBrief && !reduceMotion) {
            briefPresented = false
            setTimeout(0) { briefPresented = true }
        }
        hasBrief = nowHas
    }

    // ── 筛选与行 ──

    private fun displayRows(): List<WatchlistRow> = rows.filter { row ->
        val groupOk = activeGroup == "all" || row.groupId == activeGroup
        val statusOk = statusFilter.isEmpty() || (
            statusFilter == FILTER_MOVERS &&
                row.quote != null &&
                abs(row.quote.changePercent) >= 3.0
            )
        groupOk && statusOk
    }

    /** FR-W9：>STALE_DAYS 天没点开过的行（从未点开按加入时间计，0 时间戳不计）。 */
    private fun staleRows(): List<WatchlistRow> {
        val now = platformCurrentTimeMillis()
        return rows.filter { row ->
            val anchor = if (row.lastViewedAtMillis > 0) row.lastViewedAtMillis else row.addedAtMillis
            anchor > 0 && now - anchor >= STALE_DAYS * DAY_MS
        }
    }

    private fun staleLabel(): String {
        val stale = staleRows()
        val names = stale.take(3).joinToString("、") { it.name }
        val suffix = if (stale.size > 3) " 等 ${stale.size} 只" else ""
        return "「$names$suffix」超过 ${STALE_DAYS} 天没点开过"
    }

    /** FR-W9：点进详情 = 「看过」一笔。埋点先行，失败不影响跳转。 */
    private fun openRowDetail(symbol: String) {
        runCatching { watchlistStore.markViewed(symbol) }
        openStockDetail(symbol, Routes.WATCHLIST)
    }

    // ── 长按拖拽排序：状态机 ──

    /**
     * 让位行位移：被拖行从 [dragFrom] 挪到当前目标槽位，两行之间的行各补一个槽位
     * （±[ROW_SLOT]）。仅无过滤会话（displayList == store 顺序）下成立。
     */
    private fun rowSlotShift(index: Int): Float {
        if (dragSymbol.isEmpty()) return 0f
        val target = dragMotion.target
        if (dragFrom == target) return 0f
        return when {
            target > dragFrom && index > dragFrom && index <= target -> -ROW_SLOT
            target < dragFrom && index < dragFrom && index >= target -> ROW_SLOT
            else -> 0f
        }
    }

    /** 长按拿起：锁列表滚动 + 记录槽位。过滤视图下排序口径混乱，退回直接开菜单。 */
    private fun beginDragLift(symbol: String) {
        if (dragSymbol.isNotEmpty()) return
        if (statusFilter.isNotEmpty() || activeGroup != "all") {
            menuSymbol = symbol
            return
        }
        val index = displayList.indexOfFirst { it.symbol == symbol }
        if (index < 0) return
        dragFrom = index
        dragMotion = DragMotion(0f, index)
        dragSymbol = symbol
    }

    /** 跟手：写实时位移，并按槽距判定目标槽位（越过相邻行中点即让位）。 */
    private fun dragMove(dy: Float) {
        if (dragSymbol.isEmpty()) return
        val target = (dragFrom + (dy / ROW_SLOT).roundToInt()).coerceIn(0, displayList.lastIndex)
        if (dy == dragMotion.dy && target == dragMotion.target) return
        // 一个 observable 写入同时携带位移和目标，避免先画新位移/旧让位、再画
        // 旧位移/新让位的中间帧；这正是跨槽快速拖动时抖动和残影的高发路径。
        dragMotion = DragMotion(dy, target)
    }

    /**
     * 松手收尾：原地松手（未移动）= 长按操作菜单；移动过 = **同帧直接落数据**
     * （applyDragOrder：数据重排 + displayList diff + transform 清除同一渲染批，
     * 卡片跟手到哪就落在哪，无收尾动画）；被系统打断（cancel）只回弹，不开菜单。
     */
    private fun dragEnd(dy: Float, cancelled: Boolean) {
        if (dragSymbol.isEmpty()) return
        val symbol = dragSymbol
        val moved = abs(dy) >= DRAG_MOVE_THRESHOLD
        if (cancelled || !moved) {
            cancelDragSession()
            if (!cancelled) menuSymbol = symbol
            return
        }
        applyDragOrder(symbol)
    }

    /**
     * 会话终态落数据：槽位先取快照（cancel 会清状态）。落位走**增量**路径——
     * 静默落盘 + rows 原位单行移动 + refreshDisplay diffUpdate 对齐；绝不走
     * reload()（rows 整表重建对象 + clear+重加 = 整列 vfor 重挂载，放手即闪）。
     * diff 后只有被拖行 Delete+Insert（remount，内容不变不可感知），其余行 Keep。
     */
    private fun applyDragOrder(symbol: String) {
        val from = dragFrom
        val to = dragMotion.target
        // 行情回调若在拖拽期间到达，不能在落位前先 flush；否则会先绘制旧槽位，
        // 下一帧才移动数据，松手时就会出现卡片悬浮/二次落位。
        dragRefreshPending = false
        cancelDragSession(flushPending = false)
        if (to == from) {
            // 本次没有跨槽，但可能有行情刷新被拖拽会话挂起，仍需补回列表。
            refreshDisplay()
            return
        }
        watchlistStore.moveToIndex(symbol, to)
        val rowsIdx = rows.indexOfFirst { it.symbol == symbol }
        if (rowsIdx >= 0) {
            val row = rows.removeAt(rowsIdx)
            rows.add(to.coerceIn(0, rows.lastIndex), row)
        }
        // displayList 与新序 rows 对齐：displayList 已被上面手工移到新序，
        // diffUpdate 结果全 Keep（零视觉操作）；有挂起的行情更新也一并增量补上。
        refreshDisplay()
    }

    /**
     * 结束会话：清拖拽态（滚动解锁），并补一次被挂起的 displayList 重建。
     * dragMotion 的目标槽位在结束时保留到本轮渲染完成，避免清理状态时产生
     * 一个额外的让位中间帧；下一次 beginDragLift 会重新播种。
     */
    private fun cancelDragSession(flushPending: Boolean = true) {
        dragSymbol = ""
        dragMotion = DragMotion(0f, dragMotion.target)
        if (flushPending && dragRefreshPending) {
            dragRefreshPending = false
            refreshDisplay()
        }
    }

    // ── 长按菜单 ──

    private fun menuTitle(): String {
        val row = rows.firstOrNull { it.symbol == menuSymbol } ?: return "操作"
        val days = if (row.addedAtMillis > 0) {
            ((platformCurrentTimeMillis() - row.addedAtMillis) / DAY_MS).coerceAtLeast(0)
        } else {
            -1
        }
        val tenure = when {
            days < 0 -> ""
            days >= 180 -> " · 已加入 ${days / 30} 个月"
            else -> " · 已加入 $days 天"
        }
        return "${row.name} · 当前：${groupLabel(row.groupId)}$tenure"
    }

    private fun currentReason(): String = rows.firstOrNull { it.symbol == menuSymbol }?.reason.orEmpty()

    /** FR-W8：理由变更历史（store 内最近 3 次修改，新的在前）。 */
    private fun currentReasonHistory(): List<String> =
        watchlistStore.list().firstOrNull { it.symbol == menuSymbol }?.reasonHistory.orEmpty()

    /** FR-W2 保存理由；空输入 = 跳过（允许留空，不强制）。 */
    private fun saveReason() {
        val symbol = reasonEditSymbol
        if (symbol.isEmpty()) return
        val reason = if (reasonChip.isNotEmpty()) reasonChip else reasonTyped.trim()
        if (reason.isNotEmpty()) {
            watchlistStore.setReason(symbol, reason)
            hint = "理由已记录"
            reload()
        }
        reasonEditSymbol = ""
    }

    private fun setGroupTo(symbol: String, groupId: String) {
        watchlistStore.setGroup(symbol, groupId)
        hint = if (groupId.isEmpty()) "已清除分组" else "已移至${groupLabel(groupId)}"
        reload()
    }

    private fun pinToTop(symbol: String) {
        watchlistStore.moveToTop(symbol)
        hint = "已置顶"
        reload()
    }

    /** FR-W7：步进排序。到边界时给中性提示，不静默无反馈。 */
    private fun moveRow(symbol: String, delta: Int) {
        val rowsSnapshot = watchlistStore.list()
        val index = rowsSnapshot.indexOfFirst { it.symbol == symbol }
        if (index < 0) return
        val target = index + delta
        if (target < 0 || target > rowsSnapshot.lastIndex) {
            hint = if (delta < 0) "已经在最上面了" else "已经在最下面了"
            return
        }
        watchlistStore.moveBy(symbol, delta)
        reload()
    }

    // ── 移除与撤销 ──

    private fun remove(symbol: String) {
        val removed = watchlistStore.list().firstOrNull { it.symbol == symbol } ?: return
        watchlistStore.remove(symbol)
        lastRemoved = removed
        showUndo("已移除 ${removed.name}")
        reload()
    }

    private fun showUndo(text: String) {
        undoText = text
        clearTimeout(undoTimerRef)
        undoTimerRef = setTimeout(UNDO_TIMEOUT_MS) { undoText = "" }
    }

    private fun hideUndo() {
        clearTimeout(undoTimerRef)
        undoText = ""
    }

    private companion object {
        const val FILTER_MOVERS = "movers"
        const val UNDO_TIMEOUT_MS = 5000
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** FR-W9：超过该天数没点开过即算「很久没看」。 */
        const val STALE_DAYS = 30

        /**
         * 拖拽排序的槽距估算（行内容 ≈115 + 行距 10）。MINI 行情卡行高非严格相等
         * （异动/★ 标注行多 ~17），让位与落位按此对齐，误差最多半行内，松手落数据
         * 时由真实布局一次对齐（同帧瞬时，无动画）。改行内布局（时间线高度/标注）时同步本值。
         */
        const val ROW_SLOT = 125f

        /** 松手时位移小于该值视为「原地松手」→ 开长按菜单而非排序。 */
        const val DRAG_MOVE_THRESHOLD = 6f
    }

    private fun search(text: String) {
        candidates.clear()
        if (text.isBlank()) return
        val already = watchlistStore.symbols().toSet()
        Securities.search(text, limit = 8)
            .filterNot { it.symbol in already }
            .forEach { candidates.add(it) }
    }

    private fun add(security: Security) {
        when (watchlistStore.add(security.symbol, security.name)) {
            WatchlistAddResult.ADDED -> hint = "已加入自选：${security.name}"
            WatchlistAddResult.ALREADY_IN -> hint = "${security.name} 已在自选中"
            WatchlistAddResult.FULL -> hint = "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
        }
        lastRemoved = null
        candidates.clear()
        reload()
    }

    private fun undoRemove() {
        val item = lastRemoved ?: return
        hideUndo()
        when (watchlistStore.restore(item)) {
            WatchlistAddResult.ADDED -> hint = "已恢复 ${item.name}"
            WatchlistAddResult.ALREADY_IN -> hint = "${item.name} 已在自选中"
            WatchlistAddResult.FULL -> hint = "自选已满 ${WatchlistStore.MAX_ITEMS} 只，无法恢复"
        }
        lastRemoved = null
        reload()
    }

    /** 把渲染层过滤结果落到 [displayList]（vfor 只接受 ObservableList）。 */
    private fun refreshDisplay() {
        // 拖拽排序会话中行情到达不重建 displayList：vfor 重建会换掉行视图，
        // 跟手中的行丢 touchUp 且位移状态悬空；会话结束（cancelDragSession）后补一次。
        if (dragSymbol.isNotEmpty()) {
            dragRefreshPending = true
            return
        }
        // 增量对齐而非 clear+重加：clear+重加 让 ObservableList diff 出「全删+全插」，
        // vfor 整列视图重挂载 = 肉眼可见的整体刷新（行情每次到达都会闪）。
        // diffUpdate 按 Myers diff 只对内容变化的行 Delete+Insert，其余 Keep 复用视图。
        displayList.diffUpdate(displayRows()) { a, b -> a == b }
        // doc 30：行情/列表任一落定后重建收件箱与速览（builder 纯函数，量级小）。
        rebuildInbox()
    }

    /**
     * 重建行数据并逐个拉取行情；缓存价先占位，网络结果到达后原地替换。
     * **不做分组过滤**——聚合头口径必须是完整自选，「N 只自选」不能跟着
     * 筛选联动变成子集；分组过滤由 [displayRows] 在渲染层做。
     */
    private fun reload() {
        rows.clear()
        val items = watchlistStore.list()
        items.forEach { item ->
            rows.add(
                WatchlistRow(
                    item.symbol, item.name,
                    quoteRepository.cachedOrOffline(item.symbol),
                    item.groupId, item.reason, item.addedAtMillis,
                ),
            )
            quoteRepository.load(item.symbol) { result ->
                val index = rows.indexOfFirst { it.symbol == item.symbol }
                val quote = result.quote
                if (index >= 0 && quote != null) {
                    rows[index] = rows[index].copy(quote = quote)
                }
                dataModeLabel = result.mode.quoteLabel()
                refreshDisplay()
            }
        }
        // doc 30：事件临近输入——全市场预约日历 ∩ 自选（口径照抄 RiskMapPage：
        // 代码去后缀匹配 + 未来事件），到达后重建收件箱与速览。
        dependencies.insightRepository.loadCalendar { all ->
            val codes = watchlistStore.list().map { it.symbol.substringBefore('.') }.toSet()
            val today = platformCurrentDate()
            latestEvents = all
                .filter { it.symbol.substringBefore('.') in codes }
                .filter { it.date >= today }
                .sortedBy { it.date }
                .take(8)
            rebuildInbox()
        }
        refreshDisplay()
    }
}

internal data class WatchlistRow(
    val symbol: String,
    val name: String,
    val quote: Quote?,
    val groupId: String,
    /** FR-W2 关注理由。行上不展示（保持扫描效率），只在长按菜单里回看。 */
    val reason: String = "",
    /** FR-W9 停留时长：addedAtMillis 原样带出来，菜单里显示「已加入 N 天」。 */
    val addedAtMillis: Long = 0L,
    /** FR-W6 对话置顶标记：行上以「★ 对话置顶」小字出现。 */
    val starred: Boolean = false,
    /** FR-W9「没看过」半边：最后一次点进详情的时间。0 = 从未点开。 */
    val lastViewedAtMillis: Long = 0L,
)

/** 聚合头数据：算术事实，不含任何观点字段（见 [WatchlistPage.aggregate]）。 */
private data class Aggregate(
    val total: Int,
    val quoted: Int,
    val avgPct: Double,
    val rising: Int,
    val flat: Int,
    val falling: Int,
    val conclusion: String,
    val moverCount: Int,
)

private fun groupLabel(groupId: String): String = when (groupId) {
    "core" -> "核心"
    "research" -> "研究"
    else -> "未分组"
}

/**
 * 分组色带颜色。操作收进手势后，这是分组状态**唯一**的常驻可见表达，
 * 所以三档必须能一眼区分：核心=品牌蓝，待研究=中性灰，未分组=留空（不画）。
 */
private fun groupTint(groupId: String, theme: StockChatTheme): Color = when (groupId) {
    "core" -> theme.brand
    "research" -> theme.term
    else -> Color(0xFFFFFFFF, 0f)
}

/** 筛选 chip。分组行用 [compact] 弱化——它是第二行，不该和状态筛选抢视觉重量。 */
private fun ViewContainer<*, *>.WatchlistFilterChip(
    label: String,
    selected: () -> Boolean,
    theme: StockChatTheme,
    compact: Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            marginRight(7f)
            paddingLeft(if (compact) 9f else 11f)
            paddingRight(if (compact) 9f else 11f)
            height(if (compact) 26f else 30f)
            allCenter()
            borderRadius(if (compact) 8f else 9f)
            backgroundColor(if (selected()) theme.brandSoft else theme.surfaceMuted)
        }
        Text {
            attr {
                text(label)
                fontSizeScaled(if (compact) 10.5f else 11f)
                color(if (selected()) theme.brand else theme.textSecondary)
            }
        }
        event { click { onClick() } }
    }
}

private fun ViewContainer<*, *>.WatchlistMenuRow(
    label: String,
    destructive: Boolean,
    theme: StockChatTheme,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(46f)
            paddingLeft(16f)
            paddingRight(16f)
            justifyContentCenter()
        }
        Text {
            attr {
                text(label)
                fontSizeScaled(14.5f)
                color(if (destructive) theme.rise else theme.textPrimary)
            }
        }
        event { click { onClick() } }
    }
}

private fun WatchlistCandidateRow(
    security: Security,
    theme: StockChatTheme,
    onAdd: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(6f)
            padding(12f)
            borderRadius(12f)
            backgroundColor(theme.surfaceMuted)
        }
        View {
            attr { flex(1f) }
            Text { attr { text(security.name); fontSizeScaled(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
            Text { attr { text(security.symbol); marginTop(2f); fontSizeScaled(10f); color(theme.textTertiary) } }
        }
        View {
            attr {
                paddingLeft(12f)
                paddingRight(12f)
                height(28f)
                allCenter()
                borderRadius(8f)
                backgroundColor(theme.brandSoft)
            }
            Text { attr { text("加自选"); fontSizeScaled(12f); fontWeightSemiBold(); color(theme.brand) } }
            event { click { onAdd() } }
        }
    }
}

private fun WatchlistPendingRow(
    name: String,
    symbol: String,
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { padding(14f); borderRadius(12f); backgroundColor(theme.surfaceMuted) }
        Text { attr { text(name); fontSizeScaled(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
        Text { attr { text("$symbol · 行情加载中"); marginTop(4f); fontSizeScaled(11f); color(theme.textTertiary) } }
    }
}

private fun WatchlistEmptyState(
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
    onSearch: () -> Unit,
    onOpenMarket: () -> Unit,
) {
    container.View {
        attr { marginTop(28f); padding(18f); borderRadius(14f); backgroundColor(theme.surface) }
        Text { attr { text("还没有自选股"); fontSizeScaled(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        Text {
            attr {
                text("点右上角「搜索」加入第一只股票，也可以在聊天里长按股票名、或从股票详情页添加。加入后可以直接问「我的自选今天怎么样」。")
                marginTop(8f)
                fontSizeScaled(12.5f)
                lineHeightScaled(19f)
                color(theme.textSecondary)
            }
        }
        // 磁贴引导（2026-09-10 统一磁贴语言）：直达搜索 / 行情页，替代纯文字提示。
        View {
            attr { flexDirectionRow(); marginTop(14f) }
            FeatureTile(
                label = "去全局搜索",
                theme = theme,
                height = 64f,
                icon = { LineIconSearch(theme.textPrimary, 22f) },
                onClick = onSearch,
            )
            View { attr { width(8f) } }
            FeatureTile(
                label = "去行情页逛逛",
                theme = theme,
                height = 64f,
                icon = { LineIconBarChart(theme.textPrimary, 22f) },
                onClick = onOpenMarket,
            )
        }
    }
}
