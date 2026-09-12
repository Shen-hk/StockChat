# StockChat 四层架构整改：双人并行拆分与 Token 预算

> 版本：v1.1（新增 §12 高性价比精简版）
> 日期：2026-09-12
> 上游文档：`docs/44-全项目四层架构审计与整改实施文档_v1.0.md`
> 基线：`master` HEAD `fac42b1`，工作区干净
> 用途：把文档 44 的 13 个工作包拆成两条可并行推进的轨，供两人分工；附优先级与 token 预算。
> §1–§11 为**全量版**（47.5 人日）；**§12 为高性价比精简版（27 人日）**，是推荐的落地范围。

---

## 1. 一句话结论

文档 44 的 13 个工作包合计 **47.5 人日 / 约 8.2M tokens**，按「文件域不重叠」拆成两条轨后每轨约 **20 人日 / 3.2–3.8M tokens**，可真正并行；唯一的硬前置是 **P0-A 门禁（1 人日）** 与 **P0-C 组件归档（3.5 人日）**，在这两者落地前不能动任何 P1 页面。

---

## 2. 估算基准（实测，非估计）

```text
shared/src/commonMain/kotlin/com/kuikly/stockchat
  page/              40 文件   27,234 行   （含 page/components 20 文件 9,322 行）
  chat/              48 文件    6,926 行
  data/              31 文件    5,623 行
  detail/            25 文件    2,484 行
  cards/             13 文件    2,457 行
  composer/           8 文件      791 行
  base/               4 文件      704 行
  richtext/           3 文件      488 行
  glass/              2 文件      228 行
  chart/              2 文件      197 行
  voice/              1 文件      140 行
  ─────────────────────────────────────
  commonMain 合计   185 文件   48,368 行
  测试               90 文件    5,664 行
四端 Bridge：Android 1,061 + iOS 852 + Ohos 594 + H5 143 = 2,650 行
```

文档 44 的关键证据复核结果（全部与当前代码一致）：

| 文档断言 | 实测 |
|---|---|
| `AppChrome.kt` 1,756 行 | 1,756 ✔ |
| 7 个大页行数 | 3745 / 3143 / 2692 / 1690 / 1660 / 1391 / 738 ✔ 逐一对上 |
| 非 page 包反向 import `page.*` ≈ 66 处 | 66 处 ✔（其中 `detail/` 53 处）|
| State 仍 import page | 5 个文件 ✔（chart/ai/overlay 三域）|
| `MarketDependencies` 在 `data/` | ✔ `data/MarketDependencies.kt` |
| `PagerKeyValueStorage` 在 `data/storage` | ✔ 定义于 `data/storage/KeyValueStorage.kt` |
| `ArchitectureBoundariesTest` 只验端口可用性 | ✔ 112 行，5 个测试，零源码依赖方向检查 |

---

## 3. Token 估算方法

两种方法交叉校验，取区间：

- **方法 A（自下而上）**：`token ≈ 触及行数 × 30`。Kotlin 代码约 3.5–4 字符/token、均行约 45 字符 → 单次通读约 12 token/行；agentic 重构中同一文件平均被读 2–3 次（初读、改前复核、改后回归），新增代码量约等于删除量，再叠加编译/测试失败循环 → 综合放大系数约 30。
- **方法 B（自上而下）**：`token ≈ 人日 × 180k`。一个 agentic 工作日约 30–50 轮，含 KV 缓存命中后的平均上下文规模。

两法在总量上收敛：8,205k ÷ 47.5 人日 ≈ **173k tokens/人日**。

**不确定度声明**：下列数字为规划用估算，实际偏差可达 ±50%，主要取决于编译/测试循环次数与是否需要人工介入调参。**不要把 token 预算当作硬上限使用**，建议每轨预留 20% 缓冲。

---

## 4. 双轨划分总表

| 轨道 | 定位 | 工作包 | 人日 | Token 中值 |
|---|---|---|---:|---:|
| **A** | 共享基座 / Detail / 平台 | P0-A → P0-C-shared → P0-C-dr → P1-Detail → P1-Market → P1-Platform | 19.5 | **3.2M** |
| **B** | 装配根 / Chat / 剩余页 | P0-B → P0-C-chat → P1-Chat → P1-Watchlist → P1-Risk → P1-Glossary/Alert | 21.5 | **3.8M** |
| **尾段** | 串行，谁先空谁接 | P2 Gradle 模块化 | 6.5 | **1.2M** |
| | | **合计** | **47.5** | **8.2M** |

拆分依据：A 拿走「所有非交互页面 + 共享层 + 宿主层」，B 拿走「装配根 + 交互最重的 Chat + 三个数据型页面」。两条轨在大页文件上零重叠，共享文件只有 `AppChrome.kt` 一处（见 §8）。

---

## 5. 轨道 A 详情（共享基座 / Detail / 平台）

### A1. P0-A 建立自动架构门禁 —— 1 人日 / 80–150k

