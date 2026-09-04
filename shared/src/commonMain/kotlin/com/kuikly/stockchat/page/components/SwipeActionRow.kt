package com.kuikly.stockchat.page.components

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 单个滑动动作。
 *
 * [label] 必须写成**动作结果**而不是**状态名**（"设核心" 而非 "分组"）：动作层被
 * 内容层压在下面，用户在滑开之前看不到当前值，文案必须能自解释点击会发生什么。
 */
data class SwipeAction(
    val id: String,
    val label: String,
    val background: Color,
    val foreground: Color,
    val width: Float = SwipeRow.ACTION_WIDTH,
)

/** 左滑行的几何与手感常量。 */
object SwipeRow {
    /** 单个动作宽度（dp）。两个动作 144dp，约占 375pt 屏的 38%，未越过 40% 的舒适上限。 */
    const val ACTION_WIDTH = 72f

    /**
     * 松手吸附阈值：取「总宽 45%」与「52dp 绝对下限」的**较小者**。
     *
     * 用双阈值而非单一比例，是为了让 1 个动作（阈值 32dp）和 2 个动作（阈值 52dp）
     * 的手感一致——纯比例会让单动作行轻扫即开、多动作行怎么拖都开不了。
     *
     * 不做速度判定：Kuikly commonMain 无跨平台时间源，pan 回调也不带 velocity；
     * 用 move 次数反推时长会被 60/90/120Hz 机型打乱。距离吸附本身就是 iOS 系统的
     * 原生行为，跨端表现一致。
     */
    fun snapThreshold(totalWidth: Float): Float = minOf(totalWidth * 0.45f, 52f)

    /** 超出可展开宽度后的阻尼系数，制造"到边了"的阻力感。 */
    private const val OVERSHOOT_DAMPING = 0.32f

    /** 阻尼区最大可再拖动距离（相对总宽），防止一行被拖出去大半屏。 */
    private const val OVERSHOOT_LIMIT_RATIO = 0.34f

    /** 位移压缩：正向越界按阻尼衰减，负向直接归零（本组件只允许向左滑）。 */
    internal fun damped(raw: Float, totalWidth: Float): Float {
        if (raw <= 0f) return 0f
        if (raw <= totalWidth) return raw
        val over = (raw - totalWidth) * OVERSHOOT_DAMPING
        return totalWidth + minOf(over, totalWidth * OVERSHOOT_LIMIT_RATIO)
    }
}

/**
 * 左滑操作行：内容层在上、动作层贴右压在下层，向左拖内容层露出动作。
 *
 * **状态全部外部注入**（对齐 VoiceBar / WelcomeSection 约定：组件无状态、observable
 * 只能由 Pager 持有）。不内置状态不只是风格问题——「同时只允许一行展开」需要跨行
 * 协调，若塞进组件就得把整页的行集合也传进来，反而更难维护。
 *
 * 两条踩过坑的实现约束：
 * - 注入的 observable 必须在 `attr {}` 闭包**内**调用才建立依赖，因此 offset /
 *   animating 都以 lambda 传入，不能在外层求值后再传。
 * - `animate()` 不使用 value 实参判定，它取的是 `ReactiveObserver.
 *   currentObservablePropertyKey`（即**紧邻其左侧最后读到**的 observable）。所以
 *   offset 必须在 animate 的实参位置**现场再读一次**，否则动画会绑到 animating 上。
 *   动画注册是每帧的（AnimationState.nextAnimations 每轮清空），因此跟手阶段只要
 *   不调用 animate 就是直接落位，不会拖泥带水。
 *
 * @param offset 当前行已左移距离（dp，非负）。
 * @param animating true 表示处于吸附动画（松手后），false 表示跟手拖动中。
 * @param onDragStart 手势开始；外部应在此收起其他已展开的行。
 * @param onDrag move 中上报已阻尼的位移。
 * @param onRelease end 时上报位移，由外部判定吸附到 0 还是总宽。
 * @param onTapContent 点击内容层：已展开则收起（不跳转），未展开才跳转。
 */
fun ViewContainer<*, *>.SwipeActionRow(
    theme: StockChatTheme,
    actions: List<SwipeAction>,
    totalWidth: Float = actions.sumOf { it.width.toDouble() }.toFloat(),
    offset: () -> Float,
    animating: () -> Boolean,
    reduceMotion: Boolean = false,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onTapContent: () -> Unit,
    onLongPressContent: (() -> Unit)? = null,
    onAction: (String) -> Unit,
    content: ViewContainer<*, *>.() -> Unit,
) {
    // 手势期间的瞬时量，不驱动重绘，普通局部变量即可。
    var startX = 0f
    var baseOffset = 0f

    View {
        attr {
            flexDirectionRow()
            // 内容层左移后会越过容器左边界，必须允许溢出，否则被裁掉。
            // 注意：容器因此不能设圆角（圆角会使 overflow 失效）。
            overflow(true)
        }

        // ── 动作层：贴右绝对定位，压在内容层之下 ──
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                right(0f)
                top(0f)
                bottom(0f)
                width(totalWidth)
                flexDirectionRow()
                zIndex(0, useOutline = false)
                // 圆角裁在动作层：最后一个动作的右边缘随之收圆，与内容层对齐。
                borderRadius(theme.cardRadius)
            }
            actions.forEach { action ->
                View {
                    attr {
                        width(action.width)
                        allCenter()
                        backgroundColor(action.background)
                    }
                    Text {
                        attr {
                            text(action.label)
                            fontSize(13f)
                            fontWeightSemiBold()
                            color(action.foreground)
                        }
                    }
                    event { click { onAction(action.id) } }
                }
            }
        }

        // ── 内容层：可横向拖动，覆盖在动作层之上 ──
        View {
            attr {
                flex(1f)
                zIndex(1, useOutline = false)
                // 必须不透明：层叠成立的前提是上层能遮住下层，否则动作层会透出来。
                backgroundColor(theme.surface)
                borderRadius(theme.cardRadius)
                transform(translate = Translate(0f, 0f, offsetX = -offset()))
                if (animating() && !reduceMotion) {
                    // 第二个实参现场再读一次 offset：animate() 绑定的是最后读到的属性。
                    animate(Animation.springEaseOut(0.26f, 0.86f, 0.22f), offset())
                }
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            event {
                click { onTapContent() }
                if (onLongPressContent != null) {
                    // 长按是「更多操作」的入口（置顶/改分组/移除的显式菜单），
                    // 与横向 pan 不冲突：pan 由移动触发，长按由静止计时触发。
                    longPress { onLongPressContent.invoke() }
                }
                pan { params ->
                    when (params.state) {
                        "start" -> {
                            startX = params.x
                            baseOffset = offset()
                            onDragStart()
                        }
                        "move" -> {
                            val raw = baseOffset + (startX - params.x)
                            onDrag(SwipeRow.damped(raw, totalWidth))
                        }
                        "end" -> {
                            val raw = baseOffset + (startX - params.x)
                            onRelease(SwipeRow.damped(raw, totalWidth))
                        }
                    }
                }
            }
            content()
        }
    }
}
