package com.kuikly.stockchat.risk.ai.state

import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.risk.state.RiskScheduler
import com.kuikly.stockchat.risk.state.RiskScheduledTask
import com.kuikly.stockchat.risk.domain.sanitizeRiskAiText
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 卡底 AI 详细解读状态域唯一 owner（doc 47 B-3 第 4 步）：接管原 RiskMapPage 的
 * `skyAiState / skyAiText / skyAiError / skyAiModel` 四个 observable 与
 * `skyAiRequested / skyAiGeneration / skyAiProvider / skyAiTypewriter` 守卫。
 * 状态机与详情页/市场页 AI 卡同构：0 本地(未生成/未配置) / 1 thinking /
 * 2 streaming / 3 done / 4 error。
 *
 * 线程纪律（详情页同款，逐字保留）：provider 回调来自 Dispatchers.Default，
 * observable 只能在打字机主线程节拍与 scheduler 跳回主线程后写。
 * 兜底：12s 无首个增量如实落错（原 L862），generation 守卫逐值保留。
 */
/**
 * 打字机 Port：真实实现包 [TypewriterSmoother]（Kuikly Timer 节拍），
 * 单测注入 Immediate 驱动（与 detail/ai 的 AiInsightRevealer 同范式）。
 */
internal interface RiskAiTypewriter {
    fun append(delta: String)
    fun complete(onDone: () -> Unit)
    fun flushNow()
    fun cancel()
}

internal fun interface RiskAiTypewriterFactory {
    fun create(pagerId: String, onPublish: (String) -> Unit): RiskAiTypewriter
}

internal class PagerRiskAiTypewriterFactory : RiskAiTypewriterFactory {
    override fun create(pagerId: String, onPublish: (String) -> Unit): RiskAiTypewriter {
        val smoother = TypewriterSmoother(pagerId) { revealed -> onPublish(revealed) }
        return object : RiskAiTypewriter {
            override fun append(delta: String) = smoother.append(delta)
            override fun complete(onDone: () -> Unit) = smoother.complete(onDone)
            override fun flushNow() = smoother.flushNow()
            override fun cancel() = smoother.cancel()
        }
    }
}

internal class RiskAiCoordinator(
    private val host: RiskAiHost,
    private val scheduler: RiskScheduler,
    private val typewriterFactory: RiskAiTypewriterFactory = PagerRiskAiTypewriterFactory(),
) {
    /** 页面对 AI 域的输入视图：配置 / Provider 工厂 / 端侧事实槽位。 */
    internal interface RiskAiHost {
        fun pagerId(): String

        fun loadConfig(): AiConfig

        fun configValidationError(config: AiConfig): String?

        fun openStream(config: AiConfig): AiProvider

        /** 端侧事实槽位就绪（行情已到达至少一条）。 */
        fun factsReady(): Boolean
        /** prompt 组装（含事实槽位，本域不认识数据源）。 */
        fun buildPrompt(): String

        /** 端侧速览兜底文案。 */
        fun buildLocalSummary(): String
    }

    /** 0 本地(未生成/未配置) / 1 thinking / 2 streaming / 3 done / 4 error。 */
    var aiState: Int by observable(0)
        private set
    var aiText: String by observable("")
        private set
    var aiError: String by observable("")
        private set
    var aiModel: String by observable("")
        private set

    private var aiRequested = false
    private var aiGeneration = 0
    private var aiProvider: AiProvider? = null
    private var aiTypewriter: RiskAiTypewriter? = null

    /** 行情/事实就绪后自动请求一次（用户「底下直接有详细解读」）；失败可手动重试。 */
    fun maybeStart() {
        // 等至少一条真实行情到位再请求，避免页面首帧用空快照把一次请求机会消耗掉。
        if (aiRequested || !host.factsReady()) return
        aiRequested = true
        request()
    }

    fun actionLabel(): String = when (aiState) {
        1, 2 -> "停止"
        3 -> "重新解读"
        4 -> "重试"
        else -> "生成"
    }

    fun toggle() {
        if (aiState == 1 || aiState == 2) {
            aiProvider?.stop()
            aiGeneration++
            aiTypewriter?.flushNow()
            aiTypewriter?.cancel()
            aiTypewriter = null
            aiState = if (aiText.isNotBlank()) 3 else 0
        } else {
            aiRequested = true
            request()
        }
    }

    private fun request() {
        val config = host.loadConfig()
        val configError = host.configValidationError(config)
        if (configError != null) {
            // 未配置：保持端侧速览，不弹页跳转；来源行如实展示原因。
            aiState = 0
            aiError = "未配置 AI API（$configError）"
            return
        }
        aiProvider?.stop()
        aiTypewriter?.cancel()
        val generation = ++aiGeneration
        aiText = ""
        aiError = ""
        aiModel = config.model
        aiState = 1
        val provider = host.openStream(config)
        aiProvider = provider
        // 线程纪律（详情页同款）：provider 回调来自 Dispatchers.Default，observable
        // 只能在打字机主线程节拍与 setTimeout(0) 跳回主线程后写。
        var content = ""
        val smoother = typewriterFactory.create(host.pagerId()) { revealed ->
            if (generation != aiGeneration) return@create
            aiText = revealed
            if (aiState == 1 && revealed.isNotEmpty()) aiState = 2
        }
        aiTypewriter = smoother
        // 兜底：12s 无首个增量如实落错，避免永远停在思考态。
        scheduler.schedule(TIMEOUT_MS) {
            if (generation == aiGeneration && aiState == 1) {
                provider.stop()
                aiError = "请求超时（12 秒无响应），请重试"
                aiState = 4
            }
        }
        provider.ask(
            messages = listOf(AiChatMessage("user", host.buildPrompt())),
            onDelta = { delta ->
                content += delta
                if (generation == aiGeneration) smoother.append(delta)
            },
            onDone = {
                if (generation != aiGeneration) return@ask
                val fullContent = content
                smoother.complete {
                    scheduler.schedule(0) {
                        if (generation != aiGeneration) return@schedule
                        if (sanitizeRiskAiText(fullContent).isEmpty()) {
                            aiError = "接口未返回有效内容"
                            aiState = 4
                        } else {
                            aiState = 3
                        }
                    }
                }
            },
            onError = { message ->
                if (generation != aiGeneration) return@ask
                scheduler.schedule(0) {
                    if (generation != aiGeneration) return@schedule
                    smoother.flushNow()
                    smoother.cancel()
                    aiError = message
                    aiState = 4
                }
            },
        )
    }

    /** 未配置/失败时的端侧速览兜底内容（页面实现 host 时走 domain 纯函数）。 */
    fun localSummary(): String = host.buildLocalSummary()

    /** 供页面 vif 判断「已失败但已有部分正文」的展示分支。 */
    fun hasPartialText(): Boolean = aiText.isNotBlank()

    companion object {
        // 现状的超时是 12 秒（原 RiskMapPage L862），逐值保留。
        const val TIMEOUT_MS = 12000
    }
}
