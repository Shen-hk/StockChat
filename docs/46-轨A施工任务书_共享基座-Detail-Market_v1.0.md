# 轨 A 施工任务书 — 共享基座 / Detail / Market

> 版本：v1.0　日期：2026-09-12
> 基线：`master` HEAD `fac42b1`　**实测基线：`./gradlew :shared:testDebugUnitTest` = 298 tests / 0 failures / 0 errors**
> 上游：`docs/44`（四层架构审计与整改纲领）、`docs/45`（双人拆分与预算，§12 为精简版）
> **本文件自包含**：拿到本文件即可开工，不必读其它文档。但第 11 节的 `AGENTS.md` 铁律必须先读。

---

## 0. 怎么用这份文档

1. 你是**轨 A 的唯一写入 owner**，负责第 2 节列出的 5 个工作包。
2. 按 **A-1 → A-2 → A-3 → A-4 → A-5** 顺序做，**每个工作包一个独立 commit**，可独立回滚。
3. 每个工作包开工前，先跑第 10 节的「自动验收」确认基线是绿的；收工时再跑一遍。
4. 遇到与轨 B 的文件冲突，按第 3 节的契约处理，**不要临场协商改契约**——契约一改，两条轨都会返工。

---

## 1. 背景（读一遍就够了）

StockChat 是 Kuikly/KMP 多端项目（Android / iOS / 鸿蒙 / H5）。目标架构是 **Page / Component / Data / State 四层分离**。

当前状态（`docs/44` 的审计结论，已逐条复核）：

- 综合得分 **64/100**，属于「局部达到」；
- 项目**已经能写组件**，但「`page/components` 下同时收纳通用组件和 Feature 私有组件」，且 Feature 组件反向 import Page；
- `docs/44` 的核心判断：**不能把「已经有 components 目录」视为已经形成通用组件库**；先完成归属校正与依赖门禁，再做模块化，风险最低。

验收线是 **80/100**。轨 A 与轨 B 合计 27 人日完成 P0 与部分 P1 后，可达标。

### 硬边界（本轨所有工作包共同禁止）

**不改**产品功能、视觉、文案、动画参数、手势阈值、路由、AI Prompt、降级策略、平台协议。
**不与**包改名、依赖升级、新功能混在同一提交。

本轨做的是**物理归档 + 职责搬家**，做完之后手测应当看不出任何差异。

---

## 2. 轨道 A 总览

| 序 | 工作包 | 人日 | Token 预算 | 前置 |
|---:|---|---:|---:|---|
| A-1 | P0-A 建立自动架构门禁 | 1.0 | 80–150k | 无 |
| A-2 | P0-C-shared 共享组件归档 | 1.5 | 210–320k | 无（但须早于 B-2，见 §3.3）|
| A-3 | P0-C-detail Detail 组件与模型归档 | 1.5 | 210–330k | 无 |
| A-4 | P1-Detail 反向依赖与残余状态收口 | 5.0 | 650k–1.20M | A-3 |
| A-5 | P1-Market 建立四层纵切 | 4.5 | 500–950k | 轨 B 的 B-1 |
| | **合计** | **13.5** | **2.31M** | |

**关键路径 13.5 人日**（与轨 B 同步开工，两轨同一天收工）。

### 完成后的目标状态

| 指标 | 现在 | 完成后 |
|---|---:|---:|
| `page/components/` 文件数 | 20 | ≤16（通用件已外迁，Chat/Detail/Risk 私有件归位）|
| `detail/**` 对 `page.*` 的 import | **53 处** | **0** |
| `page/detail/` 包 | 6 文件 / 624 行 | 删除（模型迁入 `detail/domain`）|
| `StockDetailPage.kt` | 3,143 行 | < 900 行 |
| `MarketPage.kt` | 1,660 行 | < 700 行 |
| 架构门禁 | 无 | `./gradlew architectureCheck` 可跑，< 5 秒 |
| 单测 | 298 | ≥ 298（只增不减）|

---

## 3. 协作契约（与轨 B，最重要的一节）

### 3.1 文件域归属

| 文件域 | Owner | 说明 |
|---|---|---|
| `page/components/AppChrome.kt` | **B 独占** | 由 B 在 B-2 一次性整体拆开并删除。**A 全程不得触碰该文件**（A-2 需要 AppTopBar/DataModeBadge 落到 `foundation/ui` 的部分，由 B 顺带落位）|
| `page/components/` 其余文件 | **A** | LineIcons / SourceStampLine / DetailBoardBlocks / DetailTimelineChart / NewsMarquee / NewsTape / FeatureTile / SegmentBar / UndoBar / CardSheet / DivergingBar / AtmosphereBackdrop / GeneratedSvgCanvasIcons / MarketNarrativeAxis / InsightComponents / RowGestureLayer |
| `page/StockDetailPage.kt` | **A 独占** | |
| `page/MarketPage.kt` | **A 独占** | |
| `page/ChatPage.kt` | **B 独占** | |
| `page/RiskMapPage.kt`、`page/WatchlistPage.kt`、`page/GlossaryPage.kt`、`page/AlertCenterPage.kt` | **B 独占** | |
| `page/detail/**`、`detail/**` | **A 独占** | |
| `market/**`（新建） | A 独占 | |
| `chat/**` | **B** | 但 A-2 有权改其 **import 行**（见 §3.2）|
| `risk/**`（新建） | B 独占 | |
| `data/**` | **B 独占**（B-1 期间）| A 只新建 `market/data/`，不改既有文件 |
| `glass/**`、`cards/**` | **A**（仅 A-2 涉及的 CardShell / SourceStampLine）| 其余由 B 在 B-1 改 |
| 根 `build.gradle.kts`、`settings*.gradle.kts` | **A 独占** | A-1 注册 `architectureCheck` |
| `scripts/**`、`docs/architecture/**` | **A 独占** | |
| `androidApp/`、`iosApp/`、`ohosApp/`、`h5App/` | 本轨不动 | P1-Platform 已砍出本轮（见 §12）|

