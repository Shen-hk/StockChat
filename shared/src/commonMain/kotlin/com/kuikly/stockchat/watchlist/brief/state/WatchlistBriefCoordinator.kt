package com.kuikly.stockchat.watchlist.brief.state

import com.kuikly.stockchat.data.AlertInboxBuilder
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.AlertRule
import com.kuikly.stockchat.data.RiskSnapshot
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.watchlist.state.Aggregate
import com.kuikly.stockchat.watchlist.state.WatchlistRow
import com.kuikly.stockchat.watchlist.state.WatchlistScheduledTask
import com.kuikly.stockchat.watchlist.state.WatchlistScheduler
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import kotlin.math.abs

/**
 * 预警收件箱预览 + 今日速览状态域唯一 owner（doc 47 B-4 第 4 步，doc 30 小空间整合）：
 * 接管原 WatchlistPage 的 `inboxMessages / inboxUnread / inboxPreviewPresented /
 * briefOpen / briefPresented / hasBrief / briefLines`（7 个 observable/list）与
 * `rebuildInbox / rebuildBrief` 派生链（AlertInboxBuilder 纯函数，C-1 自选唯一输入源）。
 *
 * 动效：两块卡均为 R4 两拍入场（inboxPreviewPresented / briefPresented），
 * animate 恒注册（R5）、驱动 key 最后读取（R2），reduceMotion 直出。
 * 两拍计时走 [WatchlistScheduler]（组件内不启动 Timer）。
 */
internal class WatchlistBriefCoordinator(
    private val host: BriefHost,
    private val scheduler: WatchlistScheduler,
) {
    /** 页面对简报域的输入视图：Store 只读通路 + 列表域算术快照。 */
    internal interface BriefHost {
        fun watchlist(): List<WatchlistItem>

        fun alertRules(): List<AlertRule>

        fun snapshots(): List<RiskSnapshot>

        fun mutedRuleSymbols(): Set<String>

        fun exposureMuted(): Boolean

        fun quietHoursEnabled(): Boolean

        fun extraMessages(): List<AlertMessage>

        fun unreadCount(messages: List<AlertMessage>): Int

        fun reduceMotion(): Boolean

        /** 聚合头算术（列表域 aggregate()，等权口径）。 */
        fun aggregate(): Aggregate

        /** 当前行快照（列表域 rows，含最新行情）。 */
        fun rowsSnapshot(): List<WatchlistRow>
    }

    /** 收件箱当前消息（AlertInboxBuilder 派生 + pinned 合并；vfor/badge 从这里读）。 */
    val inboxMessages: ObservableList<AlertMessage> by observableList()

    /** 未读数（AlertInboxStore 口径），驱动预览条标题与角标。 */
    var inboxUnread: Int by observable(0)
        private set

    /** 预览条入场两拍（R4）。 */
    var inboxPreviewPresented: Boolean by observable(false)
        private set

    /** 速览卡展开态。 */
    var briefOpen: Boolean by observable(false)
        private set

    /** 速览卡入场两拍：首条事实到达时翻转（R4），此后恒 true 不再重复播。 */
    var briefPresented: Boolean by observable(false)
        private set

    /** 速览卡是否该出现（有异动或 30 天内事件）。 */
    var hasBrief: Boolean by observable(false)
        private set

    /** 速览卡事实行（规则引擎产出，每行一句）。 */
    val briefLines: ObservableList<String> by observableList()

    /** 预约日历 ∩ 自选的未来事件缓存（非 observable；到达即 rebuildInbox）。 */
    private var latestEvents: List<MarketCalendarEvent> = emptyList()

    private var briefEntranceTask: WatchlistScheduledTask? = null
    private var inboxEntranceTask: WatchlistScheduledTask? = null

    /** R4 两拍：预览条首帧隐藏，下一帧翻入（reduceMotion 直出）。created() 触发一次。 */
    fun armInboxEntrance() {
        if (host.reduceMotion()) {
            inboxPreviewPresented = true
        } else {
            inboxEntranceTask = scheduler.schedule(0) { inboxPreviewPresented = true }
        }
    }

    fun toggleBrief() {
        briefOpen = !briefOpen
    }

    /** 日历 ∩ 自选的事件到达（列表域 reload 的日历回调转发）。 */
    fun updateEvents(events: List<MarketCalendarEvent>) {
        latestEvents = events
        rebuildInbox()
    }

    /** 收件箱消息重建：builder 纯函数 + pinned 合并去重（契约见 doc 30 §2.4）。 */
    fun rebuildInbox() {
        val rows = host.rowsSnapshot()
        val derived = AlertInboxBuilder.build(
            watchlist = host.watchlist(),
            rules = host.alertRules(),
            quotes = rows.associate { it.symbol to it.quote },
            events = latestEvents,
            snapshots = host.snapshots(),
            mutedSymbols = host.mutedRuleSymbols(),
            exposureMuted = host.exposureMuted(),
            quietHours = host.quietHoursEnabled(),
            nowMillis = platformCurrentTimeMillis(),
            today = platformCurrentDate(),
        )
        val merged = (derived + host.extraMessages())
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtMillis }
        inboxMessages.clear()
        merged.forEach(inboxMessages::add)
        inboxUnread = host.unreadCount(merged)
        rebuildBrief(rows)
    }

    /** 速览事实行重建：聚合结论 + 最异常行 + 最近事件，缺数据的行不写（口径诚实）。 */
    private fun rebuildBrief(rows: List<WatchlistRow>) {
        val agg = host.aggregate()
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
        if (nowHas && !hasBrief && !host.reduceMotion()) {
            briefPresented = false
            briefEntranceTask = scheduler.schedule(0) { briefPresented = true }
        }
        hasBrief = nowHas
    }
}
