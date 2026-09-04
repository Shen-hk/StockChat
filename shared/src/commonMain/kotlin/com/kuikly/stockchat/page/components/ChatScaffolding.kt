package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.AccessibilityRole
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal enum class WelcomeMode { FULL, BRIEF }

internal enum class WelcomeStarterKind { MOVE, TERM, REPORT, COMPARE }

internal data class WelcomeStarter(
    val kind: WelcomeStarterKind,
    val question: String,
    val detail: String,
)

internal fun defaultWelcomeStarters(): List<WelcomeStarter> = listOf(
    WelcomeStarter(WelcomeStarterKind.MOVE, "贵州茅台今天为什么跌？", "消息面 · 资金面 · 板块联动"),
    WelcomeStarter(WelcomeStarterKind.TERM, "MACD 金叉是什么意思？", "一句话讲清，再举个例子"),
    WelcomeStarter(WelcomeStarterKind.REPORT, "用三句话讲下贵州茅台的中报", "营收 · 净利 · 同比变化"),
    WelcomeStarter(WelcomeStarterKind.COMPARE, "对比茅台和五粮液的营收与净利润", "只陈事实，不做选择建议"),
)

/**
 * 动效状态一律以取值闭包传入，并且只能在 `attr {}` 内部调用。
 *
 * Kuikly 的响应式依赖是按 attr/event 闭包收集的：只有在闭包**里面**读到 observable，
 * 该闭包才会在属性变化时重跑。在 `vif { }` 体里把 `page.xxx` 当函数实参读掉，拿到的
 * 只是建视图那一刻的快照——attr 永远不会重跑，文案不动、光标不闪。
 * 更隐蔽的是 `Attr.animate()`：它靠 ReactiveObserver 当前的 observablePropertyKey
 * 找到驱动它的属性，key 为空时直接 return，动画会静默失效、不报错。所以驱动动画的
 * observable 必须紧挨着 `animate()` 之前被读到（惯例是直接当 animate 的 value 实参）。
 */
internal fun ViewContainer<*, *>.WelcomeSection(
    theme: StockChatTheme,
    mode: WelcomeMode,
    greeting: String,
    rotatingKeyword: () -> String,
    cursorVisible: () -> Boolean,
    entranceVisible: () -> Boolean,
    onMounted: () -> Unit,
    reduceMotion: Boolean = false,
    starters: List<WelcomeStarter> = defaultWelcomeStarters(),
    onChoose: (WelcomeStarter) -> Unit,
) {
    val full = mode == WelcomeMode.FULL
    View {
        attr {
            alignSelfStretch()
            paddingTop(if (full) 18f else theme.spacing.md)
            paddingLeft(6f)
            paddingRight(6f)
            paddingBottom(theme.spacing.lg)
            alignItemsCenter()
            // 文案必须始终可见；入场只交给示例卡做阶梯上滑，避免空会话白屏。
            opacity(1f)
        }
        // 入场只能从实际挂载点启动。pageDidAppear 可能早于 body/子视图构建，
        // 在那里触发会令一次性的 presented 变化发生在 attr 注册依赖之前。
        ref { onMounted() }
        WelcomeBackdrop(theme, full)
        Text {
            attr {
                text(greeting)
                fontSize(theme.type.label)
                fontWeightMedium()
                color(theme.textSecondary)
            }
        }
        // 精简档隐去 Logo（规范 §3「36×36 或隐去」）。这是唯一能把首屏高度压到规范
        // 承诺的 40% 的杠杆——4 张示例卡是两档共有的 264dp 地板，光调间距挤不出来。
        if (full) WelcomeHero(theme, full)
        if (full) {
            Text {
                attr {
                    text("你好，我是股问")
                    marginTop(theme.spacing.lg)
                    fontSize(theme.type.h1)
                    fontWeightBold()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text("做股民的解释器，不做荐股机")
                    marginTop(6f)
                    fontSize(theme.type.sm)
                    color(theme.textSecondary)
                }
            }
            WelcomeCapabilityRow(theme)
            WelcomeIntroCopy(theme, rotatingKeyword, cursorVisible)
        }
        WelcomeTrustRow(theme, full)
        Text {
            attr {
                text("可以这样问")
                alignSelfFlexStart()
                marginTop(if (full) theme.spacing.xl else theme.spacing.md)
                fontSize(theme.type.meta)
                fontWeightSemiBold()
                color(theme.textTertiary)
            }
        }
        starters.forEachIndexed { index, starter ->
            QuestionStarterCard(starter, theme, index, entranceVisible, reduceMotion, onChoose)
        }
    }
}

