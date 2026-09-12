# StockDetailPage Wave2 剩余纵切任务提示词集

> 版本：v1.0 ｜ 日期：2026-09-12 ｜ 类型：**提示词交付物**（本文不含任何实现，仅供派发给执行方）
>
> 承接 Wave2 第 1 刀 `DetailDataCoordinator`（已完成并提交，commit `e766a28`，见 `docs/39-项目级目标架构与演进蓝图_v1.0.md` §9 与 §2 审计表）。本文提供 4 组可直接派发的提示词，覆盖 docs/39 §9 为 `StockDetailPage` 定的 Wave2 剩余顺序：
>
> D2 图表交互 → D3 AI 解读 → D4 overlay 仲裁 → D5 DSL 物理归档（收尾）。
>
> 写法与用途对齐 `docs/40-重构任务提示词集_v1.0.md`：每组提示词由「通用前缀（§1）」+「任务正文」构成，两者一起投喂；§2 起每节自包含。**与 40 的区别**：40 是项目级四条并行任务，本文是同一个页面内**必须严格串行**的四条纵切（后一条依赖前一条的边界稳定），且第 1 刀的代码已经落地，可直接作为后续三刀的范式抄写对象。

---

## 0. 使用说明

1. **投喂方式**：把 §1 通用前缀原样作为第一条消息，再把 §2/§3/§4/§5 中对应任务的正文作为第二条消息。单轮 Agent 可拼成一段投喂。
2. **严格串行顺序**：D2 → D3 → D4 → D5，每条必须合并（或至少通过全部验收门禁并独立提交）后才能开始下一条。原因见 §6。
3. **先读已完成的第 1 刀，再动手**：`shared/src/commonMain/kotlin/com/kuikly/stockchat/detail/quote/state/`（`DetailDataState.kt`、`DetailDataScheduler.kt`、`DetailDataEffect.kt`、`DetailDataCoordinator.kt`）+ `shared/src/commonTest/kotlin/com/kuikly/stockchat/detail/quote/DetailDataCoordinatorTest.kt`。D2/D3/D4 的 StatePort / Scheduler / Effect / Coordinator / fake-port 测试写法**逐一照抄这套范式**，只换域名（`quote` → `chart`/`ai`/`overlay`），不要另发明写法。也可参照 Chat 侧的 `chat/island/state/`（`IslandUiState.kt` + `IslandScheduler.kt` + `QuoteIslandCoordinator.kt` 顶部的 Effect/HostPort）。
4. **行号是活的**：本文所有行号以 commit `e766a28`（`StockDetailPage.kt` 4,201 行）为基准。D2 落地后行号会整体偏移，D3 执行前必须重新 grep 一次确认，不要死抄本文行号。
5. **每份提示词都自带验收条款**。执行方交付时必须同时给「代码 + 单测 + 验证命令输出 + 可独立回滚的 commit」。
6. **占位符**：提示词里的 `<...>` 由派单人在下发前替换为具体值。
7. 若执行方是 AI Agent，请把 §1 通用前缀单独复制一份给它，避免它凭空发明第五种 Coordinator 写法。

---

## 1. 通用前缀（D2/D3/D4/D5 共用，原样复制）

