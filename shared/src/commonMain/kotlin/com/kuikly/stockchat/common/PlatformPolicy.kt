package com.kuikly.stockchat.common

/**
 * 平台策略开关（expect/actual，2026-09-11）。
 *
 * 本仓库多端共用 commonMain。iOS 逐页验证期间修的问题分三类，为避免「为 iOS 做的改动
 * 顺带改变 Android / 鸿蒙的既有行为」（这两端本轮未做回归，未来鸿蒙同样要保持可预期），
 * 统一收在本开关后面：**Android 与鸿蒙的 actual 全部为 false，行为与本轮改动前一致。**
 *
 * - [marketFixes]：行情与图表缺陷修复。涉及腾讯行情各市场的列位表、量纲（手/股）口径、
 *   美股分时接口路径、分时槽位对齐、未知指标占位。
 * - [coreThreadMarshalling]：数据层异步回调是否需要回跳 Kuikly 核心线程。iOS 渲染层
 *   `KuiklyRenderThreadManager.assertContextQueue` 要求所有 toNative 在 context queue
 *   执行，后台线程直调会 abort（Android 无此断言，鸿蒙 ABI 侧同样不需要）。
 * - [debugHooks]：无头验证用的启动参数钩子（自动提问等），只有 iOS 宿主会注入对应参数。
 *
 * 某个平台完成回归后，把该源集的 actual 改成 `true` 即可一次性启用全部修复，
 * 不需要改 commonMain 的任何一处调用点。
 */
internal expect object PlatformProfile {
    /** 行情解析 / 图表几何修复是否生效。 */
    val marketFixes: Boolean

    /** 数据层异步回调是否需要回跳 Kuikly 核心线程。 */
    val coreThreadMarshalling: Boolean

    /** 无头验证启动参数钩子是否可用。 */
    val debugHooks: Boolean
}
