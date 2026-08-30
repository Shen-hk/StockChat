package com.kuikly.stockchat

import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.GlossaryCategory
import com.kuikly.stockchat.richtext.EntityRecognizer
import com.kuikly.stockchat.richtext.EntityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 术语表验收（12 号需求文档 FR-G1 / FR-G2）：
 * 词典规模 ≥ 50 词、五分类覆盖、识别命中率 ≥ 95%、干扰词不误标。
 * 语料是验收口径，新增词条时应同步补充，避免覆盖率口径漂移。
 */
class GlossaryTest {

    @Test
    fun glossaryReachesProjectedSize() {
        assertTrue(Glossary.all.size >= 50, "词典应至少 50 词，实际 ${Glossary.all.size}")
        val categories = Glossary.all.map { it.category }.toSet()
        assertEquals(GlossaryCategory.values().size, categories.size, "五个分类都应被覆盖")
    }

    @Test
    fun everyEntryIsExplainableInPlainLanguage() {
        Glossary.all.forEach { entry ->
            assertTrue(entry.plain.isNotBlank(), "${entry.key} 缺人话解释")
            assertTrue(entry.plain.length <= 60, "${entry.key} 人话解释超过 60 字：${entry.plain.length}")
            assertTrue(entry.example.isNotBlank(), "${entry.key} 缺例子")
        }
        Glossary.byCategory(GlossaryCategory.TECHNICAL).forEach { entry ->
            assertTrue(entry.advanced.isNotBlank(), "技术指标类 ${entry.key} 必须提供进阶解释")
        }
    }

    @Test
    fun entryKeysAreUnique() {
        val keys = Glossary.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "术语 key 必须唯一")
    }

    @Test
    fun recognitionCoverageMeetsBar() {
        val missed = CORPUS.filter { (text, key) ->
            EntityRecognizer.recognize(text).none { it.type == EntityType.TERM && it.target == key }
        }
        val rate = (CORPUS.size - missed.size).toFloat() / CORPUS.size
        assertTrue(
            rate >= 0.95f,
            "术语识别命中率 ${(rate * 100).toInt()}% 低于 95%，漏识别：$missed",
        )
    }

    @Test
    fun distractorsAreNotMarked() {
        // 与股票同名的词优先解释为股票，不误标为术语
        val pingAn = EntityRecognizer.recognize("平安今天怎么样")
        assertEquals(EntityType.STOCK, pingAn.single().type)

        // 普通英文单词不得被截出 PE / MA 这类缩写
        assertTrue(EntityRecognizer.recognize("The market people watched closely").isEmpty())
        assertTrue(EntityRecognizer.recognize("People in the market").isEmpty())

        // 日常用词不得因别名被误标
        assertTrue(EntityRecognizer.recognize("公司经营压力有所缓解").isEmpty())
    }

    @Test
    fun searchSupportsChineseAndAscii() {
        assertEquals("PE", Glossary.search("市盈率").first().key)
        assertEquals("PE", Glossary.search("PE").first().key)
        assertEquals("TURNOVER", Glossary.search("换手").first().key)
        assertEquals("DEBT_RATIO", Glossary.search("负债率").first().key)
        assertTrue(Glossary.search("不存在的词").isEmpty())
    }

    @Test
    fun asciiTermsIgnoreLetterCase() {
        val spans = EntityRecognizer.recognize("看看macd和pe")
        assertEquals(listOf("macd", "pe"), spans.map { it.text })
        assertEquals(listOf("MACD", "PE"), spans.map { it.target })
    }

    companion object {
        /** 固定测试语料：(句子, 期望命中术语 key)。 */
        private val CORPUS: List<Pair<String, String>> = listOf(
            "MACD 出现金叉了吗" to "MACD",
            "KDJ 已经进入超买区" to "KDJ",
            "这只票的 RSI 超过 80 了" to "RSI",
            "股价跌破 60 日均线" to "MA",
            "布林带开口在扩大" to "BOLL",
            "出现金叉是不是可以买" to "GOLDEN_CROSS",
            "高位出现死叉要小心" to "DEATH_CROSS",
            "这个位置有底背离" to "DIVERGENCE",
            "今天的换手率很高" to "TURNOVER",
            "开盘量比超过 3" to "VOLUME_RATIO",
            "K 线要用前复权看" to "QFQ",
            "后复权涨幅更惊人" to "HFQ",
            "60 日线是重要的支撑位" to "SUPPORT",
            "上方压力位在哪" to "RESISTANCE",
            "今天留了一个跳空缺口" to "GAP",
            "当天振幅超过 10%" to "AMPLITUDE",
            "它的市盈率偏高" to "PE",
            "市净率只有 0.8 倍" to "PB",
            "亏损公司看市销率" to "PS",
            "PEG 小于 1 说明什么" to "PEG",
            "每股收益增长很快" to "EPS",
            "净资产收益率连续多年超过 20%" to "ROE",
            "股息率有 5%" to "DIVIDEND_YIELD",
            "这家公司总市值不大" to "MARKET_CAP",
            "流通市值只有 30 亿" to "FLOAT_MARKET_CAP",
            "当前估值分位很低" to "VALUATION_PERCENTILE",
            "A 股实行 T+1 制度" to "T1",
            "今天直接封涨停" to "PRICE_LIMIT",
            "集合竞价高开两个点" to "CALL_AUCTION",
            "这只股票停牌了" to "SUSPENSION",
            "除权除息后价格下调" to "EX_RIGHTS",
            "这周有新股打新" to "IPO_SUBSCRIPTION",
            "盘后出现大宗交易" to "BLOCK_TRADE",
            "两融余额在上升" to "MARGIN",
            "券商佣金是万三" to "COMMISSION",
            "卖出要交印花税" to "STAMP_DUTY",
            "创业板涨跌幅是 20%" to "CHINEXT",
            "北向资金今天净买入" to "NORTHBOUND",
            "主力资金在流出" to "MAIN_CAPITAL",
            "上榜龙虎榜了吗" to "DRAGON_TIGER",
            "板块资金净流入居前" to "NET_INFLOW",
            "白酒板块整体走强" to "SECTOR",
            "它是这波的龙头股" to "LEADER_STOCK",
            "游资在炒作这只票" to "HOT_MONEY",
            "这只概念股没有业绩" to "CONCEPT_STOCK",
            "当前情绪面偏冷" to "SENTIMENT",
            "两市成交额放大" to "AMOUNT",
            "营业收入同比增长" to "REVENUE",
            "净利润大幅下滑" to "NET_PROFIT",
            "扣非净利润其实是亏的" to "DEDUCTED_PROFIT",
            "毛利率维持在 90%" to "GROSS_MARGIN",
            "净利率只有 3%" to "NET_MARGIN",
            "同比增速在放缓" to "YOY",
            "环比已经转正" to "MOM",
            "业绩预告超预期" to "EARNINGS_PREANNOUNCEMENT",
            "年报披露后怎么走" to "FINANCIAL_REPORT",
            "商誉减值风险大" to "GOODWILL",
            "经营性现金流为负" to "CASH_FLOW",
            "资产负债率接近 80%" to "DEBT_RATIO",
            "今年分红方案不错" to "DIVIDEND",
            "机构调研很密集" to "INSTITUTIONAL_RESEARCH",
        )
    }
}
