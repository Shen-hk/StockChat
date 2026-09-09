package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.config.ModelPreset
import com.kuikly.stockchat.data.config.ModelPresets
import com.kuikly.stockchat.data.provider.DeepSeekAiProvider
import com.kuikly.stockchat.page.components.AppTopBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.API_CONFIG, supportInLocal = true)
internal class ApiConfigPage : BasePager() {
    private var endpoint: String by observable(AiConfig.DEFAULT_ENDPOINT)
    private var model: String by observable(AiConfig.DEFAULT_MODEL)
    private var apiKey: String by observable("")
    private var revealKey: Boolean by observable(false)
    private var testing: Boolean by observable(false)
    private var statusMessage: String by observable("")
    private var statusSuccess: Boolean by observable(false)
    private var selectedPresetId: String by observable(ModelPresets.all.first().id)
    private var slotVersion: Int by observable(0)
    private val configStore by lazy { AiConfigStore(pagerId) }
    private val theme: StockChatTheme get() = appTheme()

    override fun viewDidLoad() {
        super.viewDidLoad()
        applyConfig(configStore.load())
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    // 竖向 Scroller 水平 padding 会被双倍扣除，padding(16f) 后右 padding 清 0 对齐（同 ChatPage）。
                    padding(16f)
                    paddingRight(0f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(28f + page.pagerData.safeAreaInsets.bottom)
                }
                ConfigSectionTitle("选择模型服务", "选中服务商后自动填好接口地址和模型名，再填 API Key 即可使用。", page.theme)
                Scroller {
                    // 横向 Scroller 必须写显式 height，否则内容层塌 0 被外层裁掉
                    attr { flexDirectionRow(); height(94f); marginTop(12f) }
                    ModelPresets.all.forEach { preset ->
                        PresetCard(
                            preset = preset,
                            theme = page.theme,
                            dark = page.appIsDarkTheme(),
                            isSelected = { page.selectedPresetId == preset.id },
                            hasSavedKey = { page.hasSavedKey(preset.id) },
                            onClick = { page.applyPreset(preset) },
                        )
                    }
                }
                Text {
                    attr {
                        text(page.selectedPreset()?.keyHint.orEmpty())
                        marginTop(9f)
                        fontSizeScaled(10f)
                        lineHeightScaled(16f)
                        color(page.theme.textTertiary)
                    }
                }
                ConfigField(
                    label = "API 地址",
                    hint = "例如 https://api.deepseek.com/chat/completions",
                    value = { page.endpoint },
                    theme = page.theme,
                    onChange = { page.endpoint = it },
                )
                ConfigField(
                    label = "模型名称",
                    hint = "例如 deepseek-chat",
                    value = { page.model },
                    theme = page.theme,
                    onChange = { page.model = it },
                )
                vbind({ page.selectedPreset()?.id.orEmpty() }) {
                    val models = page.selectedPreset()?.models.orEmpty()
                    if (models.size > 1) {
                        View {
                            attr {
                                marginTop(8f)
                                flexDirectionRow()
                                flexWrapWrap()
                            }
                            models.forEach { modelId ->
                                ModelVariantChip(
                                    modelId = modelId,
                                    theme = page.theme,
                                    isSelected = { page.model == modelId },
                                    onClick = { page.model = modelId },
                                )
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text("API Key")
                        marginTop(16f)
                        marginBottom(7f)
                        fontSizeScaled(12f)
                        fontWeightSemiBold()
                        color(page.theme.textSecondary)
                    }
                }
                View {
                    attr {
                        height(48f)
                        paddingLeft(12f)
                        paddingRight(8f)
                        borderRadius(page.theme.inputRadius)
                        backgroundColor(page.theme.surface)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr { flex(1f); height(44f); justifyContentCenter() }
                        vif({ page.revealKey }) {
                            Input {
                                attr {
                                    flex(1f)
                                    text(page.apiKey)
                                    fontSizeScaled(14f)
                                    color(page.theme.textPrimary)
                                    placeholder("输入你的 API Key")
                                    placeholderColor(page.theme.textTertiary)
                                }
                                event { textDidChange { page.apiKey = it.text } }
                            }
                        }
                        vif({ !page.revealKey }) {
                            Input {
                                attr {
                                    flex(1f)
                                    text(page.apiKey)
                                    fontSizeScaled(14f)
                                    color(page.theme.textPrimary)
                                    placeholder("输入你的 API Key")
                                    placeholderColor(page.theme.textTertiary)
                                    keyboardTypePassword()
                                }
                                event { textDidChange { page.apiKey = it.text } }
                            }
                        }
                    }
                    View {
                        attr { padding(8f); borderRadius(8f); backgroundColor(page.theme.surfaceMuted) }
                        Text {
                            attr {
                                text(if (page.revealKey) "隐藏" else "显示")
                                fontSizeScaled(11f)
                                color(page.theme.textSecondary)
                            }
                        }
                        event { click { page.revealKey = !page.revealKey } }
                    }
                }
                Text {
                    attr {
                        text("密钥不会写入源码或构建产物。Android/H5 都保存在当前设备；浏览器无法安全隐藏前端密钥，H5 正式环境应使用后端代理。")
                        marginTop(8f)
                        fontSizeScaled(10f)
                        lineHeightScaled(16f)
                        color(page.theme.textTertiary)
                    }
                }
                vif({ page.statusMessage.isNotEmpty() }) {
                    View {
                        attr {
                            marginTop(16f)
                            padding(12f)
                            borderRadius(10f)
                            backgroundColor(if (page.statusSuccess) page.theme.brandSoft else page.theme.surfaceMuted)
                        }
                        Text {
                            attr {
                                text(page.statusMessage)
                                fontSizeScaled(12f)
                                lineHeightScaled(18f)
                                color(if (page.statusSuccess) page.theme.brand else page.theme.textSecondary)
                            }
                        }
                    }
                }
                View {
                    attr { marginTop(18f); flexDirectionRow() }
                    ActionButton(
                        label = if (page.testing) "测试中…" else "测试连接",
                        primary = false,
                        enabled = !page.testing,
                        theme = page.theme,
                        onClick = { page.testConnection() },
                    )
                    View { attr { width(10f) } }
                    ActionButton(
                        label = "保存配置",
                        primary = true,
                        enabled = !page.testing,
                        theme = page.theme,
                        onClick = { page.saveConfig() },
                    )
                }
                View {
                    attr {
                        marginTop(12f)
                        height(42f)
                        allCenter()
                        borderRadius(11f)
                        backgroundColor(page.theme.surface)
                    }
                    Text { attr { text("清除本机配置"); fontSizeScaled(12f); color(page.theme.fall) } }
                    event { click { page.clearConfig() } }
                }
            }
            AppTopBar(
                title = "API 设置",
                subtitle = "配置仅保存在当前设备",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
        }
    }

    private fun currentConfig() = AiConfig(endpoint, model, apiKey).normalized()

    private fun saveConfig() {
        val config = currentConfig()
        val error = config.validationError()
        if (error != null) {
            showStatus(false, error)
            return
        }
        configStore.save(config)
        applyConfig(config)
        persistCurrentSlot()
        showStatus(true, "配置已保存在当前设备，下次发送消息时会直接调用该接口。")
    }

    private fun testConnection() {
        if (testing) return
        val config = currentConfig()
        val error = config.validationError()
        if (error != null) {
            showStatus(false, error)
            return
        }
        testing = true
        showStatus(false, "正在连接 API…")
        DeepSeekAiProvider(pagerId, config).testConnection { success, message ->
            testing = false
            showStatus(success, message)
        }
    }

    private fun clearConfig() {
        configStore.clearAll()
        applyConfig(AiConfig())
        revealKey = false
        slotVersion++
        showStatus(true, "已清除当前设备上的 API 配置。")
    }

    private fun applyConfig(config: AiConfig) {
        endpoint = config.endpoint
        model = config.model
        apiKey = config.apiKey
        selectedPresetId = ModelPresets.matchEndpoint(config.endpoint)?.id ?: "custom"
    }

    private fun selectedPreset(): ModelPreset? = ModelPresets.byId(selectedPresetId)

    /** 该厂商槽位里是否已保存过 API Key（读 slotVersion 建立响应式依赖） */
    private fun hasSavedKey(presetId: String): Boolean {
        val version = slotVersion
        return version >= 0 && configStore.loadPresetConfig(presetId)?.apiKey?.isNotEmpty() == true
    }

    /** 把当前表单配置写入当前所选厂商的槽位（自定义预设除外） */
    private fun persistCurrentSlot() {
        val preset = selectedPreset() ?: return
        if (preset.endpoint.isEmpty()) return
        configStore.savePresetConfig(preset.id, currentConfig())
        slotVersion++
    }

    private fun applyPreset(preset: ModelPreset) {
        if (preset.id == selectedPresetId) return
        // 先把当前厂商的配置（含 API Key）存进它的槽位，再恢复目标厂商的
        persistCurrentSlot()
        selectedPresetId = preset.id
        val saved = if (preset.endpoint.isNotEmpty()) configStore.loadPresetConfig(preset.id) else null
        if (saved != null) {
            endpoint = saved.endpoint
            model = saved.model
            apiKey = saved.apiKey
        } else if (preset.endpoint.isNotEmpty()) {
            endpoint = preset.endpoint
            model = preset.models.firstOrNull().orEmpty()
            apiKey = ""
        }
        // 自定义预设：不改动手填的地址与模型名
        showStatus(false, "")
    }

    private fun showStatus(success: Boolean, message: String) {
        statusSuccess = success
        statusMessage = message
    }
}

private fun logoAsset(preset: ModelPreset, dark: Boolean): String =
    (if (dark) "dark-" else "light-") + preset.logo + ".png"

private fun ViewContainer<*, *>.PresetCard(
    preset: ModelPreset,
    theme: StockChatTheme,
    dark: Boolean,
    isSelected: () -> Boolean,
    hasSavedKey: () -> Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            // isSelected / hasSavedKey 在 attr·vif 闭包内读取，保证选中态与配置态随 observable 变化重渲染（R1）
            val selected = isSelected()
            marginRight(8f)
            width(92f)
            height(94f)
            allCenter()
            borderRadius(14f)
            backgroundColor(if (selected) theme.brandSoft else theme.surface)
            if (selected) border(Border(1f, BorderStyle.SOLID, theme.brand))
        }
        vif({ preset.logo.isNotEmpty() }) {
            Image {
                attr {
                    src(ImageUri.pageAssets(logoAsset(preset, dark)))
                    size(40f, 40f)
                }
            }
        }
        vif({ preset.logo.isEmpty() }) {
            View {
                attr {
                    size(40f, 40f)
                    allCenter()
                    borderRadius(12f)
                    backgroundColor(Color(preset.badgeColor))
                }
                Text {
                    attr {
                        text(preset.badge)
                        fontSizeScaled(14f)
                        fontWeightBold()
                        color(Color(0xFFFFFF))
                    }
                }
            }
        }
        Text {
            attr {
                text(preset.name)
                marginTop(7f)
                fontSizeScaled(11f)
                fontWeightMedium()
                color(theme.textPrimary)
            }
        }
        vif({ hasSavedKey() }) {
            View {
                attr {
                    absolutePosition(top = 8f, right = 8f)
                    size(6f, 6f)
                    borderRadius(3f)
                    backgroundColor(theme.brand)
                }
            }
        }
        event { click { onClick() } }
    }
}

