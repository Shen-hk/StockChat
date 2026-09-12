package com.kuikly.stockchat.data.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line

actual fun createPlatformHttpClient(): PlatformHttpClient = KtorPlatformHttpClient(HttpClient(OkHttp))

internal class KtorPlatformHttpClient(private val client: HttpClient) : PlatformHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): PlatformHttpResponse {
        val response = client.get(url) { headers.forEach { (name, value) -> header(name, value) } }
        return PlatformHttpResponse(response.status.value, response.bodyAsText())
    }

    override suspend fun postStream(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit,
    ): PlatformHttpResponse {
        return client.preparePost(url) {
            contentType(ContentType.Application.Json)
            headers.forEach { (name, value) -> header(name, value) }
            setBody(body)
        }.execute { response ->
            val status = response.status.value
            val channel = response.bodyAsChannel()
            if (status in 200..299) {
                while (true) onLine(channel.readUTF8Line() ?: break)
                PlatformHttpResponse(status, "")
            } else {
                PlatformHttpResponse(status, channel.readUTF8Line().orEmpty())
            }
        }
    }
}
