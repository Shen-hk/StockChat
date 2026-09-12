# P0 纵向切片 · Wave 1 下一刀：决策 + QuoteIsland 短规格

> 依据：《39-项目级目标架构与演进蓝图》§9 Wave 1、§10.1 完成定义、§4.2 状态机模板；用户架构评审 P0。
> 背景：Chat 首刀 **Drawer** 已落地（`chat/drawer/state/ChatDrawerCoordinator.kt` + 3 测通过）。
> 本文只定义"下一刀"，不改行为、不动代码。
>
> **状态：已执行（2026-09-12）** —— 用户选定方案 A。QuoteIsland 已落地：
> `chat/island/state/{IslandUiState,IslandScheduler,QuoteIslandCoordinator}.kt`（645 行）+ `commonTest/.../chat/island/QuoteIslandCoordinatorTest.kt`（5 例全过）。
> `IslandGestureMotion/Phase/ISLAND_ANIMATION_*` 自 `page/components/AppChrome` 迁入 state 包；ChatPage 5897→5493 行（-404）。
> `:shared:compileDebugKotlinAndroid` ✓ / `:shared:compileKotlinJs` ✓ / `:shared:testDebugUnitTest` 202 例全过。
> 手顺见 `outputs/island-manual-verify.md`。
> 落地边界与短规格的差异：`CompareInsight`（AI 解读）与实体拖拽字段仍留页面，经 Effect 清理（见 §3.3）。

---

## 0. 结论速览

- Wave 1 剩余三刀：`ComposerFocus+Voice`(§9-1) / `QuoteIsland`(§9-3) / `EntityInteraction`(§9-4)。
- 蓝图明确 **"Drawer 与 Island 不并行改动"** 且顺序为 Drawer → Island → Entity，故**推荐下一刀 = QuoteIsland**。
- 但 Island 与 EntityDrag 在"投放"处强耦合（`addDraggedStockToIsland` / `addDraggedTermToIsland` / `openEntityQuoteIsland` / `isIslandFirstCompareDrop` 四函数同时属于两者）→ 需先冻结二者接缝，避免二次改。
- `ComposerFocus+Voice` 与 Island 无耦合、风险独立，可作为并行的另一选择。

---

## 1. ChatPage 状态字段地图（按 owner 归属）

ChatPage 现有约 40+ 组状态字段。按目标 owner 归类：

| owner（目标） | 字段（现状行号） | 状态 |
|---|---|---|
| `DrawerState` | drawerOpen/Mounted/Presented/Gesture（getter → drawerState）| ✅ 已迁 |
| `WelcomeState` | welcomeStarterRenderKey/Selection/previousLengths | ✅ 已迁 |
| `ChatScrollCoordinator` | chatScrollerRef/chatContentHeight/lastLoggedScrollY/backToTop* | ✅ 已迁 |
| `CardSheetState` | sheet model/mounted/presented/interactive/level | ✅ 已迁 |
| `ComposerAttachment`/`MediaSheet` | composerExpanded/attachments/media* | ✅ 部分 |
| **`IslandState`（本刀）** | islandExpanded/Symbol/GestureMotion/AutoCollapseVersion/GestureStartY/MotionRevision/DetailRouteActive/DetailRouteResetVersion/Mounted/Watchlisted + islandCompareLeft/RightSymbol/Visible + islandTermKey/TermCompareLeft/RightKey/TermCompareVisible + compareExperienceVersion + islandAnimating/islandHandoffMaskActive/islandDetailHandoffDone/islandDragWatchdogRevision | ⬜ 未迁 |
| `EntityInteraction`（下一刀）| pendingLongPressSymbol/draggedEntity/entityDragActive/entityDragX/Y/StartX/Y/entityDragName/entityDropTarget + ambiguousSymbols/Text/Action | ⬜ 未迁 |
| `CompareInsight`（随 Island）| compareCandidateKey/Symbol/compareCard/compareInsightState/Text/Error/PairKey/Version | ⬜ 未迁 |
| `ComposerFocus+Voice` | composerFocus* / voiceState/CancelArmed/ElapsedSec/Amps/MicFill/InputMode/ClockTimer/SessionVersion | ⬜ 未迁 |
| `MessageAction`（浮层）| messageActionMounted/Presented/X/Y/FollowUp/Text/Quote/Version | ⬜ 未迁（可并入 Island 或独立） |

