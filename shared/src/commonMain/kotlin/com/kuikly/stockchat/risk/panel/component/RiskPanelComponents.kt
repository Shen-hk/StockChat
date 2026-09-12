package com.kuikly.stockchat.risk.panel.component

import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.data.provider.CalendarEventKind
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.risk.domain.ChainConcentration
import com.kuikly.stockchat.risk.domain.IndustryStat
import com.kuikly.stockchat.risk.domain.chainConcentration
import com.kuikly.stockchat.risk.domain.coefficientLabel
import com.kuikly.stockchat.risk.domain.dailyReturns
import com.kuikly.stockchat.risk.domain.headline
import com.kuikly.stockchat.risk.domain.industryStats
import com.kuikly.stockchat.risk.domain.pearson
import com.kuikly.stockchat.risk.domain.portfolioStd
import com.kuikly.stockchat.risk.domain.sameDirectionDays
import com.kuikly.stockchat.risk.domain.stdOf
import com.kuikly.stockchat.risk.domain.underperformGapPct
import com.kuikly.stockchat.risk.domain.weightLabel
import com.kuikly.stockchat.risk.state.RiskUiProps
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs

/**
 * v1 六面板列表投影 + z0 氛围底 + 暴露快照 + 归因出口的渲染组件
 * （doc 47 B-3：纯 DSL 随迁，布局/文案/参数逐值保留）。
 * 所有 observable 读取都在 vif/attr 闭包内（AGENTS R1）。
 */

// ── 展示辅助（原 RiskMapPage 私有，随迁到组件域） ──

private const val CORRELATION_WINDOW = 60
private const val MIN_RETURN_DAYS = 30
private const val DAY_MS = 24L * 60 * 60 * 1000

private fun formatSnapshotDate(millis: Long): String {
    // 快照日期仅用于回看定位（都在近 12 周内），天数差粗粒度换算即可。
    val diffDays = ((platformCurrentTimeMillis() - millis) / DAY_MS).toInt()
    return when {
        diffDays <= 0 -> "本周"
        diffDays < 30 -> "$diffDays 天前"
        else -> "${diffDays / 30} 个月前"
    }
}

/** 行业堆叠条三段用中性明度阶梯（风险页不引入新饱和色）。 */
private fun industrySegmentColor(props: RiskUiProps, index: Int): Color = when (index) {
    0 -> props.theme.textTertiary
    1 -> props.theme.flat
    else -> props.theme.divider
}

/** 相关性格子：|r| 四档明度（surfaceMuted→divider→flat→textTertiary），两主题通用。 */
private fun correlationColor(props: RiskUiProps, r: Double): Color {
    val level = abs(r)
    return when {
        level >= 0.7 -> props.theme.textTertiary
        level >= 0.5 -> props.theme.flat
        level >= 0.3 -> props.theme.divider
        else -> props.theme.surfaceMuted
    }
}

// ── v1 六面板列表投影（降级档 3）：行业/集中度/相关性/波动/事件/情绪。 ──

internal fun ViewContainer<*, *>.renderRiskPanels(props: RiskUiProps) {
    val rows = props.data.rows.toList()
    val industry = industryStats(rows, props.data.industries)
    val chain = chainConcentration(rows, industry)

    // 行业未触发单链判定 → 常规面板（z1）。termKey = FR-R6 术语出口。
    vif({ !chain.triggered }) {
        this.renderIndustryContent(props, industry, chain, elevated = false)
    }

    this.renderDimensionPanel(props, "集中度", termKey = "HHI") { body ->
        body.renderConcentration(props)
    }

    this.renderDimensionPanel(props, "相关性", termKey = "CORRELATION") { body ->
        body.renderCorrelation(props)
    }

    this.renderDimensionPanel(props, "波动暴露", termKey = "VOLATILITY") { body ->
        body.renderVolatility(props)
    }

    this.renderDimensionPanel(props, "事件时间轴", termKey = "UNLOCK") { body ->
        body.renderEventTimeline(props)
    }

    this.renderDimensionPanel(props, "情绪暴露", termKey = "SENTIMENT") { body ->
        body.renderSentiment(props)
    }
}