```text
你在维护一个 Kotlin Multiplatform + 腾讯 Kuikly 的股票问答 App（仓库根目录即当前工作目录，Windows 环境，Bash/PowerShell 均可跑 Gradle）。
本轮任务的性质是**不改变产品行为的结构优化**：画面、文案、动画时长、手势阈值、AI prompt 语义、降级文案必须与优化前逐帧一致。

【一、项目事实（引用基线，2026-09-12，commit e766a28 之后）】
- StockDetailPage.kt 当前 4,201 行（`shared/src/commonMain/kotlin/com/kuikly/stockchat/page/StockDetailPage.kt`）。
- Wave2 第 1 刀已完成：`DetailDataCoordinator` 接管 Quote/Insight/News 的加载/缓存/降级，位于
  `shared/src/commonMain/kotlin/com/kuikly/stockchat/detail/quote/state/`，测试在
  `shared/src/commonTest/kotlin/com/kuikly/stockchat/detail/quote/DetailDataCoordinatorTest.kt`。
  **这是本文 D2/D3/D4 唯一要照抄的范式**：StatePort（observable 实现 + Plain 测试实现）+
  Scheduler（fun interface + Kuikly Timer 实现）+ Effect（sealed interface，通知下游、不代为执行）+
  Coordinator（唯一 owner，非 observable 镜像字段，私有 setter 同步写镜像与 StatePort，onDestroy
  递增 revision 并 cancel 任务）。
- Chat 页面的四条 Wave1 纵切（`chat/island/state`、`chat/drawer/state`、`chat/entity/state`、
  composer/voice）是同一范式在另一个页面的先例，可交叉参照。
- 目标目录（docs/39 §3）：`feature/detail/{page,component,state,data}`；本项目里 Detail 的域目录落
  在 `com.kuikly.stockchat.detail.<domain>.state`（`domain` 是该纵切的业务名，如 `quote`/`chart`/
  `ai`/`overlay`），与 `com.kuikly.stockchat.chat.<domain>.state` 同构。
- `page/detail/` 包（注意与上面的 `detail/` 顶层包不同！）已有一批更早期、非正式的纯规则/辅助类：
  `AnchorIndex`、`ContextChipStore`、`DetailChartHeader*`、`DetailRules`、`OverlayArbiter`（含
  `DetailOverlay` 枚举）。这些是页面已经在用的现成基础设施，D2/D4 应该复用/收编它们，而不是重新发明。

【二、项目铁律（违反即任务失败）】
R1 响应式读取：observable 只能在 attr{} / event{} / vif{} / vfor{} / vbind{} 闭包内读取；普通 body() 或子 builder 闭包读到的是挂载快照。
R2 动画绑定：attr{} 内先读其它 observable，再设 transform/opacity，最后读动画 driver 并让 animate() 成为该 attr 最后一条状态相关语句。
R3 一个 driver 一个 attr 只能注册一次 animate()；不同时间线用互斥分支或父子视图分离。
R4 新挂载视图首帧不播动画：入场走 mounted → presented 两帧（如 setTimeout(0) 改状态），presented 状态要在目标 attr 内被读。
R5 注册滞后一周期：本周期注册的动画由下一个 driver 变化消费；预状态周期禁止注册 0 时长 reset 动画。
R6/R7：见仓库根 AGENTS.md（attr 条件属性必须无条件全量赋值、vif creator 只构建一次、Scroller 触摸顺序等）。
R8：ohos 编译/验证的坑速查（settings.ohos.gradle.kts、SDK 路径、hdc install 等），本轮若涉及 ohos 验证必须先读。
- 关键回归教训（Chat Wave1 Island 抽取时踩过，抽取时直接复用）：**任何会被 DSL 在 attr{} 闭包内直接调用
  的公开查询方法，必须读 StatePort 的 observable，不能读 Coordinator 内部的非 observable 镜像字段**——
  读镜像不会在 attr 中注册反应式依赖，attr 不会重跑，症状是「功能都对但样式不跟着切」。迁字段前必须
  grep 全部引用，包括其他 Coordinator/Effect 分支里可能残留的旧字段名。
- iOS 线程铁律：后台线程回调不得直写 observable 或直调 native 模块；必须过 setTimeout(0) 或既有 marshal
  链路回核心线程（AI 流式相关的 D3 尤其要注意，详情页 requestAiInsight/requestCircleAi 已有这条注释）。

【三、环境与验证命令（Windows，仓库根目录执行；Bash 工具跑 `./gradlew`，PowerShell 跑 `./gradlew.bat`）】
基线门禁（每条纵切提交前必跑）：
  ./gradlew :shared:testDebugUnitTest :shared:compileKotlinJs
Android 编译：./gradlew :shared:compileDebugKotlinAndroid
ohos（仅当改动可能影响 ohosMain/共享逻辑的 ohos 侧编译时才需要，见 AGENTS.md R8）：
  必须用 Bash 工具执行，不能用 PowerShell 工具（PowerShell 里原生 exe 调用会失败）；
  先 export OHOS_SDK_HOME 与 DEVECO_SDK_HOME，再
  ./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64
git diff --check 必须干净（无尾随空白/混行结束符）。

【四、交付纪律】
1. 每条纵切一个独立、可回滚的 commit；commit message 写清「状态所有者、Effect、已验证的验证命令、剩余风险」。
2. 并发禁令：`StockDetailPage.kt` 同一时刻只能有一个写入 owner；D2/D3/D4/D5 严格串行，不要并行改。
3. 不得为了让测试/编译变绿而修改业务逻辑、不得删除既有测试。
4. 不改变任何 UI 文案、颜色、动画数值、延迟毫秒、手势阈值、AI prompt 语义、降级回退文案。
5. 交付说明必须列出：改了哪些文件、每个文件的职责变化、验证命令输出、未覆盖项与风险。

【五、输出格式要求】
- 先给一段不超过 200 字的「本轮短规格」：目标/范围/边界/不可变行为/验收方式，等确认后再动手（单轮投喂可直接按此规格执行）。
- 结束时按「交付物清单 / 验证证据 / 风险与未覆盖项」三段汇报。
```

---

## 2. 任务 D2 —— DetailChartInteractionCoordinator

### 2.1 任务正文

