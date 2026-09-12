package com.kuikly.stockchat.risk.ai.component

import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.risk.state.RiskUiProps
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 卡底 AI 详细解读块的渲染组件（doc 47 B-3 随迁）：标题 + 来源行 + 操作 +
 * 流式正文/端侧速览。状态机在 [com.kuikly.stockchat.risk.ai.state.RiskAiCoordinator]，
 * 本文件只读 observable（attr/vif 内，R1/R7）。
 */
internal fun ViewContainer<*, *>.renderSkyAiBlock(props: RiskUiProps) {
    View {
        attr {
            marginTop(14f)
            paddingLeft(12f)
            paddingRight(12f)
            paddingTop(11f)
            paddingBottom(11f)
            borderRadius(12f)
            backgroundColor(props.theme.surfaceMuted)
        }
        // 标题行：标题 + 来源 + 操作。
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("AI 详细解读")
                    fontSizeScaled(12f)
                    fontWeightSemiBold()
                    color(props.theme.textPrimary)
                }
            }
            Text {
                attr {
                    flex(1f)
                    marginLeft(8f)
                    text(
                        when {
                            props.ai.aiState == 1 || props.ai.aiState == 2 -> props.ai.aiModel
                            props.ai.aiState == 3 -> props.ai.aiModel
                            props.ai.aiError.isNotEmpty() -> props.ai.aiError
                            else -> "端侧速览"
                        },
                    )
                    fontSizeScaled(9.5f)
                    color(props.theme.textTertiary)
                }
            }
            Text {
                attr {
                    text(props.ai.actionLabel())
                    fontSizeScaled(11f)
                    color(props.theme.brand)
                }
                event { click { props.ai.toggle() } }
            }
        }
        // 状态必须放在 vif 中：普通 builder 只执行一次，流式状态/正文变化会被冻
        // 在首帧（R1/R7）。Text 的正文仍在 attr 中读取，保证每个增量都能刷新。
        vif({ props.ai.aiState == 1 }) {
            Text {
                attr {
                    text("正在调用 AI（${props.ai.aiModel}）· 流式生成中…")
                    marginTop(6f)
                    fontSizeScaled(11f)
                    lineHeightScaled(17f)
                    color(props.theme.textTertiary)
                }
            }
        }
        vif({ props.ai.aiState == 2 || props.ai.aiState == 3 }) {
            Text {
                attr {
                    text(props.ai.aiText)
                    marginTop(6f)
                    fontSizeScaled(11.5f)
                    lineHeightScaled(18f)
                    color(props.theme.textPrimary)
                }
            }
        }
        vif({ props.ai.aiState == 4 }) {
            View {
                attr { marginTop(6f) }
                Text {
                    attr {
                        text(props.ai.aiError)
                        fontSizeScaled(11f)
                        color(props.theme.fall)
                    }
                }
                if (props.ai.aiText.isNotBlank()) {
                    Text {
                        attr {
                            text(props.ai.aiText)
                            marginTop(4f)
                            fontSizeScaled(11.5f)
                            lineHeightScaled(18f)
                            color(props.theme.textSecondary)
                        }
                    }
                }
            }
        }
        vif({ props.ai.aiState == 0 }) {
            Text {
                attr {
                    text(
                        if (props.ai.aiError.isNotEmpty()) props.ai.aiError
                        else "点「生成」让 AI 基于上方真实数据写一段详细解读",
                    )
                    marginTop(6f)
                    fontSizeScaled(11.5f)
                    lineHeightScaled(18f)
                    color(props.theme.textSecondary)
                }
            }
        }
        vif({ props.ai.aiState == 0 }) {
            Text {
                attr {
                    text(props.ai.localSummary())
                    marginTop(6f)
                    fontSizeScaled(11f)
                    lineHeightScaled(17f)
                    color(props.theme.textTertiary)
                }
            }
        }
    }
}