/**
 * 方案 C 背景：顶部一束柔光（规范 6.4.1）。
 * Kuikly 无 radial-gradient，用两个竖直线性渐变的椭圆叠加近似：
 * 峰值透明度浅色 ≤ .075 / 深色 ≤ .10，精简档 ×0.6；椭圆顶端探出屏外，
 * 光束中心落在问候语与 Logo 之间（约容器纵向 22%）。
 */
private fun ViewContainer<*, *>.WelcomeBackdrop(theme: StockChatTheme, full: Boolean) {
    val dark = theme == StockChatTheme.Dark
    val dim = if (full) 1f else 0.6f
    // 两层叠加后的峰值 ≈ 浅色 .075 / 深色 .10（规范 6.4.1）；精简档 ×0.6
    val outerAlpha = (if (dark) 0.055f else 0.045f) * dim
    val innerAlpha = (if (dark) 0.050f else 0.030f) * dim
    // 光束中心 = 椭圆中心，对齐「Logo 与标题之间」：
    // 完整档 Logo 占 44~96dp、主标题 112~136dp → 取 100dp；
    // 精简档无 Logo 无标题，上部内容（问候语 12~26 + 信任条 38~72）→ 取 44dp。
    val outerCenter = if (full) 100f else 44f
    val innerCenter = if (full) 96f else 42f
    val outerHeight = if (full) 260f else 170f
    val innerHeight = if (full) 170f else 110f
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(if (full) 320f else 180f)
            touchEnable(false)
        }
        // 外圈：宽而淡的光晕，顶端探出容器形成自上而下的引导
        View {
            attr {
                absolutePosition(top = outerCenter - outerHeight / 2f, left = 0f, right = 0f)
                marginLeft(40f)
                marginRight(40f)
                height(outerHeight)
                borderRadius(outerHeight / 2f)
                touchEnable(false)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM,
                    ColorStop(theme.brand.opacity(outerAlpha), 0f),
                    ColorStop(theme.brand.opacity(outerAlpha * 0.5f), 0.45f),
                    ColorStop(theme.brand.opacity(0f), 1f),
                )
            }
        }
        // 内芯：窄而亮，压在光晕中心上，让峰值落在 Logo 与标题之间
        View {
            attr {
                absolutePosition(top = innerCenter - innerHeight / 2f, left = 0f, right = 0f)
                marginLeft(96f)
                marginRight(96f)
                height(innerHeight)
                borderRadius(innerHeight / 2f)
                touchEnable(false)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM,
                    ColorStop(theme.brand.opacity(innerAlpha), 0f),
                    ColorStop(theme.brand.opacity(innerAlpha * 0.5f), 0.45f),
                    ColorStop(theme.brand.opacity(0f), 1f),
                )
            }
        }
    }
}

private fun ViewContainer<*, *>.WelcomeHero(theme: StockChatTheme, full: Boolean) {
    View {
        attr {
            size(if (full) 52f else 36f, if (full) 52f else 36f)
            marginTop(10f)
            allCenter()
            borderRadius(if (full) 16f else 12f)
            backgroundColor(theme.brand)
            // 装饰元素，读屏跳过（规范 §6.5）
            accessibilityRole(AccessibilityRole.NONE)
        }
        LineIconTrendDown(color = theme.onBrand, size = if (full) 25f else 19f)
    }
}

