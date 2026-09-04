# 详情页图表：分时改用 KuiklyChartKit（K线保留自研 KLineChart）

> 配套原型：`docs/detail-redesign-prototype.html`（顶部蓝色进度条已挪到标题栏底边 `top:96px`）。
> 本文件只讲**真实端（Kotlin/Kuikly）**把分时图换成 `KuiklyChartKit` DSL 的方案、可复制代码、缺口与落地步骤。
> 分时渲染器已落地在 **`shared/src/commonMain/kotlin/com/kuikly/stockchat/cards/stock/KuiklyTimelineChart.kt`**（已编译通过，以它为准）；`docs/KuiklyTimelineChart.kt` 为历史快照。

## 0. 已定决策（方案 3）

用户拍板：**日K/周K/月K 蜡烛继续用自研 `KLineChart`，分时走势改用 `KuiklyChartKit` DSL。**

| 模式 | 渲染器 | 说明 |
|---|---|---|
| 分时 TIMELINE（全尺寸） | ✅ **换成 KuiklyChartKit**（`KuiklyTimelineChart`） | 库强项：面积图 + 十字线/Tooltip/平移/动画 |
| 日K / 周K / 月K | 保留自研 `KLineChart` | 库**不支持蜡烛图**，不碰 |
| 分时 MINI/COMPACT 迷你 sparkline（34/42/62px） | 保留自研 `MiniTimeline` | 纯装饰小图，无交互，换库收益低、反而重 |

> 代价：两套绘制层并存（KuiklyChartKit 管分时、自研 Canvas 管 K线）。维护面翻倍，但换来"分时体验升级 + K线零改动风险"，是当前最稳的选择。

---

## 1. 现状（被替换对象）

`StockDetailPage` / 卡片渲染器里的分时调用链（注意：分时走的是 `MiniTimeline`，不是 `KLineChart`）：

```
StockQuoteCardRenderer（行情卡 全尺寸）
  └─ MiniTimeline(container, model, context, height = 128f)        ← 换

StockChartCardRenderer.render
  ├─ MINI 密度  → MiniTimeline(... 34f)        ← 保留（迷你 sparkline）
  ├─ COMPACT    → MiniTimeline(... 62f)        ← 保留
  ├─ TIMELINE   → MiniTimeline(... 132f)       ← 换
  └─ K_LINE     → KLineChart(... DAY/WEEK/MONTH)  ← 保留（自研蜡烛）
```

数据字段（`data/provider/QuoteProvider.kt`）：

```kotlin
data class QuotePoint(val time: String, val price: Double, val volume: Double = 0.0)
data class Quote(
    val previousClose: Double,            // 昨收基准
    val rising: Boolean get() = change >= 0.0,
    val timeline: List<QuotePoint> = emptyList(),   // 分时序列
    ...
)
```

分时图的关键现状（自研 `MiniTimeline`，`StockCardRenderers.kt:530`）：纯 Canvas 画价格折线 + 昨收虚线基准，**无十字光标、无 Tooltip、无平移、无动画**。这正是要被 KuiklyChartKit 补上的部分。

---

## 2. 接入方式（工程动作，需授权）

库**未发布公共 Maven**，官方给两条路（README 原文）：

**A. 源码接入（推荐，可改可调试）**
- `git submodule add https://github.com/Shen-hk/KuiklyChartKit vendor/KuiklyChartKit`
- `settings.gradle.kts` 增加：
  ```kotlin
  include(":chartkit")
  project(":chartkit").projectDir = file("vendor/KuiklyChartKit/chartkit")
  ```
- `shared/build.gradle.kts` 的 `commonMain` 加：
  ```kotlin
  dependencies { implementation(project(":chartkit")) }
  ```

**B. 发布到本机 Maven**
```bash
cd vendor/KuiklyChartKit && ./gradlew :chartkit:publishToMavenLocal
# 本工程：
dependencies { implementation("com.guet.liang.kuiklychartview:chartkit:1.0.0") }
```

