package com.kuikly.stockchat

import com.kuikly.stockchat.composer.ComposerTextOperations
import com.kuikly.stockchat.richtext.EntityDropResolver
import com.kuikly.stockchat.richtext.EntityDropTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityDragDropTest {
    @Test
    fun resolvesIslandAndComposerWithForgivingBounds() {
        val common = DropTestViewport()

        assertEquals(EntityDropTarget.ISLAND, common.resolve(190f, 58f))
        assertEquals(EntityDropTarget.COMPOSER, common.resolve(190f, 790f))
        assertEquals(EntityDropTarget.NONE, common.resolve(190f, 390f))
    }

    @Test
    fun expandedIslandKeepsTheSecondDropTargetTall() {
        val common = DropTestViewport()

        assertEquals(EntityDropTarget.NONE, common.resolve(190f, 150f, islandExpanded = false))
        assertEquals(EntityDropTarget.ISLAND, common.resolve(190f, 150f, islandExpanded = true))
    }

    @Test
    fun dragModeStartsOnlyAfterMeaningfulMovement() {
        assertEquals(
            false,
            EntityDropResolver.hasExceededDragThreshold(100f, 100f, 106f, 106f),
        )
        assertEquals(
            true,
            EntityDropResolver.hasExceededDragThreshold(100f, 100f, 111f, 100f),
        )
    }

    @Test
    fun mentionInsertionReplacesSelectionAndKeepsTokenBoundaries() {
        val edit = ComposerTextOperations.insertMention(
            text = "分析走势",
            selectionStart = 2,
            selectionEnd = 4,
            mentionText = "@贵州茅台",
        )

        assertEquals("分析 @贵州茅台 ", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    private data class DropTestViewport(
        val width: Float = 390f,
        val height: Float = 844f,
        val status: Float = 44f,
        val bottom: Float = 34f,
    ) {
        fun resolve(x: Float, y: Float, islandExpanded: Boolean = false) = EntityDropResolver.resolve(
            pageX = x,
            pageY = y,
            pageWidth = width,
            pageHeight = height,
            statusBarHeight = status,
            safeAreaBottom = bottom,
            keyboardHeight = 0f,
            islandExpanded = islandExpanded,
        )
    }
}
