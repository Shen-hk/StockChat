package com.kuikly.stockchat.page.components

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.View

/**
 * 行手势常量（DetailBoardBlocks 等横向手势也复用）。
 */
object RowGesture {
    /** 拖动判定 slop（dp），与 Android ViewConfiguration 的 touch slop（约 8dp）同量级。 */
    const val AXIS_SLOP = 8f
}

/**
 * 行手势层：点击（跳详情）+ 长按（拿起/菜单入口）+ 长按拖拽排序跟手。
 * 原 SwipeActionRow（左滑动作行）于 2026-09-09 摘除动作层后瘦身保留——左滑
 * 展开按钮实测不实用，操作全部归口长按菜单（置顶/分组/理由/移除）。
 *
 * 手势范式（Android 真机验证过的两条坑，仍是本仓库 touch 手势的标准参考）：
 * - **用原生 touchDown/Move/Up 而非 pan**：pan 在 Android DOWN 时会
 *   `requestDisallowInterceptTouchEvent(true)` 锁死外层纵向 Scroller——行占满列表
 *   宽度，等于整页滚不动。touch 不做 disallow：纵向拖动被 Scroller 拦截后以
 *   touchCancel/touchUp(action=cancel) 收尾，未拿起时列表照常滚动。
 * - **内容子树不得挂任何事件**（行情卡传 `cardClickable = false`）：Android 上
 *   可触摸子 View 会吞掉整条触摸流，本层的 touch 收不到 → 长按/拖拽全部失效。
 *   点击由本层 click 承担；拖动/长按过的手势要把紧随其后的 click 吞掉
 *   （moved 标记），否则松手会被当成「轻点」误触跳详情。
 *
 * 拖拽排序只在长按「拿起」后激活（[dragActive] 返回 true；拿起拍外部已把
 * Scroller `scrollEnable(false)`，move 一定到达本层），期间纵向位移全部上报，
 * 不做横竖轴仲裁；未拿起时 move 一律不处理（纵向滚动归外层 Scroller）。
 *
 * @param onDragEnd [cancelled] = 被系统打断（回弹不落位）；否则由外部判菜单或落位。
 */
fun ViewContainer<*, *>.RowGestureLayer(
    onTapContent: () -> Unit,
    onLongPressContent: (() -> Unit)? = null,
    /** 长按拖拽排序会话中（仅被长按拿起的行返回 true）。为 true 时纵向位移走拖拽回调。 */
    dragActive: () -> Boolean = { false },
    /** 拖拽排序跟手回调：上报自拿起点的纵向位移（dp，向下为正）。 */
    onDragMove: (Float) -> Unit = {},
    onDragEnd: (dy: Float, cancelled: Boolean) -> Unit = { _, _ -> },
    content: ViewContainer<*, *>.() -> Unit,
) {
    // 手势期间的瞬时量，不驱动重绘，普通局部变量即可。
    var startX = 0f
    var startY = 0f
    /** 本手势发生过移动/长按：吞掉紧随其后的 click（click 在 touchUp 之后到达）。 */
    var moved = false
    /** 收尾幂等标记：touchUp 与 touchCancel（或 action=cancel 的 touchUp）只生效一次。 */
    var gestureDone = false
    /** 拖拽排序最近一次纵向位移（cancel 收尾时拿不到事件坐标，用最近值）。 */
    var lastDragDy = 0f

    View {
        event {
            click {
                if (!moved) onTapContent()
            }
            if (onLongPressContent != null) {
                // 长按 = 「拿起」入口（拖拽排序的起点；原地松手由外部转开菜单）。
                // 只在 start 拍触发：end 拍（松手）不能再重复拿。
                longPress { params ->
                    if (params.state == "start") {
                        // 拿起后的松手不算轻点：原地松手开菜单、拖动松手落位，
                        // 都不能让紧随的 click 再触发 tap 跳详情。
                        moved = true
                        onLongPressContent.invoke()
                    }
                }
            }
            touchDown { e ->
                startX = e.x
                startY = e.y
                moved = false
                gestureDone = false
                lastDragDy = 0f
            }
            touchMove { e ->
                if (gestureDone) return@touchMove
                // 拖拽排序会话中：纵向位移全部上报，不做横竖轴仲裁
                // （拿起点已由长按锁定——滚动在 lift 拍被关掉，move 一定到达本层）。
                if (dragActive()) {
                    moved = true
                    val dy = e.y - startY
                    lastDragDy = dy
                    onDragMove(dy)
                }
                // 未拿起：什么都不做——纵向滚动归外层 Scroller，横向无动作。
            }
            touchUp { e ->
                if (gestureDone) return@touchUp
                gestureDone = true
                if (dragActive()) {
                    moved = true
                    onDragEnd(e.y - startY, false)
                }
            }
            touchCancel { _ ->
                if (gestureDone) return@touchCancel
                gestureDone = true
                if (dragActive()) {
                    moved = true
                    onDragEnd(lastDragDy, true)
                }
            }
        }
        content()
    }
}
