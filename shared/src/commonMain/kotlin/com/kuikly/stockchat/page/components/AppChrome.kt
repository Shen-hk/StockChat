package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatSessionSummary
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.Quote
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
    drawerOpen: Boolean,
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
                    size(40f, 40f); allCenter(); borderRadius(20f)
                    // While the island morphs into the quote card, both side
                    // controls retreat outward and fade so the card owns the
                    // header.  Exit is short and accelerating (easeIn 0.21s)
                    // so the button always stays ahead of the expanding
                    // island edge — a longer fade would read as the button
                    // being swallowed by the card.  The return is slower and
                    // slightly delayed (easeOut 0.30s + 0.06s) so the card
                    // settles before the controls come back.
                    val e = islandPresented()
                    opacity(if (e) 0f else 1f)
                    transform(
                        scale = Scale(if (e) 0.84f else 1f, if (e) 0.84f else 1f),
                        translate = Translate(0f, 0f, offsetX = if (e) -32f else 0f),
                    )
                    touchEnable(!e)
                    animate(if (e) Animation.easeIn(0.21f) else Animation.easeOut(0.30f).delay(0.06f), e)
                }
                GlassBackdrop(theme.glass.peek, renderer)
                Text { attr { text(if (drawerOpen) "×" else "☰"); fontSize(22f); color(theme.textPrimary) } }
                event { click { onMenu() } }
            }
            // The middle slot stays empty: the floating title island below is
            // rendered as a sibling overlay so it can overflow this 44dp row
            // when it morphs into the quote card.
            View { attr { flex(1f) } }
            View {
                attr {
                    size(40f, 40f); allCenter(); borderRadius(20f)
                    // Mirror of the menu button above: same retreat motion,
                    // pushed to the right instead of the left.
                    val e = islandPresented()
                    opacity(if (e) 0f else 1f)
                    transform(
                        scale = Scale(if (e) 0.84f else 1f, if (e) 0.84f else 1f),
                        translate = Translate(0f, 0f, offsetX = if (e) 32f else 0f),
                    )
                    touchEnable(!e)
                    animate(if (e) Animation.easeIn(0.21f) else Animation.easeOut(0.30f).delay(0.06f), e)
                }
                GlassBackdrop(theme.glass.peek, renderer)
                Text { attr { text("＋"); fontSize(23f); color(theme.brand) } }
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
    val collapsedWidth = if (title == null) 128f else 200f
    val expandedWidth = (pageWidth - 28f).coerceAtLeast(collapsedWidth)
    val quoteHeight = 140f
    val fullScreenHeight = pageHeight.coerceAtLeast(quoteHeight)
    // Full-width transparent strip centres the island via flex.  It has no
    // event handler, so taps outside the island fall through to the nav
    // buttons and the scroller underneath; only the morphing child below
    // consumes touches.  Animating width/height/radius on the child (instead
    // of absolute left) keeps layout and touch bounds in sync.
    View {
        attr {
            val motion = gestureMotion()
            val navigating = motion.phase == IslandGesturePhase.OPENING_DETAIL
            absolutePosition(top = if (navigating) 0f else statusBarHeight + 4f, left = 0f, right = 0f)
            flexDirectionRow()
            justifyContentCenter()
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
                val targetHeight = when {
                    navigating -> fullScreenHeight
                    gestureEnabled -> (quoteHeight + dragY).coerceIn(36f, fullScreenHeight)
                    e && dropTextOnly -> 36f
                    e && compareVisible() -> 146f
                    e -> quoteHeight
                    else -> 36f
                }
                val targetRadius = when {
                    navigating -> 0f
                    gestureEnabled && dragY < 0f -> 24f - 6f * closeProgress
                    gestureEnabled && dragY > 0f -> 24f - 12f * spreadProgress
                    e -> 24f
                    else -> 18f
                }
                width(targetWidth)
                height(targetHeight)
                borderRadius(targetRadius)
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
                    animate(
                        if (motion.snap) Animation.linear(0f) else Animation.easeOut(0.34f),
                        expandedAnimationKey,
                    )
                }
            }
            event {
                animationCompletion { params ->
                    if (params.animationKey.isNotEmpty()) onMotionComplete(params.animationKey)
                }
            }
            vif({ (!textOnly() || expanded() || compareVisible()) && (!dropActive() || firstCompareDrop()) }) {
                GlassBackdrop(theme.glass.peek, renderer)
            }

            // Collapsed identity layer: title + live dot.  It owns taps only
            // while visible so the card beneath never swallows the toggle.
            View {
                attr {
                    val e = expanded()
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    opacity(if (e) 0f else 1f)
                    touchEnable(!e)
                    animate(Animation.easeOut(0.18f), e)
                }
                Text {
                    attr {
                        text(title ?: "StockChat.")
                        fontSize(if (title == null) 15f else 13f)
                        fontWeightBold()
                        color(if (title == null) theme.textPrimary else theme.textSecondary)
                    }
                }
                View {
                    attr {
                        size(5f, 5f)
                        marginLeft(7f)
                        borderRadius(3f)
                        backgroundColor(if (liveData()) Color(0xFF34C759) else theme.textTertiary)
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
                    val showQuote = e && !dropActive() && !compareVisible() && !openingDetail
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
                    Text { attr { text(quote()?.name ?: "贵州茅台"); fontSize(14f); fontWeightBold(); color(theme.textPrimary) } }
                    Text { attr { text(quote()?.symbol ?: "600519.SH"); marginLeft(6f); fontSize(10f); color(theme.textTertiary) } }
                    View { attr { flex(1f) } }
                    View { attr { size(5f, 5f); borderRadius(3f); backgroundColor(if (liveData()) Color(0xFF34C759) else theme.textTertiary) } }
                    Text { attr { text(if (liveData()) "实时" else "模拟"); marginLeft(4f); fontSize(9f); color(theme.textTertiary) } }
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
                    Text { attr { text("行情加载中…"); fontSize(12f); color(theme.textTertiary) } }
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
                                fontSize(26f)
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
                                    fontSize(11f)
                                    fontWeightMedium()
                                    color(quote()?.let { if (it.rising) theme.rise else theme.fall } ?: theme.textTertiary)
                                }
                            }
                        }
                    }
                    View {
                        attr { marginTop(9f); flexDirectionRow(); alignItemsCenter() }
                        Text { attr { text(quote()?.let { "高 ${Format.price(it.high)}" } ?: "高 --"); fontSize(10f); color(theme.textSecondary) } }
                        Text { attr { text(quote()?.let { "低 ${Format.price(it.low)}" } ?: "低 --"); marginLeft(10f); fontSize(10f); color(theme.textSecondary) } }
                        View { attr { flex(1f) } }
                        View {
                            attr {
                                height(24f)
                                paddingLeft(7f)
                                paddingRight(7f)
                                allCenter()
                                borderRadius(8f)
                                backgroundColor(if (watchlisted()) theme.brandSoft else theme.surfaceMuted)
                            }
                            Text {
                                attr {
                                    text(if (watchlisted()) "✓ 自选" else "＋ 自选")
                                    fontSize(10f)
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
                }
                // The visible handle stays intentionally small, while its
                // capture area is large enough for a reliable one-thumb swipe.
                // Up dismisses; down continues into the current stock detail.
                View {
                    attr {
                        val motion = gestureMotion()
                        absolutePosition(left = 0f, right = 0f, bottom = 0f)
                        height(27f)
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
                            marginTop(10f)
                            borderRadius(2f)
                            backgroundColor(Color(0xFFFFFFFF, 0.92f))
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
                            // pageY remains stable while the handle itself moves
                            // with the resizing card; local y would cancel out
                            // part of the finger travel and feel detached.
                            onGesture(params.state, params.pageY)
                        }
                    }
                }
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
                    Text { attr { text("⇄"); fontSize(21f); fontWeightBold(); color(theme.onBrand) } }
                }
                Text {
                    attr {
                        text(if (compareLeftSymbol().isEmpty()) "松手创建股票对比" else "松手加入对比")
                        marginTop(if (firstCompareDrop()) 9f else 0f)
                        fontSize(13f)
                        fontWeightSemiBold()
                        color(theme.brand)
                    }
                }
            }

            // Comparison lobby. The first drop fills one slot and keeps the island open; the
            // second drop fills the other slot and creates the full comparison panel.
            View {
                attr {
                    val visible = expanded() && !dropActive() && compareVisible() && compareLeftSymbol().isNotEmpty()
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    padding(12f)
                    opacity(if (visible) 1f else 0f)
                    touchEnable(visible)
                    animate(Animation.easeOut(0.2f), visible)
                }
                View {
                    attr { height(24f); flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text("股票对比"); fontSize(13f); fontWeightBold(); color(theme.textPrimary) } }
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
                            fontSize(10f)
                            color(theme.brand)
                        }
                    }
                    View { attr { flex(1f) } }
                    View {
                        attr { size(24f, 24f); allCenter(); borderRadius(12f); backgroundColor(theme.surfaceMuted) }
                        Text { attr { text("×"); fontSize(13f); color(theme.textSecondary) } }
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
                            fontSize(10f)
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
                fontSize(11f)
                fontWeightMedium()
                color(if (filled()) theme.textPrimary else theme.textTertiary)
            }
        }
        Text {
            attr {
                text(symbol())
                marginTop(2f)
                fontSize(8f)
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
                    fontSize(10f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text(quote()?.let { Format.percent(it.changePercent) } ?: "--")
                    marginLeft(6f)
                    fontSize(9f)
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
    liveData: Boolean,
    renderer: GlassRenderer = GlassRenderer.Default,
    visualLabel: String = renderer.statusLabel(),
    sessions: List<ChatSessionSummary> = emptyList(),
    activeSessionId: String = "",
    // Double-state presentation (CardSheet pattern): mounted via vif at the call
    // site, presented drives the open/close transition. Lambdas, not Boolean
    // params: the attr block must read the observable in place (island-button
    // pattern, AppChrome L144) or animate() silently binds to nothing and the
    // panel never slides in.
    presented: () -> Boolean = { true },
    interactive: () -> Boolean = presented,
    onClose: () -> Unit,
    onToggleDataMode: () -> Unit,
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
            opacity(if (shown) 1f else 0f)
            touchEnable(active)
            animate(Animation.easeOut(0.24f), shown)
        }
        event { click { onClose() } }
    }
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, bottom = 0f)
            width(292f)
            paddingTop(statusBarHeight + 16f)
            paddingLeft(16f)
            paddingRight(16f)
            paddingBottom(bottomInset + 14f)
            // Solid white sheet instead of frosted glass (2026-09-04): blur on
            // Android reads muddy at this size, a flat surface keeps rows legible.
            backgroundColor(Color(0xFFFFFFFF))
            boxShadow(BoxShadow(-2f, 0f, 14f, Color(0x000000, 0.12f)))
            // Slide from the left edge; open eases out, close eases in faster
            // so dismissal feels lighter than presentation (doc 22 L2).
            // shown must be read in place: see the island-button pattern note.
            val shown = presented()
            val active = interactive()
            transform(translate = Translate(0f, 0f, offsetX = if (shown) 0f else -292f))
            touchEnable(active)
            animate(if (shown) Animation.easeOut(0.30f) else Animation.easeIn(0.22f), shown)
        }

        // Brand header: gradient logo mark, wordmark and close button.
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    size(38f, 38f)
                    borderRadius(12f)
                    allCenter()
                    backgroundLinearGradient(
                        Direction.TO_RIGHT,
                        ColorStop(theme.brand, 0f),
                        ColorStop(theme.term, 1f),
                    )
                }
                Text { attr { text("S"); fontSize(18f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
            }
            View {
                attr { flex(1f); marginLeft(10f) }
                Text { attr { text("StockChat"); fontSize(17f); fontWeightBold(); color(theme.textPrimary) } }
                Text { attr { text("AI 投资助手"); marginTop(1f); fontSize(10f); color(theme.textTertiary) } }
            }
            View {
                attr { size(30f, 30f); allCenter(); borderRadius(15f); backgroundColor(theme.surfaceMuted) }
                Text { attr { text("×"); fontSize(17f); color(theme.textSecondary) } }
                event { click { onClose() } }
            }
        }

        // Primary action: start a new conversation.
        View {
            attr {
                marginTop(16f)
                height(44f)
                borderRadius(14f)
                flexDirectionRow()
                allCenter()
                backgroundLinearGradient(
                    Direction.TO_RIGHT,
                    ColorStop(theme.brand, 0f),
                    ColorStop(theme.term, 1f),
                )
            }
            Text { attr { text("＋"); fontSize(20f); fontWeightSemiBold(); color(Color(0xFFFFFFFF)) } }
            Text { attr { text("新会话"); marginLeft(6f); fontSize(15f); fontWeightSemiBold(); color(Color(0xFFFFFFFF)) } }
            event { click { onClose(); onNewChat() } }
        }

        // Session search affordance, backed by the same local history as the list below.
        View {
            attr {
                marginTop(12f)
                height(34f)
                paddingLeft(11f)
                flexDirectionRow()
                alignItemsCenter()
                borderRadius(10f)
                backgroundColor(theme.surfaceMuted)
            }
            Text { attr { text("⌕"); fontSize(15f); color(theme.textTertiary) } }
            Text {
                attr {
                    text(if (sessions.isEmpty()) "暂无历史会话" else "历史会话 · ${sessions.size}")
                    marginLeft(6f)
                    fontSize(12f)
                    color(theme.textTertiary)
                }
            }
        }

        // Conversation history, grouped by recency. It owns the remaining
        // height so the footer cards stay pinned to the bottom.
        Scroller {
            attr { flex(1f); marginTop(4f) }
            if (sessions.isEmpty()) {
                DrawerEmptyHistory(theme)
            } else {
                var lastGroup = ""
                sessions.forEach { session ->
                    if (session.groupTitle != lastGroup) {
                        DrawerGroupTitle(session.groupTitle, theme)
                        lastGroup = session.groupTitle
                    }
                    DrawerSessionItem(
                        title = session.title,
                        preview = session.preview,
                        theme = theme,
                        active = session.id == activeSessionId,
                    ) {
                        onClose()
                        onOpenSession(session.id)
                    }
                }
            }
        }

        // Quick entries with tinted icon tiles, grouped by intent so the white
        // sheet reads as sections instead of one flat 8-row stack (LDRS-R).
        DrawerGroupTitle("行情与工具", theme)
        DrawerMenuItem("◉", theme.term, theme.brandSoft, "灵动岛行情", theme) { onClose(); onToggleIsland() }
        DrawerMenuItem("⌕", theme.brand, theme.brandSoft, "全局搜索", theme) { onClose(); onOpenSearch() }
        DrawerMenuItem("▥", theme.term, theme.brandSoft, "市场总览", theme) { onClose(); onOpenMarket() }
        DrawerMenuItem("⌁", theme.term, theme.brandSoft, "异动预警", theme) { onClose(); onOpenAlerts() }
        // doc 23 信息架构：自选 → 风险地图 → 知识库是一条闭环，成组呈现
        DrawerGroupTitle("投资闭环", theme)
        DrawerMenuItem("★", theme.brand, theme.brandSoft, "自选股", theme) { onClose(); onOpenWatchlist() }
        DrawerMenuItem("◈", theme.brand, theme.brandSoft, "风险地图", theme) { onClose(); onOpenRiskMap() }
        DrawerMenuItem("⌘", theme.term, theme.brandSoft, "术语表", theme) { onClose(); onOpenGlossary() }
        DrawerMenuItem("⚙", theme.textSecondary, theme.surfaceMuted, "设置", theme, onClick = onSettings)

        Text {
            attr {
                text("StockChat v1.0 · 数据仅供参考")
                marginTop(10f)
                fontSize(9f)
                color(theme.textTertiary)
                textAlignCenter()
            }
        }
    }
}