| 项 | 内容 |
|---|---|
| 前置 | 无 |
| 新增 | `scripts/check_architecture.sh`、`docs/architecture/package-rules.md`、allowlist 文件、根 `build.gradle.kts` 注册 `architectureCheck` |
| 覆盖规则 | ① `data` 不得 import `page`/Kuikly views/reactive/Navigation/Toast；② `component` 不得 import Repository/Store/Provider/Bridge/SharedPreferences/PagerScope；③ `state` 不得 import `page`/Kuikly View/具体平台实现；④ `shared`、`foundation` 不得 import Feature；⑤ Page 新增 `setTimeout`/Provider 构造/业务 `by observable` 即失败；⑥ `page/components` 禁止新增文件 |
| allowlist 初值 | 66 条反向 import + 5 个 state 文件 + `AppChrome.kt` 等，逐条记「文件 + 原因 + 删除它的工作包」 |
| 验收 | 基线通过；手工加一条禁止 import 即失败；删除后恢复；脚本 < 5 秒 |
| 为什么排第一 | 没有它，后面所有工作的成果都可能在后续提交里悄悄回退；且它是**最便宜的一刀**，1 人日换全程序防回退 |

### A2. P0-C-shared 共享 / Foundation 组件归档 —— 2 人日 / 250–450k

| 项 | 内容 |
|---|---|
| 前置 | 无（与 P0-A 文件域不重叠，可同时做） |
| 迁移 | `AppTopBar`/`AppTopBarAction`/`AppTopBarMetric` → `foundation/ui/chrome/`；`DataModeBadge` → `foundation/ui/feedback/`；`LineIcons` → `foundation/ui/icon/`（底层 path helper 保持 private）；两套 `SourceStampLine` 合并为一个 API；`glass/GlassContainer`/`GlassRenderer` → `foundation/ui/surface` + `foundation/design`；`cards/components/CardShell.kt` → `shared/cards/component` |
| 不做 | `FeatureTile`、`UndoBar`、`SegmentBar`、`NewsMarquee/NewsTape`、Loading/Empty/Error —— 业务使用方不足两个，留在原处（见文档 44 §6.2）|
| 风险 | `AppTopBar` 被十余页面使用，是**全项目最广的一处 import 改动**；建议先改定义再一次性批量修 import，编译一次通过再提交 |
| 验收 | 截图对比无视觉变化；Android/H5 手测通过；`page/components` 只剩 allowlist |

### A3. P0-C-dr Detail / Risk 组件归档 —— 1.5 人日 / 200–350k

| 项 | 内容 |
|---|---|
| 前置 | 无 |
| 迁移 | `DetailBoardBlocks.kt`、`DetailTimelineChart.kt`、News 详情使用部分 → `detail/.../component`；`RiskSkyChart.kt` → `risk/component` |
| 关键约束 | 这是**纯物理归档**：不改布局、参数、动画、状态逻辑；`DetailTimelineChart` 内部的 Timer 与 observable **本轮不动**（由 A4 处理），本轮只搬文件位置 |
| 验收 | `detail/**/component` 与 `risk/component` 对 `page.*` import 下降；视觉无变化 |

### A4. P1-Detail 完成反向依赖与残余状态收口 —— 5 人日 / 650k–1.20M

| 项 | 内容 |
|---|---|
| 前置 | A3 |
| 新增 Coordinator | `DetailChromeCoordinator`（顶部折叠、ticker、收藏反馈、页面/灵动岛 handoff）；`DetailMotionCoordinator` 或并入 Chart Coordinator（`sonarDrift`、`drawProgress`、tape 时钟）；`DetailContentCoordinator`（company tab/presented、expanded attribution、selected sentence，可用 Reducer）|
| 移出 page 包 | `ChartFlag`、`AnomalyPoint`、`DetailOverlay`、`AnchorIndex` → 使 state 不再 import page（当前 5 个 state 文件中的 3 个）|
| 迁入组件 | `ChartLegend`、`ChartSegment`、`BusinessInsightGrid`、`AiInsightBlock`、`AttributionForecastWorkbench`、BottomBar → `detail/page/component` |
| 最难一步 | `DetailTimelineChart` 去 Timer 化：先补交互状态测试，再把内部 Timer/observable 改由 Props/Actions + Chart Coordinator 驱动 |
| 验收 | `detail/**/{component,state}` 对 `page.*` import = 0；`StockDetailPage.kt` < 900 行；四套既有 Coordinator 测试 + 新增测试全过；分时/K线、scrub、圈选 AI、overlay 仲裁、公司区 tab、收藏与 handoff 手测通过 |

### A5. P1-Market 建立四层纵切 —— 4.5 人日 / 500–950k

