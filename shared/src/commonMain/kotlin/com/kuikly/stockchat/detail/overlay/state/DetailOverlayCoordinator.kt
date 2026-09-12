package com.kuikly.stockchat.detail.overlay.state

import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.detail.domain.DetailOverlay

/**
 * Detail 页 overlay 仲裁域的唯一 owner（Wave 2 第 4 刀，见 docs/39 §9 /
 * docs/43 D4）：承接原 [OverlayArbiter] 的 U1「开新的先关旧的、点空白全关」仲裁
 * 语义（active 枚举唯一，互斥切换），并收编新闻摘要 / 披露 peek / 弹幕先览 /
 * 理由 chips 四类载荷与两拍入场/version 守卫自动消失定时器。DetailOverlay 枚举
 * 不变（跨多处 vif 条件引用）。
 *
 * 与 [com.kuikly.stockchat.detail.quote.state.DetailDataCoordinator] / D2 /
 * D3 同构：StatePort + Scheduler + Effect + Coordinator。tapePreviewVersion
 * 镜像由 Coordinator 私有持有；披露 peek 两拍入场与弹幕先览 version 守卫自动
 * 消失均经 [DetailOverlayScheduler] 排程，[onDestroy] 取消。
 */
internal class DetailOverlayCoordinator(
    private val state: DetailOverlayStatePort,
    private val scheduler: DetailOverlayScheduler,
    private val reduceMotion: Boolean,
) {
    /** 弹幕先览 version 守卫镜像；自增即让旧计时器失效。 */
    private var tapePreviewVersion = 0
    private val tasks = mutableListOf<DetailOverlayScheduledTask>()

    /** 当前 active 浮层（DSL vif 直接读取）。 */
    fun active(): DetailOverlay = state.active

    // ── 仲裁语义 ──

    /**
     * U1「开新的先关旧的」：请求新浮层 = 旧浮层立刻失效，state.active 切换为
     * target（target == NONE 等价于 close）。reasonChipsVisible 与 active 联动
     * ——仅当请求 REASON_CHIPS 时为 true，其余时刻归位。
     */
    fun request(target: DetailOverlay) {
        state.reasonChipsVisible = target == DetailOverlay.REASON_CHIPS
        state.active = if (target == DetailOverlay.NONE) DetailOverlay.NONE else target
    }

    /** U1「点空白全关」：关闭当前浮层并清 REASON_CHIPS 可见性。 */
    fun close() {
        state.active = DetailOverlay.NONE
        state.reasonChipsVisible = false
    }

    // ── B1/B2 新闻摘要与旗标 ──

    /** 打开新闻摘要：设载荷 + request(NEWS_SUMMARY)（与原 onNewsTapped 等价）。 */
    fun showSummary(item: NewsItem) {
        state.newsSummary = item
        request(DetailOverlay.NEWS_SUMMARY)
    }

    /** 关闭新闻摘要：清载荷 + close()。 */
    fun closeSummary() {
        state.newsSummary = null
        close()
    }

    // ── F1 公告/研报长按预览（peek 两拍入场，与原 showDisclosurePeek 等价）──

    fun showDisclosurePeek(item: DisclosureItem) {
        state.disclosurePeek = item
        // peek 是挂载旗（vif）、peekVisible 是过渡旗：不能同拍翻转，否则没有淡入
        if (reduceMotion) {
            state.disclosurePeekVisible = true
        } else {
            schedule(1) {
                if (state.disclosurePeek != null) state.disclosurePeekVisible = true
            }
        }
    }

    /** 与原 dismissDisclosurePeek 等价：先翻转 visible、200ms 兜底再清 peek 载荷。 */
    fun dismissDisclosurePeek() {
        if (!state.disclosurePeekVisible) return
        state.disclosurePeekVisible = false
        if (reduceMotion) {
            state.disclosurePeek = null
        } else {
            schedule(200) {
                if (!state.disclosurePeekVisible) state.disclosurePeek = null
            }
        }
    }

    // ── B1 弹幕长按先览（version 守卫自动消失 5s + 松手 700ms）──

    fun showTapePreview(item: NewsItem, pressX: Float, pressY: Float) {
        state.tapePreviewAnchorX = pressX
        state.tapePreviewAnchorY = pressY
        state.tapePreview = item
        request(DetailOverlay.TAPE_PREVIEW)
        // 5s 兜底防松手回调丢失
        val version = ++tapePreviewVersion
        schedule(5000) {
            if (version == tapePreviewVersion && state.active == DetailOverlay.TAPE_PREVIEW) {
                close()
                state.tapePreview = null
            }
        }
    }

    /** 松手 700ms 后自动消失（与原 scheduleTapePreviewDismiss 等价）。 */
    fun scheduleTapePreviewDismiss() {
        val version = ++tapePreviewVersion
        schedule(700) {
            if (version == tapePreviewVersion && state.active == DetailOverlay.TAPE_PREVIEW) {
                close()
                state.tapePreview = null
            }
        }
    }

    // ── 生命周期 ──

    fun onDestroy() {
        tasks.forEach(DetailOverlayScheduledTask::cancel)
        tasks.clear()
    }

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }
}