### 3.2 A-2 的跨轨写权限（唯一例外，务必遵守）

A-2 要把 `LineIcons` / `SourceStampLine` / `CardShell` / `GlassRenderer` 迁到 `foundation/**` 与 `shared/cards/component`。**这些符号同时被轨 B 的文件引用**，实测引用方：

| 被迁符号 | 引用它的 B 侧文件 |
|---|---|
| `page.components.LineIcon*` | `page/ChatPage.kt`、`page/WatchlistPage.kt`、`page/AlertCenterPage.kt`、`page/components/AppChrome.kt`、`chat/composer/component/*`、`chat/welcome/component/*`、`chat/session/component/*` |
| `cards.components.CardShell` | `page/ChatPage.kt`、`page/WatchlistPage.kt`、`page/GlossaryPage.kt`、`page/components/ChatScaffolding.kt`、`page/components/ChatMessageComponents.kt`、`chat/session/component/*` |
| `glass.GlassRenderer` | `page/ChatPage.kt`、`page/CardGalleryPage.kt`、`chat/session/component/*`、`cards/core/CardContext.kt`、`base/BasePager.kt` |
| `SourceStampLine` | `page/MarketCalendarPage.kt`、`cards/stock/MarketCardRenderers.kt` |

**契约**：

1. A-2 期间，A 有权修改**任何文件**，但**只准改 `import` 行**，不得改动逻辑、布局、参数、动画。
2. B 在 **A-2 完成前不得修改 `page/components/` 下的任何文件**。
3. A-2 完成后必须在 commit message 里明确列出「本次跨轨改动的 B 侧文件清单」，让 B 能核对其工作区。

### 3.3 串行点

| 编号 | 时点 | 内容 |
|---|---|---|
| **S0** | day ≈ 1（A-1 完成） | 门禁就绪，此后每个提交都跑 `architectureCheck`。B 从 day 0 起并行做 B-1，不受影响 |
| **S1** | day ≈ 2.5（A-2 完成） | **B 的 B-2 才能开工**（B-2 要拆 `AppChrome.kt`，需要 A-2 的新位置已就绪）。B 的 B-1 恰好占满 day 0–2.5，天然对齐，无空转 |
| **S2** | day ≈ 4（A-3 完成） | A-4 才能开工 |

**为什么 A-2 要排在 A-1 之后而不是更早**：A-1 只新增文件（`scripts/`、`docs/architecture/`、Gradle task），与任何人的工作区零重叠，1 人日买断全程序防回退——它必须最先做。

---

## 4. A-1 · 建立自动架构门禁

**前置**：无　**预算**：1.0 人日 / 80–150k　**性价比**：5.00 分/人日（本轨最高）

### 目标

让「反向依赖」从口头约定变成**会失败的构建**。这是后续所有工作不被回退的唯一保障。

### 范围（新增文件）

| 文件 | 内容 |
|---|---|
| `scripts/check_architecture.sh` | 扫描 `shared/src/commonMain/kotlin` 的 `package` / `import`，命中禁止规则即非零退出 |
| `scripts/architecture-allowlist.txt` | 既有债务白名单，**逐条记录「文件 + 原因 + 删除它的工作包」** |
| `docs/architecture/package-rules.md` | 把架构规则固化成短规则（团队可读版）|
| `shared/build.gradle.kts` | 注册 `architectureCheck` 任务，调用上面的脚本 |
| `docs/architecture/README.md` | 一句话说明怎么跑、怎么加白名单 |

### 必须覆盖的 6 条规则

```text
1. data        不得 import page.* / com.tencent.kuikly.core.views.* / 响应式 API / Navigation / Toast
2. component   不得 import Repository / Store / Provider / Bridge / SharedPreferences / PagerScope
3. state       不得 import page.* / Kuikly View / 具体平台实现
4. shared、foundation  不得 import 任何一个 Feature（chat / detail / market / risk / watchlist / glossary / alert）
5. Page 文件   新增 setTimeout / Provider 构造 / 业务 by observable 时失败
6. page/components  禁止新增文件（只允许逐步搬出）
```

### 怎么改（4 步）

1. **先摸清现状**：跑一遍规则，把命中项全量导出。预期命中量参考（实测）：
   - 规则 3（state → page）：**5 个文件** —— `detail/ai/state/AiInsightPrompts.kt`、`detail/chart/state/DetailChartUiState.kt`、`detail/chart/state/DetailChartInteractionCoordinator.kt`、`detail/overlay/state/DetailOverlayCoordinator.kt`、`detail/overlay/state/DetailOverlayUiState.kt`；这 5 条由 **A-3 / A-4 清除**。
   - 规则 2 命中主要在 `detail/page/component/*`（53 处中的大部分），由 **A-3 / A-4 清除**。
2. **写 allowlist**：格式建议 `相对路径 | 规则编号 | 原因 | 预计删除的工作包`。例如：
   ```text
   detail/chart/state/DetailChartUiState.kt | R3 | 依赖 page.components.ChartFlag | A-3
   ```
   **禁止整目录豁免**——必须逐文件逐条。