| 项 | 内容 |
|---|---|
| 前置 | **B 的 P0-B 必须已落地**（需要注入好的 Repository/Graph）|
| 新建 | `market/data/MarketRepository.kt`；`market/state/MarketOverviewCoordinator.kt`；`market/replay/state/MarketReplayCoordinator.kt`；`market/ai/state/MarketAiCoordinator.kt`；`market/news/state/MarketNewsCoordinator.kt`；`market/component/{MarketHero,MarketBreadth,MarketReplay,MarketNews}.kt` |
| 关键约束 | MarketAiCoordinator 可复用 Detail 的纯会话范式，但**不得让 Market import Detail Feature**；稳定后把通用会话下沉 shared |
| 验收 | `MarketPage` 无 Provider 实例、Typewriter、generation、业务 Timer；四个 Coordinator 各有状态与生命周期测试；下拉刷新、行情回放、新闻 tape/peek、两类 AI、reduce motion 手测通过；`MarketPage.kt` < 700 行 |

### A6. P1-Platform 拆分四端 Bridge —— 5.5 人日 / 550k–1.00M

| 项 | 内容 |
|---|---|
| 前置 | 无（宿主层完全独立，**最适合作为缓冲任务**：A 若被 B 的依赖卡住，可先做这个）|
| 文件 | Android `KRBridgeModule.kt` 1,061 行 / iOS `HRBridgeModule.m` 852 行 / Ohos `KRBridgeModule.ets` 594 行 / H5 `KRBridgeModule.kt` 143 行 |
| 新建 | commonMain `foundation/platform/BridgeContract.kt` + payload/result 模型 |
| 怎么改 | 原类只留方法分发、统一错误回传、capability 注册；每端拆出 Navigation、MediaPreparation/Picker、VoiceSession、Share、Haptic；Page 不再认识万能 `BridgeModule`，改由 `NavigationPort/HapticPort/MediaPort/VoicePort` adapter 执行 Effect |
| 硬约束 | **method 字符串与 JSON 字段逐字不变**，先补契约测试再搬代码；H5 不支持的能力必须显式返回 unsupported/cancel，不能静默吞回调 |
| 验收 | 四端 method 集合与 payload schema 契约测试通过；语音开始—停止—取消与媒体选择回调**各只完成一次**；退后台/页面销毁后 callback 不写已失效 State；Android/H5 自动构建通过，iOS/Ohos 按能力冒烟 |

---

## 6. 轨道 B 详情（装配根 / Chat / 剩余页）

### B1. P0-B 移动装配根和平台适配器 —— 2.5 人日 / 350–600k

| 项 | 内容 |
|---|---|
| 前置 | 无（与 P0-A 并行） |
| 迁移 | `data/MarketDependencies.kt` → `app/assembly/MarketFeatureGraph.kt`；`chat/ChatDependencies.kt` 的 `forPager` → `app/assembly/ChatFeatureGraph.kt`；`data/storage/KeyValueStorage.kt` 中的 `PagerKeyValueStorage` → `app/platform/KuiklyKeyValueStorage.kt`；`data/Appearance.kt` → `foundation/design/Appearance.kt` + `foundation/ui/FontScaleAttr.kt` |
| 新建 | 更小的 `ChatFeatureDependencies`、`DetailFeatureDependencies`、`MarketFeatureDependencies` |
| 怎么改 | ① Store/Repository 构造器只接收 Port，逐步弃用并最终删除 `constructor(pagerId)`；② `forPager` 只能出现在 App Assembly；③ Page 从 graph 取本 Feature 最小依赖集，不能拿到含九个服务的万能 `MarketDependencies`；④ `TencentQuoteProvider`、`EastMoneyInsightProvider`、OpenAiCompat Provider 分批改为注入 `PlatformHttpClient`/Clock/Storage，不直接 `acquireModule`；⑤ **本包只改装配，不改 Provider 解析与降级逻辑** |
| 为什么排第二 | `data/` 里混着 composition root，会污染后续**每一个** Repository/Store 工作；不先清掉，A5/B4/B5/B6 都要重复付这笔债 |
| 验收 | Data 层不再 import `PagerScope`/`SharedPreferencesModule`；所有 Store 单测继续用 `InMemoryKeyValueStorage`；Provider parser 既有测试全过；Android/JS 编译通过 |

### B2. P0-C-chat Chat 组件归档 —— 1.5 人日 / 200–380k

| 项 | 内容 |
|---|---|
| 前置 | 无，但**独占 `AppChrome.kt`**（见 §8）|
| 迁移 | `AppChrome.kt` 拆解：`ChatTopNav`/StockIsland/CompareIsland → `chat/island/component/QuoteIsland.kt`；`ChatDrawer` → `chat/drawer/component/ChatDrawer.kt`；`ChatMessageComponents.kt`、`ChatScaffolding.kt`、`VoiceBar.kt` → `chat/.../component` |
| 完成后 | **`AppChrome.kt` 删除**（1,756 行归零）|
| 验收 | Chat 组件不再 import `com.kuikly.stockchat.page.*`；视觉无变化 |

### B3. P1-Chat 把 ChatPage 收敛成装配层 —— 5 人日 / 700k–1.30M

