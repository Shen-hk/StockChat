package com.kuikly.stockchat.detail.ai.state

import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.provider.MarketTimelineSpec
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import kotlin.math.abs
import kotlin.math.sin

/**
 * AI 走势推演结果（详情页底部工作台「AI 走势」tab 的真实模型输出）：
 * 与主解读/圈选解读（[AiInsightSession]）不同，本会话要求模型输出严格 JSON，
 * 端侧解析为结构化数据后再渲染（方向 / 置信度 / 情景区间 / 结论 / 依据 / 路径点）。
 * path 为模型给出的情景路径（% vs 现价）；模型未给或不可解析时由
 * [synthesizeForecastPath] 端侧合成（UI 明确标注口径）。
 */
internal data class AiTrendForecast(
    val direction: String, // "up" | "down" | "flat"
    val confidence: String, // "high" | "mid" | "low"
    val rangePctHigh: Double, // 情景相对现价的最大上行幅度（%，正数）
    val rangePctLow: Double, // 情景相对现价的最大下行幅度（%，负数）
    val summary: String,
    val reasons: List<AiForecastReason>,
    val path: List<Double>, // 情景路径点（% vs 现价，首点恒为 0）
    val pathFromModel: Boolean, // false = 模型未给 path，端侧按区间合成
)

internal data class AiForecastReason(
    val title: String,
    val detail: String,
    val weight: Double, // 0..1
)

internal class DetailForecastState {
    /** 0 idle / 1 thinking / 2 streaming / 3 done / 4 error（与主解读会话同口径）。 */
    var phase: Int by observable(0)
    var error: String by observable("")
    var model: String by observable("")
    var result: AiTrendForecast? by observable(null)
    /** 思考/流式骨架的呼吸驱动：低频（450ms）自增，勿用作高频动画 key。 */
    var pulseTick: Int by observable(0)
    /** 流式期间已接收字符数（进度感展示）。 */
    var streamChars: Int by observable(0)
}

/**
 * 「AI 走势」tab 的真实 AI 推演会话（复用 [DetailAiHostPort] 的配置/Provider/线程
 * 纪律）。与 [AiInsightSession] 的差异：无逐字打字机，delta 只做进度计数，
 * 完整内容在 onDone 一次性解析为 [AiTrendForecast]。全部状态写入经
 * [DetailAiHostPort.jumpToMain] 守住主线程（铁律 A）。
 */
internal class DetailAiForecastCoordinator(
    private val state: DetailForecastState,
    private val host: DetailAiHostPort,
    private val scheduler: DetailAiScheduler,
    private val reduceMotion: Boolean,
) {
    private var generation = 0
    private var provider: AiProvider? = null
    private val tasks = mutableListOf<DetailAiScheduledTask>()

    /** 切到「AI 走势」tab 时调用：已有结果则缓存展示，进行中不重复发起。 */
    fun maybeStart() {
        when (state.phase) {
            3 -> return
            1, 2 -> return
            else -> request()
        }
    }

    /** 主动（重试/刷新）入口：无条件重新发起一次推演。 */
    fun request() {
        val config: AiConfig = host.loadConfig()
        val validationError = host.configValidationError(config)
        if (validationError != null) {
            state.error = "未配置 AI API（$validationError）"
            state.model = ""
            state.phase = 4
            return
        }
        provider?.stop()
        val gen = ++generation
        state.error = ""
        state.model = config.model
        state.result = null
        state.streamChars = 0
        state.phase = 1
        startPulse(gen)
        val forecastProvider = host.createProvider(config)
        provider = forecastProvider
        var content = ""
        // 12s 首字节兜底（与主解读会话同口径）；已进流式则交给 onDone / onError 收尾
        schedule(TIMEOUT_MS) {
            if (gen == generation && state.phase == 1) {
                forecastProvider.stop()
                state.error = "请求超时（12 秒无响应），请重试"
                state.phase = 4
            }
        }
        forecastProvider.ask(
            messages = listOf(AiChatMessage("user", buildForecastPrompt(host.quote(), host.insight(), host.newsList(), host.insight().fundFlow?.main))),
            onDelta = { delta ->
                if (gen != generation) return@ask
                content += delta
                host.jumpToMain {
                    if (gen != generation) return@jumpToMain
                    state.streamChars = content.length
                    if (state.phase == 1) state.phase = 2
                }
            },
            onDone = {
                if (gen != generation) return@ask
                host.jumpToMain {
                    if (gen != generation) return@jumpToMain
                    val parsed = parseForecastJson(content)
                    if (parsed == null) {
                        state.error = "AI 返回了无法解析的内容，请重试"
                        state.phase = 4
                    } else {
                        state.result = parsed
                        state.phase = 3
                    }
                }
            },
            onError = { message ->
                if (gen != generation) return@ask
                host.jumpToMain {
                    if (gen != generation) return@jumpToMain
                    state.error = message
                    state.phase = 4
                }
            },
        )
    }

    /** 页面离开：中断进行中的流；已完成结果保留（回到页面仍可看）。 */
    fun onDisappear() {
        if (state.phase == 1 || state.phase == 2) {
            provider?.stop()
            generation++
            state.phase = 0
            state.streamChars = 0
        }
    }

    fun onDestroy() {
        provider?.stop()
        generation++
        tasks.forEach(DetailAiScheduledTask::cancel)
        tasks.clear()
    }

    /** 思考/流式期间的低频呼吸驱动；phase 落定后自灭。reduceMotion 不启动。 */
    private fun startPulse(gen: Int) {
        if (reduceMotion) return
        fun tick() {
            if (gen != generation || state.phase >= 3) return
            state.pulseTick++
            schedule(PULSE_MS) { tick() }
        }
        schedule(PULSE_MS) { tick() }
    }

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }

    companion object {
        const val TIMEOUT_MS = 12000
        const val PULSE_MS = 450
    }
}

