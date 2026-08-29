package com.kuikly.stockchat.composer

/**
 * 输入期标的数据目录（规范 §4.2/§4.3）。
 *
 * Demo 内置目录：标的 + 指数 + 板块聚合项，预生成全拼/首字母拼音索引。
 * 后续接入远端搜索时，远端结果按同样结构并入 AtCandidate。
 */
enum class MentionType { STOCK, INDEX, BOARD, ETF }

data class CatalogEntry(
    val symbol: String,
    val name: String,
    val market: String,
    val pinyinFull: String = "",
    val pinyinAbbr: String = "",
    val kind: MentionType = MentionType.STOCK,
    /** 板块成分数量（仅 BOARD） */
    val boardCount: Int = 0,
    /** 热度分位 0..1 */
    val hot: Float = 0.5f,
    /** 是否自选（S2 数据源） */
    val watchlist: Boolean = false,
    /** 涨跌幅百分比（如 2.8 表示 +2.8%）；板块无涨跌为 null */
    val chgPct: Float? = null,
)

object ComposerCatalog {

    val stocks = listOf(
        CatalogEntry("600519.SH", "贵州茅台", "沪A", "guizhoumaotai", "gzmt", hot = 1.0f, watchlist = true, chgPct = 2.8f),
        CatalogEntry("000858.SZ", "五粮液", "深A", "wuliangye", "wly", hot = 0.9f, watchlist = true, chgPct = -4.1f),
        CatalogEntry("000333.SZ", "美的集团", "深A", "meidijituan", "mdjt", hot = 0.85f, watchlist = true, chgPct = 4.4f),
        CatalogEntry("300750.SZ", "宁德时代", "创业板", "ningdeshidai", "ndsd", hot = 0.95f, watchlist = true, chgPct = 4.8f),
        CatalogEntry("002594.SZ", "比亚迪", "深A", "biyadi", "byd", hot = 0.92f, chgPct = -4.3f),
        CatalogEntry("01211.HK", "比亚迪股份", "港股", "biyadigufen", "bydgf", hot = 0.7f, chgPct = 2.4f),
        CatalogEntry("601318.SH", "中国平安", "沪A", "zhongguopingan", "zgpa", hot = 0.8f, chgPct = 2.6f),
        CatalogEntry("000001.SZ", "平安银行", "深A", "pinganyinhang", "payh", hot = 0.8f, chgPct = 3.5f),
        CatalogEntry("600036.SH", "招商银行", "沪A", "zhaoshangyinhang", "zsyh", hot = 0.82f, chgPct = 2.2f),
        CatalogEntry("000651.SZ", "格力电器", "深A", "gelidianqi", "gldq", hot = 0.78f, watchlist = true, chgPct = 4.6f),
        CatalogEntry("600900.SH", "长江电力", "沪A", "changjiangdianli", "cjdl", hot = 0.7f, chgPct = 2.0f),
        CatalogEntry("601899.SH", "紫金矿业", "沪A", "zijinkuangye", "zjky", hot = 0.88f, chgPct = 4.1f),
        CatalogEntry("600276.SH", "恒瑞医药", "沪A", "hengruiyiyao", "hryy", hot = 0.75f, chgPct = 2.8f),
        CatalogEntry("601012.SH", "隆基绿能", "沪A", "longjilvneng", "ljln", hot = 0.72f, chgPct = 1.6f),
        CatalogEntry("002415.SZ", "海康威视", "深A", "haikangweishi", "hkws", hot = 0.7f, chgPct = 4.7f),
        CatalogEntry("600030.SH", "中信证券", "沪A", "zhongxinzhengquan", "zxzq", hot = 0.8f, chgPct = 1.5f),
        CatalogEntry("00700.HK", "腾讯控股", "港股", "tengxunkonggu", "txkg", hot = 0.98f, chgPct = 2.6f),
        CatalogEntry("03690.HK", "美团-W", "港股", "meituan", "mt", hot = 0.9f, chgPct = 3.6f),
        CatalogEntry("09988.HK", "阿里巴巴-W", "港股", "alibabA", "albb", hot = 0.9f, chgPct = -4.7f),
        CatalogEntry("AAPL.US", "苹果", "美股", "pingguo", "pg", hot = 0.95f, chgPct = -4.3f),
        CatalogEntry("NVDA.US", "英伟达", "美股", "yingweida", "ywd", hot = 1.0f, chgPct = -3.9f),
        CatalogEntry("TSLA.US", "特斯拉", "美股", "tesila", "tsl", hot = 0.93f, chgPct = -3.0f),
    )

    val indices = listOf(
        CatalogEntry("000001.SH", "上证指数", "指数", "shangzhengzhishu", "szzs", kind = MentionType.INDEX, hot = 1.0f, chgPct = 0.9f),
        CatalogEntry("399001.SZ", "深证成指", "指数", "shenzhengchengzhi", "szcz", kind = MentionType.INDEX, hot = 0.9f, chgPct = -4.6f),
        CatalogEntry("399006.SZ", "创业板指", "指数", "chuangyebanzhi", "cybz", kind = MentionType.INDEX, hot = 0.86f, chgPct = -4.0f),
        CatalogEntry("000688.SH", "科创50", "指数", "kechuang50", "kc50", kind = MentionType.INDEX, hot = 0.8f, chgPct = 3.1f),
        CatalogEntry("HSI.HK", "恒生指数", "指数", "hengshengzhishu", "hszs", kind = MentionType.INDEX, hot = 0.8f, chgPct = 4.7f),
        CatalogEntry("NDX.US", "纳斯达克", "指数", "nasidake", "nsdk", kind = MentionType.INDEX, hot = 0.85f, chgPct = -2.2f),
        CatalogEntry("SPX.US", "标普500", "指数", "biaopu500", "bp500", kind = MentionType.INDEX, hot = 0.8f, chgPct = -0.9f),
    )

    val boards = listOf(
        CatalogEntry("BK0477", "白酒", "板块", "baijiu", "bj", kind = MentionType.BOARD, boardCount = 68, hot = 0.9f),
        CatalogEntry("BK0475", "银行", "板块", "yinhang", "yh", kind = MentionType.BOARD, boardCount = 42, hot = 0.85f),
        CatalogEntry("BK0901", "新能源汽车", "板块", "xinnengyuanqiche", "xnyqc", kind = MentionType.BOARD, boardCount = 88, hot = 0.93f),
        CatalogEntry("BK0733", "CRO概念", "板块", "crogainian", "crogn", kind = MentionType.BOARD, boardCount = 25, hot = 0.6f),
        CatalogEntry("BK0490", "半导体", "板块", "bandaoti", "bdt", kind = MentionType.BOARD, boardCount = 120, hot = 0.95f),
        CatalogEntry("BK0428", "电力", "板块", "dianli", "dl", kind = MentionType.BOARD, boardCount = 75, hot = 0.7f),
    )

    /** 别名/曾用名（命中等级 0.40） */
    private val aliases = mapOf(
        "宁王" to "300750.SZ",
        "大毛" to "600519.SH",
        "茅茅" to "600519.SH",
    )

    val all: List<CatalogEntry> = stocks + indices

    fun aliasTarget(alias: String): CatalogEntry? =
        aliases[alias]?.let { a -> all.firstOrNull { it.symbol == a } }

    fun find(symbol: String): CatalogEntry? = all.firstOrNull { it.symbol == symbol }
}
