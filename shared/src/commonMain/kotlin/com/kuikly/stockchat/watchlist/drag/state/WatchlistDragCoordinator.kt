package com.kuikly.stockchat.watchlist.drag.state

import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.watchlist.state.WatchlistRow
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 长按拖拽排序状态域唯一 owner（doc 47 B-4 第 2 步）：接管原 WatchlistPage 的
 * `dragSymbol / dragMotion / dragFrom / dragRefreshPending` 与整个拖拽状态机。
 *
 * 机制（逐字保留）：长按拍把 Scroller scrollEnable 关掉（KRRecyclerView.onInterceptTouchEvent
 * 首查 scrollEnabled，false 即不拦截），后续 move 留在本行 touch 上，跟手无需 pan
 * （pan 会 disallow 父级拦截、锁死列表滚动）。拖拽期间不动数据，只对让位行施加
 * ±槽距 translate（恒注册 spring，首个让位也有动画，R5）；松手**同帧落数据**
 * （applyDragOrder）：数据重排、displayList diff 与 transform 清除落在同一次
 * 渲染批里——让位行「布局移位 + transform 归零」互相抵消，被拖行 diff 为
 * Delete+Insert 直接重挂在新槽位。松手后没有任何收尾动画，卡片跟手到哪就
 * 落在哪（2026-09-09 去掉 settle 回弹两拍：松手后再播一段位移动画被实测
 * 感知为「从原位置移到落点」的闪现）。
 * dragFrom 保留为会话起点；dragMotion 携带当前目标槽位，避免位移与让位状态
 * 分两次通知，原生列表不会在两个中间态之间来回合成。
 * （左滑动作行 2026-09-09 移除：实测不实用，置顶/移除归口长按菜单。）
 */
