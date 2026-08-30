package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * 语音态输入区（规范 docs/11 §5）：计时 + 24 条振幅波形 + 手势提示语 / 转写占位。
 *
 * 纯渲染组件，全部状态经 lambda 注入；在 Canvas draw / attr 闭包内读取
 * observable（amps / elapsedSec / cancelArmed / transcribing），由 ReactiveObserver
 * 驱动 60ms 重绘。本组件覆盖在输入框之上（父容器负责显隐），不含任何交互。
 *
 * 信号参数对齐 WorkBuddy 源码（规范 §11）：条高已在页侧完成
 * min(1, rms×2.5) 增益与非对称平滑（升 0.6 / 落 0.25）。
 */
fun ViewContainer<*, *>.VoiceBar(
    theme: StockChatTheme,
    transcribing: () -> Boolean,
    cancelArmed: () -> Boolean,
    elapsedSec: () -> Float,
    amps: () -> FloatArray,
) {
    View {
        attr {
            flex(1f)
            height(44f)
            paddingLeft(12f)
            paddingRight(12f)
            justifyContentCenter()
        }
        vif({ !transcribing() }) {
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        val sec = elapsedSec()
                        text(formatVoiceElapsed(sec))
                        fontSize(13f)
                        // 55s 后计时变 rise 红（规范 §5.2）
                        color(if (sec >= 55f) theme.rise else theme.textSecondary)
                    }
                }
                Canvas({
                    attr {
                        flex(1f)
                        height(28f)
                        marginLeft(10f)
                    }
                }) { ctx, w, h ->
                    val values = amps()
                    val armed = cancelArmed()
                    ctx.batchDraw = true
                    val n = values.size
                    if (n > 0 && w > 0f) {
                        val barW = 3f
                        val gap = 3f
                        val total = n * barW + (n - 1) * gap
                        var x = (w - total) / 2f
                        val mid = (n - 1) / 2f
                        val base = if (armed) theme.textTertiary else theme.rise
                        val cy = h / 2f
                        for (i in 0 until n) {
                            // cos 包络：中轴高两端低；alpha 同步衰减（规范 §5.1 ②）
                            val env = abs(cos((i - mid) / n * PI.toFloat()))
                            ctx.fillStyle(base.opacity(0.55f + 0.45f * env))
                            val bh = values[i].coerceIn(4f, 28f)
                            // 圆角 1.5 的竖条（路径手绘，CanvasContext 无 fillRect）
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
                            x += barW + gap
                        }
                    }
                }
            }
            Text {
                attr {
                    // 提示语是取消手势唯一的可见教学（规范 G1 / §5.2）
                    text(if (cancelArmed()) "松开手指，取消发送" else "松开发送 · 上滑取消")
                    fontSize(12f)
                    color(if (cancelArmed()) theme.textTertiary else theme.textSecondary)
                    textAlignCenter()
                    marginTop(2f)
                }
            }
        }
        vif({ transcribing() }) {
            Text {
                attr {
                    text("转写中…")
                    fontSize(12f)
                    color(theme.textTertiary)
                    textAlignCenter()
                }
            }
        }
    }
}

/** `0:07` / `1:23` 等宽格式（规范 §5.2）。 */
private fun formatVoiceElapsed(sec: Float): String {
    val total = sec.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${if (s < 10) "0$s" else "$s"}"
}