private fun ViewContainer<*, *>.DrawerGroupTitle(text: String, theme: StockChatTheme) {
    Text { attr { text(text); marginTop(14f); marginBottom(4f); marginLeft(4f); fontSize(10f); fontWeightSemiBold(); color(theme.textTertiary) } }
}

private fun ViewContainer<*, *>.DrawerSessionItem(
    title: String,
    preview: String,
    theme: StockChatTheme,
    active: Boolean = false,
    onClick: () -> Unit = {},
) {
    View {
        attr {
            height(52f)
            marginTop(2f)
            flexDirectionRow()
            alignItemsCenter()
            borderRadius(10f)
            if (active) backgroundColor(theme.brandSoft)
        }
        if (active) {
            View { attr { width(3f); height(14f); marginLeft(6f); borderRadius(2f); backgroundColor(theme.brand) } }
        }
        View {
            attr { flex(1f); marginLeft(if (active) 8f else 12f); marginRight(10f) }
            Text {
                attr {
                    text(title)
                    fontSize(13f)
                    if (active) fontWeightMedium()
                    color(if (active) theme.brand else theme.textPrimary)
                }
            }
            Text {
                attr {
                    text(preview)
                    marginTop(3f)
                    fontSize(10f)
                    color(theme.textTertiary)
                }
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
        Text { attr { text("还没有历史记录"); fontSize(13f); fontWeightMedium(); color(theme.textSecondary); textAlignCenter() } }
        Text { attr { text("开始提问后会自动保存"); marginTop(5f); fontSize(10f); color(theme.textTertiary); textAlignCenter() } }
    }
}

private fun ViewContainer<*, *>.DrawerMenuItem(glyph: String, glyphColor: Color, glyphBg: Color, label: String, theme: StockChatTheme, onClick: () -> Unit = {}) {
    View {
        attr { height(42f); marginTop(2f); flexDirectionRow(); alignItemsCenter(); borderRadius(10f) }
        View {
            attr { size(28f, 28f); marginLeft(6f); allCenter(); borderRadius(8f); backgroundColor(glyphBg) }
            Text { attr { text(glyph); fontSize(14f); color(glyphColor) } }
        }
        Text { attr { text(label); marginLeft(10f); fontSize(13f); color(theme.textPrimary); flex(1f) } }
        Text { attr { text("›"); marginRight(10f); fontSize(15f); color(theme.textTertiary) } }
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
) {
    View {
        attr {
            // All app chrome is a floating material; the page scroller is its
            // backdrop source and must remain visible underneath it.
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(statusBarHeight + 57f)
            paddingTop(statusBarHeight)
        }
        GlassBackdrop(theme.glass.sheet, renderer)
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
                    attr { paddingRight(12f); height(44f); justifyContentCenter() }
                    Text { attr { text(backLabel); fontSize(14f); fontWeightMedium(); color(theme.brand) } }
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
                    Text { attr { text(title); fontSize(18f); fontWeightBold(); color(theme.textPrimary) } }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); marginTop(1f) }
                        Text { attr { text(subtitle); fontSize(10f); color(theme.textTertiary) } }
                        vif({ compactLine() != null }) {
                            Text {
                                attr {
                                    text(compactLine() ?: "")
                                    marginLeft(8f)
                                    fontSize(10f)
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
                                        Text { attr { text(metric.label); fontSize(8.5f); color(theme.textTertiary) } }
                                        View { attr { marginTop(2f); flexDirectionRow(); alignItemsCenter() }
                                            Text { attr { text(metric.value); fontSize(10.5f); fontWeightBold(); color(if (metric.flash) metric.changeColor else theme.textPrimary) } }
                                            Text { attr { text(metric.change); marginLeft(3f); fontSize(8.5f); fontWeightSemiBold(); color(metric.changeColor) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            actions.forEach { (label, action) ->
                View {
                    attr {
                        marginLeft(6f)
                        minWidth(32f)
                        height(32f)
                        allCenter()
                        borderRadius(16f)
                        backgroundColor(if (label == "+" || label == "✓") theme.brandSoft else theme.surfaceMuted)
                        border(Border(1f, BorderStyle.SOLID, if (label == "+" || label == "✓") theme.brand.opacity(0.20f) else theme.divider))
                    }
                    Text {
                        attr {
                            text(label)
                            fontSize(13f)
                            fontWeightSemiBold()
                            color(if (label == "+" || label == "✓") theme.brand else theme.textSecondary)
                        }
                    }
                    event { click { action() } }
                }
            }
        }
        vif({ progress() == null }) {
            View { attr { height(1f); backgroundColor(theme.divider) } }
        }
        vif({ progress() != null }) {
            View {
                attr {
                    height(2f)
                    flexDirectionRow()
                    backgroundColor(theme.divider)
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
        Text { attr { text(text); fontSize(10f); fontWeightMedium(); color(theme.brand) } }
    }
}
