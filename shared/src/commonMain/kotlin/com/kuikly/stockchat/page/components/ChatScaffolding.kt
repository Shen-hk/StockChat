package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.components.CardShell
import com.kuikly.stockchat.cards.core.CardContext
import com.kuikly.stockchat.cards.core.CardDensity
import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
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

/** 用于 used-kinds 去重逻辑的全集（BRIEF 档恢复时才会真正消费）。 */
internal fun defaultWelcomeStarters(): List<WelcomeStarter> = listOf(
    WelcomeStarter(WelcomeStarterKind.MOVE, "贵州茅台今天为什么跌？", "消息面 · 资金面 · 板块联动"),
    WelcomeStarter(WelcomeStarterKind.TERM, "MACD 金叉是什么意思？", "一句话讲清，再举个例子"),
    WelcomeStarter(WelcomeStarterKind.REPORT, "用三句话讲下贵州茅台的中报", "营收 · 净利 · 同比变化"),
    WelcomeStarter(WelcomeStarterKind.COMPARE, "对比茅台和五粮液的营收与净利润", "只陈事实，不做选择建议"),
)

/** 问AI 分区的示例卡（看市场分区是跳转入口，不占推荐位）。 */
internal fun aiWelcomeStarters(): List<WelcomeStarter> = listOf(
    WelcomeStarter(WelcomeStarterKind.TERM, "MACD 金叉是什么意思？", "一句话讲清，再举个例子"),
    WelcomeStarter(WelcomeStarterKind.TERM, "市盈率（PE）多少算高？", "给区间，再举个实际例子"),
)

/**
 * 欢迎区（2026-09-05 三轮）：图标 + 主题句 + 问AI/看市场 胶囊 + 示例卡。
 * 问AI 常选中（滑块固定在左），管下方推荐语；看市场是入口，点击跳市场页。
 *
 * 动效状态一律以取值闭包传入，并且只能在 `attr {}` / `vif {}` 内部调用。
 *
 * Kuikly 的响应式依赖是按 attr/event/vif 闭包收集的：只有在闭包**里面**读到 observable，
 * 该闭包才会在属性变化时重跑。在 `vif { }` 体里把 `page.xxx` 当函数实参读掉，拿到的
 * 只是建视图那一刻的快照——attr 永远不会重跑，文案不动、光标不闪。
 * 更隐蔽的是 `Attr.animate()`：它靠 ReactiveObserver 当前的 observablePropertyKey
 * 找到驱动它的属性，key 为空时直接 return，动画会静默失效、不报错。所以驱动动画的
 * observable 必须紧挨着 `animate()` 之前被读到（惯例是直接当 animate 的 value 实参）。
 */
internal fun ViewContainer<*, *>.WelcomeSection(
    theme: StockChatTheme,
    rotatingKeyword: () -> String,
    cursorVisible: () -> Boolean,
    entranceVisible: () -> Boolean,
    /** 「看市场」胶囊选中态（取值闭包）：驱动滑块滑到右半格并高亮文案。 */
    marketTabSelected: () -> Boolean,
    onMounted: () -> Unit,
    onOpenMarket: () -> Unit,
    reduceMotion: Boolean = false,
    onChoose: (WelcomeStarter) -> Unit,
) {
    View {
        attr {
            alignSelfStretch()
            paddingTop(1f)
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
        // 顶部呼吸位：固定 50dp（用户决策 2026-09-05）。必须用固定值而不是屏高
        // 比例——0.22×屏高档（≈176dp）曾把底部示例卡推出首屏，导致"进去要先上
        // 划才能看全"。50dp 档内容总高仍留有余量，任何机型首屏完整可见。
        View { attr { height(50f) } }
        WelcomeBadge(theme)
        // 主题句：StockChat帮你看 + 轮播词 + 打字光标（22 号）
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                justifyContentCenter()
            }
            Text {
                attr {
                    text("StockChat帮你看")
                    fontSize(22f)
                    fontWeightBold()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    // 必须在 attr 内部调用取值闭包，否则打字机文案不会重绘。
                    text(rotatingKeyword())
                    fontSize(22f)
                    fontWeightBold()
                    color(theme.brand)
                }
            }
            View {
                attr {
                    width(2.5f)
                    height(24f)
                    marginLeft(3f)
                    backgroundColor(theme.brand)
                    // step-end 闪烁：直接切 opacity，不注册 animate()。
                    opacity(if (cursorVisible()) 1f else 0f)
                }
            }
        }
        WelcomeTabRow(theme, marketTabSelected, onOpenMarket)
        Text {
            attr {
                text("可以这样问")
                alignSelfFlexStart()
                marginTop(theme.spacing.xl)
                fontSize(theme.type.meta)
                fontWeightSemiBold()
                color(theme.textTertiary)
            }
        }
        defaultWelcomeStarters().forEachIndexed { index, starter ->
            QuestionStarterCard(starter, theme, index, entranceVisible, reduceMotion, onChoose)
        }
    }
}

