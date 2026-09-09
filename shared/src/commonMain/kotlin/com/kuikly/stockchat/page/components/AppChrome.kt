package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.data.lineHeightScaled

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatSessionSummary
import com.kuikly.stockchat.chart.model.TimeLineCalculator
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.kuikly.stockchat.data.entity.GlossaryCategory
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuotePoint
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

enum class IslandGesturePhase {
    IDLE,
    DRAGGING,
    RETURNING,
    CLOSING,
    OPENING_DETAIL,
}

enum class DrawerGesturePhase {
    IDLE,
    DRAGGING,
    SETTLING,
}

/**
 * 侧边栏横滑手势运动量。phase 与 offsetX 必须封装在同一个 observable 值里：
 * 归位时一次原子写入（SETTLING + 目标偏移），Kuikly 就能从手指最后一帧
 * 动画到精确端点（灵动岛 IslandGestureMotion 的同款约束）。
 */
data class DrawerGestureMotion(
    val phase: DrawerGesturePhase = DrawerGesturePhase.IDLE,
    /** 面板相对打开位的横向偏移：0 = 全开，-292 = 全关。DRAGGING 时为跟手值，SETTLING 时为目标值。 */
    val offsetX: Float = 0f,
)

/** A dense, value-first top-bar state for pages whose Hero has scrolled away. */
data class AppTopBarMetric(
    val label: String,
    val value: String,
    val change: String,
    val changeColor: Color,
    val flash: Boolean = false,
)

data class IslandGestureMotion(
    val phase: IslandGesturePhase = IslandGesturePhase.IDLE,
    val offsetY: Float = 0f,
    // A reset can have the same visual values as the current idle state.
    // Revision still invalidates the reactive layout so native anchors and
    // transforms are written again after returning from another page.
    val revision: Int = 0,
    // A forced/lifecycle reset (page cover racing a timer, returning from a
    // background page) must land on the idle geometry with no visible tween;
    // otherwise the card replays its morph from whatever frame was last on
    // screen the instant it becomes visible again.
    val snap: Boolean = false,
)

internal const val ISLAND_ANIMATION_RETURN = "island-gesture-return"
internal const val ISLAND_ANIMATION_CLOSE = "island-gesture-close"
internal const val ISLAND_ANIMATION_DETAIL = "island-gesture-detail"

/**
 * The compact, conversation-first chrome used by ChatHome.
 *
 * The liquid glass is detached: instead of one full-width bar, each live
 * control carries its own floating glass island so the material gathers
 * around the controls themselves.
 */
fun ViewContainer<*, *>.ChatTopNav(
    statusBarHeight: Float,
    theme: StockChatTheme,
    // Lambda, not Boolean: attr closures must call this in place so the
    // observable read happens inside the reactive closure (island-button
    // pattern, R1).  A Boolean snapshot captured by a wrapping lambda goes
    // stale — the island would keep reading the pre-open drawer state.
    drawerOpen: () -> Boolean,
    liveData: () -> Boolean,
    renderer: GlassRenderer = GlassRenderer.Default,
    contextTitle: String? = null,
    pageWidth: Float = 0f,
    pageHeight: Float = 0f,
    // Kuikly only re-runs attr/vif closures that read observables directly,
    // so reactive island inputs are accessors rather than frozen values.
    islandExpanded: () -> Boolean = { false },
    islandMounted: () -> Boolean = { true },
    islandQuote: () -> Quote? = { null },
    islandGestureMotion: () -> IslandGestureMotion = { IslandGestureMotion() },
    islandWatchlisted: () -> Boolean = { false },
    islandDropActive: () -> Boolean = { false },
    islandFirstCompareDrop: () -> Boolean = { false },
    islandCompareVisible: () -> Boolean = { false },
    islandTextOnly: () -> Boolean = { false },
    islandCompareLeftSymbol: () -> String = { "" },
    islandCompareRightSymbol: () -> String = { "" },
    islandCompareLeftQuote: () -> Quote? = { null },
    islandCompareRightQuote: () -> Quote? = { null },
    islandCompareInsightLoading: () -> Boolean = { false },
    islandCompareInsightAvailable: () -> Boolean = { false },
    // ===== 术语灵动岛（与股票行情岛同一形变体系，内容层分流）=====
    // 非空 = 岛当前承载术语讲解卡；此时行情卡层隐藏、术语卡层显示。
    islandTermEntry: () -> GlossaryEntry? = { null },
    islandCompareIsTerm: () -> Boolean = { false },
    islandTermCompareLeft: () -> GlossaryEntry? = { null },
    islandTermCompareRight: () -> GlossaryEntry? = { null },
    onToggleIsland: () -> Unit = {},
    onIslandGesture: (String, Float) -> Unit = { _, _ -> },
    onIslandMotionComplete: (String) -> Unit = {},
    onToggleIslandWatchlist: (String) -> Unit = {},
    onOpenIslandCompare: () -> Unit = {},
    onClearIslandCompare: () -> Unit = {},
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val islandPresented = { islandExpanded() || islandDropActive() || islandCompareVisible() }
    View {
        attr {
            // This chrome must float above the scroller.  If it participates in
            // the flex column, the page content is laid out below it and there
            // is nothing for the backdrop blur to refract.
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(statusBarHeight + 44f)
            paddingTop(statusBarHeight)
            // Solid page colour is kept only behind the system status bar.
            // The nav row sits on a transparent backdrop so its glass islands
            // float over page content; the ~3dp feather softens the edge.
            val chromeHeight = statusBarHeight + 44f
            val solidStop = (statusBarHeight / chromeHeight).coerceIn(0f, 1f)
            val featherStop = ((statusBarHeight + 3f) / chromeHeight).coerceIn(0f, 1f)
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(theme.page, 0f),
                ColorStop(theme.page, solidStop),
                ColorStop(theme.page.opacity(0f), featherStop),
            )
        }
        View {
            attr {
                height(44f)
                paddingLeft(10f)
                paddingRight(10f)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    size(44f, 44f); allCenter(); borderRadius(22f)
                    backgroundColor(theme.surface)
                    border(Border(0.5f, BorderStyle.SOLID, Color(0x000000, 0.05f)))
                    boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.14f)))
                    // 收起联动 + 跟手（用户反馈 2026-09-05 二轮）：按钮不再只挂
                    // expanded 翻转做独立补间，而是跟随卡片收缩的同一进度 p
                    // （1=完全归位可见，0=完全滑出隐藏）：
                    //   · 上滑拖拽收起 → p=closeProgress，随卡片缩小渐渐滑入；
                    //   · 松手 RETURNING/CLOSING → 与卡片共用 0.20s settle 补间；
                    //   · 点按开合 → easeIn 0.27 / easeOut 0.39（+30% 节奏）。
                    val e = islandPresented()
                    val motion = islandGestureMotion()
                    val closeProgress = (-motion.offsetY / 104f).coerceIn(0f, 1f)
                    val p = when {
                        motion.phase == IslandGesturePhase.CLOSING -> 1f
                        motion.phase == IslandGesturePhase.RETURNING -> closeProgress
                        motion.phase == IslandGesturePhase.DRAGGING && motion.offsetY < 0f -> closeProgress
                        !e -> 1f
                        else -> 0f
                    }
                    opacity(p)
                    transform(
                        scale = Scale(0.84f + 0.16f * p, 0.84f + 0.16f * p),
                        translate = Translate(0f, 0f, offsetX = -32f * (1f - p)),
                    )
                    touchEnable(!e)
                    if (motion.phase == IslandGesturePhase.CLOSING ||
                        motion.phase == IslandGesturePhase.RETURNING
                    ) {
                        val motionAnimationKey = islandGestureMotion()
                        val animationKey = if (motion.phase == IslandGesturePhase.CLOSING) {
                            ISLAND_ANIMATION_CLOSE
                        } else {
                            ISLAND_ANIMATION_RETURN
                        }
                        animate(Animation.easeOut(0.20f, key = animationKey), motionAnimationKey)
                    } else {
                        val expandedAnimationKey = islandPresented()
                        animate(
                            if (expandedAnimationKey) Animation.easeIn(0.27f) else Animation.easeOut(0.39f),
                            expandedAnimationKey,
                        )
                    }
                }
                Text { attr { text(if (drawerOpen()) "×" else "☰"); fontSizeScaled(22f); color(theme.textPrimary) } }
                event { click { onMenu() } }
            }
            // The middle slot stays empty: the floating title island below is
            // rendered as a sibling overlay so it can overflow this 44dp row
            // when it morphs into the quote card.
            View { attr { flex(1f) } }
            View {
                attr {
                    size(44f, 44f); allCenter(); borderRadius(22f)
                    backgroundColor(theme.surface)
                    border(Border(0.5f, BorderStyle.SOLID, Color(0x000000, 0.05f)))
                    boxShadow(BoxShadow(0f, 6f, 18f, Color(0x000000, 0.14f)))
                    // Mirror of the menu button above：同款进度联动 p，向右滑出。
                    val e = islandPresented()
                    val motion = islandGestureMotion()
                    val closeProgress = (-motion.offsetY / 104f).coerceIn(0f, 1f)
                    val p = when {
                        motion.phase == IslandGesturePhase.CLOSING -> 1f
                        motion.phase == IslandGesturePhase.RETURNING -> closeProgress
                        motion.phase == IslandGesturePhase.DRAGGING && motion.offsetY < 0f -> closeProgress
                        !e -> 1f
                        else -> 0f
                    }
                    opacity(p)
                    transform(
                        scale = Scale(0.84f + 0.16f * p, 0.84f + 0.16f * p),
                        translate = Translate(0f, 0f, offsetX = 32f * (1f - p)),
                    )
                    touchEnable(!e)
                    if (motion.phase == IslandGesturePhase.CLOSING ||
                        motion.phase == IslandGesturePhase.RETURNING
                    ) {
                        val motionAnimationKey = islandGestureMotion()
                        val animationKey = if (motion.phase == IslandGesturePhase.CLOSING) {
                            ISLAND_ANIMATION_CLOSE
                        } else {
                            ISLAND_ANIMATION_RETURN
                        }
                        animate(Animation.easeOut(0.20f, key = animationKey), motionAnimationKey)
                    } else {
                        val expandedAnimationKey = islandPresented()
                        animate(
                            if (expandedAnimationKey) Animation.easeIn(0.27f) else Animation.easeOut(0.39f),
                            expandedAnimationKey,
                        )
                    }
                }
                Text { attr { text("＋"); fontSizeScaled(23f); color(theme.brand) } }
                event { click { onNewChat() } }
            }
        }
    }
    vif({ islandMounted() }) {
        StockIsland(
            statusBarHeight = statusBarHeight,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            expanded = islandPresented,
            quote = islandQuote,
            gestureMotion = islandGestureMotion,
            watchlisted = islandWatchlisted,
            dropActive = islandDropActive,
            firstCompareDrop = islandFirstCompareDrop,
            compareVisible = islandCompareVisible,
            textOnly = islandTextOnly,
            compareLeftSymbol = islandCompareLeftSymbol,
            compareRightSymbol = islandCompareRightSymbol,
            compareLeftQuote = islandCompareLeftQuote,
            compareRightQuote = islandCompareRightQuote,
            compareInsightLoading = islandCompareInsightLoading,
            compareInsightAvailable = islandCompareInsightAvailable,
            termEntry = islandTermEntry,
            compareIsTerm = islandCompareIsTerm,
            termCompareLeft = islandTermCompareLeft,
            termCompareRight = islandTermCompareRight,
            liveData = liveData,
            title = contextTitle,
            theme = theme,
            renderer = renderer,
            onToggle = onToggleIsland,
            onGesture = onIslandGesture,
            onMotionComplete = onIslandMotionComplete,
            onToggleWatchlist = onToggleIslandWatchlist,
            onOpenCompare = onOpenIslandCompare,
            onClearCompare = onClearIslandCompare,
        )
    }
}
/**
 * Dynamic-island style interaction hub: the liquid-glass title capsule.
 *
 * Collapsed it is the familiar "StockChat." pill; a tap morphs it in place
 * (width / height / radius all animate together) into a live quote card,
 * and the identity layer cross-fades into the card layer.
 *
 * Kuikly only re-runs attr/vif closures that read observables *directly*,
 * so every reactive input arrives as an accessor and is invoked inside the
 * attr closures — never hoisted into builder-scope vals.
 */