/** 「AI 走势」推演 prompt：端侧事实槽位 + 严格 JSON 输出约束。 */
internal fun buildForecastPrompt(
    quote: Quote,
    insight: StockInsightBundle,
    newsList: List<NewsItem>,
    mainFlow: Double?,
): String {
    val timeline = quote.timeline
    val highPoint = timeline.maxByOrNull { it.price }
    val lowPoint = timeline.minByOrNull { it.price }
    val amplitude = if (quote.previousClose > 0.0) {
        Format.percent((quote.high - quote.low) / quote.previousClose * 100.0)
    } else "--"
    val slotCount = MarketTimelineSpec.forSymbol(quote.symbol).slotCount
    val sessionProgress = when {
        timeline.isEmpty() -> "分时数据尚未返回"
        timeline.size >= slotCount -> "已收盘"
        else -> "已交易约 ${timeline.size} 分钟（全天约 $slotCount 分钟）"
    }
    val tailMove = if (timeline.size > 30) {
        val tail = timeline.last().price
        val base = timeline[timeline.size - 31].price
        if (base > 0.0) "最近 30 分钟 ${Format.percent((tail - base) / base * 100.0)}" else null
    } else null
    val facts = buildList {
        add("现价 ${Format.price(quote.price)}（${Format.percent(quote.changePercent)}），昨收 ${Format.price(quote.previousClose)}，开盘 ${Format.price(quote.open)}")
        if (highPoint != null) add("日内最高 ${Format.price(quote.high)}（出现于 ${highPoint.time}）")
        if (lowPoint != null) add("日内最低 ${Format.price(quote.low)}（出现于 ${lowPoint.time}）")
        add("当日振幅 $amplitude；交易进度：$sessionProgress")
        tailMove?.let { add(it) }
        mainFlow?.let {
            add("今日主力资金净${if (it >= 0) "流入" else "流出"} ${Format.compactAmount(kotlin.math.abs(it))}")
        }
        insight.fundamentals?.financial?.let {
            add("最新财报（${it.reportDate}）：营收同比 ${Format.percent(it.revenueYoY)}，净利润同比 ${Format.percent(it.profitYoY)}")
        }
        if (newsList.isNotEmpty()) {
            add("近期资讯标题：${newsList.take(3).joinToString("；") { it.title }}")
        }
    }
    return buildString {
        appendLine("你是 A 股个股走势推演助手。请基于下面的今日真实数据，推演 ${quote.name}（${quote.symbol}）下一阶段的走势情景（收盘前的剩余时段；若已收盘则为下一交易日开盘初段）。")
        appendLine()
        appendLine("硬性要求：")
        appendLine("1. 只输出一个 JSON 对象；禁止 markdown、代码块围栏、解释性文字。")
        appendLine("2. JSON 字段全部必填：")
        appendLine("   \"direction\"：\"up\"（偏强）或 \"down\"（偏弱）或 \"flat\"（震荡）")
        appendLine("   \"confidence\"：\"high\" 或 \"mid\" 或 \"low\"")
        appendLine("   \"rangePctHigh\"：数字，情景相对现价的最大上行幅度，单位 %（正数，如 0.8）")
        appendLine("   \"rangePctLow\"：数字，情景相对现价的最大下行幅度，单位 %（负数，如 -0.6）")
        appendLine("   \"summary\"：一句话情景结论，不超过 30 字")
        appendLine("   \"reasons\"：数组，3-4 个对象，每个为 {\"title\":\"因素名（4 字内）\",\"detail\":\"具体依据（必须引用今日数据中的数字）\",\"weight\":0 到 1 之间的小数}，weight 表示该因素对情景的贡献权重")
        appendLine("   \"path\"：数组，5-7 个数字，情景路径点（单位 %，相对现价；第一个必须为 0，其余落在 rangePctLow 与 rangePctHigh 之间）")
        appendLine("3. detail 必须引用今日数据中的具体数字；今日数据中不存在的数字禁止编造。")
        appendLine("4. 这是情景推演，不是操作建议；JSON 内不要出现买入、卖出、仓位等建议措辞。")
        appendLine("5. 幅度要与当日振幅、资金方向相称：振幅小且资金无明显方向时，幅度应接近 0（如 ±0.5% 以内）。")
        appendLine()
        appendLine("今日数据（唯一事实来源，禁止编造未提供的数字）：")
        facts.forEach { appendLine("- $it") }
    }
}

