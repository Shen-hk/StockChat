package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

data class AlertRule(
    val symbol: String,
    val name: String,
    val thresholdPercent: Double = 3.0,
    /** 绝对涨跌额阈值；0 表示不启用金额条件。 */
    val thresholdAmount: Double = 0.0,
    val enabled: Boolean = true,
    val createdAtMillis: Long = 0L,
)

data class AlertTrigger(
    val rule: AlertRule,
    val quote: Quote,
    val title: String,
    val attribution: String,
)

interface AlertProvider {
    fun evaluate(rule: AlertRule, quote: Quote): AlertTrigger?
}

object LocalAlertProvider : AlertProvider {
    override fun evaluate(rule: AlertRule, quote: Quote): AlertTrigger? {
        val percentTriggered = kotlin.math.abs(quote.changePercent) >= rule.thresholdPercent
        val amountTriggered = rule.thresholdAmount > 0.0 && kotlin.math.abs(quote.change) >= rule.thresholdAmount
        if (!rule.enabled || (!percentTriggered && !amountTriggered)) return null
        val direction = if (quote.changePercent >= 0) "上涨" else "下跌"
        val attribution = "${rule.name}${direction}达到 ${com.kuikly.stockchat.common.Format.percent(quote.changePercent)}。先核对市场/板块是否同步，再查看最新公告；当前提示只解释已发生波动，不给出操作建议。"
        return AlertTrigger(rule, quote, "${rule.name}出现异动", attribution)
    }
}

class AlertStore(
    private val storage: KeyValueStorage,
    private val nowMillis: () -> Long = ::platformCurrentTimeMillis,
) {
    fun list(): List<AlertRule> = read()

    fun upsert(symbol: String, name: String, thresholdPercent: Double = 3.0, thresholdAmount: Double = 0.0) {
        val rows = read().filterNot { it.symbol == symbol }.toMutableList()
        rows += AlertRule(symbol, name, thresholdPercent.coerceIn(0.5, 20.0), thresholdAmount.coerceIn(0.0, 10000.0), true, nowMillis())
        write(rows)
    }

    fun remove(symbol: String) = write(read().filterNot { it.symbol == symbol })

    fun toggle(symbol: String) = write(read().map { if (it.symbol == symbol) it.copy(enabled = !it.enabled) else it })

    fun setThresholdAmount(symbol: String, amount: Double) = write(
        read().map { if (it.symbol == symbol) it.copy(thresholdAmount = amount.coerceIn(0.0, 10000.0)) else it },
    )

    private fun read(): List<AlertRule> {
        val raw = storage.getString(KEY)
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val row = array.optJSONObject(index) ?: return@repeat
                    val symbol = row.optString("symbol")
                    if (symbol.isNotEmpty()) add(
                        AlertRule(
                            symbol = symbol,
                            name = row.optString("name").ifEmpty { symbol },
                            thresholdPercent = row.optString("thresholdPercent").toDoubleOrNull() ?: 3.0,
                            thresholdAmount = row.optString("thresholdAmount").toDoubleOrNull() ?: 0.0,
                            enabled = row.optBoolean("enabled"),
                            createdAtMillis = row.optString("createdAtMillis").toLongOrNull() ?: 0L,
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun write(rows: List<AlertRule>) {
        val array = JSONArray()
        rows.forEach { rule ->
            array.put(JSONObject().apply {
                put("symbol", rule.symbol); put("name", rule.name)
                put("thresholdPercent", rule.thresholdPercent); put("enabled", rule.enabled)
                put("thresholdAmount", rule.thresholdAmount)
                put("createdAtMillis", rule.createdAtMillis)
            })
        }
        storage.setString(KEY, array.toString())
    }

    companion object { private const val KEY = "stockchat_alert_rules_v1" }
}
