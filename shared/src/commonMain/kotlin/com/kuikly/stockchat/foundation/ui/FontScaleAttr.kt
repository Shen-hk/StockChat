package com.kuikly.stockchat.foundation.ui

import com.tencent.kuikly.core.views.InputAttr
import com.tencent.kuikly.core.views.TextAreaAttr
import com.tencent.kuikly.core.views.TextAttr

/**
 * App 内字号档位的 UI 侧运行时与 Attr 扩展。
 *
 * 2026-09-12（doc 47 B-1）从 `data/Appearance.kt` 拆来：这些扩展依赖 Kuikly 的
 * `TextAttr` / `InputAttr` / `TextAreaAttr`，属 UI 基础设施而非 Data。
 * 纯设计模型（ThemeMode / FontScale / resolveStockChatTheme）在
 * `foundation/design/Appearance.kt`。
 */

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
