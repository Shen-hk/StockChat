package com.kuikly.stockchat.page.components

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import kotlin.math.PI

/**
 * Tabler Icons 风格的圆角线性图标，统一用于输入栏、抽屉与二级页顶栏。
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
    GeneratedIcon8(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.8f) {
        beginPath()
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
        stroke()
    }
}

/** Three horizontal bars: side drawer trigger. */
fun ViewContainer<*, *>.LineIconMenu(color: Color, size: Float) {
    GeneratedIcon7(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.1f) {
        beginPath()
        moveTo(0.8f, 4f); lineTo(23.2f, 4f)
        moveTo(0.8f, 12f); lineTo(23.2f, 12f)
        moveTo(0.8f, 20f); lineTo(18f, 20f)
        stroke()
    }
}

/** − : compact zoom-out control. */
fun ViewContainer<*, *>.LineIconMinus(color: Color, size: Float) {
    lineIcon(color, size) {
        beginPath()
        moveTo(5f, 12f)
        lineTo(19f, 12f)
        stroke()
    }
}

/**
 * 二级页返回：圆角短箭头（chevron-left），纯线条。
 * 与右侧 [LineIconChevronRight] 对称，小尺寸下视觉重心稳。
 */
fun ViewContainer<*, *>.LineIconArrowLeft(color: Color, size: Float) {
    GeneratedIcon3(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.2f) {
        beginPath()
        moveTo(15f, 18f)
        lineTo(9f, 12f)
        lineTo(15f, 6f)
        stroke()
    }
}

/** Right-facing chevron for chart panning; kept explicit so transforms cannot mirror it. */
fun ViewContainer<*, *>.LineIconArrowRight(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2.2f) {
        beginPath()
        moveTo(9f, 18f)
        lineTo(15f, 12f)
        lineTo(9f, 6f)
        stroke()
    }
}

/** Compact reset-to-full-range glyph for chart controls. */
fun ViewContainer<*, *>.LineIconReset(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        arc(12f, 12f, 7f, 0.45f, 5.85f, false)
        stroke()
        beginPath()
        moveTo(5.2f, 7f)
        lineTo(5.2f, 12f)
        lineTo(10f, 10f)
        stroke()
    }
}

/** Three soft dots: overflow actions（点标记，非背景填充）. */
fun ViewContainer<*, *>.LineIconDots(color: Color, size: Float) {
    lineIcon(color, size) {
        listOf(6f, 12f, 18f).forEach { x ->
            beginPath()
            arc(x, 12f, 1.6f, 0f, (2f * PI).toFloat(), false)
            fill()
        }
    }
}

/** Calendar: 圆角外框 + 顶部双挂耳 + 中线，纯线条简笔画（无填充点）. */
fun ViewContainer<*, *>.LineIconCalendar(color: Color, size: Float) {
    GeneratedIcon10(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.1f) {
        beginPath(); moveTo(3.5f, 7.3f); lineTo(20.5f, 7.3f); lineTo(20.5f, 19.2f); lineTo(3.5f, 19.2f); closePath(); stroke()
        beginPath(); moveTo(7.8f, 2.5f); lineTo(7.8f, 7.3f); moveTo(16.2f, 2.5f); lineTo(16.2f, 7.3f); stroke()
        beginPath(); moveTo(6.8f, 10.5f); lineTo(13.5f, 10.5f); moveTo(6.8f, 15.3f); lineTo(20f, 15.3f); stroke()
    }
}

/** Double check (Tabler `checks`): mark every alert as read. */
fun ViewContainer<*, *>.LineIconChecks(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        moveTo(3.5f, 12f); lineTo(7.5f, 16f); lineTo(13.5f, 8f)
        moveTo(10.5f, 16f); lineTo(12.5f, 18f); lineTo(20.5f, 7f)
        stroke()
    }
}

/**
 * 自选股：圆角书签，纯线条描边。
 * 选中态由调用方以品牌色 + 软圆角药丸背景表达（见 AppChrome 顶栏动作容器），
 * 图标自身恒为描边，不再实心填充。
 */
