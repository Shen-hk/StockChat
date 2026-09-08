package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.page.detail.FactorSpec
import com.kuikly.stockchat.page.detail.Materiality
import com.kuikly.stockchat.page.detail.replayContribution
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.reactive.handler.observable // 集成修复：observable 在 reactive.handler 包，base 包无此符号
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs

/**
 * 详情页板块级交互组件（doc 29：A1/H1/E3/F1/F3/G1/B1 各区块）。
 *
 * 风格与 NewsTape 对齐：顶层 builder 函数、首参吃 ViewContainer 作用域、theme 显式传入；
 * 状态读写遵守 R1（observable 只在 attr/event/vif/vbind 闭包里读）；入场动效只动
 * transform/opacity，时长只用 180/220/250/500ms；vif/vbind 挂载走 R4 两帧（setTimeout(0)
 * 翻转 presented），animate 在每个周期预注册（R5），不注册 0 时长动画。
 *
 * 注意：本文件 import 了 [Materiality]（来自 infra agent 并行创建的
 * com.kuikly.stockchat.page.detail.DetailRules）。若其落地为 `DetailRules.Materiality`
 * 嵌套类型，请改为 `import ...DetailRules` 并改用 `DetailRules.Materiality`。
 */

// ───────────────────────────── 共享数据类 ─────────────────────────────

/** 多空平衡器单段（doc 29 §4.11 F3）。 */
data class BalanceSegment(
    val label: String,
    val count: Int,
    val color: Color,
    val quote: String,
)

// FactorSpec / 重算纯函数已挪至 page.detail.DetailRules（doc §3 RuleEngine 家，可单测）。

/** 涨跌百分比文案：+x.xx% / -x.xx%，基准为 entryPrice。 */
private fun pctText(delta: Double, base: Double): String {
    val pct = if (base != 0.0) delta / base * 100.0 else 0.0
    return (if (pct >= 0) "+" else "") + Format.decimal(pct, 2) + "%"
}

// ───────────────────────────── 两帧入场辅助 ─────────────────────────────

/** 在当前容器所属 pager 上下一帧翻转状态（R4 挂载两帧；pagerId 来自 DeclarativeBaseView）。 */
private fun ViewContainer<*, *>.scheduleNextFrame(block: () -> Unit) {
    setTimeout(pagerId, 0, block)
}

/**
 * 集成修复：Kuikly 的 observable 委托只能挂在类成员属性上（局部变量委托拿到的是
 * KMutableProperty0，签名不匹配编译不过），组件内局部响应式状态统一收敛到本状态类。
 * internal：StockDetailPage.BusinessCardSlot（E3 行业比）同样需要局部响应式状态。
 */
internal class BlockState<T>(initial: T) {
    var value: T by observable(initial)
}

// ───────────────────────────── A1 当初理由回访卡 ─────────────────────────────

/**
 * A1 当初理由回访卡（doc 29 §4.1）。
 * - 收起态：一行「★ 当初理由 · {entryTimeLabel} 加自选 · 当时 {entryPrice}」+ 右侧箭头。
 * - 展开态（vif + 两帧入场，R4/R5，220ms）：三格 KPI（加自选价 / 至今涨跌 / 期间最大回撤）
 *   + 理由 + 事件一句话 + 状态胶囊（正=涨色底 / 负=跌色底）。
 * 展开收起由页面持有的 expanded() 驱动；本组件内部用 presented 做两帧翻转。
 */
