package com.kuikly.stockchat.data.entity

/**
 * 术语词典。术语翻译是立项调研中的第二痛点（⭐⭐⭐⭐⭐）：竞品通篇金融术语、
 * 不做通俗翻译，新手看不懂。因此每条词都要求「人话解释 + A 股语境例子」，
 * 技术指标类另附进阶解释，供展开态服务有经验的用户（自适应解释深度）。
 *
 * 词典同时驱动两处：AI 回答文本中的术语实体高亮（EntityRecognizer），
 * 以及术语表浏览页（GlossaryPage）。新增词条只需在此追加一条。
 */
enum class GlossaryCategory(val label: String) {
    TECHNICAL("技术指标"),
    VALUATION("估值指标"),
    MECHANISM("交易机制"),
    CAPITAL("资金与情绪"),
    FINANCIAL("财报概念"),
}

data class GlossaryEntry(
    /** 稳定标识，实体识别的 target；同时也是 AI 侧引用术语的键。 */
    val key: String,
    /** 展示名（中文优先）。 */
    val term: String,
    /** 英文缩写，参与识别与检索。 */
    val ascii: String = "",
    /** 其它叫法，参与识别与检索。 */
    val aliases: List<String> = emptyList(),
    val category: GlossaryCategory,
    /** 人话解释，不超过 60 字，禁止抄教科书。 */
    val plain: String,
    /** A 股语境下的一个例子。 */
    val example: String,
    /** 进阶解释（展开态展示）。技术指标类必填，其余可为空。 */
    val advanced: String = "",
) {
    /** 参与 Trie 匹配的全部词形（不含长度小于 2 的，避免单字误伤）。 */
    fun matchTokens(): List<String> =
        (listOf(term, ascii) + aliases).filter { it.length >= 2 }.distinct()

    fun searchHaystack(): String =
        (listOf(term, ascii) + aliases).joinToString(" ").uppercase()
}

