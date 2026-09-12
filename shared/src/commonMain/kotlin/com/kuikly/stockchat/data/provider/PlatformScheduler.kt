package com.kuikly.stockchat.data.provider

/**
 * 调度端口（Port）：把回调投递回 Kuikly pager 的核心线程 / context queue。
 *
 * 为什么需要它：Provider 里的 `setTimeout(0)` 不是「随便找个定时器」，它的语义是
 * 「跳到 pager 的 context queue 执行」。iOS 下所有 Kotlin→Native 调用必须在该队列
 * 上（后台线程直调会 assertContextQueue SIGABRT，Android 无断言、静默失效）。
 * 因此适配器必须持有 pagerId，而 `data/` 不允许依赖 Pager 作用域 —— 于是
 * 端口留在这里，适配器落在 `app/platform/KuiklyPlatformScheduler.kt`。
 *
 * 行为等价性：适配器用 `setTimeout(pagerId, delay, block)`，与原先 Provider 直接
 * 调用 Pager 作用域的 `setTimeout(delay) { }` 是同一条链路（Kuikly 顶层 API），
 * 队列、时序、去抖特性完全一致。
 */
interface PlatformScheduler {
    fun schedule(delayMillis: Long, block: () -> Unit)
}
