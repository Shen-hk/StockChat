package com.kuikly.stockchat.cards.theme

import com.tencent.kuikly.core.base.Color

data class StockChatTheme(
    val page: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val brand: Color,
    val brandSoft: Color,
    val term: Color,
    val riseSoft: Color,
    val fallSoft: Color,
    val onBrand: Color,
    val rise: Color,
    val fall: Color,
    val flat: Color,
    val divider: Color,
    /** 知识库「见过」态明度色（doc 24 §6.3 四段状态条；零新增饱和色，中性阶梯）。 */
    val glossarySeen: Color,
    /** Market-only glass layer; distinct from the app chrome glass palette. */
    val marketGlass: Color,
    val marketGlassEdge: Color,
    val glass: GlassPalette = GlassPalette.Light,
    val cardRadius: Float = 14f,
    val inputRadius: Float = 20f,
    val spacing: SpacingTokens = SpacingTokens(),
    val type: TypeTokens = TypeTokens(),
) {
    companion object {
        val Light = StockChatTheme(
            // 2026-09-11 用户反馈：#F5F6F8 灰得发闷，调轻一档到 #F8F9FB——
            // 仍保留极浅冷灰以衬白卡浮起（纯白画布需给所有白卡加描边/阴影，否决）。
            page = Color(0xFFF8F9FB),
            surface = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFEEF1F5),
            textPrimary = Color(0xFF1A1D23),
            textSecondary = Color(0xFF5B6573),
            textTertiary = Color(0xFF5F6B82),
            brand = Color(0xFF1E5BD6),
            brandSoft = Color(0xFFEAF1FD),
            term = Color(0xFF5F6B82),
            riseSoft = Color(0xFFFCEDED),
            fallSoft = Color(0xFFE9F4EE),
            onBrand = Color(0xFFFFFFFF),
            rise = Color(0xFFD92E2E),
            fall = Color(0xFF0C7A45),
            flat = Color(0xFF8A93A3),
            divider = Color(0xFFE2E8F0),
            glossarySeen = Color(0xFFCBD5E1),
            marketGlass = Color(0xFFFFFFFF, 0.82f),
            marketGlassEdge = Color(0xFFFFFFFF, 0.72f),
            glass = GlassPalette.Light,
        )

        val Dark = StockChatTheme(
            page = Color(0xFF141415),
            surface = Color(0xFF1D1D1F),
            surfaceMuted = Color(0xFF2C2C2E),
            textPrimary = Color(0xFFF5F5F7),
            textSecondary = Color(0xFFC7C7CC),
            textTertiary = Color(0xFF8E8E93),
            brand = Color(0xFF82A8FF),
            brandSoft = Color(0xFF1E3158),
            term = Color(0xFFC4B5FD),
            riseSoft = Color(0xFF3A2022),
            fallSoft = Color(0xFF15342C),
            onBrand = Color(0xFF101827),
            rise = Color(0xFFFF7878),
            fall = Color(0xFF54D6A6),
            flat = Color(0xFF7A7A80),
            divider = Color(0xFF363638),
            glossarySeen = Color(0xFF3E4654),
            marketGlass = Color(0xFF1D1D1F, 0.76f),
            marketGlassEdge = Color(0xFFFFFFFF, 0.08f),
            glass = GlassPalette.Dark,
        )
    }
}

data class SpacingTokens(
    val xs: Float = 4f,
    val sm: Float = 8f,
    val md: Float = 12f,
    val lg: Float = 16f,
    val xl: Float = 20f,
    val x2: Float = 24f,
    val x3: Float = 28f,
    val x4: Float = 32f,
    val x11: Float = 60f,
)

data class TypeTokens(
    val display: Float = 40f,
    val h1: Float = 20f,
    val title: Float = 16f,
    val body: Float = 14f,
    val sm: Float = 13f,
    val label: Float = 11f,
    val meta: Float = 10f,
)
