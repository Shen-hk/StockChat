package com.kuikly.stockchat.data.provider

import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.export.VBTransportInitConfig
import com.tencent.kmm.network.service.VBTransportInitHelper
import com.tencent.kmm.network.service.VBTransportService
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createPlatformHttpClient(): PlatformHttpClient {
    OhosNetworkRuntime.ensureInitialized()
    return OhosPlatformHttpClient
}

/** NetworkKMM requires one process-wide initialization before its first curl request. */
private object OhosNetworkRuntime {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        // Do not install NetworkKMM's verbose logger: it prints request headers
        // and bodies, which would expose API keys and prompts in device logs.
        VBTransportInitHelper.init(VBTransportInitConfig())
        initialized = true
    }
}

private object OhosPlatformHttpClient : PlatformHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): PlatformHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val request = VBTransportStringRequest().apply {
                this.url = url
                this.header = ohosHeaders(headers, hasJsonBody = false)
                totalTimeout = 20_000
                logTag = "StockChatGet"
                useCurl = true
            }
            VBTransportService.sendStringRequest(request) { response ->
                if (!continuation.isActive) return@sendStringRequest
                try {
                    continuation.resume(
                        PlatformHttpResponse(
                            status = if (response.errorCode == 0) 200 else 599,
                            body = response.data.ifEmpty { response.errorMessage },
                        ),
                    )
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation { VBTransportService.cancel(request.requestId) }
        }

    override suspend fun postStream(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit,
    ): PlatformHttpResponse {
        OhosNetworkRuntime.ensureInitialized()
        return postBuffered(url, headers, body, onLine)
    }

    /**
     * The NetworkKMM request owns its native request memory until completion.
     * Its callback can return an SSE body in one batch; the common typewriter
     * then reveals the parsed deltas incrementally, without a fragile native
     * callback lifetime crossing into the UI layer.
     */
    private suspend fun postBuffered(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit,
    ): PlatformHttpResponse = suspendCancellableCoroutine { continuation ->
        val request = VBTransportPostRequest().apply {
            this.url = url
            this.header = ohosHeaders(headers, hasJsonBody = true)
            this.data = body
            totalTimeout = 45_000
            logTag = "StockChatSse"
            useCurl = true
        }
        VBTransportService.sendPostRequest(request) { response ->
            if (!continuation.isActive) return@sendPostRequest
            try {
                val responseBody = when (val data = response.data) {
                    is String -> data
                    is ByteArray -> data.decodeToString()
                    else -> ""
                }.ifEmpty { response.errorMessage }
                if (response.errorCode == 0) {
                    responseBody.lineSequence().forEach(onLine)
                }
                continuation.resume(
                    PlatformHttpResponse(
                        status = if (response.errorCode == 0) openAiStatus(responseBody) else 599,
                        body = responseBody,
                    ),
                )
            } catch (error: Throwable) {
                // Never let parsing/UI scheduling exceptions unwind through
                // NetworkKMM's C callback; Kotlin/Native aborts the process.
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation { VBTransportService.cancel(request.requestId) }
    }

    /**
     * The bundled OHOS curl wrapper does not advertise decompression support.
     * Request an identity response so JSON/SSE reaches the common parser as
     * UTF-8 text instead of compressed bytes.
     */
    private fun ohosHeaders(headers: Map<String, String>, hasJsonBody: Boolean): MutableMap<String, String> =
        buildMap {
            putAll(headers)
            put("Accept-Encoding", "identity")
            put("Connection", "close")
            if (hasJsonBody) put("Content-Type", "application/json")
        }.toMutableMap()

    /** NetworkKMM exposes curl status but not the HTTP status line on OHOS. */
    private fun openAiStatus(body: String): Int {
        val error = try {
            JSONObject(body).optJSONObject("error")
        } catch (_: Throwable) {
            null
        } ?: return 200
        val marker = "${error.optString("code")} ${error.optString("type")} ${error.optString("message")}".lowercase()
        return when {
            "auth" in marker || "api_key" in marker || "unauthorized" in marker -> 401
            "rate" in marker || "too many" in marker -> 429
            "quota" in marker || "balance" in marker || "credit" in marker -> 402
            else -> 400
        }
    }
}
