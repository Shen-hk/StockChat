package com.kuikly.stockchat.common

/** H5：同 Android，保持本轮改动前的行为（本轮未做 H5 回归）。 */
internal actual object PlatformProfile {
    actual val marketFixes: Boolean = false
    actual val coreThreadMarshalling: Boolean = false
    actual val debugHooks: Boolean = false
}
