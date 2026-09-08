package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.NewsItem
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.core.ProductConceptCardModel
import com.kuikly.stockchat.cards.stock.MarketCardRenderers
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.config.DataSourceConfig
import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.CardSheetHost
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.CARD_GALLERY, supportInLocal = true)
internal class CardGalleryPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private var expandedCardKey: String by observable("")
    private var sheetCard: CardModel? by observable(null)
    private var sheetPresented: Boolean by observable(false)
    private var sheetLevel: SheetLevel by observable(SheetLevel.HALF)
    private var sheetPanStartY = 0f
    private var drilledKeys: ObservableList<String> by observableList()
    private var subThreadCardKey: String by observable("")
    private var subThreadCollapsed: Boolean by observable(false)
    private var accordionShowcaseExpandedKey: String by observable("")
    // 2026-09-08：卡片画廊同样接真实行情（QuoteRepository 三级链），组件回归
    // 用真数字跑；数据未返回前用 Quote.placeholder 占位（价格 0，不含假数据）。
    private var liveQuote: Quote? by observable(null)
    private var liveCompare: Quote? by observable(null)

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        MarketCardRenderers.ensureRegistered()
        if (!DataSourceConfig.USE_REAL_MARKET_DATA) {
            // 模拟模式（原状态）：直接取 MockDataBank 种子行情。
            liveQuote = MockDataBank.quote("600519.SH")
            liveCompare = MockDataBank.quote("000858.SZ")
            return
        }
        // 真实模式：QuoteRepository 三级链，数据未返回前用 Quote.placeholder 占位（价格 0）。
        val dependencies = MarketDependencies.forPager(pagerId)
        dependencies.quoteRepository.load("600519.SH") { liveQuote = it.quote ?: Quote.placeholder("600519.SH", "贵州茅台") }
        dependencies.quoteRepository.load("000858.SZ") { liveCompare = it.quote ?: Quote.placeholder("000858.SZ", "五粮液") }
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                // 竖向 Scroller 水平 padding 会被双倍扣除，padding(14f) 后右 padding 清 0 对齐（同 ChatPage）。
                attr { flex(1f); padding(14f); paddingRight(0f); paddingTop(page.pagerData.statusBarHeight + 73f); paddingBottom(32f) }
                vbind({ page.liveQuote to page.liveCompare }) {
                    val quote = page.liveQuote ?: Quote.placeholder("600519.SH", "贵州茅台")
                    val compare = page.liveCompare ?: Quote.placeholder("000858.SZ", "五粮液")
                    val attribution = CardPayloadParser.parse("attribution", "{\"symbol\":\"600519.SH\"}") as AttributionIntent
                    val models = listOf(
                        StockQuoteCardModel(quote),
                        StockChartCardModel(quote),
                        AttributionCardModel(quote, attribution.direction, attribution.factors),
                        DefinitionCardModel("市盈率 PE", "股价相对于每股收益的倍数，用来观察估值水平。", "同行业比较通常比跨行业比较更有意义。"),
                        InsightCardModel(quote, "这是卡片画廊中的示例解读，用于独立验证卡片外壳、主题和密度。"),
                        StockCompareCardModel(listOf(quote, compare)),
                        NewsCardModel(quote, listOf(
                            NewsItem("公司发布近期经营情况说明", "公司公告", "2 小时前"),
                            NewsItem("白酒板块盘中震荡，龙头股表现分化", "公开资讯", "3 小时前"),
                        )),
                        ProductConceptCardModel(
                            title = "交易台账 / AI 复盘",
                            value = "买入前只记录事实与原始理由，卖出或复盘时逐条核对哪些逻辑已经变化。",
                            flow = listOf("记录日期、标的与三条原始理由", "自动关联后续公告和财报", "复盘只核对逻辑变化，不评价买卖对错"),
                            boundary = "不连接券商、不代下单、不输出收益承诺。",
                            cardId = "concept:journal",
                        ),
                        ProductConceptCardModel(
                            title = "持仓结构分析",
                            value = "导入持仓后只解释集中度、行业分布和共同风险暴露，让用户看见组合里重复承担的风险。",
                            flow = listOf("本地录入持仓与成本", "聚合行业、市值与波动暴露", "用中性语言解释集中风险"),
                            boundary = "不推荐调仓比例，不给个股买卖建议。",
                            cardId = "concept:portfolio",
                        ),
                    )
                    AccordionShowcase(
                        quote = quote,
                        theme = page.theme,
                        expandedKey = { page.accordionShowcaseExpandedKey },
                        onToggleExpanded = { key ->
                            page.accordionShowcaseExpandedKey = if (page.accordionShowcaseExpandedKey == key) "" else key
                        },
                    )
                    Text {
                        attr {
                            text("同一套模型与渲染器可在聊天、详情和迷你预览中复用。")
                            marginTop(16f)
                            fontSize(12f)
                            lineHeight(18f)
                            color(page.theme.textSecondary)
                        }
                    }
                    models.forEach { model ->
                        GalleryCard(
                            model = model,
                            theme = page.theme,
                            expandedCardKey = { page.expandedCardKey },
                            drilledKeys = page.drilledKeys.toSet(),
                            subThreadCardKey = page.subThreadCardKey,
                            subThreadCollapsed = page.subThreadCollapsed,
                            onToggleExpanded = { page.expandedCardKey = if (page.expandedCardKey == it) "" else it },
                            onOpenSheet = { page.openSheet(it) },
                            onToggleDrill = { page.toggleDrill(it) },
                            onStartSubThread = { page.openSubThread(it.cardId) },
                            onToggleSubThread = { page.subThreadCollapsed = !page.subThreadCollapsed },
                            glass = page.hostGlassRenderer,
                        )
                    }
                }
            }
            AppTopBar(
                title = "卡片画廊",
                subtitle = "独立预览与组件回归",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
            vif({ page.sheetCard != null }) {
                page.sheetCard?.let { model ->
                    CardSheetHost(
                        model = model,
                        level = page.sheetLevel,
                        theme = page.theme,
                        renderer = page.hostGlassRenderer,
                        presented = page.sheetPresented,
                        viewportHeight = page.pagerData.pageViewHeight,
                        bottomInset = page.pagerData.safeAreaInsets.bottom,
                        onDismiss = { page.dismissSheet() },
                        onLower = { page.lowerSheet() },
                        onRaise = { page.raiseSheet() },
                        onPan = { state, y -> page.handleSheetPan(state, y) },
                        onOpenStock = {},
                        onTerm = {},
                    )
                }
            }
        }
    }

    private fun openSheet(model: CardModel) {
        sheetCard = model
        sheetLevel = if (model.cardType == "stock-chart") SheetLevel.FULL else SheetLevel.HALF
        sheetPresented = false
        setTimeout(16) { sheetPresented = true }
    }

    private fun dismissSheet() {
        sheetPresented = false
        setTimeout(420) {
            if (!sheetPresented) sheetCard = null
        }
    }

    private fun raiseSheet() {
        sheetLevel = when (sheetLevel) {
            SheetLevel.PEEK -> SheetLevel.HALF
            SheetLevel.HALF -> SheetLevel.FULL
            SheetLevel.FULL -> SheetLevel.FULL
        }
    }

    private fun lowerSheet() {
        when (sheetLevel) {
            SheetLevel.FULL -> sheetLevel = SheetLevel.HALF
            SheetLevel.HALF -> sheetLevel = SheetLevel.PEEK
            SheetLevel.PEEK -> dismissSheet()
        }
    }

    private fun handleSheetPan(state: String, y: Float) {
        when (state) {
            "start" -> sheetPanStartY = y
            "end" -> when {
                y - sheetPanStartY <= -28f -> raiseSheet()
                y - sheetPanStartY >= 28f -> lowerSheet()
            }
        }
    }

    private fun toggleDrill(key: String) {
        val index = drilledKeys.indexOf(key)
        if (index >= 0) drilledKeys.removeAt(index) else drilledKeys.add(key)
    }

    private fun openSubThread(cardId: String) {
        subThreadCardKey = cardId
        subThreadCollapsed = false
    }
}