/**
 * z0 氛围底：中性色 + L1 结论 + 常驻口径角标。
 * [withIndustryCard] = v1 模式下行业重叠单链集中时上浮 z3 主卡玻璃（唯一）；
 * 星图模式下 z3 唯一玻璃让位给星图主卡，不再重复上浮。
 */
internal fun ViewContainer<*, *>.renderHeadline(
    props: RiskUiProps,
    industry: List<IndustryStat>,
    chain: ChainConcentration,
    withIndustryCard: Boolean,
) {
    val total = props.data.rows.size
    View {
        attr {
            paddingTop(18f)
            paddingBottom(24f)
            paddingLeft(12f)
            paddingRight(12f)
            borderRadius(20f)
            backgroundColor(props.theme.surfaceMuted)
        }
        Text {
            attr {
                text(headline(props.data.rows.toList(), industry, chain, props.data.indexQuote))
                fontSizeScaled(18f)
                fontWeightSemiBold()
                lineHeightScaled(26f)
                color(props.theme.textPrimary)
            }
        }
        Text {
            attr {
                text("等权估算 · 非真实仓位 · 共 ${total} 只自选")
                marginTop(8f)
                fontSizeScaled(10f)
                color(props.theme.textTertiary)
            }
        }
        vif({ props.data.dataModeLabel.isNotEmpty() }) {
            Text {
                attr {
                    text(props.data.dataModeLabel)
                    marginTop(3f)
                    fontSizeScaled(10f)
                    color(props.theme.textTertiary)
                }
            }
        }

        // ── z3 主卡玻璃（唯一，条件出现）：行业重叠在单链集中时上浮 ──
        if (withIndustryCard) {
            vif({ chain.triggered }) {
                View {
                    attr {
                        marginTop(16f)
                        padding(16f)
                        borderRadius(16f)
                        backgroundColor(props.theme.marketGlass)
                        boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.10f)))
                    }
                    this.renderIndustryContent(props, industry, chain, elevated = true)
                }
            }
        }
    }
}

/** z1 维度面板外壳：弱化底色、12.5f 标题、内容自绘（每个维度的可视化形态都不同）。
 *  FR-R6 术语内联出口：标题对应的术语可点 → 记一笔「遇到」→ 跳知识库。
 *  联动契约 C-2：术语触发统一走 GlossaryStore.encounter，此处不自行记录。 */
