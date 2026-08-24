package com.kuikly.stockchat.common

import kotlin.math.abs
import kotlin.math.roundToLong

object Format {
    fun price(value: Double): String = decimal(value, 2)

    fun percent(value: Double): String = "${if (value > 0) "+" else ""}${decimal(value, 2)}%"

    fun signed(value: Double): String = "${if (value > 0) "+" else ""}${decimal(value, 2)}"

    fun compactAmount(value: Double): String = when {
        abs(value) >= 100_000_000 -> "${decimal(value / 100_000_000, 2)}亿"
        abs(value) >= 10_000 -> "${decimal(value / 10_000, 1)}万"
        else -> decimal(value, 0)
    }

    fun decimal(value: Double, digits: Int): String {
        if (!value.isFinite()) return "--"
        val factor = pow10(digits)
        val rounded = (value * factor).roundToLong()
        if (digits == 0) return rounded.toString()
        val sign = if (rounded < 0) "-" else ""
        val absolute = abs(rounded)
        val integer = absolute / factor
        val fraction = (absolute % factor).toString().padStart(digits, '0')
        return "$sign$integer.$fraction"
    }

    private fun pow10(digits: Int): Long {
        var result = 1L
        repeat(digits.coerceAtLeast(0)) { result *= 10L }
        return result
    }
}
