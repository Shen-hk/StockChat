package com.kuikly.stockchat.cards.stock

import com.kuikly.stockchat.cards.core.BillboardCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.CardRegistry
import com.kuikly.stockchat.cards.core.CardRenderer
import com.kuikly.stockchat.cards.core.CorporateActionCardModel
import com.kuikly.stockchat.cards.core.DisclosureCardModel
import com.kuikly.stockchat.cards.core.FinancialCardModel
import com.kuikly.stockchat.cards.core.FundFlowCardModel
import com.kuikly.stockchat.cards.core.ShareholderCardModel
import com.kuikly.stockchat.cards.core.ProductConceptCardModel
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.page.components.SourceStampLine
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

object MarketCardRenderers {
    private var registered = false
    fun ensureRegistered() {
        if (registered) return
        registered = true
        listOf(FundFlowRenderer, FinancialRenderer, ShareholderRenderer, BillboardRenderer, CorporateActionRenderer, DisclosureRenderer, ProductConceptRenderer)
            .forEach(CardRegistry::register)
    }
}

private object ProductConceptRenderer : CardRenderer {
    override val cardType = "product-concept"
    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        val value = model as? ProductConceptCardModel ?: return
        container.CardTitle(value.title, "概念验证", context)
        container.Text { attr { text(value.value); fontSize(12f); lineHeight(18f); color(context.theme.textSecondary) } }
        value.flow.forEachIndexed { index, step ->
            container.Text { attr { text("${index + 1}. $step"); marginTop(6f); fontSize(11f); color(context.theme.textPrimary) } }
        }
        container.Text { attr { text("边界：${value.boundary}"); marginTop(10f); fontSize(10f); lineHeight(16f); color(context.theme.fall) } }
    }
}

private object FundFlowRenderer : CardRenderer {
    override val cardType = "fund-flow"
    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        val value = (model as? FundFlowCardModel)?.value ?: return
        container.CardTitle("资金流", "主力口径", context)
        container.MetricRow(listOf("主力" to value.main, "超大单" to value.superLarge, "大单" to value.large), context)
        container.Text { attr { text(value.explanation); marginTop(8f); fontSize(11.5f); lineHeight(18f); color(context.theme.textSecondary) } }
        container.SourceStampLine(value.stamp, context.theme)
    }
}

private object FinancialRenderer : CardRenderer {
    override val cardType = "financial"
    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        val value = (model as? FinancialCardModel)?.value ?: return
        container.CardTitle("财务", value.reportDate, context)
        container.MetricRow(listOf("营收" to value.revenue, "归母净利" to value.netProfit), context, amount = true)
        container.View {
            attr { marginTop(8f); flexDirectionRow() }
            Text { attr { text("营收同比 ${Format.percent(value.revenueYoY)}"); flex(1f); fontSize(11f); color(if (value.revenueYoY >= 0) context.theme.rise else context.theme.fall) } }
            Text { attr { text("利润同比 ${Format.percent(value.profitYoY)}"); flex(1f); fontSize(11f); color(if (value.profitYoY >= 0) context.theme.rise else context.theme.fall) } }
        }
        container.Text { attr { text("EPS ${Format.decimal(value.eps, 2)} · ROE ${Format.percent(value.roe)} · 毛利率 ${Format.percent(value.grossMargin)}"); marginTop(7f); fontSize(10.5f); color(context.theme.textTertiary) } }
        container.Text { attr { text(value.explanation); marginTop(8f); fontSize(11.5f); lineHeight(18f); color(context.theme.textSecondary) } }
        container.SourceStampLine(value.stamp, context.theme)
    }
}

private object ShareholderRenderer : CardRenderer {
    override val cardType = "shareholders"
    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        val value = (model as? ShareholderCardModel)?.value ?: return
        container.CardTitle("股东户数", value.period, context)
        container.Text { attr { text("${value.holders} 户"); fontSize(20f); fontWeightBold(); color(context.theme.textPrimary) } }
        container.Text { attr { text("较上期 ${if (value.change >= 0) "+" else ""}${value.change} 户 · ${Format.percent(value.changePercent)}"); marginTop(4f); fontSize(11f); color(if (value.change > 0) context.theme.fall else context.theme.rise) } }
        container.Text { attr { text(value.explanation); marginTop(8f); fontSize(11.5f); lineHeight(18f); color(context.theme.textSecondary) } }
        container.SourceStampLine(value.stamp, context.theme)
    }
}

