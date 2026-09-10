package com.kuikly.stockchat.data.provider

/** Small cross-platform HTTP surface. Platform code owns the concrete engine. */
internal interface PlatformHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): PlatformHttpResponse
    suspend fun postStream(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit,
    ): PlatformHttpResponse
}

internal data class PlatformHttpResponse(val status: Int, val body: String)

internal expect fun createPlatformHttpClient(): PlatformHttpClient