| 项 | 内容 |
|---|---|
| 前置 | B2 |
| 新增 Coordinator | `chat/quote/state/ChatQuoteCoordinator.kt`（接管 `quoteStates`、行情请求、watchlisted 派生）；`chat/session/state/AlertPollingCoordinator.kt`（接管 `alertPollGeneration` 与轮询生命周期）；`welcomeStarterRenderKey` 收归 Welcome state/component 的 R7 render-key 所有者 |
| 迁移 | `MediaActionSheetHost` → `chat/composer/component`；其余渲染函数按 message/composer/island/drawer/session/card 分别迁到对应 component |
| 怎么改 | ① 先列 ChatPage 全部字段与函数的 owner 表，**一个字段只能属于一个域**；② 先搬 Timer/revision/Provider 调用到 Coordinator，再搬 DSL；③ Page 只留 Effect adapter（Route、Haptic、Media、Voice、Clipboard、原生 Selection）；④ Props **不允许传整个 `ChatPage` 或 `ChatViewModel`**，只传只读 accessor 与 Actions；⑤ 严守 AGENTS R1–R7，尤其 render key、mounted→presented、动画上一周期注册 |
| 验收 | `ChatPage.kt` < 1,200 行；Page 内业务 observable 为 0（ViewRef、路由参数、纯布局快照除外）；每个新 Coordinator 有 fake scheduler 测试；Chat 全链路 Android/H5 手测：输入、流式、语音、附件、实体拖拽、股票/术语对比、消息菜单、历史会话、退后台再回来 |

### B4. P1-Watchlist 拆拖拽、Undo、列表与简报 —— 3.5 人日 / 450–850k

| 项 | 内容 |
|---|---|
| 前置 | B1 |
| 新建 | `watchlist/state/WatchlistCoordinator.kt`；`watchlist/drag/state/WatchlistDragCoordinator.kt`；`watchlist/brief/state/WatchlistBriefCoordinator.kt`；`watchlist/component/{WatchlistList,WatchlistSearch,WatchlistReasonEditor,WatchlistBriefSheet,UndoBar}.kt` |
| 分工 | WatchlistCoordinator 接管 rows/displayList/filter/search/menu/reason edit 与 Store Intent；DragCoordinator 接管 `dragSymbol/motion/from/refreshPending`，**完整保留 AGENTS R5「取消时不重置 dragFrom/dragTo」约束**；Undo 计时与 lastRemoved 归 WatchlistCoordinator（组件内不起 Timer）；Brief Coordinator 接管 inbox preview、brief mounted/presented、brief lines；QuotePrefetch 通过 Effect/Repository 触发，Page 不直接构造 `TencentQuoteProvider` |
| 验收 | 拖拽排序无二次位移/闪烁；Undo 可恢复且过期无效；搜索、分组、状态筛选、原因编辑、简报、收件箱测试覆盖；`WatchlistPage.kt` < 600 行 |

### B5. P1-Risk 拆计算、星图、AI 与预警 —— 5.5 人日 / 600k–1.10M

| 项 | 内容 |
|---|---|
| 前置 | B1 |
| 迁移 | `page/risk/StarLayout.kt` 等纯算法 → `risk/domain` |
| 新建 | `risk/data/RiskRepository.kt`；`risk/state/RiskDataCoordinator.kt`；`risk/sky/state/RiskSkyCoordinator.kt`；`risk/ai/state/RiskAiCoordinator.kt`；`risk/alert/state/RiskAlertCoordinator.kt` |
| 关键约束 | 纯计算输出不可变 `RiskExposureSnapshot`，输入只含 watchlist/quotes/industry/events，**不得读取 Store/Pager**；`RiskSkyChart` 只渲染 geometry + state accessor，所有 Timer 由 Sky Coordinator 管理 |
| 验收 | 风险计算可用**纯单测**覆盖、无 Kuikly import；Page 无 AI Provider、PagerStorage、pulseRunning、drag generation；星图五层、导图、拖拽回弹、AI 降级、预警生成、reduce motion 手测通过；`RiskMapPage.kt` < 800 行 |

### B6. P1-Glossary / Alert 完成剩余中型页面 —— 3.5 人日 / 400–750k

| 项 | 内容 |
|---|---|
| 前置 | B1 |
| Glossary | `GlossaryCoordinator`（query/category/expanded/depth）；`GlossaryLearningFlowCoordinator`（queue/visible/index/pan/inertia/finished/advanced）；组件 Map/SearchResult/LearningFlow/FilterChip；`GlossaryStore` 保留 Data 层，只通过 Intent 调用 |
| Alert | `AlertInboxCoordinator`（messages/filter/open/read/mute/quiet-hours/presented/hint）；组件 AlertFilter/AlertMessageCard/AlertSettingsSheet；Quote/Event 加载走 Repository，Page 只处理路由与 Haptic 等 Effect |
| 验收 | 两页 Page 无 Timer、业务 observable、Store 直接写；学习流 pan/inertia、卡片 mounted→presented、静音与已读持久化均有测试；Glossary < 500 行、Alert < 350 行 |

