package com.kuikly.stockchat.detail.domain

import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 抓取上下文芯片仓库（doc 29 §3 ContextChipStore · ③抓取即上下文）。
 *
 * 抓取指标的「唯一状态源」：输入栏 chips 与「问 AI」请求注入共用（U3 统一来源标注）。
 * chips 用页面同款 observable 委托（与 StockDetailPage 的 `by observable(...)` 同来源），
 * 仅在 attr/event/vif/vfor/vbind 闭包内读才会建立响应式依赖（AGENTS.md R1）。
 */
data class ContextChip(val key: String, val label: String, val value: String)

class ContextChipStore {
    /** 当前已抓取的上下文芯片列表（响应式）。 */
    var chips: List<ContextChip> by observable(emptyList())

    /**
     * 按 key 去重添加；已存在返回 false（不覆盖）。
     * 对应 doc 29 ③「重复抓取去重」验收点。
     */
    fun add(chip: ContextChip): Boolean {
        if (chips.any { it.key == chip.key }) return false
        chips = chips + chip
        return true
    }

    /** 按 key 移除一枚芯片。 */
    fun remove(key: String) {
        chips = chips.filter { it.key != key }
    }

    /** 清空全部芯片。 */
    fun clear() {
        chips = emptyList()
    }
}

/**
 * 将当前 chips 拼为「问 AI」请求的上下文片段（非 observable 纯函数）。
 * 形态：结合换手率0.52%和主力净流出3.2亿， —— 无 chip 返回空串。
 */
fun ContextChipStore.promptFragment(): String {
    if (chips.isEmpty()) return ""
    val parts = chips.map { it.label + it.value }
    return "结合" + parts.joinToString("和") + "，"
}
