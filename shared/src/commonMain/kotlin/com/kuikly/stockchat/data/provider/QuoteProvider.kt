package com.kuikly.stockchat.data.provider

enum class DataMode { AUTO, ONLINE, CACHE, OFFLINE }

data class QuotePoint(val time: String, val price: Double, val volume: Double = 0.0)

data class KLinePoint(
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
)

data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val previousClose: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val amount: Double,
    val turnoverRate: Double,
    val peTtm: Double,
    val pb: Double,
    val marketCap: Double,
    val timestamp: String,
    val source: String,
    val timeline: List<QuotePoint> = emptyList(),
    val kLines: List<KLinePoint> = emptyList(),
) {
    val change: Double get() = price - previousClose
    val changePercent: Double get() = if (previousClose == 0.0) 0.0 else change / previousClose * 100.0
    val rising: Boolean get() = change >= 0.0
}

interface QuoteProvider {
    val mode: DataMode
    fun snapshot(symbol: String, onResult: (Quote?) -> Unit)
    fun timeline(symbol: String, onResult: (List<QuotePoint>) -> Unit)
    fun kLines(symbol: String, count: Int = 30, onResult: (List<KLinePoint>) -> Unit)
}
