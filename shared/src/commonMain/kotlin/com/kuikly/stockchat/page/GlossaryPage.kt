package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.DefinitionCardModel
import com.kuikly.stockchat.cards.core.ExplanationDepth
import com.kuikly.stockchat.cards.stock.StockCardRenderers
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.GlossaryEncounter
import com.kuikly.stockchat.data.GlossaryStore
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.GlossaryCategory
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.SegmentBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 知识库 v3（doc 34 定稿 → 真机）：首页即卡片流。
 *
 * 交互契约（取代 doc 30 独立闪卡页 TermCardDeckPage，已删除）：
 * - **首页即卡片流**：左右滑动/甩动直接在知识库首页翻卡，无独立复习页；
 *   轻点两侧卡片让它居中，居中卡自动展开详情。
 * - **翻过即已读**：卡片居中展示即记「已读」（[GlossaryStore.markKnown]），
 *   没有自评/测验/掌握度环——状态只影响推荐顺序，不做任何能力评价。
 * - 出牌顺序：recommend()（依赖就绪的词领读）打头 + 未读按 hitCount 降序；
 *   最后一页再往前 → 完成面板（再顺一遍含已读 / 浏览词表）。
 * - 知识页零涨跌色：状态点用 SegmentBar 同一明度阶梯（surfaceMuted →
 *   glossarySeen → brandSoft → brand）。
 *
 * 层次结构（z 轴从后到前）：
 * - 卡片流区：进度头（全库已读 N / M + 细条）→ 轮播视口 → 一行操作提示。
 * - z1 五域面板 + 四段状态条（SegmentBar，明度编码）。
 * - z2 最近在聊天里遇到 chips（×N 是触发次数，不是分）。
 * - z1 底部：完整词表折叠为二级（just-in-case 浏览不是主路径）。
 */