internal fun ViewContainer<*, *>.renderDimensionPanel(
    props: RiskUiProps,
    title: String,
    termKey: String = "",
    content: (ViewContainer<*, *>) -> Unit,
) {
    View {
        attr {
            marginTop(10f)
            padding(14f)
            borderRadius(14f)
            backgroundColor(props.theme.surface)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text(title)
                    fontSizeScaled(13.5f)
                    fontWeightSemiBold()
                    color(props.theme.textPrimary)
                }
            }
            vif({ termKey.isNotEmpty() }) {
                View {
                    attr {
                        flex(1f)
                        flexDirectionRow()
                        justifyContentFlexEnd()
                    }
                    View {
                        attr {
                            paddingLeft(8f)
                            paddingRight(8f)
                            paddingTop(3f)
                            paddingBottom(3f)
                            borderRadius(9f)
                            backgroundColor(props.theme.brandSoft)
                        }
                        event {
                            click {
                                props.glossaryEncounter(termKey)
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
            }
        }
        content(this)
    }
}

// ── ① 行业重叠：层叠堆叠条 + 段内成员 chip ──

internal fun ViewContainer<*, *>.renderIndustryContent(
    props: RiskUiProps,
    industry: List<IndustryStat>,
    chain: ChainConcentration,
    elevated: Boolean,
) {
    val total = props.data.rows.size
    if (industry.isEmpty()) {
        Text {
            attr {
                text("行业归属暂不可用（离线或接口失败），其他维度不受影响。")
                marginTop(8f)
                fontSizeScaled(11.5f)
                lineHeightScaled(17f)
                color(props.theme.textTertiary)
            }
        }
        return
    }
    val top = industry.first()
    Text {
        attr {
            text("${top.count} 只同属「${top.name}」，占 ${weightLabel(top.count, total)}")
            marginTop(if (elevated) 4f else 8f)
            fontSizeScaled(12.5f)
            color(props.theme.textSecondary)
        }
    }

    // 层叠堆叠条（不用饼图：≥5 分类不可读且无法承载成员 chip）。
    View {
        attr {
            marginTop(10f)
            height(8f)
            flexDirectionRow()
            borderRadius(4f)
            overflow(true)
        }
        industry.take(3).forEachIndexed { index, stat ->
            View {
                attr {
                    flex(stat.count.toFloat() / total)
                    height(8f)
                    backgroundColor(industrySegmentColor(props, index))
                }
            }
        }
        View {
            attr {
                flex((total - industry.take(3).sumOf { it.count }).toFloat() / total)
                height(8f)
                backgroundColor(props.theme.divider)
            }
        }
    }

    // 段图例 + 段内成员 chip。
    View {
        attr { marginTop(8f); flexDirectionRow(); flexWrapWrap() }
        industry.take(3).forEachIndexed { index, stat ->
            View {
                attr { marginRight(10f); alignItemsCenter(); flexDirectionRow() }
                View {
                    attr {
                        width(7f)
                        height(7f)
                        borderRadius(2f)
                        backgroundColor(industrySegmentColor(props, index))
                    }
                }
                Text {
                    attr {
                        text("${stat.name} ${weightLabel(stat.count, total)}")
                        marginLeft(4f)
                        fontSizeScaled(10f)
                        color(props.theme.textTertiary)
                    }
                }
            }
        }
        Text {
            attr {
                text("其他 ${weightLabel(total - industry.take(3).sumOf { it.count }, total)}")
                fontSizeScaled(10f)
                color(props.theme.textTertiary)
            }
        }
    }
    View {
        attr { marginTop(9f); flexDirectionRow(); flexWrapWrap() }
        top.members.forEach { row ->
            View {
                attr {
                    marginRight(6f)
                    marginBottom(6f)
                    paddingLeft(9f)
                    paddingRight(9f)
                    height(22f)
                    allCenter()
                    borderRadius(7f)
                    backgroundColor(props.theme.surfaceMuted)
                }
                Text {
                    attr {
                        text(row.name)
                        fontSizeScaled(10.5f)
                        color(props.theme.textSecondary)
                    }
                }
                event { click { props.actions.openStockDetail(row.symbol, Routes.RISK) } }
            }
        }
    }

    // AI 触点 #1（条件）：CR3 > 60% 才出现的一行事实注释。
    vif({ chain.cr3 > 0.60 }) {
        this.renderAnnotation(
            props,
            "前三大行业合计 ${weightLabel(chain.cr3Count, total)}——同一变量一波动，这几只常常一起动",
        )
    }
}

// ── ② 集中度：市值加权 Top3 等级条 ──

private fun ViewContainer<*, *>.renderConcentration(props: RiskUiProps) {
    val rows = props.data.rows.toList()
    val totalCap = rows.mapNotNull { it.quote?.marketCap }.filter { it > 0 }.sum()
    val byCap = rows
        .mapNotNull { row -> row.quote?.marketCap?.takeIf { it > 0 }?.let { row.name to it } }
        .sortedByDescending { it.second }
    if (byCap.size < 3 || totalCap <= 0.0) {
        Text {
            attr {
                text("市值数据不足（离线或停牌），暂无法计算集中度。")
                marginTop(8f)
                fontSizeScaled(11.5f)
                color(props.theme.textTertiary)
            }
        }
        return
    }
    val top3 = byCap.take(3)
    val top3Share = top3.sumOf { it.second } / totalCap
    Text {
        attr {
            text("市值加权口径（非你的真实仓位）：Top3 占 ${weightLabel(top3Share)}")
            marginTop(8f)
            fontSizeScaled(11.5f)
            color(props.theme.textSecondary)
        }
    }
    top3.forEach { (name, cap) ->
        val share = cap / totalCap
        View {
            attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text(name)
                    width(64f)
                    fontSizeScaled(11f)
                    color(props.theme.textSecondary)
                }
            }
            View {
                attr { flex(1f); height(6f); borderRadius(3f); backgroundColor(props.theme.surfaceMuted) }
                View {
                    attr {
                        flex(share.toFloat())
                        height(6f)
                        borderRadius(3f)
                        backgroundColor(props.theme.flat)
                    }
                }
            }
            Text {
                attr {
                    text(weightLabel(share))
                    marginLeft(8f)
                    width(38f)
                    fontSizeScaled(10f)
                    color(props.theme.textTertiary)
                }
            }
        }
    }
}

