package com.kuikly.stockchat.detail.domain

import kotlin.math.abs

/**
 * 端侧规则引擎（doc 29 §3 RuleEngine · F1重要度 / ④异动声呐 / B1情绪 / E1置顶 / E2卡内注脚）。
 *
 * 全部为纯函数：(数据) → 文案/枚举/布尔，规则名可回显（doc 29 U3 统一来源标注）。
 * 禁止 import 任何 Kuikly 运行时，保证可在 JVM 单测。
 * 其中异常检测的时间标签经 [AnchorIndex.indexToTimeLabel] 换算（同样为纯函数）。
 */

// ───────────────────────── F1 重要度评级 ─────────────────────────

enum class Materiality { HIGH, MID, LOW }

data class MaterialityResult(val level: Materiality, val rule: String)

/**
 * 公告/研报标题重要度评分（F1）。
 * HIGH：业绩/报告/减持/回购/立案/质押
 * MID：分红/派息/除权
 * LOW：其余
 * rule 回显命中关键词，供 U3「判定依据」展示。
 */
fun materialityOf(title: String): MaterialityResult {
    val highKeys = listOf("业绩", "报告", "减持", "回购", "立案", "质押")
    val midKeys = listOf("分红", "派息", "除权")
    val hitHigh = highKeys.firstOrNull { title.contains(it) }
    val hitMid = midKeys.firstOrNull { title.contains(it) }
    return when {
        hitHigh != null -> MaterialityResult(
            Materiality.HIGH,
            "命中高重要度关键词：$hitHigh"
        )
        hitMid != null -> MaterialityResult(
            Materiality.MID,
            "命中中重要度关键词：$hitMid"
        )
        else -> MaterialityResult(
            Materiality.LOW,
            "未命中高/中重要度关键词（业绩/报告/减持/回购/立案/质押/分红/派息/除权）"
        )
    }
}

// ───────────────────────── ④ 异动声呐检测 ─────────────────────────

data class AnomalyPoint(val index: Int, val isSpike: Boolean, val label: String)

/**
 * 分时异动检测（④声呐）。纯端侧规则：
 *  - 5 点窗口涨跌幅 = (p[i]-p[i-4])/p[i-4]，绝对值 ≥ 0.8%
 *  - 若提供成交量，窗口末位成交量 ≥ 前段（窗口前至多 9 点）均值 1.8 倍才算放量确认
 *  - isSpike = 成交量确认成立（无成交量数据时恒为 false，因无法确认放量）
 *  - label：「<时间> 放量/急跌/拉升 <幅度>%，量能为前段均值 <倍数> 倍」
 *  - 最多返回 5 个，按 |涨跌幅| 降序
 */
fun detectAnomalies(prices: List<Double>, volumes: List<Double>?): List<AnomalyPoint> {
    if (prices.size < 5) return emptyList()
    val hasVol = volumes != null && volumes.size == prices.size
    val points = mutableListOf<AnomalyPoint>()
    for (i in 4 until prices.size) {
        val base = prices[i - 4]
        if (base == 0.0) continue
        val pct = (prices[i] - base) / base * 100.0
        if (abs(pct) < 0.8) continue
        val volRatio = if (hasVol) {
            val priorFrom = (i - 9).coerceAtLeast(0)
            val priorMean = volumes!!.subList(priorFrom, i).average()
            if (priorMean <= 0.0) Double.POSITIVE_INFINITY else volumes[i] / priorMean
        } else Double.POSITIVE_INFINITY
        val isSpike = hasVol && volRatio >= 1.8
        if (hasVol && volRatio < 1.8) continue // 有量数据：必须放量确认
        val dir = if (pct > 0) "拉升" else "急跌"
        val verb = if (isSpike) "放量$dir" else dir
        val label = buildString {
            append(AnchorIndex.indexToTimeLabel(i))
            append(" ")
            append(verb)
            append(" ")
            append(fmt1(abs(pct)))
            append("%")
            if (hasVol) {
                append("，量能为前段均值 ")
                append(fmt1(volRatio))
                append(" 倍")
            }
        }
        points.add(AnomalyPoint(index = i, isSpike = isSpike, label = label))
    }
    return sortedByPctDesc(points, prices)
}

// AnomalyPoint 未携带涨跌幅，这里按索引回算 5 点窗口涨跌幅做降序，取前 5（保持纯函数、无副作用）
private fun sortedByPctDesc(points: List<AnomalyPoint>, prices: List<Double>): List<AnomalyPoint> {
    return points.sortedByDescending { p ->
        val base = prices.getOrNull(p.index - 4) ?: return@sortedByDescending 0.0
        if (base == 0.0) 0.0 else abs((prices[p.index] - base) / base)
    }.take(5)
}

// ───────────────────────── B1 新闻情绪打分 ─────────────────────────

data class NewsSentiment(val isPositive: Boolean?, val rule: String)

/**
 * 新闻标题情绪打分（B1 先览红绿灰点）。
 * 利好词典：上涨/净买入/增持/回购/超预期/突破/中标/利好/签约/分红 ……
 * 利空词典：下跌/减持/净卖出/立案/违规/亏损/下行/走弱/退坡/利空/警告 ……
 * 仅利好 → true；仅利空 → false；均无或兼有 → null（中性）。
 */
