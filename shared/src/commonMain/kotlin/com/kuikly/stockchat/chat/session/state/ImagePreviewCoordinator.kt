package com.kuikly.stockchat.chat.session.state

import com.tencent.kuikly.core.reactive.handler.observable

internal interface ImagePreviewStatePort { var path: String }

internal class ImagePreviewState : ImagePreviewStatePort {
    override var path: String by observable("")
}

internal class PlainImagePreviewState : ImagePreviewStatePort { override var path = "" }

/** Owns preview mount eligibility: blank paths never allocate the preview subtree. */
internal class ImagePreviewCoordinator(val state: ImagePreviewStatePort) {
    fun open(path: String) { if (path.isNotBlank()) state.path = path }
    fun close() { state.path = "" }
}