// ── ③ 相关性：N≤12 共现矩阵（明度阶梯），>12 降级 Top5 配对 ──

private fun ViewContainer<*, *>.renderCorrelation(props: RiskUiProps) {
    val rows = props.data.rows.toList()
    val returns = rows.mapNotNull { row ->
        dailyReturns(row.quote)?.let { Triple(row.symbol, row.name, it) }
    }
    val withBars = returns.filter { it.third.size >= MIN_RETURN_DAYS }
    if (withBars.size < 2) {
        Text {
            attr {
                text("日K数据不足（离线或新股），相关性暂不可算。")
                marginTop(8f)
                fontSizeScaled(11.5f)
                color(props.theme.textTertiary)
            }
        }
        return
    }
    if (withBars.size <= 12) {
        renderCorrelationMatrix(props, withBars)
    } else {
        renderTopPairs(props, withBars)
    }
}

private fun ViewContainer<*, *>.renderCorrelationMatrix(
    props: RiskUiProps,
    withBars: List<Triple<String, String, Map<String, Double>>>,
) {
    Text {
        attr {
            text("近 $CORRELATION_WINDOW 个交易日 · 点格子看配对解读 · 色深 = 同涨同跌程度")
            marginTop(8f)
            fontSizeScaled(10.5f)
            color(props.theme.textTertiary)
        }
    }
    withBars.forEach { (rowSymbol, rowName, rowReturns) ->
        View {
            attr { marginTop(4f); flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text(rowName)
                    width(52f)
                    fontSizeScaled(9f)
                    color(props.theme.textTertiary)
                }
            }
            withBars.forEach { (colSymbol, _, colReturns) ->
                val r = if (rowSymbol == colSymbol) 1.0 else pearson(rowReturns, colReturns) ?: 0.0
                View {
                    attr {
                        width(20f)
                        height(20f)
                        marginLeft(1.5f)
                        marginRight(1.5f)
                        borderRadius(4f)
                        backgroundColor(correlationColor(props, r))
                    }
                    event {
                        click {
                            props.alert.togglePair(if (rowSymbol == colSymbol) "" else "$rowSymbol|$colSymbol")
                        }
                    }
                }
            }
        }
    }
    // 选中格子的配对解读（S 层：一次点击获得两票关系，不跳页）。
    vif({ props.alert.selectedPair.isNotEmpty() }) {
        val (a, b) = props.alert.selectedPair.split("|")
        val rowA = withBars.firstOrNull { it.first == a }
        val rowB = withBars.firstOrNull { it.first == b }
        vif({ rowA != null && rowB != null }) {
            val r = pearson(rowA!!.third, rowB!!.third)
            val sameDirection = sameDirectionDays(rowA.third, rowB.third)
            View {
                attr {
                    marginTop(10f)
                    padding(10f)
                    borderRadius(10f)
                    backgroundColor(props.theme.surfaceMuted)
                }
                Text {
                    attr {
                        text("${rowA.second} ↔ ${rowB.second}：相关系数 ${coefficientLabel(r)}，同期 ${sameDirection} 天同向")
                        fontSizeScaled(11.5f)
                        lineHeightScaled(17f)
                        color(props.theme.textSecondary)
                    }
                }
                Text {
                    attr {
                        text("在知识库查看「相关系数」是什么意思 ›")
                        marginTop(6f)
                        fontSizeScaled(10.5f)
                        color(props.theme.brand)
                    }
                }
                event { click { props.actions.openPage(Routes.GLOSSARY) } }
            }
        }
    }
}

