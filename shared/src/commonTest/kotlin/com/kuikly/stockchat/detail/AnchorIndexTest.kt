package com.kuikly.stockchat.detail

import com.kuikly.stockchat.detail.domain.AnchorIndex
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

    @Test
    fun clampedMappingCoversNonSessionTimes() {
        // 交易时段内与严格版一致
        assertEquals(0, AnchorIndex.timeStringToIndexClamped("09:30"))
        assertEquals(13, AnchorIndex.timeStringToIndexClamped("09:43"))
        assertEquals(119, AnchorIndex.timeStringToIndexClamped("11:30"))
        assertEquals(120, AnchorIndex.timeStringToIndexClamped("13:00"))
        assertEquals(239, AnchorIndex.timeStringToIndexClamped("15:00"))
        // 盘前发布 → 开盘位（东财资讯常见 07:xx/08:xx 发布）
        assertEquals(0, AnchorIndex.timeStringToIndexClamped("07:30"))
        assertEquals(0, AnchorIndex.timeStringToIndexClamped("09:29"))
        // 午休发布 → 早盘末位
        assertEquals(119, AnchorIndex.timeStringToIndexClamped("11:45"))
        assertEquals(119, AnchorIndex.timeStringToIndexClamped("12:00"))
        // 盘后/晚间发布 → 尾盘位（东财 Art_ShowTime 多为盘后，落旗主路径）
        assertEquals(239, AnchorIndex.timeStringToIndexClamped("15:01"))
        assertEquals(239, AnchorIndex.timeStringToIndexClamped("16:30"))
        assertEquals(239, AnchorIndex.timeStringToIndexClamped("21:35"))
        assertEquals(239, AnchorIndex.timeStringToIndexClamped("23:59"))
        // 非法串仍返回 null
        assertNull(AnchorIndex.timeStringToIndexClamped("25:00"))
        assertNull(AnchorIndex.timeStringToIndexClamped("ab:cd"))
        assertNull(AnchorIndex.timeStringToIndexClamped("13:60"))
        assertNull(AnchorIndex.timeStringToIndexClamped("0930"))
    }
}
