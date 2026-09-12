package com.kuikly.stockchat.chat.entity

import com.kuikly.stockchat.chat.entity.state.EntityEffect
import com.kuikly.stockchat.chat.entity.state.EntityHostPort
import com.kuikly.stockchat.chat.entity.state.EntityInteractionCoordinator
import com.kuikly.stockchat.chat.entity.state.EntityScheduledTask
import com.kuikly.stockchat.chat.entity.state.EntityScheduler
import com.kuikly.stockchat.chat.entity.state.PlainEntityState
import com.kuikly.stockchat.richtext.EntityDropTarget
import com.kuikly.stockchat.richtext.EntitySpan
import com.kuikly.stockchat.richtext.EntityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityInteractionCoordinatorTest {
    @Test
    fun stationaryLongPressPreviewsQuoteWithoutStartingDrag() {
        val f = fixture()

        f.coordinator.onStockLongPress(stockSpan(), "start", isCancel = false, pageX = 100f, pageY = 500f)

        assertFalse(f.state.dragActive, "静止长按不应进入拖拽态")
        assertEquals("名称-600519.SH", f.state.dragName)
        assertEquals(EntityDropTarget.NONE, f.state.dropTarget)
        assertTrue(
            f.effects.any { it is EntityEffect.OpenStockIsland && it.symbol == "600519.SH" },
            "expected OpenStockIsland preview, got ${f.effects}",
        )
        assertTrue(EntityEffect.Haptic in f.effects)
        assertTrue(f.effects.any { it is EntityEffect.Track && it.event == "entity_hold_preview" })
    }

    @Test
    fun movePastThresholdStartsDrag() {
        val f = fixture()
        val span = stockSpan()
        f.coordinator.onStockLongPress(span, "start", isCancel = false, pageX = 100f, pageY = 500f)
        f.effects.clear()

        // 位移 20dp ≥ EntityDropResolver.DRAG_THRESHOLD(10f)
        f.coordinator.onStockLongPress(span, "move", isCancel = false, pageX = 100f, pageY = 480f)

        assertTrue(f.state.dragActive)
        assertTrue(EntityEffect.Haptic in f.effects)
        assertTrue(
            f.effects.any { it is EntityEffect.Track && it.event == "entity_drag_start" },
            "expected entity_drag_start track, got ${f.effects}",
        )
    }

    @Test
    fun subThresholdMoveKeepsPreviewGesture() {
        val f = fixture()
        val span = stockSpan()
        f.coordinator.onStockLongPress(span, "start", isCancel = false, pageX = 100f, pageY = 500f)
        f.effects.clear()

        f.coordinator.onStockLongPress(span, "move", isCancel = false, pageX = 103f, pageY = 503f)

        assertFalse(f.state.dragActive, "位移 4.2dp 未过 10dp 阈值")
        assertTrue(f.effects.isEmpty(), "阈值内不应产生效果，got ${f.effects}")
    }

    @Test
    fun droppingOnIslandAddsStockToCompare() {
        val f = fixture()
        val span = stockSpan()
        f.coordinator.onStockLongPress(span, "start", isCancel = false, pageX = 100f, pageY = 500f)
        // 岛捕获区 = statusBarHeight(47) .. 47+86 = 133（未展开）
        f.coordinator.onStockLongPress(span, "move", isCancel = false, pageX = 100f, pageY = 60f)
        assertEquals(EntityDropTarget.ISLAND, f.state.dropTarget)

        f.effects.clear()
        f.coordinator.onStockLongPress(span, "end", isCancel = false, pageX = 100f, pageY = 60f)

        assertTrue(
            f.effects.any { it is EntityEffect.AddStockToIsland && it.symbol == "600519.SH" },
            "expected AddStockToIsland, got ${f.effects}",
        )
        assertTrue(f.effects.any { it is EntityEffect.Track && it.event == "entity_drag_drop" })
        // 拖拽结束后拖拽字段全部复位
        assertFalse(f.state.dragActive)
        assertEquals(EntityDropTarget.NONE, f.state.dropTarget)
        assertEquals(null, f.state.draggedEntity)
    }

    @Test
    fun droppingOnComposerInjectsMention() {
        val f = fixture()
        val span = stockSpan()
        f.coordinator.onStockLongPress(span, "start", isCancel = false, pageX = 100f, pageY = 500f)
        // 输入栏捕获区：composerTop = 844 - 34 - 0 - 128 = 682
        f.coordinator.onStockLongPress(span, "move", isCancel = false, pageX = 100f, pageY = 750f)
        assertEquals(EntityDropTarget.COMPOSER, f.state.dropTarget)

        f.effects.clear()
        f.coordinator.onStockLongPress(span, "end", isCancel = false, pageX = 100f, pageY = 750f)

        assertTrue(
            f.effects.any { it is EntityEffect.InjectStockMention && it.symbol == "600519.SH" },
            "expected InjectStockMention, got ${f.effects}",
        )
    }

    @Test
    fun termDroppedOnComposerInjectsQuestion() {
        val f = fixture()
        val span = termSpan()
        f.coordinator.onTermLongPress(span, "start", isCancel = false, pageX = 100f, pageY = 500f)
        f.coordinator.onTermLongPress(span, "move", isCancel = false, pageX = 100f, pageY = 750f)

        f.effects.clear()
        f.coordinator.onTermLongPress(span, "end", isCancel = false, pageX = 100f, pageY = 750f)

        assertTrue(
            f.effects.any { it is EntityEffect.InjectQuestion && it.text == "术语-pe_ratio 是什么意思" },
            "expected InjectQuestion, got ${f.effects}",
        )
        assertTrue(EntityEffect.Haptic in f.effects)
    }

    @Test
    fun termDragReleaseAllowsAnotherLongPressOnTheSameTerm() {
        val f = fixture()
        val span = termSpan()

        f.coordinator.onTermLongPress(span, "start", isCancel = false, pageX = 100f, pageY = 500f)
        f.coordinator.onTermLongPress(span, "move", isCancel = false, pageX = 100f, pageY = 750f)
        f.coordinator.onTermLongPress(span, "end", isCancel = false, pageX = 100f, pageY = 750f)
        assertFalse(f.state.dragActive)

        f.effects.clear()
        f.coordinator.onTermLongPress(span, "start", isCancel = false, pageX = 100f, pageY = 500f)

        assertEquals(span, f.state.draggedEntity, "拖拽收敛不能让同一术语永久卡在 pending 状态")
        assertTrue(
            f.effects.any { it is EntityEffect.OpenTermIsland && it.key == span.target },
            "第二次长按应重新进入术语预览，got ${f.effects}",
        )
    }

    @Test
    fun ambiguousEntityDefersActionUntilChosen() {
        val f = fixture()
        val span = stockSpan(target = "600519.SH", candidates = listOf("600519.SH", "000001.SZ"))

        f.coordinator.onStockClick(span)

        assertTrue(f.effects.isEmpty(), "二义实体不应立即执行动作，got ${f.effects}")
        assertEquals("贵州茅台", f.state.ambiguousText)
        assertEquals(listOf("600519.SH", "000001.SZ"), f.state.ambiguousSymbols)

        f.coordinator.chooseAmbiguous("000001.SZ")

        assertTrue(
            f.effects.any { it is EntityEffect.OpenStockDetail && it.symbol == "000001.SZ" },
            "expected OpenStockDetail for the chosen candidate, got ${f.effects}",
        )
    }

    @Test
    fun longPressSuppressesTheBridgeFollowUpClickOnce() {
        val f = fixture()
        f.coordinator.onTermLongPress(termSpan(), "start", isCancel = false, pageX = 100f, pageY = 500f)

        assertTrue(f.coordinator.consumeTermClickSuppression("市盈率"), "补发的 click 应被消耗")
        assertFalse(f.coordinator.consumeTermClickSuppression("市盈率"), "抑制只生效一次")
    }

    @Test
    fun peekPresentsAfterOneFrameThenClearsOnDismiss() {
        val f = fixture()

        f.coordinator.showQuote("600519.SH")
        assertEquals("600519.SH", f.state.peekSymbol)
        assertFalse(f.state.peekVisible, "首帧不应可见（R4：挂载后一拍再呈现）")
        assertTrue(f.effects.any { it is EntityEffect.RequestQuote && it.symbol == "600519.SH" })

        f.scheduler.run(16)
        assertTrue(f.state.peekVisible)

        f.coordinator.dismissPeek()
        assertFalse(f.state.peekVisible)
        f.scheduler.run(180)
        assertEquals("", f.state.peekSymbol)
    }

    private fun fixture(): Fixture {
        val state = PlainEntityState()
        val scheduler = FakeEntityScheduler()
        val effects = mutableListOf<EntityEffect>()
        val host = object : EntityHostPort {
            override fun pageWidth() = 390f
            override fun pageHeight() = 844f
            override fun statusBarHeight() = 47f
            override fun safeAreaBottom() = 34f
            override fun keyboardHeight() = 0f
            override fun isIslandExpanded() = false
            override fun displayName(symbol: String, fallback: String) = "名称-$symbol"
            override fun termName(key: String) = "术语-$key"
        }
        return Fixture(
            state,
            scheduler,
            effects,
            EntityInteractionCoordinator(state, host, scheduler, effects::add),
        )
    }

    private fun stockSpan(
        target: String = "600519.SH",
        candidates: List<String> = listOf(target),
    ) = EntitySpan(
        start = 0,
        endExclusive = 4,
        text = "贵州茅台",
        type = EntityType.STOCK,
        target = target,
        candidates = candidates,
    )

    private fun termSpan(key: String = "pe_ratio") = EntitySpan(
        start = 0,
        endExclusive = 3,
        text = "市盈率",
        type = EntityType.TERM,
        target = key,
        candidates = listOf(key),
    )

    private data class Fixture(
        val state: PlainEntityState,
        val scheduler: FakeEntityScheduler,
        val effects: MutableList<EntityEffect>,
        val coordinator: EntityInteractionCoordinator,
    )
}

private class FakeEntityScheduler : EntityScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): EntityScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return EntityScheduledTask { entry.cancelled = true }
    }

    fun run(delay: Int) {
        entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}
