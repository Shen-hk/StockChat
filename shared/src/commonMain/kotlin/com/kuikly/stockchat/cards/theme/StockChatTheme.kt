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
    val divider: Color,
    val glass: GlassPalette = GlassPalette.Light,
    val cardRadius: Float = 14f,
    val inputRadius: Float = 20f,
) {
    companion object {
        val Light = StockChatTheme(
            page = Color(0xFFFAFAF8),
            surface = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFF1F1EF),
            textPrimary = Color(0xFF1D1D1F),
            textSecondary = Color(0xFF6E6E73),
            textTertiary = Color(0xFFAEAEB2),
            brand = Color(0xFF2563EB),
            brandSoft = Color(0xFFEFF6FF),
            term = Color(0xFF7C3AED),
            riseSoft = Color(0xFFFFF5F5),
            fallSoft = Color(0xFFF0FCF6),
            onBrand = Color(0xFFFFFFFF),
            rise = Color(0xFFE03131),
            fall = Color(0xFF0CA678),
            divider = Color(0xFFE8E8E6),
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
            divider = Color(0xFF363638),
            glass = GlassPalette.Dark,
        )
    }
}
