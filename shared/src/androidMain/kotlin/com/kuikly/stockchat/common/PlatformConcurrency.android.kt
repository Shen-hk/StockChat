package com.kuikly.stockchat.common

/** Android：与改动前完全一致 —— JVM 监视器锁 + accessOrder LinkedHashMap。 */
internal actual fun <K, V> newAccessOrderMap(): MutableMap<K, V> =
    LinkedHashMap<K, V>(0, 0.75f, true)

internal actual fun <T> platformSynchronized(lock: Any, block: () -> T): T =
    synchronized(lock) { block() }
