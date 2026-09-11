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

    /**
     * 「未知」统一写 `--`，不要用 0 冒充真实值。
     *
     * 上游接口各市场提供的列不同（例如港股无换手率/市净率，见 `TencentQuoteParser` 的
     * 快照列位表），解析层把缺列记 0.0；展示与 AI 上下文都必须调用本组函数，
     * 否则港股会老老实实显示成「换手 0.00%、PB 0.00」这种看起来像事实的假数据。
     *
     * 2026-09-11：三个函数的「未知」分支只在 [PlatformProfile.marketFixes] 打开时生效
     * （当前仅 iOS）。关闭时逐字符输出改动前的格式，Android / 鸿蒙显示不变。
     */
    fun ratioOrDash(value: Double, digits: Int = 2): String = when {
        !PlatformProfile.marketFixes -> decimal(value, digits) + "%"
        value > 0.0 -> decimal(value, digits) + "%"
        else -> "--"
    }

    /** 估值倍数（PE/PB）。PB ≤ 0 视为未知；PE 允许为负（亏损股负 PE 是有意义的事实）。 */
    fun multipleOrDash(value: Double, digits: Int = 2): String = when {
        !PlatformProfile.marketFixes -> decimal(value, digits)
        value.isFinite() && value != 0.0 -> decimal(value, digits)
        else -> "--"
    }

    /** 金额类；0 视为未知。 */
    fun amountOrDash(value: Double): String =
        if (PlatformProfile.marketFixes && value <= 0.0) "--" else compactAmount(value)

    private fun pow10(digits: Int): Long {
        var result = 1L
        repeat(digits.coerceAtLeast(0)) { result *= 10L }
        return result
    }
}
