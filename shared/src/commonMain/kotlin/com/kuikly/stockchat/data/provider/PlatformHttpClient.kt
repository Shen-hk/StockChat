package com.kuikly.stockchat.data.provider

/** Small cross-platform HTTP surface. Platform code owns the concrete engine. */
interface PlatformHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): PlatformHttpResponse
    suspend fun postStream(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit,
    ): PlatformHttpResponse
}

data class PlatformHttpResponse(val status: Int, val body: String)

expect fun createPlatformHttpClient(): PlatformHttpClient
