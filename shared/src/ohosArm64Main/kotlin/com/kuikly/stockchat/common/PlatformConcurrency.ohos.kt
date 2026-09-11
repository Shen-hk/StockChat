package com.kuikly.stockchat.common

/**
 * 鸿蒙（ohosArm64）：与 H5 同构，Kuikly 侧的 Kotlin 代码跑在单一 UI 线程上，
 * 无并发访问，保持直调。若后续接入多线程回调，需换成原生锁。
 */
internal actual fun <K, V> newAccessOrderMap(): MutableMap<K, V> = LinkedHashMap<K, V>()

internal actual fun <T> platformSynchronized(lock: Any, block: () -> T): T = block()
