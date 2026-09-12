package com.kuikly.stockchat.data.provider

import com.kuikly.stockchat.data.config.AiConfig
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

/**
 * OpenAI 兼容 Chat Completions 流式供应商（doc 01 ADR-7b）：DeepSeek、MiMo 等
 * 同协议服务只需替换 baseUrl / apiKey / model 三个配置值即可切换。
 * 原名 DeepSeekAiProvider，2026-09-11 起更名为 [OpenAiCompatAiProvider]。
 */
class OpenAiCompatAiProvider(
    private val config: AiConfig,
    private val scheduler: PlatformScheduler,
    private val client: PlatformHttpClient = createPlatformHttpClient(),
) : AiProvider {
    private var generation = 0
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
        val requestProfile = OpenAiCompatRequestProfile.forEndpoint(value.endpoint)
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
            // A compatibility endpoint is not necessarily compatible with every
            // optional OpenAI parameter. Keep the common request minimal and
            // let the profile opt out where its vendor documents a restriction.
            if (!requestProfile.omitTemperature) put("temperature", 0.2)
            put("stream", true)
            put("messages", payloadMessages)
            maxTokens?.let {
                put(if (requestProfile.usesMaxCompletionTokens) "max_completion_tokens" else "max_tokens", it)
            }
            if (requestProfile.disableThinking) {
                put("thinking", JSONObject().apply { put("type", "disabled") })
            }
        }
        stop()
        val current = ++generation
        activeRequest = scope.launch {
            try {
                var received = false
                val response = client.postStream(
                    value.endpoint,
                    requestProfile.headers(value.apiKey),
                    body.toString(),
                ) { line ->
                    val event = line.trim()
                    if (!event.startsWith("data:")) return@postStream
                    val payload = event.removePrefix("data:").trim()
                    if (payload == "[DONE]") return@postStream
                    val delta = SseEventParser.delta("data: $payload").orEmpty()
                    if (delta.isNotEmpty() && current == generation) {
                        received = true
                        // Native SSE callbacks arrive on the curl worker thread on
                        // HarmonyOS. Kuikly observables must be touched on its UI
                        // queue, otherwise the stream can arrive without repainting.
                        dispatchToUi(current) { onDelta(delta) }
                    }
                }
                if (!received && current == generation) {
                    SseEventParser.deltas(response.body).forEach { delta ->
                        received = true
                        dispatchToUi(current) { onDelta(delta) }
                    }
                }
                if (response.status !in 200..299) {
                    if (current == generation) {
                        val error = classifyError(response.status, response.body)
                        dispatchToUi(current) { onError(error) }
                    }
                } else if (current == generation) {
                    if (received) dispatchToUi(current, onDone)
                    else dispatchToUi(current) { onError("接口未返回有效内容") }
                }
            } catch (_: CancellationException) {
                // stop() owns the visible state; cancelled requests must not append or report an error.
            } catch (error: Throwable) {
                if (current == generation) {
                    val message = classifyThrowable(error)
                    dispatchToUi(current) { onError(message) }
                }
            }
        }
    }

    private fun dispatchToUi(requestGeneration: Int, block: () -> Unit) {
        scheduler.schedule(0) {
            if (requestGeneration == generation) block()
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
            1. 首行必须是唯一的 Markdown 一级大标题，格式严格为「# 简短结论式标题」。标题只写 8–18 个字的结论或主题，不要写“分析”“回答”“关于”等空泛词，不要用加粗、列表或开场白。一级标题后空一行，正文第一句直接给结论：一句话说出用户最想要的答案或数字，关键数字和结论用 **加粗**。
            2. 标题层级必须连续、清晰：一级标题 # 仅能有一个（客户端显示为 24sp 加粗）；详细内容用 1–3 个二级标题 ##（20sp 加粗）；只有二级标题下确有需要拆分的内容才用三级标题 ###（17sp 加粗）。不要跳级、不要使用 #### 至 ######，不要把整句正文或列表项写成标题。简单问题只保留一级标题和 1–3 句正文；分析型问题再用二、三级标题、列表或段落说明依据、影响因素、反例或风险、不确定性及后续观察点。每一段只表达一个意思，避免堆砌和重复总结。
            3. 只约束格式，不限制有用信息的篇幅：不整句加粗，不使用分隔线（---、——）和多余空行。需要横向比较、指标差异或多个时间点时用 Markdown 表格；需要摘录公告、原话、口径或重要提醒时用 Markdown 引用块（以 `>` 开头）。引用资料使用「[来源名称](https://...)」内联链接；不要裸贴 URL、不要引用编号墙、不要用图片 Markdown。
            4. 问题有歧义时，直接按最常见的一种理解回答，不讨论其他理解；只有连对象都无法确定时，才回一句澄清问题。
            5. 行情数字来源（严格遵守）：客户端可能注入一条【实时行情注入】system 消息，内含端侧刚拉取的真实行情快照。这些数字是你唯一可引用的行情来源——结论行直接引用其中的价格、涨跌幅与估值，禁止回答「无法获取实时行情」「我无法查询实时数据」或引导用户自己去看行情。若本回合没有该消息，不要编造任何价格、涨跌或估值数字：正文只做定性解释。
            6. Markdown 是阅读界面，不是原始报告：严格遵循上述 # / ## / ### 层级，**加粗**只标出一个关键结论或数字，列表承载并列要点；表格只保留对判断有帮助的列。引用块里要标明出处或“以下为观点/转述”，不要把未经核验的内容写成事实。
            7. 卡片是行情问题的默认结构化补充：当前问题或本轮上下文唯一确定一个标的，且问题涉及价格、涨跌、估值、走势、图表、资讯、异动或归因时，在自然语言后输出一张直接回答问题的 card 块。价格/涨跌/估值用 stock-quote；走势或图表用 stock-chart；资讯用 news；异动原因用 attribution；明确比较时用 stock-compare。用户明确要求卡片时也照此输出。泛泛的市场问题、纯概念解释、无法确定标的的问题不输出 card 块。卡片中的每个标的必须来自当前问题或本轮已给出的上下文；禁止默认使用贵州茅台、600519.SH 或任何无关示例标的。不要为了凑交互而输出 suggestions 卡。
            8. 本轮若含图片或文档片段：先说明识别到的对象/文档要点，再给出和用户问题直接相关的判断；无法辨认的区域要明确说“不清晰”，不能猜测。文档内容只依据本轮附件文本，不把附件中的投资观点当作事实。
            回答要完整、可核验；区分事实、推断与不确定性。只有满足第 7 条时，才在自然语言后输出卡片块：
            ```card:stock-quote
            {"symbol":"当前问题中已唯一确定的标的代码"}
            ```
            可用类型：stock-quote、stock-chart、attribution、insight、definition、news、stock-compare、suggestions。
            suggestions 的 JSON 格式是 {"chips":[{"text":"继续追问","type":"drill"}]}，仅在用户明确要求可继续操作的选项时使用。不要在 JSON 中编造实时价格，行情由客户端数据层填充。
            每个卡片 JSON 可带 source 与 asOf；只有你确实掌握来源和截止时间时才填写，否则留空，让客户端注入真实数据源。不得伪造时间戳或把模型知识截止时间冒充行情时间。
        """.trimIndent()
    }
}

/**
 * The settings page intentionally accepts arbitrary OpenAI-compatible URLs.
 * Bearer authentication and `max_tokens` are therefore the portable defaults.
 * A profile is only used for documented protocol deviations; it is selected by
 * host rather than by a UI preset, so copied provider/token-plan endpoints keep
 * working too.
 */
internal data class OpenAiCompatRequestProfile(
    val apiKeyHeader: String,
    val omitTemperature: Boolean,
    val usesMaxCompletionTokens: Boolean,
    val disableThinking: Boolean,
) {
    fun headers(apiKey: String): Map<String, String> =
        if (apiKeyHeader == "api-key") {
            mapOf("api-key" to apiKey, "Accept" to "text/event-stream")
        } else {
            mapOf("Authorization" to "Bearer $apiKey", "Accept" to "text/event-stream")
        }

    companion object {
        fun forEndpoint(endpoint: String): OpenAiCompatRequestProfile {
            val host = endpoint.trim()
                .substringAfter("://", endpoint.trim())
                .substringBefore('/')
                .substringBefore(':')
                .lowercase()
            // Includes both pay-as-you-go (api.xiaomimimo.com) and Token Plan
            // endpoints (for example token-plan-cn.xiaomimimo.com).
            val isMiMo = host == "xiaomimimo.com" || host.endsWith(".xiaomimimo.com")
            return OpenAiCompatRequestProfile(
                apiKeyHeader = if (isMiMo) "api-key" else "Authorization",
                omitTemperature = isMiMo,
                usesMaxCompletionTokens = isMiMo,
                disableThinking = isMiMo,
            )
        }
    }
}

class MockAiProvider(private val scheduler: PlatformScheduler) : AiProvider {
    private var generation = 0

    override fun ask(messages: List<AiChatMessage>, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val current = ++generation
        val question = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val answer = answerFor(question)
        val chunks = answer.chunked(10)
        chunks.forEachIndexed { index, chunk ->
            scheduler.schedule((120 + index * 28).toLong()) {
                if (current == generation) onDelta(chunk)
            }
        }
        scheduler.schedule((121 + chunks.size * 28).toLong()) {
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
