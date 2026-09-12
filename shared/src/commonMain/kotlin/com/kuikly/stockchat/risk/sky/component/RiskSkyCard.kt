package com.kuikly.stockchat.risk.sky.component

import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.risk.ai.component.renderSkyAiBlock
import com.kuikly.stockchat.risk.domain.ChainConcentration
import com.kuikly.stockchat.risk.domain.IndustryStat
import com.kuikly.stockchat.risk.domain.SkyLayer
import com.kuikly.stockchat.risk.domain.skyFocusNote
import com.kuikly.stockchat.risk.domain.skyStarSub
import com.kuikly.stockchat.risk.panel.component.renderAnnotation
import com.kuikly.stockchat.risk.panel.component.renderHeadline
import com.kuikly.stockchat.risk.sky.state.RiskSkyCoordinator.SkyViewMode
import com.kuikly.stockchat.risk.state.RiskUiProps
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 星图主卡（≥3 只自选的档 1 形态）与导图视图的渲染组件（doc 47 B-3 随迁）：
 * 图层 chips + 五投影 Canvas + 焦点注释 + 成员抽屉 + 引路星解读抽屉 +
 * Context Bar + 思维对比导图。布局/参数/手势判定逐值保留。
 */

/**
 * 星图主卡（doc 32：一张图，多图层）。z3 唯一玻璃卡。
 * AI 预算（doc 32 §3）：引路星（条件 ≤2）+ 焦点注释（条件 1），平静且无选中 = 0。
 */