private fun ViewContainer<*, *>.WelcomeCapabilityRow(theme: StockChatTheme) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(theme.spacing.lg)
        }
        listOf("解释涨跌", "讲清术语", "读懂财报").forEachIndexed { index, label ->
            View {
                attr {
                    height(24f)
                    if (index > 0) marginLeft(theme.spacing.sm)
                    paddingLeft(9f)
                    paddingRight(9f)
                    allCenter()
                    borderRadius(12f)
                    backgroundColor(theme.surfaceMuted)
                }
                Text {
                    attr {
                        text(label)
                        fontSize(theme.type.meta)
                        color(theme.textSecondary)
                    }
                }
            }
        }
    }
}

/**
 * 一句话说明（仅完整档）。轮播关键词按原型处理：brand 色半粗，尾部跟
 * 2px brand 光标（1.1s step-end 闪烁由 ChatPage 驱动 cursorVisible）。
 */
private fun ViewContainer<*, *>.WelcomeIntroCopy(
    theme: StockChatTheme,
    rotatingKeyword: () -> String,
    cursorVisible: () -> Boolean,
) {
    View {
        attr {
            marginTop(theme.spacing.lg)
            alignSelfStretch()
            alignItemsCenter()
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                justifyContentCenter()
            }
            Text {
                attr {
                    text("看不懂的")
                    fontSize(theme.type.body)
                    lineHeight(27f)
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    // 必须在 attr 内部调用取值闭包，否则打字机文案不会重绘。
                    text(rotatingKeyword())
                    fontSize(theme.type.body)
                    lineHeight(27f)
                    fontWeightSemiBold()
                    color(theme.brand)
                }
            }
            View {
                attr {
                    width(2f)
                    height(16f)
                    marginLeft(2f)
                    backgroundColor(theme.brand)
                    // step-end 闪烁：直接切 opacity，不注册 animate()。
                    opacity(if (cursorVisible()) 1f else 0f)
                }
            }
            Text {
                attr {
                    text("，我讲给你听。")
                    fontSize(theme.type.body)
                    lineHeight(27f)
                    color(theme.textSecondary)
                }
            }
        }
        Text {
            attr {
                text("只解释发生了什么，不预测该买什么。")
                fontSize(theme.type.body)
                lineHeight(27f)
                color(theme.textSecondary)
            }
        }
    }
}

private fun ViewContainer<*, *>.WelcomeTrustRow(theme: StockChatTheme, full: Boolean) {
    View {
        attr {
            alignSelfStretch()
            minHeight(34f)
            marginTop(if (full) theme.spacing.x2 else theme.spacing.md)
            paddingLeft(11f)
            paddingRight(11f)
            paddingTop(8f)
            paddingBottom(8f)
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(theme.surfaceMuted)
            borderRadius(13f)
        }
        View {
            attr {
                size(22f, 22f)
                marginRight(8f)
                allCenter()
                borderRadius(11f)
                backgroundColor(theme.brandSoft)
            }
            LineIconShieldCheck(color = theme.brand, size = 15f)
        }
        Text {
            attr {
                text("行情来自交易所实时数据 · 每条结论都标了出处和时间")
                fontSize(theme.type.meta)
                lineHeight(15f)
                color(theme.textTertiary)
                flex(1f)
            }
        }
    }
}

