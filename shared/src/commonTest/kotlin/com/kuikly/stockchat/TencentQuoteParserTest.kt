package com.kuikly.stockchat

import com.kuikly.stockchat.data.provider.TencentQuoteParser
import com.kuikly.stockchat.data.provider.KLineInterval
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TencentQuoteParserTest {
    @Test
    fun parsesQuoteAndKLinePayload() {
        val payload = JSONObject(
            """{"data":{"sh600519":{"qfqday":[["2026-08-24","1271.01","1307.99","1308.00","1270.33","25377"]],"qt":{"sh600519":["1","贵州茅台","600519","1307.99","1272.83","1271.01","25377","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","0","20260824104058","35.16","2.76","1308.00","1270.33","0","0","328266","0.20","20.08","0","0","0","0","0","16350.94","6.51"]}}}}}""",
        )

        val quote = assertNotNull(TencentQuoteParser.parseSnapshot(payload, "600519.SH"))
        assertEquals("贵州茅台", quote.name)
        assertEquals(1307.99, quote.price)
        assertEquals(3_282_660_000.0, quote.amount)
        assertEquals("2026-08-24 10:40", quote.timestamp)
        assertEquals(1, quote.kLines.size)
        assertEquals(1308.0, quote.kLines.first().high)
    }

    @Test
    fun parsesMinutePayload() {
        val payload = JSONObject("""{"data":{"sh600519":{"data":{"data":["0930 1271.01 298 37876098.00","0931 1272.00 620 121023456.78"]}}}}""")
        val points = TencentQuoteParser.parseTimeline(payload, "600519.SH")
        assertEquals(2, points.size)
        assertEquals("09:30", points.first().time)
        assertEquals(1271.01, points.first().price)
        assertEquals("09:31", points[1].time)
        assertEquals(1272.00, points[1].price)
    }

    @Test
    fun minuteDiffingProducesPerMinuteVolumeAndAmount() {
        val payload = JSONObject(
            """{"data":{"sh600519":{"data":{"data":["0930 1271.01 298 37876098.00","0931 1272.00 620 121023456.78","0932 1271.50 905 190145678.90"]}}}}""",
        )
        val points = TencentQuoteParser.parseTimeline(payload, "600519.SH")
        assertEquals(298.0, points[0].volume, 0.0001)
        assertEquals(37_876_098.0, points[0].amount, 0.01)
        assertEquals(322.0, points[1].volume, 0.0001)
        assertEquals(83_147_358.78, points[1].amount, 0.01)
        assertEquals(285.0, points[2].volume, 0.0001)
        assertEquals(69_122_222.12, points[2].amount, 0.01)
    }

    @Test
    fun minuteRowsWithoutAmountFallBackToZeroAmount() {
        val payload = JSONObject("""{"data":{"sh600519":{"data":{"data":["0930 1271.01 298"]}}}}""")
        val points = TencentQuoteParser.parseTimeline(payload, "600519.SH")
        assertEquals(1, points.size)
        assertEquals(0.0, points.single().amount)
        assertEquals(298.0, points.single().volume, 0.0001)
    }

    @Test
    fun afterCloseFillerRowsAreDropped() {
        // 2026-09-07 实测：收盘后接口按分钟追加冻结填充点（价格/累计量停在收盘值），
        // 必须截断到交易时段，末点保持 15:00。
        val payload = JSONObject(
            """{"data":{"sh600519":{"data":{"data":["0930 1324.00 227 30054800.00","1130 1322.10 12000 2000000000.00","1300 1322.00 12100 2020000000.00","1500 1316.01 25254 3336606111.32","1501 1316.01 25254 3336606111.32","1530 1316.01 25254 3336606111.32"]}}}}""",
        )
        val points = TencentQuoteParser.parseTimeline(payload, "600519.SH")
        assertEquals(4, points.size)
        assertEquals("15:00", points.last().time)
        assertEquals(1316.01, points.last().price, 0.0001)
        assertEquals(13154.0, points.last().volume, 0.0001)
    }

    @Test
    fun lunchBreakRowsOutsideSessionAreDropped() {
        // 午休（11:31–12:59）不应出现点；边界 11:30 保留
        val payload = JSONObject(
            """{"data":{"sh600519":{"data":{"data":["1129 1322.30 11900 1990000000.00","1130 1322.10 12000 2000000000.00","1230 1322.05 12050 2005000000.00"]}}}}""",
        )
        val points = TencentQuoteParser.parseTimeline(payload, "600519.SH")
        assertEquals(listOf("11:29", "11:30"), points.map { it.time })
    }

    @Test
    fun parsesProviderNativeWeeklyAndMonthlyKLines() {
        val payload = JSONObject(
            """{"data":{"sh600519":{"qfqweek":[["2026-08-21","1271.01","1304.66","1313.80","1270.33","48440"]],"qfqmonth":[["2026-08-31","1200.00","1304.66","1313.80","1190.00","484400"]]}}}""",
        )

        val weekly = TencentQuoteParser.parseKLines(payload, "600519.SH", KLineInterval.WEEK)
        val monthly = TencentQuoteParser.parseKLines(payload, "600519.SH", KLineInterval.MONTH)

        assertEquals(1, weekly.size)
        assertEquals(1313.80, weekly.single().high)
        assertEquals("2026-08-31", monthly.single().date)
    }
}
