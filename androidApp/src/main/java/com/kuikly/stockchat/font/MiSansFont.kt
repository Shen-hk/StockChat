package com.kuikly.stockchat.font

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import java.lang.reflect.Field

/**
 * 全局字体接管：在 Application 启动时把 MiSans 替换为系统默认字体。
 *
 * 覆盖两条路径，保证所有文本（Kuikly 渲染文本 + 原生控件）统一生效：
 * 1. 替换 Typeface 静态默认字段（DEFAULT / DEFAULT_BOLD / SANS_SERIF / sDefaults 数组）
 *    —— 覆盖 Paint/TextView 直接读取 Typeface.DEFAULT 的场景；
 * 2. 替换 Typeface.sSystemFontMap 中所有 sans-serif* 别名
 *    —— 覆盖 Typeface.create("sans-serif-medium", ...) 等按名字查找的场景。
 *
 * 资源缺失或反射失败时静默降级为系统字体，不影响启动。
 */
object MiSansFont {

    private const val TAG = "MiSansFont"
    private const val DIR = "fonts"

    private var sansRegular: Typeface? = null
    private var sansMedium: Typeface? = null
    private var sansDemibold: Typeface? = null

    fun apply(context: Context) {
        try {
            sansRegular = Typeface.createFromAsset(context.assets, "$DIR/MiSans-Regular.ttf")
            sansMedium = Typeface.createFromAsset(context.assets, "$DIR/MiSans-Medium.ttf")
            sansDemibold = Typeface.createFromAsset(context.assets, "$DIR/MiSans-Demibold.ttf")
        } catch (t: Throwable) {
            return // 字体资源缺失，保持系统默认
        }
        val regular = sansRegular ?: return
        val medium = sansMedium ?: regular
        val demibold = sansDemibold ?: medium

        val staticOk = replaceStaticTypefaces(regular, demibold)
        val mappedKeys = replaceSystemFontMap(regular, medium, demibold)
        Log.d(TAG, "apply: assets=ok staticDefaults=$staticOk systemFontMapKeys=$mappedKeys")
    }

    /** 返回已加载的 MiSans 字重，供 KRFontAdapter 按 fontFamily 名取用；未初始化返回 null。 */
    fun typefaceFor(fontFamily: String): Typeface? = when (fontFamily) {
        "MiSans", "MiSans-Regular" -> sansRegular
        "MiSans-Medium" -> sansMedium
        "MiSans-Demibold", "MiSans-Bold" -> sansDemibold
        else -> null
    }

    private fun replaceStaticTypefaces(regular: Typeface, demibold: Typeface): Boolean {
        val ok1 = setStatic("DEFAULT", regular)
        val ok2 = setStatic("DEFAULT_BOLD", demibold)
        val ok3 = setStatic("SANS_SERIF", regular)
        setStatic("SERIF", regular)
        setStatic("MONOSPACE", regular)
        // Typeface.defaultFromStyle() 读取 sDefaults 数组
        var defaultsOk = false
        try {
            val f: Field = Typeface::class.java.getDeclaredField("sDefaults")
            f.isAccessible = true
            val arr = f.get(null) as Array<Typeface?>
            arr[0] = regular // NORMAL
            arr[1] = demibold // BOLD
            arr[2] = regular // ITALIC
            arr[3] = demibold // BOLD_ITALIC
            defaultsOk = true
        } catch (t: Throwable) {
        }
        return ok1 && ok2 && ok3 && defaultsOk
    }

    private fun replaceSystemFontMap(regular: Typeface, medium: Typeface, demibold: Typeface): Int {
        var mapped = 0
        try {
            val f: Field = Typeface::class.java.getDeclaredField("sSystemFontMap")
            f.isAccessible = true
            val systemFontMap = f.get(null) as? MutableMap<String, Typeface> ?: return -1
            val overrides = mapOf(
                "sans-serif" to regular,
                "sans-serif-regular" to regular,
                "sans-serif-light" to regular,
                "sans-serif-thin" to regular,
                "sans-serif-condensed" to regular,
                "sans-serif-condensed-light" to regular,
                "sans-serif-smallcaps" to regular,
                "sans-serif-medium" to medium,
                "sans-serif-condensed-medium" to medium,
                "sans-serif-black" to demibold,
                "serif" to regular,
                "monospace" to regular
            )
            for ((key, value) in overrides) {
                if (systemFontMap.containsKey(key)) {
                    systemFontMap[key] = value
                    mapped++
                }
            }
        } catch (t: Throwable) {
            return -1
        }
        return mapped
    }

    private fun setStatic(fieldName: String, value: Typeface?): Boolean {
        return try {
            val f: Field = Typeface::class.java.getDeclaredField(fieldName)
            f.isAccessible = true
            f.set(null, value)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
