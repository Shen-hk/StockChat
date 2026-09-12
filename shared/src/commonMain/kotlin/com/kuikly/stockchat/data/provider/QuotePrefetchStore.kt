package com.kuikly.stockchat.data.provider

import kotlinx.coroutines.sync.Mutex

/**
 * 进程级行情预取缓存（跨页面共享，2026-09-10 详情页空白期治理）。
 *
 * 背景：[QuoteRepository] 与其缓存存储都是 pager 级实例（MarketDependencies.forPager
 * 每页各建一份），聊天页/自选页提前加载的行情详情页拿不到；详情页冷启动必须等
 * 「在线→缓存→离线」链路走完才有首个真实渲染帧，转场落地后是一段只有骨架的
 * 空白期。本对象提供跨页面的「点按前预热」：
 *
 * - 触发方调用 [warm]：openStockDetail（全部详情入口的汇聚点，转场动画期间并行
 *   拉取）、聊天页/自选页进页时批量预热自选标的；
 * - 详情页 created() 用 [peek] 查询：60s 新鲜窗口内的结果直接整体应用，整页秒开；
 *   随后的 quoteRepository.load 照常刷新（observable 同值写不通知，无闪动）。
 *
 * 数据口径与 QuoteRepository 一致：复用 TencentQuoteProvider（快照自带日 K，
 * 分时单独拉取后合并）。快照到达即先写入，详情首屏无需再等较慢的分时请求；
 * 分时到达后再补全缓存。在线失败不在此兜底——详情页自身链路会回落缓存/离线。
 *
 * 线程模型：回调可能来自 NetworkModule 后台线程，全部状态读写走跨平台 Mutex。
 */
object QuotePrefetchStore {
    /** 预取结果的新鲜窗口，对齐 [QuoteRepository.SNAPSHOT_TTL_MILLIS]。 */
    internal const val FRESH_MILLIS = 60_000L

    /** 单次 warm 最多预取的标的数（批量预热防突发请求风暴）。 */
    private const val MAX_WARM_SYMBOLS = 8

    /** LRU 容量：常态自选规模下足够，超出按最久未用淘汰。 */
    private const val MAX_ENTRIES = 16

    private class Entry(val quote: Quote, val savedAtMillis: Long)

    // accessOrder=true：get/put 都把键挪到队尾，淘汰时移除队首（最久未用）。
    private val entries = LinkedHashMap<String, Entry>()
    private val inFlight = mutableSetOf<String>()
    private val lock = Mutex()

    private inline fun <T> withLock(block: () -> T): T {
        while (!lock.tryLock()) {
            // Critical sections only update the small in-memory LRU state.
        }
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    /**
     * 并行预热一批标的（去重 + 跳过新鲜与在途）。provider 绑定调用方页面的
     * NetworkModule，须在页面存活期调用；回调晚到不阻塞、不落缓存。
     */
    fun warm(
        symbols: Collection<String>,
        provider: QuoteProvider,
        nowMillis: () -> Long = ::platformCurrentTimeMillis,
    ) {
        val targets = withLock {
            symbols.asSequence()
                .filter { it.isNotBlank() }
                .distinct()
                .filterNot { it in inFlight }
                .filterNot { entries[it]?.let { e -> nowMillis() - e.savedAtMillis <= FRESH_MILLIS } == true }
                .take(MAX_WARM_SYMBOLS)
                .toList()
                .onEach { inFlight.add(it) }
        }
        targets.forEach { symbol -> request(symbol, provider, nowMillis) }
    }

    /** 60s 内的预取结果；过期/未预热返回 null。命中会刷新 LRU 位次。 */
    fun peek(symbol: String, nowMillis: () -> Long = ::platformCurrentTimeMillis): Quote? =
        withLock {
            entries[symbol]?.takeIf { nowMillis() - it.savedAtMillis <= FRESH_MILLIS }?.also {
                entries.remove(symbol)
                entries[symbol] = it
            }?.quote
        }

    /** 单例状态清空（进程内所有页面共享本对象，单测用例间隔离专用）。 */
    internal fun clearForTest() {
        withLock {
            entries.clear()
            inFlight.clear()
        }
    }

    private fun request(symbol: String, provider: QuoteProvider, nowMillis: () -> Long) {
        // 快照（含日 K）与分时并行。首包可用性不应绑定到较慢的分时请求；
        // 局部状态也统一在锁内更新，避免两个网络回调交错时用旧序列覆盖新序列。
        var snapshotQuote: Quote? = null
        var timeline: List<QuotePoint> = emptyList()
        var pending = 2

        fun putLocked(quote: Quote) {
            entries[symbol] = Entry(quote, nowMillis())
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }

        fun settle() = withLock {
            pending -= 1
            if (pending == 0) {
                inFlight.remove(symbol)
                snapshotQuote?.takeIf { it.price > 0.0 }?.let { quote ->
                    putLocked(if (timeline.isNotEmpty()) quote.copy(timeline = timeline) else quote)
                }
            }
        }

        provider.snapshot(symbol) { quote ->
            withLock {
                snapshotQuote = quote
                // 转场期间若快照先到，价格可以立即在详情首屏使用；图表仍由页面
                // 保持骨架，直到分时已到位。
                quote?.takeIf { it.price > 0.0 }?.let(::putLocked)
            }
            settle()
        }
        provider.timeline(symbol) { points ->
            withLock {
                timeline = points
                snapshotQuote?.takeIf { it.price > 0.0 }?.let { quote ->
                    putLocked(if (points.isNotEmpty()) quote.copy(timeline = points) else quote)
                }
            }
            settle()
        }
    }
}
