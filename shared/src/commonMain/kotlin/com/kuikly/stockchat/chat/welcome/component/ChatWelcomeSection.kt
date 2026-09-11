package com.kuikly.stockchat.chat.welcome.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.fontSizeScaled
import com.kuikly.stockchat.page.components.LineIconTrendUp
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.AccessibilityRole
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
                    fontSizeScaled(22f)
                    fontWeightBold()
                    color(theme.textPrimary)
                }
            }
            Text {
                attr {
                    // 必须在 attr 内部调用取值闭包，否则打字机文案不会重绘。
                    text(rotatingKeyword())
                    fontSizeScaled(22f)
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
        // 【2026-09-11 暂时下线】下方引导语 + 示例卡，恢复时取消注释即可：
        // Text {
        //     attr {
        //         text("可以这样问")
        //         alignSelfFlexStart()
        //         marginTop(theme.spacing.xl)
        //         fontSize(theme.type.meta)
        //         fontWeightSemiBold()
        //         color(theme.textTertiary)
        //     }
        // }
        // defaultWelcomeStarters().forEachIndexed { index, starter ->
        //     QuestionStarterCard(starter, theme, index, entranceVisible, reduceMotion, onChoose)
        // }
    }
}

/** 主题句上方的品牌图标（82dp brand 圆角方块 + 趋势线，2026-09-10 放大 20%）。装饰元素，读屏跳过。 */
private fun ViewContainer<*, *>.WelcomeBadge(theme: StockChatTheme) {
    View {
        attr {
            size(82f, 82f)
            marginBottom(16f)
            allCenter()
            borderRadius(20f)
            backgroundColor(theme.brand)
            accessibilityRole(AccessibilityRole.NONE)
        }
        LineIconTrendUp(color = theme.onBrand, size = 44f)
    }
}

/**
 * 问AI / 看行情 切换（2026-09-11 整体缩小 15%：194×46 / 字号 15.3）。
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
            marginTop(20f)
            size(194f, 46f)
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(theme.surfaceMuted)
            borderRadius(12f)
        }
        // 滑块：默认停在「问AI」半格（(194-6)/2 = 94），选中看市场时滑到右半格。
        View {
            attr {
                absolutePosition(left = 3f, top = 3f)
                size(94f, 41f)
                borderRadius(10f)
                backgroundColor(theme.surface)
                touchEnable(false)
                // 取值闭包必须在 attr 内现场调用才建立依赖；translate 用 offsetX=px。
                val marketSelected = marketTabSelected()
                transform(translate = Translate(0f, 0f, offsetX = if (marketSelected) 94f else 0f))
                // 无条件注册（R2/R5）：animate 绑定最后读到的 marketTabSelected()，
                // 翻转周期消费上轮注册的 easeOut，滑动先快后慢。
                animate(Animation.easeOut(0.22f), marketTabSelected())
            }
        }
        View {
            attr {
                height(35f)
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
                    fontSizeScaled(15.3f)
                    fontWeightSemiBold()
                    color(theme.textPrimary)
                }
            }
        }
        View {
            attr {
                height(35f)
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
                    fontSizeScaled(15.3f)
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

/**
 * 引导卡（2026-09-08 改版）：玻璃小卡——只保留主问题，去掉图标/副文案/箭头。
 * 收缩为自适应宽度的紧凑卡片（不再 alignSelfStretch 占满整行），
 * 玻璃质感 = 半透明 surface + 1px 描边 + 大圆角；入场阶梯动画逻辑不变。
 */
private fun ViewContainer<*, *>.QuestionStarterCard(
    starter: WelcomeStarter,
    theme: StockChatTheme,
    index: Int,
    entranceVisible: () -> Boolean,
    reduceMotion: Boolean,
    onChoose: (WelcomeStarter) -> Unit,
) {
    View {
        attr {
            // 外层是 alignItemsCenter，必须显式改回左对齐；
            // 宽度随文案自适应，四张卡左缘对齐、长短错落成阶梯感。
            alignSelfFlexStart()
            marginTop(theme.spacing.sm)
            paddingLeft(15f)
            paddingRight(15f)
            paddingTop(9f)
            paddingBottom(9f)
            backgroundColor(theme.surface.opacity(0.72f))
            borderRadius(12f)
            border(Border(1f, BorderStyle.SOLID, theme.divider))
            // 无障碍（规范 §6.5）：朗读「示例问题：…」整句；
            // 按钮语义让读屏播报「点按两次即可激活」。
            accessibility("示例问题：${starter.question}")
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
        Text {
            attr {
                text(starter.question)
                // 2026-09-10 放大 10%：14 → 15.4。
                fontSizeScaled(15.4f)
                fontWeightMedium()
                color(theme.textPrimary)
            }
        }
        event { click { onChoose(starter) } }
    }
}