---

## 7. 优先级排序

### P0 级 —— 阻塞后续，必须最先做

| 序 | 工作包 | 轨 | 人日 | 理由 |
|---:|---|---|---:|---|
| 1 | **P0-A 门禁** | A | 1.0 | 最便宜的一刀。没有它，后面所有成果都可能被后续提交悄悄回退。且它是唯一「不改业务代码」的工作包 |
| 2 | **P0-B 装配根** | B | 2.5 | `data/` 里混着 composition root，会污染后续每一个 Repository/Store 工作 |
| 3 | **P0-C 组件归属**（3 个子包）| A+B | 5.0 | 不搬，P1 各页会继续把组件拉回 Page，做完 P1 还要返工一遍 |

### P1 级 —— 核心价值交付

| 序 | 工作包 | 轨 | 人日 | 理由 |
|---:|---|---|---:|---|
| 4 | P1-Chat 收口 | B | 5.0 | 复杂度最高（3,745 行 / 210 函数），但状态层范式已验证，收益最大、最值得先投 |
| 5 | P1-Detail 收口 | A | 5.0 | 反向依赖最集中（53/66 处），清掉后 `detail` 包才算真正独立 |
| 6 | P1-Risk 拆分 | B | 5.5 | 2,692 行且纯算法未被分离，是「计算与 UI 未分层」最严重的页面 |
| 7 | P1-Market 纵切 | A | 4.5 | 状态最少（45 分）但数据/回放/AI/新闻全部内联，返工成本高 |
| 8 | P1-Platform 四端 Bridge | A | 5.5 | 完全独立，可作缓冲任务；不拆则 Effect adapter 长期不可维护 |
| 9 | P1-Watchlist 拆分 | B | 3.5 | Store 已成熟，拖拽/Undo/简报混在 Page；注意 AGENTS R5 约束 |
| 10 | P1-Glossary/Alert | B | 3.5 | 复杂度较低，按同一模板归档即可 |

### P2 级 —— 可延后

| 序 | 工作包 | 轨 | 人日 | 理由 |
|---:|---|---|---:|---|
| 11 | P2 Gradle 模块化 | 尾段 | 6.5 | 文档明确：P2 **不是达到四层分离的前置条件**；allowlist 未清空前做模块化只会把包边界问题固化成模块边界问题 |

**最推荐顺序**：门禁 → 装配根 → 共享/Feature 组件归属 → Chat/Detail 收口 → Market/Watchlist → Risk → Glossary/Alert → 模块化。

---

## 8. 冲突域与串行点（并行安全规则，最关键一节）

两个人并行最大的风险不是工作量，而是**同时改同一个文件**。以下为强制归属：

| 文件域 | Owner | 说明 |
|---|---|---|
| `page/ChatPage.kt` | **B** 独占 | 任一时刻只能一个写入 owner |
| `page/StockDetailPage.kt`、`page/MarketPage.kt` | **A** 独占 | |
| `page/WatchlistPage.kt`、`page/RiskMapPage.kt`、`page/GlossaryPage.kt`、`page/AlertCenterPage.kt` | **B** 独占 | |
| `page/components/AppChrome.kt` | **B 独占** | ⚠️ **两轨唯一的共享文件**。同一文件含通用件（AppTopBar/DataModeBadge）与 Chat 私有件（ChatTopNav/QuoteIsland/ChatDrawer）。规则：由 B 一次性整体拆开（B2），A 在此期间**不得触碰该文件**；A2 的 `AppTopBar` 迁移排在 B2 **之后**，或由 B 顺带完成 |
| `page/components/` 其余文件 | A | LineIcons、SourceStampLine、DetailBoardBlocks、DetailTimelineChart、RiskSkyChart、News* 等 |
| `data/**` | **B 独占**（B1 期间）| A 在 B1 落地前不得改 `data/`；A5 只新建 `market/data/`（新文件，无冲突）|
| `app/assembly`、`app/platform` | B 新建并独占 | A 只读 |
| `chat/**` | B | |
| `detail/**` | A | |
| `market/**`、`risk/**` 新建包 | 各自 Page owner | A: `market/`；B: `risk/` |
| 根 `build.gradle.kts`、`settings*.gradle.kts` | **A 独占** | A 注册门禁（P0-A）与改模块（P2）；B 只能读 |
| `scripts/`、`docs/architecture/` | A | |
| `androidApp/`、`iosApp/`、`ohosApp/`、`h5App/` | **A** | B 不碰宿主层 |

### 三个串行点

| 编号 | 时点 | 内容 |
|---|---|---|
| **S0** | day ≈ 1（A 完成 P0-A） | 门禁就绪，此后**每个提交**都跑 `architectureCheck`。B 从 day 0 起并行做 B1，不受影响 |
| **S1** | day ≈ 4（B 完成 B2）/ day ≈ 4.5（A 完成 A3） | 组件归档完成，**才允许开 P1 页面**。这是唯一硬门：不搬组件就改 Page，Page 会把组件再拉回去。两轨各自自门控，无需等待对方 |
| **S2** | day ≈ 19.5（A）/ 21.5（B） | 两轨 P0+P1 全部完成，P2 才可启动 |

