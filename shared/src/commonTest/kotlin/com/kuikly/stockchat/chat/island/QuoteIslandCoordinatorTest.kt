package com.kuikly.stockchat.chat.island

import com.kuikly.stockchat.chat.island.state.IslandEffect
import com.kuikly.stockchat.chat.island.state.IslandGesturePhase
import com.kuikly.stockchat.chat.island.state.IslandHostPort
import com.kuikly.stockchat.chat.island.state.IslandScheduledTask
import com.kuikly.stockchat.chat.island.state.IslandScheduler
import com.kuikly.stockchat.chat.island.state.PlainIslandState
import com.kuikly.stockchat.chat.island.state.QuoteIslandCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuoteIslandCoordinatorTest {
    @Test
    fun toggleExpandsAndRequestsQuote() {
        val f = fixture()

        f.coordinator.toggle()

        assertTrue(f.state.expanded)
        assertEquals(IslandGesturePhase.IDLE, f.state.motion.phase)
        assertEquals<List<IslandEffect>>(
            listOf(IslandEffect.RequestQuote("600519.SH")),
            f.effects.toList(),
        )
    }

    @Test
    fun swipeUpPastCloseThresholdSettlesClosedThenUnmounts() {
        val f = fixture()
        f.coordinator.toggle()
        f.scheduler.run(400) // clear the toggle animation gate
        assertTrue(f.state.expanded)
        f.effects.clear()

        f.coordinator.onPan("start", 0f)
        assertEquals(IslandGesturePhase.DRAGGING, f.state.motion.phase)
        f.coordinator.onPan("move", -20f)
        f.coordinator.onPan("end", -20f)

        // deltaY = -20 ≤ -16 → settle closed (island settle is silent on haptics).
        assertEquals(IslandGesturePhase.CLOSING, f.state.motion.phase)
        assertEquals(-104f, f.state.motion.offsetY)
        assertTrue(f.effects.isEmpty(), "island settle emits no effect, got ${f.effects}")

        // The 560ms fallback completes the close.
        f.scheduler.run(560)
        assertFalse(f.state.expanded)
        assertEquals(IslandGesturePhase.IDLE, f.state.motion.phase)
    }

    @Test
    fun swipeDownPastDetailThresholdHandsOffToDetail() {
        val f = fixture()
        f.coordinator.toggle()
        f.scheduler.run(400)
        f.effects.clear()

        f.coordinator.onPan("start", 0f)
        f.coordinator.onPan("move", 30f)
        f.coordinator.onPan("end", 30f)

        assertEquals(IslandGesturePhase.OPENING_DETAIL, f.state.motion.phase)
        // 160ms main trigger fires the detail handoff route.
        f.scheduler.run(160)
        assertTrue(
            f.effects.any { it is IslandEffect.OpenStockDetail && it.symbol == "600519.SH" },
            "expected OpenStockDetail handoff, got ${f.effects}",
        )
    }

    @Test
    fun idleIslandAutoCollapsesAfterTwoSeconds() {
        val f = fixture()
        f.coordinator.toggle()
        assertTrue(f.state.expanded)

        f.scheduler.run(400)     // animation gate clears
        f.scheduler.run(2_000)   // auto-collapse countdown

        assertFalse(f.state.expanded)
    }

    @Test
    fun toggleWhileTermLobbyVisibleExitsComparisonInsteadOfCollapsing() {
        val f = fixture()
        f.coordinator.addDraggedTerm("pe_ratio")
        assertTrue(f.coordinator.isTermLobbyVisible())
        assertTrue(f.state.expanded)

        f.coordinator.toggle()

        assertFalse(f.coordinator.isTermLobbyVisible())
        assertFalse(f.state.expanded)
    }

    @Test
    fun secondStockDropOpensCompareLobby() {
        val f = fixture()
        f.coordinator.addDraggedStock("600519.SH")
        f.coordinator.addDraggedStock("000001.SZ")

        assertTrue(f.coordinator.isCompareLobbyVisible())
        assertTrue(f.state.expanded)
    }

    /**
     * 回归守护（2026-09-12「进入对比态后岛样式不切换」）：
     * `isCompareLobbyVisible()` / `isTermLobbyVisible()` 是 DSL 在 attr {} 闭包里直接调用的
     * 查询，必须读 StatePort 的 observable。若改回读协调器的内部镜像字段，attr 不会注册
     * 反应式依赖、不重跑 —— 对比面板/槽位/AI 解读照常工作，唯独岛身停在行情卡样式。
     *
     * 本用例只写 `state`、不经过任何协调器命令（内部镜像保持在默认值），因此它是唯一能
     * 区分「读 state」与「读镜像」两种实现的断言：读镜像时必然失败。
     */
    @Test
    fun lobbyQueriesReadStateObservablesNotInternalMirrors() {
        val f = fixture()
        assertFalse(f.coordinator.isCompareLobbyVisible())
        assertFalse(f.coordinator.isTermLobbyVisible())

        f.state.compareVisible = true
        f.state.compareLeftSymbol = "600519.SH"
        assertTrue(
            f.coordinator.isCompareLobbyVisible(),
            "isCompareLobbyVisible() must read state (DSL calls it inside attr; mirrors break reactivity)",
        )

        f.state.termCompareVisible = true
        f.state.termCompareLeftKey = "pe_ratio"
        assertTrue(
            f.coordinator.isTermLobbyVisible(),
            "isTermLobbyVisible() must read state (DSL calls it inside attr; mirrors break reactivity)",
        )
    }

    private fun fixture(): Fixture {
        val state = PlainIslandState()
        val scheduler = FakeIslandScheduler()
        val effects = mutableListOf<IslandEffect>()
        val host = object : IslandHostPort {
            override fun isPageVisible() = true
            override fun pageHeight() = 800f
            override fun hasCompareCard() = false
        }
        return Fixture(
            state,
            scheduler,
            effects,
            QuoteIslandCoordinator(state, host, scheduler, effects::add),
        )
    }

    private data class Fixture(
        val state: PlainIslandState,
        val scheduler: FakeIslandScheduler,
        val effects: MutableList<IslandEffect>,
        val coordinator: QuoteIslandCoordinator,
    )
}

private class FakeIslandScheduler : IslandScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): IslandScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return IslandScheduledTask { entry.cancelled = true }
    }

    fun run(delay: Int) {
        entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}
