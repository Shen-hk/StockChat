package com.kuikly.stockchat.page

import com.kuikly.stockchat.foundation.ui.fontSizeScaled

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.PlatformProfile
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.app.assembly.MarketDependencies
import com.kuikly.stockchat.app.assembly.MarketFeatureGraph
import com.kuikly.stockchat.data.provider.HotspotSnapshot
import com.kuikly.stockchat.data.provider.LimitUpStock
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.SectorRank
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBar
import com.kuikly.stockchat.page.components.ExplanationCard
import com.kuikly.stockchat.page.components.InsightSectionTitle
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(Routes.HOTSPOTS, supportInLocal = true)
internal class HotspotPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { MarketFeatureGraph.forPager(pagerId) }
    private var sectors: ObservableList<SectorRank> by observableList()
    private var limitUps: ObservableList<LimitUpStock> by observableList()
    // 必须 observable：created() 先给离线快照、再看远端结果覆盖。此前是普通 var，
    // 而 ExplanationCard 在读在普通 builder 闭包里（非 attr/vbind），拿到的是首帧快照，
    // 远端解读回来也不会刷新——表现为「首启解读卡数据迟到」（退出重进才正确）。
    private var snapshot: HotspotSnapshot by observable(OfflineMarketInsightProvider().hotspotValue())

    override fun created() {
        super.created()
        // 数据源开关：模拟模式预填演示数据（原状态）；真实模式 provider 返回空 = 空态。
        apply(OfflineMarketInsightProvider().hotspotValue())
        dependencies.insightRepository.loadHotspots(::apply)
    }

    private fun apply(value: HotspotSnapshot) {
        snapshot = value
        sectors.clear(); value.sectors.take(20).forEach(sectors::add)
        limitUps.clear(); value.limitUps.take(60).forEach(limitUps::add)
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                // 竖向 Scroller 水平 padding 会被双倍扣除，14/14 时右侧多出 28dp 留白；
                // 右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                attr { flex(1f); paddingLeft(14f); paddingRight(0f); paddingTop(page.pagerData.statusBarHeight + 73f); paddingBottom(70f) }
                // R1：snapshot 在远端返回后变化，解读卡必须放在响应式闭包里才会刷新；
                // 同时带上换肤重建键（ExplanationCard 以参数捕获 theme）。
                // 2026-09-11：只在 PlatformProfile.marketFixes（当前 iOS）下走响应式重建，
                // 其余平台保持改动前的单帧读取语义（首启解读卡不刷新的老行为）。
                if (PlatformProfile.marketFixes) {
                    vbind({ page.snapshot to page.themeRebuildKey() }) {
                        ExplanationCard(page.snapshot.explanation, page.snapshot.stamp, page.theme)
                    }
                } else {
                    ExplanationCard(page.snapshot.explanation, page.snapshot.stamp, page.theme)
                }

                InsightSectionTitle("板块排行", "涨幅不是推荐，结合资金与涨跌家数看", page.theme)
                vfor({ page.sectors }) { sector ->
                    View {
                        attr { marginBottom(8f); padding(13f); borderRadius(13f); backgroundColor(page.theme.surface); flexDirectionRow(); alignItemsCenter() }
                        View {
                            attr { flex(1f) }
                            Text { attr { text(sector.name); fontSizeScaled(13f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                            Text { attr { text("主力 ${Format.compactAmount(sector.mainFlow)} · ${sector.risingCount} 涨 / ${sector.fallingCount} 跌"); marginTop(4f); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                        }
                        Text { attr { text(Format.percent(sector.changePercent)); fontSizeScaled(13f); fontWeightSemiBold(); color(if (sector.changePercent >= 0) page.theme.rise else page.theme.fall) } }
                    }
                }

                InsightSectionTitle("涨停池", "连板 / 封单 / 开板次数", page.theme)
                vfor({ page.limitUps }) { stock ->
                    View {
                        attr { marginBottom(8f); padding(13f); borderRadius(13f); backgroundColor(page.theme.surface); flexDirectionRow(); alignItemsCenter() }
                        View {
                            attr { flex(1f) }
                            View {
                                attr { flexDirectionRow(); alignItemsCenter() }
                                Text { attr { text(stock.name); fontSizeScaled(13f); fontWeightSemiBold(); color(page.theme.textPrimary) } }
                                View {
                                    attr { marginLeft(7f); paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f); borderRadius(6f); backgroundColor(page.theme.riseSoft) }
                                    Text { attr { text("${stock.consecutiveBoards} 连板"); fontSizeScaled(9f); color(page.theme.rise) } }
                                }
                            }
                            Text { attr { text("${stock.sector} · 封单 ${Format.compactAmount(stock.sealedAmount)} · 开板 ${stock.openCount} 次"); marginTop(4f); fontSizeScaled(10f); color(page.theme.textTertiary) } }
                        }
                        Text { attr { text(Format.percent(stock.changePercent)); fontSizeScaled(12f); color(page.theme.rise) } }
                        event { click { page.openStockDetail(stock.symbol, Routes.HOTSPOTS) } }
                    }
                }
            }
            // AppTopBar 以参数捕获 theme（首帧快照）：挂重建键，换肤返回后随键翻转重建。
            vbind({ page.themeRebuildKey() }) {
            AppTopBar(
                title = "板块热点",
                subtitle = "热度归因，不做选股排序",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
            }
        }
    }
}

