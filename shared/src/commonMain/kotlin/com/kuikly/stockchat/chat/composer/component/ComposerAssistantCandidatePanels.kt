package com.kuikly.stockchat.chat.composer.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.MentionType
import com.kuikly.stockchat.composer.SlashCommand
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class AtCandidatePanelProps(
    val theme: StockChatTheme,
    val composing: () -> Boolean,
    val candidates: () -> ObservableList<AtCandidate>,
    val query: () -> String,
    val highlight: () -> Int,
    val onSelect: (AtCandidate) -> Unit,
)

internal data class SlashCommandPanelProps(
    val theme: StockChatTheme,
    val unknown: () -> String,
    val candidates: () -> ObservableList<SlashCommand>,
    val highlight: () -> Int,
    val suggest: (String) -> List<SlashCommand>,
    val quickActions: List<SlashCommand>,
    val onSelect: (SlashCommand) -> Unit,
)

/** Stateless DSL for the @ mention and slash-command candidate panels. */
internal object ComposerAssistantCandidatePanels {
    fun renderAt(container: ViewContainer<*, *>, props: AtCandidatePanelProps) {
        container.View {
            attr {
                marginTop(8f)
                backgroundColor(props.theme.surface)
                borderRadius(12f)
                overflow(true)
            }
            // Keep observable reads inside reactive closures (R1/R7).
            vif({ props.composing() }) {
                View {
                    attr {
                        height(EMPTY_HEIGHT)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Text { attr { text("输入中…"); fontSizeScaled(12f); color(props.theme.textTertiary) } }
                }
            }
            vif({ !props.composing() && props.candidates().isEmpty() }) {
                View {
                    attr { height(56f); alignItemsCenter(); justifyContentCenter() }
                    Text {
                        attr {
                            val q = props.query()
                            text(if (q.isEmpty()) "没有可推荐的标的" else "没有匹配“$q”的标的")
                            fontSizeScaled(12f)
                            color(props.theme.textTertiary)
                        }
                    }
                }
            }
            vif({ !props.composing() && props.candidates().isNotEmpty() }) {
                Scroller {
                    attr {
                        height(panelHeight(props.candidates().size, CANDIDATE_ROW_HEIGHT))
                        flexDirectionColumn()
                        padding(4f)
                    }
                    vfor({ props.candidates() }) { candidate ->
                        val q = props.query()
                        View {
                            attr {
                                height(CANDIDATE_ROW_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                paddingLeft(12f)
                                paddingRight(12f)
                                backgroundColor(
                                    if (props.candidates().indexOf(candidate) == props.highlight()) props.theme.brandSoft
                                    else Color(0x00000000L, 0f)
                                )
                            }
                            event { click { props.onSelect(candidate) } }
                            if (candidate.entry.kind == MentionType.BOARD) {
                                ComposerCandidateRows.renderBoard(this, candidate, q, props.theme)
                            } else {
                                ComposerCandidateRows.renderSecurity(this, candidate, q, props.theme)
                            }
                        }
                    }
                }
            }
        }
    }

    fun renderSlash(container: ViewContainer<*, *>, props: SlashCommandPanelProps) {
        container.View {
            attr {
                marginTop(8f)
                backgroundColor(props.theme.surface)
                borderRadius(12f)
                overflow(true)
            }
            vif({ props.unknown().isNotEmpty() }) {
                View {
                    attr { padding(10f); flexDirectionColumn() }
                    Text { attr { text("未识别命令：/${props.unknown()}"); fontSizeScaled(12f); color(props.theme.textSecondary) } }
                    Text { attr { text("将作为普通文本发送"); fontSizeScaled(10f); color(props.theme.textTertiary) } }
                    val suggestions = props.suggest(props.unknown())
                    if (suggestions.isNotEmpty()) {
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
                            Text { attr { text("你是不是想用"); fontSizeScaled(10f); color(props.theme.textTertiary); marginRight(6f) } }
                            suggestions.forEach { command ->
                                View {
                                    attr {
                                        height(24f)
                                        marginRight(6f)
                                        paddingLeft(8f)
                                        paddingRight(8f)
                                        allCenter()
                                        backgroundColor(props.theme.brandSoft)
                                        borderRadius(7f)
                                    }
                                    Text { attr { text("/${command.name}"); fontSizeScaled(11f); color(props.theme.brand) } }
                                    event { click { props.onSelect(command) } }
                                }
                            }
                        }
                    }
                }
            }
            vif({ props.unknown().isEmpty() && props.candidates().isEmpty() }) {
                View {
                    attr { padding(10f); flexDirectionColumn() }
                    Text { attr { text("快捷操作"); fontSizeScaled(11f); color(props.theme.textTertiary) } }
                    View {
                        attr { marginTop(7f); flexDirectionRow(); alignItemsCenter() }
                        props.quickActions.forEach { command ->
                            View {
                                attr {
                                    height(30f); marginRight(7f); paddingLeft(10f); paddingRight(10f)
                                    alignItemsCenter(); justifyContentCenter(); borderRadius(8f)
                                    backgroundColor(props.theme.brandSoft)
                                }
                                event { click { props.onSelect(command) } }
                                Text { attr { text("${command.icon} /${command.name}"); fontSizeScaled(11f); color(props.theme.brand) } }
                            }
                        }
                    }
                }
            }
            vif({ props.unknown().isEmpty() && props.candidates().isNotEmpty() }) {
                Scroller {
                    attr {
                        height(panelHeight(props.candidates().size, COMMAND_ROW_HEIGHT))
                        flexDirectionColumn()
                        padding(4f)
                    }
                    vfor({ props.candidates() }) { command ->
                        View {
                            attr {
                                height(COMMAND_ROW_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f)
                                backgroundColor(
                                    if (props.candidates().indexOf(command) == props.highlight()) props.theme.brandSoft
                                    else Color(0x00000000L, 0f)
                                )
                            }
                            event { click { props.onSelect(command) } }
                            View {
                                attr {
                                    width(28f); height(28f); marginRight(10f); alignItemsCenter(); justifyContentCenter()
                                    backgroundColor(props.theme.brandSoft); borderRadius(8f)
                                }
                                Text { attr { text(command.icon); fontSizeScaled(14f); color(props.theme.brand) } }
                            }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text { attr { text("/${command.name}"); fontSizeScaled(13f); color(props.theme.textPrimary) } }
                                Text { attr { text(command.desc); fontSizeScaled(10f); color(props.theme.textTertiary) } }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun panelHeight(rowCount: Int, rowHeight: Float): Float {
        if (rowCount <= 0) return EMPTY_HEIGHT
        val wanted = rowCount * rowHeight + PANEL_PADDING
        val capped = MAX_ROWS * rowHeight + PANEL_PADDING
        return minOf(wanted, capped)
    }
}

private const val MAX_ROWS = 3
private const val CANDIDATE_ROW_HEIGHT = 42f
private const val COMMAND_ROW_HEIGHT = 52f
private const val PANEL_PADDING = 8f
private const val EMPTY_HEIGHT = 52f
