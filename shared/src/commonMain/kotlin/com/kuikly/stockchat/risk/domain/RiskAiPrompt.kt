package com.kuikly.stockchat.risk.domain

/**
 * 卡底 AI 详细解读的 prompt 组装与输出清洗（doc 47 B-3 第 4 步随迁）：
 * **文案逐字保留自原 RiskMapPage.buildSkyAiPrompt / sanitizeSkyAiText**，
 * 禁止改 prompt（硬边界：不改 AI Prompt）。
 * 事实槽位全部端侧计算（[buildRiskAiFacts]），模型只解读不编数字。
 */

/** 端侧事实槽位（唯一事实来源）：模型只负责解读关系，禁止编数字。 */
fun buildSkyAiPromptText(facts: RiskAiFacts): String {
    val factLines = facts.toFactLines()
    return buildString {
        appendLine("你是 A 股组合风险解读助手。请基于下面的真实数据，用 4-6 句简体中文解读用户自选组合当前的风险结构。")
        appendLine()
        appendLine("硬性要求：")
        appendLine("1. 只陈述与解释以上数据体现的事实与关系，不预测后续涨跌，不给出买卖、仓位建议。")
        appendLine("2. 直接输出句子，每句以句号结尾；不要小标题、序号、加粗、markdown 或任何卡片协议。")
        appendLine()
        appendLine("组合数据（唯一事实来源，禁止编造未提供的数字）：")
        factLines.forEach { appendLine("- $it") }
    }
}

/** 清洗模型输出：剥离 markdown fence 行 + 首尾空白（原 sanitizeSkyAiText）。 */
fun sanitizeRiskAiText(raw: String): String = raw
    .lines()
    .filterNot { it.trimStart().startsWith("```") }
    .joinToString("\n")
    .trim()
