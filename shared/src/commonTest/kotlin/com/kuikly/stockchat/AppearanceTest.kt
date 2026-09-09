package com.kuikly.stockchat

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.cards.theme.TypeTokens
import com.kuikly.stockchat.data.FontScale
import com.kuikly.stockchat.data.FontScaleRuntime
import com.kuikly.stockchat.data.ThemeMode
import com.kuikly.stockchat.data.resolveStockChatTheme
import com.kuikly.stockchat.data.scaled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppearanceTest {

    @Test
    fun systemModeFollowsHostNightState() {
        assertEquals(
            StockChatTheme.Dark.page,
            resolveStockChatTheme(systemNight = true, modeId = "system", fontScaleId = "standard").page,
        )
        assertEquals(
            StockChatTheme.Light.page,
            resolveStockChatTheme(systemNight = false, modeId = "system", fontScaleId = "standard").page,
        )
    }

    @Test
    fun explicitModeOverridesSystemState() {
        val lightInNight = resolveStockChatTheme(systemNight = true, modeId = "light", fontScaleId = "standard")
        val darkInDay = resolveStockChatTheme(systemNight = false, modeId = "dark", fontScaleId = "standard")
        assertEquals(StockChatTheme.Light.page, lightInNight.page)
        assertEquals(StockChatTheme.Dark.page, darkInDay.page)
    }

    @Test
    fun unknownIdsFallBackToDefaults() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("bogus"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(null))
        assertEquals(FontScale.STANDARD, FontScale.fromId("bogus"))
        assertEquals(FontScale.STANDARD, FontScale.fromId(null))
        // 非法输入按默认档解析，不应抛异常
        val theme = resolveStockChatTheme(systemNight = false, modeId = "bogus", fontScaleId = "bogus")
        assertEquals(StockChatTheme.Light.type.body, theme.type.body)
    }

    @Test
    fun fontScaleMultipliesAllTypeTokens() {
        val base = TypeTokens()
        val scaled = base.scaled(1.3f)
        assertEquals(base.display * 1.3f, scaled.display)
        assertEquals(base.h1 * 1.3f, scaled.h1)
        assertEquals(base.title * 1.3f, scaled.title)
        assertEquals(base.body * 1.3f, scaled.body)
        assertEquals(base.sm * 1.3f, scaled.sm)
        assertEquals(base.label * 1.3f, scaled.label)
        assertEquals(base.meta * 1.3f, scaled.meta)
    }

    @Test
    fun resolvedThemeCarriesFontScaleIntoTypeTokens() {
        val theme = resolveStockChatTheme(systemNight = false, modeId = "light", fontScaleId = "large")
        assertEquals(StockChatTheme.Light.type.body * 1.15f, theme.type.body)
        // 非字号字段不受档位影响
        assertEquals(StockChatTheme.Light.cardRadius, theme.cardRadius)
        assertEquals(StockChatTheme.Light.brand, theme.brand)
    }

    @Test
    fun runtimeMapIsPerPagerWithNeutralFallback() {
        val pagerId = "appearance-test-pager"
        try {
            assertEquals(1f, FontScaleRuntime.factor(pagerId)) // 未注册 → 中性
            assertEquals(1f, FontScaleRuntime.factor("never-registered"))
            FontScaleRuntime.update(pagerId, 1.3f)
            assertEquals(1.3f, FontScaleRuntime.factor(pagerId))
            FontScaleRuntime.remove(pagerId)
            assertEquals(1f, FontScaleRuntime.factor(pagerId))
        } finally {
            FontScaleRuntime.remove(pagerId)
        }
    }

    @Test
    fun fontScaleEnumCoversSettingsTiers() {
        // 设置页四档契约：id 稳定（落盘值），factor 单调递增
        val tiers = FontScale.entries
        assertEquals(listOf("small", "standard", "large", "xlarge"), tiers.map { it.id })
        assertTrue(tiers.zipWithNext().all { (a, b) -> a.factor < b.factor })
    }

    @Test
    fun scaledStandardIsIdentity() {
        val base = TypeTokens()
        assertEquals(base, base.scaled(1.0f))
        assertNotEquals(base, base.scaled(1.15f))
    }

    @Test
    fun darkDetectionReliesOnPageColorNotDataClassEquality() {
        // appIsDarkTheme 的判等契约：appTheme() 返回 type 已缩放的 copy，
        // 不能与 Dark 单例整体判等；page 底色在两套色板间必须唯一。
        val darkScaled = resolveStockChatTheme(systemNight = false, modeId = "dark", fontScaleId = "xlarge")
        assertNotEquals(StockChatTheme.Dark, darkScaled) // 整体判等在非标准档失效
        assertEquals(StockChatTheme.Dark.page, darkScaled.page) // page 色判等恒成立
        assertNotEquals(StockChatTheme.Light.page, StockChatTheme.Dark.page)
    }
}
