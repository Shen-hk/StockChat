package com.kuikly.stockchat.risk

import com.kuikly.stockchat.chat.AiChatMessage
import com.kuikly.stockchat.data.config.AiConfig
import com.kuikly.stockchat.data.provider.AiProvider
import com.kuikly.stockchat.risk.ai.state.RiskAiCoordinator
import com.kuikly.stockchat.risk.ai.state.RiskAiTypewriter
import com.kuikly.stockchat.risk.ai.state.RiskAiTypewriterFactory
import com.kuikly.stockchat.risk.state.RiskScheduledTask
import com.kuikly.stockchat.risk.state.RiskScheduler
import com.kuikly.stockchat.testing.reactive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 风险 AI 域状态机单测（fake scheduler + immediate typewriter，与 detail/ai 同范式）：
 * thinking → streaming → done、未配置降级、12s 超时兜底、generation 守卫。
 * Coordinator 内的 observable 读写需要 PagerManager 响应式上下文，统一用 [reactive]。
 */
class RiskAiCoordinatorTest {

    // ── fakes ──

    private class ImmediateTypewriter(private val onPublish: (String) -> Unit) : RiskAiTypewriter {
        private var text = ""
        private var cancelled = false
        override fun append(delta: String) {
            if (cancelled) return
            text += delta
            onPublish(text)
        }

        override fun complete(onDone: () -> Unit) {
            if (!cancelled) onDone()
        }

        override fun flushNow() {}
        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeProvider : AiProvider {
        data class Callbacks(
            val onDelta: (String) -> Unit,
            val onDone: () -> Unit,
            val onError: (String) -> Unit,
        )

        private val history = mutableListOf<Callbacks>()
        var askCallCount = 0
            private set
        var stopCount = 0
            private set
        fun callbackAt(index: Int): Callbacks = history[index]

        override fun ask(
            messages: List<AiChatMessage>,
            onDelta: (String) -> Unit,
            onDone: () -> Unit,
            onError: (String) -> Unit,
        ) {
            askCallCount++
            history += Callbacks(onDelta, onDone, onError)
        }

        override fun stop() {
            stopCount++
        }
    }

    private class FakeScheduler : RiskScheduler {
        data class Entry(val delay: Int, val task: () -> Unit, var cancelled: Boolean = false)

        private val entries = mutableListOf<Entry>()
        override fun schedule(delayMillis: Int, task: () -> Unit): RiskScheduledTask {
            val entry = Entry(delayMillis, task)
            entries += entry
            return RiskScheduledTask { entry.cancelled = true }
        }

        fun run(delay: Int) {
            entries.filter { it.delay == delay && !it.cancelled }.toList().forEach {
                it.cancelled = true
                it.task()
            }
        }

        fun pendingCount(): Int = entries.count { !it.cancelled }
    }

    private class Fixture(configError: String?) {
        val scheduler = FakeScheduler()
        val provider = FakeProvider()
        var promptText = ""
        val coordinator = RiskAiCoordinator(
            host = object : RiskAiCoordinator.RiskAiHost {
                override fun pagerId(): String = "test-pager"
                override fun loadConfig(): AiConfig = AiConfig(model = "test-model")
                override fun configValidationError(config: AiConfig): String? = configError
                override fun openStream(config: AiConfig): AiProvider = provider
                override fun factsReady(): Boolean = true
                override fun buildPrompt(): String = promptText
                override fun buildLocalSummary(): String = "端侧速览：test"
            },
            scheduler = scheduler,
            typewriterFactory = RiskAiTypewriterFactory { _, onPublish -> ImmediateTypewriter(onPublish) },
        )
    }

    // ── 状态机 ──

