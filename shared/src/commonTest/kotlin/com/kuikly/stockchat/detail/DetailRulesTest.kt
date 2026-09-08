package com.kuikly.stockchat.detail

import com.kuikly.stockchat.page.detail.AnchorIndex
import com.kuikly.stockchat.page.detail.FactorSpec
import com.kuikly.stockchat.page.detail.Materiality
import com.kuikly.stockchat.page.detail.RelevanceAnchor
import com.kuikly.stockchat.page.detail.detectAnomalies
import com.kuikly.stockchat.page.detail.financialFootnote
import com.kuikly.stockchat.page.detail.fundFlowFootnote
import com.kuikly.stockchat.page.detail.materialityOf
import com.kuikly.stockchat.page.detail.pickPinnedCard
import com.kuikly.stockchat.page.detail.replayContribution
import com.kuikly.stockchat.page.detail.scoreNewsSentiment
import com.kuikly.stockchat.page.detail.shareholderFootnote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DetailRules 纯函数单测（doc 29 F1 / ④ / B1 / E1 / E2）。JVM 可跑，无 Kuikly 依赖。
 */
class DetailRulesTest {

    // ───── AnchorIndex 边界（doc 29 ④/B2 验收）─────
    @Test
    fun anchorBoundaries() {
        assertEquals(0, AnchorIndex.timeStringToIndex("09:30"))
        assertEquals(119, AnchorIndex.timeStringToIndex("11:30"))
        assertEquals(120, AnchorIndex.timeStringToIndex("13:00"))
        assertEquals(239, AnchorIndex.timeStringToIndex("15:00"))
        assertNull(AnchorIndex.timeStringToIndex("12:00"))
    }

    // ───── F1 重要度：每档 ≥2 例 ─────
    @Test
    fun materialityHigh() {
        val a = materialityOf("公司发布半年度业绩预告，净利润大幅增长")
        val b = materialityOf("控股股东拟减持公司股份")
        assertEquals(Materiality.HIGH, a.level)
        assertEquals(Materiality.HIGH, b.level)
        assertTrue(a.rule.contains("业绩"))
        assertTrue(b.rule.contains("减持"))
    }

    @Test
    fun materialityMid() {
        val a = materialityOf("公司公布年度分红方案")
        val b = materialityOf("本次除权除息股权登记日确定")
        assertEquals(Materiality.MID, a.level)
        assertEquals(Materiality.MID, b.level)
    }

    @Test
    fun materialityLow() {
        val a = materialityOf("公司日常生产经营正常，无应披露未披露事项")
        assertEquals(Materiality.LOW, a.level)
        assertTrue(a.rule.contains("未命中"))
    }

    // ───── ④ 异动检测：构造放量急跌 ─────
    @Test
    fun detectVolumeSpikeDrop() {
        // 0..9 平盘 10.0；10..13 急跌至 9.6（5 点窗口 -4%）
        val prices = List(14) { 10.0 }.toMutableList().apply {
            this[10] = 9.95; this[11] = 9.9; this[12] = 9.8; this[13] = 9.6
        }
        // 成交量：基线 100，仅 index 13 放量到 300
        val volumes = List(14) { 100.0 }.toMutableList().apply { this[13] = 300.0 }

        val result = detectAnomalies(prices, volumes)
        assertTrue(result.isNotEmpty(), "应检出至少一处异动")
        val drop = result.firstOrNull { it.index == 13 }
        assertTrue(drop != null, "index 13 应被检出")
        assertTrue(drop!!.isSpike, "放量急跌应 isSpike=true")
        assertTrue(drop.label.startsWith("09:43"), "时间标签应取自 AnchorIndex：${drop.label}")
        assertTrue(drop.label.contains("急跌"), "方向词应为急跌：${drop.label}")
        assertTrue(drop.label.contains("倍"), "放量应带量能倍数：${drop.label}")
        assertTrue(drop.label.contains("4.0%"), "跌幅应约 4.0%：${drop.label}")
    }

    @Test
    fun detectNoVolumeNoConfirm() {
        // 急跌但无放量：当前实现需放量确认（hasVol=true 时 volRatio<1.8 即排除）
        val prices = List(14) { 10.0 }.toMutableList().apply {
            this[10] = 9.95; this[11] = 9.9; this[12] = 9.8; this[13] = 9.6
        }
        val volumes = List(14) { 100.0 } // 全程无放量
        assertTrue(detectAnomalies(prices, volumes).isEmpty())
    }

    @Test
    fun detectNullVolumeUsesPriceOnly() {
        // 不传成交量：仅按价格 ≥0.8% 判定，isSpike 恒 false
        val prices = List(14) { 10.0 }.toMutableList().apply {
            this[13] = 9.6
        }
        val result = detectAnomalies(prices, null)
        assertTrue(result.isNotEmpty())
        assertEquals(false, result.first().isSpike)
        assertTrue(result.first().label.contains("急跌"))
        assertFalse(result.first().label.contains("倍"))
    }

