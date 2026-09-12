package com.kuikly.stockchat.detail.ai.state

import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.data.config.AiConfig

/**
 * [DetailAiInsightCoordinator] 对页面数据侧的最小依赖面，供页面适配、测试可 fake。
 * AI 配置/Provider 工厂与 paging key 一次性传入；行情/资金/分时序列等只读查询按
 * 调用时刻读取（页面实现走 D1 detailDataState 的 getter）。
 */
internal interface DetailAiHostPort {
    val pagerId: String
    fun quote(): Quote
    fun insight(): StockInsightBundle
    fun newsList(): List<NewsItem>
    fun timelineSeries(): List<Double>
    fun loadConfig(): AiConfig
    fun configValidationError(config: AiConfig): String?
    fun createProvider(config: AiConfig): AiProvider
    /** 把代码块调度回 Kuikly 核心线程（原 StockDetailPage 用 setTimeout(0) 跳转）。 */
    fun jumpToMain(block: () -> Unit)
}

/**
 * 流式输出"打字机/逐字释放"的角色抽象，参考 chat/compare 的
 * [com.kuikly.stockchat.chat.compare.state.CompareTextRevealer] 同构
 * 抽象（docs/43 D3：页面用 Pager 化 TypewriterSmoother 实现、单测用
 * ImmediateRevealer 实现，避免依赖全局 setTimeout）。
 */
internal interface AiInsightRevealer {
    fun append(delta: String)
    fun complete(onComplete: () -> Unit)
    fun cancel()
    /** 停止生成时把已收到的内容立即全部释放到回调（用户点"停止"对齐聊天页 stop 范式）。 */
    fun flushNow()
}

internal fun interface AiInsightRevealerFactory {
    fun create(onRevealed: (String) -> Unit): AiInsightRevealer
}

/** Detail 页 AI 解读域下游副作用占位（docs/43 D3：本域几乎不需要向外发效果）。 */
internal sealed interface DetailAiEffect