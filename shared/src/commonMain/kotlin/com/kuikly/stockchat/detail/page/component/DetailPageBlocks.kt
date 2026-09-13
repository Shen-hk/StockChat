package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled

import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.core.AttributionCardModel
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.core.FundFlowCardModel
import com.kuikly.stockchat.cards.core.FinancialCardModel
import com.kuikly.stockchat.cards.core.ShareholderCardModel
import com.kuikly.stockchat.cards.core.BillboardCardModel
import com.kuikly.stockchat.cards.core.CorporateActionCardModel
import com.kuikly.stockchat.cards.component.CardShell
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chart.model.ChartViewportAction
import com.kuikly.stockchat.foundation.design.GlassRenderer
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.RatingSpectrum
import kotlin.math.PI
// doc 29 集成：共享基建 + 板块组件（事件回调经这些基建接线）
import com.kuikly.stockchat.detail.domain.CardFootnote
import com.kuikly.stockchat.detail.domain.ContextChip
import com.kuikly.stockchat.detail.domain.ContextChipStore
import com.kuikly.stockchat.detail.domain.DetailCompanyProfile
import com.kuikly.stockchat.detail.domain.DetailMetric
import com.kuikly.stockchat.detail.domain.Materiality
import com.kuikly.stockchat.detail.domain.materialityOf
import com.kuikly.stockchat.detail.domain.pickPinnedCard
import com.kuikly.stockchat.detail.domain.FactorSpec
import com.kuikly.stockchat.detail.page.component.BalanceSegment
import com.kuikly.stockchat.detail.page.component.BlockState
import com.kuikly.stockchat.detail.page.component.FactorReplayBlock
import com.kuikly.stockchat.detail.page.component.IndustryCompareOverlay
import com.kuikly.stockchat.detail.page.component.MaterialityBadge
import com.kuikly.stockchat.foundation.ui.icon.LineIconPlus
import com.kuikly.stockchat.foundation.ui.icon.LineIconMinus
import com.kuikly.stockchat.foundation.ui.icon.LineIconArrowLeft
import com.kuikly.stockchat.foundation.ui.icon.LineIconArrowRight
import com.kuikly.stockchat.foundation.ui.icon.LineIconReset
import com.kuikly.stockchat.detail.ai.state.AiForecastReason
import com.kuikly.stockchat.detail.ai.state.AiTrendForecast
import com.kuikly.stockchat.detail.ai.state.DetailForecastState
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextAlign
import com.tencent.kuikly.core.views.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

private data class TrendOutlook(
    val title: String,
    val detail: String,
    val confidence: String,
    val color: Color,
)

internal data class BusinessInsightItem(
    val id: String,
    val label: String,
    val model: CardModel,
    // doc 29 E1：今日相关置顶卡（至多一张，构建处经 pickPinnedCard 仲裁）
    val pinned: Boolean = false,
    // doc 29 E2：卡内 AI 注脚（无真实输入时为 null，不显示——不伪造数据）
    val footnote: CardFootnote? = null,
)

/**
 * 行业数据与公司介绍的共享卡。横向滑动仅在横向位移明显大于纵向位移时接管，
 * 所以正常纵向浏览仍交给详情页 Scroller；标签点击为无手势偏好的等价入口。
 */
internal fun ViewContainer<*, *>.CompanyIndustryPanel(
    selectedTab: () -> Int,
    profile: () -> DetailCompanyProfile,
    quote: () -> Quote,
    theme: StockChatTheme,
    reduceMotion: Boolean,
    tabTrackWidth: Float,
    onSelectTab: (Int) -> Unit,
) {
    var downX = 0f
    var downY = 0f
    var axis = 0 // 0 未仲裁；1 横向切换；2 纵向滚动
    var gestureDone = false

    SectionLabel("公司介绍 / 公司数据", theme, strong = true)
    View {
        attr {
            marginTop(8f)
            padding(12f)
            borderRadius(14f)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            touchEnable(true)
        }
        View {
            attr {
                height(30f)
                padding(3f)
                flexDirectionRow()
                borderRadius(8f)
                backgroundColor(theme.surfaceMuted)
            }
            // 单一滑块置于标签下方；按 tab 驱动横向平移，而非两枚独立按钮各自变底色。
            View {
                attr {
                    val tab = selectedTab()
                    absolutePosition(top = 3f, left = 3f)
                    width(((tabTrackWidth - 6f) / 2f).coerceAtLeast(48f))
                    height(24f)
                    borderRadius(6f)
                    backgroundColor(theme.surface)
                    boxShadow(BoxShadow(0f, 1f, 4f, theme.textPrimary.opacity(0.08f)))
                    transform(translate = Translate(0f, 0f, offsetX = if (tab == 0) 0f else ((tabTrackWidth - 6f) / 2f).coerceAtLeast(48f)))
                    // R5：每轮都为下次 tab 改变登记滑块动画。
                    if (!reduceMotion) animate(Animation.easeOut(0.20f), tab)
                }
            }
            listOf("公司介绍", "公司数据").forEachIndexed { index, label ->
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        borderRadius(6f)
                        val active = selectedTab() == index
                        backgroundColor(Color.TRANSPARENT)
                        if (!reduceMotion) animate(Animation.easeOut(0.20f), active)
                    }
                    Text {
                        attr {
                            val active = selectedTab() == index
                            text(label)
                            fontSizeScaled(10f)
                            fontWeightSemiBold()
                            color(if (active) theme.brand else theme.textTertiary)
                            if (!reduceMotion) animate(Animation.easeOut(0.18f), active)
                        }
                    }
                    event { click { onSelectTab(index) } }
                }
            }
        }

        // vbind 负责卸载上一面、挂载下一面；内部两帧进入避免新挂载内容首帧跳变。
        vbind({ selectedTab() }) {
            if (selectedTab() == 1) {
                CompanyIndustryPanelEntrance(reduceMotion) {
                    View {
                        attr { marginTop(12f) }
                        Text {
                            attr {
                                text("公司数据")
                                fontSizeScaled(14f)
                                fontWeightSemiBold()
                                color(theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("财务、股东户数、分红与解禁、龙虎榜和资金流将在下方依次展开。")
                                marginTop(5f)
                                fontSizeScaled(11f)
                                lineHeightScaled(16f)
                                color(theme.textSecondary)
                            }
                        }
                    }
                }
            } else {
                CompanyIndustryPanelEntrance(reduceMotion) {
                    vbind({ profile() to quote() }) {
                        val company = profile()
                        val currentQuote = quote()
                        View {
                            attr { marginTop(12f) }
                            Text {
                                attr {
                                    text("${currentQuote.name} · 公司简介")
                                    fontSizeScaled(14f)
                                    fontWeightSemiBold()
                                    color(theme.textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    // 这里只保留一段摘要，详细事实仍以各业务数据卡与公告为准。
                                    text(company.summary ?: "公司简介暂未接入；可切到「公司数据」查看已接入的财务与公告相关数据。")
                                    marginTop(5f)
                                    fontSizeScaled(11f)
                                    lineHeightScaled(16f)
                                    color(theme.textSecondary)
                                }
                            }
                            if (company.tags.isNotEmpty()) {
                                View {
                                    attr { marginTop(9f); flexDirectionRow() }
                                    company.tags.forEach { tag ->
                                        View {
                                            attr {
                                                marginRight(6f)
                                                padding(4f)
                                                borderRadius(7f)
                                                backgroundColor(theme.brandSoft)
                                            }
                                            Text {
                                                attr {
                                                    text("# $tag")
                                                    fontSizeScaled(9.5f)
                                                    color(theme.brand)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            company.focus?.let { focus ->
                                Text {
                                    attr {
                                        text("阅读线索：$focus")
                                        marginTop(9f)
                                        fontSizeScaled(9.5f)
                                        lineHeightScaled(14f)
                                        color(theme.textTertiary)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text("切到「公司数据」：财务 · 股东户数 · 分红与解禁 · 龙虎榜")
                                    marginTop(8f)
                                    fontSizeScaled(9.5f)
                                    color(theme.brand)
                                }
                            }
                        }
                    }
                }
            }
        }

        event {
            touchDown { event ->
                downX = event.x
                downY = event.y
                axis = 0
                gestureDone = false
            }
            touchMove { event ->
                if (gestureDone || axis == 2) return@touchMove
                val dx = event.x - downX
                val dy = event.y - downY
                if (axis == 0) {
                    if (kotlin.math.abs(dx) < 10f && kotlin.math.abs(dy) < 10f) return@touchMove
                    axis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else 2
                }
            }
            touchUp { event ->
                if (gestureDone) return@touchUp
                gestureDone = true
                if (axis == 1 && kotlin.math.abs(event.x - downX) >= 28f) {
                    onSelectTab(if (event.x < downX) 1 else 0)
                }
            }
            touchCancel { _ -> gestureDone = true }
        }
    }
}

/** 新挂载的 tab 内容用两帧淡入上移；R5 中每帧都登记同一动画供下轮翻转消费。 */
private fun ViewContainer<*, *>.CompanyIndustryPanelEntrance(
    reduceMotion: Boolean,
    content: ViewContainer<*, *>.() -> Unit,
) {
    val presented = BlockState(reduceMotion)
    if (!reduceMotion) setTimeout(0) { presented.value = true }
    View {
        attr {
            val visible = presented.value
            opacity(if (visible) 1f else 0f)
            transform(Translate(if (visible) 0f else 0.08f, 0f))
            if (!reduceMotion) animate(Animation.easeOut(0.18f), visible)
        }
        content()
    }
}

internal fun ViewContainer<*, *>.SectionLabel(
    text: String,
    theme: StockChatTheme,
    // true = 章节级节头（原型 .sec-head .t：12px/800/主文字色）；默认弱样式仅用于
    // bento 卡标题（原型 .bcard .k 10px/text3 的就近映射）。
    strong: Boolean = false,
    // true = AI 可交互节头（原型 .ai-head .t：brand 强调色，doc29 协调规则②）
    accent: Boolean = false,
) {
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text(text)
            when {
                accent -> {
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.brand)
                }
                strong -> {
                    fontSizeScaled(12f)
                    fontWeightBold()
                    color(theme.textPrimary)
                }
                else -> {
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.textTertiary)
                }
            }
        }
    }
}

/**
 * 业务数据节头（原型 .sec-head）：标题 + 右侧 brand 提示。
 * E1 为自动置顶（无 FLIP 重放，见 doc 29 §9 有意偏差），提示用陈述文案、不做假按钮。
 */
internal fun ViewContainer<*, *>.TickerText(
    // 可变状态一律传 lambda：observable 读取延迟到 attr/vif 闭包内（R1），
    // 行情 tick 时文本/颜色随 attr 重跑刷新，lift 动画才有驱动 key（R2）。
    text: () -> String,
    previousText: () -> String,
    loading: () -> Boolean,
    fontSize: Float,
    // 宽度同样走 lambda：涨跌胶囊宽度随文本长度变化（attr 内读取，随 quote 刷新）
    width: () -> Float,
    color: () -> Color,
    theme: StockChatTheme,
    lift: () -> Boolean,
    directionUp: () -> Boolean,
    reduceMotion: Boolean,
) {
    vif({ loading() }) {
        View {
            attr {
                width(width())
                height(fontSize * 0.72f)
                borderRadius(6f)
                backgroundColor(theme.surfaceMuted)
            }
        }
    }
    vif({ !loading() }) {
    View {
        attr {
            width(width())
            height(fontSize * 1.12f)
            overflow(true)
        }
        vif({ !reduceMotion && lift() && previousText().isNotEmpty() }) {
            Text {
                attr {
                    absolutePosition(top = 0f, left = 0f)
                    text(previousText())
                    fontSize(fontSize)
                    fontWeightBold()
                    color(color().opacity(0.72f))
                    transform(Translate(0f, if (directionUp()) -0.86f else 0.86f))
                    opacity(0f)
                    animate(Animation.springEaseOut(0.30f, 0.78f, 0.18f), lift())
                }
            }
        }
        Text {
            attr {
                absolutePosition(top = 0f, left = 0f)
                text(text())
                fontSize(fontSize)
                fontWeightBold()
                color(color())
                opacity(if (lift()) 0.18f else 1f)
                if (!reduceMotion) {
                    transform(Translate(0f, if (lift()) {
                        if (directionUp()) 0.72f else -0.72f
                    } else {
                        0f
                    }))
                    animate(Animation.springEaseOut(0.32f, 0.80f, 0.16f), lift())
                }
            }
        }
        vif({ !reduceMotion && lift() }) {
            View {
                attr {
                    absolutePosition(left = 0f, right = 0f, bottom = 0f)
                    height(1f)
                    backgroundColor(color().opacity(0.24f))
                    opacity(0.6f)
                    animate(Animation.easeOut(0.20f), lift())
                }
            }
        }
    }
    }
}

private fun ViewContainer<*, *>.FocusHairline(
    visible: () -> Boolean,
    theme: StockChatTheme,
    reduceMotion: Boolean,
) {
    View {
        attr {
            absolutePositionAllZero()
            borderRadius(theme.inputRadius)
            border(Border(1.5f, BorderStyle.SOLID, if (visible()) theme.brand.opacity(0.50f) else theme.brand.opacity(0f)))
            touchEnable(false)
            if (!reduceMotion) {
                opacity(if (visible()) 1f else 0f)
                animate(Animation.easeOut(0.20f), visible())
            }
        }
    }
}

/** 走势卡头图例：价格（实色）/ 均价（虚线）/ 昨收（虚线弱化）。 */
internal fun ViewContainer<*, *>.ChartLegend(theme: StockChatTheme, tone: () -> Color) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); touchEnable(false) }
        LegendItem(theme, tone, "价格", dashed = false)
        LegendItem(theme, { theme.textSecondary }, "均价", dashed = true)
        LegendItem(theme, { theme.textTertiary }, "昨收", dashed = true)
    }
}

