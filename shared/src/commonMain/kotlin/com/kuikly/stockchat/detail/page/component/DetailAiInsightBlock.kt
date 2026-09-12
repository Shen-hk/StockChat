package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.page.AiInsightBlock
import com.kuikly.stockchat.page.RevealBlock
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * Wave 2 D5 第四组件：AI 一行归因 + AI 解读块（含句图联动点选）。
 * 从 StockDetailPage.body() 搬出原 lines 601-654，零行为变更；所有 AI 域
 * 状态经 detailAiCoordinator 只读 getter 暴露（Observable 在 attr/vif/vbind
 * 闭包内建立反应式依赖）。
 *
 * 组件只做装配，交互回链到 StockDetailPage（toggleAiInsight / pickSentence），
 * 不引入新的 Provider。
 */
internal fun ViewContainer<*, *>.DetailAiInsightBlock(
    theme: StockChatTheme,
    reduceMotion: Boolean,
    entranceVisible: () -> Boolean,
    oneLineAttributionRevealIndex: Int,
    insightBlockRevealIndex: Int,
    // ---- getters（observable）----
    quote: () -> Quote,
    aiRemoteState: () -> Int,
    aiRemoteText: () -> String,
    aiRemoteError: () -> String,
    aiRemoteModel: () -> String,
    aiRevealSource: () -> String,
    aiRevealLimit: () -> Int,
    aiAwaitingFacts: () -> Boolean,
    livePulse: () -> Boolean,
    aiActionLabel: () -> String,
    insightSentences: () -> List<String>,
    selectedSentence: () -> Int,
    oneLineAttributionText: () -> String,
    // ---- actions ----
    onToggleAiInsight: () -> Unit,
    onPickSentence: (Int) -> Unit,
) {
    // ---- AI 一行归因：全页唯一常驻 AI 触点（端侧模板，纯事实） ----
    RevealBlock(oneLineAttributionRevealIndex, entranceVisible, reduceMotion) {
        vbind({ quote() }) {
            View {
                attr {
                    marginTop(theme.spacing.md)
                    paddingLeft(10f)
                    paddingTop(7f); paddingBottom(7f)
                    backgroundColor(theme.brandSoft)
                    borderRadius(8f)
                }
                View {
                    attr {
                        absolutePosition(left = 0f, top = 7f, bottom = 7f)
                        width(3f)
                        borderRadius(1.5f)
                        backgroundColor(theme.brand)
                    }
                }
                Text {
                    attr {
                        text(oneLineAttributionText())
                        fontSizeScaled(11.5f)
                        lineHeightScaled(17f)
                        color(theme.textSecondary)
                    }
                }
            }
        }
    }

    // ---- AI 解读（叙事位：紧贴一行归因，先给解读再看业务数据）----
    // 真实化：默认走真实 LLM 流式，未配置/失败回退端侧模板并如实标注来源。
    // ② 句图联动：点句子 → 端侧按句内时间在走势图点亮真实区间
    RevealBlock(insightBlockRevealIndex, entranceVisible, reduceMotion) {
        AiInsightBlock(
            state = aiRemoteState,
            remoteText = aiRemoteText,
            remoteError = aiRemoteError,
            remoteModel = aiRemoteModel,
            localSummary = aiRevealSource,
            revealLimit = aiRevealLimit,
            actionLabel = aiActionLabel,
            remoteSentences = insightSentences,
            preparing = aiAwaitingFacts,
            breath = livePulse,
            reduceMotion = reduceMotion,
            theme = theme,
            onAction = onToggleAiInsight,
            selectedSentence = selectedSentence,
            onPickSentence = onPickSentence,
        )
    }
}