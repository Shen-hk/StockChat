package com.kuikly.stockchat.watchlist.component

import com.kuikly.stockchat.cards.component.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockQuoteCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.foundation.ui.FeatureTile
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.foundation.ui.icon.LineIconBarChart
import com.kuikly.stockchat.foundation.ui.icon.LineIconSearch
import com.kuikly.stockchat.page.components.RowGestureLayer
import com.kuikly.stockchat.watchlist.brief.state.WatchlistBriefCoordinator
import com.kuikly.stockchat.watchlist.drag.state.WatchlistDragCoordinator
import com.kuikly.stockchat.watchlist.state.WatchlistCoordinator
import com.kuikly.stockchat.watchlist.state.WatchlistRow
import com.kuikly.stockchat.watchlist.state.groupLabel
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs

/**
 * 自选列表主体（doc 47 B-4 组件层）：z0 聚合头、z1 筛选、z2 行（RowGestureLayer：
 * 点击进详情 / 长按拿起拖拽排序 / 长按原地松手开菜单）、z3 异动上浮、
 * 底部通路与数据模式角标。所有布局/文案/动画数值逐字来自原 WatchlistPage。
 *
 * observable 读取全部落在 vif / attr / vfor 内（R1）；拖拽位移动画相关的
 * transform 语义见 [WatchlistDragCoordinator]（R5：取消不重置 dragFrom/dragTo）。
 */