```text
【任务 D2：StockDetailPage 图表交互域抽取——DetailChartInteractionCoordinator】

一、现状证据（行号基准：commit e766a28，StockDetailPage.kt 4,201 行）
- 状态字段：
  crosshairIndex（228）、selectedKLineIndex（217）、chartViewportCommand/chartViewportRevision（218, 附近）、
  chartScrubLock（约 234）、chartInteractionActive（约 236，非 observable 镜像）、
  selectedSonarIndex（252）、chartBubble/chartBubblePresented（253-254）、
  circleSelecting/circleHintPresented（255, 附近）、prefillQuestion（257）、
  chartFlags（258）、bandRange（259）、selectedSentence（260，注意：本字段同时被 ② 句图联动
  「点句选中」与图表侧共用，D2 与后续「AI 洞察正文句子」渲染有交叉，见下方边界条款）。
- 圈选/声呐/气泡的产生逻辑：约 1460-1540（`onSonarTap`/`onCircleSelect` 一类的私有函数，含
  `selectedSonarIndex = index`、`bandRange = Triple(...)`、`chartBubble = text`、
  `overlayArbiter.request(DetailOverlay.CHART_BUBBLE)`、`chartBubblePresented` 两帧入场）。
- `resetCircleAiStream()`（约 1486）：圈选气泡关闭/换选时中断圈选 AI 流；**注意它同时清 chartBubble
  相关的图表交互态与 circleAi* 相关的 AI 流态两类字段**，D2 抽取时只搬前者，circleAi* 的清理留给 D3
  的 Coordinator（约定见 D3 任务书）。
- `setChartInteractionActive()`（约 2052）：手势期间锁 chartScrubLock、暂停背景动画。
- 图表侧渲染读取：约 630-720（`DetailTimelineChart`/`KLineChart` 调用点，`crosshairIndex`/
  `selectedSonarIndex`/`chartFlags`/`band`/`viewportCommand`/`selectedIndex` 等作为 props 传入，
  回调 `onScrubActive`/`onSelectIndex`/`onCircleSelect` 写回页面字段）。
- `chartViewportCommand` 的写入点（约 2343 附近，`ChartViewportCommand(action, chartViewportRevision)`）：
  周期切换/缩放等触发图表视口指令，`chartViewportRevision` 是这条链路自己的版本号。
- ② 句图联动：`selectedSentence`（点选 AI 洞察正文的句子）与 `bandRange`（联动高亮图表区间）双向绑定，
  逻辑约 1860-1880（`selectSentence`/`clearSentenceSelection` 一类函数）。这段涉及"AI 正文句子 → 图表
  高亮"的映射，边界见下方"不做"条款。

二、目标
新增 `com.kuikly.stockchat.detail.chart.state`（与 `detail.quote.state` 同构）：
1. `DetailChartUiState`（StatePort + observable 实现 + Plain 测试实现）：至少覆盖
   crosshairIndex、selectedKLineIndex、chartViewportCommand、chartScrubLock、selectedSonarIndex、
   chartBubble、chartBubblePresented、circleSelecting、circleHintPresented、prefillQuestion、
   chartFlags、bandRange。`selectedSentence` **不迁入本域**（见下方边界）。
2. `DetailChartScheduler`：手势 settle / 气泡两帧入场 / hint 两帧入场等需要的定时器端口。
3. `DetailChartEffect`：本域产生但仍需页面处理的下游副作用，例如"圈选松手后需要触发 AI 区间解读"
   （对应 D3 的地盘）——照抄第 1 刀 `DetailDataEffect.QuoteApplied` 的思路，本域只发通知，不直接调
   D3 还不存在的 AI Coordinator。命名建议：`DetailChartEffect.CircleSelectionCommitted(lo, hi)`、
   `DetailChartEffect.SonarPointTapped(index)`、`DetailChartEffect.Haptic`（如原逻辑有触感反馈）。
4. `DetailChartInteractionCoordinator`：唯一 owner，接管：
   - crosshair/scrub 读写与 chartScrubLock 的联动（原 `setChartInteractionActive`）；
   - 圈选态（circleSelecting/circleHintPresented 两帧入场）与松手后的端侧统计（声呐点/区间高亮）；
   - 声呐点选中与气泡文案的产生、气泡两帧入场（chartBubblePresented）；
   - chartViewportCommand 的版本号管理（原 chartViewportRevision）；
   - scrub 停顿预填（prefillQuestion）与新闻旗标（chartFlags）的增删。
5. 页面侧：对应字段改为只读 getter 转发（同 D1 的 `quote`/`insight` 写法）；新增
   `handleDetailChartEffect(effect)` 承接下游副作用（气泡内 AI 解读转发给 D3 完成前，暂时保留原地
   实现，等 D3 落地后再把 `requestCircleAi` 迁过去——即 D2 阶段允许 Coordinator 发出效果但页面里
   的处理函数暂不搬家，这是为了不在同一条纵切里同时动两个域）。

三、边界与禁止（本条最重要，务必先确认再动手）
- **`selectedSentence` 不属于本域**：它是"AI 洞察正文 → 图表高亮"的联动状态，语义上更贴近 D3（AI
  解读呈现），但也直接驱动 `bandRange`（图表态）。本刀**不迁移 `selectedSentence`**，保持在页面，
  只让 D2 的 `bandRange` 写入点同时兼容页面对 `selectedSentence` 的联动写法（即页面调用
  `chartCoordinator.setBandRange(...)` 或类似方法，而不是自己直接赋值 `bandRange`）。D3 或后续独立
  任务再决定 `selectedSentence` 最终归属。
- **`circleAiState`/`circleAiText`/`circleAiError`/`circleAiModel`/`circleAiGeneration`/
  `circleAiTypewriter`/`circleAiProvider`（约 264-270）以及 `requestCircleAi()`（约 1549）完全不
  属于本域**，留给 D3（与 `aiRemoteState` 系列合并到同一个 AI 流 Coordinator，理由见 D3 任务书
  「二、目标」第 1 条）。`resetCircleAiStream()` 里清理这些字段的部分本刀不动。
- **`overlayArbiter`/`DetailOverlay` 不属于本域**，属于 D4；D2 只负责在圈选气泡产生时调用
  `overlayArbiter.request(DetailOverlay.CHART_BUBBLE)`（照抄现状调用方式，不改仲裁器本身）。
- 不改：图表视觉、手势阈值（14dp/1.5x 之类）、declaration 中提到的动画时长、声呐点数量上限（≤3）、
  scrub 帧合并策略（如有）。
- 不得把 `DetailTimelineChart`/`KLineChart` 组件本身重写或迁移目录——那是 D5（DSL 物理归档）的范围，
  本刀只搬状态与状态机，组件调用点的 props 来源从"页面字段"换成"页面的只读 getter"即可，组件文件
  不动。

四、验收标准
1. `DetailChartInteractionCoordinator` 有 fake-port/fake-scheduler 单测，覆盖：圈选松手产生正确的
   `bandRange`/声呐点/气泡文案组合并发出对应 Effect；scrub 期间 chartScrubLock 正确联动；气泡/hint
   两帧入场顺序；页面销毁后（onDestroy）不再触发状态写入。
2. 全量 `:shared:testDebugUnitTest`（含既有 239 例）0 失败；`:shared:compileKotlinJs` 通过；
   `git diff --check` 干净。
3. StockDetailPage.kt 里图表交互相关字段全部改为只读 getter，无残留直接赋值（用 grep 自证）。
4. 独立、可回滚的一个 commit。

五、交付物
1. `shared/src/commonMain/kotlin/com/kuikly/stockchat/detail/chart/state/`：
   `DetailChartUiState.kt`、`DetailChartScheduler.kt`、`DetailChartEffect.kt`、
   `DetailChartInteractionCoordinator.kt`。
2. `shared/src/commonTest/kotlin/com/kuikly/stockchat/detail/chart/DetailChartInteractionCoordinatorTest.kt`。
3. `StockDetailPage.kt` 的对应改动（字段 getter 化 + `handleDetailChartEffect`）。
4. 更新 `docs/39-项目级目标架构与演进蓝图_v1.0.md` §9 Wave2 小节，标注 D2 完成情况（照抄第 1 刀
   已写好的格式：落地结果段 + 过程要点段）。
```

