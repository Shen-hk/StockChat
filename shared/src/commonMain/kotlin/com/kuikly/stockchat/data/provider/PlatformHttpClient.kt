package com.kuikly.stockchat.data.provider

import io.ktor.client.HttpClient

/** Avoids runtime engine discovery failures in packaged Android and browser builds. */
internal expect fun createPlatformHttpClient(): HttpClient
