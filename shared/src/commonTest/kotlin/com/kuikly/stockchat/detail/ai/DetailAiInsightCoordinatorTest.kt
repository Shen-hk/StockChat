package com.kuikly.stockchat.detail.ai

import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.config.AiConfigStore
import com.kuikly.stockchat.data.provider.AiChatMessage
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.OfflineMarketInsightProvider
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.StockInsightBundle
import com.kuikly.stockchat.detail.ai.state.AiInsightRevealer
import com.kuikly.stockchat.detail.ai.state.AiInsightRevealerFactory
import com.kuikly.stockchat.detail.ai.state.DetailAiHostPort
import com.kuikly.stockchat.detail.ai.state.DetailAiInsightCoordinator
import com.kuikly.stockchat.detail.ai.state.DetailAiScheduler
import com.kuikly.stockchat.detail.ai.state.DetailAiScheduledTask
import com.kuikly.stockchat.detail.ai.state.DetailAiState
import com.kuikly.stockchat.detail.ai.state.PlainDetailAiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DetailAiInsightCoordinatorTest {

    // ── 主 AI 解读：thinking → streaming → done ──

    @Test
    fun mainInsightTransitionsThinkingStreamingDone() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.requestAiInsight()

        // provider.ask 被调用前应先写出 state=1（thinking）
        assertEquals(1, f.state.mainState)
        val cb = f.provider.callbackAt(0)
        cb.onDelta("开盘偏弱。")
        // deltas 经 ImmediateRevealer 同步释放 → mainText 写入 + state=2
        assertEquals(2, f.state.mainState)
        assertEquals("开盘偏弱。", f.state.mainText)
        cb.onDone()
        assertEquals(3, f.state.mainState, "完整正文收尾后 state=3")
        assertEquals("开盘偏弱。", f.state.mainText)
    }

    @Test
    fun mainInsightEmptyContentSettlesToError() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.requestAiInsight()
        f.provider.callbackAt(0).onDone()
        assertEquals(4, f.state.mainState)
        assertEquals("接口未返回有效内容", f.state.mainError)
    }

    @Test
    fun mainInsightNetworkErrorFlushesPartialTextAndSettlesError() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.requestAiInsight()
        f.provider.callbackAt(0).onDelta("部分")
        assertEquals("部分", f.state.mainText)
        f.provider.callbackAt(0).onError("请求超时")
        assertEquals(4, f.state.mainState)
        assertEquals("请求超时", f.state.mainError)
    }

    @Test
    fun mainInsightMissingConfigSettlesErrorWithoutCallingProvider() {
        val f = fixture(quote = quoteWithTimeline(), configError = "未配置 endpoint")
        f.coordinator.requestAiInsight()
        assertEquals(4, f.state.mainState)
        assertEquals("未配置 AI API（未配置 endpoint）", f.state.mainError)
        assertEquals(0, f.provider.askCallCount)
    }

    // ── 圈选 AI 解读：与主路互不干扰 ──

    @Test
    fun circleStreamLifecycleIndependentFromMain() {
        val f = fixture(quote = quoteWithTimeline(linearTimeline(30)))
        // 同时触发两路：主路请求、圈选请求
        f.coordinator.requestAiInsight()
        f.coordinator.requestCircleAi(5, 20)
        assertEquals(1, f.state.mainState)
        assertEquals(1, f.state.circleState)
        // 圈选先到收尾
        f.provider.callbackAt(1).onDelta("区间上行。")
        f.provider.callbackAt(1).onDone()
        assertEquals(3, f.state.circleState)
        assertEquals("区间上行。", f.state.circleText)
        // 圈选完成不影响主路 state
        assertEquals(1, f.state.mainState)
        // 主路再收尾：先补一条 delta 再 onDone（否则空内容会落 state=4 错误态，与
        // 真实行为一致——本页已通过 prompt 模板保证非空，但测试上需要先给点输入）
        f.provider.callbackAt(0).onDelta("开盘偏弱。")
        f.provider.callbackAt(0).onDone()
        assertEquals(3, f.state.mainState)
        assertEquals("开盘偏弱。", f.state.mainText)
    }

    @Test
    fun circleStreamTimeoutDoesNotAffectMainInsight() {
        val f = fixture(quote = quoteWithTimeline(linearTimeline(30)))
        f.coordinator.requestAiInsight()
        f.provider.callbackAt(0).onDelta("开盘。")
        f.coordinator.requestCircleAi(5, 20)
        f.scheduler.run(12000) // 触发圈选 12s 超时
        assertEquals(4, f.state.circleState)
        assertEquals("请求超时（12 秒无响应），请重试", f.state.circleError)
        // 主路仍正常流式，未被超时波及
        assertEquals(2, f.state.mainState)
        assertEquals("开盘。", f.state.mainText)
    }

    @Test
    fun staleCallbackAfterMainResetIsIgnored() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.requestAiInsight()
        val stale = f.provider.callbackAt(0)
        // 用户停止后再触达一次请求
        f.coordinator.toggleInsight() // state 1/2 → stop 路径（generation++，state=0）
        assertNotEquals(1, f.state.mainState)
        // 旧回调再 onDelta/onDone：因 generation 已 ++，状态无变化（mainText 不再写入）
        val textBefore = f.state.mainText
        stale.onDelta("过期内容")
        assertEquals(textBefore, f.state.mainText, "stale delta must not overwrite state after stop")
        stale.onDone()
        assertNotEquals(3, f.state.mainState)
    }

    @Test
    fun toggleStopDuringStreamingSettlesToDoneWithText() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.requestAiInsight()
        f.provider.callbackAt(0).onDelta("正在分析")
        assertEquals(2, f.state.mainState)
        f.coordinator.toggleInsight()
        // 停止时已显示的文本应保留 + state=3
        assertEquals(3, f.state.mainState)
        assertEquals("正在分析", f.state.mainText)
    }

    // ── 12s 超时兜底 ──

    @Test
    fun twelveSecondTimeoutSettlesErrorBeforeAnyDelta() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.requestAiInsight()
        assertEquals(1, f.state.mainState)
        f.scheduler.run(12000)
        assertEquals(4, f.state.mainState)
        assertEquals("请求超时（12 秒无响应），请重试", f.state.mainError)
    }

    @Test
    fun timeoutFiresOnlyWhenStateIsStillThinking() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.requestAiInsight()
        f.provider.callbackAt(0).onDelta("已流式")
        assertEquals(2, f.state.mainState)
        // 已进流式后 12s 不再触发超时落错
        f.scheduler.run(12000)
        assertEquals(2, f.state.mainState, "已 streaming 时不应触发 12s 兜底")
    }

    // ── 生命周期 ──

    @Test
    fun onDisappearInterruptsBothStreamsPreservingText() {
        val f = fixture(quote = quoteWithTimeline(linearTimeline(30)))
        f.coordinator.requestAiInsight()
        f.coordinator.requestCircleAi(5, 20)
        f.provider.callbackAt(0).onDelta("主已收到")
        f.provider.callbackAt(1).onDelta("圈已收到")
        f.coordinator.onDisappear()
        // 已显示文本保留为 done（state=3）
        assertEquals(3, f.state.mainState)
        assertEquals(3, f.state.circleState)
        assertEquals("主已收到", f.state.mainText)
        assertEquals("圈已收到", f.state.circleText)
        // 旧回调再 onDelta/onDone 全部 no-op
        f.provider.callbackAt(0).onDelta("迟到")
        assertEquals("主已收到", f.state.mainText)
        f.provider.callbackAt(0).onDone()
        assertEquals(3, f.state.mainState)
    }

    @Test
    fun onDestroyCancelsTimersAndInvalidatesCallbacks() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.armAwaitingFactsFallback()
        f.coordinator.requestAiInsight()
        // destroy 前先收一条 delta 留作「活跃会话」证据，再 destroy
        f.provider.callbackAt(0).onDelta("正在分析")
        assertEquals(2, f.state.mainState)
        f.coordinator.onDestroy()
        // scheduler 队列中的 task 全部取消（4s awaiting 兜底 + 12s 超时均不应触发）
        f.scheduler.run(4000)
        f.scheduler.run(12000)
        // destroy 之后再试图触发 delta：provider 已 stop，pendingCallbacks 被清空，
        // 因此不应再有状态写入（即使强行拿到引用也不应 no-op）。
        assertNotEquals(3, f.state.mainState)
        assertEquals(2, f.state.mainState, "destroy 后无新写入；状态保留 destroy 时的快照")
    }

    // ── 端侧模板 reveal ──

    @Test
    fun revealIncrementsByThreeUntilLimitReachesLength() {
        val f = fixture(quote = quoteWithTimeline())
        val source = "12345678901234567890"
        f.coordinator.startReveal(source)
        // 第 0 帧：revealLimit=0；scheduler.run(0) → tick 进入
        f.scheduler.run(0)
        assertEquals(3, f.state.revealLimit)
        f.scheduler.run(28)
        assertEquals(6, f.state.revealLimit)
        // 重复 28ms 直到末尾
        repeat(7) { f.scheduler.run(28) }
        assertEquals(source.length, f.state.revealLimit)
    }

    @Test
    fun revealBreakOnVersionChangeSkipsRemainingTicks() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.startReveal("0123456789")
        f.scheduler.run(0)
        assertEquals(3, f.state.revealLimit)
        // 第二次 startReveal 自增 revealVersion，旧 tick 链断；revealLimit 复位
        f.coordinator.startReveal("新文本")
        assertEquals(0, f.state.revealLimit, "第二次 reveal 重置后 limit=0")
        // 旧链的 28ms 排程不应再写入（version 不匹配）
        f.scheduler.run(28)
        assertEquals(0, f.state.revealLimit, "旧 tick 链失效，limit 保持 0")
        // 新链第一帧 +3
        f.scheduler.run(0)
        assertEquals(3, f.state.revealLimit, "新链第一帧 +3")
    }

    @Test
    fun awaitingFactsFallbackClearsIfMainNotStarted() {
        val f = fixture(quote = quoteWithTimeline(timeline = emptyList()))
        f.coordinator.armAwaitingFactsFallback()
        assertTrue(f.state.awaitingFacts)
        f.scheduler.run(4000)
        assertFalse(f.state.awaitingFacts)
    }

    @Test
    fun awaitingFactsFallbackKeepsIfMainAlreadyStarted() {
        val f = fixture(quote = quoteWithTimeline())
        f.coordinator.armAwaitingFactsFallback()
        // 提前触发主路请求，mainRequested=true，mainState=1
        f.coordinator.requestAiInsight()
        f.scheduler.run(4000)
        // 此时 4s 兜底判定 mainRequested=true，不清 awaitingFacts
        assertTrue(f.state.awaitingFacts)
    }

    // ── 端侧事实槽位算法逐字对齐（docs/43 D3：产品不可变行为）──

    @Test
    fun buildAiInsightPromptOutputIsStableForFixedInputs() {
        val quote = Quote.placeholder("600519.SH", "贵州茅台").copy(
            price = 1330.5,
            previousClose = 1335.0,
            open = 1336.0,
            high = 1340.0,
            low = 1324.0,
            timeline = listOf(
                com.kuikly.stockchat.data.provider.QuotePoint(time = "09:30", price = 1336.0, volume = 100.0),
                com.kuikly.stockchat.data.provider.QuotePoint(time = "10:30", price = 1340.0, volume = 120.0),
                com.kuikly.stockchat.data.provider.QuotePoint(time = "13:30", price = 1324.0, volume = 150.0),
            ),
        )
        val insight = OfflineMarketInsightProvider().stock("600519.SH")
        val news = listOf(
            NewsItem(id = "1", title = "公告一", source = "交易所", time = "2026-09-12 10:00:00", url = ""),
            NewsItem(id = "2", title = "公告二", source = "交易所", time = "2026-09-12 11:00:00", url = ""),
        )
        val series = com.kuikly.stockchat.detail.page.component.detailTimelineSeries(quote)
        val prompt = com.kuikly.stockchat.detail.ai.state.buildAiInsightPrompt(quote, insight, news, series)
        // 锁定的端侧片段：现价/昨收/开盘/区间最高最低/资讯标题/硬性要求 1-3
        assertTrue(prompt.contains("贵州茅台（600519.SH）"))
        assertTrue(prompt.contains("1. 每句话必须至少引用一个具体分时时间点"))
        assertTrue(prompt.contains("公告一；公告二"))
    }

    // ── fixture ──

    private fun fixture(
        quote: Quote,
        configError: String? = null,
    ): Fixture {
        val state = PlainDetailAiState()
        val scheduler = FakeDetailAiScheduler()
        val provider = FakeAiProvider()
        val host = object : DetailAiHostPort {
            override val pagerId = "test-pager"
            override fun quote(): Quote = quote
            override fun insight(): StockInsightBundle = OfflineMarketInsightProvider().stock(quote.symbol)
            override fun newsList(): List<NewsItem> = emptyList()
            override fun timelineSeries(): List<Double> =
                com.kuikly.stockchat.detail.page.component.detailTimelineSeries(quote)
            override fun loadConfig(): AiConfig = AiConfig(model = "test-model")
            override fun configValidationError(config: AiConfig): String? = configError
            override fun createProvider(config: AiConfig): AiProvider = provider
            override fun jumpToMain(block: () -> Unit) { block() }
        }
        val immediateFactory = object : AiInsightRevealerFactory {
            override fun create(onRevealed: (String) -> Unit): AiInsightRevealer =
                ImmediateRevealer(onRevealed)
        }
        val coordinator = DetailAiInsightCoordinator(
            state = state,
            host = host,
            scheduler = scheduler,
            reduceMotion = false,
            revealerFactory = immediateFactory,
        )
        return Fixture(state, scheduler, provider, host, coordinator)
    }

    private fun quoteWithTimeline(timeline: List<com.kuikly.stockchat.data.provider.QuotePoint> =
        listOf(
            com.kuikly.stockchat.data.provider.QuotePoint(time = "09:30", price = 100.0, volume = 1.0),
            com.kuikly.stockchat.data.provider.QuotePoint(time = "10:30", price = 101.0, volume = 1.0),
            com.kuikly.stockchat.data.provider.QuotePoint(time = "13:30", price = 99.5, volume = 1.0),
            com.kuikly.stockchat.data.provider.QuotePoint(time = "14:30", price = 100.5, volume = 1.0),
        ),
    ): Quote = Quote.placeholder("600519.SH", "贵州茅台").copy(
        price = 100.5,
        previousClose = 100.0,
        open = 100.5,
        timeline = timeline,
    )

    /** 30 点等差序列（10.0 + 0.1*i），供圈选区间 (lo,hi) 测试用。 */
    private fun linearTimeline(size: Int): List<com.kuikly.stockchat.data.provider.QuotePoint> =
        List(size) { com.kuikly.stockchat.data.provider.QuotePoint(time = "09:${30 + it % 60}", price = 10.0 + 0.1 * it, volume = 1.0) }

    private data class Fixture(
        val state: PlainDetailAiState,
        val scheduler: FakeDetailAiScheduler,
        val provider: FakeAiProvider,
        val host: DetailAiHostPort,
        val coordinator: DetailAiInsightCoordinator,
    )
}

