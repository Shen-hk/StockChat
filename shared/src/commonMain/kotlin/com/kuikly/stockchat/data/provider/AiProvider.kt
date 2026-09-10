package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.data.config.AiConfig
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.setTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface AiProvider {
    fun ask(messages: List<AiChatMessage>, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit)
    fun stop()
}

class DeepSeekAiProvider(
    override val pagerId: String,
    private val config: AiConfig,
) : AiProvider, PagerScope {
    private var generation = 0
    private val client = createPlatformHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeRequest: Job? = null

    override fun ask(messages: List<AiChatMessage>, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) =
        requestStream(messages, null, onDelta, onDone, onError)

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        var content = ""
        requestStream(
            messages = listOf(AiChatMessage("user", "只回复 OK")),
            maxTokens = 8,
            onDelta = { content += it },
            onDone = { onResult(true, "连接成功，模型返回：${content.trim().take(40)}") },
            onError = { onResult(false, it) },
            systemPrompt = "你是 API 连通性检测助手。",
        )
    }

    override fun stop() {
        activeRequest?.cancel()
        generation++
    }

    private fun requestStream(
        messages: List<AiChatMessage>,
        maxTokens: Int?,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        systemPrompt: String = SYSTEM_PROMPT,
    ) {
        val value = config.normalized()
        value.validationError()?.let {
            onError(it)
            return
        }
        val payloadMessages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("role", message.role)
                    if (message.media.isEmpty()) {
                        put("content", message.content)
                    } else {
                        // OpenAI-compatible multimodal schema. MiMo V2.5 consumes image_url
                        // natively; users should select a vision-capable model for other APIs.
                        put("content", JSONArray().apply {
                            put(JSONObject().apply { put("type", "text"); put("text", message.content) })
                            message.media.forEach { part ->
                                part.imageDataUrl?.let { dataUrl ->
                                    put(JSONObject().apply {
                                        put("type", "image_url")
                                        put("image_url", JSONObject().apply { put("url", dataUrl) })
                                    })
                                }
                                part.documentText?.takeIf { it.isNotBlank() }?.let { text ->
                                    put(JSONObject().apply {
                                        put("type", "text")
                                        put("text", "【附件：${part.name}】\n$text")
                                    })
                                }
                            }
                        })
                    }
                })
            }
        }
        val body = JSONObject().apply {
            put("model", value.model)
            put("temperature", 0.2)
            put("stream", true)
            put("messages", payloadMessages)
            maxTokens?.let { put("max_tokens", it) }
        }
        stop()
        val current = ++generation
        activeRequest = scope.launch {
            try {
                var received = false
                val response = client.postStream(
                    value.endpoint,
                    mapOf(
                        "Authorization" to "Bearer ${value.apiKey}",
                        "Accept" to "text/event-stream",
                    ),
                    body.toString(),
                ) { line ->
                    val event = line.trim()
                    if (!event.startsWith("data:")) return@postStream
                    val payload = event.removePrefix("data:").trim()
                    if (payload == "[DONE]") return@postStream
                    val delta = SseEventParser.delta("data: $payload").orEmpty()
                    if (delta.isNotEmpty() && current == generation) {
                        received = true
                        onDelta(delta)
                    }
                }
                if (response.status !in 200..299) {
                    if (current == generation) onError(classifyError(response.status, response.body))
                } else if (current == generation) {
                    if (received) onDone() else onError("接口未返回有效内容")
                }
            } catch (_: CancellationException) {
                // stop() owns the visible state; cancelled requests must not append or report an error.
            } catch (error: Throwable) {
                if (current == generation) onError(classifyThrowable(error))
            }
        }
    }

    private fun classifyError(status: Int, body: String): String {
        val apiMessage = try { JSONObject(body).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Throwable) { "" }
        val summary = when (status) {
            401, 403 -> "鉴权失败，请检查 API Key"
            402 -> "额度不足或账户不可用"
            408, 504 -> "请求超时，请稍后重试"
            429 -> "请求过于频繁或额度已耗尽，请稍后重试"
            in 500..599 -> "模型服务暂时不可用，请稍后重试"
            else -> "请求失败（HTTP $status）"
        }
        return if (apiMessage.isEmpty()) summary else "$summary：$apiMessage"
    }

    private fun classifyThrowable(error: Throwable): String {
        val detail = error.message.orEmpty()
        return if (detail.contains("timeout", ignoreCase = true)) "请求超时，请检查网络后重试" else "网络连接失败：${detail.ifEmpty { "请检查网络后重试" }}"
    }

    companion object {
        private val SYSTEM_PROMPT = """
            你是面向中文个人投资者的股票解释助手。只做信息解释，不预测收益，不给出买入、卖出或仓位建议。
            输出形式（必须严格遵守）：
            1. 第一行直接给结论：一句话说出用户最想要的答案或数字，关键数字和结论用 **加粗**。禁止任何开场白，禁止复述或转述用户的问题（不要出现「您问的是…」「关于这个问题…」「我来帮你分析」），也不要解释你是如何理解这个问题的。
            2. 结论之后才分层展开：最多 2 个「### 小节标题」（标题不超过 8 个字），每节最多 3 行或 3 个要点。不是每个回答都需要小节——一句话能答清的就只给结论行，最多补一行说明。
            3. 只约束格式，不限制篇幅：不整句加粗，不使用分隔线（---、——）和多余空行，只有确实需要横向对比时才用表格。该长则长、该短则短，篇幅由问题本身决定，与问题无关的铺垫和总结一律不写。
            4. 问题有歧义时，直接按最常见的一种理解回答，不讨论其他理解；只有连对象都无法确定时，才回一句澄清问题。
            5. 行情数字来源（严格遵守）：客户端可能注入一条【实时行情注入】system 消息，内含端侧刚拉取的真实行情快照。这些数字是你唯一可引用的行情来源——结论行直接引用其中的价格、涨跌幅与估值，禁止回答「无法获取实时行情」「我无法查询实时数据」或引导用户自己去看行情。若本回合没有该消息，不要编造任何价格、涨跌或估值数字：正文只做定性解释，让 stock-quote 等卡片由客户端填充具体数字。
            6. Markdown 是阅读界面，不是原始报告：使用 ##/### 做短标题、**加粗**标出一个关键结论、列表只承载并列要点。引用资料时使用「[来源名称](https://...)」内联链接；不要裸贴 URL、不要引用编号墙、不要用图片 Markdown。涉及行情、走势、财报或对比时优先输出对应的 card 块，由客户端渲染成统一图表；正文只解释图表读法与结论。
            7. 本轮若含图片或文档片段：先说明识别到的对象/文档要点，再给出和用户问题直接相关的判断；无法辨认的区域要明确说“不清晰”，不能猜测。文档内容只依据本轮附件文本，不把附件中的投资观点当作事实。
            回答要简洁、可核验；区分事实、推断与不确定性。需要结构化内容时，在自然语言后输出卡片块：
            ```card:stock-quote
            {"symbol":"600519.SH"}
            ```
            可用类型：stock-quote、stock-chart、attribution、insight、definition、news、stock-compare、suggestions。
            suggestions 的 JSON 格式是 {"chips":[{"text":"继续追问","type":"drill"}]}。不要在 JSON 中编造实时价格，行情由客户端数据层填充。
            每个卡片 JSON 可带 source 与 asOf；只有你确实掌握来源和截止时间时才填写，否则留空，让客户端注入真实数据源。不得伪造时间戳或把模型知识截止时间冒充行情时间。
        """.trimIndent()
    }
}

class MockAiProvider(override val pagerId: String) : AiProvider, PagerScope {
    private var generation = 0

    override fun ask(messages: List<AiChatMessage>, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val current = ++generation
        val question = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
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
            "资讯" in question || "新闻" in question || "公告" in question -> newsAnswer()
            "为什么" in question || "原因" in question || "跌" in question || "涨" in question -> attributionAnswer()
            "走势" in question || "k线" in normalized || "分时" in question -> chartAnswer()
            else -> overviewAnswer()
        }
    }

    private fun overviewAnswer() = """
        贵州茅台当前处于**偏弱震荡**，价格变化只是结果，更值得关注成交、板块联动和后续公告。

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
        ## 波动归因

        这次波动更像是多因素叠加，**资金面贡献最大**，板块联动次之。以下是解释框架，不是买卖建议。

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

    private fun newsAnswer() = """
        资讯更适合放在不占聊天流的位置阅读。这里用底部 Sheet 承接完整列表，关闭后聊天位置保持不变。

        ```card:news
        {"symbol":"600519.SH"}
        ```

        ```card:suggestions
        {"chips":[{"text":"为什么跌","type":"drill"},{"text":"和五粮液比较","type":"diverge"}]}
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
