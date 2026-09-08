package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openChatWithQuestion
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.AlertKind
import com.kuikly.stockchat.data.AlertMessage
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.RiskSnapshot
import com.kuikly.stockchat.data.provider.CalendarEventKind
import com.kuikly.stockchat.data.provider.LimitUpStock
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.data.provider.quoteLabel
import com.kuikly.stockchat.data.storage.PagerKeyValueStorage
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.RiskSkyChart
import com.kuikly.stockchat.page.components.RiskSkyTimeBrush
import com.kuikly.stockchat.page.risk.SkyLayer
import com.kuikly.stockchat.page.risk.StarMemberIn
import com.kuikly.stockchat.page.risk.StarLayout
import com.kuikly.stockchat.page.risk.TimeBrushLayout
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 风险地图页 v1（doc 24 §6.2）：自选股的共同暴露解释器。
 *
 * 定位（doc 23 合规红线）：**不做风险的裁判，做风险的翻译器**——
 * 解释「押注了哪些共同变量」，不判定等级、不打分、不匹配。
 * 页面里没有「风险承受力 / 匹配 / 评分」词汇，口径角标「等权估算 · 非真实仓位」常驻。
 *
 * 层次结构（z 轴从后到前）：
 * - z0 氛围底：**中性色**（风险页不该制造情绪，不用涨跌色），L1 结论 + 口径角标。
 * - z3 主卡玻璃（唯一，条件出现）：行业重叠——单链集中被判定时，该维度从平铺面板
 *   上浮为主卡（「命中判定的段上浮，其余下沉」，本页层次的核心表达）。
 * - z1/z2 维度面板：集中度（市值加权等级条）、相关性（N≤12 共现矩阵，>12 降级
 *   Top5 配对等级条）、波动暴露（组合 vs 大盘同轴双条）、事件时间轴（竖向）、情绪暴露。
 *
 * AI 触点预算（本页最严）：常驻 0，条件触发 ≤3（一行内联注释，规则生成的事实句，
 * 品牌蓝左侧 2px 竖线）。行情平静时 = 0。
 *
 * 数据链：行情/日K走 QuoteRepository 三级降级链；行业走东财批量接口（一次请求整份
 * 自选，失败静默降级为「未分类」不阻塞其他维度）；事件走财报预约日历；连板走涨停池。
 *
 * 联动契约（doc 23 §6.2，必须遵守）：
 * - **C-1 自选是风险唯一输入**：本页不持有标的来源，全部从 WatchlistStore 读。
 * - **C-2 术语触发单点收口**：维度标题的术语出口只调 GlossaryStore.encounter，不自行记录。
 * - **C-3 AI 预算分账**：本页 ≤3 处、全条件触发、无常驻。
 * - **C-4 口径诚实**：等权估算/数据模式/快照口径在**该处**标注，不只页头一行。
 *
 * doc 30：本页是预警收件箱的生产端（事件/暴露变化 → putExtraMessage）。
 * 事件时间轴行与暴露快照历史面板只写不读：把已发生事实组装成 pinned EVENT /
 * EXPOSURE 消息写入 alertInboxStore，是否「已转/已生成」的显示态由构建期读一次的
 * extraMessages() id 集合驱动（convertedEventIds / generatedExposureIds）。
 */