private fun ViewContainer<*, *>.renderTopPairs(
    props: RiskUiProps,
    withBars: List<Triple<String, String, Map<String, Double>>>,
) {
    val pairs = buildList {
        withBars.forEachIndexed { i, a ->
            withBars.drop(i + 1).forEach { b ->
                pearson(a.third, b.third)?.let { r ->
                    add(Triple(a.second to b.second, r, sameDirectionDays(a.third, b.third)))
                }
            }
        }
    }.sortedByDescending { abs(it.second) }.take(5)
    Text {
        attr {
            text("标的数超过 12，降级为同涨同跌程度最高的 5 组配对")
            marginTop(8f)
            fontSizeScaled(10.5f)
            color(props.theme.textTertiary)
        }
    }
    pairs.forEach { (names, r, sameDays) ->
        View {
            attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("${names.first} ↔ ${names.second}")
                    fontSizeScaled(11f)
                    color(props.theme.textSecondary)
                }
            }
            View { attr { flex(1f) } }
            Text {
                attr {
                    text("${coefficientLabel(r)} · ${sameDays} 天同向")
                    fontSizeScaled(10f)
                    color(props.theme.textTertiary)
                }
            }
        }
    }
}

// ── ④ 波动暴露：组合 vs 大盘同轴双条 ──

private fun ViewContainer<*, *>.renderVolatility(props: RiskUiProps) {
    val rows = props.data.rows.toList()
    val index = dailyReturns(props.data.indexQuote)
    val portfolioStd = portfolioStd(rows)
    val indexStd = index?.let { stdOf(it.values.toList()) }
    if (portfolioStd == null || indexStd == null || indexStd <= 0.0) {
        Text {
            attr {
                text("日K数据不足，组合与大盘的波动对比暂不可算。")
                marginTop(8f)
                fontSizeScaled(11.5f)
                color(props.theme.textTertiary)
            }
        }
        return
    }
    val ratio = portfolioStd / indexStd
    val maxStd = maxOf(portfolioStd, indexStd)
    fun barRow(label: String, std: Double) {
        View {
            attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text(label)
                    width(84f)
                    fontSizeScaled(11f)
                    color(props.theme.textSecondary)
                }
            }
            View {
                attr { flex(1f); height(8f); borderRadius(4f); backgroundColor(props.theme.surfaceMuted) }
                View {
                    attr {
                        flex((std / maxStd).toFloat())
                        height(8f)
                        borderRadius(4f)
                        backgroundColor(props.theme.flat)
                    }
                }
            }
            Text {
                attr {
                    text("${Format.price(std * 100)}%")
                    marginLeft(8f)
                    width(46f)
                    fontSizeScaled(10f)
                    color(props.theme.textTertiary)
                }
            }
        }
    }
    Text {
        attr {
            text("近 $CORRELATION_WINDOW 个交易日 · 日收益率标准差（等权）")
            marginTop(8f)
            fontSizeScaled(10.5f)
            color(props.theme.textTertiary)
        }
    }
    barRow("你的自选（等权）", portfolioStd)
    barRow("上证指数", indexStd)
    // AI 触点 #2（条件）：比值 > 1.5 才出现。
    vif({ ratio > 1.5 }) {
        this.renderAnnotation(
            props,
            "自选组合的日波动约为大盘的 ${Format.price(ratio)} 倍——涨的时候更快，跌的时候也更急",
        )
    }
}

// ── ⑤ 事件时间轴：竖向时间线，非列表 ──

