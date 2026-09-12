package com.kuikly.stockchat.app.platform

import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.SharedPreferencesModule

/**
 * [KeyValueStorage] 的 Kuikly 适配器（Adapter）。
 *
 * 2026-09-12（doc 47 B-1）从 `data/storage/KeyValueStorage.kt` 搬来：端口
 * `KeyValueStorage` 留在 data，适配器属于平台边界，归 `app/platform`
 * —— 这是「data/ 不得出现 PagerScope / SharedPreferencesModule」的核心落点。
 */
class KuiklyKeyValueStorage(override val pagerId: String) : KeyValueStorage, PagerScope {
    private val preferences: SharedPreferencesModule
        get() = getPager().acquireModule(SharedPreferencesModule.MODULE_NAME)

    override fun getString(key: String): String = preferences.getString(key)

    override fun setString(key: String, value: String) {
        preferences.setString(key, value)
    }
}
