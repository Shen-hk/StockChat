package com.kuikly.stockchat.page

import com.kuikly.stockchat.base.BasePager
import com.kuikly.stockchat.base.setTimeout
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Routes
import com.kuikly.stockchat.common.closePage
import com.kuikly.stockchat.data.MarketDependencies
import com.kuikly.stockchat.data.TermDrill
import com.kuikly.stockchat.data.TermDrillStore
import com.kuikly.stockchat.data.entity.Glossary
import com.kuikly.stockchat.data.entity.GlossaryCategory
import com.kuikly.stockchat.data.provider.platformCurrentTimeMillis
import com.kuikly.stockchat.data.provider.platformPrefersReducedMotion
import com.kuikly.stockchat.page.components.AppTopBar
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
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 术语闪卡轮播（doc 30 原型 → 真机）。
 *
 * 交互契约（与 docs/30 原型一致）：
 * - 翻页目标由**拖拽距离 + 甩动速度**共同决定：位移过半翻一页；甩动时按
 *   「位移 + 最近 ~100ms 平均速度 × 160ms 惯性外推」取整翻页，轻甩可连翻。
 * - **居中的卡片自动展开详情**（人话解释 / A 股例子 / 进阶折叠 / 相关词），
 *   两侧卡片收缩为大字术语态；拖拽中展开态实时跟随离中心最近的卡片。
 * - 作答三态（会了 / 模糊 / 不认识）写 [TermDrillStore]；没记住的词追加队尾
 *   本轮再过一遍；答「会了」双写 [GlossaryStore.markKnown] 保持知识地图一致。
 *
 * 实现约束（AGENTS.md R1-R5）：
 * - 唯一动画驱动是 `scrollPos`（当前滚动偏移）。收尾顺序固定：先 `animating=true`
 *   注册动画，再写 `scrollPos` 目标值——R5「本圈注册、下圈消费」；
 *   跟手阶段 animating=false，attr 不注册动画直接落位。
 * - 卡片窗口化渲染（±3），window 变更只在收尾时发生，拖拽每帧只改 transform。
 * - 速度测量：pan 回调不带 velocity（SwipeActionRow 同款限制），用
 *   [platformCurrentTimeMillis] 在 move 里采样、end 时取最近样本求平均。
 */
@Page(Routes.TERM_DECK, supportInLocal = true)
internal class TermCardDeckPage : BasePager() {

    private val theme: StockChatTheme get() = if (isNightMode()) StockChatTheme.Dark else StockChatTheme.Light
    private val dependencies by lazy { MarketDependencies.forPager(pagerId) }
    private val glossaryStore get() = dependencies.glossaryStore
    private val drillStore: TermDrillStore get() = dependencies.termDrillStore
    private val reduceMotion by lazy { platformPrefersReducedMotion() }

    // ── 响应式状态（R1：只在 attr/event/vif 闭包内读）──
    private var activeCategory: GlossaryCategory? by observable(null)
    private var queue: ObservableList<String> by observableList()
    private var visible: ObservableList<Int> by observableList()
    private var scrollPos: Float by observable(0f)
    private var animating: Boolean by observable(false)
    private var finished: Boolean by observable(false)
    private var advancedOpen: Boolean by observable(false)
    private var answeredCount: Int by observable(0)
    private var streak: Int by observable(0)
    private var drillSnapshot: Map<String, Int> by observable(emptyMap())
    private var todayCount: Int by observable(0)

    // ── 手势/轮次的瞬时量，不驱动重绘 ──
    private var deckIndex = 0
    private var directCount = 0
    private var maxStreak = 0
    private val seenThisRound = mutableMapOf<String, Int>()
    private val appendedOnce = mutableSetOf<String>()
    private var panStartX = 0f
    private var panStartScroll = 0f
    private val dragSamples = mutableListOf<Pair<Long, Float>>()

    // ── 几何 ──
    private fun cardWidth(): Float = (pagerData.pageViewWidth - 52f).coerceAtMost(330f)
    private fun cardGap(): Float = 14f
    private fun step(): Float = cardWidth() + cardGap()
    private fun viewportWidth(): Float = pagerData.pageViewWidth - 28f

