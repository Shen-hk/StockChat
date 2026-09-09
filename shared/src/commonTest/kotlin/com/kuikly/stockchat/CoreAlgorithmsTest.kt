package com.kuikly.stockchat

import com.kuikly.stockchat.chart.model.KLineCalculator
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CompareCalculator
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.ExpandMode
import com.kuikly.stockchat.cards.core.InsightCardModel
import com.kuikly.stockchat.cards.core.NewsCardModel
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.data.mock.MockDataBank
import com.kuikly.stockchat.protocol.AiResponseLexer
import com.kuikly.stockchat.protocol.CardBlock
import com.kuikly.stockchat.protocol.SkeletonBlock
import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CoreAlgorithmsTest {
    @Test
    fun cardModelsDeclareInteractionModesByInformationShape() {
        val quote = MockDataBank.quote("600519.SH")!!

        assertEquals(ExpandMode.ACCORDION, StockQuoteCardModel(quote).expandMode)
        assertEquals(ExpandMode.ACCORDION, DefinitionCardModel("PE", "市盈率", "示例").expandMode)
        assertEquals(ExpandMode.BOTTOM_SHEET, NewsCardModel(quote, emptyList()).expandMode)
        assertEquals(ExpandMode.DRILL_DOWN, AttributionCardModel(quote, "上涨", emptyList()).expandMode)
        assertEquals(ExpandMode.NESTED_CONVERSATION, InsightCardModel(quote, "摘要").expandMode)
        assertEquals(ExpandMode.SIDE_BY_SIDE, StockCompareCardModel(listOf(quote)).expandMode)
    }

    @Test
    fun comparisonDeltaIsSignedAndNormalized() {
        val left = MockDataBank.quote("600519.SH")!!
        val right = MockDataBank.quote("000858.SZ")!!
        val delta = CompareCalculator.delta(left, right)

        assertEquals(left.price - right.price, delta.priceDifference)
        assertEquals(left.changePercent - right.changePercent, delta.changePercentDifference)
        assertTrue(delta.normalizedGap in 0.0..1.0)
    }

    @Test
    fun mockDataIsDeterministic() {
        val first = MockDataBank.quote("600519.SH")
        val second = MockDataBank.quote("600519.SH")
        assertEquals(first, second)
        // Demo timeline now models every A-share trading minute (09:30–15:00,
        // excluding the lunch break), rather than the retired 48-point series.
        assertEquals(241, first?.timeline?.size)
    }

    @Test
    fun lexerKeepsTextAndCardOrder() {
        val blocks = AiResponseLexer.lex("结论\n```card:stock-quote\n{\"symbol\":\"600519.SH\"}\n```\n补充")
        assertEquals(3, blocks.size)
        assertIs<CardBlock>(blocks[1])
        assertEquals("stock-quote", (blocks[1] as CardBlock).type)
    }

    @Test
    fun lexerUsesSkeletonForOpenFence() {
        val blocks = AiResponseLexer.lex("```card:stock-quote\n{\"symbol\":", finished = false)
        assertIs<SkeletonBlock>(blocks.single())
    }

    @Test
    fun entityRecognizerPrefersLongerMatches() {
        val spans = EntityRecognizer.recognize("贵州茅台的 PE 和换手率怎么看")
        assertEquals("贵州茅台", spans.first().text)
        assertTrue(spans.any { it.text == "PE" })
    }

    @Test
    fun entityRecognizerUsesContextForAmbiguousAlias() {
        val spans = EntityRecognizer.recognize("平安今天怎么样", contextSymbols = listOf("601318.SH", "000001.SZ"))
        assertEquals("000001.SZ", spans.single().target)
        assertEquals(2, spans.single().candidates.size)
    }

    @Test
    fun entityRecognizerRecognizesAsciiTermsWithoutChangingOffsets() {
        val spans = EntityRecognizer.recognize("看看macd和PE")
        assertEquals(listOf("macd", "PE"), spans.map { it.text })
        assertTrue(spans.all { it.type == EntityType.TERM })
    }

    @Test
    fun movingAverageStartsAtPeriod() {
        val lines = MockDataBank.quote("600519.SH")!!.kLines
        val ma5 = KLineCalculator.movingAverage(lines, 5)
        assertEquals(null, ma5[3])
        assertTrue(ma5[4] != null)
    }
}
