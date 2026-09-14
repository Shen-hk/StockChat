# Kuikly reactive animation rules

Apply these rules to every Kuikly animation or interaction change in this repository.

## R1 — reactive reads only

Read `observable` state only inside `attr {}`, `event {}`, `vif {}`, `vfor {}`, or `vbind {}` closures. A normal `body()` or child-builder closure (including a `Scroller` child block) reads only the initial snapshot and does not establish a reactive dependency.

Use `vif` / `vbind` when state changes the presence or quantity of views.

## R2 — bind animations to the correct state

Within an `attr {}` block, read the animation-driving observable after all other observable reads, set the target `transform` / `opacity` values, then make `animate(...)` the final statement. `animate()` uses the most recently read observable key in that `attr`, not its value argument.

Cache values such as `page.theme` outside `attr {}`. Do not read theme-like observables inside the block: they can replace the intended animation key.

## R3 — one animation registration per driver

Call `animate()` once per driver observable in a given `attr {}` block. For different timelines, use mutually exclusive branches or separate parent/child views.

## R4 — animate newly mounted views in two frames

`vif` / `vbind` mounted views do not animate on their first frame. For entrance effects use a two-phase `mounted -> presented` state change (for example, a `setTimeout(0)` state update) and keep the presented state observable inside the target `attr`.

## R5 — registration lags one change cycle

`DeclarativeBaseView.attr` runs each change cycle as: `beginApplyAttrProperty()` (consumes `AnimationState`: next→cur, hands the **previous** cycle's registration to native) → re-run attr closure (this cycle's `animate()` only queues into `nextAnimations`) → `endApplyAttrProperty()`. The animation applied to a change is the one registered in the **previous** cycle of the same driver key.

Consequences:

