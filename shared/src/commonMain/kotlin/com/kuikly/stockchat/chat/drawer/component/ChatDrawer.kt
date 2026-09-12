package com.kuikly.stockchat.chat.drawer.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chat.ChatSessionSummary
import com.kuikly.stockchat.chat.drawer.state.DrawerGestureMotion
import com.kuikly.stockchat.chat.drawer.state.DrawerGesturePhase
import com.kuikly.stockchat.foundation.design.GlassRenderer
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.icon.LineIconBarChart
import com.kuikly.stockchat.foundation.ui.icon.LineIconBellRinging
import com.kuikly.stockchat.foundation.ui.icon.LineIconBook
import com.kuikly.stockchat.foundation.ui.icon.LineIconBookmark
import com.kuikly.stockchat.foundation.ui.icon.LineIconPlus
import com.kuikly.stockchat.foundation.ui.icon.LineIconRadar
import com.kuikly.stockchat.foundation.ui.icon.LineIconSearch
import com.kuikly.stockchat.foundation.ui.icon.LineIconSliders
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Input
import com.kuikly.stockchat.foundation.ui.FeatureTile
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * Chat 抽屉：会话分组、会话项、空历史态与功能磁贴。
 *
 * 2026-09-12（doc 47 B-2）从 page/components/AppChrome.kt 原样拆出：纯物理归档，
 * 分组/会话列表逻辑一行未改。
 */
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
            DrawerTile("自选股", theme, icon = { LineIconBookmark(theme.textPrimary, 22f) }) { onClose(); onOpenWatchlist() }
            View { attr { width(8f) } }
            DrawerTile("风险地图", theme, icon = { LineIconRadar(theme.textPrimary, 22f) }) { onClose(); onOpenRiskMap() }
            View { attr { width(8f) } }
            DrawerTile("术语表", theme, icon = { LineIconBook(theme.textPrimary, 22f) }) { onClose(); onOpenGlossary() }
        }
        DrawerGroupTitle("市场行情", theme)
        View {
            attr { flexDirectionRow() }
            DrawerTile("市场总览", theme, icon = { LineIconBarChart(theme.textPrimary, 22f) }) { onClose(); onOpenMarket() }
            View { attr { width(8f) } }
            DrawerTile("异动预警", theme, icon = { LineIconBellRinging(theme.textPrimary, 22f) }) { onClose(); onOpenAlerts() }
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
                LineIconSearch(theme.textSecondary, 22f)
                event { click { onClose(); onOpenSearch() } }
            }
            View {
                attr { size(32f, 32f); marginLeft(6f); allCenter() }
                LineIconSliders(theme.textSecondary, 22f)
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
 * 抽屉功能磁贴：2026-09-10 起委托公共 FeatureTile（白卡 + 细描边 + 浅投影，
 * 线条图标 Lucide 对齐，图标 22 / 文字 10）。icon 传绘制闭包，onClick 普通闭包。
 */
private fun ViewContainer<*, *>.DrawerTile(
    label: String,
    theme: StockChatTheme,
    icon: ViewContainer<*, *>.() -> Unit,
    onClick: () -> Unit,
) {
    FeatureTile(label = label, theme = theme, icon = icon, onClick = onClick)
}
