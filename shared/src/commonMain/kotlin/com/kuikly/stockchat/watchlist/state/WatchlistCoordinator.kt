package com.kuikly.stockchat.watchlist.state

import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.entity.Security
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.MarketInsightRepository
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.quoteLabel
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import kotlin.math.abs

/**
 * 自选列表数据/筛选/搜索/菜单/理由编辑/Undo 域唯一 owner（doc 47 B-4 第 1 步）：
 * 接管原 WatchlistPage 的 `rows / displayList / candidates / hint / dataModeLabel /
 * activeGroup / statusFilter / searchOpen / menuSymbol / reasonEditSymbol / reasonChip /
 * reasonTyped / undoText`（13 个 observable/list）与全部 Store Intent。
 *
 * Undo 计时与 `lastRemoved` 归本域（doc 47：组件内不得启动 Timer），走 [WatchlistScheduler]。
 * 拖拽会话域见 [WatchlistDragCoordinator]；收件箱/速览域见 [WatchlistBriefCoordinator]。
 */
internal class WatchlistCoordinator(
    private val host: WatchlistHost,
    private val scheduler: WatchlistScheduler,
) {
    /** 页面对列表域的输入视图：Store / 行情仓储 / 洞察仓储 / 预取。 */
    internal interface WatchlistHost {
        fun watchlistStore(): WatchlistStore

        fun quoteRepository(): QuoteRepository

        fun insightRepository(): MarketInsightRepository

        /** 详情页预取（2026-09-10 空白期治理）。装配根产出 Provider，页面不直接构造。 */
        fun warmPrefetch(symbols: List<String>)
    }

    private val watchlistStore: WatchlistStore get() = host.watchlistStore()

    /** 自选行（reload 内同步装满；行情到达原地替换单行）。 */
    val rows: ObservableList<WatchlistRow> by observableList()

    /** 渲染层过滤结果。vfor 只接受 ObservableList，所以过滤结果要落到这份拷贝上。 */
    val displayList: ObservableList<WatchlistRow> by observableList()

    val candidates: ObservableList<Security> by observableList()

    var hint: String by observable("")
        private set

    var dataModeLabel: String by observable("")
        private set

    /** 分组 Tab：all = 全部，空串 = 未分组，其余为具体分组。 */
    var activeGroup: String by observable("all")
        private set

    /** 状态筛选："" = 全部，"movers" = 仅异动行。分组筛选在 [activeGroup]。 */
    var statusFilter: String by observable("")
        private set

    /** 搜索浮层开关：常驻搜索框下沉到 z5 后，这是唯一入口。 */
    var searchOpen: Boolean by observable(false)
        private set

    /** 长按菜单正在操作的行；空 = 菜单关闭。 */
    var menuSymbol: String by observable("")
        private set

    // ── FR-W2 关注理由浮层：三入口（搜索添加 / 长按菜单 / 详情页）共用同一编辑浮层 ──
    /** 理由浮层正在编辑的行；空 = 关闭。 */
    var reasonEditSymbol: String by observable("")
        private set
    /** 快捷理由 chip 当前选中项；"" = 未选（用自定义输入）。 */
    var reasonChip: String by observable("")
        private set
    /** 自定义输入的实时同步值（TextArea isSyncEdit）。选中 chip 后再打字则 chip 让位。 */
    var reasonTyped: String by observable("")
        private set

    // ── 撤销条：移除是破坏性操作，且从「常驻可见按钮」改成手势后误触率上升，
    //    撤销入口必须落在手指附近（底部），不能沿用顶部 hint。 ──
    var undoText: String by observable("")
        private set
    private var undoTimerTask: WatchlistScheduledTask? = null
    private var lastRemoved: WatchlistItem? = null

    // ── 跨域接线（页面在 created() 装配；默认空实现保证单测可独立构造） ──

    /** 拖拽会话进行中（displayList 重建必须挂起，vfor 重建会换视图丢 touchUp）。 */
    var isDragging: () -> Boolean = { false }
    /** 拖拽会话中到达的刷新 → 转 pending，会话结束时补一次。 */
    var onDragPendingRefresh: () -> Unit = {}
    /** displayList/rows 变化后的下游联动（→ 收件箱/速览重建）。 */
    var onDisplayRebuilt: () -> Unit = {}
    /** 日历 ∩ 自选的事件到达（→ 速览域重建收件箱与速览）。 */
    var onEventsLoaded: (List<MarketCalendarEvent>) -> Unit = {}

    // ── 装载与渲染对齐 ──

    /**
     * 重建行数据并逐个拉取行情；缓存价先占位，网络结果到达后原地替换。
     * **不做分组过滤**——聚合头口径必须是完整自选，「N 只自选」不能跟着
     * 筛选联动变成子集；分组过滤由 [displayRows] 在渲染层做。
     */
    fun reload() {
        rows.clear()
        val items = watchlistStore.list()
        items.forEach { item ->
            rows.add(
                WatchlistRow(
                    item.symbol, item.name,
                    host.quoteRepository().cachedOrOffline(item.symbol),
                    item.groupId, item.reason, item.addedAtMillis,
                ),
            )
            host.quoteRepository().load(item.symbol) { result ->
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
        host.insightRepository().loadCalendar { all ->
            val codes = watchlistStore.list().map { it.symbol.substringBefore('.') }.toSet()
            val today = platformCurrentDate()
            val events = all
                .filter { it.symbol.substringBefore('.') in codes }
                .filter { it.date >= today }
                .sortedBy { it.date }
                .take(8)
            onEventsLoaded(events)
        }
        refreshDisplay()
    }

    /** 详情页预取（created 触发一次；warm 内部自带去重与单次 8 标的上限）。 */
    fun warmPrefetch() {
        host.warmPrefetch(watchlistStore.list().map { it.symbol })
    }

    /** 把渲染层过滤结果落到 [displayList]（vfor 只接受 ObservableList）。 */
    fun refreshDisplay() {
        // 拖拽排序会话中行情到达不重建 displayList：vfor 重建会换掉行视图，
        // 跟手中的行丢 touchUp 且位移状态悬空；会话结束（cancelDragSession）后补一次。
        if (isDragging()) {
            onDragPendingRefresh()
            return
        }
        // 增量对齐而非 clear+重加：clear+重加 让 ObservableList diff 出「全删+全插」，
        // vfor 整列视图重挂载 = 肉眼可见的整体刷新（行情每次到达都会闪）。
        // diffUpdate 按 Myers diff 只对内容变化的行 Delete+Insert，其余 Keep 复用视图。
        displayList.diffUpdate(displayRows()) { a, b -> a == b }
        // doc 30：行情/列表任一落定后重建收件箱与速览（builder 纯函数，量级小）。
        onDisplayRebuilt()
    }

    // ── 筛选意图（chip 点击；渲染层只读，写入收口在本域） ──

    fun selectAllFilter() {
        statusFilter = ""
        activeGroup = "all"
        refreshDisplay()
    }

    fun selectMoversFilter() {
        statusFilter = FILTER_MOVERS
        refreshDisplay()
    }

    fun selectGroup(groupId: String) {
        activeGroup = groupId
        reload()
    }

    // ── 筛选与行 ──

    fun displayRows(): List<WatchlistRow> = rows.filter { row ->
        val groupOk = activeGroup == "all" || row.groupId == activeGroup
        val statusOk = statusFilter.isEmpty() || (
            statusFilter == FILTER_MOVERS &&
                row.quote != null &&
                abs(row.quote.changePercent) >= 3.0
            )
        groupOk && statusOk
    }

    /** FR-W9：>STALE_DAYS 天没点开过的行（从未点开按加入时间计，0 时间戳不计）。 */
    fun staleRows(): List<WatchlistRow> {
        val now = platformCurrentTimeMillis()
        return rows.filter { row ->
            val anchor = if (row.lastViewedAtMillis > 0) row.lastViewedAtMillis else row.addedAtMillis
            anchor > 0 && now - anchor >= STALE_DAYS * DAY_MS
        }
    }

    fun staleLabel(): String {
        val stale = staleRows()
        val names = stale.take(3).joinToString("、") { it.name }
        val suffix = if (stale.size > 3) " 等 ${stale.size} 只" else ""
        return "「$names$suffix」超过 ${STALE_DAYS} 天没点开过"
    }

    /** FR-W9：点进详情 = 「看过」一笔。埋点先行，失败不影响跳转（路由由页面执行）。 */
    fun markViewed(symbol: String) {
        runCatching { watchlistStore.markViewed(symbol) }
    }

    // ── z0 聚合头 ──

    /**
     * 聚合数据。**结论是算术不是观点**（规则引擎，§6.1）：等权涨跌幅回答「整体」，
     * 红绿盘比回答「结构」，两者组合成一句 ≤16 字的事实性描述，不做任何预测。
     */
    fun aggregate(): Aggregate {
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

    // ── 搜索（z5 浮层的输入侧；浮层 UI 在组件层） ──

    fun search(text: String) {
        candidates.clear()
        if (text.isBlank()) return
        val already = watchlistStore.symbols().toSet()
        Securities.search(text, limit = 8)
            .filterNot { it.symbol in already }
            .forEach { candidates.add(it) }
    }

    fun closeSearch() {
        searchOpen = false
        candidates.clear()
    }

    fun openSearch() {
        searchOpen = true
    }

    fun add(security: Security) {
        when (watchlistStore.add(security.symbol, security.name)) {
            WatchlistAddResult.ADDED -> hint = "已加入自选：${security.name}"
            WatchlistAddResult.ALREADY_IN -> hint = "${security.name} 已在自选中"
            WatchlistAddResult.FULL -> hint = "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
        }
        lastRemoved = null
        candidates.clear()
        reload()
    }

    // ── 长按菜单 ──

    fun closeMenu() {
        menuSymbol = ""
    }

    /** 打开某行的长按菜单（拖拽域「原地松手/过滤视图长按」入口）。 */
    fun openMenuFor(symbol: String) {
        menuSymbol = symbol
    }

    fun menuTitle(): String {
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

    fun currentReason(): String = rows.firstOrNull { it.symbol == menuSymbol }?.reason.orEmpty()

    /** FR-W8：理由变更历史（store 内最近 3 次修改，新的在前）。 */
    fun currentReasonHistory(): List<String> =
        watchlistStore.list().firstOrNull { it.symbol == menuSymbol }?.reasonHistory.orEmpty()

    /** FR-W2 保存理由；空输入 = 跳过（允许留空，不强制）。 */
    fun saveReason() {
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

    /** 理由浮层打开（菜单入口）：清 chip 与手输，切到目标行。 */
    fun beginReasonEdit() {
        reasonChip = ""
        reasonTyped = ""
        reasonEditSymbol = menuSymbol
        menuSymbol = ""
    }

    fun closeReasonEdit() {
        reasonEditSymbol = ""
    }

    /** 理由手输与 chip 互斥：打字即让位 chip。 */
    fun onReasonTyped(text: String) {
        reasonTyped = text
        if (text.isNotEmpty()) {
            reasonChip = ""
        }
    }

    fun toggleReasonChip(chip: String) {
        reasonChip = if (reasonChip == chip) "" else chip
    }

    fun setGroupTo(symbol: String, groupId: String) {
        watchlistStore.setGroup(symbol, groupId)
        hint = if (groupId.isEmpty()) "已清除分组" else "已移至${groupLabel(groupId)}"
        reload()
    }

    fun pinToTop(symbol: String) {
        watchlistStore.moveToTop(symbol)
        hint = "已置顶"
        reload()
    }

    /** FR-W7：步进排序。到边界时给中性提示，不静默无反馈。 */
    fun moveRow(symbol: String, delta: Int) {
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

    fun remove(symbol: String) {
        val removed = watchlistStore.list().firstOrNull { it.symbol == symbol } ?: return
        watchlistStore.remove(symbol)
        lastRemoved = removed
        showUndo("已移除 ${removed.name}")
        reload()
    }

    private fun showUndo(text: String) {
        undoText = text
        undoTimerTask?.cancel()
        undoTimerTask = scheduler.schedule(UNDO_TIMEOUT_MS) { undoText = "" }
    }

    fun hideUndo() {
        undoTimerTask?.cancel()
        undoText = ""
    }

    fun undoRemove() {
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

    private companion object {
        const val FILTER_MOVERS = "movers"
        const val UNDO_TIMEOUT_MS = 5000
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** FR-W9：超过该天数没点开过即算「很久没看」。 */
        const val STALE_DAYS = 30
    }
}

/** 分组标签（菜单标题与提示共用）。 */
internal fun groupLabel(groupId: String): String = when (groupId) {
    "core" -> "核心"
    "research" -> "研究"
    else -> "未分组"
}

/** 自选行模型（行情加载只替换 quote 字段，其余保持自选口径）。 */
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

/** 聚合头数据：算术事实，不含任何观点字段（见 [WatchlistCoordinator.aggregate]）。 */
internal data class Aggregate(
    val total: Int,
    val quoted: Int,
    val avgPct: Double,
    val rising: Int,
    val flat: Int,
    val falling: Int,
    val conclusion: String,
    val moverCount: Int,
)
