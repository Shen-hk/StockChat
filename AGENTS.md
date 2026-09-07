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

## Review checklist

- Is every interaction target inside a reactive closure and proven to receive its event inside scrolling containers?
- Is each `animate()` the last observable-dependent operation in its `attr` block?
- Is the target state read in the same `attr` block and not only in an outer builder closure?
- Does the animation registered in the current cycle equal the one the next driver change should play (R5)?
- Are reduced-motion behavior and Android/H5/iOS runtime interactions verified after the change?

When debugging a silent Kuikly animation, inspect reactive dependency registration and event hit testing before changing timing or easing.

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