private fun ViewContainer<*, *>.StockIsland(
    statusBarHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    expanded: () -> Boolean,
    quote: () -> Quote?,
    gestureMotion: () -> IslandGestureMotion,
    watchlisted: () -> Boolean,
    dropActive: () -> Boolean,
    firstCompareDrop: () -> Boolean,
    compareVisible: () -> Boolean,
    textOnly: () -> Boolean,
    compareLeftSymbol: () -> String,
    compareRightSymbol: () -> String,
    compareLeftQuote: () -> Quote?,
    compareRightQuote: () -> Quote?,
    compareInsightLoading: () -> Boolean,
    compareInsightAvailable: () -> Boolean,
    termEntry: () -> GlossaryEntry?,
    compareIsTerm: () -> Boolean,
    termCompareLeft: () -> GlossaryEntry?,
    termCompareRight: () -> GlossaryEntry?,
    liveData: () -> Boolean,
    title: String?,
    theme: StockChatTheme,
    renderer: GlassRenderer,
    onToggle: () -> Unit,
    onGesture: (String, Float) -> Unit,
    onMotionComplete: (String) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onOpenCompare: () -> Unit,
    onClearCompare: () -> Unit,
) {
    // Two horizontal insets for the two morph endpoints.  The collapsed pill
    // sits 60dp from the left edge (mirroring the side-button column so the
    // header reads as a balanced row of three capsules); the expanded card
    // snaps back to a 14dp gutter so it still reads as "almost full width".
    // 用户决策 2026-09-05：胶囊锚点常驻 60dp，侧边栏展开时不再横移让位——
    // 抽屉盖住它即可，收起后原位出现，全程没有横向跳动。
    val expandedIslandInset = 14f
    val collapsedIslandLeft = 60f
    // Collapsed pill width is fitted to the title text: a per-character advance
    // (bold weight, default font) plus a small side padding.  This keeps the
    // capsule tight around "StockChat" and stretches naturally when a longer
    // context title (e.g. "贵州茅台 600519.SH") is supplied.  Tuning notes:
    // 15pt bold ≈ 10dp/char on Kuikly default font, 13pt bold ≈ 8dp/char;
    // padding keeps the glyphs from touching the pill edge.
    val charAdvance = if (title == null) 10f else 8f
    val charCount = (title ?: "StockChat").length
    // Collapsed geometry scaled +10% (2026-09-05) to match the enlarged
    // 44dp side buttons; expanded card geometry is untouched.
    val collapsedWidth = ((charCount * charAdvance + 20f) * 1.1f).coerceAtLeast(88f)
    val collapsedHeight = 39.6f
    val collapsedRadius = 19.8f
    val expandedWidth = (pageWidth - expandedIslandInset * 2f).coerceAtLeast(collapsedWidth)
    val quoteHeight = 140f
    // 术语讲解卡：标题行 + 人话解释（≤3 行）+ A股例子（≤2 行），比行情卡高 18dp。
    val termHeight = 158f
    val fullScreenHeight = pageHeight.coerceAtLeast(quoteHeight)
    // Full-width transparent strip lays the island out from the left edge so
    // the pill keeps its 60dp offset in the collapsed state.  It has no event
    // handler, so taps outside the island fall through to the nav buttons and
    // the scroller underneath; only the morphing child below consumes
    // touches.  Animating width/height/radius on the child (instead of
    // absolute left) keeps layout and touch bounds in sync.
    View {
        attr {
            val motion = gestureMotion()
            val navigating = motion.phase == IslandGesturePhase.OPENING_DETAIL
            absolutePosition(top = if (navigating) 0f else statusBarHeight + 4f, left = 0f, right = 0f)
            flexDirectionRow()
            justifyContentFlexStart()
            if (motion.phase == IslandGesturePhase.OPENING_DETAIL) {
                animate(Animation.easeOut(0.18f), gestureMotion())
            } else if (motion.snap) {
                animate(Animation.linear(0f), gestureMotion())
            }
        }
        View {
            attr {
                val e = expanded()
                val motion = gestureMotion()
                val navigating = motion.phase == IslandGesturePhase.OPENING_DETAIL
                val dragY = motion.offsetY
                val closeProgress = (-dragY / 104f).coerceIn(0f, 1f)
                val spreadProgress = (dragY / 180f).coerceIn(0f, 1f)
                val dropTextOnly = dropActive() && !firstCompareDrop()
                val gestureEnabled = e && !dropTextOnly && !compareVisible()
                val targetWidth = when {
                    navigating -> pageWidth.coerceAtLeast(expandedWidth)
                    gestureEnabled && dragY < 0f ->
                        expandedWidth - (expandedWidth - collapsedWidth) * closeProgress
                    gestureEnabled && dragY > 0f ->
                        expandedWidth + (pageWidth - expandedWidth).coerceAtLeast(0f) * spreadProgress
                    e && !dropTextOnly -> expandedWidth
                    else -> collapsedWidth
                }
                // 跟手拖拽的高度基准必须取当前模式的展开态高度：术语卡 158f
                // 若沿用 quoteHeight，拖动起手第一帧会跳变 18dp。
                val expandedBaseHeight = if (termEntry() != null) termHeight else quoteHeight
                val targetHeight = when {
                    navigating -> fullScreenHeight
                    // 收回位移固定为 -104dp。股票卡 140 - 104 恰好接近收起态，
                    // 但术语卡 158 - 104 会停在 54dp，导致收回后的胶囊 Y 轴
                    // 留下固定的变高。负向拖拽须和宽度一样按收起进度插值，
                    // 使任意展开内容都精确落到 collapsedHeight。
                    gestureEnabled && dragY < 0f ->
                        expandedBaseHeight - (expandedBaseHeight - collapsedHeight) * closeProgress
                    gestureEnabled -> (expandedBaseHeight + dragY).coerceIn(collapsedHeight, fullScreenHeight)
                    e && dropTextOnly -> collapsedHeight
                    e && compareVisible() -> 146f
                    e && termEntry() != null -> termHeight
                    e -> quoteHeight
                    else -> collapsedHeight
                }
                val targetRadius = when {
                    navigating -> 0f
                    gestureEnabled && dragY < 0f -> 24f - 6f * closeProgress
                    gestureEnabled && dragY > 0f -> 24f - 12f * spreadProgress
                    e -> 24f
                    else -> collapsedRadius
                }
                width(targetWidth)
                height(targetHeight)
                borderRadius(targetRadius)
                // The horizontal anchor must interpolate on the SAME branches
                // as targetWidth.  If the collapsed dock is only applied after
                // expanded() flips false, the pill's left edge stays pinned at
                // the 14dp gutter while the width shrinks (drag-to-close), and
                // the dock offset teleports in one frame at release — the
                // "anchor is wrong" effect.  Mirroring the width branches keeps
                // left edge and right edge shrinking in lockstep.
                val collapsedLeft = collapsedIslandLeft
                val targetLeft = when {
                    navigating -> expandedIslandInset
                    gestureEnabled && dragY < 0f ->
                        expandedIslandInset -
                            (expandedIslandInset - collapsedLeft) * closeProgress
                    e && !dropTextOnly -> expandedIslandInset
                    else -> collapsedLeft
                }
                marginLeft(targetLeft)
                backgroundColor(theme.surface)
                border(Border(0.5f, BorderStyle.SOLID, Color(0x000000, 0.05f)))
                boxShadow(BoxShadow(0f, 8f, 22f, Color(0x000000, 0.16f)))
                transform(
                    translate = Translate(
                        0f,
                        0f,
                        offsetY = 0f,
                    )
                )
                // Motion phase and target offset live in one observable value.
                // This makes release a single atomic update, so Kuikly always
                // animates from the finger's last frame to one exact endpoint.
                val isGestureSettling =
                    motion.phase == IslandGesturePhase.RETURNING ||
                    motion.phase == IslandGesturePhase.CLOSING ||
                    motion.phase == IslandGesturePhase.OPENING_DETAIL
                if (isGestureSettling) {
                    val motionAnimationKey = gestureMotion()
                    val animationKey = when (motion.phase) {
                        IslandGesturePhase.RETURNING -> ISLAND_ANIMATION_RETURN
                        IslandGesturePhase.CLOSING -> ISLAND_ANIMATION_CLOSE
                        IslandGesturePhase.OPENING_DETAIL -> ISLAND_ANIMATION_DETAIL
                        else -> ""
                    }
                    animate(
                        Animation.easeOut(
                            if (motion.phase == IslandGesturePhase.OPENING_DETAIL) 0.18f else 0.20f,
                            key = animationKey,
                        ),
                        motionAnimationKey,
                    )
                } else {
                    val expandedAnimationKey = expanded()
                    // 0.34 → 0.44（用户决策 2026-09-05 放慢 30%）：与两侧按钮
                    // 的滑入/滑出保持同一节奏，整组开合联动。
                    animate(
                        if (motion.snap) Animation.linear(0f) else Animation.easeOut(0.44f),
                        expandedAnimationKey,
                    )
                }
            }
            event {
                animationCompletion { params ->
                    if (params.animationKey.isNotEmpty()) onMotionComplete(params.animationKey)
                }
            }

            // Collapsed identity layer: title only.  It owns taps only
            // while visible so the card beneath never swallows the toggle.
            View {
                attr {
                    val e = expanded()
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    // 与两侧按钮同款进度联动 p（用户反馈 2026-09-05 二轮）：
                    // "StockChat" 随卡片收缩进度渐渐显现——拖拽收起时跟手淡入、
                    // 松手与卡片共用 0.20s settle 归位、点按收起用 0.44s 补间
                    // 与形变同长，全程没有空白期。
                    val motion = gestureMotion()
                    val closeProgress = (-motion.offsetY / 104f).coerceIn(0f, 1f)
                    val p = when {
                        motion.phase == IslandGesturePhase.CLOSING -> 1f
                        motion.phase == IslandGesturePhase.RETURNING -> closeProgress
                        motion.phase == IslandGesturePhase.DRAGGING && motion.offsetY < 0f -> closeProgress
                        !e -> 1f
                        else -> 0f
                    }
                    opacity(p)
                    touchEnable(!e)
                    if (motion.phase == IslandGesturePhase.CLOSING ||
                        motion.phase == IslandGesturePhase.RETURNING
                    ) {
                        val motionAnimationKey = gestureMotion()
                        val animationKey = if (motion.phase == IslandGesturePhase.CLOSING) {
                            ISLAND_ANIMATION_CLOSE
                        } else {
                            ISLAND_ANIMATION_RETURN
                        }
                        animate(Animation.easeOut(0.20f, key = animationKey), motionAnimationKey)
                    } else {
                        val expandedAnimationKey = expanded()
                        animate(Animation.easeOut(0.44f), expandedAnimationKey)
                    }
                }
                Text {
                    attr {
                        text(title ?: "StockChat")
                        fontSizeScaled(if (title == null) 15f else 13f)
                        fontWeightBold()
                        color(if (title == null) theme.textPrimary else theme.textSecondary)
                    }
                }
                event { click { if (!expanded()) onToggle() } }
            }

            // Expanded quote-card layer.
            View {
                attr {
                    val e = expanded()
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    paddingLeft(16f)
                    paddingRight(16f)
                    paddingTop(12f)
                    paddingBottom(19f)
                    val motion = gestureMotion()
                    val dragY = motion.offsetY
                    val closeProgress = (-dragY / 104f).coerceIn(0f, 1f)
                    val spreadProgress = (dragY / 180f).coerceIn(0f, 1f)
                    val openingDetail = motion.phase == IslandGesturePhase.OPENING_DETAIL
                    // 术语模式下行情卡层让位给术语讲解卡（二者互斥，会话级分流）。
                    val showQuote = e && termEntry() == null && !dropActive() && !compareVisible() && !openingDetail
                    val gestureFade = maxOf(closeProgress, spreadProgress * 0.72f)
                    opacity(if (showQuote) 1f - gestureFade else 0f)
                    transform(
                        Translate(
                            0f,
                            when {
                                dragY < 0f -> -0.08f * closeProgress
                                dragY > 0f -> 0.05f * spreadProgress
                                else -> 0f
                            },
                        )
                    )
                    touchEnable(showQuote)
                    // Exactly one animate() call per pass: registering a second one
                    // for the same property silently clobbers the first, so the two
                    // timelines must stay mutually exclusive, mirroring the outer
                    // card's own isGestureSettling branch above.
                    val isGestureSettling =
                        motion.phase == IslandGesturePhase.RETURNING ||
                        motion.phase == IslandGesturePhase.CLOSING ||
                        motion.phase == IslandGesturePhase.OPENING_DETAIL
                    if (isGestureSettling) {
                        val motionAnimationKey = gestureMotion()
                        animate(
                            if (motion.snap) {
                                Animation.linear(0f)
                            } else {
                                Animation.easeOut(if (openingDetail) 0.18f else 0.20f)
                            },
                            motionAnimationKey,
                        )
                    } else if (motion.phase != IslandGesturePhase.DRAGGING) {
                        val expandedAnimationKey = expanded()
                        animate(
                            if (motion.snap) Animation.linear(0f) else Animation.easeOut(0.26f),
                            expandedAnimationKey,
                        )
                    }
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    // 真实行情未返回前不给占位名称/代码（不得用示例股票冒充）。
                    Text { attr { text(quote()?.name ?: "行情加载中"); fontSizeScaled(14f); fontWeightBold(); color(theme.textPrimary) } }
                    Text { attr { text(quote()?.symbol ?: ""); marginLeft(6f); fontSizeScaled(10f); color(theme.textTertiary) } }
                    View { attr { flex(1f) } }
                    View { attr { size(5f, 5f); borderRadius(3f); backgroundColor(if (liveData()) Color(0xFF34C759) else theme.textTertiary) } }
                    Text { attr { text(if (liveData()) "实时" else "模拟"); marginLeft(4f); fontSizeScaled(9f); color(theme.textTertiary) } }
                    // 自选按钮上移到右上角（用户反馈 2026-09-07）：原位置悬在底行
                    // 最右、四周是大片空白，且卡片底部 44dp 是下拉详情手势的捕获带。
                    // 右上角紧贴实时标识，视线动线顺（名称 → 代码 → 状态 → 操作）。
                    View {
                        attr {
                            marginLeft(8f)
                            height(20f)
                            paddingLeft(7f)
                            paddingRight(7f)
                            allCenter()
                            borderRadius(10f)
                            backgroundColor(if (watchlisted()) theme.brandSoft else theme.surfaceMuted)
                        }
                        Text {
                            attr {
                                text(if (watchlisted()) "✓ 自选" else "＋ 自选")
                                fontSizeScaled(10f)
                                fontWeightMedium()
                                color(if (watchlisted()) theme.brand else theme.textSecondary)
                            }
                        }
                        event {
                            click {
                                if (expanded()) quote()?.let { onToggleWatchlist(it.symbol) }
                            }
                        }
                    }
                }
                View {
                    // Loading hint occupies the same slot as the stats block;
                    // both stay mounted and trade visibility so the island
                    // never needs a structural rebuild when the quote lands.
                    attr {
                        flex(1f)
                        allCenter()
                        opacity(if (quote() == null) 1f else 0f)
                        touchEnable(false)
                    }
                    Text { attr { text("行情加载中…"); fontSizeScaled(12f); color(theme.textTertiary) } }
                }
                View {
                    attr {
                        absolutePosition(top = 44f, left = 16f, right = 16f, bottom = 23f)
                        opacity(if (quote() == null) 0f else 1f)
                        touchEnable(quote() != null)
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsFlexEnd() }
                        Text {
                            attr {
                                text(quote()?.let { Format.price(it.price) } ?: "--")
                                fontSizeScaled(26f)
                                fontWeightBold()
                                color(quote()?.let { if (it.rising) theme.rise else theme.fall } ?: theme.textPrimary)
                            }
                        }
                        View {
                            attr {
                                marginLeft(10f)
                                marginBottom(3f)
                                paddingLeft(7f)
                                paddingRight(7f)
                                paddingTop(2f)
                                paddingBottom(2f)
                                borderRadius(7f)
                                backgroundColor(quote()?.let { if (it.rising) theme.riseSoft else theme.fallSoft } ?: theme.surfaceMuted)
                            }
                            Text {
                                attr {
                                    text(quote()?.let { "${if (it.rising) "▲" else "▼"} ${Format.signed(it.change)}  ${Format.percent(it.changePercent)}" } ?: "--")
                                    fontSizeScaled(11f)
                                    fontWeightMedium()
                                    color(quote()?.let { if (it.rising) theme.rise else theme.fall } ?: theme.textTertiary)
                                }
                            }
                        }
                    }
                    View {
                        attr { marginTop(9f); flexDirectionRow(); alignItemsCenter() }
                        Text { attr { text(quote()?.let { "高 ${Format.price(it.high)}" } ?: "高 --"); fontSizeScaled(10f); color(theme.textSecondary) } }
                        Text { attr { text(quote()?.let { "低 ${Format.price(it.low)}" } ?: "低 --"); marginLeft(10f); fontSizeScaled(10f); color(theme.textSecondary) } }
                    }
                    // 右侧留白填上简笔分时（用户反馈 2026-09-07）：虚线昨收基准 +
                    // 单色折线，数据来自 quote.timeline（QuoteRepository 异步填充）；
                    // 分时未就绪时退化为 open/high/low/现价 合成的 5 点简笔示意，
                    // 右半区不再是一片空白。draw 闭包内读 quote() observable，
                    // 行情/分时到达时 ReactiveObserver 驱动重绘（composer 渐变描边同款）。
                    Canvas({
                        attr {
                            absolutePosition(right = 0f, top = 2f)
                            width(98f)
                            height(66f)
                        }
                    }) { canvas, width, canvasHeight ->
                        val q = quote() ?: return@Canvas
                        val points = q.timeline.ifEmpty { islandSketchPoints(q) }
                        if (points.isEmpty() || q.previousClose <= 0.0) return@Canvas
                        val geometry = TimeLineCalculator.calculate(points, width, canvasHeight, q.previousClose)
                        if (geometry.points.isEmpty()) return@Canvas
                        canvas.beginPath()
                        canvas.moveTo(0f, geometry.baselineY)
                        canvas.lineTo(width, geometry.baselineY)
                        canvas.setLineDash(listOf(3f, 4f))
                        canvas.strokeStyle(theme.divider)
                        canvas.lineWidth(1f)
                        canvas.stroke()
                        canvas.setLineDash(emptyList())
                        canvas.beginPath()
                        geometry.points.forEachIndexed { index, point ->
                            if (index == 0) canvas.moveTo(point.x, point.y) else canvas.lineTo(point.x, point.y)
                        }
                        canvas.strokeStyle(if (q.rising) theme.rise else theme.fall)
                        canvas.lineWidth(1.5f)
                        canvas.lineCapRound()
                        canvas.stroke()
                    }
                }
                // The visible handle stays intentionally small, while its
                // capture area is large enough for a reliable one-thumb swipe.
                // Up dismisses; down continues into the current stock detail.
                IslandGestureHandle(gestureMotion, onGesture)
            }

            // Expanded term-card layer：术语讲解卡（长按蓝色术语高亮进入）。
            // 与行情卡同一条形变/手势管线，只是内容层分流：标题行 + 分类 chip +
            // 人话解释 + A股例子；上滑收起、下滑进入术语表（与详情页分流）。
            // 布局必须与行情卡层同构：absolutePosition 铺满卡片 + 同款内边距。
            // 之前是普通流式子节点且零内边距——文字顶到卡片左缘、层高只随内容
            // 收缩，内部把手 absolutePosition(bottom=0) 锚不到卡片真实底边
            // （小白条悬浮在卡片中部、44dp 捕获带压住例句）。
            View {
                attr {
                    val e = expanded()
                    val motion = gestureMotion()
                    val dragY = motion.offsetY
                    val closeProgress = (-dragY / 104f).coerceIn(0f, 1f)
                    val spreadProgress = (dragY / 180f).coerceIn(0f, 1f)
                    val openingDetail = motion.phase == IslandGesturePhase.OPENING_DETAIL
                    val showTerm = e && termEntry() != null && !dropActive() && !compareVisible() && !openingDetail
                    val gestureFade = maxOf(closeProgress, spreadProgress * 0.72f)
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    // 与行情卡层同款水平/顶部内边距；底部 26dp 让例句末行
                    // 避开把手可见条（条体距卡底 13dp）。
                    paddingLeft(16f)
                    paddingRight(16f)
                    paddingTop(12f)
                    paddingBottom(26f)
                    opacity(if (showTerm) 1f - gestureFade else 0f)
                    transform(
                        Translate(
                            0f,
                            when {
                                dragY < 0f -> -0.08f * closeProgress
                                dragY > 0f -> 0.05f * spreadProgress
                                else -> 0f
                            },
                        )
                    )
                    touchEnable(showTerm)
                    // 与行情卡层同款：每个驱动恰好一次 animate（R2/R3）。
                    val isGestureSettling =
                        motion.phase == IslandGesturePhase.RETURNING ||
                        motion.phase == IslandGesturePhase.CLOSING ||
                        motion.phase == IslandGesturePhase.OPENING_DETAIL
                    if (isGestureSettling) {
                        val motionAnimationKey = gestureMotion()
                        animate(
                            if (motion.snap) {
                                Animation.linear(0f)
                            } else {
                                Animation.easeOut(if (openingDetail) 0.18f else 0.20f)
                            },
                            motionAnimationKey,
                        )
                    } else if (motion.phase != IslandGesturePhase.DRAGGING) {
                        val expandedAnimationKey = expanded()
                        animate(
                            if (motion.snap) Animation.linear(0f) else Animation.easeOut(0.26f),
                            expandedAnimationKey,
                        )
                    }
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(termEntry()?.term ?: ""); fontSizeScaled(14f); fontWeightBold(); color(theme.textPrimary) } }
                    View {
                        attr {
                            marginLeft(7f)
                            paddingLeft(6f)
                            paddingRight(6f)
                            paddingTop(2f)
                            paddingBottom(2f)
                            borderRadius(7f)
                            backgroundColor(theme.brandSoft)
                        }
                        Text { attr { text(termEntry()?.category?.label ?: ""); fontSizeScaled(9f); fontWeightMedium(); color(theme.term) } }
                    }
                    View { attr { flex(1f) } }
                    Text { attr { text("术语"); fontSizeScaled(9f); color(theme.textTertiary) } }
                }
                Text {
                    attr {
                        text(termEntry()?.plain ?: "")
                        marginTop(9f)
                        fontSizeScaled(12f)
                        lineHeightScaled(17f)
                        // 卡高 158f 的内容预算：标题行 20 + 9 + 3×17 + 7 + 2×15
                        // ≈ 117 ≤ 158-12-26。超限截断，防止溢出卡底被裁切。
                        lines(3)
                        color(theme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(termEntry()?.let { "例 ${it.example}" } ?: "")
                        marginTop(7f)
                        fontSizeScaled(10.5f)
                        lineHeightScaled(15f)
                        lines(2)
                        color(theme.textSecondary)
                    }
                }
                IslandGestureHandle(gestureMotion, onGesture)
            }

            // Drag target layer. It replaces the quote content while a stock is hovering so the
            // destination and the result of releasing are unambiguous.
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    allCenter()
                    opacity(if (dropActive()) 1f else 0f)
                    touchEnable(false)
                    backgroundColor(if (firstCompareDrop()) theme.brandSoft else Color(0xFFFFFFFF, 0f))
                    animate(Animation.easeOut(0.14f), dropActive())
                }
                View {
                    attr {
                        size(if (firstCompareDrop()) 42f else 0f, if (firstCompareDrop()) 42f else 0f)
                        borderRadius(21f)
                        allCenter()
                        backgroundColor(theme.brand)
                        opacity(if (firstCompareDrop()) 1f else 0f)
                    }
                    Text { attr { text("⇄"); fontSizeScaled(21f); fontWeightBold(); color(theme.onBrand) } }
                }
                Text {
                    attr {
                        text(
                            if (termEntry() != null) {
                                if (termCompareLeft() == null) "松手创建术语对比" else "松手加入术语对比"
                            } else {
                                if (compareLeftSymbol().isEmpty()) "松手创建股票对比" else "松手加入对比"
                            }
                        )
                        marginTop(if (firstCompareDrop()) 9f else 0f)
                        fontSizeScaled(13f)
                        fontWeightSemiBold()
                        color(theme.brand)
                    }
                }
            }

            // Comparison lobby. The first drop fills one slot and keeps the island open; the
            // second drop fills the other slot and creates the full comparison panel.
            View {
                attr {
                    // R5 修复（用户反馈 2026-09-05 退出对比残留文字）：可见性条件
                    // 必须全部无条件读取。原写法 `a && b && c && d` 短路求值，使
                    // animate() 绑定的最后一个 observable 随状态漂移（开态绑
                    // compareLeftSymbol、收起态绑 expanded），退出对比时上一周期
                    // 的注册与本次变更的 driver 错位，淡出丢失 → 文字残留原生层。
                    val e = expanded()
                    val dropping = dropActive()
                    val inCompare = compareVisible()
                    val hasLeft = compareLeftSymbol().isNotEmpty() || termCompareLeft() != null
                    val visible = e && !dropping && inCompare && hasLeft
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    padding(12f)
                    opacity(if (visible) 1f else 0f)
                    touchEnable(visible)
                    animate(Animation.easeOut(0.2f), visible)
                }
                // 硬清理兜底：退出对比时 vif 直接卸载全部内容——即使容器透明度
                // 动画注册再被竞态吃掉，也不可能残留任何对比文字。入场淡入不受
                // R4 影响：容器常驻挂载，只有内容随 visible 挂/卸，透明度渐变
                // 作用在容器上。
                vif({
                    expanded() && !dropActive() && compareVisible() &&
                        (compareLeftSymbol().isNotEmpty() || termCompareLeft() != null)
                }) {
                // 股票对比 lobby 与术语对比 lobby 互斥分流（会话级）。
                vif({ !compareIsTerm() }) {
                View {
                    attr { height(24f); flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text("股票对比"); fontSizeScaled(13f); fontWeightBold(); color(theme.textPrimary) } }
                    Text {
                        attr {
                            val ready = compareRightSymbol().isNotEmpty() &&
                                compareLeftQuote() != null && compareRightQuote() != null
                            text(
                                when {
                                    compareRightSymbol().isEmpty() -> "已选 1/2"
                                    compareInsightLoading() -> "AI 解读中"
                                    compareInsightAvailable() -> "含 AI 解读"
                                    ready -> "对比就绪"
                                    else -> "正在读取行情"
                                }
                            )
                            marginLeft(7f)
                            fontSizeScaled(10f)
                            color(theme.brand)
                        }
                    }
                    View { attr { flex(1f) } }
                    View {
                        attr { size(24f, 24f); allCenter(); borderRadius(12f); backgroundColor(theme.surfaceMuted) }
                        Text { attr { text("×"); fontSizeScaled(13f); color(theme.textSecondary) } }
                        event { click { onClearCompare() } }
                    }
                }
                View {
                    attr { height(52f); marginTop(7f); flexDirectionRow() }
                    CompareIslandSlot(
                        name = { compareLeftQuote()?.name ?: "正在读取" },
                        symbol = compareLeftSymbol,
                        quote = compareLeftQuote,
                        filled = { true },
                        theme = theme,
                    )
                    View { attr { width(8f) } }
                    CompareIslandSlot(
                        name = { compareRightQuote()?.name ?: "拖入另一只股票" },
                        symbol = compareRightSymbol,
                        quote = compareRightQuote,
                        filled = { compareRightSymbol().isNotEmpty() },
                        theme = theme,
                    )
                }
                View {
                    attr {
                        val ready = compareRightSymbol().isNotEmpty() &&
                            compareLeftQuote() != null && compareRightQuote() != null
                        height(25f)
                        marginTop(5f)
                        borderRadius(9f)
                        allCenter()
                        backgroundColor(if (ready) theme.brand else theme.surfaceMuted)
                    }
                    Text {
                        attr {
                            val ready = compareRightSymbol().isNotEmpty() &&
                                compareLeftQuote() != null && compareRightQuote() != null
                            text(
                                when {
                                    compareRightSymbol().isEmpty() -> "继续拖入股票实体"
                                    compareInsightLoading() -> "行情对比已生成，AI 解读中"
                                    compareInsightAvailable() -> "查看对比与 AI 解读"
                                    ready -> "查看对比"
                                    else -> "正在生成对比"
                                }
                            )
                            fontSizeScaled(10f)
                            fontWeightMedium()
                            color(if (ready) theme.onBrand else theme.textTertiary)
                        }
                    }
                    event {
                        click {
                            val ready = compareRightSymbol().isNotEmpty() &&
                                compareLeftQuote() != null && compareRightQuote() != null
                            if (ready) onOpenCompare()
                        }
                    }
                }
                }
                // 术语对比 lobby：第一只术语拖入占左槽，第二只占右槽（R5 同款
                // 可见性全部无条件读取，避免退出对比残留文字）。
                vif({ compareIsTerm() }) {
                View {
                    attr { height(24f); flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text("术语对比"); fontSizeScaled(13f); fontWeightBold(); color(theme.textPrimary) } }
                    Text {
                        attr {
                            text(
                                when {
                                    termCompareRight() == null -> "已选 1/2"
                                    compareInsightLoading() -> "AI 解读中"
                                    compareInsightAvailable() -> "含 AI 解读"
                                    else -> "对比就绪"
                                }
                            )
                            marginLeft(7f)
                            fontSizeScaled(10f)
                            color(theme.term)
                        }
                    }
                    View { attr { flex(1f) } }
                    View {
                        attr { size(24f, 24f); allCenter(); borderRadius(12f); backgroundColor(theme.surfaceMuted) }
                        Text { attr { text("×"); fontSizeScaled(13f); color(theme.textSecondary) } }
                        event { click { onClearCompare() } }
                    }
                }
                View {
                    attr { height(52f); marginTop(7f); flexDirectionRow() }
                    CompareIslandSlot(
                        name = { termCompareLeft()?.term ?: "正在读取" },
                        symbol = { termCompareLeft()?.category?.label ?: "" },
                        quote = { null },
                        filled = { true },
                        theme = theme,
                    )
                    View { attr { width(8f) } }
                    CompareIslandSlot(
                        name = { termCompareRight()?.term ?: "拖入另一个术语" },
                        symbol = { termCompareRight()?.category?.label ?: "" },
                        quote = { null },
                        filled = { termCompareRight() != null },
                        theme = theme,
                    )
                }
                View {
                    attr {
                        height(25f)
                        marginTop(5f)
                        borderRadius(9f)
                        allCenter()
                        backgroundColor(if (termCompareRight() != null) theme.brand else theme.surfaceMuted)
                    }
                    Text {
                        attr {
                            text(
                                when {
                                    termCompareRight() == null -> "继续拖入术语实体"
                                    compareInsightLoading() -> "对比已生成，AI 解读中"
                                    else -> "查看对比"
                                }
                            )
                            fontSizeScaled(10f)
                            fontWeightMedium()
                            color(if (termCompareRight() != null) theme.onBrand else theme.textTertiary)
                        }
                    }
                    event { click { if (termCompareRight() != null) onOpenCompare() } }
                }
                }
                }
            }
        }
    }
}