private fun ViewContainer<*, *>.QuestionStarterCard(
    starter: WelcomeStarter,
    theme: StockChatTheme,
    index: Int,
    entranceVisible: () -> Boolean,
    reduceMotion: Boolean,
    onChoose: (WelcomeStarter) -> Unit,
) {
    val iconBackground = when (starter.kind) {
        WelcomeStarterKind.MOVE -> theme.riseSoft
        WelcomeStarterKind.TERM -> theme.brandSoft
        WelcomeStarterKind.REPORT -> theme.fallSoft
        WelcomeStarterKind.COMPARE -> theme.brandSoft
    }
    val iconColor = when (starter.kind) {
        WelcomeStarterKind.MOVE -> theme.rise
        WelcomeStarterKind.TERM -> theme.term
        WelcomeStarterKind.REPORT -> theme.fall
        WelcomeStarterKind.COMPARE -> theme.brand
    }
    View {
        attr {
            alignSelfStretch()
            minHeight(58f)
            marginTop(theme.spacing.sm)
            paddingLeft(theme.spacing.md)
            paddingRight(theme.spacing.md)
            paddingTop(9f)
            paddingBottom(9f)
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
            border(Border(1f, BorderStyle.SOLID, theme.divider))
            // 无障碍（规范 §6.5）：朗读「示例问题：…」整句，副说明一并读出；
            // 按钮语义让读屏播报「点按两次即可激活」。
            accessibility("示例问题：${starter.question}。${starter.detail}")
            accessibilityRole(AccessibilityRole.BUTTON)
            accessibilityInfo(clickable = true, longClickable = false)
            // 入场：上滑 + 淡入，40ms 阶梯延迟（规范 7 · 动效）。
            // percentageY = 0.28 → 卡片自身高度的 28%（58dp 卡约 16dp 上滑）。
            if (reduceMotion) {
                // 减弱动态效果：直接落终态，且不读取动画 observable、不注册 animate()。
                opacity(1f)
                transform(Translate(0f, 0f))
            } else {
                val visible = entranceVisible()
                opacity(if (visible) 1f else 0f)
                transform(Translate(0f, if (visible) 0f else 0.28f))
                // 第二个实参必须现场再读一次 observable：animate() 取的是
                // ReactiveObserver 里「最近一次被读到」的属性 key，用局部变量
                // 会让 key 落空，动画不报错但完全不生效。
                //
                // 【关键·源码级】注册滞后一个周期：Attr.beginApplyAttrProperty 在
                // attr 闭包重跑「之前」消费 AnimationState（next→cur），把上一个
                // 周期注册的动画下发给 native；本周期 animate() 注册的只进
                // nextAnimations，要等下一个同 key 变更周期才会被消费。
                // 所以这里必须【无条件】注册入场动画（含未呈现态）——flip 周期
                // 消费到的正是挂载时注册的这份 easeOut。若在未呈现态注册
                // linear(0)（想「复位瞬时」），flip 消费到的就是这个 linear(0)，
                // 入场退化为 0 时长瞬移——这正是本次入场动效失效的根因。
                // 与 CardSheet 无条件注册 easeOut 是同一范式。
                animate(
                    Animation.easeOut(0.30f).delay(0.08f + 0.04f * index),
                    entranceVisible(),
                )
            }
        }
        View {
            attr {
                size(32f, 32f)
                allCenter()
                borderRadius(10f)
                backgroundColor(iconBackground)
                marginRight(10f)
            }
            when (starter.kind) {
                WelcomeStarterKind.MOVE -> LineIconTrendDown(color = iconColor, size = 18f)
                WelcomeStarterKind.TERM -> LineIconBook(color = iconColor, size = 18f)
                WelcomeStarterKind.REPORT -> LineIconFileText(color = iconColor, size = 18f)
                WelcomeStarterKind.COMPARE -> LineIconColumns(color = iconColor, size = 18f)
            }
        }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text(starter.question)
                    fontSize(theme.type.body)
                    fontWeightMedium()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    text(starter.detail)
                    marginTop(3f)
                    fontSize(theme.type.meta)
                    lineHeight(15f)
                    color(theme.textTertiary)
                }
            }
        }
        LineIconChevronRight(color = theme.textTertiary, size = 17f)
        event { click { onChoose(starter) } }
    }
}

internal fun ViewContainer<*, *>.DateDivider(theme: StockChatTheme) {
    View {
        attr { marginTop(14f); marginBottom(8f); flexDirectionRow(); alignItemsCenter() }
        View { attr { height(1f); flex(1f); backgroundColor(theme.divider) } }
        Text { attr { text("今天"); marginLeft(10f); marginRight(10f); fontSize(11f); color(theme.textTertiary) } }
        View { attr { height(1f); flex(1f); backgroundColor(theme.divider) } }
    }
}

