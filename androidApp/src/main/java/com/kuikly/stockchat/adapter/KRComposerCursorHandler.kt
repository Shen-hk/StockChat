package com.kuikly.stockchat.adapter

import android.widget.EditText
import com.tencent.kuikly.core.render.android.export.IKuiklyRenderViewExport
import com.tencent.kuikly.core.render.android.export.IKuiklyRenderViewPropExternalHandler

/**
 * Keeps the native caret enabled for the persistent chat composer.
 *
 * Kuikly's TextArea owns the input connection, so the common layer only sends
 * a narrow marker.  The marker is deliberately handled outside normal style
 * props: style/layout commits must not recreate the IME connection merely to
 * restore a caret.
 */
object KRComposerCursorHandler : IKuiklyRenderViewPropExternalHandler {
    private const val KEEP_CURSOR_VISIBLE = "stockChatKeepCursorVisible"

    override fun setViewExternalProp(
        renderViewExport: IKuiklyRenderViewExport,
        propKey: String,
        propValue: Any,
    ): Boolean {
        if (propKey != KEEP_CURSOR_VISIBLE) return false
        (renderViewExport.view() as? EditText)?.isCursorVisible = propValue != false && propValue != 0
        return true
    }

    override fun resetViewExternalProp(
        renderViewExport: IKuiklyRenderViewExport,
        propKey: String,
    ): Boolean {
        if (propKey != KEEP_CURSOR_VISIBLE) return false
        // The composer remains a normal editable control when the render view
        // is recycled; focus still determines whether Android actually draws it.
        (renderViewExport.view() as? EditText)?.isCursorVisible = true
        return true
    }
}
