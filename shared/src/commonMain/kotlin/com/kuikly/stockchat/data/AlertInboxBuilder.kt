package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.CalendarEventKind
// 集成修复：LocalAlertProvider 在本包（AlertStore.kt），从 provider 子包 import 无法解析
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.platformCurrentHour
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis

/**
 * 预警收件箱消息构建器（doc 30 §2.2）：**纯函数、无副作用、零 LLM**。
 *
 * 三类消息全部由规则引擎从已有数据派生：
 * - MOVE：AlertStore 价格规则 ∩ 当前行情（复用 LocalAlertProvider 判定）；
 * - EVENT：未来 30 天预约事件 ∩ 自选（解禁/财报/分红；打新不产生消息）；
 * - EXPOSURE：风险地图最近两次周快照的暴露变化（|ΔCR3| ≥ 10 百分点或成员数变化）。
 *
 * 合规（doc 30 §1）：每条消息只陈述已发生/已预约的事实，facts 是「归因检查顺序」
 * 或「事实卡」，不含任何操作建议；快照 < 2 条绝不生成 EXPOSURE（不编造对比）；
 * 免打扰开启时丢弃 15 点后触发的 MOVE（合并进次日，v1 简化口径）。
 *
 * 调用方职责：把 `AlertInboxStore.extraMessages()`（pinned 消息）合并进结果并按
 * id 去重——本构建器不读 store（保持纯函数，RiskMapPage 与 WatchlistPage 共用）。
 */
object AlertInboxBuilder {

    fun build(
        watchlist: List<WatchlistItem>,
        rules: List<AlertRule>,
        quotes: Map<String, Quote?>,
        events: List<MarketCalendarEvent>,
        snapshots: List<RiskSnapshot>,
        mutedSymbols: Set<String>,
        exposureMuted: Boolean,
        quietHours: Boolean,
        nowMillis: Long,
        today: String,
    ): List<AlertMessage> {
        val nameBySymbol = watchlist.associate { it.symbol to it.name }
        val messages = buildList {
            addMoveMessages(rules, quotes, nameBySymbol, mutedSymbols, quietHours, nowMillis)
            addEventMessages(events, nameBySymbol, today, nowMillis)
            addExposureMessage(snapshots, exposureMuted, nowMillis)
        }
        return messages.sortedByDescending { it.createdAtMillis }
    }

    // ── ① 行情异动：价格阈值触发（AlertStore 规则 + LocalAlertProvider 判定） ──

    private fun MutableList<AlertMessage>.addMoveMessages(
        rules: List<AlertRule>,
        quotes: Map<String, Quote?>,
        nameBySymbol: Map<String, String>,
        mutedSymbols: Set<String>,
        quietHours: Boolean,
        nowMillis: Long,
    ) {
        // 免打扰（doc 30 §2.2）：15 点后（收盘 15:00 收盘，留 5 分钟缓冲简化为按小时）
        // 不再即时出卡，异动合并进次日早间（「收盘后免打扰」的产品语义）。
        if (quietHours && platformCurrentHour() >= 15) return
        rules.filter { it.enabled && it.symbol !in mutedSymbols }.forEach { rule ->
            val quote = quotes[rule.symbol] ?: return@forEach
            val trigger = LocalAlertProvider.evaluate(rule, quote) ?: return@forEach
            val name = nameBySymbol[rule.symbol] ?: rule.name
            val direction = if (quote.changePercent >= 0) "上涨" else "下跌"
            add(
                AlertMessage(
                    id = "MOVE:${rule.symbol}:${rule.thresholdPercent}",
                    kind = AlertKind.MOVE,
                    symbol = rule.symbol,
                    name = name,
                    title = "${name}${direction}达到 ${formatPct(quote.changePercent)}",
                    summary = "触发了你设置的 ±${trimNum(rule.thresholdPercent)}% 阈值，先核对板块与公告再下判断。",
                    facts = listOf(
                        "① 板块是否同步：对照同板块行情，先区分是个股波动还是板块整体波动",
                        "② 有无最新公告：近 7 天公告可在详情页查看，公告常是异动的直接解释",
                        "③ 量价关系：现价 ${com.kuikly.stockchat.common.Format.price(quote.price)} · ${formatPct(quote.changePercent)}，放量与否可结合详情页走势确认",
                    ),
                    createdAtMillis = nowMillis,
                    askQuestion = "${name}今天为什么${direction}这么多？",
                ),
            )
        }
    }

    // ── ② 事件临近：未来 30 天预约事件 ∩ 自选 ──