internal fun ViewContainer<*, *>.renderSkyMode(
    props: RiskUiProps,
    industry: List<IndustryStat>,
    chain: ChainConcentration,
) {
    this.renderHeadline(props, industry, chain, withIndustryCard = false)
    View {
        attr {
            marginTop(10f)
            padding(16f)
            borderRadius(16f)
            backgroundColor(props.theme.marketGlass)
            boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.10f)))
        }

        // 主卡视图分段切换器（星图/导图）。条件背景/文字色 if-else 两分支全量赋值
        // （attr 条件属性不设不清，2026-09-10 坑）。
        View {
            attr {
                flexDirectionRow()
                padding(2f)
                borderRadius(10f)
                backgroundColor(props.theme.surfaceMuted)
            }
            SkyViewMode.entries.forEach { mode ->
                View {
                    attr {
                        flex(1f)
                        height(26f)
                        allCenter()
                        borderRadius(8f)
                        backgroundColor(
                            if (props.sky.skyViewMode == mode) props.theme.surface else props.theme.surfaceMuted,
                        )
                    }
                    event { click { props.sky.applySkyViewMode(mode) } }
                    Text {
                        attr {
                            text(mode.label)
                            fontSizeScaled(11.5f)
                            color(
                                if (props.sky.skyViewMode == mode) props.theme.textPrimary else props.theme.textTertiary,
                            )
                        }
                    }
                }
            }
        }

        // ── 星图视图：图层 chips → Canvas → 日程/注释/抽屉/Context Bar ──
        vif({ props.sky.skyViewMode == SkyViewMode.CHART }) {

            // 图层 chips（拇指区）：单 observable driver（skyLayer）。
            View {
                attr {
                    marginTop(12f)
                    flexDirectionRow(); flexWrapWrap() }
                SkyLayer.entries.forEach { layer ->
                    View {
                        attr {
                            marginRight(8f)
                            marginBottom(8f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            height(28f)
                            allCenter()
                            borderRadius(9f)
                            backgroundColor(if (props.sky.skyLayer == layer) props.theme.brandSoft else props.theme.surfaceMuted)
                        }
                        event { click { props.sky.applySkyLayer(layer) } }
                        Text {
                            attr {
                                text(layer.label)
                                fontSizeScaled(11.5f)
                                color(if (props.sky.skyLayer == layer) props.theme.brand else props.theme.textSecondary)
                            }
                        }
                    }
                }
            }

            // 层 tip + 术语出口（联动契约 C-2：每层 tip 一词可点，单点收口）。
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        flex(1f)
                        text(props.sky.layerTip())
                        fontSizeScaled(10.5f)
                        lineHeightScaled(16f)
                        color(props.theme.textTertiary)
                    }
                }
                View {
                    attr {
                        marginLeft(8f)
                        paddingLeft(8f)
                        paddingRight(8f)
                        paddingTop(3f)
                        paddingBottom(3f)
                        borderRadius(9f)
                        backgroundColor(props.theme.brandSoft)
                    }
                    event {
                        click {
                            props.glossaryEncounter(props.sky.skyLayer.termKey)
                            props.actions.openPage(Routes.GLOSSARY)
                        }
                    }
                    Text {
                        attr {
                            text("这是什么 ›")
                            fontSizeScaled(10f)
                            color(props.theme.brand)
                        }
                    }
                }
            }

            // 星图 Canvas（五投影同图，切层星星不换位置）。
            View {
                attr { marginTop(8f) }
                RiskSkyChart(
                    theme = props.theme,
                    geometry = { props.sky.skyGeometry() },
                    correlations = { props.sky.correlations },
                    layer = { props.sky.skyLayer },
                    selectedSymbol = { props.sky.skySelectedSymbol },
                    beaconClusterIndex = { props.sky.skyBeaconClusterIndex() },
                    beaconPhase = { props.sky.beaconPhase },
                    volRatioOf = { symbol -> props.sky.volRatios[symbol] },
                    boardsOf = { symbol ->
                        props.data.limitUps.firstOrNull { it.second.symbol == symbol }?.first?.consecutiveBoards ?: 0
                    },
                    changePercentOf = { symbol ->
                        props.data.rows.firstOrNull { it.symbol == symbol }?.quote?.changePercent
                    },
                    eventsOf = { symbol ->
                        com.kuikly.stockchat.risk.domain.skyEventsOf(symbol, props.data.events)
                    },
                    dragOffsets = { props.sky.skyDragOffsets },
                    canvasHeight = { props.sky.skyGeometry().requiredHeight },
                    reduceMotion = props.reduceMotion,
                    onTapStar = { props.sky.onSkyStarTap(it) },
                    onTapBeacon = { props.sky.onSkyBeaconTap() },
                    onTapCluster = { props.sky.onSkyClusterTap(it) },
                    onTapBlank = { props.sky.onSkyBlankTap() },
                    onDragStar = { symbol, dx, dy -> props.sky.onSkyStarDrag(symbol, dx, dy) },
                    onDragEnd = { props.sky.onSkyStarDragEnd() },
                    onLongPressStar = { props.sky.onSkyStarLongPress(it) },
                )
            }

            // 焦点注释（Spotlight，预算 1）：选中即浮现，取消即收起。
            vif({ props.sky.skySelectedSymbol.isNotEmpty() }) {
                vbind({ props.sky.skySelectedSymbol }) {
                    val note = skyFocusNote(
                        props.sky.skySelectedSymbol,
                        props.data.rows.toList(),
                        props.sky.correlations,
                        props.sky.volRatios,
                        props.data.limitUps,
                        props.data.events,
                    )
                    this.renderAnnotation(props, note)
                }
            }

            // 成员抽屉（就地展开面板，非底部 sheet——vif 新视图做不了入场动画，R4）。
            vif({ props.sky.skySelectedSymbol.isNotEmpty() }) {
                vbind({ props.sky.skySelectedSymbol }) {
                    this.renderSkyStarDrawer(props)
                }
            }
            vif({ props.sky.skySelectedCluster.isNotEmpty() }) {
                vbind({ props.sky.skySelectedCluster }) {
                    this.renderSkyClusterDrawer(props, props.sky.skySelectedCluster)
                }
            }

            // 长按星才出现的 Context Bar：不占平静态页面空间。
            vif({ props.sky.skyContextSymbol.isNotEmpty() }) {
                vbind({ props.sky.skyContextSymbol }) { this.renderSkyContextBar(props) }
            }

            // 引路星解读抽屉（点击光环升起：3 行规则事实 + 追问出口）。
            vif({ props.sky.skyBeaconDrawer }) {
                this.renderSkyBeaconDrawer(props, chain)
            }
        } // vif CHART

        // ── 导图视图：聊天思维 vs 标准选股思路（缺失环节可点补课）──
        vif({ props.sky.skyViewMode == SkyViewMode.MINDMAP }) {
            this.renderMindMap(props)
        }

        // ── 卡底 AI 详细解读（流式）：两种视图共用，行情事实就绪后自动生成一次 ──
        this.renderSkyAiBlock(props)
    }
}

