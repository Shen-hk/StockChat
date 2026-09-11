package com.kuikly.stockchat.common

/**
 * 鸿蒙（ohosArm64）：同 Android，保持本轮改动前的行为。
 *
 * 鸿蒙还在接入阶段，尚未做过真实回归；这里显式关闭，保证后续接入时
 * 行为可预期。完成回归后改成 `true` 即可与 iOS 对齐。
 */
internal actual object PlatformProfile {
    actual val marketFixes: Boolean = false
    actual val coreThreadMarshalling: Boolean = false
    actual val debugHooks: Boolean = false
}