3. **写规则文档**：`docs/architecture/package-rules.md` 用第 5 节的「强制依赖方向」图 + 上面 6 条规则，配一两个正/反例。
4. **注册 Gradle 任务**。`shared/build.gradle.kts` 目前 178 行、**没有任何自定义 task**（实测），在文件末尾追加即可：
   ```kotlin
   tasks.register<Exec>("architectureCheck") {
       group = "verification"
       description = "扫描 commonMain 的 package/import，阻止新增反向依赖"
       commandLine("bash", "scripts/check_architecture.sh")
   }
   ```

### 不做

- 不修**任何**既有违规（那是 A-3/A-4 的活）。A-1 只做「测量 + 拦截新增」。
- 不引入 detekt / ktlint / Konsist 等第三方依赖。脚本用 `grep` / `awk` 即可，保持零依赖、可离线跑。
- 不接 CI（项目当前**没有** CI 配置，实测无 `.github` / `.gitlab-ci.yml` / `.circleci`）。

### 自动验收

```bash
# 1. 基线通过
./gradlew architectureCheck            # 期望：BUILD SUCCESSFUL

# 2. 加一条违规应失败
echo 'import com.kuikly.stockchat.page.MarketPage' >> \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/data/provider/QuoteClock.kt
./gradlew architectureCheck            # 期望：BUILD FAILED，且报出该文件行号
git checkout -- shared/src/commonMain/kotlin/com/kuikly/stockchat/data/provider/QuoteClock.kt

# 3. 恢复后再次通过
./gradlew architectureCheck            # 期望：BUILD SUCCESSFUL

# 4. 速度
time ./gradlew architectureCheck       # 期望：脚本本身 < 5 秒（Gradle 冷启动不计）
```

### 完成定义

- [ ] 6 条规则全部生效
- [ ] allowlist 逐条含「文件 + 原因 + 删除工作包」，无整目录豁免
- [ ] 手工加违规 → 失败；删除 → 恢复通过
- [ ] 脚本单次执行 < 5 秒
- [ ] `docs/architecture/package-rules.md` 已写

---

## 5. A-2 · 共享组件归档（P0-C-shared）

**前置**：无，但**必须早于 B-2**（见 §3.2）　**预算**：1.5 人日 / 210–320k

### 目标

把「通用组件」从 `page/components/` 里物理搬到 `foundation/**`，让共享基座有一个**独立的、不依赖 Feature 的**目录。这是文档 44 所说「不能把已有 components 目录视为通用组件库」的整改动作。

### 范围（实测行数）

| 现在 | 目标位置 | 行数 | 只搬不合并的私有 helper |
|---|---|---:|---|
| `page/components/LineIcons.kt` | `foundation/ui/icon/LineIcons.kt` | 827 | 底层 path helper 保持 `private` |
| `page/components/InsightComponents.kt` 中的 `SourceStampLine` + `cards/components/SourceStampLine.kt` | `foundation/ui/feedback/SourceStampLine.kt` | 56 + ? | **两套入口合并为一个 API**，Card 与 Insight 都调它 |
| `glass/GlassRenderer.kt`（tuning / token） | `foundation/design/GlassTokens.kt` | — | |
| `glass/GlassContainer.kt`（View wrapper） | `foundation/ui/surface/GlassSurface.kt` | 228（两文件合计）| |
| `cards/components/CardShell.kt` | `shared/cards/component/CardShell.kt` | 159 | **保留 `CardContext` / Events**；不得反向 import Feature |
| `page/components/FeatureTile.kt` | 暂留原位 | 78 | 见「不做」 |

### 怎么改（5 步）

1. **先 `git mv` 定义，再批量修 import。** 顺序必须是：移动文件 → 改 package 声明 → 一次性全仓修 import → 编译。不要边移边编译，会产生大量中间态报错。
2. **LineIcons（影响面最大，11 个文件）**：实测引用方为 `page/ChatPage.kt`、`page/AlertCenterPage.kt`、`page/WatchlistPage.kt`、`page/StockDetailPage.kt`、`page/MarketPage.kt`、`chat/composer/component/ComposerActionRow.kt`、`chat/composer/component/ComposerInputRow.kt`、`chat/welcome/component/ChatWelcomeSection.kt`、`chat/session/component/SessionOverlays.kt`、`chat/session/component/ChatContextOverlays.kt`、`detail/page/component/DetailOverlays.kt`。**这些文件的 import 行由 A 改，逻辑一行不动**（§3.2 契约）。
3. **SourceStampLine 合并**：两套入口先做 API 对齐（参数名、默认值、色调来源），确认两个调用方（`cards/stock/MarketCardRenderers.kt`、`page/MarketPage.kt` / `page/MarketCalendarPage.kt`）行为一致后再删旧入口。**合并是本次唯一允许改内部实现的动作**，但对外仍须零视觉差异。
4. **Glass 拆分**：`Renderer/tuning/token` 归 `foundation/design`，`View wrapper` 归 `foundation/ui/surface`。引用方 9 个文件，同样只改 import。
5. **CardShell**：`cards/components/` → `shared/cards/component/`，保留 `CardContext` / Events 契约；引用方 10 个文件只见 import 变化。

### 不做

