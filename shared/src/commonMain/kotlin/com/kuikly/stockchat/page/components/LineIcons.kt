package com.kuikly.stockchat.page.components

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import kotlin.math.PI

/**
 * Line icons for the composer (input bar).
 *
 * All icons are designed on a 24×24 grid and drawn with [Canvas] so the
 * stroke scales losslessly with `size`.  The grid transform is applied via
 * `ctx.scale(k, k)`, which also scales `lineWidth` — coordinates below are
 * therefore written in raw 24-grid units.
 */

private const val GRID = 24f
private const val STROKE = 1.8f

private fun ViewContainer<*, *>.lineIcon(
    color: Color,
    size: Float,
    draw: CanvasContext.(k: Float) -> Unit,
) {
    Canvas({
        attr { width(size); height(size) }
    }) { ctx, w, _ ->
        val k = w / GRID
        ctx.batchDraw = true
        ctx.scale(k, k)
        ctx.strokeStyle(color)
        ctx.fillStyle(color)
        ctx.lineWidth(STROKE)
        ctx.lineCapRound()
        ctx.draw(k)
    }
}

private fun CanvasContext.roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float) {
    beginPath()
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arc(x + w - r, y + r, r, (-PI / 2).toFloat(), 0f, false)
    lineTo(x + w, y + h - r)
    arc(x + w - r, y + h - r, r, 0f, (PI / 2).toFloat(), false)
    lineTo(x + r, y + h)
    arc(x + r, y + h - r, r, (PI / 2).toFloat(), PI.toFloat(), false)
    lineTo(x, y + r)
    arc(x + r, y + r, r, PI.toFloat(), (PI * 1.5).toFloat(), false)
    closePath()
}

/** ＋ : new chat / attachment. */
fun ViewContainer<*, *>.LineIconPlus(color: Color, size: Float) {
    lineIcon(color, size) {
        beginPath()
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
        stroke()
    }
}

/** Microphone: voice input. */
fun ViewContainer<*, *>.LineIconMic(color: Color, size: Float) {
    lineIcon(color, size) {
        // Capsule head (6×11, top at y=3).
        beginPath()
        moveTo(9f, 6f)
        arc(12f, 6f, 3f, PI.toFloat(), (2 * PI).toFloat(), false)
        lineTo(15f, 11f)
        arc(12f, 11f, 3f, 0f, PI.toFloat(), false)
        closePath()
        stroke()
        // Cradle arc.
        beginPath()
        arc(12f, 11.5f, 6f, PI.toFloat(), 0f, true)
        stroke()
        // Stem + base.
        beginPath()
        moveTo(12f, 17.5f)
        lineTo(12f, 21f)
        moveTo(8.5f, 21f)
        lineTo(15.5f, 21f)
        stroke()
    }
}

/** Camera: shoot a photo for the composer. */
fun ViewContainer<*, *>.LineIconCamera(color: Color, size: Float) {
    lineIcon(color, size) {
        // Body.
        roundRectPath(3f, 8f, 18f, 11f, 2.5f)
        stroke()
        // Viewfinder hump.
        beginPath()
        moveTo(9f, 8f)
        lineTo(10.2f, 5.8f)
        lineTo(13.8f, 5.8f)
        lineTo(15f, 8f)
        stroke()
        // Lens.
        beginPath()
        arc(12f, 13.5f, 3.2f, 0f, (2 * PI).toFloat(), false)
        stroke()
    }
}

/** Photo: pick from album. */
fun ViewContainer<*, *>.LineIconPhoto(color: Color, size: Float) {
    lineIcon(color, size) {
        // Frame.
        roundRectPath(3.5f, 5f, 17f, 14f, 2.5f)
        stroke()
        // Sun.
        beginPath()
        arc(8.8f, 9.5f, 1.6f, 0f, (2 * PI).toFloat(), false)
        stroke()
        // Mountains.
        beginPath()
        moveTo(4f, 18.2f)
        lineTo(9.5f, 12f)
        lineTo(13f, 15.2f)
        lineTo(16f, 12.5f)
        lineTo(20.5f, 16.8f)
        stroke()
    }
}

/** Paper plane: send message. */
fun ViewContainer<*, *>.LineIconSend(color: Color, size: Float) {
    lineIcon(color, size) {
        beginPath()
        moveTo(21.5f, 2.5f)
        lineTo(14.5f, 21.5f)
        lineTo(11f, 13f)
        lineTo(2.5f, 9.5f)
        closePath()
        stroke()
        beginPath()
        moveTo(21.5f, 2.5f)
        lineTo(11f, 13f)
        stroke()
    }
}

/** Filled rounded square: stop recording / stop generating. */
fun ViewContainer<*, *>.LineIconStop(color: Color, size: Float) {
    lineIcon(color, size) {
        roundRectPath(7.5f, 7.5f, 9f, 9f, 2f)
        fill()
    }
}

/** Filled dot: recording indicator. */
fun ViewContainer<*, *>.LineIconRecordingDot(color: Color, size: Float) {
    lineIcon(color, size) {
        beginPath()
        arc(12f, 12f, 5.5f, 0f, (2 * PI).toFloat(), false)
        fill()
    }
}
