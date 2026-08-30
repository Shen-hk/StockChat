package com.kuikly.stockchat.chat

/**
 * 「我的自选今天怎么样」类问句的识别（12 号需求文档 FR-W4）。
 *
 * 股问原本是「无记忆的单次问答」：没有自选数据时，这类最自然的问题无法回答。
 * 命中后由 ChatViewModel 注入自选摘要作为 system 注记，并在回答尾部补齐各自选的
 * 行情卡——卡片数字仍由端侧按 symbol 填充真实行情，意图与数据分离的原则不变。
 */
object WatchlistIntent {
    private val OWNERSHIP = listOf(
        "我的自选", "自选股", "自选", "我的持仓", "我持有的", "我的票", "我的股票", "关注的股票",
    )
    private val INQUIRY = listOf(
        "怎么样", "怎样", "如何", "今天", "最近", "情况", "表现", "涨跌", "涨了", "跌了",
        "复盘", "走势", "还好吗", "好不好", "吗",
    )

    /** 必须同时出现「所属」与「询问」成分，避免「打开自选股」这类指令被误判为问句。 */
    fun matches(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        return OWNERSHIP.any { normalized.contains(it) } && INQUIRY.any { normalized.contains(it) }
    }
}
