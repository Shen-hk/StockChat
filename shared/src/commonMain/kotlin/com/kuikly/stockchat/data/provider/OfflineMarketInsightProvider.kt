package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.config.DataSourceConfig

/**
 * 离线洞察提供者，按 [DataSourceConfig.USE_REAL_MARKET_DATA] 分支：
 * - 模拟模式（false，默认）：恢复 2026-09-08 之前的整套演示数据（假指数、假板块、
 *   假财报），stamp 标注「离线演示数据 / DEMO」，即原状态。
 * - 真实模式（true）：一律返回**空结果**——UI 以"待接入/暂无数据"呈现，不冒充真实行情。
 *
 * 页面初始态引用本类的构造（形状与在线数据一致，避免可空类型扩散），开关切换页面层无感。
 */
class OfflineMarketInsightProvider : FundFlowProvider, FundamentalProvider, DisclosureProvider, MarketOverviewProvider, RatingSpectrumProvider {
    private val realMode = DataSourceConfig.USE_REAL_MARKET_DATA

    // 真实模式：数据源未返回；模拟模式：演示数据戳。
    private val stamp = if (realMode) {
        SourceStamp("数据源未返回", platformCurrentDate(), SourceTier.MARKET_DATA, DataMode.OFFLINE)
    } else {
        SourceStamp("离线演示数据", "2026-09-02", SourceTier.DEMO, DataMode.OFFLINE)
    }

