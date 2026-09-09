package com.kuikly.stockchat.chat.composer

import com.kuikly.stockchat.chat.composer.state.ComposerAttachmentCoordinator
import com.kuikly.stockchat.chat.composer.state.MAX_COMPOSER_ATTACHMENTS
import com.kuikly.stockchat.chat.composer.state.PlainComposerAttachmentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComposerAttachmentCoordinatorTest {
    @Test
    fun attachmentLimitRemoveAndPromptArePreserved() {
        val state = PlainComposerAttachmentState()
        val coordinator = ComposerAttachmentCoordinator(state)

        val image = coordinator.add("/tmp/chart.png", "chart.png", isImage = true)!!
        coordinator.add("/tmp/report.pdf", "report.pdf", isImage = false)
        repeat(MAX_COMPOSER_ATTACHMENTS - 2) { index ->
            coordinator.add("/tmp/$index.png", "$index.png", isImage = true)
        }

        assertNull(coordinator.add("/tmp/overflow.png", "overflow.png", isImage = true))
        assertEquals("帮我解读这张图片和文档《report.pdf》", coordinator.defaultPrompt())
        coordinator.remove(image.id)
        assertEquals(MAX_COMPOSER_ATTACHMENTS - 1, state.attachments.size)
        coordinator.clear()
        assertEquals(0, state.attachments.size)
    }
}
