package com.kuikly.stockchat.common

/** H5：同 Android，保持本轮改动前的行为（本轮未做 H5 回归）。 */
internal actual object PlatformProfile {
    actual val marketFixes: Boolean = false
    actual val coreThreadMarshalling: Boolean = false
    actual val debugHooks: Boolean = false

    /**
     * H5 Web 渲染层只实现了 beginPath/moveTo/fill 等逐条 bridge handler，**没有 batchDraw handler**。
     * 设 `true` 会让所有画命令被攒到 JSONArray，最终一次 `batchDraw('m',JsonArray)` 走 bridge 直接被静默丢弃 → Canvas 图标空白。
     * 设 `false` 让每条 fill() 走逐条 bridge，正常绘制（每图标多走一次跨边界桥，性能影响可忽略）。
     */
    actual val canvasBatchDrawSupported: Boolean = false
}
