package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.data.provider.Quote
import kotlin.math.abs

data class CompareDelta(
    val priceDifference: Double,
    val changePercentDifference: Double,
    val normalizedGap: Double,
)

object CompareCalculator {
    fun delta(left: Quote, right: Quote): CompareDelta {
        val priceDifference = left.price - right.price
        val changePercentDifference = left.changePercent - right.changePercent
        val denominator = maxOf(abs(left.changePercent), abs(right.changePercent), 0.01)
        return CompareDelta(priceDifference, changePercentDifference, (abs(changePercentDifference) / denominator).coerceIn(0.0, 1.0))
    }
}