    @Test
    fun detectTooShort() {
        assertTrue(detectAnomalies(listOf(1.0, 2.0), listOf(1.0, 2.0)).isEmpty())
    }

    // ───── B1 新闻情绪 ─────
    @Test
    fun newsSentimentPositive() {
        val s = scoreNewsSentiment("主力资金净买入，机构增持公司股份")
        assertEquals(true, s.isPositive)
        assertTrue(s.rule.contains("净买入"))
    }

    @Test
    fun newsSentimentNegative() {
        val s = scoreNewsSentiment("公司收到立案告知书，业绩预亏下行")
        assertEquals(false, s.isPositive)
        assertTrue(s.rule.contains("立案"))
    }

    @Test
    fun newsSentimentNeutral() {
        val s = scoreNewsSentiment("公司召开临时股东大会审议常规议案")
        assertNull(s.isPositive)
    }

    // ───── E1 置顶仲裁 ─────
    @Test
    fun pickPinnedEmpty() {
        assertNull(pickPinnedCard(emptyList(), listOf("cardA")))
    }

    @Test
    fun pickPinnedNoHit() {
        val anchors = listOf(
            RelevanceAnchor("cardA", pinnedToday = false, reason = "r1"),
            RelevanceAnchor("cardB", pinnedToday = true, reason = "r2")
        )
        // cardB 置顶但不在 priority 中 → 无命中
        assertNull(pickPinnedCard(anchors, listOf("cardA", "cardC")))
    }

    @Test
    fun pickPinnedHit() {
        val anchors = listOf(
            RelevanceAnchor("cardA", pinnedToday = false, reason = "r1"),
            RelevanceAnchor("cardB", pinnedToday = true, reason = "r2")
        )
        assertEquals("cardB", pickPinnedCard(anchors, listOf("cardA", "cardB")))
    }

    // ───── E2 注脚阈值 ─────
    @Test
    fun fundFlowFootnoteThreshold() {
        // 不足 3 日 → null
        assertNull(fundFlowFootnote(listOf(-1.0, -2.0)))
        // 连续 3 日净流出 → 命中
        val f = fundFlowFootnote(listOf(1.0, 2.0, -1.0, -2.0, -3.0))
        assertTrue(f != null)
        assertTrue(f!!.text.contains("连续 3 日净流出"))
        assertTrue(f.text.contains("近 20 日首次"))
        // 仅 2 日同号 → null
        assertNull(fundFlowFootnote(listOf(1.0, -1.0, -2.0)))
        // 转正则净流入注脚
        val g = fundFlowFootnote(listOf(-1.0, 2.0, 3.0, 4.0))
        assertTrue(g != null && g.text.contains("净流入"))
    }

    @Test
    fun shareholderFootnoteConcentrate() {
        // 户数降 + 户均升 → 筹码集中
        val ok = shareholderFootnote(holderCountChangePct = -5.0, perHolderChangePct = 3.0)
        assertTrue(ok != null && ok.text.contains("筹码集中"))
        // 户数升 → null
        assertNull(shareholderFootnote(holderCountChangePct = 5.0, perHolderChangePct = 3.0))
        // 户均降 → null
        assertNull(shareholderFootnote(holderCountChangePct = -5.0, perHolderChangePct = -3.0))
    }

    @Test
    fun financialFootnotePercentile() {
        val f = financialFootnote(revenueGrowthPct = 12.0, industryPercentile = 80)
        assertTrue(f != null && f.text.contains("高于行业 80"))
        // 负增长 → null
        assertNull(financialFootnote(revenueGrowthPct = -2.0, industryPercentile = 80))
    }

    // ───── G1 因子权重重放（Σ base×weight，doc §4.12「重算与手算一致」验收）─────
    @Test
    fun replayContributionSumsWeights() {
        val factors = listOf(
            FactorSpec("资金面", -0.30),
            FactorSpec("板块联动", -0.14),
            FactorSpec("市场整体", 0.05),
            FactorSpec("个股事件", -0.23),
        )
        // 复原态（全 1.0×）＝ Σ base = -0.62
        assertEquals(-0.62, replayContribution(factors, listOf(1.0, 1.0, 1.0, 1.0)), 1e-9)
        // 手算：-0.30×2 + -0.14 + 0.05 + -0.23×0.5 = -0.805
        assertEquals(-0.805, replayContribution(factors, listOf(2.0, 1.0, 1.0, 0.5)), 1e-9)
        // 全 0× → 不贡献
        assertEquals(0.0, replayContribution(factors, listOf(0.0, 0.0, 0.0, 0.0)), 1e-9)
        // 权重缺失位按 1.0 兜底（复原语义）
        assertEquals(-0.92, replayContribution(factors, listOf(2.0)), 1e-9)
    }
}