    override fun created() {
        super.created()
        refreshSnapshots()
        startRound()
    }

    override fun body(): ViewBuilder {
        val page = this
        return {
            attr { backgroundColor(page.theme.page) }
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    paddingTop(page.pagerData.statusBarHeight + 73f)
                    paddingBottom(24f)
                }
                page.renderCategoryChips(this)
                page.renderLearnBar(this)
                page.renderDeckViewport(this)
                page.renderActions(this)
            }
            AppTopBar(
                title = "术语闪卡",
                subtitle = "像背单词一样，把概念轮着过一遍",
                statusBarHeight = page.pagerData.statusBarHeight,
                theme = page.theme,
                renderer = page.hostGlassRenderer,
                backLabel = "返回",
                onBack = { page.closePage() },
            )
        }
    }

    // ── 分类筛选 ──

    private fun renderCategoryChips(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { flexDirectionRow(); flexWrapWrap(); marginBottom(4f) }
            DeckChip(
                label = "全部",
                theme = page.theme,
                selected = { page.activeCategory == null },
                onTap = { page.pickCategory(null) },
                container = this,
            )
            GlossaryCategory.values().forEach { category ->
                DeckChip(
                    label = category.label,
                    theme = page.theme,
                    selected = { page.activeCategory == category },
                    onTap = { page.pickCategory(category) },
                    container = this,
                )
            }
        }
    }

    private fun pickCategory(category: GlossaryCategory?) {
        activeCategory = category
        startRound()
    }

    private fun poolKeys(): List<String> =
        Glossary.all
            .filter { activeCategory == null || it.category == activeCategory }
            .map { it.key }

    // ── 进度条 ──

    private fun renderLearnBar(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f); marginBottom(8f) }
            View {
                attr {
                    width(page.viewportWidth() - 96f)
                    height(6f)
                    borderRadius(3f)
                    backgroundColor(page.theme.surfaceMuted)
                }
                View {
                    attr {
                        height(6f)
                        borderRadius(3f)
                        backgroundColor(page.theme.brand)
                        width(
                            (
                                page.answeredCount.toFloat() /
                                    (page.queue.size + page.answeredCount).coerceAtLeast(1)
                                ) * (page.viewportWidth() - 96f)
                        )
                    }
                }
            }
            Text {
                attr {
                    text("${page.answeredCount} / ${page.queue.size + page.answeredCount}")
                    fontSize(11f)
                    color(page.theme.textTertiary)
                    marginLeft(8f)
                }
            }
            vif({ page.streak >= 2 }) {
                Text {
                    attr {
                        text("🔥 连中 ${page.streak}")
                        fontSize(11f)
                        color(page.theme.rise)
                        marginLeft(8f)
                    }
                }
            }
            vif({ page.todayCount > 0 }) {
                Text {
                    attr {
                        text("今日 ${page.todayCount}")
                        fontSize(11f)
                        color(page.theme.textTertiary)
                        marginLeft(8f)
                    }
                }
            }
        }
    }

    // ── 轮播视口 ──

    private fun renderDeckViewport(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                height(DECK_HEIGHT + 12f)
                marginBottom(10f)
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            vfor({ page.visible }) { idx ->
                page.renderDeckCard(idx, this)
            }
            vif({ page.finished }) {
                page.renderFinished(this)
            }
            event {
                pan { params ->
                    when (params.state) {
                        "start" -> page.onPanStart(params.x)
                        "move" -> page.onPanMove(params.x)
                        "end" -> page.onPanEnd(params.x)
                    }
                }
            }
        }
    }

    private fun renderDeckCard(idx: Int, container: ViewContainer<*, *>) {
        val page = this
        if (idx < 0 || idx >= queue.size) return
        val key = queue[idx]
        val entry = Glossary.byKey(key) ?: return
        container.View {
            attr {
                absolutePosition(left = 0f, top = 6f)
                size(page.cardWidth(), DECK_HEIGHT)
                borderRadius(20f)
                backgroundColor(page.theme.surface)
                boxShadow(BoxShadow(0f, 10f, 26f, Color(0x000000, 0.08f)))
                page.applyCardTransform(this, idx)
            }
            event {
                click {
                    // 轻点两侧卡片 → 让它居中；居中卡片上的点击交给内部元素
                    if (abs(idx * page.step() - page.scrollPos) >= page.step() / 2f) {
                        page.animateToIndex(idx)
                    }
                }
            }

            // 紧凑态：大字术语（未居中）
            vif({ abs(idx * page.step() - page.scrollPos) >= page.step() / 2f }) {
                page.renderCardFront(entry, this)
            }
            // 详情态：居中自动展开
            vif({ abs(idx * page.step() - page.scrollPos) < page.step() / 2f }) {
                page.renderCardDetail(entry, this)
            }
        }
    }

    /**
     * 卡片位移/缩放/透明度全部由 [scrollPos] 单一驱动：
     * off = idx×step − scrollPos，off=0 即居中。跟手时 animating=false 直接落位；
     * 收尾时先注册动画（animating=true 一拍）再改 scrollPos（R5）。
     */
    private fun applyCardTransform(attr: com.tencent.kuikly.core.base.Attr, idx: Int) {
        val step = step()
        val off = idx * step - scrollPos
        val near = (abs(off) / step).coerceAtMost(1f)
        val expand = 1f - near
        val scale = 0.88f + 0.12f * expand
        attr.zIndex((100f - abs(off)).roundToInt(), useOutline = false)
        attr.opacity(if (abs(off) > step * 1.7f) 0f else 0.45f + 0.55f * expand)
        attr.transform(
            rotate = Rotate.DEFAULT,
            scale = Scale(scale, scale),
            translate = Translate(0f, 0f, viewportWidth() / 2f - cardWidth() / 2f + off, near * 14f),
        )
        if (animating && !reduceMotion) {
            // animate 绑定最后读到的 observable：此处实参位置再读一次 scrollPos
            attr.animate(Animation.springEaseOut(0.34f, 0.86f, 0.9f), scrollPos)
        }
    }

    private fun renderCardFront(entry: com.kuikly.stockchat.data.entity.GlossaryEntry, container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { flex(1f); flexDirectionColumn() }
            DeckCategoryTag(entry.category.label, page.theme, this)
            page.renderMasteryDot(entry.key, this)
            View {
                attr { flex(1f); justifyContentCenter(); alignItemsCenter() }
                Text {
                    attr {
                        text(entry.term)
                        fontSize(if (entry.term.length <= 6) 34f else 26f)
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
            }
        }
    }

    private fun renderCardDetail(entry: com.kuikly.stockchat.data.entity.GlossaryEntry, container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { flex(1f); flexDirectionColumn() }
            DeckCategoryTag(entry.category.label, page.theme, this)
            page.renderMasteryDot(entry.key, this)
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
                    marginTop(12f)
                }
            }
            Text {
                attr {
                    text(entry.plain)
                    fontSize(15f)
                    lineHeight(23f)
                    color(page.theme.textPrimary)
                    marginTop(5f)
                }
            }
            Text {
                attr {
                    text("A 股语境例子")
                    fontSize(10f)
                    letterSpacing(2f)
                    color(page.theme.textTertiary)
                    marginTop(12f)
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
                    event { click { page.advancedOpen = !page.advancedOpen } }
                    Text {
                        attr {
                            text(if (page.advancedOpen) "收起进阶 ▴" else "进阶解释 ▾")
                            fontSize(11f)
                            color(page.theme.brand)
                        }
                    }
                }
                vif({ page.advancedOpen }) {
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
            // 相关术语：同域 + 依赖路径优先（FR-K9），点击即切换并记一笔
            val related = Glossary.relatedOf(entry.key, limit = 3)
            if (related.isNotEmpty()) {
                View {
                    attr { flexDirectionRow(); flexWrapWrap(); marginTop(12f); alignItemsCenter() }
                    Text {
                        attr {
                            text("接下来 ")
                            fontSize(10.5f)
                            color(page.theme.textTertiary)
                        }
                    }
                    related.forEach { rel ->
                        View {
                            attr {
                                marginLeft(6f)
                                marginTop(4f)
                                paddingTop(3f)
                                paddingBottom(3f)
                                paddingLeft(9f)
                                paddingRight(9f)
                                borderRadius(9f)
                                backgroundColor(page.theme.surfaceMuted)
                            }
                            event { click { page.advanceToKey(rel.key) } }
                            Text {
                                attr {
                                    text(rel.term)
                                    fontSize(11f)
                                    color(page.theme.brand)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderMasteryDot(key: String, container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr {
                absolutePosition(top = 20f, right = 20f)
                size(8f, 8f)
                borderRadius(4f)
                backgroundColor(page.masteryColor(page.drillSnapshot[key] ?: TermDrill.STATE_NEW))
            }
        }
    }

    private fun masteryColor(state: Int): Color = when (state) {
        TermDrill.STATE_KNOWN -> theme.fall
        TermDrill.STATE_FUZZY -> theme.flat
        TermDrill.STATE_FORGOT -> theme.rise
        else -> theme.divider
    }

    // ── 完成面板 ──

    private fun renderFinished(container: ViewContainer<*, *>) {
        val page = this
        val poolLeft = page.poolKeys().count { page.drillSnapshot[it] != TermDrill.STATE_KNOWN }
        container.View {
            attr {
                absolutePositionAllZero()
                backgroundColor(page.theme.page)
                allCenter()
            }
            View {
                attr {
                    width(page.cardWidth())
                    padding(20f)
                    borderRadius(20f)
                    backgroundColor(page.theme.surface)
                    boxShadow(BoxShadow(0f, 10f, 26f, Color(0x000000, 0.08f)))
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(if (page.maxStreak >= 5) "🏆" else if (page.maxStreak >= 3) "🎉" else "💪")
                        fontSize(38f)
                    }
                }
                Text {
                    attr {
                        text("本轮学完了")
                        fontSize(19f)
                        fontWeightBold()
                        color(page.theme.textPrimary)
                        marginTop(8f)
                    }
                }
                Text {
                    attr {
                        text("没记住的词已排进下一轮")
                        fontSize(12f)
                        color(page.theme.textTertiary)
                        marginTop(4f)
                    }
                }
                View {
                    attr { flexDirectionRow(); marginTop(16f) }
                    page.renderDoneStat("${page.answeredCount}", "作答张数", this)
                    page.renderDoneStat("${page.directCount}", "一次记会", this)
                    page.renderDoneStat("${page.maxStreak}", "最高连中", this)
                }
                View {
                    attr {
                        marginTop(18f)
                        paddingTop(11f); paddingBottom(11f)
                        paddingLeft(30f); paddingRight(30f)
                        borderRadius(999f)
                        backgroundColor(page.theme.brand)
                        allCenter()
                    }
                    event { click { page.startRound(page.poolLeftIsZero()) } }
                    Text {
                        attr {
                            text(
                                if (poolLeft > 0) "继续学剩余 $poolLeft 张" else "全部掌握！再过一遍"
                            )
                            fontSize(14f)
                            fontWeightSemiBold()
                            color(page.theme.onBrand)
                        }
                    }
                }
                View {
                    attr {
                        marginTop(8f)
                        paddingTop(10f); paddingBottom(10f)
                        paddingLeft(22f); paddingRight(22f)
                        borderRadius(999f)
                        backgroundColor(page.theme.surfaceMuted)
                        allCenter()
                    }
                    event { click { page.pickCategory(page.nextCategory()) } }
                    Text {
                        attr {
                            text("换个分类")
                            fontSize(13f)
                            color(page.theme.textSecondary)
                        }
                    }
                }
            }
        }
    }

    private fun poolLeftIsZero(): Boolean =
        poolKeys().all { drillStore.state(it) == TermDrill.STATE_KNOWN }

    private fun nextCategory(): GlossaryCategory? {
        val cats = listOf<GlossaryCategory?>(null) + GlossaryCategory.values().toList()
        val current = cats.indexOf(activeCategory)
        return cats[(current + 1).mod(cats.size)]
    }

    private fun renderDoneStat(value: String, label: String, container: ViewContainer<*, *>) {
        val theme = this.theme
        container.View {
            attr { marginLeft(12f); marginRight(12f); alignItemsCenter() }
            Text {
                attr {
                    text(value)
                    fontSize(22f)
                    fontWeightBold()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text(label)
                    fontSize(10f)
                    color(theme.textTertiary)
                    marginTop(2f)
                }
            }
        }
    }

    // ── 作答按钮 ──

    private fun renderActions(container: ViewContainer<*, *>) {
        val page = this
        container.View {
            attr { flexDirectionRow(); marginBottom(8f) }
            DeckActionButton(
                label = "😵 不认识",
                background = page.theme.rise,
                textColor = page.theme.onBrand,
                onTap = { page.answer(TermDrill.STATE_FORGOT) },
                container = this,
            )
            DeckActionButton(
                label = "🤔 有点模糊",
                background = page.theme.flat,
                textColor = page.theme.onBrand,
                onTap = { page.answer(TermDrill.STATE_FUZZY) },
                container = this,
            )
            DeckActionButton(
                label = "✓ 会了",
                background = page.theme.fall,
                textColor = page.theme.onBrand,
                onTap = { page.answer(TermDrill.STATE_KNOWN) },
                container = this,
            )
        }
    }

    // ── 手势：翻页 = 拖拽距离 + 甩动速度 ──

    private fun onPanStart(x: Float) {
        // 打断进行中的收尾动画，从当前位置继续跟手
        animating = false
        panStartX = x
        panStartScroll = scrollPos
        dragSamples.clear()
        dragSamples.add(platformCurrentTimeMillis() to 0f)
    }

    private fun onPanMove(x: Float) {
        val dx = x - panStartX
        val now = platformCurrentTimeMillis()
        dragSamples.add(now to dx)
        if (dragSamples.size > 6) dragSamples.removeAt(0)
        // 手指右移（dx>0）→ 内容右移 → scrollPos 减小
        scrollPos = panStartScroll - dx
    }

    private fun onPanEnd(x: Float) {
        if (queue.isEmpty()) return
        val dx = x - panStartX
        val now = platformCurrentTimeMillis()
        // 速度：取最近 ~100ms 的样本求平均（pan 回调不带 velocity，自行测速）
        var velocityDpS = 0f
        val base = dragSamples.firstOrNull { now - it.first <= FLING_SAMPLE_MS } ?: dragSamples.firstOrNull()
        if (base != null) {
            val dt = (now - base.first) / 1000f
            if (dt > 0.004f) velocityDpS = (dx - base.second) / dt
        }
        val step = step()
        // 位移 + 惯性外推（160ms）共同决定翻几页
        val projected = -(dx + velocityDpS * FLING_HORIZON_S)
        var steps = (projected / step).roundToInt()
        if (steps == 0 && abs(dx) > step * 0.3f) steps = if (dx < 0) 1 else -1
        val target = (deckIndex + steps).coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        animateToIndex(target)
    }

    // ── 轮次与出牌 ──

    private fun startRound(includeKnown: Boolean = false) {
        val keys = drillStore.deckOrder(poolKeys(), includeKnown)
        queue.clear()
        keys.forEach { queue.add(it) }
        seenThisRound.clear()
        appendedOnce.clear()
        deckIndex = 0
        answeredCount = 0
        directCount = 0
        streak = 0
        maxStreak = 0
        finished = queue.isEmpty()
        advancedOpen = false
        animating = false
        rebuildWindow(0)
        scrollPos = 0f
    }

    private fun rebuildWindow(center: Int) {
        if (queue.isEmpty()) {
            visible.clear()
            return
        }
        val lo = (center - 3).coerceAtLeast(0)
        val hi = (center + 3).coerceAtMost(queue.size - 1)
        val want = (lo..hi).toList()
        if (visible.toList() == want) return
        visible.clear()
        want.forEach { visible.add(it) }
    }

    /** 收尾翻页：先建窗口、再注册动画、最后写目标偏移（R5 顺序不能反）。 */
    private fun animateToIndex(target: Int) {
        if (queue.isEmpty()) return
        val clamped = target.coerceIn(0, queue.size - 1)
        rebuildWindow(clamped)
        advancedOpen = false
        deckIndex = clamped
        if (reduceMotion) {
            animating = false
            scrollPos = clamped * step()
            return
        }
        animating = true
        scrollPos = clamped * step()
        setTimeout(ANIM_MS.toInt()) { // 集成修复：base.setTimeout 入参为 Int，ANIM_MS 声明为 Long
            animating = false
            rebuildWindow(deckIndex)
        }
    }

    private fun answer(state: Int) {
        if (finished || queue.isEmpty() || deckIndex >= queue.size) return
        val key = queue[deckIndex]
        val seen = (seenThisRound[key] ?: 0) + 1
        seenThisRound[key] = seen
        answeredCount++
        if (state == TermDrill.STATE_KNOWN) {
            if (seen == 1) directCount++
            streak++
            if (streak > maxStreak) maxStreak = streak
        } else {
            streak = 0
        }
        drillStore.record(key, state)
        if (state == TermDrill.STATE_KNOWN) {
            // 双写：知识地图的「已读」与闪卡的「会了」保持一致
            glossaryStore.markKnown(key)
        }
        refreshSnapshots()
        if (state != TermDrill.STATE_KNOWN && key !in appendedOnce) {
            appendedOnce.add(key)
            queue.add(key)
        }
        if (deckIndex < queue.size - 1) {
            animateToIndex(deckIndex + 1)
        } else {
            finished = true
        }
    }

    /** 相关词/前置词跳转：在队列里就直接滑过去，不在就插到当前卡后面。 */
    private fun advanceToKey(key: String) {
        if (finished || queue.isEmpty()) return
        val idx = queue.indexOf(key)
        if (idx >= 0) {
            if (idx != deckIndex) animateToIndex(idx)
            return
        }
        queue.add(deckIndex + 1, key)
        animateToIndex(deckIndex + 1)
    }

    private fun refreshSnapshots() {
        drillSnapshot = drillStore.all().mapValues { it.value.state }
        todayCount = drillStore.todayLearned()
    }

    private companion object {
        const val DECK_HEIGHT = 430f
        const val ANIM_MS = 420L
        /** 甩动测速窗口（ms）。 */
        const val FLING_SAMPLE_MS = 100L
        /** 惯性外推时长（s）：松手后按此时间以出射速度继续滑。 */
        const val FLING_HORIZON_S = 0.16f
    }
}

private fun DeckCategoryTag(label: String, theme: StockChatTheme, container: ViewContainer<*, *>) {
    container.View {
        attr {
            alignSelfFlexStart()
            paddingTop(3f); paddingBottom(3f)
            paddingLeft(9f); paddingRight(9f)
            borderRadius(9f)
            backgroundColor(theme.brandSoft)
        }
        Text {
            attr {
                text(label)
                fontSize(10.5f)
                color(theme.brand)
            }
        }
    }
}

private fun DeckChip(
    label: String,
    theme: StockChatTheme,
    selected: () -> Boolean,
    onTap: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            marginRight(7f)
            marginBottom(7f)
            paddingTop(6f); paddingBottom(6f)
            paddingLeft(12f); paddingRight(12f)
            borderRadius(15f)
            backgroundColor(if (selected()) theme.textPrimary else theme.surface)
        }
        Text {
            attr {
                text(label)
                fontSize(12.5f)
                color(if (selected()) theme.onBrand else theme.textSecondary)
            }
        }
        event { click { onTap() } }
    }
}

private fun DeckActionButton(
    label: String,
    background: Color,
    textColor: Color,
    onTap: () -> Unit,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr {
            flex(1f)
            height(46f)
            allCenter()
            borderRadius(15f)
            backgroundColor(background)
        }
        Text {
            attr {
                text(label)
                fontSize(14f)
                fontWeightSemiBold()
                color(textColor)
            }
        }
        event { click { onTap() } }
    }
}