@Page(Routes.RISK, supportInLocal = true)
internal class RiskMapPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val watchlistStore get() = dependencies.watchlistStore
    private val quoteRepository get() = dependencies.quoteRepository
    private val insightRepository get() = dependencies.insightRepository
    private val glossaryStore get() = dependencies.glossaryStore
    private val riskSnapshotStore get() = dependencies.riskSnapshotStore

    private data class RiskRow(
        val symbol: String,
        val name: String,
        val quote: Quote? = null,
    )

    private var rows: ObservableList<RiskRow> by observableList()
    private var indexQuote: Quote? by observable(null)
    private var industries: Map<String, String> by observable(emptyMap())
    private var events: List<MarketCalendarEvent> by observable(emptyList())
    private var limitUps: List<Pair<LimitUpStock, RiskRow>> by observable(emptyList())
    private var dataModeLabel: String by observable("")

    // ── 星图模式（doc 32 §6.1）状态：图层/选中/时间刷/引路星脉冲 ──

    /**
     * 当前投影图层。持久化 key [SKY_LAYER_KEY]（返回态保持）。
     * 改名只能走 [applySkyLayer]——不能定义 setSkyLayer 函数（与属性委托生成的
     * JVM setter 签名冲突，Platform declaration clash，doc 32 §6.1 实测坑）。
     */
    private var skyLayer: SkyLayer by observable(SkyLayer.CLUSTER)

    /** 选中星（symbol）；空 = 未选中。点同星 = 取消。 */
    private var skySelectedSymbol: String by observable("")

    /** 选中团域（行业名）；空 = 未选中。 */
    private var skySelectedCluster: String by observable("")

    /** 引路星解读抽屉展开态。 */
    private var skyBeaconDrawer: Boolean by observable(false)

    /** 时间刷 knob 连续位置 0..1（日程层）。 */
    private var skyKnobFrac: Float by observable(0f)

    /** 时间刷命中的事件日下标（-1 = 未命中；日程层命中圈跟着亮）。 */
    private var skyCalDay: Int by observable(-1)

    /** 两两相关系数（key "A|B"，A 在列表序在前）；星图连线数据源。 */
    private var correlations: Map<String, Double> by observable(emptyMap())

    /** 个股日波动 ÷ 沪深300 日波动（等权口径）；颠簸层光晕数据源。 */
    private var volRatios: Map<String, Double> by observable(emptyMap())

    /** 引路星/颠簸光晕脉冲相位 0..1（12 步 × 55ms 步进，reduceMotion 恒 0）。 */
    private var beaconPhase: Float by observable(0f)

    /** LINK 层拖星的瞬态偏移；布局本身不变，松手后回弹至 0。 */
    private var skyDragOffsets: Map<String, Pair<Float, Float>> by observable(emptyMap())
    private var skyDragReturnGeneration = 0
    private var skyContextSymbol: String by observable("")

    /** 脉冲步进器运行标记（图层离开脉冲层自动停摆，切回由 applySkyLayer 重启）。 */
    private var pulseRunning = false

    private val skyStorage by lazy { PagerKeyValueStorage(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    /** 相关性矩阵选中的配对 "symA|symB"；空 = 未选中。 */
    private var selectedPair: String by observable("")

    /**
     * 构建期读一次 alertInboxStore.extraMessages() 得到的 id 集合（EVENT 类）。
     * 事件时间轴据此判定某行事件「是否已转预警」，转成功后同步追加 id 触发刷新。
     */
    private var convertedEventIds: Set<String> by observable(emptySet())

    /**
     * 同上，EXPOSURE 类 id 集合（"EXPOSURE:<capturedAtMillis>"）。
     * 暴露快照面板据此判定「是否已被生成预警」。
     */
    private var generatedExposureIds: Set<String> by observable(emptySet())

    /**
     * 「转预警」后的瞬时提示，时间轴底部一行 brand 色文案。
     * 置 "已加入预警收件箱 ✓" 后 2.5s 经 setTimeout 清空；
     * 清空用同值 early-return 规避：先写 "" 由 vif(isNotEmpty) 让视图消失即可。
     */
    private var eventToInboxHint: String by observable("")

    override fun created() {
        super.created()
        // 返回态保持：恢复上次图层（doc 32 §3.1 P0 增强）。
        SkyLayer.fromName(skyStorage.getString(SKY_LAYER_KEY))?.let { skyLayer = it }
        reload()
        // 离线/缓存行情先行可算一次（在线加载回调里会再刷）。
        refreshSkyData()
        ensureBeaconPulse()
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    // 竖向 Scroller 水平 padding 会被双倍扣除，14/14 时右侧多出 28dp 留白；
                    // 右 padding 留 0，左右各 14dp 对齐（同 ChatPage）。
                    paddingLeft(14f)
                    paddingRight(0f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(32f)
                }

                vif({ page.rows.isEmpty() }) {
                    View {
                        attr {
                            marginTop(24f)
                            padding(20f)
                            borderRadius(16f)
                            backgroundColor(page.theme.surface)
                        }
                        Text {
                            attr {
                                text("风险地图的输入是你的自选")
                                fontSize(15f)
                                fontWeightSemiBold()
                                color(page.theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("先在自选页添加几只股票，这里才能画出它们共享了哪些变量。不采集任何仓位信息，全部按等权估算。")
                                marginTop(10f)
                                fontSize(12.5f)
                                lineHeight(19f)
                                color(page.theme.textSecondary)
                            }
                        }
                        View {
                            attr {
                                marginTop(14f)
                                alignSelfFlexStart()
                                paddingLeft(14f)
                                paddingRight(14f)
                                height(30f)
                                allCenter()
                                borderRadius(9f)
                                backgroundColor(page.theme.brandSoft)
                            }
                            Text {
                                attr {
                                    text("去自选页看看")
                                    fontSize(12f)
                                    color(page.theme.brand)
                                }
                            }
                            event { click { page.closePage() } }
                        }
                    }
                }

                vif({ page.rows.isNotEmpty() }) {
                    page.renderRiskMap(this)
                }
            }
            AppTopBar(
                title = "风险地图",
                subtitle = "你的自选押注了哪些共同变量",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
        }
    }

    // ── 主渲染 ──

    private fun renderRiskMap(container: ViewContainer<*, *>) {
        val page = this
        val industry = page.industryStats()
        val chain = page.chainConcentration(industry)
        container.View {
            attr { marginTop(6f) }

            // doc 32 §5 降级分档：≥3 只自选走星图主卡（档 1）；
            // 1–2 只回落六面板列表投影（档 3，v1 全保留）。rows 在 created() 内同步
            // 装满、行情加载只替换元素不改 size，构建期判定稳定。
            if (page.rows.size >= 3) {
                page.renderSkyMode(this, industry, chain)
            } else {
                page.renderHeadline(this, industry, chain, withIndustryCard = true)
                page.renderPanels(this)
            }

            // FR-R10 暴露快照：≥2 条才有「变化」可看，单点不渲染（两种模式共用）。
            page.renderSnapshotHistory(this)

            // FR-R9 跑输大盘归因通路（条件出现）：本页只给事实差值 + 提问出口，
            // 归因本体复用对话里的 attribution 卡片（AttributionIntent），不自行编造原因。
            vif({ page.underperformGapPct() != null }) {
                page.renderAttributionEntry(this)
            }

            Text {
                attr {
                    text("本页只翻译共同暴露，不判定风险等级，不构成投资建议。")
                    marginTop(16f)
                    fontSize(11f)
                    lineHeight(17f)
                    color(page.theme.textTertiary)
                }
            }
        }
    }

    /** v1 六面板列表投影（降级档 3）：行业/集中度/相关性/波动/事件/情绪。 */
    private fun renderPanels(container: ViewContainer<*, *>) {
        val page = this
        val industry = page.industryStats()
        val chain = page.chainConcentration(industry)

        // 行业未触发单链判定 → 常规面板（z1）。termKey = FR-R6 术语出口。
        vif({ !chain.triggered }) {
            page.renderDimensionPanel(this, "行业重叠", termKey = "SECTOR") { body ->
                page.renderIndustryContent(body, industry, chain, elevated = false)
            }
        }

        page.renderDimensionPanel(container, "集中度", termKey = "HHI") { body ->
            page.renderConcentration(body)
        }

        page.renderDimensionPanel(container, "相关性", termKey = "CORRELATION") { body ->
            page.renderCorrelation(body)
        }

        page.renderDimensionPanel(container, "波动暴露", termKey = "VOLATILITY") { body ->
            page.renderVolatility(body)
        }

        page.renderDimensionPanel(container, "事件时间轴", termKey = "UNLOCK") { body ->
            page.renderEventTimeline(body)
        }

        page.renderDimensionPanel(container, "情绪暴露", termKey = "SENTIMENT") { body ->
            page.renderSentiment(body)
        }
    }

    /**
     * z0 氛围底：中性色 + L1 结论 + 常驻口径角标。
     * [withIndustryCard] = v1 模式下行业重叠单链集中时上浮 z3 主卡玻璃（唯一）；
     * 星图模式下 z3 唯一玻璃让位给星图主卡，不再重复上浮。
     */
    private fun renderHeadline(
        container: ViewContainer<*, *>,
        industry: List<IndustryStat>,
        chain: ChainConcentration,
        withIndustryCard: Boolean,
    ) {
        val page = this
        val total = page.rows.size
        container.View {
            attr {
                paddingTop(18f)
                paddingBottom(24f)
                paddingLeft(12f)
                paddingRight(12f)
                borderRadius(20f)
                backgroundColor(page.theme.surfaceMuted)
            }
            Text {
                attr {
                    text(page.headline(industry, chain))
                    fontSize(18f)
                    fontWeightSemiBold()
                    lineHeight(26f)
                    color(page.theme.textPrimary)
                }
            }
            Text {
                attr {
                    text("等权估算 · 非真实仓位 · 共 ${total} 只自选")
                    marginTop(8f)
                    fontSize(10f)
                    color(page.theme.textTertiary)
                }
            }
            vif({ page.dataModeLabel.isNotEmpty() }) {
                Text {
                    attr {
                        text(page.dataModeLabel)
                        marginTop(3f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
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
                            backgroundColor(page.theme.marketGlass)
                            boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.10f)))
                        }
                        page.renderIndustryContent(this, industry, chain, elevated = true)
                    }
                }
            }
        }
    }

    // ── 星图模式（doc 32：一张图，多图层）──

    /**
     * 星图主卡（≥3 只自选的档 1 形态）：图层 chips + 五投影 Canvas + 焦点注释 +
     * 成员抽屉 + 引路星解读抽屉；日程层带时间刷。z3 唯一玻璃卡。
     * AI 预算（doc 32 §3）：引路星（条件 ≤2）+ 焦点注释（条件 1），平静且无选中 = 0。
     */
    private fun renderSkyMode(
        container: ViewContainer<*, *>,
        industry: List<IndustryStat>,
        chain: ChainConcentration,
    ) {
        val page = this
        page.renderHeadline(container, industry, chain, withIndustryCard = false)
        container.View {
            attr {
                marginTop(10f)
                padding(16f)
                borderRadius(16f)
                backgroundColor(page.theme.marketGlass)
                boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.10f)))
            }

            // 图层 chips（拇指区）：单 observable driver（skyLayer）。
            View {
                attr { flexDirectionRow(); flexWrapWrap() }
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
                            backgroundColor(if (page.skyLayer == layer) page.theme.brandSoft else page.theme.surfaceMuted)
                        }
                        event { click { page.applySkyLayer(layer) } }
                        Text {
                            attr {
                                text(layer.label)
                                fontSize(11.5f)
                                color(if (page.skyLayer == layer) page.theme.brand else page.theme.textSecondary)
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
                        text(page.skyLayerTip())
                        fontSize(10.5f)
                        lineHeight(16f)
                        color(page.theme.textTertiary)
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
                        backgroundColor(page.theme.brandSoft)
                    }
                    event {
                        click {
                            page.glossaryStore.encounter(page.skyLayer.termKey)
                            page.openPage(Routes.GLOSSARY)
                        }
                    }
                    Text {
                        attr {
                            text("这是什么 ›")
                            fontSize(10f)
                            color(page.theme.brand)
                        }
                    }
                }
            }

            // 星图 Canvas（五投影同图，切层星星不换位置）。
            View {
                attr { marginTop(8f) }
                RiskSkyChart(
                    theme = page.theme,
                    geometry = { page.skyGeometry() },
                    correlations = { page.correlations },
                    layer = { page.skyLayer },
                    selectedSymbol = { page.skySelectedSymbol },
                    beaconClusterIndex = { page.skyBeaconClusterIndex() },
                    beaconPhase = { page.beaconPhase },
                    volRatioOf = { symbol -> page.volRatios[symbol] },
                    boardsOf = { symbol ->
                        page.limitUps.firstOrNull { it.second.symbol == symbol }?.first?.consecutiveBoards ?: 0
                    },
                    changePercentOf = { symbol ->
                        page.rows.firstOrNull { it.symbol == symbol }?.quote?.changePercent
                    },
                    eventsOf = { page.skyEventsOf(it) },
                    calendarDayLabel = { page.skyDayLabel() },
                    dragOffsets = { page.skyDragOffsets },
                    canvasHeight = { page.skyGeometry().requiredHeight },
                    reduceMotion = page.reduceMotion,
                    onTapStar = { page.onSkyStarTap(it) },
                    onTapBeacon = { page.onSkyBeaconTap() },
                    onTapCluster = { page.onSkyClusterTap(it) },
                    onTapBlank = { page.onSkyBlankTap() },
                    onDragStar = { symbol, dx, dy -> page.onSkyStarDrag(symbol, dx, dy) },
                    onDragEnd = { page.onSkyStarDragEnd() },
                    onLongPressStar = { page.onSkyStarLongPress(it) },
                )
            }

            // 日程层：时间刷 + 「那天」一句事实。
            vif({ page.skyLayer == SkyLayer.SCHEDULE }) {
                page.renderSkySchedule(this)
            }

            // 焦点注释（Spotlight，预算 1）：选中即浮现，取消即收起。
            vif({ page.skySelectedSymbol.isNotEmpty() }) {
                vbind({ page.skySelectedSymbol }) {
                    page.renderAnnotation(this, page.skyFocusNote(page.skySelectedSymbol))
                }
            }

            // 成员抽屉（就地展开面板，非底部 sheet——vif 新视图做不了入场动画，R4）。
            vif({ page.skySelectedSymbol.isNotEmpty() }) {
                vbind({ page.skySelectedSymbol }) {
                    page.renderSkyStarDrawer(this)
                }
            }
            vif({ page.skySelectedCluster.isNotEmpty() }) {
                vbind({ page.skySelectedCluster }) {
                    page.renderSkyClusterDrawer(this, page.skySelectedCluster)
                }
            }

            // 长按星才出现的 Context Bar：不占平静态页面空间。
            vif({ page.skyContextSymbol.isNotEmpty() }) {
                vbind({ page.skyContextSymbol }) { page.renderSkyContextBar(this) }
            }

            // 引路星解读抽屉（点击光环升起：3 行规则事实 + 追问出口）。
            vif({ page.skyBeaconDrawer }) {
                page.renderSkyBeaconDrawer(this, chain)
            }
        }
    }

    /** 日程层时间刷 + 命中日事实句（无事件日显示「正常走」）。 */
    private fun renderSkySchedule(container: ViewContainer<*, *>) {
        val page = this
        if (page.skyEventDays().isEmpty()) {
            container.Text {
                attr {
                    text("未来 30 天自选没有已预约事件，正常走。")
                    marginTop(10f)
                    fontSize(10.5f)
                    color(page.theme.textTertiary)
                }
            }
            return
        }
        container.View {
            attr { marginTop(10f) }
            RiskSkyTimeBrush(
                theme = page.theme,
                dayLabels = { page.skyEventDays().map { it.substring(5) } },
                knobFraction = { page.skyKnobFrac },
                selectedIndex = { page.skyCalDay },
                containerWidth = page.skyContainerWidth(),
                onScrubFraction = { frac ->
                    page.skyKnobFrac = frac
                    page.skyCalDay = TimeBrushLayout.nearestIndexForFraction(frac, page.skyEventDays().size)
                },
                onRelease = { dragged, index ->
                    if (!dragged && page.skyCalDay == index) {
                        // 已选日再点 = 取消命中。
                        page.skyCalDay = -1
                    } else {
                        page.skyCalDay = index
                        page.skyKnobFrac = TimeBrushLayout.fractionForIndex(index, page.skyEventDays().size)
                    }
                },
            )
            Text {
                attr {
                    text(page.skyCalSentence())
                    marginTop(6f)
                    fontSize(10.5f)
                    lineHeight(16f)
                    color(page.theme.textTertiary)
                }
            }
        }
    }

    /** 单星成员抽屉：一行摘要（两次点击到详情：星 → 抽屉 → 详情）。 */
    private fun renderSkyStarDrawer(container: ViewContainer<*, *>) {
        val page = this
        val symbol = page.skySelectedSymbol
        val row = page.rows.firstOrNull { it.symbol == symbol } ?: return
        container.View {
            attr {
                marginTop(10f)
                paddingLeft(12f)
                paddingRight(12f)
                paddingTop(10f)
                paddingBottom(10f)
                borderRadius(12f)
                backgroundColor(page.theme.surfaceMuted)
                flexDirectionRow()
                alignItemsCenter()
            }
            event { click { page.openStockDetail(row.symbol, Routes.RISK) } }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(row.name)
                        fontSize(12f)
                        color(page.theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(page.skyStarSub(row))
                        marginTop(2f)
                        fontSize(10.5f)
                        lineHeight(15f)
                        color(page.theme.textTertiary)
                    }
                }
            }
            val pct = row.quote?.changePercent
            Text {
                attr {
                    text(pct?.let { Format.percent(it) } ?: "--")
                    fontSize(12f)
                    color(if ((pct ?: 0.0) >= 0) page.theme.rise else page.theme.fall)
                }
            }
            Text {
                attr {
                    text("进详情 ›")
                    marginLeft(10f)
                    fontSize(11f)
                    color(page.theme.brand)
                }
            }
        }
    }

    /** 团域成员抽屉：同链成员 chip 流，点 chip 进详情。 */
    private fun renderSkyClusterDrawer(container: ViewContainer<*, *>, clusterName: String) {
        val page = this
        val members = page.rows.filter {
            (page.industries[it.symbol]?.takeIf { n -> n.isNotBlank() } ?: "未分类") == clusterName
        }
        if (members.isEmpty()) return
        container.View {
            attr {
                marginTop(10f)
                padding(12f)
                borderRadius(12f)
                backgroundColor(page.theme.surfaceMuted)
            }
            Text {
                attr {
                    text("「${clusterName}」的 ${members.size} 只成员")
                    fontSize(11f)
                    fontWeightSemiBold()
                    color(page.theme.textPrimary)
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
                            backgroundColor(page.theme.surface)
                        }
                        event { click { page.openStockDetail(row.symbol, Routes.RISK) } }
                        Text {
                            attr {
                                text(row.name)
                                fontSize(10.5f)
                                color(page.theme.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 引路星解读抽屉：3 行规则事实 + 追问出口（复用对话通路，零 LLM）。 */
    private fun renderSkyBeaconDrawer(container: ViewContainer<*, *>, chain: ChainConcentration) {
        val page = this
        val total = page.rows.size
        container.View {
            attr {
                marginTop(10f)
                padding(12f)
                borderRadius(12f)
                backgroundColor(page.theme.surfaceMuted)
            }
            Text {
                attr {
                    text("为什么圈住这团")
                    fontSize(11f)
                    fontWeightSemiBold()
                    color(page.theme.textPrimary)
                }
            }
            listOf(
                "「${chain.topName}」${chain.topCount} 只同属一条链，占 ${page.weightLabel(chain.topCount, total)}（等权估算）",
                "前三大行业合计 ${page.weightLabel(chain.cr3Count, total)}，是这张图里最挤的一片",
                "行业归属来自公开行业分类 · 非你的真实仓位",
            ).forEach { fact ->
                Text {
                    attr {
                        text(fact)
                        marginTop(6f)
                        fontSize(10.5f)
                        lineHeight(16f)
                        color(page.theme.textSecondary)
                    }
                }
            }
            View {
                attr { marginTop(8f) }
                event {
                    click {
                        page.openChatWithQuestion(
                            "我的自选里「${chain.topName}」的这几只为什么经常一起涨跌？",
                            "来自风险地图：星团「${chain.topName}」",
                        )
                    }
                }
                Text {
                    attr {
                        text("问一句「为什么经常一起涨跌」 ›")
                        fontSize(11f)
                        color(page.theme.brand)
                    }
                }
            }
        }
    }

    /** z1 维度面板外壳：弱化底色、12.5f 标题、内容自绘（每个维度的可视化形态都不同）。
     *  FR-R6 术语内联出口：标题对应的术语可点 → 记一笔「遇到」→ 跳知识库。
     *  联动契约 C-2：术语触发统一走 GlossaryStore.encounter，此处不自行记录。 */
    private fun renderDimensionPanel(
        container: ViewContainer<*, *>,
        title: String,
        termKey: String = "",
        content: (ViewContainer<*, *>) -> Unit,
    ) {
        val page = this
        container.View {
            attr {
                marginTop(10f)
                padding(14f)
                borderRadius(14f)
                backgroundColor(page.theme.surface)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(title)
                        fontSize(13.5f)
                        fontWeightSemiBold()
                        color(page.theme.textPrimary)
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
                                backgroundColor(page.theme.brandSoft)
                            }
                            event {
                                click {
                                    page.glossaryStore.encounter(termKey)
                                    page.openPage(Routes.GLOSSARY)
                                }
                            }
                            Text {
                                attr {
                                    text("这是什么 ›")
                                    fontSize(10f)
                                    color(page.theme.brand)
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

    private fun renderIndustryContent(
        container: ViewContainer<*, *>,
        industry: List<IndustryStat>,
        chain: ChainConcentration,
        elevated: Boolean,
    ) {
        val page = this
        val total = page.rows.size
        if (industry.isEmpty()) {
            container.Text {
                attr {
                    text("行业归属暂不可用（离线或接口失败），其他维度不受影响。")
                    marginTop(8f)
                    fontSize(11.5f)
                    lineHeight(17f)
                    color(page.theme.textTertiary)
                }
            }
            return
        }
        val top = industry.first()
        container.Text {
            attr {
                text("${top.count} 只同属「${top.name}」，占 ${page.weightLabel(top.count, total)}")
                marginTop(if (elevated) 4f else 8f)
                fontSize(12.5f)
                color(page.theme.textSecondary)
            }
        }

        // 层叠堆叠条（不用饼图：≥5 分类不可读且无法承载成员 chip）。
        container.View {
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
                        backgroundColor(page.industrySegmentColor(index))
                    }
                }
            }
            View {
                attr {
                    flex((total - industry.take(3).sumOf { it.count }).toFloat() / total)
                    height(8f)
                    backgroundColor(page.theme.divider)
                }
            }
        }

        // 段图例 + 段内成员 chip。
        container.View {
            attr { marginTop(8f); flexDirectionRow(); flexWrapWrap() }
            industry.take(3).forEachIndexed { index, stat ->
                View {
                    attr { marginRight(10f); alignItemsCenter(); flexDirectionRow() }
                    View {
                        attr {
                            width(7f)
                            height(7f)
                            borderRadius(2f)
                            backgroundColor(page.industrySegmentColor(index))
                        }
                    }
                    Text {
                        attr {
                            text("${stat.name} ${page.weightLabel(stat.count, total)}")
                            marginLeft(4f)
                            fontSize(10f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("其他 ${page.weightLabel(total - industry.take(3).sumOf { it.count }, total)}"
                    )
                    fontSize(10f)
                    color(page.theme.textTertiary)
                }
            }
        }
        container.View {
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
                        backgroundColor(page.theme.surfaceMuted)
                    }
                    Text {
                        attr {
                            text(row.name)
                            fontSize(10.5f)
                            color(page.theme.textSecondary)
                        }
                    }
                    event { click { page.openStockDetail(row.symbol, Routes.RISK) } }
                }
            }
        }

        // AI 触点 #1（条件）：CR3 > 60% 才出现的一行事实注释。
        vif({ chain.cr3 > 0.60 }) {
            page.renderAnnotation(
                container,
                "前三大行业合计 ${page.weightLabel(chain.cr3Count, total)}——同一变量一波动，这几只常常一起动",
            )
        }
    }

    // ── ② 集中度：市值加权 Top3 等级条 ──

    private fun renderConcentration(container: ViewContainer<*, *>) {
        val page = this
        val totalCap = page.rows.mapNotNull { it.quote?.marketCap }.filter { it > 0 }.sum()
        val byCap = page.rows
            .mapNotNull { row -> row.quote?.marketCap?.takeIf { it > 0 }?.let { row.name to it } }
            .sortedByDescending { it.second }
        if (byCap.size < 3 || totalCap <= 0.0) {
            container.Text {
                attr {
                    text("市值数据不足（离线或停牌），暂无法计算集中度。")
                    marginTop(8f)
                    fontSize(11.5f)
                    color(page.theme.textTertiary)
                }
            }
            return
        }
        val top3 = byCap.take(3)
        val top3Share = top3.sumOf { it.second } / totalCap
        container.Text {
            attr {
                text("市值加权口径（非你的真实仓位）：Top3 占 ${page.weightLabel(top3Share)}")
                marginTop(8f)
                fontSize(11.5f)
                color(page.theme.textSecondary)
            }
        }
        top3.forEach { (name, cap) ->
            val share = cap / totalCap
            container.View {
                attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(name)
                        width(64f)
                        fontSize(11f)
                        color(page.theme.textSecondary)
                    }
                }
                View {
                    attr { flex(1f); height(6f); borderRadius(3f); backgroundColor(page.theme.surfaceMuted) }
                    View {
                        attr {
                            flex(share.toFloat())
                            height(6f)
                            borderRadius(3f)
                            backgroundColor(page.theme.flat)
                        }
                    }
                }
                Text {
                    attr {
                        text(page.weightLabel(share))
                        marginLeft(8f)
                        width(38f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
            }
        }
    }

    // ── ③ 相关性：N≤12 共现矩阵（明度阶梯），>12 降级 Top5 配对 ──

    private fun renderCorrelation(container: ViewContainer<*, *>) {
        val page = this
        val returns = page.rows.mapNotNull { row ->
            page.dailyReturns(row.quote)?.let { Triple(row.symbol, row.name, it) }
        }
        val withBars = returns.filter { it.third.size >= MIN_RETURN_DAYS }
        if (withBars.size < 2) {
            container.Text {
                attr {
                    text("日K数据不足（离线或新股），相关性暂不可算。")
                    marginTop(8f)
                    fontSize(11.5f)
                    color(page.theme.textTertiary)
                }
            }
            return
        }
        if (withBars.size <= 12) {
            page.renderCorrelationMatrix(container, withBars)
        } else {
            page.renderTopPairs(container, withBars)
        }
    }

    private fun renderCorrelationMatrix(
        container: ViewContainer<*, *>,
        withBars: List<Triple<String, String, Map<String, Double>>>,
    ) {
        val page = this
        container.Text {
            attr {
                text("近 ${CORRELATION_WINDOW} 个交易日 · 点格子看配对解读 · 色深 = 同涨同跌程度")
                marginTop(8f)
                fontSize(10.5f)
                color(page.theme.textTertiary)
            }
        }
        withBars.forEach { (rowSymbol, rowName, rowReturns) ->
            container.View {
                attr { marginTop(4f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(rowName)
                        width(52f)
                        fontSize(9f)
                        color(page.theme.textTertiary)
                    }
                }
                withBars.forEach { (colSymbol, _, colReturns) ->
                    val r = if (rowSymbol == colSymbol) 1.0 else page.pearson(rowReturns, colReturns) ?: 0.0
                    View {
                        attr {
                            width(20f)
                            height(20f)
                            marginLeft(1.5f)
                            marginRight(1.5f)
                            borderRadius(4f)
                            backgroundColor(page.correlationColor(r))
                        }
                        event {
                            click {
                                page.selectedPair = if (rowSymbol == colSymbol) {
                                    ""
                                } else {
                                    "$rowSymbol|$colSymbol"
                                }
                            }
                        }
                    }
                }
            }
        }
        // 选中格子的配对解读（S 层：一次点击获得两票关系，不跳页）。
        vif({ page.selectedPair.isNotEmpty() }) {
            val (a, b) = page.selectedPair.split("|")
            val rowA = withBars.firstOrNull { it.first == a }
            val rowB = withBars.firstOrNull { it.first == b }
            vif({ rowA != null && rowB != null }) {
                val r = page.pearson(rowA!!.third, rowB!!.third)
                val sameDirection = page.sameDirectionDays(rowA.third, rowB.third)
                container.View {
                    attr {
                        marginTop(10f)
                        padding(10f)
                        borderRadius(10f)
                        backgroundColor(page.theme.surfaceMuted)
                    }
                    Text {
                        attr {
                            text("${rowA.second} ↔ ${rowB.second}：相关系数 ${page.coefficientLabel(r)}，同期 ${sameDirection} 天同向")
                            fontSize(11.5f)
                            lineHeight(17f)
                            color(page.theme.textSecondary)
                        }
                    }
                    Text {
                        attr {
                            text("在知识库查看「相关系数」是什么意思 ›")
                            marginTop(6f)
                            fontSize(10.5f)
                            color(page.theme.brand)
                        }
                    }
                    event { click { page.openPage(Routes.GLOSSARY) } }
                }
            }
        }
    }

    private fun renderTopPairs(
        container: ViewContainer<*, *>,
        withBars: List<Triple<String, String, Map<String, Double>>>,
    ) {
        val page = this
        val pairs = buildList {
            withBars.forEachIndexed { i, a ->
                withBars.drop(i + 1).forEach { b ->
                    page.pearson(a.third, b.third)?.let { r ->
                        add(Triple(a.second to b.second, r, page.sameDirectionDays(a.third, b.third)))
                    }
                }
            }
        }.sortedByDescending { abs(it.second) }.take(5)
        container.Text {
            attr {
                text("标的数超过 12，降级为同涨同跌程度最高的 5 组配对")
                marginTop(8f)
                fontSize(10.5f)
                color(page.theme.textTertiary)
            }
        }
        pairs.forEach { (names, r, sameDays) ->
            container.View {
                attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("${names.first} ↔ ${names.second}")
                        fontSize(11f)
                        color(page.theme.textSecondary)
                    }
                }
                View { attr { flex(1f) } }
                Text {
                    attr {
                        text("${page.coefficientLabel(r)} · ${sameDays} 天同向")
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
            }
        }
    }

    // ── ④ 波动暴露：组合 vs 大盘同轴双条 ──

    private fun renderVolatility(container: ViewContainer<*, *>) {
        val page = this
        val members = page.rows.mapNotNull { page.dailyReturns(it.quote) }
        val index = page.dailyReturns(page.indexQuote)
        val portfolioStd = page.portfolioStd()
        val indexStd = index?.let { page.stdOf(it.values.toList()) }
        if (portfolioStd == null || indexStd == null || indexStd <= 0.0) {
            container.Text {
                attr {
                    text("日K数据不足，组合与大盘的波动对比暂不可算。")
                    marginTop(8f)
                    fontSize(11.5f)
                    color(page.theme.textTertiary)
                }
            }
            return
        }
        val ratio = portfolioStd / indexStd
        val maxStd = maxOf(portfolioStd, indexStd)
        fun barRow(label: String, std: Double) {
            container.View {
                attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(label)
                        width(84f)
                        fontSize(11f)
                        color(page.theme.textSecondary)
                    }
                }
                View {
                    attr { flex(1f); height(8f); borderRadius(4f); backgroundColor(page.theme.surfaceMuted) }
                    View {
                        attr {
                            flex((std / maxStd).toFloat())
                            height(8f)
                            borderRadius(4f)
                            backgroundColor(page.theme.flat)
                        }
                    }
                }
                Text {
                    attr {
                        text("${Format.price(std * 100)}%")
                        marginLeft(8f)
                        width(46f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
            }
        }
        container.Text {
            attr {
                text("近 ${CORRELATION_WINDOW} 个交易日 · 日收益率标准差（等权）")
                marginTop(8f)
                fontSize(10.5f)
                color(page.theme.textTertiary)
            }
        }
        barRow("你的自选（等权）", portfolioStd)
        barRow("上证指数", indexStd)
        // AI 触点 #2（条件）：比值 > 1.5 才出现。
        vif({ ratio > 1.5 }) {
            page.renderAnnotation(
                container,
                "自选组合的日波动约为大盘的 ${Format.price(ratio)} 倍——涨的时候更快，跌的时候也更急",
            )
        }
    }

    // ── ⑤ 事件时间轴：竖向时间线，非列表 ──

    private fun renderEventTimeline(container: ViewContainer<*, *>) {
        val page = this
        val upcoming = page.futureEvents()
        if (upcoming.isEmpty()) {
            container.Text {
                attr {
                    text("自选标的未来没有已预约的披露事件。")
                    marginTop(8f)
                    fontSize(11.5f)
                    color(page.theme.textTertiary)
                }
            }
            return
        }
        upcoming.forEach { event ->
            // doc 30：每行右端动作。IPO 不产生消息（与 AlertInboxBuilder 同口径），不提供「转预警」。
            val eventId = "EVENT:${event.symbol}:${event.date}"
            val convertible = event.kind != CalendarEventKind.IPO
            container.View {
                attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(event.date.substring(5))
                        width(44f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
                View {
                    attr { alignItemsCenter(); width(10f) }
                    View {
                        attr {
                            width(6f)
                            height(6f)
                            borderRadius(3f)
                            backgroundColor(page.theme.flat)
                        }
                    }
                }
                View {
                    attr { flex(1f) }
                    Text {
                        attr {
                            text("${event.name} · ${event.kind.label}")
                            fontSize(11.5f)
                            color(page.theme.textSecondary)
                        }
                    }
                    Text {
                        attr {
                            text(event.title)
                            marginTop(2f)
                            fontSize(10.5f)
                            color(page.theme.textTertiary)
                        }
                    }
                    event { click { page.openStockDetail(event.symbol, Routes.RISK) } }
                }
                // 右端动作：未转→「转预警 ›」，已转→「已在收件箱 ✓」（doc 30）。
                vif({ convertible }) {
                    vif({ !page.convertedEventIds.contains(eventId) }) {
                        View {
                            attr {
                                marginLeft(8f)
                                paddingLeft(8f)
                                paddingRight(8f)
                                height(24f)
                                allCenter()
                                borderRadius(8f)
                                backgroundColor(page.theme.brandSoft)
                            }
                            Text {
                                attr {
                                    text("转预警 ›")
                                    fontSize(11f)
                                    color(page.theme.brand)
                                }
                            }
                            event {
                                click {
                                    page.convertEventToInbox(event, eventId)
                                }
                            }
                        }
                    }
                    vif({ page.convertedEventIds.contains(eventId) }) {
                        Text {
                            attr {
                                text("已在收件箱 ✓")
                                marginLeft(8f)
                                fontSize(10f)
                                color(page.theme.textTertiary)
                            }
                        }
                    }
                }
            }
        }
        // AI 触点 #3（条件）：未来存在解禁/减持类事件才出现。
        vif({ upcoming.any { it.kind == CalendarEventKind.UNLOCK } }) {
            page.renderAnnotation(
                container,
                "未来 30 天内自选有解禁安排——解禁不等于下跌，但意味着可流通筹码增加",
            )
        }
        // doc 30：转预警成功后的瞬时提示（brand 色，2.5s 后清空）。
        vif({ page.eventToInboxHint.isNotEmpty() }) {
            container.Text {
                attr {
                    text(page.eventToInboxHint)
                    marginTop(10f)
                    fontSize(10.5f)
                    color(page.theme.brand)
                }
            }
        }
    }

    /**
     * doc 30：把单条事件组装成 pinned EVENT 消息写入预警收件箱（只写不读）。
     * id 严格用 "EVENT:$symbol:$date"，与 AlertInboxBuilder 同 id 规则避免重复。
     * 事实句只陈述已发生/已预约事项，不含任何 §1 禁词（风险承受/匹配/建议仓位/调仓等）。
     */
    private fun convertEventToInbox(event: MarketCalendarEvent, eventId: String) {
        val kindLabel = when (event.kind) {
            CalendarEventKind.EARNINGS -> "财报"
            CalendarEventKind.UNLOCK -> "解禁"
            CalendarEventKind.DIVIDEND -> "分红"
            else -> event.kind.label
        }
        val title = when (event.kind) {
            CalendarEventKind.EARNINGS -> "${event.name}：财报预约披露 ${event.date}"
            else -> "${event.name}：${kindLabel}进入 30 天窗口"
        }
        // 事实卡：描述已预约事项，不下结论、不预测、无操作暗示。
        val facts = when (event.kind) {
            CalendarEventKind.UNLOCK -> listOf(
                "解禁日期为 ${event.date}，意味着可流通筹码增加（解禁≠减持，不等于必然下跌）",
                "具体解禁规模以公司公告为准，本页只陈述已发生/已预约事项",
            )
            CalendarEventKind.EARNINGS -> listOf(
                "财报预约披露日期为 ${event.date}（统计描述，不是预测）",
                "披露前后波动可能放大，具体以公司公告为准",
            )
            else -> listOf(
                "${kindLabel}安排于 ${event.date}（统计描述，非预测）",
                "具体以公司公告为准，本页只陈述已发生/已预约事项",
            )
        }
        val msg = AlertMessage(
            id = eventId,
            kind = AlertKind.EVENT,
            symbol = event.symbol,
            name = event.name,
            title = title,
            summary = "${event.name} 的${kindLabel}安排在 ${event.date}（统计描述，非预测）",
            facts = facts,
            createdAtMillis = platformCurrentTimeMillis(),
            askQuestion = "「${event.name}的${kindLabel}意味着什么？」",
            termKey = if (event.kind == CalendarEventKind.UNLOCK) "UNLOCK" else "",
            pinned = true,
        )
        dependencies.alertInboxStore.putExtraMessage(msg)
        // 同步已转 observable，避免回读 store；新 Set 实例触发刷新。
        convertedEventIds = convertedEventIds + eventId
        // 底部瞬时提示：先置文案，2.5s 后经 setTimeout 清空（同值 early-return 规避：先写 "" 由 vif 消失）。
        eventToInboxHint = "已加入预警收件箱 ✓"
        setTimeout(2500) { eventToInboxHint = "" }
    }

    /**
     * doc 30：把最近两次快照的 CR3 / 成员数变化组装成 EXPOSURE 消息写入预警收件箱（只写不读）。
     * id 严格用 "EXPOSURE:<newer.capturedAtMillis>"，与 AlertInboxBuilder 同 id 规则避免重复。
     * 文案用品牌蓝语境、只陈述事实（Top 行业 CR3 / 成员数变化 + 「等权估算」口径），
     * 不带任何「该减仓 / 调仓」暗示（§1 禁词表）。
     */
    private fun generateExposureAlert(older: RiskSnapshot, newer: RiskSnapshot, exposureId: String) {
        val concentrationWord = if (newer.cr3Percent >= older.cr3Percent) "集中" else "分散"
        val msg = AlertMessage(
            id = exposureId,
            kind = AlertKind.EXPOSURE,
            symbol = "",
            name = "组合",
            title = "你的组合比上次更${concentrationWord}了",
            summary = "Top 行业 CR3 ${older.cr3Percent}% → ${newer.cr3Percent}%（等权估算）",
            facts = listOf(
                "Top 行业 CR3：${older.cr3Percent}% → ${newer.cr3Percent}%",
                "成员数：${older.memberCount} → ${newer.memberCount}",
                "变化主因见风险地图 · 等权估算",
            ),
            createdAtMillis = newer.capturedAtMillis,
            askQuestion = "我的自选组合集中度变化说明什么？",
            termKey = "HHI",
            pinned = false,
        )
        dependencies.alertInboxStore.putExtraMessage(msg)
        // 同步已生成 observable，避免回读 store。
        generatedExposureIds = generatedExposureIds + exposureId
    }

    // ── ⑥ 情绪暴露：连板梯队成员 ──

    private fun renderSentiment(container: ViewContainer<*, *>) {
        val page = this
        val members = page.limitUpMembers()
        if (members.isEmpty()) {
            container.Text {
                attr {
                    text("今日自选中没有涨停标的，情绪暴露不明显。")
                    marginTop(8f)
                    fontSize(11.5f)
                    color(page.theme.textTertiary)
                }
            }
            return
        }
        container.Text {
            attr {
                text("自选中有 ${members.size} 只在今日涨停池")
                marginTop(8f)
                fontSize(11.5f)
                color(page.theme.textSecondary)
            }
        }
        members.forEach { member ->
            container.View {
                attr {
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(10f)
                    paddingRight(10f)
                    height(30f)
                    borderRadius(9f)
                    backgroundColor(page.theme.surfaceMuted)
                }
                Text {
                    attr {
                        text(member.second.name)
                        fontSize(11.5f)
                        color(page.theme.textSecondary)
                    }
                }
                Text {
                    attr {
                        text("${member.first.consecutiveBoards} 连板 · ${member.first.sector}")
                        marginLeft(8f)
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
                event { click { page.openStockDetail(member.second.symbol, Routes.RISK) } }
            }
        }
    }

    /** 条件触发的一行事实注释：品牌蓝左侧 2px 竖线，≤28 字，不展开、不放按钮。 */
    private fun renderAnnotation(container: ViewContainer<*, *>, text: String) {
        val page = this
        container.View {
            attr {
                marginTop(10f)
                flexDirectionRow()
            }
            View {
                attr {
                    width(2f)
                    borderRadius(1f)
                    backgroundColor(page.theme.brand)
                }
            }
            Text {
                attr {
                    text(text)
                    marginLeft(8f)
                    fontSize(10.5f)
                    lineHeight(16f)
                    color(page.theme.textSecondary)
                }
            }
        }
    }

    // ── 星图状态与纯映射（无副作用部分尽量薄，几何/刷子映射在 page/risk 可单测）──

    /** 图层切换单点入口：持久化 + 清选中 + 离开日程层清时间刷命中 + 脉冲启停。 */
    private fun applySkyLayer(layer: SkyLayer) {
        if (skyLayer == layer) return
        skyLayer = layer
        skyStorage.setString(SKY_LAYER_KEY, layer.name)
        skySelectedSymbol = ""
        skySelectedCluster = ""
        skyBeaconDrawer = false
        if (layer != SkyLayer.SCHEDULE) skyCalDay = -1
        ensureBeaconPulse()
    }

    private fun skyContainerWidth(): Float = pagerData.pageViewWidth - 28f - 32f

    /** 星→团→星布局（确定性纯函数，输入来自 rows + industries 两个 observable）。 */
    private fun skyGeometry() = StarLayout.layout(
        rows.map { row ->
            StarMemberIn(
                symbol = row.symbol,
                name = row.name,
                industry = industries[row.symbol]?.takeIf { n -> n.isNotBlank() } ?: "未分类",
            )
        },
        skyContainerWidth(),
    )

    /** 重算两两相关系数与波动倍率（行情/日K/指数到达后调用）。 */
    private fun refreshSkyData() {
        val returns = rows.mapNotNull { row ->
            dailyReturns(row.quote)?.let { row.symbol to it }
        }
        val cors = HashMap<String, Double>()
        for (i in returns.indices) {
            for (j in i + 1 until returns.size) {
                pearson(returns[i].second, returns[j].second)?.let { r ->
                    cors["${returns[i].first}|${returns[j].first}"] = r
                }
            }
        }
        correlations = cors
        val indexStd = dailyReturns(indexQuote)?.let { stdOf(it.values.toList()) }
        volRatios = if (indexStd != null && indexStd > 0.0) {
            returns.associate { (symbol, rets) -> symbol to stdOf(rets.values.toList()) / indexStd }
        } else {
            emptyMap()
        }
    }

    /** 引路星指向的团下标（布局把最大团排在下标 0）；单链未判定 = 平静 = 无引路星。 */
    private fun skyBeaconClusterIndex(): Int =
        if (rows.size >= 3 && chainConcentration(industryStats()).triggered) 0 else -1

    private fun skyEventsOf(symbol: String): List<MarketCalendarEvent> {
        val code = symbol.substringBefore('.')
        return events.filter { it.symbol == symbol || it.symbol.substringBefore('.') == code }
    }

    /** 时间刷刻度日（升序去重完整日期串）。 */
    private fun skyEventDays(): List<String> = events.map { it.date }.distinct().sorted()

    /** 日程命中日 "MM-dd"（空 = 未命中）。 */
    private fun skyDayLabel(): String {
        val days = skyEventDays()
        return if (skyCalDay in days.indices) days[skyCalDay].substring(5) else ""
    }

    private fun skyCalSentence(): String {
        val days = skyEventDays()
        if (skyCalDay !in days.indices) return "拖动手柄扫未来 30 天，扫到哪天那颗星就亮"
        val day = days[skyCalDay]
        val dayEvents = events.filter { it.date == day }
        if (dayEvents.isEmpty()) return "那天：自选没有已预约事件，正常走"
        return "那天：" + dayEvents.joinToString("；") { "${it.name} · ${it.kind.label}" }
    }

    private fun skyLayerTip(): String = when (skyLayer) {
        SkyLayer.CLUSTER -> "抱团：圈 = 一条链，圈越大挤得越多 · 光晕 = 单只波动"
        SkyLayer.LINK -> "牵连：线 = 近 ${CORRELATION_WINDOW} 日相关系数，粗亮 = 同涨同跌更狠（|r|≥0.5 才画）"
        SkyLayer.VOLATILITY -> "颠簸：光晕呼吸 = 波动倍率 · 数字 = 日波动 ÷ 沪深300（等权）"
        SkyLayer.SCHEDULE -> "日程：拖下方手柄扫未来 30 天，扫到哪天、那颗星就亮"
        SkyLayer.HEAT -> "热度：环纹一圈 = 一个连板 · 其余星退暗（只看风头上的）"
    }

    // ── 星图手势处理：点按、LINK 层拖星牵引、长按 Context Bar ──

    private fun onSkyStarTap(symbol: String) {
        skyBeaconDrawer = false
        skySelectedCluster = ""
        skyContextSymbol = ""
        skySelectedSymbol = if (skySelectedSymbol == symbol) "" else symbol
    }

    private fun onSkyClusterTap(name: String) {
        skyBeaconDrawer = false
        skySelectedSymbol = ""
        skySelectedCluster = if (skySelectedCluster == name) "" else name
    }

    private fun onSkyBeaconTap() {
        skySelectedSymbol = ""
        skySelectedCluster = ""
        skyBeaconDrawer = !skyBeaconDrawer
    }

    private fun onSkyBlankTap() {
        skySelectedSymbol = ""
        skySelectedCluster = ""
        skyBeaconDrawer = false
        skyContextSymbol = ""
    }

    /** LINK 层牵引：直连星按相关系数比例跟随，负相关反向；全部收口在画布内。 */
    private fun onSkyStarDrag(symbol: String, dx: Float, dy: Float) {
        if (skyLayer != SkyLayer.LINK) return
        skyDragReturnGeneration++
        val g = skyGeometry()
        val width = skyContainerWidth()
        fun clamped(star: com.kuikly.stockchat.page.risk.SkyStar, ox: Float, oy: Float): Pair<Float, Float> {
            // 星名画在星上方 -21f、涨跌幅 +26f，边距再放一档防文字被裁。
            val marginX = StarLayout.STAR_RADIUS + 8f
            val marginY = StarLayout.STAR_RADIUS + 26f
            val nx = (star.x + ox).coerceIn(marginX, (width - marginX).coerceAtLeast(marginX)) - star.x
            val ny = (star.y + oy).coerceIn(marginY, (g.requiredHeight - marginY).coerceAtLeast(marginY)) - star.y
            return nx to ny
        }
        val next = HashMap<String, Pair<Float, Float>>()
        g.stars.firstOrNull { it.symbol == symbol }?.let { next[symbol] = clamped(it, dx, dy) }
            ?: return
        rows.filter { it.symbol != symbol }.forEach { other ->
            val r = StarLayout.lookupCorrelation(correlations, symbol, other.symbol) ?: return@forEach
            if (abs(r) < StarLayout.LINK_MIN_R) return@forEach
            val pulled = g.stars.firstOrNull { it.symbol == other.symbol } ?: return@forEach
            next[other.symbol] = clamped(pulled, dx * r.toFloat() * 0.58f, dy * r.toFloat() * 0.58f)
        }
        skyDragOffsets = next
    }

    /** 松手后在约 0.4 秒内指数回弹；减少动态效果时立即归零。 */
    private fun onSkyStarDragEnd() {
        val version = ++skyDragReturnGeneration
        if (reduceMotion) {
            skyDragOffsets = emptyMap()
            return
        }
        fun rebound(step: Int) {
            if (version != skyDragReturnGeneration) return
            val next = skyDragOffsets.mapValues { (_, value) -> value.first * 0.72f to value.second * 0.72f }
                .filterValues { abs(it.first) > 0.3f || abs(it.second) > 0.3f }
            skyDragOffsets = next
            if (step < 10 && next.isNotEmpty()) setTimeout(40) { rebound(step + 1) }
        }
        rebound(0)
    }

    private fun onSkyStarLongPress(symbol: String) {
        skySelectedCluster = ""
        skyBeaconDrawer = false
        skySelectedSymbol = symbol
        skyContextSymbol = symbol
    }

    /** 当前图层下的长按提问条；问句自然，焦点由 route context 传递。 */
    private fun renderSkyContextBar(container: ViewContainer<*, *>) {
        val page = this
        val symbol = page.skyContextSymbol
        val row = page.rows.firstOrNull { it.symbol == symbol } ?: return
        val layerName = page.skyLayer.label
        val questions = listOf(
            "${row.name}今天为什么这样动？",
            "${row.name}和谁牵连最明显？",
            "${row.name}在${layerName}这层说明什么？",
        )
        container.View {
            attr { marginTop(10f); padding(10f); borderRadius(12f); backgroundColor(page.theme.brandSoft) }
            Text { attr { text("按住「${row.name}」· 想问哪一句？"); fontSize(10.5f); color(page.theme.brand) } }
            View {
                attr { marginTop(7f); flexDirectionRow(); flexWrapWrap() }
                questions.forEach { question ->
                    View {
                        attr {
                            marginRight(6f); marginBottom(5f); paddingLeft(8f); paddingRight(8f); height(25f)
                            allCenter(); borderRadius(8f); backgroundColor(page.theme.surface)
                        }
                        event {
                            click {
                                page.openChatWithQuestion(question, "来自风险地图：星「${row.name}」（${layerName}层）")
                            }
                        }
                        Text { attr { text(question); fontSize(10f); color(page.theme.textSecondary) } }
                    }
                }
            }
        }
    }

    /** 焦点注释（Spotlight）：全端侧模板，数字来自 Provider，零 LLM。 */
    private fun skyFocusNote(symbol: String): String {
        val row = rows.firstOrNull { it.symbol == symbol } ?: return ""
        val strong = rows.count { other ->
            other.symbol != symbol &&
                StarLayout.lookupCorrelation(correlations, symbol, other.symbol)
                    ?.let { abs(it) > 0.6 } == true
        }
        val parts = mutableListOf<String>()
        parts += if (strong > 0) {
            "与 $strong 只相关系数>0.6"
        } else {
            "与谁都不连（近 ${CORRELATION_WINDOW} 日）"
        }
        volRatios[symbol]?.let { parts += "波动 ${Format.decimal(it, 1)}×大盘（等权估算）" }
        val boards = limitUps.firstOrNull { it.second.symbol == symbol }?.first?.consecutiveBoards ?: 0
        if (boards > 0) parts += "今日 $boards 连板"
        skyEventsOf(symbol).firstOrNull()?.let {
            parts += "${it.date.substring(5)} ${it.kind.label}已预约"
        }
        return "「${row.name}」：${parts.joinToString(" · ")}"
    }

    /** 成员抽屉摘要行：行业 + 事件 + 连板 + 波动（有啥写啥，全部事实）。 */
    private fun skyStarSub(row: RiskRow): String {
        val parts = mutableListOf<String>()
        parts += industries[row.symbol]?.takeIf { n -> n.isNotBlank() } ?: "未分类"
        skyEventsOf(row.symbol).firstOrNull()?.let {
            parts += "${it.date.substring(5)} ${it.kind.label}"
        }
        val boards = limitUps.firstOrNull { it.second.symbol == row.symbol }?.first?.consecutiveBoards ?: 0
        if (boards > 0) parts += "$boards 连板"
        volRatios[row.symbol]?.let { parts += "波动 ${Format.decimal(it, 1)}×大盘" }
        return parts.joinToString(" · ")
    }

    /** 引路星/光晕脉冲步进：12 步 × 55ms；仅脉冲层运行，reduceMotion 不启动（doc 32 §6.1）。 */
    private fun ensureBeaconPulse() {
        if (reduceMotion || pulseRunning) return
        pulseRunning = true
        tickBeaconPulse()
    }

    private fun tickBeaconPulse() {
        if (!pulseRunning) return
        val needed = rows.size >= 3 &&
            (skyLayer == SkyLayer.CLUSTER || skyLayer == SkyLayer.VOLATILITY)
        if (!needed) {
            pulseRunning = false
            return
        }
        beaconPhase = (beaconPhase + 1f / BEACON_STEPS) % 1f
        setTimeout(BEACON_STEP_MS.toInt()) { tickBeaconPulse() }
    }

    // ── 数据装载 ──

    private fun reload() {
        // doc 30：构建期读一次收件箱已写消息的 id 集合，驱动「已转/已生成」显示态。
        refreshInboxConverted()
        val items = watchlistStore.list()
        rows.clear()
        items.forEach { item ->
            rows.add(RiskRow(item.symbol, item.name, quoteRepository.cachedOrOffline(item.symbol)))
            quoteRepository.load(item.symbol) { result ->
                val index = rows.indexOfFirst { it.symbol == item.symbol }
                val quote = result.quote
                if (index >= 0 && quote != null) {
                    rows[index] = rows[index].copy(quote = quote)
                }
                dataModeLabel = result.mode.quoteLabel()
            }
        }
        if (items.isEmpty()) return

        // 大盘基准（波动暴露的分母）。
        quoteRepository.load(INDEX_SYMBOL) { result ->
            result.quote?.let { indexQuote = it }
        }

        // 行业归属：一次批量请求；失败降级为空表，不阻塞其他维度。
        insightRepository.loadIndustries(items.map { it.symbol }) { map ->
            industries = map
            captureSnapshotIfDue()
        }

        // 事件时间轴：全市场预约日历 ∩ 自选代码。
        insightRepository.loadCalendar { all ->
            val codes = items.map { it.symbol.substringBefore('.') }.toSet()
            val today = platformCurrentDate()
            events = all
                .filter { it.symbol.substringBefore('.') in codes || it.symbol in items.map { item -> item.symbol } }
                .filter { it.date >= today }
                .sortedBy { it.date }
                .take(6)
        }

        // 情绪暴露：涨停池 ∩ 自选。
        insightRepository.loadHotspots { snapshot ->
            val bySymbol = rows.associateBy { it.symbol }
            limitUps = snapshot.limitUps.mapNotNull { limitUp ->
                bySymbol[limitUp.symbol]?.let { limitUp to it }
            }
        }
    }

    /** 读一次 alertInboxStore.extraMessages() 的 id 集合，填充已转/已生成 observable。 */
    private fun refreshInboxConverted() {
        val ids = dependencies.alertInboxStore.extraMessages().map { it.id }.toSet()
        convertedEventIds = ids
        generatedExposureIds = ids
    }

    // ── 纯计算（无副作用，可测）──

    /**
     * FR-R10：暴露快照留存（每周一次）。行业数据到达后尝试采集，
     * 7 天内重复打开不重复存。波动比取当期 portfolioStd 与大盘的比值。
     */
    private fun captureSnapshotIfDue() {
        val industry = industryStats()
        if (industry.isEmpty()) return
        val chain = chainConcentration(industry)
        val ratio = portfolioStd()?.let { std ->
            dailyReturns(indexQuote)?.let { indexReturns ->
                val indexStd = stdOf(indexReturns.values.toList())
                if (indexStd > 0.0) std / indexStd else null
            }
        }
        riskSnapshotStore.maybeCapture(
            RiskSnapshot(
                capturedAtMillis = platformCurrentTimeMillis(),
                memberCount = rows.size,
                topIndustry = chain.topName,
                topIndustryCount = chain.topCount,
                cr3Percent = (chain.cr3 * 100).toInt(),
                volRatioPercent = ratio?.let { (it * 100).toInt() },
            ),
        )
    }

    /** FR-R10：快照历史面板。竖向时间线形态（与事件时间轴同构），≥2 条才显示。 */
    private fun renderSnapshotHistory(container: ViewContainer<*, *>) {
        val page = this
        val snapshots = page.riskSnapshotStore.all()
        if (snapshots.size < 2) return
        // doc 30：最近两次快照的 CR3 变化，决定是否展示「生成暴露变化预警」入口。
        val ascSnapshots = snapshots.sortedBy { it.capturedAtMillis }
        val olderSnap = ascSnapshots[ascSnapshots.lastIndex - 1]
        val newerSnap = ascSnapshots.last()
        val deltaCr3 = abs(newerSnap.cr3Percent - olderSnap.cr3Percent)
        val exposureId = "EXPOSURE:${newerSnap.capturedAtMillis}"
        container.View {
            attr {
                marginTop(10f)
                padding(14f)
                borderRadius(14f)
                backgroundColor(page.theme.surface)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("暴露变化")
                        fontSize(13.5f)
                        fontWeightSemiBold()
                        color(page.theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        flex(1f)
                        text("每周自动留存一次")
                        textAlignRight()
                        fontSize(10f)
                        color(page.theme.textTertiary)
                    }
                }
            }
            snapshots.takeLast(6).reversed().forEach { snapshot ->
                container.View {
                    attr { marginTop(10f); flexDirectionRow() }
                    Text {
                        attr {
                            text(page.formatSnapshotDate(snapshot.capturedAtMillis))
                            width(64f)
                            fontSize(10f)
                            color(page.theme.textTertiary)
                        }
                    }
                    View {
                        attr { alignItemsCenter(); width(10f) }
                        View {
                            attr {
                                width(6f)
                                height(6f)
                                borderRadius(3f)
                                backgroundColor(page.theme.flat)
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
                            fontSize(11f)
                            lineHeight(16f)
                            color(page.theme.textSecondary)
                        }
                    }
                }
            }
        }
        // doc 30：暴露变化预警入口（仅当最近两次快照 |ΔCR3| ≥ 10 个百分点）。
        vif({ deltaCr3 >= 10 }) {
            vif({ !page.generatedExposureIds.contains(exposureId) }) {
                container.View {
                    attr { marginTop(10f); flexDirectionRow(); alignItemsCenter() }
                    Text {
                        attr {
                            text("对比上次 · 生成暴露变化预警 ›")
                            flex(1f)
                            fontSize(11f)
                            color(page.theme.brand) // EXPOSURE 用品牌蓝语境，不用涨跌色
                        }
                    }
                    event { click { page.generateExposureAlert(olderSnap, newerSnap, exposureId) } }
                }
            }
            vif({ page.generatedExposureIds.contains(exposureId) }) {
                container.View {
                    attr { marginTop(10f); flexDirectionRow() }
                    Text {
                        attr {
                            text("已生成 ✓")
                            fontSize(10f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            }
        }
    }

    // ── FR-R9 跑输大盘归因通路 ──

    /**
     * 组合今日等权涨跌幅 - 大盘今日涨跌幅。差值 ≤ -0.3pct（跑输 0.3 个百分点以上）
     * 才触发，返回差值（负数）；行情未就绪或未跑输返回 null。
     */
    private fun underperformGapPct(): Double? {
        val indexPct = indexQuote?.changePercent ?: return null
        val quotes = rows.mapNotNull { it.quote }
        if (quotes.isEmpty()) return null
        val gap = quotes.map { it.changePercent }.average() - indexPct
        return if (gap <= -0.3) gap else null
    }

    /** 出口行：事实差值 + 提问出口。归因由对话侧 attribution 卡片完成（复用，不新造）。 */
    private fun renderAttributionEntry(container: ViewContainer<*, *>) {
        val page = this
        val gap = page.underperformGapPct() ?: return
        container.View {
            attr {
                marginTop(10f)
                paddingLeft(14f)
                paddingRight(14f)
                paddingTop(12f)
                paddingBottom(12f)
                borderRadius(12f)
                backgroundColor(page.theme.surface)
                flexDirectionRow()
                alignItemsCenter()
            }
            event { click { page.openChatWithQuestion("我的自选今天为什么跌得比大盘多？") } }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text("今天比大盘多跌 ${Format.price(-gap)}%")
                        fontSize(12.5f)
                        color(page.theme.textSecondary)
                    }
                }
                Text {
                    attr {
                        text("问一句「为什么」，看逐项归因 ›")
                        marginTop(3f)
                        fontSize(10.5f)
                        color(page.theme.brand)
                    }
                }
            }
            Text {
                attr {
                    text("去问")
                    fontSize(11.5f)
                    color(page.theme.brand)
                }
            }
        }
    }

    private fun formatSnapshotDate(millis: Long): String {
        // 快照日期仅用于回看定位（都在近 12 周内），天数差粗粒度换算即可。
        val diffDays = ((platformCurrentTimeMillis() - millis) / DAY_MS).toInt()
        return when {
            diffDays <= 0 -> "本周"
            diffDays < 30 -> "$diffDays 天前"
            else -> "${diffDays / 30} 个月前"
        }
    }

    private data class IndustryStat(
        val name: String,
        val count: Int,
        val members: List<RiskRow>,
    )

    private data class ChainConcentration(
        val triggered: Boolean,
        val topName: String,
        val topCount: Int,
        val cr3: Double,
        val cr3Count: Int,
    )

    /** 行业分组（等权 = 只数占比）；行业未知的归「未分类」。 */
    private fun industryStats(): List<IndustryStat> {
        if (rows.isEmpty()) return emptyList()
        return rows.groupBy { industries[it.symbol]?.takeIf { name -> name.isNotBlank() } ?: "未分类" }
            .map { (name, members) -> IndustryStat(name, members.size, members) }
            .sortedByDescending { it.count }
    }

    /** 单链集中判定：单一行业 >50%，或前三大行业合计 >70%。 */
    private fun chainConcentration(industry: List<IndustryStat>): ChainConcentration {
        val total = rows.size
        if (total == 0 || industry.isEmpty()) return ChainConcentration(false, "", 0, 0.0, 0)
        val top = industry.first()
        val cr3Count = industry.take(3).sumOf { it.count }
        val cr3 = cr3Count.toDouble() / total
        val topWeight = top.count.toDouble() / total
        return ChainConcentration(
            triggered = topWeight > 0.50 || cr3 > 0.70,
            topName = top.name,
            topCount = top.count,
            cr3 = cr3,
            cr3Count = cr3Count,
        )
    }

    /** L1 结论：纯规则，中性措辞，不预判方向。 */
    private fun headline(industry: List<IndustryStat>, chain: ChainConcentration): String {
        val total = rows.size
        val ratio = portfolioStd()?.let { std ->
            dailyReturns(indexQuote)?.let { indexReturns ->
                val indexStd = stdOf(indexReturns.values.toList())
                if (indexStd > 0.0) std / indexStd else null
            }
        }
        return when {
            chain.triggered && chain.topName != "未分类" ->
                "你关注的 $total 只，有 ${chain.topCount} 只押在同一条链上（${chain.topName}）"
            ratio != null && ratio > 1.5 ->
                "$total 只自选的等权波动是大盘的 ${Format.price(ratio)} 倍"
            else -> "$total 只自选的共同暴露画像：单链集中不明显"
        }
    }

    /** 日收益率序列（date → close/prevClose - 1），只取最近 [CORRELATION_WINDOW] 根。 */
    private fun dailyReturns(quote: Quote?): Map<String, Double>? {
        val bars = quote?.kLines.orEmpty()
        if (bars.size < 2) return null
        val window = bars.takeLast(CORRELATION_WINDOW + 1)
        return buildMap {
            for (i in 1 until window.size) {
                val prev = window[i - 1].close
                if (prev > 0.0) {
                    put(window[i].date, window[i].close / prev - 1.0)
                }
            }
        }
    }

    private fun pearson(a: Map<String, Double>, b: Map<String, Double>): Double? {
        val common = a.keys.intersect(b.keys)
        if (common.size < MIN_RETURN_DAYS) return null
        val xs = common.map { a.getValue(it) }
        val ys = common.map { b.getValue(it) }
        val n = common.size.toDouble()
        val mx = xs.average()
        val my = ys.average()
        var cov = 0.0
        var vx = 0.0
        var vy = 0.0
        repeat(xs.size) { i ->
            val dx = xs[i] - mx
            val dy = ys[i] - my
            cov += dx * dy
            vx += dx * dx
            vy += dy * dy
        }
        if (vx <= 0.0 || vy <= 0.0) return null
        return (cov / sqrt(vx * vy)).coerceIn(-1.0, 1.0)
    }

    private fun sameDirectionDays(a: Map<String, Double>, b: Map<String, Double>): Int =
        a.keys.intersect(b.keys).count { date ->
            (a.getValue(date) > 0 && b.getValue(date) > 0) || (a.getValue(date) < 0 && b.getValue(date) < 0)
        }

    /** 等权组合日收益 = 各成员当日收益的算术平均（按有数据的成员计）。 */
    private fun portfolioStd(): Double? {
        val members = rows.mapNotNull { dailyReturns(it.quote) }
        if (members.size < 2) return null
        val dates = members.flatMap { it.keys }.toSet()
        val portfolio = dates.mapNotNull { date ->
            val values = members.mapNotNull { it[date] }
            if (values.isNotEmpty()) values.average() else null
        }
        if (portfolio.size < MIN_RETURN_DAYS) return null
        return stdOf(portfolio)
    }

    private fun stdOf(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance)
    }

    private fun futureEvents(): List<MarketCalendarEvent> = events

    private fun limitUpMembers(): List<Pair<LimitUpStock, RiskRow>> = limitUps

    // ── 展示辅助 ──

    private fun weightLabel(count: Int, total: Int): String =
        if (total <= 0) "0%" else "${(count * 100.0 / total).toInt()}%"

    private fun weightLabel(share: Double): String = "${(share * 100).toInt()}%"

    private fun coefficientLabel(r: Double?): String =
        if (r == null) "样本不足" else "${if (r >= 0) "+" else ""}${Format.price(r)}"

    /** 行业堆叠条三段用中性明度阶梯（风险页不引入新饱和色）。 */
    private fun industrySegmentColor(index: Int): Color = when (index) {
        0 -> theme.textTertiary
        1 -> theme.flat
        else -> theme.divider
    }

    /** 相关性格子：|r| 四档明度（surfaceMuted→divider→flat→textTertiary），两主题通用。 */
    private fun correlationColor(r: Double): Color {
        val level = abs(r)
        return when {
            level >= 0.7 -> theme.textTertiary
            level >= 0.5 -> theme.flat
            level >= 0.3 -> theme.divider
            else -> theme.surfaceMuted
        }
    }

    private companion object {
        // FR-R7：波动基准 = 沪深300（更接近「大盘组合」口径；原用上证指数已改）。
        const val INDEX_SYMBOL = "000300.SH"
        const val CORRELATION_WINDOW = 60
        const val MIN_RETURN_DAYS = 30
        const val DAY_MS = 24L * 60 * 60 * 1000

        // 星图（doc 32）：图层持久化 key 与引路星脉冲步进参数。
        const val SKY_LAYER_KEY = "stockchat_risk_sky_layer_v1"
        const val BEACON_STEPS = 12
        const val BEACON_STEP_MS = 55L
    }
}
