# 包依赖规则（强制，违反即构建失败）

> 建立于 2026-09-12（doc 46 A-1）｜扫描对象：`shared/src/commonMain/kotlin/com/kuikly/stockchat`
> 执行：`./gradlew architectureCheck`（等价 `bash scripts/check_architecture.sh`）

## 1. 目标架构：Page / Component / Data / State 四层分离

```text
                      ┌───────────────────────────────┐
                      │  page/**  （Page：只有装配与渲染）│
                      └───────┬──────────────┬────────┘
                              │              │
                 ┌────────────▼───┐    ┌─────▼──────────────┐
                 │ feature 组件   │    │ feature state      │
                 │ chat/**/component │  │ **/state/**        │
                 │ detail|risk|... │   │ Coordinator 等     │
                 └────────┬───────┘    └─────┬──────────────┘
                          │                  │
                 ┌────────▼──────────────────▼────────┐
                 │  data/**   （Port / Store / Repo）  │
                 └────────┬───────────────────────────┘
                          │
   ┌──────────────────────▼───────────────────────────┐
   │ foundation/** · shared/**（设计模型 / UI 基座 /  │
   │ 平台适配器 app/platform）—— 不依赖任何 Feature    │
   └──────────────────────────────────────────────────┘

  app/assembly/**（App 装配根）在所有人之上：只有它能拿 Pager / 建 Provider / 组装 Store。
```

**一句话**：依赖只能**从上往下**，`foundation` / `data` 永远不知道上面有谁。

## 2. 六条规则

| 编号 | 规则 | 典型反例（会被拦下） |
|---|---|---|
| **R1** | `data/**` 不得 import `page.*`、`com.tencent.kuikly.core.views.*`、响应式 API（`core.reactive.*`）、`Navigation`、`Toast` | `data/provider/X.kt` 里 `import com.kuikly.stockchat.page.MarketPage` |
| **R2** | `**/component(s)/**` 不得 import Store / Repository / Provider / Bridge / SharedPreferences / `PagerScope` | `detail/page/component/X.kt` 里 `import ...WatchlistStore` |
| **R3** | `**/state/**` 不得 import `page.*`、Kuikly View、具体平台实现（`platform.*` / `java.*` / `android.*`） | `detail/chart/state/X.kt` 里 `import ...page.components.ChartFlag` |
| **R4** | `foundation/**`、`shared/**` 不得 import 任何一个 Feature（chat / detail / market / risk / watchlist / glossary / alert） | `foundation/ui/X.kt` 里 `import ...chat.ChatMessage` |
| **R5** | `page/*Page.kt` 的 `setTimeout(` / `XxxProvider(` 构造 / `by observable(` **计数不得增加**（基线 `scripts/architecture-baseline.txt`） | 在 `WatchlistPage.kt` 里直接 `TencentQuoteProvider(pagerId)` |
| **R6** | `page/components/**` **禁止新增文件**（该目录只出不进） | 往 `page/components/` 里加一个 `NewCard.kt` |

**正例（这样写不会被拦）**

```kotlin
// ✅ data 层只依赖端口；平台能力由 app/platform 的适配器实现
internal interface PlatformScheduler { fun schedule(delayMillis: Long, block: () -> Unit) }

// ✅ component 从参数拿数据，不自己去取
fun ViewContainer<*, *>.NewsCard(item: NewsItem, onClick: () -> Unit) { ... }

// ✅ state 层只依赖本层与 data 的接口
class RiskDataCoordinator(private val repository: RiskRepository) { ... }
```

**反例（会失败）**

```kotlin
// ❌ component 里直接读 Store —— 组件不可复用、测试要造存储
fun ViewContainer<*, *>.Row() { val rows = WatchlistStore(storage).list() }

// ❌ state 层 import page 侧类型 —— 层次倒挂，page 一改 state 就崩
import com.kuikly.stockchat.page.detail.AnchorIndex
```

## 3. 命中之后怎么办

1. **新写出来的违规 → 改代码。** 门禁存在的唯一目的就是这个。
2. **既有历史债务 → 登记豁免**，在 `scripts/architecture-allowlist.txt` 追加一行：

   ```text
   相对路径 | 规则编号 | 原因 | 预计清除的工作包
   ```

   纪律：
   - **禁止整目录豁免**，必须逐文件逐条；
   - 每条都要写清「为什么现在还不能改」与「由哪个工作包清掉」；
   - 债务清掉后**必须删掉对应行**，否则门禁对这条永久失效。
3. **R5 计数若不慎增加**：先问自己是不是把业务 Timer / Provider 又塞回页面了。确属正当新增（例如纯 UI 的焦点恢复定时器），才在提交里显式上调 `scripts/architecture-baseline.txt` 并说明理由——基线**只允许下调来收紧**，上调必须留痕。
4. **R6 想加文件**：放到 `foundation/**`（通用）或对应 Feature 包（`chat/**`、`risk/**`、`watchlist/**`…）。`page/components/` 是待拆目录，不是组件库。

## 4. 免测范围

门禁**只扫 `commonMain`**：平台源集（`androidMain` / `iosMain` / `jsMain` / `ohosArm64Main`）里的适配器天然要碰平台 API，不在此列。四端宿主层（`androidApp` / `iosApp` / `ohosApp` / `h5App`）不扫。
