package com.kuikly.stockchat.data.provider

import android.animation.ValueAnimator

internal actual fun platformPrefersReducedMotion(): Boolean =
    runCatching { !ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