⚠️ **首要风险 · Kuikly 版本 skew**：KuiklyChartKit README 标注基于 **Kuikly 2.7.0**，而 StockChat 当前是 **`KUIKLY_VERSION = "2.25.0"`**（`buildSrc/.../KotlinBuildVar.kt`）。差了 17 个 minor 版本。
- 走 **源码接入（A）** 时，chartkit 源码会跟着 StockChat 的 2.25.0 一起编译，skew 可控；但必须跑 `./gradlew :chartkit:testDebugUnitTest` 确认它用到的 Kuikly Core API 在 2.25.0 没断（库有 44 个单测，正好用来验证）。
- 走 **制品（B）** 把 chartkit 锁死在 2.7.0 编译则更危险（二进制层面可能与 2.25.0 不兼容）。**优先 A**。

---

## 3. 分时走势 KuiklyTimelineChart（完整 DSL，API 已核对 README）

> ⚠️ **README 与实现不符（重要）**：README 速览写的是 `Chart { labels/series·area·line·axes/grid/interaction/animation/theme }` + `event { pointSelected/viewportChanged }`，但库**实际**是 `AreaChart`/`BarChart` 等 `ViewContainer` 扩展 + `attr { data/area/yAxis/line/... }` + `event { onItemSelected }`。**下方为对照 `KuiklyTimelineChart.kt` 源码核实的真实 DSL**（已在 Kuikly 2.25.0 下 `:shared:compileKotlinJs` 编译通过）。
> 完整可复制代码见 **`shared/src/commonMain/kotlin/com/kuikly/stockchat/cards/stock/KuiklyTimelineChart.kt`**；权威图表设计见 **`docs/19-详情页完整设计方案_v1.0.md` §4**。

```kotlin
internal fun KuiklyTimelineChart(
    container: ViewContainer<*, *>,
    quote: Quote, context: CardContext,
    height: Float = 132f,
    onPointSelected: (label: String, value: Float) -> Unit = { _, _ -> },
) {
    val pts = quote.timeline
    val prices = pts.map { it.price.toFloat() }
    val baseline = quote.previousClose.toFloat()
    val isRising = quote.rising
    val lineColor = if (isRising) RISE else FALL
    val fillColor = if (isRising) RISE_FILL else FALL_FILL

    // yAxis 对称到 previousClose 上下，使昨收落在视觉中线（AreaChart 会填到 0，不能用恒定 series 画基准）
    val lo = minOf(prices.minOrNull() ?: baseline, baseline)
    val hi = maxOf(prices.maxOrNull() ?: baseline, baseline)
    val pad = ((hi - lo) * 0.12f).coerceAtLeast(0.01f)

    val pointSeries = ChartSeries(
        name = "价格",
        items = pts.mapIndexed { i, p -> ChartPoint(x = i.toFloat(), y = p.price.toFloat(), label = p.time) },
        color = lineColor,
    )
    val timelineTheme = ChartTheme.light().copy(
        backgroundColor = PAGE, axisColor = GRID, gridColor = GRID,
        labelColor = INK3, selectionColor = lineColor,
    )

    container.AreaChart {                              // 主图：分时面积 + Tooltip + 数据过渡
        attr {
            height(height); marginTop(10f); alignSelfStretch()
            theme = timelineTheme
            data(pointSeries)
            xAxis { visible = false }
            yAxis { visible = true; includeZero = false
                    min = lo - pad; max = hi + pad; tickCount = 4 }
            line { smooth = true; showPoints = false; lineWidth = 2f }
            area { fillColors = listOf(fillColor) }
            legend { visible = false }; grid { visible = true }
            tooltip { enabled = true }
            dataTransition { enabled = true; durationMs = 420 }
        }
        event { onItemSelected { sel -> onPointSelected(sel.item.label, sel.item.y) } }
    }

    val barSeries = ChartSeries(                        // 成交量副图（BarChart 用 data()，无 barData）
        name = "成交量",
        items = pts.map { BarEntry(it.time, it.volume.toFloat()) },
        color = VOL,
    )
    container.BarChart {
        attr {
            height(56f); marginTop(6f); alignSelfStretch()
            theme = timelineTheme
            data(barSeries)
            xAxis { visible = false }; yAxis { visible = false }
            legend { visible = false }
            bars { showValueLabels = false; cornerRadius = 2f }
            tooltip { enabled = true }
            dataTransition { enabled = true; durationMs = 420 }
        }
    }
}
```

