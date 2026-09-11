package com.kuikly.stockchat.common

/** H5：单线程事件循环，无并发访问，保持直调即可。 */
internal actual fun <K, V> newAccessOrderMap(): MutableMap<K, V> = LinkedHashMap<K, V>()

internal actual fun <T> platformSynchronized(lock: Any, block: () -> T): T = block()