> 观察：Island 相关字段约 **22 个**，是 Chat 剩余最大单块状态；`islandAnimating` / `islandDetailHandoffDone` / `islandHandoffMaskActive` / `islandDragWatchdogRevision` 目前是**非 observable 的裸字段**，正是"无 owner Timer 字段"的典型，必须一并收口。

---

## 2. 候选切片对比

| 维度 | **QuoteIsland**（推荐） | EntityInteraction |
|---|---|---|
| 状态字段 | ~22（含 4 个裸字段） | ~12 |
| 函数 | ~30 | ~14 |
| 定时器 | 16/80/180/360/400/420/520/560/800/2000/60000ms 等 20+ 处 | 16/180/240/900/1500ms 等 ~8 处 |
| 自定义 Port | 无（用页面 setTimeout） | 无 |
| 手势 | 纵向 pan（start/move/end/cancel）+ 看门狗 | 横向拖拽 + 长按 + 投放命中 |
| 外部耦合 | ChatTopNav、Glossary、requestQuote、Routes、entityDropTarget | EntitySpan、RichText、Glossary、Island |
| 接缝 | **投放接缝**：entityDragActive/entityDropTarget 被 Island 读 | 同左，反向 |
| 顺序约束 | 蓝图 §9-3，**在 Entity 之前** | 蓝图 §9-4，依赖 Island 的投放语义 |
| 结论 | **先做**，冻结投放接缝 | 之后做，复用接缝契约 |

**为何 Island 先**：蓝图显式排序 + "Drawer 与 Island 不并行"；Island 的状态与定时器密度最高、裸字段最多，收口收益最大；Entity 的"投放目标"语义依赖 Island 的 drop 契约，先定 Island 可让 Entity 一刀切干净。

---

## 3. 推荐切片：QuoteIsland 短规格

### 3.1 目标
把 Island 的**展开/收敛/自动收起/详情交接/返回复位**状态机迁入 `chat/island/state/`，ChatPage 只保留：`requestQuote` 调用、`Routes` 路由、`Glossary` 查询、`acquireModule` Effect 执行与 DSL 接线。

### 3.2 范围（IN）
- `islandExpanded/Symbol/GestureMotion/Mounted/Watchlisted` 与全部 `island*Version/StartY/Revision/DebugHook` 字段。
- 函数：`toggleIsland`、`handleIslandGesture`、`settleIslandGestureBack`、`settleIslandClosedFromGesture`、`openIslandDetailFromGesture`、`completeIslandMotion`、`resetIslandMotion`、`armIslandDragWatchdog`、`scheduleIslandAutoCollapse`、`invalidateIslandAutoCollapse`、`noteIslandInteraction`、`forceIslandCollapsedForDetailRoute`、`remountIslandCollapsedForDetailRoute`、`scheduleIslandDetailReturnReset`、`cancelIslandDetailRouteReset`。
- 对比 lobby 的**可见性判定**与开关：`isIslandFirstCompareDrop`、`isIslandCompareLobbyVisible`、`isIslandTermCompareLobbyVisible`、`openIslandComparePanel`、`clearIslandCompare`、`clearIslandTermCompare`、`addDraggedStockToIsland`、`addDraggedTermToIsland`（投放落点）。

### 3.3 边界（OUT，留给后续刀）
- **CompareInsight 请求**（AI 拉取/流式）→ 独立 `CompareInsightCoordinator`（可本轮后半或下一刀）。
- **EntityDrag 手势本体**（长按、拖拽帧、命中检测）→ EntityInteraction 刀。
- **UI 渲染**：`ChatTopNav` 的岛渲染保持不动（本刀只把它的 props 回调指向 Coordinator）。

