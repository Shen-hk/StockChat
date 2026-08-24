package com.kuikly.stockchat

import com.kuikly.stockchat.data.config.AiConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AiConfigTest {
    @Test
    fun trimsRuntimeConfiguration() {
        val config = AiConfig(" https://example.com/v1/chat/completions/ ", " deepseek-chat ", " key ").normalized()
        assertEquals("https://example.com/v1/chat/completions", config.endpoint)
        assertEquals("deepseek-chat", config.model)
        assertEquals("key", config.apiKey)
    }

    @Test
    fun validatesRequiredFieldsAndEndpoint() {
        assertNotNull(AiConfig(apiKey = "").validationError())
        assertNotNull(AiConfig(endpoint = "api.example.com", apiKey = "key").validationError())
        assertNull(AiConfig(apiKey = "key").validationError())
    }
}
