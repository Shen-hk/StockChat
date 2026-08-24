package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.provider.DeepSeekAiProvider
import com.kuikly.stockchat.page.components.AppTopBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
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
    private val configStore by lazy { AiConfigStore(pagerId) }
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    override fun viewDidLoad() {
        super.viewDidLoad()
        applyConfig(configStore.load())
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            AppTopBar(
                title = "API 设置",
                subtitle = "配置仅保存在当前设备",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
            Scroller {
                attr {
                    flex(1f)
                    padding(16f)
                    paddingBottom(28f + page.pagerData.safeAreaInsets.bottom)
                }
                ConfigSectionTitle("DeepSeek API", "聊天回答将直接调用这里配置的接口。", page.theme)
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
                Text {
                    attr {
                        text("API Key")
                        marginTop(16f)
                        marginBottom(7f)
                        fontSize(12f)
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
                                    fontSize(14f)
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
                                    fontSize(14f)
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
                                fontSize(11f)
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
                        fontSize(10f)
                        lineHeight(16f)
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
                                fontSize(12f)
                                lineHeight(18f)
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
                    Text { attr { text("清除本机配置"); fontSize(12f); color(page.theme.fall) } }
                    event { click { page.clearConfig() } }
                }
            }
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
        configStore.clear()
        applyConfig(AiConfig())
        revealKey = false
        showStatus(true, "已清除当前设备上的 API 配置。")
    }

    private fun applyConfig(config: AiConfig) {
        endpoint = config.endpoint
        model = config.model
        apiKey = config.apiKey
    }

    private fun showStatus(success: Boolean, message: String) {
        statusSuccess = success
        statusMessage = message
    }
}

private fun ViewContainer<*, *>.ConfigSectionTitle(title: String, description: String, theme: StockChatTheme) {
    Text { attr { text(title); fontSize(20f); fontWeightBold(); color(theme.textPrimary) } }
    Text {
        attr {
            text(description)
            marginTop(5f)
            marginBottom(4f)
            fontSize(12f)
            lineHeight(18f)
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
            fontSize(12f)
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
                fontSize(14f)
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
                fontSize(13f)
                fontWeightSemiBold()
                color(if (primary) theme.onBrand else theme.textSecondary)
            }
        }
        event { click { if (enabled) onClick() } }
    }
}