// ── 导图视图（思维对比）：用户聊天思维 × 标准选股思路六环 ──

/**
 * 思维对比导图（2026-09-10 用户定案）：分析用户聊天中的思维方式，
 * 与标准选股思路六环逐环对比，标出缺失环节。纯端侧规则归档（零 LLM）：
 * 实心环 = 聊过（次数 + 例句），灰环 = 缺失，点缺失环一键跳对话页补课。
 * 注：vif 每次激活重建（R7），切到导图时重新读聊天存档，画像即最新。
 */
private fun ViewContainer<*, *>.renderMindMap(props: RiskUiProps) {
    val chatDeps = props.chatDependencies
    val userMessages = chatDeps.sessionStore.peekAllMessages()
        .filter { it.role == com.kuikly.stockchat.chat.MessageRole.USER && !it.failed }
        .map { it.content }
    val hits = com.kuikly.stockchat.chat.ChatThinkingProfile.analyze(userMessages)
    val covered = com.kuikly.stockchat.chat.ChatThinkingProfile.coveredCount(hits)
    val missing = com.kuikly.stockchat.chat.ChatThinkingProfile.missingTitles(hits)
    View {
        attr { marginTop(12f) }
        // 汇总行：覆盖度一句话。
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    paddingRight(10f)
                    paddingLeft(10f)
                    paddingTop(6f)
                    paddingBottom(6f)
                    borderRadius(10f)
                    allCenter()
                    backgroundColor(props.theme.brandSoft)
                }
                Text {
                    attr {
                        text("标准选股思路 · 6 环")
                        fontSizeScaled(11f)
                        fontWeightSemiBold()
                        color(props.theme.brand)
                    }
                }
            }
            Text {
                attr {
                    flex(1f)
                    marginLeft(10f)
                    text(
                        if (userMessages.isEmpty()) "聊天里还没有提问记录"
                        else if (missing.isEmpty()) "六环全覆盖——聊得很完整"
                        else "覆盖 $covered/6 环 · 缺：$missing",
                    )
                    fontSizeScaled(11f)
                    lineHeightScaled(15f)
                    color(props.theme.textSecondary)
                }
            }
        }
        Text {
            attr {
                text("实心 = 你在聊天里问过 · 点灰色节点，用一句预置问题补上这一环")
                marginTop(8f)
                fontSizeScaled(10.5f)
                color(props.theme.textTertiary)
            }
        }
        // 六环分支：左侧脊柱点 + 右侧环节卡。
        hits.forEachIndexed { index, hit ->
            View {
                attr { marginTop(10f); flexDirectionRow(); alignItemsStretch() }
                // 脊柱列：状态点 + 连接线（非末环）。
                View {
                    attr { width(14f); alignItemsCenter() }
                    View {
                        attr {
                            width(8f)
                            height(8f)
                            borderRadius(4f)
                            marginTop(14f)
                            backgroundColor(if (hit.covered) props.theme.brand else props.theme.divider)
                        }
                    }
                    if (index < hits.lastIndex) {
                        View {
                            attr {
                                width(1.5f)
                                flex(1f)
                                marginTop(2f)
                                borderRadius(1f)
                                backgroundColor(props.theme.divider)
                            }
                        }
                    }
                }
                // 环节卡。
                View {
                    attr {
                        flex(1f)
                        marginLeft(8f)
                        paddingLeft(10f)
                        paddingRight(10f)
                        paddingTop(8f)
                        paddingBottom(8f)
                        borderRadius(10f)
                        backgroundColor(
                            if (hit.covered) props.theme.surfaceMuted else props.theme.brandSoft,
                        )
                    }
                    if (!hit.covered) {
                        event {
                            click {
                                props.actions.openChatWithQuestion(
                                    hit.stage.askPrompt,
                                    "来自风险地图：选股思路缺「${hit.stage.title}」环",
                                    "",
                                )
                            }
                        }
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter() }
                        Text {
                            attr {
                                flex(1f)
                                text("${index + 1}. ${hit.stage.title}")
                                fontSizeScaled(12f)
                                color(if (hit.covered) props.theme.textPrimary else props.theme.brand)
                            }
                        }
                        Text {
                            attr {
                                text(if (hit.covered) "聊过 ${hit.count} 次" else "缺这一环 ›")
                                fontSizeScaled(10.5f)
                                color(if (hit.covered) props.theme.textTertiary else props.theme.brand)
                            }
                        }
                    }
                    Text {
                        attr {
                            marginTop(3f)
                            text(
                                when {
                                    hit.covered && hit.example.isNotEmpty() -> "如：${hit.example}"
                                    else -> hit.stage.hint
                                },
                            )
                            fontSizeScaled(10.5f)
                            lineHeightScaled(15f)
                            color(props.theme.textTertiary)
                        }
                    }
                }
            }
        }
        if (userMessages.isEmpty()) {
            Text {
                attr {
                    text("先去聊几句——每问到一个环节，这里就会点亮一块。")
                    marginTop(10f)
                    fontSizeScaled(10.5f)
                    color(props.theme.textTertiary)
                }
            }
        }
    }
}

