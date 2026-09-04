package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.AlertRule
import com.kuikly.stockchat.data.AlertTrigger
import com.kuikly.stockchat.data.LocalAlertProvider
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.InsightSectionTitle
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.ALERTS, supportInLocal = true)
internal class AlertCenterPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private var rows: ObservableList<AlertRow> by observableList()
    private var candidates: ObservableList<WatchlistItem> by observableList()

    override fun created() {
        super.created()
        reload()
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr { flex(1f); paddingLeft(14f); paddingRight(14f); paddingTop(page.pagerData.statusBarHeight + 73f); paddingBottom(70f) }
                View {
                    attr { padding(14f); borderRadius(14f); backgroundColor(page.theme.brandSoft) }
                    Text { attr { text("预警之后，先解释为什么"); fontSize(14f); fontWeightSemiBold(); color(page.theme.brand) } }
                    Text { attr { text("应用在前台时刷新行情；达到阈值后直接展示归因检查顺序。当前 Demo 不做远程推送，也不提供买卖建议。"); marginTop(6f); fontSize(12f); lineHeight(18f); color(page.theme.textSecondary) } }
                }

                vif({ page.candidates.isNotEmpty() }) {
                    InsightSectionTitle("从自选添加", "默认监控日涨跌幅绝对值 ≥ 3%", page.theme)
                    vfor({ page.candidates }) { item ->
                        View {
                            attr { marginBottom(7f); padding(12f); borderRadius(12f); backgroundColor(page.theme.surface); flexDirectionRow(); alignItemsCenter() }
                            View {
                                attr { flex(1f) }
                                Text { attr { text(item.name); fontSize(13f); color(page.theme.textPrimary) } }
                                Text { attr { text(item.symbol); marginTop(2f); fontSize(10f); color(page.theme.textTertiary) } }
                            }
                            Text { attr { text("启用 ±3%"); fontSize(11f); fontWeightSemiBold(); color(page.theme.brand) } }
                            event { click { page.add(item) } }
                        }
                    }
                }

                InsightSectionTitle("监控中", "前台刷新 · 本地保存", page.theme)
                vif({ page.rows.isEmpty() }) {
                    View {
                        attr { padding(16f); borderRadius(13f); backgroundColor(page.theme.surface) }
                        Text { attr { text("还没有预警规则"); fontSize(14f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                        Text { attr { text("先到自选股添加关注标的，再回来设置阈值。"); marginTop(6f); fontSize(11.5f); color(page.theme.textSecondary) } }
                        Text { attr { text("打开自选股 ›"); marginTop(10f); fontSize(11f); color(page.theme.brand) } }
                        event { click { page.openPage(Routes.WATCHLIST) } }
                    }
                }
                vfor({ page.rows }) { row ->
                    View {
                        attr { marginBottom(9f); padding(14f); borderRadius(13f); backgroundColor(page.theme.surface) }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter() }
                            View {
                                attr { flex(1f) }
                                Text { attr { text(row.rule.name); fontSize(14f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                                Text { attr { text("${row.rule.symbol} · |涨跌幅| ≥ ${Format.percent(row.rule.thresholdPercent)}"); marginTop(3f); fontSize(10f); color(page.theme.textTertiary) } }
                            }
                            Text { attr { text(if (row.rule.enabled) "监控中" else "已暂停"); fontSize(10.5f); color(if (row.rule.enabled) page.theme.brand else page.theme.textTertiary) } }
                        }
                        row.quote?.let { quote ->
                            Text { attr { text("现价 ${Format.price(quote.price)} · ${Format.percent(quote.changePercent)}"); marginTop(9f); fontSize(12f); color(if (quote.rising) page.theme.rise else page.theme.fall) } }
                        }
                        row.trigger?.let { trigger ->
                            View {
                                attr { marginTop(9f); padding(11f); borderRadius(10f); backgroundColor(page.theme.brandSoft) }
                                Text { attr { text(trigger.title); fontSize(11.5f); fontWeightSemiBold(); color(page.theme.brand) } }
                                Text { attr { text(trigger.attribution); marginTop(5f); fontSize(11f); lineHeight(17f); color(page.theme.textSecondary) } }
                            }
                        }
                        View {
                            attr { marginTop(10f); flexDirectionRow() }
                            View {
                                attr { paddingRight(18f); paddingTop(5f); paddingBottom(5f) }
                                Text { attr { text(if (row.rule.enabled) "暂停" else "恢复"); fontSize(11f); color(page.theme.brand) } }
                                event { click { page.toggle(row.rule.symbol) } }
                            }
                            View {
                                attr { paddingRight(18f); paddingTop(5f); paddingBottom(5f) }
                                Text { attr { text("查看详情"); fontSize(11f); color(page.theme.brand) } }
                                event { click { page.openStockDetail(row.rule.symbol, Routes.ALERTS) } }
                            }
                            View {
                                attr { paddingTop(5f); paddingBottom(5f) }
                                Text { attr { text("删除"); fontSize(11f); color(page.theme.fall) } }
                                event { click { page.remove(row.rule.symbol) } }
                            }
                        }
                    }
                }
            }
            AppTopBar(
                title = "异动预警",
                subtitle = "阈值触发 + 归因提示，不做远程推送",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
        }
    }

    private fun add(item: WatchlistItem) {
        dependencies.alertStore.upsert(item.symbol, item.name)
        reload()
    }

    private fun toggle(symbol: String) { dependencies.alertStore.toggle(symbol); reload() }
    private fun remove(symbol: String) { dependencies.alertStore.remove(symbol); reload() }

    private fun reload() {
        rows.clear()
        val rules = dependencies.alertStore.list()
        val symbols = rules.map { it.symbol }.toSet()
        candidates.clear(); dependencies.watchlistStore.list().filterNot { it.symbol in symbols }.forEach(candidates::add)
        rules.forEach { rule ->
            val initial = dependencies.quoteRepository.cachedOrOffline(rule.symbol)
            rows.add(AlertRow(rule, initial, initial?.let { LocalAlertProvider.evaluate(rule, it) }))
            dependencies.quoteRepository.load(rule.symbol) { result ->
                val index = rows.indexOfFirst { it.rule.symbol == rule.symbol }
                val quote = result.quote
                if (index >= 0) rows[index] = rows[index].copy(quote = quote, trigger = quote?.let { LocalAlertProvider.evaluate(rule, it) })
            }
        }
    }
}

private data class AlertRow(val rule: AlertRule, val quote: Quote?, val trigger: AlertTrigger?)
