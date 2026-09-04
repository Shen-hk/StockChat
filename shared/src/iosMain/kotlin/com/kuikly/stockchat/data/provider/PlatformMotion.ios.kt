package com.kuikly.stockchat.data.provider

import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

internal actual fun platformPrefersReducedMotion(): Boolean =
    UIAccessibilityIsReduceMotionEnabled()
