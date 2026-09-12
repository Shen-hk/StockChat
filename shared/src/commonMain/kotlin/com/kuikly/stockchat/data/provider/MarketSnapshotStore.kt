package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.common.Format
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 市场页时间机器基建（doc 33 §7 / doc 36 §7）：数据本来就在轮询流里，
 * SnapshotStore 只负责「接住它」——把每次 loadOverview 返回的 [MarketOverview]
 * 按分钟节流入库，形成当日 0..240 的帧序列（0=09:30，120=13:00，240=15:00）。
 *
 * 纯 Kotlin、无 UI 依赖、可注入假序列单测；内存环封顶 [MAX_FRAMES] 帧，
 * 当日闭合（隔日归档暂不入 v1，页面冷启动无帧时退化为静态态）。
 */
data class MarketSnapshotFrame(
    /** 交易分钟索引 0..240（0=09:30，120=13:00，240=15:00）。 */
    val minute: Int,
    val overview: MarketOverview,
) {
    /** 全市场红盘率（多空分界 50%）；无样本时回落 50（中性）。 */
    val redPct: Double
        get() {
            val total = overview.risingCount + overview.fallingCount
            return if (total == 0) 50.0 else overview.risingCount * 100.0 / total
        }

    val timeLabel: String get() = timeLabelOf(minute)
}

/** 交易分钟索引 ↔ 时刻换算（A 股 09:30–11:30 / 13:00–15:00，午休 90 分钟折叠）。 */
fun tradingMinuteOf(hour: Int, minute: Int): Int? {
    val total = hour * 60 + minute
    return when (total) {
        in 570..690 -> total - 570            // 09:30..11:30
        in 780..900 -> 120 + (total - 780)    // 13:00..15:00
        else -> null
    }
}

fun timeLabelOf(minute: Int): String {
    val m = minute.coerceIn(0, 240)
    val total = if (m <= 120) 570 + m else 780 + (m - 120)
    val h = total / 60
    val min = total % 60
    return "${h.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
}

class MarketSnapshotStore {

    private val framesByMinute = linkedMapOf<Int, MarketSnapshotFrame>()

    val isEmpty: Boolean get() = framesByMinute.isEmpty()

    /** 按分钟升序的帧序列。 */
    fun all(): List<MarketSnapshotFrame> = framesByMinute.values.sortedBy { it.minute }

    fun frameAt(minute: Int): MarketSnapshotFrame? = framesByMinute[minute]

    fun latestMinute(): Int? = framesByMinute.keys.maxOrNull()

    fun clear() = framesByMinute.clear()

    /**
     * 每分钟保留最新一帧（轮询按分钟归档，doc 33 §7.1）；超出封顶后丢最旧的上午帧。
     * [minuteOfDay] 由页侧传平台当前时刻的「时×60+分」；不在交易时段的帧不记录。
     */
    fun record(overview: MarketOverview, minuteOfDay: Int) {
        val minute = tradingMinuteOf(minuteOfDay / 60, minuteOfDay % 60) ?: return
        framesByMinute[minute] = MarketSnapshotFrame(minute, overview)
        if (framesByMinute.size > MAX_FRAMES) {
            framesByMinute.keys.minOrNull()?.let(framesByMinute::remove)
        }
    }

    /** 演示/测试直填一帧（绕过节流）。 */
    fun put(frame: MarketSnapshotFrame) {
        framesByMinute[frame.minute] = frame
    }

    private companion object {
        const val MAX_FRAMES = 260
    }
}

/**
 * 端侧事件检测（doc 33 §7.2 / doc 21 §4 阈值）：纯函数，输入快照序列、输出
 * 「已发生的事实」列表。L2 级事件才允许上钉子，天然把数量控制在 ≤5。
 * AI 只把事件串成话（润色），不发现事件、不预测——合规铁律。
 */
