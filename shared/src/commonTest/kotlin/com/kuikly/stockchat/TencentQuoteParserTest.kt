package com.kuikly.stockchat

import com.kuikly.stockchat.data.provider.TencentQuoteParser
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
        val payload = JSONObject("""{"data":{"sh600519":{"data":{"data":["0930 1271.01 298 37876098.00","0931 1272.00 120 100"]}}}}""")
        val points = TencentQuoteParser.parseTimeline(payload, "600519.SH")
        assertEquals(2, points.size)
        assertEquals("09:30", points.first().time)
        assertEquals(1271.01, points.first().price)
    }
}
