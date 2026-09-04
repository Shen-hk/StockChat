package com.kuikly.stockchat.data.storage

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.SharedPreferencesModule

/** Minimal persistence port used by common business stores. */
interface KeyValueStorage {
    fun getString(key: String): String
    fun setString(key: String, value: String)
}

/** Kuikly adapter kept at the infrastructure boundary. */
class PagerKeyValueStorage(override val pagerId: String) : KeyValueStorage, PagerScope {
    private val preferences: SharedPreferencesModule
        get() = getPager().acquireModule(SharedPreferencesModule.MODULE_NAME)

    override fun getString(key: String): String = preferences.getString(key)

    override fun setString(key: String, value: String) {
        preferences.setString(key, value)
    }
}

/** Deterministic storage for tests and non-platform consumers. */
class InMemoryKeyValueStorage(
    initialValues: Map<String, String> = emptyMap(),
) : KeyValueStorage {
    private val values = initialValues.toMutableMap()

    override fun getString(key: String): String = values[key].orEmpty()

    override fun setString(key: String, value: String) {
        values[key] = value
    }

    fun snapshot(): Map<String, String> = values.toMap()
}
