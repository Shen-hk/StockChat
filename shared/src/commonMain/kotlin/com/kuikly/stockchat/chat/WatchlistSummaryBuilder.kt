package com.kuikly.stockchat.chat

import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.WatchlistItem
import com.kuikly.stockchat.data.provider.Quote

/** Builds the deterministic local answer for watchlist questions. */
object WatchlistSummaryBuilder {

    fun build(
        items: List<WatchlistItem>,
        quoteFor: (String) -> Quote?,
    ): String {
        if (items.isEmpty()) {
            return "你还没有添加自选股。可以在抽屉进入「自选股」搜索添加，也可以在聊天里长按股票名或从详情页加入。"
        }

        val quotes = items.mapNotNull { quoteFor(it.symbol) }
        val rising = quotes.count { it.change > 0.0 }
        val falling = quotes.count { it.change < 0.0 }
        val flat = quotes.size - rising - falling
        val lead = when {
            rising > falling -> "你的自选今天整体偏强，${rising} 只上涨、${falling} 只下跌。"
            falling > rising -> "你的自选今天整体偏弱，${falling} 只下跌、${rising} 只上涨。"
            else -> "你的自选今天分化不大，上涨和下跌家数接近。"
        }
        val detail = if (quotes.isEmpty()) {
            "行情暂不可用，下面先列出自选标的，卡片会继续按统一行情链路加载。"
        } else {
            val leaders = quotes.sortedByDescending { it.changePercent }.take(3)
                .joinToString("、") { "${it.name} ${Format.percent(it.changePercent)}" }
            "当前统计：涨 $rising / 跌 $falling / 平 $flat。表现靠前：$leaders。"
        }
        val cards = items.joinToString("\n\n") { item ->
            "```card:stock-quote\n{\"symbol\":\"${item.symbol}\"}\n```"
        }
        return "$lead\n\n$detail\n\n$cards"
    }
}
