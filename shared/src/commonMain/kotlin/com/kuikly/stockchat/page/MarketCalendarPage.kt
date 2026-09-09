package com.kuikly.stockchat.page

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.SourceStampLine
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.CALENDAR, supportInLocal = true)
internal class MarketCalendarPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private var events: ObservableList<MarketCalendarEvent> by observableList()

    override fun created() {
        super.created()
        // 数据源开关：模拟模式预填演示日历（原状态）；真实模式 provider 返回空 = 空态。
        OfflineMarketInsightProvider().calendarValue().forEach(events::add)
        dependencies.insightRepository.loadCalendar { values -> events.clear(); values.forEach(events::add) }
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                // 竖向 Scroller 水平 padding 会被双倍扣除，14/14 时右侧多出 28dp 留白；
                // 右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                attr { flex(1f); paddingLeft(14f); paddingRight(0f); paddingTop(page.pagerData.statusBarHeight + 73f); paddingBottom(70f) }
                View {
                    attr { padding(14f); borderRadius(14f); backgroundColor(page.theme.brandSoft) }
                    Text { attr { text("把重要日期放到判断之前"); fontSizeScaled(14f); fontWeightSemiBold(); color(page.theme.brand) } }
                    Text { attr { text("财报、打新、分红和解禁会改变信息密度。这里提醒你何时核对事实，不预测事件后的涨跌。"); marginTop(6f); fontSizeScaled(12f); lineHeightScaled(18f); color(page.theme.textSecondary) } }
                }
                vfor({ page.events }) { item ->
                    View {
                        attr { marginTop(10f); padding(14f); borderRadius(13f); backgroundColor(page.theme.surface); flexDirectionRow() }
                        View {
                            attr { width(74f) }
                            Text { attr { text(item.date.drop(5)); fontSizeScaled(16f); fontWeightBold(); color(page.theme.textPrimary) } }
                            Text { attr { text(item.kind.label); marginTop(4f); fontSizeScaled(10f); color(page.theme.brand) } }
                        }
                        View {
                            attr { flex(1f) }
                            Text { attr { text("${item.name} · ${item.title}"); fontSizeScaled(13f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                            Text { attr { text(item.symbol); marginTop(4f); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                            SourceStampLine(item.stamp, page.theme)
                        }
                        event { click { if (item.symbol.contains('.')) page.openStockDetail(item.symbol, Routes.CALENDAR) } }
                    }
                }
            }
            AppTopBar(
                title = "市场日历",
                subtitle = "先知道什么时候要重新核对事实",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
        }
    }
}
