package com.kuikly.stockchat.foundation.design

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.cards.theme.TypeTokens

/**
 * 外观设置（通用设置页）的取值模型与主题解析。
 *
 * 2026-09-12（doc 47 B-1）从 `data/Appearance.kt` 拆来：这里是**纯设计模型**，
 * 不依赖任何 Kuikly View / Attr，与 Data 无关。UI 侧的字号运行时在
 * `foundation/ui/FontScaleAttr.kt`。
 *
 * 设计约束（为什么不用 observable 全局单例）：Kuikly 的响应式作用域是
 * pager 级的，跨页面的全局 observable 无法驱动其他页面的 attr 重算。
 * 因此持久化交给 SharedPreferencesModule，每页在 BasePager 里读一次并
 * 以本页 observable 承载（appTheme），页面重进/再出现时刷新——与系统
 * 夜间模式（themeDidChanged → nightModel）同一套生命周期。
 */

/** 主题模式：跟随系统 / 浅色 / 深色。 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** 字体缩放档位，factor 乘到全部 TypeTokens 上。 */
enum class FontScale(val id: String, val label: String, val factor: Float) {
    SMALL("small", "小", 0.9f),
    STANDARD("standard", "标准", 1.0f),
    LARGE("large", "大", 1.15f),
    XLARGE("xlarge", "特大", 1.3f),
    ;

    companion object {
        fun fromId(id: String?): FontScale = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

object AppearancePrefs {
    const val KEY_THEME_MODE = "appearance_theme_mode"
    const val KEY_FONT_SCALE = "appearance_font_scale"
}

/** TypeTokens 全字段等比缩放（字号档位落点）。 */
fun TypeTokens.scaled(factor: Float): TypeTokens = copy(
    display = display * factor,
    h1 = h1 * factor,
    title = title * factor,
    body = body * factor,
    sm = sm * factor,
    label = label * factor,
    meta = meta * factor,
)

/**
 * 把（是否系统夜间 + 用户偏好）解析成最终主题。
 * 纯函数：BasePager.appTheme 与 SettingsPage 实时预览共用，保证两处
 * 解析口径永远一致。
 */
fun resolveStockChatTheme(
    systemNight: Boolean,
    modeId: String,
    fontScaleId: String,
): StockChatTheme {
    val base = when (ThemeMode.fromId(modeId)) {
        ThemeMode.DARK -> StockChatTheme.Dark
        ThemeMode.LIGHT -> StockChatTheme.Light
        ThemeMode.SYSTEM -> if (systemNight) StockChatTheme.Dark else StockChatTheme.Light
    }
    return base.copy(type = base.type.scaled(FontScale.fromId(fontScaleId).factor))
}
