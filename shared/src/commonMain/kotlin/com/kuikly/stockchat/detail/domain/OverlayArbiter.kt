package com.kuikly.stockchat.detail.domain

import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 浮层仲裁器（doc 29 §3 OverlayArbiter · U1 浮层仲裁 / U4 呼吸预算）。
 *
 * 语义（U1）：全页同时最多一个就地浮层。声呐气泡 / 圈选气泡 / 新闻摘要 / 先览气泡 /
 * 回访卡 / 理由 chips 互斥。开新的先关旧的（request 新层即关闭旧层）；点空白全关。
 * U4 的「同屏至多一组脉冲」也由本仲裁器兼管——同一时刻 active 唯一即天然满足。
 */
enum class DetailOverlay {
    NONE,           // 无浮层
    CHART_BUBBLE,   // 图表就地气泡（④声呐 / ①圈选 / ⑤预填）
    NEWS_SUMMARY,   // 新闻摘要（B2 摘要条 / B1 先览）
    TAPE_PREVIEW,   // 弹幕带先览气泡（B1）
    REVISIT,        // 当初理由回访卡（A1）
    REASON_CHIPS,   // 加自选快捷理由 chips（H1；2026-09-10 起从 ⋯ 菜单进入）
    MORE_MENU       // 顶栏 ⋯ 更多操作菜单（2026-09-10：记当初理由 / AI 解读 / 复制代码）
}

class OverlayArbiter {
    /** 当前活跃浮层（响应式，初始 NONE）。 */
    var active: DetailOverlay by observable(DetailOverlay.NONE)

    /**
     * 请求展示某浮层。target == NONE 等价于 close()（关闭当前浮层）。
     * 因 active 唯一，请求新层即自动关闭旧层，满足 U1「开新的先关旧的」。
     */
    fun request(target: DetailOverlay) {
        active = if (target == DetailOverlay.NONE) DetailOverlay.NONE else target
    }

    /** 关闭当前浮层（点空白统一入口）。 */
    fun close() {
        active = DetailOverlay.NONE
    }
}
