package com.kuikly.stockchat.common

/**
 * 平台策略开关（expect/actual，2026-09-11；2026-09-12 增补渲染能力位）。
 *
 * 本仓库多端共用 commonMain。为避免「为某一端做的改动顺带改变其它端的既有行为」，
 * 跨端差异统一收在本开关后面。按性质分两组：
 *
 * **A. 行为开关（默认关，回归通过才开）** —— 初始值 Android / 鸿蒙 / H5 全 `false`，
 * 行为与引入前一致；iOS 逐页验证后开启。
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
 *
 * **B. 渲染能力位（描述 native 事实，不是「开不开」）** —— 取值由各端渲染层
 * 实际实现了哪些 bridge 命令决定，`true` 的含义是「该端的 native 真的支持」。
 *
 * - [canvasBatchDrawSupported]：该端渲染层是否实现 `batchDraw` 命令。
 *   `CanvasContext.batchDraw = true` 时，Kotlin 侧会把整帧绘制命令缓冲起来，
 *   只向 native 发一条 `batchDraw`（JSON 命令数组）。**若该端 native 没有实现它，
 *   这一帧的所有绘制命令全部丢失 —— Canvas 显示为空白。**
 *   实测（2026-09-12，直接扫二进制字面量）：
 *   - Android `core-render-android@2.25.0` 的 `KRCanvasView.class` 含 `batchDraw` → true
 *   - 鸿蒙 `@kuikly-open/render@2.25.0` 的 `libkuikly.so` **不含** `batchDraw`
 *     （同版本其它画布命令 `beginPath`/`moveTo`/`arc`/`fill` 等均在）→ false
 *   - iOS / H5：保持既有取值（这两端未做本轮验证，不动其行为）
 *   同源命令缺口还有 `clipPathIntersect` / `clipPathDifference` / `createRadialGradient`
 *   / `measureText` / `setLineDash`，鸿蒙上同样是静默 no-op，使用前需一并评估。
 */
internal expect object PlatformProfile {
    /** 行情解析 / 图表几何修复是否生效。 */
    val marketFixes: Boolean

    /** 数据层异步回调是否需要回跳 Kuikly 核心线程。 */
    val coreThreadMarshalling: Boolean

    /** 模型请求协程是否必须在宿主主线程上恢复。 */
    val aiProviderMainDispatcher: Boolean

    /** 无头验证启动参数钩子是否可用。 */
    val debugHooks: Boolean

    /** 该端渲染层是否实现 `batchDraw`（画布批量绘制）命令。 */
    val canvasBatchDrawSupported: Boolean
}
