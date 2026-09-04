package com.kuikly.stockchat.data.provider

internal actual fun platformPrefersReducedMotion(): Boolean =
    runCatching {
        js("typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches") as Boolean
    }.getOrDefault(false)
