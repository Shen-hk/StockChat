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
    strokeWidth: Float = STROKE,
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
        ctx.lineWidth(strokeWidth)
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

/**
 * Microphone with in-tube volume fill（WorkBuddy 真身，规范 §5.1 ①）：
 * 话筒管作为 clip 区域，一块矩形随音量从管底向上充填。
 * [fill01] 在 Canvas draw 闭包内被读取——传 lambda 读 observable 即可驱动 60ms 重绘
 * （CanvasView.draw 被 ReactiveObserver 包裹，读到的 observable 变化自动重画）。
 */
fun ViewContainer<*, *>.LineIconMicWithFill(
    color: Color,
    fillColor: Color,
    size: Float,
    fill01: () -> Float,
) {
    Canvas({
        attr { width(size); height(size) }
    }) { ctx, w, _ ->
        val k = w / GRID
        ctx.batchDraw = true
        ctx.scale(k, k)
        ctx.lineWidth(STROKE)
        ctx.lineCapRound()

        // 管内充填：管形（描边内缩 1）做 clip，管底 y=13 向上充填，高度 1.5→9。
        val f = fill01().coerceIn(0f, 1f)
        if (f > 0.01f) {
            ctx.save()
            ctx.beginPath()
            ctx.moveTo(10f, 6f)
            ctx.arc(12f, 6f, 2f, PI.toFloat(), (2 * PI).toFloat(), false)
            ctx.lineTo(14f, 11f)
            ctx.arc(12f, 11f, 2f, 0f, PI.toFloat(), false)
            ctx.closePath()
            ctx.clip(true)
            val h = 1.5f + f * 7.5f
            ctx.fillStyle(fillColor)
            ctx.beginPath()
            ctx.moveTo(9f, 13f - h)
            ctx.lineTo(15f, 13f - h)
            ctx.lineTo(15f, 14f)
            ctx.lineTo(9f, 14f)
            ctx.closePath()
            ctx.fill()
            ctx.restore()
        }

        // 描边（与 LineIconMic 同形）。
        ctx.strokeStyle(color)
        ctx.beginPath()
        ctx.moveTo(9f, 6f)
        ctx.arc(12f, 6f, 3f, PI.toFloat(), (2 * PI).toFloat(), false)
        ctx.lineTo(15f, 11f)
        ctx.arc(12f, 11f, 3f, 0f, PI.toFloat(), false)
        ctx.closePath()
        ctx.stroke()
        ctx.beginPath()
        ctx.arc(12f, 11.5f, 6f, PI.toFloat(), 0f, true)
        ctx.stroke()
        ctx.beginPath()
        ctx.moveTo(12f, 17.5f)
        ctx.lineTo(12f, 21f)
        ctx.moveTo(8.5f, 21f)
        ctx.lineTo(15.5f, 21f)
        ctx.stroke()
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

/**
 * Soundwave（Lucide `audio-lines` 对齐）：语音输入入口。
 * 5 根圆头竖线、中轴对称的波形高度。
 */
fun ViewContainer<*, *>.LineIconAudioLines(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        // x=4, 8, 12, 16, 20；高度 6 / 12 / 18 / 10 / 4。
        moveTo(4f, 9f)
        lineTo(4f, 15f)
        moveTo(8f, 6f)
        lineTo(8f, 18f)
        moveTo(12f, 3f)
        lineTo(12f, 21f)
        moveTo(16f, 7f)
        lineTo(16f, 17f)
        moveTo(20f, 10f)
        lineTo(20f, 14f)
        stroke()
    }
}

/**
 * 键盘（Lucide `keyboard` 对齐）：语音模式下"返回文字输入"的切换图标。
 * 圆角外框 + 三行按键点 + 底部空格条。
 */
fun ViewContainer<*, *>.LineIconKeyboard(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // 外框：x=2 y=4 w=20 h=16 rx=2。
        roundRectPath(2f, 4f, 20f, 16f, 2f)
        stroke()
        // 按键点（圆头短段渲染为圆点）：第一行 y=8，第二行 y=12。
        beginPath()
        for (x in listOf(6f, 10f, 14f, 18f)) {
            moveTo(x - 0.2f, 8f)
            lineTo(x + 0.2f, 8f)
        }
        for (x in listOf(8f, 12f, 16f)) {
            moveTo(x - 0.2f, 12f)
            lineTo(x + 0.2f, 12f)
        }
        stroke()
        // 空格条。
        beginPath()
        moveTo(7f, 16f)
        lineTo(17f, 16f)
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

/** Trend down (Lucide `trending-down`): market movement explanation. */
fun ViewContainer<*, *>.LineIconTrendDown(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        moveTo(22f, 17f)
        lineTo(13.5f, 8.5f)
        lineTo(8.5f, 13.5f)
        lineTo(2f, 7f)
        stroke()
        beginPath()
        moveTo(16f, 17f)
        lineTo(22f, 17f)
        lineTo(22f, 11f)
        stroke()
    }
}

/** Trend up (Lucide `trending-up`): mirrored from [LineIconTrendDown] along the horizontal axis. */
fun ViewContainer<*, *>.LineIconTrendUp(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        moveTo(22f, 7f)
        lineTo(13.5f, 15.5f)
        lineTo(8.5f, 10.5f)
        lineTo(2f, 17f)
        stroke()
        beginPath()
        moveTo(16f, 7f)
        lineTo(22f, 7f)
        lineTo(22f, 13f)
        stroke()
    }
}

