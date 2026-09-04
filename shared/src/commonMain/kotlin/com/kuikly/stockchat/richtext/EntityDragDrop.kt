package com.kuikly.stockchat.richtext

/** Drop destinations shared by the entity gesture and the page chrome. */
enum class EntityDropTarget {
    NONE,
    ISLAND,
    COMPOSER,
}

/**
 * Pure hit testing for stock-entity drags.
 *
 * The targets are intentionally more forgiving than their visible bounds. A finger hides the
 * dragged chip on a phone, so requiring pixel-perfect drops makes the interaction feel broken.
 */
object EntityDropResolver {
    const val DRAG_THRESHOLD = 10f

    fun hasExceededDragThreshold(
        startX: Float,
        startY: Float,
        currentX: Float,
        currentY: Float,
        threshold: Float = DRAG_THRESHOLD,
    ): Boolean {
        val dx = currentX - startX
        val dy = currentY - startY
        return dx * dx + dy * dy >= threshold * threshold
    }

    fun resolve(
        pageX: Float,
        pageY: Float,
        pageWidth: Float,
        pageHeight: Float,
        statusBarHeight: Float,
        safeAreaBottom: Float,
        keyboardHeight: Float,
        islandExpanded: Boolean,
    ): EntityDropTarget {
        if (pageWidth <= 0f || pageHeight <= 0f) return EntityDropTarget.NONE

        val horizontalInset = 18f
        val insideHorizontalTarget = pageX in horizontalInset..(pageWidth - horizontalInset)
        val islandBottom = statusBarHeight + if (islandExpanded) 158f else 86f
        if (insideHorizontalTarget && pageY in statusBarHeight.coerceAtLeast(0f)..islandBottom) {
            return EntityDropTarget.ISLAND
        }

        val composerCaptureHeight = if (keyboardHeight > 0f) 190f else 128f
        val composerTop = pageHeight - safeAreaBottom.coerceAtLeast(0f) -
            keyboardHeight.coerceAtLeast(0f) - composerCaptureHeight
        if (insideHorizontalTarget && pageY >= composerTop) return EntityDropTarget.COMPOSER

        return EntityDropTarget.NONE
    }
}