private fun ViewContainer<*, *>.renderEventTimeline(props: RiskUiProps) {
    val upcoming = props.data.events
    if (upcoming.isEmpty()) {
        Text {
            attr {
                text("自选标的未来没有已预约的披露事件。")
                marginTop(8f)
                fontSizeScaled(11.5f)
                color(props.theme.textTertiary)
            }
        }
        return
    }
    upcoming.forEach { event ->
        // doc 30：每行右端动作。IPO 不产生消息（与 AlertInboxBuilder 同口径），不提供「转预警」。
        val eventId = "EVENT:${event.symbol}:${event.date}"
        val convertible = event.kind != CalendarEventKind.IPO
        View {
            attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text(event.date.substring(5))
                    width(44f)
                    fontSizeScaled(10f)
                    color(props.theme.textTertiary)
                }
            }
            View {
                attr { alignItemsCenter(); width(10f) }
                View {
                    attr {
                        width(6f)
                        height(6f)
                        borderRadius(3f)
                        backgroundColor(props.theme.flat)
                    }
                }
            }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text("${event.name} · ${event.kind.label}")
                        fontSizeScaled(11.5f)
                        color(props.theme.textSecondary)
                    }
                }
                Text {
                    attr {
                        text(event.title)
                        marginTop(2f)
                        fontSizeScaled(10.5f)
                        color(props.theme.textTertiary)
                    }
                }
                event { click { props.actions.openStockDetail(event.symbol, Routes.RISK) } }
            }
            // 右端动作：未转→「转预警 ›」，已转→「已在收件箱 ✓」（doc 30）。
            vif({ convertible }) {
                vif({ !props.alert.convertedEventIds.contains(eventId) }) {
                    View {
                        attr {
                            marginLeft(8f)
                            paddingLeft(8f)
                            paddingRight(8f)
                            height(24f)
                            allCenter()
                            borderRadius(8f)
                            backgroundColor(props.theme.brandSoft)
                        }
                        Text {
                            attr {
                                text("转预警 ›")
                                fontSizeScaled(11f)
                                color(props.theme.brand)
                            }
                        }
                        event {
                            click {
                                props.alert.convertEventToInbox(event, eventId)
                            }
                        }
                    }
                }
                vif({ props.alert.convertedEventIds.contains(eventId) }) {
                    Text {
                        attr {
                            text("已在收件箱 ✓")
                            marginLeft(8f)
                            fontSizeScaled(10f)
                            color(props.theme.textTertiary)
                        }
                    }
                }
            }
        }
    }
    // AI 触点 #3（条件）：未来存在解禁/减持类事件才出现。
    vif({ upcoming.any { it.kind == CalendarEventKind.UNLOCK } }) {
        this.renderAnnotation(
            props,
            "未来 30 天内自选有解禁安排——解禁不等于下跌，但意味着可流通筹码增加",
        )
    }
    // doc 30：转预警成功后的瞬时提示（brand 色，2.5s 后清空）。
    vif({ props.alert.eventToInboxHint.isNotEmpty() }) {
        Text {
            attr {
                text(props.alert.eventToInboxHint)
                marginTop(10f)
                fontSizeScaled(10.5f)
                color(props.theme.brand)
            }
        }
    }
}

// ── ⑥ 情绪暴露：连板梯队成员 ──

