package com.kuikly.stockchat.page

import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.BridgeModule
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openChatWithQuestion // 集成修复：缺失 import
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.AlertInboxBuilder
import com.kuikly.stockchat.data.AlertKind
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.AlertRule
import com.kuikly.stockchat.app.assembly.MarketDependencies
import com.kuikly.stockchat.app.assembly.MarketFeatureGraph
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBar
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBarAction
import com.kuikly.stockchat.foundation.ui.icon.LineIconChecks
import com.kuikly.stockchat.page.components.InsightSectionTitle
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 预警收件箱页（doc 30：原「异动预警」升级版，@Page 名与 Routes.ALERTS 不变以兼容路由）。
 *
 * 定位：小空间的消息中心。三类消息共用一条时间线——
 * - 行情异动（MOVE）：AlertStore 价格规则 ∩ 实时行情，展开给「归因检查顺序」；
 * - 事件临近（EVENT）：预约日历 ∩ 自选（解禁/财报/分红），展开给「事实卡」；
 * - 暴露变化（EXPOSURE）：风险地图周快照对比，展开给口径事实 + 等权标注。
 *
 * 合规（doc 23 N-R6 维持 + doc 30 §1）：应用内收件箱、无远程推送；消息只解释
 * 已发生/已预约的事实；Mock 演示消息明确标注为测试，不构成真实 AI 结论；「问 AI」
 * 走 openChatWithQuestion 进对话页；静默/免打扰是一等动作；等权估算口径随消息标注。
 *
 * 数据链（pull 模型）：打开页面时由 [AlertInboxBuilder] 从规则/行情/日历/快照派生
 * 消息（纯函数），pinned 消息（风险地图「转预警」写入）合并去重；已读/静默/免打扰
 * 状态落 AlertInboxStore。
 *
 * 联动契约（doc 23 §6.2 + doc 30）：自选是全部输入源（C-1）；术语出口走
 * GlossaryStore.encounter 单点收口（C-2）；本页 AI 触点仅「问 AI」出口且用户主动（C-3）。
 *
 * 动效（AGENTS.md R1–R5）：首屏与消息列表各自沿用全局搜索的阶梯入场；消息数据异步
 * 到达时会重新走 mounted → presented 两拍，避免列表在终态才挂载而丢失动画。animate 恒
 * 注册（R5）且驱动 key 最后读取（R2）；免打扰滑块动画绑定 quietHoursFlag；
 * reduceMotion 全部直出。
 */
