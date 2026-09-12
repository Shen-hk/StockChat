package com.kuikly.stockchat.chat.compare.state

import com.kuikly.stockchat.cards.core.StockCompareCardModel
import com.kuikly.stockchat.chat.TypewriterSmoother
import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.entity.GlossaryEntry
import com.tencent.kuikly.core.reactive.handler.observable

/** 对比解读状态。页面与 DSL 只读取该端口，协调器使用镜像处理命令与异步回调。 */
internal interface CompareInsightStatePort {
    var candidateCardKey: String
    var candidateSymbol: String
    var card: StockCompareCardModel?
    var insightState: CompareInsightState
    var insightText: String
    var insightError: String
}

internal class CompareInsightStateHolder : CompareInsightStatePort {
    override var candidateCardKey: String by observable("")
    override var candidateSymbol: String by observable("")
    override var card: StockCompareCardModel? by observable(null)
    override var insightState: CompareInsightState by observable(CompareInsightState.IDLE)
    override var insightText: String by observable("")
    override var insightError: String by observable("")
}

internal class PlainCompareInsightState : CompareInsightStatePort {
    override var candidateCardKey = ""
    override var candidateSymbol = ""
    override var card: StockCompareCardModel? = null
    override var insightState = CompareInsightState.IDLE
    override var insightText = ""
    override var insightError = ""
}

internal enum class CompareInsightState { IDLE, LOADING, READY, ERROR }

/** 页面执行的副作用；协调器不读取 Quote 列表或触达路由/原生模块。 */
internal sealed interface CompareInsightEffect {
    data class RequestQuote(val symbol: String) : CompareInsightEffect
}

internal interface CompareInsightRequester {
    fun request(
        prompt: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    )
}

internal interface CompareTextRevealer {
    fun append(delta: String)
    fun complete(onComplete: () -> Unit)
    fun cancel()
}

internal fun interface CompareTextRevealerFactory {
    fun create(onRevealed: (String) -> Unit): CompareTextRevealer
}

internal class PagerCompareTextRevealerFactory(
    private val pagerId: String,
) : CompareTextRevealerFactory {
    override fun create(onRevealed: (String) -> Unit): CompareTextRevealer {
        val smoother = TypewriterSmoother(pagerId, onRevealed)
        return object : CompareTextRevealer {
            override fun append(delta: String) = smoother.append(delta)
            override fun complete(onComplete: () -> Unit) = smoother.complete(onComplete)
            override fun cancel() = smoother.cancel()
        }
    }
}

/**
 * 股票/术语对比的唯一状态 owner。pair revision 使旧会话的流式增量和收尾都失效；
 * 逐字显示由注入的 revealer 保持原有 TypewriterSmoother 时序。
 */