/**
 * 无分时数据时的简笔示意点：用真实 open / high / low / 现价按
 * 昨收 → 开盘 → 回踩 → 冲高 → 现价 的次序串成一条折线。
 * 只示意当日波动区间（简笔画，用户口径 2026-09-07），不伪造分钟级轨迹。
 */
private fun islandSketchPoints(q: Quote): List<QuotePoint> {
    if (q.price <= 0.0 || q.previousClose <= 0.0) return emptyList()
    val rising = q.rising
    return listOf(
        QuotePoint("", q.previousClose),
        QuotePoint("", if (q.open > 0.0) q.open else q.previousClose),
        QuotePoint("", if (rising) q.low else q.high),
        QuotePoint("", if (rising) q.high else q.low),
        QuotePoint("", q.price),
    )
}

/**
 * 灵动岛卡底手势条：行情卡与术语卡共用。可见把手刻意小（4dp），捕获区 44dp；
 * 上滑收起、下滑进入详情（股票）或术语表（术语）。pan 用 pageY，捕获区加高
 * 不影响跟踪精度。
 */
private fun ViewContainer<*, *>.IslandGestureHandle(
    gestureMotion: () -> IslandGestureMotion,
    onGesture: (String, Float) -> Unit,
) {
    View {
        attr {
            val motion = gestureMotion()
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            height(44f)
            alignItemsCenter()
            capture(CaptureRule.pan(CaptureRuleDirection.VERTICAL))
            touchEnable(motion.phase != IslandGesturePhase.OPENING_DETAIL)
        }
        View {
            attr {
                val motion = gestureMotion()
                val openingDetail = motion.phase == IslandGesturePhase.OPENING_DETAIL
                val dragging = motion.phase == IslandGesturePhase.DRAGGING
                width(44f)
                height(4f)
                // 44 - 4 - 13 = 27: keeps the visible bar 13dp above the card
                // bottom, exactly where it was with the old 27dp capture area.
                marginTop(27f)
                borderRadius(2f)
                // Grey grabber (iOS style): on the pure-white card the old
                // white@0.92 bar was invisible.
                backgroundColor(Color(0x000000, 0.18f))
                opacity(if (openingDetail) 0f else 1f)
                transform(
                    scale = Scale(
                        if (dragging) 1.08f else 1f,
                        if (dragging) 1.08f else 1f,
                    )
                )
                val motionAnimationKey = gestureMotion()
                animate(
                    if (motion.snap) Animation.linear(0f) else Animation.easeOut(0.16f),
                    motionAnimationKey,
                )
            }
        }
        event {
            pan { params ->
                // pageY remains stable while the handle itself moves with the
                // resizing card; local y would cancel out part of the finger
                // travel and feel detached.
                onGesture(params.state, params.pageY)
            }
        }
    }
}