@Suppress("UNUSED_PARAMETER")
fun ViewContainer<*, *>.LineIconBookmark(color: Color, size: Float, selected: Boolean = false) {
    GeneratedIcon4(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.2f) {
        beginPath()
        moveTo(5.2f, 21.9f); lineTo(5.2f, 4.3f); quadraticCurveTo(5.2f, 2f, 7.5f, 2f); lineTo(20.9f, 2f); quadraticCurveTo(22.8f, 2f, 22.8f, 4.3f); lineTo(22.8f, 20.9f); lineTo(12f, 17.3f); closePath(); stroke()
    }
}

/**
 * 风险地图：用三个圆润节点与连接关系表达“组合关系”，取代传统雷达扫描圈。
 * 这是供应链库常见的节点/路径思路，也更贴合本页用途。
 */
fun ViewContainer<*, *>.LineIconRadar(color: Color, size: Float) {
    GeneratedIcon5(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.2f) {
        beginPath()
        moveTo(21f, 5f); lineTo(3f, 5f); moveTo(21f, 12f); lineTo(3f, 12f); moveTo(16f, 19f); lineTo(3f, 19f); stroke()
        beginPath(); moveTo(14f, 3f); lineTo(14f, 7f); moveTo(8f, 10f); lineTo(8f, 14f); moveTo(16f, 17f); lineTo(16f, 21f); stroke()
    }
}

/** 异动预警：轮廓铃铛 + 一颗提示点，纯线条（钟体描边，仅提示点为实心点标记）. */
fun ViewContainer<*, *>.LineIconBellRinging(color: Color, size: Float) {
    GeneratedIcon1(color, size)
    return
    lineIcon(color, size, strokeWidth = 1.9f) {
        beginPath()
        moveTo(6f, 8f)
        arc(12f, 8f, 6f, PI.toFloat(), 0f, false)
        bezierCurveTo(18f, 15f, 21f, 17f, 21f, 17f)
        lineTo(3f, 17f)
        bezierCurveTo(6f, 15f, 6f, 8f, 6f, 8f)
        stroke()
        beginPath()
        moveTo(10.3f, 21f)
        quadraticCurveTo(12f, 23f, 13.7f, 21f)
        stroke()
        beginPath()
        arc(12f, 8f, 6f, PI.toFloat(), 0f, false)
        stroke()
        beginPath(); moveTo(5f, 18.5f); lineTo(19f, 18.5f); stroke()
    }
}

/**
 * 关闭叉（Lucide `x` 对齐）：附件缩略图删除、弹层关闭。
 * 小尺寸下使用，画成对角双线段。
 */
fun ViewContainer<*, *>.LineIconClose(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2.6f) {
        beginPath()
        moveTo(6.5f, 6.5f)
        lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f)
        lineTo(6.5f, 17.5f)
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
    GeneratedIcon6(color, size)
    return
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

/**
 * Trash (Lucide `trash-2` 对齐)：滑动动作「移除」。
 * 桶盖横杆 + 圆角桶身 + 提手 + 两道内部竖线。
 */
fun ViewContainer<*, *>.LineIconTrash(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // 盖杆 M3 6h18。
        beginPath()
        moveTo(3f, 6f)
        lineTo(21f, 6f)
        stroke()
        // 桶身 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6（圆角用 arc 转写）。
        beginPath()
        moveTo(19f, 6f)
        lineTo(19f, 20f)
        arc(17f, 20f, 2f, 0f, (PI / 2).toFloat(), false)
        lineTo(7f, 22f)
        arc(7f, 20f, 2f, (PI / 2).toFloat(), PI.toFloat(), false)
        lineTo(5f, 6f)
        stroke()
        // 提手 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2。
        beginPath()
        moveTo(8f, 6f)
        lineTo(8f, 4f)
        arc(10f, 4f, 2f, PI.toFloat(), (2 * PI).toFloat(), false)
        lineTo(14f, 2f)
        arc(14f, 4f, 2f, (PI * 1.5f).toFloat(), (2 * PI).toFloat(), false)
        lineTo(16f, 6f)
        stroke()
        // 内部竖线 x1=10 x2=10 y1=11 y2=17 / x=14 同。
        beginPath()
        moveTo(10f, 11f)
        lineTo(10f, 17f)
        moveTo(14f, 11f)
        lineTo(14f, 17f)
        stroke()
    }
}

