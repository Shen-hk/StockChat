package com.kuikly.stockchat.testing

import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.manager.BridgeManager
import com.tencent.kuikly.core.manager.PagerManager
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager

/**
 * 单测用的 Kuikly 响应式上下文：Kuikly 的全局 `observable` / `observableList`
 * 委托在读写时要求 `PagerManager.getCurrentReactiveObserver()` 存在（否则抛
 * ReactiveObserverNotFoundException），而 observer 只在 `createPager` 时注册。
 *
 * 本工具注册一个最小假 Pager（onCreate/onDestroy 全 no-op，不建 shadow 树、
 * 不触 native），把 `BridgeManager.currentPageId` 指向它，使测试体里的
 * observable 读写走通（无依赖收集时 notifyGetValue/notifyPropertyObserver
 * 均为安全 no-op）。用完即销毁，不留全局状态。
 *
 * 用法：`reactive { val c = Coordinator(...); c.reload(); assertEquals(...) }`
 */
fun <T> reactive(block: () -> T): T {
    val pagerId = "test-pager-${System.nanoTime()}"
    PagerManager.registerPageRouter("kuikly_reactive_test") { ReactiveContextPager() }
    PagerManager.createPager(pagerId, "kuikly_reactive_test", "{}")
    BridgeManager.currentPageId = pagerId
    try {
        return block()
    } finally {
        BridgeManager.currentPageId = ""
        PagerManager.destroyPager(pagerId)
    }
}

/** 空壳 Pager：只为了在 PagerManager 里挂一个 ReactiveObserver。 */
private class ReactiveContextPager : Pager() {
    override fun body(): ViewBuilder = {}
    override fun onCreatePager(pagerId: String, pageData: JSONObject) = Unit
    override fun onDestroyPager() = Unit
}