private fun ViewContainer<*, *>.LegendItem(theme: StockChatTheme, color: () -> Color, label: String, dashed: Boolean) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); marginLeft(if (label == "价格") 0f else 8f) }
        View {
            attr {
                width(10f)
                if (dashed) {
                    height(0f)
                    borderBottom(Border(1.5f, BorderStyle.DASHED, color()))
                } else {
                    height(2.4f)
                    borderRadius(1.2f)
                    backgroundColor(color())
                }
                touchEnable(false)
            }
        }
        Text {
            attr {
                text(label)
                marginLeft(3f)
                fontSizeScaled(9f)
                color(theme.textTertiary)
            }
        }
    }
}

/** 次级指标行：无分隔线的轻量 label-value 列（doc 26 §6）。 */
internal fun ViewContainer<*, *>.SecondaryMetricRow(
    items: List<DetailMetric>,
    theme: StockChatTheme,
    marginTop: Float = 0f,
    // doc 29 ③：单元格长按 400ms（U5）→ 抓取为上下文 chip
    onGrabCell: ((DetailMetric) -> Unit)? = null,
) {
    View {
        attr { flexDirectionRow(); marginTop(marginTop) }
        items.forEach { item ->
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(item.label)
                        fontSizeScaled(9.5f)
                        color(theme.textTertiary)
                    }
                }
                Text {
                    attr {
                        text(item.value)
                        marginTop(2f)
                        fontSizeScaled(12f)
                        fontWeightMedium()
                        color(item.valueColor ?: theme.textSecondary)
                    }
                }
                if (onGrabCell != null) {
                    event { longPress { onGrabCell.invoke(item) } }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.BusinessInsightGrid(
    items: List<BusinessInsightItem>,
    context: CardContext,
    theme: StockChatTheme,
    wide: Boolean,
    // 全页阶梯入场的起始序号：走势卡 0 / 指标板 1 / AI 归因行 2 之后接续，
    // 业务卡数量异步到达会变化，body 重跑时按当帧 size 顺延即可。
    baseIndex: Int,
    // 传 lambda 而非 Boolean：闭包实参是建视图时的首帧快照（R1），
    // observable 的读取必须延迟到 RevealBlock 的 attr 闭包内才建立依赖。
    entranceVisible: () -> Boolean,
    reduceMotion: Boolean,
    // doc 29 E2：注脚点击 → 页面展示判定依据（U3）
    onFootnoteClick: (CardFootnote) -> Unit = {},
) {
    if (items.isEmpty()) {
        View {
            attr {
                marginTop(theme.spacing.lg)
                height(74f)
                borderRadius(theme.cardRadius)
                backgroundColor(theme.surfaceMuted)
                allCenter()
            }
            Text {
                attr {
                    text("业务数据正在加载")
                    fontSize(theme.type.label)
                    color(theme.textTertiary)
                }
            }
        }
        return
    }
    // 数据卡的内容高度不一致（资金/财务/股东/龙虎榜/公司行为），固定行的 2×N
    // 网格会在较短卡下方留下突兀空白。改为按预估信息密度自动分配的双列瀑布流，
    // 每一列独立向下排布；仅单卡时保持满宽。
    if (wide || items.size > 1) {
        val columns = balancedBusinessColumns(items)
        View {
            attr {
                marginTop(theme.spacing.md)
                flexDirectionRow()
                alignItemsFlexStart()
            }
            columns.forEachIndexed { columnIndex, column ->
                View {
                    attr {
                        flex(1f)
                        if (columnIndex == 0) marginRight(6f) else marginLeft(6f)
                    }
                    column.forEach { item ->
                        val itemIndex = items.indexOf(item)
                        View {
                            attr { marginTop(if (item == column.first()) 0f else 12f) }
                            RevealBlock(baseIndex + itemIndex, entranceVisible, reduceMotion) {
                                BusinessCardSlot(item, theme, onFootnoteClick, label = item.label, card = {
                                    CardShell(item.model, context, pinnedRing = item.pinned, noTopMargin = true)
                                })
                            }
                        }
                    }
                }
            }
        }
        return
    }
    items.forEachIndexed { index, item ->
        RevealBlock(baseIndex + index, entranceVisible, reduceMotion) {
            BusinessCardSlot(item, theme, onFootnoteClick, label = item.label, card = {
                CardShell(item.model, context, pinnedRing = item.pinned, noTopMargin = true)
            })
        }
    }
}

/** Greedy two-column packing keeps variable-length business cards from reserving blank row space. */
private fun balancedBusinessColumns(items: List<BusinessInsightItem>): List<List<BusinessInsightItem>> {
    val columns = listOf(mutableListOf<BusinessInsightItem>(), mutableListOf<BusinessInsightItem>())
    val loads = floatArrayOf(0f, 0f)
    fun estimatedHeight(item: BusinessInsightItem) = when (item.model) {
        is FinancialCardModel -> 1.15f
        is ShareholderCardModel -> 1.10f
        is BillboardCardModel -> 1.30f
        is CorporateActionCardModel -> 1.45f
        is FundFlowCardModel -> 1.00f
        else -> 1.10f
    }
    items.forEach { item ->
        val target = if (loads[0] <= loads[1]) 0 else 1
        columns[target] += item
        loads[target] += estimatedHeight(item)
    }
    return columns
}

/**
 * doc 29 E1/E2/E3 业务卡槽：置顶卡由 CardShell(pinnedRing) 在卡自身边框上画 brand
 * 描边 + 「今日相关」角标（v1.0 描边画在外层 wrapper 上，与卡片之间隔着标签和
 * 边距，光圈外一圈留白、不贴合，已废弃）；有注脚的卡在卡底加 brand 小字（点击
 * 展示判定依据 + 「· 端侧规则」）；长按 400ms（U5）→ E3 行业对比覆盖层（只读），
 * 松手 2.2s 后弹回。
 */
private fun ViewContainer<*, *>.BusinessCardSlot(
    item: BusinessInsightItem,
    theme: StockChatTheme,
    onFootnoteClick: (CardFootnote) -> Unit,
    label: String,
    card: ViewContainer<*, *>.() -> Unit,
) {
    // E3 覆盖层状态（BlockState：observable 委托仅支持类成员，局部状态收敛）
    val compareShown = BlockState(false)
    var compareHideRevision = 0
    View {
        attr {
            alignSelfStretch()
        }
        // E3 长按行业比：start 挂载覆盖层；end/cancel 起算 2.2s 弹回。
        // 重按会使旧计时失效（revision），长按中不会中途消失。
        event {
            longPress { params ->
                when (params.state) {
                    "start" -> {
                        compareHideRevision++
                        compareShown.value = true
                    }
                    "end", "cancel" -> {
                        val revision = compareHideRevision
                        setTimeout(2200) {
                            if (revision == compareHideRevision) compareShown.value = false
                        }
                    }
                }
            }
        }
        SectionLabel(label, theme)
        // 卡片锚点容器：「今日相关」角标与 E3 覆盖层都以它为定位基准。
        // CardShell 以 noTopMargin/pinnedRing 关掉自身上边距，锚点上边距统一
        // 补回与标签的间距，于是锚点边界 = 卡片边界（置顶与否皆成立）——
        // 覆盖层 absolutePositionAllZero 即与正常卡片同宽、同高、同位。
        View {
            attr {
                alignSelfStretch()
                marginTop(10f)
            }
            card()
            // 「今日相关」角标必须画在 card() 之后（2026-09-09 修复「今日相被盖住」）：
            // 角标 absolutePosition(top=-8f) 骑在卡顶边上，若先于卡片挂载，后画的
            // 卡身背景会把角标下半截（含文字下半）盖住。后画者在上，角标才完整可见。
            vif({ item.pinned }) {
                View {
                    attr {
                        absolutePosition(top = -8f, right = 10f)
                        height(16f)
                        paddingLeft(8f)
                        paddingRight(8f)
                        allCenter()
                        borderRadius(8f)
                        backgroundColor(theme.brand)
                        touchEnable(false)
                    }
                    Text {
                        attr {
                            text("今日相关")
                            fontSizeScaled(9f)
                            fontWeightSemiBold()
                            color(theme.onBrand)
                        }
                    }
                }
            }
            vif({ compareShown.value }) {
                IndustryCompareOverlay(theme, label)
            }
        }
    }
}

internal fun ViewContainer<*, *>.RevealBlock(
    index: Int,
    visible: () -> Boolean,
    reduceMotion: Boolean,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            // 驱动 observable 必须在 attr 闭包内读取（R1），并置于其他读取之后、
            // 紧邻 animate()（R2）。此前以普通 Boolean 快照传入：attr 不重跑、
            // animate() 绑定不到 key，入场链路整体失效。
            //
            // 节奏对齐 ChatScaffolding.QuestionStarterCard（欢迎语四张引导卡）：
            // 上滑 28% 自高 + 淡入，easeOut 0.375s，阶梯延迟 0.08s 起步、步长
            // 0.094s（下一张在前一张进行到 25% 时启动）。
            val shown = visible()
            opacity(if (shown) 1f else 0f)
            if (!reduceMotion) {
                transform(Translate(0f, if (shown) 0f else 0.28f))
                // 无条件注册 easeOut（含未呈现态）：flip 周期消费的正是上一周期
                // 注册的这份动画（R5）。此前 else 分支注册 linear(0)，入场被
                // 消费成 0 时长瞬移——与 CardSheet/ChatScaffolding 同一范式。
                animate(Animation.easeOut(0.375f).delay(0.08f + 0.094f * index), shown)
            }
        }
        content()
    }
}

