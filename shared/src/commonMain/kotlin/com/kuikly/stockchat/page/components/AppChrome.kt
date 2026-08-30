package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatSessionSummary
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.glass.GlassBackdrop
import com.kuikly.stockchat.glass.GlassRenderer
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

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
    // Kuikly only re-runs attr/vif closures that read observables directly,
    // so reactive island inputs are accessors rather than frozen values.
    islandExpanded: () -> Boolean = { false },
    islandQuote: () -> Quote? = { null },
    onToggleIsland: () -> Unit = {},
    onOpenIslandDetail: (String) -> Unit = {},
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
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
                    val e = islandExpanded()
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
                    val e = islandExpanded()
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
    StockIsland(
        statusBarHeight = statusBarHeight,
        pageWidth = pageWidth,
        expanded = islandExpanded,
        quote = islandQuote,
        liveData = liveData,
        title = contextTitle,
        theme = theme,
        renderer = renderer,
        onToggle = onToggleIsland,
        onOpenDetail = onOpenIslandDetail,
    )
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
    expanded: () -> Boolean,
    quote: () -> Quote?,
    liveData: () -> Boolean,
    title: String?,
    theme: StockChatTheme,
    renderer: GlassRenderer,
    onToggle: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val collapsedWidth = if (title == null) 128f else 200f
    val expandedWidth = (pageWidth - 28f).coerceAtLeast(collapsedWidth)
    // Full-width transparent strip centres the island via flex.  It has no
    // event handler, so taps outside the island fall through to the nav
    // buttons and the scroller underneath; only the morphing child below
    // consumes touches.  Animating width/height/radius on the child (instead
    // of absolute left) keeps layout and touch bounds in sync.
    View {
        attr {
            absolutePosition(top = statusBarHeight + 4f, left = 0f, right = 0f)
            flexDirectionRow()
            justifyContentCenter()
        }
        View {
            attr {
                val e = expanded()
                width(if (e) expandedWidth else collapsedWidth)
                height(if (e) 132f else 36f)
                borderRadius(if (e) 24f else 18f)
                animate(Animation.easeOut(0.34f), e)
            }
            GlassBackdrop(theme.glass.peek, renderer)

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
                    paddingBottom(11f)
                    opacity(if (e) 1f else 0f)
                    transform(Translate(0f, if (e) 0f else 0.10f))
                    touchEnable(e)
                    animate(Animation.easeOut(0.26f), e)
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(quote()?.name ?: "贵州茅台"); fontSize(14f); fontWeightBold(); color(theme.textPrimary) } }
                    Text { attr { text(quote()?.symbol ?: "600519.SH"); marginLeft(6f); fontSize(10f); color(theme.textTertiary) } }
                    View { attr { flex(1f) } }
                    View { attr { size(5f, 5f); borderRadius(3f); backgroundColor(if (liveData()) Color(0xFF34C759) else theme.textTertiary) } }
                    Text { attr { text(if (liveData()) "实时" else "模拟"); marginLeft(4f); fontSize(9f); color(theme.textTertiary) } }
                    View {
                        attr { size(24f, 24f); marginLeft(10f); allCenter(); borderRadius(12f); backgroundColor(theme.surfaceMuted) }
                        Text { attr { text("×"); fontSize(13f); color(theme.textSecondary) } }
                        event { click { onToggle() } }
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
                    Text { attr { text("行情加载中…"); fontSize(12f); color(theme.textTertiary) } }
                }
                View {
                    attr {
                        absolutePosition(top = 44f, left = 16f, right = 16f, bottom = 11f)
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
                        Text { attr { text(quote()?.let { "额 ${Format.compactAmount(it.amount)}" } ?: "额 --"); marginLeft(10f); fontSize(10f); color(theme.textSecondary) } }
                        View { attr { flex(1f) } }
                        Text { attr { text("查看详情 ›"); fontSize(11f); fontWeightMedium(); color(theme.brand) } }
                        event {
                            click {
                                if (expanded()) quote()?.let { onOpenDetail(it.symbol) }
                            }
                        }
                    }
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
    onClose: () -> Unit,
    onToggleDataMode: () -> Unit,
    onCycleVisualMode: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onToggleIsland: () -> Unit = {},
    onOpenGlossary: () -> Unit = {},
    onOpenWatchlist: () -> Unit = {},
    onSettings: () -> Unit,
) {
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
            backgroundColor(Color(0x59000000))
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
        }
        GlassBackdrop(theme.glass.sheet, renderer)

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

        // Quick entries with tinted icon tiles.
        View { attr { height(1f); marginTop(8f); marginBottom(6f); backgroundColor(theme.divider) } }
        DrawerMenuItem("◉", theme.term, theme.brandSoft, "灵动岛演示（茅台）", theme) { onClose(); onToggleIsland() }
        DrawerMenuItem("★", theme.brand, theme.brandSoft, "自选股", theme) { onClose(); onOpenWatchlist() }
        DrawerMenuItem("⌘", theme.term, theme.brandSoft, "术语表", theme) { onClose(); onOpenGlossary() }
        DrawerMenuItem("▦", theme.textSecondary, theme.surfaceMuted, "卡片图鉴", theme) { onClose(); onOpenGallery() }
        DrawerMenuItem("⚙", theme.textSecondary, theme.surfaceMuted, "设置", theme, onClick = onSettings)

        // Data source & rendering preferences card.
        View {
            attr {
                marginTop(10f)
                paddingLeft(12f)
                paddingRight(12f)
                paddingTop(11f)
                paddingBottom(4f)
                borderRadius(14f)
                backgroundColor(theme.surface)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View { attr { size(8f, 8f); borderRadius(4f); backgroundColor(if (liveData) Color(0xFF34C759) else theme.textTertiary) } }
                View {
                    attr { flex(1f); marginLeft(8f) }
                    Text { attr { text(if (liveData) "实时数据" else "模拟数据"); fontSize(13f); fontWeightSemiBold(); color(theme.textPrimary) } }
                    Text { attr { text(if (liveData) "行情与信息均为真实数据" else "使用本地演示数据"); marginTop(1f); fontSize(10f); color(theme.textTertiary) } }
                }
                DrawerSwitch(liveData, theme, onToggleDataMode)
            }
            View { attr { height(1f); marginTop(11f); backgroundColor(theme.divider) } }
            View {
                attr { height(38f); flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("渲染模式"); fontSize(12f); color(theme.textSecondary); flex(1f) } }
                Text { attr { text(visualLabel); fontSize(11f); color(theme.textTertiary) } }
                Text { attr { text("切换"); marginLeft(8f); fontSize(11f); fontWeightMedium(); color(theme.brand) } }
                event { click { onCycleVisualMode() } }
            }
        }
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

private fun ViewContainer<*, *>.DrawerSwitch(on: Boolean, theme: StockChatTheme, onClick: () -> Unit) {
    View {
        attr {
            width(42f)
            height(25f)
            borderRadius(13f)
            backgroundColor(if (on) theme.brand else theme.surfaceMuted)
        }
        View {
            attr {
                size(19f, 19f)
                borderRadius(10f)
                backgroundColor(Color(0xFFFFFFFF))
                if (on) {
                    absolutePosition(top = 3f, right = 3f)
                } else {
                    absolutePosition(top = 3f, left = 3f)
                }
            }
        }
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
                attr { flex(1f) }
                Text { attr { text(title); fontSize(18f); fontWeightBold(); color(theme.textPrimary) } }
                Text { attr { text(subtitle); marginTop(1f); fontSize(10f); color(theme.textTertiary) } }
            }
            actions.forEach { (label, action) ->
                View {
                    attr { marginLeft(6f); padding(8f); borderRadius(9f); backgroundColor(theme.surfaceMuted) }
                    Text { attr { text(label); fontSize(12f); color(theme.textSecondary) } }
                    event { click { action() } }
                }
            }
        }
        View { attr { height(1f); backgroundColor(theme.divider) } }
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
