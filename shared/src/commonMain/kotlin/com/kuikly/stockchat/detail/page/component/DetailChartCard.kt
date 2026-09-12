package com.kuikly.stockchat.detail.page.component

import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.StockChartCardModel
import com.kuikly.stockchat.cards.core.StockChartMode
import com.kuikly.stockchat.cards.core.StockChartPeriod
import com.kuikly.stockchat.cards.stock.KLineChart
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.chart.model.ChartViewportAction
import com.kuikly.stockchat.chart.model.ChartViewportCommand
import com.kuikly.stockchat.foundation.ui.fontSizeScaled
import com.kuikly.stockchat.foundation.ui.lineHeightScaled
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.detail.page.component.ChartFlag
import com.kuikly.stockchat.detail.page.component.DetailTimelineChart
import com.kuikly.stockchat.detail.domain.AnomalyPoint
import com.kuikly.stockchat.detail.domain.DetailOverlay
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * Wave 2 D5 第三组件：走势主卡（K线/分时 + 圈选 hint + 图表气泡 + 视口控件）。
 * 从 StockDetailPage.body() 搬出原 lines 504-743，零行为变更；所有 chart
 * 域状态来自 detailChartCoordinator 的只读 getter（Observable 读取由 R1
 * 在 attr/vif/vbind 闭包内建立反应式依赖）。
 *
 * 组件自身只做"装配"——所有交互经回调回链到 StockDetailPage（chartCoordinator
 * / openChatWithQuestion / closeChartBubble / chartBubbleSourceLabel），
 * 不引入新 Provider/Repository。
 */