- **不上移 `FeatureTile`**：实测只有 4 个使用方（`ChatPage`、`WatchlistPage`、`StockDetailPage`、`detail/page/component/DetailOverlays.kt`），且三处调用的 padding / title-subtitle / action / accessibility **尚未统一**。按文档 44 §6.2，先留原位作为 foundation 候选，等统一接口后再冻结 API。
- 不上移 `UndoBar`（主要服务 Watchlist，归 B）、`SegmentBar`（主要服务 Glossary，归 B）、`NewsMarquee`/`NewsTape`（Market 与 Detail 复用尚未稳定）、Loading/Empty/Error（文案与尺寸未盘点）。
- **不合并/重命名** `GeneratedSvgCanvasIcons.kt`（1,789 行，是 `page/components` 里最大的文件，但它是图标资源，不属本次范围）。
- 不改任何 `batchDraw` Canvas 的代码（见 §11.3 的 iOS 渲染层缺口）。

### 自动验收

```bash
./gradlew architectureCheck
./gradlew :shared:testDebugUnitTest                 # 期望 ≥298，failures 0
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

### 人工验收

- **截图对比**：改前改后各截 6 张（Chat 首页、Watchlist、StockDetail、Market、AlertCenter、CardGallery——即所有 LineIcons / GlassRenderer 消费页），逐张比对**必须零差异**。
- 换肤（浅色/深色）+ 字号档（小/标准/大/特大）各切一次，确认 `fontSizeScaled` 链路未受影响。
- Android 与 H5 手测。

### 完成定义

- [ ] `foundation/ui/{icon,feedback,surface}` 与 `foundation/design` 目录已建立并有内容
- [ ] 两套 `SourceStampLine` 只剩一套
- [ ] 上述 11 + 10 + 9 个引用文件的 import 全部指向新位置，逻辑零改动
- [ ] 编译三端通过、298 单测全过、截图零差异
- [ ] commit message 列出「跨轨改动的 B 侧文件清单」

---

## 6. A-3 · Detail 组件与模型归档（P0-C-detail）

**前置**：无　**预算**：1.5 人日 / 210–330k

### 目标

把 Detail 私有的组件与纯模型从 `page` 包里拿出来，为 A-4 清除 53 处反向依赖铺路。**本包只搬位置，不改任何逻辑、参数、动画。**

### 范围（三部分）

**（1）`page/detail/` 6 个纯模型文件 → `detail/domain/`**

| 文件 | 行数 | 里面有什么（实测）|
|---|---:|---|
| `DetailRules.kt` | 256 | `enum Materiality`(L15)、`fun materialityOf`(L26)、`data class AnomalyPoint`(L49)、`fun scoreNewsSentiment`(L113)、`data class RelevanceAnchor`(L137)、`fun pickPinnedCard`(L143)、`fun shareholderFootnote`(L192)、`data class FactorSpec`(L241) |
| `DetailChartHeader.kt` | 160 | `internal data class DetailChartHeaderSnapshot`(L31) |
| `AnchorIndex.kt` | 95 | `object AnchorIndex`(L19) |
| `ContextChipStore.kt` | 47 | — |
| `OverlayArbiter.kt` | 38 | `enum class DetailOverlay`(L12) |
| `DetailCompanyProfile.kt` | 28 | `internal object DetailCompanyProfileCatalog`(L16) |

搬完后 `page/detail/` 目录**删除**。

**（2）两个大图表组件 → `detail/page/component/`**

| 现在 | 目标 | 行数 |
|---|---|---:|
| `page/components/DetailBoardBlocks.kt` | `detail/page/component/DetailBoardBlocks.kt` | 946 |
| `page/components/DetailTimelineChart.kt` | `detail/page/component/DetailTimelineChart.kt` | 980 |
| `page/components/NewsTape.kt` / `NewsMarquee.kt` 的 Detail 使用部分 | 视引用情况决定是否跟随 | 331 / 150 |

**（3）`page/StockDetailPage.kt` 里定义的 14 个具名区块 → `detail/page/component/`**

这是**实测出的确切清单**（行号取自当前 HEAD），全部从 `StockDetailPage.kt` 移出：

| 符号 | 现在行号 | 备注 |
|---|---:|---|
| `internal data class BusinessInsightItem` | 1312 | 数据类 |
| `internal fun ViewContainer<*, *>.CompanyIndustryPanel` | 1326 | |
| `internal fun ViewContainer<*, *>.SectionLabel` | 1541 | 仅 Detail 消费，归 detail |
| `internal fun ViewContainer<*, *>.TickerText` | 1579 | |
| `internal fun ViewContainer<*, *>.ChartLegend` | 1678 | |
| `internal fun ViewContainer<*, *>.BusinessInsightGrid` | 1752 | |
| `internal fun ViewContainer<*, *>.RevealBlock` | 1930 | 仅 Detail 消费，归 detail |
| `internal fun ViewContainer<*, *>.ChartSegment` | 1960 | |
| `internal fun ViewContainer<*, *>.ChartViewportControls` | 2025 | |
| `internal fun ViewContainer<*, *>.AiInsightBlock` | 2292 | |
| `internal fun ViewContainer<*, *>.ChartLoadingSkeleton` | 2508 | |
| `internal fun ViewContainer<*, *>.AttributionForecastWorkbench` | 2722 | |
| `internal fun ViewContainer<*, *>.DisclosureMaterialityBlock` | 2998 | |
| `fun balanceSegmentsFor` | — | **纯函数**，归 `detail/domain`，不是 UI |

**为什么 `RevealBlock` / `SectionLabel` 归 detail 而不归 foundation**：实测消费方只有 `StockDetailPage.kt` 与 `detail/page/component/*`（`DetailChartCard`、`DetailAiInsightBlock`、`DetailAttributionBoard`、`DetailCompanyInfoSection`），**没有任何 Market / Risk / Chat 文件引用**。按「两个以上真实 Feature 使用方才能上移」的准入规则，它们不够格进 foundation。

### 怎么改（3 步）

1. **先搬（1）纯模型**：这一步直接解掉 5 个 state 文件里的反向依赖（见 §7 的清单）。搬完编译一次。
2. **再搬（2）两个大图表组件**：`DetailTimelineChart` **内部的 Timer 和 observable 本轮不动**——A-4 才处理。本步只改文件位置与 package。
3. **最后搬（3）14 个区块**：按行号从大到小移（避免行号漂移），每移 3–4 个编译一次。

### 不做

- **不动** `DetailTimelineChart` 内部的 `setTimeout` / observable（A-4 的活，且 A-4 要先补测试再改）
- **不动** `page/components/NewsTape.kt` 的节拍逻辑（属 Market 弹幕，A-5 处理）
- 不改任何布局、参数、动画、手势
- 不搬 `page/components/CardSheet.kt`（Chat 与 Detail 共用，留原位）

### 自动验收

```bash
./gradlew architectureCheck
./gradlew :shared:testDebugUnitTest
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

**验收指标**：`detail/**` 对 `page.*` 的 import 应从 **53 处降到 ≤ 35 处**（剩下的在 `detail/page/component/*`，由 A-4 清）。

```bash
grep -rc --include='*.kt' '^import com\.kuikly\.stockchat\.page\.' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/detail | \
  grep -v ':0' | wc -l
```

### 完成定义

- [ ] `page/detail/` 目录已删除，6 个文件的内容已在 `detail/domain/`
- [ ] 14 个具名区块已全部离开 `StockDetailPage.kt`
- [ ] `StockDetailPage.kt` 行数明显下降（本包预期降到 ~2,600 行；A-4 继续压到 < 900）
- [ ] 三端编译通过、298 单测全过、截图零差异

---

## 7. A-4 · P1-Detail 收口（反向依赖与残余状态）

**前置**：A-3　**预算**：5.0 人日 / 650k–1.20M

### 目标

1. **清空 `detail/**` 对 `page.*` 的全部 import**（这是本项目最密集的规则违反点）；
2. 把 `StockDetailPage.kt` 残余的 22 个页面 observable 按域收归 Coordinator；
3. 让 `DetailTimelineChart` 不再自持 Timer 与业务 observable。

### 范围

**（1）清除反向依赖 —— 确切清单（实测）**

5 个 state 文件当前 import 的 `page.*` 符号：

| 文件 | 依赖的 page 符号 |
|---|---|
| `detail/ai/state/AiInsightPrompts.kt` | `page.detail.AnchorIndex`、`page.components.detailTimelineSeries` |
| `detail/chart/state/DetailChartUiState.kt` | `page.components.ChartFlag`、`page.detail.AnomalyPoint` |
| `detail/chart/state/DetailChartInteractionCoordinator.kt` | `page.components.ChartFlag`、`page.detail.AnchorIndex`、`page.detail.AnomalyPoint` |
| `detail/overlay/state/DetailOverlayCoordinator.kt` | `page.detail.DetailOverlay` |
| `detail/overlay/state/DetailOverlayUiState.kt` | `page.detail.DetailOverlay` |

**注意 `ChartFlag` 与 `detailTimelineSeries` 定义在 `page/components/DetailTimelineChart.kt`（L37 / L53）**——A-3 已把这个文件搬到 `detail/page/component/`，所以这两个符号自然换包。**若 A-3 漏了这一步，A-4 的第一件事就是补上。**

`detail/page/component/*` 剩余的反向 import 涉及这些符号，全部需要随 A-3 的搬迁消失：
`page.AiInsightBlock`、`page.RevealBlock`、`page.SectionLabel`、`page.TickerText`、`page.AttributionForecastWorkbench`、`page.ChartLegend`、`page.ChartSegment`、`page.ChartViewportControls`、`page.ChartLoadingSkeleton`、`page.BusinessInsightGrid`、`page.BusinessInsightItem`、`page.CompanyIndustryPanel`、`page.DisclosureMaterialityBlock`、`page.balanceSegmentsFor`、`page.detail.*`、`page.components.{ChartFlag, DetailTimelineChart, BalanceSpectrumBlock, NewsMarquee, NewsSummaryBar, LineIconCopy, LineIconPin, QuickReasonChips, formatTapeTime, truncateByWidth, FeatureTile, LineIconBarChart}`

（`page.components.FeatureTile`、`NewsMarquee`、`NewsSummaryBar` 留在原位，只需改 import 到新包。）

**（2）新增 3 个 Coordinator**

| 新增文件 | 接管什么 |
|---|---|
| `detail/state/DetailChromeCoordinator.kt` | 顶部折叠、行情 ticker、收藏反馈、页面/灵动岛 handoff |
| `detail/state/DetailMotionCoordinator.kt`（或并入 Chart Coordinator）| `sonarDrift`、`drawProgress`、tape 时钟 —— **保证「一个时序一个 owner」** |
| `detail/state/DetailContentCoordinator.kt` | company tab / presented、expanded attribution、selected sentence。**若只是局部同步状态，用 Reducer 即可，不必强造网络 Coordinator** |

**（3）`DetailTimelineChart` 去 Timer 化**

现状：组件内部直接 `setTimeout`，并自持多项 observable 交互状态。

**强制顺序**：**先写交互状态测试，再移除组件内 Timer。** 反过来做的话，一旦行为回归你将没有任何判别依据。

### 怎么改（5 步）

1. `grep` 出 `StockDetailPage.kt` 全部字段与函数的 owner 表，**一个字段只能属于一个域**（写在 commit message 里）。
2. 先搬 Timer / revision / Provider 调用到 Coordinator，**再**搬 DSL。
3. Page 只保留 Effect adapter：Route、Haptic、Media、Voice、Clipboard、原生 Selection。
4. **Props 不允许传整个 `StockDetailPage` 或 ViewModel**，只传只读 accessor 与 Actions。
5. 严守 `AGENTS.md` R1–R7，尤其：render key、mounted→presented 两拍入场、动画**上一周期注册**（R5）。

### 不做

- 不改分时/K 线的绘制与坐标系（`MarketTimelineSpec.lotSize` 相关逻辑一行不动）
- 不改圈选 AI 的 prompt 与坐标映射（端侧算槽位、模型只许引用 `HH:MM` 的既有约定）
- 不改 overlay 仲裁优先级
- 不动 `base/BasePager.kt`（B 的文件域）

### 自动验收

```bash
./gradlew architectureCheck            # 期望：detail 相关 allowlist 条目清零
./gradlew :shared:testDebugUnitTest    # 既有 4 套 Detail Coordinator 测试 + 新增全过
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

**硬指标**：

```bash
# detail/** 对 page.* 的 import 必须为 0
grep -rn --include='*.kt' '^import com\.kuikly\.stockchat\.page\.' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/detail | wc -l    # 期望 0
```

### 人工验收（逐项手测）

- [ ] 分时图 / K 线切换
- [ ] 图表 scrub（拖拽游标）
- [ ] 圈选 AI（横拖 >14dp 且 |dx| > |dy|×1.5 触发）
- [ ] overlay 仲裁（多个浮层同时出现时的优先级）
- [ ] 公司区 tab 切换
- [ ] 收藏与灵动岛 handoff
- [ ] **快速重复操作**：连续快速切 tab / 连续 scrub
- [ ] **离页回调**：操作中途返回，确认旧回调 no-op
- [ ] **退后台再回来**
- [ ] **reduce motion** 开启后动效降级
- [ ] Android + H5

### 完成定义

- [ ] `detail/**/{component,state}` 对 `page.*` 的 import = **0**
- [ ] `StockDetailPage.kt` < **900 行**
- [ ] `DetailTimelineChart` 不直接 `setTimeout`、不自持业务 observable
- [ ] 每个新 Coordinator 有 fake scheduler 测试
- [ ] 上述 11 项手测全过

---

## 8. A-5 · P1-Market 建立四层纵切

**前置**：**轨 B 的 B-1（装配根已落地）**。按排期 B-1 在 day 2.5 完成，A-5 在 day 9 开工，不构成阻塞；若 A 因故提前，**必须先确认 B-1 已合并**。
**预算**：4.5 人日 / 500–950k

### 目标

`MarketPage.kt` 目前 1,660 行、**32 个 observable、34 处 `setTimeout`**，把数据、回放、两套 AI、新闻节拍、刷新和 UI 全放在 Page 里。本包按四层拆开。

### 范围

**现状实测（`MarketPage.kt`）**

| 状态组 | 字段（行号）|
|---|---|
| Overview | `overview`(107)、`refreshing`(114)、`tickPhase`(116)、`tickDirection`(117)、`hotspots`(119)、`expandedBoardLevel`(120)、`dataModeLabel` |
| 入场动效 | `heroEntered`(123)、`ladderEntered`(124)、`stickyIndexVisible`(125)、`stripEntered`(130)、`breadthEntered`(132)、`volumeEntered`(134)、`marketEntrancePhase`(136) + 6 拍 `setTimeout(1/75/150/225/300/375)` |
| 下拉刷新 | `pullDistance`(126)、`pullReady`(127)、`refreshResultVisible`(128) |
| 回放 | `snapshotFrames`(142)、`scrubMinute`(144)、`replayChipVisible`(145)、`axisMetric`(147)、`activeSlice`(149) + `scrubTweenGeneration` |
| 主 AI | `aiState`(156)、`aiText`(157)、`aiPulse`(159) |
| 新闻弹幕 | `tapeOffset`(173)、`tapePaused`(174)、`tapeTimerStarted`(175，非 observable)、`peek`/`peekVisible`(121/122)、`newsPeek`(177)、`newsPeekVisible`(178) + `startTapeTimer()` 33ms 步进链 |
| 新闻 AI | `newsAiState`(182)、`newsAiText`(183) |
| 定时刷新 | `liveRefreshGeneration` + `setTimeout(15_000)`(334) |

**新建（8 个文件）**

```text
market/data/MarketRepository.kt
market/state/MarketOverviewCoordinator.kt       # overview / hotspots / refresh / tick / entrance
market/replay/state/MarketReplayCoordinator.kt  # snapshotFrames / scrubMinute / axisMetric / activeSlice / tween + live generation
market/ai/state/MarketAiCoordinator.kt          # 主 AI 与新闻 AI 合并
market/news/state/MarketNewsCoordinator.kt      # tape offset / pause / peek / presented
market/component/MarketHero.kt
market/component/MarketBreadth.kt
market/component/MarketReplay.kt
market/component/MarketNews.kt
```

### 怎么改（5 步）

1. **Overview Coordinator** 接管 overview / hotspots / refresh / tick / entrance。注意入场是 **6 拍 `setTimeout` 阶梯**（1/75/150/225/300/375ms），迁移时保持节奏逐值不变。
2. **Replay Coordinator** 接管 `snapshotFrames` / `scrubMinute` / `axisMetric` / `activeSlice` 和 tween + live generation。现状有两处 generation 守卫（`scrubTweenGeneration` at L292/303、`liveRefreshGeneration` at L332/334）——**统一收归这个 Coordinator，保证一个时序一个 owner**。
3. **MarketAiCoordinator**：用**参数化 AiSession** 合并主 AI 与新闻 AI 的重复流式逻辑。
   - 可复用 Detail 的纯会话范式，**但不得让 `market` import `detail` Feature**（`architectureCheck` 会拦）。
   - 稳定后把通用会话下沉 `shared/`。
4. **News Coordinator** 接管 tape offset / pause / peek / presented。现有 `startTapeTimer()` 是 **33ms 步进（≈30dp/s）**，`reduceMotion` 下不流动——逐值保留。
5. Page 只把 **Repository 结果作为 Intent** 送给 Coordinator，**不直接决定缓存/离线降级**。

### 不做

- 不改行情字段解析（`fqkline` 快照 `qt` 的下标语义、涨跌配色、`lotSize` 规则）
- 不改 `slotCount`（A股 241 / 港股 331 / 美股 391）
- 不改新闻弹幕速度与 pause 行为
- 不改两套 AI 的 prompt 与降级策略
- 不动 `QuoteRepository`（B 的文件域，只通过其接口消费）

### 自动验收

```bash
./gradlew architectureCheck
./gradlew :shared:testDebugUnitTest          # 4 个新 Coordinator 各要有状态与生命周期测试
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

