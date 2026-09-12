package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.data.AppearancePrefs
import com.kuikly.stockchat.data.FontScale
import com.kuikly.stockchat.data.MarketDataPrefs
import com.kuikly.stockchat.data.MarketDataSource
import com.kuikly.stockchat.data.ThemeMode
import com.kuikly.stockchat.page.components.AppTopBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 通用设置页（2026-09-09）：主题换肤（跟随系统/浅色/深色）+ 字号档位。
 *
 * 写入路径：click → SharedPreferencesModule 落盘 → reloadAppearance() 重读
 * 到本页 observable → AppTopBar 与 Scroller 子树包在 vbind(themeRebuildKey)
 * 里，键翻转整树重建拿到新 theme（参数捕获的 theme 是首帧快照，builder
 * 闭包读 observable 不注册依赖）；选中态经 isSelected 在 attr 内直读
 * observable，无需重建即响应。字号演示栏的 theme.type.* 已含档位缩放，
 * 随同一重建键刷新。
 * 其他页面在 pageDidAppear 时同步重读（BasePager），跨页无响应式通道，
 * 与系统夜间模式 themeDidChanged 同一生命周期约定。
 */
@Page(Routes.SETTINGS, supportInLocal = true)
internal class SettingsPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private var marketDataSource: MarketDataSource by observable(MarketDataSource.REAL)

    private fun persistAppearance(key: String, value: String) {
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .setItem(key, value)
        reloadAppearance()
    }

    override fun created() {
        super.created()
        val prefs = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        marketDataSource = MarketDataSource.fromId(prefs.getString(MarketDataPrefs.KEY_SOURCE))
    }

    private fun persistMarketDataSource(source: MarketDataSource) {
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .setString(MarketDataPrefs.KEY_SOURCE, source.id)
        marketDataSource = source
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            // AppTopBar 及 Scroller 子树都以参数捕获 theme（body 只跑一次的
            // 首帧快照，R1：builder 闭包读 observable 不注册依赖），仅靠
            // reloadAppearance 写 observable 只能让根节点背景色重算。与
            // ChatTopNav 同一约定：vbind 键（themeRebuildKey）翻转时整树
            // 重建，换主题/字号档立即生效，无需退出重进。字号档在键里，
            // FontPreviewCard 的 theme.type.*（已含缩放）随重建拿到新值。
            vbind({ page.themeRebuildKey() }) {
            Scroller {
                attr {
                    flex(1f)
                    // 竖向 Scroller 水平 padding 双倍扣除，padding(16f) 后右 padding 清 0（同 ChatPage/ApiConfigPage）。
                    padding(16f)
                    paddingRight(0f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(28f + page.pagerData.safeAreaInsets.bottom)
                }
                // Scroller 子块是普通 builder 闭包（R1）：这里读 observable
                // 不注册依赖，fontSizeScaled 查的 FontScaleRuntime 表也无
                // 响应式通道——字号/主题变更必须靠 vbind 键翻转重建全部
                // 子项（含字号演示栏），滚动位置保留在 Scroller 上不重置。
                vbind({ page.themeRebuildKey() }) {
                SettingsSectionTitle("外观", "主题切换立即生效；跟随系统时随端侧昼夜模式自动切换。", page.theme)
                View {
                    attr { flexDirectionRow(); marginTop(12f) }
                    val cardWidth = (page.pagerData.pageViewWidth - 32f - 16f) / 3f
                    ThemeMode.entries.forEach { mode ->
                        if (mode != ThemeMode.SYSTEM) View { attr { width(8f) } }
                        ThemeModeCard(
                            mode = mode,
                            theme = page.theme,
                            width = cardWidth,
                            isSelected = { page.appearanceThemeModeId() == mode.id },
                            onClick = { page.persistAppearance(AppearancePrefs.KEY_THEME_MODE, mode.id) },
                        )
                    }
                }
                SettingsSectionTitle("字体大小", "作用于正文与说明文字，预览即时更新。", page.theme)
                View {
                    attr { flexDirectionRow(); marginTop(12f); flexWrapWrap() }
                    FontScale.entries.forEachIndexed { index, scale ->
                        if (index > 0) View { attr { width(8f) } }
                        FontScaleChip(
                            scale = scale,
                            theme = page.theme,
                            isSelected = { page.appearanceFontScaleId() == scale.id },
                            onClick = { page.persistAppearance(AppearancePrefs.KEY_FONT_SCALE, scale.id) },
                        )
                    }
                }
                FontPreviewCard(theme = page.theme)
                SettingsSectionTitle("模型与数据", "", page.theme)
                SettingsSectionTitle("行情来源", "真实接口未在等待窗口内返回有效行情时，会自动切换为该股票的本地 Mock 交易日数据。", page.theme)
                View {
                    attr {
                        marginTop(12f)
                        height(44f)
                        padding(4f)
                        borderRadius(12f)
                        backgroundColor(page.theme.surfaceMuted)
                        flexDirectionRow()
                    }
                    MarketDataSource.entries.forEach { source ->
                        MarketDataSourceTab(
                            source = source,
                            theme = page.theme,
                            isSelected = { page.marketDataSource == source },
                            onClick = { page.persistMarketDataSource(source) },
                        )
                    }
                }
                Text {
                    attr {
                        text(
                            if (page.marketDataSource == MarketDataSource.REAL) {
                                "当前：优先请求真实行情；切回此项后每次请求都会先探测接口。"
                            } else {
                                "当前：直接使用本地 Mock 数据（固定为一个交易日）。"
                            }
                        )
                        marginTop(8f)
                        fontSizeScaled(11f)
                        color(page.theme.textTertiary)
                    }
                }
                ApiConfigEntryRow(theme = page.theme) {
                    page.openPage(Routes.API_CONFIG)
                }
                Text {
                    attr {
                        text("设置保存在本机，字号档位对全部页面即时生效。")
                        marginTop(14f)
                        fontSizeScaled(11f)
                        color(page.theme.textTertiary)
                    }
                }
                }
            }
            }
            // AppTopBar 必须声明在 Scroller 之后：顶栏是 absolutePosition 浮层，
            // 后声明的全屏 Scroller z 序更高，原生 ScrollView 会吃掉顶栏区域的
            // 触摸——放在前面时返回键永远收不到 click（其他页面均为此后置顺序）。
            vbind({ page.themeRebuildKey() }) {
            AppTopBar(
                title = "通用设置",
                subtitle = "",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                backLabel = "‹",
                onBack = { page.closePage() },
            )
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketDataSourceTab(
    source: MarketDataSource,
    theme: StockChatTheme,
    isSelected: () -> Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            height(36f)
            borderRadius(9f)
            backgroundColor(if (isSelected()) theme.surface else Color(0x00000000))
            allCenter()
        }
        event { click { onClick() } }
        Text {
            attr {
                text(source.label)
                fontSizeScaled(13f)
                fontWeightMedium()
                color(if (isSelected()) theme.brand else theme.textSecondary)
            }
        }
    }
}

private fun ViewContainer<*, *>.SettingsSectionTitle(title: String, subtitle: String, theme: StockChatTheme) {
    Text {
        attr {
            text(title)
            marginTop(if (subtitle.isEmpty()) 20f else 20f)
            fontSizeScaled(13f)
            fontWeightSemiBold()
            color(theme.textPrimary)
        }
    }
    if (subtitle.isNotEmpty()) {
        Text {
            attr {
                text(subtitle)
                marginTop(4f)
                fontSizeScaled(11f)
                color(theme.textTertiary)
            }
        }
    }
}

/** 主题模式卡：迷你色板预览（SYSTEM 显示浅/深对半，LIGHT/DARK 各自页面色 + 文字点）。 */
private fun ViewContainer<*, *>.ThemeModeCard(
    mode: ThemeMode,
    theme: StockChatTheme,
    width: Float,
    isSelected: () -> Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            width(width)
            height(76f)
            borderRadius(12f)
            backgroundColor(theme.surface)
            border(Border(1f, BorderStyle.SOLID, if (isSelected()) theme.brand else theme.divider))
            allCenter()
        }
        event { click { onClick() } }
        // 色板：固定展示两主题的页面色，不随当前选中态变化（预览即所见）。
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            val previewPage = when (mode) {
                ThemeMode.LIGHT -> StockChatTheme.Light.page
                ThemeMode.DARK -> StockChatTheme.Dark.page
                ThemeMode.SYSTEM -> theme.page // 跟随系统：用当前实况色 + 月牙标识
            }
            View {
                attr {
                    size(34f, 24f)
                    borderRadius(6f)
                    backgroundColor(previewPage)
                    border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                    allCenter()
                }
                Text {
                    attr {
                        text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "A"
                                ThemeMode.LIGHT -> "A"
                                ThemeMode.DARK -> "A"
                            }
                        )
                        fontSizeScaled(11f)
                        fontWeightBold()
                        // SYSTEM 用当前实况文字色（预览底=当前实况页面色，恒有对比度）；
                        // LIGHT/DARK 固定用各自色板的文字色（预览即所见）。
                        color(
                            when (mode) {
                                ThemeMode.SYSTEM -> theme.textPrimary
                                ThemeMode.LIGHT -> StockChatTheme.Light.textPrimary
                                ThemeMode.DARK -> StockChatTheme.Dark.textPrimary
                            }
                        )
                    }
                }
            }
            View {
                attr { marginLeft(8f) }
                Text {
                    attr {
                        text(mode.label)
                        fontSizeScaled(12f)
                        fontWeightMedium()
                        color(if (isSelected()) theme.brand else theme.textSecondary)
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.FontScaleChip(
    scale: FontScale,
    theme: StockChatTheme,
    isSelected: () -> Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(34f)
            paddingLeft(14f)
            paddingRight(14f)
            borderRadius(17f)
            backgroundColor(if (isSelected()) theme.brandSoft else theme.surfaceMuted)
            allCenter()
        }
        event { click { onClick() } }
        Text {
            attr {
                text(scale.label)
                fontSizeScaled(13f)
                fontWeightMedium()
                color(if (isSelected()) theme.brand else theme.textSecondary)
            }
        }
    }
}

/** 字号实时预览卡：一条问答气泡，字号取自 theme.type（已含档位缩放）。 */
private fun ViewContainer<*, *>.FontPreviewCard(theme: StockChatTheme) {
    View {
        attr {
            marginTop(12f)
            borderRadius(theme.cardRadius)
            backgroundColor(theme.surface)
            padding(14f)
        }
        Text {
            attr {
                text("预览")
                fontSize(theme.type.meta)
                color(theme.textTertiary)
            }
        }
        View {
            attr {
                marginTop(10f)
                backgroundColor(theme.brandSoft)
                borderRadius(14f)
                padding(10f)
            }
            Text {
                attr {
                    text("市盈率是什么意思？")
                    fontSize(theme.type.body)
                    color(theme.textPrimary)
                }
            }
        }
        View {
            attr {
                marginTop(8f)
                backgroundColor(theme.surfaceMuted)
                borderRadius(14f)
                padding(10f)
            }
            Text {
                attr {
                    text("市盈率（PE）= 股价 ÷ 每股收益，衡量市场愿意为公司每 1 元盈利支付的价格。")
                    fontSize(theme.type.sm)
                    lineHeight(theme.type.sm * 1.5f)
                    color(theme.textPrimary)
                }
            }
        }
        Text {
            attr {
                text("行情数字由端侧直填，AI 只负责解释")
                marginTop(8f)
                fontSize(theme.type.meta)
                color(theme.textTertiary)
            }
        }
    }
}

private fun ViewContainer<*, *>.ApiConfigEntryRow(theme: StockChatTheme, onClick: () -> Unit) {
    View {
        attr {
            marginTop(12f)
            height(60f)
            paddingLeft(14f)
            paddingRight(12f)
            borderRadius(theme.cardRadius)
            backgroundColor(theme.surface)
            flexDirectionRow()
            alignItemsCenter()
        }
        event { click { onClick() } }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text("模型与 API 配置")
                    fontSizeScaled(14f)
                    fontWeightMedium()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text("接口地址、模型名与 API Key")
                    marginTop(2f)
                    fontSizeScaled(11f)
                    color(theme.textTertiary)
                }
            }
        }
        Text {
            attr {
                text("›")
                fontSizeScaled(20f)
                color(theme.textTertiary)
            }
        }
    }
}
