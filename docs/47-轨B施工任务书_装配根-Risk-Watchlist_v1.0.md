# 轨 B 施工任务书 — 装配根 / Chat & Risk 组件 / Risk / Watchlist

> 版本：v1.0　日期：2026-09-12
> 基线：`master` HEAD `fac42b1`　**实测基线：`./gradlew :shared:testDebugUnitTest` = 298 tests / 0 failures / 0 errors**
> 上游：`docs/44`（四层架构审计与整改纲领）、`docs/45`（双人拆分与预算，§12 为精简版）
> **本文件自包含**：拿到本文件即可开工，不必读其它文档。但第 11 节的 `AGENTS.md` 铁律必须先读。

---

## 0. 怎么用这份文档

1. 你是**轨 B 的唯一写入 owner**，负责第 2 节列出的 4 个工作包。
2. 按 **B-1 → B-2 → B-3 → B-4** 顺序做，**每个工作包一个独立 commit**，可独立回滚。
3. 每个工作包开工前，先跑第 10 节的「自动验收」确认基线是绿的；收工时再跑一遍。
4. 遇到与轨 A 的文件冲突，按第 3 节的契约处理，**不要临场协商改契约**——契约一改，两条轨都会返工。

---

## 1. 背景（读一遍就够了）

StockChat 是 Kuikly/KMP 多端项目（Android / iOS / 鸿蒙 / H5）。目标架构是 **Page / Component / Data / State 四层分离**。

当前状态（`docs/44` 的审计结论，已逐条复核）：

- 综合得分 **64/100**，属于「局部达到」；
- **Data 层**：`MarketDependencies` 位于 `data` 包，实际却是 App 的 composition root；`PagerKeyValueStorage` 位于 `data/storage` 却依赖 `PagerScope` / `SharedPreferencesModule`。数据能力强于目录表达；
- **Component 层**：`page/components/AppChrome.kt` **1,756 行**，同时装通用件（AppTopBar / DataModeBadge）和 Chat 私有件（ChatTopNav / StockIsland / ChatDrawer）；
- **State 层**：Chat 已抽 8 个 Coordinator、Detail 已抽 4 个，**但 Risk / Watchlist 基本未纵切**。

验收线是 **80/100**。轨 A 与轨 B 合计 27 人日完成 P0 与部分 P1 后，可达标。

### 硬边界（本轨所有工作包共同禁止）

**不改**产品功能、视觉、文案、动画参数、手势阈值、路由、AI Prompt、降级策略、平台协议。
**不与**包改名、依赖升级、新功能混在同一提交。

**特别提醒**：B-1 只改**装配方式**，不改 Provider 的解析逻辑与降级策略。

---

## 2. 轨道 B 总览

| 序 | 工作包 | 人日 | Token 预算 | 前置 |
|---:|---|---:|---:|---|
| B-1 | P0-B 装配根与平台适配器 | 2.5 | 350–600k | 无 |
| B-2 | P0-C-chat/risk 组件归档 | 2.0 | 290–480k | **轨 A 的 A-2 完成**（见 §3.3）|
| B-3 | P1-Risk 拆计算 / 星图 / AI / 预警 | 5.5 | 600k–1.10M | B-1 |
| B-4 | P1-Watchlist 拆拖拽 / Undo / 列表 / 简报 | 3.5 | 450–850k | B-1 |
| | **合计** | **13.5** | **2.36M** | |

**关键路径 13.5 人日**（与轨 A 同步开工，两轨同一天收工）。

### 完成后的目标状态

| 指标 | 现在 | 完成后 |
|---|---:|---:|
| `data/` 对 `PagerScope` / `SharedPreferencesModule` 的 import | 有（`data/storage/KeyValueStorage.kt`）| **0** |
| composition root 位置 | `data/MarketDependencies.kt` | `app/assembly/MarketFeatureGraph.kt` |
| `AppChrome.kt` | 1,756 行 | **删除** |
| `page/components/` 文件数 | 20 | ≤16 |
| `RiskMapPage.kt` | 2,692 行 | < 800 行 |
| `WatchlistPage.kt` | 1,690 行 | < 600 行 |
| Page 直接构造 `TencentQuoteProvider` | 有（Watchlist L188）| **0** |
| 单测 | 298 | ≥ 298（只增不减）|

---

## 3. 协作契约（与轨 A，最重要的一节）

### 3.1 文件域归属

| 文件域 | Owner | 说明 |
|---|---|---|
| `page/components/AppChrome.kt` | **B 独占** | 由你在 B-2 一次性**整体拆开并删除**。`AppTopBar` / `DataModeBadge` 也由你顺带落到 `foundation/ui`（轨 A 不碰此文件）|
| `page/ChatPage.kt` | **B 独占** | |
| `page/RiskMapPage.kt`、`page/WatchlistPage.kt`、`page/GlossaryPage.kt`、`page/AlertCenterPage.kt` | **B 独占** | |
| `chat/**`、`risk/**`（新建）| **B 独占** | |
| `data/**` | **B 独占**（B-1 期间）| 轨 A 只新建 `market/data/`，不改既有文件 |
| `app/assembly/**`、`app/platform/**`（新建）| B 独占 | 轨 A 只读 |
| `page/components/` 除 AppChrome.kt 外的文件 | **轨 A** | 见 §3.2 |
| `page/StockDetailPage.kt`、`page/MarketPage.kt` | **轨 A 独占** | |
| `page/detail/**`、`detail/**`、`market/**` | **轨 A 独占** | |
| `glass/**`、`cards/**`、`base/**` | **轨 A**（B-1 会改 import 行，见下）| |
| 根 `build.gradle.kts`、`settings*.gradle.kts` | **轨 A 独占** | A-1 注册 `architectureCheck`；B 只读 |
| `scripts/**`、`docs/architecture/**` | 轨 A 独占 | |
| `androidApp/`、`iosApp/`、`ohosApp/`、`h5App/` | 本轨不动 | P1-Platform 已砍出本轮（见 §12）|