// 图表导航：单独占一行，留出足够的触控面积，也不会遮住高低点与图例。
internal fun ViewContainer<*, *>.ChartSegment(
    theme: StockChatTheme,
    chartMode: () -> StockChartMode,
    chartPeriod: () -> StockChartPeriod,
    reduceMotion: Boolean,
    onSelect: (StockChartMode, StockChartPeriod) -> Unit,
) {
    val tabs = listOf(
        StockChartMode.TIMELINE to "分时",
        StockChartMode.K_LINE to "日K",
        StockChartMode.K_LINE to "周K",
        StockChartMode.K_LINE to "月K",
    )
    // activeIndex 在闭包内实时派生（R1）：捕获计算结果会让点亮态全部冻结。
    fun activeIdx(): Int = when {
        chartMode() == StockChartMode.TIMELINE -> 0
        chartPeriod() == StockChartPeriod.WEEK -> 2
        chartPeriod() == StockChartPeriod.MONTH -> 3
        else -> 1
    }
    View {
        attr {
            // Match the viewport-control group's height in the shared toolbar.
            marginBottom(0f)
            padding(4f)
            flexDirectionRow()
            alignSelfFlexStart()
            backgroundColor(theme.marketGlass)
            borderRadius(13f)
            border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
        }
        tabs.forEachIndexed { index, (mode, label) ->
            val period = when (index) {
                2 -> StockChartPeriod.WEEK
                3 -> StockChartPeriod.MONTH
                else -> StockChartPeriod.DAY
            }
            fun active(): Boolean = index == activeIdx()
            View {
                attr {
                    width(42f)
                    height(32f)
                    marginRight(if (index < 3) 4f else 0f)
                    allCenter()
                    borderRadius(10f)
                    // 条件属性全量赋值（attr 不设不清）：点亮/熄灭两态都显式给全
                    backgroundColor(if (active()) theme.brandSoft else theme.marketGlass)
                    border(Border(1f, BorderStyle.SOLID, if (active()) theme.brand.opacity(0.34f) else theme.marketGlassEdge))
                    boxShadow(BoxShadow(0f, 2f, 6f, if (active()) theme.brand.opacity(0.12f) else Color(0L)))
                    if (!reduceMotion) animate(Animation.easeOut(0.16f), active())
                }
                Text {
                    attr {
                        text(label)
                        fontSizeScaled(11f)
                        fontWeightSemiBold()
                        color(if (active()) theme.brand else theme.textSecondary)
                    }
                }
                event { click { onSelect(mode, period) } }
            }
        }
    }
}

/** Shared, visible viewport controls for 分时 / 日K / 周K / 月K. */
internal fun ViewContainer<*, *>.ChartViewportControls(
    theme: StockChatTheme,
    onAction: (ChartViewportAction) -> Unit,
) {
    val controls = listOf(
        ChartViewportAction.ZOOM_IN,
        ChartViewportAction.ZOOM_OUT,
        ChartViewportAction.PAN_LEFT,
        ChartViewportAction.PAN_RIGHT,
        ChartViewportAction.RESET,
    )
    View {
        attr {
            padding(4f)
            flexDirectionRow()
            backgroundColor(theme.marketGlass.opacity(0.96f))
            borderRadius(12f)
            border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
            boxShadow(BoxShadow(0f, 3f, 10f, theme.textPrimary.opacity(0.10f)))
        }
        controls.forEachIndexed { index, action ->
            View {
                attr {
                    width(24f)
                    height(30f)
                    allCenter()
                    borderRadius(8f)
                    if (index > 0) marginLeft(2f)
                }
                when (action) {
                    ChartViewportAction.ZOOM_IN -> Text { attr { text("+"); fontSizeScaled(18f); color(theme.textPrimary) } }
                    ChartViewportAction.ZOOM_OUT -> Text { attr { text("−"); fontSizeScaled(18f); color(theme.textPrimary) } }
                    ChartViewportAction.PAN_LEFT -> Text { attr { text("‹"); fontSizeScaled(22f); color(theme.textPrimary) } }
                    ChartViewportAction.PAN_RIGHT -> Text { attr { text("›"); fontSizeScaled(22f); color(theme.textPrimary) } }
                    ChartViewportAction.RESET -> LineIconReset(theme.textPrimary, 15f)
                    ChartViewportAction.NONE -> Unit
                }
                event { click { onAction(action) } }
            }
        }
    }
}

private fun ViewContainer<*, *>.LiveDot(theme: StockChatTheme, pulse: () -> Boolean, reduceMotion: Boolean) {
    View {
        attr {
            marginTop(theme.spacing.sm)
            flexDirectionRow()
            alignItemsCenter()
        }
        View {
            attr {
                size(18f, 18f)
                allCenter()
                marginLeft(-4f)
                marginRight(3f)
            }
            View {
                attr {
                    absolutePosition(top = 1f, left = 1f)
                    val p = pulse()
                    size(if (p) 16f else 10f, if (p) 16f else 10f)
                    borderRadius(if (p) 8f else 5f)
                    backgroundColor(theme.brand.opacity(if (p) 0f else 0.18f))
                    border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(if (p) 0f else 0.28f)))
                    if (!reduceMotion) animate(Animation.linear(0.68f), pulse())
                    touchEnable(false)
                }
            }
            View {
                attr {
                    size(8f, 8f)
                    borderRadius(4f)
                    backgroundColor(theme.brand)
                    opacity(if (pulse()) 0.82f else 1f)
                    boxShadow(BoxShadow(0f, 0f, 8f, theme.brand.opacity(0.24f)))
                    if (!reduceMotion) {
                        transform(scale = if (pulse()) Scale(1.08f, 1.08f) else Scale.DEFAULT)
                        animate(Animation.easeOut(0.34f), pulse())
                    }
                }
            }
        }
        Text {
            attr {
                text("实时同步")
                marginLeft(7f)
                fontSize(theme.type.meta)
                color(theme.textTertiary)
            }
        }
    }
}

