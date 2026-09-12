package com.kuikly.stockchat.data.config

/**
 * 主流模型服务商预设。
 *
 * 所有预设均为 OpenAI 兼容的 chat/completions 接口（Bearer 鉴权），
 * 与 [com.kuikly.stockchat.data.provider.OpenAiCompatAiProvider] 的请求格式一致；
 * 选中预设后只需再填 API Key 即可使用。
 */
data class ModelPreset(
    val id: String,
    /** 展示名 */
    val name: String,
    /** 厂商 logo 文件名主干（页面资源，前缀 light-/dark- + .png 由页面按主题拼接）；空表示无 logo，用 [badge] 文字兜底 */
    val logo: String,
    /** 无 logo 时的文字徽标简称 */
    val badge: String,
    /** 文字徽标底色（ARGB，必须带 0xFF alpha，否则 Color() 解析为全透明） */
    val badgeColor: Long,
    /** 接口地址；自定义预设为空，由用户手填 */
    val endpoint: String,
    /** 预置模型名，第一个为默认模型 */
    val models: List<String>,
    /** API Key 获取渠道提示 */
    val keyHint: String,
)

object ModelPresets {

    val all: List<ModelPreset> = listOf(
        ModelPreset(
            id = "deepseek",
            name = "DeepSeek",
            logo = "deepseek-color",
            badge = "DS",
            badgeColor = 0xFF4D6BFE,
            endpoint = "https://api.deepseek.com/chat/completions",
            models = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
            keyHint = "在 platform.deepseek.com 创建 API Key；已切换到仍受支持的 V4 模型。",
        ),
        ModelPreset(
            id = "kimi",
            name = "Kimi",
            logo = "kimi-badge",
            badge = "K",
            badgeColor = 0xFF1F2329,
            endpoint = "https://api.moonshot.cn/v1/chat/completions",
            models = listOf("kimi-k2.6", "kimi-k3"),
            keyHint = "在 platform.moonshot.cn（月之暗面开放平台）创建 API Key。",
        ),
        ModelPreset(
            id = "glm",
            name = "智谱 GLM",
            logo = "zhipu-color",
            badge = "GLM",
            badgeColor = 0xFF3859FF,
            endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            models = listOf("glm-5", "glm-4.7-flash"),
            keyHint = "在 open.bigmodel.cn 创建 API Key；glm-4.7-flash 免费。",
        ),
        ModelPreset(
            id = "qwen",
            name = "通义千问",
            logo = "qwen-color",
            badge = "千问",
            badgeColor = 0xFF615CEC,
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            models = listOf("qwen-plus", "qwen-max", "qwen-turbo"),
            keyHint = "在阿里云百炼（bailian.console.aliyun.com）开通服务并创建 API Key。",
        ),
        ModelPreset(
            id = "doubao",
            name = "豆包",
            logo = "doubao-color",
            badge = "豆包",
            badgeColor = 0xFF1456F0,
            endpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            models = listOf("doubao-seed-2-1-pro-260628"),
            keyHint = "在火山方舟（console.volcengine.com/ark）创建 API Key；模型名也可填推理接入点 ID（ep- 开头）。",
        ),
        ModelPreset(
            id = "hunyuan",
            name = "腾讯混元",
            logo = "hunyuan-color",
            badge = "混元",
            badgeColor = 0xFF0052D9,
            endpoint = "https://tokenhub.tencentmaas.com/v1/chat/completions",
            models = listOf("hy3-preview"),
            keyHint = "在腾讯云 TokenHub 控制台创建 API Key；该 OpenAI 兼容接口已替代旧混元接口。",
        ),
        ModelPreset(
            id = "openai",
            name = "OpenAI",
            logo = "openai",
            badge = "GPT",
            badgeColor = 0xFF10A37F,
            endpoint = "https://api.openai.com/v1/chat/completions",
            models = listOf("gpt-5.6-terra", "gpt-5.6-luna"),
            keyHint = "在 platform.openai.com 创建 API Key。",
        ),
        ModelPreset(
            id = "mimo",
            name = "小米 MiMo",
            logo = "mimo-color",
            badge = "Mi",
            badgeColor = 0xFFFF6900,
            endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
            models = listOf("mimo-v2.5", "mimo-v2.5-pro"),
            keyHint = "在 platform.xiaomimimo.com 创建 API Key；mimo-v2.5 支持原生图像、音频、视频与长上下文理解。",
        ),
        ModelPreset(
            id = "custom",
            name = "自定义",
            logo = "stockchat",
            badge = "自",
            badgeColor = 0xFF8A8F99,
            endpoint = "",
            models = emptyList(),
            keyHint = "手动填写任意 OpenAI 兼容的 /chat/completions 接口地址与模型名。",
        ),
    )

    fun byId(id: String): ModelPreset? = all.firstOrNull { it.id == id }

    /** 按已保存的接口地址反查预设，找不到（含自定义接口）返回 null。 */
    fun matchEndpoint(endpoint: String): ModelPreset? =
        all.firstOrNull { it.endpoint.isNotEmpty() && it.endpoint == endpoint }
}
