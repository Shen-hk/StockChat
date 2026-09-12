package com.kuikly.stockchat.detail.domain

/**
 * 公司介绍 DSL 的只读内容模型。
 *
 * 该目录只维护已核验的一句话事实和阅读线索；页面与 View DSL 只消费模型，
 * 不在渲染层编造未知标的的经营结论。
 */
internal data class DetailCompanyProfile(
    val summary: String?,
    val tags: List<String> = emptyList(),
    val focus: String? = null,
)

/** 本地公司画像目录。未知标的显式返回空画像，保留既有空态展示。 */
internal object DetailCompanyProfileCatalog {
    fun forSymbol(symbol: String): DetailCompanyProfile = when (symbol) {
        "600519.SH" -> DetailCompanyProfile("以茅台酒为核心产品，覆盖系列酒、葡萄酒与相关配套业务。", listOf("白酒", "高端消费"), "阅读营收增速、利润率与渠道相关披露。")
        "000858.SZ" -> DetailCompanyProfile("以五粮液品牌为核心，主营白酒产品的生产与销售。", listOf("白酒", "大众消费"), "可结合财务增速与行业景气度阅读。")
        "601318.SH" -> DetailCompanyProfile("综合金融集团，业务覆盖保险、银行、资产管理与医疗养老。", listOf("保险", "综合金融"), "重点看保险、银行等业务的披露口径变化。")
        "600036.SH" -> DetailCompanyProfile("全国性股份制商业银行，提供零售、公司及财富管理等金融服务。", listOf("银行", "财富管理"), "可从盈利、估值与股东数据交叉阅读。")
        "300750.SZ" -> DetailCompanyProfile("聚焦动力电池、储能电池与电池材料，服务新能源汽车和储能市场。", listOf("动力电池", "储能"), "结合行业资金、财务增速与公告追踪。")
        "002594.SZ" -> DetailCompanyProfile("业务覆盖新能源汽车、电池、电子及轨道交通等领域。", listOf("新能源汽车", "电池"), "业务跨度较广，阅读时注意分业务口径。")
        "688981.SH" -> DetailCompanyProfile("提供晶圆代工及配套技术服务，覆盖多类工艺平台与应用场景。", listOf("半导体", "晶圆制造"), "可结合行业景气、财务披露与公司公告理解。")
        "00700.HK" -> DetailCompanyProfile("互联网科技企业，业务覆盖社交、数字内容、金融科技与企业服务。", listOf("互联网", "数字内容"), "关注核心业务披露与行业景气变化。")
        else -> DetailCompanyProfile(null)
    }
}