internal fun ViewContainer<*, *>.RevisitCard(
    theme: StockChatTheme,
    expanded: () -> Boolean,
    onToggle: () -> Unit,
    entryTimeLabel: String,
    reason: String,
    entryPrice: Double,
    currentPrice: () -> Double,
    maxDrawdownPct: () -> Double,
    eventsSummary: String,
    statusText: () -> String,
    statusPositive: () -> Boolean,
    reduceMotion: Boolean,
) {
    // 集成修复：局部 observable 委托 → BlockState（observable 仅支持类成员属性）
    val presented = BlockState(false)

    View {
        attr {
            marginTop(theme.spacing.lg)
            padding(16f)
            borderRadius(theme.cardRadius)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
        }

        // 头部：始终可见，点击展开/收起
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text("★ 当初理由")
                    fontSize(theme.type.label)
                    fontWeightSemiBold()
                    color(theme.textSecondary)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("$entryTimeLabel 加自选 · 当时 ${Format.price(entryPrice)}")
                    fontSize(theme.type.meta)
                    color(theme.textTertiary)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text(if (expanded()) "⌄" else "›")
                    marginLeft(8f)
                    fontSize(theme.type.body)
                    color(theme.brand)
                }
            }
            event { click { onToggle() } }
        }

        // 展开面板：vif 挂载 + 两帧入场（R4/R5）
        vif({ expanded() }) {
            // 每次挂载先复位，保证反复展开都能重播入场
            presented.value = false
            scheduleNextFrame { presented.value = true }

            View {
                attr {
                    marginTop(12f)
                    opacity(if (reduceMotion || presented.value) 1f else 0f)
                    if (!reduceMotion) {
                        transform(Translate(0f, if (presented.value) 0f else 0.12f))
                        // animate 为 attr 末句、驱动键为 presented（R2/R5）；无条件注册 easeOut
                        animate(Animation.easeOut(0.22f), presented.value)
                    }
                }

                // 三格 KPI
                View {
                    attr {
                        flexDirectionRow()
                    }
                    // 集成修复（doc 29 A1）：entryPrice<=0（旧数据未记录加自选价）时 KPI 显「—」而非 0.00
                    kpiCell(theme, "加自选价", { if (entryPrice > 0) Format.price(entryPrice) else "—" }, { theme.textPrimary })
                    kpiCell(
                        theme, "至今涨跌",
                        { if (entryPrice > 0) pctText(currentPrice() - entryPrice, entryPrice) else "—" },
                        { if (entryPrice <= 0) theme.textTertiary else if (currentPrice() >= entryPrice) theme.rise else theme.fall },
                    )
                    kpiCell(
                        theme, "期间最大回撤",
                        { Format.decimal(maxDrawdownPct(), 2) + "%" },
                        { theme.fall }, // 回撤用跌色（绿）
                    )
                }

                // 理由（空则提示「未写理由」，doc §4.1）
                Text {
                    attr {
                        text("理由：${if (reason.isBlank()) "未写理由" else reason}")
                        marginTop(10f)
                        fontSize(theme.type.meta)
                        lineHeight(15f)
                        color(theme.textTertiary)
                    }
                }

                // 事件一句话
                Text {
                    attr {
                        text(eventsSummary)
                        marginTop(6f)
                        fontSize(theme.type.label)
                        lineHeight(16f)
                        color(theme.textSecondary)
                    }
                }

                // 状态胶囊：正=涨色底 / 负=跌色底
                View {
                    attr {
                        marginTop(10f)
                        alignSelfFlexStart()
                        paddingLeft(10f)
                        paddingRight(10f)
                        height(24f)
                        allCenter()
                        borderRadius(12f)
                        backgroundColor(if (statusPositive()) theme.riseSoft else theme.fallSoft)
                    }
                    Text {
                        attr {
                            text(statusText())
                            fontSize(theme.type.meta)
                            fontWeightMedium()
                            color(if (statusPositive()) theme.rise else theme.fall)
                        }
                    }
                }
            }
        }
    }
}

/** KPI 单格：label 在上、value 在下，value 由 lambda 提供以保证响应式（R1）。 */
private fun ViewContainer<*, *>.kpiCell(
    theme: StockChatTheme,
    label: String,
    value: () -> String,
    valueColor: () -> Color,
) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
            alignItemsCenter()
        }
        Text {
            attr {
                text(label)
                fontSize(theme.type.meta)
                color(theme.textTertiary)
            }
        }
        Text {
            attr {
                marginTop(4f)
                text(value())
                fontSize(theme.type.body)
                fontWeightSemiBold()
                color(valueColor())
            }
        }
    }
}

// ───────────────────────────── H1 快捷理由 chips ─────────────────────────────

/**
 * H1 加自选快捷理由 chips（doc 29 §4.13 / §2 U1）。
 * 居中横排 chips（白底 brand 描边圆角胶囊）；visible 翻转驱动 vif 挂载 + 上浮淡入 250ms（R4/R5 两帧）。
 */
