package com.kuikly.stockchat

import android.view.MotionEvent
import kotlin.math.abs

/**
 * 纯观测型手势侦察：识别「大且快」的右向横滑，作为抽屉展开的全页手势入口。
 *
 * 背景：Kuikly Android 渲染层（2.25.0）不消费 capture prop，且列表气泡/卡片
 * 等可触摸子 View 会吞掉整条触摸流——纯 Kuikly 层无法在主页面任意位置识别
 * 横滑。因此侦察放在 Activity.dispatchTouchEvent：只读 MotionEvent、永不消费，
 * 完全不影响 Kuikly 视图层自己的触摸分发（滚动/点击/长按/拖拽均不受干扰）。
 *
 * 命中条件（2026-09-08 定为「大且快」横滑防误触；2026-09-13 起手区放宽到全屏）：
 *  1. 起手位置不限（用户决策 2026-09-13：从 60% 扩大到 100% 全屏，屏幕
 *     任意位置右滑均可触发；历史 40%→60%→100%。方向/距离/速度门限保留）；
 *  2. 横向净位移 ≥ 屏宽 24%；
 *  3. 总时长 ≤ 420ms；
 *  4. 平均速度 ≥ 900dp/s；
 *  5. 纵向位移 ≤ 横向的 45%（明显是横滑而非列表滚动）。
 * 任一阶段方向不对（向左回退/纵向主导/多指）即放弃跟踪。
 *
 * 命中后通过 host 回调通知 Kuikly 页面，是否真开抽屉由页面守卫决定
 * （抽屉/卡片弹层已开、页面不可见等场景忽略）。
 */
internal class DrawerFlingDetector(
    private val onFling: () -> Unit,
) {
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var tracking = false

    fun onTouchEvent(ev: MotionEvent, screenWidthPx: Int, density: Float) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downAt = ev.eventTime
                tracking = screenWidthPx > 0 && ev.x <= screenWidthPx * START_ZONE_FRACTION
            }

            // 多指（捏合/第二根手指按下）直接放弃。
            MotionEvent.ACTION_POINTER_DOWN -> tracking = false

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dy) > abs(dx) && abs(dy) > 48f * density) {
                    // 纵向主导 = 列表滚动，交给页面自己处理。
                    tracking = false
                    return
                }
                if (dx < -16f * density) {
                    // 向左回退超过 slop = 方向不对。
                    tracking = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return
                tracking = false
                if (ev.actionMasked == MotionEvent.ACTION_CANCEL) return
                val dx = ev.x - downX
                val dy = ev.y - downY
                val dt = (ev.eventTime - downAt).coerceAtLeast(1)
                val minDistPx = screenWidthPx * MIN_DISTANCE_FRACTION
                val velocityDps = (dx / dt) * 1000f / density
                if (
                    dx >= minDistPx &&
                    dt <= MAX_DURATION_MS &&
                    abs(dy) <= 0.45f * dx &&
                    velocityDps >= MIN_VELOCITY_DPS
                ) {
                    onFling()
                }
            }
        }
    }

    private companion object {
        // 起手区占屏宽比例（用户决策 2026-09-13：全屏 100%；历史 40%→60%→100%）。
        const val START_ZONE_FRACTION = 1.0f
        const val MIN_DISTANCE_FRACTION = 0.24f
        const val MAX_DURATION_MS = 420L
        const val MIN_VELOCITY_DPS = 900f
    }
}