### 3.2 B-1 的跨轨写权限（唯一例外）

B-1 要改 `data/Appearance.kt` 的归属，而它的 **`fontSizeScaled` / `lineHeightScaled` 扩展被 43 个文件引用**（实测），其中包含轨 A 的文件。**契约**：

1. B-1 有权修改**任何文件**的 `import` 行，**但只准改 import**，不得改逻辑、布局、参数、动画。
2. B-1 完成后必须在 commit message 里列出「本次跨轨改动的 A 侧文件清单」。

实测受影响的 A 侧文件（部分）：`page/StockDetailPage.kt`、`page/MarketPage.kt`、`page/GlossaryPage.kt`*、`page/components/AppChrome.kt`（A 侧除 AppChrome）、`page/components/{CardSheet,FeatureTile,DetailBoardBlocks,UndoBar,VoiceBar,ChatScaffolding,NewsMarquee,DetailTimelineChart,NewsTape,ChatMessageComponents,InsightComponents}.kt`、`detail/page/component/*`、`cards/components/*`、`cards/stock/*`。

（*注：`GlossaryPage.kt` 与 `ChatPage.kt` 等 B 自己的文件自然也在这个 43 里，一并改。）

### 3.3 串行点

| 编号 | 时点 | 内容 |
|---|---|---|
| **S0** | day ≈ 1（轨 A 完成 A-1） | 门禁就绪，此后每个提交都跑 `architectureCheck`。你从 day 0 起并行做 B-1，不受影响 |
| **S1** | day ≈ 2.5（轨 A 完成 A-2） | **你的 B-2 才能开工**。原因：A-2 把 `LineIcons` / `CardShell` / `GlassRenderer` / `SourceStampLine` 迁到新位置，而 `AppChrome.kt` 与 Chat 组件正是这些符号的消费方——A-2 未完成时拆 `AppChrome.kt`，你会基于旧位置写代码，随后被 A 的 import 改动打断。**你的 B-1 恰好占满 day 0–2.5，天然对齐，无空转** |
| **S2** | day ≈ 4.5（B-2 完成） | B-3 / B-4 才能开工（它们消费 B-2 归位的 Chat/Risk 组件与 B-1 的装配根）|

---

## 4. B-1 · 装配根与平台适配器（P0-B）

**前置**：无　**预算**：2.5 人日 / 350–600k

### 目标

把「App 装配」从业务数据包里物理分离出去，让 `data/` 真正只负责数据。**本包只改装配，不改 Provider 的解析与降级逻辑。**

### 范围

**（1）`data/MarketDependencies.kt`（60 行）→ `app/assembly/MarketFeatureGraph.kt`**

现状实测：`class MarketDependencies` 持有 **9 个服务**，`companion object.forPager(pagerId, storage = PagerKeyValueStorage(pagerId))` 在业务包里直接 new 出全部实现：

```kotlin
val eastMoney = EastMoneyInsightProvider(pagerId)
MarketDependencies(
    watchlistStore = WatchlistStore(storage),
    alertStore = AlertStore(storage),
    alertInboxStore = AlertInboxStore(storage),
    glossaryStore = GlossaryStore(storage),
    riskSnapshotStore = RiskSnapshotStore(storage),
    quoteRepository = QuoteRepository(
        online = TencentQuoteProvider(pagerId),
        selectedSource = { MarketDataPrefs.source(storage) },
        cacheStore = SharedPreferencesQuoteCacheStore(storage),
    ),
    insightRepository = MarketInsightRepository(
        onlineFundFlow = eastMoney, onlineFundamentals = eastMoney,
        onlineDisclosures = eastMoney,
        onlineMarket = FallbackMarketOverviewProvider(
            primary = eastMoney,
            fallback = TencentIndexOverviewProvider(pagerId),
        ),
        onlineIndustry = eastMoney, onlineRatingSpectrum = eastMoney,
    ),
    securitySearchProvider = eastMoney,
    stockNewsProvider = eastMoney,
)
```

**（2）`chat/ChatDependencies.kt` 的 `forPager` 工厂 → `app/assembly/ChatFeatureGraph.kt`**

**（3）`data/storage/KeyValueStorage.kt` 里的 `PagerKeyValueStorage` → `app/platform/KuiklyKeyValueStorage.kt`**

⚠️ **关键**：这个文件里有**三个**声明，**只有中间那个要搬**：

| 行号 | 声明 | 去向 |
|---:|---|---|
| 7–10 | `interface KeyValueStorage` | **留在 `data/storage`**（这是 Port）|
| 13–22 | `class PagerKeyValueStorage : KeyValueStorage, PagerScope` | **搬到 `app/platform`**（这是 Adapter）|
| 25–37 | `class InMemoryKeyValueStorage` | **留在 `data/storage`**（测试用）|

