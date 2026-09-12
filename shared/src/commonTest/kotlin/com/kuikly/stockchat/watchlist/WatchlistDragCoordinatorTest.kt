package com.kuikly.stockchat.watchlist

import com.kuikly.stockchat.data.WatchlistStore
import com.kuikly.stockchat.data.mock.MockQuoteProvider
import com.kuikly.stockchat.data.provider.QuoteRepository
import com.kuikly.stockchat.data.storage.InMemoryKeyValueStorage
import com.kuikly.stockchat.testing.reactive
import com.kuikly.stockchat.watchlist.drag.state.WatchlistDragCoordinator
import com.kuikly.stockchat.watchlist.state.KuiklyWatchlistScheduler
import com.kuikly.stockchat.watchlist.state.WatchlistCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 拖拽排序状态域单测（doc 47 B-4）：拿起/跟手/落位/取消，**重点锁定 R5 约束——
 * 取消会话后 dragFrom / dragMotion.target 保持陈旧值，不得被「顺手清理」**。
 * 列表层复用真实 [WatchlistCoordinator]（rows/displayList/refreshDisplay 原生链路）。
 */
class WatchlistDragCoordinatorTest {

    private class Fixture {
        val store = WatchlistStore(InMemoryKeyValueStorage())
        val quoteRepository = QuoteRepository(online = MockQuoteProvider(), offline = MockQuoteProvider())
        val insightRepository = silentInsightRepository()
        var menuOpens = 0
        var refreshes = 0
        var canDrag = true

        val listCoordinator = WatchlistCoordinator(
            host = object : WatchlistCoordinator.WatchlistHost {
                override fun watchlistStore(): WatchlistStore = store

                override fun quoteRepository(): QuoteRepository = quoteRepository

                override fun insightRepository() = this@Fixture.insightRepository

                override fun warmPrefetch(symbols: List<String>) {}
            },
            scheduler = KuiklyWatchlistScheduler(),
        )

        val coordinator: WatchlistDragCoordinator = WatchlistDragCoordinator(
            host = object : WatchlistDragCoordinator.DragHost {
                override fun displayList() = listCoordinator.displayList

                override fun rows() = listCoordinator.rows

                override fun store(): WatchlistStore = store

                override fun canDragDirectly(): Boolean = canDrag

                override fun openMenu(symbol: String) {
                    menuOpens++
                }

                override fun refreshDisplay() {
                    // 模拟页面接线：refreshDisplay 内部的 isDragging 门控指向拖拽域。
                    if (coordinator.dragSymbol.isNotEmpty()) {
                        coordinator.markRefreshPending()
                    } else {
                        listCoordinator.refreshDisplay()
                        refreshes++
                    }
                }
            },
        )

        fun seed(vararg symbols: String) {
            symbols.forEach { store.add(it, it) }
            listCoordinator.reload()
        }
    }

    @Test
    fun beginDragLiftSeedsSession() = reactive {
        val f = Fixture()
        f.seed("a", "b", "c")
        assertTrue(f.coordinator.dragSymbol.isEmpty())

        f.coordinator.beginDragLift("b")
        assertEquals("b", f.coordinator.dragSymbol)
        assertEquals(1, f.coordinator.dragFrom)
        assertEquals(0f, f.coordinator.dragMotion.dy)
        assertEquals(1, f.coordinator.dragMotion.target)

        // 会话中再拿起：忽略
        f.coordinator.beginDragLift("a")
        assertEquals("b", f.coordinator.dragSymbol)
    }

    @Test
    fun filteredViewLongPressOpensMenuInsteadOfDrag() = reactive {
        val f = Fixture()
        f.seed("a", "b")
        f.canDrag = false
        f.coordinator.beginDragLift("a")
        assertEquals("", f.coordinator.dragSymbol, "过滤视图不进入拖拽")
        assertEquals(1, f.menuOpens, "退回长按菜单")
    }

    @Test
    fun dragMoveUpdatesAtomicMotionSnapshot() = reactive {
        val f = Fixture()
        f.seed("a", "b", "c", "d")
        f.coordinator.beginDragLift("a")
        f.coordinator.dragMove(130f)
        assertEquals(130f, f.coordinator.dragMotion.dy)
        assertEquals(1, f.coordinator.dragMotion.target, "越过相邻行中点让位")
        f.coordinator.dragMove(300f)
        assertEquals(2, f.coordinator.dragMotion.target)
    }