object Glossary {
    val all: List<GlossaryEntry> = listOf(
        // ── 技术指标 ────────────────────────────────────────────────
        GlossaryEntry(
            key = "MACD", term = "MACD", ascii = "MACD", aliases = listOf("指数平滑异同平均", "平滑异同移动平均线"),
            category = GlossaryCategory.TECHNICAL,
            plain = "用两条均线的远近判断涨跌动力的强弱，比单看均线更早给出方向变化。",
            example = "茅台股价创新低但 MACD 绿柱在缩短，说明跌势在减弱，但还没到反转确认。",
            advanced = "由 DIF（快线）与 DEA（慢线）及柱状图组成：DIF 上穿 DEA 称金叉，下穿称死叉。柱状图由正转负代表短期动能衰减。单一周期易失灵，常需与成交量、价格位置交叉验证。",
        ),
        GlossaryEntry(
            key = "KDJ", term = "KDJ", ascii = "KDJ", aliases = listOf("随机指标"),
            category = GlossaryCategory.TECHNICAL,
            plain = "看当前价格在最近一段时间高低区间里的位置，判断是涨过头还是跌过头。",
            example = "短线连续上涨后 KDJ 的 J 值超过 100，通常是短期过热的提示，不宜追高。",
            advanced = "K 为快速线、D 为慢速线，J = 3K − 2D，敏感度最高。震荡市中 K 在 20 以下回升常被视为超卖修复；单边趋势市中会长期钝化，需要配合趋势指标使用。",
        ),
        GlossaryEntry(
            key = "RSI", term = "RSI", ascii = "RSI", aliases = listOf("相对强弱指标"),
            category = GlossaryCategory.TECHNICAL,
            plain = "把一段时间里涨的幅度和跌的幅度放在一起比，衡量买卖力量谁更强。",
            example = "RSI 升到 80 以上，说明这段时间买方明显占优，追进去的成本已经偏高。",
            advanced = "常用 6 日 / 12 日 / 24 日三个周期。超过 70 一般称超买、低于 30 称超卖，但强趋势中指标会长期停留在极端区，单看阈值容易误判。",
        ),
        GlossaryEntry(
            key = "MA", term = "均线", ascii = "MA", aliases = listOf("移动平均线", "平均线", "5日线", "20日线", "60日线"),
            category = GlossaryCategory.TECHNICAL,
            plain = "把最近若干天的收盘价平均后连成的线，用来抹平日常波动、看清方向。",
            example = "股价跌破 60 日均线且均线开始走平向下，中期趋势通常已经转弱。",
            advanced = "周期越短越灵敏、越滞后小；越长越稳但反应慢。多头排列（短期均线在长期之上）与空头排列是常用的趋势判断框架，均线同时充当支撑与压力。",
        ),
        GlossaryEntry(
            key = "BOLL", term = "布林带", ascii = "BOLL", aliases = listOf("布林线", "布林通道"),
            category = GlossaryCategory.TECHNICAL,
            plain = "在均线上下各画一条波动带，价格贴上轨算偏热、贴下轨算偏冷。",
            example = "股价沿布林上轨连续运行，说明短期偏强，但一旦开口收敛就要留意变盘。",
            advanced = "中轨为 20 日均线，上下轨为中轨 ± 2 倍标准差。带宽收窄代表波动率压缩、常预示变盘；开口扩张代表趋势展开。",
        ),
        GlossaryEntry(
            key = "GOLDEN_CROSS", term = "金叉", aliases = listOf("黄金交叉"),
            category = GlossaryCategory.TECHNICAL,
            plain = "短期指标线从下往上穿过长期指标线，常被视为转强的信号。",
            example = "MACD 出现金叉当天成交量同步放大，信号的可靠性比缩量金叉更高。",
            advanced = "金叉只是概率信号而非保证：零轴下方的金叉通常弱于零轴上方，震荡市中频繁出现的金叉多为噪音，需结合位置与量能过滤。",
        ),
        GlossaryEntry(
            key = "DEATH_CROSS", term = "死叉", aliases = listOf("死亡交叉"),
            category = GlossaryCategory.TECHNICAL,
            plain = "短期指标线从上往下穿过长期指标线，常被视为转弱的信号。",
            example = "高位出现死叉且跌破均线，往往比低位死叉更值得警惕。",
            advanced = "与金叉对称，同样受位置影响：低位缩量死叉可能是最后一跌，高位放量死叉的杀伤力通常更大。",
        ),
        GlossaryEntry(
            key = "DIVERGENCE", term = "背离", aliases = listOf("底背离", "顶背离"),
            category = GlossaryCategory.TECHNICAL,
            plain = "价格创新高（新低）而指标没有跟上，暗示当前趋势的动力在减弱。",
            example = "股价创阶段新低但 MACD 低点反而抬高，这种底背离常被解读为跌势衰竭。",
            advanced = "背离可以持续很久，属于「预警」而非「拐点确认」；需要价格结构（如突破前高）与量能配合才算被验证。",
        ),
        GlossaryEntry(
            key = "TURNOVER", term = "换手率", ascii = "HSL", aliases = listOf("换手"),
            category = GlossaryCategory.TECHNICAL,
            plain = "当天成交的股票占流通股的比例，衡量这只票今天有多热。",
            example = "换手率从 1% 突然放大到 15%，通常意味着有大资金进场或离场。",
            advanced = "不同市值区间的合理换手率差异很大：大盘蓝筹 1%-3% 属常态，小盘题材股 10% 以上才显活跃。需与历史分位而非绝对值比较。",
        ),
        GlossaryEntry(
            key = "VOLUME_RATIO", term = "量比", aliases = listOf("量比值"),
            category = GlossaryCategory.TECHNICAL,
            plain = "当前每分钟成交量与过去五天同期的比值，看今天是不是突然放量。",
            example = "开盘量比超过 3，说明早盘资金关注度明显提升。",
            advanced = "量比随时间衰减：开盘半小时的量比天然偏高，尾盘的意义有限，通常配合分时均价线一起看。",
        ),
        GlossaryEntry(
            key = "QFQ", term = "前复权", aliases = listOf("复权"),
            category = GlossaryCategory.TECHNICAL,
            plain = "把历史价格按分红送股倒推调整，让 K 线在除权前后连得上。",
            example = "看长期走势一定要用前复权，否则除权当天会出现假的暴跌缺口。",
            advanced = "前复权以最新价为基准回推历史价，适合看长期趋势；后复权以最早价为基准前推，适合看真实累计收益；不复权保留原始成交价。",
        ),
        GlossaryEntry(
            key = "HFQ", term = "后复权",
            category = GlossaryCategory.TECHNICAL,
            plain = "以最早的价格为基准往前推，保留你真实拿到手的累计涨幅。",
            example = "十年长牛股用后复权看，涨幅往往比前复权数字更惊人，因为把分红送股都算进去了。",
        ),
        GlossaryEntry(
            key = "SUPPORT", term = "支撑位", aliases = listOf("支撑区"),
            category = GlossaryCategory.TECHNICAL,
            plain = "股价下跌时比较容易止跌的位置，通常来自前期的密集成交区。",
            example = "股价三次回踩 60 日均线都没跌破，这条均线就是短期的支撑位。",
            advanced = "支撑与压力会互相转换：一旦有效跌破，原支撑往往变成后续反弹的压力。有效性取决于成交量与停留时间，而非精确点位。",
        ),
        GlossaryEntry(
            key = "RESISTANCE", term = "压力位", aliases = listOf("阻力位"),
            category = GlossaryCategory.TECHNICAL,
            plain = "股价上涨时比较难突破的位置，上方套牢盘多在此聚集。",
            example = "股价逼近前期高点却连续放量滞涨，说明上方压力位抛压较重。",
        ),
        GlossaryEntry(
            key = "GAP", term = "缺口", aliases = listOf("跳空", "跳空缺口"),
            category = GlossaryCategory.TECHNICAL,
            plain = "今天的最低价高于昨天的最高价（或相反），K 线之间出现的空白。",
            example = "业绩预增后一字涨停形成跳空缺口，通常代表消息面的强烈预期。",
            advanced = "分突破缺口、持续缺口与衰竭缺口；缺口回补与否取决于是否伴随量能。A 股一字板形成的缺口短期多不回补。",
        ),
        GlossaryEntry(
            key = "AMPLITUDE", term = "振幅",
            category = GlossaryCategory.TECHNICAL,
            plain = "当天最高价与最低价的差距，衡量这只票今天波动有多大。",
            example = "振幅 12% 说明多空分歧极大，持仓体验会很颠簸。",
        ),

        // ── 估值指标 ────────────────────────────────────────────────
        GlossaryEntry(
            key = "PE", term = "市盈率", ascii = "PE", aliases = listOf("本益比", "PE_TTM"),
            category = GlossaryCategory.VALUATION,
            plain = "股价除以每股收益，粗略理解成「按当前盈利多少年回本」。",
            example = "同为 30 倍市盈率，高增长的科技股和成熟的银行股，含义完全不同。",
            advanced = "静态 PE 用去年年报、TTM 用最近四个季度、动态 PE 用预测盈利，口径不同数值差异很大。亏损企业 PE 为负，此时应改用 PS 或现金流口径。",
        ),
        GlossaryEntry(
            key = "PB", term = "市净率", ascii = "PB",
            category = GlossaryCategory.VALUATION,
            plain = "股价除以每股净资产，看的是你为公司账面的家底付了多少钱。",
            example = "破净（PB 小于 1）在银行股中常见，但不等于一定便宜。",
            advanced = "重资产行业（银行、地产、钢铁）更适用 PB；轻资产公司的核心资产是人和品牌，账面净资产参考价值有限。",
        ),
        GlossaryEntry(
            key = "PS", term = "市销率", ascii = "PS",
            category = GlossaryCategory.VALUATION,
            plain = "市值除以营业收入，给还没赚钱但收入增长快的公司估值。",
            example = "创新药公司常年亏损，用 PE 没法比，常用市销率横向比较。",
        ),
        GlossaryEntry(
            key = "PEG", term = "PEG", ascii = "PEG", aliases = listOf("市盈率相对盈利增长比率"),
            category = GlossaryCategory.VALUATION,
            plain = "市盈率除以盈利增速，把「贵不贵」和「长得快不快」放在一起看。",
            example = "30 倍 PE 配 30% 增速，PEG 约等于 1，通常被认为估值与增长匹配。",
            advanced = "增速为负时 PEG 无意义；用未来预期增速计算会引入预测误差，敏感性很高，建议做多情景测算。",
        ),
        GlossaryEntry(
            key = "EPS", term = "每股收益", ascii = "EPS",
            category = GlossaryCategory.VALUATION,
            plain = "公司净利润摊到每一股上有多少，是算市盈率的分母。",
            example = "每股收益从 1 元涨到 1.5 元，若股价不变，市盈率会自然下降。",
        ),
        GlossaryEntry(
            key = "ROE", term = "净资产收益率", ascii = "ROE", aliases = listOf("净资产收益率"),
            category = GlossaryCategory.VALUATION,
            plain = "公司用股东的钱一年能赚回多少比例，长期看是衡量赚钱能力的核心指标。",
            example = "连续十年 ROE 超过 20% 的消费龙头，通常具备很强的品牌护城河。",
            advanced = "可用杜邦分析拆为净利率 × 资产周转率 × 权益乘数，高 ROE 若来自高杠杆，风险显著高于来自高净利率的情形。",
        ),
        GlossaryEntry(
            key = "DIVIDEND_YIELD", term = "股息率", aliases = listOf("分红率"),
            category = GlossaryCategory.VALUATION,
            plain = "一年分的现金红利除以股价，相当于这只股票的「利息」。",
            example = "股息率 5% 的银行股，在低利率环境下对长期资金有吸引力。",
            advanced = "需区分静态（去年分红 / 现价）与预期（今年预计分红 / 现价）；一次性特别分红会虚高静态股息率。",
        ),
        GlossaryEntry(
            key = "MARKET_CAP", term = "总市值", aliases = listOf("市值"),
            category = GlossaryCategory.VALUATION,
            plain = "股价乘以总股本，也就是市场给这家公司开的总价。",
            example = "总市值 2 万亿的公司上涨 1%，需要的资金量远大于小盘股。",
        ),
        GlossaryEntry(
            key = "FLOAT_MARKET_CAP", term = "流通市值", aliases = listOf("流通盘"),
            category = GlossaryCategory.VALUATION,
            plain = "按实际能在市场上买卖的股票算出来的市值，更贴近真实交易规模。",
            example = "流通市值小意味着少量资金就能拉动股价，波动通常更大。",
        ),
        GlossaryEntry(
            key = "VALUATION_PERCENTILE", term = "估值分位", aliases = listOf("历史分位", "估值百分位"),
            category = GlossaryCategory.VALUATION,
            plain = "当前估值在自己历史区间里所处的位置，用来判断相对高低。",
            example = "市盈率处于近十年 20% 分位，说明比过去八成的时间都便宜。",
            advanced = "分位数依赖样本区间与公司自身的经营周期，若基本面发生结构性变化（如主营业务转型），历史分位的参考性会下降。",
        ),

        // ── 交易机制 ────────────────────────────────────────────────
        GlossaryEntry(
            key = "T1", term = "T+1", aliases = listOf("T加一"),
            category = GlossaryCategory.MECHANISM,
            plain = "A 股当天买入的股票要等下一个交易日才能卖出，资金同样是次日可用。",
            example = "早上追高买入后当天大跌，T+1 制度下当天无法止损。",
        ),
        GlossaryEntry(
            key = "PRICE_LIMIT", term = "涨跌停", aliases = listOf("涨停", "跌停", "涨停板", "跌停板"),
            category = GlossaryCategory.MECHANISM,
            plain = "A 股对多数股票设单日涨跌幅上限（一般 10%，ST 股 5%），到价即停。",
            example = "一字涨停封单很大时，想买往往买不进，次日继续冲高的概率和开板风险并存。",
        ),
        GlossaryEntry(
            key = "CALL_AUCTION", term = "集合竞价", aliases = listOf("竞价"),
            category = GlossaryCategory.MECHANISM,
            plain = "开盘和收盘前几分钟集中撮合买卖单，一次性定出成交价。",
            example = "集合竞价高开 3%，通常反映隔夜消息的乐观预期。",
        ),
        GlossaryEntry(
            key = "SUSPENSION", term = "停牌", aliases = listOf("停牌中"),
            category = GlossaryCategory.MECHANISM,
            plain = "交易所因重大事项临时停止该股票交易，期间无法买卖。",
            example = "筹划重组的公司停牌数月，复牌后价格往往一次性反映全部预期。",
        ),
        GlossaryEntry(
            key = "EX_RIGHTS", term = "除权除息", aliases = listOf("除权", "除息", "XR", "DR"),
            category = GlossaryCategory.MECHANISM,
            plain = "分红送股后按比例调低股价，账面上看似下跌，实际资产并没有减少。",
            example = "10 送 10 后股价从 20 元变成 10 元，你手上的股票数量翻倍，总资产不变。",
        ),
        GlossaryEntry(
            key = "IPO_SUBSCRIPTION", term = "打新", aliases = listOf("新股申购", "申购新股"),
            category = GlossaryCategory.MECHANISM,
            plain = "在新股上市前按发行价申购，中签后通常在上市初期卖出。",
            example = "科创板打新需要对应的市值底仓，不是有钱就能参与。",
        ),
        GlossaryEntry(
            key = "BLOCK_TRADE", term = "大宗交易", aliases = listOf("大宗"),
            category = GlossaryCategory.MECHANISM,
            plain = "买卖双方在场外协商的大额交易，不进入集合竞价撮合。",
            example = "大股东通过大宗交易减持，接盘方通常有 6 个月锁定期。",
        ),
        GlossaryEntry(
            key = "MARGIN", term = "融资融券", aliases = listOf("两融", "融资", "融券"),
            category = GlossaryCategory.MECHANISM,
            plain = "融资是借钱买股票（看涨加杠杆），融券是借股票卖出（看跌）。",
            example = "两融余额持续攀升，说明市场上愿意加杠杆的资金在增加。",
            advanced = "杠杆会同时放大收益与亏损，维持担保比例低于警戒线会被要求追加保证金，否则触发强制平仓。",
        ),
        GlossaryEntry(
            key = "COMMISSION", term = "佣金", aliases = listOf("手续费"),
            category = GlossaryCategory.MECHANISM,
            plain = "买卖股票时付给券商的费用，按成交金额的一定比例收取。",
            example = "万分之三的佣金意味着 10 万元交易需要付 30 元。",
        ),
        GlossaryEntry(
            key = "STAMP_DUTY", term = "印花税",
            category = GlossaryCategory.MECHANISM,
            plain = "卖出股票时按成交金额缴纳给国家的税，目前 A 股为千分之一。",
            example = "卖出 10 万元股票，印花税 100 元，这是卖出端的固定成本。",
        ),
        GlossaryEntry(
            key = "CHINEXT", term = "创业板", aliases = listOf("创业板指"),
            category = GlossaryCategory.MECHANISM,
            plain = "面向成长型企业的板块，涨跌幅上限 20%，波动比主板更大。",
            example = "创业板股票单日涨跌可达 20%，持有体验更颠簸。",
        ),

        // ── 资金与情绪 ──────────────────────────────────────────────
        GlossaryEntry(
            key = "NORTHBOUND", term = "北向资金", aliases = listOf("北上资金", "陆股通"),
            category = GlossaryCategory.CAPITAL,
            plain = "通过沪深港通从香港流入 A 股的资金，常被当作外资态度的风向标。",
            example = "北向资金连续净买入消费龙头，往往被解读为外资看好。",
            advanced = "北向资金中混杂着借道的外资与内资，且每日只公布成交额前十大个股，单日数据噪音大，宜看趋势而非单日。",
        ),
        GlossaryEntry(
            key = "MAIN_CAPITAL", term = "主力资金", aliases = listOf("主力净流入", "主力"),
            category = GlossaryCategory.CAPITAL,
            plain = "按单笔成交金额划分出的大额资金净流入，用来观察大资金的进出。",
            example = "股价横盘但主力资金持续净流入，可能是资金在悄悄吸筹。",
            advanced = "「主力」的划分口径各平台不一致（单笔金额阈值不同），不同软件的数值不可直接比较，只适合同一平台内看相对变化。",
        ),
        GlossaryEntry(
            key = "DRAGON_TIGER", term = "龙虎榜", aliases = listOf("龙虎榜数据"),
            category = GlossaryCategory.CAPITAL,
            plain = "交易所每日公布的异动股票买卖前五席位，能看到谁在买谁在卖。",
            example = "龙虎榜上出现知名游资席位，次日往往吸引跟风资金关注。",
        ),
        GlossaryEntry(
            key = "NET_INFLOW", term = "净流入", aliases = listOf("资金净流入"),
            category = GlossaryCategory.CAPITAL,
            plain = "一段时间内买入资金减去卖出资金的差额，正数表示资金在进场。",
            example = "板块净流入居前，说明当天资金正在向这个方向集中。",
        ),
        GlossaryEntry(
            key = "SECTOR", term = "板块", aliases = listOf("行业板块", "概念板块"),
            category = GlossaryCategory.CAPITAL,
            plain = "把同行业或同一题材的股票归为一组，用来看资金的整体偏好。",
            example = "白酒板块集体走强时，个股很难走出独立行情。",
        ),
        GlossaryEntry(
            key = "LEADER_STOCK", term = "龙头股", aliases = listOf("龙头"),
            category = GlossaryCategory.CAPITAL,
            plain = "板块中最先启动、涨幅领先、最受资金关注的那只股票。",
            example = "龙头股封板后排跟风股补涨，龙头一旦炸板，跟风股往往跌得更快。",
        ),
        GlossaryEntry(
            key = "HOT_MONEY", term = "游资", aliases = listOf("热钱"),
            category = GlossaryCategory.CAPITAL,
            plain = "以短线快进快出为特点的投机资金，常集中炒作题材股。",
            example = "游资主导的行情通常来得快去得也快，追高容易被套在山顶。",
        ),
        GlossaryEntry(
            key = "CONCEPT_STOCK", term = "概念股", aliases = listOf("题材股"),
            category = GlossaryCategory.CAPITAL,
            plain = "因为某个热门题材（如人工智能）被资金追捧的股票，未必有对应业绩。",
            example = "概念炒作退潮时，没有业绩支撑的公司通常跌回原点。",
        ),
        GlossaryEntry(
            key = "SENTIMENT", term = "情绪面", aliases = listOf("市场情绪"),
            category = GlossaryCategory.CAPITAL,
            plain = "市场整体的乐观或恐慌程度，常从涨跌家数、连板高度观察。",
            example = "上涨家数不足一千家且跌停激增，说明情绪处于冰点。",
        ),
        GlossaryEntry(
            key = "AMOUNT", term = "成交额", aliases = listOf("成交金额"),
            category = GlossaryCategory.CAPITAL,
            plain = "当天成交股票的总金额，比成交量更能反映真实的资金规模。",
            example = "两市成交额从 8000 亿放大到 1.5 万亿，通常意味着增量资金入场。",
        ),

        // ── 财报概念 ────────────────────────────────────────────────
        GlossaryEntry(
            key = "REVENUE", term = "营业收入", aliases = listOf("营收", "销售收入"),
            category = GlossaryCategory.FINANCIAL,
            plain = "公司一段时间里卖东西收到的总收入，是利润的源头。",
            example = "营收增长但利润下滑，说明成本或费用吃掉了增量收入。",
        ),
        GlossaryEntry(
            key = "NET_PROFIT", term = "净利润", aliases = listOf("净利", "归属净利润"),
            category = GlossaryCategory.FINANCIAL,
            plain = "收入减去所有成本、费用和税之后真正赚到手的钱。",
            example = "净利润连续三个季度加速增长，通常是基本面转好的信号。",
        ),
        GlossaryEntry(
            key = "DEDUCTED_PROFIT", term = "扣非净利润", aliases = listOf("扣非"),
            category = GlossaryCategory.FINANCIAL,
            plain = "扣掉卖房、补贴这类一次性收益后的净利润，更能反映主业能力。",
            example = "净利润大增但扣非净利润亏损，说明盈利主要来自变卖资产。",
        ),
        GlossaryEntry(
            key = "GROSS_MARGIN", term = "毛利率", aliases = listOf("毛利"),
            category = GlossaryCategory.FINANCIAL,
            plain = "毛利占收入的比例，反映产品的赚钱空间和议价能力。",
            example = "毛利率长期稳定在 90% 以上，通常意味着极强的品牌壁垒。",
        ),
        GlossaryEntry(
            key = "NET_MARGIN", term = "净利率", aliases = listOf("净利率水平"),
            category = GlossaryCategory.FINANCIAL,
            plain = "净利润占收入的比例，扣除所有开销后真正落到口袋的比例。",
            example = "营收百亿但净利率只有 3%，属于典型的薄利多销生意。",
        ),
        GlossaryEntry(
            key = "YOY", term = "同比", aliases = listOf("同比增长", "同比增速"),
            category = GlossaryCategory.FINANCIAL,
            plain = "和去年同一个时期相比，用来消除淡旺季的影响。",
            example = "一季度营收同比增长 25%，是与去年一季度比较的结果。",
        ),
        GlossaryEntry(
            key = "MOM", term = "环比", aliases = listOf("环比增长"),
            category = GlossaryCategory.FINANCIAL,
            plain = "和上一个相邻时期相比，更能看出最近的变化趋势。",
            example = "环比由负转正，说明经营状况在最近一个季度出现改善。",
        ),
        GlossaryEntry(
            key = "EARNINGS_PREANNOUNCEMENT", term = "业绩预告", aliases = listOf("业绩预增", "业绩预亏", "预增"),
            category = GlossaryCategory.FINANCIAL,
            plain = "公司在正式财报前提前披露的业绩范围，是重要的预期管理工具。",
            example = "业绩预告大幅超预期，股价往往在正式财报前就已经反应。",
        ),
        GlossaryEntry(
            key = "FINANCIAL_REPORT", term = "财报", aliases = listOf("财务报告", "季报", "年报", "半年报"),
            category = GlossaryCategory.FINANCIAL,
            plain = "公司定期披露的经营成绩单，A 股为一季报、半年报、三季报和年报。",
            example = "年报披露后股价大跌，通常是业绩低于此前市场的预期。",
        ),
        GlossaryEntry(
            key = "GOODWILL", term = "商誉", aliases = listOf("商誉减值"),
            category = GlossaryCategory.FINANCIAL,
            plain = "收购时多付的那部分钱，一旦被收购方不达预期就要减值冲掉利润。",
            example = "商誉占净资产比例过高的公司，年报爆雷风险明显更大。",
        ),
        GlossaryEntry(
            key = "CASH_FLOW", term = "现金流", aliases = listOf("经营性现金流", "现金流量"),
            category = GlossaryCategory.FINANCIAL,
            plain = "公司实际收到和付出的现金，比账面利润更难被修饰。",
            example = "利润很高但经营性现金流长期为负，说明赚的多是应收账款。",
        ),
        GlossaryEntry(
            key = "DEBT_RATIO", term = "资产负债率", aliases = listOf("负债率"),
            category = GlossaryCategory.FINANCIAL,
            plain = "总负债占总资产的比例，衡量这家公司借了多少钱在经营。",
            example = "地产公司负债率 80% 以上属常见，但放在科技公司身上就相当危险。",
            advanced = "合理区间高度依赖行业：银行、地产天然高杠杆，制造业通常低于 60%。需结合有息负债结构与现金流覆盖能力判断。",
        ),
        GlossaryEntry(
            key = "DIVIDEND", term = "分红", aliases = listOf("派息", "送股"),
            category = GlossaryCategory.FINANCIAL,
            plain = "公司把赚到的利润按持股比例分给股东，可以是现金也可以是股票。",
            example = "每 10 股派 30 元，意味着持有 100 股能拿到 300 元现金（含税）。",
        ),
        GlossaryEntry(
            key = "INSTITUTIONAL_RESEARCH", term = "机构调研", aliases = listOf("调研"),
            category = GlossaryCategory.FINANCIAL,
            plain = "基金、券商等机构上门了解公司经营情况，调研密度常被当作关注度的指标。",
            example = "一个月接待十几批机构调研，通常意味着机构在认真评估这家公司。",
        ),
    )

