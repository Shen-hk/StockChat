package com.kuikly.stockchat.chat.message.state

import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer

internal interface MessageActionStatePort {
    var mounted: Boolean
    var presented: Boolean
    var pageX: Float
    var pageY: Float
    var followUpAllowed: Boolean
}

internal class MessageActionState : MessageActionStatePort {
    override var mounted: Boolean by observable(false)
    override var presented: Boolean by observable(false)
    override var pageX: Float by observable(0f)
    override var pageY: Float by observable(0f)
    override var followUpAllowed: Boolean by observable(false)
}

internal class PlainMessageActionState : MessageActionStatePort {
    override var mounted = false
    override var presented = false
    override var pageX = 0f
    override var pageY = 0f
    override var followUpAllowed = false
}

internal fun interface MessageActionScheduledTask { fun cancel() }

internal fun interface MessageActionScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): MessageActionScheduledTask
}

internal class KuiklyMessageActionScheduler : MessageActionScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): MessageActionScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return MessageActionScheduledTask(timer::cancel)
    }
}

internal sealed interface MessageActionEffect {
    data class CollectSelection(
        val messageId: String,
        val pageX: Float,
        val pageY: Float,
        val fallback: MessageActionFallback,
    ) : MessageActionEffect

    data class ClearSelection(val messageId: String) : MessageActionEffect
}

internal data class MessageActionFallback(
    val text: String,
    val allowFollowUp: Boolean,
)

/**
 * 消息长按菜单的唯一状态 owner。文本选区本身归原生 ViewRef，故通过 Effect 请求
 * 页面读取/清除选区；菜单的 R4/R5 两拍挂载与所有延迟均在此处统一管理。
 */
internal class MessageActionCoordinator(
    val state: MessageActionStatePort,
    private val scheduler: MessageActionScheduler,
    private val onEffect: (MessageActionEffect) -> Unit,
) {
    private var mounted = false
    private var presented = false
    private var revision = 0
    private var selectedMessageId = ""
    private var selectedPageX = 0f
    private var selectedPageY = 0f
    private var actionText = ""
    private var actionQuote = ""
    private val tasks = mutableListOf<MessageActionScheduledTask>()

    fun beginSelection(
        messageId: String,
        pageX: Float,
        pageY: Float,
        fallback: MessageActionFallback,
    ) {
        selectedMessageId = messageId
        selectedPageX = pageX
        selectedPageY = pageY
        val selectionRevision = revision
        schedule(SELECTION_SETTLE_MS) {
            if (selectionRevision == revision && selectedMessageId == messageId) {
                onEffect(MessageActionEffect.CollectSelection(messageId, pageX, pageY, fallback))
            }
        }
    }

    fun selectionAnchorFor(messageId: String): Pair<Float, Float>? =
        if (selectedMessageId == messageId) selectedPageX to selectedPageY else null

    fun selectedMessageId(): String = selectedMessageId

    fun isCurrentSelection(messageId: String): Boolean = selectedMessageId == messageId

    fun show(text: String, allowFollowUp: Boolean, pageX: Float, pageY: Float) {
        if (text.isBlank()) return
        actionText = text
        actionQuote = text.replace(Regex("\\s+"), " ").trim().let {
            if (it.length > MAX_QUOTE_CHARS) "${it.take(MAX_QUOTE_CHARS)}…" else it
        }
        state.followUpAllowed = allowFollowUp
        state.pageX = pageX
        state.pageY = pageY
        val nextRevision = ++revision
        if (mounted) {
            setPresented(true)
        } else {
            setMounted(true)
            // R4: vif 新挂载首帧不动画，下一拍才触发展现态。
            schedule(0) {
                if (nextRevision == revision && mounted) setPresented(true)
            }
        }
    }

    fun dismiss() {
        if (!mounted) return
        onEffect(MessageActionEffect.ClearSelection(selectedMessageId))
        val nextRevision = ++revision
        setPresented(false)
        schedule(EXIT_DURATION_MS) {
            if (nextRevision == revision && !presented) setMounted(false)
        }
    }

    fun actionText(): String = actionText
    fun actionQuote(): String = actionQuote

    fun onDestroy() {
        tasks.forEach(MessageActionScheduledTask::cancel)
        tasks.clear()
    }

    private fun schedule(delayMillis: Int, task: () -> Unit) {
        tasks += scheduler.schedule(delayMillis, task)
    }

    private fun setMounted(value: Boolean) { mounted = value; state.mounted = value }
    private fun setPresented(value: Boolean) { presented = value; state.presented = value }

    private companion object {
        const val SELECTION_SETTLE_MS = 500
        const val EXIT_DURATION_MS = 220
        const val MAX_QUOTE_CHARS = 60
    }
}
