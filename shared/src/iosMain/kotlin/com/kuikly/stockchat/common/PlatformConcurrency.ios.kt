package com.kuikly.stockchat.common

import platform.Foundation.NSRecursiveLock

/**
 * iOS：Kotlin/Native 没有 JVM 的 `synchronized`，用 Foundation 的递归锁实现同等互斥。
 * 预取缓存的回调来自平台 HTTP / NetworkModule 线程，锁不能省。
 */
private val platformConcurrencyLock = NSRecursiveLock()

internal actual fun <K, V> newAccessOrderMap(): MutableMap<K, V> = LinkedHashMap<K, V>()

internal actual fun <T> platformSynchronized(lock: Any, block: () -> T): T {
    platformConcurrencyLock.lock()
    return try {
        block()
    } finally {
        platformConcurrencyLock.unlock()
    }
}
