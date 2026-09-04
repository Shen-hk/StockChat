package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
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
import com.kuikly.stockchat.page.components.AppTopBar
import com.kuikly.stockchat.page.components.SegmentBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

/**
 * 知识库 v2（doc 24 §6.3 重构规格）：首页从「词表」→「地图」。
 *
 * 层次结构（z 轴从后到前）：
 * - z0 氛围底：极淡品牌色（知识页不用涨跌色），高约 180px。
 * - z3 主卡玻璃（唯一）：「下一步该懂什么」推荐卡——推荐引擎纯本地规则
 *   （候选 = 未读 且 全部前置已理解；排序 = hitCount 降序 → 最近触发 →
 *   所属域已读比例），依赖路径「先懂 / 看懂后」第一次被画出来。
 * - z1 衬后：5 个概念域层叠面板 + 四段状态条（明度编码，零新增饱和色）。
 * - z2 常规面：最近遇到 chips（×N 是触发次数，不是分）。
 * - z1 底部：完整词表折叠为二级（just-in-case 浏览不是主路径）。
 *
 * 「遇到」的定义保持克制（见 [GlossaryStore]）：只有用户真实撞上术语才算
 * ——聊天里点术语高亮（ChatPage 埋点）、在词表里展开词条。零记录新用户
 * 落到「从第一个概念开始」的兜底推荐，无白屏。
 */
@Page(Routes.GLOSSARY, supportInLocal = true)
internal class GlossaryPage : BasePager() {
    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val glossaryStore: GlossaryStore get() = dependencies.glossaryStore

    /** "map" = 知识地图首页；"list" = 完整词表二级页（搜索 + 分类浏览）。 */
    private var viewMode: String by observable(VIEW_MAP)

    // ── 二级词表的状态（沿用 v1）──
    private var query: String by observable("")
    private var activeCategory: GlossaryCategory? by observable(null)
    private var rows: ObservableList<GlossaryRow> by observableList()
    private var expandedKey: String by observable("")
    private var explanationDepth: ExplanationDepth by observable(ExplanationDepth.PARAGRAPH)

    /** 遇到记录快照；observable 驱动地图重渲染，真数据源在 [glossaryStore]。 */
    private var encounterSnapshot: Map<String, GlossaryEncounter> by observable(emptyMap())

    override fun created() {
        super.created()
        StockCardRenderers.ensureRegistered()
        refreshEncounters()
        applyFilter()
    }

    override fun body(): ViewBuilder {
        val page = this
        // 非受控铁律：TextArea 的 text 只作为挂载种子，绝不绑定响应式文本，
        // 否则会形成 native→observable→prop 的回声循环（光标消失/退格错乱）。
        val searchSeed = page.query
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(32f)
                }

                // ── 地图首页 ──
                vif({ page.viewMode == VIEW_MAP }) {
                    page.renderKnowledgeMap(this)
                }