### 一个依赖例外

`A5 P1-Market` 依赖 `B1 P0-B` 的产物（可注入的 Repository/Graph）。按当前排期，A 到 day 9.5 才开 Market，B 的 P0-B 在 day 2.5 已完成，**不构成阻塞**。若 A 提前做 Market，必须先确认 B1 已合并。

---

## 9. Token 预算汇总

| 工作包 | 轨 | 人日 | Token 区间 | 中值 |
|---|---|---:|---|---:|
| P0-A 门禁 | A | 1.0 | 80–150k | 115k |
| P0-B 装配根 / 平台适配器 | B | 2.5 | 350–600k | 475k |
| P0-C-shared 共享组件归档 | A | 2.0 | 250–450k | 350k |
| P0-C-chat Chat 组件归档 | B | 1.5 | 200–380k | 290k |
| P0-C-dr Detail/Risk 组件归档 | A | 1.5 | 200–350k | 275k |
| P1-Chat 收口 | B | 5.0 | 700k–1.30M | 1.00M |
| P1-Detail 收口 | A | 5.0 | 650k–1.20M | 925k |
| P1-Market 纵切 | A | 4.5 | 500–950k | 725k |
| P1-Watchlist 拆分 | B | 3.5 | 450–850k | 650k |
| P1-Risk 拆分 | B | 5.5 | 600k–1.10M | 850k |
| P1-Glossary/Alert | B | 3.5 | 400–750k | 575k |
| P1-Platform 四端 Bridge | A | 5.5 | 550k–1.00M | 775k |
| P2 Gradle 模块化 | 尾段 | 6.5 | 800k–1.60M | 1.20M |
| **合计** | | **47.5** | **5.4M–10.9M** | **8.2M** |

**分轨预算**

| 轨 | 人日 | Token 中值 | 建议预留（+20%）|
|---|---:|---:|---:|
| A | 19.5 | 3.2M | 3.8M |
| B | 21.5 | 3.8M | 4.6M |
| 尾段 P2 | 6.5 | 1.2M | 1.4M |
| **合计** | **47.5** | **8.2M** | **9.8M** |

---

## 10. 每个工作包的固定交付模板

### 自动验收（每个工作包都必须跑）

```bash
./gradlew architectureCheck                              # P0-A 落地后生效
./gradlew :shared:testDebugUnitTest                      # 基线 298 测试，新增后只增不减
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinJs
git diff --check
```

涉及 Ohos common/actual 时额外（AGENTS R8）：

```bash
./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64
```

### 提交要求

- **一个状态域一个 commit**，每个工作包独立可回滚；
- 提交前打印 `git diff --cached --stat`；
- 不得夹带其他会话 WIP；
- 大 Page 同一时刻只有一个写入 owner；
- commit message 包含：变更文件、职责变化、测试证据、未覆盖风险。

### 不做（所有工作包通用禁止项）

不改视觉、文案、动画数值、手势阈值、路由、Prompt、降级策略；**不与包改名、依赖升级或新功能混在同一提交**。

---

## 11. 里程碑判据

| 里程碑 | 判据 |
|---|---|
| **M0 day 3.5** | `architectureCheck` 上线且基线通过；`data/` 不再 import `PagerScope`；`AppChrome.kt` 归零 |
| **M1 day 9.5** | Chat 与 Detail 两个大页各 < 1,200 / < 900 行；`detail/**` 对 `page.*` import = 0 |
| **M2 day 18** | Market/Watchlist/Risk/Glossary/Alert 五页纵切完成，各自 Page 达行数目标 |
| **M3 day 21.5** | 四端 Bridge 拆分完成，契约测试四端通过；allowlist 清空 |
| **M4 day 28** | P2 模块化完成；文档 44 §9 的 12 条最终验收表全部勾选 |

---

## 12. 高性价比精简版（推荐落地范围）

### 12.1 先砍掉收益为零的两个包

全量 47.5 人日对「拿到四层分离实质效果」是过量的。有 **12 人日 / 1.98M** 的工作包**对四层分离得分贡献为 0**，依据是文档 44 自己写明的两句：

- §4.5：「宿主层**不计入** Page / Component / Data / State 四层本体」→ **P1-Platform 对 80 分验收线无直接贡献**
- §1：「P2 模块化**不是**达到四层分离的前置条件」→ **P2 属延后项**

这两个包是纯可维护性投资（值得做，但不该占用本轮架构整改的预算）。砍掉它们**不损失任何验收分**。剩 35.5 人日 / 6.2M。

### 12.2 性价比评级（口径：每分收获 / 人日）

收益 = 该包直接抬升的维度分（按文档 44 §3 的维度权重与当前得分估算）：

