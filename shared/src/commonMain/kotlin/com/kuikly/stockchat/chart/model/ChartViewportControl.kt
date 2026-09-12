package com.kuikly.stockchat.chart.model

/** A single, revisioned command from the detail-page chart controls. */
enum class ChartViewportAction {
    NONE,
    ZOOM_IN,
    ZOOM_OUT,
    PAN_LEFT,
    PAN_RIGHT,
    RESET,
}

/**
 * The revision makes consecutive taps on the same button observable.  Viewport ownership stays
 * inside each chart, while the detail page only broadcasts the user's intent.
 */
data class ChartViewportCommand(
    val action: ChartViewportAction = ChartViewportAction.NONE,
    val revision: Int = 0,
)