    fun stock(symbol: String): StockInsightBundle = if (realMode) {
        StockInsightBundle()
    } else {
        StockInsightBundle(
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
    }

    fun overviewValue(): MarketOverview = if (realMode) {
        MarketOverview(
            indices = emptyList(),
            risingCount = 0,
            fallingCount = 0,
            flatCount = 0,
            limitUpCount = 0,
            limitDownCount = 0,
            sectors = emptyList(),
            stamp = stamp,
        )
    } else {
        MarketOverview(
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
    }

    fun hotspotValue(): HotspotSnapshot = if (realMode) {
        HotspotSnapshot(sectors = emptyList(), limitUps = emptyList(), stamp = stamp)
    } else {
        HotspotSnapshot(
            sectors = demoSectors(),
            limitUps = listOf(
                LimitUpStock("000635.SZ", "英力特", 9.97, "化学原料", 2, 116_408_340.0, 0),
                LimitUpStock("000892.SZ", "欢瑞世纪", 9.94, "影视院线", 3, 534_509_300.0, 0),
                LimitUpStock("003005.SZ", "竞业达", 10.01, "IT 服务", 4, 226_077_300.0, 0),
            ),
            stamp = stamp,
        )
    }

    fun calendarValue(): List<MarketCalendarEvent> = if (realMode) {
        emptyList()
    } else {
        listOf(
            MarketCalendarEvent("2026-09-03", "000001.SZ", "平安银行", CalendarEventKind.EARNINGS, "预约披露财报", stamp),
            MarketCalendarEvent("2026-09-04", "A00001", "示例新股", CalendarEventKind.IPO, "网上申购日", stamp),
            MarketCalendarEvent("2026-09-08", "600519.SH", "贵州茅台", CalendarEventKind.DIVIDEND, "分红事项跟踪", stamp),
        )
    }

    private fun demoSectors() = listOf(
        SectorRank("BK1382", "地面兵装", 6.82, 1_166_802_432.0, 12, 0),
        SectorRank("BK1497", "化妆品制造", 5.99, 99_123_190.0, 5, 3),
        SectorRank("BK1296", "视频媒体", 4.51, -412_926_800.0, 1, 0),
        SectorRank("BK1441", "橡胶制品", 3.68, 58_917_308.0, 8, 7),
        SectorRank("BK1277", "白酒", -1.42, -1_502_000_000.0, 5, 15),
    )

    /**
     * 市场页新闻弹幕带（doc 36 §9.1）：演示模式给一组带日内时间戳的快讯，
     * 供时间机器「随帧过滤」（回放到 HH:MM 只显示当时已发布的新闻）。
     * 真实模式返回空——市场级快讯的真实数据源未接入前不冒充。
     * [time] 保持 `yyyy-MM-dd HH:mm:ss` 形状，与 NewsTape 的 formatTapeTime 口径一致。
     */
    fun marketNewsValue(): List<NewsItem> = if (realMode) {
        emptyList()
    } else {
        listOf(
            NewsItem("mkt-n1", "央行公开市场今日净投放 1200 亿元", "公开媒体", "2026-09-09 09:32:00", "", "演示数据：数量级与表述均为占位，仅供交互演示。"),
            NewsItem("mkt-n2", "两市成交额开盘 30 分钟突破 4000 亿", "公开媒体", "2026-09-09 09:41:00", "", ""),
            NewsItem("mkt-n3", "工信部：加快推进机器人产业创新发展", "公开媒体", "2026-09-09 09:48:00", "", ""),
            NewsItem("mkt-n4", "北证50 盘中涨超 2%，成交额创阶段新高", "公开媒体", "2026-09-09 09:55:00", "", ""),
            NewsItem("mkt-n5", "存储芯片现货价连续第三周上行", "公开媒体", "2026-09-09 10:06:00", "", ""),
            NewsItem("mkt-n6", "券商板块冲高回落，早盘振幅 2.1%", "公开媒体", "2026-09-09 10:19:00", "", ""),
            NewsItem("mkt-n7", "沪深两市红盘率回落至 45% 下方", "公开媒体", "2026-09-09 10:47:00", "", ""),
            NewsItem("mkt-n8", "国家统计局：8 月 CPI 同比上涨 0.4%", "公开媒体", "2026-09-09 11:02:00", "", ""),
            NewsItem("mkt-n9", "创业板指午后翻红，现涨 0.3%", "公开媒体", "2026-09-09 13:26:00", "", ""),
            NewsItem("mkt-n10", "两市炸板数升至 22 家", "公开媒体", "2026-09-09 13:38:00", "", ""),
            NewsItem("mkt-n11", "北向资金净流出收窄至 44 亿", "公开媒体", "2026-09-09 14:05:00", "", ""),
            NewsItem("mkt-n12", "机器人板块尾盘拉升，涨幅重回 1%", "公开媒体", "2026-09-09 14:31:00", "", ""),
            NewsItem("mkt-n13", "上证指数收复 3300 点整数关口", "公开媒体", "2026-09-09 14:46:00", "", ""),
            NewsItem("mkt-n14", "两市全天成交 1.79 万亿，较昨日缩量", "公开媒体", "2026-09-09 15:01:00", "", ""),
        )
    }

    override fun fundFlow(symbol: String, onResult: (FundFlow?) -> Unit) = onResult(stock(symbol).fundFlow)
    override fun fundamentals(symbol: String, onResult: (FundamentalBundle?) -> Unit) = onResult(stock(symbol).fundamentals)
    override fun calendar(onResult: (List<MarketCalendarEvent>) -> Unit) = onResult(calendarValue())
    override fun disclosures(symbol: String, onResult: (List<DisclosureItem>) -> Unit) = onResult(stock(symbol).disclosures)
    override fun overview(onResult: (MarketOverview?) -> Unit) = onResult(overviewValue())
    override fun hotspots(onResult: (HotspotSnapshot?) -> Unit) = onResult(hotspotValue())

    /**
     * F3 评级光谱离线兜底：模拟模式给与详情页演示段一致的占位光谱（明确标注演示数据）；
     * 真实模式一律 null——在线源失败时由 UI 回落演示段，这里不重复产出。
     */
    override fun ratingSpectrum(symbol: String, onResult: (RatingSpectrum?) -> Unit) {
        if (realMode) {
            onResult(null)
            return
        }
        onResult(
            RatingSpectrum(
                segments = listOf(
                    RatingSpectrumSegment("买入", 4, "示例 · 演示数据：偏多观点的占位引用，仅用于展示评级光谱交互，不构成任何建议。"),
                    RatingSpectrumSegment("增持", 3, "示例 · 演示数据：谨慎看多的占位引用，观点切换仅为形态演示。"),
                    RatingSpectrumSegment("中性", 2, "示例 · 演示数据：中性观点的占位引用，等待更多数据验证。"),
                    RatingSpectrumSegment("减持", 1, "示例 · 演示数据：偏空观点的占位引用，仅展示光谱另一端。"),
                ),
                stamp = stamp.copy(tier = SourceTier.RESEARCH),
            )
        )
    }
}
