package com.kuikly.stockchat.chat.composer.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.CommandParam
import com.kuikly.stockchat.composer.MentionType
import com.kuikly.stockchat.composer.ParamType
import com.kuikly.stockchat.composer.SlashCommand
import com.kuikly.stockchat.data.fontSizeScaled
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class CommandParamPanelProps(
    val theme: StockChatTheme,
    val renderKeys: ObservableList<Int>,
    val command: () -> SlashCommand?,
    val resolveArgs: (SlashCommand) -> Map<String, String>,
    val missingRequired: (SlashCommand, Map<String, String>) -> List<CommandParam>,
    val currentQuery: (SlashCommand) -> String,
    val rankCandidates: (String) -> List<AtCandidate>,
    val onCancel: () -> Unit,
    val onClearFilled: (SlashCommand, CommandParam) -> Unit,
    val onSelectSecurity: (AtCandidate) -> Unit,
    val onSelectEnum: (String) -> Unit,
)

/** Stateless DSL for command parameter slots and their inline selectors. */
internal object ComposerCommandParamPanel {
    fun render(container: ViewContainer<*, *>, props: CommandParamPanelProps) {
        // R7: every render-key collection operation rebuilds the full derived frame.
        container.vfor({ props.renderKeys }) { _ ->
            val command = props.command() ?: return@vfor
            val args = props.resolveArgs(command)
            val missing = props.missingRequired(command, args)
            val trailing = props.currentQuery(command)
            val currentKey = missing.firstOrNull()?.key
                ?: command.params.firstOrNull { param ->
                    args[param.key].isNullOrBlank() && param.type == ParamType.ENUM && trailing in param.enumOptions
                }?.key
                ?: command.params.firstOrNull { args[it.key].isNullOrBlank() }?.key
            val currentParam = command.params.firstOrNull { it.key == currentKey }
            val requiredTotal = command.params.count { it.required }
            val wanted = 32f + command.params.size * 40f + 36f +
                if (currentParam?.type == ParamType.SECURITY) {
                    18f + MAX_ROWS * (CANDIDATE_ROW_HEIGHT + 4f)
                } else {
                    0f
                }

            Scroller {
                attr {
                    height(minOf(wanted, MAX_ROWS * COMMAND_ROW_HEIGHT + PANEL_PADDING))
                    marginTop(8f)
                    flexDirectionColumn()
                    backgroundColor(props.theme.surface)
                    borderRadius(12f)
                    padding(10f)
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginBottom(8f) }
                    View {
                        attr {
                            width(24f); height(24f); marginRight(8f); alignItemsCenter(); justifyContentCenter()
                            backgroundColor(props.theme.brandSoft); borderRadius(6f)
                        }
                        Text { attr { text(command.icon); fontSizeScaled(12f); color(props.theme.brand) } }
                    }
                    Text { attr { text("/${command.name} · 参数"); fontSizeScaled(13f); color(props.theme.textPrimary) } }
                    View { attr { flex(1f) } }
                    if (requiredTotal > 0) {
                        Text {
                            attr {
                                text("必填 ${requiredTotal - missing.size}/$requiredTotal")
                                fontSizeScaled(10f)
                                color(if (missing.isEmpty()) props.theme.brand else props.theme.textSecondary)
                                marginRight(8f)
                            }
                        }
                    }
                    View {
                        attr {
                            height(22f); paddingLeft(8f); paddingRight(8f)
                            alignItemsCenter(); justifyContentCenter()
                            backgroundColor(props.theme.surfaceMuted); borderRadius(7f)
                        }
                        event { click { props.onCancel() } }
                        Text { attr { text("✕ 取消"); fontSizeScaled(10f); color(props.theme.textSecondary) } }
                    }
                }
                command.params.forEach { param ->
                    val filled = args[param.key].orEmpty()
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(4f)
                            padding(6f)
                            backgroundColor(
                                when {
                                    filled.isNotEmpty() -> props.theme.brandSoft
                                    param.key == currentKey -> props.theme.surface
                                    else -> props.theme.surfaceMuted
                                }
                            )
                            borderRadius(8f)
                        }
                        if (filled.isNotEmpty()) {
                            event { click { props.onClearFilled(command, param) } }
                        }
                        View {
                            attr { flex(1f); flexDirectionColumn() }
                            Text {
                                attr {
                                    text(param.label + if (param.required) " *" else "（可选）")
                                    fontSizeScaled(11f)
                                    color(if (param.key == currentKey) props.theme.brand else props.theme.textSecondary)
                                }
                            }
                            Text {
                                attr {
                                    text(if (filled.isNotEmpty()) filled else param.placeholder)
                                    fontSizeScaled(12f)
                                    color(if (filled.isNotEmpty()) props.theme.textPrimary else props.theme.textTertiary)
                                }
                            }
                        }
                        Text {
                            attr {
                                text(
                                    if (filled.isNotEmpty()) "重填" else when (param.type) {
                                        ParamType.SECURITY -> "@"
                                        ParamType.ENUM -> "选"
                                        else -> "文"
                                    }
                                )
                                fontSizeScaled(9f)
                                color(props.theme.textTertiary)
                            }
                        }
                    }
                }
                if (currentParam?.type == ParamType.SECURITY) {
                    val query = props.currentQuery(command)
                    val candidates = props.rankCandidates(query).take(MAX_ROWS)
                    Text {
                        attr {
                            text(
                                when {
                                    query.isEmpty() -> "选择${currentParam.label}"
                                    candidates.isEmpty() -> "没有匹配「$query」的标的 · 可输入完整名称或代码后发送"
                                    else -> "匹配「$query」"
                                }
                            )
                            marginTop(10f)
                            fontSizeScaled(10f)
                            color(props.theme.textTertiary)
                        }
                    }
                    candidates.forEachIndexed { index, candidate ->
                        View {
                            attr {
                                height(CANDIDATE_ROW_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                marginTop(4f)
                                paddingLeft(10f)
                                paddingRight(10f)
                                backgroundColor(if (index == 0) props.theme.brandSoft else props.theme.surfaceMuted)
                                borderRadius(8f)
                            }
                            event { click { props.onSelectSecurity(candidate) } }
                            if (candidate.entry.kind == MentionType.BOARD) {
                                ComposerCandidateRows.renderBoard(this, candidate, query, props.theme)
                            } else {
                                ComposerCandidateRows.renderSecurity(this, candidate, query, props.theme)
                            }
                        }
                    }
                } else if (currentParam?.type == ParamType.ENUM) {
                    Text {
                        attr {
                            text("选择${currentParam.label}")
                            marginTop(10f)
                            fontSizeScaled(10f)
                            color(props.theme.textTertiary)
                        }
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); marginTop(6f) }
                        currentParam.enumOptions.forEach { option ->
                            View {
                                attr {
                                    height(28f)
                                    marginRight(7f)
                                    paddingLeft(11f)
                                    paddingRight(11f)
                                    allCenter()
                                    backgroundColor(props.theme.brandSoft)
                                    borderRadius(8f)
                                }
                                Text { attr { text(option); fontSizeScaled(12f); fontWeightMedium(); color(props.theme.brand) } }
                                event { click { props.onSelectEnum(option) } }
                            }
                        }
                    }
                }
                View {
                    attr { marginTop(8f); alignItemsCenter(); justifyContentCenter(); height(28f) }
                    Text {
                        attr {
                            text(
                                when {
                                    missing.isNotEmpty() -> "还差必填：${missing.joinToString("、") { it.label }} · 点下方候选或直接输入"
                                    command.params.any { args[it.key].isNullOrBlank() } -> "必填已齐 · 可点选可选参数或直接发送"
                                    else -> "参数已齐 · 点击发送键发送"
                                }
                            )
                            fontSizeScaled(10f)
                            color(props.theme.textTertiary)
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_ROWS = 3
private const val CANDIDATE_ROW_HEIGHT = 42f
private const val COMMAND_ROW_HEIGHT = 52f
private const val PANEL_PADDING = 8f
