package com.kuikly.stockchat.common

/**
 * 跨平台并发原语（2026-09-11）。
 *
 * 两处 JVM-only 的 API 曾被迫在 commonMain 里改写：
 *  - `synchronized(lock) { }`
 *  - `LinkedHashMap(capacity, loadFactor, accessOrder = true)`
 *
 * 直接改写会让 **Android 的锁与 LRU 语义被顺带弱化**（本轮修复只针对 iOS）。
 * 这里收成 expect/actual：Android 走原生 JVM 语义，其它平台用各自可用的等价实现，
 * 调用点（[com.kuikly.stockchat.data.provider.QuotePrefetchStore]）保持单一实现。
 */

/**
 * LRU 用的访问序 Map。
 * Android 走 JVM `accessOrder=true`（get/put 自动把键挪到队尾）；
 * 其它平台按插入序，调用方在命中/写入时自行把键移到队尾，LRU 语义等价。
 */
internal expect fun <K, V> newAccessOrderMap(): MutableMap<K, V>

/** 互斥执行 [block]。Android 走 JVM 监视器锁，其它平台用平台原生锁。 */
internal expect fun <T> platformSynchronized(lock: Any, block: () -> T): T