---

## 3. 任务 D3 —— DetailAiInsightCoordinator

### 3.1 任务正文

```text
【任务 D3：StockDetailPage AI 解读域抽取——DetailAiInsightCoordinator】

必须在 D2 合并之后才能开始（D3 依赖 D2 发出的 `CircleSelectionCommitted` 一类 Effect 作为圈选 AI
解读的触发入口；行号基准需先按 D2 落地后的文件重新 grep）。

一、现状证据（行号为 D2 之前、commit e766a28 的基准，D3 开工前必须重 grep 一次）
- 主 AI 洞察状态机（页面 doc 注释称"0 本地 / 1 thinking / 2 streaming / 3 done / 4 error"）：
  aiRemoteState/aiRemoteText/aiRemoteError/aiRemoteModel/aiRemoteGeneration/aiRemoteRequested
  （约 168-173）、aiRemoteProvider（约 175）、activeAiTypewriter（约 180）、aiAwaitingFacts（约 183，
  骨架占位态，注意它的 4s 兜底 setTimeout 在页面 created() 里，第 1 刀改动时特意保留在页面未搬，
  D3 应该一并收编）、aiRevealLimit/aiRevealSource/aiRevealVersion（约 163-164, 189，"端侧模板逐字显
  示"节奏，与流式 AI 文本是两套独立机制，务必分清）。
- 圈选区间 AI 解读状态机（与主状态机同构的第二套）：circleAiState/circleAiText/circleAiError/
  circleAiModel/circleAiGeneration/circleAiTypewriter/circleAiProvider（约 264-270）。
- 函数：`startAiReveal()`（约 2122，端侧模板打字机，非 LLM）、`maybeStartAiInsight()`（约 2149，行
  情+洞察就绪后自动触发一次）、`aiActionLabel()`（约 2158）、`toggleAiInsight()`（约 2165，停止/重新
  解读/重试/生成四态文案切换）、`requestAiInsight()`（约 2179，真实 LLM 请求，含 12s 超时兜底、线程
  纪律注释、TypewriterSmoother 用法）、`buildAiInsightPrompt()`（约 2256，端侧事实槽位拼 prompt，
  **prompt 语义与事实槽位算法是产品不可变行为，逐字不能改**）、`buildInsightSummary()`（约 2296，
  端侧模板兜底文案）、`sanitizeAiText()`（约 1894）、`resetCircleAiStream()`（约 1486，仅清理
  circleAi* 部分，D2 已把图表态部分处理掉）、`requestCircleAi(lo, hi)`（约 1549，与 requestAiInsight
  几乎同构的第二套实现）。
- `pageWillDestroy()`（新增于第 1 刀，紧邻 `pageDidDisappear()`）：目前只调用了
  `detailDataCoordinator.onDestroy()`；`pageDidDisappear()` 里现有 aiRemoteProvider?.stop() /
  activeAiTypewriter?.cancel() / aiRemoteGeneration++ 等中断逻辑（原文件约 373-389 附近，D2 之后行
  号会变）需要迁移为 `aiInsightCoordinator.onDisappear()`（不是 onDestroy——页面离开但未销毁时仍要
  中断流式，这是与 `DetailDataCoordinator.onDestroy()` 不同的生命周期钩子，命名不要混用）。
- iOS 线程注释（约 176-179，"网络回调在 Dispatchers.Default 后台线程触发……此前 onDelta 直写
  aiRemoteText 曾导致详情页偶发闪退"）：这是历史事故记录，Coordinator 化时**必须保留同等的
  TypewriterSmoother + setTimeout(0) 回核心线程纪律**，不能图省事直写。

二、目标
新增 `com.kuikly.stockchat.detail.ai.state`：
1. **合并两套同构状态机**：主 AI 洞察（aiRemote*）与圈选区间 AI 解读（circleAi*）目前是两份几乎
   相同的实现（状态枚举同构、请求/超时/流式/typewriter 逻辑同构，唯一区别是 prompt 内容与呈现位置）。
   设计一个参数化的 `AiInsightSession`（或类似的可复用状态机单元，内部持有 generation/typewriter/
   provider/12s 超时守卫），`DetailAiInsightCoordinator` 内部持有两个实例（一个给主洞察、一个给圈选
   解读），而不是复制两份 Coordinator 代码。这样以后新增第三处 AI 流（如果有）只增加一个实例。
   **不强制要求抽成通用跨页组件**（那是 docs/40 P2-B 的范围，"AI 解读流式输出模块 DSL 化"，规模更大，
   涉及 CoreThreadPort 等跨页基座）；本刀只要求"页面内部不再有两份几乎相同的状态机代码"。
2. `DetailAiUiState`（StatePort）：暴露两份会话各自的 state/text/error/model；`aiAwaitingFacts`、
   `aiRevealLimit`/`aiRevealSource` 一并收编（它们是同一"AI 解读呈现"域的状态，不是独立域）。
3. `DetailAiScheduler`：12s 超时、4s 骨架兜底、端侧模板打字机节拍。
4. `DetailAiEffect`：本域几乎不需要向外发效果（AI 流是终端消费者），但如果圈选解读仍需要图表侧配合
   （例如解读完成后气泡要不要保持展开），用 Effect 通知 D2 已落地的 `DetailChartInteractionCoordinator`
   或页面转发，不要反向 import D2 的 Coordinator 类型（保持 state 层之间不互相 import，只经页面转发，
   或经一个只读 HostPort 查询，参照 Chat 侧 `IslandHostPort`/`hasCompareCard()` 的写法）。
5. `DetailAiInsightCoordinator`：唯一 owner，接管 aiRemote* 全部字段与函数、circleAi* 全部字段与
   `requestCircleAi`、以及 `maybeStartAiInsight`（触发时机的判断逻辑：行情 timeline 非空 + insight
   非 loading + 未请求过，这几个判断条件目前跨了 D1 的 quote/insight 状态与本域的 aiRemoteRequested，
   Coordinator 需要通过只读 HostPort 查询 D1 的 `quote.timeline`/`insight.loading`，不要直接
   import `DetailDataCoordinator`，而是让页面把这两个只读查询作为 HostPort 传入，保持 state 层之间
   解耦——同一模式可参照 Chat 的 `IslandHostPort.hasCompareCard()` 转发 CompareInsightCoordinator
   的查询）。
6. 页面侧新增 `pageDidDisappear()` 里调用 `aiInsightCoordinator.onDisappear()`（中断流式但不清理已
   显示文本，语义对齐现状"离开页面即中断进行中的 AI 解读流"）；`pageWillDestroy()` 里追加
   `aiInsightCoordinator.onDestroy()`。

三、边界与禁止
- **prompt 语义、事实槽位算法、AnchorIndex 时间锚点映射、失败回退文案（"未配置 AI API"/"请求超时
  （12 秒无响应）"/"接口未返回有效内容"等）一字不能改**——这些是产品已验证的文案与算法，Coordinator
  化只是搬家，不是重写。
- 不改：TypewriterSmoother 的 33ms 节拍与自适应加速参数（如现状有）、12s 超时阈值、4s 骨架兜底阈值、
  端侧模板逐字显示的速度参数。
- 不得引入新的 HTTP 客户端或流式框架；`TypewriterSmoother`、`AiProvider`（`DeepSeekAiProvider`/
  `MockAiProvider`）继续复用现有实现，不重写。
- 不得把两套状态机合并成"看起来像一套但语义耦合"的单状态机（例如共用一个 aiRemoteGeneration 给两
  路请求）——两路解读必须能独立取消、独立超时、互不影响对方的 generation 计数，只是**实现代码**复用，
  不是**运行时状态**共享。
- 不做跨页面的 AI 流式基座抽取（docs/40 P2-B 的 CoreThreadPort/AiStreamDsl）；那是更大范围的独立
  任务，本刀只在页面内部消除重复。

四、验收标准
1. fake scheduler + fake provider 单测：覆盖主洞察与圈选解读各自的 thinking→streaming→done/error
   四态迁移、12s 超时兜底、generation 失效后旧回调 no-op、`onDisappear`/`onDestroy` 中断流式且不再
   写状态。两路会话必须有独立的测试用例证明互不干扰（例如同时触发两路请求，其中一路超时不影响另一
   路）。
2. `buildAiInsightPrompt()` 与端侧事实槽位算法搬家前后**逐字节对比**（可写一个临时测试固定输入snapshot
   断言输出字符串不变，验收后可保留作回归测试，不必删除）。
3. 全量 `:shared:testDebugUnitTest` 0 失败；`:shared:compileKotlinJs` 通过；`git diff --check` 干净。
4. 独立、可回滚的一个 commit。

五、交付物
1. `shared/src/commonMain/kotlin/com/kuikly/stockchat/detail/ai/state/`：状态机四件套 + 可复用的
   `AiInsightSession`（或等价抽象）。
2. `shared/src/commonTest/kotlin/com/kuikly/stockchat/detail/ai/DetailAiInsightCoordinatorTest.kt`。
3. `StockDetailPage.kt` 的对应改动 + `pageDidDisappear`/`pageWillDestroy` 的钩子调用。
4. `docs/39` §9 Wave2 小节 D3 完成情况更新。
```

