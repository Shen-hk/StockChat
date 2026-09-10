package com.kuikly.stockchat.page.components

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.View

/**
 * 详情页氛围底（doc 26 §3，对齐原型 `26-详情页走势主体重构-原型v1.html` 的 .atmo）：
 * 固定整屏层（原型 position:absolute; inset:0、pointer-events:none，不随内容滚动）。
 * 调用方必须把它放在 Scroller **之前**（body 根容器首个子项），让它垫在滚动内容之下。
 * 渐变对齐原型：`linear-gradient(180deg, toneSoft 0% → toneSoft@45% 18% → page 58%)`，
 * 58% 以下保持 page 底色；渐变比例按 Canvas 实测屏高计算，与 HTML 的 58% 逐点对齐。
 * 无任何圆形光晕（用户 2026-09-07 明确：光晕不要）。
 *
 * 历史坑（2026-09-07 修复"氛围底失效"）：旧实现把本层挂进 Scroller 内容、用
 * absolutePosition 负 top/负 left 向外撑。受 Scroller 子项水平 padding 双倍扣除
 * （见 MEMORY.md）与绝对定位锚点不确定性影响，满色段被推到屏外；帧尺寸一旦塌成
 * 0，Canvas draw 早退，整层永远画不出来。挪到 Scroller 之下后负偏移 hack 全部
 * 移除，渐变 0% 即屏幕顶端，视觉与原型一致。
 *
 * - 涨/跌：toneSoft = riseSoft/fallSoft（doc 19 token，零新增饱和色）；
 * - 平盘/停牌：调用方传中性 toneSoft（surfaceMuted），渐变自然退化为极浅灰；
 * - R1：toneSoft 在 draw 闭包内读取建立响应式依赖（与 DetailTimelineChart 同款
 *   Canvas 重绘机制），行情 tick 驱动换色，无需 vbind 重建视图。
 */
internal fun ViewContainer<*, *>.AtmosphereBackdrop(
    toneSoft: () -> Color,
    pageColor: () -> Color,
) {
    View {
        attr {
            absolutePositionAllZero()
            touchEnable(false)
        }
        Canvas({
            attr {
                absolutePositionAllZero()
                touchEnable(false)
            }
        }) { canvas, width, height ->
            val soft = toneSoft()
            val page = pageColor()
            if (height <= 0f || width <= 0f) return@Canvas

            // 对齐原型 .atmo：linear-gradient(180deg, soft 0%, soft@0.45 18%, page 58%)
            val gradient = canvas.createLinearGradient(0f, 0f, 0f, height)
            gradient.addColorStop(0f, soft)
            gradient.addColorStop(0.18f, soft.opacity(0.45f))
            gradient.addColorStop(0.58f, page)
            gradient.addColorStop(1f, page)
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
