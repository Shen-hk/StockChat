package com.kuikly.stockchat.page.detail

import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.KLinePoint
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePoint
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailChartHeaderTest {
    @Test
    fun companyProfileCatalogKeepsKnownAndUnknownSymbolsExplicit() {
        val known = DetailCompanyProfileCatalog.forSymbol("600519.SH")
        val unknown = DetailCompanyProfileCatalog.forSymbol("000001.SZ")

        assertEquals("以茅台酒为核心产品，覆盖系列酒、葡萄酒与相关配套业务。", known.summary)
        assertEquals(listOf("白酒", "高端消费"), known.tags)
        assertEquals(null, unknown.summary)
        assertEquals(emptyList(), unknown.tags)
    }

    @Test
    fun fallsBackToLatestQuoteWhenNothingIsInspected() {
        val snapshot = DetailChartHeaderResolver.resolve(input())

        assertEquals("101.00", snapshot.priceText)
        assertEquals(1.0, snapshot.change)
        assertEquals("更新于 10:30", snapshot.caption)
        assertEquals(listOf("今开", "最高", "最低", "涨跌", "成交量", "成交额", "换手", "振幅"), snapshot.metrics.map { it.label })
    }

    @Test
    fun timelineInspectionUsesTheSelectedPointForTheHero() {
        val snapshot = DetailChartHeaderResolver.resolve(
            input(crosshairIndex = 1),
        )

        assertEquals("102.00", snapshot.priceText)
        assertEquals(2.0, snapshot.change)
        assertEquals(2.0, snapshot.changePercent)
        assertEquals("查看 09:31 分时 · 均价 0.11 · 量 220手", snapshot.caption)
        assertEquals("220", snapshot.metrics[4].value)
        assertEquals("2600", snapshot.metrics[5].value)
    }

    @Test
    fun klineInspectionUsesTheSelectedCandleWithoutChangingTheFallbackQuote() {
        val snapshot = DetailChartHeaderResolver.resolve(
            input(
                mode = StockChartMode.K_LINE,
                period = StockChartPeriod.DAY,
                selectedKLineIndex = 1,
            ),
        )

        assertEquals("104.00", snapshot.priceText)
        assertEquals(2.0, snapshot.change)
        assertEquals(1.9607843137254901, snapshot.changePercent)
        assertEquals("查看 2026-09-12 日K · 收盘价与当根 OHLC 已同步", snapshot.caption)
        assertEquals(listOf("开", "高", "低", "涨跌"), snapshot.metrics.take(4).map { it.label })
    }

    private fun input(
        mode: StockChartMode = StockChartMode.TIMELINE,
        period: StockChartPeriod = StockChartPeriod.DAY,
        crosshairIndex: Int = -1,
        selectedKLineIndex: Int = -1,
    ): DetailChartHeaderInput = DetailChartHeaderInput(
        quote = Quote(
            symbol = "600519.SH",
            name = "测试股",
            price = 101.0,
            previousClose = 100.0,
            open = 99.0,
            high = 103.0,
            low = 98.0,
            volume = 1_000.0,
            amount = 12_000.0,
            turnoverRate = 1.2,
            peTtm = 0.0,
            pb = 0.0,
            marketCap = 0.0,
            timestamp = "10:30",
            source = "test",
            timeline = listOf(
                QuotePoint("09:30", 100.0, 100.0, 1_000.0),
                QuotePoint("09:31", 102.0, 220.0, 2_600.0),
            ),
            kLines = listOf(
                KLinePoint("2026-09-11", 100.0, 102.0, 103.0, 99.0, 1_000.0),
                KLinePoint("2026-09-12", 102.0, 104.0, 105.0, 101.0, 1_200.0),
            ),
        ),
        mode = mode,
        period = period,
        crosshairIndex = crosshairIndex,
        selectedKLineIndex = selectedKLineIndex,
        theme = StockChatTheme.Light,
        fallbackTone = StockChatTheme.Light.rise,
        fallbackCaption = "更新于 10:30",
        amplitudePercent = 5.0,
    )
}