/**
 * 宽松解析模型输出：剥代码围栏 → 截取首尾大括号 → JSON。字段缺省/类型异常时
 * 尽量降级保留（区间按 direction 合成、path 端侧补）；summary 与 reasons 全空
 * 时返回 null（视为解析失败，UI 走错误态）。
 */
internal fun parseForecastJson(raw: String): AiTrendForecast? {
    val cleaned = raw
        .lines()
        .filterNot { it.trimStart().startsWith("```") }
        .joinToString("\n")
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val json = try {
        JSONObject(cleaned.substring(start, end + 1))
    } catch (_: Throwable) {
        return null
    }
    val direction = normalizeEnum(json.optString("direction"), listOf("up", "down"), "flat")
    val confidence = normalizeEnum(json.optString("confidence"), listOf("high", "mid"), "mid")
    var high = json.optDouble("rangePctHigh", Double.NaN)
    var low = json.optDouble("rangePctLow", Double.NaN)
    if (high.isNaN() || low.isNaN() || abs(high) > 30.0 || abs(low) > 30.0) {
        // 区间不可信时按方向合成，保持 UI 可渲染
        when (direction) {
            "up" -> { high = 0.8; low = -0.3 }
            "down" -> { high = 0.3; low = -0.8 }
            else -> { high = 0.4; low = -0.4 }
        }
    }
    if (high < low) {
        val swapped = high
        high = low
        low = swapped
    }
    val summary = json.optString("summary").trim()
    val reasons = buildList {
        val array = json.optJSONArray("reasons") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            val detail = item.optString("detail").trim()
            if (title.isEmpty() && detail.isEmpty()) continue
            var weight = item.optDouble("weight", Double.NaN)
            if (weight.isNaN() || weight < 0.0 || weight > 1.0) weight = 0.25
            add(AiForecastReason(title.ifEmpty { "因素 ${i + 1}" }, detail, weight))
        }
    }
    if (summary.isEmpty() && reasons.isEmpty()) return null
    val path = buildList {
        val array = json.optJSONArray("path") ?: JSONArray()
        for (i in 0 until array.length()) {
            val v = array.optDouble(i, Double.NaN)
            if (!v.isNaN() && abs(v) <= 30.0) add(v)
        }
    }.let { if (it.size >= 2) it.take(8) else emptyList() }
    return AiTrendForecast(
        direction = direction,
        confidence = confidence,
        rangePctHigh = high,
        rangePctLow = low,
        summary = summary.ifEmpty { reasons.firstOrNull()?.detail.orEmpty() },
        reasons = reasons,
        path = path.ifEmpty { synthesizeForecastPath(direction, high, low) },
        pathFromModel = path.size >= 2,
    )
}

private fun normalizeEnum(raw: String, allowed: List<String>, fallback: String): String {
    val value = raw.trim().lowercase()
    return allowed.firstOrNull { value.contains(it) } ?: fallback
}

/** 端侧合成情景路径：0 起步、平滑趋近区间中枢并带确定性微扰（同输入同输出）。 */
internal fun synthesizeForecastPath(direction: String, high: Double, low: Double): List<Double> {
    val mid = (high + low) / 2.0
    val scale = maxOf(abs(high - low) / 2.0, 0.15)
    val count = 7
    return List(count) { i ->
        if (i == 0) 0.0
        else {
            val t = i.toDouble() / (count - 1).toDouble()
            val eased = t * (2.0 - t) // easeOut 二次：前期快、后期缓，路径不突兀
            val wiggle = sin(i * 2.3) * scale * 0.14 * (1.0 - t)
            val value = mid * eased + wiggle
            value.coerceIn(low, high)
        }
    }
}