/**
 * Copy（Lucide `copy` 对齐）：复制消息正文。
 * 前景圆角矩形 + 后景 sheet 的三段圆角路径。
 */
fun ViewContainer<*, *>.LineIconCopy(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // 后景 sheet：M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2。
        beginPath()
        moveTo(4f, 16f)
        arc(4f, 14f, 2f, (PI / 2).toFloat(), PI.toFloat(), false)
        lineTo(2f, 4f)
        arc(4f, 4f, 2f, PI.toFloat(), (PI * 1.5f).toFloat(), false)
        lineTo(14f, 2f)
        arc(14f, 4f, 2f, (PI * 1.5f).toFloat(), (2 * PI).toFloat(), false)
        stroke()
        // 前景：rect x=8.5 y=8.5 w=13 h=13 rx=2.5。
        beginPath()
        roundRectPath(8.5f, 8.5f, 13f, 13f, 2.5f)
        stroke()
    }
}

/** Refresh: 二级页刷新，圆弧 + 箭头，纯线条（描边与同级图标统一为 2f）. */
fun ViewContainer<*, *>.LineIconRefresh(color: Color, size: Float) {
    GeneratedIcon9(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.1f) {
        beginPath()
        arc(12f, 12f, 8.6f, (-PI * 0.18f).toFloat(), (PI * 1.62f).toFloat(), false)
        stroke()
        beginPath()
        moveTo(20.3f, 5.1f); lineTo(20.3f, 10.6f); lineTo(14.9f, 10.6f)
        stroke()
    }
}

/**
 * Share（Lucide `share` 对齐）：分享回复。
 * 上开口方盒 + 上出箭头。
 */
fun ViewContainer<*, *>.LineIconShare(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // 盒身：M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8。
        beginPath()
        moveTo(4f, 12f)
        lineTo(4f, 20f)
        arc(6f, 20f, 2f, PI.toFloat(), (PI / 2).toFloat(), true)
        lineTo(18f, 22f)
        arc(18f, 20f, 2f, (PI / 2).toFloat(), 0f, true)
        lineTo(20f, 12f)
        stroke()
        // 箭头：polyline 16 6 12 2 8 6 + line 12 2 12 15。
        beginPath()
        moveTo(16f, 6f)
        lineTo(12f, 2f)
        lineTo(8f, 6f)
        moveTo(12f, 2f)
        lineTo(12f, 15f)
        stroke()
    }
}

/**
 * Pin (Lucide `pin` 对齐)：滑动动作「置顶」。
 * 圆角头部（上宽下窄）→ 外张弧 → 底座横板 → 下方针脚。
 */
fun ViewContainer<*, *>.LineIconPin(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // 头部：两侧 x=9/15 自 y≈10.8 上收至 y=6，顶部经两个 r=2 圆角连 y=2 横边。
        beginPath()
        moveTo(9f, 10.8f)
        lineTo(9f, 6f)
        lineTo(8f, 6f)
        arc(8f, 4f, 2f, (PI / 2).toFloat(), (PI * 1.5f).toFloat(), false)
        lineTo(16f, 2f)
        arc(16f, 4f, 2f, (PI * 1.5f).toFloat(), (PI / 2).toFloat(), false)
        lineTo(15f, 6f)
        lineTo(15f, 10.8f)
        stroke()
        // 外张 + 底座：M9 10.76 经弧/线到 (5,15.24)V16，横过 (19,16)，镜像收回到 (15,10.76)。
        beginPath()
        moveTo(9f, 10.8f)
        lineTo(7.9f, 12.55f)
        lineTo(6.1f, 13.45f)
        lineTo(5f, 15.2f)
        lineTo(5f, 16f)
        lineTo(19f, 16f)
        lineTo(19f, 15.2f)
        lineTo(17.9f, 13.45f)
        lineTo(16.1f, 12.55f)
        lineTo(15f, 10.8f)
        stroke()
        // 针脚 M12 17v5。
        beginPath()
        moveTo(12f, 17f)
        lineTo(12f, 22f)
        stroke()
    }
}

