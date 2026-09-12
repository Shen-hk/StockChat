package com.kuikly.stockchat.page

import com.kuikly.stockchat.app.assembly.ChatFeatureGraph
import com.kuikly.stockchat.app.platform.KuiklyKeyValueStorage
import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.common.openChatWithQuestion
import com.kuikly.stockchat.common.openPage
import com.kuikly.stockchat.common.openStockDetail
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.provider.platformCurrentDate
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.foundation.ui.chrome.AppTopBar
import com.kuikly.stockchat.risk.ai.state.RiskAiCoordinator
import com.kuikly.stockchat.risk.alert.state.RiskAlertCoordinator
import com.kuikly.stockchat.risk.data.DefaultRiskRepository
import com.kuikly.stockchat.risk.domain.RiskRow
import com.kuikly.stockchat.risk.domain.StarMemberIn
import com.kuikly.stockchat.risk.domain.buildRiskAiFacts
import com.kuikly.stockchat.risk.domain.buildSkyAiPromptText
import com.kuikly.stockchat.risk.domain.chainConcentration
import com.kuikly.stockchat.risk.domain.industryStats
import com.kuikly.stockchat.risk.domain.riskAiLocalSummary
import com.kuikly.stockchat.risk.domain.skyEventsOf
import com.kuikly.stockchat.risk.domain.underperformGapPct
import com.kuikly.stockchat.risk.panel.component.renderAttributionEntry
import com.kuikly.stockchat.risk.panel.component.renderHeadline
import com.kuikly.stockchat.risk.panel.component.renderRiskPanels
import com.kuikly.stockchat.risk.panel.component.renderSnapshotHistory
import com.kuikly.stockchat.risk.sky.component.renderSkyMode
import com.kuikly.stockchat.risk.sky.state.RiskSkyCoordinator
import com.kuikly.stockchat.risk.state.KuiklyRiskScheduler
import com.kuikly.stockchat.risk.state.RiskDataCoordinator
import com.kuikly.stockchat.risk.state.RiskPageActions
import com.kuikly.stockchat.risk.state.RiskUiProps
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

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
 *
 * 2026-09-12（doc 47 B-3）：本页收敛为装配层——数据/星图/AI/预警四个状态域
 * 分属 `risk/{data,state,sky,state,ai/state,alert/state}` 的 Coordinator，
 * 渲染 DSL 分属 `risk/{panel,sky,ai}/component`；本文件只保留装配、生命周期
 * 与 Effect adapter（Route / 术语出口）。
 */
@Page(Routes.RISK, supportInLocal = true)
internal class RiskMapPage : BasePager() {
    private val theme: StockChatTheme get() = appTheme()
    private val dependencies by lazy { com.kuikly.stockchat.app.assembly.MarketFeatureGraph.forPager(pagerId) }
    private val watchlistStore get() = dependencies.watchlistStore
    private val glossaryStore get() = dependencies.glossaryStore
    private val riskSnapshotStore get() = dependencies.riskSnapshotStore
    private val skyAiDependencies by lazy { ChatFeatureGraph.forPager(pagerId) }
    private val skyStorage by lazy { KuiklyKeyValueStorage(pagerId) }
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    // ── 数据域：rows / indexQuote / industries / events / limitUps / dataModeLabel ──

    private val dataCoordinator: RiskDataCoordinator by lazy {
        RiskDataCoordinator(
            repository = com.kuikly.stockchat.risk.data.DefaultRiskRepository(
                quoteRepository = dependencies.quoteRepository,
                insightRepository = dependencies.insightRepository,
            ),
            snapshotStore = riskSnapshotStore,
            onQuoteArrived = {
                // 行情是异步到达的；相关系数/波动/AI 事实槽位必须随新行情重算。
                skyCoordinator.refreshSkyData(dataCoordinator.rows.toList(), dataCoordinator.indexQuote)
                aiCoordinator.maybeStart()
            },
            onIndustriesArrived = {
                dataCoordinator.captureSnapshotIfDue()
            },
        )
    }

