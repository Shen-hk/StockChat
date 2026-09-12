package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.config.DataSourceConfig

/**
 * 离线洞察提供者，按 [DataSourceConfig.USE_REAL_MARKET_DATA] 分支：
 * - 模拟模式（false，默认）：整套演示数据（假指数、假板块、假财报），stamp 标注
 *   「离线演示数据 / DEMO」。
 * - 真实模式（true）：个股级数据（fundFlow/fundamentals/disclosures）一律返回**空结果**
 *   ——不冒充个股真实行情；但**市场页**的 overview/hotspots/news 返回演示数据作为兜底，
 *   在线请求失败时让市场页有内容可看（stamp 仍标注 DEMO，不冒充真实在线行情）。
 *
 * 页面初始态引用本类的构造（形状与在线数据一致，避免可空类型扩散），开关切换页面层无感。
 */
class OfflineMarketInsightProvider : FundFlowProvider, FundamentalProvider, DisclosureProvider, MarketOverviewProvider, RatingSpectrumProvider {
    private val realMode = DataSourceConfig.USE_REAL_MARKET_DATA

    // 市场页演示兜底戳（真实/模拟模式均用 DEMO 标注，不冒充真实在线行情）。
    private val demoStamp = SourceStamp("离线演示数据", "2026-09-02", SourceTier.DEMO, DataMode.OFFLINE)
    // 个股级：真实模式不产出，模拟模式用演示戳。
    private val stamp = if (realMode) {
        SourceStamp("数据源未返回", platformCurrentDate(), SourceTier.MARKET_DATA, DataMode.OFFLINE)
    } else {
        demoStamp
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

    /**
     * 市场页 overview 兜底：真实/模拟模式均返回同一组演示数据（DEMO 标注）。
     * 真实模式下作为在线请求失败时的兜底——让市场页有内容可看，不冒充真实在线行情。
     */
    fun overviewValue(): MarketOverview =
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
            stamp = demoStamp,
            turnoverAmount = 1_790_000_000_000.0,
            yesterdayTurnoverAmount = 1_860_000_000_000.0,
            fiveDayAverageTurnoverAmount = 1_960_000_000_000.0,
            sealRate = 0.76,
            brokenBoardCount = 14,
            highestBoard = 4,
            yesterdayHighestBoard = 7,
            northboundFlow = -6_614_000_000.0,
            breadthSamples = listOf(
                BreadthSample("沪市主板", 620, 1_010, 48, 1_700, 372_000_000_000.0),
                BreadthSample("科创板", 96, 420, 22, 540, 118_000_000_000.0),
                BreadthSample("深市主板", 430, 1_130, 96, 1_700, 421_000_000_000.0),
                BreadthSample("创业板", 190, 760, 76, 1_080, 505_000_000_000.0),
                BreadthSample("北交所", 50, 540, 68, 640, 374_000_000_000.0),
            ),
        )

    /** 市场页热点兜底：真实/模拟模式均返回同一组演示板块+涨停池（DEMO 标注）。 */
    fun hotspotValue(): HotspotSnapshot =
        HotspotSnapshot(
            sectors = demoSectors(),
            limitUps = listOf(
                LimitUpStock("000635.SZ", "英力特", 9.97, "化学原料", 2, 116_408_340.0, 0),
                LimitUpStock("000892.SZ", "欢瑞世纪", 9.94, "影视院线", 3, 534_509_300.0, 0),
                LimitUpStock("003005.SZ", "竞业达", 10.01, "IT 服务", 4, 226_077_300.0, 0),
            ),
            stamp = demoStamp,
        )

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
        SectorRank("BK1036", "半导体", 3.26, 1_286_000_000.0, 112, 39),
        SectorRank("BK1055", "机器人", 2.48, 936_000_000.0, 86, 31),
        SectorRank("BK0477", "通信设备", 1.76, 512_000_000.0, 74, 43),
        SectorRank("BK0737", "证券", 0.84, 278_000_000.0, 31, 18),
        SectorRank("BK0480", "医药商业", -1.35, -426_000_000.0, 19, 67),
        SectorRank("BK0474", "白酒", -2.12, -814_000_000.0, 8, 53),
    )

    /**
     * 市场页新闻弹幕带（doc 36 §9.1）：真实/模拟模式均给一组带日内时间戳的演示快讯，
     * 供时间机器「随帧过滤」（回放到 HH:MM 只显示当时已发布的新闻）。
     * [time] 保持 `yyyy-MM-dd HH:mm:ss` 形状，与 NewsTape 的 formatTapeTime 口径一致。
     */
    fun marketNewsValue(): List<NewsItem> = listOf(
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
