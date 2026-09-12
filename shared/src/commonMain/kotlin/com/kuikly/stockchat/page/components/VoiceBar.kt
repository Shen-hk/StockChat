package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.foundation.ui.fontSizeScaled

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.PI

/**
 * 语音态输入区：滚动声波 + 手势提示语 / 转写占位。
 *
 * 2026-09-06 豆包式改版：录音只发生在折叠栏 48 高的中段按钮里，本组件为
 * absolutePosition 全覆盖覆盖层（与"按住说话"占位视图同域），波形/提示
 * 排成一行垂直居中。声波为滚动波形：录音开始时整条为空白，新采样从右缘
 * 进入、向左生长（节奏由桥接层上报频率决定，安卓侧 ~35ms/格，页侧
 * updateVoiceAmplitude 负责），绘制右对齐、静音样本不画——即"声波从
 * 后面长出来并从右向左走过"；旧样本（左缘）alpha 淡出。不做任何光晕、
 * 不显示计时。
 *
 * 纯渲染组件，全部状态经 lambda 注入；在 Canvas draw 闭包内读取
 * observable（amps / cancelArmed），由 ReactiveObserver 驱动重绘。条高
 * 已在页侧完成 min(1, rms×2.5) 增益与非对称平滑（升 0.6 / 落 0.25），
 * 静音样本为 0。
 *
 * 不得把 amps 挂到 vbind key——高频驱动每帧整树重建，Canvas 还没来得及
 * 渲染就被下一帧重建销毁，波形全空白（铁律：高频驱动不得挂 vbind key）。
 * 正确做法是在 draw 闭包内直接读 observable，让响应式系统驱动 Canvas
 * 重绘（Android 端 draw 闭包注册响应式依赖，原始版本即此模式）。
 */
fun ViewContainer<*, *>.VoiceBar(
    theme: StockChatTheme,
    transcribing: () -> Boolean,
    cancelArmed: () -> Boolean,
    amps: () -> FloatArray,
) {
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(12f)
            // 纯渲染组件：放行触摸，让按住/上滑手势落到下层"按住说话"覆盖层，
            // 否则录音中 touchUp 被波形区截断，松手事件丢失。
            touchEnable(false)
        }
        vif({ !transcribing() }) {
            Canvas({
                attr {
                    flex(1f)
                    height(26f)
                    marginRight(8f)
                }
            }) { ctx, w, h ->
                // 在 draw 闭包内直接读 observable（amps/cancelArmed），响应式
                // 系统在值变化时触发 Canvas 重绘。不挂 vbind key——高频驱动
                // 每 ~35ms 一次整树重建会令 Canvas 在渲染前被销毁（铁律）。
                val values = amps()
                val armed = cancelArmed()
                ctx.batchDraw = true
                val n = values.size
                if (n > 1 && w > 0f) {
                    val barW = 3f
                    val gap = 3f
                    val total = n * barW + (n - 1) * gap
                    var x = w - total
                    val base = if (armed) theme.rise else theme.brand
                    val cy = h / 2f
                    for (i in 0 until n) {
                        val bh = values[i]
                        if (bh > 0.8f) {
                            val fade = 0.35f + 0.65f * (i.toFloat() / (n - 1).toFloat())
                            ctx.fillStyle(base.opacity(fade))
                            val top = cy - bh / 2f
                            val bottom = cy + bh / 2f
                            val r = minOf(1.5f, bh / 2f)
                            ctx.beginPath()
                            ctx.moveTo(x, top + r)
                            ctx.lineTo(x, bottom - r)
                            ctx.arc(x + r, bottom - r, r, PI.toFloat(), (PI / 2).toFloat(), true)
                            ctx.lineTo(x + barW - r, bottom)
                            ctx.arc(x + barW - r, bottom - r, r, (PI / 2).toFloat(), 0f, true)
                            ctx.lineTo(x + barW, top + r)
                            ctx.arc(x + barW - r, top + r, r, 0f, (PI * 1.5).toFloat(), true)
                            ctx.lineTo(x + r, top)
                            ctx.arc(x + r, top + r, r, (PI * 1.5).toFloat(), PI.toFloat(), true)
                            ctx.closePath()
                            ctx.fill()
                        }
                        x += barW + gap
                    }
                }
            }
            Text {
                attr {
                    // 提示语是取消手势唯一的可见教学（规范 G1 / §5.2）
                    text(if (cancelArmed()) "松开手指，取消发送" else "松手发送 · 上滑取消")
                    fontSizeScaled(11f)
                    color(if (cancelArmed()) theme.rise else theme.textSecondary)
                }
            }
        }
        vif({ transcribing() }) {
            Text {
                attr {
                    flex(1f)
                    text("转写中…")
                    fontSizeScaled(12f)
                    color(theme.textTertiary)
                    textAlignCenter()
                }
            }
        }
    }
}
