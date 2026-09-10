package com.kuikly.stockchat.data.provider

import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.service.VBTransportService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Harmony Native uses KuiklyBase NetworkKMM's libcurl transport. */
internal actual fun createPlatformHttpClient(): PlatformHttpClient = OhosPlatformHttpClient

private object OhosPlatformHttpClient : PlatformHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): PlatformHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val request = VBTransportStringRequest().apply {
                this.url = url
                this.header = headers.toMutableMap()
                totalTimeout = 20_000
                logTag = "StockChatGet"
            }
            VBTransportService.sendStringRequest(request) { response ->
                if (continuation.isActive) {
                    continuation.resume(PlatformHttpResponse(
                        status = if (response.errorCode == 0) 200 else 599,
                        body = response.data,
                    ))
                }
            }
            continuation.invokeOnCancellation { VBTransportService.cancel(request.requestId) }
        }

    override suspend fun postStream(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit,
    ): PlatformHttpResponse = suspendCancellableCoroutine { continuation ->
        val request = VBTransportPostRequest().apply {
            this.url = url
            this.header = (headers + ("Content-Type" to "application/json")).toMutableMap()
            this.data = body
            totalTimeout = 45_000
            logTag = "StockChatSse"
        }
        VBTransportService.sendPostRequest(request) { response ->
            val responseBody = when (val data = response.data) {
                is String -> data
                is ByteArray -> data.decodeToString()
                else -> ""
            }
            if (response.errorCode == 0) responseBody.lineSequence().forEach(onLine)
            if (continuation.isActive) {
                continuation.resume(PlatformHttpResponse(
                    status = if (response.errorCode == 0) 200 else 599,
                    body = responseBody,
                ))
            }
        }
        continuation.invokeOnCancellation { VBTransportService.cancel(request.requestId) }
    }
}