    private fun MutableList<AlertMessage>.addEventMessages(
        events: List<MarketCalendarEvent>,
        nameBySymbol: Map<String, String>,
        today: String,
        nowMillis: Long,
    ) {
        events
            .filter { it.kind != CalendarEventKind.IPO && it.date >= today }
            .forEach { event ->
                val name = nameBySymbol[event.symbol] ?: event.name
                val (title, summary, facts, termKey) = when (event.kind) {
                    CalendarEventKind.UNLOCK -> AlertFacts(
                        title = "${name}：解禁进入 30 天窗口",
                        summary = "${event.date} 有解禁安排。解禁≠减持，只代表可流通数量变化。",
                        facts = listOf(
                            "解禁日期为 ${event.date}，意味着可流通筹码增加（解禁≠减持，不等于必然下跌）",
                            "具体解禁规模以公司公告为准，本页只陈述已预约事项",
                        ),
                        termKey = "UNLOCK",
                    )
                    CalendarEventKind.EARNINGS -> AlertFacts(
                        title = "${name}：财报预约披露 ${event.date}",
                        summary = "来自交易所财报预约日历。披露日临近时波动通常放大（统计口径）。",
                        facts = listOf(
                            "财报预约披露日期为 ${event.date}（统计描述，不是预测）",
                            "披露前后波动可能放大，具体以公司公告为准",
                        ),
                        termKey = "",
                    )
                    else -> AlertFacts(
                        title = "${name}：${event.kind.label}安排临近",
                        summary = "${event.date} 有${event.kind.label}安排（预约口径）。",
                        facts = listOf(
                            "${event.kind.label}安排于 ${event.date}（统计描述，非预测）",
                            "具体以公司公告为准，本页只陈述已预约事项",
                        ),
                        termKey = "",
                    )
                }
                add(
                    AlertMessage(
                        id = "EVENT:${event.symbol}:${event.date}",
                        kind = AlertKind.EVENT,
                        symbol = event.symbol,
                        name = name,
                        title = title,
                        summary = summary,
                        facts = facts,
                        createdAtMillis = nowMillis,
                        askQuestion = "${name}的${event.kind.label}意味着什么？",
                        termKey = termKey,
                    ),
                )
            }
    }

    // ── ③ 暴露变化：最近两次周快照对比 ──

    private fun MutableList<AlertMessage>.addExposureMessage(
        snapshots: List<RiskSnapshot>,
        exposureMuted: Boolean,
        nowMillis: Long,
    ) {
        if (exposureMuted || snapshots.size < 2) return
        val asc = snapshots.sortedBy { it.capturedAtMillis }
        val older = asc[asc.lastIndex - 1]
        val newer = asc.last()
        val deltaCr3 = newer.cr3Percent - older.cr3Percent
        val memberDelta = newer.memberCount - older.memberCount
        // 触发口径（doc 30 §2.2）：|ΔCR3| ≥ 10 个百分点，或成员数变化且 CR3 也有移动；
        // 成员数不动、CR3 不动 = 快照无实质变化，不产生消息。
        val triggered = kotlin.math.abs(deltaCr3) >= 10 || (memberDelta != 0 && deltaCr3 != 0)
        if (!triggered) return
        val concentrationWord = when {
            deltaCr3 > 0 -> "集中"
            deltaCr3 < 0 -> "分散"
            else -> "调整"
        }
        add(
            AlertMessage(
                id = "EXPOSURE:${newer.capturedAtMillis}",
                kind = AlertKind.EXPOSURE,
                symbol = "",
                name = "组合",
                title = "你的组合比上次更${concentrationWord}了",
                summary = "Top 行业 CR3 ${older.cr3Percent}% → ${newer.cr3Percent}%（等权估算）。",
                facts = listOf(
                    "Top 行业 CR3：${older.cr3Percent}% → ${newer.cr3Percent}%",
                    "成员数：${older.memberCount} → ${newer.memberCount}",
                    "这是「你正在承担什么」的变化，不是「该调整」的暗示 · 等权估算",
                ),
                createdAtMillis = nowMillis,
                askQuestion = "我的自选组合集中度变化说明什么？",
                termKey = "HHI",
            ),
        )
    }

    private data class AlertFacts(
        val title: String,
        val summary: String,
        val facts: List<String>,
        val termKey: String,
    )

    private fun formatPct(value: Double): String =
        "${com.kuikly.stockchat.common.Format.signed(value)}%"

    private fun trimNum(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
