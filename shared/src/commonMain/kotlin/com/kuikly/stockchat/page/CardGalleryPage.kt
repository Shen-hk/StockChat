package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.NewsItem
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text

@Page(Routes.CARD_GALLERY, supportInLocal = true)
internal class CardGalleryPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
    }

    override fun body(): ViewBuilder {
        val page = this
        val quote = MockDataBank.quote("600519.SH")!!
        val attribution = CardPayloadParser.parse("attribution", "{\"symbol\":\"600519.SH\"}") as AttributionIntent
        val models = listOf(
            StockQuoteCardModel(quote),
            StockChartCardModel(quote),
            AttributionCardModel(quote, attribution.direction, attribution.factors),
            DefinitionCardModel("市盈率 PE", "股价相对于每股收益的倍数，用来观察估值水平。", "同行业比较通常比跨行业比较更有意义。"),
            InsightCardModel(quote, "这是卡片画廊中的示例解读，用于独立验证卡片外壳、主题和密度。"),
            StockCompareCardModel(listOf(quote, MockDataBank.quote("000858.SZ")!!)),
            NewsCardModel(quote, listOf(
                NewsItem("公司发布近期经营情况说明", "公司公告", "2 小时前"),
                NewsItem("白酒板块盘中震荡，龙头股表现分化", "公开资讯", "3 小时前"),
            )),
        )
        return {
            attr { backgroundColor(page.theme.page) }
            AppTopBar(
                title = "卡片画廊",
                subtitle = "独立预览与组件回归",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
            Scroller {
                attr { flex(1f); padding(14f); paddingBottom(32f) }
                Text {
                    attr {
                        text("同一套模型与渲染器可在聊天、详情和迷你预览中复用。")
                        fontSize(12f)
                        lineHeight(18f)
                        color(page.theme.textSecondary)
                    }
                }
                models.forEach { model ->
                    CardShell(model, CardContext(page.theme, CardDensity.COMPACT, { }))
                }
            }
        }
    }
}
