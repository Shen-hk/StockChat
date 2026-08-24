package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.protocol.AttributionIntent
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.CardPayloadParser
import com.kuikly.stockchat.protocol.DefinitionIntent
import com.kuikly.stockchat.protocol.SkeletonBlock
import com.kuikly.stockchat.protocol.SymbolCardIntent
import com.kuikly.stockchat.protocol.UnknownIntent

object CardAssembler {
    fun assemble(block: CardBlock): CardModel {
        return when (val intent = CardPayloadParser.parse(block.type, block.payload)) {
            is SymbolCardIntent -> {
                val quote = MockDataBank.quote(intent.symbol) ?: MockDataBank.quote("600519.SH")!!
                when (intent.type) {
                    "stock-quote" -> StockQuoteCardModel(quote, block.id)
                    "stock-chart" -> StockChartCardModel(quote, block.id)
                    "insight" -> InsightCardModel(
                        quote,
                        "当前波动主要由短线资金与板块联动共同驱动，基本面判断仍需结合后续公告。",
                        block.id,
                    )
                    "stock-compare" -> StockCompareCardModel(
                        listOfNotNull(quote, MockDataBank.quote(if (quote.symbol == "000858.SZ") "600519.SH" else "000858.SZ")),
                        block.id,
                    )
                    "news" -> NewsCardModel(
                        quote,
                        listOf(
                            NewsItem("公司近期经营信息受到市场关注", "公司公告", "今天"),
                            NewsItem("所属板块盘中震荡，龙头表现分化", "公开资讯", "3 小时前"),
                            NewsItem("机构关注需求节奏与盈利质量", "研报摘要", "昨天"),
                        ),
                        block.id,
                    )
                    else -> UnknownCardModel(intent.type, block.payload, block.id)
                }
            }
            is DefinitionIntent -> DefinitionCardModel(intent.term, intent.plainText, intent.example, block.id)
            is AttributionIntent -> {
                val quote = MockDataBank.quote(intent.symbol) ?: MockDataBank.quote("600519.SH")!!
                AttributionCardModel(quote, intent.direction, intent.factors, block.id)
            }
            is UnknownIntent -> UnknownCardModel(intent.type, intent.rawPayload, block.id)
            else -> UnknownCardModel(block.type, block.payload, block.id)
        }
    }

    fun assemble(block: SkeletonBlock): CardModel = SkeletonCardModel(block.type, block.id)
}
