package com.kuikly.stockchat.glass

import com.kuikly.stockchat.cards.theme.GlassMaterial
import kotlin.math.max

/**
 * Current visual quality selected by the platform renderer.
 *
 * Native hosts select a reduced mode when accessibility or device capability
 * requires it; the cross-platform default uses Kuikly's blur adapter.
 */
enum class GlassRenderingMode {
    REALTIME,
    SNAPSHOT,
    SIMPLIFIED,
}

data class ResolvedGlassMaterial(
    val id: String,
    val blurRadius: Float,
    val tint: Long,
    /** Lightweight tint inside the blurred capture; intentionally independent of surface tint. */
    val backdropTintAlpha: Float,
    val tintAlpha: Float,
    val strokeWidth: Float,
    val strokeAlpha: Float,
    val cornerRadius: Float,
    val elevation: Float,
)

class GlassRenderer(val mode: GlassRenderingMode = GlassRenderingMode.SIMPLIFIED) {
    fun resolve(material: GlassMaterial): ResolvedGlassMaterial = when (mode) {
        GlassRenderingMode.REALTIME -> ResolvedGlassMaterial(
            id = material.id,
            blurRadius = material.blurRadius,
            tint = material.tint,
            backdropTintAlpha = GlassTuning.REALTIME_BACKDROP_TINT_ALPHA,
            // The material token is the design-system value. A real blur layer
            // already carries luminance, so applying its full tint would turn it
            // into an opaque panel instead of glass.
            tintAlpha = material.tintAlpha * GlassTuning.REALTIME_SURFACE_TINT_FACTOR,
            strokeWidth = material.strokeWidth,
            strokeAlpha = if (material.blurRadius > 0f) max(material.strokeAlpha, 0.50f) else material.strokeAlpha,
            cornerRadius = material.cornerRadius,
            elevation = material.elevation,
        )

        GlassRenderingMode.SNAPSHOT -> ResolvedGlassMaterial(
            id = material.id,
            // Kuikly has no public snapshot recapture API. Use a live low-radius
            // blur instead, so scrolling never reveals an out-of-date capture.
            blurRadius = material.blurRadius * GlassTuning.SNAPSHOT_BLUR_FACTOR,
            tint = material.tint,
            backdropTintAlpha = GlassTuning.SNAPSHOT_BACKDROP_TINT_ALPHA,
            tintAlpha = material.tintAlpha * GlassTuning.SNAPSHOT_SURFACE_TINT_FACTOR,
            strokeWidth = material.strokeWidth,
            strokeAlpha = material.strokeAlpha,
            cornerRadius = material.cornerRadius,
            elevation = material.elevation,
        )

        GlassRenderingMode.SIMPLIFIED -> ResolvedGlassMaterial(
            id = material.id,
            blurRadius = 0f,
            tint = material.tint,
            backdropTintAlpha = 0f,
            tintAlpha = if (material.tintAlpha == 0f) 0f else max(material.tintAlpha, 0.92f),
            strokeWidth = material.strokeWidth,
            strokeAlpha = material.strokeAlpha,
            cornerRadius = material.cornerRadius,
            elevation = material.elevation,
        )
    }

    fun statusLabel(): String = when (mode) {
        GlassRenderingMode.REALTIME -> "视觉·实时"
        GlassRenderingMode.SNAPSHOT -> "视觉·快照"
        GlassRenderingMode.SIMPLIFIED -> "视觉·简化"
    }

    companion object {
        /** The native Kuikly Blur view provides this path on Android, iOS and H5. */
        val Default = GlassRenderer(GlassRenderingMode.REALTIME)

        fun fromHostMode(value: String?): GlassRenderer = GlassRenderer(
            when (value?.lowercase()) {
                "realtime" -> GlassRenderingMode.REALTIME
                "snapshot" -> GlassRenderingMode.SNAPSHOT
                else -> GlassRenderingMode.SIMPLIFIED
            },
        )
    }
}

/** Optical constants, separated from design-system material tokens. */
internal object GlassTuning {
    const val REALTIME_SURFACE_TINT_FACTOR = 0.36f
    const val SNAPSHOT_SURFACE_TINT_FACTOR = 0.62f
    const val REALTIME_BACKDROP_TINT_ALPHA = 0.05f
    const val SNAPSHOT_BACKDROP_TINT_ALPHA = 0.08f
    const val SNAPSHOT_BLUR_FACTOR = 0.50f
}
