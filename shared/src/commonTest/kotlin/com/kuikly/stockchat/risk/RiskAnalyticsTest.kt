package com.kuikly.stockchat.risk

import com.kuikly.stockchat.data.provider.KLinePoint
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.risk.domain.RiskRow
import com.kuikly.stockchat.risk.domain.bestCorrelationPair
import com.kuikly.stockchat.risk.domain.buildRiskAiFacts
import com.kuikly.stockchat.risk.domain.buildSkyAiPromptText
import com.kuikly.stockchat.risk.domain.chainConcentration
import com.kuikly.stockchat.risk.domain.computeCorrelations
import com.kuikly.stockchat.risk.domain.dailyReturns
import com.kuikly.stockchat.risk.domain.headline
import com.kuikly.stockchat.risk.domain.industryStats
import com.kuikly.stockchat.risk.domain.pearson
import com.kuikly.stockchat.risk.domain.portfolioStd
import com.kuikly.stockchat.risk.domain.riskAiLocalSummary
import com.kuikly.stockchat.risk.domain.sanitizeRiskAiText
import com.kuikly.stockchat.risk.domain.underperformGapPct
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 风险纯计算域单测（doc 47 B-3 完成定义：风险计算可用纯单测覆盖，无 Kuikly import）。
 * 口径断言锁定：单链判定阈值、跑输 -0.3pct、AI 触点阈值。
 */
class RiskAnalyticsTest {

    // ── 输入构造 ──

    /** 30+ 根日K，收益序列 = (close/prev - 1)，等差上行。 */
    private fun quote(
        symbol: String,
        name: String = symbol,
        dailyReturn: Double = 0.01,
        days: Int = 40,
        marketCap: Double = 0.0,
        changePercent: Double = 0.0,
    ): Quote {
        val base = 10.0
        val kLines = (0 until days).map { i ->
            KLinePoint(
                date = "d${i.toString().padStart(2, '0')}",
                open = base * (1.0 + dailyReturn * i),
                close = base * (1.0 + dailyReturn * (i + 1)),
                high = base * (1.0 + dailyReturn * (i + 1)),
                low = base * (1.0 + dailyReturn * i),
                volume = 100.0,
            )
        }
        return Quote.placeholder(symbol, name).copy(
            previousClose = 10.0,
            price = 10.0 * (1.0 + changePercent / 100.0),
            marketCap = marketCap,
            kLines = kLines,
        )
    }

    private fun rows(vararg specs: Triple<String, String, Quote>): List<RiskRow> =
        specs.map { RiskRow(it.first, it.second, it.third) }

    // ── 相关系数 ──

    @Test
    fun pearsonPerfectlyCorrelatedSeriesIsOne() {
        val a = dailyReturns(quote("a", dailyReturn = 0.01))
        val b = dailyReturns(quote("b", dailyReturn = 0.01))
        assertNotNull(a)
        assertNotNull(b)
        val r = pearson(a, b)
        assertNotNull(r)
        assertTrue(abs(r - 1.0) < 1e-9, "同序列相关系数恒为 1，实际 $r")
    }

    @Test
    fun pearsonInsufficientCommonDatesReturnsNull() {
        val a = dailyReturns(quote("a", days = 10))
        val b = dailyReturns(quote("b", days = 40))
        assertNotNull(a)
        assertNotNull(b)
        // 公共日期 < MIN_RETURN_DAYS(30) → null
        assertNull(pearson(a, b))
    }

    @Test
    fun dailyReturnsRequiresAtLeastTwoBars() {
        assertNull(dailyReturns(Quote.placeholder("x")))
        assertNull(dailyReturns(quote("x", days = 1)))
        assertNotNull(dailyReturns(quote("x", days = 2)))
    }

    // ── 行业分组与单链集中判定 ──

    @Test
    fun industryStatsGroupsUnknownAsUnclassified() {
        val rows = rows(
            Triple("a", "A", quote("a")),
            Triple("b", "B", quote("b")),
            Triple("c", "C", quote("c")),
        )
        val stats = industryStats(rows, mapOf("a" to "白酒", "b" to "  "))
        // 原始口径（RiskMapPage industryStats）：按只数降序，「未分类」不置底
        assertEquals(2, stats.size)
        assertEquals("未分类", stats[0].name)
        assertEquals(2, stats[0].count, "空白行业名与缺失一样归未分类")
        assertEquals("白酒", stats[1].name)
        assertEquals(1, stats[1].count)
    }

    @Test
    fun chainConcentrationTriggersWhenTopWeightOver50Percent() {
        val rows = rows(
            Triple("a", "A", quote("a")),
            Triple("b", "B", quote("b")),
            Triple("c", "C", quote("c")),
        )
        // a、b 同行业 → 2/3 > 50% → 触发
        val industry = industryStats(rows, mapOf("a" to "白酒", "b" to "白酒", "c" to "电子"))
        val chain = chainConcentration(rows, industry)
        assertTrue(chain.triggered)
        assertEquals("白酒", chain.topName)
        assertEquals(2, chain.topCount)
        // CR3 = 3/3 = 100%
        assertEquals(3, chain.cr3Count)
    }

