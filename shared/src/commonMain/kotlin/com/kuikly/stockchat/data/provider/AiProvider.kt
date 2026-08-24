package com.kuikly.stockchat.data.provider

import com.tencent.kuikly.core.base.PagerScope
import com.kuikly.stockchat.data.config.AiConfig
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.setTimeout

interface AiProvider {
    fun ask(question: String, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit)
    fun stop()
}

class DeepSeekAiProvider(
    override val pagerId: String,
    private val config: AiConfig,
) : AiProvider, PagerScope {
    private var generation = 0
    private val network: NetworkModule get() = getPager().acquireModule(NetworkModule.MODULE_NAME)

    override fun ask(question: String, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val current = ++generation
        requestCompletion(question, SYSTEM_PROMPT, null) { content, error ->
            if (current != generation) return@requestCompletion
            if (error != null) {
                onError(error)
            } else {
                emitChunks(content.orEmpty(), current, onDelta, onDone)
            }
        }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        val current = ++generation
        requestCompletion("只回复 OK", "你是 API 连通性检测助手。", 8) { content, error ->
            if (current != generation) return@requestCompletion
            if (error != null) onResult(false, error)
            else onResult(true, "连接成功，模型返回：${content.orEmpty().trim().take(40)}")
        }
    }

    override fun stop() {
        generation++
    }

    private fun emitChunks(content: String, current: Int, onDelta: (String) -> Unit, onDone: () -> Unit) {
        val chunks = content.chunked(12)
        chunks.forEachIndexed { index, chunk ->
            this.setTimeout(index * 18) {
                if (current == generation) onDelta(chunk)
            }
        }
        this.setTimeout(chunks.size * 18 + 1) {
            if (current == generation) onDone()
        }
    }

    private fun requestCompletion(
        question: String,
        systemPrompt: String,
        maxTokens: Int?,
        callback: (String?, String?) -> Unit,
    ) {
        val value = config.normalized()
        value.validationError()?.let {
            callback(null, it)
            return
        }
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", question) })
        }
        val body = JSONObject().apply {
            put("model", value.model)
            put("temperature", 0.2)
            put("stream", false)
            put("messages", messages)
            maxTokens?.let { put("max_tokens", it) }
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${value.apiKey}")
        }
        network.httpRequest(value.endpoint, true, body, headers, timeout = 45) { data, success, error, response ->
            val content = data.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (success && content.isNotEmpty()) {
                callback(content, null)
            } else {
                val apiMessage = data.optJSONObject("error")?.optString("message").orEmpty()
                val status = response.statusCode?.let { "HTTP $it" }.orEmpty()
                val detail = apiMessage.ifEmpty { error }.ifEmpty { "接口未返回有效内容" }
                callback(null, listOf(status, detail).filter { it.isNotEmpty() }.joinToString("："))
            }
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
            你是面向中文个人投资者的股票解释助手。只做信息解释，不预测收益，不给出买入、卖出或仓位建议。
            回答要简洁、可核验；区分事实、推断与不确定性。需要结构化内容时，在自然语言后输出卡片块：
            ```card:stock-quote
            {"symbol":"600519.SH"}
            ```
            可用类型：stock-quote、stock-chart、attribution、insight、definition、suggestions。
            suggestions 的 JSON 格式是 {"chips":[{"text":"继续追问","type":"drill"}]}。不要在 JSON 中编造实时价格，行情由客户端数据层填充。
        """.trimIndent()
    }
}

class MockAiProvider(override val pagerId: String) : AiProvider, PagerScope {
    private var generation = 0

    override fun ask(question: String, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val current = ++generation
        val answer = answerFor(question)
        val chunks = answer.chunked(10)
        chunks.forEachIndexed { index, chunk ->
            this.setTimeout(120 + index * 28) {
                if (current == generation) onDelta(chunk)
            }
        }
        this.setTimeout(121 + chunks.size * 28) {
            if (current == generation) onDone()
        }
    }

    override fun stop() {
        generation++
    }

    private fun answerFor(question: String): String {
        val normalized = question.lowercase()
        return when {
            "pe" in normalized || "市盈率" in question || "macd" in normalized -> definitionAnswer(question)
            "五粮液" in question || "对比" in question || "比较" in question -> compareAnswer()
            "为什么" in question || "原因" in question || "跌" in question || "涨" in question -> attributionAnswer()
            "走势" in question || "k线" in normalized || "分时" in question -> chartAnswer()
            else -> overviewAnswer()
        }
    }

    private fun overviewAnswer() = """
        贵州茅台当前处于偏弱震荡。价格变化本身只是结果，更值得关注的是成交、板块联动和后续公告。

        ```card:stock-quote
        {"symbol":"600519.SH"}
        ```

        ```card:insight
        {"symbol":"600519.SH"}
        ```

        ```card:suggestions
        {"chips":[{"text":"为什么跌","type":"drill"},{"text":"看分时走势","type":"drill"},{"text":"PE 是什么","type":"diverge"}]}
        ```
    """.trimIndent()

    private fun attributionAnswer() = """
        这次波动更像是多因素叠加，资金面贡献最大，板块联动次之。以下是解释框架，不是买卖建议。

        ```card:attribution
        {"symbol":"600519.SH","direction":"fall","factors":[{"name":"资金面","weight":0.42,"confidence":"high","desc":"成交放大且价格承压，主动卖压偏强。","source":"行情数据推断"},{"name":"板块联动","weight":0.28,"confidence":"medium","desc":"白酒板块同步走弱，对个股形成拖累。","source":"板块行情"},{"name":"消息面","weight":0.18,"confidence":"medium","desc":"暂未发现足以单独解释波动的重大公告。","source":"公开信息"},{"name":"情绪面","weight":0.12,"confidence":"low","desc":"短线风险偏好回落，放大了价格波动。","source":"市场宽度"}]}
        ```

        ```card:suggestions
        {"chips":[{"text":"看分时走势","type":"drill"},{"text":"和五粮液比较","type":"diverge"}]}
        ```
    """.trimIndent()

    private fun chartAnswer() = """
        分时线显示价格重心逐步下移。单日走势只能说明短线交易状态，不能替代基本面判断。

        ```card:stock-chart
        {"symbol":"600519.SH"}
        ```

        ```card:suggestions
        {"chips":[{"text":"为什么跌","type":"drill"},{"text":"看关键指标","type":"diverge"}]}
        ```
    """.trimIndent()

    private fun definitionAnswer(question: String): String {
        val isMacd = "macd" in question.lowercase()
        val term = if (isMacd) "MACD" else "市盈率 PE"
        val plain = if (isMacd) "用两组移动平均线的关系观察趋势和动能，金叉不等于一定上涨。" else "股价相对于每股收益的倍数，用来观察市场为当前盈利支付了多少价格。"
        return """
            $term 适合用来辅助理解，但不能单独决定买卖。

            ```card:definition
            {"term":"$term","plainText":"$plain","example":"同一个 PE 水平，在高增长行业和成熟行业中的含义可能完全不同。"}
            ```

            ```card:suggestions
            {"chips":[{"text":"茅台的 PE 怎么看","type":"drill"},{"text":"PB 是什么","type":"diverge"}]}
            ```
        """.trimIndent()
    }

    private fun compareAnswer() = """
        贵州茅台与五粮液都受白酒行业景气影响，但估值、品牌结构和渠道节奏不同。先看两只标的当前状态，再比较长期指标。

        ```card:stock-compare
        {"symbol":"000858.SZ"}
        ```

        ```card:suggestions
        {"chips":[{"text":"比较 PE","type":"drill"},{"text":"为什么一起跌","type":"diverge"}]}
        ```
    """.trimIndent()
}
