package com.kuikly.stockchat.data.config

import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

data class AiConfig(
    val endpoint: String = DEFAULT_ENDPOINT,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
) {
    fun normalized(): AiConfig = copy(
        endpoint = endpoint.trim().trimEnd('/'),
        model = model.trim(),
        apiKey = apiKey.trim(),
    )

    fun validationError(): String? {
        val value = normalized()
        return when {
            value.apiKey.isEmpty() -> "请填写 API Key"
            value.endpoint.isEmpty() -> "请填写 API 地址"
            !value.endpoint.startsWith("https://") && !value.endpoint.startsWith("http://") ->
                "API 地址需要以 https:// 或 http:// 开头"
            value.model.isEmpty() -> "请填写模型名称"
            else -> null
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}

class AiConfigStore(
    private val preferences: KeyValueStorage,
) {
    fun load(): AiConfig {
        val raw = preferences.getString(STORAGE_KEY)
        if (raw.isEmpty()) return AiConfig()
        return try {
            val json = JSONObject(raw)
            AiConfig(
                endpoint = json.optString("endpoint").ifEmpty { AiConfig.DEFAULT_ENDPOINT },
                model = json.optString("model").ifEmpty { AiConfig.DEFAULT_MODEL },
                apiKey = json.optString("apiKey"),
            ).normalized()
        } catch (_: Throwable) {
            AiConfig()
        }
    }

    fun save(config: AiConfig) {
        val value = config.normalized()
        preferences.setString(
            STORAGE_KEY,
            JSONObject().apply {
                put("endpoint", value.endpoint)
                put("model", value.model)
                put("apiKey", value.apiKey)
            }.toString(),
        )
    }

    fun clear() {
        preferences.setString(STORAGE_KEY, "")
    }

    /**
     * 按厂商预设分槽位保存完整配置（endpoint/model/apiKey），
     * 切换厂商时各自恢复自己的配置，互不覆盖。
     */
    fun loadPresetConfig(presetId: String): AiConfig? {
        val raw = preferences.getString(SLOT_STORAGE_KEY)
        if (raw.isEmpty()) return null
        return try {
            val slot = JSONObject(raw).optJSONObject(presetId) ?: return null
            AiConfig(
                endpoint = slot.optString("endpoint"),
                model = slot.optString("model"),
                apiKey = slot.optString("apiKey"),
            ).normalized()
        } catch (_: Throwable) {
            null
        }
    }

    fun savePresetConfig(presetId: String, config: AiConfig) {
        val raw = preferences.getString(SLOT_STORAGE_KEY)
        val root = try {
            if (raw.isEmpty()) JSONObject() else JSONObject(raw)
        } catch (_: Throwable) {
            JSONObject()
        }
        val value = config.normalized()
        root.put(
            presetId,
            JSONObject().apply {
                put("endpoint", value.endpoint)
                put("model", value.model)
                put("apiKey", value.apiKey)
            },
        )
        preferences.setString(SLOT_STORAGE_KEY, root.toString())
    }

    /** 清除当前生效配置与所有厂商槽位 */
    fun clearAll() {
        clear()
        preferences.setString(SLOT_STORAGE_KEY, "")
    }

    companion object {
        private const val STORAGE_KEY = "stockchat_ai_config_v1"
        private const val SLOT_STORAGE_KEY = "stockchat_ai_config_slots_v1"
    }
}