---

## 4. 任务 D4 —— DetailOverlayCoordinator

### 4.1 任务正文

```text
【任务 D4：StockDetailPage overlay 仲裁域抽取——DetailOverlayCoordinator】

必须在 D2/D3 合并之后才能开始（overlay 的多个来源——图表气泡属 D2、AI 解读呈现可能影响 overlay 关闭
时机属 D3——先稳定再收口 overlay 本身）。行号基准需在 D2/D3 落地后重新 grep。

一、现状证据（commit e766a28 基准，供预判范围，开工前必须重 grep）
- `page/detail/OverlayArbiter.kt`（已存在，28 行，非本文新增）：`DetailOverlay` 枚举（NONE/
  CHART_BUBBLE/NEWS_SUMMARY/TAPE_PREVIEW/REVISIT/REASON_CHIPS/MORE_MENU）+ `OverlayArbiter` 类
  （`active: DetailOverlay` observable + `request()`/`close()`）。**这是 U1「全页同时最多一个就地
  浮层」的现成仲裁器，D4 不是重新发明它，而是把它从"页面直接持有的裸对象"升级为符合四层规范的
  StatePort，并把散落在页面里的各 overlay 载荷状态收编进同一个 Coordinator。**
- 载荷状态：newsSummary（约 236）、disclosurePeek/disclosurePeekVisible（约 279-280）、
  tapePreview/tapePreviewAnchorX/tapePreviewAnchorY/tapePreviewVersion（约 271-274）、
  reasonChipsVisible（约 276 附近，H1 快捷理由 chips）。
- 函数：新闻摘要展开/收起（约 1792-1799，`toggleNewsSummary`/`showNewsSummary` 一类）、披露/研报
  预览两拍呈现（约 1418-1430，`showDisclosurePeek`/`hideDisclosurePeek`，含 reduceMotion 分支与
  200ms 兜底）、弹幕先览显示与自动消失（约 1810-1830，`showTapePreview`，version 守卫的自动消失
  timer）、`overlayArbiter.close()` 的各调用点（点蒙层/「关闭」/新请求覆盖旧请求，散落约 1096-1370
  之间的 body() DSL 与对应 handler）。
- body() 里 vif 分支：约 573-590（newsSummary）、约 1183-1200（tapePreview）、约 1238-1370
  （disclosurePeek，含 RESEARCH/公告两种 kind 的差异渲染）、约 1090-1160（MORE_MENU/REASON_CHIPS）。
  **这些 DSL 分支本刀不搬家**（DSL 物理归档是 D5），本刀只改这些分支读取的字段来源（从页面字段
  变成 Coordinator 的只读 getter），vif 条件表达式本身不动。

二、目标
1. 把 `OverlayArbiter` 升级/包装为符合四层规范的 `DetailOverlayStatePort`（`active: DetailOverlay`
   + 各载荷字段），提供 observable 实现与 Plain 测试实现（可以让现有 `OverlayArbiter` 类保留、内部
   被新 StatePort 组合持有，避免动它已经工作正常的 `request()`/`close()` 语义；也可以直接把它的字段
   吸收进新 StatePort——两种做法都可以，选更小改动量的一种，但必须在交付说明里写清楚选择理由）。
2. `DetailOverlayScheduler`：披露 peek 两拍入场的 setTimeout、弹幕先览自动消失的 version 守卫计时。
3. `DetailOverlayEffect`：如果某个 overlay 关闭时需要通知图表域清掉高亮（例如新闻旗标预览关闭要不要
   清 `bandRange`），用 Effect 通知页面转发给 D2 的 Coordinator，不直接互相 import（同 D3 的处理
   原则）。
4. `DetailOverlayCoordinator`：唯一 owner，接管 `active` 仲裁 + newsSummary/disclosurePeek/
   tapePreview/reasonChipsVisible 四类载荷的产生、两拍入场、自动消失计时、互斥关闭。

三、边界与禁止
- 不改：U1「开新的先关旧的、点空白全关」的仲裁语义；U4 呼吸预算（同屏至多一组脉冲，仲裁器天然满足
  的部分不动）；披露 peek 与弹幕先览的两拍时长、自动消失时长；disclosurePeek 对 RESEARCH 与非
  RESEARCH 两种 kind 的差异渲染逻辑（渲染逻辑本身不搬，只搬状态来源）。
- `DetailOverlay` 枚举值不得增删或改名（跨多处 vif 条件引用，改名等于隐性重构渲染分支）。
- 不做 D5 的 DSL 物理归档（body() 里的 vif 分支/组件结构本刀不动位置）。
- 不改变声呐/圈选气泡的产生逻辑（D2 已完成，D4 只消费 D2 发出的"需要展示 CHART_BUBBLE"这一件事，
  不重新实现）。

四、验收标准
1. fake-scheduler 单测：覆盖仲裁互斥（开新关旧）、点空白全关、披露 peek 两拍入场、弹幕先览的 version
   守卫自动消失（含"消失前又发生新交互，旧计时器不应误关新内容"的时序）、页面销毁后计时器全部取消。
2. 全量 `:shared:testDebugUnitTest` 0 失败；`:shared:compileKotlinJs` 通过；`git diff --check` 干净。
3. 独立、可回滚的一个 commit。

五、交付物
1. `shared/src/commonMain/kotlin/com/kuikly/stockchat/detail/overlay/state/` 四件套（或对
   `page/detail/OverlayArbiter.kt` 的就地升级，视 §二.1 的选择而定，两种情况都要在此列出实际路径）。
2. `shared/src/commonTest/kotlin/com/kuikly/stockchat/detail/overlay/DetailOverlayCoordinatorTest.kt`。
3. `StockDetailPage.kt` 的对应改动。
4. `docs/39` §9 Wave2 小节 D4 完成情况更新。
```