internal class CompareInsightCoordinator(
    val state: CompareInsightStatePort,
    private val requester: CompareInsightRequester,
    private val revealerFactory: CompareTextRevealerFactory,
    private val onEffect: (CompareInsightEffect) -> Unit,
) {
    private var candidateCardKey = ""
    private var candidateSymbol = ""
    private var card: StockCompareCardModel? = null
    private var insightState = CompareInsightState.IDLE
    private var insightText = ""
    private var insightError = ""
    private var pairKey = ""
    private var revision = 0
    private var activeRevealer: CompareTextRevealer? = null

    fun hasCard(): Boolean = card != null

    fun selectCardCandidate(
        cardKey: String,
        symbol: String,
        quoteFor: (String) -> Quote?,
    ) {
        onEffect(CompareInsightEffect.RequestQuote(symbol))
        if (candidateSymbol.isEmpty() || candidateSymbol == symbol) {
            setCandidateCardKey(cardKey)
            setCandidateSymbol(symbol)
            setCard(null)
            return
        }
        val left = quoteFor(candidateSymbol)
        val right = quoteFor(symbol)
        if (left != null && right != null) {
            setCard(StockCompareCardModel(listOf(left, right), "active-compare:${left.symbol}:${right.symbol}"))
            clearCandidate()
            requestStockInsight(left, right)
        } else {
            onEffect(CompareInsightEffect.RequestQuote(candidateSymbol))
            onEffect(CompareInsightEffect.RequestQuote(symbol))
        }
    }

    fun syncIslandCard(left: Quote?, right: Quote?) {
        if (left == null || right == null) return
        setCard(StockCompareCardModel(listOf(left, right), "island-compare:${left.symbol}:${right.symbol}"))
        requestStockInsight(left, right)
    }

    fun requestTermInsight(left: GlossaryEntry, right: GlossaryEntry) {
        val nextPairKey = "term:${left.key}:${right.key}"
        if (pairKey == nextPairKey && insightState != CompareInsightState.ERROR) return
        beginRequest(nextPairKey)
        val prompt = "用不超过 120 字向 A 股新手解释金融术语「${left.term}」和「${right.term}」的区别与联系，" +
            "各举一个它们分别适用的小场景。只做事实性解释，不要给任何买卖建议或倾向性结论。"
        request(prompt, nextPairKey)
    }

    fun requestStockInsight(left: Quote, right: Quote) {
        val nextPairKey = "${left.symbol}:${right.symbol}"
        if (pairKey == nextPairKey && insightState != CompareInsightState.ERROR) return
        beginRequest(nextPairKey)
        request(buildStockPrompt(left, right), nextPairKey)
    }

    fun retryStockInsight(left: Quote, right: Quote) {
        pairKey = ""
        requestStockInsight(left, right)
    }

    fun retryTermInsight(left: GlossaryEntry, right: GlossaryEntry) {
        pairKey = ""
        requestTermInsight(left, right)
    }

    fun clearCard() = setCard(null)

    fun clearCandidate() {
        setCandidateCardKey("")
        setCandidateSymbol("")
    }

    fun reset() {
        revision++
        activeRevealer?.cancel()
        activeRevealer = null
        pairKey = ""
        setInsightState(CompareInsightState.IDLE)
        setInsightText("")
        setInsightError("")
    }

    fun resetForNewSession() {
        clearCandidate()
        clearCard()
        reset()
    }

    fun onDestroy() = reset()

    private fun beginRequest(nextPairKey: String) {
        activeRevealer?.cancel()
        pairKey = nextPairKey
        setInsightState(CompareInsightState.LOADING)
        setInsightText("")
        setInsightError("")
    }

    private fun request(prompt: String, requestPairKey: String) {
        val requestRevision = ++revision
        val revealer = revealerFactory.create { revealed ->
            if (isCurrent(requestRevision, requestPairKey)) setInsightText(revealed)
        }
        activeRevealer = revealer
        requester.request(
            prompt = prompt,
            onDelta = { delta ->
                if (isCurrent(requestRevision, requestPairKey)) revealer.append(delta)
            },
            onDone = {
                if (!isCurrent(requestRevision, requestPairKey)) {
                    revealer.cancel()
                    return@request
                }
                revealer.complete {
                    if (!isCurrent(requestRevision, requestPairKey)) return@complete
                    setInsightText(insightText.ifBlank { "暂未生成对比解读" })
                    setInsightState(CompareInsightState.READY)
                }
            },
            onError = { error ->
                revealer.cancel()
                if (!isCurrent(requestRevision, requestPairKey)) return@request
                setInsightError(error)
                setInsightState(CompareInsightState.ERROR)
            },
        )
    }

    private fun isCurrent(requestRevision: Int, requestPairKey: String): Boolean =
        requestRevision == revision && pairKey == requestPairKey

    private fun buildStockPrompt(left: Quote, right: Quote): String =
        """
            请基于以下两只股票的即时行情做一个简洁对比解读。
            要求：
            1. 只解释差异和可能关注点，不给买卖建议。
            2. 用 3 到 5 句中文，适合显示在手机卡片里。
            3. 明确说明价格、涨跌幅、日内高低点、成交额、换手率里的关键差异。

            股票 A：${left.name} ${left.symbol}
            价格：${Format.price(left.price)}
            涨跌幅：${Format.percent(left.changePercent)}
            日内高低：${Format.price(left.high)} / ${Format.price(left.low)}
            成交额：${Format.compactAmount(left.amount)}
            换手率：${Format.decimal(left.turnoverRate, 2)}%

            股票 B：${right.name} ${right.symbol}
            价格：${Format.price(right.price)}
            涨跌幅：${Format.percent(right.changePercent)}
            日内高低：${Format.price(right.high)} / ${Format.price(right.low)}
            成交额：${Format.compactAmount(right.amount)}
            换手率：${Format.decimal(right.turnoverRate, 2)}%
        """.trimIndent()

    private fun setCandidateCardKey(value: String) { candidateCardKey = value; state.candidateCardKey = value }
    private fun setCandidateSymbol(value: String) { candidateSymbol = value; state.candidateSymbol = value }
    private fun setCard(value: StockCompareCardModel?) { card = value; state.card = value }
    private fun setInsightState(value: CompareInsightState) { insightState = value; state.insightState = value }
    private fun setInsightText(value: String) { insightText = value; state.insightText = value }
    private fun setInsightError(value: String) { insightError = value; state.insightError = value }
}