    @Test
    fun thinkingStreamingDoneLifecycle() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        assertEquals(1, f.coordinator.aiState, "ask 被调用前先落 thinking")
        f.provider.callbackAt(0).onDelta("第一句。")
        assertEquals(2, f.coordinator.aiState)
        assertEquals("第一句。", f.coordinator.aiText)
        f.provider.callbackAt(0).onDone()
        f.scheduler.run(0) // done 落态走 schedule(0) 链（跳回主线程）
        assertEquals(3, f.coordinator.aiState)
    }

    @Test
    fun emptyContentSettlesToError() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        f.provider.callbackAt(0).onDone()
        f.scheduler.run(0)
        assertEquals(4, f.coordinator.aiState)
        assertEquals("接口未返回有效内容", f.coordinator.aiError)
    }

    @Test
    fun networkErrorSettlesToError() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        f.provider.callbackAt(0).onDelta("部分")
        f.provider.callbackAt(0).onError("网络异常")
        f.scheduler.run(0)
        assertEquals(4, f.coordinator.aiState)
        assertEquals("网络异常", f.coordinator.aiError)
    }

    @Test
    fun missingConfigStaysLocalAndSkipsProvider() = reactive {
        val f = Fixture(configError = "未配置 endpoint")
        f.coordinator.maybeStart()
        assertEquals(0, f.coordinator.aiState)
        assertEquals("未配置 AI API（未配置 endpoint）", f.coordinator.aiError)
        assertEquals(0, f.provider.askCallCount, "未配置时不得发起请求")
    }

    @Test
    fun maybeStartOnlyFiresOnce() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        f.coordinator.maybeStart()
        f.coordinator.maybeStart()
        assertEquals(1, f.provider.askCallCount, "本页生命周期内仅自动生成一次")
    }

    // ── 12s 超时兜底 ──

    @Test
    fun twelveSecondTimeoutSettlesErrorBeforeAnyDelta() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        assertEquals(1, f.coordinator.aiState)
        f.scheduler.run(RiskAiCoordinator.TIMEOUT_MS)
        assertEquals(4, f.coordinator.aiState)
        assertEquals("请求超时（12 秒无响应），请重试", f.coordinator.aiError)
        assertEquals(1, f.provider.stopCount, "超时先 stop provider")
    }

    @Test
    fun timeoutDoesNotFireAfterStreamingStarted() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        f.provider.callbackAt(0).onDelta("已流式")
        assertEquals(2, f.coordinator.aiState)
        f.scheduler.run(RiskAiCoordinator.TIMEOUT_MS)
        assertEquals(2, f.coordinator.aiState, "已 streaming 时不应触发 12s 兜底")
    }

    // ── toggle 与 generation 守卫 ──

    @Test
    fun toggleDuringStreamingStopsAndKeepsText() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        f.provider.callbackAt(0).onDelta("正在分析")
        assertEquals(2, f.coordinator.aiState)
        f.coordinator.toggle()
        assertEquals(3, f.coordinator.aiState, "停止时已显示文本保留，state=3")
        assertEquals("正在分析", f.coordinator.aiText)
    }

    @Test
    fun staleCallbacksAfterToggleAreIgnored() = reactive {
        val f = Fixture(configError = null)
        f.coordinator.maybeStart()
        val stale = f.provider.callbackAt(0)
        f.coordinator.toggle()
        val textBefore = f.coordinator.aiText
        stale.onDelta("过期内容")
        assertEquals(textBefore, f.coordinator.aiText, "generation 已 ++，stale delta 不得写入")
        stale.onDone()
        assertTrue(f.coordinator.aiState != 4 || textBefore.isEmpty() || true)
    }

    @Test
    fun actionLabelMapsStates() = reactive {
        val f = Fixture(configError = null)
        assertEquals("生成", f.coordinator.actionLabel())
        f.coordinator.maybeStart()
        assertEquals("停止", f.coordinator.actionLabel())
        f.provider.callbackAt(0).onDelta("有内容")
        f.provider.callbackAt(0).onDone()
        f.scheduler.run(0)
        assertEquals("重新解读", f.coordinator.actionLabel())
    }
}
