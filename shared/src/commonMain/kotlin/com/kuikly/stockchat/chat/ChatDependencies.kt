package com.kuikly.stockchat.chat

import com.kuikly.stockchat.data.AlertStore
import com.kuikly.stockchat.data.GlossaryStore
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.provider.PlatformScheduler
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.provider.SecuritySearchProvider

/**
 * Page-scoped dependency graph for the chat feature.
 *
 * 2026-09-12（doc 47 B-1）：本类只保留**数据载体**角色，`forPager` 工厂已搬到
 * `app/assembly/ChatFeatureGraph.kt` —— Feature 层不再自行获取 Pager
 * （硬指标：`fun forPager` 只允许出现在 app/assembly）。
 */
class ChatDependencies(
    val configStore: AiConfigStore,
    val sessionStore: ChatSessionStore,
    val watchlistStore: WatchlistStore,
    val alertStore: AlertStore,
    val glossaryStore: GlossaryStore,
    val quoteRepository: QuoteRepository,
    val securitySearchProvider: SecuritySearchProvider,
    /** 投递回调到 pager context queue 的端口（Mock/在线 Provider 共用）。 */
    val platformScheduler: PlatformScheduler,
    val aiProviderFactory: (AiConfig) -> AiProvider,
)