---

## 5. 任务 D5 —— DSL 物理归档（Wave2 收尾）

### 5.1 任务正文

```text
【任务 D5：StockDetailPage 收尾——Hero/Chart/Insight/News 归入 feature component】

必须在 D2/D3/D4 全部合并之后才能开始。这是 docs/39 §9 Wave2 顺序里的第 5 步，也是整条 Wave2 的收口，
写法对齐 docs/42-ChatPage最终收口实施规格_v1.0.md 的「DSL 物理归档」批次（Chat 侧已完成的同类工作，
commit 序列见该文档「2026-09-12 已完成批次」一节，可直接抄流程，不必抄内容）。

一、现状证据
- `StockDetailPage.body()`（约 1,020 行的单个 ViewBuilder，D2/D3/D4 落地后行数可能略降但仍是全页面
  最大的单体函数）目前包含：顶部报价 Hero 行、新闻弹幕/摘要条、主图表卡（分时+K线三周期）、二级指标
  胶囊、AI 一行归因、AI 洞察块、公司简介/数据 tab、公告研报卡、圈选气泡/声呐/披露 peek 等浮层。
  D2/D3/D4 完成后，这些 UI 区块读取的状态已经全部来自各 Coordinator 的只读 getter，是"DSL 物理归档"
  的前提条件（Chat 侧的经验：状态机先归位，组件搬家才安全，反过来做风险更高）。

二、目标
1. 按现有视觉区块边界（不是按状态域边界）把 body() 拆成若干 `feature/detail/component` 下的具名
   DSL 组件（如 `DetailHeroSection`、`DetailNewsTicker`、`DetailChartCard`、`DetailAiInsightBlock`、
   `DetailCompanyInfoSection`、`DetailDisclosureBoard`），每个组件只接受 Props + Actions（只读 lambda
   与回调），不持有 Repository/Store/Provider/BridgeModule，不做数据请求或生命周期决策（对齐
   docs/39 §4 四层职责表里 component 的"明确禁止"栏）。
2. `StockDetailPage.body()` 收敛为纯装配：调用上述组件 + 传入各 Coordinator 的只读 getter/回调，不
   再直接内联大段 vif/attr。
3. 命名与放置遵循 docs/39 §3 目标目录：`com.kuikly.stockchat.detail.<domain>.component`（例如图表相
   关组件放 `detail.chart.component`，若某组件跨多个域可放 `detail.page.component`，不强制每个组件
   都能唯一归类到某个 D2/D3/D4 的域，视觉边界优先）。

三、边界与禁止
- 零行为变更：本刀是纯粹的物理搬家（对齐 docs/42 的"DSL 物理归档：从 ChatPage 迁入 feature
  component；无行为改动"），不得在搬家过程中"顺手"修一个看起来奇怪的地方——发现的任何疑似 bug 记录
  下来，留给独立任务，不要混进这个 commit。
- 不得把多个域的组件强行拆成一个大文件"图方便"；也不得为了"看起来模块化"过度拆碎到每个组件只有
  几行——按现状视觉区块的自然边界拆，边界不清楚的留一个 TODO 注释说明，不要臆断。
- DetailTimelineChart/KLineChart 等已经独立成文件的图表组件本身不改动，只改 StockDetailPage 里调用
  它们的那一层容器归档位置。

四、验收标准
1. `StockDetailPage.kt` 行数给出前后对比（预期显著下降，具体数字不设硬性下限，"Page 行数只是信号不
   是 KPI"，见 docs/39 §10.3）。
2. 每个新组件文件都能说明"接受哪些 Props/Actions、不持有什么"，可用 docs/39 §6 共享组件准入表的口径
   自查（本刀的组件大概率仍是 feature 内部组件，不必满足共享准入条件，除非确实有跨页复用证据）。
3. 全量 `:shared:testDebugUnitTest` 0 失败；`:shared:compileKotlinJs` 通过；`git diff --check` 干净。
4. 每个视觉区块的搬家可以是独立 commit（比 D2/D3/D4 的粒度更细也可以），但必须都在 D5 这一个批次
   内完成并连续提交，不要和 D2/D3/D4 的状态机改动混在同一批 commit 里。

五、交付物
1. `feature/detail/component`（或按上文命名放在各域 component 子目录）下的具名组件文件。
2. 精简后的 `StockDetailPage.body()`。
3. `docs/39` §9 Wave2 小节标注整条 Wave2 完成；同时更新 §2 审计表格 StockDetailPage 行的最终行数与
   判断（届时 Wave2 应整体标注为"已完成"，格式参照现有 ChatPage 那一行"Wave 1 已完成"的写法）。
```

