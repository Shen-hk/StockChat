package com.kuikly.stockchat.chat.welcome.data

import com.kuikly.stockchat.data.storage.KeyValueStorage

/**
 * Persistence for the welcome-card rotation. This deliberately knows neither
 * Pager nor the welcome UI, so it can be reused and tested off-platform.
 */
internal class WelcomeStarterStore(
    private val storage: KeyValueStorage,
    private val allKinds: Set<String>,
) {
    fun usedKinds(): Set<String> = storage.getString(USED_KIND_KEY)
        .split('|')
        .filter { it.isNotBlank() }
        .toSet()

    fun markUsed(kind: String) {
        if (kind !in allKinds) return
        val next = usedKinds().let { current ->
            if (current.containsAll(allKinds)) mutableSetOf() else current.toMutableSet()
        }
        next += kind
        storage.setString(USED_KIND_KEY, next.joinToString("|"))
    }

    private companion object {
        const val USED_KIND_KEY = "stockchat_welcome_used_starter_kinds_v1"
    }
}