| 工作包 | 轨 | 人日 | Token | 直接收益 | **分 / 人日** | 级 |
|---|---|---:|---:|---:|---:|:--|
| **P0-A 架构门禁** | A | 1.0 | 115k | 门禁维度 +5 | **5.00** | **S** |
| **P0-C 共享组件归档** | A | 2.0 | 350k | Component + 共享组件 +3.5 | **1.75** | **A** |
| **P0-C Chat 组件归档** | B | 1.5 | 290k | Component +2.5 | **1.67** | **A** |
| **P0-B 装配根 / 平台适配器** | B | 2.5 | 475k | Data +4 | **1.60** | **A** |
| **P0-C Detail/Risk 组件归档** | A | 1.5 | 275k | Component +2 | **1.33** | **A** |
| P1-Watchlist 拆分 | B | 3.5 | 650k | Page/State +4 | 1.14 | B |
| P1-Market 纵切 | A | 4.5 | 725k | Page/State +5 | 1.11 | B |
| P1-Risk 拆分 | B | 5.5 | 850k | Page/State +5 | 0.91 | B |
| P1-Detail 收口 | A | 5.0 | 925k | 反向依赖清零 + Page/State +4 | 0.80 | B |
| P1-Glossary / Alert | B | 3.5 | 575k | Page/State +2.5 | 0.71 | C |
| P1-Chat 收口 | B | 5.0 | 1.00M | Page +3，但手测矩阵最重 | 0.60 | C |
| P1-Platform 四端 Bridge | A | 5.5 | 775k | **不计入四层本体 → 0** | **0** | **D** |
| P2 Gradle 模块化 | 尾段 | 6.5 | 1.20M | **非四层分离前置 → 0** | **0** | **D** |

**三个反直觉的结论**：

1. **P0-A 门禁的性价比是第二名的近 3 倍**（5.00 vs 1.75）。1 人日买断全程序防回退，且它不改任何业务代码——这 1 人日不该省。
2. **P1-Chat 是全项目最大的文件（3,745 行）但性价比排倒数第三**（0.60）。原因：Chat 已抽走 8 个 Coordinator，76 分是全项目最高，**边际收益递减**；而它的验收矩阵最重（输入/流式/语音/附件/实体拖拽/对比/菜单/历史/退后台 9 项全链路手测，语音与附件还需真机）。**成本几乎全在验证，不在编码。**
3. **P1-Platform 虽然文档给了 5.5 人日，但对验收线贡献为 0**。它值得做，但不是架构整改的账。

### 12.3 精简版范围

| 层 | 包含 | 人日 | Token |
|---|---|---:|---:|
| **核心（S / A 级，不可省）** | P0-A + P0-B + P0-C 三子包 | 8.5 | 1.50M |
| **高收益（B 级）** | P1-Detail + P1-Risk + P1-Market + P1-Watchlist | 18.5 | 3.15M |
| **可延后（C 级）** | P1-Chat + P1-Glossary/Alert | 8.5 | 1.58M |
| **建议砍出本轮（D 级）** | P1-Platform + P2 | 12.0 | 1.98M |
| **精简版合计（核心 + 高收益）** | | **27.0** | **4.66M** |

精简版保留全量版 **57% 的人日与 token**，但覆盖了：全部结构性前置（门禁 / 装配根 / 组件归属）、**66 处反向依赖中 `detail/` 的 53 处**、以及**四个得分最低的页面**（Risk 42 / Market 45 / Watchlist 48 / Detail 72）。

### 12.4 精简版两轨分工（13.5 : 13.5 人日，**完全文件不相交**）

> 施工细节见 `docs/46-轨A施工任务书_共享基座-Detail-Market_v1.0.md` 与 `docs/47-轨B施工任务书_装配根-Risk-Watchlist_v1.0.md`。

**轨 A — 共享基座 / Detail / Market** · 13.5 人日 · 2.31M

| 序 | 工作包 | 人日 | Token | 前置 |
|---:|---|---:|---:|---|
| A-1 | P0-A 架构门禁 | 1.0 | 115k | 无 |
| A-2 | P0-C-shared 共享组件归档（LineIcons / SourceStampLine 合并 / Glass / CardShell）| 1.5 | 265k | 无 |
| A-3 | P0-C-detail Detail 组件与模型归档 | 1.5 | 275k | 无 |
| A-4 | P1-Detail 反向依赖与残余状态收口 | 5.0 | 925k | A-3 |
| A-5 | P1-Market 四层纵切 | 4.5 | 725k | 轨 B 的 B-1 |

**轨 B — 装配根 / Chat & Risk 组件 / Risk / Watchlist** · 13.5 人日 · 2.36M

| 序 | 工作包 | 人日 | Token | 前置 |
|---:|---|---:|---:|---|
| B-1 | P0-B 装配根与平台适配器 | 2.5 | 475k | 无 |
| B-2 | P0-C-chat/risk 组件归档（**含 AppChrome.kt 整体拆除**）| 2.0 | 385k | **轨 A 的 A-2** |
| B-3 | P1-Risk 拆计算 / 星图 / AI / 预警 | 5.5 | 850k | B-1 |
| B-4 | P1-Watchlist 拆拖拽 / Undo / 列表 / 简报 | 3.5 | 650k | B-1 |