**硬指标**：`MarketPage.kt` 中

```bash
grep -cE 'setTimeout|Provider\(|by observable' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/page/MarketPage.kt
```

- 业务 `setTimeout` → **0**
- `Provider(` 实例化 → **0**
- 业务 `by observable` → 仅剩页面纯布局快照
- 文件 < **700 行**

### 人工验收

- [ ] 下拉刷新的三段状态（下拉中 / 到位 / 结果提示）
- [ ] 行情回放（拖拽 scrub、tween 落点、live 刷新与 scrub 互斥）
- [ ] 新闻 tape 流动 / 暂停 / peek 弹出
- [ ] 两类 AI（主 AI、新闻 AI）流式输出与超时降级
- [ ] 页面入场 6 拍阶梯动画
- [ ] reduce motion 全项降级
- [ ] 快速重复：连续下拉刷新、连续 scrub
- [ ] 离页回调、退后台再回来
- [ ] Android + H5

### 完成定义

- [ ] 4 个新 Coordinator 各有测试
- [ ] `MarketPage.kt` < 700 行，无 Provider 实例 / Typewriter / generation / 业务 Timer
- [ ] `market/**` 不 import `detail/**`
- [ ] 上述 9 项手测全过

---

## 9. A 轨的里程碑

| 里程碑 | 时点 | 判据 |
|---|---|---|
| **MA0** | day 1 | `architectureCheck` 上线且基线通过 |
| **MA1** | day 4 | 共享组件已外迁；`page/detail/` 删除；14 个区块离开 `StockDetailPage.kt` |
| **MA2** | day 9 | `StockDetailPage.kt` < 900 行；`detail/**` 对 `page.*` import = 0 |
| **MA3** | day 13.5 | `MarketPage.kt` < 700 行；4 个 Market Coordinator 各有测试；allowlist 中 A 轨条目清零 |

