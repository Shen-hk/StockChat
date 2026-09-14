package com.kuikly.stockchat.chat.composer.component

import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Stateless DSL rows shared by @ mention and command-security candidate panels. */
internal object ComposerCandidateRows {
    fun renderSecurity(
        row: ViewContainer<*, *>,
        candidate: AtCandidate,
        query: String,
        theme: StockChatTheme,
    ) {
        val entry = candidate.entry
        row.View {
            attr { flex(1f); flexDirectionRow(); alignItemsCenter(); marginRight(8f) }
            val (pre, hit, suffix) = splitHighlight(entry.name, query)
            if (pre.isNotEmpty()) Text { attr { text(pre); fontSizeScaled(14f); color(theme.textPrimary) } }
            if (hit.isNotEmpty()) Text { attr { text(hit); fontSizeScaled(14f); fontWeightBold(); color(theme.brand) } }
            if (suffix.isNotEmpty()) Text { attr { text(suffix); fontSizeScaled(14f); color(theme.textPrimary) } }
        }
        row.Text {
            attr {
                text(entry.symbol); fontSizeScaled(11f); color(theme.textTertiary)
                width(74f); textAlignRight(); marginRight(6f)
            }
        }
        row.View {
            attr {
                width(38f); height(16f); marginRight(6f); alignItemsCenter(); justifyContentCenter()
                backgroundColor(theme.surfaceMuted); borderRadius(4f)
            }
            Text { attr { text(entry.market); fontSizeScaled(9f); color(theme.textSecondary) } }
        }
        row.Text {
            attr {
                text(formatChgPct(entry.chgPct)); fontSizeScaled(12f); fontWeightSemiBold()
                color(chgColor(entry.chgPct, theme)); width(48f); textAlignRight(); marginRight(6f)
            }
        }
        row.View {
            attr {
                width(32f); height(16f); alignItemsCenter(); justifyContentCenter()
                backgroundColor(theme.surfaceMuted); borderRadius(4f)
            }
            Text { attr { text(candidate.source); fontSizeScaled(9f); color(theme.textSecondary) } }
        }
    }

    fun renderBoard(
        row: ViewContainer<*, *>,
        candidate: AtCandidate,
        query: String,
        theme: StockChatTheme,
    ) {
        val entry = candidate.entry
        row.View {
            attr { flex(1f); flexDirectionRow(); alignItemsCenter(); marginRight(8f) }
            val (pre, hit, suffix) = splitHighlight(entry.name, query)
            if (pre.isNotEmpty()) Text { attr { text(pre); fontSizeScaled(14f); color(theme.brand) } }
            if (hit.isNotEmpty()) Text { attr { text(hit); fontSizeScaled(14f); fontWeightBold(); color(theme.brand) } }
            if (suffix.isNotEmpty()) Text { attr { text(suffix); fontSizeScaled(14f); color(theme.brand) } }
        }
        row.Text { attr { text("共 ${entry.boardCount} 只"); fontSizeScaled(11f); color(theme.textTertiary); marginRight(6f) } }
        row.View {
            attr {
                paddingLeft(5f); paddingRight(5f); height(16f); marginRight(6f); alignItemsCenter(); justifyContentCenter()
                backgroundColor(theme.brandSoft); borderRadius(4f)
            }
            Text { attr { text("板块"); fontSizeScaled(9f); color(theme.brand) } }
        }
        row.View {
            attr {
                paddingLeft(5f); paddingRight(5f); height(16f); alignItemsCenter(); justifyContentCenter()
                backgroundColor(theme.surfaceMuted); borderRadius(4f)
            }
            Text { attr { text(candidate.source); fontSizeScaled(9f); color(theme.textSecondary) } }
        }
    }

    private fun splitHighlight(name: String, query: String): Triple<String, String, String> {
        if (query.isEmpty()) return Triple(name, "", "")
        val index = name.indexOf(query, ignoreCase = true)
        if (index < 0) return Triple(name, "", "")
        return Triple(name.substring(0, index), name.substring(index, index + query.length), name.substring(index + query.length))
    }

    private fun formatChgPct(pct: Float?): String {
        if (pct == null) return "--"
        val sign = if (pct >= 0f) "+" else "−"
        val absolute = if (pct >= 0f) pct else -pct
        val whole = absolute.toInt()
        return "$sign$whole.${((absolute - whole) * 10).toInt()}%"
    }

    private fun chgColor(pct: Float?, theme: StockChatTheme): Color = when {
        pct == null -> theme.textTertiary
        pct > 0f -> theme.rise
        pct < 0f -> theme.fall
        else -> theme.textSecondary
    }
}
