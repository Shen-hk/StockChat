package com.kuikly.stockchat.common

/**
 * iOS：三类修复全部启用。
 *
 * 本轮的逐页验证就在 iOS 上做（iPhone 13 Pro 模拟器 + 无头冒烟），
 * 线程回跳由 `setTimeout(0)` 承担，跨线程调用由
 * `iosApp/scripts/apply_kuikly_patches.rb` 的 crossThreadToNative 补丁 marshal 回
 * context queue（补丁随 Podfile 的 post_install 自动重放）。
 */
internal actual object PlatformProfile {
    actual val marketFixes: Boolean = true
    actual val coreThreadMarshalling: Boolean = true
    actual val debugHooks: Boolean = true

    /** 保持本轮既有行为（iOS 本轮已逐页验证，未发现画布空白）。 */
    actual val canvasBatchDrawSupported: Boolean = true
}
