package com.kuikly.stockchat.risk.sky.state

import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.storage.KeyValueStorage
import com.kuikly.stockchat.risk.domain.RiskRow
import com.kuikly.stockchat.risk.domain.SkyLayer
import com.kuikly.stockchat.risk.domain.StarLayout
import com.kuikly.stockchat.risk.domain.StarMemberIn
import com.kuikly.stockchat.risk.domain.computeCorrelations
import com.kuikly.stockchat.risk.domain.computeVolRatios
import com.kuikly.stockchat.risk.state.RiskScheduledTask
import com.kuikly.stockchat.risk.state.RiskScheduler
import kotlin.math.abs
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 星图状态域唯一 owner（doc 47 B-3 第 3 步）：接管原 RiskMapPage 的
 * `skyLayer / skyViewMode / skySelectedSymbol / skySelectedCluster / skyBeaconDrawer /
 * correlations / volRatios / beaconPhase / skyDragOffsets / skyContextSymbol` 与
 * `pulseRunning`、`skyDragReturnGeneration` 两个非 observable 守卫，以及图层/视图
 * 模式持久化、脉冲 Timer、拖拽回弹 Timer。
 *
 * ⚠️ 拖拽回弹物理参数（0.72 衰减 / 40ms 步进 / 10 步上限 / 0.3 归零阈值）与
 * 长按 500ms 武装（RiskSkyChart 内）逐值保留，禁止顺手调整。
 *
 * 数据口径全部经 [Inputs] 注入（rows 快照 / 行业归属 / 容器宽度 / 单链判定），
 * 本域不持有 Store 与 Pager。
 */