data class MarketEvent(
    val minute: Int,
    val title: String,
    /** 一句可陈述的统计事实（含具体数字）。 */
    val fact: String,
    /** 复盘句高亮区间（帧索引）。 */
    val segStart: Int,
    val segEnd: Int,
) {
    val timeLabel: String get() = timeLabelOf(minute)
}

object MarketEventDetector {

    private const val WINDOW = 30          // 对比窗口（分钟）
    private const val RED_DROP_PP = 7.0    // 红盘率骤降阈值（百分点）
    private const val RED_SURGE_PP = 5.0   // 红盘率骤升阈值
    private const val LIMIT_SURGE = 10     // 涨停家数骤增阈值
    private const val SEAL_DROP = 0.10     // 封板率骤降阈值（占比）

    fun detect(frames: List<MarketSnapshotFrame>): List<MarketEvent> {
        if (frames.size < 3) return emptyList()
        val events = mutableListOf<MarketEvent>()
        val sorted = frames.sortedBy { it.minute }

        // ① 早盘涨停潮：前 60 分钟涨停家数骤增
        val morning = sorted.filter { it.minute <= 60 }
        if (morning.size >= 2) {
            val first = morning.first()
            val peak = morning.maxByOrNull { it.overview.limitUpCount }!!
            if (peak.overview.limitUpCount - first.overview.limitUpCount >= LIMIT_SURGE) {
                events += MarketEvent(
                    minute = peak.minute,
                    title = "早盘涨停潮",
                    fact = "涨停 ${first.overview.limitUpCount} → ${peak.overview.limitUpCount} 家，红盘率 ${Format.decimal(first.redPct, 0)}% → ${Format.decimal(peak.redPct, 0)}%",
                    segStart = first.minute,
                    segEnd = peak.minute,
                )
            }
        }

        // ② 放量跳水：红盘率窗口骤降
        detectRedSwing(sorted, rising = false)?.let { events += it }

        // ③ 午后炸板潮 / 封板率走低
        val afternoon = sorted.filter { it.minute >= 120 && it.overview.sealRate != null }
        if (afternoon.size >= 2) {
            var dropFrom: MarketSnapshotFrame? = null
            var dropTo: MarketSnapshotFrame? = null
            var best = 0.0
            for (i in afternoon.indices) {
                for (j in i + 1 until afternoon.size) {
                    val a = afternoon[i]
                    val b = afternoon[j]
                    if (b.minute - a.minute > WINDOW || b.minute - a.minute <= 0) continue
                    val drop = (a.overview.sealRate ?: 1.0) - (b.overview.sealRate ?: 1.0)
                    if (drop > best) {
                        best = drop
                        dropFrom = a
                        dropTo = b
                    }
                }
            }
            if (dropFrom != null && dropTo != null && best >= SEAL_DROP) {
                events += MarketEvent(
                    minute = dropTo.minute,
                    title = "炸板增多 · 封板率走低",
                    fact = "封板率 ${Format.decimal((dropFrom.overview.sealRate ?: 0.0) * 100, 0)}% → ${Format.decimal((dropTo.overview.sealRate ?: 0.0) * 100, 0)}%，炸板 ${dropTo.overview.brokenBoardCount ?: 0} 只",
                    segStart = dropFrom.minute,
                    segEnd = dropTo.minute,
                )
            }
        }

        // ④ 尾盘回流：最后 60 分钟红盘率回升
        val tail = sorted.filter { it.minute >= 180 }
        if (tail.size >= 2) {
            val low = tail.minByOrNull { it.redPct }!!
            val last = tail.last()
            if (last.redPct - low.redPct >= RED_SURGE_PP && last.minute > low.minute) {
                events += MarketEvent(
                    minute = last.minute,
                    title = "尾盘回流",
                    fact = "红盘率 ${Format.decimal(low.redPct, 0)}% → ${Format.decimal(last.redPct, 0)}%，北向净流出收窄",
                    segStart = low.minute,
                    segEnd = last.minute,
                )
            }
        }

        // 同 60 分钟内的事件去重（保留最早的），并按时间排序后截断到 5 条。
        val deduped = mutableListOf<MarketEvent>()
        for (event in events.sortedBy { it.minute }) {
            if (deduped.none { abs(it.minute - event.minute) < 60 }) deduped += event
        }
        return deduped.take(5)
    }