/** Open book (Lucide `book-open`): terminology learning. */
fun ViewContainer<*, *>.LineIconBook(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // Left page.
        beginPath()
        moveTo(2f, 3f)
        lineTo(8f, 3f)
        arc(8f, 7f, 4f, (-PI / 2).toFloat(), 0f, false)
        lineTo(12f, 21f)
        arc(9f, 21f, 3f, 0f, (-PI / 2).toFloat(), true)
        lineTo(2f, 18f)
        closePath()
        stroke()
        // Right page (mirror of the left one).
        beginPath()
        moveTo(22f, 3f)
        lineTo(16f, 3f)
        arc(16f, 7f, 4f, (-PI / 2).toFloat(), PI.toFloat(), true)
        lineTo(12f, 21f)
        arc(15f, 21f, 3f, PI.toFloat(), (PI * 1.5).toFloat(), false)
        lineTo(22f, 18f)
        closePath()
        stroke()
    }
}

/** File text (Lucide `file-text`): financial report reading. */
fun ViewContainer<*, *>.LineIconFileText(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // Body: rounded 2 corners, folded corner closed with a diagonal edge.
        beginPath()
        moveTo(15f, 2f)
        lineTo(8f, 2f)
        arc(8f, 4f, 2f, (-PI / 2).toFloat(), PI.toFloat(), true)
        lineTo(6f, 20f)
        arc(8f, 20f, 2f, PI.toFloat(), (PI / 2).toFloat(), true)
        lineTo(18f, 22f)
        arc(18f, 20f, 2f, (PI / 2).toFloat(), 0f, true)
        lineTo(20f, 7f)
        closePath()
        stroke()
        // Fold.
        beginPath()
        moveTo(14f, 2f)
        lineTo(14f, 6f)
        arc(16f, 6f, 2f, PI.toFloat(), (PI / 2).toFloat(), true)
        lineTo(20f, 8f)
        stroke()
        // Text lines.
        beginPath()
        moveTo(16f, 13f)
        lineTo(8f, 13f)
        moveTo(16f, 17f)
        lineTo(8f, 17f)
        moveTo(10f, 9f)
        lineTo(8f, 9f)
        stroke()
    }
}

/** Columns 2 (Lucide `columns-2`): factual side-by-side comparison. */
fun ViewContainer<*, *>.LineIconColumns(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        roundRectPath(3f, 3f, 18f, 18f, 2f)
        stroke()
        beginPath()
        moveTo(12f, 3f)
        lineTo(12f, 21f)
        stroke()
    }
}

/** Shield check: trust and source traceability. */
fun ViewContainer<*, *>.LineIconShieldCheck(color: Color, size: Float) {
    lineIcon(color, size) {
        beginPath()
        moveTo(12f, 3.5f)
        lineTo(18.5f, 6f)
        lineTo(18f, 12.5f)
        arc(12f, 18f, 6f, 0.35f, (PI - 0.35f).toFloat(), false)
        lineTo(5.5f, 6f)
        closePath()
        stroke()
        beginPath()
        moveTo(8.5f, 12.2f)
        lineTo(11f, 14.6f)
        lineTo(15.7f, 9.8f)
        stroke()
    }
}

/** Chevron right: secondary disclosure. */
fun ViewContainer<*, *>.LineIconChevronRight(color: Color, size: Float) {
    lineIcon(color, size) {
        beginPath()
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
        stroke()
    }
}

/** Chevron up: back-to-top. Geometry aligned with Lucide `chevron-up` (m18 15-6-6-6 6). */
fun ViewContainer<*, *>.LineIconChevronUp(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        moveTo(18f, 15f)
        lineTo(12f, 9f)
        lineTo(6f, 15f)
        stroke()
    }
}
