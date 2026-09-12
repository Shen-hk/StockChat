package com.kuikly.stockchat.data.storage

/** Minimal persistence port used by common business stores. */
interface KeyValueStorage {
    fun getString(key: String): String
    fun setString(key: String, value: String)
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