internal class RiskSkyCoordinator(
    private val inputs: Inputs,
    private val storage: KeyValueStorage,
    private val scheduler: RiskScheduler,
    private val reduceMotion: Boolean,
) {
    /** 星图对页面数据域的只读视图（页面用 RiskDataCoordinator 实现它）。 */
    internal interface Inputs {
        /** 星图布局输入（symbol/name/industry，来自 rows + industries）。 */
        fun members(): List<StarMemberIn>

        /** 画布容器宽度（pageViewWidth - 28 - 32）。 */
        fun containerWidth(): Float

        /** 单链集中是否判定（引路星 = 最大团下标 0 的显示门控）。 */
        fun chainTriggered(): Boolean

        /** rows 快照（拖拽牵引联动、脉冲门控 rows>=3）。 */
        fun rowsSnapshot(): List<RiskRow>
    }

    /** 主卡视图模式（星图/导图）；持久化 key [SKY_VIEW_KEY]（返回态保持）。 */
    internal enum class SkyViewMode(val label: String) {
        CHART("星图"),
        MINDMAP("导图"),
    }

    /**
     * 当前投影图层。持久化 key [SKY_LAYER_KEY]（返回态保持）。
     * 改名只能走 [applySkyLayer]——不能定义 setSkyLayer 函数（与属性委托生成的
     * JVM setter 签名冲突，Platform declaration clash，doc 32 §6.1 实测坑）。
     */
    var skyLayer: SkyLayer by observable(SkyLayer.CLUSTER)
        private set

    var skyViewMode: SkyViewMode by observable(SkyViewMode.CHART)
        private set

    /** 选中星（symbol）；空 = 未选中。点同星 = 取消。 */
    var skySelectedSymbol: String by observable("")
        private set

    /** 选中团域（行业名）；空 = 未选中。 */
    var skySelectedCluster: String by observable("")
        private set

    /** 引路星解读抽屉展开态。 */
    var skyBeaconDrawer: Boolean by observable(false)
        private set

    /** 两两相关系数（key "A|B"，A 在列表序在前）；星图连线数据源。 */
    var correlations: Map<String, Double> by observable(emptyMap())
        private set

    /** 个股日波动 ÷ 沪深300 日波动（等权口径）；颠簸层光晕数据源。 */
    var volRatios: Map<String, Double> by observable(emptyMap())
        private set

    /** 引路星/颠簸光晕脉冲相位 0..1（12 步 × 55ms 步进，reduceMotion 恒 0）。 */
    var beaconPhase: Float by observable(0f)
        private set

    /** 拖星（长按确认后）的瞬态偏移；布局本身不变，松手后回弹至 0。 */
    var skyDragOffsets: Map<String, Pair<Float, Float>> by observable(emptyMap())
        private set

    var skyContextSymbol: String by observable("")
        private set

    /** 脉冲步进器运行标记（图层离开脉冲层自动停摆，切回由 applySkyLayer 重启）。 */
    private var pulseRunning = false

    /** 拖拽回弹链的版本守卫；新拖拽/新回弹使旧链失效。 */
    private var skyDragReturnGeneration = 0

    private var pulseTask: RiskScheduledTask? = null

    /** 返回态保持：恢复上次图层（doc 32 §3.1 P0 增强）与视图模式。页面 created() 调用一次。 */
    fun restore() {
        SkyLayer.fromName(storage.getString(SKY_LAYER_KEY))?.let { skyLayer = it }
        SkyViewMode.entries.firstOrNull { it.name == storage.getString(SKY_VIEW_KEY) }
            ?.let { skyViewMode = it }
    }

    /** 图层切换单点入口：持久化 + 清选中 + 脉冲启停。 */
    fun applySkyLayer(layer: SkyLayer) {
        if (skyLayer == layer) return
        skyLayer = layer
        storage.setString(SKY_LAYER_KEY, layer.name)
        skySelectedSymbol = ""
        skySelectedCluster = ""
        skyBeaconDrawer = false
        ensureBeaconPulse()
    }

    /** 主卡视图切换单点入口：持久化即可，两侧内容各自 vif 挂载（R7），无需清状态。 */
    fun applySkyViewMode(mode: SkyViewMode) {
        if (skyViewMode == mode) return
        skyViewMode = mode
        storage.setString(SKY_VIEW_KEY, mode.name)
    }

    /** 重算两两相关系数与波动倍率（行情/日K/指数到达后由页面调用）。 */
    fun refreshSkyData(rows: List<RiskRow>, indexQuote: Quote?) {
        correlations = computeCorrelations(rows)
        volRatios = computeVolRatios(rows, indexQuote)
    }

    /** 引路星指向的团下标（布局把最大团排在下标 0）；单链未判定 = 平静 = 无引路星。 */
    fun skyBeaconClusterIndex(): Int =
        if (inputs.rowsSnapshot().size >= 3 && inputs.chainTriggered()) 0 else -1

    /** 星→团→星布局（确定性纯函数，输入来自 rows + industries 两个 observable）。 */
    fun skyGeometry() = StarLayout.layout(
        inputs.members(),
        inputs.containerWidth(),
        correlations,
    )

    fun layerTip(): String = when (skyLayer) {
        SkyLayer.CLUSTER -> "抱团：圈 = 一条链，圈越大挤得越多 · 光晕 = 单只波动倍率"
        SkyLayer.LINK -> "牵连：线 = 近 ${CORRELATION_WINDOW} 日相关系数，粗亮 = 同涨同跌更狠（|r|≥0.5 才画）"
    }

    // ── 星图手势处理：点按、LINK 层拖星牵引、长按 Context Bar ──

    fun onSkyStarTap(symbol: String) {
        skyBeaconDrawer = false
        skySelectedCluster = ""
        skyContextSymbol = ""
        skySelectedSymbol = if (skySelectedSymbol == symbol) "" else symbol
    }

    fun onSkyClusterTap(name: String) {
        skyBeaconDrawer = false
        skySelectedSymbol = ""
        skySelectedCluster = if (skySelectedCluster == name) "" else name
    }

    fun onSkyBeaconTap() {
        skySelectedSymbol = ""
        skySelectedCluster = ""
        skyBeaconDrawer = !skyBeaconDrawer
    }

    fun onSkyBlankTap() {
        skySelectedSymbol = ""
        skySelectedCluster = ""
        skyBeaconDrawer = false
        skyContextSymbol = ""
    }

    /** 拖星牵引：直连星按相关系数比例跟随，负相关反向；全部收口在画布内。
     *  两图层均开放（2026-09-10 用户反馈放宽）。前置仍是长按确认（RiskSkyChart
     *  内 500ms 武装）——长按会先弹 Context Bar，手指继续移动即切换为牵引意图，
     *  此时收掉提问条，避免拖着星还挂着提问。 */
    fun onSkyStarDrag(symbol: String, dx: Float, dy: Float) {
        skyContextSymbol = ""
        skyDragReturnGeneration++
        val g = skyGeometry()
        val width = inputs.containerWidth()
        fun clamped(star: com.kuikly.stockchat.risk.domain.SkyStar, ox: Float, oy: Float): Pair<Float, Float> {
            // 星名画在星上方 -21f、涨跌幅 +26f，边距再放一档防文字被裁。
            val marginX = StarLayout.STAR_RADIUS + 8f
            val marginY = StarLayout.STAR_RADIUS + 26f
            val nx = (star.x + ox).coerceIn(marginX, (width - marginX).coerceAtLeast(marginX)) - star.x
            val ny = (star.y + oy).coerceIn(marginY, (g.requiredHeight - marginY).coerceAtLeast(marginY)) - star.y
            return nx to ny
        }
        val next = HashMap<String, Pair<Float, Float>>()
        g.stars.firstOrNull { it.symbol == symbol }?.let { next[symbol] = clamped(it, dx, dy) }
            ?: return
        inputs.rowsSnapshot().filter { it.symbol != symbol }.forEach { other ->
            val r = StarLayout.lookupCorrelation(correlations, symbol, other.symbol) ?: return@forEach
            if (abs(r) < StarLayout.LINK_MIN_R) return@forEach
            val pulled = g.stars.firstOrNull { it.symbol == other.symbol } ?: return@forEach
            next[other.symbol] = clamped(pulled, dx * r.toFloat() * 0.58f, dy * r.toFloat() * 0.58f)
        }
        skyDragOffsets = next
    }

    /** 松手后在约 0.4 秒内指数回弹；减少动态效果时立即归零。 */
    fun onSkyStarDragEnd() {
        val version = ++skyDragReturnGeneration
        if (reduceMotion) {
            skyDragOffsets = emptyMap()
            return
        }
        fun rebound(step: Int) {
            if (version != skyDragReturnGeneration) return
            val next = skyDragOffsets.mapValues { (_, value) -> value.first * 0.72f to value.second * 0.72f }
                .filterValues { abs(it.first) > 0.3f || abs(it.second) > 0.3f }
            skyDragOffsets = next
            if (step < 10 && next.isNotEmpty()) {
                pulseTask = scheduler.schedule(REBOUND_STEP_MS) { rebound(step + 1) }
            }
        }
        rebound(0)
    }

    fun onSkyStarLongPress(symbol: String) {
        skySelectedCluster = ""
        skyBeaconDrawer = false
        skySelectedSymbol = symbol
        skyContextSymbol = symbol
    }

    // ── 引路星脉冲 ──

    /** 引路星/光晕脉冲步进：12 步 × 55ms；仅脉冲层运行，reduceMotion 不启动（doc 32 §6.1）。 */
    fun ensureBeaconPulse() {
        if (reduceMotion || pulseRunning) return
        pulseRunning = true
        tickBeaconPulse()
    }

    private fun tickBeaconPulse() {
        if (!pulseRunning) return
        // 抱团/牵连两层引路星光环都可见（LINK 层 0.35 淡显）。
        if (inputs.rowsSnapshot().size < 3) {
            pulseRunning = false
            return
        }
        beaconPhase = (beaconPhase + 1f / BEACON_STEPS) % 1f
        pulseTask = scheduler.schedule(BEACON_STEP_MS) { tickBeaconPulse() }
    }

    companion object {
        const val CORRELATION_WINDOW = 60

        // 星图（doc 32）：图层持久化 key 与引路星脉冲步进参数。
        const val SKY_LAYER_KEY = "stockchat_risk_sky_layer_v1"
        const val SKY_VIEW_KEY = "stockchat_risk_sky_view_v1"
        const val BEACON_STEPS = 12
        const val BEACON_STEP_MS = 55
        private const val REBOUND_STEP_MS = 40
    }
}
