package com.kuikly.stockchat.page.risk

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 风险星图纯几何布局（doc 32 §6.1）：无随机数、无时间源，同输入恒同输出，可确定性单测。
 *
 * 只负责「星放哪里 / 团多大 / 线多粗 / 刷子怎么命中」这类纯映射；
 * 绘制与手势在 `RiskSkyChart`（Kuikly Canvas 组件），页面状态在 `RiskMapPage`。
 * 数据口径全部来自 Provider 纯函数（pearson / stdOf 等，G-R2 零幻觉），本文件不做任何计算口径。
 */

/** 五投影图层（doc 32 §1，口语命名、全事实语态，无等级/评分词）。 */
enum class SkyLayer(val label: String, val termKey: String) {
    /** 行业重叠 + 集中度 → 位置 + 团域。 */
    CLUSTER("抱团", "SECTOR"),

    /** 相关性 → 连线（|r|≥0.5 才画，粗细/亮度=强度）。 */
    LINK("牵连", "CORRELATION"),

    /** 波动暴露 → 光晕（N×大盘，等权）。 */
    VOLATILITY("颠簸", "VOLATILITY"),

    /** 事件时间轴 → 徽标 + 时间刷。 */
    SCHEDULE("日程", "UNLOCK"),

    /** 情绪暴露 → 连板环纹。 */
    HEAT("热度", "SENTIMENT"),
    ;

    companion object {
        fun fromName(name: String): SkyLayer? = entries.firstOrNull { it.name == name }
    }
}

/** 布局输入：一只自选（symbol 唯一）。 */
data class StarMemberIn(
    val symbol: String,
    val name: String,
    val industry: String,
)

/** 布局输出：一颗星。坐标为 Canvas 视图坐标（dp），(0,0) 在左上角。 */
data class SkyStar(
    val symbol: String,
    val name: String,
    val industry: String,
    val x: Float,
    val y: Float,
    /** 所属团在 [SkyGeometry.clusters] 中的下标。 */
    val clusterIndex: Int,
)

/** 布局输出：一个团域（虚线圈）。 */
data class SkyClusterBounds(
    val name: String,
    val cx: Float,
    val cy: Float,
    /** 团域半径（圆形：无 ContextApi.ellipse，统一用圆）。 */
    val radius: Float,
    val memberCount: Int,
)

data class SkyGeometry(
    val stars: List<SkyStar>,
    val clusters: List<SkyClusterBounds>,
    /** 建议画布高度（含上下留白），页面据此设 Canvas 容器高度。 */
    val requiredHeight: Float,
)

object StarLayout {
    const val STAR_RADIUS = 13f
    const val CLUSTER_GAP = 22f
    const val ROW_GAP = 26f
    const val MIN_HEIGHT = 320f
    const val TOP_PAD = 40f
    const val LINK_MIN_R = 0.5

    /** 星体命中半径（doc 32 §6.1：星体 20dp 命中）。 */
    const val STAR_HIT_DP = 20f

    /**
     * 团内成员环半径：星在团心圆周均匀分布，相邻星弧间距 ≥ 星直径 + 14f 间隙；
     * 下限 40f 保证小团不挤。成员 1 只返回 0f（星落团心）。
     */
    fun memberRingRadius(memberCount: Int): Float {
        if (memberCount <= 1) return 0f
        val spacing = STAR_RADIUS * 2 + 14f
        return max(40f, (memberCount * spacing / (2.0 * PI)).toFloat())
    }

    /** 团域半径 = 成员环半径 + 标注/星标 breathing room；至少容纳一颗星。 */
    fun clusterRadius(memberCount: Int): Float =
        if (memberCount <= 1) STAR_RADIUS + 18f else memberRingRadius(memberCount) + STAR_RADIUS + 14f

    /**
     * 颠簸层光晕半径（doc 32 §1：光晕按波动倍率）。volRatio = 个股日波动 ÷ 沪深300 日波动；
     * 0.5×~2.5× 线性映射到 10~28dp，未知/异常返回 0f（不画光晕）。
     */
    fun haloRadiusDp(volRatio: Double?): Float {
        if (volRatio == null || volRatio <= 0.0) return 0f
        val clamped = volRatio.coerceIn(0.5, 2.5)
        return (10f + ((clamped - 0.5) / 2.0 * 18.0)).toFloat()
    }

    /** 牵连线宽：|r|≥0.5 才画（0f=不画），r=0.5→0.8dp，r=1→2.9dp。负相关与正相关同宽（强度口径）。 */
    fun linkWidthDp(r: Double): Float {
        val level = abs(r)
        if (level < LINK_MIN_R) return 0f
        return 0.8f + ((level - LINK_MIN_R) * 4.2f).toFloat()
    }

    /**
     * 相关性查找：key 约定 "A|B"（A 在前）；双向回退——调用方不保证顺序。
     * 自身/缺失返回 null。
     */
    fun lookupCorrelation(correlations: Map<String, Double>, a: String, b: String): Double? {
        if (a == b || a.isEmpty() || b.isEmpty()) return null
        return correlations["$a|$b"] ?: correlations["$b|$a"]
    }

