package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.cards.core.BillboardCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CorporateActionCardModel
import com.kuikly.stockchat.cards.core.CompanyOverviewCardModel
import com.kuikly.stockchat.cards.core.FinancialCardModel
import com.kuikly.stockchat.cards.core.FundFlowCardModel
import com.kuikly.stockchat.cards.core.ShareholderCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.detail.page.component.BalanceSpectrumBlock
import com.kuikly.stockchat.detail.domain.DetailCompanyProfileCatalog
import com.kuikly.stockchat.detail.domain.RelevanceAnchor
import com.kuikly.stockchat.detail.domain.materialityOf
import com.kuikly.stockchat.detail.domain.pickPinnedCard
import com.kuikly.stockchat.detail.domain.shareholderFootnote
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.View

/**
 * Wave 2 D5 第五组件：公司介绍/数据 tab + 公司数据业务卡 + 公告研报。
 * 从 StockDetailPage.body() 搬出原 lines 626-730，零行为变更；所有数据经
 * detailDataCoordinator (quote/insight) 的只读 getter 暴露，反应式依赖由
 * R1 在 vbind/vif 闭包内建立。
 *
 * 组件只做装配，交互回链到 StockDetailPage（selectCompanyInfoTab /
 * toastHint / detailOverlayCoordinator.showDisclosurePeek）。
 */
internal fun ViewContainer<*, *>.DetailCompanyInfoSection(
    theme: StockChatTheme,
    reduceMotion: Boolean,
    entranceVisible: () -> Boolean,
    tabPanelRevealIndex: Int,
    disclosureRevealIndex: Int,
    symbol: () -> String,
    quote: () -> Quote,
    insight: () -> StockInsightBundle,
    companyInfoTab: () -> Int,
    companyDataPresented: () -> Boolean,
    tabTrackWidth: Float,
    containerWidth: Float,
    wide: Boolean,
    ctx: CardContext,
    onSelectCompanyTab: (Int) -> Unit,
    onToastHint: (String) -> Unit,
    onShowDisclosurePeek: (DisclosureItem) -> Unit,
) {
    // 公司介绍 / 公司数据共享同一信息区；公司数据面承接下方事实卡。
    RevealBlock(tabPanelRevealIndex, entranceVisible, reduceMotion) {
        CompanyIndustryPanel(
            selectedTab = companyInfoTab,
            profile = { DetailCompanyProfileCatalog.forSymbol(symbol()) },
            quote = quote,
            theme = theme,
            reduceMotion = reduceMotion,
            tabTrackWidth = tabTrackWidth,
            onSelectTab = onSelectCompanyTab,
        )
    }

    // ---- 公司数据：只在右侧「公司数据」切面挂载。 ----
    vif({ companyInfoTab() == 1 }) {
        vbind({ insight() }) {
            val fundamentals = insight().fundamentals
            // E1 今日相关置顶：billboard 数据非空 → 该卡 pinnedToday（至多一张置顶）
            val relevanceAnchors = listOf(
                RelevanceAnchor("fund-flow", false, ""),
                RelevanceAnchor("financial", false, ""),
                RelevanceAnchor("shareholders", false, ""),
                RelevanceAnchor("billboard", fundamentals?.billboard != null, "今日上龙虎榜"),
                RelevanceAnchor("actions", false, ""),
            )
            val pinnedId = pickPinnedCard(relevanceAnchors, relevanceAnchors.map { it.cardId })
            // E2 注脚：仅用真实可得输入——股东户数户均变化在「总股本不变」假设下推导。
            val shareholderNote = fundamentals?.shareholder?.let { sh ->
                val denom = 100.0 + sh.changePercent
                val perHolder = if (denom != 0.0) -sh.changePercent / denom * 100.0 else 0.0
                shareholderFootnote(sh.changePercent, perHolder)
            }
            val businessCards = listOfNotNull(
                insight().fundFlow?.let { BusinessInsightItem("fund-flow", "资金流", FundFlowCardModel(it, "fund-flow:${symbol()}")) },
                fundamentals?.financial?.let { BusinessInsightItem("financial", "财务", FinancialCardModel(it, "financial:${symbol()}")) },
                DetailCompanyProfileCatalog.forSymbol(symbol()).let { profile ->
                    BusinessInsightItem(
                        "company-overview",
                        "主营与赛道",
                        CompanyOverviewCardModel(profile.summary, profile.tags, profile.focus, "company-overview:${symbol()}"),
                    )
                },
                fundamentals?.shareholder?.let {
                    BusinessInsightItem("shareholders", "股东户数", ShareholderCardModel(it, "shareholders:${symbol()}"), footnote = shareholderNote)
                },
                fundamentals?.billboard?.let {
                    BusinessInsightItem("billboard", "龙虎榜", BillboardCardModel(it, "billboard:${symbol()}"), pinned = "billboard" == pinnedId)
                },
                fundamentals?.actions?.takeIf { it.isNotEmpty() }?.let {
                    BusinessInsightItem("actions", "分红与解禁", CorporateActionCardModel(it, "actions:${symbol()}"))
                },
            ).let { cards -> if (pinnedId == null) cards else cards.sortedByDescending { it.pinned } }
            BusinessInsightGrid(
                items = businessCards,
                context = ctx,
                theme = theme,
                wide = wide,
                baseIndex = 0,
                entranceVisible = companyDataPresented,
                reduceMotion = reduceMotion,
                // E2 注脚点击 → 展示判定依据（U3 两步溯源）
                onFootnoteClick = { note -> onToastHint("${note.rationale} · 端侧规则") },
            )
        }
    }

    RevealBlock(disclosureRevealIndex, entranceVisible, reduceMotion) {
        SectionLabel("公告与研报", theme, strong = true)
        // F1+F3 同卡（原型 .ann-card）：公告要点与研报评级光谱是同一张卡
        // 的上下两段，对外是一个视图，中间不再隔两张卡的白边。
        View {
            attr {
                marginTop(8f)
                padding(12f)
                borderRadius(14f)
                backgroundColor(theme.surface)
                border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            }
            // vbind({insight})：公告列表随 insight 加载重建（R1）
            vbind({ insight() }) {
                // F1 公告要点 · 端侧评级（前 3 条，标题+徽章+日期，点击条目看判定依据）
                DisclosureMaterialityBlock(
                    items = insight().disclosures.take(3),
                    theme = theme,
                    inset = true,
                    onExplain = { title -> onToastHint(materialityOf(title).rule) },
                    onPeek = onShowDisclosurePeek,
                )
            }

            // ---- F3 多空观点光谱：并入公告与研报同一张卡（原型 .balance 在 ann-card 内） ----
            vbind({ insight() }) {
                BalanceSpectrumBlock(
                    theme = theme,
                    segments = balanceSegmentsFor(insight().ratingSpectrum, theme),
                    initialIndex = 0,
                    containerWidth = containerWidth,
                    reduceMotion = reduceMotion,
                    inset = true,
                )
            }
        }
    }
}
