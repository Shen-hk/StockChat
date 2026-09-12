package com.kuikly.stockchat.app.platform

import com.kuikly.stockchat.data.provider.PlatformScheduler
import com.tencent.kuikly.core.timer.setTimeout

/**
 * [PlatformScheduler] 的 Kuikly 适配器：绑定 pagerId，投递到该 pager 的 context queue。
 *
 * 与迁移前 Provider 内 `PagerScope.setTimeout(delay) { }` 是同一个 Kuikly 顶层 API
 * （`setTimeout(pagerId, timeout, callback)`），因此队列与时序逐字节等价 ——
 * iOS 的 assertContextQueue 约束、Android 的主线程回写前提都不变。
 */
class KuiklyPlatformScheduler(private val pagerId: String) : PlatformScheduler {
    override fun schedule(delayMillis: Long, block: () -> Unit) {
        setTimeout(pagerId, delayMillis.toInt(), block)
    }
}
