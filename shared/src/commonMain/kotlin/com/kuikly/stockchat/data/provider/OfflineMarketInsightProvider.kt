package com.kuikly.stockchat.data.provider

/** Stable demo data used by H5/CORS failures and as the final leg of the fallback chain. */
class OfflineMarketInsightProvider : FundFlowProvider, FundamentalProvider, DisclosureProvider, MarketOverviewProvider {
    private val stamp = SourceStamp("离线演示数据", "2026-09-02", SourceTier.DEMO, DataMode.OFFLINE)

    fun stock(symbol: String): StockInsightBundle = StockInsightBundle(
        fundFlow = FundFlow(-39_650_000.0, 48_080_000.0, -87_870_000.0, 39_790_000.0, -2_600.0, stamp),
        fundamentals = FundamentalBundle(
            financial = FinancialSummary("2026-06-30", 92_278_072_083.0, 44_516_880_422.0, 1.30, -1.95, 35.57, 16.75, 89.56, stamp),
            shareholder = ShareholderSnapshot(296_404, 53_245, 21.90, "2026-06-30", stamp),
            billboard = BillboardRecord("2013-01-28", "历史龙虎榜记录，近期无上榜", 304_142_576.0, 683_034_128.0, -378_891_552.0, stamp),
            actions = listOf(CorporateAction("2026 年半年度不分配不转增", "2026-08-15", "已披露", stamp)),
        ),
        disclosures = listOf(
            DisclosureItem("demo-ann", DisclosureKind.ANNOUNCEMENT, "2026 年半年度报告摘要", "上市公司公告", "2026-08-15", "营业收入同比小幅增长，归母净利润同比下降；经营现金流同比改善。", "利润同比下降", "", stamp),
            DisclosureItem("demo-report", DisclosureKind.RESEARCH, "中报点评：主营稳健，结构仍在调整", "示例券商研报", "2026-08-21", "研报关注收入韧性与产品结构调整。评级属于机构观点，不代表股问立场。", "机构观点", "", stamp.copy(tier = SourceTier.RESEARCH)),
        ),
    )

    fun overviewValue(): MarketOverview = MarketOverview(
        indices = listOf(
            MarketIndex("000001", "上证指数", 3947.26, -0.82, 3993.82, 3938.30),
            MarketIndex("399001", "深证成指", 13635.33, -1.71, 13855.48, 13618.93),
            MarketIndex("399006", "创业板指", 3319.39, -2.18, 3389.04, 3311.72),
            MarketIndex("000688", "科创50", 1278.52, -1.04, 1304.20, 1271.04),
            MarketIndex("899050", "北证50", 1437.88, 2.50, 1450.03, 1398.21),
            MarketIndex("000300", "沪深300", 4521.53, -0.76, 4560.72, 4510.16),
            MarketIndex("000016", "上证50", 3045.72, -0.45, 3062.10, 3040.50),
            MarketIndex("000905", "中证500", 6984.62, -1.22, 7059.88, 6978.35),
            MarketIndex("HSI", "恒生指数", 25496.32, 0.37, 25603.80, 25390.11),
        ),
        risingCount = 1386,
        fallingCount = 3860,
        flatCount = 310,
        limitUpCount = 40,
        limitDownCount = 8,
        sectors = demoSectors(),
        stamp = stamp,
        turnoverAmount = 1_790_000_000_000.0,
        yesterdayTurnoverAmount = 1_860_000_000_000.0,
        fiveDayAverageTurnoverAmount = 1_960_000_000_000.0,
        sealRate = 0.76,
        brokenBoardCount = 14,
        highestBoard = 4,
        yesterdayHighestBoard = 7,
        northboundFlow = -6_614_000_000.0,
    )

    fun hotspotValue(): HotspotSnapshot = HotspotSnapshot(
        sectors = demoSectors(),
        limitUps = listOf(
            LimitUpStock("000635.SZ", "英力特", 9.97, "化学原料", 2, 116_408_340.0, 0),
            LimitUpStock("000892.SZ", "欢瑞世纪", 9.94, "影视院线", 3, 534_509_300.0, 0),
            LimitUpStock("003005.SZ", "竞业达", 10.01, "IT 服务", 4, 226_077_300.0, 0),
        ),
        stamp = stamp,
    )

    fun calendarValue(): List<MarketCalendarEvent> = listOf(
        MarketCalendarEvent("2026-09-03", "000001.SZ", "平安银行", CalendarEventKind.EARNINGS, "预约披露财报", stamp),
        MarketCalendarEvent("2026-09-04", "A00001", "示例新股", CalendarEventKind.IPO, "网上申购日", stamp),
        MarketCalendarEvent("2026-09-08", "600519.SH", "贵州茅台", CalendarEventKind.DIVIDEND, "分红事项跟踪", stamp),
    )

    private fun demoSectors() = listOf(
        SectorRank("BK1382", "地面兵装", 6.82, 1_166_802_432.0, 12, 0),
        SectorRank("BK1497", "化妆品制造", 5.99, 99_123_190.0, 5, 3),
        SectorRank("BK1296", "视频媒体", 4.51, -412_926_800.0, 1, 0),
        SectorRank("BK1441", "橡胶制品", 3.68, 58_917_308.0, 8, 7),
        SectorRank("BK1277", "白酒", -1.42, -1_502_000_000.0, 5, 15),
    )

    override fun fundFlow(symbol: String, onResult: (FundFlow?) -> Unit) = onResult(stock(symbol).fundFlow)
    override fun fundamentals(symbol: String, onResult: (FundamentalBundle?) -> Unit) = onResult(stock(symbol).fundamentals)
    override fun calendar(onResult: (List<MarketCalendarEvent>) -> Unit) = onResult(calendarValue())
    override fun disclosures(symbol: String, onResult: (List<DisclosureItem>) -> Unit) = onResult(stock(symbol).disclosures)
    override fun overview(onResult: (MarketOverview?) -> Unit) = onResult(overviewValue())
    override fun hotspots(onResult: (HotspotSnapshot?) -> Unit) = onResult(hotspotValue())
}
