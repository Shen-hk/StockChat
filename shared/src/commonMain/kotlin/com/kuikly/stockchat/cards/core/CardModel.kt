package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.protocol.FactorIntent

interface CardModel {
    val cardType: String
    val cardId: String
    val expandMode: ExpandMode
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
}

data class StockChartCardModel(
    val quote: Quote,
    val mode: StockChartMode = StockChartMode.TIMELINE,
    val period: StockChartPeriod = StockChartPeriod.DAY,
    override val cardId: String = "stock-chart:${quote.symbol}",
) : CardModel {
    override val cardType: String = "stock-chart"
    override val expandMode: ExpandMode = ExpandMode.ACCORDION
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
}

data class DefinitionCardModel(
    val term: String,
    val plainText: String,
    val example: String,
    override val cardId: String = "definition:$term",
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
}

data class StockCompareCardModel(
    val quotes: List<Quote>,
    override val cardId: String = "stock-compare:${quotes.joinToString { it.symbol }}",
) : CardModel {
    override val cardType: String = "stock-compare"
    override val expandMode: ExpandMode = ExpandMode.SIDE_BY_SIDE
}

data class NewsItem(val title: String, val source: String, val time: String)

data class NewsCardModel(
    val quote: Quote,
    val items: List<NewsItem>,
    override val cardId: String = "news:${quote.symbol}",
) : CardModel {
    override val cardType: String = "news"
    override val expandMode: ExpandMode = ExpandMode.BOTTOM_SHEET
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
