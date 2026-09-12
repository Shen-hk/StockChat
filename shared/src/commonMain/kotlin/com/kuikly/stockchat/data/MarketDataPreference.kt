package com.kuikly.stockchat.data

import com.kuikly.stockchat.data.storage.KeyValueStorage

/** 用户可选的股票行情来源；真实模式失败时仍会自动降级到 [MOCK]。 */
enum class MarketDataSource(val id: String, val label: String) {
    REAL("real", "真实数据"),
    MOCK("mock", "Mock 数据");

    companion object {
        fun fromId(value: String): MarketDataSource = entries.firstOrNull { it.id == value } ?: REAL
    }
}

/** 行情来源的持久化契约。所有 pager 共用同一份偏好。 */
object MarketDataPrefs {
    const val KEY_SOURCE = "market_data_source"

    fun source(storage: KeyValueStorage): MarketDataSource =
        MarketDataSource.fromId(storage.getString(KEY_SOURCE))

    fun persist(storage: KeyValueStorage, source: MarketDataSource) {
        storage.setString(KEY_SOURCE, source.id)
    }
}
