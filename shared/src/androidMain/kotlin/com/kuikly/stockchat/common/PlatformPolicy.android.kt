package com.kuikly.stockchat.common

/**
 * Android：三项全部关闭 —— **保持本轮改动前的行为**。
 *
 * 本轮修复全部为 iOS 侧问题驱动，Android 未做回归，因此不随 iOS 一起变更：
 * 行情解析仍用既有列位/量纲口径，数据层回调仍按原路径直接回调
 * （Android 渲染层没有 assertContextQueue）。
 */
internal actual object PlatformProfile {
    actual val marketFixes: Boolean = false
    actual val coreThreadMarshalling: Boolean = false
    actual val debugHooks: Boolean = false

    /** `core-render-android@2.25.0` 的 `KRCanvasView` 实现了 `batchDraw`，保持批处理。 */
    actual val canvasBatchDrawSupported: Boolean = true
}
