package com.kuikly.stockchat.page.components

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.View

/**
 * 详情页氛围底（doc 26 §3，对齐原型 `26-详情页走势主体重构-原型v1.html` 的 .atmo）：
 * 横向铺满、纵向渐变——`linear-gradient(180deg, toneSoft 0% → toneSoft@45% 18% → page 58%)`，
 * 58% 以下保持 page 底色，无任何圆形光晕（用户 2026-09-07 明确：光晕不要）。
 * 单 Canvas 承担（Kuikly 无渐变 attr；fillRect 用路径闭合模拟）。
 *
 * - 涨/跌：toneSoft = riseSoft/fallSoft（doc 19 token，零新增饱和色）；
 * - 平盘/停牌：调用方传中性 toneSoft（surfaceMuted），渐变自然退化为极浅灰；
 * - R1：toneSoft 以 lambda 注入，vbind 读取 observable 驱动换色重建。
 */
internal fun ViewContainer<*, *>.AtmosphereBackdrop(
    toneSoft: () -> Color,
    pageColor: Color,
    washHeight: Float,
    topOffset: Float = 0f,
    sideExtend: Float = 0f,
) {
    View {
        attr {
            absolutePosition(top = -topOffset, left = -sideExtend, right = -sideExtend)
            height(washHeight)
            touchEnable(false)
        }
        vbind({ toneSoft() }) {
            Canvas({
                attr {
                    absolutePositionAllZero()
                    height(washHeight)
                }
            }) { canvas, width, height ->
                val soft = toneSoft()
                if (height <= 0f) return@Canvas

                // 对齐原型 .atmo：linear-gradient(180deg, soft 0%, soft@0.45 18%, page 58%)
                val gradient = canvas.createLinearGradient(0f, 0f, 0f, height)
                gradient.addColorStop(0f, soft)
                gradient.addColorStop(0.18f, soft.opacity(0.45f))
                gradient.addColorStop(0.58f, pageColor)
                gradient.addColorStop(1f, pageColor)
                canvas.fillStyle(gradient)
                canvas.beginPath()
                canvas.moveTo(0f, 0f)
                canvas.lineTo(width, 0f)
                canvas.lineTo(width, height)
                canvas.lineTo(0f, height)
                canvas.closePath()
                canvas.fill()
            }
        }
    }
}
