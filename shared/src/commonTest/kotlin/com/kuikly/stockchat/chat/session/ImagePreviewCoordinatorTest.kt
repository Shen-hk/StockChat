package com.kuikly.stockchat.chat.session

import com.kuikly.stockchat.chat.session.state.ImagePreviewCoordinator
import com.kuikly.stockchat.chat.session.state.PlainImagePreviewState
import kotlin.test.Test
import kotlin.test.assertEquals

class ImagePreviewCoordinatorTest {
    @Test
    fun blankPathDoesNotReplacePreviewAndCloseReleasesIt() {
        val state = PlainImagePreviewState()
        val coordinator = ImagePreviewCoordinator(state)
        coordinator.open("/tmp/chart.png")
        coordinator.open("  ")
        assertEquals("/tmp/chart.png", state.path)
        coordinator.close()
        assertEquals("", state.path)
    }
}
