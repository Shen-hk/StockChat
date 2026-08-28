package com.kuikly.stockchat.richtext

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.KuiklyStreamingMarkdown
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.MarkdownDimens
import com.tencent.kuiklybase.config.MarkdownPadding
import com.tencent.kuiklybase.config.MarkdownTypography
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.streaming.MarkdownStreamingState
import com.tencent.kuikly.core.views.View

private const val ENTITY_URL_PREFIX = "stockchat-entity://"

fun ViewContainer<*, *>.EntityRichText(
    rawText: String,
    theme: StockChatTheme,
    contextSymbols: List<String> = emptyList(),
    onStockClick: (EntitySpan) -> Unit,
    onStockLongPress: (EntitySpan, String, Boolean) -> Unit,
    onTermClick: (String) -> Unit,
) {
    val adapted = EntityMarkdownAdapter.withEntityLinks(rawText, contextSymbols)
    View {
        attr {
            alignSelfStretch()
            capture(CaptureRule.longPress())
        }
        KuiklyMarkdown(
            content = adapted.content,
            config = stockMarkdownConfig(
                theme = theme,
                entities = adapted.entities,
                onStockClick = onStockClick,
                onStockLongPress = onStockLongPress,
                onTermClick = onTermClick,
            ),
        )
    }
}

fun ViewContainer<*, *>.EntityStreamingMarkdown(
    rawText: String,
    theme: StockChatTheme,
    contextSymbols: List<String> = emptyList(),
    onStockClick: (EntitySpan) -> Unit,
    onStockLongPress: (EntitySpan, String, Boolean) -> Unit,
    onTermClick: (String) -> Unit,
) {
    val adapted = EntityMarkdownAdapter.withEntityLinks(rawText, contextSymbols)
    val state = MarkdownStreamingState()
    val blocks = state.update(adapted.content, force = true).orEmpty()
    val config = stockMarkdownConfig(
        theme = theme,
        entities = adapted.entities,
        onStockClick = onStockClick,
        onStockLongPress = onStockLongPress,
        onTermClick = onTermClick,
    )
    View {
        attr {
            alignSelfStretch()
            capture(CaptureRule.longPress())
        }
        blocks.forEach { block ->
            KuiklyStreamingMarkdown(state = state, block = block, config = config)
        }
    }
}

internal object EntityMarkdownAdapter {
    fun withEntityLinks(rawText: String, contextSymbols: List<String> = emptyList()): EntityMarkdownContent {
        val entities = EntityRecognizer.recognize(rawText, contextSymbols)
            .filterNot { isInsideMarkdownLink(rawText, it.start) || isInsideInlineCode(rawText, it.start) }
        if (entities.isEmpty()) return EntityMarkdownContent(rawText, emptyList())

        var cursor = 0
        val output = StringBuilder()
        entities.forEachIndexed { index, entity ->
            output.append(rawText.substring(cursor, entity.start))
            val label = escapeLinkLabel(entity.text)
            output.append("[$label]($ENTITY_URL_PREFIX$index)")
            cursor = entity.endExclusive
        }
        output.append(rawText.substring(cursor))
        return EntityMarkdownContent(output.toString(), entities)
    }

    private fun escapeLinkLabel(text: String): String =
        text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")

    private fun isInsideMarkdownLink(text: String, offset: Int): Boolean {
        val previousOpen = text.lastIndexOf('[', startIndex = offset)
        val previousClose = text.lastIndexOf(']', startIndex = offset)
        val nextClose = text.indexOf(']', startIndex = offset)
        val nextParen = if (nextClose >= 0) text.indexOf('(', startIndex = nextClose) else -1
        return previousOpen > previousClose && nextClose >= offset && nextParen == nextClose + 1
    }

    private fun isInsideInlineCode(text: String, offset: Int): Boolean {
        val before = text.take(offset).count { it == '`' }
        val after = text.drop(offset).count { it == '`' }
        return before % 2 == 1 && after > 0
    }
}

internal data class EntityMarkdownContent(
    val content: String,
    val entities: List<EntitySpan>,
)

