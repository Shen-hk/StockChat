package com.kuikly.stockchat

import com.kuikly.stockchat.composer.ComposerEditingReducer
import com.tencent.kuikly.core.views.TextInputState
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposerEditingReducerTest {

    @Test
    fun textOnlyBackspaceMovesCursorLeft() {
        val result = ComposerEditingReducer.nativeText(TextInputState("abc", 3, 3), "ab")

        assertEquals(TextInputState("ab", 2, 2), result)
    }

    @Test
    fun textOnlySelectionDeletionCollapsesAtSelectionStart() {
        val result = ComposerEditingReducer.nativeText(TextInputState("abcdef", 2, 5), "abf")

        assertEquals(TextInputState("abf", 2, 2), result)
    }

    @Test
    fun duplicateTextEventPreservesNativeComposition() {
        val composing = TextInputState("mao", 3, 3, 0, 3)

        assertEquals(composing, ComposerEditingReducer.nativeText(composing, "mao"))
    }

    @Test
    fun staleSelectionEventCannotMoveCurrentCursor() {
        val current = TextInputState("ab", 2, 2)
        val stale = TextInputState("abc", 1, 1)

        assertEquals(current, ComposerEditingReducer.nativeSelection(current, stale))
    }

    @Test
    fun programmaticStateClampsCursor() {
        assertEquals(TextInputState("ab", 2, 2), ComposerEditingReducer.programmatic("ab", 99))
    }
}
