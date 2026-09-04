package com.kuikly.stockchat.composer

/** Result of a deterministic composer edit, including the cursor that should be restored. */
data class ComposerTextEdit(val text: String, val cursor: Int)

/** Pure text transformations shared by composer interaction handlers. */
object ComposerTextOperations {

    /** Inserts a solid @ mention in place of the current selection. */
    fun insertMention(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        mentionText: String,
    ): ComposerTextEdit {
        require(mentionText.startsWith('@')) { "Mention must start with @" }

        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
        val before = text.substring(0, start)
        val after = text.substring(end)
        val leadingSpace = if (before.lastOrNull()?.isWhitespace() == false) " " else ""
        val trailingSpace = if (after.firstOrNull()?.isWhitespace() == true) "" else " "
        val inserted = leadingSpace + mentionText + trailingSpace
        return ComposerTextEdit(
            text = before + inserted + after,
            cursor = start + inserted.length,
        )
    }

    /**
     * Replaces an active @ or / trigger fragment and inserts [trigger] at the current cursor.
     * A separating space is added when the character before the cursor is not a boundary.
     */
    fun insertTrigger(
        text: String,
        cursor: Int,
        activeSession: TriggerSession?,
        trigger: Char,
    ): ComposerTextEdit {
        require(trigger == '@' || trigger == '/') { "Unsupported trigger: $trigger" }

        var editedText = text
        var editedCursor = cursor.coerceIn(0, text.length)
        if (activeSession != null) {
            val start = activeSession.triggerStart.coerceIn(0, editedText.length)
            val end = editedCursor.coerceAtLeast(start).coerceAtMost(editedText.length)
            editedText = editedText.substring(0, start) + editedText.substring(end)
            editedCursor = start
        }

        val before = editedText.substring(0, editedCursor)
        val after = editedText.substring(editedCursor)
        val separator = if (before.lastOrNull()?.isWhitespace() == false) " " else ""
        val inserted = separator + trigger
        return ComposerTextEdit(
            text = before + inserted + after,
            cursor = editedCursor + inserted.length,
        )
    }

    /** Start of the unfinished parameter token at the end of [text]. */
    fun lastParameterTokenStart(text: String, minimumStart: Int): Int {
        if (text.isEmpty()) return 0
        val lowerBound = minimumStart.coerceIn(0, text.length)
        var end = text.length
        while (end > lowerBound && text[end - 1].isWhitespace()) end--
        var start = end
        while (start > lowerBound && !text[start - 1].isWhitespace()) start--
        return if (end == text.length && start < end) start else text.length
    }

    fun triggerAbortReason(text: String, cursor: Int, previous: TriggerSession): String {
        if (previous.triggerStart !in text.indices || text[previous.triggerStart] != previous.type) {
            return "delete-out"
        }
        if (cursor <= previous.triggerStart || cursor > text.length) return "cursor-out"
        return if (text.substring(previous.triggerStart, cursor).any { it.isWhitespace() }) {
            "space"
        } else {
            "cursor-out"
        }
    }
}