private fun ViewContainer<*, *>.CompareIslandSlot(
    name: () -> String,
    symbol: () -> String,
    quote: () -> Quote?,
    filled: () -> Boolean,
    theme: StockChatTheme,
) {
    View {
        attr {
            flex(1f)
            height(52f)
            paddingLeft(10f)
            paddingRight(10f)
            justifyContentCenter()
            borderRadius(12f)
            backgroundColor(if (filled()) theme.surface else theme.surfaceMuted)
        }
        Text {
            attr {
                text(name())
                fontSizeScaled(11f)
                fontWeightMedium()
                color(if (filled()) theme.textPrimary else theme.textTertiary)
            }
        }
        Text {
            attr {
                text(symbol())
                marginTop(2f)
                fontSizeScaled(8f)
                color(theme.textTertiary)
            }
        }
        View {
            attr {
                marginTop(4f)
                flexDirectionRow()
                alignItemsCenter()
                opacity(if (quote() == null) 0f else 1f)
            }
            Text {
                attr {
                    text(quote()?.let { Format.price(it.price) } ?: "--")
                    fontSizeScaled(10f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text(quote()?.let { Format.percent(it.changePercent) } ?: "--")
                    marginLeft(6f)
                    fontSizeScaled(9f)
                    color(quote()?.let { if (it.rising) theme.rise else theme.fall } ?: theme.textTertiary)
                }
            }
        }
    }
}

fun ViewContainer<*, *>.ChatDrawer(
    statusBarHeight: Float,
    bottomInset: Float,
    theme: StockChatTheme,
    renderer: GlassRenderer = GlassRenderer.Default,
    visualLabel: String = renderer.statusLabel(),
    sessions: List<ChatSessionSummary> = emptyList(),
    // 历史会话搜索词（取值闭包，R1）：vbind 内现场读取，输入时列表实时过滤。
    historyQuery: () -> String = { "" },
    onHistoryQuery: (String) -> Unit = {},
    // Double-state presentation (CardSheet pattern): mounted via vif at the call
    // site, presented drives the open/close transition. Lambdas, not Boolean
    // params: the attr block must read the observable in place (island-button
    // pattern, AppChrome L144) or animate() silently binds to nothing and the
    // panel never slides in.
    presented: () -> Boolean = { true },
    interactive: () -> Boolean = presented,
    // 侧边栏横滑手势状态（取值闭包，不能传快照，理由同 presented）。
    gestureMotion: () -> DrawerGestureMotion = { DrawerGestureMotion() },
    onPan: (String, Float) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    onCycleVisualMode: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onToggleIsland: () -> Unit = {},
    onOpenGlossary: () -> Unit = {},
    onOpenWatchlist: () -> Unit = {},
    onOpenRiskMap: () -> Unit = {},
    onOpenMarket: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    onSettings: () -> Unit,
) {
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
            backgroundColor(Color(0x59000000))
            val shown = presented()
            val active = interactive()
            val motion = gestureMotion()
            // 跟手阶段遮罩随拖动进度渐显，松手后交给端点值 + 常规过渡。
            opacity(
                when (motion.phase) {
                    DrawerGesturePhase.DRAGGING ->
                        ((motion.offsetX + 292f) / 292f).coerceIn(0f, 1f)
                    else -> if (shown) 1f else 0f
                }
            )
            touchEnable(active)
            // 横向 pan 捕获：遮罩上左拖可跟手收起面板，纵向滚动与点击不受影响。
            capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            // 时长 +25%（用户决策 2026-09-05）：0.24 → 0.30，与面板同速。
            animate(Animation.easeOut(0.30f), presented())
        }
        event {
            click { onClose() }
            pan { params -> onPan(params.state, params.pageX) }
        }
    }
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, bottom = 0f)
            width(292f)
            paddingTop(statusBarHeight + 16f)
            paddingLeft(16f)
            paddingRight(16f)
            paddingBottom(bottomInset + 4f)
            // Solid sheet instead of frosted glass (2026-09-04): blur on
            // Android reads muddy at this size, a flat surface keeps rows
            // legible. 2026-09-09: follow theme.surface so dark mode reads
            // correctly (text colors inside already come from theme.*).
            backgroundColor(theme.surface)
            boxShadow(BoxShadow(-2f, 0f, 14f, Color(0x000000, 0.12f)))
            val motion = gestureMotion()
            val shown = presented()
            val active = interactive()
            // 手势接管期间偏移量由 DrawerGestureMotion 驱动（跟手值或归位目标值）；
            // 常规态沿用 presented 双状态端点。
            val targetX =
                if (motion.phase == DrawerGesturePhase.IDLE) {
                    if (shown) 0f else -292f
                } else {
                    motion.offsetX
                }
            transform(translate = Translate(0f, 0f, offsetX = targetX))
            // 跟手拖拽发生在面板上，此阶段必须可触（即便 drawerOpen 还没翻转）。
            touchEnable(active || motion.phase != DrawerGesturePhase.IDLE)
            // R2/R3：每个分支恰好一次 animate，且分支互斥；实参位置现场再读一次
            // observable，保证 animate 绑定到正确的驱动 key（RowGestureLayer 范式）。
            when (motion.phase) {
                DrawerGesturePhase.IDLE ->
                    // 菜单按钮开合：开先快后慢（easeOut），关先慢后快（easeIn）。
                    // 时长 +25%（用户决策 2026-09-05）：0.30→0.375 / 0.22→0.275。
                    animate(
                        if (shown) Animation.easeOut(0.375f) else Animation.easeIn(0.275f),
                        presented(),
                    )
                DrawerGesturePhase.DRAGGING -> Unit // 跟手：直接落位，不注册动画
                DrawerGesturePhase.SETTLING ->
                    // 手势归位：展开先快后慢（easeOut），收起先慢后快（easeIn）。
                    // 时长 +25%：0.30→0.375 / 0.24→0.30。
                    animate(
                        if (motion.offsetX > -146f) Animation.easeOut(0.375f) else Animation.easeIn(0.30f),
                        gestureMotion(),
                    )
            }
            // 横向 pan 捕获：在面板任意位置左拖即可收起；内部纵向列表滚动不受影响。
            capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
        }
        event {
            pan { params -> onPan(params.state, params.pageX) }
        }

        // 顶部一行（2026-09-08 四轮）：历史搜索框 + 右侧「＋」小按钮（原
        // 新会话大按钮与头部行合并）。wordmark 移到底部固定栏的头像标题。
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    flex(1f)
                    height(36f)
                    paddingLeft(10f)
                    paddingRight(10f)
                    flexDirectionRow()
                    alignItemsCenter()
                    borderRadius(18f)
                    backgroundColor(theme.surfaceMuted)
                }
                LineIconSearch(theme.textTertiary, 15f)
                Input {
                    attr {
                        flex(1f)
                        // 横向容器 + alignItemsCenter 下 flex 只管宽度，高度必须显式给，
                        // 否则输入框塌 0 点不中（ApiConfig 是纵向容器 flex 即满高，无此问题）。
                        height(32f)
                        marginLeft(6f)
                        fontSizeScaled(13f)
                        color(theme.textPrimary)
                        placeholder("搜索历史会话")
                        placeholderColor(theme.textTertiary)
                    }
                    event { textDidChange { onHistoryQuery(it.text) } }
                }
            }
            View {
                attr {
                    size(36f, 36f)
                    marginLeft(8f)
                    allCenter()
                    borderRadius(12f)
                    backgroundColor(theme.brand)
                    boxShadow(BoxShadow(0f, 4f, 10f, theme.brand.opacity(0.35f)))
                }
                LineIconPlus(theme.onBrand, 17f)
                event { click { onClose(); onNewChat() } }
            }
        }

        // 整合后的功能入口：个人空间 / 市场行情 两组磁贴（3 列）。历史会话
        // 沉到底部（2026-09-08 二轮：高频入口靠上，会话记录靠下）。
        DrawerGroupTitle("个人空间", theme)
        View {
            attr { flexDirectionRow() }
            DrawerTile("自选股", theme, icon = { LineIconStar(theme.textPrimary, 22f) }) { onClose(); onOpenWatchlist() }
            View { attr { width(8f) } }
            DrawerTile("风险地图", theme, icon = { LineIconShieldCheck(theme.textPrimary, 22f) }) { onClose(); onOpenRiskMap() }
            View { attr { width(8f) } }
            DrawerTile("术语表", theme, icon = { LineIconBook(theme.textPrimary, 22f) }) { onClose(); onOpenGlossary() }
        }
        DrawerGroupTitle("市场行情", theme)
        View {
            attr { flexDirectionRow() }
            DrawerTile("市场总览", theme, icon = { LineIconBarChart(theme.textPrimary, 22f) }) { onClose(); onOpenMarket() }
            View { attr { width(8f) } }
            DrawerTile("异动预警", theme, icon = { LineIconBell(theme.textPrimary, 22f) }) { onClose(); onOpenAlerts() }
            // 全局搜索入口已移到底部固定栏（2026-09-08 五轮）。
        }

        // Conversation history, grouped by recency. It owns the remaining
        // height so it stays pinned to the bottom of the drawer. 搜索框已
        // 上移到顶部一行；列表包 vbind 现场 read historyQuery（R1），输入
        // 即时过滤。条目去背景、只展示总结标题（2026-09-08 三轮）。
        Scroller {
            attr { flex(1f); marginTop(14f) }
            vbind({ historyQuery() }) {
                val q = historyQuery().trim()
                val visible = if (q.isEmpty()) sessions else sessions.filter {
                    it.title.contains(q, ignoreCase = true)
                }
                if (sessions.isEmpty()) {
                    DrawerEmptyHistory(theme)
                } else if (visible.isEmpty()) {
                    Text {
                        attr {
                            text("没有匹配的会话")
                            marginTop(14f)
                            fontSizeScaled(13f)
                            color(theme.textTertiary)
                        }
                    }
                } else {
                    var lastGroup = ""
                    visible.forEach { session ->
                        if (session.groupTitle != lastGroup) {
                            DrawerGroupTitle(session.groupTitle, theme)
                            lastGroup = session.groupTitle
                        }
                        DrawerSessionItem(title = session.title, theme = theme) {
                            onClose()
                            onOpenSession(session.id)
                        }
                    }
                }
            }
        }

        // Bottom fixed bar（2026-09-08 四轮）：头像 + 标题在左、设置在右，
        // 均固定不随会话列表滚动。
        View {
            attr {
                marginTop(8f)
                height(44f)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr { size(26f, 26f); borderRadius(8f); allCenter(); backgroundColor(theme.brand) }
                Text { attr { text("S"); fontSizeScaled(13f); fontWeightBold(); color(theme.onBrand) } }
            }
            Text {
                attr {
                    text("StockChat")
                    marginLeft(8f)
                    flex(1f)
                    fontSizeScaled(14f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                }
            }
            View {
                attr { size(32f, 32f); allCenter() }
                LineIconSearch(theme.textSecondary, 19f)
                event { click { onClose(); onOpenSearch() } }
            }
            View {
                attr { size(32f, 32f); marginLeft(6f); allCenter() }
                LineIconSliders(theme.textSecondary, 19f)
                event { click { onSettings() } }
            }
        }
    }
}