                // ── 二级词表（折叠降级）──
                vif({ page.viewMode == VIEW_LIST }) {
                    page.renderListTopBar(this, searchSeed)
                    page.renderWordList(this)
                }
            }
            AppTopBar(
                title = if (page.viewMode == VIEW_MAP) "知识库" else "全部概念",
                subtitle = if (page.viewMode == VIEW_MAP) {
                    "你遇到过的概念都会出现在这里"
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

    // ── 地图首页 ──

    private fun renderKnowledgeMap(container: ViewContainer<*, *>) {
        val page = this@GlossaryPage
        val rec = page.recommend()
        container.View {
            attr { marginTop(6f) }

            // z0 氛围底：极淡品牌色（不用涨跌色），承载 z3 主卡。
            View {
                attr {
                    paddingTop(18f)
                    paddingBottom(26f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(20f)
                    backgroundColor(page.theme.brandSoft)
                }

                // z3 主卡玻璃（唯一）：下一步该懂什么。
                View {
                    attr {
                        marginLeft(12f)
                        marginRight(12f)
                        marginTop(14f)
                        padding(16f)
                        borderRadius(16f)
                        backgroundColor(page.theme.marketGlass)
                        boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.10f)))
                    }
                    Text {
                        attr {
                            text("下一步该懂什么")
                            fontSize(11f)
                            color(page.theme.textTertiary)
                        }
                    }
                    vif({ rec != null }) {
                        val entry = rec!!.entry
                        Text {
                            attr {
                                text(entry.term)
                                marginTop(8f)
                                fontSize(20f)
                                fontWeightSemiBold()
                                color(page.theme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(entry.plain)
                                marginTop(6f)
                                fontSize(13f)
                                lineHeight(20f)
                                color(page.theme.textSecondary)
                            }
                        }
                        // 依赖路径：先懂 / 看懂后——有向关系第一次被画出来（LDRS-R）。
                        vif({ page.hasPrereqOrDependent(entry.key) }) {
                            View {
                                attr {
                                    marginTop(12f)
                                    paddingTop(10f)
                                    paddingBottom(2f)
                                    flexDirectionRow()
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
                                vif({ page.prereqLabel(entry.key).isNotEmpty() }) {
                                    Text {
                                        attr {
                                            text(page.prereqLabel(entry.key))
                                            fontSize(11f)
                                            color(page.theme.textTertiary)
                                        }
                                    }
                                    event {
                                        click { page.openEntryInList(page.firstUnreadPrereq(entry.key)) }
                                    }
                                }
                                vif({ page.dependentLabel(entry.key).isNotEmpty() }) {
                                    Text {
                                        attr {
                                            text(page.dependentLabel(entry.key))
                                            marginLeft(14f)
                                            fontSize(11f)
                                            color(page.theme.textTertiary)
                                        }
                                    }
                                    event {
                                        click { page.openEntryInList(page.firstUnreadDependent(entry.key)) }
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                text(rec!!.reason)
                                marginTop(10f)
                                fontSize(10.5f)
                                color(page.theme.textTertiary)
                            }
                        }
                        // 已理解：唯一显式的状态推进入口（不搞积分、不搞评分）。
                        View {
                            attr {
                                marginTop(12f)
                                alignSelfFlexEnd()
                                paddingLeft(14f)
                                paddingRight(14f)
                                height(30f)
                                allCenter()
                                borderRadius(9f)
                                backgroundColor(page.theme.brandSoft)
                            }
                            Text {
                                attr {
                                    text("已理解")
                                    fontSize(12f)
                                    fontWeightSemiBold()
                                    color(page.theme.brand)
                                }
                            }
                            event {
                                click {
                                    page.glossaryStore.markKnown(entry.key)
                                    page.refreshEncounters()
                                }
                            }
                        }
                    }
                    vif({ rec == null }) {
                        Text {
                            attr {
                                text("全部概念都已理解。词表仍在二级页，随时可以回来翻。")
                                marginTop(8f)
                                fontSize(13f)
                                lineHeight(20f)
                                color(page.theme.textSecondary)
                            }
                        }
                    }
                }
            }

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

            // z2 最近遇到：触发次数是事实标注，不是分数。
            vif({ page.recentEncounters().isNotEmpty() }) {
                View {
                    attr { marginTop(16f) }
                    Text {
                        attr {
                            text("最近遇到")
                            fontSize(12f)
                            fontWeightSemiBold()
                            color(page.theme.term)
                        }
                    }
                    View {
                        attr { marginTop(8f); flexDirectionRow(); flexWrapWrap() }
                        page.recentEncounters().forEach { enc ->
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
        }
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

    private fun recentEncounters(): List<GlossaryEncounter> = glossaryStore.recent(limit = 5)

    // ── 依赖路径标签 ──

    private fun hasPrereqOrDependent(key: String): Boolean =
        Glossary.prerequisitesOf(key).isNotEmpty() || Glossary.dependentsOf(key).isNotEmpty()

    private fun prereqLabel(key: String): String {
        val names = Glossary.prerequisitesOf(key).mapNotNull { Glossary.byKey(it)?.term }
        return if (names.isEmpty()) "" else "先懂：${names.joinToString("、")} ›"
    }

    private fun dependentLabel(key: String): String {
        val names = Glossary.dependentsOf(key).mapNotNull { Glossary.byKey(it)?.term }
        return if (names.isEmpty()) "" else "看懂后：${names.first()} ›"
    }

    private fun firstUnreadPrereq(key: String): String? =
        Glossary.prerequisitesOf(key).firstOrNull { encounterSnapshot[it]?.isKnown != true }
            ?: Glossary.prerequisitesOf(key).firstOrNull()

    private fun firstUnreadDependent(key: String): String? =
        Glossary.dependentsOf(key).firstOrNull { encounterSnapshot[it]?.isKnown != true }
            ?: Glossary.dependentsOf(key).firstOrNull()

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
