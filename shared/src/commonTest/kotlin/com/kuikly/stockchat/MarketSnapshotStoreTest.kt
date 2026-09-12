package com.kuikly.stockchat

import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.MarketDemoDaySynthesizer
import com.kuikly.stockchat.data.provider.MarketEventDetector
import com.kuikly.stockchat.data.provider.MarketIndex
import com.kuikly.stockchat.data.provider.MarketOverview
import com.kuikly.stockchat.data.provider.MarketSnapshotFrame
import com.kuikly.stockchat.data.provider.MarketSnapshotStore
import com.kuikly.stockchat.data.provider.SectorRank
import com.kuikly.stockchat.data.provider.SourceStamp
import com.kuikly.stockchat.data.provider.timeLabelOf
import com.kuikly.stockchat.data.provider.tradingMinuteOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketSnapshotStoreTest {

    private val stamp = SourceStamp("测试源", "10:30", com.kuikly.stockchat.data.provider.SourceTier.MARKET_DATA)

    private fun overview(rising: Int, falling: Int, limitUp: Int = 40, seal: Double = 0.8, minute: String = "10:30") = MarketOverview(
        indices = listOf(MarketIndex("000001", "上证指数", 3300.0, -0.5)),
        risingCount = rising,
        fallingCount = falling,
        flatCount = 100,
        limitUpCount = limitUp,
        limitDownCount = 5,
        sectors = listOf(SectorRank("BK1", "机器人", 2.0, 1e8, 10, 1)),
        stamp = stamp.copy(asOf = minute),
        turnoverAmount = 1_000_000_000_000.0,
        sealRate = seal,
        brokenBoardCount = 10,
        highestBoard = 4,
        northboundFlow = -3_000_000_000.0,
    )

    @Test
    fun tradingMinuteCoversBothSessionsAndFoldsLunch() {
        assertEquals(0, tradingMinuteOf(9, 30))
        assertEquals(120, tradingMinuteOf(11, 30))
        assertEquals(120, tradingMinuteOf(13, 0))
        assertEquals(240, tradingMinuteOf(15, 0))
        assertEquals(null, tradingMinuteOf(12, 30))
        assertEquals(null, tradingMinuteOf(16, 54))
    }

    @Test
    fun timeLabelRoundTripsThroughTradingMinute() {
        assertEquals("09:30", timeLabelOf(0))
        assertEquals("11:30", timeLabelOf(120))
        assertEquals("13:01", timeLabelOf(121))
        assertEquals("15:00", timeLabelOf(240))
    }

    @Test
    fun recordKeepsLatestFrameForEachMinute() {
        val store = MarketSnapshotStore()
        store.record(overview(1000, 1000), 9 * 60 + 30)
        store.record(overview(1100, 900), 9 * 60 + 30) // 同分钟：更新为最新真实快照
        store.record(overview(1200, 800), 9 * 60 + 31)
        store.record(overview(1200, 800), 12 * 60 + 30) // 午休：不入库

        val frames = store.all()
        assertEquals(2, frames.size)
        assertEquals(0, frames.first().minute)
        assertEquals(1, frames.last().minute)
        assertEquals(1100, frames.first().overview.risingCount)
    }

    @Test
    fun redPctIsDerivedFromBreadth() {
        val frame = MarketSnapshotFrame(0, overview(1500, 500))
        assertEquals(75.0, frame.redPct)
    }

    @Test
    fun demoSynthesizerProducesFullDayEndingAtBaseAnchors() {
        // 用红盘率偏高的 base（60%），使缩放后的日内摆幅仍可测
        val base = overview(3148, 2098, limitUp = 40, seal = 0.76)
        val frames = MarketDemoDaySynthesizer.synthesize(base)

        assertEquals(MarketDemoDaySynthesizer.FRAMES + 1, frames.size)
        assertEquals(0, frames.first().minute)
        assertEquals(240, frames.last().minute)
        // 每帧时间戳随帧变化（回放态 stamp 诚实）
        assertEquals("09:30", frames.first().overview.stamp.asOf)
        assertEquals("15:00", frames.last().overview.stamp.asOf)
        // 收盘帧锚定回 base：红盘率、涨停、封板率、成交额与指数涨跌幅一致
        val last = frames.last()
        val baseRed = base.risingCount * 100.0 / (base.risingCount + base.fallingCount)
        assertEquals(baseRed, last.redPct, 0.5)
        assertEquals(base.limitUpCount, last.overview.limitUpCount)
        assertEquals(base.sealRate!!, last.overview.sealRate!!, 1e-6)
        assertEquals(base.turnoverAmount!!, last.overview.turnoverAmount!!, 1.0)
        assertEquals(base.indices.first().changePercent, last.overview.indices.first().changePercent, 1e-6)
        // 盘中有波澜：红盘率最大值明显高于收盘锚点（冲高），最小值明显低于（回落）
        val reds = frames.map { it.redPct }
        assertTrue(reds.max() - reds.min() > 15.0)
    }

    @Test
    fun detectorFindsDipAndSurgeOnSynthesizedDay() {
        val base = overview(1386, 3860)
        val frames = MarketDemoDaySynthesizer.synthesize(base)
        val events = MarketEventDetector.detect(frames)

        assertTrue(events.isNotEmpty(), "合成日内应至少检测出一个事件")
        assertTrue(events.size <= 5, "L2 事件上钉上限 5")
        events.forEach { event ->
            assertTrue(event.minute in 0..240)
            assertTrue(event.fact.isNotBlank())
            assertTrue(event.segStart <= event.minute)
        }
        // 事实句必须带具体数字（「已发生事实」而非判断）
        assertTrue(events.any { it.fact.contains("%") })
    }

    @Test
    fun detectorStaysSilentOnFlatDay() {
        val store = MarketSnapshotStore()
        for (minute in 0..240 step 30) {
            store.put(MarketSnapshotFrame(minute, overview(2000, 2000, minute = timeLabelOf(minute))))
        }
        assertEquals(emptyList(), MarketEventDetector.detect(store.all()))
    }

    @Test
    fun detectorFormatsFactsWithPercentages() {
        // 构造 10 分钟内红盘率骤降 37.5pp 的序列（detect 要求 ≥3 帧）
        val store = MarketSnapshotStore()
        store.put(MarketSnapshotFrame(60, overview(3000, 1000, minute = "10:30")))
        store.put(MarketSnapshotFrame(90, overview(1500, 2500, minute = "11:00")))
        store.put(MarketSnapshotFrame(120, overview(3000, 1000, minute = "13:00")))
        val events = MarketEventDetector.detect(store.all())
        val dip = events.first { it.title == "放量跳水" }
        assertTrue(dip.fact.contains("75"))
        assertTrue(dip.fact.contains("38"))
        assertEquals(90, dip.minute)
    }
}
