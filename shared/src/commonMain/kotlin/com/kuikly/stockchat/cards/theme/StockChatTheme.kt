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
    val onBrand: Color,
    val rise: Color,
    val fall: Color,
    val divider: Color,
    val cardRadius: Float = 14f,
    val inputRadius: Float = 12f,
) {
    companion object {
        val Light = StockChatTheme(
            page = Color(0xFFF5F7F6),
            surface = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFEDF1EF),
            textPrimary = Color(0xFF17201C),
            textSecondary = Color(0xFF58645E),
            textTertiary = Color(0xFF87918C),
            brand = Color(0xFF176B4D),
            brandSoft = Color(0xFFDDEDE6),
            onBrand = Color(0xFFFFFFFF),
            rise = Color(0xFFD64545),
            fall = Color(0xFF16865E),
            divider = Color(0xFFDCE3DF),
        )

        val Dark = StockChatTheme(
            page = Color(0xFF111715),
            surface = Color(0xFF1A211E),
            surfaceMuted = Color(0xFF242D29),
            textPrimary = Color(0xFFF2F5F3),
            textSecondary = Color(0xFFB4BDB8),
            textTertiary = Color(0xFF7F8B85),
            brand = Color(0xFF64BA95),
            brandSoft = Color(0xFF263E34),
            onBrand = Color(0xFF102019),
            rise = Color(0xFFFF7474),
            fall = Color(0xFF55C99A),
            divider = Color(0xFF313B36),
        )
    }
}