    /** 红盘率窗口骤变（跳水/拉升共用）：找窗口内最大摆幅。 */
    private fun detectRedSwing(sorted: List<MarketSnapshotFrame>, rising: Boolean): MarketEvent? {
        var best = 0.0
        var from: MarketSnapshotFrame? = null
        var to: MarketSnapshotFrame? = null
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (b.minute - a.minute > WINDOW || b.minute == a.minute) continue
                val swing = b.redPct - a.redPct
                val hit = if (rising) swing >= RED_DROP_PP else swing <= -RED_DROP_PP
                val magnitude = abs(swing)
                if (hit && magnitude > best) {
                    best = magnitude
                    from = a
                    to = b
                }
            }
        }
        if (from == null || to == null) return null
        return MarketEvent(
            minute = to.minute,
            title = if (rising) "红盘率快速拉升" else "放量跳水",
            fact = "红盘率 ${Format.decimal(from.redPct, 0)}% → ${Format.decimal(to.redPct, 0)}%（${WINDOW} 分钟内）",
            segStart = from.minute,
            segEnd = to.minute,
        )
    }
}

/**
 * 演示日合成器：DEMO 模式下用当份演示快照做「锚点」，沿确定性的日内路径
 * 展开 241 帧序列（冲高回落、二次探底、尾盘修复），让时间机器在演示态完整可玩。
 * 真实模式不调用——真实帧只能来自真实轮询，绝不冒充。
 *
 * 纯函数、确定性（同一 base 永远得到同一序列，可单测）。
 */
object MarketDemoDaySynthesizer {

    /** 关键帧：分钟 → 数值（线性插值）。 */
    private fun keyframe(kf: List<Pair<Int, Double>>): DoubleArray {
        val out = DoubleArray(FRAMES + 1)
        for (i in 0 until kf.size - 1) {
            val (t0, v0) = kf[i]
            val (t1, v1) = kf[i + 1]
            for (t in t0..t1) out[t] = v0 + (v1 - v0) * ((t - t0).toDouble() / ((t1 - t0).coerceAtLeast(1)))
        }
        for (t in 0..FRAMES) if (out[t] == 0.0 && t > kf.last().first) out[t] = kf.last().second
        return out
    }

    /** 演示路径形状（相对值，最终值统一缩放到 base 的收盘锚点）。 */
    private val shapeDipRecover = listOf(0 to 0.45, 35 to 1.30, 72 to 0.40, 100 to 0.18, 150 to -0.25, 200 to 0.75, 240 to 1.0)
    private val shapeSlowRise = listOf(0 to 0.30, 60 to 0.55, 120 to 0.70, 180 to 0.85, 240 to 1.0)
    private val shapeDefiant = listOf(0 to 0.50, 40 to 0.90, 90 to 1.20, 150 to 1.35, 200 to 1.15, 240 to 1.0)
    private val shapeFade = listOf(0 to 1.40, 50 to 1.15, 110 to 0.95, 170 to 0.90, 240 to 1.0)
    private val shapeOvertake = listOf(0 to 0.25, 45 to 0.60, 110 to 1.05, 170 to 1.45, 210 to 1.30, 240 to 1.0)