internal fun ViewContainer<*, *>.RecentSymbolRow(theme: StockChatTheme, onSelect: (String) -> Unit) {
    Scroller {
        attr { height(28f); flexDirectionRow() }
        listOf("📍 贵州茅台", "五粮液", "上证指数", "+ 添加关注").forEach { label ->
            View {
                attr { height(26f); marginRight(7f); paddingLeft(10f); paddingRight(10f); justifyContentCenter(); backgroundColor(theme.surfaceMuted); borderRadius(13f) }
                Text { attr { text(label); fontSize(11f); color(if (label.startsWith("+")) theme.brand else theme.textSecondary) } }
                event { click { if (!label.startsWith("+")) onSelect(label.removePrefix("📍 ")) } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.RegressionQuestionRow(
    theme: StockChatTheme,
    onQuestion: (String) -> Unit,
) {
    val cases = listOf(
        "手风琴" to "回归：手风琴 贵州茅台最近怎么样",
        "资讯 Sheet" to "回归：资讯 Sheet 看贵州茅台资讯",
        "归因下钻" to "回归：归因下钻 为什么跌",
        "分支追问" to "回归：分支追问 贵州茅台最近怎么样",
        "焦点放大" to "回归：焦点放大 贵州茅台最近怎么样",
        "对比卡" to "回归：对比卡 贵州茅台和五粮液比较",
    )
    Scroller {
        attr {
            height(42f)
            paddingLeft(14f)
            paddingRight(14f)
            paddingBottom(6f)
            flexDirectionRow()
        }
        cases.forEach { item ->
            View {
                attr {
                    height(32f)
                    marginRight(7f)
                    paddingLeft(10f)
                    paddingRight(10f)
                    justifyContentCenter()
                    borderRadius(10f)
                    backgroundColor(theme.surfaceMuted)
                }
                Text {
                    attr {
                        text(item.first)
                        fontSize(11f)
                        fontWeightMedium()
                        color(theme.textSecondary)
                    }
                }
                event { click { onQuestion(item.second) } }
            }
        }
    }
}


internal fun ViewContainer<*, *>.ActiveComparePanel(
    model: StockCompareCardModel,
    theme: StockChatTheme,
    insightLoading: () -> Boolean = { false },
    insightText: () -> String = { "" },
    insightError: () -> String = { "" },
    onRetryInsight: () -> Unit = {},
    onOpenStock: (String) -> Unit,
    onClose: () -> Unit,
) {
    View {
        attr {
            marginLeft(12f)
            marginRight(12f)
            marginBottom(8f)
            padding(10f)
            backgroundColor(theme.surface)
            borderRadius(theme.cardRadius)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("对比视图")
                    fontSize(12f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                    flex(1f)
                }
            }
            Text { attr { text("退出"); fontSize(11f); color(theme.textSecondary) } }
            event { click { onClose() } }
        }
        CardShell(
            model,
            CardContext(
                theme = theme,
                density = CardDensity.FULL,
                onOpenStock = onOpenStock,
            ),
        )
        View {
            attr {
                marginTop(10f)
                padding(10f)
                borderRadius(10f)
                backgroundColor(theme.surfaceMuted)
            }
            Text {
                attr {
                    text("AI 解读")
                    fontSize(11f)
                    fontWeightSemiBold()
                    color(theme.textSecondary)
                }
            }
            Text {
                attr {
                    val content = when {
                        insightLoading() -> "正在读取两只股票的差异..."
                        insightError().isNotBlank() -> insightError()
                        insightText().isNotBlank() -> insightText()
                        else -> "等待第二只股票完成对比"
                    }
                    text(content)
                    marginTop(6f)
                    fontSize(11f)
                    lineHeight(17f)
                    color(if (insightError().isNotBlank()) theme.fall else theme.textSecondary)
                }
            }
            View {
                attr {
                    val visible = insightError().isNotBlank()
                    height(if (visible) 24f else 0f)
                    marginTop(if (visible) 8f else 0f)
                    paddingLeft(9f)
                    paddingRight(9f)
                    alignSelfFlexStart()
                    allCenter()
                    borderRadius(8f)
                    backgroundColor(theme.brandSoft)
                    opacity(if (visible) 1f else 0f)
                    touchEnable(visible)
                }
                Text { attr { text("重试"); fontSize(10f); fontWeightMedium(); color(theme.brand) } }
                event { click { if (insightError().isNotBlank()) onRetryInsight() } }
            }
        }
    }
}
