package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.entity.Security
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.InsightSectionTitle
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

@Page(Routes.SEARCH, supportInLocal = true)
internal class GlobalSearchPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private var securities: ObservableList<Security> by observableList()
    private var terms: ObservableList<GlossaryEntry> by observableList()
    private lateinit var dependencies: MarketDependencies
    private var searchGeneration = 0

    override fun created() {
        super.created()
        dependencies = MarketDependencies.forPager(pagerId)
        search("")
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                // 竖向 Scroller 水平 padding 会被双倍扣除（子项测量宽 = 视宽 - 2×(左+右)），
                // 14/14 时右侧实测多出 28dp 留白；右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                attr { flex(1f); paddingLeft(14f); paddingRight(0f); paddingTop(page.pagerData.statusBarHeight + 73f); paddingBottom(70f) }
                View {
                    attr { height(42f); flexDirectionRow(); alignItemsCenter(); paddingLeft(12f); paddingRight(12f); borderRadius(12f); backgroundColor(page.theme.surfaceMuted) }
                    Text { attr { text("⌕"); fontSizeScaled(18f); color(page.theme.textTertiary) } }
                    TextArea {
                        attr {
                            flex(1f); marginLeft(7f); fontSizeScaled(14f); lineHeightScaled(21f)
                            color(page.theme.textPrimary); backgroundColor(Color(0xFFFFFFFF, 0f))
                            // 垂直居中关键（同 ChatPage 折叠态范式）：原生 TextArea 恒为
                            // TOP|START 顶对齐（KRTextAreaView setGravity(51)），固定 40 高
                            // 盒子会让文本贴顶。让盒子收缩到单行内容高度（21f），顶对齐即
                            // 等于居中，再由外层 alignItemsCenter 把盒子放进 42 高的行里。
                            // minHeight 兜空态：空文本时原生内容高度为 0，占位符需可见。
                            minHeight(21f)
                            maxHeight(40f)
                            placeholder("代码 / 名称 / 拼音首字母 / 术语")
                            placeholderColor(page.theme.textTertiary); tintColor(page.theme.brand); selectionColor(page.theme.brand)
                        }
                        event { textDidChange(isSyncEdit = true) { page.search(it.text) } }
                    }
                }

                InsightSectionTitle("股票与指数", "名称、代码、全拼与首字母", page.theme)
                vfor({ page.securities }) { security ->
                    View {
                        attr { marginBottom(7f); padding(13f); borderRadius(12f); backgroundColor(page.theme.surface); flexDirectionRow(); alignItemsCenter() }
                        View {
                            attr { flex(1f) }
                            Text { attr { text(security.name); fontSizeScaled(13.5f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                            Text { attr { text(security.symbol); marginTop(3f); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                        }
                        Text { attr { text("查看详情 ›"); fontSizeScaled(11f); color(page.theme.brand) } }
                        event { click { page.openStockDetail(security.symbol, Routes.SEARCH) } }
                    }
                }

                InsightSectionTitle("术语", "点开查看人话解释与例子", page.theme)
                vfor({ page.terms }) { entry ->
                    View {
                        attr { marginBottom(7f); padding(13f); borderRadius(12f); backgroundColor(page.theme.surface) }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter() }
                            Text { attr { text(entry.term); fontSizeScaled(13.5f); fontWeightSemiBold(); color(page.theme.term) } }
                            Text { attr { text(entry.ascii.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                        }
                        Text { attr { text(entry.plain); marginTop(5f); fontSizeScaled(11.5f); lineHeightScaled(17f); color(page.theme.textSecondary) } }
                        event { click { page.openPage(Routes.GLOSSARY) } }
                    }
                }
            }
            // AppTopBar 以参数捕获 theme（首帧快照）：挂重建键，换肤返回后随键翻转重建。
            vbind({ page.themeRebuildKey() }) {
            AppTopBar(
                title = "全局搜索",
                subtitle = "股票、指数和术语，一个入口",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
            }
        }
    }

    private fun search(query: String) {
        val local = Securities.search(query, 20)
        securities.clear(); local.forEach(securities::add)
        terms.clear(); Glossary.search(query, 12).forEach(terms::add)
        val generation = ++searchGeneration
        if (query.isBlank()) return
        setTimeout(280) {
            if (generation != searchGeneration) return@setTimeout
            dependencies.securitySearchProvider.searchSecurities(query) { remote ->
                if (generation != searchGeneration || remote.isEmpty()) return@searchSecurities
                val merged = (remote + local).distinctBy(Security::symbol).take(20)
                securities.clear(); merged.forEach(securities::add)
            }
        }
    }
}