搬完之后，`data/storage/KeyValueStorage.kt` 应当**不再 import** `PagerScope` 与 `SharedPreferencesModule`——这正是 B-1 的核心验收指标。

**（4）`data/Appearance.kt`（129 行）拆成两个文件**

现状实测内容与去向：

| 现在（行号）| 符号 | 去向 |
|---:|---|---|
| L20 | `enum class ThemeMode` | `foundation/design/Appearance.kt` |
| L32 | `enum class FontScale` | `foundation/design/Appearance.kt` |
| L44 | `object AppearancePrefs`（两个持久化 key）| `foundation/design/Appearance.kt` |
| L50 | `fun TypeTokens.scaled(factor)` | `foundation/design/Appearance.kt` |
| L118 | `fun resolveStockChatTheme(...)` | `foundation/design/Appearance.kt` |
| L67 | `object FontScaleRuntime` | **`foundation/ui/FontScaleAttr.kt`** |
| L88–96 | `TextAttr.fontSizeScaled` / `lineHeightScaled` | **`foundation/ui/FontScaleAttr.kt`** |
| L98 | `InputAttr.fontSizeScaled` | **`foundation/ui/FontScaleAttr.kt`** |
| L103–111 | `TextAreaAttr.fontSizeScaled` / `lineHeightScaled` | **`foundation/ui/FontScaleAttr.kt`** |

**为什么这样切**：文件顶部注释说得清楚——`Appearance.kt` 依赖 Kuikly 的 `TextAttr` / `InputAttr` / `TextAreaAttr`，它是 **Design/UI 基础设施，不是 Data**。`FontScaleRuntime` + 那几个 Attr 扩展是纯 UI 侧，`ThemeMode` / `FontScale` / `resolveStockChatTheme` 是纯设计模型。

**（5）新建更小的 Feature 依赖接口/数据类**

`ChatFeatureDependencies`、`DetailFeatureDependencies`、`MarketFeatureDependencies`。**Page 从 App graph 取本 Feature 的最小依赖集，不能拿到含 9 个服务的万能 `MarketDependencies`。**

### 怎么改（5 步）

1. **先 `git mv` 四个文件**，改 package 声明，编译一次拿到完整报错清单。
2. **Store / Repository 构造器只接收 Port**；逐步弃用并最终删除 `constructor(pagerId)`。
3. **`forPager` 只能出现在 App Assembly**；Feature / Data 内不能自行获取 Pager。
4. **Provider 改造分批做**：`TencentQuoteProvider`（348 行）、`EastMoneyInsightProvider`（643 行）、OpenAI 兼容 Provider（`AiProvider.kt` 366 行）改为**注入 `PlatformHttpClient` / Clock / Storage**，不再直接 `acquireModule`。**一次改一个 Provider，改完就跑 parser 既有测试。**
5. **最后改 `Appearance.kt` 的 43 个消费方 import**（§3.2 契约）。

### 不做

- **不改**任何 Provider 的 URL、参数、字段解析、缓存策略、离线降级分支
- **不改** `USE_REAL_MARKET_DATA` 总开关的语义（唯一总开关在 `data/config/DataSourceConfig.kt`，禁止引入第二开关）
- 不动 `page/` 下任何文件（除 import 行）
- 不动四端宿主层

### 自动验收

```bash
./gradlew architectureCheck
./gradlew :shared:testDebugUnitTest          # 期望 ≥298；Store 单测继续用 InMemoryKeyValueStorage
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

**硬指标**：

```bash
# data/ 不得再出现 PagerScope / SharedPreferencesModule
grep -rnE --include='*.kt' 'PagerScope|SharedPreferencesModule' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/data | wc -l    # 期望 0

# forPager 只能出现在 app/assembly
grep -rn --include='*.kt' 'fun forPager' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat | \
  grep -v 'app/assembly' | wc -l                                    # 期望 0