internal fun ViewContainer<*, *>.WatchlistScrollContent(
    theme: StockChatTheme,
    data: WatchlistCoordinator,
    drag: WatchlistDragCoordinator,
    brief: WatchlistBriefCoordinator,
    reduceMotion: Boolean,
    onOpenDetail: (String) -> Unit,
    onOpenSearchPage: () -> Unit,
    onOpenMarketPage: () -> Unit,
    onOpenRiskPage: () -> Unit,
    onOpenAlertsPage: () -> Unit,
) {
    val pageTheme = theme

    // ── z0 聚合头：回答「我的自选今天整体怎么样」 ──
    vif({ data.rows.isNotEmpty() }) {
        WatchlistAggregateHeader(theme = pageTheme, data = data, onOpenRiskPage = onOpenRiskPage)
    }

    // ── doc 30：预警收件箱预览（消息提示的首页入口，badge 即未读数） ──
    vif({ brief.inboxMessages.isNotEmpty() }) {
        WatchlistInboxPreviewRow(
            theme = pageTheme,
            brief = brief,
            reduceMotion = reduceMotion,
            onOpenAlerts = onOpenAlerts@{ /* 由页面注入路由 */ },
        )
    }

    // ── z1 筛选：状态在上，分组弱化到第二行 ──
    View {
        attr { marginTop(12f); flexDirectionRow(); alignItemsCenter() }
        WatchlistFilterChip(
            label = "全部 ${data.rows.size}",
            selected = { data.statusFilter.isEmpty() && data.activeGroup == "all" },
            theme = pageTheme,
            compact = false,
        ) {
            data.selectAllFilter()
        }
        vif({ data.aggregate().moverCount > 0 }) {
            WatchlistFilterChip(
                label = "异动 ${data.aggregate().moverCount}",
                selected = { data.statusFilter == FILTER_MOVERS },
                theme = pageTheme,
                compact = false,
            ) {
                data.selectMoversFilter()
            }
        }
    }
    View {
        attr { marginTop(7f); flexDirectionRow(); alignItemsCenter() }
        listOf("core" to "核心观察", "research" to "待研究", "" to "未分组").forEach { (id, label) ->
            WatchlistFilterChip(
                label = label,
                selected = { data.activeGroup == id },
                theme = pageTheme,
                compact = true,
            ) {
                data.selectGroup(id)
            }
        }
    }

    // ── 手势标注：操作收进手势后必须有可见的入口说明，否则发现不了。
    // 一行弱提示常驻（textTertiary 10.5f），不与筛选 chip 抢视觉重量。 ──
    vif({ data.rows.isNotEmpty() }) {
        Text {
            attr {
                text("长按卡片拖动可排序 · 长按原地松手看更多操作")
                marginTop(9f)
                fontSizeScaled(10.5f)
                color(pageTheme.textTertiary)
            }
        }
    }

    // ── FR-W9「没看过」半边：久未点开的一句话提示（事实陈述，不劝删） ──
    vif({ data.staleRows().isNotEmpty() }) {
        Text {
            attr {
                text(data.staleLabel())
                marginTop(10f)
                fontSizeScaled(11.5f)
                lineHeightScaled(17f)
                color(pageTheme.textTertiary)
            }
        }
    }

    vif({ data.hint.isNotEmpty() }) {
        Text {
            attr {
                text(data.hint)
                marginTop(10f)
                fontSizeScaled(12f)
                color(pageTheme.term)
            }
        }
    }

    vif({ data.displayList.isEmpty() && data.rows.isNotEmpty() }) {
        Text {
            attr {
                text("当前筛选下没有标的")
                marginTop(24f)
                fontSizeScaled(12.5f)
                color(pageTheme.textTertiary)
            }
        }
    }

    vif({ data.rows.isEmpty() }) {
        WatchlistEmptyState(
            theme = pageTheme,
            container = this,
            // 空态的首要任务是找到第一只股票；直接进入完整搜索页，避免
            // 仅唤起本页 z5 输入浮层而让用户误以为页面没有跳转。
            onSearch = onOpenSearchPage,
            onOpenMarket = onOpenMarketPage,
        )
    }

    vfor({ data.displayList }) { row ->
        // 主题捕获在构建作用域：theme 读取的是 observable(nightModel)（见
        // BasePager.isNightMode），若在 attr 里读到会覆盖动画 key（R2 高危
        // 陷阱，MarketPage 同款处理）。
        val rowTheme = pageTheme
        // ── 外层：拖拽会话层。跟手位移 / 让位位移 / 层级与投影。
        // 与内层动画分视图隔离，避免多驱动共键（R3）。──
        View {
            attr {
                marginTop(10f)
                if (drag.dragSymbol == row.symbol) {
                    zIndex(30, useOutline = false)
                    // 阴影和大比例缩放会放大原生合成器的脏矩形；拖拽每帧
                    // 更新 transform 时容易看到上一帧残影，层级已经足够表达抬起。
                    boxShadow(BoxShadow(0f, 4f, 10f, Color(0x000000, 0.10f)))
                    // 跟手位移直出，不注册动画（每帧写会跟动画互相拖拽）；
                    // 松手由 applyDragOrder 同帧落数据并清 transform，无收尾动画。
                    transform(translate = Translate(0f, 0f, offsetY = drag.dragMotion.dy))
                } else {
                    // attr 是增量应用：不显式复位会保留拿起拍的 zIndex/阴影。
                    // 向上重排时该 View 通常被 vfor 复用，于是看起来卡片仍悬浮。
                    zIndex(0, useOutline = false)
                    boxShadow(BoxShadow(0f, 0f, 0f, Color(0x000000, 0f)))
                    // 让位行索引必须在 attr 内实时读取。vfor 对未变行会复用
                    // 原 View；在构建闭包缓存 index 会让上一次排序后的索引残留。
                    val currentIndex = data.displayList.indexOfFirst { it.symbol == row.symbol }
                    transform(
                        translate = Translate(0f, 0f, offsetY = drag.rowSlotShift(currentIndex)),
                    )
                }
            }
            // ── 中层：拿起缩放（「松动」手感）。拖拽态结束时必须直接归零，
            // 不能注册 dragSymbol spring；否则松手落位后还会延迟播放回落动画，
            // 视觉上像股票悬浮在落点上方。──
            View {
                attr {
                    val lifted = drag.dragSymbol == row.symbol
                    val liftScale = if (lifted) 1.02f else 1f
                    transform(scale = Scale(liftScale, liftScale))
                }
                View {
                    RowGestureLayer(
                        onTapContent = { onOpenDetail(row.symbol) },
                        onLongPressContent = { drag.beginDragLift(row.symbol) },
                        dragActive = { drag.dragSymbol == row.symbol },
                        onDragMove = { dy -> drag.dragMove(dy) },
                        onDragEnd = { dy, cancelled -> drag.dragEnd(dy, cancelled) },
                    ) {
                        View {
                            attr {
                                alignSelfStretch()
                                flexDirectionRow()
                                // 原SwipeActionRow内容层承担的底色/圆角移到内容根节点
                                // （动作层摘除后手势层不再管样式）。
                                backgroundColor(rowTheme.surface)
                                borderRadius(rowTheme.cardRadius)
                                // MINI 渲染器本身不画卡壳，统一由这一层提供圆角与
                                // 轻投影；不随各标的图表/涨跌状态变化成平铺样式。
                                boxShadow(BoxShadow(0f, 2f, 8f, Color(0x182238, 0.07f)))
                            }
                            // 分组从「常驻按钮」收进手势后，状态不能跟着一起消失：
                            // 用一条 3dp 色带把分组留在行内（LDRS-R：关系→图形映射），
                            // 视觉成本近乎为零，但「这行属于哪一组」始终可见。
                            View {
                                attr {
                                    width(3f)
                                    backgroundColor(groupTint(row.groupId, pageTheme))
                                }
                            }
                            View {
                                attr { flex(1f); padding(12f) }
                                val quote = row.quote
                                if (quote != null) {
                                    CardShell(
                                        StockQuoteCardModel(quote, cardId = "watchlist:${row.symbol}"),
                                        CardContext(
                                            theme = pageTheme,
                                            density = CardDensity.MINI,
                                            onOpenStock = { onOpenDetail(row.symbol) },
                                            cardKey = "watchlist:${row.symbol}",
                                            cardClickable = false,
                                        ),
                                    )
                                } else {
                                    WatchlistPendingRow(name = row.name, symbol = row.symbol, theme = pageTheme, container = this)
                                }
                                vif({ row.quote != null && abs(row.quote!!.changePercent) >= 3.0 }) {
                                    Text {
                                        val pct = row.quote?.changePercent ?: 0.0
                                        attr {
                                            text(if (pct > 0) "异动 ↑" else "异动 ↓")
                                            marginTop(4f)
                                            fontSizeScaled(9.5f)
                                            color(if (pct > 0) pageTheme.rise else pageTheme.fall)
                                        }
                                    }
                                }
                                // FR-W6：对话加入的标的带 ★ 来源标记（置顶由 sortOrder 保证）
                                vif({ row.starred }) {
                                    Text {
                                        attr {
                                            text("★ 对话加入")
                                            marginTop(4f)
                                            fontSizeScaled(9.5f)
                                            color(pageTheme.brand)
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

    // ── doc 30：今日速览（规则引擎事实句，条件触发；平静日不出现） ──
    vif({ brief.hasBrief }) {
        WatchlistBriefCard(theme = pageTheme, brief = brief, reduceMotion = reduceMotion)
    }

    // ── FR-W5 底部通路：扫描完列表，下一问是「我押注了什么」 ──
    vif({ data.rows.isNotEmpty() }) {
        View {
            attr {
                marginTop(14f)
                height(44f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(14f)
                paddingRight(14f)
                borderRadius(12f)
                backgroundColor(pageTheme.surfaceMuted)
            }
            event { click { onOpenRiskPage() } }
            Text {
                attr {
                    flex(1f)
                    text("我押注了什么？看共同暴露 ›")
                    fontSizeScaled(12.5f)
                    color(pageTheme.textSecondary)
                }
            }
            Text {
                attr {
                    text("风险地图")
                    fontSizeScaled(11.5f)
                    color(pageTheme.brand)
                }
            }
        }
    }

    vif({ data.dataModeLabel.isNotEmpty() }) {
        Text {
            attr {
                text(data.dataModeLabel)
                marginTop(16f)
                fontSizeScaled(11f)
                lineHeightScaled(17f)
                color(pageTheme.textTertiary)
            }
        }
    }
}

private const val FILTER_MOVERS = "movers"

// ── z0 聚合头 ──

/** 聚合头渲染（算术事实来自 [WatchlistCoordinator.aggregate]）。 */
internal fun ViewContainer<*, *>.WatchlistAggregateHeader(
    theme: StockChatTheme,
    data: WatchlistCoordinator,
    onOpenRiskPage: () -> Unit,
) {
    val pageTheme = theme
    val agg = data.aggregate()
    View {
        attr {
            padding(18f)
            paddingTop(20f)
            borderRadius(18f)
            backgroundColor(aggregateTint(agg.avgPct, pageTheme))
        }
        // FR-W5：聚合头即风险地图入口——「整体怎么样」的下一问永远是「我押注了什么」
        event { click { onOpenRiskPage() } }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("${agg.total} 只自选 · 等权")
                    fontSizeScaled(11f)
                    color(pageTheme.textTertiary)
                }
            }
            vif({ agg.quoted < agg.total }) {
                Text {
                    attr {
                        text(" · ${agg.total - agg.quoted} 只无报价")
                        fontSizeScaled(11f)
                        color(pageTheme.textTertiary)
                    }
                }
            }
        }
        Text {
            attr {
                text(Format.percent(agg.avgPct))
                marginTop(6f)
                fontSizeScaled(34f)
                fontWeightBold()
                color(aggregateColor(agg.avgPct, pageTheme))
            }
        }
        Text {
            attr {
                text(agg.conclusion)
                marginTop(5f)
                fontSizeScaled(12.5f)
                color(pageTheme.textSecondary)
            }
        }
        View {
            attr { marginTop(16f) }
            DivergingBar(theme = pageTheme, rising = agg.rising, flat = agg.flat, falling = agg.falling)
        }
        Text {
            attr {
                text("涨 ${agg.rising} · 平 ${agg.flat} · 跌 ${agg.falling}")
                marginTop(7f)
                fontSizeScaled(10f)
                color(pageTheme.textTertiary)
            }
        }
    }
}

/** 氛围底取色：随整体方向取 riseSoft/fallSoft，平静取中性。只做氛围不做强调。 */
private fun aggregateTint(avgPct: Double, theme: StockChatTheme): Color = when {
    avgPct > 0.05 -> theme.riseSoft
    avgPct < -0.05 -> theme.fallSoft
    else -> theme.surfaceMuted
}

private fun aggregateColor(avgPct: Double, theme: StockChatTheme): Color = when {
    avgPct > 0.005 -> theme.rise
    avgPct < -0.005 -> theme.fall
    else -> theme.flat
}

/**
 * 分组色带颜色。操作收进手势后，这是分组状态**唯一**的常驻可见表达，
 * 所以三档必须能一眼区分：核心=品牌蓝，待研究=中性灰，未分组=留空（不画）。
 */
internal fun groupTint(groupId: String, theme: StockChatTheme): Color = when (groupId) {
    "core" -> theme.brand
    "research" -> theme.term
    else -> Color(0xFFFFFFFF, 0f)
}

/** 筛选 chip。分组行用 [compact] 弱化——它是第二行，不该和状态筛选抢视觉重量。 */
internal fun ViewContainer<*, *>.WatchlistFilterChip(
    label: String,
    selected: () -> Boolean,
    theme: StockChatTheme,
    compact: Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            marginRight(7f)
            paddingLeft(if (compact) 9f else 11f)
            paddingRight(if (compact) 9f else 11f)
            height(if (compact) 26f else 30f)
            allCenter()
            borderRadius(if (compact) 8f else 9f)
            backgroundColor(if (selected()) theme.brandSoft else theme.surfaceMuted)
        }
        Text {
            attr {
                text(label)
                fontSizeScaled(if (compact) 10.5f else 11f)
                color(if (selected()) theme.brand else theme.textSecondary)
            }
        }
        event { click { onClick() } }
    }
}

internal fun WatchlistPendingRow(
    name: String,
    symbol: String,
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
) {
    container.View {
        attr { padding(14f); borderRadius(12f); backgroundColor(theme.surfaceMuted) }
        Text { attr { text(name); fontSizeScaled(14f); fontWeightSemiBold(); color(theme.textPrimary) } }
        Text { attr { text("$symbol · 行情加载中"); marginTop(4f); fontSizeScaled(11f); color(theme.textTertiary) } }
    }
}

internal fun WatchlistEmptyState(
    theme: StockChatTheme,
    container: ViewContainer<*, *>,
    onSearch: () -> Unit,
    onOpenMarket: () -> Unit,
) {
    container.View {
        attr { marginTop(28f); padding(18f); borderRadius(14f); backgroundColor(theme.surface) }
        Text { attr { text("还没有自选股"); fontSizeScaled(15f); fontWeightSemiBold(); color(theme.textPrimary) } }
        Text {
            attr {
                text("点右上角「搜索」加入第一只股票，也可以在聊天里长按股票名、或从股票详情页添加。加入后可以直接问「我的自选今天怎么样」。")
                marginTop(8f)
                fontSizeScaled(12.5f)
                lineHeightScaled(19f)
                color(theme.textSecondary)
            }
        }
        // 磁贴引导（2026-09-10 统一磁贴语言）：直达搜索 / 行情页，替代纯文字提示。
        View {
            attr { flexDirectionRow(); marginTop(14f) }
            FeatureTile(
                label = "去全局搜索",
                theme = theme,
                height = 64f,
                icon = { LineIconSearch(theme.textPrimary, 22f) },
                onClick = onSearch,
            )
            View { attr { width(8f) } }
            FeatureTile(
                label = "去行情页逛逛",
                theme = theme,
                height = 64f,
                icon = { LineIconBarChart(theme.textPrimary, 22f) },
                onClick = onOpenMarket,
            )
        }
    }
}