internal fun ViewContainer<*, *>.QuickReasonChips(
    theme: StockChatTheme,
    reasons: List<String>,
    visible: () -> Boolean,
    onPick: (String) -> Unit,
    reduceMotion: Boolean,
) {
    // 集成修复：局部 observable 委托 → BlockState（observable 仅支持类成员属性）
    val presented = BlockState(false)

    vif({ visible() }) {
        presented.value = false
        scheduleNextFrame { presented.value = true }

        View {
            attr {
                marginTop(theme.spacing.sm)
                flexDirectionRow()
                flexWrapWrap()
                justifyContentCenter()
                opacity(if (reduceMotion || presented.value) 1f else 0f)
                if (!reduceMotion) {
                    transform(Translate(0f, if (presented.value) 0f else 0.12f))
                    animate(Animation.easeOut(0.25f), presented.value)
                }
            }
            reasons.forEach { r ->
                View {
                    attr {
                        marginTop(8f)
                        marginRight(8f)
                        paddingLeft(14f)
                        paddingRight(14f)
                        height(32f)
                        allCenter()
                        borderRadius(16f)
                        backgroundColor(theme.surface)
                        border(Border(1f, BorderStyle.SOLID, theme.brand))
                    }
                    Text {
                        attr {
                            text(r)
                            fontSize(theme.type.label)
                            fontWeightMedium()
                            color(theme.brand)
                        }
                    }
                    event { click { onPick(r) } }
                }
            }
        }
    }
}

// ───────────────────────────── F1 重要度徽章 ─────────────────────────────

/** 橙 / 灰 两枚常量色（doc 29 §4.11 F1 材质点）。theme 无对应 token，按规格硬编码。 */
private val MAT_ORANGE = Color(0xE8A13C)
private val MAT_GRAY = Color(0xB7BDC7)

/**
 * F1 重要度徽章（doc 29 §4.11）。三个 5dp 圆点：
 * HIGH = 3 个涨色 / MID = 2 个橙 / LOW = 1 个灰。
 * level 为页面经 DetailRules.MaterialityScorer 判定的结果（响应式读取，R1）。
 */
internal fun ViewContainer<*, *>.MaterialityBadge(
    theme: StockChatTheme,
    level: () -> Materiality,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
        }
        // 第一点：始终存在，颜色随评级
        dotView(theme, {
            when (level()) {
                Materiality.HIGH -> theme.rise
                Materiality.MID -> MAT_ORANGE
                Materiality.LOW -> MAT_GRAY
            }
        })
        // 第二点：HIGH / MID
        vif({ level() == Materiality.HIGH || level() == Materiality.MID }) {
            dotView(theme, { if (level() == Materiality.HIGH) theme.rise else MAT_ORANGE })
        }
        // 第三点：仅 HIGH
        vif({ level() == Materiality.HIGH }) {
            dotView(theme, { theme.rise })
        }
    }
}

/** 5dp 情绪/材质圆点，颜色由 lambda 提供以保证响应式（R1）。 */
private fun ViewContainer<*, *>.dotView(theme: StockChatTheme, color: () -> Color) {
    View {
        attr {
            width(5f)
            height(5f)
            borderRadius(2.5f)
            backgroundColor(color())
            marginRight(3f)
        }
    }
}

// ───────────────────────────── F3 多空平衡器 ─────────────────────────────

/**
 * F3 多空平衡光谱（doc 29 §4.11）。
 * - 分色比例条：各段宽度 = count 占比；选中段高亮（提亮），圆形滑块（白底 brand 描边）居中于选中段。
 * - 两端「多/空」小字（多=涨色 / 空=跌色）。
 * - 下方当前评级观点引用区：背景 surfaceMuted、10px 文本，随 selected 切换淡入 180ms（vbind 重挂载两帧）。
 * 交互：在比例条上点按或水平拖动切换到对应段（用 touch x 算命中段，参考 DetailTimelineChart 的 pan 处理范式）。
 */