    // ── 星图域：图层/选中/脉冲/拖拽（含持久化与 Timer） ──

    private val skyCoordinator: RiskSkyCoordinator by lazy {
        RiskSkyCoordinator(
            inputs = object : RiskSkyCoordinator.Inputs {
                override fun members(): List<StarMemberIn> = dataCoordinator.rows.map { row ->
                    StarMemberIn(
                        symbol = row.symbol,
                        name = row.name,
                        industry = dataCoordinator.industries[row.symbol]?.takeIf { n -> n.isNotBlank() } ?: "未分类",
                    )
                }

                override fun containerWidth(): Float = pagerData.pageViewWidth - 28f - 32f

                override fun chainTriggered(): Boolean {
                    val rows = dataCoordinator.rows.toList()
                    return chainConcentration(rows, industryStats(rows, dataCoordinator.industries)).triggered
                }

                override fun rowsSnapshot(): List<RiskRow> = dataCoordinator.rows.toList()
            },
            storage = skyStorage,
            scheduler = KuiklyRiskScheduler(),
            reduceMotion = reduceMotion,
        )
    }

    // ── AI 域：真实 LLM 流式 + 端侧速览兜底（12s 超时 / generation 守卫） ──

    private val aiCoordinator: RiskAiCoordinator by lazy {
        RiskAiCoordinator(
            host = object : RiskAiCoordinator.RiskAiHost {
                override fun pagerId(): String = this@RiskMapPage.pagerId

                override fun loadConfig(): AiConfig = skyAiDependencies.configStore.load()

                override fun configValidationError(config: AiConfig): String? = config.validationError()

                override fun openStream(config: AiConfig) = skyAiDependencies.aiProviderFactory(config)

                override fun factsReady(): Boolean {
                    val rows = dataCoordinator.rows.toList()
                    return rows.isNotEmpty() && rows.any { it.quote != null }
                }

                override fun buildPrompt(): String {
                    val rows = dataCoordinator.rows.toList()
                    val facts = buildRiskAiFacts(
                        rows = rows,
                        industries = dataCoordinator.industries,
                        correlations = skyCoordinator.correlations,
                        indexQuote = dataCoordinator.indexQuote,
                        events = dataCoordinator.events,
                        limitUps = dataCoordinator.limitUps,
                    )
                    return buildSkyAiPromptText(facts)
                }

                override fun buildLocalSummary(): String {
                    val rows = dataCoordinator.rows.toList()
                    val facts = buildRiskAiFacts(
                        rows = rows,
                        industries = dataCoordinator.industries,
                        correlations = skyCoordinator.correlations,
                        indexQuote = dataCoordinator.indexQuote,
                        events = dataCoordinator.events,
                        limitUps = dataCoordinator.limitUps,
                    )
                    val firstEvent = skyEventsOf(rows.firstOrNull()?.symbol.orEmpty(), dataCoordinator.events)
                        .firstOrNull()
                    return riskAiLocalSummary(facts, firstEvent)
                }
            },
            scheduler = KuiklyRiskScheduler(),
        )
    }

    // ── 预警域：converted/generated ids + 收件箱只写通路 + 2.5s 提示 ──

    private val alertCoordinator: RiskAlertCoordinator by lazy {
        RiskAlertCoordinator(
            inboxStore = dependencies.alertInboxStore,
            scheduler = KuiklyRiskScheduler(),
        )
    }

    override fun created() {
        super.created()
        // 返回态保持：恢复上次图层（doc 32 §3.1 P0 增强）与视图模式。
        skyCoordinator.restore()
        // doc 30：构建期读一次收件箱已写消息的 id 集合，驱动「已转/已生成」显示态。
        alertCoordinator.refreshConverted()
        reload()
        // 离线/缓存行情先行可算一次（在线加载回调里会再刷）。
        skyCoordinator.refreshSkyData(dataCoordinator.rows.toList(), dataCoordinator.indexQuote)
        aiCoordinator.maybeStart()
        skyCoordinator.ensureBeaconPulse()
    }