/** Search (Lucide `search`): magnifier. */
fun ViewContainer<*, *>.LineIconSearch(color: Color, size: Float) {
    GeneratedIcon0(color, size)
    return
    lineIcon(color, size, strokeWidth = 2.1f) {
        beginPath()
        arc(10.6f, 10.6f, 7.1f, 0f, (PI * 2).toFloat(), false)
        stroke()
        beginPath()
        moveTo(20.8f, 20.8f); lineTo(15.7f, 15.7f)
        stroke()
    }
}

/** Star (Lucide `star`): watchlist / favorites. */
fun ViewContainer<*, *>.LineIconStar(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        moveTo(12f, 2.5f)
        lineTo(14.85f, 8.28f)
        lineTo(21.22f, 9.2f)
        lineTo(16.61f, 13.69f)
        lineTo(17.7f, 20.04f)
        lineTo(12f, 17.05f)
        lineTo(6.3f, 20.04f)
        lineTo(7.39f, 13.69f)
        lineTo(2.78f, 9.2f)
        lineTo(9.15f, 8.28f)
        closePath()
        stroke()
    }
}

/** Bell (Lucide `bell`): alerts. */
fun ViewContainer<*, *>.LineIconBell(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        // 钟体：M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9。
        beginPath()
        moveTo(6f, 8f)
        arc(12f, 8f, 6f, PI.toFloat(), 0f, false)
        bezierCurveTo(18f, 15f, 21f, 17f, 21f, 17f)
        lineTo(3f, 17f)
        bezierCurveTo(6f, 15f, 6f, 8f, 6f, 8f)
        stroke()
        // 钟摆：M10.3 21a1.94 1.94 0 0 0 3.4 0。
        beginPath()
        moveTo(10.3f, 21f)
        quadraticCurveTo(12f, 23f, 13.7f, 21f)
        stroke()
    }
}

/** Bar chart (Lucide `chart-column`): market overview. */
fun ViewContainer<*, *>.LineIconBarChart(color: Color, size: Float) {
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        moveTo(3f, 3f)
        lineTo(3f, 19f)
        arc(5f, 19f, 2f, PI.toFloat(), (PI / 2).toFloat(), true)
        lineTo(21f, 21f)
        stroke()
        beginPath()
        moveTo(7f, 16f)
        lineTo(7f, 13f)
        moveTo(11f, 16f)
        lineTo(11f, 9f)
        moveTo(15f, 16f)
        lineTo(15f, 11f)
        moveTo(19f, 16f)
        lineTo(19f, 7f)
        stroke()
    }
}

/** Sliders horizontal (Lucide `sliders-horizontal`): settings. */
fun ViewContainer<*, *>.LineIconSliders(color: Color, size: Float) {
    GeneratedIcon2(color, size)
    return
    lineIcon(color, size, strokeWidth = 2f) {
        beginPath()
        // 横线三段。
        moveTo(21f, 5f); lineTo(3f, 5f)
        moveTo(21f, 12f); lineTo(3f, 12f)
        moveTo(21f, 19f); lineTo(3f, 19f)
        stroke()
        beginPath()
        arc(11f, 5f, 2f, 0f, (2f * PI).toFloat(), false)
        arc(8f, 12f, 2f, 0f, (2f * PI).toFloat(), false)
        arc(16f, 19f, 2f, 0f, (2f * PI).toFloat(), false)
        stroke()
    }
}
