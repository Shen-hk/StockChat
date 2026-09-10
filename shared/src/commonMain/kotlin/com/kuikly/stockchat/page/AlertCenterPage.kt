package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
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
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.InsightSectionTitle
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
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
 * 已发生/已预约的事实；「问 AI」走 openChatWithQuestion 进对话页，本页零 AI 生成
 * 文本；静默/免打扰是一等动作；等权估算口径随消息标注。
 *
 * 数据链（pull 模型）：打开页面时由 [AlertInboxBuilder] 从规则/行情/日历/快照派生
 * 消息（纯函数），pinned 消息（风险地图「转预警」写入）合并去重；已读/静默/免打扰
 * 状态落 AlertInboxStore。
 *
 * 联动契约（doc 23 §6.2 + doc 30）：自选是全部输入源（C-1）；术语出口走
 * GlossaryStore.encounter 单点收口（C-2）；本页 AI 触点仅「问 AI」出口且用户主动（C-3）。
 *
 * 动效（AGENTS.md R1–R5）：消息卡入场走 R4 两拍（inboxPresented + setTimeout 翻转），
 * animate 恒注册（R5）且驱动 key 最后读取（R2）；免打扰滑块动画绑定 quietHoursFlag；
 * reduceMotion 全部直出。
 */
@Page(Routes.ALERTS, supportInLocal = true)
internal class AlertCenterPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    /** 渲染层列表：vfor 只接受 ObservableList，筛选结果落到这份拷贝（照抄 WatchlistPage）。 */
    private var displayList: ObservableList<AlertMessage> by observableList()
    private var candidates: ObservableList<WatchlistItem> by observableList()

    /** 全量消息（非 observable；改动经 rebuildMessages → refreshDisplay 落到渲染层）。 */
    private var allMessages: List<AlertMessage> = emptyList()

    /** 分类计数（筛选 chip 角标）。key = ""|MOVE|EVENT|EXPOSURE，chip attr 内读取（R1）。 */
    private var kindCounts: Map<String, Int> by observable(emptyMap())

    /** 筛选："" = 全部。切换经 refreshDisplay 落拷贝。 */
    private var inboxFilter: String by observable("")

    /** 同屏只展开一张卡；空 = 全部收起。 */
    private var openMessageId: String by observable("")

    /** 已读 id 集合的 observable 镜像（store 为准，操作后同步），驱动未读态渲染。 */
    private var readIdSet: Set<String> by observable(emptySet())

    /** 静默规则集合镜像（MOVE 静默按钮显示态）。 */
    private var mutedSymbols: Set<String> by observable(emptySet())

    /** 暴露变化整类静默镜像。 */
    private var exposureMutedFlag: Boolean by observable(false)

    /** 收盘后免打扰开关（滑块动画的驱动 key，R2 最后读取）。 */
    private var quietHoursFlag: Boolean by observable(false)

    /** 消息卡入场两拍（R4）：mounted=false 起步，setTimeout(0) 翻转为 true 播放入场。 */
    private var inboxPresented: Boolean by observable(false)

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
        mutedSymbols = dependencies.alertInboxStore.mutedRuleSymbols()
        exposureMutedFlag = dependencies.alertInboxStore.exposureMuted()
        quietHoursFlag = dependencies.alertInboxStore.quietHoursEnabled()
        // R4 两拍：首帧以隐藏态挂载，下一帧翻转为 true 播放入场（reduceMotion 直出）。
        if (reduceMotion) {
            inboxPresented = true
        } else {
            setTimeout(0) { inboxPresented = true }
        }
        reload()
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

                Text {
                    attr {
                        text("只解释已发生的事 · 不构成操作建议 · 无远程推送")
                        fontSizeScaled(10.5f)
                        color(page.theme.textTertiary)
                    }
                }

                // ── 筛选行：三类消息 + 计数 ──
                View {
                    attr { marginTop(10f); flexDirectionRow() }
                    page.renderFilterChip(this, "", "全部")
                    page.renderFilterChip(this, AlertKind.MOVE.name, AlertKind.MOVE.label)
                    page.renderFilterChip(this, AlertKind.EVENT.name, AlertKind.EVENT.label)
                    page.renderFilterChip(this, AlertKind.EXPOSURE.name, AlertKind.EXPOSURE.label)
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
                    View {
                        attr {
                            marginTop(12f)
                            padding(16f)
                            borderRadius(13f)
                            backgroundColor(page.theme.surface)
                        }
                        Text {
                            attr { text("收件箱是干净的"); fontSizeScaled(14f); fontWeightSemiBold(); color(page.theme.textPrimary) }
                        }
                        Text {
                            attr {
                                text("异动和事件触发后会出现在这里，每条都附带已发生事实的归因检查。")
                                marginTop(6f); fontSizeScaled(11.5f); lineHeightScaled(17f); color(page.theme.textSecondary)
                            }
                        }
                    }
                }
                vif({ !page.watchlistNonEmpty() }) {
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

                // ── 消息时间线 ──
                vfor({ page.displayList }) { msg ->
                    page.renderMessageCard(this, msg)
                }

                // ── 免打扰：消息节奏比提醒感更重要 ──
                page.renderQuietHoursCard(this)

                // ── 监控规则区（保留 AlertStore 能力，降级为收件箱次级区）──
                vif({ page.candidates.isNotEmpty() }) {
                    InsightSectionTitle("从自选添加", "默认监控日涨跌幅绝对值 ≥ 3%", page.theme)
                    vfor({ page.candidates }) { item ->
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

                InsightSectionTitle("价格监控规则", "启用的规则触发后生成「行情异动」消息", page.theme)
                vfor({ page.latestRules }) { rule ->
                    page.renderRuleRow(this, rule)
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
                    "全部已读" to { page.markAllReadNow() },
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
                // 入场两拍（R4/R5）：目标值由 inboxPresented 决定，animate 恒注册且
                // 是本 attr 最后一次 observable 读取（R2/R3：每卡独立 attr 块，各驱动一次）。
                opacity(if (page.inboxPresented) 1f else 0f)
                transform(translate = com.tencent.kuikly.core.base.Translate(0f, 0f, offsetY = if (page.inboxPresented) 0f else 12f))
                if (!page.reduceMotion) {
                    animate(Animation.easeOut(0.28f), page.inboxPresented)
                }
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

                // ── 展开区：事实卡 + 动作（vif 直出，不做高度动画）──
                vif({ page.openMessageId == msg.id }) {
                    View {
                        attr {
                            marginTop(10f)
                            padding(11f)
                            borderRadius(10f)
                            backgroundColor(cardTheme.brandSoft)
                        }
                        Text {
                            attr {
                                text(if (msg.kind == AlertKind.MOVE) "✦ 归因检查顺序" else "✦ 事实卡")
                                fontSizeScaled(10f)
                                fontWeightSemiBold()
                                color(cardTheme.brand)
                            }
                        }
                        msg.facts.forEach { fact -> // 集成修复：vfor 只接受 ObservableList，普通 List 用 forEach
                            Text {
                                attr {
                                    text(fact)
                                    marginTop(5f)
                                    fontSizeScaled(11f)
                                    lineHeightScaled(17f)
                                    color(cardTheme.textSecondary)
                                }
                            }
                        }
                        View {
                            attr { marginTop(10f); flexDirectionRow() }
                            page.renderAction(this, "问 AI", primary = true) {
                                page.openChatWithQuestion(msg.askQuestion, focusSymbol = msg.symbol)
                            }
                            if (msg.symbol.isNotEmpty()) {
                                page.renderAction(this, "看详情", primary = false) {
                                    page.openStockDetail(msg.symbol, Routes.ALERTS)
                                }
                            }
                            when (msg.kind) {
                                AlertKind.MOVE -> {
                                    if (msg.symbol in page.mutedSymbols) {
                                        page.renderAction(this, "已静默 ✓", primary = false, enabled = false) {}
                                    } else {
                                        page.renderAction(this, "静默本规则", primary = false) { page.muteMove(msg) }
                                    }
                                }
                                AlertKind.EVENT -> {
                                    if (msg.termKey.isNotEmpty()) {
                                        page.renderAction(this, "「${msg.termKey}」是什么意思", primary = false) { page.openTerm(msg) }
                                    }
                                }
                                AlertKind.EXPOSURE -> {
                                    page.renderAction(this, "去风险地图", primary = false) { page.openPage(Routes.RISK) }
                                    if (!page.exposureMutedFlag) {
                                        page.renderAction(this, "不再提醒此类", primary = false) { page.muteExposure() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 动作按钮：primary = 品牌底白字；enabled = false 时为静态确认态。 */
    private fun renderAction(
        container: ViewContainer<*, *>,
        label: String,
        primary: Boolean,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        val actionTheme = theme
        container.View {
            attr {
                marginRight(8f)
                paddingLeft(12f)
                paddingRight(12f)
                height(30f)
                borderRadius(9f)
                backgroundColor(if (primary) actionTheme.brand else actionTheme.surface)
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
                            enabled -> actionTheme.textPrimary
                            else -> actionTheme.textTertiary
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

    private fun renderRuleRow(container: ViewContainer<*, *>, rule: AlertRule) {
        val page = this
        val rowTheme = theme
        container.View {
            attr {
                marginBottom(9f)
                padding(14f)
                borderRadius(13f)
                backgroundColor(rowTheme.surface)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr { flex(1f) }
                Text { attr { text(rule.name); fontSizeScaled(14f); fontWeightSemiBold(); color(rowTheme.textPrimary) } }
                Text {
                    attr {
                        text("${rule.symbol} · |涨跌幅| ≥ ${Format.percent(rule.thresholdPercent)}")
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
            Text {
                attr { text(if (rule.enabled) " 暂停" else " 恢复"); fontSizeScaled(11f); color(rowTheme.brand) }
                event { click { page.toggleRule(rule.symbol) } }
            }
            Text {
                attr { text(" 删除"); fontSizeScaled(11f); color(rowTheme.fall) }
                event { click { page.removeRule(rule.symbol) } }
            }
        }
    }

    // ── 派生辅助 ──

    private fun watchlistNonEmpty(): Boolean = dependencies.watchlistStore.list().isNotEmpty()

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
        val merged = (derived + store.extraMessages())
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtMillis }
        allMessages = merged
        kindCounts = mapOf(
            "" to merged.size,
            AlertKind.MOVE.name to merged.count { it.kind == AlertKind.MOVE },
            AlertKind.EVENT.name to merged.count { it.kind == AlertKind.EVENT },
            AlertKind.EXPOSURE.name to merged.count { it.kind == AlertKind.EXPOSURE },
        )
        refreshDisplay()
    }

    /** 筛选结果落到 ObservableList（vfor 只吃拷贝，照抄 WatchlistPage.refreshDisplay）。 */
    private fun refreshDisplay() {
        displayList.clear()
        val filtered = if (inboxFilter.isEmpty()) allMessages else allMessages.filter { it.kind.name == inboxFilter }
        filtered.forEach(displayList::add)
    }

    // ── 用户动作 ──

    private fun toggleExpand(msg: AlertMessage) {
        if (openMessageId == msg.id) {
            openMessageId = ""
            return
        }
        openMessageId = msg.id
        // 展开即已读（doc 30 §3.1）：store 落盘 + observable 镜像同步驱动未读态。
        dependencies.alertInboxStore.markRead(msg.id)
        readIdSet = dependencies.alertInboxStore.readIds()
    }

    private fun markAllReadNow() {
        dependencies.alertInboxStore.markAllRead(allMessages.map { it.id })
        readIdSet = dependencies.alertInboxStore.readIds()
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
}
