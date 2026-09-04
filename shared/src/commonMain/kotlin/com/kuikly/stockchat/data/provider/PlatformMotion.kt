package com.kuikly.stockchat.data.provider

/**
 * Whether the user has asked the OS to reduce motion (Android animator
 * duration scale = 0 / iOS Reduce Motion / web prefers-reduced-motion).
 * Motion-heavy flourishes (typewriter loops, blinking cursors, entrance
 * staggers) must fall back to a static presentation when this is true.
 */
internal expect fun platformPrefersReducedMotion(): Boolean