---

## 6. 依赖关系、串行顺序与派单模板

```text
D1（已完成，DetailDataCoordinator）
      │
      ▼
D2 图表交互（DetailChartInteractionCoordinator）
      │  发出 CircleSelectionCommitted 等 Effect，D3 消费
      ▼
D3 AI 解读（DetailAiInsightCoordinator，合并 aiRemote*/circleAi* 两套状态机）
      │  AI 解读呈现的状态稳定后，overlay 的关闭时机才好收口
      ▼
D4 overlay 仲裁（DetailOverlayCoordinator，收编既有 OverlayArbiter）
      │  状态机全部归位后才能安全搬 UI
      ▼
D5 DSL 物理归档（Hero/Chart/Insight/News → feature component，Wave2 收尾）
```

**为什么必须严格串行（而不是像 docs/40 的 P1-P4 那样部分并行）**：D2/D3/D4/D5 全部改同一个文件
`StockDetailPage.kt`，且后一条纵切依赖前一条把某些字段/函数迁出页面后留下的稳定接口（例如 D3 要用
D2 发出的 Effect，D4 的 overlay 关闭时机要看 D3 的呈现状态）。这与 docs/40 里 P1 之后 P2/P3/P4 可以
分模块并行是不同的情况——本文四条纵切共享同一个写入热点，并发只会导致合并冲突与语义踩踏。

