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
            advanced = "后复权把分红、送股等收益累计到后续价格中，适合衡量长期持有的总回报，但价格不对应当时真实成交价。",
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
            advanced = "压力位是成交密集区而非精确价格；放量有效突破后，原压力常会转为回踩支撑，缩量假突破则更容易回落。",
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
            advanced = "振幅通常按（最高价－最低价）÷昨收价计算；高位放量大振幅常代表分歧加剧，低位则需结合换手与趋势判断。",
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

        // ── 风险与组合（FR-K8 扩充：风险地图的术语出口需要这些词） ──
        GlossaryEntry(
            key = "POSITION", term = "仓位", aliases = listOf("持仓比例", "轻仓", "重仓"),
            category = GlossaryCategory.MECHANISM,
            plain = "你投进股市的钱占可用资金的比例，几成仓就是说投了百分之几十。",
            example = "「半仓」= 一半资金买了股票，剩下一半还是现金，跌了还有钱补，涨了也不踏空。",
        ),
        GlossaryEntry(
            key = "WEIGHT", term = "权重", aliases = listOf("占比"),
            category = GlossaryCategory.MECHANISM,
            plain = "一只股票在你全部持仓里占的比例，权重越大，它涨跌对你的总资产影响越大。",
            example = "10 万本金里 6 万买茅台，茅台权重就是 60%，它跌 5% 你的组合就跌约 3%。",
        ),
        GlossaryEntry(
            key = "DIVERSIFY", term = "分散投资", aliases = listOf("分散", "鸡蛋不放一个篮子"),
            category = GlossaryCategory.MECHANISM,
            plain = "把钱分到不同行业、不同类型的标的上，让单一标的的坏消息不至于伤到全部。",
            example = "全部押一只券商股，行情冷就全亏；分一半到公用事业股，波动会明显变小。",
        ),
        GlossaryEntry(
            key = "REBALANCE", term = "再平衡", aliases = listOf("调仓再平衡"),
            category = GlossaryCategory.MECHANISM,
            plain = "定期把涨大了、占比变高的部分卖一些，买回占比变低的，恢复原来的分配。",
            example = "原定股票各占一半，股票涨到七成后卖出一部分买回另一边，涨的落袋、跌的补位。",
        ),
        GlossaryEntry(
            key = "RESTRICTED_SHARE", term = "限售股", aliases = listOf("限售股份"),
            category = GlossaryCategory.MECHANISM,
            plain = "上市时承诺一段时间内不能卖的股票，到期才能流通。",
            example = "新股上市一年后，原始股东的限售股解禁流通，市场上的可卖股票变多。",
        ),
        GlossaryEntry(
            key = "UNLOCK", term = "解禁", aliases = listOf("限售解禁", "解禁期"),
            category = GlossaryCategory.MECHANISM,
            plain = "限售股到期可以卖了。可卖筹码突然变多，股价常在解禁日前承压。",
            example = "公告下月有占总股本 20% 的解禁，说明大股东理论上可以卖，抛压预期会先反映在股价里。",
        ),
        GlossaryEntry(
            key = "SHARE_REDUCTION", term = "减持", aliases = listOf("股东减持", "减持公告"),
            category = GlossaryCategory.MECHANISM,
            plain = "大股东或高管卖出自家股票。人数多、金额大时，常被解读为对股价没信心。",
            example = "公告三位高管拟合计减持 2%，短线情绪通常受压，要看减持比例和用途。",
        ),
        GlossaryEntry(
            key = "STD_DEV", term = "标准差", aliases = listOf("均方差"),
            category = GlossaryCategory.TECHNICAL,
            plain = "衡量一组数字离平均有多散。日收益的标准差大 = 这只票上蹿下跳得厉害。",
            example = "A 股日波动标准差 1%，B 股 3%，同样的行情下 B 的过山车幅度大约是 A 的三倍。",
            advanced = "日标准差 × √250 可粗略年化。它衡量的是波动幅度本身，不分涨跌方向——大涨大跌都会推高标准差。",
        ),
        GlossaryEntry(
            key = "VOLATILITY", term = "波动率", ascii = "VOL", aliases = listOf("波动性"),
            category = GlossaryCategory.TECHNICAL,
            plain = "价格波动剧烈程度的指标，通常由日收益标准差年化而来，越高越颠簸。",
            example = "说某票「波动率是大盘两倍」，意思是它每天的起伏幅度大约是大盘的两倍。",
            advanced = "常指年化历史波动率 = 日收益标准差 × √250。另有期权隐含波动率（IV），反映市场对未来波动的预期，与历史波动率不同。",
        ),
        GlossaryEntry(
            key = "CORRELATION", term = "相关系数", ascii = "r", aliases = listOf("相关性", "相关度"),
            category = GlossaryCategory.TECHNICAL,
            plain = "两个标的涨跌步调有多一致，从 -1 到 +1。越接近 1 越同步，接近 0 各走各的。",
            example = "两只白酒股相关系数 0.9，同涨同跌；白酒股和银行股 0.3，一起拿才算分散。",
            advanced = "常用日收益率的 Pearson 相关系数。注意：相关性不稳定，行情剧变时会快速抬升——「平时不同步、危机时齐跌」是常见现象。",
        ),
        GlossaryEntry(
            key = "BETA", term = "贝塔", ascii = "β", aliases = listOf("贝塔系数", "Beta"),
            category = GlossaryCategory.TECHNICAL,
            plain = "个股跟着大盘涨跌的放大倍数。贝塔 1.5 = 大盘涨 1% 它平均涨 1.5%，跌时也放大。",
            example = "券商股贝塔普遍高，牛市里弹性大；公用事业股贝塔低，大盘怎么走它都波澜不惊。",
            advanced = "由个股对指数收益做回归得到，= 相关系数 × (个股波动率 / 指数波动率)。贝塔高不代表能赚钱，只代表对大盘的敏感度高。",
        ),
        GlossaryEntry(
            key = "ALPHA", term = "阿尔法", ascii = "α", aliases = listOf("阿尔法收益", "超额收益"),
            category = GlossaryCategory.TECHNICAL,
            plain = "扣掉大盘带来的部分后，你自己多赚（或多亏）的那部分。",
            example = "大盘涨 10%，你的组合涨 13%，多出的 3% 就近似你的阿尔法——正负才是本事，跟着大盘的部分不是。",
            advanced = "严格定义来自 CAPM 回归的截距项。散户场景下用「组合收益 − 贝塔 × 指数收益」估算即可，注意成本与运气成分。",
        ),
        GlossaryEntry(
            key = "SHARPE", term = "夏普比率", ascii = "Sharpe", aliases = listOf("夏普指数", "夏普值"),
            category = GlossaryCategory.TECHNICAL,
            plain = "每承受一份波动换来多少超额收益，是衡量「赚得稳不稳」的常用比例。",
            example = "两个组合都年赚 15%，一个睡得着觉（低波动）一个天天过山车，夏普比率会把前者算得更高。",
            advanced = "夏普 = (收益 − 无风险利率) / 收益标准差。比较时要在相似周期与标的类型间进行；短周期高夏普常是运气或杠杆的产物。",
        ),
        GlossaryEntry(
            key = "MAX_DRAWDOWN", term = "最大回撤", aliases = listOf("回撤"),
            category = GlossaryCategory.TECHNICAL,
            plain = "从阶段最高点跌到最低点的最大幅度，衡量最疼的时候有多疼。",
            example = "净值从 1.5 跌到 1.2，最大回撤 20%——问问自己能不能扛住，再决定要不要这样的组合。",
            advanced = "回撤 = (峰值 − 谷值) / 峰值。注意两点：回撤 50% 需要涨 100% 才能回本，亏损与回本不对称；它只描述历史最差一段，不预测未来最大跌幅。",
        ),
        GlossaryEntry(
            key = "HHI", term = "赫芬达尔指数", ascii = "HHI", aliases = listOf("HHI指数", "集中度指数"),
            category = GlossaryCategory.CAPITAL,
            plain = "把各部分占比平方后加总的集中度指标，数值越大越「把鸡蛋放一个篮子」。",
            example = "五只票各占 20%，HHI = 0.2；一只占 80% 其余各 5%，HHI 高出数倍——后者其实更集中。",
            advanced = "HHI = Σ(权重²)。等权 N 只时 HHI = 1/N。它对头部权重敏感：一只 60% 的票对 HHI 的贡献超过其余所有票之和。",
        ),
        GlossaryEntry(
            key = "LIQUIDITY", term = "流动性", aliases = listOf("流通性"),
            category = GlossaryCategory.CAPITAL,
            plain = "想买能买到、想卖能卖掉、且不明显推动价格的能力。",
            example = "日成交几亿的大盘股随时进出；日成交几十万的冷门票，一笔稍大的卖单就能砸出长下影。",
        ),
        GlossaryEntry(
            key = "RISK_PREMIUM", term = "风险溢价", aliases = listOf("股权风险溢价"),
            category = GlossaryCategory.VALUATION,
            plain = "承担风险比把钱存银行应该多赚的部分。市场给的不确定性补偿。",
            example = "无风险利率 2%、股市长期回报约 8%，中间约 6% 就是风险溢价——它是对承担波动的报酬，不是保证。",
        ),
        GlossaryEntry(
            key = "ST_STOCK", term = "ST股票", ascii = "ST", aliases = listOf("ST股", "戴帽"),
            category = GlossaryCategory.MECHANISM,
            plain = "因财务异常或其他风险被交易所特别处理的股票，名字前带 ST，涨跌幅限制也不同。",
            example = "*ST 前缀意味着退市风险警示，这类票可能直接退市，波动规则与普通股不同。",
        ),
        GlossaryEntry(
            key = "LEVERAGE", term = "杠杆", aliases = listOf("加杠杆", "杠杆率"),
            category = GlossaryCategory.MECHANISM,
            plain = "借钱投资。涨跌都会被同倍放大，亏起来可能超过本金。",
            example = "1 倍杠杆下股价跌 20% 本金亏 20%；两倍杠杆跌 20% 本金就亏 40%，还可能被强制平仓。",
        ),
    )

    private val byKeyIndex: Map<String, GlossaryEntry> by lazy { all.associateBy { it.key } }

    fun byKey(key: String): GlossaryEntry? = byKeyIndex[key]

    /**
     * 检索术语表：中文名、英文缩写、别名均可命中，且支持片段匹配。
     * FR-K6 拼音首字母检索：[pinyinInitials] 里的首字母串精确命中 = 高分，
     * 前缀命中 = 中分（"hsl" → 换手率，"syl" → 市盈率）。
     */
    fun search(query: String, limit: Int = 100): List<GlossaryEntry> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return all
        return all.mapNotNull { entry ->
            val haystack = entry.searchHaystack()
            val initials = pinyinInitials[entry.key].orEmpty().uppercase()
            val score = when {
                entry.term.uppercase() == q -> 100
                entry.ascii.equals(q, ignoreCase = true) -> 95
                entry.aliases.any { it.uppercase() == q } -> 90
                initials.isNotEmpty() && initials == q -> 88
                entry.term.uppercase().startsWith(q) -> 80
                entry.ascii.startsWith(q) -> 75
                initials.isNotEmpty() && initials.startsWith(q) -> 72
                haystack.contains(q) -> 60
                else -> return@mapNotNull null
            }
            score to entry
        }
            .sortedWith(compareByDescending<Pair<Int, GlossaryEntry>> { it.first }.thenBy { it.second.key })
            .map { it.second }
            .take(limit)
    }

    /**
     * FR-K9 相关术语推荐：同域（同分类）优先，依赖路径上的词（先懂/看懂后）
     * 永远排最前——推荐要服务依赖链，不只是「同类凑数」。
     */
    fun relatedOf(key: String, limit: Int = 3): List<GlossaryEntry> {
        val self = byKey(key) ?: return emptyList()
        val linked = (dependentsOf(key) + prerequisitesOf(key))
            .distinct()
            .mapNotNull { byKey(it) }
        val sameDomain = all.filter { it.category == self.category && it.key != key && linked.none { l -> l.key == it.key } }
        return (linked + sameDomain).take(limit)
    }

    /**
     * 拼音首字母索引（key → 首字母串），FR-K6 的数据源。
     * 手工维护：commonMain 无拼音库，词条量 80 且增速低，手工表成本最低且可审校。
     */
    private val pinyinInitials: Map<String, String> = mapOf(
        "MA" to "jx", "BOLL" to "bld", "GOLDEN_CROSS" to "jc", "DEATH_CROSS" to "sc",
        "DIVERGENCE" to "bl", "TURNOVER" to "hsl", "VOLUME_RATIO" to "lb", "QFQ" to "qfq",
        "HFQ" to "hfq", "SUPPORT" to "zcw", "RESISTANCE" to "ylw", "GAP" to "qk",
        "AMPLITUDE" to "zf", "PE" to "syl", "PB" to "sjl", "PS" to "sxsl",
        "EPS" to "mgsy", "ROE" to "jzcsyl", "DIVIDEND_YIELD" to "gxl", "MARKET_CAP" to "zsz",
        "FLOAT_MARKET_CAP" to "ltsz", "VALUATION_PERCENTILE" to "gzfw", "PRICE_LIMIT" to "zdt",
        "CALL_AUCTION" to "jhjj", "SUSPENSION" to "tp", "EX_RIGHTS" to "cqcx", "IPO_SUBSCRIPTION" to "dx",
        "BLOCK_TRADE" to "dzjy", "MARGIN" to "rzrq", "COMMISSION" to "yj", "STAMP_DUTY" to "yhs",
        "CHINEXT" to "cyb", "NORTHBOUND" to "bxzj", "MAIN_CAPITAL" to "zlzj", "DRAGON_TIGER" to "lhb",
        "NET_INFLOW" to "jlr", "SECTOR" to "bk", "LEADER_STOCK" to "ltg", "HOT_MONEY" to "yz",
        "CONCEPT_STOCK" to "gng", "SENTIMENT" to "qxm", "AMOUNT" to "cje", "REVENUE" to "yysr",
        "NET_PROFIT" to "jlr", "DEDUCTED_PROFIT" to "kfjlr", "GROSS_MARGIN" to "mll", "NET_MARGIN" to "jll",
        "YOY" to "tb", "MOM" to "hb", "EARNINGS_PREANNOUNCEMENT" to "yjyg", "FINANCIAL_REPORT" to "cb",
        "GOODWILL" to "sy", "CASH_FLOW" to "xjl", "DEBT_RATIO" to "zcfzl", "DIVIDEND" to "fh",
        "INSTITUTIONAL_RESEARCH" to "jgdy",
        // 风险与组合
        "POSITION" to "cw", "WEIGHT" to "qz", "DIVERSIFY" to "fstz", "REBALANCE" to "zph",
        "RESTRICTED_SHARE" to "xsg", "UNLOCK" to "jj", "SHARE_REDUCTION" to "jc",
        "STD_DEV" to "bzc", "VOLATILITY" to "bdl", "CORRELATION" to "xgxs", "BETA" to "bt",
        "ALPHA" to "aef", "SHARPE" to "xpbl", "MAX_DRAWDOWN" to "zdhc", "HHI" to "hfdezs",
        "LIQUIDITY" to "ldx", "RISK_PREMIUM" to "fxyj", "ST_STOCK" to "st", "LEVERAGE" to "gg",
    )

    fun byCategory(category: GlossaryCategory): List<GlossaryEntry> = all.filter { it.category == category }

    /**
     * 依赖路径（doc 24 §6.3 改动 #4）：学某个概念前应先懂的前置概念。
     * 有向关系第一次被画出来——「先懂 / 看懂后」两条路径都由这张表驱动。
     * 只收录确有依赖关系的词，无依赖 = 空表 = 可直接学。
     */
    val prerequisites: Map<String, List<String>> = mapOf(
        "GOLDEN_CROSS" to listOf("MA", "MACD"),
        "DEATH_CROSS" to listOf("MA", "MACD"),
        "DIVERGENCE" to listOf("MACD"),
        "BOLL" to listOf("MA"),
        "SUPPORT" to listOf("MA"),
        "RESISTANCE" to listOf("MA"),
        "PEG" to listOf("PE"),
        "EPS" to listOf("NET_PROFIT"),
        "VALUATION_PERCENTILE" to listOf("PE", "PB"),
        "FLOAT_MARKET_CAP" to listOf("MARKET_CAP"),
        "DIVIDEND_YIELD" to listOf("DIVIDEND", "MARKET_CAP"),
        "VOLUME_RATIO" to listOf("TURNOVER"),
        "NET_INFLOW" to listOf("MAIN_CAPITAL"),
        "DEDUCTED_PROFIT" to listOf("NET_PROFIT"),
        "GROSS_MARGIN" to listOf("REVENUE"),
        "NET_MARGIN" to listOf("REVENUE", "NET_PROFIT"),
        "YOY" to listOf("FINANCIAL_REPORT"),
        "MOM" to listOf("YOY"),
    )

    /** 反向依赖：「看懂后」能解锁哪些概念。 */
    private val dependentsIndex: Map<String, List<String>> by lazy {
        val index = mutableMapOf<String, MutableList<String>>()
        prerequisites.forEach { (key, pres) ->
            pres.forEach { pre -> index.getOrPut(pre) { mutableListOf() }.add(key) }
        }
        index
    }

    fun prerequisitesOf(key: String): List<String> = prerequisites[key].orEmpty()

    fun dependentsOf(key: String): List<String> = dependentsIndex[key].orEmpty()

    private val keyByTokenIndex: Map<String, String> by lazy {
        val index = mutableMapOf<String, String>()
        matchTokens().forEach { (token, key) ->
            val upper = token.uppercase()
            if (!index.containsKey(upper)) index[upper] = key
        }
        index
    }

    /** 把用户实际遇到的词形（正名/缩写/别名）还原成稳定 key；聊天侧「遇到」埋点用。 */
    fun keyForToken(token: String): String? = keyByTokenIndex[token.trim().uppercase()]

    /** 供 EntityRecognizer 构建 Trie：词形 → 稳定 key。 */
    fun matchTokens(): List<Pair<String, String>> =
        all.flatMap { entry -> entry.matchTokens().map { token -> token to entry.key } }
}
