package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.entity.Security
import com.kuikly.stockchat.data.provider.NewsItem
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EastMoneyInsightParser {
    data class MarketTotals(val turnoverAmount: Double)
    fun parseSecurities(root: JSONObject): List<Security> =
        root.optJSONObject("QuotationCodeTable")
            ?.optJSONArray("Data")
            ?.objects()
            .orEmpty()
            .mapNotNull { row ->
                val code = row.optString("Code")
                val name = row.optString("Name")
                if (code.isBlank() || name.isBlank()) return@mapNotNull null
                val marketNum = row.optString("MktNum")
                val typeName = row.optString("SecurityTypeName")
                // 判定顺序：板块/指数在前（它们的 MktNum 与个股前缀重叠，如板块 90、
                // 港股指数 116），再落个股；基金/债券等直接不进候选，避免面板出现噪音。
                val kind: String
                val market: String
                val symbol: String
                val stockMarkets = setOf("沪A", "深A", "京A", "B股", "港股", "美股")
                when {
                    typeName == "板块" || marketNum == "90" -> {
                        kind = "board"; market = "板块"; symbol = code  // Code 即 BKxxxx
                    }
                    typeName.contains("指数") -> {
                        kind = "index"; market = "指数"
                        symbol = when (marketNum) {
                            "1" -> "$code.SH"
                            "0" -> "$code.SZ"
                            "116" -> "$code.HK"
                            else -> return@mapNotNull null
                        }
                    }
                    typeName in stockMarkets ||
                        (typeName.isEmpty() && marketNum in setOf("1", "0", "116", "105", "106", "107")) -> {
                        kind = "stock"
                        market = typeName.ifEmpty {
                            when (marketNum) {
                                "1" -> "沪A"; "0" -> "深A"; "116" -> "港股"; else -> "美股"
                            }
                        }
                        symbol = when (marketNum) {
                            "1" -> "$code.SH"
                            "0" -> "$code.SZ"
                            "116" -> "$code.HK"
                            "105", "106", "107" -> "$code.US"
                            else -> return@mapNotNull null
                        }
                    }
                    else -> return@mapNotNull null
                }
                Security(
                    symbol = symbol,
                    name = name,
                    aliases = listOf(row.optString("PinYin"), code).filter(String::isNotBlank),
                    market = market,
                    kind = kind,
                )
            }
            .distinctBy(Security::symbol)

    fun parseFundFlow(root: JSONObject, asOf: String): FundFlow? {
        val row = root.rows("data", "diff").firstOrNull() ?: return null
        return FundFlow(
            main = row.double("f62"),
            superLarge = row.double("f66"),
            large = row.double("f72"),
            medium = row.double("f78"),
            small = row.double("f84"),
            stamp = marketStamp(asOf),
        )
    }

    fun parseFinancial(root: JSONObject): FinancialSummary? {
        val row = root.rows("result", "data").firstOrNull() ?: return null
        val reportDate = row.date("REPORTDATE")
        return FinancialSummary(
            reportDate = reportDate,
            revenue = row.double("TOTAL_OPERATE_INCOME"),
            netProfit = row.double("PARENT_NETPROFIT"),
            revenueYoY = row.double("YSTZ"),
            profitYoY = row.double("SJLTZ"),
            eps = row.double("BASIC_EPS"),
            roe = row.double("WEIGHTAVG_ROE"),
            grossMargin = row.double("XSMLL"),
            stamp = exchangeStamp(row.date("NOTICE_DATE").ifEmpty { reportDate }),
        )
    }

    fun parseShareholder(root: JSONObject): ShareholderSnapshot? {
        val row = root.rows("result", "data").firstOrNull() ?: return null
        return ShareholderSnapshot(
            holders = row.long("HOLDER_NUM"),
            change = row.long("HOLDER_NUM_CHANGE"),
            changePercent = row.double("HOLDER_NUM_RATIO"),
            period = row.date("END_DATE"),
            stamp = exchangeStamp(row.date("HOLD_NOTICE_DATE")),
        )
    }

    fun parseBillboard(root: JSONObject): BillboardRecord? {
        val row = root.rows("result", "data").firstOrNull() ?: return null
        return BillboardRecord(
            tradeDate = row.date("TRADE_DATE"),
            reason = row.optString("EXPLANATION").ifEmpty { row.optString("EXPLAIN") },
            buyAmount = row.double("BILLBOARD_BUY_AMT"),
            sellAmount = row.double("BILLBOARD_SELL_AMT"),
            netAmount = row.double("BILLBOARD_NET_AMT"),
            stamp = marketStamp(row.date("TRADE_DATE")),
        )
    }

    fun parseActions(root: JSONObject): List<CorporateAction> = root.rows("result", "data").mapNotNull { row ->
        val title = row.optString("IMPL_PLAN_PROFILE").ifEmpty { row.optString("ASSIGNDSCRPT") }
        if (title.isEmpty()) null else CorporateAction(
            title = title,
            date = row.date("EX_DIVIDEND_DATE").ifEmpty { row.date("NOTICE_DATE") },
            status = row.optString("ASSIGN_PROGRESS").ifEmpty { "已披露" },
            stamp = exchangeStamp(row.date("NOTICE_DATE")),
        )
    }

    fun parseAnnouncements(root: JSONObject, fallbackCode: String = ""): List<DisclosureItem> = root.rows("data", "list").map { row ->
        val id = row.optString("art_code")
        val title = row.optString("title_ch").ifEmpty { row.optString("title") }
        val date = row.date("notice_date").ifEmpty { row.date("display_time") }
        val code = row.optString("stock_code").ifEmpty { fallbackCode }
        DisclosureItem(
            id = id,
            kind = DisclosureKind.ANNOUNCEMENT,
            title = title,
            publisher = "上市公司公告",
            date = date,
            summary = summarizeTitle(title),
            riskLabel = riskLabel(title),
            url = if (id.isEmpty() || code.isEmpty()) "" else "https://data.eastmoney.com/notices/detail/$code/$id.html",
            stamp = exchangeStamp(date),
        )
    }

    fun parseReports(root: JSONObject): List<DisclosureItem> = root.rows(null, "data").map { row ->
        val title = row.optString("title")
        val date = row.date("publishDate")
        val publisher = row.optString("orgSName").ifEmpty { row.optString("orgName") }.ifEmpty { "券商研报" }
        DisclosureItem(
            id = row.optString("infoCode"),
            kind = DisclosureKind.RESEARCH,
            title = title,
            publisher = publisher,
            date = date,
            // 列表接口不提供研报正文。这里明确以标题提炼为摘要，避免长按预览把
            // 券商、日期等元数据误当成主要内容，也不把标题臆造成正文结论。
            summary = summarizeResearchTitle(title),
            riskLabel = "机构观点",
            url = "",
            stamp = SourceStamp(publisher, date, SourceTier.RESEARCH),
        )
    }

    /**
     * F3 研报评级光谱（2026-09-09 茅台实测）：report/list 每行带 emRatingName /
     * sRatingName（买入/增持/中性/减持及各家变体），按档位聚合并保留每档最近一份
     * 研报的「券商 日期：标题」作为观点原文。无评级字段的行不参与聚合；
     * 全部为空时返回 null（UI 回落演示段）。
     */
    fun parseRatingSpectrum(root: JSONObject, asOf: String): RatingSpectrum? {
        data class Row(val bucket: String, val date: String, val quote: String)
        val rows = root.rows(null, "data").mapNotNull { row ->
            val rating = row.optString("emRatingName").ifEmpty { row.optString("sRatingName") }
            val bucket = ratingBucket(rating) ?: return@mapNotNull null
            val title = row.optString("title").trim()
            val publisher = row.optString("orgSName").ifEmpty { row.optString("orgName") }.ifEmpty { "券商研报" }
            Row(bucket, row.date("publishDate"), "$publisher ${row.date("publishDate")}：${title.take(72)}")
        }
        if (rows.isEmpty()) return null
        val segments = listOf("买入", "增持", "中性", "减持").mapNotNull { label ->
            val bucket = rows.filter { it.bucket == label }
            if (bucket.isEmpty()) return@mapNotNull null
            val latest = bucket.maxBy { it.date }
            RatingSpectrumSegment(label, bucket.size, latest.quote)
        }
        if (segments.isEmpty()) return null
        return RatingSpectrum(segments, SourceStamp("东方财富研报库 · 券商评级", asOf, SourceTier.RESEARCH))
    }

    /** 券商评级措辞归档：各家口径不一（推荐/优于大市/持有等），按关键词就近归档。 */
    private fun ratingBucket(rating: String): String? = when {
        rating.isEmpty() -> null
        "买入" in rating -> "买入"
        "增持" in rating || "推荐" in rating || "优于" in rating -> "增持"
        "中性" in rating || "持有" in rating || "观望" in rating || "区间" in rating -> "中性"
        "减持" in rating || "卖出" in rating || "回避" in rating -> "减持"
        else -> null
    }

    fun withAnnouncementContent(item: DisclosureItem, contentRoot: JSONObject?): DisclosureItem {
        val content = contentRoot?.optJSONObject("data")?.optString("notice_content").orEmpty()
        if (content.isBlank()) return item
        val cleaned = content.replace(Regex("\\s+"), " ").trim()
        val summary = cleaned
            .substringAfter("重要提示", cleaned)
            .take(900)
            .trim()
            .ifEmpty { item.summary }
        return item.copy(summary = summary, riskLabel = riskLabel("${item.title} $summary"))
    }

    fun parseIndices(root: JSONObject): List<MarketIndex> = root.rows("data", "diff").map { row ->
        MarketIndex(
            code = row.optString("f12"),
            name = row.optString("f14"),
            price = row.double("f2"),
            changePercent = row.double("f3"),
            high = row.double("f15").takeIf { it > 0.0 },
            low = row.double("f16").takeIf { it > 0.0 },
        )
    }

    fun parseSectors(root: JSONObject): List<SectorRank> = root.rows("data", "diff").map { row ->
        SectorRank(
            code = row.optString("f12"),
            name = row.optString("f14"),
            changePercent = row.double("f3"),
            mainFlow = row.double("f62"),
            risingCount = row.int("f104"),
            fallingCount = row.int("f105"),
        )
    }

    fun parseBreadth(root: JSONObject): Triple<Int, Int, Int> {
        var rising = 0
        var falling = 0
        var flat = 0
        root.rows("data", "diff").forEach { row ->
            when {
                row.double("f3") > 0.0 -> rising++
                row.double("f3") < 0.0 -> falling++
                else -> flat++
            }
        }
        return Triple(rising, falling, flat)
    }

    /**
     * 按市场大类解析宽度样本（2026-09-08）。f3 带不上数值（停牌等 "-"）的行不参与
     * 涨跌判定，计入 total 与 counted 的差额（uncovered）；data.total 为该大类全部
     * 证券数，f6 求和为该大类成交额。
     */
    fun parseBreadthSample(root: JSONObject, name: String): BreadthSample {
        var rising = 0
        var falling = 0
        var flat = 0
        var amount = 0.0
        root.rows("data", "diff").forEach { row ->
            val change = row.optString("f3").toDoubleOrNull()
            amount += row.double("f6")
            when {
                change == null -> Unit
                change > 0.0 -> rising++
                change < 0.0 -> falling++
                else -> flat++
            }
        }
        val counted = rising + falling + flat
        val total = root.optJSONObject("data")?.optString("total")?.toIntOrNull() ?: counted
        return BreadthSample(name, rising, falling, flat, total, amount)
    }

    /** The broad market list carries the market-wide成交额 in f6 for every row. */
    fun parseMarketTotals(root: JSONObject): MarketTotals {
        val amount = root.rows("data", "diff").sumOf { it.double("f6") }
        return MarketTotals(amount)
    }

    fun parseLimitUps(root: JSONObject): List<LimitUpStock> {
        val rows = root.optJSONObject("data")?.optJSONArray("pool") ?: return emptyList()
        return rows.objects().map { row ->
            val code = row.optString("c")
            val suffix = if (row.int("m") == 1) "SH" else "SZ"
            LimitUpStock(
                symbol = "$code.$suffix",
                name = row.optString("n").replace(" ", ""),
                changePercent = row.double("zdp"),
                sector = row.optString("hybk").ifEmpty { "其他" },
                consecutiveBoards = row.int("lbc").coerceAtLeast(1),
                sealedAmount = row.double("fund"),
                openCount = row.int("zbc"),
            )
        }
    }

    fun parsePoolCount(root: JSONObject): Int = root.optJSONObject("data")?.int("tc") ?: 0

    /**
     * 东财个股资讯 `np-listapi`（2026-09-07 茅台实测）：字段
     * Art_Code / Art_Title / Art_ShowTime / Author / Art_Url，UTF-8。
     * 按 Art_Code 去重，标题为空或与代码弱相关的榜单行不做过滤（交给 UI 端克制呈现）。
     */
    fun parseNews(root: JSONObject): List<NewsItem> = root.rows("data", "list")
        .mapNotNull { row ->
            val id = row.optString("Art_Code")
            val title = row.optString("Art_Title").trim()
            if (id.isEmpty() || title.isEmpty()) return@mapNotNull null
            NewsItem(
                id = id,
                title = title,
                source = row.optString("Author").ifEmpty { "东方财富" },
                time = row.optString("Art_ShowTime"),
                url = row.optString("Art_Url"),
            )
        }
        .distinctBy(NewsItem::id)

    fun parseCalendar(root: JSONObject): List<MarketCalendarEvent> = root.rows("result", "data").mapNotNull { row ->
        val code = row.optString("SECURITY_CODE")
        val date = row.date("APPOINT_PUBLISH_DATE").ifEmpty { row.date("ACTUAL_PUBLISH_DATE") }
        if (code.isEmpty() || date.isEmpty()) null else MarketCalendarEvent(
            date = date,
            symbol = row.optString("SECUCODE").ifEmpty { code },
            name = row.optString("SECURITY_NAME_ABBR"),
            kind = CalendarEventKind.EARNINGS,
            title = "${row.optString("REPORT_TYPE_NAME").ifEmpty { "预约披露财报" }}",
            stamp = exchangeStamp(date),
        )
    }

    /** f12=代码 f13=市场 f100=行业 → symbol → 行业名。空白行业（指数等）不落表。 */
    fun parseIndustries(root: JSONObject, symbols: List<String>): Map<String, String> {
        val byCodeAndMarket = HashMap<String, String>()
        root.rows("data", "diff").forEach { row ->
            val code = row.optString("f12")
            val market = row.optString("f13")
            val industry = row.optString("f100").trim()
            if (code.isNotEmpty() && industry.isNotEmpty() && industry != "-") {
                byCodeAndMarket["$market.$code"] = industry
            }
        }
        return buildMap {
            symbols.forEach { symbol ->
                val code = symbol.substringBefore('.')
                val market = if (symbol.endsWith(".SH", ignoreCase = true)) "1" else "0"
                byCodeAndMarket["$market.$code"]?.let { put(symbol, it) }
            }
        }
    }

    private fun summarizeTitle(title: String): String = when {
        "业绩" in title || "报告" in title -> "公司披露定期报告或业绩相关信息。优先核对收入、利润、现金流及同比变化。"
        "分红" in title || "权益" in title -> "公告涉及股东回报或权益变动，请以登记日、除权日和实施进度为准。"
        "风险" in title -> "公告包含风险相关信息，建议直接阅读原文中的风险范围、影响期间和应对措施。"
        "股东" in title || "减持" in title || "增持" in title -> "公告涉及股东或持股变化，需区分计划、实施进展与已完成三个阶段。"
        else -> "这是公司正式披露信息。摘要只帮助定位重点，关键事实请以公告原文为准。"
    }

    /**
     * 东财研报列表只有标题和机构元数据，不能伪造正文摘要。去除栏目/括号等噪声后，
     * 把标题中的核心判断组织成可读的长按摘要，并保留机构观点的边界说明。
     */
    private fun summarizeResearchTitle(title: String): String {
        val cleaned = title
            .replace(Regex("[【\\[][^】\\]]*[】\\]]"), "")
            .replace(Regex("[（(][^）)]{0,36}[）)]"), "")
            .replace(Regex("^(首次覆盖|深度研究|公司研究|行业研究|点评)\\s*[：:]?\\s*"), "")
            .trim()
            .ifEmpty { "该机构发布了公司研究报告" }
        val focus = cleaned.substringAfter('：', cleaned).substringAfter(':', cleaned).trim()
        return "标题提炼：$focus。报告结论、评级和盈利预测仅代表该机构观点，需结合原报告的假设与风险提示判断。"
    }

    private fun riskLabel(text: String): String = when {
        listOf("风险", "亏损", "下降", "减持", "处罚", "终止", "诉讼").any { it in text } -> "关注风险"
        listOf("增长", "增持", "分红", "中标", "回购").any { it in text } -> "积极事项"
        else -> "中性披露"
    }

    private fun marketStamp(asOf: String) = SourceStamp("东方财富公开行情", asOf, SourceTier.MARKET_DATA)
    private fun exchangeStamp(asOf: String) = SourceStamp("上市公司/交易所披露", asOf, SourceTier.EXCHANGE)

    private fun JSONObject.rows(parent: String?, key: String): List<JSONObject> {
        val container = if (parent == null) this else optJSONObject(parent) ?: return emptyList()
        return container.optJSONArray(key)?.objects().orEmpty()
    }
    private fun JSONArray.objects(): List<JSONObject> = buildList {
        repeat(length()) { index -> optJSONObject(index)?.let(::add) }
    }
    private fun JSONObject.double(key: String): Double = optString(key).toDoubleOrNull() ?: 0.0
    private fun JSONObject.long(key: String): Long = optString(key).toDoubleOrNull()?.toLong() ?: 0L
    private fun JSONObject.int(key: String): Int = optString(key).toDoubleOrNull()?.toInt() ?: 0
    private fun JSONObject.date(key: String): String = optString(key).take(10)
}