**已核对（无需再确认）**：`area` 填充用 `area { fillColors = listOf(color) }`（~20%α 同色）；昨收基准用 y 轴对称范围 + caption 代替（库不暴露任意基准线）；`BarChart` 数据方法是 `data(ChartSeries<BarEntry>)`，不是 `barData`（`barData` 仅 `MixedChart` 有）；`interaction.maxRenderPointCount` 校验 `>=4`，分时点数可能不足故不设置（走自动预算）。

---

## 4. K_LINE 模式 —— 不改动

`KLineChart`（`StockCardRenderers.kt:200`）继续服务日K/周K/月K，蜡烛绘制逻辑、MA 线、数据来源（`quote.kLines`/`weekKLines`/`monthKLines` + `KLineCalculator`）一律不动。本方案不引入"简化 K线"。

---

## 5. 主题对接（浅色版 token）

`ChartTheme.light().copy(...)` 支持 `backgroundColor / axisColor / gridColor / labelColor / selectionColor`（对照 `KuiklyTimelineChart.kt` 实际用法）。映射：

```kotlin
backgroundColor = Color(0xFFF5F6F8)   // --page
mutedTextColor  = Color(0xFF69748F)   // --ink-3 (4.67:1，过 AA)
gridColor       = Color(0xFFE2E8F0)   // 浅灰网格
// 十字线/Tooltip 文字建议白底 + --ink-1，与卡片一致
```

红涨绿跌语义色：`RISE = 0xFFD92E2E`（4.79:1）、`FALL = 0xFF0C7A45`（5.2:1），均过 WCAG AA 4.5。接入后建议从 `context.theme.rise/fall` 读取，避免硬编码。

---

## 6. 相对自研 MiniTimeline 的改进（仅分时）

| 维度 | 自研 MiniTimeline | 换 KuiklyChartKit 后 |
|---|---|---|
| 十字光标 + Tooltip | 无 | 库自带，命中/事件（`onItemSelected`）已单测 |
| 切换/实时动画 | 无 | `dataTransition { enabled; durationMs }` 数据过渡，连续更新不回跳 |
| 面积渐变质感 | 手画折线 | `area { fillColors }` 原生同色半透明填充 |
| 页面滚动冲突 | 需小心 | 库明确"只吃横向、不阻塞纵向" |
| 成交量副图 | 无 | `BarChart` 独立渲染（库未暴露命令式 setViewport，主副图暂不联动） |
| 空数据态 | 需自写 | 库内置 |

---

## 7. 落地步骤（确认后我来执行）

1. **你授权**我动工程结构 → 我执行 §2 的 submodule 接入（`settings.gradle.kts` + `shared/build.gradle.kts` + `.gitmodules`），并跑 `:chartkit:testDebugUnitTest` 在 Kuikly 2.25.0 上验证（首要风险）。
2. 把 `docs/KuiklyTimelineChart.kt` 拷进 `shared/src/commonMain/kotlin/com/kuikly/stockchat/cards/stock/`，颜色改读 `context.theme`。
3. 在 `StockCardRenderers.kt` 改两处 TIMELINE 全尺寸调用（见下方 diff），`KLineChart` 与迷你 sparkline 不动。
4. 构建 `:androidApp:assembleDebug` + `:shared:compileKotlinJs` 双端验证。
5. 顶栏进度条（HTML 原型已改 `top:96px`）同步映射到 `StockDetailPage.kt` 的 topbar 组件。

### 调度改动 diff（`StockCardRenderers.kt`）

```diff
  // StockQuoteCardRenderer —— 行情卡全尺寸分时
- MiniTimeline(container, model, context, height = 128f)
+ KuiklyTimelineChart(container, model.quote, context, height = 128f)

  // StockChartCardRenderer.render —— TIMELINE 分支
- if (model.mode == StockChartMode.TIMELINE) MiniTimeline(container, StockQuoteCardModel(model.quote), context, height = 132f)
- else KLineChart(container, model, context)
+ if (model.mode == StockChartMode.TIMELINE) KuiklyTimelineChart(container, model.quote, context, height = 132f)
+ else KLineChart(container, model, context)
  // 注：MINI/COMPACT 的 MiniTimeline(34f/62f) 与 K_LINE 的 KLineChart 均保留
```

> 两处都在 `cards.stock` 包内，`KuiklyTimelineChart` 无需额外 import。
