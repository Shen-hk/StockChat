package com.kuikly.stockchat.chat

/**
 * 选股思路画像（doc 32 §6.3，2026-09-10 用户定案）：
 * 把用户聊天里的提问按「标准选股思路」六环做端侧关键词归档，找出缺失环节。
 * 纯规则零 LLM（与 FR「暴露指标不调 LLM」同纪律）；关键词命中即算覆盖，宁松勿严。
 *
 * 用途：风险地图「导图」视图——实心环 = 用户在聊天里聊过（附次数与例子），
 * 灰环 = 缺失环节，点缺失环直接跳对话页用预置问句补上这一环。
 */
object ChatThinkingProfile {

    /** 标准选股思路的六个环节（顺序即链路：先选标的 → 层层检验 → 风险收口）。 */
    data class Stage(
        val key: String,
        val title: String,
        /** 这一环回答什么问题。 */
        val hint: String,
        val keywords: List<String>,
        /** 缺这一环时的一键补课问句（自然语态，跳 openChatWithQuestion）。 */
        val askPrompt: String,
    )

    val STAGES: List<Stage> = listOf(
        Stage(
            key = "target",
            title = "明确标的",
            hint = "先圈定要看哪只、为什么是它",
            keywords = listOf("自选", "持仓", "买什么", "选股", "600519", "00700", "茅台", "腾讯", "宁德", "中际", "新易盛", "这只", "哪只"),
            askPrompt = "帮我按「先选标的」的思路过一遍：我的自选里最值得先深入研究的是哪只，为什么？",
        ),
        Stage(
            key = "industry",
            title = "行业与赛道",
            hint = "它所在的链景气不景气、上下游谁受益",
            keywords = listOf("行业", "赛道", "板块", "产业链", "上游", "下游", "景气", "产能", "周期"),
            askPrompt = "帮我从行业与赛道角度看我的自选：所在链条景气度如何，上下游谁受益？",
        ),
        Stage(
            key = "fundamental",
            title = "基本面",
            hint = "财报、营收利润、估值贵不贵",
            keywords = listOf("基本面", "财报", "业绩", "营收", "利润", "净利", "现金流", "毛利率", "估值", "市盈率", "市净率", "roe", "股息"),
            askPrompt = "帮我从基本面看我的自选：最新财报的营收、利润和估值处在什么水平？",
        ),
        Stage(
            key = "technique",
            title = "走势与技术",
            hint = "K线、均线、量能在说什么",
            keywords = listOf("k线", "k 线", "均线", "支撑", "压力", "趋势", "成交量", "放量", "缩量", "突破", "回踩", "技术面", "分时"),
            askPrompt = "帮我从走势与技术面看我的自选：趋势、量能和关键位置现在怎么说？",
        ),
        Stage(
            key = "event",
            title = "事件与催化",
            hint = "接下来有什么事会发生、影响什么",
            keywords = listOf("事件", "催化", "公告", "政策", "解禁", "财报日", "业绩会", "回购", "分红", "日历", "印花税", "会议"),
            askPrompt = "我的自选未来 30 天有哪些已预约事件（财报/解禁/公告），分别可能怎么影响？",
        ),
        Stage(
            key = "risk",
            title = "风险与仓位",
            hint = "错了会怎样、是不是押得太重",
            keywords = listOf("风险", "回撤", "波动", "仓位", "止损", "分散", "对冲", "集中", "杠杆", "押", "重仓"),
            askPrompt = "帮我审视我的自选组合：最大的集中风险在哪里，怎样算「押得太重」？（仅分析风险结构）",
        ),
    )

    /** 单环归档结果：[count] 为用户消息命中次数（0 = 缺失），[example] 为首条命中例句。 */
    data class StageHit(
        val stage: Stage,
        val count: Int,
        val example: String,
    ) {
        val covered: Boolean get() = count > 0
    }

    /** 对用户消息（仅 USER 角色）做六环归档；顺序与 [STAGES] 一致。 */
    fun analyze(userMessages: List<String>): List<StageHit> = STAGES.map { stage ->
        var count = 0
        var example = ""
        userMessages.forEach { message ->
            val content = message.lowercase()
            if (stage.keywords.any { content.contains(it) }) {
                count++
                if (example.isEmpty()) example = message.cleanExample()
            }
        }
        StageHit(stage, count, example)
    }

    /** 覆盖环数（0..6），导图汇总行用。 */
    fun coveredCount(hits: List<StageHit>): Int = hits.count { it.covered }

    /** 缺失环标题串（如「基本面、风险与仓位」；全覆盖返回空串）。 */
    fun missingTitles(hits: List<StageHit>): String =
        hits.filterNot { it.covered }.joinToString("、") { it.stage.title }

    private fun String.cleanExample(): String =
        replace(Regex("```card:[\\s\\S]*?```"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { if (it.length > 24) "${it.take(24)}…" else it }
}