@Page(Routes.GLOSSARY, supportInLocal = true)
internal class GlossaryPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val glossaryStore: GlossaryStore get() = dependencies.glossaryStore

    /** "map" = 知识地图首页；"list" = 完整词表二级页（搜索 + 分类浏览）。 */
    private var viewMode: String by observable(VIEW_MAP)

    // ── 交接淡入（与 StockDetailPage 同款）：术语岛下滑 → 无动画 push，
    // 页面内容就地淡入接管灵动岛玻璃帧（R4 两帧翻转 + 500ms 兜底）。
    private var handoffPresented: Boolean by observable(true)
    private var handoffFadeActive = false
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    // ── 二级词表的状态（沿用 v1）──
    private var query: String by observable("")
    private var activeCategory: GlossaryCategory? by observable(null)
    private var rows: ObservableList<GlossaryRow> by observableList()
    private var expandedKey: String by observable("")
    private var explanationDepth: ExplanationDepth by observable(ExplanationDepth.PARAGRAPH)

    /** 遇到记录快照；observable 驱动地图重渲染，真数据源在 [glossaryStore]。 */
    private var encounterSnapshot: Map<String, GlossaryEncounter> by observable(emptyMap())

    // ── 首页卡片流（doc 34）：翻页即已读，无自评 ──
    private var flowQueue: ObservableList<String> by observableList()
    private var flowVisible: ObservableList<Int> by observableList()
    private var flowScrollPos: Float by observable(0f)
    private var flowAnimating: Boolean by observable(false)
    private var flowFinished: Boolean by observable(false)
    private var flowAdvancedOpen: Boolean by observable(false)

    /** 本轮翻过（首次记为已读）的概念数；完成面板用。 */
    private var flowSessionCount: Int by observable(0)

    // ── 手势瞬时量，不驱动重绘：pan 回调不带 velocity，用时间戳采样测速 ──
    private var flowIndex = 0
    private var flowPanStartX = 0f
    private var flowPanStartScroll = 0f
    private val flowDragSamples = mutableListOf<Pair<Long, Float>>()

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        handoffFadeActive = pagerData.params.optString("krTransition") == "islandExpand"
        handoffPresented = !handoffFadeActive || reduceMotion
        if (handoffFadeActive) {
            // R4 两帧翻转：首帧 opacity 0 挂载，下一帧翻转为可见触发淡入；
            // 500ms 兜底防 ref→setTimeout 链路丢失导致页面停在透明态。
            setTimeout(0) { handoffPresented = true }
            setTimeout(500) { handoffPresented = true }
        }
        refreshEncounters()
        applyFilter()
        startFlow(includeRead = false)
    }

    override fun body(): ViewBuilder {
        val page = this
        // 非受控铁律：TextArea 的 text 只作为挂载种子，绝不绑定响应式文本，
        // 否则会形成 native→observable→prop 的回声循环（光标消失/退格错乱）。
        val searchSeed = page.query
        return {
            attr { backgroundColor(page.theme.page) }
            // 交接容器：整页内容（含顶栏）在容器上统一淡入，接管灵动岛
            // 形变铺满全屏后的玻璃帧（与 StockDetailPage 同一条转场语言）。
            View {
            attr {
                flex(1f)
                opacity(if (page.handoffPresented) 1f else 0f)
                if (!page.reduceMotion) {
                    animate(Animation.easeOut(0.22f), "glossary-handoff")
                }
            }
            // 首页取消垂直滚动（用户指定）：View 直排、内容超屏裁剪。
            // 卡片流必须是页面上唯一的滚动/手势层，纵向滚动容器彻底退出手势竞争。
            vif({ page.viewMode == VIEW_MAP }) {
                View {
                    attr {
                        flex(1f)
                        paddingLeft(14f)
                        paddingRight(14f)
                        paddingTop(page.pagerData.statusBarHeight + 73f)
                        paddingBottom(32f)
                    }
                    page.renderKnowledgeMap(this)
                }
            }

            // ── 二级词表（折叠降级）──
            vif({ page.viewMode == VIEW_LIST }) {
                Scroller {
                    attr {
                        flex(1f)
                        paddingLeft(14f)
                        paddingRight(14f)
                        paddingTop(page.pagerData.statusBarHeight + 73f)
                        paddingBottom(32f)
                    }
                    page.renderListTopBar(this, searchSeed)
                    page.renderWordList(this)
                }
            }
            AppTopBar(
                title = if (page.viewMode == VIEW_MAP) "知识库" else "全部概念",
                subtitle = if (page.viewMode == VIEW_MAP) {
                    "从你遇到过的词，顺到还没懂的词"
                } else {
                    "共 ${Glossary.all.size} 个概念，点击展开例子与进阶解释"
                },
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = if (page.viewMode == VIEW_MAP) "返回" else "地图",
                onBack = {
                    if (page.viewMode == VIEW_MAP) page.closePage() else page.viewMode = VIEW_MAP
                },
            )
            }
        }
    }

    // ── 地图首页 ──

    private fun renderKnowledgeMap(container: ViewContainer<*, *>) {
        val page = this@GlossaryPage
        container.View {
            attr { marginTop(6f) }

            // 首页即卡片流（doc 34）：左右滑动直接翻页，翻过即记「已读」，无自评。
            page.renderFlowArea(this)

            // z1 五域面板：按未遇到数降序——缺口最大的域排最前。
            page.domainStats().forEach { stat ->
                View {
                    attr {
                        marginTop(10f)
                        padding(14f)
                        borderRadius(14f)
                        backgroundColor(page.theme.surface)
                    }
                    event { click { page.openCategoryInList(stat.category) } }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter() }
                        Text {
                            attr {
                                text(stat.category.label)
                                fontSize(13.5f)
                                fontWeightSemiBold()
                                color(page.theme.textPrimary)
                            }
                        }
                        View { attr { flex(1f) } }
                        Text {
                            attr {
                                text("已遇 ${stat.encountered} / 共 ${stat.total}")
                                fontSize(11f)
                                color(page.theme.textTertiary)
                            }
                        }
                    }
                    View {
                        attr { marginTop(10f) }
                        SegmentBar(
                            theme = page.theme,
                            unseen = stat.unseen,
                            seen = stat.seen,
                            common = stat.common,
                            known = stat.known,
                        )
                    }
                    Text {
                        attr {
                            text("未遇到 ${stat.unseen} · 见过 ${stat.seen} · 常见 ${stat.common} · 已读 ${stat.known}")
                            marginTop(7f)
                            fontSize(10f)
                            color(page.theme.textTertiary)
                        }
                    }
                }
            }

            // z2 最近在聊天里遇到：按触发次数降序，×N 是事实标注，不是分数。
            vif({ page.recentByHits().isNotEmpty() }) {
                View {
                    attr { marginTop(16f) }
                    Text {
                        attr {
                            text("最近在聊天里遇到")
                            fontSize(12f)
                            fontWeightSemiBold()
                            color(page.theme.term)
                        }
                    }
                    View {
                        attr { marginTop(8f); flexDirectionRow(); flexWrapWrap() }
                        page.recentByHits().forEach { enc ->
                            val term = Glossary.byKey(enc.key)?.term ?: enc.key
                            View {
                                attr {
                                    marginRight(7f)
                                    marginBottom(7f)
                                    paddingLeft(10f)
                                    paddingRight(10f)
                                    height(26f)
                                    allCenter()
                                    borderRadius(8f)
                                    backgroundColor(page.theme.surfaceMuted)
                                }
                                event { click { page.openEntryInList(enc.key) } }
                                Text {
                                    attr {
                                        text("$term ×${enc.hitCount}")
                                        fontSize(11f)
                                        color(page.theme.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // z1 底部：完整词表折叠入口（just-in-case 浏览降级为二级）。
            View {
                attr {
                    marginTop(14f)
                    height(48f)
                    allCenter()
                    borderRadius(14f)
                    backgroundColor(page.theme.surfaceMuted)
                }
                event { click { page.viewMode = VIEW_LIST } }
                Text {
                    attr {
                        text("浏览全部 ${Glossary.all.size} 个概念 ›")
                        fontSize(13f)
                        fontWeightSemiBold()
                        color(page.theme.textSecondary)
                    }
                }
            }

            // 合规脚注（doc 34）：状态只影响推荐顺序，不做任何能力评价。
            Text {
                attr {
                    text("状态只影响推荐顺序，不做任何能力评价 · 记录仅存本地")
                    marginTop(10f)
                    fontSize(10.5f)
                    lineHeight(16f)
                    color(page.theme.textTertiary)
                }
            }
        }
    }

    // ── 首页卡片流（doc 34 定稿 → 真机）──
    //
    // 实现约束（AGENTS.md R1-R5，原 TermCardDeckPage 已验证的同款几何）：
    // - 唯一动画驱动是 flowScrollPos。收尾顺序固定：先 flowAnimating=true 注册动画，
    //   再写 flowScrollPos 目标值——R5「本圈注册、下圈消费」；跟手阶段直接落位。
    // - 卡片窗口化渲染（±3），window 变更只在收尾时发生，拖拽每帧只改 transform。
    // - pan 回调不带 velocity：platformCurrentTimeMillis 采样、end 取最近 ~100ms 平均。

    private fun renderFlowArea(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { marginBottom(4f) }
            // 进度头：读一张刷一次（encounterSnapshot 驱动）
            View {
                attr { flexDirectionRow(); alignItemsCenter(); marginBottom(8f) }
                Text {
                    attr {
                        text("下一步该懂什么")
                        fontSize(12f)
                        fontWeightSemiBold()
                        color(page.theme.term)
                    }
                }
                View { attr { flex(1f) } }
                Text {
                    attr {
                        text("全库已读 ${page.flowReadCount()} / ${Glossary.all.size}")
                        fontSize(11f)
                        color(page.theme.textTertiary)
                    }
                }
            }
            View {
                attr {
                    height(6f)
                    borderRadius(3f)
                    backgroundColor(page.theme.surfaceMuted)
                    overflow(true)
                }
                View {
                    attr {
                        height(6f)
                        borderRadius(3f)
                        backgroundColor(page.theme.brand)
                        width(page.flowReadRatio() * page.flowViewportWidth())
                    }
                }
            }
            // 轮播视口：横向 pan 捕获，纵向滚动让给外层 Scroller
            View {
                attr {
                    marginTop(10f)
                    height(FLOW_CARD_HEIGHT + 12f)
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                }
                vfor({ page.flowVisible }) { idx ->
                    page.renderFlowCard(idx, this)
                }
                vif({ page.flowFinished }) {
                    page.renderFlowDone(this)
                }
                event {
                    pan { params ->
                        when (params.state) {
                            "start" -> page.onFlowPanStart(params.x)
                            "move" -> page.onFlowPanMove(params.x)
                            "end" -> page.onFlowPanEnd(params.x)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("左右滑动或甩动翻页 · 翻过的词自动记为「已读」，仅影响推荐顺序")
                    marginTop(8f)
                    fontSize(10.5f)
                    color(page.theme.textTertiary)
                }
            }
        }
    }

    private fun renderFlowCard(idx: Int, container: ViewContainer<*, *>) {
        val page = this
        if (idx < 0 || idx >= flowQueue.size) return
        val key = flowQueue[idx]
        val entry = Glossary.byKey(key) ?: return
        container.View {
            attr {
                absolutePosition(left = 0f, top = 6f)
                size(page.flowCardWidth(), FLOW_CARD_HEIGHT)
                borderRadius(20f)
                backgroundColor(page.theme.surface)
                boxShadow(BoxShadow(0f, 10f, 26f, Color(0x000000, 0.08f)))
                page.applyFlowCardTransform(this, idx)
            }
            event {
                click {
                    // 轻点两侧卡片 → 居中；居中卡片的点击交给内部元素
                    if (abs(idx * page.flowStep() - page.flowScrollPos) >= page.flowStep() / 2f) {
                        page.animateFlowTo(idx)
                    }
                }
            }
            // 紧凑态：大字术语（未居中）
            vif({ abs(idx * page.flowStep() - page.flowScrollPos) >= page.flowStep() / 2f }) {
                page.renderFlowCardFront(entry, this)
            }
            // 详情态：居中自动展开
            vif({ abs(idx * page.flowStep() - page.flowScrollPos) < page.flowStep() / 2f }) {
                page.renderFlowCardDetail(entry, this)
            }
        }
    }

    /**
     * 卡片位移/缩放/透明度全部由 [flowScrollPos] 单一驱动：
     * off = idx×step − flowScrollPos，off=0 即居中。跟手时 flowAnimating=false 直接落位；
     * 收尾时先注册动画（flowAnimating=true 一拍）再改 flowScrollPos（R5）。
     */
    private fun applyFlowCardTransform(attr: com.tencent.kuikly.core.base.Attr, idx: Int) {
        val step = flowStep()
        val off = idx * step - flowScrollPos
        val near = (abs(off) / step).coerceAtMost(1f)
        val expand = 1f - near
        val scale = 0.88f + 0.12f * expand
        attr.zIndex((100f - abs(off)).roundToInt(), useOutline = false)
        attr.opacity(if (abs(off) > step * 1.7f) 0f else 0.45f + 0.55f * expand)
        attr.transform(
            rotate = Rotate.DEFAULT,
            scale = Scale(scale, scale),
            translate = Translate(0f, 0f, flowViewportWidth() / 2f - flowCardWidth() / 2f + off, near * 14f),
        )
        if (flowAnimating && !reduceMotion) {
            // animate 绑定最后读到的 observable：此处实参位置再读一次 flowScrollPos
            attr.animate(Animation.springEaseOut(0.34f, 0.86f, 0.9f), flowScrollPos)
        }
    }

    /** 分类 tag + 状态点（明度阶梯与 SegmentBar 同一语言，知识页零涨跌色）。 */
    private fun renderFlowCardHeader(entry: GlossaryEntry, container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                paddingTop(14f)
                paddingLeft(16f)
                paddingRight(16f)
            }
            View {
                attr {
                    paddingTop(3f)
                    paddingBottom(3f)
                    paddingLeft(9f)
                    paddingRight(9f)
                    borderRadius(9f)
                    backgroundColor(page.theme.brandSoft)
                }
                Text {
                    attr {
                        text(entry.category.label)
                        fontSize(10.5f)
                        color(page.theme.brand)
                    }
                }
            }
            View { attr { flex(1f) } }
            View {
                attr {
                    size(8f, 8f)
                    borderRadius(4f)
                    backgroundColor(page.flowStageColor(entry.key))
                }
            }
        }
    }

    private fun renderFlowCardFront(entry: GlossaryEntry, container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { flex(1f); flexDirectionColumn() }
            page.renderFlowCardHeader(entry, this)
            View {
                attr { flex(1f); justifyContentCenter(); alignItemsCenter() }
                Text {
                    attr {
                        text(entry.term)
                        fontSize(if (entry.term.length <= 6) 32f else 25f)
                        fontWeightBold()
                        color(page.theme.textPrimary)
                        textAlignCenter()
                    }
                }
                if (entry.ascii.isNotEmpty()) {
                    Text {
                        attr {
                            text(entry.ascii)
                            fontSize(14f)
                            color(page.theme.textTertiary)
                            marginTop(6f)
                            textAlignCenter()
                        }
                    }
                }
                if (entry.aliases.isNotEmpty()) {
                    Text {
                        attr {
                            text("又叫：${entry.aliases.joinToString(" · ")}")
                            fontSize(11.5f)
                            color(page.theme.textTertiary)
                            marginTop(8f)
                            textAlignCenter()
                        }
                    }
                }
            }
            // 前置概念 chips：紧凑态也把依赖路径画出来（LDRS-R）
            vif({ Glossary.prerequisitesOf(entry.key).isNotEmpty() }) {
                View {
                    attr {
                        flexDirectionRow()
                        flexWrapWrap()
                        alignItemsCenter()
                        paddingLeft(16f)
                        paddingRight(16f)
                        paddingBottom(14f)
                    }
                    Text {
                        attr {
                            text("先懂")
                            fontSize(10.5f)
                            color(page.theme.textTertiary)
                        }
                    }
                    Glossary.prerequisitesOf(entry.key).forEach { preKey ->
                        val pre = Glossary.byKey(preKey) ?: return@forEach
                        FlowChip("${pre.term} ›", page.theme, { page.jumpToFlowKey(preKey) }, this)
                    }
                }
            }
        }
    }

    private fun renderFlowCardDetail(entry: GlossaryEntry, container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { flex(1f); flexDirectionColumn() }
            page.renderFlowCardHeader(entry, this)
            // 内容区：直接纵向排列（旧版 TermCardDeckPage 同款）。不能用内层 Scroller——
            // 内层滚动容器会抢走触摸事件，视口上的横向 pan 再也收不到，卡片无法左右滑。
            // 代价：进阶展开后超长内容在定高卡内裁剪（旧版同款取舍）。
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    marginTop(10f)
                    paddingLeft(16f)
                    paddingRight(16f)
                    paddingBottom(14f)
                }
                Text {
                    attr {
                        text(entry.term + if (entry.ascii.isNotEmpty()) "  ${entry.ascii}" else "")
                        fontSize(19f)
                        fontWeightSemiBold()
                        color(page.theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("人话解释")
                        fontSize(10f)
                        letterSpacing(2f)
                        color(page.theme.textTertiary)
                        marginTop(10f)
                    }
                }
                Text {
                    attr {
                        text(entry.plain)
                        fontSize(14.5f)
                        lineHeight(22f)
                        color(page.theme.textPrimary)
                        marginTop(5f)
                    }
                }
                // 一行归因：为什么是这张（端侧模板，事实性陈述）
                Text {
                    attr {
                        text("为什么是这张：${page.flowWhyText(entry)}")
                        fontSize(10.5f)
                        lineHeight(16f)
                        color(page.theme.textTertiary)
                        marginTop(7f)
                    }
                }
                Text {
                    attr {
                        text("A 股语境例子")
                        fontSize(10f)
                        letterSpacing(2f)
                        color(page.theme.textTertiary)
                        marginTop(11f)
                    }
                }
                View {
                    attr {
                        marginTop(5f)
                        paddingTop(9f)
                        paddingBottom(9f)
                        paddingLeft(10f)
                        paddingRight(10f)
                        borderRadius(0f, 8f, 8f, 0f)
                        backgroundColor(page.theme.surfaceMuted)
                    }
                    Text {
                        attr {
                            text(entry.example)
                            fontSize(12.5f)
                            lineHeight(19f)
                            color(page.theme.textSecondary)
                        }
                    }
                }
                // 进阶解释：默认收起（克制），点开才占位
                if (entry.advanced.isNotEmpty()) {
                    View {
                        attr {
                            alignSelfFlexStart()
                            marginTop(10f)
                            paddingTop(4f)
                            paddingBottom(4f)
                            paddingLeft(9f)
                            paddingRight(9f)
                            borderRadius(9f)
                            backgroundColor(page.theme.brandSoft)
                        }
                        event { click { page.flowAdvancedOpen = !page.flowAdvancedOpen } }
                        Text {
                            attr {
                                text(if (page.flowAdvancedOpen) "收起进阶 ▴" else "进阶解释 ▾")
                                fontSize(11f)
                                color(page.theme.brand)
                            }
                        }
                    }
                    vif({ page.flowAdvancedOpen }) {
                        Text {
                            attr {
                                text(entry.advanced)
                                fontSize(12f)
                                lineHeight(19f)
                                color(page.theme.textSecondary)
                                marginTop(6f)
                            }
                        }
                    }
                }
                // 先懂 / 看懂后：依赖路径 chips，点击 = 滑到那张卡
                vif({ page.hasFlowDeps(entry.key) }) {
                    View {
                        attr {
                            marginTop(12f)
                            paddingTop(10f)
                            paddingBottom(2f)
                            flexDirectionRow()
                            flexWrapWrap()
                            alignItemsCenter()
                        }
                        // 容器顶部分隔线
                        View {
                            attr {
                                absolutePosition()
                                top(0f)
                                left(0f)
                                right(0f)
                                height(1f)
                                backgroundColor(page.theme.divider)
                            }
                        }
                        vif({ Glossary.prerequisitesOf(entry.key).isNotEmpty() }) {
                            Text {
                                attr {
                                    text("先懂")
                                    fontSize(10.5f)
                                    color(page.theme.textTertiary)
                                }
                            }
                            Glossary.prerequisitesOf(entry.key).forEach { preKey ->
                                val pre = Glossary.byKey(preKey) ?: return@forEach
                                FlowChip("${pre.term} ›", page.theme, { page.jumpToFlowKey(preKey) }, this)
                            }
                        }
                        vif({ Glossary.dependentsOf(entry.key).isNotEmpty() }) {
                            Text {
                                attr {
                                    text("看懂后")
                                    marginLeft(if (Glossary.prerequisitesOf(entry.key).isEmpty()) 0f else 14f)
                                    marginTop(4f)
                                    fontSize(10.5f)
                                    color(page.theme.textTertiary)
                                }
                            }
                            Glossary.dependentsOf(entry.key).take(3).forEach { depKey ->
                                val dep = Glossary.byKey(depKey) ?: return@forEach
                                FlowChip("${dep.term} ›", page.theme, { page.jumpToFlowKey(depKey) }, this)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 完成面板：这一遍顺完了（无庆祝表情、无连胜、无作答统计——不做能力评价）。 */
    private fun renderFlowDone(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { absolutePositionAllZero(); allCenter() }
            View {
                attr {
                    width(page.flowCardWidth())
                    padding(20f)
                    borderRadius(20f)
                    backgroundColor(page.theme.surface)
                    boxShadow(BoxShadow(0f, 10f, 26f, Color(0x000000, 0.08f)))
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("这一遍顺完了")
                        fontSize(19f)
                        fontWeightBold()
                        color(page.theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("这一遍翻过 ${page.flowSessionCount} 个概念 · 全库已读 ${page.flowReadCount()} / ${Glossary.all.size}")
                        fontSize(12f)
                        color(page.theme.textTertiary)
                        marginTop(6f)
                        textAlignCenter()
                    }
                }
                View {
                    attr {
                        marginTop(16f)
                        paddingTop(11f)
                        paddingBottom(11f)
                        paddingLeft(28f)
                        paddingRight(28f)
                        borderRadius(999f)
                        backgroundColor(page.theme.brand)
                        allCenter()
                    }
                    event { click { page.startFlow(includeRead = true) } }
                    Text {
                        attr {
                            text("再顺一遍（含已读）")
                            fontSize(14f)
                            fontWeightSemiBold()
                            color(page.theme.onBrand)
                        }
                    }
                }
                View {
                    attr {
                        marginTop(8f)
                        paddingTop(10f)
                        paddingBottom(10f)
                        paddingLeft(22f)
                        paddingRight(22f)
                        borderRadius(999f)
                        backgroundColor(page.theme.surfaceMuted)
                        allCenter()
                    }
                    event { click { page.viewMode = VIEW_LIST } }
                    Text {
                        attr {
                            text("浏览词表与搜索")
                            fontSize(13f)
                            color(page.theme.textSecondary)
                        }
                    }
                }
            }
        }
    }

    /** 一行归因文案（doc 34 whyText）：只陈述事实，不做判断。 */
    private fun flowWhyText(entry: GlossaryEntry): String {
        val hits = encounterSnapshot[entry.key]?.hitCount ?: 0
        if (hits > 0) return "你在自选与风险地图里遇到过「${entry.term}」$hits 次"
        val readPres = Glossary.prerequisitesOf(entry.key).filter { encounterSnapshot[it]?.isKnown == true }
        if (readPres.isNotEmpty()) {
            return "前置的「${readPres.mapNotNull { Glossary.byKey(it)?.term }.joinToString("、")}」你都已读过"
        }
        val domainRead = Glossary.byCategory(entry.category).count { encounterSnapshot[it.key]?.isKnown == true }
        if (domainRead > 0) return "「${entry.category.label}」里你已读过 $domainRead 个，顺着补齐这一域"
        return "还没遇到过，从最基础的概念开始"
    }

    private fun hasFlowDeps(key: String): Boolean =
        Glossary.prerequisitesOf(key).isNotEmpty() || Glossary.dependentsOf(key).isNotEmpty()

    // ── 卡片流队列与手势 ──

    private fun flowCardWidth(): Float = (pagerData.pageViewWidth - 52f).coerceAtMost(330f)
    private fun flowStep(): Float = flowCardWidth() + 14f
    private fun flowViewportWidth(): Float = pagerData.pageViewWidth - 28f

    private fun flowReadCount(): Int = encounterSnapshot.values.count { it.isKnown }

    private fun flowReadRatio(): Float =
        if (Glossary.all.isEmpty()) 0f else flowReadCount().toFloat() / Glossary.all.size

    private fun flowStageColor(key: String): Color {
        val stage = encounterSnapshot[key]?.displayStage() ?: GlossaryEncounter.STAGE_UNSEEN
        return when (stage) {
            GlossaryEncounter.STAGE_KNOWN -> theme.brand
            GlossaryEncounter.STAGE_COMMON -> theme.brandSoft
            GlossaryEncounter.STAGE_SEEN -> theme.glossarySeen
            else -> theme.surfaceMuted
        }
    }

    /**
     * 建队（doc 34 buildFlowQueue）：未读（或全部）按 hitCount 降序；
     * 非重刷流让 recommend()（依赖就绪的词）领读第一张。
     */
    private fun startFlow(includeRead: Boolean) {
        val known: (String) -> Boolean = { encounterSnapshot[it]?.isKnown == true }
        val ordered = Glossary.all
            .filter { includeRead || !known(it.key) }
            .sortedWith(
                compareByDescending<GlossaryEntry> { encounterSnapshot[it.key]?.hitCount ?: 0 }
                    .thenBy { domainReadRatio(it.category) }
                    .thenBy { -Glossary.all.indexOf(it) },
            )
            .map { it.key }
        flowQueue.clear()
        if (!includeRead) {
            recommend()?.let { rec -> flowQueue.add(rec.entry.key) }
        }
        ordered.forEach { key -> if (!flowQueue.contains(key)) flowQueue.add(key) }
        flowIndex = 0
        flowSessionCount = 0
        flowAdvancedOpen = false
        flowAnimating = false
        flowFinished = flowQueue.isEmpty()
        flowVisible.clear()
        if (!flowFinished) {
            rebuildFlowWindow(0)
            flowScrollPos = 0f
            syncFlowRead()
        }
    }

    /** 居中展示即记「已读」（翻过即已读，仅影响推荐顺序；不重复计数与落盘）。 */
    private fun syncFlowRead() {
        if (flowFinished || flowQueue.isEmpty() || flowIndex >= flowQueue.size) return
        val key = flowQueue[flowIndex]
        if (encounterSnapshot[key]?.isKnown == true) return
        glossaryStore.markKnown(key)
        flowSessionCount++
        refreshEncounters() // 进度头 / 五域 / 最近遇到 chips 即时刷新
    }

    private fun rebuildFlowWindow(center: Int) {
        if (flowQueue.isEmpty()) {
            flowVisible.clear()
            return
        }
        val lo = (center - 3).coerceAtLeast(0)
        val hi = (center + 3).coerceAtMost(flowQueue.size - 1)
        val want = (lo..hi).toList()
        if (flowVisible.toList() == want) return
        flowVisible.clear()
        want.forEach { flowVisible.add(it) }
    }

    /** 收尾翻页：先建窗口、再注册动画、最后写目标偏移（R5 顺序不能反）。 */
    private fun animateFlowTo(target: Int) {
        if (flowQueue.isEmpty()) return
        val clamped = target.coerceIn(0, flowQueue.size - 1)
        rebuildFlowWindow(clamped)
        flowAdvancedOpen = false
        flowIndex = clamped
        if (reduceMotion) {
            flowAnimating = false
            flowScrollPos = clamped * flowStep()
            syncFlowRead()
            return
        }
        flowAnimating = true
        flowScrollPos = clamped * flowStep()
        setTimeout(FLOW_ANIM_MS.toInt()) {
            flowAnimating = false
            rebuildFlowWindow(flowIndex)
        }
        syncFlowRead()
    }

    /** 先懂 / 看懂后 chips：在队列里就滑过去，不在就插到当前卡后面。 */
    private fun jumpToFlowKey(key: String) {
        if (flowFinished || flowQueue.isEmpty()) return
        val idx = flowQueue.indexOf(key)
        if (idx >= 0) {
            if (idx != flowIndex) animateFlowTo(idx)
            return
        }
        flowQueue.add(flowIndex + 1, key)
        animateFlowTo(flowIndex + 1)
    }

    // ── 手势：翻页 = 拖拽距离 + 甩动速度（pan 不带 velocity，自行采样）──

    private fun onFlowPanStart(x: Float) {
        // 打断进行中的收尾动画，从当前位置继续跟手
        flowAnimating = false
        flowPanStartX = x
        flowPanStartScroll = flowScrollPos
        flowDragSamples.clear()
        flowDragSamples.add(platformCurrentTimeMillis() to 0f)
    }

    private fun onFlowPanMove(x: Float) {
        val dx = x - flowPanStartX
        val now = platformCurrentTimeMillis()
        flowDragSamples.add(now to dx)
        if (flowDragSamples.size > 6) flowDragSamples.removeAt(0)
        // 手指右移（dx>0）→ 内容右移 → flowScrollPos 减小
        flowScrollPos = flowPanStartScroll - dx
    }

    private fun onFlowPanEnd(x: Float) {
        if (flowQueue.isEmpty()) return
        val dx = x - flowPanStartX
        val now = platformCurrentTimeMillis()
        // 速度：取最近 ~100ms 的样本求平均
        var velocityDpS = 0f
        val base = flowDragSamples.firstOrNull { now - it.first <= FLOW_FLING_SAMPLE_MS }
            ?: flowDragSamples.firstOrNull()
        if (base != null) {
            val dt = (now - base.first) / 1000f
            if (dt > 0.004f) velocityDpS = (dx - base.second) / dt
        }
        val step = flowStep()
        // 位移 + 惯性外推（160ms）共同决定翻几页
        val projected = -(dx + velocityDpS * FLOW_FLING_HORIZON_S)
        var steps = (projected / step).roundToInt()
        if (steps == 0 && abs(dx) > step * 0.3f) steps = if (dx < 0) 1 else -1
        if (flowIndex == flowQueue.size - 1 && steps > 0) {
            // 最后一页再往前 → 完成面板
            flowVisible.clear()
            flowFinished = true
            return
        }
        animateFlowTo(flowIndex + steps)
    }

    // ── 二级词表（v1 结构保留，搜索框在二级页顶部 = §6.3 改动 #5）──

    private fun renderListTopBar(container: ViewContainer<*, *>, searchSeed: String) {
        val page = this@GlossaryPage
        container.View {
            attr {
                height(38f)
                marginBottom(10f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(11f)
                paddingRight(11f)
                borderRadius(10f)
                backgroundColor(page.theme.surfaceMuted)
            }
            Text { attr { text("⌕"); fontSize(15f); color(page.theme.textTertiary) } }
            TextArea {
                attr {
                    flex(1f)
                    marginLeft(6f)
                    height(36f)
                    fontSize(13f)
                    color(page.theme.textPrimary)
                    backgroundColor(Color(0xFFFFFFFF, 0f))
                    text(searchSeed)
                    placeholder("搜索术语：市盈率 / PE / 换手")
                    placeholderColor(page.theme.textTertiary)
                    tintColor(page.theme.brand)
                    selectionColor(page.theme.brand)
                }
                event {
                    textDidChange(isSyncEdit = true) { state ->
                        page.query = state.text
                        page.applyFilter()
                    }
                }
            }
        }

        View {
            attr { flexDirectionRow(); marginBottom(12f) }
            ExplanationDepth.values().forEach { depth ->
                GlossaryFilterChip(
                    label = depth.label,
                    theme = page.theme,
                    selected = { page.explanationDepth == depth },
                    onTap = { page.explanationDepth = depth },
                    container = this,
                )
            }
        }

        container.View {
            attr { flexDirectionRow(); marginBottom(12f) }
            GlossaryFilterChip(
                label = "全部",
                theme = page.theme,
                selected = { page.activeCategory == null },
                onTap = { page.activeCategory = null; page.applyFilter() },
                container = this,
            )
            GlossaryCategory.values().forEach { category ->
                GlossaryFilterChip(
                    label = category.label,
                    theme = page.theme,
                    selected = { page.activeCategory == category },
                    onTap = {
                        page.activeCategory = if (page.activeCategory == category) null else category
                        page.applyFilter()
                    },
                    container = this,
                )
            }
        }
    }

    private fun renderWordList(container: ViewContainer<*, *>) {
        val page = this@GlossaryPage
        container.vfor({ page.rows }) { row ->
            when (row) {
                is GlossaryRow.Header -> GlossarySectionTitle(row.label, row.count, page.theme, this)
                is GlossaryRow.Item -> {
                    val entry = row.entry
                    val key = "glossary:${entry.key}"
                    CardShell(
                        DefinitionCardModel(
                            term = entry.term,
                            plainText = entry.plain,
                            example = entry.example,
                            cardId = key,
                            advanced = entry.advanced,
                            category = entry.category.label,
                            depth = page.explanationDepth,
                        ),
                        CardContext(
                            theme = page.theme,
                            density = CardDensity.COMPACT,
                            onOpenStock = {},
                            expanded = page.expandedKey == key,
                            onToggleExpanded = { page.toggleEntry(entry.key) },
                            cardKey = key,
                        ),
                    )
                    // FR-K9 相关术语：只在展开态出现（克制：不展开不占位）。
                    // 点击 = 切换到该词条并记一笔遇到，依赖路径上的词排最前。
                    vif({ page.expandedKey == key }) {
                        val related = Glossary.relatedOf(entry.key)
                        vif({ related.isNotEmpty() }) {
                            View {
                                attr {
                                    marginTop(-6f)
                                    marginBottom(8f)
                                    marginLeft(12f)
                                    marginRight(12f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    flexWrapWrap()
                                }
                                Text {
                                    attr {
                                        text("相关：")
                                        fontSize(10.5f)
                                        color(page.theme.textTertiary)
                                    }
                                }
                                related.forEach { rel ->
                                    View {
                                        attr {
                                            marginRight(6f)
                                            marginTop(4f)
                                            paddingTop(3f)
                                            paddingBottom(3f)
                                            paddingLeft(8f)
                                            paddingRight(8f)
                                            borderRadius(9f)
                                            backgroundColor(page.theme.brandSoft)
                                        }
                                        event {
                                            click {
                                                // 复用 toggleEntry：记一笔遇到 + 刷新地图快照 + 展开
                                                page.toggleEntry(rel.key)
                                            }
                                        }
                                        Text {
                                            attr {
                                                text(rel.term)
                                                fontSize(10.5f)
                                                color(page.theme.brand)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        container.Text {
            attr {
                text("术语解释由股问整理，仅用于理解概念，不构成投资建议。")
                marginTop(16f)
                fontSize(11f)
                lineHeight(17f)
                color(page.theme.textTertiary)
            }
        }
    }

    // ── 推荐引擎（纯本地规则，无 LLM）──

    private data class GlossaryRecommendation(
        val entry: GlossaryEntry,
        val reason: String,
    )

    /**
     * 「下一步该懂什么」（doc 24 §6.3 规则）：
     * 候选 = { 状态 ∈ {UNSEEN, SEEN} 且 全部 prerequisites = 已理解 }；
     * 排序 = hitCount 降序 → 最近触发倒序 → 所属域已读比例降序 → 词典序兜底。
     * 候选为空时退化为「未满足前置最少」的未读词；全部已读返回 null（有兜底文案）。
     */
    private fun recommend(): GlossaryRecommendation? {
        val snaps = encounterSnapshot
        fun isKnown(key: String) = snaps[key]?.isKnown == true
        val unread = Glossary.all.filter { !isKnown(it.key) }
        if (unread.isEmpty()) return null

        val eligible = unread.filter { entry ->
            Glossary.prerequisitesOf(entry.key).all { isKnown(it) }
        }
        val pool = if (eligible.isNotEmpty()) {
            eligible
        } else {
            listOf(unread.minByOrNull { entry ->
                Glossary.prerequisitesOf(entry.key).count { !isKnown(it) }
            }!!)
        }
        val picked = pool.maxWithOrNull(
            compareBy<GlossaryEntry> { snaps[it.key]?.hitCount ?: 0 }
                .thenBy { snaps[it.key]?.lastTriggeredMillis ?: 0L }
                .thenBy { domainReadRatio(it.category) }
                .thenBy { -Glossary.all.indexOf(it) },
        )!!
        val reason = when {
            snaps[picked.key]?.hitCount ?: 0 > 0 -> "为什么现在推给你：你最近遇到过「${picked.term}」${snaps[picked.key]!!.hitCount} 次"
            Glossary.prerequisitesOf(picked.key).isEmpty() -> "为什么现在推给你：没有任何前置概念，可以直接开始"
            else -> "为什么现在推给你：前置概念已经就绪"
        }
        return GlossaryRecommendation(picked, reason)
    }

    private fun domainReadRatio(category: GlossaryCategory): Double {
        val stat = domainStats().firstOrNull { it.category == category } ?: return 0.0
        return if (stat.total == 0) 0.0 else stat.known.toDouble() / stat.total
    }

    // ── 五域统计 ──

    private data class DomainStat(
        val category: GlossaryCategory,
        val total: Int,
        val unseen: Int,
        val seen: Int,
        val common: Int,
        val known: Int,
    ) {
        val encountered: Int get() = seen + common + known
    }

    private fun domainStats(): List<DomainStat> {
        val snaps = encounterSnapshot
        return GlossaryCategory.values().map { category ->
            val entries = Glossary.byCategory(category)
            var unseen = 0
            var seen = 0
            var common = 0
            var known = 0
            entries.forEach { entry ->
                val enc = snaps[entry.key]
                when (enc?.displayStage() ?: GlossaryEncounter.STAGE_UNSEEN) {
                    GlossaryEncounter.STAGE_KNOWN -> known++
                    GlossaryEncounter.STAGE_COMMON -> common++
                    GlossaryEncounter.STAGE_SEEN -> seen++
                    else -> unseen++
                }
            }
            DomainStat(category, entries.size, unseen, seen, common, known)
        }.sortedByDescending { it.unseen }
    }

    /** 最近在聊天里遇到（按触发次数降序，×N 是事实标注不是分）。 */
    private fun recentByHits(): List<GlossaryEncounter> =
        encounterSnapshot.values.filter { it.hitCount > 0 }.sortedByDescending { it.hitCount }.take(6)

    // ── 状态与跳转 ──

    private fun refreshEncounters() {
        encounterSnapshot = glossaryStore.all()
    }

    /** 展开词条 = 真实遇到，记一笔（聊天侧点击术语的埋点在 ChatPage）。 */
    private fun toggleEntry(key: String) {
        val storeKey = key.removePrefix("glossary:")
        if (expandedKey != key) {
            glossaryStore.encounter(storeKey)
            refreshEncounters()
        }
        expandedKey = if (expandedKey == key) "" else key
    }

    /** 从地图跳到二级页并展开某个词条（依赖路径 / 最近遇到 chips 的落点）。 */
    private fun openEntryInList(key: String?) {
        val target = key ?: return
        activeCategory = null
        query = ""
        applyFilter()
        viewMode = VIEW_LIST
        val entryKey = "glossary:$target"
        if (expandedKey != entryKey) {
            glossaryStore.encounter(target)
            refreshEncounters()
        }
        expandedKey = entryKey
    }

    private fun openCategoryInList(category: GlossaryCategory) {
        activeCategory = category
        query = ""
        applyFilter()
        viewMode = VIEW_LIST
    }

    /** 检索 + 分类筛选后重建行数据；列表更新走 ObservableList，不依赖响应式重放。 */
    private fun applyFilter() {
        val matched = Glossary.search(query)
            .filter { entry -> activeCategory?.let { entry.category == it } ?: true }
        val built = mutableListOf<GlossaryRow>()
        if (matched.isEmpty()) {
            rows.clear()
            rows.add(GlossaryRow.Header("没有匹配的术语", 0))
            return
        }
        if (query.isBlank()) {
            // 浏览态按分类分组，组头吸顶由分组标题承担。
            GlossaryCategory.values().forEach { category ->
                val group = matched.filter { it.category == category }
                if (group.isEmpty()) return@forEach
                built.add(GlossaryRow.Header(category.label, group.size))
                group.forEach { built.add(GlossaryRow.Item(it)) }
            }
        } else {
            built.add(GlossaryRow.Header("搜索结果", matched.size))
            matched.forEach { built.add(GlossaryRow.Item(it)) }
        }
        rows.clear()
        built.forEach { rows.add(it) }
    }

    private companion object {
        const val VIEW_MAP = "map"
        const val VIEW_LIST = "list"

        /**
         * 卡片定高（无测高 API，不追求逐卡贴合）。不能加内层 Scroller——内层滚动容器
         * 会抢走触摸事件导致横向 pan 失效，因此内容超长时按定高裁剪（旧版同款取舍）。
         */
        const val FLOW_CARD_HEIGHT = 400f
        const val FLOW_ANIM_MS = 420L

        /** 甩动测速窗口（ms）。 */
        const val FLOW_FLING_SAMPLE_MS = 100L

        /** 惯性外推时长（s）：松手后按此时间以出射速度继续滑。 */
        const val FLOW_FLING_HORIZON_S = 0.16f
    }
}

internal sealed class GlossaryRow {
    data class Header(val label: String, val count: Int) : GlossaryRow()
    data class Item(val entry: GlossaryEntry) : GlossaryRow()
}

private fun GlossarySectionTitle(
    label: String,
    count: Int,
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { marginTop(12f); marginBottom(2f) }
        Text {
            attr {
                text(if (count > 0) "$label · $count" else label)
                fontSize(12f)
                fontWeightSemiBold()
                color(theme.term)
            }
        }
    }
}

/** 卡片流依赖路径 chip（先懂 / 看懂后）。 */
private fun FlowChip(
    label: String,
    theme: StockChatTheme,
    onTap: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            marginLeft(6f)
            marginTop(4f)
            paddingTop(3f)
            paddingBottom(3f)
            paddingLeft(9f)
            paddingRight(9f)
            borderRadius(9f)
            backgroundColor(theme.brandSoft)
        }
        Text {
            attr {
                text(label)
                fontSize(11f)
                color(theme.brand)
            }
        }
        event { click { onTap() } }
    }
}

private fun GlossaryFilterChip(
    label: String,
    theme: StockChatTheme,
    selected: () -> Boolean,
    onTap: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            marginRight(6f)
            paddingLeft(10f)
            paddingRight(10f)
            height(28f)
            allCenter()
            borderRadius(14f)
            backgroundColor(if (selected()) theme.brandSoft else theme.surfaceMuted)
        }
        Text {
            attr {
                text(label)
                fontSize(12f)
                color(if (selected()) theme.brand else theme.textSecondary)
            }
        }
        event { click { onTap() } }
    }
}
