package com.kuikly.stockchat.data

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.cards.theme.TypeTokens
import com.tencent.kuikly.core.views.InputAttr
import com.tencent.kuikly.core.views.TextAreaAttr
import com.tencent.kuikly.core.views.TextAttr

/**
 * 外观设置（通用设置页）的三个持久化键与取值模型。
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
 * pagerId → 当前字号缩放系数（通用设置档位）。
 * BasePager 在 created/pageDidAppear 写入、pageWillDestroy 移除；Kuikly 的
 * attr 构建全部发生在主线程，HashMap 足够。之所以按 pagerId 建表而不是
 * 全局单值：attr 闭包（vfor/vif 重绑定）可能在其他 pager 前台期间重放，
 * 按 pagerId 查表才能保证各页面各取所需。
 */
object FontScaleRuntime {
    private val factors = HashMap<String, Float>()

    fun update(pagerId: String, factor: Float) {
        factors[pagerId] = factor
    }

    fun remove(pagerId: String) {
        factors.remove(pagerId)
    }

    fun factor(pagerId: String): Float = factors[pagerId] ?: 1f
}

/**
 * 字号/行高缩放扩展（Kuikly Attr 基类 Props 实现 IPagerId，因此任何 attr
 * 闭包内都能无感拿到 pagerId，调用点 receiver 零改动）。配合框架原生
 * FontModule 的区别：FontModule 跟随宿主系统字号（native 侧定系数），
 * 这里乘的是 App 内「通用设置-字体大小」档位；两者刻意不叠加，
 * scaleFontSizeEnable 保持默认关闭。
 */
fun TextAttr.fontSizeScaled(size: Float): TextAttr {
    fontSize(size * FontScaleRuntime.factor(pagerId))
    return this
}

fun TextAttr.lineHeightScaled(lineHeight: Float): TextAttr {
    lineHeight(lineHeight * FontScaleRuntime.factor(pagerId))
    return this
}

fun InputAttr.fontSizeScaled(size: Float): InputAttr {
    fontSize(size * FontScaleRuntime.factor(pagerId))
    return this
}

fun TextAreaAttr.fontSizeScaled(size: Float): TextAreaAttr {
    fontSize(size * FontScaleRuntime.factor(pagerId))
    return this
}

fun TextAreaAttr.lineHeightScaled(lineHeight: Float): TextAreaAttr {
    lineHeight(lineHeight * FontScaleRuntime.factor(pagerId))
    return this
}

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
