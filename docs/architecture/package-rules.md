# 架构门禁：包依赖规则

> 落地于 `scripts/check_architecture.sh`，由 `./gradlew architectureCheck` 调用。
> 目标：把「反向依赖」从口头约定变成**会失败的构建**。

## 1. 目标架构与强制依赖方向

StockChat 的目标分层是 **Page / Component / State / Data** 四层。依赖只允许**自上而下**：

```text
                    ┌──────────────────────────────────────────┐
   Page 层          │  page/*Page.kt                            │  只做装配 + Effect adapter
                    └───────────────┬──────────────────────────┘
                                    │ 允许向下
                    ┌───────────────▼──────────────────────────┐
   Component 层     │  <feature>/**/component/**, page/components │  只做 UI，拿只读 accessor + Actions
                    └───────────────┬──────────────────────────┘
                                    │ 允许向下
                    ┌───────────────▼──────────────────────────┐
   State 层         │  <feature>/**/state/**                    │  纯状态机：observable / Reducer / Coordinator
                    └───────────────┬──────────────────────────┘
                                    │ 允许向下
                    ┌───────────────▼──────────────────────────┐
   Data 层          │  data/**                                  │  数据源、缓存、序列化、网络
                    └──────────────────────────────────────────┘

   ── 横向（任何层都不许反向依赖）────────────────────────────────
   shared / foundation  ──✗──▶  任何 Feature（chat / detail / market / risk / ...）
   Component            ──✗──▶  Page 层
   State                ──✗──▶  Page 层 / UI
```

一句话记法：**上层可以知道下层，下层永远不知道上层。**

## 2. 六条规则

| 编号 | 规则 | 为什么 |
|---|---|---|
| **R1** | `data/**` 不得 import `page.*`、`com.tencent.kuikly.core.views.*`、响应式 API（`core.reactive`）、平台桥/导航（`core.nvi` 非 serialization 部分）、`Toast` | 数据层一旦摸到 UI，就无法在测试里替换、也无法被别的 Feature 复用 |
| **R2** | `**/component/**`（含 `page/components/`）不得 import `Repository` / `Store` / `Provider` / `Bridge` / `SharedPreferences` / `PagerScope`，也不得 import `page.*` | 组件只应拿「只读 accessor + Actions」；直接抓数据源会让 UI 与存储/网络耦合，也让组件无法跨 Feature 复用 || **R3** | `**/state/**` 不得 import `page.*`、Kuikly View、具体平台实现（`core.module`） | 状态机必须能在没有 UI 的环境下跑单测（项目里已有一批 fake scheduler 测试就是这么做的） |
| **R4** | `shared.*` / `foundation.*` 不得 import 任何 Feature（`chat` / `detail` / `market` / `risk` / `watchlist` / `glossary` / `alert`）与 `page.*` | 共享基座是所有人依赖的东西；它依赖 Feature 就变成了循环依赖的种子 |
| **R5** | `page/*Page.kt` 的 `setTimeout(` / `Provider(` / `by observable` **计数只许降不许升** | Page 是装配点，不是时序 owner。新逻辑应当写进 Coordinator，而不是往 Page 里再加一个 Timer |
| **R6** | `page/components/` **禁止新增文件** | 该目录只允许把文件**搬出去**（去 `foundation/**` 或各 Feature 的 `component/`）。它是历史遗留的「混合收纳」目录，不是通用组件库 |

### 补充说明（口径）

- **R1 不拦 `core.nvi.serialization.json`**：那是数据层的序列化工具，数据层用它是对的。被拦的是 `core.nvi` 下的桥/导航能力。
- **R2 的「数据访问层」以 `data` 包界定**：只有 `com.kuikly.stockchat.data.**` 下的 `*Repository` / `*Store` / `*Provider` / `*Bridge` 才算违规。否则 Feature 自己的同名后缀类型会被误伤（例：`detail.domain.ContextChipStore` 是纯模型，不是存储）。
- **R2 的「不得 import `page.*`」是本项目最密集的违反点**：既有债务记录在架构白名单中，新代码不得增加命中。
- **R5 的计数口径**：`grep` 级别的字面出现次数（`setTimeout(` / `Provider(` / `by observable`），不是「语义上的定时器个数」。基线由 `--baseline` 生成，检查与基线同源，所以口径一致即可。
- **R5 / R6 不是「必须清零」，而是「不许变差」**。它们对付的是「顺手再塞一个」——这是 Page 膨胀的主要机制。

## 3. 白名单（既有债务）

`scripts/architecture-allowlist.txt` 逐条登记**已经存在**的违规。

```text
<相对路径> | <规则号> | <被豁免的 import 符号> | <原因 / 预计删除的工作包>
```

- 相对路径 = 相对 `shared/src/commonMain/kotlin/com/kuikly/stockchat/`
- **匹配粒度 = 文件 × 规则 × 符号**（三级）。因此：同一个文件在同一个规则下，只要新增一个「不在表里」的符号，门禁**依然失败**。
- **禁止通配符、禁止整目录豁免。** 每一行都必须写清「谁在什么时候删掉它」。
- 没有删除计划的条目 = 把技术债永久化，评审时会被打回。

## 4. 正例 / 反例

**反例（会被 R3 拦下）** —— State 层直接引用页面里的 UI 类型：

```kotlin
// detail/chart/state/DetailChartUiState.kt
package com.kuikly.stockchat.detail.chart.state
import com.kuikly.stockchat.page.components.ChartFlag   // ✗ R3：state 依赖 page
```

**正例** —— 类型下沉到 Feature 自己的 domain，两边都依赖它：

```kotlin
// detail/domain/ChartFlag.kt
package com.kuikly.stockchat.detail.domain
enum class ChartFlag { ... }

// detail/chart/state/DetailChartUiState.kt
import com.kuikly.stockchat.detail.domain.ChartFlag    // ✓ 同层内依赖
```

**反例（会被 R2 拦下）** —— 组件直接抓数据源：

```kotlin
// detail/page/component/DetailChartCard.kt
import com.kuikly.stockchat.data.provider.QuoteRepository     // ✗ R2
```

**正例** —— 组件只接收只读数据与回调：

```kotlin
internal fun ViewContainer<*, *>.DetailChartCard(
    series: List<Double>,            // 只读数据
    onScrub: (Int) -> Unit,          // Action
)
```

**反例（会被 R6 拦下）** —— 往 `page/components/` 加新文件：

```bash
echo 'package com.kuikly.stockchat.page.components' > \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/page/components/MyNewWidget.kt   # ✗ R6
```

**正例** —— 放到真正归属的地方：

```text
通用 → shared/src/commonMain/kotlin/com/kuikly/stockchat/foundation/ui/...
Feature 私有 → shared/src/commonMain/kotlin/com/kuikly/stockchat/<feature>/page/component/...
```

## 5. 门禁不管什么

- **不管产品行为**：视觉、文案、动画参数、手势阈值、路由、AI Prompt、降级策略、平台协议都不在扫描范围内。
- **不管是否已接 CI**：项目当前没有 CI 配置，门禁靠本地 `./gradlew architectureCheck` 与各工作包的验收流程执行。
- **不替代人工评审**：它只能证明「依赖方向没变差」，不能证明设计对不对。
