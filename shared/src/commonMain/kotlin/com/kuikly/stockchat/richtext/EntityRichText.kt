package com.kuikly.stockchat.richtext

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.KuiklyStreamingMarkdown
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.MarkdownDimens
import com.tencent.kuiklybase.config.MarkdownPadding
import com.tencent.kuiklybase.config.MarkdownTypography
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.streaming.MarkdownBlock
import com.tencent.kuiklybase.streaming.MarkdownStreamingState
import com.tencent.kuikly.core.views.View

private const val ENTITY_URL_PREFIX = "stockchat-entity://"

// 流式 flush 节流间隔：库文档建议调用方用定时器 ~100ms flush 一次，
// 高频 delta 只累积文本，渲染按块增量 diff，避免整树重建打崩原生层。
private const val STREAM_FLUSH_INTERVAL_MS = 100

fun ViewContainer<*, *>.EntityRichText(
    rawText: String,
    theme: StockChatTheme,
    contextSymbols: List<String> = emptyList(),
    onStockClick: (EntitySpan) -> Unit,
    onStockLongPress: (EntitySpan, LongPressParams) -> Unit,
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

/**
 * 流式 Markdown 渲染（kuiklybase 官方范式）：
 * - [MarkdownStreamingState] 全程持久，每次 flush 全量解析 + 块级 diff；
 * - 定时器每 100ms flush 一次，[observableList.diffUpdate] 只对 id 变化的
 *   尾部块重建视图，已完成的块保持不动——绝不逐 delta 整树重挂载
 *   （会触发 shadow must not null / duplicate createFlexNode 原生崩溃）；
 * - 实体链接适配每轮 flush 对全文重跑：前缀实体索引随文本增长保持稳定，
 *   早先挂载的块视图捕获的旧 entities 列表仍然有效。
 *
 * [textProvider] / [isStreaming] 供定时器回调读取当前值，不走响应式；
 * 响应式渲染只依赖 [blocks]（vfor 内读取，符合 R1）。
 * [timerScope] 提供定时器归属的 Pager 上下文（通常传消息本体）。
 */
fun ViewContainer<*, *>.EntityStreamingMarkdown(
    textProvider: () -> String,
    isStreaming: () -> Boolean,
    timerScope: PagerScope,
    theme: StockChatTheme,
    contextSymbols: List<String> = emptyList(),
    onStockClick: (EntitySpan) -> Unit,
    onStockLongPress: (EntitySpan, LongPressParams) -> Unit,
    onTermClick: (String) -> Unit,
) {
    val state = MarkdownStreamingState()
    val blocksHolder = StreamingBlocksHolder(timerScope.pagerId)
    var entities: List<EntitySpan> = emptyList()

    fun flush(force: Boolean) {
        val adapted = EntityMarkdownAdapter.withEntityLinks(textProvider(), contextSymbols)
        entities = adapted.entities
        state.update(adapted.content, force = force)?.let { newBlocks ->
            blocksHolder.blocks.diffUpdate(newBlocks) { old, new -> old.id == new.id }
        }
    }

    // mount 即渲染已到达的内容，避免空白一闪
    flush(force = true)

    fun scheduleFlush() {
        timerScope.setTimeout(STREAM_FLUSH_INTERVAL_MS) {
            if (isStreaming()) {
                flush(force = false)
                scheduleFlush()
            } else {
                // 收尾：强制 flush 最终文本后自然停表
                flush(force = true)
            }
        }
    }
    scheduleFlush()

    View {
        attr {
            alignSelfStretch()
            capture(CaptureRule.longPress())
        }
        vfor({ blocksHolder.blocks }) { block ->
            KuiklyStreamingMarkdown(
                state = state,
                block = block,
                config = stockMarkdownConfig(
                    theme = theme,
                    entities = entities,
                    onStockClick = onStockClick,
                    onStockLongPress = onStockLongPress,
                    onTermClick = onTermClick,
                ),
            )
        }
    }
}

/**
 * 流式块列表的宿主：observableList 委托必须挂在类属性上（局部委托变量要求
 * Nothing? 接收者，ReadWriteProperty&lt;Any?, V&gt; 不适用）。每次流式挂载新建
 * 一个实例，委托 propertyOwnerId 天然唯一、互不串键。
 */
private class StreamingBlocksHolder(
    override val pagerId: String,
) : PagerScope {
    var blocks: ObservableList<MarkdownBlock> by observableList()
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
    onStockLongPress: (EntitySpan, LongPressParams) -> Unit,
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
            entityFromUrl(url, entities)?.let { entity ->
                if (entity.type == EntityType.STOCK) onStockClick(entity) else onTermClick(entity.text)
            }
        },
        onLinkLongPress = { url, params ->
            entityFromUrl(url, entities)?.let { entity ->
                if (entity.type == EntityType.STOCK) onStockLongPress(entity, params)
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
    val body = TextStyleConfig(fontSize = 16f, lineHeight = 25f, color = textColor)
    val compact = TextStyleConfig(fontSize = 15f, lineHeight = 23f, color = textColor)
    return MarkdownTypography(
        text = body,
        code = TextStyleConfig(fontSize = 14f, lineHeight = 21f, color = textColor),
        inlineCode = TextStyleConfig(fontSize = 15f, color = textColor),
        h1 = TextStyleConfig(fontSize = 20f, lineHeight = 28f, fontWeight = FontWeight.Bold, color = textColor),
        h2 = TextStyleConfig(fontSize = 18f, lineHeight = 26f, fontWeight = FontWeight.SemiBold, color = textColor),
        h3 = TextStyleConfig(fontSize = 17f, lineHeight = 24f, fontWeight = FontWeight.SemiBold, color = textColor),
        h4 = TextStyleConfig(fontSize = 16f, lineHeight = 25f, fontWeight = FontWeight.SemiBold, color = textColor),
        h5 = body,
        h6 = body,
        quote = TextStyleConfig(fontSize = 15f, lineHeight = 23f, color = secondary),
        paragraph = body,
        ordered = compact,
        bullet = compact,
        list = compact,
        table = TextStyleConfig(fontSize = 14f, lineHeight = 21f, color = textColor),
        textLink = body,
    )
}

private fun entityFromUrl(url: String, entities: List<EntitySpan>): EntitySpan? {
    if (!url.startsWith(ENTITY_URL_PREFIX)) return null
    return url.removePrefix(ENTITY_URL_PREFIX).toIntOrNull()?.let(entities::getOrNull)
}
