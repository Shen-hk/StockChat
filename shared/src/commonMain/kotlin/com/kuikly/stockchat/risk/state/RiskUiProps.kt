package com.kuikly.stockchat.risk.state

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatDependencies
import com.kuikly.stockchat.data.RiskSnapshot
import com.kuikly.stockchat.risk.ai.state.RiskAiCoordinator
import com.kuikly.stockchat.risk.alert.state.RiskAlertCoordinator
import com.kuikly.stockchat.risk.sky.state.RiskSkyCoordinator

/**
 * Risk 页组件层的只读 Props（doc 47 B-3）：
 * **组件不允许接收整个 Page**——只拿主题、reduce motion、四个状态域 coordinator、
 * 路由/Haptic 类 Actions 与术语出口。所有 observable 读取仍发生在组件的
 * `attr {}` / `vif {}` / `vbind {}` 闭包内（AGENTS R1）。
 */
internal class RiskUiProps(
    val theme: StockChatTheme,
    val reduceMotion: Boolean,
    val data: RiskDataCoordinator,
    val sky: RiskSkyCoordinator,
    val ai: RiskAiCoordinator,
    val alert: RiskAlertCoordinator,
    val actions: RiskPageActions,
    /** 导图视图读聊天存档（sessionStore.peekAllMessages，构建期快照）。 */
    val chatDependencies: ChatDependencies,
    /** 联动契约 C-2：术语触发单点收口，只调 GlossaryStore.encounter。 */
    val glossaryEncounter: (String) -> Unit,
    /** FR-R10 快照历史（构建期读一次）。 */
    val snapshotHistory: () -> List<RiskSnapshot>,
)

/** 路由出口 Effects：Page 独占，组件只回调。 */
internal class RiskPageActions(
    val openPage: (String) -> Unit,
    val openStockDetail: (String, String) -> Unit,
    /** (question, focusNote, focusSymbol) —— focusSymbol 可为空串。 */
    val openChatWithQuestion: (String, String, String) -> Unit,
)