private fun stockMarkdownConfig(
    theme: StockChatTheme,
    entities: List<EntitySpan>,
    onStockClick: (EntitySpan) -> Unit,
    onStockLongPress: (EntitySpan, String, Boolean) -> Unit,
    onTermClick: (String) -> Unit,
): MarkdownConfig {
    val dark = theme == StockChatTheme.Dark
    return MarkdownConfig(
        colors = MarkdownColors(
            text = if (dark) 0xFFF5F5F7 else 0xFF1D1D1F,
            codeBackground = if (dark) 0xFF2C2C2E else 0xFFF1F1EF,
            inlineCodeBackground = if (dark) 0xFF2C2C2E else 0xFFF1F1EF,
            dividerColor = if (dark) 0xFF363638 else 0xFFE8E8E6,
            tableBackground = if (dark) 0xFF1D1D1F else 0xFFFFFFFF,
            blockQuoteBar = if (dark) 0xFF82A8FF else 0xFF2563EB,
            blockQuoteBackground = if (dark) 0xFF1E3158 else 0xFFEFF6FF,
            linkColor = if (dark) 0xFF82A8FF else 0xFF2563EB,
            codeText = if (dark) 0xFFF5F5F7 else 0xFF1D1D1F,
        ),
        typography = stockMarkdownTypography(dark),
        dimens = MarkdownDimens(
            dividerThickness = 1f,
            codeBackgroundCornerSize = 8f,
            blockQuoteThickness = 3f,
            blockQuoteCornerSize = 8f,
            tableCellWidth = 120f,
            tableCellPadding = 10f,
            tableCornerSize = 8f,
        ),
        padding = MarkdownPadding(
            block = 3f,
            list = 2f,
            listItemTop = 2f,
            listItemBottom = 2f,
            listIndent = 14f,
            codeBlock = 10f,
            blockQuotePaddingLeft = 10f,
            blockQuoteBarPaddingLeft = 4f,
            blockQuoteTextVertical = 8f,
        ),
        onLinkClick = { url, _ ->
            println("[STOCKCHAT_DBG] onLinkClick url=$url")
            entityFromUrl(url, entities)?.let { entity ->
                if (entity.type == EntityType.STOCK) onStockClick(entity) else onTermClick(entity.text)
            }
        },
        onLinkLongPress = { url, params ->
            if (params.state != "move" || params.isCancel) {
                println("[STOCKCHAT_DBG] onLinkLongPress url=$url state=${params.state} isCancel=${params.isCancel}")
            }
            entityFromUrl(url, entities)?.let { entity ->
                if (entity.type == EntityType.STOCK) onStockLongPress(entity, params.state, params.isCancel)
            }
        },
        unorderedListBullet = { _, depth ->
            when (depth % 3) {
                0 -> "• "
                1 -> "◦ "
                else -> "▪ "
            }
        },
        eolAsNewLine = false,
        codeHighlightEnabled = false,
        codeHighlightDarkTheme = dark,
    )
}

private fun stockMarkdownTypography(dark: Boolean): MarkdownTypography {
    val textColor = if (dark) 0xFFF5F5F7 else 0xFF1D1D1F
    val secondary = if (dark) 0xFFC7C7CC else 0xFF6E6E73
    val body = TextStyleConfig(fontSize = 14f, lineHeight = 22f, color = textColor)
    val compact = TextStyleConfig(fontSize = 13f, lineHeight = 20f, color = textColor)
    return MarkdownTypography(
        text = body,
        code = TextStyleConfig(fontSize = 12f, lineHeight = 18f, color = textColor),
        inlineCode = TextStyleConfig(fontSize = 13f, color = textColor),
        h1 = TextStyleConfig(fontSize = 18f, lineHeight = 25f, fontWeight = FontWeight.Bold, color = textColor),
        h2 = TextStyleConfig(fontSize = 16f, lineHeight = 23f, fontWeight = FontWeight.SemiBold, color = textColor),
        h3 = TextStyleConfig(fontSize = 15f, lineHeight = 22f, fontWeight = FontWeight.SemiBold, color = textColor),
        h4 = TextStyleConfig(fontSize = 14f, lineHeight = 22f, fontWeight = FontWeight.SemiBold, color = textColor),
        h5 = body,
        h6 = body,
        quote = TextStyleConfig(fontSize = 13f, lineHeight = 20f, color = secondary),
        paragraph = body,
        ordered = compact,
        bullet = compact,
        list = compact,
        table = TextStyleConfig(fontSize = 12f, lineHeight = 18f, color = textColor),
        textLink = body,
    )
}

private fun entityFromUrl(url: String, entities: List<EntitySpan>): EntitySpan? {
    if (!url.startsWith(ENTITY_URL_PREFIX)) return null
    return url.removePrefix(ENTITY_URL_PREFIX).toIntOrNull()?.let(entities::getOrNull)
}
