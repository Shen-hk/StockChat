package com.kuikly.stockchat.detail.overlay.state

import com.kuikly.stockchat.data.provider.NewsItem
import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.detail.domain.DetailOverlay
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * Detail 页 overlay 仲裁域的可渲染状态端口（Wave 2 第 4 刀）。
 * 唯一浮层 active + 四类载荷（newsSummary / tapePreview / disclosurePeek /
 * reasonChipsVisible）经 StatePort observable 统一驱动；版本号（tapePreviewVersion）
 * 等私有镜像由 Coordinator 持有，不外露。DetailOverlay 枚举仍放在
 * `page.detail`（跨多处 vif 条件引用，按 docs/43 D4 不得改名/不得搬迁）。
 */
internal interface DetailOverlayStatePort {
    var active: DetailOverlay
    var newsSummary: NewsItem?
    var tapePreview: NewsItem?
    var tapePreviewAnchorX: Float
    var tapePreviewAnchorY: Float
    var disclosurePeek: DisclosureItem?
    var disclosurePeekVisible: Boolean
    var reasonChipsVisible: Boolean
}

internal class DetailOverlayState : DetailOverlayStatePort {
    override var active: DetailOverlay by observable(DetailOverlay.NONE)
    override var newsSummary: NewsItem? by observable(null)
    override var tapePreview: NewsItem? by observable(null)
    override var tapePreviewAnchorX: Float by observable(0f)
    override var tapePreviewAnchorY: Float by observable(0f)
    override var disclosurePeek: DisclosureItem? by observable(null)
    override var disclosurePeekVisible: Boolean by observable(false)
    override var reasonChipsVisible: Boolean by observable(false)
}

internal class PlainDetailOverlayState : DetailOverlayStatePort {
    override var active: DetailOverlay = DetailOverlay.NONE
    override var newsSummary: NewsItem? = null
    override var tapePreview: NewsItem? = null
    override var tapePreviewAnchorX: Float = 0f
    override var tapePreviewAnchorY: Float = 0f
    override var disclosurePeek: DisclosureItem? = null
    override var disclosurePeekVisible: Boolean = false
    override var reasonChipsVisible: Boolean = false
}