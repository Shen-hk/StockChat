package com.kuikly.stockchat.app.platform

import com.kuikly.stockchat.data.storage.KeyValueStorage
import kotlinx.browser.window

internal actual fun platformKeyValueStorage(pagerId: String): KeyValueStorage = BrowserKeyValueStorage

private object BrowserKeyValueStorage : KeyValueStorage {
    private const val PREFIX = "stockchat:"

    override fun getString(key: String): String = try {
        window.localStorage.getItem(PREFIX + key)
            // H5 首次打开没有原生行情回调可等待，默认走页面明确标注的 Mock 数据；
            // 设置页选择「真实数据」后会覆盖这份默认值。
            ?: if (key == "market_data_source") "mock" else ""
    } catch (_: Throwable) {
        ""
    }

    override fun setString(key: String, value: String) {
        try {
            window.localStorage.setItem(PREFIX + key, value)
        } catch (_: Throwable) {
            // Private-mode or quota errors leave the current session usable without persistence.
        }
    }
}