internal fun ViewContainer<*, *>.BalanceSpectrumBlock(
    theme: StockChatTheme,
    segments: List<BalanceSegment>,
    initialIndex: Int = 0,
    containerWidth: Float,
    reduceMotion: Boolean,
) {
    // 集成修复：局部 observable 委托 → BlockState
    val selected = BlockState(
        initialIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0)),
    )
    val barW = (containerWidth - 32f).coerceAtLeast(40f)

    // 命中段：touch x（相对比例条左缘）→ 占比 → 落在哪段
    fun selectAt(x: Float) {
        if (segments.isEmpty()) return
        val frac = (x / barW).coerceIn(0f, 1f)
        val total = segments.sumOf { it.count }.toFloat().coerceAtLeast(1f)
        var acc = 0f
        for (i in segments.indices) {
            val start = acc / total
            acc += segments[i].count
            val end = acc / total
            if (frac <= end) { selected.value = i; return }
        }
        selected.value = segments.lastIndex
    }

    View {
        attr {
            marginTop(theme.spacing.lg)
            paddingLeft(16f)
            paddingRight(16f)
            paddingTop(12f)
            paddingBottom(12f)
            borderRadius(theme.cardRadius)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
        }

        // 两端多/空
        View {
            attr {
                flexDirectionRow()
                justifyContentSpaceBetween()
            }
            Text { attr { text("多"); fontSize(theme.type.meta); color(theme.rise) } }
            Text { attr { text("空"); fontSize(theme.type.meta); color(theme.fall) } }
        }

        // 比例条 + 滑块（嵌套 flex 居中，避免绝对定位依赖）
        View {
            attr {
                marginTop(8f)
                height(12f)
                flexDirectionRow()
                borderRadius(6f)
                // 集成修复：capture 属 attr 作用域方法，不能挂在视图构建作用域
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            segments.forEachIndexed { i, seg ->
                View {
                    attr {
                        flex(seg.count.toFloat())
                        allCenter()
                        backgroundColor(seg.color)
                        opacity(if (i == selected.value) 1f else 0.72f)
                    }
                    vif({ i == selected.value }) {
                        View {
                            attr {
                                width(18f)
                                height(18f)
                                borderRadius(9f)
                                backgroundColor(theme.surface)
                                border(Border(1.5f, BorderStyle.SOLID, theme.brand))
                                boxShadow(BoxShadow(0f, 2f, 8f, theme.brand.opacity(0.25f)))
                            }
                        }
                    }
                }
            }
            event {
                pan { params ->
                    if (params.state == "start" || params.state == "move") selectAt(params.x)
                }
                click { params -> selectAt(params.x) }
            }
        }

        // 当前评级观点引用区：随 selected 切换淡入 180ms（vbind 重挂载两帧）
        vbind({ selected.value }) {
            val seg = segments.getOrNull(selected.value) ?: segments.first()
            QuotePanel(theme, seg.quote, reduceMotion)
        }
    }
}

/**
 * 引用区单面板（F3 引文）。随 selected 重挂载，内部用 qp 做 180ms 淡入上移两帧（R4/R5）。
 * 背景 surfaceMuted、10px 文本（doc §4.11）。
 */
private fun ViewContainer<*, *>.QuotePanel(
    theme: StockChatTheme,
    quote: String,
    reduceMotion: Boolean,
) {
    // 集成修复：局部 observable 委托 → BlockState
    val qp = BlockState(false)
    scheduleNextFrame { qp.value = true }

    View {
        attr {
            marginTop(10f)
            padding(12f)
            borderRadius(theme.cardRadius)
            backgroundColor(theme.surfaceMuted)
            opacity(if (reduceMotion || qp.value) 1f else 0f)
            if (!reduceMotion) {
                transform(Translate(0f, if (qp.value) 0f else 0.08f))
                animate(Animation.easeOut(0.18f), qp.value)
            }
        }
        Text {
            attr {
                text(quote)
                fontSize(10f)
                lineHeight(15f)
                color(theme.textSecondary)
            }
        }
    }
}

// ───────────────────────────── G1 因子权重重放 ─────────────────────────────

/**
 * G1 因子权重重放（doc 29 §4.12）。
 * - 顶部固定 8.5px 灰字说明「拖动理解贡献敏感度，数学重算非预测」。
 * - 每行：名称 + 可拖/可点轨道（0–2.0×，默认 1.0×，knob 拖动或点按定位）+ 右侧权重 "1.0×"。
 *   内部 weights 为 observable List，拖动/点按更新对应权重。
 * - 底部结果行：重算涨跌 = Σ base×weight（2 位小数，涨红跌绿）+ 以 0 为中心的贡献条
 *   （重算值相对 actual 的比例）+ 「复原」文字按钮（权重全部回 1.0）+ 残差行（未解释部分）。
 * 重算逻辑见 [com.kuikly.stockchat.page.detail.replayContribution]（纯函数，已配单测）。
 */
