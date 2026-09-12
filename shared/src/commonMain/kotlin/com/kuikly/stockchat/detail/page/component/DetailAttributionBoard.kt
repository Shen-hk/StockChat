package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.page.AttributionForecastWorkbench
import com.kuikly.stockchat.page.RevealBlock
import com.kuikly.stockchat.page.SectionLabel
import com.kuikly.stockchat.page.detail.FactorSpec
import com.tencent.kuikly.core.base.ViewContainer

/**
 * Wave 2 D5 第六组件：涨跌归因 × AI 走势推演工作台。从 StockDetailPage.body()
 * 搬出原 lines 648-665（RevealBlock 10），零行为变更。
 *
 * 工作台内部 factors/actualPct/quote/mainFlow 均通过闭包传入——observable 在
 * attr/vif 闭包内由 R1 建立反应式依赖（详见 Kuikly AGENTS.md）。
 */
internal fun ViewContainer<*, *>.DetailAttributionBoard(
    theme: StockChatTheme,
    reduceMotion: Boolean,
    entranceVisible: () -> Boolean,
    revealIndex: Int,
    quote: () -> Quote,
    mainFlow: () -> Double?,
    containerWidth: Float,
) {
    RevealBlock(revealIndex, entranceVisible, reduceMotion) {
        SectionLabel("涨跌归因与走势推演", theme, strong = true)
        AttributionForecastWorkbench(
            theme = theme,
            factors = listOf(
                FactorSpec("资金面", -0.30),
                FactorSpec("板块联动", -0.14),
                FactorSpec("市场整体", 0.05),
                FactorSpec("个股事件", -0.23),
            ),
            actualPct = { quote().changePercent },
            quote = quote,
            mainFlow = mainFlow,
            containerWidth = containerWidth,
            reduceMotion = reduceMotion,
        )
    }
}