internal class WatchlistDragCoordinator(
    private val host: DragHost,
) {
    /** 页面对拖拽域的输入视图：列表/数据域与 Store 的只读通路 + 会话收尾回调。 */
    internal interface DragHost {
        fun displayList(): ObservableList<WatchlistRow>

        /** 落位用的完整行序（rows，与 displayList 仅在过滤视图下不同；拖拽仅无过滤会话可用）。 */
        fun rows(): ObservableList<WatchlistRow>

        fun store(): WatchlistStore

        /** 无过滤会话才允许直接拖拽；过滤视图下退回开菜单（口径混乱）。 */
        fun canDragDirectly(): Boolean

        /** 原地松手 = 开长按菜单。 */
        fun openMenu(symbol: String)

        /** 会话结束/落位后补一次 displayList 重建（列表域 refreshDisplay）。 */
        fun refreshDisplay()
    }

    /** 拖拽会话中的行；空 = 无会话。 */
    var dragSymbol: String by observable("")
        private set

    /** 跟手位移与目标槽位的原子快照，避免一次 move 触发两轮不一致的渲染。 */
    var dragMotion: DragMotion by observable(DragMotion(0f, 0))
        private set

    /**
     * 拿起时/当前目标槽位（displayList 索引，仅无过滤会话可用）。
     * 会话结束后故意不复位（R5 陷阱，见类注释），由 beginDragLift 重播种。
     */
    var dragFrom: Int by observable(0)
        private set

    /** 拖拽会话期间行情到达被挂起的 displayList 重建（vfor 重建会换视图丢 touchUp）。 */
    private var dragRefreshPending: Boolean = false

    // ── 长按拖拽排序：状态机 ──

    /**
     * 让位行位移：被拖行从 [dragFrom] 挪到当前目标槽位，两行之间的行各补一个槽位
     * （±[ROW_SLOT]）。仅无过滤会话（displayList == store 顺序）下成立。
     */
    fun rowSlotShift(index: Int): Float {
        if (dragSymbol.isEmpty()) return 0f
        val target = dragMotion.target
        if (dragFrom == target) return 0f
        return when {
            target > dragFrom && index > dragFrom && index <= target -> -ROW_SLOT
            target < dragFrom && index < dragFrom && index >= target -> ROW_SLOT
            else -> 0f
        }
    }

    /** 长按拿起：锁列表滚动 + 记录槽位。过滤视图下排序口径混乱，退回直接开菜单。 */
    fun beginDragLift(symbol: String) {
        if (dragSymbol.isNotEmpty()) return
        if (!host.canDragDirectly()) {
            host.openMenu(symbol)
            return
        }
        val index = host.displayList().indexOfFirst { it.symbol == symbol }
        if (index < 0) return
        dragFrom = index
        dragMotion = DragMotion(0f, index)
        dragSymbol = symbol
    }

    /** 跟手：写实时位移，并按槽距判定目标槽位（越过相邻行中点即让位）。 */
    fun dragMove(dy: Float) {
        if (dragSymbol.isEmpty()) return
        val target = (dragFrom + (dy / ROW_SLOT).roundToInt()).coerceIn(0, host.displayList().lastIndex)
        if (dy == dragMotion.dy && target == dragMotion.target) return
        // 一个 observable 写入同时携带位移和目标，避免先画新位移/旧让位、再画
        // 旧位移/新让位的中间帧；这正是跨槽快速拖动时抖动和残影的高发路径。
        dragMotion = DragMotion(dy, target)
    }

    /**
     * 松手收尾：原地松手（未移动）= 长按操作菜单；移动过 = **同帧直接落数据**
     * （applyDragOrder：数据重排 + displayList diff + transform 清除同一渲染批，
     * 卡片跟手到哪就落在哪，无收尾动画）；被系统打断（cancel）只回弹，不开菜单。
     */
    fun dragEnd(dy: Float, cancelled: Boolean) {
        if (dragSymbol.isEmpty()) return
        val symbol = dragSymbol
        val moved = abs(dy) >= DRAG_MOVE_THRESHOLD
        if (cancelled || !moved) {
            cancelDragSession()
            if (!cancelled) host.openMenu(symbol)
            return
        }
        applyDragOrder(symbol)
    }

    /**
     * 会话终态落数据：槽位先取快照（cancel 会清状态）。落位走**增量**路径——
     * 静默落盘 + rows 原位单行移动 + refreshDisplay diffUpdate 对齐；绝不走
     * reload()（rows 整表重建对象 + clear+重加 = 整列 vfor 重挂载，放手即闪）。
     * diff 后只有被拖行 Delete+Insert（remount，内容不变不可感知），其余行 Keep。
     */
    private fun applyDragOrder(symbol: String) {
        val from = dragFrom
        val to = dragMotion.target
        // 行情回调若在拖拽期间到达，不能在落位前先 flush；否则会先绘制旧槽位，
        // 下一帧才移动数据，松手时就会出现卡片悬浮/二次落位。
        dragRefreshPending = false
        cancelDragSession(flushPending = false)
        if (to == from) {
            // 本次没有跨槽，但可能有行情刷新被拖拽会话挂起，仍需补回列表。
            host.refreshDisplay()
            return
        }
        host.store().moveToIndex(symbol, to)
        val rows = host.rows()
        val rowsIdx = rows.indexOfFirst { it.symbol == symbol }
        if (rowsIdx >= 0) {
            val row = rows.removeAt(rowsIdx)
            rows.add(to.coerceIn(0, rows.lastIndex), row)
        }
        // displayList 与新序 rows 对齐：displayList 已被上面手工移到新序，
        // diffUpdate 结果全 Keep（零视觉操作）；有挂起的行情更新也一并增量补上。
        host.refreshDisplay()
    }

    /**
     * 结束会话：清拖拽态（滚动解锁），并补一次被挂起的 displayList 重建。
     * dragMotion 的目标槽位在结束时保留到本轮渲染完成，避免清理状态时产生
     * 一个额外的让位中间帧；下一次 beginDragLift 会重新播种。
     * ⚠️ R5 硬约束：**不得重置 dragFrom / dragMotion.target**——同批次重置动画
     * 驱动 observable 会把 transform 清除过程当作动画重播（布局瞬跳 + 二次位移，
     * 2026-09-09 事故）。所有读取都以 dragSymbol 为门控。
     */
    private fun cancelDragSession(flushPending: Boolean = true) {
        dragSymbol = ""
        dragMotion = DragMotion(0f, dragMotion.target)
        if (flushPending && dragRefreshPending) {
            dragRefreshPending = false
            host.refreshDisplay()
        }
    }

    /** 会话中 displayList 重建请求转 pending（列表域 refreshDisplay 调用）。 */
    fun markRefreshPending() {
        dragRefreshPending = true
    }

    /**
     * 页面被系统手势、路由切换或原生覆盖层打断时，行未必能收到 touchCancel。
     * 主动收尾，避免遗留的 dragSymbol 让外层 Scroller 永久保持禁用。
     * 保留 dragFrom/target，仍遵守 R5 的动画注册约束。
     */
    fun cancelActiveSession() {
        if (dragSymbol.isNotEmpty()) cancelDragSession()
    }

    companion object {
        /**
         * 拖拽排序的槽距估算（行内容 ≈115 + 行距 10）。MINI 行情卡行高非严格相等
         * （异动/★ 标注行多 ~17），让位与落位按此对齐，误差最多半行内，松手落数据
         * 时由真实布局一次对齐（同帧瞬时，无动画）。改行内布局（时间线高度/标注）时同步本值。
         */
        const val ROW_SLOT = 125f

        /** 松手时位移小于该值视为「原地松手」→ 开长按菜单而非排序。 */
        const val DRAG_MOVE_THRESHOLD = 6f
    }
}

/** 跟手位移 + 目标槽位的原子快照。 */
internal data class DragMotion(val dy: Float, val target: Int)
