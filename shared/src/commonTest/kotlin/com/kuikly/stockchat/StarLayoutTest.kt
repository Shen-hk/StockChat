package com.kuikly.stockchat

import com.kuikly.stockchat.page.risk.StarLayout
import com.kuikly.stockchat.page.risk.StarMemberIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 风险星图纯几何布局单测（doc 32 §6.1：StarLayout 11 用例口径——
 * 确定性、无随机数无时间源，同输入恒同输出）。
 */
class StarLayoutTest {
    private fun member(symbol: String, name: String, industry: String) =
        StarMemberIn(symbol, name, industry)

    private val members = listOf(
        member("a", "甲股", "算力"),
        member("b", "乙股", "算力"),
        member("c", "丙股", "算力"),
        member("d", "丁股", "白酒"),
        member("e", "戊股", "白酒"),
        member("f", "己股", "电力"),
    )

    @Test
    fun layoutIsDeterministic() {
        assertEquals(StarLayout.layout(members, 360f), StarLayout.layout(members, 360f))
    }

    @Test
    fun clustersGroupByIndustryAndSortBySize() {
        val g = StarLayout.layout(members, 360f)
        assertEquals(3, g.clusters.size)
        assertEquals(3, g.clusters[0].memberCount)
        assertEquals("算力", g.clusters[0].name)
        assertEquals(2, g.clusters[1].memberCount)
        assertEquals(1, g.clusters[2].memberCount)
    }

    @Test
    fun everyMemberBecomesExactlyOneStarInOwnCluster() {
        val g = StarLayout.layout(members, 360f)
        assertEquals(members.size, g.stars.size)
        assertEquals(members.map { it.symbol }, g.stars.map { it.symbol })
        g.stars.forEach { star ->
            assertEquals(g.clusters[star.clusterIndex].name, star.industry)
        }
    }

    @Test
    fun starsStayInsideTheirClusterCircle() {
        val g = StarLayout.layout(members, 360f)
        g.stars.forEach { star ->
            val c = g.clusters[star.clusterIndex]
            val dx = star.x - c.cx
            val dy = star.y - c.cy
            assertTrue(
                dx * dx + dy * dy <= c.radius * c.radius + 0.5f,
                "星 ${star.symbol} 应落在团域圈内",
            )
        }
    }

    @Test
    fun firstMemberSitsAtTopOfRing() {
        val g = StarLayout.layout(members, 360f)
        val first = g.stars.first()
        val c = g.clusters[first.clusterIndex]
        assertEquals(c.cx, first.x, 0.5f)
        assertTrue(first.y < c.cy, "首成员应在团心上方（-90° 起始）")
    }

    @Test
    fun singleMemberStarFallsAtClusterCenter() {
        val g = StarLayout.layout(listOf(member("x", "独苗", "电力")), 360f)
        assertEquals(1, g.stars.size)
        val c = g.clusters[0]
        assertEquals(c.cx, g.stars[0].x, 0.5f)
        assertEquals(c.cy, g.stars[0].y, 0.5f)
    }

    @Test
    fun rowWrappingKeepsClustersWithinCanvasWidth() {
        val many = (0 until 10).map { member("s$it", "股$it", "行业${it / 2}") }
        val g = StarLayout.layout(many, 320f)
        g.clusters.forEach { c ->
            assertTrue(c.cx - c.radius >= -1f && c.cx + c.radius <= 321f, "团域应落在画布宽度内")
        }
    }

    @Test
    fun lookupCorrelationIsBidirectional() {
        val cors = mapOf("a|b" to 0.8)
        assertEquals(0.8, StarLayout.lookupCorrelation(cors, "a", "b"))
        assertEquals(0.8, StarLayout.lookupCorrelation(cors, "b", "a"))
        assertNull(StarLayout.lookupCorrelation(cors, "a", "c"))
        assertNull(StarLayout.lookupCorrelation(cors, "a", "a"))
    }

    @Test
    fun memberRingRadiusMeetsSpacingFloor() {
        assertEquals(0f, StarLayout.memberRingRadius(1))
        assertEquals(40f, StarLayout.memberRingRadius(2))
        // 环半径应随成员数不减。
        assertTrue(StarLayout.memberRingRadius(5) >= StarLayout.memberRingRadius(3))
    }

    @Test
    fun haloRadiusReflectsVolatilityRatio() {
        assertEquals(0f, StarLayout.haloRadiusDp(null))
        assertEquals(0f, StarLayout.haloRadiusDp(-1.0))
        assertTrue(StarLayout.haloRadiusDp(2.0) > StarLayout.haloRadiusDp(0.5))
        // 超界倍率收口（0.5~2.5 线性映射 10~28dp）。
        assertEquals(StarLayout.haloRadiusDp(2.5), StarLayout.haloRadiusDp(9.9))
    }

    @Test
    fun linkWidthOnlyAboveHalfCorrelation() {
        assertEquals(0f, StarLayout.linkWidthDp(0.3))
        assertEquals(0f, StarLayout.linkWidthDp(-0.49))
        assertTrue(StarLayout.linkWidthDp(0.5) > 0f)
        assertTrue(StarLayout.linkWidthDp(0.9) > StarLayout.linkWidthDp(0.6))
        // 负相关与正相关同宽（强度口径）。
        assertEquals(StarLayout.linkWidthDp(0.8), StarLayout.linkWidthDp(-0.8))
    }
}
