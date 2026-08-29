package com.kuikly.stockchat.composer

/**
 * @ 候选数据源与排序（规范 §4.2）。
 *
 * 五级数据源按优先级合并去重；空 query 给推荐序列（最近提及 + 自选 + 指数）；
 * 非空 query 按命中等级 + 热度 + 最近提及 + 自选打分，稳定排序防列表跳动。
 */
data class AtCandidate(
    val entry: CatalogEntry,
    /** 命中等级 0..1（见规范 §4.2 权重表） */
    val level: Float,
    val score: Float,
    /** 数据来源标签：最近 / 自选 / 指数 / 板块 / 搜索 */
    val source: String,
) {
    val symbol get() = entry.symbol
    val name get() = entry.name
}

object AtCandidateProvider {

    const val MAX_ROWS = 12

    /**
     * 候选排序主入口。
     * @param query 触发词（上屏文本；组合态期间由调用方冻结）
     * @param recentMentions 最近提及过的标的 symbol（S1，最多 5 条，新的在前）
     */
    fun rank(query: String, recentMentions: List<String> = emptyList()): List<AtCandidate> {
        if (query.isEmpty()) return recommend(recentMentions)
        val ql = query.lowercase()
        val hits = mutableListOf<AtCandidate>()

        for (entry in ComposerCatalog.all) {
            val level = matchLevel(ql, entry)
            if (level <= 0f) continue
            val isRecent = entry.symbol in recentMentions
            val score = 60f * level +
                15f * entry.hot +
                10f * (if (isRecent) 1f else 0f) +
                10f * (if (entry.watchlist) 1f else 0f)
            hits += AtCandidate(entry, level, score, sourceOf(entry, isRecent))
        }

        // 概念/板块聚合项（命中等级 0.60）
        for (board in ComposerCatalog.boards) {
            val bl = if (board.name.contains(query)) 0.60f
            else if (board.pinyinFull.startsWith(ql) || board.pinyinAbbr.startsWith(ql)) 0.55f
            else 0f
            if (bl <= 0f) continue
            hits += AtCandidate(board, bl, 60f * bl + 15f * board.hot, "板块")
        }

        // 同分数稳定性：score 降序 + symbol 字典序兜底
        return hits.sortedWith(
            compareByDescending<AtCandidate> { it.score }.thenBy { it.symbol }
        ).take(MAX_ROWS)
    }

    /** 空 query 推荐序列：S1 最近提及 + S2 自选 + S3 指数，去重截断。 */
    private fun recommend(recentMentions: List<String>): List<AtCandidate> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<AtCandidate>()
        recentMentions.take(5).forEach { symbol ->
            ComposerCatalog.find(symbol)?.let { entry ->
                if (seen.add(entry.symbol)) out += AtCandidate(entry, 0f, 0f, "最近")
            }
        }
        ComposerCatalog.stocks.filter { it.watchlist }.forEach { entry ->
            if (seen.add(entry.symbol)) out += AtCandidate(entry, 0f, 0f, "自选")
        }
        ComposerCatalog.indices.forEach { entry ->
            if (seen.add(entry.symbol)) out += AtCandidate(entry, 0f, 0f, "指数")
        }
        return out.take(MAX_ROWS)
    }

    private fun sourceOf(entry: CatalogEntry, isRecent: Boolean): String = when {
        isRecent -> "最近"
        entry.watchlist -> "自选"
        entry.kind == MentionType.INDEX -> "指数"
        else -> "搜索"
    }

    private fun matchLevel(ql: String, entry: CatalogEntry): Float = when {
        entry.symbol.lowercase() == ql -> 1.00f
        entry.name.startsWith(ql.removePrefix("@")) -> 0.95f
        entry.name.contains(ql.removePrefix("@")) -> 0.90f
        entry.pinyinFull.startsWith(ql) -> 0.80f
        entry.pinyinAbbr.startsWith(ql) -> 0.70f
        else -> {
            // 别名/曾用名
            val target = ComposerCatalog.aliasTarget(ql)
            if (target?.symbol == entry.symbol) 0.40f else 0f
        }
    }
}
