package com.kuikly.stockchat

import com.kuikly.stockchat.risk.domain.TimeBrushLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 时间刷映射单测（doc 32 §6.1：TimeBrushLayoutTest 6 例口径）。
 * 事件日刻度均匀线性、PAD=26 绘制与命中共用。
 */
class TimeBrushLayoutTest {
    @Test
    fun ticksAreEmptyForNoEvents() {
        assertTrue(TimeBrushLayout.tickXs(0, 360f).isEmpty())
        assertEquals(-1, TimeBrushLayout.nearestIndexForFraction(0.5f, 0))
        assertEquals(0f, TimeBrushLayout.fractionForIndex(0, 0))
    }

    @Test
    fun singleEventTickCenters() {
        val xs = TimeBrushLayout.tickXs(1, 360f)
        assertEquals(1, xs.size)
        assertEquals(180f, xs[0])
        // 单事件下任何 fraction 都命中 index 0。
        assertEquals(0, TimeBrushLayout.nearestIndexForFraction(0f, 1))
        assertEquals(0, TimeBrushLayout.nearestIndexForFraction(1f, 1))
        assertEquals(0.5f, TimeBrushLayout.fractionForIndex(0, 1))
    }

    @Test
    fun ticksSpanTrackEvenlyWithPad() {
        val xs = TimeBrushLayout.tickXs(5, 360f)
        assertEquals(5, xs.size)
        assertEquals(TimeBrushLayout.PAD, xs.first())
        assertEquals(360f - TimeBrushLayout.PAD, xs.last())
        // 均匀：相邻间距相等。
        val step = xs[1] - xs[0]
        xs.zipWithNext().forEach { (a, b) -> assertEquals(step, b - a, 0.01f) }
    }

    @Test
    fun nearestIndexForFractionRoundsAndClamps() {
        assertEquals(0, TimeBrushLayout.nearestIndexForFraction(0f, 5))
        assertEquals(4, TimeBrushLayout.nearestIndexForFraction(1f, 5))
        // 4 事件 3 段：0.4 → 段位 1.2 → 最近 index 1。
        assertEquals(1, TimeBrushLayout.nearestIndexForFraction(0.4f, 4))
        assertEquals(3, TimeBrushLayout.nearestIndexForFraction(0.9f, 4))
    }

    @Test
    fun fractionForIndexIsInverseOfNearest() {
        for (n in 2..6) {
            for (i in 0 until n) {
                val f = TimeBrushLayout.fractionForIndex(i, n)
                assertEquals(i, TimeBrushLayout.nearestIndexForFraction(f, n), "n=$n i=$i")
            }
        }
    }

    @Test
    fun tapHitRequiresSlopAndSharesGeometryWithDraw() {
        val xs = TimeBrushLayout.tickXs(3, 360f)
        // 精确命中刻度。
        assertEquals(0, TimeBrushLayout.nearestIndexForX(xs[0], 3, 360f))
        assertEquals(2, TimeBrushLayout.nearestIndexForX(xs[2], 3, 360f))
        // 刻度旁 TAP_SLOP 内命中。
        assertEquals(1, TimeBrushLayout.nearestIndexForX(xs[1] + TimeBrushLayout.TAP_SLOP_DP - 1f, 3, 360f))
        // 超出 slop 不命中。
        assertEquals(-1, TimeBrushLayout.nearestIndexForX(xs[1] + TimeBrushLayout.TAP_SLOP_DP + 1f, 3, 360f))
        // knob 绘制 x 与 fraction 映射同用一份 PAD 几何：fraction 0 → 轨道最左 PAD。
        assertEquals(TimeBrushLayout.PAD, TimeBrushLayout.xForFraction(0f, 360f))
        assertEquals(360f - TimeBrushLayout.PAD, TimeBrushLayout.xForFraction(1f, 360f))
    }
}
