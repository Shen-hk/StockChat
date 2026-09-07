package com.kuikly.stockchat.chat

import com.kuikly.stockchat.common.Format
import com.kuikly.stockchat.composer.ComposerCatalog
import com.kuikly.stockchat.composer.MentionType
import com.kuikly.stockchat.composer.SendPayload
import com.kuikly.stockchat.data.entity.Securities
import com.kuikly.stockchat.data.provider.DataMode
import com.kuikly.stockchat.data.provider.Quote
import com.kuikly.stockchat.data.provider.QuoteRepository

/**
 * 回答前的行情上下文注入（ADR-11 意图与数据分离的补充，不改变分工）：
 *
 * 卡片数字一直由端侧 Provider 填充真实行情，但模型**正文**拿不到任何数字——
 * 当用户问「茅台现在多少钱」时，第一行结论只能说「无法获取实时行情」。
 * 这里在发送前把端侧刚拉取的最新快照以 system 消息注入，让正文与卡片同源：
 * 数字仍然全部来自端侧 Provider，模型只负责引用，不允许编造。
 *
 * 注入条件：标的可解析 且 快照模式为 ONLINE / CACHE。
 * 离线演示数据绝不注入——否则等于把假数字伪装成实时行情喂给模型；
 * 此时不注入，模型按「无行情」处理（定性回答 + 卡片兜底），与旧行为一致。
 */
object ChatQuoteContext {
    private const val MAX_SYMBOLS = 3

    /**
     * 解析标的并拉取快照，回调恰好一次（快照链 在线→缓存→离线 保证回调用，
     * 外层调用方需自行带兜底计时器，见 ChatViewModel.streamWithProvider）。
     */
    fun resolve(payload: SendPayload, repository: QuoteRepository, onResult: (String?) -> Unit) {
        val symbols = resolveSymbols(payload)
        if (symbols.isEmpty()) {
            onResult(null)
            return
        }
        var pending = symbols.size
        val lines = mutableListOf<String>()
        var finished = false
        symbols.forEach { symbol ->
            repository.snapshotForContext(symbol) { result ->
                if (finished) return@snapshotForContext
                val quote = result.quote
                if (result.mode != DataMode.OFFLINE && quote != null && quote.price > 0.0) {
                    lines += describe(quote)
                }
                pending--
                if (pending == 0 && !finished) {
                    finished = true
                    onResult(lines.takeIf { it.isNotEmpty() }?.let(::buildNote))
                }
            }
        }
    }

    /**
     * @ 提及优先（输入期已固化、无歧义），纯文本问句按名称/别名扫描兜底；
     * 板块提及取代表成分股快照。去重后最多 [MAX_SYMBOLS] 只，控制 token 开销。
     */
    internal fun resolveSymbols(payload: SendPayload): List<String> {
        val out = mutableListOf<String>()
        payload.mentions.forEach { mention ->
            when (mention.type) {
                MentionType.BOARD -> ComposerCatalog.boardConstituents(mention.symbol, 2).forEach { entry ->
                    if (entry.symbol !in out) out += entry.symbol
                }
                else -> if (mention.symbol !in out) out += mention.symbol
            }
        }
        if (out.size < MAX_SYMBOLS) {
            val question = payload.renderedPrompt ?: payload.text
            Securities.all.forEach { security ->
                if (out.size >= MAX_SYMBOLS) return@forEach
                val matched = question.contains(security.name) ||
                    security.aliases.any { it.length >= 2 && question.contains(it, ignoreCase = true) }
                if (matched && security.symbol !in out) out += security.symbol
            }
        }
        return out.take(MAX_SYMBOLS)
    }

    private fun buildNote(lines: List<String>): String = buildString {
        append("【实时行情注入】以下数字是客户端刚从行情源拉取的真实数据，是本回合唯一可信的行情来源。")
        append("结论行请直接引用这些数字，禁止回答「无法获取实时行情」。未列出的标的不提供行情数字，不要编造：\n")
        lines.forEach { append("- ").append(it).append('\n') }
    }

    internal fun describe(quote: Quote): String = buildString {
        append(quote.symbol).append(' ').append(quote.name)
        append("：现价 ").append(Format.price(quote.price)).append(" 元")
        append("，涨跌 ").append(Format.signed(quote.change))
        append("（").append(Format.percent(quote.changePercent)).append("）")
        append("，昨收 ").append(Format.price(quote.previousClose))
        append("，今开 ").append(Format.price(quote.open))
        append("，最高 ").append(Format.price(quote.high))
        append("，最低 ").append(Format.price(quote.low))
        append("，成交额 ").append(Format.compactAmount(quote.amount))
        append("，换手率 ").append(Format.percent(quote.turnoverRate))
        append("，PE(TTM) ").append(Format.decimal(quote.peTtm, 2))
        append("，PB ").append(Format.decimal(quote.pb, 2))
        append("，总市值 ").append(Format.compactAmount(quote.marketCap)).append(" 元")
        if (quote.timestamp.isNotBlank()) append("（截至 ").append(quote.timestamp).append("）")
        if (quote.source.isNotBlank()) append("，来源：").append(quote.source)
    }
}