    private val byKeyIndex: Map<String, GlossaryEntry> by lazy { all.associateBy { it.key } }

    fun byKey(key: String): GlossaryEntry? = byKeyIndex[key]

    /**
     * 检索术语表：中文名、英文缩写、别名均可命中，且支持片段匹配。
     * 用于术语表页面的搜索框（拼音首字母检索为 P1）。
     */
    fun search(query: String, limit: Int = 100): List<GlossaryEntry> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return all
        return all.mapNotNull { entry ->
            val haystack = entry.searchHaystack()
            val score = when {
                entry.term.uppercase() == q -> 100
                entry.ascii.equals(q, ignoreCase = true) -> 95
                entry.aliases.any { it.uppercase() == q } -> 90
                entry.term.uppercase().startsWith(q) -> 80
                entry.ascii.startsWith(q) -> 75
                haystack.contains(q) -> 60
                else -> return@mapNotNull null
            }
            score to entry
        }
            .sortedWith(compareByDescending<Pair<Int, GlossaryEntry>> { it.first }.thenBy { it.second.key })
            .map { it.second }
            .take(limit)
    }

    fun byCategory(category: GlossaryCategory): List<GlossaryEntry> = all.filter { it.category == category }

    /** 供 EntityRecognizer 构建 Trie：词形 → 稳定 key。 */
    fun matchTokens(): List<Pair<String, String>> =
        all.flatMap { entry -> entry.matchTokens().map { token -> token to entry.key } }
}
