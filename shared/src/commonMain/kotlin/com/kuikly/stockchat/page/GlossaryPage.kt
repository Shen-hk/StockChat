package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.ExplanationDepth
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.GlossaryCategory
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.kuikly.stockchat.page.components.AppTopBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

/**
 * 术语表浏览页（12 号需求文档 FR-G3）。
 *
 * 立项调研的第二个痛点是「术语不翻译」，而聊天里的术语只能等 AI 回答碰巧提到才看得到。
 * 本页把词典变成可主动浏览的形态：分类分组 + 中英文/缩写检索 + 点击手风琴展开进阶解释。
 *
 * 卡片全部复用 DefinitionCardModel 与既有渲染器，本页不新造任何解释类组件。
 */
@Page(Routes.GLOSSARY, supportInLocal = true)
internal class GlossaryPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private var query: String by observable("")
    private var activeCategory: GlossaryCategory? by observable(null)
    private var rows: ObservableList<GlossaryRow> by observableList()
    private var expandedKey: String by observable("")
    private var explanationDepth: ExplanationDepth by observable(ExplanationDepth.PARAGRAPH)

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        applyFilter()
    }

    override fun body(): ViewBuilder {
        val page = this
        // 非受控铁律：TextArea 的 text 只作为挂载种子，绝不绑定响应式文本，
        // 否则会形成 native→observable→prop 的回声循环（光标消失/退格错乱）。
        val searchSeed = page.query
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(32f)
                }
                View {
                    attr {
                        height(38f)
                        marginBottom(10f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(11f)
                        paddingRight(11f)
                        borderRadius(10f)
                        backgroundColor(page.theme.surfaceMuted)
                    }
                    Text { attr { text("⌕"); fontSize(15f); color(page.theme.textTertiary) } }
                    TextArea {
                        attr {
                            flex(1f)
                            marginLeft(6f)
                            height(36f)
                            fontSize(13f)
                            color(page.theme.textPrimary)
                            backgroundColor(Color(0xFFFFFFFF, 0f))
                            text(searchSeed)
                            placeholder("搜索术语：市盈率 / PE / 换手")
                            placeholderColor(page.theme.textTertiary)
                            tintColor(page.theme.brand)
                            selectionColor(page.theme.brand)
                        }
                        event {
                            textDidChange(isSyncEdit = true) { state ->
                                page.query = state.text
                                page.applyFilter()
                            }
                        }
                    }
                }

                View {
                    attr { flexDirectionRow(); marginBottom(12f) }
                    ExplanationDepth.values().forEach { depth ->
                        GlossaryFilterChip(
                            label = depth.label,
                            theme = page.theme,
                            selected = { page.explanationDepth == depth },
                            onTap = { page.explanationDepth = depth },
                            container = this,
                        )
                    }
                }

                View {
                    attr { flexDirectionRow(); marginBottom(12f) }
                    GlossaryFilterChip(
                        label = "全部",
                        theme = page.theme,
                        selected = { page.activeCategory == null },
                        onTap = { page.activeCategory = null; page.applyFilter() },
                        container = this,
                    )
                    GlossaryCategory.values().forEach { category ->
                        GlossaryFilterChip(
                            label = category.label,
                            theme = page.theme,
                            selected = { page.activeCategory == category },
                            onTap = {
                                page.activeCategory = if (page.activeCategory == category) null else category
                                page.applyFilter()
                            },
                            container = this,
                        )
                    }
                }

                vfor({ page.rows }) { row ->
                    when (row) {
                        is GlossaryRow.Header -> GlossarySectionTitle(row.label, row.count, page.theme, this)
                        is GlossaryRow.Item -> {
                            val entry = row.entry
                            val key = "glossary:${entry.key}"
                            CardShell(
                                DefinitionCardModel(
                                    term = entry.term,
                                    plainText = entry.plain,
                                    example = entry.example,
                                    cardId = key,
                                    advanced = entry.advanced,
                                    category = entry.category.label,
                                    depth = page.explanationDepth,
                                ),
                                CardContext(
                                    theme = page.theme,
                                    density = CardDensity.COMPACT,
                                    onOpenStock = {},
                                    expanded = page.expandedKey == key,
                                    onToggleExpanded = {
                                        page.expandedKey = if (page.expandedKey == key) "" else key
                                    },
                                    cardKey = key,
                                ),
                            )
                        }
                    }
                }

                Text {
                    attr {
                        text("术语解释由股问整理，仅用于理解概念，不构成投资建议。")
                        marginTop(16f)
                        fontSize(11f)
                        lineHeight(17f)
                        color(page.theme.textTertiary)
                    }
                }
            }
            AppTopBar(
                title = "术语表",
                subtitle = "共 ${Glossary.all.size} 个常用概念，点击展开例子与进阶解释",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
        }
    }

    /** 检索 + 分类筛选后重建行数据；列表更新走 ObservableList，不依赖响应式重放。 */
    private fun applyFilter() {
        val matched = Glossary.search(query)
            .filter { entry -> activeCategory?.let { entry.category == it } ?: true }
        val built = mutableListOf<GlossaryRow>()
        if (matched.isEmpty()) {
            rows.clear()
            rows.add(GlossaryRow.Header("没有匹配的术语", 0))
            return
        }
        if (query.isBlank()) {
            // 浏览态按分类分组，组头吸顶由分组标题承担。
            GlossaryCategory.values().forEach { category ->
                val group = matched.filter { it.category == category }
                if (group.isEmpty()) return@forEach
                built.add(GlossaryRow.Header(category.label, group.size))
                group.forEach { built.add(GlossaryRow.Item(it)) }
            }
        } else {
            built.add(GlossaryRow.Header("搜索结果", matched.size))
            matched.forEach { built.add(GlossaryRow.Item(it)) }
        }
        rows.clear()
        built.forEach { rows.add(it) }
    }
}

internal sealed class GlossaryRow {
    data class Header(val label: String, val count: Int) : GlossaryRow()
    data class Item(val entry: GlossaryEntry) : GlossaryRow()
}

private fun GlossarySectionTitle(
    label: String,
    count: Int,
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { marginTop(12f); marginBottom(2f) }
        Text {
            attr {
                text(if (count > 0) "$label · $count" else label)
                fontSize(12f)
                fontWeightSemiBold()
                color(theme.term)
            }
        }
    }
}

private fun GlossaryFilterChip(
    label: String,
    theme: StockChatTheme,
    selected: () -> Boolean,
    onTap: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            marginRight(6f)
            paddingLeft(10f)
            paddingRight(10f)
            height(28f)
            allCenter()
            borderRadius(14f)
            backgroundColor(if (selected()) theme.brandSoft else theme.surfaceMuted)
        }
        Text {
            attr {
                text(label)
                fontSize(12f)
                color(if (selected()) theme.brand else theme.textSecondary)
            }
        }
        event { click { onTap() } }
    }
}