- Register, in every cycle (including the pre-state/mount cycle), the animation you want the **next** driver change to play. `CardSheet` and the welcome starter cards register the entrance `easeOut` unconditionally — the flip cycle then consumes exactly that.
- Direction mapping for two-state (open/close) animations: the registration made in the **closed** state is what the **open** transition plays, and the open-state registration plays on close (`AnimationManager.willBeginAnimation` copies the previous cycle's `nextAnimations` into `curAnimations` at `beginApply`; verified against core 2.25.0 sources, 2026-09-13). The old ChatDrawer/ChatPage drawer comments had the two directions swapped.
- Never register a zero-duration "reset" animation (e.g. `Animation.linear(0f)`) in the pre-state cycle: the presentation cycle will consume it and the entrance degrades to an instant jump. Same-value observable writes do not notify (`ObservableProperties.setValue` early-returns), so an unwanted stale registration cannot be flushed by re-writing the same value.
- Keep a version-guarded fallback timer for entrance sequences: if the `ref` → `setTimeout` chain loses a link, the view must not stay stuck at `opacity 0` (see `ChatPage.scheduleWelcomeEntranceSafety`).
- Never reset an animation-driver observable in the same batch as a layout/data change that also clears the corresponding transforms. Views holding a live registration keyed on that observable will consume the reset (N→0) and animate the transform clear — layout snaps instantly while the offset replays as a visible second move (2026-09-09 watchlist drag-drop flash; fix: leave `dragFrom`/`dragTo` stale in `WatchlistPage.cancelDragSession`, all reads gate on `dragSymbol`, `beginDragLift` re-seeds).

## Review checklist

- Is every interaction target inside a reactive closure and proven to receive its event inside scrolling containers?
- Is each `animate()` the last observable-dependent operation in its `attr` block?
- Is the target state read in the same `attr` block and not only in an outer builder closure?
- Does the animation registered in the current cycle equal the one the next driver change should play (R5)?
- Are reduced-motion behavior and Android/H5/iOS runtime interactions verified after the change?

When debugging a silent Kuikly animation, inspect reactive dependency registration and event hit testing before changing timing or easing.

## R6 — attr/theme accessor pitfalls (consolidated 2026-09-09, appearance/settings phase)

- **Deep builder closures: use explicit `page.theme`.** Inside nested `vif`/component closures the page-level property can fail to resolve as an implicit receiver (`'val theme' cannot be called in this context`). Batch rewrites that introduce `theme.` references must compile right after; fix stragglers to `page.theme.<x>`.
- **Data-class equality ≠ identity of intent.** Resolver functions that `copy()` a theme (e.g. `appTheme()` with font-scale applied) produce values that never equal the base palette singleton. Compare a discriminating field (like `page` color) or a key tuple, never the whole data class.
- **Early pager lifecycle: native bridge may not be attached.** `created()`-time `toNative` calls can be silently dropped. Gate first bridge syncs on `viewDidLoad`, and dedupe retries with a "last synced value" guard so dropped early calls can't suppress the first real one.
- **Edits can be silently swallowed (EBUSY/IDE lock).** After editing files also open in the IDE, grep the expected marker; on EBUSY retry the same edit. Same for Gradle `fileHashes.lock` access-denied — `gradlew --stop` then retry.

## R7 — panel/list re-render (consolidated 2026-09-10, composer @// panels)

- **`vif` creator builds ONCE per activation.** `ConditionView.createSubViewIfNeed` guards with `didCreated`: content is created when the condition flips false→true and destroyed on true→false — it does NOT re-run while the condition stays true. A `version >= 0`-style condition therefore never refreshes content. Any state read in a plain builder closure inside `vif` is frozen at activation (2026-09-10: @ panel stuck at "没有可推荐的标的"; param panel frozen while typing).
- **Reactive reads must sit in `attr`/`vif` conditions/`vfor`**, never in the enclosing builder closure (R1, but note the Scroller-child case too). Branch switching → `vif` conditions that actually flip; per-row data → `vfor` over the `ObservableList` (it processes collection operations incrementally and re-runs item creators); dynamic text → read the observable inside the `Text` `attr` closure.
- **指令 creator 内的组件扩展必须挂到当前 receiver。** 在 `vif` / `vbind` 里写 `page.SomeComponent(...)` 会把 `addChild` 发给页面根节点而非指令节点；初始化后旁挂的节点不会由指令同步进 DOM，也不会进入 Scroller 的内容测量。表现可能是浮层条件已翻转却不显示，或列表卡片可见但 Scroller 认为内容高度不足。应写 `SomeComponent(...)` / `this.SomeComponent(...)`，页面实例只用于传参数和回调（2026-09-13 自选列表不可滚、长按菜单不显示）。
- **Full-panel rebuild trick:** for content derived from non-observable composites (e.g. `args = f(viewModel.inputText, mentionEntities)`), keep a single-element `ObservableList<Int>` render key and bump it (`clear()+add()`) at every mutation site; wrap the panel content in `vfor({ key }) { … }` so each bump rebuilds the frame. All bump sites must be guarded by the same condition as the panel's mount, and the vfor item must create exactly one child (`Scroller` on the LoopDirectivesView receiver, not the outer container). Missing the closing brace of that vfor lambda silently demotes later `private fun`s into local functions → cascade of "Unresolved reference" at call sites above.
- **Gradle may compile a torn mid-write file.** With parallel sessions editing the same file, a compile can report dozens of bogus "Unresolved reference" for methods that grep shows exist. Check file mtime, wait for stability, recompile before diagnosing.

## R8 — ohos build & verification (consolidated 2026-09-11)

- **ohos Kotlin tasks need their own settings file.** `linkDebugSharedOhosArm64` (and the other ohos targets) are only registered by `settings.ohos.gradle.kts`: run `./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64`. Under the default settings that task does not exist.
- **The ohos compiler resolves the SDK from a hard-coded DevEco path.** When DevEco lives elsewhere, the link dies with `OHOS SDK is not found in 'C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony'` — that is a plugin default, not the install location. Inject it in the calling shell: `export OHOS_SDK_HOME="C:/Users/shenhk/DevEco Studio/sdk/default/openharmony"` plus `DEVECO_SDK_HOME="C:/Users/shenhk/DevEco Studio"`. `runOhosApp.ps1` sets these for the HAP build only; a plain Gradle run from your shell does not inherit them.
- **HAP page images come from `ohosApp/entry/src/main/resources/rawfile/`**, mirrored from `shared/src/commonMain/assets/` by `runOhosApp.ps1`. `ImageUri.pageAssets` resolves against the HAP rawfile bundle on HarmonyOS, and the Android assets source-set declaration does not feed the separate HAP build (symptom: every vendor logo missing). The mirror directory is generated — gitignored, never hand-edited.
- **`ohosApp/build-profile.json5` is machine-local.** DevEco auto-signing writes local certificate paths and encrypted passwords into it, so it is gitignored and marked `git update-index --skip-worktree` (undo with `--no-skip-worktree`; a fresh clone must reconfigure signing).
- **Verification order that actually gates a change:** `:shared:compileDebugKotlinAndroid` + `:shared:testDebugUnitTest` cover commonMain; only the ohos link covers `ohosArm64Main`. Green Android does not imply green ohos.
- **Run the whole ohos pipeline from the Bash tool, not the PowerShell tool** (2026-09-12). In the PowerShell tool session, native exe invocation fails with `无法在管道中间运行文档` (even `where.exe`) and `Start-Process` silently no-ops — clang++ preflight, gradle, hvigor, hdc all die there. From Bash, `./gradlew.bat`, `node hvigor.js`, and `hdc.exe` all work; export `OHOS_SDK_HOME`/`DEVECO_SDK_HOME` first (hvigor also needs them and its daemon caches env — `hvigor --stop-daemon` after changing). Do not invoke `powershell.exe` from Bash — the security layer rejects it, so `runOhosApp.ps1` as a whole cannot be driven from here; execute its steps individually instead.
- **`hdc install` wants a bare filename.** Run from the HAP output directory (`ohosApp/entry/build/default/outputs/default`) with `hdc -t <id> install -r entry-default-signed.hap`; a Windows absolute path gets mangled by MSYS/path conversion and hdc treats it as a missing relative file (symptom: `Error opening file ... path:d:\...\D://Project//...`).

### R8a — 换一台机器跑鸿蒙：三个被 gitignore 吞掉的前置（re-diagnosed 2026-09-14）

`runOhosApp.ps1` 的手顺只有在**原作者那台机器**上成立。新机器上按顺序会撞下面五件事，前四件已修复，第五件是环境性阻塞。

1. **脚本硬编码了 DevEco 安装根目录。** 原为 `C:\Users\shenhk\DevEco Studio`；本机在 `D:\dev\DevEco Studio`，第一步 `Test-Path` 校验即 throw。改为经 `DEVECO_SDK_HOME` → 候选目录列表解析；`clang++.exe` 也用通配发现，不要钉死 `llvm-<版本>` 目录名。
2. **`clang++` 预检在全新机器上是假阳性。** `~/.konan/dependencies` 为空是**首次 link 前的正常状态**（LLVM 会在 `:shared:linkDebugSharedOhosArm64` 时下载，本机实测 1.3G、约 12 分钟），不是被应用控制策略拦截。预检必须 warn，不能 throw。
3. **`ohosApp/entry/libs/arm64-v8a/` 的预编译库从未入库**（`.gitignore` 第 18 行全局 `*.so`）。缺 `libpbcurlwrapper.so` / `libc++_shared.so` / `libopenssl.so` 时，Kotlin/Native link 直接死 —— `shared/build.ohos.gradle.kts` 有 `linkerOpts("-lpbcurlwrapper")`，`cpp/CMakeLists.txt` 也 `add_library(pbcurlwrapper SHARED IMPORTED)`。来源：`Tencent-TDS/KuiklyBase-components` → `NetworkKMM/ohosApp/entry/libs/arm64-v8a/`。**国内 raw.githubusercontent 不可达**，用 `gh api <path> -H "Accept: application/vnd.github.raw"`，并且**必须校验 git blob sha**：截断的下载仍然以合法 ELF 头开头（本次 `libpbcurlwrapper.so` 首次只取到 36785/991600 字节）。
4. **`ohpm` 依赖是半装状态。** `oh_modules/.ohpm/` 下有包目录但链接目录为空、无 `lock.json5`，`ohpm install --all` 报 `ENOENT ... .ohpm\lock.json5`。把 `ohosApp/oh_modules` 与 `ohosApp/entry/oh_modules` 移开重装（均为 gitignore 的可再生缓存）。`entry/oh_modules/@kuikly-open/render/libs/arm64-v8a/libkuikly.so` 正是 `CMakeLists.txt` 要的。
5. **hvigor 的依赖自举会在 `.npmrc.lock` 上中止（本机阻塞，未解）。** 入口必须用 `tools/hvigor/bin/hvigorw.bat`（配 `NODE_HOME=<deveco>/tools/node` 且该目录进 `PATH`）；裸 `node .../hvigor/hvigor/bin/hvigor.js` 报 `Cannot find module '@ohos/hvigor-ohos-plugin'`。用 hvigorw 后必现：`Installing dependencies...` → `[safe-delete] 操作失败: ...\.hvigor\project_caches\<hash>\workspace\.npmrc.lock: Error during a \`trash\` operation: Unknown { description: "Some operations were aborted" }`。**不是缺包**（包已装好，hvigor 仍每轮重入该分支）；`HVIGOR_DEPENDENCY_USE_NPM=true` 无效；关沙箱重试同样复现；`C:\$Recycle.Bin` 存在且 NTFS、BitBucket 配置正常，故疑为 hvigor 的 trash 式 safe-delete 在此调用上下文里不可用。**绕过方式：用 DevEco Studio 构建（Build > Build Hap(s)）或 DevEco 自带终端**。hvigor 之前的环节（Kotlin/Native link → `libshared.so`）在 Bash 里是通的。

另外两条：

- **`ohosApp/build-profile.json5` 实际已被跟踪**（`git ls-files -v` 返回 `H`，不是 `S`）。`.gitignore` 第 87 行对它无效，HEAD 里就存着原作者的绝对证书路径与加密口令（`C:\Users\shenhk\.ohos\config\*.p12|.cer|.p7b`）。**任何非原作者的克隆都无法签名**，必须由 DevEco 自动签名重写该文件（要登录华为账号，只能人来做）。签名 material 的 7 个字段全必填。
- **MSYS 路径转换也会打崩 `node` 的参数列表**，不只是 hdc。传 `/d/dev/DevEco Studio/.../hvigor.js` 会让 Node 去解析 `D:\d\dev\DevEco Studio\...` 并 `MODULE_NOT_FOUND`；给 node 可执行程序传参一律用 Windows 风格（`D:/dev/...`）。`hvigorw` 非交互驱动时还要加 `--no-daemon`，否则守护进程接手后调用方约 7 秒就返回、只打印 `Starting hvigor daemon.`，不产出 HAP。仓库内也没有 `ohosApp/hvigor/outputs/sync/`，说明该工程从未被 DevEco 同步过。

---

## R9 — 本机工具链陷阱（consolidated 2026-09-12，docs/46 轨 A 施工）

- **BSD grep 不支持 `\b` 与 `\|`，而且失败是静默的。** 本机（macOS `/usr/bin/grep`）上
  `grep -E '\bFoo\b'`、`grep 'a\|b'` 都不按预期工作，典型表现是**返回 0 命中却不报错**。
  用 `\b` 做的「某符号是否被引用」判断会得到**假阴性**，据此做的可见性判断/搬迁决策会直接编不过。
  2026-09-12 实例：据此判定「`StockDetailPage` 不调用任何 private helper」，实际调用了 2 个
  （`SecondaryMetricRow`、`DetailBottomBar`），编译才暴露。
  → 词边界用 `grep -w`；多选分支用 `grep -E 'a|b'`；拿不准就用 perl。
- **BWK awk（`/usr/bin/awk`，`awk version 20200816`）把正则字面量当函数实参时，求值为布尔 0/1。**
  `function cnt(s, pat, t) { gsub(pat, "&", t) }` 配 `cnt($0, /setTimeout[(]/)` 会让 `pat`
  变成 `0`/`1`，计数彻底错乱且不报任何错。→ 正则内联进 `gsub`，或把模式当字符串传参。
- **本机进程启动极贵。** 实测 39 次 `grep -c` 要 3.8s，一次 `find` 要 1.4s。
  批量扫描脚本必须把逻辑合并进**单次 awk**，不要写「逐文件 grep」的循环；
  否则 `architectureCheck` 跑不进 5s 预算（实测从 16s 降到 1.9s 就是靠这个）。
- **Kotlin 增量编译认内容哈希，`touch` 不会触发重编译。** 想重看编译警告要
  `./gradlew <task> --rerun-tasks`。另外本项目编译**不输出** unused import 警告，
  清理 import 用 `tools/find_unused_imports.pl`（保守检测：注释里出现过就保留；`tools/` 已 gitignore）。

## R10 — vfor / vif creator 结构硬约束（consolidated 2026-09-12，「浏览全部 N 个概念」闪退事故）

- **`vfor` 的 creator 闭包必须生成「恰好一个」非指令子节点。** Kuikly core
  `directives/LoopDirectivesView.kt:156-172` 的 `invokeItemCreator` 会在调用前后比对
  `childrenSize()`：增量 `!= 1` 立即 `throwRuntimeError("vfor creator闭包内必须需要且仅一个孩子节点的生成")`；
  紧接着还检查 `child.isVirtualView()`（vif/vfor/vbind），命中则
  `throwRuntimeError("vfor creator闭包内子孩子必须为非条件指令，如vif , vfor")`。
  **两者都是硬抛，端上表现是「进到这一屏即闪退」，且 Android / iOS / H5 / 鸿蒙全端一致**
  ——因为它在 commonMain 的框架层，与平台无关。
- **正确写法：两个兄弟节点要包一层无色容器。**
  ```kotlin
  vfor({ page.rows }) { row ->
      View {                       // ← creator 的唯一子节点
          attr { alignSelfStretch() }
          CardShell(...)           // ← 卡身
          vif({ page.expandedKey == key }) { ... }   // ← 相关术语
      }
  }
  ```
  反面写法（2026-09-12 事故现场）：`vfor { CardShell(...); vif(...) }` —— 同层两条语句，
  增量 2，必崩。`vif` 自身的 creator **没有**这个计数检查，因此 `vif` 里放几条都行；
  约束只作用在 `vfor` 的**直接** creator 上。
- **`when` / `if` 的分支是互斥的，每支各算一个节点**（各支内部仍须恰好 1 个），
  所以 `vfor { when (row) { is Header -> Title(...); is Item -> { View { ... } } } }` 合法。
- **检测手法**：只需找「`vfor` creator 体内出现 `vif`」的位置人工核对最浅缩进层语句数
  （2026-09-12 全仓扫描仅 3 处：`GlobalSearchPage:149`、`GlossaryPage:1052`、`WatchlistPage:326`，
  均为单节点合法）。基于缩进/括号的通用静态审计**误报率极高**（嵌套容器的子节点无法与
  creator 骨架层区分），不要指望它；**真正可靠的判据是运行到该页**——用
  `KR_ROOT_PAGE=<页>` 冒烟钩子直接落到目标页，看 `missing shadow`/`RuntimeException` 计数与进程存活。
- **回归防线**：新增/重构任何 `vfor` 行渲染时，先看 creator 顶层是不是只有一条视图语句；
  崩了优先怀疑这条约束，而不是数据或主题。

---

## R10 — 画布批处理是**平台能力**，不是「开关」（consolidated 2026-09-12）

- **症状**：鸿蒙上所有 `Canvas` 自绘内容**整块空白** —— 线性图标、SVG 图标、
  语音波形、composer 渐变描边全部不显示，Android 上却完全正常。
- **根因**：`CanvasContext.batchDraw = true` 会把**整帧**绘制命令缓冲起来，只向
  native 发一条 `batchDraw`（JSON 命令数组）。鸿蒙锁定的 `@kuikly-open/render@2.25.0`
  **没有实现这条命令**，于是整帧被静默丢弃（不报错、不崩溃）。
  Kuikly 上游 `main` 分支持久化前没有它，升级 har 才会带上。
- **判定方式（可复现，不必跑真机）**：
  ```bash
  python - <<'PY'
  d = open("ohosApp/oh_modules/.ohpm/@kuikly-open+render@<v>/oh_modules/@kuikly-open/render/libs/arm64-v8a/libkuikly.so","rb").read()
  for s in [b'batchDraw', b'beginPath', b'clipPathIntersect', b'createRadialGradient', b'measureText', b'setLineDash']:
      print(s.decode(), d.count(s))
  PY
  ```
  Android 侧对照物：`core-render-android-<v>-api.jar` 里 `KRCanvasView.class` 含 `batchDraw` 字符串。
- **本仓口径**：批处理是否开启统一走 `PlatformProfile.canvasBatchDrawSupported`
  （`com.kuikly.stockchat.common`，expect/actual）。Android/iOS/H5 = `true`（保持既有行为），
  **鸿蒙 = `false`**（逐条下发是等价回退：native 支持全部单条命令，只是 bridge 调用次数多些）。
  **新增任何 `Canvas` 都不要写死 `batchDraw = true`。**
- **同源命令缺口**（鸿蒙上同样是静默 no-op，用到先确认）：`clipPathIntersect`、
  `clipPathDifference`、`createRadialGradient`、`measureText`、`setLineDash`。
- **教训**：多端共用的画布代码里，任何「性能优化型 API」都必须先确认**每一端 native
  都实现了**，否则它就是一个静默的渲染开关。`:shared:compileDebugKotlinAndroid` 绿
  不代表鸿蒙绿 —— 这类问题只有看鸿蒙渲染层产物或真机才能发现。

---

# Vibe coding workflow conventions

Conventions for AI-assisted development in this repository (multiple AI sessions may work on the same codebase). Confirmed direction from mentor feedback, 2026-09-07.

## Light constraints + periodic cleanup

- Keep hard rules minimal (this file). Do not try to enumerate every constraint up front — it is impossible to be exhaustive at prototype stage.
- After each development phase, run a consolidation pass: collect the pitfalls actually hit and fold them back into this rules file (or the relevant docs). Rules grow from real errors, not speculation.

## Spec-assisted execution for long requirements

- Do not raw-vibe long requirements. Write a short spec first (goal, scope, boundaries, acceptance), give the AI a simple review of it, then let it run autonomously.

## Session hygiene

- Start new AI sessions frequently; long sessions accumulate stale context and drift from the architecture.
- During and after each session, have the AI summarize its own work — decisions, pitfalls, invariants — so the summary can be persisted and carried into the next session.

## Batched commits out of one dirty tree

- Group by dependency: commit the depended-on change first (appearance → insight → cards → detail → chat/market → cleanup). A file carrying several features goes to the dominant feature, and the message names the secondary change.
- `git commit` with **no** pathspec commits the entire index. To split one file across features (e.g. `AiProvider.kt` = ohos streaming + card policy), stage hunk by hunk: `git diff -- <file> > p.patch`, keep the file header plus a single `@@` hunk, then `git apply --cached --recount p.patch`. Write temp patches inside the repo — `git.exe` does not resolve msys `/tmp`.
- Do **not** "fix" a mixed commit by passing pathspecs: `git commit -- <paths>` takes the *worktree* content of those paths and bypasses the index, re-mixing the feature that was just split out. Check `git diff --cached --stat` (and `git show :<path> | grep` the staged blob) right before committing; if a stray file slipped in, `git reset --soft HEAD~1` + restage.
- `git status --short --cached` is not a valid invocation (`--cached` belongs to `diff`). An invalid option there breaks the `&&` chain, so the commit silently never runs while the log still looks unchanged.
- A file reported as modified while `git diff` is empty is usually `core.autocrlf=true` newline noise; one `git add` clears it and there is nothing to commit.
- Stage only the batch: after each commit, re-read `git status --short` before the next one, and never stage a neighboring session's WIP files.

## ⚠️ Machine hazard: a git delete also takes the whole parent directory (measured 2026-09-11)

- **Symptom.** Any git operation that deletes a path — `git rm`, or switching to a commit/branch that lacks some files — leaves the **entire containing directory gone, together with every other file in it**. Isolated repro: commit a fresh 3-file directory, `git rm` one file → the directory itself disappears while the other two files vanish too (verified on `D:\Project\StockQuote`, NTFS local disk, not a junction/symlink, sparse-checkout off).
- **Mechanism.** After unlinking, git prunes the parent directory; on this machine that pruning is turned into a recursive delete by the environment layer. Rewriting is safe: an in-place checkout and a branch round-trip that only *modified* one file left all 68 files intact. Plain `rm` is safe too, and leaves siblings untouched.
- **Real incident.** A single `git checkout` to a stale branch missing 6 files emptied `docs/` (68 files) and `outputs/` (8 files); `git status` then reported 71 entries of ` D`.
- **Lossless recovery.** Because everything was committed, `git restore --source=HEAD --worktree -- .` brought all of it back. Run it only when `git status` shows ` D` entries and no `M`/`??` — otherwise it overwrites uncommitted work.
- **Avoidance.** Delete with `rm` + `git add -A` instead of `git rm`; avoid hopping between branches; look at `git status` right after any delete/switch. Uncommitted edits inside a directory that gets wiped are genuinely lost — commit early.
- **Integrity check.** Empty `git status --short` plus a per-file pass. `git ls-files` quotes and escapes non-ASCII names, so test existence via `git ls-files -z` with `while IFS= read -r -d '' f`; a plain `[ -e "$f" ]` over `git ls-files` reports every Chinese-named file as missing.