**并发禁令**：`StockDetailPage.kt`、`page/detail/OverlayArbiter.kt`（D4 触及前不要顺手改）同一时刻
只能有一个 owner 写。

**派单模板（每条纵切派发时都要带上）**：
- 任务编号（D2/D3/D4/D5）
- 本条纵切在 `docs/39` §9 Wave2 里对应第几步、依赖哪条纵切已合并
- 本文对应小节的现状证据（提醒执行方开工前重新 grep 行号）
- 验证命令 + 完成定义（代码 + 单测 + 命令输出 + 独立 commit + docs/39 进度更新，五者齐全）

---

## 7. 假设与待确认事项

1. **域目录命名**沿用 D1 已落地的 `com.kuikly.stockchat.detail.<domain>.state` 风格（`domain` 用
   `chart`/`ai`/`overlay`）。若后续 Gradle 模块化（Wave5）阶段需要改成别的包结构，是迁移期的独立任务，
   不影响本文四条纵切现在的落点。
2. **D3 是否需要同时完成 docs/40 P2-B（AI 解读跨页 DSL 化/CoreThreadPort）**：本文假设**不需要**——
   D3 只消除 StockDetailPage 内部两套 AI 状态机的重复，不做跨 Chat/Detail 两个页面的统一抽象，那是
   规模更大的独立任务（docs/40 §3.2）。如果实际要求是"顺便把两个页面的 AI 流也统一"，请先说明，D3
   任务书需要重写并追加与 ChatPage 的并发禁令协调条款。
3. **D4 是否要保留 `page/detail/OverlayArbiter.kt` 这个文件路径不变**：本文允许执行方二选一（就地
   升级 or 吸收进新包），因为现有类已经工作正常、改动量最小化优先于"包结构好看"。若你有强烈的目录
   规范要求（例如必须物理搬进 `detail/overlay/`），请明确指出。
4. **D5 的组件粒度**按"现有视觉区块自然边界"而非"每个状态域一个组件"划分，因为一个视觉区块（如主
   图表卡）可能同时读取 D1 的 quote 与 D2 的 crosshair 状态，拆碎到状态域粒度会导致组件数量爆炸且
   Props 传递链更长。如果你更偏好严格按状态域拆组件，请在派发 D5 前说明。
5. 本文四条纵切完成后，`StockDetailPage` 即达到与 `ChatPage`（docs/42）同等的"页面只剩装配与生命
   周期"收口状态，届时应比照 docs/42 的格式为 Detail 单独写一份《StockDetailPage 最终收口实施规格》，
   记录已完成批次与不变行为——建议作为 D5 交付物的附带产出，而不是再开一个新文档编号。
```
