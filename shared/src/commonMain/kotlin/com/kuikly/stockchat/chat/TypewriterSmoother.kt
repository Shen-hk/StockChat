package com.kuikly.stockchat.chat

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.timer.setTimeout

/**
 * 流式输出打字机平滑器（业界标准做法，参考 Vercel AI SDK smoothStream /
 * ChatGPT 类产品的 typewriter 模式）：
 *
 * - 网络层 delta 到达即全量累积进 [buffer]（不丢、不等待）；
 * - 显示端按固定节拍（[TICK_MS]）从 buffer 中"逐字释放"，释放的字数随积压
 *   自适应：积压 1 个字 → 每 tick 释放 1 个字（打字机手感，约 30 字/秒）；
 *   积压越大释放越快（[LAG_TICKS] 个 tick 追平），保证网络突发大批量 delta
 *   时不会整段整行地"蹦"出来，也不会越落越远；
 * - 按码点推进（emoji 等代理对不会被劈成半个乱码）；
 * - 卡片协议区（```card:...）由 [hideCardProtocol] 遮挡：完整 fence 截断，
 *   结尾处的半个 fence 前缀也扣住，避免闪现反引号。
 *
 * 生命周期：[append] 驱动 start；[complete] 在网络 onDone 时登记收尾回调，
 * 显示追平后触发一次；[cancel]（用户停止 / 出错）丢弃后续释放；[flushNow]
 * （停止生成时）立即把已收到的内容全部显示。完成后所有入口均为幂等 no-op。
 */
internal class TypewriterSmoother(
    override val pagerId: String,
    private val onPublish: (String) -> Unit,
) : PagerScope {
    private val buffer = StringBuilder()
    private var cursor = 0
    private var tickerRunning = false
    private var networkDone = false
    private var finished = false
    private var cancelled = false
    private var completion: (() -> Unit)? = null

    fun append(delta: String) {
        if (cancelled || finished) return
        buffer.append(delta)
        if (!tickerRunning) startTicker()
    }

    /** 网络流结束；显示端追平 buffer 后回调 [onComplete]（恰好已追平则同步触发）。 */
    fun complete(onComplete: () -> Unit) {
        if (cancelled || finished) {
            onComplete()
            return
        }
        networkDone = true
        completion = onComplete
        maybeFinish()
    }

    /** 立即显示全部已收到内容（停止生成时用），之后由 [cancel] 终止节拍。 */
    fun flushNow() {
        if (cancelled || finished) return
        cursor = buffer.length
        publish()
    }

    fun cancel() {
        cancelled = true
        completion = null
    }

    private fun maybeFinish() {
        if (!networkDone || cancelled || finished) return
        if (cursor >= buffer.length) {
            finished = true
            val callback = completion
            completion = null
            callback?.invoke()
        }
    }

    private fun startTicker() {
        tickerRunning = true
        setTimeout(TICK_MS) {
            tickerRunning = false
            if (cancelled || finished) return@setTimeout
            val backlog = buffer.length - cursor
            if (backlog > 0) {
                cursor += advanceLength(revealCount(backlog))
                publish()
            }
            if (cancelled || finished) return@setTimeout
            if (cursor < buffer.length) {
                startTicker()
            } else {
                maybeFinish()
            }
        }
    }

    private fun advanceLength(maxChars: Int): Int {
        var chars = 0
        var index = cursor
        while (index < buffer.length && chars < maxChars) {
            val char = buffer[index]
            index += if (char.isHighSurrogate() && index + 1 < buffer.length && buffer[index + 1].isLowSurrogate()) 2 else 1
            chars++
        }
        return index - cursor
    }

    private fun publish() {
        onPublish(buffer.substring(0, cursor))
    }

    companion object {
        private const val TICK_MS = 33
        private const val LAG_TICKS = 9
        internal const val MAX_CHARS_PER_TICK = 16
        private const val CARD_FENCE = "```card"

        /**
         * 释放速率：积压 [backlog] 个字时本 tick 释放多少字。
         * 小积压 1 字/tick（打字手感），大积压按 [LAG_TICKS] 个 tick 追平的速度
         * 加速，封顶 [MAX_CHARS_PER_TICK]。纯函数，便于测试。
         */
        internal fun revealCount(backlog: Int): Int =
            (backlog / LAG_TICKS + 1).coerceAtMost(MAX_CHARS_PER_TICK).coerceAtMost(backlog)

        /**
         * 流式期间遮挡卡片协议区。完整 fence 从头截断；结尾处不完整的 fence
         * 前缀（"…``"、"…```c" 等）一并扣住，等下一轮补齐再判断，避免闪现。
         */
        internal fun hideCardProtocol(text: String): String {
            val full = text.indexOf(CARD_FENCE)
            if (full >= 0) return text.substring(0, full)
            for (partial in CARD_FENCE.length - 1 downTo 1) {
                if (text.endsWith(CARD_FENCE.substring(0, partial))) {
                    return text.substring(0, text.length - partial)
                }
            }
            return text
        }
    }
}