/** 单星成员抽屉：一行摘要（两次点击到详情：星 → 抽屉 → 详情）。 */
private fun ViewContainer<*, *>.renderSkyStarDrawer(props: RiskUiProps) {
    val symbol = props.sky.skySelectedSymbol
    val row = props.data.rows.firstOrNull { it.symbol == symbol } ?: return
    View {
        attr {
            marginTop(10f)
            paddingLeft(12f)
            paddingRight(12f)
            paddingTop(10f)
            paddingBottom(10f)
            borderRadius(12f)
            backgroundColor(props.theme.surfaceMuted)
            flexDirectionRow()
            alignItemsCenter()
        }
        event { click { props.actions.openStockDetail(row.symbol, Routes.RISK) } }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text(row.name)
                    fontSizeScaled(12f)
                    color(props.theme.textPrimary)
                }
            }
            Text {
                attr {
                    text(
                        com.kuikly.stockchat.risk.domain.skyStarSub(
                            row,
                            props.data.industries,
                            props.sky.volRatios,
                            props.data.limitUps,
                            props.data.events,
                        ),
                    )
                    marginTop(2f)
                    fontSizeScaled(10.5f)
                    lineHeightScaled(15f)
                    color(props.theme.textTertiary)
                }
            }
        }
        val pct = row.quote?.changePercent
        Text {
            attr {
                text(pct?.let { Format.percent(it) } ?: "--")
                fontSizeScaled(12f)
                color(if ((pct ?: 0.0) >= 0) props.theme.rise else props.theme.fall)
            }
        }
        Text {
            attr {
                text("进详情 ›")
                marginLeft(10f)
                fontSizeScaled(11f)
                color(props.theme.brand)
            }
        }
    }
}