internal fun ViewContainer<*, *>.DetailBottomBar(
    theme: StockChatTheme,
    renderer: GlassRenderer,
    bottomInset: Float,
    watchlisted: () -> Boolean,
    feedback: () -> Boolean,
    reduceMotion: Boolean,
    onToggleWatchlist: () -> Unit,
    onBackToChat: () -> Unit,
    onAskAi: () -> Unit,
    // doc 29 ③：抓取上下文 chips（点击移除，去重由 ContextChipStore 负责）
    chips: () -> List<ContextChip> = { emptyList() },
    onRemoveChip: (ContextChip) -> Unit = {},
    // doc 29 ⑤：scrub 停顿预填（灰字展示，与用户手打区分；只预填不发送）
    prefill: () -> String = { "" },
) {
    View {
        attr {
            absolutePosition(
                bottom = 18f + bottomInset,
                left = 14f,
                right = 14f,
            )
        }
        // ③ 抓取 chips 行：vbind 按 chips 列表重建（vif 只判有无，1→2 枚不会重跑内容闭包，
        // 此前第二枚 chip 不显示、删除后残留旧 chip 的 bug 即源于此）
        vbind({ chips() }) {
            if (chips().isNotEmpty()) {
                View {
                    attr {
                        marginBottom(6f)
                        paddingLeft(4f)
                        paddingRight(4f)
                        flexDirectionRow()
                        flexWrapWrap()
                    }
                    chips().forEach { chip ->
                        View {
                            attr {
                                marginRight(6f)
                                marginBottom(4f)
                                paddingLeft(10f)
                                paddingRight(10f)
                                height(28f)
                                allCenter()
                                borderRadius(14f)
                                backgroundColor(theme.surface)
                                border(Border(1f, BorderStyle.SOLID, theme.brand.opacity(0.6f)))
                            }
                            Text {
                                attr {
                                    text("${chip.label} ${chip.value} ×")
                                    fontSize(theme.type.meta)
                                    fontWeightMedium()
                                    color(theme.brand)
                                }
                            }
                            event { click { onRemoveChip(chip) } }
                        }
                    }
                }
            }
        }
        // ⑤ 预填灰字行（区别手打：surfaceMuted 底 + 三级灰字 + 「预填」前缀）
        vif({ prefill().isNotEmpty() }) {
            View {
                attr {
                    marginBottom(6f)
                    alignSelfFlexStart()
                    paddingLeft(10f)
                    paddingRight(10f)
                    paddingTop(5f)
                    paddingBottom(5f)
                    borderRadius(12f)
                    backgroundColor(theme.surfaceMuted)
                    border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                }
                Text {
                    attr {
                        text("预填 · ${prefill()}")
                        fontSize(theme.type.meta)
                        color(theme.textTertiary)
                    }
                }
            }
        }
        // 原型同款单一输入栏：自选由顶栏 + 管理，底栏只承担“带上下文问 AI”。
        // 旧版三枚操作按钮重复了页面已有入口，也把视觉重心从图表拉走。
        View {
            attr {
                height(52f)
                padding(4f)
                borderRadius(25f)
                flexDirectionRow()
                alignItemsCenter()
                backgroundColor(theme.surface)
                border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                // 2026-09-10 用户反馈：底栏输入框阴影太浅，加深（0.16→0.32，位移/模糊同步加大）。
                boxShadow(BoxShadow(0f, 12f, 32f, theme.textPrimary.opacity(0.32f)))
            }
            View {
                attr {
                    flex(1f)
                    height(44f)
                    paddingLeft(12f)
                    justifyContentCenter()
                }
                Text {
                    attr {
                        text(if (prefill().isNotEmpty()) prefill() else "问点什么…（图表上停顿试试）")
                        fontSize(theme.type.label)
                        fontWeightMedium()
                        color(if (prefill().isNotEmpty()) theme.brand else theme.textTertiary)
                    }
                }
                event { click { onAskAi() } }
            }
            View {
                attr {
                    size(42f, 42f)
                    allCenter()
                    borderRadius(21f)
                    backgroundColor(theme.brand)
                    boxShadow(BoxShadow(0f, 4f, 12f, theme.brand.opacity(0.35f)))
                }
                Text { attr { text("↑"); fontSizeScaled(18f); fontWeightSemiBold(); color(theme.onBrand) } }
                event { click { onAskAi() } }
            }
        }
    }
}

/** ⋯ 更多操作菜单条目（2026-09-10）：纯文字行，整行 44dp 触控。 */
private fun ViewContainer<*, *>.DetailBottomAction(
    label: () -> String,
    primary: Boolean,
    theme: StockChatTheme,
    feedback: () -> Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            height(42f)
            marginLeft(2f)
            marginRight(2f)
            allCenter()
            borderRadius(21f)
            backgroundColor(if (primary) theme.brand else theme.surface.opacity(0.52f))
            border(Border(1f, BorderStyle.SOLID, if (feedback()) theme.brand else theme.divider.opacity(if (primary) 0f else 0.55f)))
            boxShadow(
                if (feedback()) BoxShadow(0f, 3f, 12f, theme.brand.opacity(0.18f))
                else BoxShadow(0f, 0f, 0f, theme.brand.opacity(0f))
            )
            if (!reduceMotion) {
                transform(scale = if (feedback()) Scale(1.03f, 1.03f) else Scale.DEFAULT)
                animate(Animation.easeOut(0.18f), feedback())
            }
        }
        Text {
            attr {
                text(label())
                fontSize(theme.type.sm)
                fontWeightSemiBold()
                color(if (primary) theme.onBrand else theme.textPrimary)
            }
        }
        FocusHairline({ feedback() || primary }, theme, reduceMotion)
        event { click { onClick() } }
    }
}

internal fun ViewContainer<*, *>.AiInsightBlock(
    state: () -> Int,
    remoteText: () -> String,
    remoteError: () -> String,
    remoteModel: () -> String,
    localSummary: () -> String,
    revealLimit: () -> Int,
    actionLabel: () -> String,
    remoteSentences: () -> List<String>,
    theme: StockChatTheme,
    onAction: () -> Unit,
    // true = 行情/资金事实尚未就绪、AI 请求还没发出（占位骨架，防先闪端侧模板再跳骨架）
    preparing: () -> Boolean = { false },
    // 呼吸相位（复用页侧 livePulse 680ms 翻转）：占位骨架整体明暗呼吸，等待真实 AI
    // 返回期间的「活着」反馈（2026-09-09 用户定案：呼吸占位框，替代静态骨架）
    breath: () -> Boolean = { false },
    reduceMotion: Boolean = false,
    selectedSentence: () -> Int = { -1 },
    onPickSentence: (Int) -> Unit = {},
) {
    View {
        attr {
            marginTop(theme.spacing.lg)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            boxShadow(BoxShadow(0f, 6f, 18f, theme.textPrimary.opacity(0.06f)))
        }
        View { attr { height(4f); backgroundColor(theme.brand) } }
        View {
            attr { padding(theme.spacing.lg) }
            // 头部：标题 + 真实动作按钮（生成/停止/重新解读/重试，随状态机变化）
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("AI 解读")
                        flex(1f)
                        fontSize(theme.type.label)
                        fontWeightSemiBold()
                        color(theme.brand)
                    }
                }
                Text {
                    attr {
                        text(actionLabel())
                        fontSize(theme.type.meta)
                        fontWeightSemiBold()
                        color(theme.brand)
                    }
                    event { click { onAction() } }
                }
            }
            // 内容层：状态机 + 文本共同驱动重建；打字机 reveal 属数据驱动（R1），
            // 无注册动画，重建不会丢动画状态。
            vbind({ state() to (remoteText() to revealLimit()) }) {
                val st = state()
                val remote = remoteText().trim()
                when {
                    // thinking：呼吸占位框 + 进度说明（等真实 LLM 返回，不落端侧模板）
                    st == 1 -> {
                        AiInsightPlaceholder(
                            theme = theme,
                            message = "正在读取行情事实并生成解读…",
                            breath = breath,
                        )
                    }
                    // 占位图：页面已打开但行情/资金事实还没就绪、AI 请求尚未发出。
                    // 此前这段时间显示端侧模板文字、随后再跳骨架，观感慢且割裂。
                    st == 0 && preparing() -> {
                        AiInsightPlaceholder(
                            theme = theme,
                            message = "行情与资金数据就绪后，会自动生成 AI 解读…",
                            breath = breath,
                            reduceMotion = reduceMotion,
                        )
                    }
                    // 远程流式/完成：逐句渲染 + 句图联动
                    remote.isNotEmpty() && st in 2..3 -> {
                        InsightSentences(
                            sentences = remoteSentences(),
                            streaming = st == 2,
                            theme = theme,
                            selectedSentence = selectedSentence,
                            onPickSentence = onPickSentence,
                        )
                    }
                    // 错误回退：红字如实报错；已有部分流式文本则保留，否则退端侧模板
                    st == 4 -> {
                        Text {
                            attr {
                                text("AI 调用失败：${remoteError()}")
                                marginTop(theme.spacing.md)
                                fontSize(theme.type.meta)
                                color(theme.fall)
                            }
                        }
                        if (remote.isNotEmpty()) {
                            InsightSentences(
                                sentences = remoteSentences(),
                                streaming = false,
                                theme = theme,
                                selectedSentence = selectedSentence,
                                onPickSentence = onPickSentence,
                            )
                        } else {
                            InsightSentences(
                                sentences = splitLocalReveal(localSummary(), revealLimit()),
                                streaming = false,
                                theme = theme,
                                selectedSentence = selectedSentence,
                                onPickSentence = onPickSentence,
                            )
                        }
                    }
                    // 本地（未配置/未请求）：端侧模板打字机揭示
                    else -> {
                        val sentences = splitLocalReveal(localSummary(), revealLimit())
                        if (sentences.isEmpty()) {
                            View {
                                attr {
                                    marginTop(theme.spacing.md)
                                    width(180f); height(18f); borderRadius(5f)
                                    backgroundColor(theme.surface.opacity(0.72f))
                                }
                            }
                            View {
                                attr {
                                    marginTop(theme.spacing.sm)
                                    width(240f); height(12f); borderRadius(4f)
                                    backgroundColor(theme.surface.opacity(0.62f))
                                }
                            }
                        } else {
                            InsightSentences(
                                sentences = sentences,
                                streaming = false,
                                theme = theme,
                                selectedSentence = selectedSentence,
                                onPickSentence = onPickSentence,
                            )
                        }
                    }
                }
            }
            // 底部来源行（如实标注内容来源——这是「真 AI」与「端侧规则」的分界线）
            vbind({ state() to (remoteModel() to remoteError()) }) {
                val label = when {
                    state() == 1 -> "正在调用 AI（${remoteModel()}）· 流式生成中"
                    state() == 0 && preparing() -> "等待行情与资金数据就绪 · 就绪后自动调用 AI"
                    state() == 2 || state() == 3 -> "AI 生成（${remoteModel()}）· 仅供参考，不构成投资建议"
                    state() == 4 -> if (remoteText().isNotBlank()) "AI 流中断，以上为已生成的部分内容" else "AI 调用失败，以上为端侧规则摘要（未调用 AI）"
                    else -> "端侧规则摘要 · 未调用 AI（配置 API 后点「生成」获得真实解读）"
                }
                Text {
                    attr {
                        text(label)
                        marginTop(theme.spacing.md)
                        fontSize(theme.type.meta)
                        color(theme.textTertiary)
                    }
                }
            }
            // ② 句图联动可发现性提示
            Text {
                attr {
                    text("点句子在走势图高亮对应区间 · 时间取自句内引用，未引用时间的句子按顺序近似定位")
                    marginTop(8f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

/** 端侧模板的打字机揭示：截断到 revealLimit 后按句切分。 */
private fun splitLocalReveal(summary: String, revealLimit: Int): List<String> {
    if (revealLimit <= 0) return emptyList()
    val revealed = summary.take(revealLimit.coerceAtMost(summary.length))
    return revealed.split(Regex("[。，]")).map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * AI 解读占位骨架（thinking / 等待事实就绪两态共用）：
 * 三行条 + 一行说明，整体随 breath 相位做明暗呼吸（等待真实 AI 的「活着」反馈；
 * breath 恒 false（reduceMotion）时静态显示）。
 */
private fun ViewContainer<*, *>.AiInsightPlaceholder(
    theme: StockChatTheme,
    message: String,
    breath: () -> Boolean = { false },
    reduceMotion: Boolean = false,
) {
    // 呼吸：容器 opacity 随相位翻转 0.45↔1.0（R1/R2：attr 内读相位、animate 收尾；
    // R5：每次翻转重跑 attr 都重新注册，下一拍消费上一拍注册的动画）。
    // reduceMotion：恒全亮静态，不注册动画。
    View {
        attr {
            marginTop(theme.spacing.md)
            opacity(if (reduceMotion || breath()) 1f else 0.45f)
            if (!reduceMotion) animate(Animation.easeOut(0.68f), breath())
        }
        View { attr { width(220f); height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.14f)) } }
        View { attr { width(180f); height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.10f)); marginTop(9f) } }
        View { attr { width(200f); height(11f); borderRadius(5f); backgroundColor(theme.brand.opacity(0.08f)); marginTop(9f) } }
        Text { attr { text(message); marginTop(10f); fontSizeScaled(10f); color(theme.brand) } }
    }
}

