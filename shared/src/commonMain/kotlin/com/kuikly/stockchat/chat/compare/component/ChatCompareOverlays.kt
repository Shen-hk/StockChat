package com.kuikly.stockchat.chat.compare.component

import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.compare.state.CompareInsightState
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.page.components.ActiveComparePanel
import com.kuikly.stockchat.page.components.TermComparePanel
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.View

internal data class ChatCompareOverlayProps(
    val theme: StockChatTheme,
    val bottomInset: Float,
    val stockModel: () -> StockCompareCardModel?,
    val termVisible: () -> Boolean,
    val leftTermKey: () -> String,
    val rightTermKey: () -> String,
    val insightState: () -> CompareInsightState,
    val insightText: () -> String,
    val insightError: () -> String,
    val onRetryInsight: () -> Unit,
    val onOpenStock: (String) -> Unit,
    val onCloseStock: () -> Unit,
    val onCloseTerm: () -> Unit,
)

/** Stock and glossary comparison modals sharing the same bottom-anchored layout. */
internal object ChatCompareOverlays {
    fun render(container: ViewContainer<*, *>, props: ChatCompareOverlayProps) {
        container.vif({ props.stockModel() != null }) {
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    backgroundColor(Color(0x59000000)); touchEnable(true)
                    animate(Animation.easeOut(0.2f), props.stockModel() != null)
                }
                event { click { props.onCloseStock() } }
            }
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    justifyContentFlexEnd(); paddingBottom(props.bottomInset)
                }
                props.stockModel()?.let { model ->
                    ActiveComparePanel(
                        model = model,
                        theme = props.theme,
                        insightLoading = { props.insightState() == CompareInsightState.LOADING },
                        insightText = props.insightText,
                        insightError = props.insightError,
                        onRetryInsight = props.onRetryInsight,
                        onOpenStock = props.onOpenStock,
                        onClose = props.onCloseStock,
                    )
                }
            }
        }
        container.vif({
            props.termVisible() && props.leftTermKey().isNotEmpty() && props.rightTermKey().isNotEmpty()
        }) {
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    backgroundColor(Color(0x59000000)); touchEnable(true)
                    animate(Animation.easeOut(0.2f), props.termVisible())
                }
                event { click { props.onCloseTerm() } }
            }
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    justifyContentFlexEnd(); paddingBottom(props.bottomInset)
                }
                Glossary.byKey(props.leftTermKey())?.let { left ->
                    Glossary.byKey(props.rightTermKey())?.let { right ->
                        TermComparePanel(
                            left = left,
                            right = right,
                            theme = props.theme,
                            insightLoading = { props.insightState() == CompareInsightState.LOADING },
                            insightText = props.insightText,
                            insightError = props.insightError,
                            onRetryInsight = props.onRetryInsight,
                            onClose = props.onCloseTerm,
                        )
                    }
                }
            }
        }
    }
}