/** 团域成员抽屉：同链成员 chip 流，点 chip 进详情。 */
private fun ViewContainer<*, *>.renderSkyClusterDrawer(props: RiskUiProps, clusterName: String) {
    val members = props.data.rows.filter {
        (props.data.industries[it.symbol]?.takeIf { n -> n.isNotBlank() } ?: "未分类") == clusterName
    }
    if (members.isEmpty()) return
    View {
        attr {
            marginTop(10f)
            padding(12f)
            borderRadius(12f)
            backgroundColor(props.theme.surfaceMuted)
        }
        Text {
            attr {
                text("「${clusterName}」的 ${members.size} 只成员")
                fontSizeScaled(11f)
                fontWeightSemiBold()
                color(props.theme.textPrimary)
            }
        }
        View {
            attr { marginTop(8f); flexDirectionRow(); flexWrapWrap() }
            members.forEach { row ->
                View {
                    attr {
                        marginRight(6f)
                        marginBottom(6f)
                        paddingLeft(9f)
                        paddingRight(9f)
                        height(24f)
                        allCenter()
                        borderRadius(8f)
                        backgroundColor(props.theme.surface)
                    }
                    event { click { props.actions.openStockDetail(row.symbol, Routes.RISK) } }
                    Text {
                        attr {
                            text(row.name)
                            fontSizeScaled(10.5f)
                            color(props.theme.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** 引路星解读抽屉：3 行规则事实 + 追问出口（复用对话通路，零 LLM）。 */
private fun ViewContainer<*, *>.renderSkyBeaconDrawer(props: RiskUiProps, chain: ChainConcentration) {
    val total = props.data.rows.size
    View {
        attr {
            marginTop(10f)
            padding(12f)
            borderRadius(12f)
            backgroundColor(props.theme.surfaceMuted)
        }
        Text {
            attr {
                text("为什么圈住这团")
                fontSizeScaled(11f)
                fontWeightSemiBold()
                color(props.theme.textPrimary)
            }
        }
        listOf(
            "「${chain.topName}」${chain.topCount} 只同属一条链，占 ${com.kuikly.stockchat.risk.domain.weightLabel(chain.topCount, total)}（等权估算）",
            "前三大行业合计 ${com.kuikly.stockchat.risk.domain.weightLabel(chain.cr3Count, total)}，是这张图里最挤的一片",
            "行业归属来自公开行业分类 · 非你的真实仓位",
        ).forEach { fact ->
            Text {
                attr {
                    text(fact)
                    marginTop(6f)
                    fontSizeScaled(10.5f)
                    lineHeightScaled(16f)
                    color(props.theme.textSecondary)
                }
            }
        }
        View {
            attr { marginTop(8f) }
            event {
                click {
                    props.actions.openChatWithQuestion(
                        "我的自选里「${chain.topName}」的这几只为什么经常一起涨跌？",
                        "来自风险地图：星团「${chain.topName}」",
                        "",
                    )
                }
            }
            Text {
                attr {
                    text("问一句「为什么经常一起涨跌」 ›")
                    fontSizeScaled(11f)
                    color(props.theme.brand)
                }
            }
        }
    }
}

/** 当前图层下的长按提问条；问句自然，焦点由 route context 传递。 */
private fun ViewContainer<*, *>.renderSkyContextBar(props: RiskUiProps) {
    val symbol = props.sky.skyContextSymbol
    val row = props.data.rows.firstOrNull { it.symbol == symbol } ?: return
    val layerName = props.sky.skyLayer.label
    val questions = listOf(
        "${row.name}今天为什么这样动？",
        "${row.name}和谁牵连最明显？",
        "${row.name}在${layerName}这层说明什么？",
    )
    View {
        attr { marginTop(10f); padding(10f); borderRadius(12f); backgroundColor(props.theme.brandSoft) }
        Text { attr { text("按住「${row.name}」· 想问哪一句？"); fontSizeScaled(10.5f); color(props.theme.brand) } }
        View {
            attr { marginTop(7f); flexDirectionRow(); flexWrapWrap() }
            questions.forEach { question ->
                View {
                    attr {
                        marginRight(6f); marginBottom(5f); paddingLeft(8f); paddingRight(8f); height(25f)
                        allCenter(); borderRadius(8f); backgroundColor(props.theme.surface)
                    }
                    event {
                        click {
                            props.actions.openChatWithQuestion(
                                question,
                                "来自风险地图：星「${row.name}」（${layerName}层）",
                                symbol,
                            )
                        }
                    }
                    Text { attr { text(question); fontSizeScaled(10f); color(props.theme.textSecondary) } }
                }
            }
        }
    }
}