/**
 * 图表区加载骨架（quoteLoading 期间替代空白图表，2026-09-10）：396dp 圆角玻璃
 * 基座 + 三条占位条（上两行拟价格行位、底部一行拟量能带），整体随 breath 相位
 * 明暗呼吸（复用 AI 占位骨架范式：R2/R5 每次 attr 重跑重注册，下一拍消费）。
 * reduceMotion 恒全亮静态。
 */
internal fun ViewContainer<*, *>.ChartLoadingSkeleton(
    theme: StockChatTheme,
    breath: () -> Boolean = { false },
    reduceMotion: Boolean = false,
) {
    View {
        attr {
            marginTop(theme.spacing.lg)
            height(396f)
            borderRadius(16f)
            backgroundColor(theme.marketGlass.opacity(0.6f))
            border(Border(1f, BorderStyle.SOLID, theme.marketGlassEdge))
            opacity(if (reduceMotion || breath()) 1f else 0.55f)
            if (!reduceMotion) animate(Animation.easeOut(0.68f), breath())
        }
        View {
            attr {
                absolutePosition(left = 16f, top = 56f)
                width(196f); height(11f); borderRadius(5f)
                backgroundColor(theme.textTertiary.opacity(0.32f))
            }
        }
        View {
            attr {
                absolutePosition(left = 16f, top = 80f)
                width(132f); height(11f); borderRadius(5f)
                backgroundColor(theme.textTertiary.opacity(0.20f))
            }
        }
        View {
            attr {
                absolutePosition(left = 16f, top = 344f)
                width(176f); height(8f); borderRadius(4f)
                backgroundColor(theme.textTertiary.opacity(0.16f))
            }
        }
    }
}

/** 逐句渲染（首句强调 + 后续圆点行），流式态末句尾随光标；点句回调带全局句序号。 */
private fun ViewContainer<*, *>.InsightSentences(
    sentences: List<String>,
    streaming: Boolean,
    theme: StockChatTheme,
    selectedSentence: () -> Int,
    onPickSentence: (Int) -> Unit,
) {
    if (sentences.isEmpty()) return
    fun displayText(s: String, isLast: Boolean): String =
        s + if (streaming && isLast) " ▍" else if (s.endsWith("。") || s.endsWith("，")) "" else "。"
    // 首句（句 0）
    View {
        attr { marginTop(theme.spacing.sm) }
        Text {
            attr {
                text(displayText(sentences.first(), sentences.size == 1))
                fontSize(theme.type.body)
                fontWeightSemiBold()
                color(if (selectedSentence() == 0) theme.brand else theme.textPrimary)
                lineHeightScaled(21f)
            }
        }
        event { click { onPickSentence(0) } }
    }
    sentences.drop(1).forEachIndexed { index, s ->
        View {
            attr { flexDirectionRow(); marginTop(theme.spacing.sm); alignItemsFlexStart() }
            View {
                attr {
                    width(6f); height(6f); borderRadius(3f)
                    backgroundColor(if (selectedSentence() == index + 1) theme.brand else theme.textTertiary)
                    marginTop(6f); marginRight(theme.spacing.sm)
                }
            }
            Text {
                attr {
                    flex(1f)
                    text(displayText(s, index == sentences.size - 2))
                    fontSize(theme.type.sm)
                    lineHeightScaled(19f)
                    color(if (selectedSentence() == index + 1) theme.brand else theme.textSecondary)
                }
            }
            // ② 句图联动：点句 → 页面端侧计算真实锚点点亮区间带
            event { click { onPickSentence(index + 1) } }
        }
    }
}

private fun ViewContainer<*, *>.AttributionBlock(
    model: AttributionCardModel,
    theme: StockChatTheme,
    expandedKey: () -> String,
    // 行情方向走 lambda：model 是首帧快照，标题需随 quote tick 在 attr 内实时刷新（R1）
    rising: () -> Boolean,
    reduceMotion: Boolean,
    onToggle: (String) -> Unit,
) {
    val factors = model.factors
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text("为什么${if (rising()) "涨" else "跌"}")
            fontSize(theme.type.title)
            fontWeightSemiBold()
            color(theme.textPrimary)
        }
    }
    factors.forEachIndexed { index, factor ->
        val key = "$index:${factor.name}"
        // expanded 必须在闭包内实时求值：expandedKey() 读 observable（R1），
        // 捕获成 Boolean 会让 vif 永不重跑、animate 绑不到 key。
        fun expanded(): Boolean = expandedKey() == key
        View {
            attr {
                marginTop(theme.spacing.md); paddingTop(theme.spacing.md)
                if (index > 0) borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
                if (!reduceMotion) animate(Animation.easeOut(0.18f), expanded())
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(factor.name)
                        flex(1f)
                        fontSize(theme.type.sm)
                        fontWeightMedium()
                        color(theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("${Format.decimal(factor.weight * 100, 0)}%")
                        fontSize(theme.type.label)
                        color(theme.textSecondary)
                    }
                }
                Text {
                    attr {
                        text(if (expanded()) "⌄" else "›")
                        marginLeft(8f)
                        fontSize(theme.type.body)
                        color(theme.brand)
                    }
                }
            }
            Text {
                attr {
                    text(factor.description)
                    marginTop(4f)
                    fontSize(theme.type.label)
                    lineHeightScaled(16f)
                    color(theme.textSecondary)
                }
            }
            View {
                attr {
                    marginTop(8f)
                    height(4f)
                    borderRadius(2f)
                    flexDirectionRow()
                    backgroundColor(theme.surfaceMuted)
                }
                View {
                    attr {
                        flex(factor.weight.toFloat().coerceIn(0.02f, 1f))
                        borderRadius(2f)
                        backgroundColor(theme.brand)
                    }
                }
                View { attr { flex((1f - factor.weight.toFloat()).coerceAtLeast(0.001f)) } }
            }
            vif({ expanded() }) {
                View {
                    attr {
                        marginTop(theme.spacing.sm)
                        padding(theme.spacing.md)
                        borderRadius(theme.inputRadius)
                        backgroundColor(theme.surfaceMuted)
                        border(Border(1f, BorderStyle.SOLID, theme.divider))
                        opacity(if (expanded()) 1f else 0f)
                        if (!reduceMotion) {
                            transform(Translate(0f, if (expanded()) 0f else 0.10f))
                            animate(Animation.easeOut(0.20f), expanded())
                        }
                    }
                    Text {
                        attr {
                            text("依据：${factor.source.ifEmpty { model.source.ifEmpty { "行情与公开资料" } }} · ${model.asOf.ifEmpty { "当前快照" }}")
                            fontSize(theme.type.meta)
                            fontWeightSemiBold()
                            color(theme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text("这项归因主要说明它对当日涨跌的相对影响强弱，需要和其它原因一起看。")
                            marginTop(5f)
                            fontSize(theme.type.label)
                            lineHeightScaled(16f)
                            color(theme.textSecondary)
                        }
                    }
                }
            }
            event { click { onToggle(key) } }
        }
    }
}

/**
 * 把可手调的归因重放和 AI 情景推演收在一个工作台内。用户可随时回到手动归因，
 * AI 面则明确展示「读取快照 → 提取信号 → 合成情景」的推测链，而非黑盒结论。
 */
internal fun ViewContainer<*, *>.AttributionForecastWorkbench(
    theme: StockChatTheme,
    factors: List<FactorSpec>,
    actualPct: () -> Double,
    quote: () -> Quote,
    mainFlow: () -> Double?,
    forecastState: DetailForecastState,
    onRequestForecast: () -> Unit,
    onRetryForecast: () -> Unit,
    containerWidth: Float,
    reduceMotion: Boolean,
) {
    val mode = BlockState(0) // 0 = 用户手动归因，1 = AI 推测过程
    View {
        attr {
            marginTop(8f)
            padding(12f)
            borderRadius(14f)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
        }
        View {
            attr {
                height(30f)
                padding(3f)
                flexDirectionRow()
                borderRadius(8f)
                backgroundColor(theme.surfaceMuted)
            }
            listOf("手动归因", "AI 走势").forEachIndexed { index, label ->
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        borderRadius(6f)
                        val active = mode.value == index
                        backgroundColor(if (active) theme.brandSoft else Color.TRANSPARENT)
                        if (!reduceMotion) animate(Animation.easeOut(0.18f), active)
                    }
                    Text {
                        attr {
                            val active = mode.value == index
                            text(label)
                            fontSizeScaled(10f)
                            fontWeightSemiBold()
                            color(if (active) theme.brand else theme.textTertiary)
                            if (!reduceMotion) animate(Animation.easeOut(0.18f), active)
                        }
                    }
                    event { click { mode.value = index; if (index == 1) onRequestForecast() } }
                }
            }
        }
        vbind({ mode.value }) {
            if (mode.value == 0) {
                WorkbenchPaneEntrance(reduceMotion) {
                    FactorReplayBlock(
                        theme = theme,
                        factors = factors,
                        actualPct = actualPct,
                        containerWidth = containerWidth - 24f,
                        reduceMotion = reduceMotion,
                    )
                }
            } else {
                WorkbenchPaneEntrance(reduceMotion) {
                    AiInferenceProcessBlock(
                        theme = theme,
                        quote = quote,
                        mainFlow = mainFlow,
                        reduceMotion = reduceMotion,
                    )
                    AiForecastResultBlock(
                        theme = theme,
                        quote = quote,
                        mainFlow = mainFlow,
                        forecastState = forecastState,
                        reduceMotion = reduceMotion,
                        onRetry = onRetryForecast,
                    )
                }
            }
        }
    }
}