internal fun ViewContainer<*, *>.DetailChartCard(
    theme: StockChatTheme,
    reduceMotion: Boolean,
    entranceVisible: () -> Boolean,
    revealIndex: Int,
    ctx: CardContext,
    containerWidth: Float,
    // ---- 状态 getters（observable 经闭包读）----
    chartMode: () -> StockChartMode,
    chartPeriod: () -> StockChartPeriod,
    quote: () -> Quote,
    crosshairIndex: () -> Int,
    drawProgress: () -> Float,
    sonarDrift: () -> Float,
    chartDataLoading: () -> Boolean,
    livePulse: () -> Boolean,
    chartViewportCommand: () -> ChartViewportCommand,
    selectedKLineIndex: () -> Int,
    sonarPoints: () -> List<AnomalyPoint>,
    selectedSonarIndex: () -> Int,
    chartFlags: () -> List<ChartFlag>,
    bandRange: () -> Triple<Int, Int, Boolean>?,
    circleSelecting: () -> Boolean,
    circleHintPresented: () -> Boolean,
    chartBubble: () -> String,
    chartBubblePresented: () -> Boolean,
    // 圈选 AI 流（detailAiCoordinator 持有）：气泡内层展示，由本组件只读读取
    circleAiState: () -> Int,
    circleAiText: () -> String,
    circleAiError: () -> String,
    chartBubbleSourceLabel: () -> String,
    bubbleOverlayActive: () -> Boolean,
    // ---- 回调（Actions）----
    onChangeModePeriod: (StockChartMode, StockChartPeriod) -> Unit,
    onIssueViewportCommand: (ChartViewportAction) -> Unit,
    onSelectKLineIndex: (Int) -> Unit,
    onScrub: (Int) -> Unit,
    onScrubPause: (Int) -> Unit,
    onScrubLeave: () -> Unit,
    onScrubActive: (Boolean) -> Unit,
    onZoomActive: (Boolean) -> Unit,
    onCrosshairActive: (Boolean) -> Unit,
    onTapSonar: (Int) -> Unit,
    onCircleSelect: (Int, Int) -> Unit,
    onSelectStateChange: (Boolean) -> Unit,
    onBlankTap: () -> Unit,
    onCloseChartBubble: () -> Unit,
    onOpenChatFromBubble: () -> Unit,
    toneColor: () -> Color,
) {
    RevealBlock(revealIndex, entranceVisible, reduceMotion) {
        View {
            attr { marginTop(-3f) }
            // 左侧切换周期，右侧操作视窗；中间保留弹性空白，互不拥挤。
            View {
                attr { flexDirectionRow(); alignItemsCenter(); marginBottom(7f) }
                ChartSegment(theme, chartMode, chartPeriod, reduceMotion) { m, p ->
                    onChangeModePeriod(m, p)
                }
                View { attr { flex(1f) } }
                ChartViewportControls(theme) { action -> onIssueViewportCommand(action) }
            }
            // 图例固定在工具栏下一行，不再漂浮压住高低点和价格曲线。
            View {
                attr { alignSelfFlexEnd(); marginBottom(6f); touchEnable(false) }
                ChartLegend(theme, toneColor)
            }
            // 图表区加载骨架（chartDataLoading=分时尚未到位时的空白期治理）。
            vif({ chartDataLoading() }) {
                ChartLoadingSkeleton(theme, livePulse, reduceMotion)
            }
            // 分时主体（自绘）与 K 线互斥切换
            vif({ chartMode() == StockChartMode.TIMELINE && !chartDataLoading() }) {
                DetailTimelineChart(
                    theme = theme,
                    quote = quote,
                    crosshairIndex = crosshairIndex,
                    drawProgress = drawProgress,
                    motionPhase = sonarDrift,
                    reduceMotion = reduceMotion,
                    containerWidth = containerWidth,
                    onScrub = onScrub,
                    sonarIndices = { sonarPoints().map { p -> p.index } },
                    selectedSonarIndex = selectedSonarIndex,
                    onSonarTap = onTapSonar,
                    flags = chartFlags,
                    band = bandRange,
                    onCircleSelect = { s, e -> onCircleSelect(s, e) },
                    onSelectStateChange = onSelectStateChange,
                    onScrubPause = onScrubPause,
                    onScrubLeave = onScrubLeave,
                    onScrubActive = onScrubActive,
                    onBlankTap = onBlankTap,
                    viewportCommand = chartViewportCommand,
                )
            }
            vif({ chartMode() == StockChartMode.K_LINE && chartPeriod() == StockChartPeriod.DAY && !chartDataLoading() }) {
                KLineChart(
                    container = this,
                    model = StockChartCardModel(quote(), StockChartMode.K_LINE, StockChartPeriod.DAY),
                    context = ctx,
                    selectedIndex = selectedKLineIndex,
                    onSelectIndex = onSelectKLineIndex,
                    chartHeight = 396f,
                    onZoomActive = onZoomActive,
                    onCrosshairActive = onCrosshairActive,
                    viewportCommand = chartViewportCommand,
                )
            }
            vif({ chartMode() == StockChartMode.K_LINE && chartPeriod() == StockChartPeriod.WEEK && !chartDataLoading() }) {
                KLineChart(
                    container = this,
                    model = StockChartCardModel(quote(), StockChartMode.K_LINE, StockChartPeriod.WEEK),
                    context = ctx,
                    selectedIndex = selectedKLineIndex,
                    onSelectIndex = onSelectKLineIndex,
                    chartHeight = 396f,
                    onZoomActive = onZoomActive,
                    onCrosshairActive = onCrosshairActive,
                    viewportCommand = chartViewportCommand,
                )
            }
            vif({ chartMode() == StockChartMode.K_LINE && chartPeriod() == StockChartPeriod.MONTH && !chartDataLoading() }) {
                KLineChart(
                    container = this,
                    model = StockChartCardModel(quote(), StockChartMode.K_LINE, StockChartPeriod.MONTH),
                    context = ctx,
                    selectedIndex = selectedKLineIndex,
                    onSelectIndex = onSelectKLineIndex,
                    chartHeight = 396f,
                    onZoomActive = onZoomActive,
                    onCrosshairActive = onCrosshairActive,
                    viewportCommand = chartViewportCommand,
                )
            }
            // ① 圈选态 hint（vif + 两帧入场，R4）
            vif({ circleSelecting() && circleHintPresented() }) {
                Text {
                    attr {
                        absolutePosition(left = 16f, top = 46f)
                        text("圈选中：松手生成这段走势的解读")
                        fontSizeScaled(10f)
                        fontWeightMedium()
                        color(theme.brand)
                        opacity(if (circleHintPresented()) 1f else 0f)
                        if (!reduceMotion) {
                            animate(Animation.easeOut(0.18f), circleHintPresented())
                        }
                        touchEnable(false)
                    }
                }
            }
            // ④/① 就地气泡（doc 29 U1/U2/U3）
            vif({ bubbleOverlayActive() && chartBubble().isNotEmpty() }) {
                View {
                    attr {
                        absolutePosition(left = 16f, right = 16f, bottom = 12f)
                        padding(12f)
                        borderRadius(14f)
                        backgroundColor(theme.surface)
                        border(Border(1.2f, BorderStyle.SOLID, theme.brand.opacity(0.6f)))
                        boxShadow(BoxShadow(0f, 6f, 18f, theme.brand.opacity(0.14f)))
                        opacity(if (chartBubblePresented()) 1f else 0f)
                        if (!reduceMotion) {
                            transform(Translate(0f, if (chartBubblePresented()) 0f else 0.08f))
                            animate(Animation.easeOut(0.22f), chartBubblePresented())
                        }
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter() }
                        Text {
                            attr {
                                text(chartBubbleSourceLabel())
                                fontSizeScaled(9f)
                                fontWeightSemiBold()
                                color(theme.brand)
                                flex(1f)
                            }
                        }
                        Text {
                            attr {
                                text("×")
                                fontSizeScaled(12f)
                                color(theme.textTertiary)
                            }
                        }
                        event { click { onCloseChartBubble() } }
                    }
                    Text {
                        attr {
                            text(chartBubble())
                            marginTop(6f)
                            fontSizeScaled(11.5f)
                            lineHeightScaled(17f)
                            color(theme.textPrimary)
                        }
                    }
                    // ① 圈选 AI 解读流式
                    vif({ circleAiState() == 1 && circleAiText().isBlank() }) {
                        Text {
                            attr {
                                text("正在生成区间解读…")
                                marginTop(4f)
                                fontSizeScaled(11f)
                                color(theme.textTertiary)
                            }
                        }
                    }
                    vif({ circleAiText().isNotBlank() }) {
                        Text {
                            attr {
                                text(circleAiText())
                                marginTop(4f)
                                fontSizeScaled(11.5f)
                                lineHeightScaled(17f)
                                color(theme.textPrimary)
                            }
                        }
                    }
                    vif({ circleAiState() == 4 }) {
                        Text {
                            attr {
                                text("AI 调用失败：${circleAiError()} · 以上为端侧统计")
                                marginTop(4f)
                                fontSizeScaled(10f)
                                lineHeightScaled(14f)
                                color(theme.textTertiary)
                            }
                        }
                    }
                    View {
                        attr {
                            marginTop(8f)
                            alignSelfFlexStart()
                            height(28f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            allCenter()
                            borderRadius(14f)
                            backgroundColor(theme.brandSoft)
                        }
                        Text {
                            attr {
                                text("去对话深聊 ›")
                                fontSizeScaled(11f)
                                fontWeightSemiBold()
                                color(theme.brand)
                            }
                        }
                        event { click { onOpenChatFromBubble() } }
                    }
                }
            }
        }
    }
}