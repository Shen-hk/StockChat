package com.kuikly.stockchat

import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.ModelPresets
import com.kuikly.stockchat.data.provider.OpenAiCompatRequestProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun everyBuiltInPresetHasARunnableOpenAiCompatibleConfiguration() {
        ModelPresets.all.filter { it.id != "custom" }.forEach { preset ->
            assertTrue(preset.endpoint.startsWith("https://"), "${preset.name} endpoint")
            assertTrue(preset.endpoint.endsWith("/chat/completions"), "${preset.name} endpoint path")
            assertTrue(preset.models.isNotEmpty(), "${preset.name} models")
        }
    }

    @Test
    fun mimoUsesItsDocumentedCompatibilityHeadersAndParameters() {
        val profile = OpenAiCompatRequestProfile.forEndpoint("https://api.xiaomimimo.com/v1/chat/completions")
        assertEquals("key", profile.headers("key")["api-key"])
        assertNull(profile.headers("key")["Authorization"])
        assertTrue(profile.omitTemperature)
        assertTrue(profile.usesMaxCompletionTokens)
        assertTrue(profile.disableThinking)
    }

    @Test
    fun mimoTokenPlanEndpointUsesTheSameCompatibilityProfile() {
        val profile = OpenAiCompatRequestProfile.forEndpoint("https://token-plan-cn.xiaomimimo.com/v1/chat/completions")
        assertEquals("key", profile.headers("key")["api-key"])
        assertTrue(profile.usesMaxCompletionTokens)
        assertTrue(profile.disableThinking)
    }
}
