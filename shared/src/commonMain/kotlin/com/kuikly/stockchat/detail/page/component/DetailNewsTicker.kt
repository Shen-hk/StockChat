package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.page.components.NewsMarquee
import com.kuikly.stockchat.page.components.NewsSummaryBar
import com.kuikly.stockchat.detail.domain.scoreNewsSentiment
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.View

/**
 * Wave 2 D5 第二组件：新闻弹幕 + 摘要条（D5 视觉边界）。从 StockDetailPage.body()
 * 搬出原 lines 484-524（NewsMarquee + 摘要条 vif），保持「零行为变更」：仅物理搬迁，
 * 渲染口径、反应式依赖、点按/长按交互、回链与理由 chips 共用 overlay 仲裁。
 *
 * 长按先览（tapePreview）属于就地浮层（DetailOverlay.TAPE_PREVIEW），仍由本页
 * body() 内的 detailOverlayCoordinator 仲裁，此处仅持有数据 + 交互回链，不引入
 * 新的状态机或 Provider。
 *
 * Props：观察/取值闭包（observable 通过闭包读取，attr 内建立反应式依赖）。
 * Actions：回链到 StockDetailPage 的 onTapItem / showTapePreview / scheduleTapePreviewDismiss
 * / askAboutNews / openUrl，不持有生命周期或请求。
 */
internal fun ViewContainer<*, *>.DetailNewsTicker(
    theme: StockChatTheme,
    reduceMotion: Boolean,
    newsList: () -> List<NewsItem>,
    tapeOffset: () -> Float,
    tapeLoopWidth: () -> Float,
    newsSummary: () -> NewsItem?,
    onTapItem: (NewsItem) -> Unit,
    onLongPressItem: (NewsItem, Float, Float) -> Unit,
    onLongPressRelease: () -> Unit,
    onAskAi: (NewsItem) -> Unit,
    onOpenUrl: (NewsItem) -> Unit,
    isNewsFlagged: (NewsItem) -> Boolean,
) {
    // ---- 新闻弹幕 v2（对齐市场页）：无背板持续流动、屏幕边缘流出。
    // 交互口径不变：点按 = 落旗 + 展开摘要条（摘要展开即暂停流动，
    // 收起恢复）；长按 = 先览气泡（先览期间同样暂停）。
    NewsMarquee(
        theme = theme,
        items = newsList,
        // B1 情绪点 + 摘要头「利好/利空」：端侧词典打分，涨红跌绿（U2）
        sentimentOf = { item -> scoreNewsSentiment(item.title).isPositive },
        offset = tapeOffset,
        loopWidth = tapeLoopWidth,
        onTapItem = onTapItem,
        selected = newsSummary,
        // B1 长按先览（U5 400ms）：TAPE_PREVIEW 层，松手 700ms 后消失（5s 兜底）；
        // pageX/pageY = 触摸点在根 Page 坐标系，气泡锚定所按条目正下方
        onLongPressItem = onLongPressItem,
        onLongPressRelease = onLongPressRelease,
    )

    // B2 摘要条（原卡内展开改为弹幕下独立圆角块；交互不变）
    vif({ newsSummary() != null }) {
        vbind({ newsSummary()?.id ?: "" }) {
            val news = newsSummary()
            if (news != null) {
                View {
                    attr { marginTop(8f); borderRadius(12f) }
                    NewsSummaryBar(
                        theme = theme,
                        news = news,
                        sentiment = scoreNewsSentiment(news.title).isPositive,
                        flagged = isNewsFlagged(news),
                        onToggle = { onTapItem(news) },
                        onAskAi = onAskAi,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        }
    }
}