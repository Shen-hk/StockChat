package com.kuikly.stockchat.cards.theme

/**
 * The only glass materials permitted in StockChat.
 *
 * The private constructor keeps business UI from inventing one-off blur and
 * transparency values. Use [GlassPalette] from [StockChatTheme] instead.
 */
class GlassMaterial private constructor(
    val id: String,
    val blurRadius: Float,
    val tint: Long,
    val tintAlpha: Float,
    val saturationBoost: Float,
    val strokeWidth: Float,
    val strokeAlpha: Float,
    val cornerRadius: Float,
    val elevation: Float,
    val transitionOnly: Boolean = false,
) {
    companion object {
        internal fun sheet(tint: Long) = GlassMaterial(
            id = "glass.sheet",
            blurRadius = 48f,
            tint = tint,
            tintAlpha = 0.80f,
            saturationBoost = 1.25f,
            strokeWidth = 1f,
            strokeAlpha = 0.30f,
            cornerRadius = 24f,
            elevation = 8f,
        )

        internal fun peek(tint: Long) = GlassMaterial(
            id = "glass.peek",
            blurRadius = 32f,
            tint = tint,
            tintAlpha = 0.68f,
            saturationBoost = 1.20f,
            strokeWidth = 1f,
            strokeAlpha = 0.35f,
            cornerRadius = 20f,
            elevation = 6f,
        )

        internal fun cardEdge(tint: Long) = GlassMaterial(
            id = "glass.cardEdge",
            blurRadius = 0f,
            tint = tint,
            tintAlpha = 0f,
            saturationBoost = 1f,
            strokeWidth = 1f,
            strokeAlpha = 0.25f,
            cornerRadius = 12f,
            elevation = 0f,
        )

        internal fun dissolve(tint: Long) = GlassMaterial(
            id = "glass.dissolve",
            blurRadius = 32f,
            tint = tint,
            tintAlpha = 0.68f,
            saturationBoost = 1.20f,
            strokeWidth = 0f,
            strokeAlpha = 0f,
            cornerRadius = 0f,
            elevation = 0f,
            transitionOnly = true,
        )
    }
}

data class GlassPalette(
    val sheet: GlassMaterial,
    val peek: GlassMaterial,
    val cardEdge: GlassMaterial,
    val dissolve: GlassMaterial,
) {
    companion object {
        /** Warm white and warm black match the app's neutral theme tokens. */
        val Light = GlassPalette(
            sheet = GlassMaterial.sheet(0xFAFAF8),
            peek = GlassMaterial.peek(0xFAFAF8),
            cardEdge = GlassMaterial.cardEdge(0xFAFAF8),
            dissolve = GlassMaterial.dissolve(0xFAFAF8),
        )

        val Dark = GlassPalette(
            sheet = GlassMaterial.sheet(0x1D1D1F),
            peek = GlassMaterial.peek(0x1D1D1F),
            cardEdge = GlassMaterial.cardEdge(0x1D1D1F),
            dissolve = GlassMaterial.dissolve(0x1D1D1F),
        )
    }
}