private fun ViewContainer<*, *>.ModelVariantChip(
    modelId: String,
    theme: StockChatTheme,
    isSelected: () -> Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            val selected = isSelected()
            marginRight(8f)
            marginBottom(8f)
            paddingLeft(10f)
            paddingRight(10f)
            height(26f)
            allCenter()
            borderRadius(13f)
            backgroundColor(if (selected) theme.brandSoft else theme.surface)
            if (selected) border(Border(1f, BorderStyle.SOLID, theme.brand))
        }
        Text {
            attr {
                // selected 在各自 attr 闭包内读取，保证选中态随选中项变化重渲染（R1）
                val selected = isSelected()
                text(modelId)
                fontSizeScaled(11f)
                color(if (selected) theme.brand else theme.textSecondary)
            }
        }
        event { click { onClick() } }
    }
}

private fun ViewContainer<*, *>.ConfigSectionTitle(title: String, description: String, theme: StockChatTheme) {
    Text { attr { text(title); fontSizeScaled(20f); fontWeightBold(); color(theme.textPrimary) } }
    Text {
        attr {
            text(description)
            marginTop(5f)
            marginBottom(4f)
            fontSizeScaled(12f)
            lineHeightScaled(18f)
            color(theme.textSecondary)
        }
    }
}

private fun ViewContainer<*, *>.ConfigField(
    label: String,
    hint: String,
    value: () -> String,
    theme: StockChatTheme,
    onChange: (String) -> Unit,
) {
    Text {
        attr {
            text(label)
            marginTop(16f)
            marginBottom(7f)
            fontSizeScaled(12f)
            fontWeightSemiBold()
            color(theme.textSecondary)
        }
    }
    View {
        attr {
            height(48f)
            paddingLeft(12f)
            paddingRight(12f)
            borderRadius(theme.inputRadius)
            backgroundColor(theme.surface)
            justifyContentCenter()
        }
        Input {
            attr {
                flex(1f)
                text(value())
                fontSizeScaled(14f)
                color(theme.textPrimary)
                placeholder(hint)
                placeholderColor(theme.textTertiary)
            }
            event { textDidChange { onChange(it.text) } }
        }
    }
}

private fun ViewContainer<*, *>.ActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    theme: StockChatTheme,
    onClick: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            height(46f)
            allCenter()
            borderRadius(11f)
            backgroundColor(if (primary) theme.brand else theme.surface)
            opacity(if (enabled) 1f else 0.55f)
        }
        Text {
            attr {
                text(label)
                fontSizeScaled(13f)
                fontWeightSemiBold()
                color(if (primary) theme.onBrand else theme.textSecondary)
            }
        }
        event { click { if (enabled) onClick() } }
    }
}