/** 主题句上方的品牌图标（58dp brand 圆角方块 + 趋势线）。装饰元素，读屏跳过。 */
private fun ViewContainer<*, *>.WelcomeBadge(theme: StockChatTheme) {
    View {
        attr {
            size(68f, 68f)
            marginBottom(16f)
            allCenter()
            borderRadius(17f)
            backgroundColor(theme.brand)
            accessibilityRole(AccessibilityRole.NONE)
        }
        LineIconTrendUp(color = theme.onBrand, size = 37f)
    }
}

/**
 * 问AI / 看市场 切换（圆角长方形 190×45 / 字号 15，用户决策 2026-09-05；
 * 不再是全圆胶囊）。
 * 问AI 常选中；看市场是跳转入口：点击后滑块滑到右半格，滑动结束由调用方
 * 震动并跳转市场页（时序在 ChatPage.handleWelcomeMarketTap）。
 */
private fun ViewContainer<*, *>.WelcomeTabRow(
    theme: StockChatTheme,
    marketTabSelected: () -> Boolean,
    onOpenMarket: () -> Unit,
) {
    View {
        attr {
            marginTop(24f)
            size(190f, 45f)
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(theme.surfaceMuted)
            borderRadius(12f)
        }
        // 滑块：默认停在「问AI」半格（(190-6)/2 = 92），选中看市场时滑到右半格。
        View {
            attr {
                absolutePosition(left = 3f, top = 3f)
                size(92f, 39f)
                borderRadius(10f)
                backgroundColor(theme.surface)
                touchEnable(false)
                // 取值闭包必须在 attr 内现场调用才建立依赖；translate 用 offsetX=px。
                val marketSelected = marketTabSelected()
                transform(translate = Translate(0f, 0f, offsetX = if (marketSelected) 92f else 0f))
                // 无条件注册（R2/R5）：animate 绑定最后读到的 marketTabSelected()，
                // 翻转周期消费上轮注册的 easeOut，滑动先快后慢。
                animate(Animation.easeOut(0.22f), marketTabSelected())
            }
        }
        View {
            attr {
                height(34f)
                flex(1f)
                allCenter()
                borderRadius(17f)
                // 读屏：按钮语义 + 朗读。
                accessibility("问AI，当前选中")
                accessibilityRole(AccessibilityRole.BUTTON)
                accessibilityInfo(clickable = true, longClickable = false)
            }
            Text {
                attr {
                    text("问AI")
                    fontSize(15f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                }
            }
        }
        View {
            attr {
                height(34f)
                flex(1f)
                allCenter()
                borderRadius(17f)
                accessibility("看市场，打开市场总览")
                accessibilityRole(AccessibilityRole.BUTTON)
                accessibilityInfo(clickable = true, longClickable = false)
            }
            Text {
                attr {
                    text("看行情")
                    fontSize(15f)
                    fontWeightSemiBold()
                    // 滑块滑到右半格时同步高亮，与滑块动画共用同一驱动。
                    color(if (marketTabSelected()) theme.textPrimary else theme.textSecondary)
                }
            }
            event { click { onOpenMarket() } }
        }
    }
}

/**
 * 方案 C 背景：顶部一束柔光（规范 6.4.1）。
 * Kuikly 无 radial-gradient，用「同心胶囊由外向内逐层收窄提亮」近似径向衰减。
 * 旧版两层大台阶（.045/.030）+ 内芯 stop0 满量 alpha，会读成两枚叠放胶囊，
 * 且内芯顶边在可见区留下一条硬线——这是丑感主因。现改四层：
 *   1) 层间 alpha 台阶 ≤ .02，胶囊轮廓互相融掉；
 *   2) 每层渐变两端全透明、中点峰值（0.25/0.75 处 55% 过渡站近似钟形），
 *      任何一层都不露硬边，也不再依赖「顶端探出屏外 + 父容器裁剪」这一假设。
 * 四层峰值之和 ≈ 浅色 .075 / 深色 .10（规范 6.4.1）；精简档 ×0.6。
 */
private fun ViewContainer<*, *>.WelcomeBackdrop(theme: StockChatTheme, full: Boolean) {
    val dark = theme == StockChatTheme.Dark
    val dim = if (full) 1f else 0.6f
    // 四层几何：(高度, 左右内缩)。由外向内收窄；最内层保证宽 > 高，避免退化成圆斑。
    val geometry = if (full) {
        listOf(300f to 16f, 228f to 58f, 162f to 100f, 104f to 138f)
    } else {
        listOf(188f to 16f, 142f to 54f, 100f to 92f, 66f to 126f)
    }
    // 各层峰值 alpha，逐层求和守预算：浅色 .018+.020+.020+.017 = .075，
    // 深色 .024+.027+.027+.022 = .100（规范 6.4.1），精简档整体 ×0.6。
    val peaks = if (dark) {
        listOf(0.024f, 0.027f, 0.027f, 0.022f)
    } else {
        listOf(0.018f, 0.020f, 0.020f, 0.017f)
    }
    // 光束中心对齐「Logo 与标题之间」（含 50dp 顶部呼吸位，见 WelcomeSection）：
    // 完整档 Logo 占 94~162dp、主标题 162~192dp → 取 148dp；
    // 精简档无 Logo 无标题，上部内容（问候语 12~26 + 信任条 38~72）→ 取 94dp。
    val center = if (full) 148f else 94f
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(if (full) 260f else 150f)
            touchEnable(false)
        }
        geometry.forEachIndexed { index, (glowHeight, inset) ->
            val alpha = peaks[index] * dim
            View {
                attr {
                    absolutePosition(top = center - glowHeight / 2f, left = 0f, right = 0f)
                    marginLeft(inset)
                    marginRight(inset)
                    height(glowHeight)
                    borderRadius(glowHeight / 2f)
                    touchEnable(false)
                    // 两端全透明 → 中点峰值，0.25/0.75 处 55% 过渡近似钟形衰减
                    backgroundLinearGradient(
                        Direction.TO_BOTTOM,
                        ColorStop(theme.brand.opacity(0f), 0f),
                        ColorStop(theme.brand.opacity(alpha * 0.55f), 0.25f),
                        ColorStop(theme.brand.opacity(alpha), 0.5f),
                        ColorStop(theme.brand.opacity(alpha * 0.55f), 0.75f),
                        ColorStop(theme.brand.opacity(0f), 1f),
                    )
                }
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
            // 入场：上滑 + 淡入，阶梯式延迟（规范 7 · 动效）。
            // percentageY = 0.28 → 卡片自身高度的 28%（58dp 卡约 16dp 上滑）。
            // 节奏（2026-09-05）：时长 0.375s（0.30 × 1.25，整体放慢 25%）；
            // 下一张在前一张进行到 25% 时启动 → 步长 = 0.375 × 0.25 ≈ 0.094s。
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
                    Animation.easeOut(0.375f).delay(0.08f + 0.094f * index),
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
                // 白色背景胶囊（2026-09-05），细描边保证落在玻璃胶囊上仍可辨。
                attr { height(26f); marginRight(7f); paddingLeft(10f); paddingRight(10f); justifyContentCenter(); backgroundColor(Color(0xFFFFFFFF)); borderRadius(13f); border(Border(0.5f, BorderStyle.SOLID, theme.divider)) }
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
            // 蒙层之上的浮层投影：让弹窗从压暗的背景里"浮"出来。
            boxShadow(BoxShadow(0f, 8f, 28f, Color(0x000000, 0.22f)))
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

/**
 * 术语对比全屏面板（灵动岛术语对比的第二只落槽后弹出，对标 ActiveComparePanel）。
 * 两列并排：术语名 + 分类 + 人话解释 + A股例子；底部 AI 解读块复用股票对比的
 * insight 状态机（股票/术语对比会话互斥，状态可安全共用）。纯词典端侧内容 +
 * AI 事实性解读，不做任何「该选哪个」的价值判断（合规文案铁律）。
 */
internal fun ViewContainer<*, *>.TermComparePanel(
    left: GlossaryEntry,
    right: GlossaryEntry,
    theme: StockChatTheme,
    insightLoading: () -> Boolean = { false },
    insightText: () -> String = { "" },
    insightError: () -> String = { "" },
    onRetryInsight: () -> Unit = {},
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
            boxShadow(BoxShadow(0f, 8f, 28f, Color(0x000000, 0.22f)))
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text("术语对比")
                    fontSize(12f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                    flex(1f)
                }
            }
            Text { attr { text("退出"); fontSize(11f); color(theme.textSecondary) } }
            event { click { onClose() } }
        }
        View {
            attr { marginTop(8f); flexDirectionRow() }
            TermCompareColumn(left, theme)
            View { attr { width(8f) } }
            TermCompareColumn(right, theme)
        }
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
                        insightLoading() -> "正在生成两个概念的区别与联系..."
                        insightError().isNotBlank() -> insightError()
                        insightText().isNotBlank() -> insightText()
                        else -> "等待第二个术语完成对比"
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

private fun ViewContainer<*, *>.TermCompareColumn(
    entry: GlossaryEntry,
    theme: StockChatTheme,
) {
    View {
        attr {
            flex(1f)
            padding(9f)
            borderRadius(10f)
            backgroundColor(theme.surfaceMuted)
        }
        Text { attr { text(entry.term); fontSize(13f); fontWeightBold(); color(theme.textPrimary) } }
        View {
            attr {
                marginTop(4f)
                paddingLeft(6f)
                paddingRight(6f)
                paddingTop(2f)
                paddingBottom(2f)
                alignSelfFlexStart()
                borderRadius(6f)
                backgroundColor(theme.brandSoft)
            }
            Text { attr { text(entry.category.label); fontSize(8.5f); fontWeightMedium(); color(theme.term) } }
        }
        Text {
            attr {
                text(entry.plain)
                marginTop(7f)
                fontSize(11f)
                lineHeight(16f)
                color(theme.textPrimary)
            }
        }
        Text {
            attr {
                text("例 ${entry.example}")
                marginTop(6f)
                fontSize(9.5f)
                lineHeight(14f)
                color(theme.textSecondary)
            }
        }
    }
}
