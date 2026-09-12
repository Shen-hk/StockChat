package com.kuikly.stockchat.app.assembly

import com.kuikly.stockchat.app.platform.KuiklyKeyValueStorage
import com.kuikly.stockchat.app.platform.KuiklyPlatformScheduler
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.chat.ChatSessionStore
import com.kuikly.stockchat.data.AlertStore
import com.kuikly.stockchat.data.GlossaryStore
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.provider.OpenAiCompatAiProvider
import com.kuikly.stockchat.data.provider.PlatformScheduler
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.provider.SecuritySearchProvider
import com.kuikly.stockchat.data.storage.KeyValueStorage

/**
 * Chat Feature 的页面级最小依赖集（doc 47 §4(5)）。
 * 消费方：B-2 的 Chat 组件归档与后续 P1-Chat 收口。
 */
class ChatFeatureDependencies(
    val configStore: AiConfigStore,
    val sessionStore: ChatSessionStore,
    val watchlistStore: WatchlistStore,
    val alertStore: AlertStore,
    val glossaryStore: GlossaryStore,
    val quoteRepository: QuoteRepository,
    val securitySearchProvider: SecuritySearchProvider,
    val platformScheduler: PlatformScheduler,
)

/**
 * Chat Feature 的装配根。
 *
 * 2026-09-12（doc 47 B-1）：`ChatDependencies.forPager` 工厂从 `chat/` 搬到这里，
 * Feature 层不再自行获取 Pager —— `ChatDependencies` 本身仍是 chat 层的数据载体，
 * 只由本对象实例化。
 */
object ChatFeatureGraph {
    fun forPager(
        pagerId: String,
        storage: KeyValueStorage = KuiklyKeyValueStorage(pagerId),
    ): ChatDependencies {
        val market = MarketFeatureGraph.forPager(pagerId, storage)
        val scheduler = KuiklyPlatformScheduler(pagerId)
        return ChatDependencies(
            configStore = AiConfigStore(storage),
            sessionStore = ChatSessionStore(storage),
            watchlistStore = market.watchlistStore,
            alertStore = market.alertStore,
            glossaryStore = market.glossaryStore,
            quoteRepository = market.quoteRepository,
            securitySearchProvider = market.securitySearchProvider,
            platformScheduler = scheduler,
            aiProviderFactory = { config -> OpenAiCompatAiProvider(config, scheduler) },
        )
    }

    /**
     * 仅 ApiConfigPage 需要的极小依赖：它只读写 AI 配置，不该为了一个
     * `AiConfigStore` 建整张 Chat 图（doc 47 硬指标：Page 不得自行获取 Pager）。
     */
    fun aiConfigStore(
        pagerId: String,
        storage: KeyValueStorage = KuiklyKeyValueStorage(pagerId),
    ): AiConfigStore = AiConfigStore(storage)

    /**
     * API 设置页「测试连接」用的一次性 Provider（暴露 `testConnection`，故返回具体类型）。
     * 由装配根产出，页面不再自行 new Provider。
     */
    fun connectionTestProvider(pagerId: String, config: AiConfig): OpenAiCompatAiProvider =
        OpenAiCompatAiProvider(config, KuiklyPlatformScheduler(pagerId))

    /** Chat Feature 最小依赖集入口（doc 47 §4(5)）。 */
    fun feature(
        pagerId: String,
        storage: KeyValueStorage = KuiklyKeyValueStorage(pagerId),
    ): ChatFeatureDependencies {
        val market = MarketFeatureGraph.forPager(pagerId, storage)
        return ChatFeatureDependencies(
            configStore = AiConfigStore(storage),
            sessionStore = ChatSessionStore(storage),
            watchlistStore = market.watchlistStore,
            alertStore = market.alertStore,
            glossaryStore = market.glossaryStore,
            quoteRepository = market.quoteRepository,
            securitySearchProvider = market.securitySearchProvider,
            platformScheduler = KuiklyPlatformScheduler(pagerId),
        )
    }
}