    @Test
    fun chainConcentrationNotTriggeredWhenDispersed() {
        // 5 只全不同行业：top = 1/5 = 20%，CR3 = 3/5 = 60% → 双阈值都不触发。
        // （4 只全分散时 CR3 = 75% > 70% 会触发——这是原始口径，见 RiskMapPage。）
        val rows = rows(
            Triple("a", "A", quote("a")),
            Triple("b", "B", quote("b")),
            Triple("c", "C", quote("c")),
            Triple("d", "D", quote("d")),
            Triple("e", "E", quote("e")),
        )
        val industry = industryStats(
            rows,
            mapOf("a" to "白酒", "b" to "电子", "c" to "医药", "d" to "银行", "e" to "汽车"),
        )
        val chain = chainConcentration(rows, industry)
        assertEquals(false, chain.triggered)
    }

    // ── 组合波动与跑输归因 ──

    @Test
    fun portfolioStdNeedsAtLeastTwoMembers() {
        val single = rows(Triple("a", "A", quote("a")))
        assertNull(portfolioStd(single))
        val pair = rows(
            Triple("a", "A", quote("a")),
            Triple("b", "B", quote("b")),
        )
        assertNotNull(portfolioStd(pair))
    }

    @Test
    fun underperformGapOnlyTriggersBeyondThreshold() {
        val members = rows(
            Triple("a", "A", quote("a", changePercent = -2.0)),
            Triple("b", "B", quote("b", changePercent = -2.0)),
        )
        val index = quote("idx", changePercent = -1.0) // 组合 -2% vs 大盘 -1% → gap = -1.0
        val gap = underperformGapPct(members, index)
        assertNotNull(gap)
        assertEquals(-1.0, gap, 1e-9)

        // 差值 -0.1pct（阈值内）→ null
        val mild = rows(
            Triple("a", "A", quote("a", changePercent = -1.1)),
            Triple("b", "B", quote("b", changePercent = -1.1)),
        )
        assertNull(underperformGapPct(mild, index))
        // 没有行情 → null
        assertNull(underperformGapPct(members, null))
    }

    // ── L1 结论（纯规则） ──

    @Test
    fun headlinePrefersChainFindingWhenTriggered() {
        val rows = rows(
            Triple("a", "A", quote("a")),
            Triple("b", "B", quote("b")),
            Triple("c", "C", quote("c")),
        )
        val industry = industryStats(rows, mapOf("a" to "白酒", "b" to "白酒", "c" to "电子"))
        val chain = chainConcentration(rows, industry)
        val text = headline(rows, industry, chain, indexQuote = null)
        assertTrue(text.contains("押在同一条链上（白酒）"), "实际：$text")
    }

    @Test
    fun headlineFallsBackToNeutralSummaryWhenCalm() {
        val rows = rows(
            Triple("a", "A", quote("a")),
            Triple("b", "B", quote("b")),
            Triple("c", "C", quote("c")),
            Triple("d", "D", quote("d")),
            Triple("e", "E", quote("e")),
        )
        val industry = industryStats(
            rows,
            mapOf("a" to "白酒", "b" to "电子", "c" to "医药", "d" to "银行", "e" to "汽车"),
        )
        val chain = chainConcentration(rows, industry)
        val text = headline(rows, industry, chain, indexQuote = null)
        assertTrue(text.contains("单链集中不明显"), "实际：$text")
    }

    // ── AI 事实槽位与 prompt ──

    @Test
    fun riskAiFactsCoverAllSlotsAndPromptLocksHardRequirements() {
        val rows = rows(
            Triple("a", "A股一", quote("a", changePercent = 1.0)),
            Triple("b", "A股二", quote("b", changePercent = -0.5)),
            Triple("c", "A股三", quote("c", changePercent = 2.0)),
        )
        val facts = buildRiskAiFacts(
            rows = rows,
            industries = mapOf("a" to "白酒", "b" to "白酒", "c" to "电子"),
            correlations = computeCorrelations(rows),
            indexQuote = null,
            events = emptyList(),
            limitUps = emptyList(),
        )
        assertEquals(3, facts.total)
        assertNotNull(facts.industrySummary)
        val prompt = buildSkyAiPromptText(facts)
        assertTrue(prompt.contains("你是 A 股组合风险解读助手"))
        assertTrue(prompt.contains("不给出买卖、仓位建议"))
        assertTrue(prompt.contains("- 自选共 3 只（等权视角，非真实仓位）"))
        assertTrue(prompt.contains("- 最挤的链：白酒 2 只"))
    }

    @Test
    fun localSummaryFallsBackWhenNoFacts() {
        val rows = rows(Triple("a", "A", Quote.placeholder("a")))
        val facts = buildRiskAiFacts(rows, emptyMap(), emptyMap(), null, emptyList(), emptyList())
        val text = riskAiLocalSummary(facts, firstEvent = null)
        assertEquals("端侧速览：行情数据还在路上，稍等片刻再生成。", text)
    }

    @Test
    fun sanitizeStripsMarkdownFences() {
        assertEquals("正文", sanitizeRiskAiText("```kotlin\n正文\n```"))
        assertEquals("第一行\n第二行", sanitizeRiskAiText("第一行\n第二行"))
    }

    // ── 相关性 pair ──

    @Test
    fun bestCorrelationPairPicksLargestAbsoluteValue() {
        val a = rows(
            Triple("a", "甲", quote("a", dailyReturn = 0.01)),
            Triple("b", "乙", quote("b", dailyReturn = 0.01)),
            Triple("c", "丙", quote("c", dailyReturn = -0.005, days = 40)),
        )
        val cors = computeCorrelations(a)
        val best = bestCorrelationPair(a, cors)
        assertNotNull(best)
        assertEquals("甲" to "乙", best.first)
        assertEquals(1.0, best.second, 1e-9)
    }
}
