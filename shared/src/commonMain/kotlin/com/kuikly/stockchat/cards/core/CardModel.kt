package com.kuikly.stockchat.cards.core

import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.protocol.FactorIntent

interface CardModel {
    val cardType: String
    val cardId: String
}

data class StockQuoteCardModel(
    val quote: Quote,
    override val cardId: String = "stock-quote:${quote.symbol}",
) : CardModel { override val cardType: String = "stock-quote" }

data class StockChartCardModel(
    val quote: Quote,
    override val cardId: String = "stock-chart:${quote.symbol}",
) : CardModel { override val cardType: String = "stock-chart" }

data class InsightCardModel(
    val quote: Quote,
    val summary: String,
    override val cardId: String = "insight:${quote.symbol}",
) : CardModel { override val cardType: String = "insight" }

data class DefinitionCardModel(
    val term: String,
    val plainText: String,
    val example: String,
    override val cardId: String = "definition:$term",
) : CardModel { override val cardType: String = "definition" }

data class AttributionCardModel(
    val quote: Quote,
    val direction: String,
    val factors: List<FactorIntent>,
    override val cardId: String = "attribution:${quote.symbol}",
) : CardModel { override val cardType: String = "attribution" }

data class StockCompareCardModel(
    val quotes: List<Quote>,
    override val cardId: String = "stock-compare:${quotes.joinToString { it.symbol }}",
) : CardModel { override val cardType: String = "stock-compare" }

data class NewsItem(val title: String, val source: String, val time: String)

data class NewsCardModel(
    val quote: Quote,
    val items: List<NewsItem>,
    override val cardId: String = "news:${quote.symbol}",
) : CardModel { override val cardType: String = "news" }

data class UnknownCardModel(
    override val cardType: String,
    val rawPayload: String,
    override val cardId: String = "unknown:$cardType:${rawPayload.hashCode()}",
) : CardModel

data class SkeletonCardModel(
    override val cardType: String,
    override val cardId: String = "skeleton:$cardType",
) : CardModel