    /**
     * 确定性布局：同行业聚团 → 团按成员数降序（稳定）→ 团按画布宽度逐行排布 →
     * 团内成员从正上方起顺时针均匀圆周分布。首团固定在第一行，同输入恒同输出。
     */
    fun layout(members: List<StarMemberIn>, width: Float): SkyGeometry {
        if (members.isEmpty() || width <= 0f) {
            return SkyGeometry(emptyList(), emptyList(), MIN_HEIGHT)
        }
        // 分组：保留首次出现顺序，再按数量降序稳定排序（与 industryStats 口径一致）。
        val grouped = members.groupBy { it.industry.ifBlank { "未分类" } }
            .map { (name, list) -> name to list }
            .sortedByDescending { it.second.size }

        val clusterBounds = grouped.map { (name, list) -> Triple(name, list, clusterRadius(list.size)) }

        // 逐行排布：贪心装行（单团超宽也独立成行）。
        data class Placed(val index: Int, val cx: Float, val cy: Float, val radius: Float)
        val placed = ArrayList<Placed>(clusterBounds.size)
        var rowIndex = mutableListOf<Placed>()
        var rowWidth = 0f
        var rowTop = TOP_PAD
        var rowHeight = 0f

        fun flushRow() {
            if (rowIndex.isEmpty()) return
            val totalW = rowWidth
            var cursor = (width - totalW) / 2f
            for (p in rowIndex) {
                val cx = cursor + p.radius
                placed.add(Placed(p.index, cx, rowTop + p.radius, p.radius))
                cursor += p.radius * 2 + CLUSTER_GAP
            }
            rowTop += rowHeight + ROW_GAP
            rowIndex = mutableListOf()
            rowWidth = 0f
            rowHeight = 0f
        }

        clusterBounds.forEachIndexed { index, (_, _, radius) ->
            val w = radius * 2
            if (rowWidth > 0f && rowWidth + CLUSTER_GAP + w > width) {
                flushRow()
            }
            if (rowWidth > 0f) rowWidth += CLUSTER_GAP
            rowWidth += w
            rowHeight = max(rowHeight, radius * 2)
            rowIndex.add(Placed(index, 0f, 0f, radius))
        }
        flushRow()

        val clusters = placed.map { p ->
            SkyClusterBounds(
                name = grouped[p.index].first,
                cx = p.cx,
                cy = p.cy,
                radius = p.radius,
                memberCount = grouped[p.index].second.size,
            )
        }

        // 团内成员圆周分布：第一颗在正上方（-90°），顺时针。
        val stars = ArrayList<SkyStar>(members.size)
        placed.forEach { p ->
            val list = grouped[p.index].second
            val ring = memberRingRadius(list.size)
            list.forEachIndexed { i, member ->
                val angle = (-PI / 2.0 + 2.0 * PI * i / list.size)
                val x = p.cx + (ring * cos(angle)).toFloat()
                val y = p.cy + (ring * sin(angle)).toFloat()
                stars.add(SkyStar(member.symbol, member.name, member.industry, x, y, p.index))
            }
        }

        val contentBottom = (placed.maxOfOrNull { it.cy + it.radius } ?: TOP_PAD)
        return SkyGeometry(
            stars = stars,
            clusters = clusters,
            requiredHeight = max(MIN_HEIGHT, contentBottom + TOP_PAD),
        )
    }
}

/**
 * 时间刷映射（doc 32 §6.1）：事件日刻度**均匀线性**（不是真实时间比例）——
 * 时间刷的用途是「等可达性地扫选事件日」，均匀间距保证相邻密集日期标签不互压。
 * PAD=26dp 供绘制与命中共用（同一份几何，不会画得准、点不准）。
 */
object TimeBrushLayout {
    const val PAD = 26f

    /** n 个事件日在轨道上的刻度 x（n==0 空表；n==1 居中）。 */
    fun tickXs(eventCount: Int, width: Float): List<Float> {
        if (eventCount <= 0 || width <= 0f) return emptyList()
        if (eventCount == 1) return listOf(width / 2f)
        val trackW = (width - PAD * 2).coerceAtLeast(1f)
        return List(eventCount) { i -> PAD + trackW * i / (eventCount - 1) }
    }

    /** knob 连续位置 fraction(0..1)（0=轨道最左）→ 最近事件刻度下标。 */
    fun nearestIndexForFraction(fraction: Float, eventCount: Int): Int {
        if (eventCount <= 0) return -1
        if (eventCount == 1) return 0
        val f = fraction.coerceIn(0f, 1f)
        return (f * (eventCount - 1)).let { kotlin.math.round(it).toInt() }.coerceIn(0, eventCount - 1)
    }

    /** 事件刻度下标 → knob fraction（吸附用）。 */
    fun fractionForIndex(index: Int, eventCount: Int): Float {
        if (eventCount <= 0) return 0f
        if (eventCount == 1) return 0.5f
        val i = index.coerceIn(0, eventCount - 1)
        return i.toFloat() / (eventCount - 1)
    }

    /** knob fraction → 轨道 x（绘制 knob 用，与 tickXs 同一几何）。 */
    fun xForFraction(fraction: Float, width: Float): Float {
        val trackW = (width - PAD * 2).coerceAtLeast(1f)
        return PAD + fraction.coerceIn(0f, 1f) * trackW
    }

    /** tap 命中：x 距最近刻度 ≤ [TAP_SLOP_DP] 返回其下标，否则 -1。 */
    fun nearestIndexForX(x: Float, eventCount: Int, width: Float): Int {
        val ticks = tickXs(eventCount, width)
        if (ticks.isEmpty()) return -1
        var best = -1
        var bestDist = Float.MAX_VALUE
        ticks.forEachIndexed { i, tx ->
            val d = abs(x - tx)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return if (bestDist <= TAP_SLOP_DP) best else -1
    }

    const val TAP_SLOP_DP = 22f
}

/** 通用小工具：限制在 [min,max]（测试与组件共用）。 */
fun clampF(v: Float, min: Float, max: Float): Float = min(max(v, min), max)
