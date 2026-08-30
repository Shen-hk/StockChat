package com.kuikly.stockchat.composer

import com.tencent.kuikly.core.views.TextInputState

/**
 * Reconciles the two native input event streams without ever writing back to the editor.
 * The native full editing state is authoritative; text-only events are a platform fallback.
 */
internal object ComposerEditingReducer {

    fun programmatic(text: String, cursor: Int = text.length): TextInputState {
        val safeCursor = cursor.coerceIn(0, text.length)
        return TextInputState(text, safeCursor, safeCursor)
    }

    fun nativeState(state: TextInputState): TextInputState = state.coerceToTextBounds()

    fun nativeText(previous: TextInputState, text: String): TextInputState {
        // Android reports the full editing state first. Preserve its exact selection and
        // composition when the following textDidChange carries the same text.
        if (previous.text == text) return previous

        // Fallback for a platform/path that only reports textDidChange. Moving the cursor
        // by the text-length delta is exact for insertion/backspace and selection deletion.
        val delta = text.length - previous.text.length
        val cursor = (previous.selectionEnd + delta).coerceIn(0, text.length)
        return TextInputState(text, cursor, cursor)
    }

    fun nativeSelection(previous: TextInputState, event: TextInputState): TextInputState {
        // A queued selection event from an older text revision must not move the current
        // cursor. A full state event will reconcile both text and selection when needed.
        if (event.text != previous.text) return previous
        return event.coerceToTextBounds()
    }
}
