package com.kuikly.stockchat.chat.composer.state

import com.tencent.kuikly.core.reactive.handler.observable

internal const val MAX_COMPOSER_ATTACHMENTS = 4

/** Attachment data is UI-neutral so the Page can pass native media results in as effects. */
internal data class ComposerAttachment(
    val id: String,
    val path: String,
    val name: String,
    val isImage: Boolean,
) {
    val kind: String get() = if (isImage) "image" else "file"

    /** Kuikly core has no text ellipsis API, so display names are bounded here. */
    val displayName: String
        get() {
            val base = name.ifBlank { if (isImage) "图片" else "文档" }
            return if (base.length <= 14) base else base.take(7) + "…" + base.takeLast(6)
        }
}

internal interface ComposerAttachmentStatePort {
    var attachments: List<ComposerAttachment>
    var mediaSheetMounted: Boolean
    var mediaSheetPresented: Boolean
}

internal class ComposerAttachmentState : ComposerAttachmentStatePort {
    override var attachments: List<ComposerAttachment> by observable(emptyList())
    override var mediaSheetMounted: Boolean by observable(false)
    override var mediaSheetPresented: Boolean by observable(false)
}

internal class PlainComposerAttachmentState : ComposerAttachmentStatePort {
    override var attachments: List<ComposerAttachment> = emptyList()
    override var mediaSheetMounted = false
    override var mediaSheetPresented = false
}

/** Owns attachment collection changes; it never reads a Kuikly observable outside reactive UI. */
internal class ComposerAttachmentCoordinator(
    private val state: ComposerAttachmentStatePort,
) {
    private var sequence = 0
    private var attachments: List<ComposerAttachment> = emptyList()

    fun add(path: String, name: String, isImage: Boolean): ComposerAttachment? {
        if (attachments.size >= MAX_COMPOSER_ATTACHMENTS) return null
        return ComposerAttachment(
            id = "att_${++sequence}",
            path = path,
            name = name,
            isImage = isImage,
        ).also { attachment ->
            attachments = attachments + attachment
            state.attachments = attachments
        }
    }

    fun remove(id: String) {
        attachments = attachments.filterNot { it.id == id }
        state.attachments = attachments
    }

    fun clear() {
        if (attachments.isEmpty()) return
        attachments = emptyList()
        state.attachments = attachments
    }

    fun defaultPrompt(): String {
        val hasImage = attachments.any { it.isImage }
        val documentName = attachments.firstOrNull { !it.isImage }?.name.orEmpty()
        return when {
            hasImage && documentName.isNotEmpty() -> "帮我解读这张图片和文档《$documentName》"
            hasImage -> "帮我解读这张图片"
            documentName.isNotEmpty() -> "帮我解读文档《$documentName》"
            else -> "帮我解读这些资料"
        }
    }
}
