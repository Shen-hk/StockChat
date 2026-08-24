package com.kuikly.stockchat.data.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(Js)