**勘察后的两处归属微调**（相比 v1.0 的 14:13 方案）：

1. **`AppChrome.kt` 整体判给 B**。实测该文件 1,756 行中 **Chat 私有件约 1,475 行（84%）、通用件仅 238 行（16%）**。让 B 一次性拆完（连通用件一起落位），A 全程不碰此文件——**消除了两轨唯一的共享文件**。
2. **`RiskSkyChart.kt`（404 行）从 A 移到 B**，因为它服务 `RiskMapPage`，而 Risk 整条 Feature 属 B。

调整后每轨各 13.5 人日，且除「A-2 有权改 B 侧文件的 import 行」这一条受控例外外，**两轨文件域完全不相交**。

**契约要点**：

- 根 `build.gradle.kts`、`settings*.gradle.kts`、`scripts/`、`docs/architecture/`、四端宿主层 → **A 独占**；
- `data/**` 在 B-1 期间 → **B 独占**；A 只新建 `market/data/`；
- 大页单一写者：A 独占 `StockDetailPage.kt` / `MarketPage.kt`，B 独占 `ChatPage.kt` / `RiskMapPage.kt` / `WatchlistPage.kt` / `GlossaryPage.kt` / `AlertCenterPage.kt`；
- **A-2 迁移 `LineIcons` / `CardShell` / `GlassRenderer` / `SourceStampLine` 时，有权修改任何文件的 `import` 行，但只准改 import**（实测这些符号被 `ChatPage.kt`、`chat/**`、`AppChrome.kt` 等 B 侧文件引用）；
- **B-1 拆分 `data/Appearance.kt` 时同理**（`fontSizeScaled` 被 **43 个文件**消费）。

**串行点（两个）**：

| 编号 | 时点 | 内容 |
|---|---|---|
| **S0** | day 1 | A-1 门禁就绪，此后每个提交都跑 `architectureCheck`。B 从 day 0 起并行做 B-1 |
| **S1** | day 2.5 | A-2 完成 → **B-2 才能开工**。B 的 B-1 恰好占满 day 0–2.5，天然对齐无空转 |
| **S2** | day 4 | A-3 完成 → A-4 开工；day 4.5 B-2 完成 → B-3/B-4 开工 |

### 12.5 全量版 vs 精简版

| 指标 | 全量版 | 精简版 | 差异 |
|---|---:|---:|---|
| 人日 | 47.5 | 27.0 | −43% |
| Token 中值 | 8.2M | 4.66M | −43% |
| 并行轨数 | 2 + 串行尾段 | 2 | 去掉尾段依赖 |
| 关键路径 | day 28 | **day 14** | −50% |
| 门禁维度 | +5 | +5 | 相同 |
| Data 层纯度 | +4 | +4 | 相同 |
| Component 层独立性 | +7 | +7 | 相同 |
| 覆盖的低分页 | 全部 7 页 | 4 页（42/45/48/72 分）| 少 Chat(76)、Glossary(51)、Alert(60) |
| 未覆盖 | — | 宿主层可维护性、Gradle 模块化 | 均为延后项，不扣验收分 |

**结论**：如果目标是「用最小代价把四层分离做到可验收」，精简版是更优选择——**砍掉的全是收益最低或为零的部分**。

### 12.6 若日后要加回 P1-Chat

P1-Chat（5 人日 / 1.00M）加回后总量变 32 人日，需重新配平。推荐把 **P1-Watchlist 从轨 B 移到轨 A**：

- 轨 A：P0-C-shared + P0-C-dr + P1-Detail + P1-Market + P1-Watchlist = 16.5 人日
- 轨 B：P0-A + P0-B + P0-C-chat + P1-Risk + P1-Chat = 15.5 人日

注意此方案把 P0-A 与 P0-B 放在同一轨，两轨起始的「门禁 + 装配根」并行度消失，轨 A 在 day 0 就开工而门禁要到 day 1 才就绪——**加回 Chat 时建议维持 P0-A 独立先行一天，不要为了配平牺牲门禁的先行性**。

### 12.7 精简版的验收节拍

| 里程碑 | 判据 |
|---|---|
| **M0 day 4** | `architectureCheck` 上线且基线通过；`data/` 不再 import `PagerScope`；`AppChrome.kt` 归零 |
| **M1 day 9.5** | `StockDetailPage.kt` < 900 行；`detail/**` 对 `page.*` import = 0 |
| **M2 day 14** | `MarketPage.kt` < 700、`RiskMapPage.kt` < 800、`WatchlistPage.kt` < 600；allowlist 降至个位数 |
| **M3 day 14** | 文档 44 §9 的 12 条验收表中，除「宿主层」与「模块化」相关的 2 条外全部勾选 |