private fun ViewContainer<*, *>.AccordionShowcase(
    quote: com.kuikly.stockchat.data.provider.Quote,
    theme: StockChatTheme,
    expandedKey: () -> String,
    onToggleExpanded: (String) -> Unit,
) {
    Text {
        attr {
            text("手风琴")
            fontSize(15f)
            fontWeightSemiBold()
            color(theme.textPrimary)
        }
    }
    listOf(
        StockQuoteCardModel(quote, cardId = "accordion-demo:quote"),
        StockChartCardModel(quote, cardId = "accordion-demo:chart"),
    ).forEach { model ->
        val key = "accordion-showcase:${model.cardId}"
        vif({ expandedKey() == key }) {
            CardShell(
                model,
                CardContext(
                    theme = theme,
                    density = CardDensity.COMPACT,
                    onOpenStock = {},
                    expanded = true,
                    onToggleExpanded = { onToggleExpanded(key) },
                    cardKey = key,
                ),
            )
        }
        vif({ expandedKey() != key }) {
            CardShell(
                model,
                CardContext(
                    theme = theme,
                    density = CardDensity.COMPACT,
                    onOpenStock = {},
                    expanded = false,
                    onToggleExpanded = { onToggleExpanded(key) },
                    cardKey = key,
                ),
            )
        }
    }
}

