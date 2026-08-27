package com.kuikly.stockchat.richtext

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.Span

fun ViewContainer<*, *>.EntityRichText(
    rawText: String,
    theme: StockChatTheme,
    contextSymbols: List<String> = emptyList(),
    onStockClick: (EntitySpan) -> Unit,
    onStockLongPress: (EntitySpan, String, Boolean) -> Unit,
    onTermClick: (String) -> Unit,
) {
    val text = rawText
        .replace("**", "")
        .lines()
        .joinToString("\n") { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#### ") -> trimmed.removePrefix("#### ")
                trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
                trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
                trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
                trimmed.startsWith("> ") -> trimmed.removePrefix("> ")
                else -> line
            }
        }
    val spans = EntityRecognizer.recognize(text, contextSymbols)
    val primaryStock = spans.firstOrNull { it.type == EntityType.STOCK }
    RichText {
        attr {
            fontSize(14f)
            lineHeight(22f)
            color(theme.textPrimary)
        }
        var cursor = 0
        spans.forEach { entity ->
            if (entity.start > cursor) {
                Span {
                    text(text.substring(cursor, entity.start))
                    fontSize(14f)
                    color(theme.textPrimary)
                }
            }
            Span {
                text(entity.text + if (entity.type == EntityType.TERM) " 📖" else "")
                fontSize(14f)
                fontWeightMedium()
                color(if (entity.type == EntityType.TERM) theme.term else theme.brand)
                if (entity.type != EntityType.TERM) textDecorationUnderLine()
                click {
                    if (entity.type == EntityType.STOCK) onStockClick(entity)
                    else onTermClick(entity.text)
                }
            }
            cursor = entity.endExclusive
        }
        if (cursor < text.length) {
            Span {
                text(text.substring(cursor))
                fontSize(14f)
                color(theme.textPrimary)
            }
        }
        if (text.isEmpty()) {
            Span { text("") }
        }
        primaryStock?.let { stock ->
            event {
                longPress { params ->
                    onStockLongPress(stock, params.state, params.isCancel)
                }
            }
        }
    }
}
