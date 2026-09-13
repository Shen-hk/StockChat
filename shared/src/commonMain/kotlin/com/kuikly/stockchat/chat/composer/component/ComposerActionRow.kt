package com.kuikly.stockchat.chat.composer.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.StreamState
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.icon.LineIconAudioLines
import com.kuikly.stockchat.foundation.ui.icon.LineIconPlus
import com.kuikly.stockchat.foundation.ui.icon.LineIconStop
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class ComposerActionRowProps(
    val theme: StockChatTheme,
    val expanded: () -> Boolean,
    val presented: () -> Boolean,
    val voiceBusy: () -> Boolean,
    val themeKey: () -> String,
    val streamState: () -> StreamState,
    val sendBlocked: () -> Boolean,
    val onTrigger: (Char) -> Unit,
    val onVoice: () -> Unit,
    val onMedia: () -> Unit,
    val onStop: () -> Unit,
    val onSend: () -> Unit,
)

internal fun ViewContainer<*, *>.ComposerActionRow(props: ComposerActionRowProps) {
    View {
        attr {
            val isExpanded = props.expanded()
            height(if (isExpanded) ACTION_ROW_HEIGHT else 0f)
            marginTop(if (isExpanded) ACTION_ROW_GAP else 0f)
            opacity(if (isExpanded) 1f else 0f)
            touchEnable(isExpanded)
            animate(Animation.easeOut(LAYOUT_DURATION), isExpanded)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            TriggerButton(props, '@', 0f)
            TriggerButton(props, '/', 0.03f, marginLeft = 8f)
            View { attr { flex(1f) } }
            AnimatedAction(props, 0.06f) {
                View {
                    attr { size(46f, 46f); marginRight(6f); allCenter(); borderRadius(23f) }
                    vbind(props.themeKey) { LineIconAudioLines(color = props.theme.textPrimary, size = 26f) }
                    event { click { props.onVoice() } }
                }
            }
            AnimatedAction(props, 0.09f) {
                View {
                    attr {
                        size(46f, 46f); marginRight(4f); allCenter(); borderRadius(23f)
                        opacity(if (props.voiceBusy()) 0.4f else 1f); touchEnable(!props.voiceBusy())
                    }
                    LineIconPlus(color = props.theme.textSecondary, size = 22f)
                    event { click { props.onMedia() } }
                }
            }
            AnimatedAction(props, 0.12f) {
                View {
                    attr {
                        size(44f, 44f); allCenter(); borderRadius(22f)
                        backgroundColor(
                            when {
                                // Keep the stop action visually related to the send action:
                                // streaming is an active state, not a disabled grey control.
                                props.streamState() == StreamState.STREAMING -> props.theme.brand
                                props.sendBlocked() -> props.theme.surfaceMuted
                                else -> props.theme.brand
                            }
                        )
                        // A tight shadow gives the circular control lift without leaving a
                        // dark, square-looking halo around it.
                        boxShadow(BoxShadow(0f, 2f, 5f, Color(0x000000, 0.12f)))
                        opacity(if (props.voiceBusy()) 0.4f else 1f); touchEnable(!props.voiceBusy())
                    }
                    vif({ props.streamState() == StreamState.STREAMING }) {
                        LineIconStop(color = props.theme.onBrand, size = 15f)
                    }
                    vif({ props.streamState() != StreamState.STREAMING }) {
                        Text {
                            attr {
                                text("↑"); fontSizeScaled(18f); fontWeightSemiBold()
                                color(if (props.sendBlocked()) props.theme.textTertiary else props.theme.onBrand)
                            }
                        }
                    }
                    event { click { if (props.streamState() == StreamState.STREAMING) props.onStop() else props.onSend() } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.TriggerButton(
    props: ComposerActionRowProps,
    trigger: Char,
    delay: Float,
    marginLeft: Float = 0f,
) {
    AnimatedAction(props, delay) {
        View {
            attr {
                size(46f, 46f); if (marginLeft > 0f) marginLeft(marginLeft)
                allCenter(); borderRadius(23f)
                opacity(if (props.voiceBusy()) 0.4f else 1f); touchEnable(!props.voiceBusy())
            }
            Text { attr { text(trigger.toString()); fontSizeScaled(18f); fontWeightSemiBold(); color(props.theme.brand) } }
            event { click { props.onTrigger(trigger) } }
        }
    }
}

private fun ViewContainer<*, *>.AnimatedAction(
    props: ComposerActionRowProps,
    delay: Float,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            opacity(if (props.presented()) 1f else 0f)
            transform(Translate(0f, if (props.presented()) 0f else 8f))
            animate(Animation.easeOut(0.24f).delay(delay), props.presented())
        }
        content()
    }
}

private const val ACTION_ROW_HEIGHT = 46f
private const val ACTION_ROW_GAP = 8f
private const val LAYOUT_DURATION = 0.28f
