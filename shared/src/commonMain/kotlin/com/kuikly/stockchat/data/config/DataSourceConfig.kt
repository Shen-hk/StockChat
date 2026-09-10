package com.kuikly.stockchat.data.config

/**
 * 数据源总开关（2026-09-09）。代码级开关，不做 UI 入口。
 *
 * USE_REAL_MARKET_DATA = false（默认，即当前状态）：恢复到 2026-09-08 之前的
 * 模拟数据源——行情失败回落 MockDataBank（MockQuoteProvider），市场/热点/日历/
 * 详情页用 OfflineMarketInsightProvider 的演示数据，卡片画廊用 MockDataBank 种子行情。
 *
 * USE_REAL_MARKET_DATA = true：全真实数据链路——行情 = 腾讯在线 → 缓存 → 空态；
 * 洞察 = 东方财富接口，降级终点一律空结果（"待接入/暂无数据"），宽度样本按
 * 沪主板/科创板/深主板/创业板/北交所五个大类拆分并在市场页页底标注。
 *
 * 真实数据链路验证通过后，把这里的常量翻为 true 即可全量切换，页面层无感。
 */
object DataSourceConfig {
    const val USE_REAL_MARKET_DATA = true
}
