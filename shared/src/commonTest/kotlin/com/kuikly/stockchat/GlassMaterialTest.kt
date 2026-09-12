package com.kuikly.stockchat

import com.kuikly.stockchat.cards.theme.GlassPalette
import com.kuikly.stockchat.foundation.design.GlassRenderer
import com.kuikly.stockchat.foundation.design.GlassRenderingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassMaterialTest {
    @Test
    fun paletteExposesOnlyTheFourApprovedMaterials() {
        val palette = GlassPalette.Light

        assertEquals(
            listOf("glass.sheet", "glass.peek", "glass.cardEdge", "glass.dissolve"),
            listOf(palette.sheet.id, palette.peek.id, palette.cardEdge.id, palette.dissolve.id),
        )
        assertEquals(48f, palette.sheet.blurRadius)
        assertEquals(32f, palette.peek.blurRadius)
        assertEquals(0f, palette.cardEdge.blurRadius)
        assertEquals(true, palette.dissolve.transitionOnly)
    }

    @Test
    fun simplifiedModeKeepsContentLayersOpaqueButLeavesCardEdgesTransparent() {
        val renderer = GlassRenderer(GlassRenderingMode.SIMPLIFIED)

        assertEquals(0.92f, renderer.resolve(GlassPalette.Light.sheet).tintAlpha)
        assertEquals(0.92f, renderer.resolve(GlassPalette.Light.peek).tintAlpha)
        assertEquals(0f, renderer.resolve(GlassPalette.Light.cardEdge).tintAlpha)
        assertEquals("视觉·简化", renderer.statusLabel())
    }

    @Test
    fun realtimeModePreservesAVisibleBlurredBackground() {
        val renderer = GlassRenderer(GlassRenderingMode.REALTIME)

        // sheet token 0.80→0.60（「更透亮」，GlassTokens 同步调整）：0.60 × 0.36 = 0.216
        assertTrue(renderer.resolve(GlassPalette.Light.sheet).tintAlpha in 0.21f..0.22f)
        assertTrue(renderer.resolve(GlassPalette.Light.peek).tintAlpha in 0.24f..0.25f)
        assertEquals(0.05f, renderer.resolve(GlassPalette.Light.sheet).backdropTintAlpha)
        assertEquals(0.05f, renderer.resolve(GlassPalette.Light.peek).backdropTintAlpha)
    }

    @Test
    fun snapshotUsesLiveLowRadiusBlurInsteadOfAStaleCapture() {
        val snapshot = GlassRenderer(GlassRenderingMode.SNAPSHOT).resolve(GlassPalette.Light.sheet)

        assertEquals(24f, snapshot.blurRadius)
        assertEquals(0.08f, snapshot.backdropTintAlpha)
    }
}
