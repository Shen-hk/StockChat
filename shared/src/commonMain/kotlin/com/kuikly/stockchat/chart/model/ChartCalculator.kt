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
