package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.WatchlistAddResult
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.entity.Security
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteRepositoryStore
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.page.components.AppTopBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

/**
 * 自选股列表页（12 号需求文档 FR-W2 / FR-W3）。
 *
 * 设计取向遵循「聊看一体」：入口放在抽屉而非独立 Tab，列表行复用 MINI 行情渲染器，
 * 点击直接进详情页；行情走三级降级链并诚实标注数据模式，不拿陈旧价格冒充实时。
 */
@Page(Routes.WATCHLIST, supportInLocal = true)
internal class WatchlistPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private val watchlistStore by lazy { WatchlistStore(pagerId) }
    private val quoteRepository by lazy { QuoteRepositoryStore.shared(pagerId) }
    private var rows: ObservableList<WatchlistRow> by observableList()
    private var candidates: ObservableList<Security> by observableList()
    private var hint: String by observable("")
    private var lastRemoved: WatchlistItem? = null
    private var dataModeLabel: String by observable("")

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        reload()
    }

    override fun body(): ViewBuilder {
        val page = this
        // 非受控铁律：TextArea 的 text 只作挂载种子，不绑定响应式文本。
        val searchSeed = ""
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(60f)
                }
                View {
                    attr {
                        height(38f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(11f)
                        paddingRight(11f)
                        borderRadius(10f)
                        backgroundColor(page.theme.surfaceMuted)
                    }
                    Text { attr { text("＋"); fontSize(15f); color(page.theme.textTertiary) } }
                    TextArea {
                        attr {
                            flex(1f)
                            marginLeft(6f)
                            height(36f)
                            fontSize(13f)
                            color(page.theme.textPrimary)
                            backgroundColor(Color(0xFFFFFFFF, 0f))
                            text(searchSeed)
                            placeholder("搜索股票加入自选：贵州茅台 / 600519")
                            placeholderColor(page.theme.textTertiary)
                            tintColor(page.theme.brand)
                            selectionColor(page.theme.brand)
                        }
                        event {
                            textDidChange(isSyncEdit = true) { state -> page.search(state.text) }
                        }
                    }
                }

                vif({ page.candidates.isNotEmpty() }) {
                    View {
                        attr { marginTop(8f) }
                        vfor({ page.candidates }) { security ->
                            WatchlistCandidateRow(
                                security = security,
                                theme = page.theme,
                                onAdd = { page.add(security) },
                                container = this,
                            )
                        }
                    }
                }

                vif({ page.hint.isNotEmpty() }) {
                    Text {
                        attr {
                            text(page.hint)
                            marginTop(10f)
                            fontSize(12f)
                            color(page.theme.term)
                        }
                        event { click { page.undoRemove() } }
                    }
                }

                vif({ page.rows.isEmpty() }) {
                    WatchlistEmptyState(theme = page.theme, container = this)
                }

                vfor({ page.rows }) { row ->
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); marginTop(10f) }
                        View {
                            attr { flex(1f) }
                            val quote = row.quote
                            if (quote != null) {
                                CardShell(
                                    StockQuoteCardModel(quote, cardId = "watchlist:${row.symbol}"),
                                    CardContext(
                                        theme = page.theme,
                                        density = CardDensity.MINI,
                                        onOpenStock = { page.openStockDetail(row.symbol, Routes.WATCHLIST) },
                                        cardKey = "watchlist:${row.symbol}",
                                    ),
                                )
                            } else {
                                WatchlistPendingRow(name = row.name, symbol = row.symbol, theme = page.theme, container = this)
                            }
                        }
                        View {
                            attr {
                                marginLeft(8f)
                                paddingLeft(10f)
                                paddingRight(10f)
                                height(30f)
                                allCenter()
                                borderRadius(8f)
                                backgroundColor(page.theme.surfaceMuted)
                            }
                            Text { attr { text("移除"); fontSize(12f); color(page.theme.textSecondary) } }
                            event {
                                click { page.remove(row.symbol) }
                            }
                        }
                    }
                }

                vif({ page.dataModeLabel.isNotEmpty() }) {
                    Text {
                        attr {
                            text(page.dataModeLabel)
                            marginTop(16f)
                            fontSize(11f)
                            lineHeight(17f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            }
            AppTopBar(
                title = "自选股",
                subtitle = "已关注 ${WatchlistStore.MAX_ITEMS} 只上限内的标的，点进详情可继续追问",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
        }
    }

    private fun search(text: String) {
        candidates.clear()
        if (text.isBlank()) return
        val already = watchlistStore.symbols().toSet()
        Securities.search(text, limit = 8)
            .filterNot { it.symbol in already }
            .forEach { candidates.add(it) }
    }

    private fun add(security: Security) {
        when (watchlistStore.add(security.symbol, security.name)) {
            WatchlistAddResult.ADDED -> hint = "已加入自选：${security.name}"
            WatchlistAddResult.ALREADY_IN -> hint = "${security.name} 已在自选中"
            WatchlistAddResult.FULL -> hint = "自选已满 ${WatchlistStore.MAX_ITEMS} 只，先移除一些吧"
        }
        lastRemoved = null
        candidates.clear()
        reload()
    }

    private fun remove(symbol: String) {
        val removed = watchlistStore.list().firstOrNull { it.symbol == symbol } ?: return
        watchlistStore.remove(symbol)
        lastRemoved = removed
        hint = "已移除 ${removed.name}，点此撤销"
        reload()
    }

    private fun undoRemove() {
        val item = lastRemoved ?: return
        when (watchlistStore.restore(item)) {
            WatchlistAddResult.ADDED -> hint = "已恢复 ${item.name}"
            WatchlistAddResult.ALREADY_IN -> hint = "${item.name} 已在自选中"
            WatchlistAddResult.FULL -> hint = "自选已满 ${WatchlistStore.MAX_ITEMS} 只，无法恢复"
        }
        lastRemoved = null
        reload()
    }

    /** 重建行数据并逐个拉取行情；缓存价先占位，网络结果到达后原地替换。 */
    private fun reload() {
        rows.clear()
        watchlistStore.list().forEach { item ->
            rows.add(WatchlistRow(item.symbol, item.name, quoteRepository.cachedOrOffline(item.symbol)))
            quoteRepository.load(item.symbol) { result ->
                val index = rows.indexOfFirst { it.symbol == item.symbol }
                val quote = result.quote
                if (index >= 0 && quote != null) {
                    rows[index] = rows[index].copy(quote = quote)
                }
                dataModeLabel = result.mode.quoteLabel()
            }
        }
    }
}

internal data class WatchlistRow(
    val symbol: String,
    val name: String,
    val quote: Quote?,
)

private fun WatchlistCandidateRow(
    security: Security,
    theme: StockChatTheme,
    onAdd: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(6f)
            padding(12f)
            borderRadius(12f)
            backgroundColor(theme.surface)
        }
        View {
            attr { flex(1f) }
            Text { attr { text(security.name); fontSize(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
            Text { attr { text(security.symbol); marginTop(2f); fontSize(10f); color(theme.textTertiary) } }
        }
        View {
            attr {
                paddingLeft(12f)
                paddingRight(12f)
                height(28f)
                allCenter()
                borderRadius(8f)
                backgroundColor(theme.brandSoft)
            }
            Text { attr { text("加自选"); fontSize(12f); fontWeightSemiBold(); color(theme.brand) } }
            event { click { onAdd() } }
        }
    }
}

private fun WatchlistPendingRow(
    name: String,
    symbol: String,
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { padding(14f); borderRadius(12f); backgroundColor(theme.surface) }
        Text { attr { text(name); fontSize(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
        Text { attr { text("$symbol · 行情加载中"); marginTop(4f); fontSize(11f); color(theme.textTertiary) } }
    }
}

private fun WatchlistEmptyState(
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { marginTop(28f); padding(18f); borderRadius(14f); backgroundColor(theme.surface) }
        Text { attr { text("还没有自选股"); fontSize(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        Text {
            attr {
                text("在上面搜索股票加入自选，也可以在聊天里长按股票名、或从股票详情页添加。加入后可以直接问「我的自选今天怎么样」。")
                marginTop(8f)
                fontSize(12.5f)
                lineHeight(19f)
                color(theme.textSecondary)
            }
        }
    }
}
