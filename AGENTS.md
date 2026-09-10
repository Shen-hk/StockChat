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
- **Full-panel rebuild trick:** for content derived from non-observable composites (e.g. `args = f(viewModel.inputText, mentionEntities)`), keep a single-element `ObservableList<Int>` render key and bump it (`clear()+add()`) at every mutation site; wrap the panel content in `vfor({ key }) { … }` so each bump rebuilds the frame. All bump sites must be guarded by the same condition as the panel's mount, and the vfor item must create exactly one child (`Scroller` on the LoopDirectivesView receiver, not the outer container). Missing the closing brace of that vfor lambda silently demotes later `private fun`s into local functions → cascade of "Unresolved reference" at call sites above.
- **Gradle may compile a torn mid-write file.** With parallel sessions editing the same file, a compile can report dozens of bogus "Unresolved reference" for methods that grep shows exist. Check file mtime, wait for stability, recompile before diagnosing.

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
