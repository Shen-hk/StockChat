package com.kuikly.stockchat.foundation.ui.chrome

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.foundation.design.GlassRenderer
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.icon.LineIconArrowLeft
import com.kuikly.stockchat.foundation.ui.icon.LineIconRadar
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * AppTopBar（顶栏）+ AppTopBarMetric/Action（顶栏数据模型）。
 *
 * 2026-09-12（doc 47 B-2）从 page/components/AppChrome.kt 原样拆出：纯物理归档，
 * 布局、参数、动画、状态逻辑一行未改（AppChrome.kt 已删除）。
 */
/** A dense, value-first top-bar state for pages whose Hero has scrolled away. */
data class AppTopBarMetric(
    val label: String,
    val value: String,
    val change: String,
    val changeColor: Color,
    val flash: Boolean = false,
)

/** A compact, cross-platform Tabler-style action for a secondary-page top bar. */
data class AppTopBarAction(
    val icon: ViewContainer<*, *>.(Color, Float, Boolean) -> Unit,
    val onClick: () -> Unit,
    val selected: () -> Boolean = { false },
)

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
    actions: List<AppTopBarAction> = emptyList(),
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
                    attr { minWidth(44f); height(44f); allCenter() }
                    // “地图”是术语页的视图切换；其余二级页面统一为返回箭头。
                    View {
                        attr {
                            width(32f)
                            height(32f)
                            allCenter()
                            borderRadius(10f)
                            backgroundColor(theme.surface.opacity(0f))
                        }
                        if (backLabel == "地图") {
                            LineIconRadar(theme.brand, 18f)
                        } else {
                            LineIconArrowLeft(theme.brand, 19f)
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
            actions.forEach { action ->
                // 二级页操作使用同一枚柔和圆角按钮；不再裸露 Tabler 式细线。
                View {
                    attr {
                        marginLeft(4f)
                        minWidth(44f)
                        height(44f)
                        allCenter()
                    }
                    vbind({ action.selected() }) {
                        val selected = action.selected()
                        View {
                            attr {
                                width(32f)
                                height(32f)
                                allCenter()
                                borderRadius(10f)
                                backgroundColor(if (selected) theme.brand.opacity(0.14f) else theme.divider.opacity(0.42f))
                            }
                            action.icon.invoke(this, if (selected) theme.brand else theme.textPrimary, 19f, selected)
                        }
                    }
                    event { click { action.onClick() } }
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