    fun synthesize(base: MarketOverview): List<MarketSnapshotFrame> {
        if (base.indices.isEmpty()) return emptyList()
        val redPath = keyframe(listOf(0 to 53.0, 35 to 58.0, 50 to 52.0, 72 to 41.0, 100 to 44.0, 119 to 45.0, 150 to 40.0, 170 to 43.0, 195 to 46.0, 227 to 49.0, 240 to 51.2))
        val baseRed = if (base.risingCount + base.fallingCount == 0) 50.0 else base.risingCount * 100.0 / (base.risingCount + base.fallingCount)
        val redScale = baseRed / redPath[240]

        val luPath = keyframe(listOf(0 to 14.0, 20 to 38.0, 35 to 46.0, 72 to 34.0, 119 to 31.0, 150 to 33.0, 200 to 39.0, 240 to 43.0))
        val luScale = base.limitUpCount.coerceAtLeast(1) / luPath[240]
        val ldPath = keyframe(listOf(0 to 3.0, 72 to 9.0, 150 to 9.0, 240 to 6.0))
        val ldScale = base.limitDownCount.coerceAtLeast(1) / ldPath[240]
        val sealPath = keyframe(listOf(0 to 0.82, 35 to 0.86, 72 to 0.61, 119 to 0.70, 150 to 0.62, 200 to 0.74, 240 to 0.78))
        val sealScale = (base.sealRate ?: 0.76) / sealPath[240]
        val northPath = keyframe(listOf(0 to -6.0, 35 to -12.0, 72 to -38.0, 150 to -52.0, 200 to -44.0, 240 to -36.0))
        val northScale = (base.northboundFlow ?: -6_614_000_000.0) / northPath[240]
        val amtPath = keyframe(listOf(0 to 0.02, 60 to 0.42, 119 to 0.78, 120 to 0.79, 180 to 1.25, 240 to 1.79))
        val amtScale = (base.turnoverAmount ?: 1_790_000_000_000.0) / amtPath[240]
        val boardPath = keyframe(listOf(0 to 3.0, 35 to 5.0, 160 to 4.0, 240 to 4.0))
        val boardScale = (base.highestBoard ?: 4) / boardPath[240]

        // 指数路径：首个指数走「冲高回落」，其余按序取不同形状（保证盘中位次有变化）。
        val indexShapes = listOf(shapeDipRecover, shapeSlowRise, shapeOvertake, shapeDefiant, shapeFade)
        val indexPaths = base.indices.mapIndexed { i, _ -> keyframe(indexShapes[i % indexShapes.size]) }
        val sectorShapes = listOf(shapeDipRecover, shapeOvertake, shapeSlowRise, shapeFade, shapeDefiant)
        val sectorPaths = base.sectors.mapIndexed { i, _ -> keyframe(sectorShapes[i % sectorShapes.size]) }

        val store = MarketSnapshotStore()
        for (minute in 0..FRAMES) {
            val red = redPath[minute] * redScale
            val total = base.risingCount + base.fallingCount
            val rising = (total * red / 100.0).roundToInt().coerceIn(0, total)
            val falling = (total - rising).coerceAtLeast(0)
            val indices = base.indices.mapIndexed { i, idx ->
                val pct = idx.changePercent * indexPaths[i][minute]
                val denom = 1 + idx.changePercent / 100.0
                val prevClose = if (abs(denom) < 1e-9) idx.price else idx.price / denom
                idx.copy(
                    price = prevClose * (1 + pct / 100.0),
                    changePercent = pct,
                )
            }
            val sectors = base.sectors.mapIndexed { i, s ->
                s.copy(changePercent = s.changePercent * sectorPaths[i][minute])
            }
            val frameOverview = base.copy(
                indices = indices,
                risingCount = rising,
                fallingCount = falling,
                flatCount = base.flatCount,
                limitUpCount = (luPath[minute] * luScale).roundToInt(),
                limitDownCount = (ldPath[minute] * ldScale).roundToInt(),
                sectors = sectors,
                stamp = base.stamp.copy(asOf = timeLabelOf(minute)),
                turnoverAmount = amtPath[minute] * amtScale,
                sealRate = (sealPath[minute] * sealScale).coerceIn(0.0, 1.0),
                highestBoard = (boardPath[minute] * boardScale).roundToInt(),
                northboundFlow = northPath[minute] * northScale,
            )
            store.put(MarketSnapshotFrame(minute, frameOverview))
        }
        return store.all()
    }

    const val FRAMES = 240
}