internal fun ViewContainer<*, *>.FactorReplayBlock(
    theme: StockChatTheme,
    factors: List<FactorSpec>,
    // lambda 而非 Double：builder 闭包只取首帧快照（R1），lambda 延迟到 attr 闭包内读取，
    // 行情 tick 后残差行随 attr 重跑刷新，且组件不被 vbind 重建（拖动中的权重不丢失）。
    actualPct: () -> Double,
    containerWidth: Float,
    reduceMotion: Boolean,
) {
    // 集成修复：局部 observable 委托 → BlockState
    val weights = BlockState(factors.map { 1.0 })
    val trackW = (containerWidth - 188f).coerceAtLeast(80f)

    fun handleTrack(i: Int, x: Float) {
        val frac = (x / trackW).coerceIn(0f, 1f)
        val w = (frac * 2.0).coerceIn(0.0, 2.0)
        weights.value = weights.value.toMutableList().also { it[i] = w }
    }

    View {
        attr {
            marginTop(theme.spacing.lg)
            padding(16f)
            borderRadius(theme.cardRadius)
            backgroundColor(theme.surface)
            border(Border(0.5f, BorderStyle.SOLID, theme.divider))
        }

        // 顶部固定说明（8.5px 灰字）
        Text {
            attr {
                text("拖动理解贡献敏感度，数学重算非预测")
                fontSize(8.5f)
                color(theme.textTertiary)
            }
        }

        // 各因子行
        factors.forEachIndexed { i, f ->
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(10f)
                }
                Text {
                    attr {
                        width(88f)
                        text(f.name)
                        fontSize(theme.type.label)
                        color(theme.textSecondary)
                    }
                }
                // 轨道（嵌套 flex 定位 knob：前 spacer=flex(weight)，knob 固定，后 spacer=flex(2-weight)）
                View {
                    attr {
                        width(trackW)
                        height(8f)
                        alignItemsCenter()
                        borderRadius(4f)
                        backgroundColor(theme.surfaceMuted)
                        // 集成修复：capture 属 attr 作用域方法，不能挂在视图构建作用域
                        capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                    }
                    View { attr { flex(weights.value[i].toFloat().coerceAtLeast(0.001f)) } }
                    View {
                        attr {
                            width(16f)
                            height(16f)
                            borderRadius(8f)
                            backgroundColor(theme.brand)
                        }
                    }
                    View { attr { flex((2.0 - weights.value[i]).coerceAtLeast(0.001).toFloat()) } }
                    event {
                        pan { params ->
                            if (params.state == "start" || params.state == "move") handleTrack(i, params.x)
                        }
                        click { params -> handleTrack(i, params.x) }
                    }
                }
                Text {
                    attr {
                        width(48f)
                        marginLeft(12f)
                        text("${Format.decimal(weights.value[i], 1)}×")
                        fontSize(theme.type.label)
                        color(theme.textPrimary)
                        textAlignRight()
                    }
                }
            }
        }

        // 底部结果行
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(14f)
                paddingTop(12f)
                borderTop(Border(0.5f, BorderStyle.SOLID, theme.divider))
            }
            // 重算涨跌（在自身 attr 内读 weights，保证响应式 R1）
            Text {
                attr {
                    text("重算涨跌 ")
                    fontSize(theme.type.label)
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    val r = replayContribution(factors, weights.value)
                    text((if (r >= 0) "+" else "") + Format.decimal(r, 2) + "%")
                    fontSize(theme.type.body)
                    fontWeightSemiBold()
                    color(if (r >= 0) theme.rise else theme.fall)
                }
            }
            // 贡献条（以 0 为中心）
            View {
                attr {
                    flex(1f)
                    marginLeft(12f)
                    marginRight(12f)
                    height(6f)
                    flexDirectionRow()
                    borderRadius(3f)
                    backgroundColor(theme.surfaceMuted)
                }
                // 左半（负向）
                View {
                    attr {
                        flex(1f)
                        justifyContentFlexEnd()
                    }
                    vif({ replayContribution(factors, weights.value) < 0 }) {
                        val r = replayContribution(factors, weights.value)
                        val s = maxOf(abs(actualPct()), abs(r), 0.5)
                        val f = (abs(r) / s).toFloat().coerceIn(0f, 1f)
                        View { attr { flex(f); backgroundColor(theme.fall) } }
                    }
                }
                // 右半（正向）
                View {
                    attr { flex(1f) }
                    vif({ replayContribution(factors, weights.value) > 0 }) {
                        val r = replayContribution(factors, weights.value)
                        val s = maxOf(abs(actualPct()), abs(r), 0.5)
                        val f = (abs(r) / s).toFloat().coerceIn(0f, 1f)
                        View { attr { flex(f); backgroundColor(theme.rise) } }
                    }
                }
            }
            // 复原
            Text {
                attr {
                    text("复原")
                    fontSize(theme.type.label)
                    fontWeightMedium()
                    color(theme.brand)
                }
                event { click { weights.value = factors.map { 1.0 } } }
            }
        }

        // G1 残差行（doc §4.12「残差=未解释部分如实展示」）：
        // 未解释 = 实际涨跌 − 重算值；attr 闭包内读 weights/actualPct 建立响应式（R1）。
        Text {
            attr {
                marginTop(6f)
                val r = replayContribution(factors, weights.value)
                val residual = actualPct() - r
                text(
                    "实际涨跌 " + signedPct(actualPct()) +
                        " · 未解释部分 " + signedPct(residual) +
                        "（实际 − 重算，模型未覆盖的成分）"
                )
                fontSize(8.5f)
                color(theme.textTertiary)
            }
        }
    }
}

