package com.kuikly.stockchat.common

/**
 * 鸿蒙（ohosArm64）：行为开关同 Android，全部保持本轮改动前的行为。
 *
 * 鸿蒙还在接入阶段，尚未做过真实回归；这里显式关闭，保证后续接入时
 * 行为可预期。完成回归后改成 `true` 即可与 iOS 对齐。
 *
 * 渲染能力位与 Android 不同：`canvasBatchDrawSupported = false`（见下）。
 */
internal actual object PlatformProfile {
    actual val marketFixes: Boolean = false
    actual val coreThreadMarshalling: Boolean = false
    actual val debugHooks: Boolean = false

    /**
     * 置 `false` 是**修复项**，不是「暂缓启用」：锁定的 `@kuikly-open/render@2.25.0`
     * 的 `libkuikly.so` 没有实现 `batchDraw` 命令（`beginPath`/`moveTo`/`arc`/`fill`
     * 等单条命令都在，唯独没有 `batchDraw`）。批处理开启后整帧命令只发一条
     * `batchDraw`，native 侧静默忽略 → 线性图标、语音波形、composer 描边全部空白。
     * 逐条下发是同等的回退路径（native 支持全部单条命令），只是 bridge 调用次数略多。
     */
    actual val canvasBatchDrawSupported: Boolean = false
}
