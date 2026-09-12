package com.kuikly.stockchat.chat.composer.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.icon.LineIconAudioLines
import com.kuikly.stockchat.foundation.ui.icon.LineIconKeyboard
import com.kuikly.stockchat.foundation.ui.icon.LineIconPlus
import com.kuikly.stockchat.page.components.VoiceBar
import com.kuikly.stockchat.voice.VoiceState
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class ComposerInputRowProps(
    val theme: StockChatTheme,
    val visuallyExpanded: () -> Boolean,
    val chromePresented: () -> Boolean,
    val themeKey: () -> String,
    val voiceMode: () -> Boolean,
    val voiceState: () -> VoiceState,
    val voiceCancelArmed: () -> Boolean,
    val voiceAmplitudes: () -> FloatArray,
    val renderTextArea: (ViewContainer<*, *>) -> Unit,
    val onMiddleTap: () -> Unit,
    val onOpenMedia: () -> Unit,
    val onToggleVoiceMode: () -> Unit,
    val onVoiceDown: (Float) -> Unit,
    val onVoiceMove: (Float) -> Unit,
    val onVoiceUp: () -> Unit,
)

internal fun ViewContainer<*, *>.ComposerInputRow(props: ComposerInputRowProps) {
    View {
        attr { flexDirectionRow(); alignItemsCenter() }
        vif({ !props.visuallyExpanded() }) {
            View {
                attr { size(48f, 48f); marginRight(7f); allCenter(); borderRadius(24f) }
                View {
                    attr {
                        opacity(if (!props.chromePresented()) 1f else 0f)
                        transform(scale = if (!props.chromePresented()) Scale.DEFAULT else Scale(0.6f, 0.6f))
                        animate(Animation.easeOut(0.2f), !props.chromePresented())
                    }
                    vbind(props.themeKey) { LineIconPlus(color = props.theme.textPrimary, size = 24f) }
                }
                event { click { props.onOpenMedia() } }
            }
        }
        View {
            attr {
                flex(1f); minHeight(if (props.visuallyExpanded()) 44f else 48f)
                paddingLeft(12f); paddingRight(12f); justifyContentCenter(); borderRadius(16f)
                backgroundColor(Color(0xFFFFFFFF, 0f))
            }
            event { click { props.onMiddleTap() } }
            props.renderTextArea(this)
            vif({ props.voiceMode() }) {
                View {
                    attr { height(48f); allCenter(); borderRadius(16f); backgroundColor(props.theme.surface) }
                    vif({ props.voiceState() == VoiceState.IDLE }) {
                        Text { attr { text("按住 说话"); fontSizeScaled(15f); color(props.theme.textSecondary) } }
                    }
                    event {
                        touchDown { props.onVoiceDown(it.pageY) }
                        touchMove { props.onVoiceMove(it.pageY) }
                        touchUp { props.onVoiceUp() }
                    }
                }
            }
            vif({ props.voiceState() != VoiceState.IDLE }) {
                VoiceBar(props.theme, { props.voiceState() == VoiceState.TRANSCRIBING }, props.voiceCancelArmed, props.voiceAmplitudes)
            }
        }
        vif({ !props.visuallyExpanded() }) {
            View {
                attr {
                    opacity(if (!props.chromePresented()) 1f else 0f)
                    transform(scale = if (!props.chromePresented()) Scale.DEFAULT else Scale(0.6f, 0.6f))
                    animate(Animation.easeOut(0.2f), !props.chromePresented())
                }
                View {
                    attr { size(48f, 48f); marginLeft(7f); allCenter(); borderRadius(24f) }
                    vif({ !props.voiceMode() }) { vbind(props.themeKey) { LineIconAudioLines(props.theme.textPrimary, 27f) } }
                    vif({ props.voiceMode() }) { vbind(props.themeKey) { LineIconKeyboard(props.theme.textPrimary, 27f) } }
                    event { click { props.onToggleVoiceMode() } }
                }
            }
        }
    }
}