/** G1 残差用带符号百分比：+x.xx% / -x.xx%。 */
private fun signedPct(v: Double): String =
    (if (v >= 0) "+" else "") + Format.decimal(v, 2) + "%"

// ───────────────────────────── E3 长按行业比 ─────────────────────────────

/**
 * E3 行业对比静态样本（doc 29 §4.10「静态行业样本」）。端侧暂无行业截面数据，
 * 与 F3/G1 同范式挂「示例 · 端侧静态样本」标注，只述事实不构成建议。
 */
private val INDUSTRY_SAMPLE_PEERS: List<Pair<String, Double>> = listOf(
    "同业样本一" to 1.62,
    "同业样本二" to 0.84,
    "同业样本三" to -0.35,
    "同业样本四" to -1.12,
    "同业样本五" to -2.05,
)

/**
 * E3 卡内覆盖层：行业 Top5 横条（只读）。由 BusinessCardSlot 的长按手势驱动：
 * longPress start 挂载、松手 2.2s 后由调用方卸载（弹回）。touchEnable(false)
 * 保证不拦截手势，松手事件仍落在卡片上。
 */
internal fun ViewContainer<*, *>.IndustryCompareOverlay(
    theme: StockChatTheme,
    cardLabel: String,
) {
    val maxAbs = INDUSTRY_SAMPLE_PEERS.maxOf { abs(it.second) }.coerceAtLeast(0.01)
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(theme.surface.opacity(0.97f))
            borderRadius(theme.cardRadius)
            border(Border(1f, BorderStyle.SOLID, theme.divider))
            padding(12f)
            touchEnable(false)
        }
        Text {
            attr {
                text("$cardLabel · 行业内对比 Top5（示例 · 端侧静态样本）")
                fontSize(theme.type.label)
                fontWeightSemiBold()
                color(theme.textPrimary)
            }
        }
        INDUSTRY_SAMPLE_PEERS.forEach { (name, pct) ->
            View {
                attr { marginTop(8f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        width(62f)
                        text(name)
                        fontSize(theme.type.meta)
                        color(theme.textSecondary)
                    }
                }
                // 横条：以最大 |pct| 归一化的比例填充（涨红跌绿，U2 数据语义色）
                View {
                    attr { flex(1f); height(6f); borderRadius(3f); backgroundColor(theme.surfaceMuted); flexDirectionRow() }
                    View {
                        attr {
                            flex((abs(pct) / maxAbs).toFloat().coerceIn(0.02f, 1f))
                            height(6f)
                            borderRadius(3f)
                            backgroundColor(if (pct >= 0) theme.rise else theme.fall)
                        }
                    }
                    View { attr { flex((1.0 - abs(pct) / maxAbs).toFloat().coerceAtLeast(0.001f)) } }
                }
                Text {
                    attr {
                        width(52f)
                        marginLeft(6f)
                        text(signedPct(pct))
                        fontSize(theme.type.meta)
                        textAlignRight()
                        color(if (pct >= 0) theme.rise else theme.fall)
                    }
                }
            }
        }
        Text {
            attr {
                marginTop(8f)
                text("松手约 2 秒后自动弹回 · 只述事实，不构成建议")
                fontSize(8.5f)
                color(theme.textTertiary)
            }
        }
    }
}