private fun ViewContainer<*, *>.GalleryCard(
    model: CardModel,
    theme: StockChatTheme,
    expandedCardKey: () -> String,
    drilledKeys: Set<String>,
    subThreadCardKey: String,
    subThreadCollapsed: Boolean,
    onToggleExpanded: (String) -> Unit,
    onOpenSheet: (CardModel) -> Unit,
    onToggleDrill: (String) -> Unit,
    onStartSubThread: (CardModel) -> Unit,
    onToggleSubThread: () -> Unit,
    glass: com.kuikly.stockchat.glass.GlassRenderer,
) {
    Text {
        attr {
            text(model.cardType)
            marginTop(18f)
            fontSize(13f)
            fontWeightSemiBold()
            color(theme.textSecondary)
        }
    }
    listOf(CardDensity.FULL, CardDensity.COMPACT, CardDensity.MINI).forEach { density ->
        val cardKey = "${model.cardId}:${density.name}"
        Text {
            attr {
                text(density.name)
                marginTop(8f)
                fontSize(9f)
                color(theme.textTertiary)
            }
        }
        if (density == CardDensity.MINI) {
            View {
                attr {
                    marginTop(4f)
                    padding(10f)
                    backgroundColor(theme.surface)
                    borderRadius(theme.cardRadius)
                }
                GalleryCardShell(model, theme, density, cardKey, expandedCardKey, drilledKeys, onToggleExpanded, onOpenSheet, onToggleDrill, onStartSubThread, glass)
            }
        } else {
            GalleryCardShell(model, theme, density, cardKey, expandedCardKey, drilledKeys, onToggleExpanded, onOpenSheet, onToggleDrill, onStartSubThread, glass)
        }
        if (model is InsightCardModel && density == CardDensity.COMPACT && subThreadCardKey == model.cardId) {
            GallerySubThread(theme, subThreadCollapsed, onToggleSubThread)
        }
    }
}

private fun ViewContainer<*, *>.GalleryCardShell(
    model: CardModel,
    theme: StockChatTheme,
    density: CardDensity,
    cardKey: String,
    expandedCardKey: () -> String,
    drilledKeys: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onOpenSheet: (CardModel) -> Unit,
    onToggleDrill: (String) -> Unit,
    onStartSubThread: (CardModel) -> Unit,
    glass: com.kuikly.stockchat.glass.GlassRenderer,
) {
    vif({ expandedCardKey() == cardKey }) {
        CardShell(model, GalleryCardContext(model, theme, density, cardKey, true, drilledKeys, onToggleExpanded, onOpenSheet, onToggleDrill, onStartSubThread, glass))
    }
    vif({ expandedCardKey() != cardKey }) {
        CardShell(model, GalleryCardContext(model, theme, density, cardKey, false, drilledKeys, onToggleExpanded, onOpenSheet, onToggleDrill, onStartSubThread, glass))
    }
}

private fun GalleryCardContext(
    model: CardModel,
    theme: StockChatTheme,
    density: CardDensity,
    cardKey: String,
    expanded: Boolean,
    drilledKeys: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onOpenSheet: (CardModel) -> Unit,
    onToggleDrill: (String) -> Unit,
    onStartSubThread: (CardModel) -> Unit,
    glass: com.kuikly.stockchat.glass.GlassRenderer,
) = CardContext(
    theme = theme,
    density = density,
    onOpenStock = {},
    expanded = expanded,
    onToggleExpanded = { onToggleExpanded(cardKey) },
    onOpenSheet = onOpenSheet,
    drilledKeys = drilledKeys,
    onToggleDrill = onToggleDrill,
    onStartSubThread = onStartSubThread,
    cardKey = cardKey,
    glass = glass,
)

private fun ViewContainer<*, *>.GallerySubThread(theme: StockChatTheme, collapsed: Boolean, onToggle: () -> Unit) {
    View {
        attr { marginTop(8f); marginLeft(16f); padding(10f); backgroundColor(theme.brandSoft); borderRadius(8f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text { attr { text("分支：AI 解读深挖"); fontSize(11f); fontWeightMedium(); color(theme.brand); flex(1f) } }
            Text { attr { text(if (collapsed) "展开" else "收起"); fontSize(11f); color(theme.brand) } }
            event { click { onToggle() } }
        }
        if (!collapsed) {
            Text { attr { text("这里承接该卡片的独立追问；聊天页会调用 AI 流式补全回答。") ; marginTop(7f); fontSize(12f); lineHeight(18f); color(theme.textSecondary) } }
        }
    }
}
