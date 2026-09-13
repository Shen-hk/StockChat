package com.kuikly.stockchat.common

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.initMainHandler
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

/** Initializes the HarmonyOS coroutine main dispatcher with the host N-API loop. */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("stockchat_init_coroutines_main")
fun initOhosCoroutineMain(env: COpaquePointer) {
    initMainHandler(env.reinterpret())
}