private fun ViewContainer<*, *>.renderSentiment(props: RiskUiProps) {
    val members = props.data.limitUps
    if (members.isEmpty()) {
        Text {
            attr {
                text("今日自选中没有涨停标的，情绪暴露不明显。")
                marginTop(8f)
                fontSizeScaled(11.5f)
                color(props.theme.textTertiary)
            }
        }
        return
    }
    Text {
        attr {
            text("自选中有 ${members.size} 只在今日涨停池")
            marginTop(8f)
            fontSizeScaled(11.5f)
            color(props.theme.textSecondary)
        }
    }
    members.forEach { member ->
        View {
            attr {
                marginTop(8f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(10f)
                paddingRight(10f)
                height(30f)
                borderRadius(9f)
                backgroundColor(props.theme.surfaceMuted)
            }
            Text {
                attr {
                    text(member.second.name)
                    fontSizeScaled(11.5f)
                    color(props.theme.textSecondary)
                }
            }
            Text {
                attr {
                    text("${member.first.consecutiveBoards} 连板 · ${member.first.sector}")
                    marginLeft(8f)
                    fontSizeScaled(10f)
                    color(props.theme.textTertiary)
                }
            }
            event { click { props.actions.openStockDetail(member.second.symbol, Routes.RISK) } }
        }
    }
}

/** 条件触发的一行事实注释：品牌蓝左侧 2px 竖线，≤28 字，不展开、不放按钮。 */
internal fun ViewContainer<*, *>.renderAnnotation(props: RiskUiProps, text: String) {
    View {
        attr {
            marginTop(10f)
            flexDirectionRow()
        }
        View {
            attr {
                width(2f)
                borderRadius(1f)
                backgroundColor(props.theme.brand)
            }
        }
        Text {
            attr {
                text(text)
                marginLeft(8f)
                fontSizeScaled(10.5f)
                lineHeightScaled(16f)
                color(props.theme.textSecondary)
            }
        }
    }
}

/** FR-R10：快照历史面板。竖向时间线形态（与事件时间轴同构），≥2 条才显示。 */
internal fun ViewContainer<*, *>.renderSnapshotHistory(props: RiskUiProps) {
    val snapshots = props.snapshotHistory()
    if (snapshots.size < 2) return
    // doc 30：最近两次快照的 CR3 变化，决定是否展示「生成暴露变化预警」入口。
    val ascSnapshots = snapshots.sortedBy { it.capturedAtMillis }
    val olderSnap = ascSnapshots[ascSnapshots.lastIndex - 1]
    val newerSnap = ascSnapshots.last()
    val deltaCr3 = abs(newerSnap.cr3Percent - olderSnap.cr3Percent)
    val exposureId = "EXPOSURE:${newerSnap.capturedAtMillis}"
    View {
        attr {
            marginTop(10f)
            padding(14f)
            borderRadius(14f)
            backgroundColor(props.theme.surface)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("暴露变化")
                    fontSizeScaled(13.5f)
                    fontWeightSemiBold()
                    color(props.theme.textPrimary)
                }
            }
            Text {
                attr {
                    flex(1f)
                    text("每周自动留存一次")
                    textAlignRight()
                    fontSizeScaled(10f)
                    color(props.theme.textTertiary)
                }
            }
        }
        snapshots.takeLast(6).reversed().forEach { snapshot ->
            View {
                attr { marginTop(10f); flexDirectionRow() }
                Text {
                    attr {
                        text(formatSnapshotDate(snapshot.capturedAtMillis))
                        width(64f)
                        fontSizeScaled(10f)
                        color(props.theme.textTertiary)
                    }
                }
                View {
                    attr { alignItemsCenter(); width(10f) }
                    View {
                        attr {
                            width(6f)
                            height(6f)
                            borderRadius(3f)
                            backgroundColor(props.theme.flat)
                        }
                    }
                }
                Text {
                    attr {
                        flex(1f)
                        text(
                            "${snapshot.memberCount} 只 · ${snapshot.topIndustry} ${snapshot.topIndustryCount} 只 · " +
                                "CR3 ${snapshot.cr3Percent}% · ${snapshot.volRatioLabel()}",
                        )
                        fontSizeScaled(11f)
                        lineHeightScaled(16f)
                        color(props.theme.textSecondary)
                    }
                }
            }
        }
    }
    // doc 30：暴露变化预警入口（仅当最近两次快照 |ΔCR3| ≥ 10 个百分点）。
    vif({ deltaCr3 >= 10 }) {
        vif({ !props.alert.generatedExposureIds.contains(exposureId) }) {
            View {
                attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("对比上次 · 生成暴露变化预警 ›")
                        flex(1f)
                        fontSizeScaled(11f)
                        color(props.theme.brand) // EXPOSURE 用品牌蓝语境，不用涨跌色
                    }
                }
                event { click { props.alert.generateExposureAlert(olderSnap, newerSnap, exposureId) } }
            }
        }
        vif({ props.alert.generatedExposureIds.contains(exposureId) }) {
            View {
                attr { marginTop(10f); flexDirectionRow() }
                Text {
                    attr {
                        text("已生成 ✓")
                        fontSizeScaled(10f)
                        color(props.theme.textTertiary)
                    }
                }
            }
        }
    }
}

// ── FR-R9 跑输大盘归因通路 ──

/** 出口行：事实差值 + 提问出口。归因由对话侧 attribution 卡片完成（复用，不新造）。 */
internal fun ViewContainer<*, *>.renderAttributionEntry(props: RiskUiProps) {
    val gap = underperformGapPct(props.data.rows.toList(), props.data.indexQuote) ?: return
    View {
        attr {
            marginTop(10f)
            paddingLeft(14f)
            paddingRight(14f)
            paddingTop(12f)
            paddingBottom(12f)
            borderRadius(12f)
            backgroundColor(props.theme.surface)
            flexDirectionRow()
            alignItemsCenter()
        }
        event { click { props.actions.openChatWithQuestion("我的自选今天为什么跌得比大盘多？", "", "") } }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text("今天比大盘多跌 ${Format.price(-gap)}%")
                    fontSizeScaled(12.5f)
                    color(props.theme.textSecondary)
                }
            }
            Text {
                attr {
                    text("问一句「为什么」，看逐项归因 ›")
                    marginTop(3f)
                    fontSizeScaled(10.5f)
                    color(props.theme.brand)
                }
            }
        }
        Text {
            attr {
                text("去问")
                fontSizeScaled(11.5f)
                color(props.theme.brand)
            }
        }
    }
}