fun scoreNewsSentiment(title: String): NewsSentiment {
    val positiveWords = listOf(
        "上涨", "净买入", "增持", "回购", "超预期", "突破", "中标", "利好", "签约", "分红"
    )
    val negativeWords = listOf(
        "下跌", "减持", "净卖出", "立案", "违规", "亏损", "下行", "走弱", "退坡", "利空", "警告"
    )
    val hitPos = positiveWords.filter { title.contains(it) }
    val hitNeg = negativeWords.filter { title.contains(it) }
    val rule = buildString {
        append("利好词：")
        append(if (hitPos.isEmpty()) "无" else hitPos.joinToString("/"))
        append("；利空词：")
        append(if (hitNeg.isEmpty()) "无" else hitNeg.joinToString("/"))
    }
    return when {
        hitPos.isNotEmpty() && hitNeg.isEmpty() -> NewsSentiment(true, rule)
        hitNeg.isNotEmpty() && hitPos.isEmpty() -> NewsSentiment(false, rule)
        else -> NewsSentiment(null, rule)
    }
}

// ───────────────────────── E1 今日相关置顶 ─────────────────────────

data class RelevanceAnchor(val cardId: String, val pinnedToday: Boolean, val reason: String)

/**
 * 今日相关置顶仲裁（E1）：返回第一个 pinnedToday 且位于 priority 中的 cardId；
 * 无命中返回 null（维持原序，doc 29 E1「无事件日原序」）。
 */
fun pickPinnedCard(anchors: List<RelevanceAnchor>, priority: List<String>): String? {
    return anchors.firstOrNull { it.pinnedToday && it.cardId in priority }?.cardId
}

// ───────────────────────── E2 卡内注脚 ─────────────────────────

data class CardFootnote(val text: String, val rationale: String)

/**
 * 资金流注脚（E2/资金卡）：末段连续 ≥3 日同号净流向才给注脚。
 * 例：「连续 3 日净流出，为近 20 日首次」。rationale 回显判定规则。
 */
fun fundFlowFootnote(dailyNetInflows: List<Double>): CardFootnote? {
    if (dailyNetInflows.size < 3) return null
    val last = dailyNetInflows.last()
    val sign = when {
        last > 0.0 -> 1
        last < 0.0 -> -1
        else -> 0
    }
    if (sign == 0) return null
    // 从末尾向前数连续同号天数
    var run = 0
    for (i in dailyNetInflows.indices.reversed()) {
        val s = when {
            dailyNetInflows[i] > 0.0 -> 1
            dailyNetInflows[i] < 0.0 -> -1
            else -> 0
        }
        if (s == sign) run++ else break
    }
    if (run < 3) return null
    val dir = if (sign > 0) "净流入" else "净流出"
    val firstIdx = dailyNetInflows.size - run
    val isFirstIn20 = firstIdx >= (dailyNetInflows.size - 20).coerceAtLeast(0)
    val text = buildString {
        append("连续 ")
        append(run)
        append(" 日")
        append(dir)
        if (isFirstIn20) append("，为近 20 日首次")
    }
    val rationale = "判定规则：末 $run 日净流向同号（${dir}）且 ≥3 日；首段起始索引 $firstIdx"
    return CardFootnote(text, rationale)
}

/**
 * 股东户数注脚（E2/股东卡）：户数降 + 户均升 → 筹码集中。
 */
fun shareholderFootnote(holderCountChangePct: Double, perHolderChangePct: Double): CardFootnote? {
    if (holderCountChangePct >= 0.0 || perHolderChangePct <= 0.0) return null
    val text = buildString {
        append("筹码集中（户数下降 ")
        append(fmt1(abs(holderCountChangePct)))
        append("%，户均持股上升 ")
        append(fmt1(perHolderChangePct))
        append("%）")
    }
    val rationale = "判定规则：户数变化 ${fmt1(holderCountChangePct)}% < 0 且 户均变化 ${fmt1(perHolderChangePct)}% > 0"
    return CardFootnote(text, rationale)
}

/**
 * 财务注脚（E2/财务卡）：营收正增长时给「增速高于行业 N% 分位」注脚。
 */
fun financialFootnote(revenueGrowthPct: Double, industryPercentile: Int): CardFootnote? {
    if (revenueGrowthPct <= 0.0) return null
    val text = buildString {
        append("营收增速 ")
        append(fmt1(revenueGrowthPct))
        append("% 高于行业 ")
        append(industryPercentile)
        append("% 分位")
    }
    val rationale = "判定规则：营收增速 ${fmt1(revenueGrowthPct)}% > 0，行业分位 $industryPercentile"
    return CardFootnote(text, rationale)
}

// ───────────────────────── 内部格式化工具 ─────────────────────────

/** 保留 1 位小数（多平台安全，避免依赖 JVM String.format）。 */
internal fun fmt1(v: Double): String {
    val neg = v < 0.0
    val a = kotlin.math.abs(v)
    val scaled = kotlin.math.round(a * 10.0) / 10.0
    val i = kotlin.math.floor(scaled).toLong()
    val d = kotlin.math.round((scaled - i) * 10.0).toLong()
    val s = "$i.$d"
    return if (neg) "-$s" else s
}

// ───────────────────────── G1 因子权重重放 ─────────────────────────

/**
 * 因子重放单因子（doc 29 §4.12）。baseContributionPct 为「对当日涨跌」的基准贡献百分比。
 * 此前落在 UI 文件（DetailBoardBlocks），按 §3「RuleEngine 是端侧规则模板的家」
 * 挪入纯函数层，使 §4.12 验收「重算结果与手算一致（纯函数单测）」可执行。
 */
data class FactorSpec(
    val name: String,
    val baseContributionPct: Double,
)

/**
 * G1 数学重算：Σ base_i × weight_i（与页面涨跌无关的纯函数）。
 * 权重缺失时按 1.0（复原态）参与计算。
 */
fun replayContribution(factors: List<FactorSpec>, weights: List<Double>): Double {
    var sum = 0.0
    factors.forEachIndexed { i, f ->
        sum += f.baseContributionPct * weights.getOrElse(i) { 1.0 }
    }
    return sum
}
