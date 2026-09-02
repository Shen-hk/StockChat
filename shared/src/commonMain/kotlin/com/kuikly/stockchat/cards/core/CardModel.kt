package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.protocol.FactorIntent
import com.kuikly.stockchat.data.provider.BillboardRecord
import com.kuikly.stockchat.data.provider.CorporateAction
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.FinancialSummary
import com.kuikly.stockchat.data.provider.FundFlow
import com.kuikly.stockchat.data.provider.ShareholderSnapshot

interface CardModel {
    val cardType: String
    val cardId: String
    val expandMode: ExpandMode
    val source: String get() = ""
    val asOf: String get() = ""
}

enum class ExpandMode {
    ACCORDION,
    BOTTOM_SHEET,
    DRILL_DOWN,
    NESTED_CONVERSATION,
    FOCUS_MAGNIFY,
    SIDE_BY_SIDE,
    STATIC,
}

data class StockQuoteCardModel(
    val quote: Quote,
    override val cardId: String = "stock-quote:${quote.symbol}",
) : CardModel {
    override val cardType: String = "stock-quote"
    override val expandMode: ExpandMode = ExpandMode.ACCORDION
    override val source: String get() = quote.source
    override val asOf: String get() = quote.timestamp
}

data class StockChartCardModel(
    val quote: Quote,
    val mode: StockChartMode = StockChartMode.TIMELINE,
    val period: StockChartPeriod = StockChartPeriod.DAY,
    override val cardId: String = "stock-chart:${quote.symbol}",
) : CardModel {
    override val cardType: String = "stock-chart"
    override val expandMode: ExpandMode = ExpandMode.ACCORDION
    override val source: String get() = quote.source
    override val asOf: String get() = quote.timestamp
}

enum class StockChartMode { TIMELINE, K_LINE }
enum class StockChartPeriod(val label: String, val grouping: Int) { DAY("日 K", 1), WEEK("周 K", 5), MONTH("月 K", 20) }

data class InsightCardModel(
    val quote: Quote,
    val summary: String,
    override val cardId: String = "insight:${quote.symbol}",
) : CardModel {
    override val cardType: String = "insight"
    override val expandMode: ExpandMode = ExpandMode.NESTED_CONVERSATION
    override val source: String get() = quote.source
    override val asOf: String get() = quote.timestamp
}

data class DefinitionCardModel(
    val term: String,
    val plainText: String,
    val example: String,
    override val cardId: String = "definition:$term",
    /** 进阶解释，仅在展开态（FULL）展示，服务有经验的用户。 */
    val advanced: String = "",
    val category: String = "",
    val depth: ExplanationDepth = ExplanationDepth.PARAGRAPH,
) : CardModel {
    override val cardType: String = "definition"
    override val expandMode: ExpandMode = ExpandMode.ACCORDION
}

data class AttributionCardModel(
    val quote: Quote,
    val direction: String,
    val factors: List<FactorIntent>,
    override val cardId: String = "attribution:${quote.symbol}",
) : CardModel {
    override val cardType: String = "attribution"
    override val expandMode: ExpandMode = ExpandMode.DRILL_DOWN
    override val source: String get() = quote.source
    override val asOf: String get() = quote.timestamp
}

data class StockCompareCardModel(
    val quotes: List<Quote>,
    override val cardId: String = "stock-compare:${quotes.joinToString { it.symbol }}",
) : CardModel {
    override val cardType: String = "stock-compare"
    override val expandMode: ExpandMode = ExpandMode.SIDE_BY_SIDE
    override val source: String get() = quotes.joinToString { it.source }.ifEmpty { "" }
    override val asOf: String get() = quotes.map { it.timestamp }.filter { it.isNotEmpty() }.maxOrNull().orEmpty()
}

data class NewsItem(val title: String, val source: String, val time: String)

data class NewsCardModel(
    val quote: Quote,
    val items: List<NewsItem>,
    override val cardId: String = "news:${quote.symbol}",
) : CardModel {
    override val cardType: String = "news"
    override val expandMode: ExpandMode = ExpandMode.BOTTOM_SHEET
    override val source: String get() = items.joinToString { it.source }
    override val asOf: String get() = items.firstOrNull()?.time.orEmpty()
}

data class UnknownCardModel(
    override val cardType: String,
    val rawPayload: String,
    override val cardId: String = "unknown:$cardType:${rawPayload.hashCode()}",
) : CardModel { override val expandMode: ExpandMode = ExpandMode.STATIC }

data class SkeletonCardModel(
    override val cardType: String,
    override val cardId: String = "skeleton:$cardType",
) : CardModel { override val expandMode: ExpandMode = ExpandMode.STATIC }

data class FundFlowCardModel(val value: FundFlow, override val cardId: String = "fund-flow") : CardModel {
    override val cardType = "fund-flow"; override val expandMode = ExpandMode.ACCORDION
    override val source get() = value.stamp.source; override val asOf get() = value.stamp.asOf
}

enum class ExplanationDepth(val label: String) {
    ONE_SENTENCE("一句话"),
    PARAGRAPH("一段话"),
    EXAMPLE_AND_DATA("带例子"),
}

data class FinancialCardModel(val value: FinancialSummary, override val cardId: String = "financial") : CardModel {
    override val cardType = "financial"; override val expandMode = ExpandMode.ACCORDION
    override val source get() = value.stamp.source; override val asOf get() = value.stamp.asOf
}

data class ShareholderCardModel(val value: ShareholderSnapshot, override val cardId: String = "shareholders") : CardModel {
    override val cardType = "shareholders"; override val expandMode = ExpandMode.ACCORDION
    override val source get() = value.stamp.source; override val asOf get() = value.stamp.asOf
}

data class BillboardCardModel(val value: BillboardRecord, override val cardId: String = "billboard") : CardModel {
    override val cardType = "billboard"; override val expandMode = ExpandMode.ACCORDION
    override val source get() = value.stamp.source; override val asOf get() = value.stamp.asOf
}

data class CorporateActionCardModel(val values: List<CorporateAction>, override val cardId: String = "corporate-actions") : CardModel {
    override val cardType = "corporate-actions"; override val expandMode = ExpandMode.ACCORDION
    override val source get() = values.firstOrNull()?.stamp?.source.orEmpty(); override val asOf get() = values.firstOrNull()?.stamp?.asOf.orEmpty()
}

data class DisclosureCardModel(val values: List<DisclosureItem>, override val cardId: String = "disclosures") : CardModel {
    override val cardType = "disclosures"; override val expandMode = ExpandMode.BOTTOM_SHEET
    override val source get() = values.firstOrNull()?.stamp?.source.orEmpty(); override val asOf get() = values.firstOrNull()?.stamp?.asOf.orEmpty()
}

data class ProductConceptCardModel(
    val title: String,
    val value: String,
    val flow: List<String>,
    val boundary: String,
    override val cardId: String,
) : CardModel {
    override val cardType = "product-concept"
    override val expandMode = ExpandMode.ACCORDION
}
