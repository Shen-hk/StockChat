package com.kuikly.stockchat.chat

import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.AlertStore
import com.kuikly.stockchat.data.GlossaryStore
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.provider.DeepSeekAiProvider
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage

/** Page-scoped dependency graph for the chat feature. */
class ChatDependencies(
    val configStore: AiConfigStore,
    val sessionStore: ChatSessionStore,
    val watchlistStore: WatchlistStore,
    val alertStore: AlertStore,
    val glossaryStore: GlossaryStore,
    val quoteRepository: QuoteRepository,
    val aiProviderFactory: (AiConfig) -> AiProvider,
) {
    companion object {
        fun forPager(pagerId: String): ChatDependencies {
            val storage = PagerKeyValueStorage(pagerId)
            val market = MarketDependencies.forPager(pagerId, storage)
            return ChatDependencies(
                configStore = AiConfigStore(storage),
                sessionStore = ChatSessionStore(storage),
                watchlistStore = market.watchlistStore,
                alertStore = market.alertStore,
                glossaryStore = market.glossaryStore,
                quoteRepository = market.quoteRepository,
                aiProviderFactory = { config -> DeepSeekAiProvider(pagerId, config) },
            )
        }
    }
}