### 3.4 不可变行为（必须逐值保留）
| 项 | 值 | 出处 |
|---|---|---|
| 关闭阈值 deltaY | ≤ -16f | handleIslandGesture end |
| 详情阈值 deltaY | ≥ 20f | 同上 |
| move 上界/下界 | coerceIn(-104f, max(0.42*H,180f)) | 同上 |
| 看门狗 | 800ms，revision 守卫，DRAGGING 才收敛 | armIslandDragWatchdog |
| 自动收起 | 2000ms，多条件门（pageVisible/expanded/!animating/IDLE/非 lobby）| scheduleIslandAutoCollapse |
| 动效解锁 | 400ms → islandAnimating=false | toggleIsland / auto-collapse |
| settle 兜底 | 560ms（≥0.44s morph）| settle*Gesture |
| 详情交接 | 160ms 主触发 + 420ms 兜底，`islandDetailHandoffDone` 幂等 | openIslandDetailFromGesture |
| 返回复位重写帧 | 16/80/180/360ms + 520ms 收尾，`islandDetailRouteResetVersion` 守卫 | scheduleIslandDetailReturnReset |
| remount | 先 unmount → 16ms → mount | remountIslandCollapsedForDetailRoute |

### 3.5 结构（按 §4.2 模板）
```
chat/island/state/
  IslandUiState.kt        # IslandStatePort + Kuikly/Plain 双实现（仿 Drawer）
  IslandIntent.kt         # Toggle / Pan(phase,y) / MotionComplete(key) / Interaction / Watchdog
  IslandEffect.kt         # OPEN_DETAIL(symbol) / REQUEST_QUOTE(symbol) / (无 haptic)
  IslandScheduler.kt      # fun interface + Kuikly( Timer() ) 实现
  QuoteIslandCoordinator.kt
commonTest/.../chat/island/QuoteIslandCoordinatorTest.kt
```
> 与 Drawer 一致：`IslandGestureMotion/Phase` 若仍在 `page/components`，本刀迁入 `chat/island/state`（同 Drawer 对 `DrawerGestureMotion` 的处理）。

### 3.6 验收
1. `:shared:compileDebugKotlinAndroid` ✓；`:shared:testDebugUnitTest` ✓（含新增 fake-scheduler 时序测试 ≥4：展开翻转、上滑关闭、下滑详情、自动收起）。
2. 无新增无 owner 的 Timer/observable；`pageWillDestroy` 只调 `islandCoordinator.onDestroy()`。
3. Android 手测（按 Drawer 手顺模板）：点按展开/收起、上滑关闭、下滑进详情、返回复位、对比 lobby 点击退出、无操作 2s 自动收起。
4. `git diff --check` ✓，独立可回滚 commit。

---

## 4. 迁移映射（示例）

| 现 ChatPage | 去向 |
|---|---|
| `islandGestureMotion`（observable）| `islandState.motion`（经 getter 转发，保反应式，同 Drawer） |
| `toggleIsland()` | `coordinator.toggle()` + Effect 执行 `requestQuote`/`openPage` |
| `handleIslandGesture(s,y)` | `coordinator.onPan(s,y)` |
| `completeIslandMotion(k)` | `coordinator.onMotionComplete(k)` |
| `scheduleIslandAutoCollapse()` | Coordinator 内 `schedule(2000)`，`pageVisible` 作 Port |
| `forceIslandCollapsedForDetailRoute()` / `...ReturnReset()` | Coordinator 的 `onDetailRouteEnter/Exit()`；路由由 Page Effect 执行 |
| `isIslandCompareLobbyVisible()` | Coordinator 纯查询（供 DSL 读取） |

---

## 5. 风险

1. **投放接缝**：`islandDropActive/islandFirstCompareDrop` 读 `entityDragActive/entityDropTarget`（EntityDrag owner）。本刀先把它作为**只读 Port** 传入 Coordinator，接缝契约冻结；EntityDrag 刀再实现该 Port。
2. **反应式**：同 Drawer，getter 转发到 state observable，依赖追踪在 observable delegate 层，不受 Kotlin getter 影响（已由 Drawer/CardSheet/Welcome 验证）。
3. **iOS 线程**：`Timer()` 调度（同 Welcome，已证 context-queue 安全）。
4. **动画 driver**：`islandGestureMotion` 带 `revision`，`completeIslandMotion` 以 phase 门控；迁移必须保 `revision` 递增语义（R5 上一轮注册被下一轮消费）。

---

## 6. 下一步（请确认）
- **A（推荐）**：按本规格执行 QuoteIsland 抽取（一刀一提交）。
- **B**：先做 EntityInteraction（先定投放契约，Island 后做）。
- **C**：先做 ComposerFocus+Voice（与 Island 无耦合，风险独立）。

> 无论选哪条，都遵循：先补自动化边界门禁 warning 版（评审 P2-6）可另起一刀，不与此耦合。
