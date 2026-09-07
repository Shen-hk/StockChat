package com.kuikly.stockchat

import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.data.provider.QuotePoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 固化 2026-09-07 贵州茅台(600519)盘中实测基线（腾讯分时，09:30-09:33 四根）：
 * `HHMM 价 累计量(手) 累计额(元)`；昨收 1330.05，今开 1324.00。
 */
class DetailTimelineChartTest {
    private val pc = 1330.05
    private val open = 1324.00

    private val quotes = listOf(
        QuotePoint("09:30", 1324.00, 227.0, 30_054_800.00),
        QuotePoint("09:31", 1331.35, 768.0, 101_956_708.27),
        QuotePoint("09:32", 1331.34, 851.0, 113_229_644.23),
        QuotePoint("09:33", 1325.00, 461.0, 61_431_709.68),
    )

    @Test
    fun symmetricGeometryMapsToFixed241Slots() {
        val geometry = TimeLineCalculator.calculateSymmetric(quotes, width = 300f, height = 220f, baseline = pc)
        val slotStep = 300f / 240f
        assertEquals(0f, geometry.points.first().x)
        assertEquals(slotStep, geometry.points[1].x)
        assertEquals(slotStep * 3, geometry.points.last().x)
    }

    @Test
    fun symmetricRadiusKeepsBaselineCentered() {
        val geometry = TimeLineCalculator.calculateSymmetric(quotes, width = 300f, height = 220f, baseline = pc)
        assertEquals(220f / 2f, geometry.baselineY)
        val radius = (maxOf(abs(1331.35 - pc), abs(1324.00 - pc)) * 1.08).coerceAtLeast(pc * 0.001)
        assertEquals(pc + radius, geometry.upper, 1e-9)
        assertEquals(pc - radius, geometry.lower, 1e-9)
        assertTrue(geometry.yFor(pc) - geometry.baselineY < 1e-3f)
    }

    @Test
    fun averagePriceUsesRealAmountThenApproximateWithinHalfPercent() {
        val realAvg = TimeLineCalculator.averagePrices(quotes, pc)
        assertEquals(1324.00, realAvg.first(), 0.01)
        assertEquals(30_054_800.00 / (227.0 * 100.0), realAvg.first(), 0.01)
        val cumulativeAmount = quotes.sumOf { it.amount }
        val cumulativeVolume = quotes.sumOf { it.volume }
        assertEquals(cumulativeAmount / (cumulativeVolume * 100.0), realAvg.last(), 0.5)

        val approximate = TimeLineCalculator.averagePrices(quotes.map { it.copy(amount = 0.0) }, pc)
        val realLast = realAvg.last()
        assertTrue(abs(approximate.last() - realLast) / realLast < 0.005)
    }

    @Test
    fun volumeFlagsCompareFirstMinuteAgainstOpen() {
        val flags = TimeLineCalculator.volumeRisingFlags(quotes, open)
        assertEquals(null, flags.first()) // 09:30 对今开 1324.00：平
        assertEquals(true, flags[1])      // 1324.00 → 1331.35
        assertEquals(false, flags[2])     // 1331.35 → 1331.34
        assertEquals(false, flags[3])
    }

    @Test
    fun highLowAnchorIndicesPointAtExtremes() {
        val highIndex = quotes.indices.maxBy { quotes[it].price }
        val lowIndex = quotes.indices.minBy { quotes[it].price }
        assertEquals(1, highIndex)
        assertEquals(0, lowIndex)
    }

    @Test
    fun zeroAmountQuotesStillYieldAverageViaPriceVolumePath() {
        val noAmount = quotes.map { it.copy(amount = 0.0) }
        val averages = TimeLineCalculator.averagePrices(noAmount, pc)
        val cumVolume = noAmount.sumOf { it.volume }
        val approxAmount = noAmount.sumOf { it.price * it.volume * 100.0 }
        assertEquals(approxAmount / (cumVolume * 100.0), averages.last(), 0.5)
    }

    @Test
    fun emptyAndDegenerateInputsAreSafe() {
        val empty = TimeLineCalculator.calculateSymmetric(emptyList(), 300f, 220f, pc)
        assertTrue(empty.points.isEmpty())
        assertEquals(110f, empty.baselineY)

        val flat = TimeLineCalculator.calculateSymmetric(listOf(QuotePoint("09:30", pc)), 300f, 220f, pc)
        assertEquals(pc + pc * 0.001, flat.upper, 1e-9)
    }
}