    private fun reload() {
        // C-1 自选是风险唯一输入：标的来源只有 WatchlistStore，列表交给数据域装载。
        dataCoordinator.reload(watchlistStore.list())
    }

    /** 组件层只读 Props（组件不认识 Page，只拿 coordinator + Effect）。 */
    private fun uiProps(): RiskUiProps = RiskUiProps(
        theme = theme,
        reduceMotion = reduceMotion,
        data = dataCoordinator,
        sky = skyCoordinator,
        ai = aiCoordinator,
        alert = alertCoordinator,
        actions = RiskPageActions(
            openPage = { route -> openPage(route) },
            openStockDetail = { symbol, source -> openStockDetail(symbol, source) },
            openChatWithQuestion = { question, focusNote, focusSymbol ->
                openChatWithQuestion(question, focusNote, focusSymbol)
            },
        ),
        glossaryEncounter = { termKey -> glossaryStore.encounter(termKey) },
        snapshotHistory = { riskSnapshotStore.all() },
        chatDependencies = skyAiDependencies,
    )

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

                vif({ page.dataCoordinator.rows.isEmpty() }) {
                    page.renderEmptyState(this)
                }

                vif({ page.dataCoordinator.rows.isNotEmpty() }) {
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

    /** 自选为空的开导引（文案/视觉逐字保留）。 */
    private fun renderEmptyState(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                marginTop(24f)
                padding(20f)
                borderRadius(16f)
                backgroundColor(page.theme.surface)
            }
            Text {
                attr {
                    text("风险地图的输入是你的自选")
                    fontSizeScaled(15f)
                    fontWeightSemiBold()
                    color(page.theme.textPrimary)
                }
            }
            Text {
                attr {
                    text("先在自选页添加几只股票，这里才能画出它们共享了哪些变量。不采集任何仓位信息，全部按等权估算。")
                    marginTop(10f)
                    fontSizeScaled(12.5f)
                    lineHeightScaled(19f)
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
                        fontSizeScaled(12f)
                        color(page.theme.brand)
                    }
                }
                event { click { page.openPage(Routes.SEARCH) } }
            }
        }
    }

    // ── 主渲染（装配：计算走 domain 纯函数，DSL 走组件） ──

    private fun renderRiskMap(container: ViewContainer<*, *>) {
        val page = this
        val props = page.uiProps()
        val rows = page.dataCoordinator.rows.toList()
        val industry = industryStats(rows, page.dataCoordinator.industries)
        val chain = chainConcentration(rows, industry)
        container.View {
            attr { marginTop(6f) }

            // doc 32 §5 降级分档：≥3 只自选走星图主卡（档 1）；
            // 1–2 只回落六面板列表投影（档 3，v1 全保留）。rows 在 created() 内同步
            // 装满、行情加载只替换元素不改 size，构建期判定稳定。
            if (page.dataCoordinator.rows.size >= 3) {
                container.renderSkyMode(props, industry, chain)
            } else {
                container.renderHeadline(props, industry, chain, withIndustryCard = true)
                container.renderRiskPanels(props)
            }

            // FR-R10 暴露快照：≥2 条才有「变化」可看，单点不渲染（两种模式共用）。
            container.renderSnapshotHistory(props)

            // FR-R9 跑输大盘归因通路（条件出现）：本页只给事实差值 + 提问出口，
            // 归因本体复用对话里的 attribution 卡片（AttributionIntent），不自行编造原因。
            vif({ underperformGapPct(page.dataCoordinator.rows.toList(), page.dataCoordinator.indexQuote) != null }) {
                container.renderAttributionEntry(props)
            }

            Text {
                attr {
                    text("本页只翻译共同暴露，不判定风险等级，不构成投资建议。")
                    marginTop(16f)
                    fontSizeScaled(11f)
                    lineHeightScaled(17f)
                    color(page.theme.textTertiary)
                }
            }
        }
    }
}