/** 新切面固定走两帧挂载，使手动归因与 AI 推测的内容切换都有可见过渡。 */
private fun ViewContainer<*, *>.WorkbenchPaneEntrance(
    reduceMotion: Boolean,
    content: ViewContainer<*, *>.() -> Unit,
) {
    val presented = BlockState(reduceMotion)
    if (!reduceMotion) setTimeout(0) { presented.value = true }
    View {
        attr {
            val visible = presented.value
            opacity(if (visible) 1f else 0f)
            transform(Translate(0f, if (visible) 0f else 0.10f))
            if (!reduceMotion) animate(Animation.easeOut(0.20f), visible)
        }
        content()
    }
}

/** AI 推测的可见中间步骤；其结论仍由下方明确标注为情景判断的卡片给出。 */
private fun ViewContainer<*, *>.AiInferenceProcessBlock(
    theme: StockChatTheme,
    quote: () -> Quote,
    mainFlow: () -> Double?,
    reduceMotion: Boolean,
) {
    val phase = BlockState(if (reduceMotion) 3 else 0)
    if (!reduceMotion) {
        setTimeout(0) { phase.value = 1 }
        setTimeout(150) { phase.value = 2 }
        setTimeout(300) { phase.value = 3 }
    }
    vbind({ quote() to mainFlow() }) {
        val currentQuote = quote()
        val flow = mainFlow()
        val outlook = trendOutlook(currentQuote, flow, theme)
        val steps = listOf(
            "读取市场快照" to "涨跌 ${Format.percent(currentQuote.changePercent)} · 振幅 ${Format.percent(if (currentQuote.previousClose == 0.0) 0.0 else (currentQuote.high - currentQuote.low) / currentQuote.previousClose * 100.0)}",
            "提取归因信号" to (flow?.let { "主力资金${if (it >= 0) "净流入" else "净流出"}${Format.compactAmount(kotlin.math.abs(it))}" } ?: "资金数据暂未返回"),
            "合成下一阶段情景" to outlook.title,
        )
        View {
            attr {
                marginTop(12f)
                padding(10f)
                borderRadius(10f)
                backgroundColor(theme.brandSoft.opacity(0.60f))
            }
            Text {
                attr {
                    text("AI 推测过程")
                    fontSizeScaled(11f)
                    fontWeightSemiBold()
                    color(theme.brand)
                }
            }
            steps.forEachIndexed { index, (title, detail) ->
                View {
                    attr {
                        val shown = phase.value >= index + 1
                        marginTop(9f)
                        flexDirectionRow()
                        opacity(if (shown) 1f else 0f)
                        transform(Translate(if (shown) 0f else 0.08f, 0f))
                        if (!reduceMotion) animate(Animation.easeOut(0.18f), phase.value)
                    }
                    View {
                        attr {
                            width(16f); height(16f); borderRadius(8f)
                            allCenter()
                            backgroundColor(if (phase.value >= index + 1) theme.brand else theme.surfaceMuted)
                        }
                        Text { attr { text("${index + 1}"); fontSizeScaled(8.5f); fontWeightSemiBold(); color(if (phase.value >= index + 1) theme.onBrand else theme.textTertiary) } }
                    }
                    View {
                        attr { flex(1f); marginLeft(7f) }
                        Text { attr { text(title); fontSizeScaled(10f); fontWeightSemiBold(); color(theme.textPrimary) } }
                        Text { attr { text(detail); marginTop(2f); fontSizeScaled(9.5f); lineHeightScaled(14f); color(theme.textSecondary) } }
                    }
                }
            }
        }
    }
}

/**
 * A compact, explicitly uncertain next-session scenario derived from the same quote and fund-flow
 * inputs as the attribution block. It is labelled as a modelled outlook, never a trade signal.
 */