@Page(Routes.ALERTS, supportInLocal = true)
internal class AlertCenterPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketFeatureGraph.forPager(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    /** 渲染层列表：vfor 只接受 ObservableList，筛选结果落到这份拷贝（照抄 WatchlistPage）。 */
    private var displayList: ObservableList<AlertMessage> by observableList()
    private var candidates: ObservableList<WatchlistItem> by observableList()

    /** 全量消息（非 observable；改动经 rebuildMessages → refreshDisplay 落到渲染层）。 */
    private var allMessages: List<AlertMessage> = emptyList()

    /** 分诊/分类计数（筛选 chip 角标），chip attr 内读取（R1）。 */
    private var kindCounts: Map<String, Int> by observable(emptyMap())

    /** 筛选："" = 主收件箱；另有未读、稍后看和消息类型。切换经 refreshDisplay 落拷贝。 */
    private var inboxFilter: String by observable("")

    /** 同屏只展开一张卡；空 = 全部收起。 */
    private var openMessageId: String by observable("")

    /** 已读 id 集合的 observable 镜像（store 为准，操作后同步），驱动未读态渲染。 */
    private var readIdSet: Set<String> by observable(emptySet())

    /**
     * 「稍后看」id 集合缓存：每次变更都同步刷新 displayList/kindCounts，
     * 不额外占用 Page observable 预算。
     */
    private var deferredIdSet: Set<String> = emptySet()

    /** 静默规则集合镜像（MOVE 静默按钮显示态）。 */
    private var mutedSymbols: Set<String> by observable(emptySet())

    /** 暴露变化整类静默镜像。 */
    private var exposureMutedFlag: Boolean by observable(false)

    /** 收盘后免打扰开关（滑块动画的驱动 key，R2 最后读取）。 */
    private var quietHoursFlag: Boolean by observable(false)

    /** Android 通知试播偏好；刷新由 kindCounts 承接，避免增加 Page observable。 */
    private var notificationVibrationEnabled = true

    /** 全页阶梯入场两拍（R4）：mounted=false 起步，setTimeout(0) 翻转为 true 播放。 */
    private var inboxPresented: Boolean by observable(false)

    /** 消息卡独立两拍：异步行情/日历数据到达后也能先挂载在起始位，再逐条入场。 */
    private var messageCardsPresented: Boolean by observable(false)
    private var messageEntranceVersion = 0
    private var messageCardsMounted = false
    private var hasAppeared = false

    /** 动作后的瞬时提示（静默成功 / 术语已记一笔），2.5s 清空。 */
    private var actionHint: String by observable("")

    // ── 派生消息的数据源缓存（行情/事件为普通缓存，任一到达即 rebuildMessages）──
    private val quoteMap: MutableMap<String, Quote?> = mutableMapOf()
    private var latestEvents: List<MarketCalendarEvent> = emptyList()
    /** 规则行参与 vfor 渲染，增删后需驱动刷新，所以落 ObservableList。 */
    private var latestRules: ObservableList<AlertRule> by observableList()

    override fun created() {
        super.created()
        readIdSet = dependencies.alertInboxStore.readIds()
        deferredIdSet = dependencies.alertInboxStore.deferredIds()
        mutedSymbols = dependencies.alertInboxStore.mutedRuleSymbols()
        exposureMutedFlag = dependencies.alertInboxStore.exposureMuted()
        quietHoursFlag = dependencies.alertInboxStore.quietHoursEnabled()
        notificationVibrationEnabled = dependencies.alertInboxStore.notificationVibrationEnabled()
        reload()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        hasAppeared = true
        // 与 GlobalSearchPage 同款的两拍与兜底：首帧先登记动画，下一帧才翻转状态。
        inboxPresented = reduceMotion
        if (!reduceMotion) {
            setTimeout(0) { inboxPresented = true }
            setTimeout(600) { inboxPresented = true }
        }
        armMessageCardsEntrance()
    }

    override fun body(): ViewBuilder {
        val page = this
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
                }

                AlertRevealBlock(0, { page.inboxPresented }, page.reduceMotion) {
                    Text {
                        attr {
                            text("只解释已发生的事 · 不构成操作建议 · 无远程推送")
                            fontSizeScaled(10.5f)
                            color(page.theme.textTertiary)
                        }
                    }
                }

                // ── 分诊 + 类型筛选：主收件箱 / 未读 / 稍后看 + 三类消息 ──
                AlertRevealBlock(1, { page.inboxPresented }, page.reduceMotion) {
                    View {
                        attr { marginTop(10f); flexDirectionRow(); flexWrapWrap() }
                        page.renderFilterChip(this, "", "收件箱")
                        page.renderFilterChip(this, FILTER_UNREAD, "未读")
                        page.renderFilterChip(this, FILTER_DEFERRED, "稍后看")
                        page.renderFilterChip(this, AlertKind.MOVE.name, AlertKind.MOVE.label)
                        page.renderFilterChip(this, AlertKind.EVENT.name, AlertKind.EVENT.label)
                        page.renderFilterChip(this, AlertKind.EXPOSURE.name, AlertKind.EXPOSURE.label)
                    }
                }

                vif({ page.actionHint.isNotEmpty() }) {
                    Text {
                        attr {
                            text(page.actionHint)
                            marginTop(8f)
                            fontSizeScaled(10.5f)
                            color(page.theme.brand)
                        }
                    }
                }

                // ── 空态 ──
                vif({ page.displayList.isEmpty() && page.watchlistNonEmpty() }) {
                    AlertRevealBlock(2, { page.inboxPresented }, page.reduceMotion) {
                        View {
                            attr {
                                marginTop(12f)
                                padding(16f)
                                borderRadius(13f)
                                backgroundColor(page.theme.surface)
                            }
                            Text {
                                attr {
                                    text(page.emptyTitle())
                                    fontSizeScaled(14f); fontWeightSemiBold(); color(page.theme.textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text(page.emptyDescription())
                                    marginTop(6f); fontSizeScaled(11.5f); lineHeightScaled(17f); color(page.theme.textSecondary)
                                }
                            }
                        }
                    }
                }
                vif({ !page.watchlistNonEmpty() }) {
                    AlertRevealBlock(2, { page.inboxPresented }, page.reduceMotion) {
                        View {
                            attr {
                                marginTop(12f)
                                padding(16f)
                                borderRadius(13f)
                                backgroundColor(page.theme.surface)
                            }
                            Text {
                                attr { text("收件箱的输入是你的自选"); fontSizeScaled(14f); fontWeightSemiBold(); color(page.theme.textPrimary) }
                            }
                            Text {
                                attr {
                                    text("先到自选股添加关注标的，异动与事件会自动进入这里。")
                                    marginTop(6f); fontSizeScaled(11.5f); lineHeightScaled(17f); color(page.theme.textSecondary)
                                }
                            }
                            Text {
                                attr { text("打开自选股 ›"); marginTop(10f); fontSizeScaled(11f); color(page.theme.brand) }
                            }
                            event { click { page.openPage(Routes.WATCHLIST) } }
                        }
                    }
                }

                // ── 消息时间线 ──
                vforIndex({ page.displayList }) { msg, index, _ ->
                    // 接在页头说明/筛选行之后错开，让行情异动等收件箱消息
                    // 明确成为后续入场节点；异步到达时仍由 messageCardsPresented 单独驱动。
                    AlertRevealBlock(
                        index = 2 + index,
                        visible = { page.messageCardsPresented },
                        reduceMotion = page.reduceMotion,
                        onMounted = page::onMessageCardMounted,
                    ) {
                        page.renderMessageCard(this, msg)
                    }
                }

                // ── 免打扰 + Mock 通知演示 ──
                AlertRevealBlock(6, { page.inboxPresented }, page.reduceMotion) {
                    page.renderQuietHoursCard(this)
                }
                AlertRevealBlock(7, { page.inboxPresented }, page.reduceMotion) {
                    page.renderSystemNotificationCard(this)
                }

                // ── 监控规则区（保留 AlertStore 能力，降级为收件箱次级区）──
                vif({ page.candidates.isNotEmpty() }) {
                    InsightSectionTitle("从自选添加", "默认监控日涨跌幅绝对值 ≥ 3%", page.theme)
                    vforIndex({ page.candidates }) { item, index, _ ->
                        AlertRevealBlock(8 + index, { page.inboxPresented }, page.reduceMotion) {
                            View {
                                attr {
                                    marginBottom(7f); padding(12f); borderRadius(12f)
                                    backgroundColor(page.theme.surface); flexDirectionRow(); alignItemsCenter()
                                }
                                View {
                                    attr { flex(1f) }
                                    Text { attr { text(item.name); fontSizeScaled(13f); color(page.theme.textPrimary) } }
                                    Text { attr { text(item.symbol); marginTop(2f); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                                }
                                Text { attr { text("启用 ±3%"); fontSizeScaled(11f); fontWeightSemiBold(); color(page.theme.brand) } }
                                event { click { page.addRule(item) } }
                            }
                        }
                    }
                }

                AlertRevealBlock(8, { page.inboxPresented }, page.reduceMotion) {
                    InsightSectionTitle("价格监控规则", "启用的规则触发后生成「行情异动」消息", page.theme)
                }
                vforIndex({ page.latestRules }) { rule, index, _ ->
                    AlertRevealBlock(9 + index, { page.inboxPresented }, page.reduceMotion) {
                        page.renderRuleRow(this, rule)
                    }
                }
            }

            // AppTopBar 以参数捕获 theme（首帧快照）：挂重建键，换肤返回后随键翻转重建。
            vbind({ page.themeRebuildKey() }) {
            AppTopBar(
                title = "预警收件箱",
                subtitle = "行情异动 · 事件临近 · 暴露变化 · 只解释已发生的事",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
                actions = listOf(
                    AppTopBarAction(icon = { color, size, _ -> LineIconChecks(color, size) }, onClick = { page.markAllReadNow() }),
                ),
            )
            }
        }
    }

    // ── 筛选 chip ──

    private fun renderFilterChip(container: ViewContainer<*, *>, key: String, label: String) {
        val page = this
        val chipTheme = theme
        container.View {
            attr {
                height(30f)
                paddingLeft(11f)
                paddingRight(11f)
                marginRight(7f)
                marginBottom(7f)
                borderRadius(9f)
                // 选中态读 inboxFilter（R1，attr 内读 → 筛选切换即时重渲染）。
                backgroundColor(if (page.inboxFilter == key) chipTheme.brandSoft else chipTheme.surfaceMuted)
                flexDirectionRow()
                alignItemsCenter()
            }
            event { click {
                page.inboxFilter = if (page.inboxFilter == key) "" else key
                page.refreshDisplay()
            } }
            Text {
                attr {
                    // 计数读 kindCounts（R1）。
                    text("$label ${page.kindCounts[key] ?: 0}")
                    fontSizeScaled(11f)
                    fontWeightSemiBold()
                    color(if (page.inboxFilter == key) chipTheme.brand else chipTheme.textSecondary)
                }
            }
        }
    }

    // ── 消息卡 ──

    private fun renderMessageCard(container: ViewContainer<*, *>, msg: AlertMessage) {
        val page = this
        val cardTheme = theme // 构建作用域捕获（R2：theme 是 observable，不得进 attr 后于驱动读取）
        val isDark = appIsDarkTheme()
        val (kindBgColor, kindFgColor) = kindChipColors(cardTheme, isDark, msg.kind)
        container.View {
            attr {
                marginBottom(9f)
                padding(13f)
                borderRadius(13f)
                backgroundColor(cardTheme.surface)
            }
            event { click { page.toggleExpand(msg) } }

            // 头行：未读点 + 类型徽标 + 标题 + 时间口径
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                vif({ msg.id !in page.readIdSet }) {
                    View {
                        attr {
                            width(7f); height(7f); borderRadius(4f)
                            marginRight(6f)
                            backgroundColor(cardTheme.rise)
                        }
                    }
                }
                View {
                    attr {
                        paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f)
                        borderRadius(6f)
                        backgroundColor(kindBgColor)
                    }
                    Text {
                        attr {
                            text(msg.kind.label)
                            fontSizeScaled(9f)
                            fontWeightSemiBold()
                            color(kindFgColor)
                        }
                    }
                }
                vif({ msg.id in page.deferredIdSet }) {
                    Text {
                        attr {
                            text("稍后")
                            marginLeft(6f)
                            fontSizeScaled(9.5f)
                            color(cardTheme.brand)
                        }
                    }
                }

                Text {
                    attr {
                        flex(1f)
                        text(msg.title)
                        marginLeft(6f)
                        fontSizeScaled(12.5f)
                        fontWeightSemiBold()
                        color(cardTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(page.timeLabel(msg))
                        marginLeft(6f)
                        fontSizeScaled(9.5f)
                        color(cardTheme.textTertiary)
                    }
                }
            }

            // 收起态一行事实；已读卡整体降透明度（读 readIdSet，无动画直出）
            View {
                attr { opacity(if (msg.id in page.readIdSet) 0.72f else 1f) }
                Text {
                    attr {
                        text(msg.summary)
                        marginTop(6f)
                        fontSizeScaled(11f)
                        lineHeightScaled(16f)
                        color(cardTheme.textSecondary)
                    }
                }

                // ── 展开区：先给即时事实速览，深度 AI 解释主动交给对话页 ──
                vif({ page.openMessageId == msg.id }) {
                    View {
                        attr {
                            marginTop(11f)
                            padding(12f)
                            borderRadius(12f)
                            backgroundColor(cardTheme.brandSoft)
                        }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter() }
                            View {
                                attr { flex(1f) }
                                Text {
                                    attr {
                                        text(if (msg.kind == AlertKind.MOVE) "归因速览" else "事实速览")
                                        fontSizeScaled(12f)
                                        fontWeightSemiBold()
                                        color(cardTheme.textPrimary)
                                    }
                                }
                                Text {
                                    attr {
                                        text("基于已发生事实，不替代后续核实")
                                        marginTop(2f)
                                        fontSizeScaled(9.5f)
                                        color(cardTheme.textTertiary)
                                    }
                                }
                            }
                            View {
                                attr {
                                    paddingLeft(7f); paddingRight(7f); paddingTop(3f); paddingBottom(3f)
                                    borderRadius(7f); backgroundColor(cardTheme.surface)
                                    alignItemsCenter(); justifyContentCenter()
                                }
                                Text { attr { text("可追溯"); fontSizeScaled(9f); fontWeightSemiBold(); color(cardTheme.brand) } }
                            }
                        }
                        msg.facts.forEach { fact -> // vfor 只接受 ObservableList，普通 List 用 forEach
                            View {
                                attr {
                                    marginTop(7f); padding(9f); borderRadius(9f)
                                    backgroundColor(cardTheme.surface); flexDirectionRow(); alignItemsCenter()
                                }
                                View {
                                    attr {
                                        width(5f); height(5f); borderRadius(3f); marginRight(7f)
                                        backgroundColor(cardTheme.brand)
                                    }
                                }
                                Text {
                                    attr {
                                        flex(1f); text(fact); fontSizeScaled(10.5f)
                                        lineHeightScaled(16f); color(cardTheme.textSecondary)
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                text("需要判断原因、影响或下一步验证时，再交给 AI 结合对话上下文分析。")
                                marginTop(9f); fontSizeScaled(10f); lineHeightScaled(15f); color(cardTheme.textTertiary)
                            }
                        }
                        View {
                            attr { marginTop(11f); flexDirectionRow(); flexWrapWrap() }
                            page.renderAction(this, "让 AI 深入解释", primary = true) {
                                page.openChatWithQuestion(msg.askQuestion, focusSymbol = msg.symbol)
                            }
                            if (msg.symbol.isNotEmpty()) {
                                page.renderAction(this, "查看行情详情", primary = false) {
                                    page.openStockDetail(msg.symbol, Routes.ALERTS)
                                }
                            }
                            vif({ msg.id !in page.deferredIdSet }) {
                                page.renderAction(this, "稍后看", primary = false) { page.toggleDeferred(msg) }
                            }
                            vif({ msg.id in page.deferredIdSet }) {
                                page.renderAction(this, "移出稍后", primary = false) { page.toggleDeferred(msg) }
                            }
                            when (msg.kind) {
                                AlertKind.MOVE -> {
                                    if (msg.symbol.isNotEmpty()) {
                                        vif({ msg.symbol in page.mutedSymbols }) {
                                            page.renderAction(this, "已静默 ✓", primary = false, enabled = false) {}
                                        }
                                        vif({ msg.symbol !in page.mutedSymbols }) {
                                            page.renderAction(this, "静默本规则", primary = false) { page.muteMove(msg) }
                                        }
                                    }
                                }
                                AlertKind.EVENT -> {
                                    if (msg.termKey.isNotEmpty()) {
                                        page.renderAction(this, "「${msg.termKey}」是什么意思", primary = false) { page.openTerm(msg) }
                                    }
                                }
                                AlertKind.EXPOSURE -> {
                                    page.renderAction(this, "去风险地图", primary = false) { page.openPage(Routes.RISK) }
                                    vif({ !page.exposureMutedFlag }) {
                                        page.renderAction(this, "不再提醒此类", primary = false) { page.muteExposure() }
                                    }
                                }
                            }
                            page.renderAction(this, "删除", primary = false, destructive = true) { page.deleteMessage(msg) }
                        }
                    }
                }
            }
        }
    }

    /** 动作按钮：primary = 品牌底白字；destructive = 警示色文字（如「删除」）；enabled = false 时为静态确认态。 */
    private fun renderAction(
        container: ViewContainer<*, *>,
        label: String,
        primary: Boolean,
        enabled: Boolean = true,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ) {
        val actionTheme = theme
        container.View {
            attr {
                marginRight(8f)
                marginBottom(8f)
                paddingLeft(12f)
                paddingRight(12f)
                height(30f)
                borderRadius(9f)
                backgroundColor(if (primary) actionTheme.brand else actionTheme.surface)
                alignItemsCenter()
                justifyContentCenter()
            }
            if (enabled) {
                event { click { onClick() } }
            }
            Text {
                attr {
                    text(label)
                    fontSizeScaled(11f)
                    fontWeightSemiBold()
                    color(
                        when {
                            primary -> actionTheme.onBrand
                            !enabled -> actionTheme.textTertiary
                            destructive -> actionTheme.fall
                            else -> actionTheme.textPrimary
                        },
                    )
                }
            }
        }
    }

    /** 免打扰卡：滑块开关，动画驱动 key = quietHoursFlag（R2：最后读取）。 */
    private fun renderQuietHoursCard(container: ViewContainer<*, *>) {
        val page = this
        val cardTheme = theme
        container.View {
            attr {
                marginTop(14f)
                padding(13f)
                borderRadius(13f)
                backgroundColor(cardTheme.surface)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr { flex(1f) }
                Text {
                    attr { text("收盘后免打扰"); fontSizeScaled(12f); fontWeightSemiBold(); color(cardTheme.textPrimary) }
                }
                Text {
                    attr {
                        text("15 点后触发的异动不再即时出卡，合并到次日早间")
                        marginTop(2f)
                        fontSizeScaled(10f)
                        color(cardTheme.textTertiary)
                    }
                }
            }
            View {
                attr {
                    width(38f)
                    height(22f)
                    borderRadius(11f)
                    backgroundColor(if (page.quietHoursFlag) cardTheme.brand else cardTheme.divider)
                }
                event { click { page.toggleQuietHours() } }
                View {
                    attr {
                        width(18f)
                        height(18f)
                        borderRadius(9f)
                        marginTop(2f)
                        marginLeft(if (page.quietHoursFlag) 18f else 2f)
                        backgroundColor(Color(0xFFFFFFFF, 1f))
                        if (!page.reduceMotion) {
                            animate(Animation.easeOut(0.18f), page.quietHoursFlag)
                        }
                    }
                }
            }
        }
    }

    /** 多端 Mock 股票预警：系统横幅与收件箱同一条演示消息。 */
    private fun renderSystemNotificationCard(container: ViewContainer<*, *>) {
        val page = this
        val cardTheme = theme
        container.View {
            attr {
                marginTop(9f)
                padding(13f)
                borderRadius(13f)
                backgroundColor(cardTheme.surface)
            }
            Text { attr { text("Mock 股票预警试播"); fontSizeScaled(12f); fontWeightSemiBold(); color(cardTheme.textPrimary) } }
            Text {
                attr {
                    text("Android / iOS / 鸿蒙 · 系统横幅与收件箱同步显示「AI 归因（模拟）」")
                    marginTop(2f); fontSizeScaled(10f); color(cardTheme.textTertiary)
                }
            }
            View {
                attr { height(38f); marginTop(9f); flexDirectionRow(); alignItemsCenter() }
                event { click { page.toggleNotificationVibration() } }
                Text { attr { flex(1f); text("通知震动"); fontSizeScaled(11f); color(cardTheme.textPrimary) } }
                Text {
                    attr {
                        val enabled = page.kindCounts[SETTING_NOTIFICATION_VIBRATION] == 1
                        text(if (enabled) "已开" else "已关")
                        fontSizeScaled(10.5f); fontWeightSemiBold()
                        color(if (enabled) cardTheme.brand else cardTheme.textTertiary)
                    }
                }
            }
            View {
                attr { height(44f); marginTop(5f); flexDirectionRow() }
                View {
                    attr { flex(1f); height(44f); borderRadius(10f); backgroundColor(cardTheme.brand); alignItemsCenter(); justifyContentCenter() }
                    event { click { page.previewMockStockAlert(delayMillis = 0L) } }
                    Text { attr { text("立刻发送"); fontSizeScaled(11.5f); fontWeightSemiBold(); color(cardTheme.onBrand) } }
                }
                View {
                    attr { width(8f) }
                }
                View {
                    attr { flex(1f); height(44f); borderRadius(10f); backgroundColor(cardTheme.surfaceMuted); alignItemsCenter(); justifyContentCenter() }
                    event { click { page.previewMockStockAlert(delayMillis = 3_000L) } }
                    Text { attr { text("3 秒后发送"); fontSizeScaled(11.5f); fontWeightSemiBold(); color(cardTheme.brand) } }
                }
            }
        }
    }

    private fun renderRuleRow(container: ViewContainer<*, *>, rule: AlertRule) {
        val page = this
        val rowTheme = theme
        container.View {
            attr {
                marginBottom(9f)
                padding(14f)
                borderRadius(13f)
                backgroundColor(rowTheme.surface)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f) }
                    Text { attr { text(rule.name); fontSizeScaled(14f); fontWeightSemiBold(); color(rowTheme.textPrimary) } }
                    Text {
                        attr {
                            text("${rule.symbol} · |涨跌幅| ≥ ${Format.percent(rule.thresholdPercent)}${page.amountThresholdLabel(rule)}")
                            marginTop(3f); fontSizeScaled(10f); color(rowTheme.textTertiary)
                        }
                    }
                }
                Text {
                    attr {
                        text(if (rule.enabled) "监控中" else "已暂停")
                        fontSizeScaled(10.5f)
                        color(if (rule.enabled) rowTheme.brand else rowTheme.textTertiary)
                    }
                }
            }
            View {
                attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr { text(if (rule.enabled) "暂停监控" else "恢复监控"); fontSizeScaled(11f); color(rowTheme.brand) }
                    event { click { page.toggleRule(rule.symbol) } }
                }
                Text {
                    attr { text("  ${page.amountThresholdActionLabel(rule)}"); fontSizeScaled(11f); color(rowTheme.brand) }
                    event { click { page.cycleRuleAmountThreshold(rule) } }
                }
                Text {
                    attr { text("  删除"); fontSizeScaled(11f); color(rowTheme.fall) }
                    event { click { page.removeRule(rule.symbol) } }
                }
            }
        }
    }

    // ── 派生辅助 ──

    private fun watchlistNonEmpty(): Boolean = dependencies.watchlistStore.list().isNotEmpty()

    private fun emptyTitle(): String = when (inboxFilter) {
        FILTER_UNREAD -> "未读已经清空"
        FILTER_DEFERRED -> "没有稍后处理的预警"
        AlertKind.MOVE.name -> "暂无行情异动"
        AlertKind.EVENT.name -> "暂无临近事件"
        AlertKind.EXPOSURE.name -> "暂无暴露变化"
        else -> "收件箱是干净的"
    }

    private fun emptyDescription(): String = when (inboxFilter) {
        FILTER_UNREAD -> "新预警到达后会集中出现在这里，方便快速逐条处理。"
        FILTER_DEFERRED -> "展开消息后点「稍后看」，它会离开主收件箱并保留在这里。"
        else -> "异动和事件触发后会出现在这里，每条都附带已发生事实的归因检查。"
    }

    /** 消息时间口径：不引入钟表格式化依赖，用业务口径标签（doc 30 §3.1）。 */
    private fun timeLabel(msg: AlertMessage): String = when (msg.kind) {
        AlertKind.MOVE -> "今日盘中"
        // pinned EVENT 的 id = "EVENT:sym:date"，同 builder 规则，可反解事件日期。
        AlertKind.EVENT -> msg.id.substringAfterLast(':')
        AlertKind.EXPOSURE -> "周快照对比"
    }

    /**
     * 类型徽标配色（doc 30 §3.1）：MOVE=涨跌语境、EVENT=琥珀警示（仅 light 硬编码，
     * dark 降级为 muted 中性色）、EXPOSURE=品牌蓝（风险不制造情绪）。
     * 返回 (背景, 前景)。
     */
    private fun kindChipColors(theme: StockChatTheme, isDark: Boolean, kind: AlertKind): Pair<Color, Color> =
        when (kind) {
            AlertKind.MOVE -> theme.riseSoft to theme.rise
            AlertKind.EVENT ->
                if (isDark) theme.surfaceMuted to theme.textSecondary
                else Color(0xFFFFF7E8) to Color(0xFFA66A00)
            AlertKind.EXPOSURE -> theme.brandSoft to theme.brand
        }

    // ── 数据装载 ──

    private fun reload() {
        val watchlist = dependencies.watchlistStore.list()
        latestRules.clear()
        dependencies.alertStore.list().forEach(latestRules::add)
        candidates.clear()
        val monitored = latestRules.map { it.symbol }.toSet()
        watchlist.filterNot { it.symbol in monitored }.forEach(candidates::add)

        // 规则标的行情：先缓存快照，再逐只刷新（三级降级链，照抄旧页模式）。
        latestRules.forEach { rule ->
            quoteMap[rule.symbol] = dependencies.quoteRepository.cachedOrOffline(rule.symbol)
            dependencies.quoteRepository.load(rule.symbol) { result ->
                quoteMap[rule.symbol] = result.quote
                rebuildMessages(watchlist)
            }
        }

        // 事件临近：全市场预约日历 ∩ 自选（口径照抄 RiskMapPage：代码去后缀匹配 + 未来事件）。
        dependencies.insightRepository.loadCalendar { all ->
            val codes = watchlist.map { it.symbol.substringBefore('.') }.toSet()
            val rawSymbols = watchlist.map { it.symbol }.toSet()
            val today = platformCurrentDate()
            latestEvents = all
                .filter { it.symbol.substringBefore('.') in codes || it.symbol in rawSymbols }
                .filter { it.date >= today }
                .sortedBy { it.date }
                .take(8)
            rebuildMessages(watchlist)
        }

        rebuildMessages(watchlist)
    }

    /** 数据任一源到达后重建全量消息（builder 纯函数 + pinned 合并去重 + 时间倒序）。 */
    private fun rebuildMessages(watchlist: List<WatchlistItem>) {
        val store = dependencies.alertInboxStore
        val derived = AlertInboxBuilder.build(
            watchlist = watchlist,
            rules = latestRules.toList(),
            quotes = quoteMap,
            events = latestEvents,
            snapshots = dependencies.riskSnapshotStore.all(),
            mutedSymbols = store.mutedRuleSymbols(),
            exposureMuted = store.exposureMuted(),
            quietHours = store.quietHoursEnabled(),
            nowMillis = platformCurrentTimeMillis(),
            today = platformCurrentDate(),
        )
        val dismissedIds = store.dismissedIds()
        val merged = (derived + store.extraMessages())
            .distinctBy { it.id }
            .filterNot { it.id in dismissedIds }
            .sortedByDescending { it.createdAtMillis }
        allMessages = merged
        rebuildFilterCounts()
        refreshDisplay()
    }

    /** 筛选结果落到 ObservableList（vfor 只吃拷贝，照抄 WatchlistPage.refreshDisplay）。 */
    private fun refreshDisplay() {
        val hadCards = displayList.isNotEmpty()
        val filtered = when (inboxFilter) {
            FILTER_UNREAD -> allMessages.filter { it.id !in readIdSet && it.id !in deferredIdSet }
            FILTER_DEFERRED -> allMessages.filter { it.id in deferredIdSet }
            "" -> allMessages.filter { it.id !in deferredIdSet }
            else -> allMessages.filter { it.kind.name == inboxFilter && it.id !in deferredIdSet }
        }
        // 数据晚于页面到达时，先把卡片驱动切回起始位；随后 vfor 挂载，下一帧统一翻转。
        if (hasAppeared && !hadCards && filtered.isNotEmpty()) armMessageCardsEntrance()
        displayList.clear()
        filtered.forEach(displayList::add)
        if (filtered.isEmpty()) messageCardsMounted = false
    }

    /** 为现有或即将挂载的消息卡登记「隐藏 → 阶梯可见」两拍，并用版本防止旧计时器串场。 */
    private fun armMessageCardsEntrance(presentationDelay: Int = 0) {
        val version = ++messageEntranceVersion
        messageCardsPresented = reduceMotion
        if (!reduceMotion) {
            setTimeout(presentationDelay) { if (version == messageEntranceVersion) messageCardsPresented = true }
            // 兜底：生命周期/列表批处理丢一环时，也不能让卡片停在透明起始态。
            setTimeout(600) { if (version == messageEntranceVersion) messageCardsPresented = true }
        }
    }

    /**
     * vfor 消息项的真实挂载点。pageDidAppear 可能先于列表子节点创建，必须等 ref
     * 到达后再留出一帧注册动画，否则 presented 会先翻到终态、消息卡直接出现（R4/R5）。
     */
    private fun onMessageCardMounted() {
        if (messageCardsMounted) return
        messageCardsMounted = true
        armMessageCardsEntrance(presentationDelay = 32)
    }

    // ── 用户动作 ──

    private fun toggleExpand(msg: AlertMessage) {
        if (openMessageId == msg.id) {
            openMessageId = ""
            if (inboxFilter == FILTER_UNREAD) refreshDisplay()
            return
        }
        if (openMessageId.isNotEmpty() && inboxFilter == FILTER_UNREAD) refreshDisplay()
        openMessageId = msg.id
        // 展开即已读（doc 30 §3.1）：store 落盘 + observable 镜像同步驱动未读态。
        dependencies.alertInboxStore.markRead(msg.id)
        readIdSet = dependencies.alertInboxStore.readIds()
        rebuildFilterCounts()
    }

    private fun markAllReadNow() {
        dependencies.alertInboxStore.markAllRead(allMessages.map { it.id })
        readIdSet = dependencies.alertInboxStore.readIds()
        rebuildFilterCounts()
        if (inboxFilter == FILTER_UNREAD) refreshDisplay()
    }

    private fun toggleDeferred(msg: AlertMessage) {
        val deferred = dependencies.alertInboxStore.toggleDeferred(msg.id)
        readIdSet = dependencies.alertInboxStore.readIds()
        deferredIdSet = dependencies.alertInboxStore.deferredIds()
        actionHint = if (deferred) "已移到稍后看 · 不再占用未读角标" else "已移回主收件箱"
        openMessageId = ""
        rebuildFilterCounts()
        refreshDisplay()
        scheduleHintClear()
    }

    /**
     * 用户主动删除一条消息（不同于「稍后看」的分诊）：pinned/extra 直接从持久化列表
     * 移除，派生消息记入 dismissedIds 过滤，直到触发条件变化产生新 id 才会重现。
     */
    private fun deleteMessage(msg: AlertMessage) {
        dependencies.alertInboxStore.deleteMessage(msg.id)
        if (openMessageId == msg.id) openMessageId = ""
        actionHint = "已删除该条消息"
        rebuildMessages(dependencies.watchlistStore.list())
        scheduleHintClear()
    }

    private fun rebuildFilterCounts() {
        kindCounts = mapOf(
            "" to allMessages.count { it.id !in deferredIdSet },
            FILTER_UNREAD to allMessages.count { it.id !in readIdSet && it.id !in deferredIdSet },
            FILTER_DEFERRED to allMessages.count { it.id in deferredIdSet },
            AlertKind.MOVE.name to allMessages.count { it.kind == AlertKind.MOVE && it.id !in deferredIdSet },
            AlertKind.EVENT.name to allMessages.count { it.kind == AlertKind.EVENT && it.id !in deferredIdSet },
            AlertKind.EXPOSURE.name to allMessages.count { it.kind == AlertKind.EXPOSURE && it.id !in deferredIdSet },
            SETTING_NOTIFICATION_VIBRATION to if (notificationVibrationEnabled) 1 else 0,
        )
    }

    private fun muteMove(msg: AlertMessage) {
        dependencies.alertInboxStore.toggleMute(msg.symbol)
        mutedSymbols = dependencies.alertInboxStore.mutedRuleSymbols()
        actionHint = "已静默 ${msg.name} 的行情异动 · 可在规则区恢复"
        scheduleHintClear()
    }

    private fun muteExposure() {
        dependencies.alertInboxStore.setExposureMuted(true)
        exposureMutedFlag = dependencies.alertInboxStore.exposureMuted()
        actionHint = "已关闭「暴露变化」类提醒"
        scheduleHintClear()
    }

    private fun toggleQuietHours() {
        dependencies.alertInboxStore.setQuietHours(!quietHoursFlag)
        quietHoursFlag = dependencies.alertInboxStore.quietHoursEnabled()
    }

    private fun toggleNotificationVibration() {
        notificationVibrationEnabled = !notificationVibrationEnabled
        dependencies.alertInboxStore.setNotificationVibrationEnabled(notificationVibrationEnabled)
        rebuildFilterCounts()
    }

    private fun previewMockStockAlert(delayMillis: Long) {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).postMockStockAlert(notificationVibrationEnabled, delayMillis)
        if (delayMillis == 0L) {
            saveMockStockAlert()
            actionHint = "Mock 股票预警已发送，并同步写入收件箱"
        } else {
            actionHint = "3 秒后发送 Mock 股票预警；切到后台也可接收"
            setTimeout(delayMillis.toInt()) {
                saveMockStockAlert()
            }
        }
        scheduleHintClear()
    }

    private fun saveMockStockAlert() {
        val createdAt = platformCurrentTimeMillis()
        dependencies.alertInboxStore.putExtraMessage(
            AlertMessage(
                id = "MOCK_STOCK_ALERT:$createdAt",
                kind = AlertKind.MOVE,
                symbol = "600519.SH",
                name = "贵州茅台",
                title = "贵州茅台上涨 4.28%（测试）",
                summary = "AI 归因（模拟）：演示行情显示股价上涨 ¥68.80；请以真实行情、公告与数据源为准。",
                facts = listOf(
                    "AI 归因（模拟）：本条为功能演示，不是实时分析或投资建议",
                    "模拟行情：现价 ¥1,676.80，较昨收上涨 ¥68.80（+4.28%）",
                    "演示检查项：核对板块表现、最新公告与量价关系",
                ),
                createdAtMillis = createdAt,
                askQuestion = "贵州茅台今天上涨的可能原因有哪些？",
                pinned = true,
            ),
        )
        rebuildMessages(dependencies.watchlistStore.list())
    }

    private fun amountThresholdLabel(rule: AlertRule): String =
        if (rule.thresholdAmount > 0.0) " · |涨跌额| ≥ ¥${formatRuleAmount(rule.thresholdAmount)}" else " · 金额未设置"

    private fun amountThresholdActionLabel(rule: AlertRule): String =
        if (rule.thresholdAmount > 0.0) "金额 ¥${formatRuleAmount(rule.thresholdAmount)}" else "设金额"

    private fun cycleRuleAmountThreshold(rule: AlertRule) {
        val next = when (rule.thresholdAmount) {
            0.0 -> 1.0
            1.0 -> 2.0
            2.0 -> 5.0
            else -> 0.0
        }
        dependencies.alertStore.setThresholdAmount(rule.symbol, next)
        actionHint = if (next > 0.0) "${rule.name} 的涨跌金额阈值已设为 ±¥${formatRuleAmount(next)}" else "${rule.name} 的涨跌金额阈值已关闭"
        scheduleHintClear()
        reload()
    }

    private fun formatRuleAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

    /** 术语出口（C-2 单点收口）：encounter 记一笔后给行内提示，不强制跳页。 */
    private fun openTerm(msg: AlertMessage) {
        if (msg.termKey.isEmpty()) return
        dependencies.glossaryStore.encounter(msg.termKey)
        actionHint = "「${msg.termKey}」已记入知识库 · 可到知识库查看人话解释"
        scheduleHintClear()
    }

    private fun scheduleHintClear() {
        setTimeout(2500) { actionHint = "" }
    }

    private fun addRule(item: WatchlistItem) {
        dependencies.alertStore.upsert(item.symbol, item.name)
        reload()
    }

    private fun toggleRule(symbol: String) {
        dependencies.alertStore.toggle(symbol)
        reload()
    }

    private fun removeRule(symbol: String) {
        dependencies.alertStore.remove(symbol)
        reload()
    }

    private companion object {
        const val FILTER_UNREAD = "__UNREAD__"
        const val FILTER_DEFERRED = "__DEFERRED__"
        const val SETTING_NOTIFICATION_VIBRATION = "__NOTIFICATION_VIBRATION__"
    }
}

/** 全局搜索同款：内容从下方轻推入场，列表项按序错开；减弱动态偏好下直接展示终态。 */
private fun ViewContainer<*, *>.AlertRevealBlock(
    index: Int,
    visible: () -> Boolean,
    reduceMotion: Boolean,
    onMounted: (() -> Unit)? = null,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            val shown = visible()
            opacity(if (shown) 1f else 0f)
            if (!reduceMotion) {
                transform(
                    translate = com.tencent.kuikly.core.base.Translate(
                        0f,
                        if (shown) 0f else 0.28f,
                    ),
                )
                // 与 GlobalSearchPage 使用同一缓动与阶梯间隔；visible 是本 attr 最后读取的驱动。
                animate(Animation.easeOut(0.375f).delay(0.08f + 0.094f * index), visible())
            }
        }
        if (onMounted != null) ref { onMounted() }
        content()
    }
}