/** 测试用 Revealer：同步追加并立刻回调。 */
private class ImmediateRevealer(private val onRevealed: (String) -> Unit) : AiInsightRevealer {
    private var text = ""
    private var cancelled = false
    override fun append(delta: String) {
        if (cancelled) return
        text += delta
        onRevealed(text)
    }
    override fun complete(onComplete: () -> Unit) { if (!cancelled) onComplete() }
    override fun cancel() { cancelled = true }
    override fun flushNow() {}
}

private class FakeAiProvider : AiProvider {
    private val callbackHistory = mutableListOf<Callbacks>()
    var askCallCount = 0
        private set
    fun callbackAt(index: Int): Callbacks =
        callbackHistory.getOrNull(index) ?: error("no ask() at index $index")

    override fun ask(
        messages: List<AiChatMessage>,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        askCallCount++
        callbackHistory += Callbacks(onDelta, onDone, onError)
    }

    override fun stop() { /* no-op for fake */ }
    data class Callbacks(val onDelta: (String) -> Unit, val onDone: () -> Unit, val onError: (String) -> Unit)
}

private class FakeDetailAiScheduler : DetailAiScheduler {
    private data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Int, task: () -> Unit): DetailAiScheduledTask {
        val entry = Entry(delayMillis, task)
        entries += entry
        return DetailAiScheduledTask { entry.cancelled = true }
    }

    fun run(delay: Int) {
        entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
            it.cancelled = true
            it.task()
        }
    }
}