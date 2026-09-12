package com.kuikly.stockchat.chat

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.handler.observable

enum class MessageRole { SYSTEM, USER, ASSISTANT }

/**
 * 消息层附件（UI 无关）：发送时从输入栏快照进用户消息，仅用于气泡回显。
 * 附件文件由宿主复制到应用私有持久目录；元数据随会话保存，恢复后仍可预览和查看文件名。
 */
data class MessageAttachment(
    val id: String,
    val path: String,
    val name: String,
    val isImage: Boolean,
) {
    val kind: String get() = if (isImage) "image" else "file"

    /** Kuikly core 无文本省略 API，与输入栏预览同款截断。 */
    val displayName: String
        get() {
            val base = name.ifBlank { if (isImage) "图片" else "文档" }
            return if (base.length <= 14) base else base.take(7) + "…" + base.takeLast(6)
        }
}

class ChatMessage(
    override val pagerId: String,
    val id: String,
    val role: MessageRole,
    content: String,
    streaming: Boolean = false,
    failed: Boolean = false,
    cancelled: Boolean = false,
    attachments: List<MessageAttachment> = emptyList(),
) : PagerScope {
    var content: String by observable(content)
    var streaming: Boolean by observable(streaming)
    var failed: Boolean by observable(failed)
    var cancelled: Boolean by observable(cancelled)

    /** 仅用户消息携带；创建后不再变化（渲染层按创建时快照读取即可）。 */
    var attachments: List<MessageAttachment> by observable(attachments)
}

enum class StreamState { IDLE, STREAMING, STOPPED, ERROR }

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
    val groupTitle: String,
)
