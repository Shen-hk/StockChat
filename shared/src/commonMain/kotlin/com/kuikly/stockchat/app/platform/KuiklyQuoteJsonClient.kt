package com.kuikly.stockchat.app.platform

import com.kuikly.stockchat.data.provider.QuoteJsonClient
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * [QuoteJsonClient] 的 Kuikly 适配器：直接把调用透传给 `NetworkModule.requestGet`。
 *
 * 引擎、超时（NetworkModule 内部 30s）、回调所在队列都与 B-1 之前完全一致；
 * 本类只负责「谁持有 Pager / 谁 acquireModule」这一件事。
 */
class KuiklyQuoteJsonClient(override val pagerId: String) : QuoteJsonClient, PagerScope {
    private val network: NetworkModule
        get() = getPager().acquireModule(NetworkModule.MODULE_NAME)

    override fun requestGet(
        url: String,
        params: JSONObject,
        onResult: (data: JSONObject, success: Boolean, errorMsg: String) -> Unit,
    ) {
        network.requestGet(url, params) { data, success, errorMsg, _ ->
            onResult(data, success, errorMsg)
        }
    }
}
