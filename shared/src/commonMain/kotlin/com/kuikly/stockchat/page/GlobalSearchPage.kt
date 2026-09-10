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
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
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
    private var query: String by observable("")
    private var selectedTab: String by observable(ALL_TAB)
    private var tabs: ObservableList<String> by observableList()
    private lateinit var dependencies: MarketDependencies
    private var searchGeneration = 0

    override fun created() {
        super.created()
        dependencies = MarketDependencies.forPager(pagerId)
        tabs.clear()
        (listOf(ALL_TAB) + Securities.categories).forEach(tabs::add)
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

                // 搜索框：纯色胶囊
                View {
                    attr {
                        height(42f); flexDirectionRow(); alignItemsCenter()
                        paddingLeft(14f); paddingRight(14f); borderRadius(21f)
                        backgroundColor(page.theme.surfaceMuted)
                    }
                    Text { attr { text("⌕"); fontSizeScaled(18f); color(page.theme.textTertiary) } }
                    TextArea {
                        attr {
                            flex(1f); marginLeft(7f); fontSizeScaled(14f); lineHeightScaled(21f)
                            color(page.theme.textPrimary); backgroundColor(Color(0xFFFFFFFF, 0f))
                            // 垂直居中关键（同 ChatPage 折叠态范式）：原生 TextArea 恒为
                            // TOP|START 顶对齐，让盒子收缩到单行内容高度，顶对齐即等于
                            // 居中，再由外层 alignItemsCenter 放进 42 高的行里。
                            minHeight(21f)
                            maxHeight(40f)
                            placeholder("代码 / 名称 / 拼音首字母 / 术语")
                            placeholderColor(page.theme.textTertiary); tintColor(page.theme.brand); selectionColor(page.theme.brand)
                        }
                        event { textDidChange(isSyncEdit = true) { page.onQueryChanged(it.text) } }
                    }
                }

                // 市场分类 tab：仅在浏览态（无关键词）显示；输入关键词即全局搜索全部市场
                vif({ page.query.isBlank() }) {
                    Scroller {
                        // 横向 Scroller 必须显式高度
                        attr { height(44f); flexDirectionRow(); alignItemsCenter(); marginTop(14f); marginBottom(2f); paddingRight(14f) }
                        vfor({ page.tabs }) { tab ->
                            View {
                                attr {
                                    height(34f); paddingLeft(16f); paddingRight(16f); allCenter()
                                    borderRadius(17f); marginRight(8f)
                                    // 条件属性全量赋值（R6）：选中态与未选中态各自完整着色，
                                    // 取消选中时阴影同步清零，不会残留上次值。
                                    val selected = page.selectedTab == tab
                                    if (selected) {
                                        backgroundColor(page.theme.brand)
                                        boxShadow(BoxShadow(0f, 4f, 12f, page.theme.brand.opacity(0.35f)))
                                    } else {
                                        backgroundColor(page.theme.surfaceMuted)
                                        boxShadow(BoxShadow(0f, 0f, 0f, Color(0x000000, 0f)))
                                    }
                                }
                                Text {
                                    attr {
                                        text(tab); fontSizeScaled(12.5f); fontWeightMedium()
                                        color(if (page.selectedTab == tab) Color(0xFFFFFFFF, 1f) else page.theme.textSecondary)
                                    }
                                }
                                event { click { page.selectTab(tab) } }
                            }
                        }
                    }
                }

                InsightSectionTitle("股票与指数", "名称、代码、全拼与首字母", page.theme)
                vfor({ page.securities }) { security ->
                    View {
                        attr {
                            marginBottom(8f); padding(14f); borderRadius(16f)
                            backgroundColor(page.theme.surface)
                            boxShadow(BoxShadow(0f, 3f, 10f, Color(0x000000, 0.07f)))
                            flexDirectionRow(); alignItemsCenter()
                        }
                        View {
                            attr { flex(1f) }
                            Text { attr { text(security.name); fontSizeScaled(14f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                            Text { attr { text(security.symbol); marginTop(3f); fontSizeScaled(10.5f); color(page.theme.textTertiary) } }
                        }
                        vif({ security.market.isNotEmpty() }) {
                            View {
                                attr {
                                    height(22f); paddingLeft(9f); paddingRight(9f); allCenter()
                                    borderRadius(11f); backgroundColor(page.theme.surfaceMuted); marginRight(9f)
                                }
                                Text { attr { text(security.market); fontSizeScaled(10f); color(page.theme.brand) } }
                            }
                        }
                        Text { attr { text("›"); fontSizeScaled(15f); color(page.theme.textTertiary) } }
                        event { click { page.openStockDetail(security.symbol, Routes.SEARCH) } }
                    }
                }
                vif({ page.securities.size == 0 }) {
                    View {
                        attr {
                            height(88f); allCenter(); borderRadius(16f); backgroundColor(page.theme.surfaceMuted)
                        }
                        Text { attr { text("没有匹配的标的，换个关键词试试"); fontSizeScaled(12.5f); color(page.theme.textTertiary) } }
                    }
                }

                InsightSectionTitle("术语", "点开查看人话解释与例子", page.theme)
                vfor({ page.terms }) { entry ->
                    View {
                        attr {
                            marginBottom(8f); padding(14f); borderRadius(16f)
                            backgroundColor(page.theme.surface)
                            boxShadow(BoxShadow(0f, 3f, 10f, Color(0x000000, 0.07f)))
                        }
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

    private fun onQueryChanged(text: String) {
        query = text
        search(text)
    }

    private fun selectTab(tab: String) {
        if (selectedTab == tab) return
        selectedTab = tab
        if (query.isNotBlank()) return
        // 使仍在途的远端合并失效，避免把搜索结果混进分类浏览列表
        searchGeneration++
        fillBrowse()
    }

    private fun search(queryText: String) {
        if (queryText.isBlank()) {
            searchGeneration++
            fillBrowse()
            terms.clear(); Glossary.search("", 12).forEach(terms::add)
            return
        }
        val local = Securities.search(queryText, 20)
        securities.clear(); local.forEach(securities::add)
        terms.clear(); Glossary.search(queryText, 12).forEach(terms::add)
        val generation = ++searchGeneration
        setTimeout(280) {
            if (generation != searchGeneration) return@setTimeout
            dependencies.securitySearchProvider.searchSecurities(queryText) { remote ->
                if (generation != searchGeneration || remote.isEmpty()) return@searchSecurities
                val merged = (remote + local).distinctBy(Security::symbol).take(20)
                securities.clear(); merged.forEach(securities::add)
            }
        }
    }

    /** 浏览态：按当前 tab 的市场分类填充标的列表 */
    private fun fillBrowse() {
        val tab = selectedTab
        securities.clear()
        Securities.all
            .filter { tab == ALL_TAB || it.market == tab }
            .forEach(securities::add)
    }

    private companion object {
        const val ALL_TAB = "全部"
    }
}
