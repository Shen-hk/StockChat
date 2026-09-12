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
private const val STREAM_CARD_PREFIX = "```card:"

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
    onTermLongPress: (EntitySpan, LongPressParams) -> Unit,
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
                onTermLongPress = onTermLongPress,
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
    onTermLongPress: (EntitySpan, LongPressParams) -> Unit,
) {
    val state = MarkdownStreamingState()
    val blocksHolder = StreamingBlocksHolder(timerScope.pagerId)
    var entities: List<EntitySpan> = emptyList()

    fun flush(force: Boolean) {
        // 结构化卡片的 JSON 在流式过程中不是给用户阅读的内容。让它继续进入
        // Markdown 的全量解析会在 payload 变长时反复触发布局，也会在卡片落位前
        // 短暂露出协议文本。先收敛为一条稳定的占位说明，最终态再由卡片渲染器接管。
        val adapted = EntityMarkdownAdapter.withEntityLinks(
            stripStreamingCardMarkup(textProvider()),
            contextSymbols,
        )
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
                    onTermLongPress = onTermLongPress,
                ),
            )
        }
    }
}

/**
 * 流式输出里将 card fence 收敛成固定高度的轻提示：
 * - 未闭合与已闭合的 card 都不会参与 Markdown/实体识别，避免 JSON 每 100ms 重排；
 * - 提示的内容只取 card 类型，不随 payload 增长而变化，保证滚动尾随平稳；
 * - 流结束后此函数不再参与，完整卡片由 [AiResponseLexer] 的完成态渲染。
 */
internal fun stripStreamingCardMarkup(content: String): String {
    if (!content.contains(STREAM_CARD_PREFIX)) return content

    val output = StringBuilder(content.length)
    var cursor = 0
    while (cursor < content.length) {
        val start = content.indexOf(STREAM_CARD_PREFIX, cursor)
        if (start < 0) {
            output.append(content, cursor, content.length)
            break
        }
        output.append(content, cursor, start)
        // card fence 按协议独占新行；占位自身会补两个段落换行，去掉这个分隔换行
        // 以免在正文与占位之间凭空多出一整行。
        if (output.lastOrNull() == '\n') output.setLength(output.length - 1)
        val typeStart = start + STREAM_CARD_PREFIX.length
        val headerEnd = content.indexOf('\n', typeStart)
        val type = if (headerEnd < 0) content.substring(typeStart).trim() else content.substring(typeStart, headerEnd).trim()
        output.append("\n\n> 正在准备")
        output.append(streamingCardLabel(type))
        output.append("…\n\n")

        if (headerEnd < 0) break
        val end = content.indexOf("```", headerEnd + 1)
        if (end < 0) break
        cursor = end + 3
    }
    return output.toString()
}

private fun streamingCardLabel(type: String): String = when (type) {
    "stock-quote" -> "行情卡片"
    "stock-chart" -> "走势图卡片"
    "news" -> "资讯卡片"
    "attribution" -> "归因卡片"
    "definition" -> "术语卡片"
    else -> "内容卡片"
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
    onTermLongPress: (EntitySpan, LongPressParams) -> Unit,
): MarkdownConfig {
    val dark = theme == StockChatTheme.Dark
    return MarkdownConfig(
        colors = MarkdownColors(
            text = if (dark) 0xFFF5F5F7 else 0xFF1D1D1F,
            // Markdown 的非正文元素共享一组克制的蓝青信息色：表格、引用、代码和
            // 链接在长回答中都有自己的层次，但不抢正文与涨跌色的注意力。
            codeBackground = if (dark) 0xFF202A3D else 0xFFF2F6FF,
            inlineCodeBackground = if (dark) 0xFF26334A else 0xFFE7F0FF,
            dividerColor = if (dark) 0xFF33486A else 0xFFC9D9F2,
            tableBackground = if (dark) 0xFF172235 else 0xFFF5F9FF,
            blockQuoteBar = if (dark) 0xFF79B8FF else 0xFF1677D2,
            blockQuoteBackground = if (dark) 0xFF172B4D else 0xFFEAF4FF,
            linkColor = if (dark) 0xFF8CC8FF else 0xFF1269B0,
            codeText = if (dark) 0xFFF5F5F7 else 0xFF1D1D1F,
        ),
        typography = stockMarkdownTypography(dark),
        dimens = MarkdownDimens(
            dividerThickness = 1f,
            codeBackgroundCornerSize = 8f,
            blockQuoteThickness = 3f,
            blockQuoteCornerSize = 8f,
            // 窄屏里表格更像一个信息卡，而不是铺满屏的电子表格。
            tableCellWidth = 108f,
            tableCellPadding = 9f,
            tableCornerSize = 10f,
        ),
        padding = MarkdownPadding(
            block = 5f,
            list = 3f,
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
                if (entity.type == EntityType.STOCK) onStockLongPress(entity, params) else onTermLongPress(entity, params)
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
    // 正文保持轻盈，重点交给 Markdown 的粗体与标题；长文档不会显得拥挤。
    val body = TextStyleConfig(fontSize = 16f, lineHeight = 25f, fontWeight = FontWeight.Medium, color = textColor)
    val compact = TextStyleConfig(fontSize = 15f, lineHeight = 23f, fontWeight = FontWeight.Medium, color = textColor)
    return MarkdownTypography(
        text = body,
        code = TextStyleConfig(fontSize = 14f, lineHeight = 21f, fontWeight = FontWeight.Medium, color = textColor),
        inlineCode = TextStyleConfig(fontSize = 15f, fontWeight = FontWeight.SemiBold, color = textColor),
        // 与输出协议对齐：唯一的大标题清晰领起回答，二、三级标题逐级收束。
        h1 = TextStyleConfig(fontSize = 24f, lineHeight = 34f, fontWeight = FontWeight.Bold, color = textColor),
        h2 = TextStyleConfig(fontSize = 20f, lineHeight = 29f, fontWeight = FontWeight.Bold, color = textColor),
        h3 = TextStyleConfig(fontSize = 17f, lineHeight = 25f, fontWeight = FontWeight.Bold, color = textColor),
        h4 = TextStyleConfig(fontSize = 16f, lineHeight = 25f, fontWeight = FontWeight.Bold, color = textColor),
        h5 = body,
        h6 = body,
        quote = TextStyleConfig(fontSize = 15f, lineHeight = 23f, fontWeight = FontWeight.SemiBold, color = secondary),
        paragraph = body,
        ordered = compact,
        bullet = compact,
        list = compact,
        table = TextStyleConfig(fontSize = 14f, lineHeight = 21f, fontWeight = FontWeight.SemiBold, color = textColor),
        textLink = body,
    )
}

private fun entityFromUrl(url: String, entities: List<EntitySpan>): EntitySpan? {
    if (!url.startsWith(ENTITY_URL_PREFIX)) return null
    return url.removePrefix(ENTITY_URL_PREFIX).toIntOrNull()?.let(entities::getOrNull)
}
