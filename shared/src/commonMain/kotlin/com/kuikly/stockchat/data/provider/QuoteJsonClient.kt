package com.kuikly.stockchat.data.provider

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 行情 JSON GET 端口（Port）：语义等同 Kuikly 网络模块的 requestGet，但不暴露
 * Pager 作用域。
 *
 * 之所以另开端口而不是直接复用 `PlatformHttpClient`（ktor）：行情链路的传输层切换是
 * 行为变更（引擎、超时、回落时序都会变），B-1 的硬边界是「只改装配，不改解析与降级」。
 * 因此这里把「向 Kuikly 网络模块发一次 GET」抽成端口，适配器
 * `app/platform/KuiklyQuoteJsonClient.kt` 仍旧走同一个网络模块 —— 引擎、回调线程、
 * 重试时机与改动前逐字节一致，只是 `data/` 不再自己取模块。
 *
 * @param onResult data / success / errorMsg，对齐 Kuikly NMResponse 的前三个字段
 *   （第四个 NetworkResponse 行情链路未使用，端口不带）。
 */
interface QuoteJsonClient {
    fun requestGet(
        url: String,
        params: JSONObject,
        onResult: (data: JSONObject, success: Boolean, errorMsg: String) -> Unit,
    )
}
