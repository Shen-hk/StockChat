package com.kuikly.stockchat

import com.kuikly.stockchat.data.AlertStore
import com.kuikly.stockchat.data.LocalAlertProvider
import com.kuikly.stockchat.data.provider.EastMoneyInsightParser
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.SourceTier
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EastMoneyInsightParserTest {
    @Test
    fun parsesNewsListWithDedupAndEmptyTitleFilter() {
        val root = JSONObject(
            """{"data":{"list":[
                {"Art_Code":"20260907102346140823780","Art_Title":"白酒巨头集体入局，却不愿为低度赛道豪赌","Author":"钛媒体APP","Art_ShowTime":"2026-09-07 10:23:42","Art_Url":"http://caifuhao.eastmoney.com/news/20260907102346140823780"},
                {"Art_Code":"20260907102346140823780","Art_Title":"重复行应被去重","Author":"x","Art_ShowTime":"2026-09-07 10:23:42","Art_Url":""},
                {"Art_Code":"","Art_Title":"缺少编码的行应被过滤","Author":"x","Art_ShowTime":"2026-09-07 10:00:00","Art_Url":""},
                {"Art_Code":"20260907100000000000001","Art_Title":"","Author":"x","Art_ShowTime":"2026-09-07 10:00:00","Art_Url":""}
            ]}}""",
        )
        val news = EastMoneyInsightParser.parseNews(root)

        assertEquals(1, news.size)
        val item = news.single()
        assertEquals("20260907102346140823780", item.id)
        assertEquals("白酒巨头集体入局，却不愿为低度赛道豪赌", item.title)
        assertEquals("钛媒体APP", item.source)
        assertEquals("2026-09-07 10:23:42", item.time)
        assertEquals("http://caifuhao.eastmoney.com/news/20260907102346140823780", item.url)
    }

    @Test
    fun parsesFundFlowAndNeverDependsOnFieldOrder() {
        val root = JSONObject("""{"data":{"diff":[{"f84":-2602,"f62":-39785971,"f78":39788576,"f72":-87867184,"f66":48081213}]}}""")
        val value = assertNotNull(EastMoneyInsightParser.parseFundFlow(root, "2026-09-02"))

        assertEquals(-39_785_971.0, value.main)
        assertEquals(48_081_213.0, value.superLarge)
        assertEquals(SourceTier.MARKET_DATA, value.stamp.tier)
    }

    @Test
    fun parsesFinancialStatementWithMissingOptionalFields() {
        val root = JSONObject("""{"result":{"data":[{"REPORTDATE":"2026-06-30 00:00:00","NOTICE_DATE":"2026-08-15 00:00:00","TOTAL_OPERATE_INCOME":92278072083.21,"PARENT_NETPROFIT":44516880421.86,"YSTZ":1.3001,"SJLTZ":-1.95,"BASIC_EPS":35.57,"WEIGHTAVG_ROE":16.75,"XSMLL":89.555}]}}""")
        val value = assertNotNull(EastMoneyInsightParser.parseFinancial(root))

        assertEquals("2026-06-30", value.reportDate)
        assertEquals(-1.95, value.profitYoY)
        assertTrue("利润" in value.explanation)
    }

    @Test
    fun parsesLimitUpPoolAndMarketBreadth() {
        val pool = JSONObject("""{"data":{"tc":40,"pool":[{"c":"003005","m":0,"n":"竞 业 达","zdp":10.01,"hybk":"IT服务","lbc":4,"fund":226077300,"zbc":0}]}}""")
        val breadth = JSONObject("""{"data":{"diff":[{"f3":1.2},{"f3":-0.5},{"f3":0}]}}""")

        val stock = EastMoneyInsightParser.parseLimitUps(pool).single()
        assertEquals("003005.SZ", stock.symbol)
        assertEquals(4, stock.consecutiveBoards)
        assertEquals(40, EastMoneyInsightParser.parsePoolCount(pool))
        assertEquals(Triple(1, 1, 1), EastMoneyInsightParser.parseBreadth(breadth))
    }

    @Test
    fun aggregatesTurnoverWithoutChangingBreadthSemantics() {
        val breadth = JSONObject("""{"data":{"diff":[{"f3":1.2,"f6":120000000},{"f3":-0.5,"f6":340000000},{"f3":0,"f6":5000000}]}}""")

        assertEquals(465_000_000.0, EastMoneyInsightParser.parseMarketTotals(breadth).turnoverAmount)
        assertEquals(Triple(1, 1, 1), EastMoneyInsightParser.parseBreadth(breadth))
    }

    @Test
    fun parsesIndexIntradayRangeWhenTheProviderSuppliesIt() {
        val root = JSONObject("""{"data":{"diff":[{"f12":"000001","f14":"上证指数","f2":3947.26,"f3":-0.82,"f15":3993.82,"f16":3938.30}]}}""")

        val index = EastMoneyInsightParser.parseIndices(root).single()

        assertEquals(3993.82, index.high)
        assertEquals(3938.30, index.low)
    }

    @Test
    fun parsesGlobalSymbolsBoardsAndIndexesFromSuggest() {
        val root = JSONObject(
            """{"QuotationCodeTable":{"Data":[""" +
                """{"Code":"600519","Name":"贵州茅台","PinYin":"GZMT","MktNum":"1","SecurityTypeName":"沪A"},""" +
                """{"Code":"000858","Name":"五粮液","PinYin":"WLY","MktNum":"0","SecurityTypeName":"深A"},""" +
                """{"Code":"00700","Name":"腾讯控股","PinYin":"TXKG","MktNum":"116","SecurityTypeName":"港股"},""" +
                """{"Code":"AAPL","Name":"苹果","PinYin":"PG","MktNum":"105","SecurityTypeName":"美股"},""" +
                """{"Code":"BK0477","Name":"白酒","PinYin":"BJ","MktNum":"90","SecurityTypeName":"板块"},""" +
                """{"Code":"000001","Name":"上证指数","PinYin":"SZZS","MktNum":"1","SecurityTypeName":"指数"},""" +
                """{"Code":"161725","Name":"白酒基金LOF","PinYin":"BJJJLOF","MktNum":"0","SecurityTypeName":"基金"}""" +
            """]}}"""
        )

        val values = EastMoneyInsightParser.parseSecurities(root)

        assertEquals(
            listOf("600519.SH", "000858.SZ", "00700.HK", "AAPL.US", "BK0477", "000001.SH"),
            values.map { it.symbol },
        )
        assertEquals("板块", values.first { it.symbol == "BK0477" }.market)
        assertEquals("board", values.first { it.symbol == "BK0477" }.kind)
        assertEquals("index", values.first { it.symbol == "000001.SH" }.kind)
        assertEquals("港股", values.first { it.symbol == "00700.HK" }.market)
        assertEquals("GZMT", values.first().aliases.first())
    }

    @Test
    fun alertRulesPersistAndProduceAnExplanation() {
        val storage = InMemoryKeyValueStorage()
        val store = AlertStore(storage, nowMillis = { 42L })
        store.upsert("600519.SH", "贵州茅台", 3.0)

        val rule = store.list().single()
        val quote = OfflineMarketInsightProvider().let {
            com.kuikly.stockchat.data.mock.MockDataBank.quote("600519.SH")!!.copy(price = 90.0, previousClose = 100.0)
        }
        val trigger = assertNotNull(LocalAlertProvider.evaluate(rule, quote))

        assertEquals(42L, rule.createdAtMillis)
        assertTrue("先核对" in trigger.attribution)
    }
}
