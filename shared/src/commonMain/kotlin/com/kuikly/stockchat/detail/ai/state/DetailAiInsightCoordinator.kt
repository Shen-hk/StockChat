package com.kuikly.stockchat.detail.ai.state

/**
 * Detail 页 AI 解读域的唯一 owner（Wave 2 第 3 刀，见 docs/39 §9 / docs/43 D3）：
 * 合并主 AI 洞察（aiRemote*）与圈选区间 AI 解读（circleAi*）两套同构状态机为
 * 两个 [AiInsightSession] 实例；同时接管占位图 awaitingFacts 与端侧模板逐字
 * 显示的 revealLimit / revealSource。下游副作用（overlay/bandRange）不在本域
 * 执行（docs/43 D3：本域几乎不需要向外发效果）。
 *
 * 线程纪律：Provider 回调全部来自后台线程（[AiInsightSession] 已通过
 * [AiInsightRevealer] 节拍器与 [DetailAiHostPort.jumpToMain] 守住主线程写入，
 * 与原 StockDetailPage 注释的历史事故记录一致——onDelta 直写 observable 曾
 * 闪退 + 不刷新）。
 */
internal class DetailAiInsightCoordinator(
    private val state: DetailAiStatePort,
    private val host: DetailAiHostPort,
    private val scheduler: DetailAiScheduler,
    private val reduceMotion: Boolean,
    private val revealerFactory: AiInsightRevealerFactory = PagerAiInsightRevealerFactory(host.pagerId),
) {
    private val mainSession = AiInsightSession(
        tag = "main",
        state = MainSessionState(state),
        pagerId = { host.pagerId },
        loadConfig = { host.loadConfig() },
        configValidationError = { config -> host.configValidationError(config) },
        providerFactory = { config -> host.createProvider(config) },
        revealerFactory = revealerFactory,
        scheduler = scheduler,
        jumpToMain = { block -> host.jumpToMain(block) },
    )
    private val circleSession = AiInsightSession(
        tag = "circle",
        state = CircleSessionState(state),
        pagerId = { host.pagerId },
        loadConfig = { host.loadConfig() },
        configValidationError = { config -> host.configValidationError(config) },
        providerFactory = { config -> host.createProvider(config) },
        revealerFactory = revealerFactory,
        scheduler = scheduler,
        jumpToMain = { block -> host.jumpToMain(block) },
    )

    /** 端侧模板逐字显示的版本号（mirror）；自增即让旧 tick 链失效。 */
    private var revealVersion = 0
    /** 主 AI 是否已发起过至少一次请求（与原 aiRemoteRequested 等价，页面不直接读写）。 */
    private var mainRequested = false
    private val tasks = mutableListOf<DetailAiScheduledTask>()

    // ── 端侧模板逐字显示（reveal） ──

    /**
     * 端侧模板文字逐字显示（doc 26 §4.5）；reduceMotion 时直接全显示、不入帧。
     * 与原 `startAiReveal()` 等价：source 与已展示末尾一致则跳过；否则重置
     * revealSource + revealLimit=0，节拍 28ms/step、一次 +3 字符。
     */
    fun startReveal(source: String) {
        if (source == state.revealSource && state.revealLimit >= source.length) return
        state.revealSource = source
        val version = ++revealVersion
        // 文本已即时全显示（reduceMotion / 极短文本）：直接落终帧、退出
        if (source.isEmpty() || reduceMotion) {
            state.revealLimit = source.length
            return
        }
        state.revealLimit = 0
        schedule(0) {
            revealTick(source, version)
        }
    }

    private fun revealTick(source: String, version: Int) {
        if (version != revealVersion) return
        val next = (state.revealLimit + 3).coerceAtMost(source.length)
        state.revealLimit = next
        if (next < source.length) schedule(28) { revealTick(source, version) }
    }

    // ── 主 AI 解读 ──

    /**
     * 行情+洞察就绪后自动请求一次（与原 `maybeStartAiInsight` 等价）：
     * 已请求过 / 分时为空 / insight 仍在 loading → 不启动；否则置
     * awaitingFacts=false 并发起一次真实请求。
     */
    fun maybeStartAiInsight() {
        if (mainRequested) return
        if (host.quote().timeline.isEmpty()) return
        if (host.insight().loading) return
        mainRequested = true
        state.awaitingFacts = false
        requestAiInsight()
    }

    /**
     * 主动按钮：流式期间（state 1/2）→ [AiInsightSession.stop]；否则重新发起。
     * 与原 `toggleAiInsight()` 等价。
     */
    fun toggleInsight() {
        if (state.mainState == 1 || state.mainState == 2) {
            mainSession.stop()
        } else {
            requestAiInsight()
        }
    }

    /**
     * 真实 LLM 请求（与原 `requestAiInsight` 等价）：
     * 1. config 校验失败 → state=4 + "未配置 AI API（err）"，不发请求；
     * 2. 已通过 → 自增 generation、设 model、清 text/error、state=1；
     *    同步清句图联动（页面 created() / InsightLoaded 触发前可能残留区间带，
     *    字段 selectedSentence 在页面，bandRange 由 D2 Coordinator 清；本刀保持
     *    原语义，由页面在调用本方法前自行清 bandRange / selectedSentence）。
     */
    fun requestAiInsight() {
        val quote = host.quote()
        val insight = host.insight()
        val newsList = host.newsList()
        val timelineSeries = host.timelineSeries()
        mainSession.request(buildAiInsightPrompt(quote, insight, newsList, timelineSeries))
    }

    // ── 圈选 AI 解读 ──

    /**
     * 圈选松手 + 区间有效后由 D2 Coordinator 的 [DetailChartEffect.CircleSelectionCommitted]
     * 触发页面转发给本方法（docs/43 D2→D3 边界）。
     */
    fun requestCircleAi(lo: Int, hi: Int) {
        val quote = host.quote()
        val timelineSeries = host.timelineSeries()
        if (timelineSeries.size < 2) return
        circleSession.request(buildCircleAiPrompt(quote, timelineSeries, lo, hi))
    }

    /** 关气泡/换选时清圈选 AI 流（与原 `resetCircleAiStream` 等价）。 */
    fun cancelCircleAi() {
        circleSession.cancelStream()
    }

    /** 主会话当前状态；供页面 `aiActionLabel()` / toggleAiInsight 等组合查询。 */
    fun mainState(): Int = state.mainState

    // ── 占位图与生命周期 ──

    /**
     * 与原 `created()` 中的 4s awaitingFacts 兜底 setTimeout 等价：等极端弱网
     * 下一直收不到分时则回落端侧模板，不再无限显示骨架。已发起过主请求或已进入
     * thinking/streaming → 不再回退 awaitingFacts。
     */
    fun armAwaitingFactsFallback() {
        state.awaitingFacts = true
        schedule(AWAITING_FALLBACK_MS) {
            if (!mainRequested && state.mainState == 0) {
                state.awaitingFacts = false
            }
        }
    }

    /** 页面离开：中断进行中的两路流（保留已显示文本）+ 断 reveal 链。 */
    fun onDisappear() {
        revealVersion++
        mainSession.onDisappear()
        circleSession.onDisappear()
    }

    fun onDestroy() {
        revealVersion++
        mainSession.onDestroy()
        circleSession.onDestroy()
        tasks.forEach(DetailAiScheduledTask::cancel)
        tasks.clear()
    }

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }

    companion object {
        const val AWAITING_FALLBACK_MS = 4000
    }
}

/** 页面端 [DetailAiHostPort] 实现：在 StockDetailPage 内匿名 object：注册全部只读 getter。 */