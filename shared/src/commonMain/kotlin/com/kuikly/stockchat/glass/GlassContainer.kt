package com.kuikly.stockchat.glass

import com.kuikly.stockchat.cards.theme.GlassMaterial
import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Blur
import com.tencent.kuikly.core.views.View

private const val KUIKLY_MAX_BLUR_RADIUS_DP = 12.5f
private const val SEMANTIC_TO_KUIKLY_BLUR_DIVISOR = 3f
// Diagnostic switch: isolates the backdrop blur from the final tinted skin.
private const val DRAW_GLASS_SURFACE_SKIN = false

/**
 * Shared, platform-neutral glass container.
 *
 * At the G3 fallback tier it uses a 0.92 solid tint, a fine highlight border,
 * and a restrained shadow. Platform adapters can later consume blurRadius to
 * provide G1/G2 rendering without forcing callers to change their UI code.
 */
fun ViewContainer<*, *>.GlassContainer(
    material: GlassMaterial,
    renderer: GlassRenderer = GlassRenderer.Default,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        GlassBackdrop(material, renderer)
        content()
    }
}

/**
 * Adds the material layers behind the current container's content.
 *
 * Kuikly maps [Blur] to Android's RenderEffect path, iOS blur material, and
 * CSS backdrop-filter on H5. [SEMANTIC_TO_KUIKLY_BLUR_DIVISOR] maps the
 * document's semantic radius to Kuikly, which caps native blur at
 * [KUIKLY_MAX_BLUR_RADIUS_DP].
 */
fun ViewContainer<*, *>.GlassBackdrop(
    material: GlassMaterial,
    renderer: GlassRenderer = GlassRenderer.Default,
    blurVisible: Boolean? = null,
) {
    val resolved = renderer.resolve(material)
    if (resolved.blurRadius > 0f) {
        Blur {
            attr {
                absolutePositionAllZero()
                val visibleRadius = if (blurVisible == false) 0f else resolved.blurRadius
                blurRadius((visibleRadius / SEMANTIC_TO_KUIKLY_BLUR_DIVISOR)
                    .coerceAtMost(KUIKLY_MAX_BLUR_RADIUS_DP))
                if (blurVisible != null) animate(Animation.easeOut(0.40f), blurVisible)
                // Visual-priority Android mode also captures independent layers.
                blurOtherLayer(renderer.mode == GlassRenderingMode.REALTIME)
                borderRadius(resolved.cornerRadius)
                touchEnable(false)
                // Fixed optical tint: surface tint carries Sheet/Peek identity.
                backgroundColor(Color(resolved.tint, resolved.backdropTintAlpha))
            }
        }
    }
    if (renderer.mode == GlassRenderingMode.REALTIME && resolved.blurRadius > 0f) {
        // A restrained specular band is what makes the material read as glass,
        // not merely as a translucent rectangle.
        val sheenStops = if (material.tint == 0xFAFAF8L) {
            arrayOf(
                ColorStop(Color(0x000000, 0.04f), 0f),
                ColorStop(Color(0x000000, 0.02f), 0.34f),
                ColorStop(Color(0x000000, 0.08f), 0.85f),
                ColorStop(Color(0x000000, 0.04f), 1f),
            )
        } else {
            arrayOf(
                ColorStop(Color(0xFFFFFF, 0.22f), 0f),
                ColorStop(Color(0xFFFFFF, 0.05f), 0.34f),
                ColorStop(Color(0xFFFFFF, 0.08f), 0.85f),
                ColorStop(Color(0xFFFFFF, 0.04f), 1f),
            )
        }
        View {
            attr {
                absolutePositionAllZero()
                touchEnable(false)
                borderRadius(resolved.cornerRadius)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM,
                    *sheenStops,
                )
            }
        }
    }
    if (DRAW_GLASS_SURFACE_SKIN) {
        View {
            attr {
                absolutePositionAllZero()
                touchEnable(false)
                applyGlassSurfaceSkin(material, renderer)
            }
        }
    }
}

/** Draws the final tinted surface, border, and shadow. It never adds blur. */
fun Attr.applyGlassSurfaceSkin(
    material: GlassMaterial,
    renderer: GlassRenderer = GlassRenderer.Default,
): Attr {
    val resolved = renderer.resolve(material)
    backgroundColor(Color(resolved.tint, resolved.tintAlpha))
    borderRadius(resolved.cornerRadius)
    if (resolved.strokeWidth > 0f) {
        border(Border(resolved.strokeWidth, BorderStyle.SOLID, Color(0xFFFFFF, resolved.strokeAlpha)))
    }
    if (resolved.elevation > 0f) {
        boxShadow(BoxShadow(0f, resolved.elevation / 2f, resolved.elevation, Color(0x000000, 0.12f)))
    }
    return this
}
