package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.View

/**
 * 分段状态条（doc 24 §7）：高 6px、圆角 3、段间 1px 留白，段色按明度阶梯编码。
 *
 * 知识库五域的「未遇到 / 见过 / 常见 / 已读」是部分-整体关系（LDRS-R），
 * 文案列得再全也不如一条比例条直观。零新增饱和色：四段全部取自主题既有
 * 明度阶梯（surfaceMuted → glossarySeen → brandSoft → brand）。
 */
fun ViewContainer<*, *>.SegmentBar(
    theme: StockChatTheme,
    unseen: Int,
    seen: Int,
    common: Int,
    known: Int,
) {
    val total = unseen + seen + common + known
    if (total <= 0) return
    View {
        attr { height(6f); flexDirectionRow(); borderRadius(3f); overflow(true) }
        segment(theme.surfaceMuted, unseen, total)
        segment(theme.glossarySeen, seen, total)
        segment(theme.brandSoft, common, total)
        segment(theme.brand, known, total)
    }
}

private fun ViewContainer<*, *>.segment(color: com.tencent.kuikly.core.base.Color, count: Int, total: Int) {
    if (count <= 0) return
    View {
        attr {
            flex(count.toFloat() / total)
            height(6f)
            backgroundColor(color)
            marginRight(1f)
        }
    }
}
