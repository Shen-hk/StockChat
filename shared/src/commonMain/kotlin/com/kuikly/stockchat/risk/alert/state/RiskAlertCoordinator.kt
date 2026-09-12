package com.kuikly.stockchat.risk.alert.state

import com.kuikly.stockchat.data.AlertInboxStore
import com.kuikly.stockchat.data.AlertKind
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.RiskSnapshot
import com.kuikly.stockchat.data.provider.CalendarEventKind
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.risk.state.RiskScheduler
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 预警域唯一 owner（doc 47 B-3 第 5 步）：接管原 RiskMapPage 的
 * `selectedPair / convertedEventIds / generatedExposureIds / eventToInboxHint`
 * 与「事件转预警 / 暴露变化生成预警」两条到收件箱的只写通路（doc 30）。
 *
 * 「已转/已生成」的显示态由构建期读一次的 extraMessages() id 集合驱动，
 * 转成功后同步追加 id 触发刷新（避免回读 store）。
 *
 * ⚠️ 底部瞬时提示的清除时序必须原样保留：置文案后 2.5 秒经 scheduler 清空，
 * 且用「先写 "" 由 vif(isNotEmpty) 消失」规避同值 early-return（Kuikly 实测坑）。
 */
internal class RiskAlertCoordinator(
    private val inboxStore: AlertInboxStore,
    private val scheduler: RiskScheduler,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    /** 相关性矩阵选中的配对 "symA|symB"；空 = 未选中。 */
    var selectedPair: String by observable("")
        private set

    /** 构建期读一次 inbox 的 id 集合（EVENT 类）；转成功后同步追加。 */
    var convertedEventIds: Set<String> by observable(emptySet())
        private set

    /** 同上，EXPOSURE 类 id 集合（"EXPOSURE:<capturedAtMillis>"）。 */
    var generatedExposureIds: Set<String> by observable(emptySet())
        private set

    /** 「转预警」后的瞬时提示，时间轴底部一行 brand 色文案。 */
    var eventToInboxHint: String by observable("")
        private set

    fun togglePair(pair: String) {
        selectedPair = if (selectedPair == pair) "" else pair
    }

    /** 读一次 alertInboxStore.extraMessages() 的 id 集合，填充已转/已生成 observable。 */
    fun refreshConverted() {
        val ids = inboxStore.extraMessages().map { it.id }.toSet()
        convertedEventIds = ids
        generatedExposureIds = ids
    }

    /**
     * doc 30：把单条事件组装成 pinned EVENT 消息写入预警收件箱（只写不读）。
     * id 严格用 "EVENT:$symbol:$date"，与 AlertInboxBuilder 同 id 规则避免重复。
     * 事实句只陈述已发生/已预约事项，不含任何 §1 禁词（风险承受/匹配/建议仓位/调仓等）。
     */
    fun convertEventToInbox(event: MarketCalendarEvent, eventId: String) {
        val kindLabel = when (event.kind) {
            CalendarEventKind.EARNINGS -> "财报"
            CalendarEventKind.UNLOCK -> "解禁"
            CalendarEventKind.DIVIDEND -> "分红"
            else -> event.kind.label
        }
        val title = when (event.kind) {
            CalendarEventKind.EARNINGS -> "${event.name}：财报预约披露 ${event.date}"
            else -> "${event.name}：${kindLabel}进入 30 天窗口"
        }
        // 事实卡：描述已预约事项，不下结论、不预测、无操作暗示。
        val facts = when (event.kind) {
            CalendarEventKind.UNLOCK -> listOf(
                "解禁日期为 ${event.date}，意味着可流通筹码增加（解禁≠减持，不等于必然下跌）",
                "具体解禁规模以公司公告为准，本页只陈述已发生/已预约事项",
            )
            CalendarEventKind.EARNINGS -> listOf(
                "财报预约披露日期为 ${event.date}（统计描述，不是预测）",
                "披露前后波动可能放大，具体以公司公告为准",
            )
            else -> listOf(
                "${kindLabel}安排于 ${event.date}（统计描述，非预测）",
                "具体以公司公告为准，本页只陈述已发生/已预约事项",
            )
        }
        val msg = AlertMessage(
            id = eventId,
            kind = AlertKind.EVENT,
            symbol = event.symbol,
            name = event.name,
            title = title,
            summary = "${event.name} 的${kindLabel}安排在 ${event.date}（统计描述，非预测）",
            facts = facts,
            createdAtMillis = nowMillis(),
            askQuestion = "「${event.name}的${kindLabel}意味着什么？」",
            termKey = if (event.kind == CalendarEventKind.UNLOCK) "UNLOCK" else "",
            pinned = true,
        )
        inboxStore.putExtraMessage(msg)
        // 同步已转 observable，避免回读 store；新 Set 实例触发刷新。
        convertedEventIds = convertedEventIds + eventId
        // 底部瞬时提示：先置文案，2.5s 后清空（同值 early-return 规避：先写 "" 由 vif 消失）。
        eventToInboxHint = "已加入预警收件箱 ✓"
        scheduler.schedule(HINT_CLEAR_MS) { eventToInboxHint = "" }
    }

    /**
     * doc 30：把最近两次快照的 CR3 / 成员数变化组装成 EXPOSURE 消息写入预警收件箱（只写不读）。
     * id 严格用 "EXPOSURE:<newer.capturedAtMillis>"，与 AlertInboxBuilder 同 id 规则避免重复。
     * 文案用品牌蓝语境、只陈述事实（Top 行业 CR3 / 成员数变化 + 「等权估算」口径），
     * 不带任何「该减仓 / 调仓」暗示（§1 禁词表）。
     */
    fun generateExposureAlert(older: RiskSnapshot, newer: RiskSnapshot, exposureId: String) {
        val concentrationWord = if (newer.cr3Percent >= older.cr3Percent) "集中" else "分散"
        val msg = AlertMessage(
            id = exposureId,
            kind = AlertKind.EXPOSURE,
            symbol = "",
            name = "组合",
            title = "你的组合比上次更${concentrationWord}了",
            summary = "Top 行业 CR3 ${older.cr3Percent}% → ${newer.cr3Percent}%（等权估算）",
            facts = listOf(
                "Top 行业 CR3：${older.cr3Percent}% → ${newer.cr3Percent}%",
                "成员数：${older.memberCount} → ${newer.memberCount}",
                "变化主因见风险地图 · 等权估算",
            ),
            createdAtMillis = newer.capturedAtMillis,
            askQuestion = "我的自选组合集中度变化说明什么？",
            termKey = "HHI",
            pinned = false,
        )
        inboxStore.putExtraMessage(msg)
        // 同步已生成 observable，避免回读 store。
        generatedExposureIds = generatedExposureIds + exposureId
    }

    companion object {
        // 原状提示是置文案后 2.5 秒清除（原 RiskMapPage L1904），逐值保留。
        const val HINT_CLEAR_MS = 2500
    }
}
