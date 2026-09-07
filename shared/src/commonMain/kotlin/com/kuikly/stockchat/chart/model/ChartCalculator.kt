package com.kuikly.stockchat.chart.model

import com.kuikly.stockchat.data.provider.KLinePoint
import com.kuikly.stockchat.data.provider.QuotePoint

data class ChartPoint(val x: Float, val y: Float)

data class ChartGeometry(
    val points: List<ChartPoint>,
    val minValue: Double,
    val maxValue: Double,
    val baselineY: Float,
)

object TimeLineCalculator {
    fun calculate(
        quotes: List<QuotePoint>,
        width: Float,
        height: Float,
        baseline: Double,
    ): ChartGeometry {
        if (quotes.isEmpty() || width <= 0f || height <= 0f) {
            return ChartGeometry(emptyList(), baseline, baseline, height / 2f)
        }
        val rawMin = minOf(quotes.minOf { it.price }, baseline)
        val rawMax = maxOf(quotes.maxOf { it.price }, baseline)
        val padding = ((rawMax - rawMin) * 0.12).coerceAtLeast(baseline * 0.002)
        val min = rawMin - padding
        val max = rawMax + padding
        val range = (max - min).coerceAtLeast(0.0001)
        val xStep = if (quotes.size == 1) 0f else width / (quotes.size - 1)
        val points = quotes.mapIndexed { index, quote ->
            ChartPoint(index * xStep, ((max - quote.price) / range * height).toFloat())
        }
        return ChartGeometry(points, min, max, ((max - baseline) / range * height).toFloat())
    }

    /**
     * 详情页自绘分时的对称几何：昨收恒居视觉中线，涨跌半径对称（doc 26 §4.2）。
     * [slots] 为全天固定槽位数（A 股 241）；盘中未走完的槽位由图表层留空（生长态）。
     */
    fun calculateSymmetric(
        quotes: List<QuotePoint>,
        width: Float,
        height: Float,
        baseline: Double,
        padRate: Double = 1.08,
        slots: Int = 241,
    ): SymmetricGeometry {
        if (quotes.isEmpty() || width <= 0f || height <= 0f || slots < 2) {
            return SymmetricGeometry(emptyList(), baseline, baseline, height / 2f, height)
        }
        val deviations = quotes.map { kotlin.math.abs(it.price - baseline) }
        val avg = averagePrices(quotes, baseline).last()
        val deviation = maxOf(deviations.max(), kotlin.math.abs(avg - baseline)) * padRate
        val radius = deviation.coerceAtLeast(baseline * 0.001).coerceAtLeast(1e-6)
        val upper = baseline + radius
        val lower = baseline - radius
        val slotStep = width / (slots - 1)
        val points = quotes.mapIndexed { index, quote ->
            val slot = index.coerceAtMost(slots - 1)
            ChartPoint(slot * slotStep, yFor(quote.price, upper, lower, height))
        }
        return SymmetricGeometry(points, lower, upper, yFor(baseline, upper, lower, height), height)
    }

    private fun yFor(price: Double, upper: Double, lower: Double, height: Float): Float =
        ((upper - price) / (upper - lower).coerceAtLeast(1e-9) * height).toFloat()

    /**
     * 均价序列（逐点累计，doc 26 §4.6）：
     * ① 真实口径 `cumAmount / (cumVolume × 100)`（amount 逐分钟、volume 手）；
     * ② amount 全 0（旧缓存/Mock）→ 近似 `Σ(price×volume×100) / (cumVolume×100)`；
     * ③ 连量都没有 → 退化为开盘至今的价格均值。
     */
    fun averagePrices(quotes: List<QuotePoint>, baseline: Double): List<Double> {
        if (quotes.isEmpty()) return emptyList()
        val hasRealAmount = quotes.any { it.amount > 0.0 }
        var cumVolume = 0.0
        var cumAmount = 0.0
        var priceSum = 0.0
        var count = 0
        return quotes.map { point ->
            count++
            priceSum += point.price
            cumVolume += point.volume
            cumAmount += if (hasRealAmount) point.amount else point.price * point.volume * 100.0
            val shares = cumVolume * 100.0
            when {
                hasRealAmount && shares > 0.0 -> cumAmount / shares
                !hasRealAmount && point.volume > 0.0 && cumVolume > 0.0 -> cumAmount / (cumVolume * 100.0)
                else -> priceSum / count
            }
        }
    }

    /**
     * 量能红绿判定（doc 26 §4.3-6）：该分钟相对前一分钟涨=true、跌=false、平=null；
     * 首根对今开比较。
     */
    fun volumeRisingFlags(quotes: List<QuotePoint>, openPrice: Double): List<Boolean?> {
        if (quotes.isEmpty()) return emptyList()
        var previous = if (openPrice > 0.0) openPrice else quotes.first().price
        return quotes.map { point ->
            val flag = when {
                point.price > previous -> true
                point.price < previous -> false
                else -> null
            }
            previous = point.price
            flag
        }
    }
}

/** 对称分时几何。[lower]/[upper] 为昨收 ± 半径；[height] 保留用于 [yFor] 换算任意价格。 */
data class SymmetricGeometry(
    val points: List<ChartPoint>,
    val lower: Double,
    val upper: Double,
    val baselineY: Float,
    val height: Float,
) {
    fun yFor(price: Double): Float =
        ((upper - price) / (upper - lower).coerceAtLeast(1e-9) * height).toFloat()
}

object KLineCalculator {
    fun movingAverage(lines: List<KLinePoint>, period: Int): List<Double?> {
        if (period <= 0) return List(lines.size) { null }
        return lines.indices.map { index ->
            if (index + 1 < period) null
            else lines.subList(index + 1 - period, index + 1).map { it.close }.average()
        }
    }

    fun aggregate(lines: List<KLinePoint>, grouping: Int): List<KLinePoint> {
        if (grouping <= 1) return lines
        return lines.chunked(grouping).map { group ->
            KLinePoint(
                date = "${group.first().date}~${group.last().date}",
                open = group.first().open,
                close = group.last().close,
                high = group.maxOf { it.high },
                low = group.minOf { it.low },
                volume = group.sumOf { it.volume },
            )
        }
    }
}