private fun ViewContainer<*, *>.DrawerGroupTitle(text: String, theme: StockChatTheme) {
    Text { attr { text(text); marginTop(14f); marginBottom(4f); marginLeft(4f); fontSizeScaled(11f); fontWeightSemiBold(); color(theme.textTertiary) } }
}

/**
 * 历史会话条目：纯文字行（无背景、无 preview），只展示总结标题
 * （2026-09-08 三轮：去背景 + 仅题目）。标题超长时单行截断。
 */
private fun ViewContainer<*, *>.DrawerSessionItem(
    title: String,
    theme: StockChatTheme,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(38f)
            marginTop(2f)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text(title)
                fontSizeScaled(14f)
                color(theme.textPrimary)
                lines(1)
            }
        }
        event { click { onClick() } }
    }
}

private fun ViewContainer<*, *>.DrawerEmptyHistory(theme: StockChatTheme) {
    View {
        attr {
            marginTop(18f)
            paddingLeft(12f)
            paddingRight(12f)
            paddingTop(14f)
            paddingBottom(14f)
            borderRadius(12f)
            backgroundColor(theme.surfaceMuted)
        }
        Text { attr { text("还没有历史记录"); fontSizeScaled(14f); fontWeightMedium(); color(theme.textSecondary); textAlignCenter() } }
        Text { attr { text("开始提问后会自动保存"); marginTop(5f); fontSizeScaled(11f); color(theme.textTertiary); textAlignCenter() } }
    }
}