---

## 10. 验收命令与提交模板

### 每个工作包收工必跑

```bash
./gradlew architectureCheck                            # A-1 落地后生效
./gradlew :shared:testDebugUnitTest                    # 基线 298，只增不减
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

### 提交要求

- **一个工作包一个 commit**，独立可回滚；
- 提交前打印 `git diff --cached --stat`；
- **不得夹带轨 B 的 WIP**（`git status` 里出现 B 的文件域改动，停下来先确认）；
- `StockDetailPage.kt` 与 `MarketPage.kt` 任一时刻**只有你一个写入 owner**；
- commit message 必须包含四段：

```text
refactor(detail): 收口反向依赖与残余状态

变更文件：<列表>
职责变化：<哪些字段/函数从 Page 移到哪个 Coordinator>
测试证据：architectureCheck 通过；testDebugUnitTest 312 tests / 0 failures
未覆盖风险：<例如 iOS 未手测；reduce motion 仅做了代码走查>
```

---

## 11. 项目铁律速查（必读，违反会出事故）

完整版见仓库根目录 `AGENTS.md`。以下是做本轨工作**最会踩的**部分。

### 11.1 Kuikly 响应式与动画（AGENTS R1–R7）

- **R1 只有 `attr {}` / `event {}` / `vif {}` / `vfor {}` / `vbind {}` 里的读取才是响应式的。** 普通 `body()` 或子 builder 闭包（**包括 `Scroller` 的 child block**）只读到初始快照。搬 DSL 时最容易在这里出错。
- **R2 `animate(...)` 必须是 `attr {}` 块里最后一个 observable 依赖操作**——它用的是「最近一次读到的 observable key」而不是它的值参数。**主题类 observable 要在 `attr` 外缓存**，否则会顶掉预期的动画 key。
- **R3** 同一 `attr {}` 里一个驱动 observable 只能注册一次 `animate()`。
- **R4** `vif` / `vbind` 新挂载的视图**第一帧不会播动画**，入场要用 `mounted → presented` 两拍（`setTimeout(0)` 更新状态）。
- **R5 注册滞后一周期**：本周期注册的动画，是**下一个**驱动变化才播放的。推论：
  - 每周期（含挂载前周期）都要注册「下一次变化想播的」动画；
  - **绝不能在 pre-state 周期注册 0 时长的「重置」动画**（如 `Animation.linear(0f)`），presentation 周期会消费它，入场退化成瞬跳；
  - 入场序列必须有**带版本守卫的兜底定时器**，防止 `ref → setTimeout` 断链后视图卡在 `opacity 0`；
  - **绝不在同一次 layout/数据变更的批次里重置动画驱动 observable**（Views 会消费 N→0 的 reset，把 transform 清除过程重播一遍，表现为「布局瞬跳 + 第二次位移」）。
- **R6** 深层 builder 闭包里用显式 `page.theme`（隐式 receiver 可能解析失败）；数据类相等 ≠ 语义相同，比较**判别字段**而非整个 data class。
- **R7 `vif` 的 creator 只在「条件 false→true」时构建一次**，条件保持 true 期间**不会重跑**。面板/列表要刷新，用「单元素 `ObservableList<Int>` render key + `vfor`」整体重建。

### 11.2 平台隔离（用户硬要求）

**为某一端（通常是 iOS）做的修复，不得改变其他端的行为。**

唯一开关是 `common/PlatformPolicy.kt`（`expect object`，4 个源集各有 actual）：`marketFixes` / `coreThreadMarshalling` / `debugHooks`。**现状 iosMain 全 `true`，android / js / ohos 全 `false`。**

→ **本轨是纯归档工作，正常不需要动这里。** 如果发现非要动，说明你在改行为，停下来找我确认。

### 11.3 iOS 渲染层已知缺口

`iosApp/scripts/apply_kuikly_patches.rb` 有 **9 条幂等补丁**兜底上游缺口。其中：

- 缺 `css_batchDraw` → 所有 `batchDraw=true` 的 Canvas **全空白**（图标、语音波形；逐条下发的图表不受影响）；
- 缺 `css_clip` 差集语义 → 流光描边被填成实心块。

**如果你新搬的组件里有 `batchDraw` Canvas，先确认补丁已套用。** 本轨只搬位置，不应新增任何 `batchDraw` Canvas。

### 11.4 其他

- **同一文件禁止并行 Edit**（会互相覆盖）；Gradle `fileHashes.lock` 被占 → `./gradlew --stop` 后重试。
- **`page/components` 禁止新增文件**（门禁规则 6）。
- 详情页预取走 `QuotePrefetchStore`（全局单例，LRU 16 / 60s / `price <= 0` 不入），`created()` 命中直接赋 `quote + sonarPoints`，**不走 `applyQuote`**（防幽灵帧）。

---

## 12. 可选加项（本轮已砍出，日后可接）

以下两项**不计入 13.5 人日**，对四层分离得分贡献为 0（依据 `docs/44` §4.5「宿主层不计入四层本体」、§1「P2 不是四层分离前置条件」），但属于可维护性投资，A 收工后有余力可接：

| 项 | 人日 | Token | 说明 |
|---|---:|---:|---|
| P1-Platform 拆分四端 Bridge | 5.5 | 550k–1.00M | Android 1,061 + iOS 852 + Ohos 594 + H5 143 = 2,650 行。保留薄 Bridge 作协议入口，内部按 Navigation / Media / Voice / Share / Haptic 分 service；**method 字符串与 JSON 字段逐字不变**，先补契约测试再搬代码 |
| P2 Gradle 模块化 | 6.5 | 800k–1.60M | 只在 allowlist 基本清空后做。迁移顺序：纯 Kotlin domain → design token → cards → UI → chart → Feature |

---

## 13. 开工前自检清单

- [ ] 我读完了 `AGENTS.md` 的 R1–R8
- [ ] 我已确认工作区干净（`git status`），没有别人的 WIP
- [ ] 我知道本轨独占哪些文件、以及 `AppChrome.kt` 归轨 B
- [ ] 我知道 **A-2 完成前，B 不能动 `page/components/`**
- [ ] 我知道 **A-2 会让我在 B 的文件里改 import 行**（只改 import，不改逻辑）
- [ ] 我知道 `MarketPage.kt` 要**等 B-1 的装配根落地**后才能开工
- [ ] 我在 `docs/45` §12 确认过我的工作包范围

---

## 附：实测基线数据（供随时对照）

```text
commonMain                185 文件   48,368 行
  page/                    40 文件   27,234 行（含 page/components 20 文件 9,322 行）
  chat/                    48 文件    6,926 行
  data/                    31 文件    5,623 行
  detail/                  25 文件    2,484 行
  cards/                   13 文件    2,457 行
测试                       90 文件    5,664 行
单测执行                   298 tests / 0 failures / 0 errors
四端 Bridge              2,650 行（Android 1061 / iOS 852 / Ohos 594 / H5 143）

非 page 包 import page.*：66 处（detail/ 占 53）
state 文件 import page：5 个
page/components 文件数：20，其中 AppChrome.kt 1,756 行、GeneratedSvgCanvasIcons.kt 1,789 行
```