```

### 人工验收

- [ ] 行情链路：A 股 / 港股 / 美股各拉一只（`sh600519` / `hk00700` / `usAAPL`），价格、涨跌幅、成交额正常
- [ ] Watchlist 增删改查、持久化后重启仍在
- [ ] 设置页切主题（跟随系统 / 浅色 / 深色）+ 字号 4 档，全局生效
- [ ] 退后台再回来，设置仍保留
- [ ] Android + H5

### 完成定义

- [ ] `data/` 不再 import `PagerScope` / `SharedPreferencesModule`
- [ ] composition root 已在 `app/assembly`；`forPager` 只出现在那里
- [ ] `Appearance.kt` 已拆为 `foundation/design` + `foundation/ui`
- [ ] 三端编译通过、298 单测全过
- [ ] commit message 列出「跨轨改动的 A 侧文件清单」

---

## 5. B-2 · Chat 与 Risk 组件归档（P0-C-chat/risk）

**前置**：**轨 A 的 A-2 完成**（day 2.5，见 §3.3）　**预算**：2.0 人日 / 290–480k

### 目标

删除 `AppChrome.kt`，把 Chat 私有组件归位到 `chat/**/component`，Risk 星图归位到 `risk/sky/component`。**纯物理归档：不改布局、参数、动画、状态逻辑。**

### 范围（1）`AppChrome.kt` 拆解 —— 实测的确切构成

这个文件 **1,756 行**，其中 **Chat 私有件约 1,475 行（84%），通用件只有约 238 行（16%）**：

| 行范围 | 声明 | 去向 |
|---:|---|---|
| L44–70 | `data class AppTopBarMetric`、`data class AppTopBarAction` | `foundation/ui/chrome/AppTopBar.kt` |
| L71–325 | `fun ViewContainer<*, *>.ChatTopNav`（255 行）| `chat/island/component/QuoteIsland.kt` |
| L326–1101 | `private fun ViewContainer<*, *>.StockIsland`（**776 行**）| `chat/island/component/QuoteIsland.kt` |
| L1102–1118 | `private fun islandSketchPoints(q: Quote): List<QuotePoint>` | 跟随唯一调用方（StockIsland）|
| L1119–1170 | `private fun ViewContainer<*, *>.IslandGestureHandle` | `chat/island/component/QuoteIsland.kt` |
| L1171–1230 | `private fun ViewContainer<*, *>.CompareIslandSlot` | `chat/island/component/QuoteIsland.kt` |
| L1231–1484 | `fun ViewContainer<*, *>.ChatDrawer`（254 行）| `chat/drawer/component/ChatDrawer.kt` |
| L1485–1492 | `private fun ViewContainer<*, *>.DrawerGroupTitle` | `chat/drawer/component/ChatDrawer.kt` |
| L1493–1516 | `private fun ViewContainer<*, *>.DrawerSessionItem` | 同上 |
| L1517–1536 | `private fun ViewContainer<*, *>.DrawerEmptyHistory` | 同上 |
| L1537–1545 | `private fun ViewContainer<*, *>.DrawerTile` | 同上 |
| L1546–1737 | `fun ViewContainer<*, *>.AppTopBar`（192 行）| `foundation/ui/chrome/AppTopBar.kt` |
| L1738–1756 | `fun ViewContainer<*, *>.DataModeBadge` | `foundation/ui/feedback/DataModeBadge.kt` |

**拆完后 `AppChrome.kt` 删除。**

⚠️ **`AppTopBar` 被十余个页面使用**，是全项目最广的一处 import 改动。建议：**先落定义，再一次性批量修 import，编译通过再提交**——不要边移边编译。

**注意 `DataModeBadge`**：按文档 44 的要求，它**不依赖 Page 或数据源，只接收展示文案/色调**。如果你发现当前实现里它自己读了数据源，**不要改逻辑**——只搬位置，把「该不该拆数据依赖」作为 TODO 记进 commit message，由后续工作包处理。

### 范围（2）其余三个 Chat 组件 + 一个 Risk 组件

| 现在 | 目标 | 行数 |
|---|---|---:|
| `page/components/ChatMessageComponents.kt` | `chat/message/component/` | 661 |
| `page/components/ChatScaffolding.kt` | `chat/.../component/` | 373 |
| `page/components/VoiceBar.kt` | `chat/voice/component/` | 120 |
| `page/components/RiskSkyChart.kt` | `risk/sky/component/` | 404 |

### 范围（3）不能整体迁移的部分（文档 44 §6.3 明确）

- `AppChrome.kt` **整体**——其中 QuoteIsland、ChatDrawer 是 Chat Feature，必须拆开而不是整包搬；
- `DetailTimelineChart`、`DetailBoardBlocks`——属 Detail Feature，归轨 A；
- `ChatMessageComponents`、`VoiceBar`、`ComparePanel`——属 Chat Feature，**只能进 `chat/**`，不能进 `foundation`**。

### 怎么改（4 步）

1. **先拆 `AppChrome.kt` 的 Chat 部分**（1,475 行），落进 `chat/island/component` 与 `chat/drawer/component`。
2. **再拆通用部分**（238 行），`AppTopBar` 落 `foundation/ui/chrome`、`DataModeBadge` 落 `foundation/ui/feedback`。
3. **批量修 import**：`AppTopBar` 的十余个消费页面 + `DataModeBadge` 的消费方。**只改 import 行。**
4. **删 `AppChrome.kt`**，编译，截图对比。

**私有 helper 跟随唯一调用方**；被两个 Feature 使用的 helper，先定义小的无业务 API 再上移。

### 不做

- 不改 `StockIsland` 的任何手势阈值与动画节奏（776 行里全是交互细节，一行都不要动）
- 不改 `ChatDrawer` 的分组/会话列表逻辑
- **不做光晕效果**——语音波形的光晕是用户明确否决过的
- 不改 `VoiceBar` 的 `VOICE_AMP_BARS=56` 与右对齐
- 不动 `DetailTimelineChart`（轨 A）

### 自动验收

```bash
./gradlew architectureCheck
./gradlew :shared:testDebugUnitTest
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

**硬指标**：

```bash
ls shared/src/commonMain/kotlin/com/kuikly/stockchat/page/components/AppChrome.kt  # 期望：No such file
grep -rn --include='*.kt' '^import com\.kuikly\.stockchat\.page\.' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/chat | wc -l    # 期望 0
```

### 人工验收

- [ ] Chat 首页：顶部导航、商品岛、对比岛、抽屉展开/收起
- [ ] 抽屉：分组标题、会话项、空历史态、Tile
- [ ] 十余个页面的 `AppTopBar` 标题/动作/指标显示正确
- [ ] `DataModeBadge` 在数据源切换时的文案与色调
- [ ] Risk 星图渲染正常
- [ ] **截图对比零差异**（至少覆盖 Chat、Risk、Watchlist、Market、StockDetail）
- [ ] Android + H5

### 完成定义

- [ ] `AppChrome.kt` 已删除
- [ ] `chat/**` 对 `page.*` 的 import = 0
- [ ] `page/components/` 文件数从 20 降到 ≤16
- [ ] 三端编译通过、298 单测全过、截图零差异

---

## 6. B-3 · P1-Risk 拆计算 / 星图 / AI / 预警

**前置**：B-1　**预算**：5.5 人日 / 600k–1.10M

### 目标

`RiskMapPage.kt` 目前 **2,692 行**，纯算法、数据、星图手势、AI 流、预警生成和持久化混在一起。**Risk 是全项目评分最低的 Feature（42 分）**，提升空间最大。

### 范围

**现状实测（`RiskMapPage.kt` 的 25 个 observable）**

| 状态域 | 字段（行号）|
|---|---|
| 数据 | `rows`(113, ObservableList<RiskRow>)、`indexQuote`(114)、`industries`(115)、`events`(116)、`limitUps`(117)、`dataModeLabel`(118) |
| 星图 | `skyLayer`(127)、`skyViewMode`(130)、`skySelectedSymbol`(133)、`skySelectedCluster`(136)、`skyBeaconDrawer`(139)、`correlations`(142)、`volRatios`(145)、`beaconPhase`(148)、`skyDragOffsets`(151)、`skyContextSymbol`(153) |
| 星图 AI | `skyAiState`(162)、`skyAiText`(163)、`skyAiError`(164)、`skyAiModel`(165) + `skyAiGeneration`（守卫在 L843/854/860/871/874/877/889/890）|
| 预警 | `selectedPair`(176)、`convertedEventIds`(182)、`generatedExposureIds`(188)、`eventToInboxHint`(195) + `setTimeout(2500)`(L1904) |

**迁移与新建**

| 动作 | 目标 |
|---|---|
| `page/risk/StarLayout.kt`（294 行）| `risk/domain/StarLayout.kt` |
| 新建 | `risk/data/RiskRepository.kt` |
| 新建 | `risk/state/RiskDataCoordinator.kt` |
| 新建 | `risk/sky/state/RiskSkyCoordinator.kt` |
| 新建 | `risk/ai/state/RiskAiCoordinator.kt` |
| 新建 | `risk/alert/state/RiskAlertCoordinator.kt` |
| `page/components/RiskSkyChart.kt` | 已在 B-2 归位到 `risk/sky/component` |

### 怎么改（6 步）

1. **先把计算模型纯化**：纯计算输出**不可变 `RiskExposureSnapshot`**，**输入只包含 `watchlist` / `quotes` / `industry` / `events`**；**不得读取 Store / Pager**。这一步是整个 B-3 的地基——算不纯，后面四个 Coordinator 都会脏。
2. **Data Coordinator** 负责加载与组合，**Repository** 负责降级。
3. **Sky Coordinator** 接管 `layer` / `viewMode` / `selected` / `drag` / `pulse` / `context drawer` 和**持久化 Effect**。
4. **AI Coordinator** 接管 Provider / typewriter / generation / timeout。现状的超时是 **12 秒**（L860），generation 守卫在 L843 起——**逐值保留**。
5. **Alert Coordinator** 接管 `converted` / `generated` ids 和提示时序。现状提示是**置文案后 2.5 秒清除**（L1904），且用「先写 `""` 由 `vif` 消失」规避同值 early-return——**这个技巧必须原样保留**。
6. **`RiskSkyChart` 只渲染 geometry + state accessor**，所有 Timer 由 Sky Coordinator 管理。

### 不做

- 不改星图五层的视觉布局与配色
- 不改拖拽回弹的物理参数
- 不改 AI 的 prompt 与降级策略
- 不改预警生成的判定规则
- 不动 `data/**`（B-1 已定稿）

### 自动验收

```bash
./gradlew architectureCheck
./gradlew :shared:testDebugUnitTest
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

**硬指标**：

```bash
# 风险计算必须是纯 Kotlin，无 Kuikly import
grep -rnE --include='*.kt' 'com\.tencent\.kuikly' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/risk/domain | wc -l   # 期望 0

# RiskMapPage 不得再有 AI Provider / PagerStorage / pulseRunning / drag generation
grep -nE 'Provider\(|PagerStorage|pulseRunning|Generation' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/page/RiskMapPage.kt   # 期望为空
```

### 人工验收

- [ ] 星图五层（CLUSTER 等）切换
- [ ] 导图（context drawer）
- [ ] 拖拽回弹
- [ ] AI 降级（断网 / 超时 12s）
- [ ] 预警生成 → 收件箱
- [ ] 底部提示 2.5 秒自动清除
- [ ] reduce motion
- [ ] 快速重复：连续切层、连续拖拽
- [ ] 离页回调、退后台再回来
- [ ] Android + H5

### 完成定义

- [ ] 风险计算可用**纯单测**覆盖，无 Kuikly import
- [ ] Page 无 AI Provider / PagerStorage / pulseRunning / drag generation
- [ ] 5 个新 Coordinator 各有状态与生命周期测试
- [ ] `RiskMapPage.kt` < **800 行**
- [ ] 上述 10 项手测全过

---

## 7. B-4 · P1-Watchlist 拆拖拽 / Undo / 列表 / 简报

**前置**：B-1　**预算**：3.5 人日 / 450–850k

### 目标

`WatchlistPage.kt` 目前 **1,690 行**，同时持有列表数据、搜索、原因编辑、拖拽、Undo、简报和收件箱。Store 层已较成熟，缺的是状态收口。

### 范围

**现状实测（`WatchlistPage.kt` 的 25 个 observable / list）**

| 状态域 | 字段（行号）|
|---|---|
| 列表与筛选 | `rows`(99, ObservableList)、`displayList`(101)、`candidates`(102)、`hint`(103)、`dataModeLabel`(105)、`activeGroup`(107)、`statusFilter`(110)、`searchOpen`(113)、`menuSymbol`(116) |
| 原因编辑 | `reasonEditSymbol`(120)、`reasonChip`(122)、`reasonTyped`(124) |
| 拖拽 | `dragSymbol`(140)、`dragMotion`(142)、`dragFrom`(147)、`dragRefreshPending`(149, **非 observable**) + `private data class DragMotion(dy, target)`(91) |
| Undo | `undoText`(153)、`undoTimerRef`(154, **非 observable**) |
| 简报 / 收件箱 | `inboxMessages`(158, ObservableList)、`inboxUnread`(160)、`inboxPreviewPresented`(162)、`briefOpen`(164)、`briefPresented`(166)、`hasBrief`(168)、`briefLines`(170, ObservableList) |

**⚠️ 实测发现的越界点（必须修）**：

```kotlin
// WatchlistPage.kt L188 —— Page 直接构造了 Provider
QuotePrefetchStore.warm(watchlistStore.list().map { it.symbol }, TencentQuoteProvider(pagerId))
```

**Page 不得直接构造 `TencentQuoteProvider`。** QuotePrefetch 必须通过 **Effect / Repository** 触发。

**迁移与新建**

| 动作 | 目标 |
|---|---|
| 新建 | `watchlist/state/WatchlistCoordinator.kt` |
| 新建 | `watchlist/drag/state/WatchlistDragCoordinator.kt` |
| 新建 | `watchlist/brief/state/WatchlistBriefCoordinator.kt` |
| 新建 | `watchlist/component/WatchlistList.kt`、`WatchlistSearch.kt`、`WatchlistReasonEditor.kt`、`WatchlistBriefSheet.kt`、`UndoBar.kt` |
| 现有私有函数 | `groupLabel`(1535)、`groupTint`(1545)、`WatchlistFilterChip`(1552)、`WatchlistMenuRow`(1580)、`WatchlistCandidateRow`(1604)、`WatchlistPendingRow`(1639)、`WatchlistEmptyState`(1652) → 按域迁到对应 component |

**职责划分**

1. **WatchlistCoordinator** 接管 `rows` / `displayList` / `filter` / `search` / `menu` / `reason edit` 与 **Store Intent**。
2. **DragCoordinator** 接管 `dragSymbol` / `motion` / `from` / `refreshPending`。
3. **Undo 计时与 `lastRemoved` 归 WatchlistCoordinator** —— **组件内不得启动 Timer**。
4. **Brief Coordinator** 接管 inbox preview、brief `mounted`/`presented`、brief lines。
5. **QuotePrefetch 通过 Effect / Repository 触发**（修掉 L188）。

### ⚠️ AGENTS R5 硬约束（本项目踩过坑，务必遵守）

拖拽状态有一个**反直觉但必须保留**的约束：

> **取消拖拽时，不得重置 `dragFrom` / `dragTo`。**

原因（AGENTS.md R5 实证记录）：同一次批次里重置动画驱动 observable 会让持有该 key 的活跃动画注册**消费这次 N→0 的 reset**，把 `transform` 清除过程当作动画重播——表现是**布局瞬跳 + 第二次可见位移**（2026-09-09 watchlist 拖拽掉位闪烁事故）。

正确做法：`cancelDragSession` 里**让 `dragFrom` / `dragTo` 保持陈旧值**；所有读取都以 `dragSymbol` 为门控；`beginDragLift` 负责重新播种。

**迁移时逐字段核对这条约束，不要"顺手清理"成干净的状态重置。**

### 不做

- 不改拖拽阈值与手势判定
- 不改 Undo 的过期时长与文案
- 不改简报生成规则与收件箱已读逻辑
- 不动 `data/WatchlistStore.kt`（B-1 已定稿）
- 不改空态/候选行/待定行的视觉

### 自动验收

```bash
./gradlew architectureCheck
./gradlew :shared:testDebugUnitTest
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

**硬指标**：

```bash
# Page 不得再直接构造 Provider
grep -nE 'TencentQuoteProvider\(|Provider\(' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/page/WatchlistPage.kt   # 期望为空

# Page 不得再有业务 Timer
grep -nE 'setTimeout|by observable' \
  shared/src/commonMain/kotlin/com/kuikly/stockchat/page/WatchlistPage.kt
  # 期望：仅剩纯布局快照，无业务 setTimeout
```

### 人工验收

- [ ] **拖拽排序：无二次位移、无闪烁**（这是本包最容易回归的点，务必反复测）
- [ ] 拖拽取消后位置正确，再次长按能正常抬起
- [ ] Undo：可恢复；过期后无效
- [ ] 搜索：开合、候选列表、加自选
- [ ] 分组切换 + 状态筛选
- [ ] 原因编辑：chip 选择 + 手输
- [ ] 简报：mounted→presented 两拍入场
- [ ] 收件箱：预览卡入场、未读计数
- [ ] 快速重复：连续长按拖拽、连续 Undo
- [ ] 离页回调、退后台再回来
- [ ] reduce motion
- [ ] Android + H5

### 完成定义

- [ ] 拖拽无二次位移/闪烁；Undo 可恢复且过期无效
- [ ] 搜索、分组、状态筛选、原因编辑、简报、收件箱均有测试覆盖
- [ ] `WatchlistPage.kt` < **600 行**
- [ ] Page 不直接构造 `TencentQuoteProvider`
- [ ] 上述 12 项手测全过

---

## 8. B 轨的里程碑

| 里程碑 | 时点 | 判据 |
|---|---|---|
| **MB0** | day 2.5 | `data/` 不再 import `PagerScope`；composition root 已在 `app/assembly`；`Appearance.kt` 已拆 |
| **MB1** | day 4.5 | `AppChrome.kt` 已删除；`chat/**` 对 `page.*` import = 0 |
| **MB2** | day 10 | `RiskMapPage.kt` < 800 行；`risk/domain` 无 Kuikly import |
| **MB3** | day 13.5 | `WatchlistPage.kt` < 600 行；拖拽无二次位移；`data/**` 与 `chat/**` 的 allowlist 条目清零 |

---

## 9. 验收命令与提交模板

### 每个工作包收工必跑

```bash
./gradlew architectureCheck                            # 轨 A 的 A-1 落地后生效
./gradlew :shared:testDebugUnitTest                    # 基线 298，只增不减
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

### 提交要求

- **一个工作包一个 commit**，独立可回滚；
- 提交前打印 `git diff --cached --stat`；
- **不得夹带轨 A 的 WIP**（`git status` 里出现 A 的文件域改动，停下来先确认）；
- `ChatPage.kt` / `RiskMapPage.kt` / `WatchlistPage.kt` 任一时刻**只有你一个写入 owner**；
- commit message 必须包含四段：

```text
refactor(risk): 拆出数据/星图/AI/预警四个状态域

变更文件：<列表>
职责变化：<哪些字段从 RiskMapPage 移到哪个 Coordinator>
测试证据：architectureCheck 通过；testDebugUnitTest 326 tests / 0 failures
未覆盖风险：<例如 iOS 未手测；星图 reduce motion 仅代码走查>
```

---

## 10. 项目铁律速查（必读，违反会出事故）

完整版见仓库根目录 `AGENTS.md`。以下是做本轨工作**最会踩**的部分。

### 10.1 Kuikly 响应式与动画（AGENTS R1–R7）

- **R1 只有 `attr {}` / `event {}` / `vif {}` / `vfor {}` / `vbind {}` 里的读取才是响应式的。** 普通 `body()` 或子 builder 闭包（**包括 `Scroller` 的 child block**）只读到初始快照。搬 DSL 时最容易在这里出错。
- **R2 `animate(...)` 必须是 `attr {}` 块里最后一个 observable 依赖操作**——它用的是「最近一次读到的 observable key」而不是它的值参数。**主题类 observable 要在 `attr` 外缓存**，否则会顶掉预期的动画 key。
- **R3** 同一 `attr {}` 里一个驱动 observable 只能注册一次 `animate()`。
- **R4** `vif` / `vbind` 新挂载的视图**第一帧不会播动画**，入场要用 `mounted → presented` 两拍（`setTimeout(0)` 更新状态）。Watchlist 的收件箱预览卡与简报卡都是这个模式。
- **R5 注册滞后一周期 + 取消陷阱**：本周期注册的动画是**下一个**驱动变化才播放。推论：
  - 每周期（含挂载前周期）都要注册「下一次变化想播的」动画；
  - **绝不能在 pre-state 周期注册 0 时长的「重置」动画**（如 `Animation.linear(0f)`），presentation 周期会消费它，入场退化成瞬跳；
  - **绝不在同一次 layout/数据变更的批次里重置动画驱动 observable** —— 见 §7 的拖拽约束；
  - 入场序列必须有**带版本守卫的兜底定时器**，防止 `ref → setTimeout` 断链后视图卡在 `opacity 0`。
- **R6** 深层 builder 闭包里用显式 `page.theme`（隐式 receiver 可能解析失败）；数据类相等 ≠ 语义相同，比较**判别字段**而非整个 data class。
- **R7 `vif` 的 creator 只在「条件 false→true」时构建一次**，条件保持 true 期间**不会重跑**。面板/列表要刷新，用「单元素 `ObservableList<Int>` render key + `vfor`」整体重建。

### 10.2 平台隔离（用户硬要求）

**为某一端（通常是 iOS）做的修复，不得改变其他端的行为。**

唯一开关是 `common/PlatformPolicy.kt`（`expect object`，4 个源集各有 actual）：`marketFixes` / `coreThreadMarshalling` / `debugHooks`。**现状 iosMain 全 `true`，android / js / ohos 全 `false`。**

→ **本轨是归档 + 状态收口工作，正常不需要动这里。** 如果发现非要动，说明你在改行为，停下来找负责人确认。

配套 `common/PlatformConcurrency.kt`：`newAccessOrderMap()`、`platformSynchronized()`。**不要为了让 iOS 编译过而弱化 Android**（JVM 真锁 + accessOrder 是 Android 的正确实现）。

### 10.3 iOS 线程铁律

Kuikly iOS 要求**所有 Kotlin→Native 调用（`setTimeout` / 模块 / callback）都在 context queue 执行**；后台线程直调会 `assertContextQueue` SIGABRT（**Android 无断言、不报错**）。

合法回核心线程入口只有两个：native 模块回调 marshaling、既有 `setTimeout(0)` 链路。**搬 Coordinator 时如果涉及异步回调，注意这条。**

### 10.4 iOS 渲染层已知缺口

`iosApp/scripts/apply_kuikly_patches.rb` 有 **9 条幂等补丁**兜底上游缺口：

- 缺 `css_batchDraw` → 所有 `batchDraw=true` 的 Canvas **全空白**（图标、语音波形；逐条下发的图表不受影响）；
- 缺 `css_clip` 差集语义 → 流光描边被填成实心块。

**本轨只搬位置，不应新增任何 `batchDraw` Canvas。** 若搬迁的组件里已有，先确认补丁已套用。

### 10.5 其他

- **同一文件禁止并行 Edit**（会互相覆盖）；Gradle `fileHashes.lock` 被占 → `./gradlew --stop` 后重试。
- **`page/components` 禁止新增文件**（门禁规则 6）。
- **vfor 只认 ObservableList**；`vif` creator 只构建一次（R7）。
- 桥回传 Map 只认 `List` / `Map` / 基本类型，批量数据用 `List<Map<String, String>>`。

---

## 11. 可选加项（本轮已砍出，日后可接）

以下三项**不计入 13.5 人日**，对四层分离得分贡献为 0 或很低，但属于核心价值交付，B 收工后有余力可接：

| 项 | 人日 | Token | 说明 |
|---|---:|---:|---|
| **P1-Chat 收口** | 5.0 | 700k–1.30M | `ChatPage.kt` **3,745 行 / 约 210 个函数**。新增 `chat/quote/state/ChatQuoteCoordinator.kt`（接管 `quoteStates`、行情请求、watchlisted 派生）与 `chat/session/state/AlertPollingCoordinator.kt`（接管 `alertPollGeneration`）；`MediaActionSheetHost` → `chat/composer/component`。目标 < 1,200 行。**注意：这项性价比排倒数第三（0.60 分/人日）——Chat 已抽 8 个 Coordinator、76 分是全项目最高，边际收益递减，而验收矩阵最重（9 项全链路手测，语音与附件需真机，成本几乎全在验证）。** |
| P1-Glossary / Alert | 3.5 | 400–750k | `GlossaryCoordinator` + `GlossaryLearningFlowCoordinator`（`queue`/`visible`/`index`/`pan`/`inertia`/`finished`/`advanced`）；`AlertInboxCoordinator`（`messages`/`filter`/`open`/`read`/`mute`/`quiet-hours`/`presented`/`hint`）。目标 Glossary < 500 行、Alert < 350 行 |
| P2 Gradle 模块化 | 6.5 | 800k–1.60M | 只在 allowlist 基本清空后做。迁移顺序：纯 Kotlin domain → design token → cards → UI → chart → Feature。**一次提交只迁一个模块** |

---

## 12. 开工前自检清单

- [ ] 我读完了 `AGENTS.md` 的 R1–R8
- [ ] 我已确认工作区干净（`git status`），没有别人的 WIP
- [ ] 我知道本轨独占哪些文件、以及 `AppChrome.kt` 归我（由我在 B-2 整体拆开并删除）
- [ ] 我知道 **B-2 要等轨 A 的 A-2 完成**（day 2.5），而我的 B-1 恰好占满前面这段时间
- [ ] 我知道 **B-1 会让我在 A 的文件里改 import 行**（只改 import，不改逻辑）
- [ ] 我知道 **B-3 / B-4 要等 B-1**（装配根）
- [ ] 我知道 Watchlist 拖拽有 **AGENTS R5 的「取消时不重置 dragFrom/dragTo」约束**，不能"顺手清理"
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

关键文件行数：
  page/ChatPage.kt                      3,745
  page/StockDetailPage.kt               3,143   ← 轨 A
  page/RiskMapPage.kt                   2,692   ← 本轨
  page/components/GeneratedSvgCanvasIcons.kt  1,789
  page/components/AppChrome.kt          1,756   ← 本轨 B-2 删除
  page/WatchlistPage.kt                 1,690   ← 本轨
  page/MarketPage.kt                    1,660   ← 轨 A
  data/provider/EastMoneyInsightProvider.kt      643  ← 本轨 B-1
  page/components/ChatMessageComponents.kt       661  ← 本轨 B-2
  page/components/RiskSkyChart.kt                404  ← 本轨 B-2
  page/risk/StarLayout.kt                        294  ← 本轨 B-3
  data/Appearance.kt                             129  ← 本轨 B-1 拆分（43 个文件消费）

`fontSizeScaled` / `lineHeightScaled` 消费文件数：43
`data/` 内 PagerScope / SharedPreferencesModule 命中：data/storage/KeyValueStorage.kt
```