private fun ViewContainer<*, *>.AiTrendForecastBlock(
    theme: StockChatTheme,
    quote: () -> Quote,
    mainFlow: () -> Double?,
) {
    vbind({ quote() to mainFlow() }) {
        val outlook = trendOutlook(quote(), mainFlow(), theme)
        View {
            attr {
                marginTop(12f)
                paddingTop(10f); paddingBottom(10f)
                paddingLeft(12f); paddingRight(12f)
                borderLeft(Border(3f, BorderStyle.SOLID, outlook.color))
                backgroundColor(theme.surfaceMuted.opacity(0.68f))
                borderRadius(10f)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("AI 走势推演")
                        fontSizeScaled(12f)
                        fontWeightSemiBold()
                        color(theme.textPrimary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text("情景判断 · ${outlook.confidence}")
                        fontSizeScaled(10f)
                        color(outlook.color)
                    }
                }
            }
            Text {
                attr {
                    text(outlook.title)
                    marginTop(5f)
                    fontSizeScaled(14f)
                    fontWeightSemiBold()
                    color(outlook.color)
                }
            }
            Text {
                attr {
                    text(outlook.detail)
                    marginTop(3f)
                    fontSizeScaled(11f)
                    lineHeightScaled(16f)
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    text("仅基于当前价量与资金快照推演，不构成买卖建议。")
                    marginTop(6f)
                    fontSizeScaled(9.5f)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

// ─────────── AI 走势推演 · 真实模型结果区（2026-09-12）───────────
// 消费 DetailAiForecastCoordinator 的结构化输出：方向/置信度/情景区间/结论/依据
// （含权重）+ AI 情景路径折线。旧 [AiTrendForecastBlock]（端侧规则拼文案）降级
// 为 AI 失败时的回退渲染，不再作为主展示。

/** 「AI 走势」tab 结果区三态：1/2 思考骨架、3 结果卡、4 失败回退。 */
private fun ViewContainer<*, *>.AiForecastResultBlock(
    theme: StockChatTheme,
    quote: () -> Quote,
    mainFlow: () -> Double?,
    forecastState: DetailForecastState,
    reduceMotion: Boolean,
    onRetry: () -> Unit,
) {
    vbind({ forecastState.phase }) {
        when (forecastState.phase) {
            1, 2 -> AiForecastSkeleton(theme, forecastState, reduceMotion)
            3 -> AiForecastResultCard(theme, quote, forecastState, reduceMotion, onRetry)
            else -> AiForecastErrorCard(theme, quote, mainFlow, forecastState, reduceMotion, onRetry)
        }
    }
}

/** 思考/流式骨架：三点呼吸（pulseTick 低频驱动）+ 已接收字数。 */
private fun ViewContainer<*, *>.AiForecastSkeleton(
    theme: StockChatTheme,
    forecastState: DetailForecastState,
    reduceMotion: Boolean,
) {
    val presented = BlockState(reduceMotion)
    if (!reduceMotion) setTimeout(0) { presented.value = true }
    View {
        attr {
            marginTop(12f)
            paddingTop(10f); paddingBottom(12f)
            paddingLeft(12f); paddingRight(12f)
            borderLeft(Border(3f, BorderStyle.SOLID, theme.brand))
            backgroundColor(theme.surfaceMuted.opacity(0.68f))
            borderRadius(10f)
            opacity(if (presented.value) 1f else 0f)
            transform(Translate(0f, if (presented.value) 0f else 0.10f))
            if (!reduceMotion) animate(Animation.easeOut(0.20f), presented.value)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("AI 走势推演")
                    fontSizeScaled(12f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text(if (forecastState.phase == 2) "接收中" else "推演中")
                    fontSizeScaled(10f)
                    color(theme.textTertiary)
                }
            }
        }
        View {
            attr { marginTop(9f); flexDirectionRow(); alignItemsCenter(); height(14f) }
            listOf(0, 1, 2).forEach { index ->
                View {
                    attr {
                        width(5f); height(5f); borderRadius(2.5f)
                        marginLeft(if (index == 0) 0f else 4f)
                        backgroundColor(theme.brand)
                        val lit = (forecastState.pulseTick + index) % 3 == 0
                        opacity(if (lit) 1f else 0.30f)
                        if (!reduceMotion) animate(Animation.linear(0.30f), lit)
                    }
                }
            }
            Text {
                attr {
                    text(
                        if (forecastState.phase == 2 && forecastState.streamChars > 0) {
                            "已接收 ${forecastState.streamChars} 字"
                        } else {
                            "正在结合行情、资金与资讯推演情景"
                        }
                    )
                    marginLeft(8f)
                    fontSizeScaled(9.5f)
                    color(theme.textTertiary)
                    flex(1f)
                }
            }
        }
        View {
            attr { marginTop(10f); height(10f); width(190f); borderRadius(5f); backgroundColor(theme.divider.opacity(0.55f)) }
        }
        View {
            attr { marginTop(6f); height(10f); width(250f); borderRadius(5f); backgroundColor(theme.divider.opacity(0.35f)) }
        }
    }
}

/**
 * AI 结果卡：标题行（方向徽章 + 置信度 + 重新推演）→ 数据行（左侧情景区间/结论，
 * 右侧情景折线图）→ 推演依据（权重条 + 错峰入场）→ 口径脚注。
 * 折线 draw-on 与依据错峰均为两帧挂载后 setTimeout 链驱动（R4/R5）。
 */
private fun ViewContainer<*, *>.AiForecastResultCard(
    theme: StockChatTheme,
    quote: () -> Quote,
    forecastState: DetailForecastState,
    reduceMotion: Boolean,
    onRetry: () -> Unit,
) {
    vbind({ forecastState.result }) {
        val forecast = forecastState.result ?: return@vbind
        val presented = BlockState(reduceMotion)
        if (!reduceMotion) setTimeout(0) { presented.value = true }
        // 情景折线 draw-on：0→1，30ms × 16 步 ≈ 480ms
        val drawProgress = BlockState(if (reduceMotion) 1f else 0f)
        if (!reduceMotion) {
            for (step in 1..16) setTimeout(step * 30) { drawProgress.value = step / 16f }
        }
        // 依据条目错峰入场
        val reasonStep = BlockState(if (reduceMotion) Int.MAX_VALUE else 0)
        if (!reduceMotion) {
            for (step in 1..(forecast.reasons.size + 1)) setTimeout(260 + step * 90) { reasonStep.value = step }
        }
        val tone = forecastDirectionColor(theme, forecast.direction)
        View {
            attr {
                marginTop(12f)
                paddingTop(10f); paddingBottom(12f)
                paddingLeft(12f); paddingRight(12f)
                borderLeft(Border(3f, BorderStyle.SOLID, tone))
                backgroundColor(theme.surfaceMuted.opacity(0.68f))
                borderRadius(10f)
                opacity(if (presented.value) 1f else 0f)
                transform(Translate(0f, if (presented.value) 0f else 0.10f))
                if (!reduceMotion) animate(Animation.easeOut(0.22f), presented.value)
            }
            // ── 标题行 ──
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("AI 走势推演")
                        fontSizeScaled(12f)
                        fontWeightSemiBold()
                        color(theme.textPrimary)
                        flex(1f)
                    }
                }
                View {
                    attr {
                        paddingLeft(7f); paddingRight(7f)
                        paddingTop(2f); paddingBottom(2f)
                        borderRadius(8f)
                        backgroundColor(tone.opacity(0.14f))
                    }
                    Text {
                        attr {
                            text(forecastDirectionLabel(forecast.direction))
                            fontSizeScaled(9.5f)
                            fontWeightSemiBold()
                            color(tone)
                        }
                    }
                }
                Text {
                    attr {
                        text("置信度 ${forecastConfidenceLabel(forecast.confidence)}")
                        marginLeft(7f)
                        fontSizeScaled(9.5f)
                        color(theme.textTertiary)
                    }
                }
                Text {
                    attr {
                        text("↻ 重新推演")
                        marginLeft(9f)
                        fontSizeScaled(9.5f)
                        fontWeightSemiBold()
                        color(theme.brand)
                    }
                    event { click { onRetry() } }
                }
            }
            // ── 数据行：左侧区间/结论，右侧情景折线图 ──
            View {
                attr { marginTop(9f); flexDirectionRow(); alignItemsFlexStart() }
                View {
                    attr { flex(1f); marginRight(10f) }
                    Text {
                        attr {
                            text("情景区间")
                            fontSizeScaled(9.5f)
                            color(theme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text("${Format.percent(forecast.rangePctHigh)} ~ ${Format.percent(forecast.rangePctLow)}")
                            marginTop(2f)
                            fontSizeScaled(16f)
                            fontWeightSemiBold()
                            color(tone)
                        }
                    }
                    Text {
                        attr {
                            val price = quote().price
                            val hi = price * (1.0 + forecast.rangePctHigh / 100.0)
                            val lo = price * (1.0 + forecast.rangePctLow / 100.0)
                            text("对应 ${Format.price(lo)} ~ ${Format.price(hi)}")
                            marginTop(1f)
                            fontSizeScaled(9.5f)
                            color(theme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(forecast.summary)
                            marginTop(7f)
                            fontSizeScaled(11f)
                            lineHeightScaled(16f)
                            color(theme.textSecondary)
                        }
                    }
                }
                ForecastScenarioChart(theme, forecastState, drawProgress, reduceMotion)
            }
            // ── 推演依据 ──
            if (forecast.reasons.isNotEmpty()) {
                View {
                    attr {
                        marginTop(11f)
                        val shown = reasonStep.value >= 1
                        opacity(if (shown) 1f else 0f)
                        if (!reduceMotion) animate(Animation.easeOut(0.18f), shown)
                    }
                    Text {
                        attr {
                            text("推演依据 · ${forecast.reasons.size} 条（权重为模型标注）")
                            fontSizeScaled(10f)
                            fontWeightSemiBold()
                            color(theme.textSecondary)
                        }
                    }
                }
                forecast.reasons.forEachIndexed { index, reason ->
                    AiForecastReasonRow(theme, reason, index + 2, reasonStep, reduceMotion)
                }
            }
            // ── 口径脚注 ──
            Text {
                attr {
                    text(
                        buildString {
                            append("情景推演来自 ${forecastState.model.ifEmpty { "AI 模型" }}；")
                            append(
                                if (forecast.pathFromModel) "折线为 AI 情景路径，" else "AI 未给出路径，折线为端侧按情景区间示意，"
                            )
                            append("不构成投资建议。")
                        }
                    )
                    marginTop(10f)
                    fontSizeScaled(9f)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

/** 单条推演依据：序号圈 + 因素名/权重 + 依据正文；随 reasonStep 错峰入场。 */
private fun ViewContainer<*, *>.AiForecastReasonRow(
    theme: StockChatTheme,
    reason: AiForecastReason,
    revealAt: Int,
    reasonStep: BlockState<Int>,
    reduceMotion: Boolean,
) {
    View {
        attr {
            marginTop(9f)
            flexDirectionRow()
            val shown = reduceMotion || reasonStep.value >= revealAt
            opacity(if (shown) 1f else 0f)
            transform(Translate(if (shown) 0f else 0.08f, 0f))
            if (!reduceMotion) animate(Animation.easeOut(0.20f), shown)
        }
        View {
            attr {
                width(16f); height(16f); borderRadius(8f)
                allCenter()
                backgroundColor(theme.brand)
            }
            Text {
                attr {
                    text("${revealAt - 1}")
                    fontSizeScaled(8.5f)
                    fontWeightSemiBold()
                    color(theme.onBrand)
                }
            }
        }
        View {
            attr { flex(1f); marginLeft(7f) }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(reason.title)
                        fontSizeScaled(10f)
                        fontWeightSemiBold()
                        color(theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("${(reason.weight * 100).roundToInt().coerceIn(0, 100)}%")
                        marginLeft(6f)
                        fontSizeScaled(8.5f)
                        color(theme.textTertiary)
                    }
                }
                // 权重条：AI 标注的贡献权重（与手动归因权重条同视觉语言）
                View {
                    attr {
                        flex(1f); marginLeft(8f); height(3f); borderRadius(1.5f)
                        backgroundColor(theme.divider.opacity(0.6f))
                    }
                    View {
                        attr {
                            flex(reason.weight.toFloat().coerceIn(0.03f, 1f))
                            height(3f)
                            borderRadius(1.5f)
                            backgroundColor(theme.brand.opacity(0.75f))
                        }
                    }
                }
            }
            if (reason.detail.isNotEmpty()) {
                Text {
                    attr {
                        text(reason.detail)
                        marginTop(2f)
                        fontSizeScaled(9.5f)
                        lineHeightScaled(14f)
                        color(theme.textSecondary)
                    }
                }
            }
        }
    }
}

/**
 * 情景折线小图（124×96）：x 轴「现在 → 下一阶段」，y 轴为相对现价的 %。
 * 元素：虚线零轴 + 置信锥（区间随时间张开的三角形带）+ AI 情景路径折线
 * （draw-on 入场）+ 终点标注。全部为展示层：数据来自 [AiTrendForecast]。
 */
private fun ViewContainer<*, *>.ForecastScenarioChart(
    theme: StockChatTheme,
    forecastState: DetailForecastState,
    drawProgress: BlockState<Float>,
    reduceMotion: Boolean,
) {
    Canvas({
        attr {
            width(124f)
            height(96f)
            touchEnable(false)
        }
    }) { canvas, width, height ->
        val forecast = forecastState.result ?: return@Canvas
        val progress = if (reduceMotion) 1f else drawProgress.value
        val padLeft = 2f
        val padTop = 4f
        val padBottom = 14f
        val plotW = (width - padLeft * 2).coerceAtLeast(1f)
        val plotH = (height - padTop - padBottom).coerceAtLeast(1f)
        // 纵轴尺度：对称于 0，包住情景区间（上限 9.9% 防极端值压扁图形）
        val scale = maxOf(abs(forecast.rangePctHigh), abs(forecast.rangePctLow), 0.3).coerceAtMost(9.9)
        val yFor: (Double) -> Float = { pct ->
            padTop + ((1.0 - (pct / scale + 1.0) / 2.0) * plotH).toFloat()
        }
        val tone = forecastDirectionColor(theme, forecast.direction)

        // 零轴（虚线）
        canvas.beginPath()
        canvas.moveTo(padLeft, yFor(0.0))
        canvas.lineTo(padLeft + plotW, yFor(0.0))
        canvas.setLineDash(listOf(3f, 3f))
        canvas.lineWidth(0.8f)
        canvas.strokeStyle(theme.textTertiary.opacity(0.6f))
        canvas.stroke()
        canvas.setLineDash(emptyList())

        // 置信锥：从「现在」的 0 张开到「下一阶段」的 [low, high]
        canvas.beginPath()
        canvas.moveTo(padLeft, yFor(0.0))
        canvas.lineTo(padLeft + plotW, yFor(forecast.rangePctHigh))
        canvas.lineTo(padLeft + plotW, yFor(forecast.rangePctLow))
        canvas.closePath()
        canvas.fillStyle(tone.opacity(0.10f))
        canvas.fill()

        // 情景路径折线（draw-on：按 progress 截取可见点数）
        val path = forecast.path
        if (path.size >= 2) {
            val xFor: (Int) -> Float = { i -> padLeft + i.toFloat() / (path.size - 1).toFloat() * plotW }
            val visible = if (progress >= 1f) path.size else (path.size * progress).roundToInt().coerceIn(2, path.size)
            canvas.beginPath()
            for (i in 0 until visible) {
                val x = xFor(i)
                val y = yFor(path[i])
                if (i == 0) canvas.moveTo(x, y) else canvas.lineTo(x, y)
            }
            canvas.lineWidth(1.6f)
            canvas.strokeStyle(tone)
            canvas.stroke()
            // 终点：圆点 + 数值标注
            val endX = xFor(visible - 1)
            val endY = yFor(path[visible - 1])
            canvas.beginPath()
            canvas.arc(endX, endY, 2.5f, 0f, (2.0 * PI).toFloat(), false)
            canvas.fillStyle(tone)
            canvas.fill()
            canvas.font(8.5f)
            canvas.fillStyle(theme.textSecondary)
            canvas.textAlign(TextAlign.RIGHT)
            canvas.fillText(Format.percent(path[visible - 1]), endX, endY - 5f)
        }

        // x 轴标注
        canvas.font(8.5f)
        canvas.fillStyle(theme.textTertiary)
        canvas.textAlign(TextAlign.LEFT)
        canvas.fillText("现在", padLeft, height - 2f)
        canvas.textAlign(TextAlign.RIGHT)
        canvas.fillText("下一阶段", padLeft + plotW, height - 2f)
        canvas.textAlign(TextAlign.LEFT)
    }
}

/** AI 失败态：错误信息 + 重试入口 + 降级为端侧规则推演（旧卡，信息不为空）。 */
private fun ViewContainer<*, *>.AiForecastErrorCard(
    theme: StockChatTheme,
    quote: () -> Quote,
    mainFlow: () -> Double?,
    forecastState: DetailForecastState,
    reduceMotion: Boolean,
    onRetry: () -> Unit,
) {
    View {
        attr {
            marginTop(12f)
            paddingTop(10f); paddingBottom(12f)
            paddingLeft(12f); paddingRight(12f)
            borderLeft(Border(3f, BorderStyle.SOLID, theme.textTertiary))
            backgroundColor(theme.surfaceMuted.opacity(0.68f))
            borderRadius(10f)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("AI 走势推演")
                    fontSizeScaled(12f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("↻ 重试")
                    fontSizeScaled(9.5f)
                    fontWeightSemiBold()
                    color(theme.brand)
                }
                event { click { onRetry() } }
            }
        }
        Text {
            attr {
                text(forecastState.error.ifEmpty { "AI 暂时不可用" })
                marginTop(6f)
                fontSizeScaled(10f)
                lineHeightScaled(15f)
                color(theme.fall)
            }
        }
        Text {
            attr {
                text("以下为端侧规则推演（非 AI 输出）：")
                marginTop(8f)
                fontSizeScaled(9f)
                color(theme.textTertiary)
            }
        }
        AiTrendForecastBlock(theme, quote, mainFlow)
    }
}

private fun forecastDirectionLabel(direction: String): String = when (direction) {
    "up" -> "偏强"
    "down" -> "偏弱"
    else -> "震荡"
}

private fun forecastConfidenceLabel(confidence: String): String = when (confidence) {
    "high" -> "高"
    "mid" -> "中"
    else -> "低"
}

private fun forecastDirectionColor(theme: StockChatTheme, direction: String): Color = when (direction) {
    "up" -> theme.rise
    "down" -> theme.fall
    else -> theme.textSecondary
}

private fun trendOutlook(q: Quote, mainFlow: Double?, theme: StockChatTheme): TrendOutlook {
    if (q.previousClose <= 0.0) {
        return TrendOutlook("等待行情数据", "需等待昨收、涨跌和成交数据完整后再生成情景。", "低", theme.textSecondary)
    }
    val flowSignal = when {
        mainFlow == null -> 0
        mainFlow > 0.0 -> 1
        mainFlow < 0.0 -> -1
        else -> 0
    }
    val priceSignal = when {
        q.changePercent > 0.8 -> 2
        q.changePercent > 0.15 -> 1
        q.changePercent < -0.8 -> -2
        q.changePercent < -0.15 -> -1
        else -> 0
    }
    val amplitude = (q.high - q.low) / q.previousClose * 100.0
    val score = priceSignal + flowSignal
    val flowText = mainFlow?.let { "主力资金${if (it >= 0.0) "净流入" else "净流出"}${Format.compactAmount(kotlin.math.abs(it))}" } ?: "主力资金尚未返回"
    return when {
        score >= 2 -> TrendOutlook(
            "偏强延续情景",
            "涨跌 ${Format.percent(q.changePercent)}，$flowText；若量能不明显回落，短线可能维持偏强节奏。振幅 ${Format.percent(amplitude)}，仍需防高波动回撤。",
            "中", theme.rise,
        )
        score <= -2 -> TrendOutlook(
            "偏弱修复情景",
            "涨跌 ${Format.percent(q.changePercent)}，$flowText；若后续没有资金回流，弱势可能延续。振幅 ${Format.percent(amplitude)}，留意波动进一步放大。",
            "中", theme.fall,
        )
        else -> TrendOutlook(
            "区间震荡情景",
            "涨跌 ${Format.percent(q.changePercent)}，$flowText；价格与资金信号尚未同向，下一阶段更可能围绕当日区间反复确认。",
            "低", theme.textSecondary,
        )
    }
}

/**
 * doc 29 F1 公告要点 · 端侧评级（公告与研报卡之前的摘要块）：
 * 前 3 条公告/研报（标题+徽章+日期），高重要度加粗；点击条目 toast 判定依据（U3）。
 * 徽章为三枚圆点：HIGH 三涨色 / MID 两橙 / LOW 一灰（DetailBoardBlocks.MaterialityBadge）。
 *
 * [inset] = 并入「公告与研报」大卡（原型 .ann-card：公告行直接落在卡面上，行间细分割线）；
 * false = 独立灰底子卡（旧形态，保留兼容）。
 */
internal fun ViewContainer<*, *>.DisclosureMaterialityBlock(
    items: List<DisclosureItem>,
    theme: StockChatTheme,
    inset: Boolean = false,
    onExplain: (String) -> Unit,
    // 长按条目 → 页级预览浮层（MarketPage peek 同款，2026-09-09 新增）：
    // toast 稍纵即逝看不完详情，长按浮卡可停留细看（松手不消失，点蒙层/关闭收回）。
    onPeek: (DisclosureItem) -> Unit,
) {
    vif({ items.isNotEmpty() }) {
        View {
            attr {
                if (!inset) {
                    marginBottom(theme.spacing.md)
                    padding(theme.spacing.md)
                    borderRadius(theme.inputRadius)
                    backgroundColor(theme.surfaceMuted)
                    border(Border(0.5f, BorderStyle.SOLID, theme.divider))
                }
            }
            Text {
                attr {
                    text("公告要点 · 端侧评级")
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.textSecondary)
                }
            }
            items.forEachIndexed { index, item ->
                // 评级在 attr 闭包内实时求值（纯函数，无副作用）
                fun level() = materialityOf(item.title).level
                View {
                    attr {
                        marginTop(8f)
                        if (inset && index > 0) {
                            // 并卡模式：公告行之间对齐原型的 0.5px 细分割线
                            paddingTop(8f)
                            borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
                        }
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    MaterialityBadge(theme, { level() })
                    Text {
                        attr {
                            text(item.title)
                            fontSize(theme.type.label)
                            // F1：高重要度整行加粗
                            if (level() == Materiality.HIGH) fontWeightBold() else fontWeightMedium()
                            color(theme.textPrimary)
                            lineHeightScaled(16f)
                        }
                    }
                    Text {
                        attr {
                            text("  ${item.date}")
                            fontSize(theme.type.meta)
                            color(theme.textTertiary)
                        }
                    }
                    event {
                        click { onExplain(item.title) }
                        longPress { params ->
                            if (params.state == "start") onPeek(item)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("长按条目看详情 · 点按看判定依据 · 端侧规则")
                    marginTop(8f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.NewsSection(theme: StockChatTheme) {
    val items = listOf(
        Triple("公司发布近期经营情况说明", "公司公告", "2 小时前"),
        Triple("白酒板块盘中震荡，龙头股表现分化", "证券时报", "3 小时前"),
        Triple("机构关注消费复苏节奏与渠道库存", "公开研报摘要", "昨天"),
    )
    Text {
        attr {
            marginTop(theme.spacing.x3)
            text("相关资讯")
            fontSize(theme.type.label)
            fontWeightSemiBold()
            color(theme.textTertiary)
        }
    }
    items.forEachIndexed { index, item ->
        View {
            attr {
                marginTop(theme.spacing.md); paddingTop(theme.spacing.md)
                if (index > 0) borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
            }
            Text {
                attr {
                    text(item.first)
                    fontSize(theme.type.sm)
                    lineHeightScaled(19f)
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text("${item.second}  ${item.third}")
                    marginTop(5f)
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                }
            }
        }
    }
}

// ───────────────────────── F3 研报评级光谱（真实数据 + 演示兜底） ─────────────────────────

/**
 * 真实评级光谱（东财研报库近 90 天 emRatingName 聚合）→ BalanceSegment；
 * 在线缺失/无覆盖时回落演示段（维持原占位形态与「示例 · 演示数据」标注），光谱不空转。
 */
internal fun balanceSegmentsFor(spectrum: RatingSpectrum?, theme: StockChatTheme): List<BalanceSegment> {
    val real = spectrum?.segments
        ?.takeIf { it.isNotEmpty() }
        ?.map { seg -> BalanceSegment(seg.label, seg.count, ratingSpectrumColor(seg.label, theme), seg.quote) }
    return real ?: listOf(
        BalanceSegment("买入", 4, theme.rise, "示例 · 演示数据：偏多观点的占位引用，仅用于展示评级光谱交互，不构成任何建议。"),
        BalanceSegment("增持", 3, theme.rise.opacity(0.55f), "示例 · 演示数据：谨慎看多的占位引用，观点切换仅为形态演示。"),
        BalanceSegment("中性", 2, theme.textTertiary, "示例 · 演示数据：中性观点的占位引用，等待更多数据验证。"),
        BalanceSegment("减持", 1, theme.fall, "示例 · 演示数据：偏空观点的占位引用，仅展示光谱另一端。"),
    )
}

/** 档位配色：多端看涨红/看跌绿，中性走次要文本色。 */
private fun ratingSpectrumColor(label: String, theme: StockChatTheme) = when (label) {
    "买入" -> theme.rise
    "增持" -> theme.rise.opacity(0.55f)
    "减持" -> theme.fall
    else -> theme.textTertiary
}
