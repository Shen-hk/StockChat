package com.kuikly.stockchat.detail

import com.kuikly.stockchat.page.detail.AnchorIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * AnchorIndex 单测（doc 29 §4.4 ④ / §4.3 B2 验收「旗位置与时间轴一致」）。
 * 纯 JVM 单测，不依赖 Kuikly 运行时。
 */
class AnchorIndexTest {

    @Test
    fun indexCountIs240() {
        assertEquals(240, AnchorIndex.INDEX_COUNT)
    }

    @Test
    fun morningBoundaries() {
        assertEquals(0, AnchorIndex.timeStringToIndex("09:30"))
        // 11:30 收在早盘末位 119
        assertEquals(119, AnchorIndex.timeStringToIndex("11:30"))
    }

    @Test
    fun afternoonBoundaries() {
        assertEquals(120, AnchorIndex.timeStringToIndex("13:00"))
        // 15:00 收在午盘末位 239
        assertEquals(239, AnchorIndex.timeStringToIndex("15:00"))
    }

    @Test
    fun lunchAndInvalidReturnNull() {
        // 午休（含 12:00）一律 null，符合 doc 29「跨日新闻置灰」
        assertNull(AnchorIndex.timeStringToIndex("12:00"))
        assertNull(AnchorIndex.timeStringToIndex("11:45"))
        assertNull(AnchorIndex.timeStringToIndex("15:01"))
        assertNull(AnchorIndex.timeStringToIndex("09:29"))
        assertNull(AnchorIndex.timeStringToIndex("25:00"))
        assertNull(AnchorIndex.timeStringToIndex("ab:cd"))
        assertNull(AnchorIndex.timeStringToIndex("13:60"))
    }

    @Test
    fun inverseLabelRoundTrip() {
        // 均匀 1 点/分钟映射：13:35 -> 155，索引 155 -> 13:35
        assertEquals(155, AnchorIndex.timeStringToIndex("13:35"))
        assertEquals("13:35", AnchorIndex.indexToTimeLabel(155))
        // 早盘 09:43 -> 13
        assertEquals(13, AnchorIndex.timeStringToIndex("09:43"))
        assertEquals("09:43", AnchorIndex.indexToTimeLabel(13))
        // 末位互逆
        assertEquals(119, AnchorIndex.timeStringToIndex("11:30"))
        assertEquals("11:29", AnchorIndex.indexToTimeLabel(119))
        assertEquals(239, AnchorIndex.timeStringToIndex("15:00"))
        assertEquals("14:59", AnchorIndex.indexToTimeLabel(239))
    }

    @Test
    fun minutesSinceOpenMapping() {
        assertEquals(0, AnchorIndex.minutesSinceOpenToIndex(0))      // 09:30
        assertEquals(120, AnchorIndex.minutesSinceOpenToIndex(120))  // 13:00
        assertEquals(239, AnchorIndex.minutesSinceOpenToIndex(240))  // 15:00 夹紧
        assertEquals(239, AnchorIndex.minutesSinceOpenToIndex(239))
        assertNull(AnchorIndex.minutesSinceOpenToIndex(-1))
        assertNull(AnchorIndex.minutesSinceOpenToIndex(241))
    }

    @Test
    fun outOfRangeIndexLabelEmpty() {
        assertEquals("", AnchorIndex.indexToTimeLabel(-1))
        assertEquals("", AnchorIndex.indexToTimeLabel(240))
    }
}