class EastMoneyInsightProvider(
    private val scheduler: PlatformScheduler,
    private val client: PlatformHttpClient = createPlatformHttpClient(),
) : FundFlowProvider, FundamentalProvider, DisclosureProvider, MarketOverviewProvider, SecuritySearchProvider, IndustryProvider, StockNewsProvider, RatingSpectrumProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val throttle = Mutex()
    private var lastRequestAt = 0L

    override fun searchSecurities(query: String, onResult: (List<Security>) -> Unit) {
        scope.launch {
            if (query.isBlank()) {
                deliver { onResult(emptyList()) }
                return@launch
            }
            val encoded = encodeUrlQueryComponent(query.trim())
            val url = "https://searchapi.eastmoney.com/api/suggest/get?input=$encoded&type=14&token=D43BF722C8E33D1E3C1E4A8C9DFD2A52&count=20"
            val result = request(url)?.let(EastMoneyInsightParser::parseSecurities).orEmpty()
            deliver { onResult(result) }
        }
    }

    override fun fundFlow(symbol: String, onResult: (FundFlow?) -> Unit) {
        scope.launch {
            val fields = "f12,f14,f2,f3,f62,f66,f72,f78,f84"
            val root = request("https://push2.eastmoney.com/api/qt/ulist.np/get?fltt=2&invt=2&fields=$fields&secids=${secId(symbol)}")
            deliver { onResult(root?.let { EastMoneyInsightParser.parseFundFlow(it, platformCurrentDate()) }) }
        }
    }

    override fun fundamentals(symbol: String, onResult: (FundamentalBundle?) -> Unit) {
        scope.launch {
            val code = symbol.substringBefore('.')
            val filter = "(SECURITY_CODE=%22$code%22)"
            val financial = request(dataCenterUrl("RPT_LICO_FN_CPD", filter, "REPORTDATE", 1))
            val holder = request(dataCenterUrl("RPT_HOLDERNUMLATEST", filter, "END_DATE", 1))
            val billboard = request(dataCenterUrl("RPT_DAILYBILLBOARD_DETAILSNEW", filter, "TRADE_DATE", 1))
            val actions = request(dataCenterUrl("RPT_SHAREBONUS_DET", filter, "REPORT_DATE", 3))
            val bundle = FundamentalBundle(
                financial = financial?.let(EastMoneyInsightParser::parseFinancial),
                shareholder = holder?.let(EastMoneyInsightParser::parseShareholder),
                billboard = billboard?.let(EastMoneyInsightParser::parseBillboard),
                actions = actions?.let(EastMoneyInsightParser::parseActions).orEmpty(),
            ).takeIf { it.financial != null || it.shareholder != null || it.billboard != null || it.actions.isNotEmpty() }
            deliver { onResult(bundle) }
        }
    }

    override fun disclosures(symbol: String, onResult: (List<DisclosureItem>) -> Unit) {
        scope.launch {
            val code = symbol.substringBefore('.')
            val annRoot = request("https://np-anotice-stock.eastmoney.com/api/security/ann?sr=-1&page_size=6&page_index=1&ann_type=A&client_source=web&stock_list=$code")
            var announcements = annRoot?.let { EastMoneyInsightParser.parseAnnouncements(it, code) }.orEmpty()
            // 列表接口只给标题；长按详情必须有可读的公告正文，不用标题摘要冒充文章。
            // 只补前 3 条（详情页展示上限），避免后台无节制拉取历史公告。
            if (announcements.isNotEmpty()) {
                val hydrated = mutableListOf<DisclosureItem>()
                var index = 0
                while (index < announcements.size) {
                    val item = announcements[index]
                    val resolved = if (index < 3 && item.id.isNotEmpty()) {
                        val content = request("https://np-cnotice-stock.eastmoney.com/api/content/ann?art_code=${item.id}&client_source=web&page_index=1")
                        EastMoneyInsightParser.withAnnouncementContent(item, content)
                    } else item
                    hydrated += resolved
                    index++
                }
                announcements = hydrated
            }
            val reportsRoot = request("https://reportapi.eastmoney.com/report/list?pageSize=5&pageNo=1&qType=0&beginTime=2024-01-01&endTime=${platformCurrentDate()}&code=$code")
            val reports = reportsRoot?.let(EastMoneyInsightParser::parseReports).orEmpty()
            deliver { onResult((announcements.take(4) + reports.take(3))) }
        }
    }

    override fun overview(onResult: (MarketOverview?) -> Unit) {
        scope.launch {
            val indices = request("https://push2.eastmoney.com/api/qt/ulist.np/get?fltt=2&invt=2&fields=f12,f14,f2,f3,f15,f16&secids=1.000001,0.399001,0.399006,1.000688,0.899050,1.000300,1.000016,1.000905,100.HSI")
            // 2026-09-08：宽度样本按市场大类分别拉取（沪主板/科创板/深主板/创业板/北交所），
            // 情绪算法覆盖全市场；每类 total 与可判定家数的差额（停牌等）按大类透传给页底标注。
            val breadthSamples = breadthCategories.mapNotNull { (name, fs) ->
                request(breadthCategoryUrl(fs))?.let { EastMoneyInsightParser.parseBreadthSample(it, name) }
            }
            val sectors = request(sectorUrl(20))
            val date = platformCurrentDate(compact = true)
            val upPool = request(limitPoolUrl(false, date, 100))
            val downPool = request(limitPoolUrl(true, date, 100))
            val indexValues = indices?.let(EastMoneyInsightParser::parseIndices).orEmpty()
            val sectorValues = sectors?.let(EastMoneyInsightParser::parseSectors).orEmpty()
            val limitUps = upPool?.let(EastMoneyInsightParser::parseLimitUps).orEmpty()
            val result = MarketOverview(
                indices = indexValues,
                risingCount = breadthSamples.sumOf { it.rising },
                fallingCount = breadthSamples.sumOf { it.falling },
                flatCount = breadthSamples.sumOf { it.flat },
                limitUpCount = upPool?.let(EastMoneyInsightParser::parsePoolCount) ?: 0,
                limitDownCount = downPool?.let(EastMoneyInsightParser::parsePoolCount) ?: 0,
                sectors = sectorValues,
                stamp = SourceStamp("东方财富公开行情", platformCurrentDate(), SourceTier.MARKET_DATA),
                turnoverAmount = breadthSamples.sumOf { it.amount }.takeIf { it > 0.0 },
                sealRate = limitUps.takeIf { it.isNotEmpty() }?.let { rows -> rows.count { it.openCount == 0 }.toDouble() / rows.size },
                brokenBoardCount = limitUps.sumOf { it.openCount },
                highestBoard = limitUps.maxOfOrNull { it.consecutiveBoards },
                breadthSamples = breadthSamples,
            ).takeIf { it.indices.isNotEmpty() || it.sectors.isNotEmpty() || it.breadthSamples.isNotEmpty() }
            deliver { onResult(result) }
        }
    }

    override fun hotspots(onResult: (HotspotSnapshot?) -> Unit) {
        scope.launch {
            val sectors = request(sectorUrl(20))
            val pool = request(limitPoolUrl(false, platformCurrentDate(compact = true), 100))
            val result = HotspotSnapshot(
                sectors = sectors?.let(EastMoneyInsightParser::parseSectors).orEmpty(),
                limitUps = pool?.let(EastMoneyInsightParser::parseLimitUps).orEmpty(),
                stamp = SourceStamp("东方财富板块与涨停池", platformCurrentDate(), SourceTier.MARKET_DATA),
            ).takeIf { it.sectors.isNotEmpty() || it.limitUps.isNotEmpty() }
            deliver { onResult(result) }
        }
    }

    override fun calendar(onResult: (List<MarketCalendarEvent>) -> Unit) {
        scope.launch {
            val filter = "(APPOINT_PUBLISH_DATE%3E=%27${platformCurrentDate()}%27)"
            val root = request(dataCenterUrl("RPT_PUBLIC_BS_APPOIN", filter, "APPOINT_PUBLISH_DATE", 40, ascending = true))
            deliver { onResult(root?.let(EastMoneyInsightParser::parseCalendar).orEmpty()) }
        }
    }

    /** 一次批量请求拉取整份自选的行业归属（f100）。 */
    override fun industries(symbols: List<String>, onResult: (Map<String, String>) -> Unit) {
        if (symbols.isEmpty()) {
            deliver { onResult(emptyMap()) }
            return
        }
        scope.launch {
            val secids = symbols.joinToString(",") { secId(it) }
            // f12=代码 f13=市场(1=SH/0=SZ) f100=行业；f13 必须带上：
            // 000001.SH(上证指数) 与 000001.SZ(平安银行) 只靠代码会撞车。
            val root = request("https://push2.eastmoney.com/api/qt/ulist.np/get?fltt=2&invt=2&fields=f12,f13,f100&secids=$secids")
            deliver { onResult(root?.let { EastMoneyInsightParser.parseIndustries(it, symbols) }.orEmpty()) }
        }
    }

    /** 东财个股资讯：mTypeAndCode 用 `1.沪代码` / `0.深代码`；接口要求 UA + Referer。 */
    override fun stockNews(symbol: String, onResult: (List<NewsItem>) -> Unit) {
        scope.launch {
            val code = symbol.substringBefore('.')
            val market = if (symbol.endsWith(".SH", ignoreCase = true)) "1" else "0"
            val url = "https://np-listapi.eastmoney.com/comm/web/getListInfo?cfh=1&client=web" +
                "&mTypeAndCode=$market.$code&type=1&pageSize=20&pageIndex=1"
            val root = request(url)
            deliver { onResult(root?.let(EastMoneyInsightParser::parseNews).orEmpty()) }
        }
    }

    /** 近 90 天券商研报评级聚合（F3 光谱真实数据源）。beginTime 必填（缺失时接口 400）。 */
    override fun ratingSpectrum(symbol: String, onResult: (RatingSpectrum?) -> Unit) {
        scope.launch {
            val code = symbol.substringBefore('.')
            val begin = platformDateDaysAgo(90)
            val url = "https://reportapi.eastmoney.com/report/list?pageSize=50&pageNo=1&qType=0&beginTime=$begin&endTime=${platformCurrentDate()}&code=$code"
            val root = request(url)
            deliver { onResult(root?.let { EastMoneyInsightParser.parseRatingSpectrum(it, platformCurrentDate()) }) }
        }
    }

    private suspend fun request(url: String): JSONObject? = try {
        throttle.withLock {
            val wait = 210L - (platformCurrentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = platformCurrentTimeMillis()
        }
        val response = client.get(url, eastMoneyHeaders)
        if (response.status !in 200..299) null else runCatching { JSONObject(response.body) }.getOrNull()
    } catch (_: Throwable) {
        null
    }

    private fun deliver(block: () -> Unit) {
        scheduler.schedule(0) { block() }
    }

    private fun secId(symbol: String): String {
        val code = symbol.substringBefore('.')
        return when (symbol.substringAfter('.', "").uppercase()) {
            "SH" -> "1.$code"
            // 港股在东财 secid 体系里是 116 前缀；此前港股资金流/基本面全部静默失败。
            "HK" -> "116.$code"
            else -> "0.$code"
        }
    }

    private fun dataCenterUrl(report: String, filter: String, sort: String, size: Int, ascending: Boolean = false): String =
        "https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=$report&columns=ALL&filter=$filter&pageNumber=1&pageSize=$size&sortTypes=${if (ascending) 1 else -1}&sortColumns=$sort"

    /** 宽度样本的市场大类拆分（fs 口径）：沪主板/科创板/深主板/创业板/北交所，覆盖全部 A 股。 */
    private val breadthCategories = listOf(
        "沪市主板" to "m:1+t:2",
        "科创板" to "m:1+t:23",
        "深市主板" to "m:0+t:6",
        "创业板" to "m:0+t:80",
        "北交所" to "m:0+t:81",
    )

    private fun breadthCategoryUrl(fs: String): String =
        "https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=6000&po=1&np=1&fltt=2&invt=2&fid=f3&fs=$fs&fields=f3,f6"

    private fun sectorUrl(size: Int): String =
        "https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=$size&po=1&np=1&fltt=2&invt=2&fid=f3&fs=m:90+t:2&fields=f12,f14,f3,f62,f104,f105,f106"

    private fun limitPoolUrl(down: Boolean, date: String, size: Int): String {
        val topic = if (down) "DTPool" else "ZTPool"
        val dpt = if (down) "wz.ztzt" else "wz.ztzt"
        return "https://push2ex.eastmoney.com/getTopic$topic?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=$dpt&Pageindex=0&pagesize=$size&sort=fbt:asc&date=$date"
    }

    private val eastMoneyHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 StockChat/1.0",
        "Referer" to "https://quote.eastmoney.com/",
        "Accept" to "application/json,text/plain,*/*",
    )
}

private fun encodeUrlQueryComponent(value: String): String = buildString {
    val hex = "0123456789ABCDEF"
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        if ((code in 'a'.code..'z'.code) || (code in 'A'.code..'Z'.code) ||
            (code in '0'.code..'9'.code) || code == '-'.code || code == '_'.code ||
            code == '.'.code || code == '~'.code) {
            append(code.toChar())
        } else {
            append('%').append(hex[code shr 4]).append(hex[code and 0x0F])
        }
    }
}