private object BillboardRenderer : CardRenderer {
    override val cardType = "billboard"
    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        val value = (model as? BillboardCardModel)?.value ?: return
        container.CardTitle("龙虎榜", value.tradeDate, context)
        container.Text { attr { text(value.reason); fontSize(11.5f); lineHeight(18f); color(context.theme.textSecondary) } }
        container.MetricRow(listOf("买入" to value.buyAmount, "卖出" to value.sellAmount, "净额" to value.netAmount), context, amount = true)
        container.Text { attr { text("龙虎榜只记录触发异动规则的交易日；没有近期记录不代表没有机构交易。"); marginTop(7f); fontSize(10.5f); color(context.theme.textTertiary) } }
        container.SourceStampLine(value.stamp, context.theme)
    }
}

private object CorporateActionRenderer : CardRenderer {
    override val cardType = "corporate-actions"
    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        val values = (model as? CorporateActionCardModel)?.values ?: return
        container.CardTitle("分红与解禁", "公司行为", context)
        values.take(3).forEach { item ->
            container.View {
                attr { marginTop(7f) }
                Text { attr { text(item.title); fontSize(11.5f); color(context.theme.textPrimary) } }
                Text { attr { text("${item.date} · ${item.status}"); marginTop(2f); fontSize(9.5f); color(context.theme.textTertiary) } }
            }
        }
        values.firstOrNull()?.let { container.SourceStampLine(it.stamp, context.theme) }
    }
}

private object DisclosureRenderer : CardRenderer {
    override val cardType = "disclosures"
    override fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext) {
        val values = (model as? DisclosureCardModel)?.values ?: return
        container.CardTitle("公告与研报", "摘要不替代原文", context)
        values.take(if (context.density.name == "FULL") 7 else 3).forEachIndexed { index, item ->
            container.View {
                attr { if (index > 0) marginTop(12f); paddingBottom(9f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(item.kind.label); fontSize(9.5f); color(if (item.kind.name == "ANNOUNCEMENT") context.theme.brand else context.theme.term) } }
                    Text { attr { text("${item.publisher} · ${item.date}"); marginLeft(7f); fontSize(9.5f); color(context.theme.textTertiary) } }
                }
                Text { attr { text(item.title); marginTop(5f); fontSize(12f); fontWeightSemiBold(); color(context.theme.textPrimary) } }
                Text { attr { text(item.summary); marginTop(5f); fontSize(10.8f); lineHeight(17f); color(context.theme.textSecondary) } }
                Text { attr { text(item.riskLabel); marginTop(5f); fontSize(9.5f); color(if (item.riskLabel == "关注风险") context.theme.fall else context.theme.brand) } }
                SourceStampLine(item.stamp, context.theme)
            }
        }
    }
}

private fun ViewContainer<*, *>.CardTitle(title: String, subtitle: String, context: CardContext) {
    View {
        attr { marginBottom(9f); flexDirectionRow(); alignItemsCenter() }
        Text { attr { text(title); flex(1f); fontSize(14f); fontWeightBold(); color(context.theme.textPrimary) } }
        Text { attr { text(subtitle); fontSize(9.5f); color(context.theme.textTertiary) } }
    }
}

private fun ViewContainer<*, *>.MetricRow(values: List<Pair<String, Double>>, context: CardContext, amount: Boolean = false) {
    View {
        attr { marginTop(6f); flexDirectionRow() }
        values.forEach { (label, value) ->
            View {
                attr { flex(1f) }
                Text { attr { text(label); fontSize(9.5f); color(context.theme.textTertiary) } }
                Text {
                    attr {
                        text(if (amount) Format.compactAmount(value) else Format.compactAmount(value))
                        marginTop(3f); fontSize(12f); fontWeightSemiBold()
                        color(if (value >= 0) context.theme.rise else context.theme.fall)
                    }
                }
            }
        }
    }
}
