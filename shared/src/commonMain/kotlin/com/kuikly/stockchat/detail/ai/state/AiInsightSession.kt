package com.kuikly.stockchat.detail.ai.state

import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.chat.TypewriterSmoother

/**
 * 主 / 圈选两套 AI 流式会话的可复用状态机单元（docs/43 D3）：
 * - generation 自增使同会话前后两次请求互不串；
 * - Provider 回调来自后台线程，observable / Revealer 写入须经 Revealer 节拍器
 *   （页面的 TypewriterSmoother）或 [DetailAiHostPort.jumpToMain]（即原页面
 *   setTimeout(0) 跳回主线程）；
 * - 12s 首字节超时未到 → 如实落 state=4（"请求超时（12 秒无响应），请重试"）
 *   并停 provider；已进流式则交给 onDone / onError 收尾。
 *
 * 两路会话独立持有 generation / provider / revealer，互不耦合。
 */
internal class AiInsightSession internal constructor(
    private val tag: String,
    private val state: SessionState,
    private val pagerId: () -> String,
    private val loadConfig: () -> com.kuikly.stockchat.data.config.AiConfig,
    private val configValidationError: (com.kuikly.stockchat.data.config.AiConfig) -> String?,
    private val providerFactory: (com.kuikly.stockchat.data.config.AiConfig) -> com.kuikly.stockchat.data.provider.AiProvider,
    private val revealerFactory: AiInsightRevealerFactory,
    private val scheduler: DetailAiScheduler,
    private val jumpToMain: (() -> Unit) -> Unit,
) {
    private var generation = 0
    private var provider: com.kuikly.stockchat.data.provider.AiProvider? = null
    private var revealer: AiInsightRevealer? = null

    /**
     * 发起一次请求。已配置校验失败 → state=4 + 错误文案，不发请求；
     * 已通过 → 自增 generation、清 text/error、设 model=配置 model、state=1，
     * 启动 provider 与 12s 超时守卫。
     */
    fun request(prompt: String) {
        val config = loadConfig()
        val error = configValidationError(config)
        if (error != null) {
            state.setText("")
            state.setError("未配置 AI API（$error）")
            state.setModel("")
            state.setState(4)
            return
        }
        provider?.stop()
        revealer?.cancel()
        val gen = ++generation
        state.setText("")
        state.setError("")
        state.setModel(config.model)
        state.setState(1)
        val provider = providerFactory(config)
        this.provider = provider
        var content = ""
        val revealer = revealerFactory.create { revealed ->
            if (gen != generation) return@create
            state.setText(revealed)
            if (state.snapshotState() == 1 && revealed.isNotEmpty()) state.setState(2)
        }
        this.revealer = revealer
        // 12s 首字节兜底（同原 requestAiInsight / requestCircleAi）；已进流式则交给 onDone / onError
        scheduler.schedule(TIMEOUT_MS) {
            if (gen == generation && state.snapshotState() == 1) {
                provider.stop()
                state.setError("请求超时（12 秒无响应），请重试")
                state.setState(4)
            }
        }
        provider.ask(
            messages = listOf(AiChatMessage("user", prompt)),
            onDelta = { delta ->
                content += delta
                if (gen == generation) revealer.append(delta)
            },
            onDone = {
                if (gen != generation) return@ask
                val fullContent = content
                // 显示端把已收到的文本打完再落定（收尾回调由节拍器在主线程触发）
                revealer.complete {
                    jumpToMain {
                        if (gen != generation) return@jumpToMain
                        if (sanitizeAiText(fullContent).isEmpty()) {
                            state.setError("接口未返回有效内容")
                            state.setState(4)
                        } else {
                            state.setState(3)
                        }
                    }
                }
            },
            onError = { message ->
                if (gen != generation) return@ask
                // 出错也把已收到的部分流式文本放出来，再如实标错（跳回主线程写状态）
                jumpToMain {
                    if (gen != generation) return@jumpToMain
                    revealer.flushNow()
                    revealer.cancel()
                    state.setError(message)
                    state.setState(4)
                }
            },
        )
    }

    /** 用户主动停止生成：保留已显示文本，落 state=3（有文本）或 state=0（无）。 */
    fun stop() {
        if (state.snapshotState() == 1 || state.snapshotState() == 2) {
            provider?.stop()
            generation++
            revealer?.flushNow()
            revealer?.cancel()
            revealer = null
            state.setState(if (state.snapshotText().isNotBlank()) 3 else 0)
        }
    }

    /** 彻底中断当前会话（用于气泡切换、新请求覆盖、页面离开前的中断）：清 text/error，state=0。 */
    fun cancelStream() {
        provider?.stop()
        revealer?.cancel()
        revealer = null
        generation++
        state.setText("")
        state.setError("")
        state.setState(0)
    }

    /** 页面离开：中断进行中的流（generation 失效使残留回调全部 no-op）但保留已显示文本。 */
    fun onDisappear() {
        provider?.stop()
        revealer?.cancel()
        revealer = null
        generation++
        if (state.snapshotState() == 1 || state.snapshotState() == 2) {
            state.setState(if (state.snapshotText().isNotBlank()) 3 else 0)
        }
    }

    fun onDestroy() {
        provider?.stop()
        revealer?.cancel()
        revealer = null
        generation++
    }

    companion object {
        const val TIMEOUT_MS = 12000
    }
}

/** 单会话的状态写入适配（同一 StatePort 上挂两套会话，主/圈选分别走各自的 setter）。 */
internal interface SessionState {
    fun setState(value: Int)
    fun setText(value: String)
    fun setError(value: String)
    fun setModel(value: String)
    fun snapshotState(): Int
    fun snapshotText(): String
}

internal class MainSessionState(private val port: DetailAiStatePort) : SessionState {
    override fun setState(value: Int) { port.mainState = value }
    override fun setText(value: String) { port.mainText = value }
    override fun setError(value: String) { port.mainError = value }
    override fun setModel(value: String) { port.mainModel = value }
    override fun snapshotState() = port.mainState
    override fun snapshotText() = port.mainText
}

internal class CircleSessionState(private val port: DetailAiStatePort) : SessionState {
    override fun setState(value: Int) { port.circleState = value }
    override fun setText(value: String) { port.circleText = value }
    override fun setError(value: String) { port.circleError = value }
    override fun setModel(value: String) { port.circleModel = value }
    override fun snapshotState() = port.circleState
    override fun snapshotText() = port.circleText
}

/** 页面用的 Revealer 工厂：把 Chat TypewriterSmoother 包成 [AiInsightRevealer]。 */
internal class PagerAiInsightRevealerFactory(
    private val pagerId: String,
) : AiInsightRevealerFactory {
    override fun create(onRevealed: (String) -> Unit): AiInsightRevealer {
        val smoother = TypewriterSmoother(pagerId, onRevealed)
        return object : AiInsightRevealer {
            override fun append(delta: String) = smoother.append(delta)
            override fun complete(onComplete: () -> Unit) = smoother.complete(onComplete)
            override fun cancel() = smoother.cancel()
            override fun flushNow() = smoother.flushNow()
        }
    }
}