/**
 * 抽屉功能磁贴：白卡 + 细描边 + 浅投影，线条图标（LineIcons，Lucide 对齐）
 * 大尺寸 + 小标签（2026-09-08 五轮：图标 22 / 文字 10，修正"图标小字大"
 * 的比例失调）。icon 传绘制闭包，onClick 普通闭包，无 R2-R5 涉及。
 */
private fun ViewContainer<*, *>.DrawerTile(
    label: String,
    theme: StockChatTheme,
    icon: ViewContainer<*, *>.() -> Unit,
    onClick: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            height(64f)
            flexDirectionColumn()
            allCenter()
            borderRadius(13f)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
            boxShadow(BoxShadow(0f, 2f, 8f, Color(0x000000, 0.06f)))
        }
        icon()
        Text { attr { text(label); marginTop(5f); fontSizeScaled(10f); color(theme.textSecondary) } }
        event { click { onClick() } }
    }
}

fun ViewContainer<*, *>.AppTopBar(
    title: String,
    subtitle: String,
    statusBarHeight: Float,
    theme: StockChatTheme,
    renderer: GlassRenderer = GlassRenderer.Default,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    // Scroll-driven state arrives as value closures, not plain values (same rule
    // as ChatDrawer's presented/interactive above). A caller reading its own
    // observable in the page body would hand us a first-frame snapshot: attr
    // never re-runs, and animate() finds no observablePropertyKey, so the
    // compact handoff silently never plays. These must be READ INSIDE attr.
    compactLine: () -> String? = { null },
    compactLineColor: () -> Color? = { null },
    compactVisible: () -> Boolean = { compactLine() != null },
    progress: () -> Float? = { null },
    reduceMotion: Boolean = false,
    compactMetrics: () -> List<AppTopBarMetric> = { emptyList() },
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    // 2026-09-08：顶栏统一去毛玻璃，改为 theme.surface 实色 + 发丝分隔线
    // （与详情页原型一致）。renderer 参数保留以兼容既有调用点，当前不参与绘制。
) {
    View {
        attr {
            // All app chrome is a floating material; the page scroller is its
            // backdrop source and must remain visible underneath it.
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(statusBarHeight + 57f)
            paddingTop(statusBarHeight)
        }
        View {
            attr {
                absolutePositionAllZero()
                backgroundColor(theme.surface)
                touchEnable(false)
            }
        }
        View {
            attr {
                height(56f)
                paddingLeft(16f)
                paddingRight(12f)
                flexDirectionRow()
                alignItemsCenter()
            }
            if (backLabel != null) {
                View {
                    attr { paddingRight(8f); minWidth(44f); height(44f); justifyContentCenter() }
                    Text {
                        attr {
                            text(backLabel)
                            // 单字符（‹）按大号图形字号渲染，文字标签（返回）保持常规。
                            fontSizeScaled(if (backLabel.length == 1) 22f else 15f)
                            fontWeightMedium()
                            color(theme.brand)
                        }
                    }
                    event { click { onBack() } }
                }
            }
            View {
                attr { flex(1f); height(56f); justifyContentCenter() }
                View {
                    attr {
                        // Every non-driving read happens first; animate() goes
                        // last so compactVisible's read is the one that owns the
                        // animation key (Attr.animate uses the LAST observable
                        // read in the block, not its `value` argument).
                        val dense = compactMetrics().isNotEmpty()
                        val compact = compactVisible()
                        opacity(if (dense && compact) 0f else 1f)
                        touchEnable(!dense || !compact)
                        if (!reduceMotion && dense) {
                            transform(Translate(0f, if (compact) -0.08f else 0f))
                            animate(Animation.easeOut(0.20f), compactVisible())
                        }
                    }
                    Text { attr { text(title); fontSizeScaled(19f); fontWeightBold(); color(theme.textPrimary) } }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); marginTop(2f) }
                        Text { attr { text(subtitle); fontSizeScaled(10.5f); color(theme.textTertiary) } }
                        vif({ compactLine() != null }) {
                            Text {
                                attr {
                                    text(compactLine() ?: "")
                                    marginLeft(8f)
                                    fontSizeScaled(10f)
                                    fontWeightSemiBold()
                                    color(compactLineColor() ?: theme.textSecondary)
                                    val compact = compactVisible()
                                    opacity(if (compact) 1f else 0f)
                                    if (!reduceMotion) {
                                        transform(Translate(0f, if (compact) 0f else -0.12f))
                                        animate(Animation.easeOut(0.18f), compactVisible())
                                    }
                                }
                            }
                        }
                    }
                }
                vif({ compactMetrics().isNotEmpty() }) {
                    View {
                        attr {
                            absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                            zIndex(2, useOutline = false)
                            val compact = compactVisible()
                            opacity(if (compact) 1f else 0f)
                            touchEnable(compact)
                            if (!reduceMotion) {
                                transform(Translate(0f, if (compact) 0f else 0.08f))
                                animate(Animation.easeOut(0.20f), compactVisible())
                            }
                        }
                        // The fade lives on the container above; only the row
                        // rebuilds when the numbers change, so a quote update
                        // never interrupts an in-flight compact handoff.
                        View { attr { height(56f); flexDirectionRow(); alignItemsCenter() }
                            vbind({ compactMetrics() }) {
                                compactMetrics().forEachIndexed { index, metric ->
                                    View {
                                        attr {
                                            flex(1f)
                                            paddingLeft(if (index == 0) 0f else 4f)
                                            paddingRight(4f)
                                            borderRadius(5f)
                                            // Compact market metrics are L1 only: no
                                            // panel/background flash, just a brief
                                            // semantic colour change on the value.
                                            backgroundColor(theme.surface.opacity(0f))
                                        }
                                        Text { attr { text(metric.label); fontSizeScaled(8.5f); color(theme.textTertiary) } }
                                        View { attr { marginTop(2f); flexDirectionRow(); alignItemsCenter() }
                                            Text { attr { text(metric.value); fontSizeScaled(10.5f); fontWeightBold(); color(if (metric.flash) metric.changeColor else theme.textPrimary) } }
                                            Text { attr { text(metric.change); marginLeft(3f); fontSizeScaled(8.5f); fontWeightSemiBold(); color(metric.changeColor) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            actions.forEach { (label, action) ->
                // 2026-09-08：去圈圈框框，动作只保留实体字形、放大到 44pt 触控区，
                // 靠字号与字重撑住存在感，不再用底色/描边圈住。
                View {
                    attr {
                        marginLeft(4f)
                        minWidth(44f)
                        height(44f)
                        allCenter()
                    }
                    Text {
                        attr {
                            text(label)
                            // 符号字形（＋ ✓ ⋯）给图形级字号；两字以上是文字动作。
                            fontSizeScaled(if (label.length > 1) 15f else 21f)
                            fontWeightSemiBold()
                            color(if (label == "+" || label == "✓") theme.brand else theme.textPrimary)
                        }
                    }
                    event { click { action() } }
                }
            }
        }
        vif({ progress() != null }) {
            View {
                attr {
                    height(2f)
                    flexDirectionRow()
                    // 轨道只做暗示不做分割感：淡到几乎不可见，进度填充才是主角。
                    backgroundColor(theme.divider.opacity(0.30f))
                }
                // Read inside attr so scroll progress actually tracks; the two
                // flex weights are one fact, so both read the same closure.
                View { attr { flex((progress() ?: 0f).coerceIn(0f, 1f).coerceAtLeast(0.001f)); backgroundColor(theme.brand) } }
                View { attr { flex((1f - (progress() ?: 0f).coerceIn(0f, 1f)).coerceAtLeast(0.001f)) } }
            }
        }
    }
}

fun ViewContainer<*, *>.DataModeBadge(
    theme: StockChatTheme,
    text: String = "离线演示模式",
    renderer: GlassRenderer = GlassRenderer.Default,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            paddingTop(5f)
            paddingBottom(5f)
            paddingLeft(8f)
            paddingRight(8f)
        }
        GlassBackdrop(theme.glass.peek, renderer)
        View { attr { size(6f, 6f); borderRadius(3f); backgroundColor(theme.brand); marginRight(5f) } }
        Text { attr { text(text); fontSizeScaled(10f); fontWeightMedium(); color(theme.brand) } }
    }
}