    @Test
    fun dragEndSmallMovementOpensMenu() = reactive {
        val f = Fixture()
        f.seed("a", "b")
        f.coordinator.beginDragLift("a")
        f.coordinator.dragEnd(3f, cancelled = false)
        assertEquals(1, f.menuOpens, "原地松手 = 开菜单")
        assertEquals("", f.coordinator.dragSymbol, "会话结束")
        assertEquals(listOf("a", "b"), f.store.list().map { it.symbol }, "数据不动")
    }

    @Test
    fun dragEndAppliesOrderAndReordersRows() = reactive {
        val f = Fixture()
        f.seed("a", "b", "c")
        f.coordinator.beginDragLift("a")
        f.coordinator.dragMove(260f) // a → 槽位 2
        f.coordinator.dragEnd(260f, cancelled = false)

        assertEquals(listOf("b", "c", "a"), f.store.list().map { it.symbol }, "store 落位（moveToIndex 用删除前 lastIndex）")
        // ⚠️ 原始口径逐字保留：rows 的插桩是 removeAt 后 coerceIn(0, rows.lastIndex)，
        // 拖到最后一槽时 rows/displayList 得 [b,a,c]（与 store 暂不一致，下次 reload 对齐）。
        // 本测试锁定该既有行为；如需对齐属行为修复，须走行为变更评审，不在 B-4 内顺手改。
        assertEquals(listOf("b", "a", "c"), f.listCoordinator.displayList.map { it.symbol })
        assertEquals("", f.coordinator.dragSymbol)
        assertTrue(f.refreshes >= 1, "落位后补一次 displayList 重建")
    }

    @Test
    fun cancelDragSessionDoesNotResetDragFrom() = reactive {
        val f = Fixture()
        f.seed("a", "b", "c")
        f.coordinator.beginDragLift("a")
        val seededFrom = f.coordinator.dragFrom
        f.coordinator.dragEnd(1f, cancelled = true)

        assertEquals("", f.coordinator.dragSymbol, "会话清空")
        // ⚠️ R5 硬约束：取消时不得重置 dragFrom / dragMotion.target。
        // 同批次重置动画驱动 observable 会把 transform 清除当作动画重播
        // （布局瞬跳 + 二次位移，2026-09-09 事故）。
        assertEquals(seededFrom, f.coordinator.dragFrom, "dragFrom 保持陈旧值，由下次 beginDragLift 重播种")
        assertEquals(seededFrom, f.coordinator.dragMotion.target, "目标槽位同样保留")
        assertEquals(0f, f.coordinator.rowSlotShift(0), "dragSymbol 已空，让位位移门控归零")
        assertEquals(0, f.menuOpens, "系统打断只回弹，不开菜单")
    }

    @Test
    fun rowSlotShiftShiftsOnlyRowsBetweenFromAndTarget() = reactive {
        val f = Fixture()
        f.seed("a", "b", "c", "d")
        f.coordinator.beginDragLift("a") // from = 0
        f.coordinator.dragMove(260f) // target = 2
        assertEquals(-125f, f.coordinator.rowSlotShift(1), "1..target 之间上移一槽")
        assertEquals(-125f, f.coordinator.rowSlotShift(2))
        assertEquals(0f, f.coordinator.rowSlotShift(3), "target 之后不动")
        assertEquals(0f, f.coordinator.rowSlotShift(0), "被拖行本身不动让位")
    }

    @Test
    fun refreshPendingGateDefersDisplayRebuildDuringSession() = reactive {
        val f = Fixture()
        f.seed("a", "b")
        f.coordinator.beginDragLift("a")
        f.coordinator.markRefreshPending()
        assertEquals(0, f.refreshes, "会话中重建被挂起")

        f.coordinator.dragEnd(1f, cancelled = true)
        assertTrue(f.refreshes >= 1, "会话结束补一次挂起的重建")
        assertFalse(f.coordinator.dragSymbol.isNotEmpty())
    }
}
