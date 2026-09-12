package com.kuikly.stockchat.chat.island.state

/**
 * 灵动岛 feature 需要页面/平台执行的副作用。Page 只做 Effect adapter：
 * 路由、行情请求、Glossary 查询、触感、Toast、以及 CompareInsight / 实体拖拽
 * 字段的清理（这两个域尚未迁出，仍由页面持有）。
 */
internal sealed interface IslandEffect {
    object Haptic : IslandEffect
    data class RequestQuote(val symbol: String) : IslandEffect
    data class OpenStockDetail(val symbol: String) : IslandEffect
    object OpenGlossary : IslandEffect
    data class Toast(val message: String) : IslandEffect

    /** 自选态是自选域（页面持有），岛打开时请页面刷新 [IslandEffect.RefreshWatchlisted]。 */
    object RefreshWatchlisted : IslandEffect
    data class EncounterTerm(val key: String) : IslandEffect

    /** 对比卡（页面持有）：由 quotes 组装 StockCompareCardModel 并触发解读。 */
    object SyncCompareCard : IslandEffect
    object SyncTermComparePanel : IslandEffect
    object ResetCompareInsight : IslandEffect
    object ClearCompareCard : IslandEffect
    object ClearCompareCandidate : IslandEffect

    /** 实体拖拽字段（EntityInteraction 域，暂留页面）。 */
    object ClearStockDrag : IslandEffect
    object ClearTermDrag : IslandEffect
}

/**
 * 页面提供给协调器的只读端口：可见性、页面高度（手势边界）、是否已有对比卡
 * （toggle 展开时的对比 lobby 判定）。全部在调用时惰性求值。
 */
internal interface IslandHostPort {
    fun isPageVisible(): Boolean
    fun pageHeight(): Float
    fun hasCompareCard(): Boolean
}

/**
 * 灵动岛的唯一状态 owner：展开/收敛、自动收起、详情交接、返回复位、对比 lobby。
 * 阈值与时长与迁移前逐值一致（见各常量）。
 */
internal class QuoteIslandCoordinator(
    val state: IslandStatePort,
    private val host: IslandHostPort,
    private val scheduler: IslandScheduler,
    private val onEffect: (IslandEffect) -> Unit,
) {
    // Mirrors of UI observables. Timers write reactive state but never read it:
    // R1 permits observable reads only inside reactive closures (same rule the
    // Welcome coordinator documents).
    private var expanded = false
    private var symbol = "600519.SH"
    private var mounted = true
    private var motion = IslandGestureMotion()
    private var compareVisible = false
    private var compareLeftSymbol = ""
    private var compareRightSymbol = ""
    private var termKey = ""
    private var termCompareLeftKey = ""
    private var termCompareRightKey = ""
    private var termCompareVisible = false

    // Non-observable lifecycle/version guards.
    private var animating = false
    private var gestureStartY = 0f
    private var motionRevision = 0
    private var autoCollapseVersion = 0
    private var watchdogRevision = 0
    private var detailHandoffDone = false
    private var handoffMaskActive = false
    private var detailRouteActive = false
    private var detailRouteResetVersion = 0
    private var compareExperienceVersion = 0
    private val tasks = mutableListOf<IslandScheduledTask>()

    // ===== 页面生命周期（由 Page 转发）=====

    fun onPageAppear() {
        if (detailRouteActive) scheduleDetailReturnReset()
    }

    /**
     * pageDidDisappear 的强制归位：详情页盖住本页时 JS 态可能已读作 idle，
     * 但原生视图还在等收起几何的写入——此处在遮罩期之外无条件写一次。
     */
    fun onPageDisappear() {
        if (detailRouteActive && !handoffMaskActive) {
            setMounted(false)
            forceCollapsedForDetailRoute()
        }
    }

    // ===== 手势与收敛 =====

    fun toggle() {
        // A tap can be re-delivered to stacked layers while the morph
        // re-layouts; ignore toggles until the animation settles.
        if (animating || motion.phase != IslandGesturePhase.IDLE) return
        // 对比 lobby 在场时 expanded() 恒为真，单纯翻转 expanded 会被 compareVisible
        // 架空（点了没反应）。用户决策 2026-09-05：此时点击 = 退出整个对比体验。
        if (isCompareLobbyVisible()) {
            clearCompareExperience()
            return
        }
        if (isTermLobbyVisible()) {
            clearTermCompareExperience()
            return
        }
        cancelDetailRouteReset()
        invalidateAutoCollapse()
        animating = true
        setExpanded(!expanded)
        if (expanded && compareLeftSymbol.isNotEmpty() && compareRightSymbol.isEmpty() && !host.hasCompareCard()) {
            setCompareVisible(true)
        }
        if (expanded) {
            onEffect(IslandEffect.RequestQuote(symbol))
            scheduleAutoCollapse()
        }
        schedule(400) { animating = false }
    }

    fun onPan(phase: String, y: Float) {
        when (phase) {
            "start" -> {
                noteInteraction()
                if (!expanded || animating || isCompareLobbyVisible() || isTermLobbyVisible()) return
                // 死手势接管（同看门狗根因）：end/cancel 丢失后 motion 卡在 DRAGGING，
                // 原 IDLE 门会让此后所有手势与点按全部失效。新 pan 的 start 即证明
                // 旧事件流已死，仅对 DRAGGING 残留直接接管；settle 相位仍忽略。
                if (motion.phase != IslandGesturePhase.IDLE) {
                    if (motion.phase != IslandGesturePhase.DRAGGING) return
                    resetMotion()
                }
                gestureStartY = y
                setMotion(motion.copy(phase = IslandGesturePhase.DRAGGING, offsetY = 0f))
                armDragWatchdog()
            }
            "move" -> if (motion.phase == IslandGesturePhase.DRAGGING) {
                setMotion(
                    motion.copy(
                        phase = IslandGesturePhase.DRAGGING,
                        offsetY = (y - gestureStartY).coerceIn(-DRAG_UP_LIMIT, dragDownLimit()),
                    ),
                )
                armDragWatchdog()
            }
            "end", "cancel" -> {
                if (motion.phase != IslandGesturePhase.DRAGGING) return
                val deltaY = (y - gestureStartY).coerceIn(-DRAG_UP_LIMIT, dragDownLimit())
                when {
                    // Trigger thresholds kept low so a short flick is enough
                    // (16dp close / 20dp detail).
                    phase == "end" && deltaY <= CLOSE_THRESHOLD -> settleClosedFromGesture()
                    phase == "end" && deltaY >= DETAIL_THRESHOLD -> openDetailFromGesture(symbol)
                    else -> settleGestureBack()
                }
            }
        }
    }

    /** 手势源视图重布局时原生可能丢弃 end/cancel——看门狗补齐 DRAGGING 的兜底收敛。 */
    private fun armDragWatchdog() {
        val revision = ++watchdogRevision
        schedule(WATCHDOG_MS) {
            if (revision == watchdogRevision && motion.phase == IslandGesturePhase.DRAGGING) {
                val deltaY = motion.offsetY
                when {
                    deltaY <= CLOSE_THRESHOLD -> settleClosedFromGesture()
                    deltaY >= DETAIL_THRESHOLD -> openDetailFromGesture(symbol)
                    else -> settleGestureBack()
                }
            }
        }
    }

    private fun settleGestureBack() {
        setMotion(motion.copy(phase = IslandGesturePhase.RETURNING, offsetY = 0f))
        // 兜底必须 ≥ 系统内最长 morph（0.44s easeOut），且相位门控。
        schedule(SETTLE_FALLBACK_MS) { onMotionComplete(ISLAND_ANIMATION_RETURN) }
    }

    private fun settleClosedFromGesture() {
        animating = true
        setMotion(motion.copy(phase = IslandGesturePhase.CLOSING, offsetY = -DRAG_UP_LIMIT))
        schedule(SETTLE_FALLBACK_MS) { onMotionComplete(ISLAND_ANIMATION_CLOSE) }
    }

    private fun openDetailFromGesture(symbol: String) {
        if (
            !expanded ||
            motion.phase != IslandGesturePhase.DRAGGING ||
            // 术语岛没有 symbol（只有 termKey），下滑去术语表走同一条 OPENING_DETAIL
            // 管线——空 symbol 不能提前 return，否则 motion 永远停在 DRAGGING。
            (symbol.isEmpty() && termKey.isEmpty())
        ) return
        detailHandoffDone = false
        setMotion(motion.copy(phase = IslandGesturePhase.OPENING_DETAIL, offsetY = 0f))
        animating = true
        // 容器变换交接：形变到 ~90% 就启动路由（160ms 主触发 / 420ms 渲染兜底，
        // 都走相位门 + detailHandoffDone 幂等门）。
        schedule(160) { onMotionComplete(ISLAND_ANIMATION_DETAIL) }
        schedule(420) { onMotionComplete(ISLAND_ANIMATION_DETAIL) }
    }

    fun onMotionComplete(animationKey: String) {
        when {
            animationKey == ISLAND_ANIMATION_RETURN &&
                motion.phase == IslandGesturePhase.RETURNING -> {
                resetMotion()
            }
            animationKey == ISLAND_ANIMATION_CLOSE &&
                motion.phase == IslandGesturePhase.CLOSING -> {
                setExpanded(false)
                resetMotion()
            }
            animationKey == ISLAND_ANIMATION_DETAIL &&
                motion.phase == IslandGesturePhase.OPENING_DETAIL &&
                !detailHandoffDone -> {
                detailHandoffDone = true
                val detailSymbol = symbol
                detailRouteActive = true
                detailRouteResetVersion++
                // 详情与对比互斥：进详情路由清掉对比会话，避免返回后 lobby 借
                // compareVisible 复活。清态延后到交接淡入结束（玻璃帧是淡入的底）。
                handoffMaskActive = true
                if (termKey.isNotEmpty()) {
                    onEffect(IslandEffect.OpenGlossary)
                } else {
                    onEffect(IslandEffect.OpenStockDetail(detailSymbol))
                }
                schedule(HANDOFF_MASK_MS) {
                    handoffMaskActive = false
                    setExpanded(false)
                    setCompareVisible(false)
                    setCompareLeftSymbol("")
                    setCompareRightSymbol("")
                    setTermKey("")
                    setTermCompareLeftKey("")
                    setTermCompareRightKey("")
                    setTermCompareVisible(false)
                    remountCollapsedForDetailRoute()
                }
            }
        }
    }

    // ===== 自动收起 =====

    /** 普通展开岛的任意点按/手势会重新开始倒计时。 */
    fun noteInteraction() {
        if (!expanded || isCompareLobbyVisible() || isTermLobbyVisible()) return
        scheduleAutoCollapse()
    }

    private fun invalidateAutoCollapse() {
        autoCollapseVersion++
    }

    private fun scheduleAutoCollapse() {
        val version = ++autoCollapseVersion
        schedule(AUTO_COLLAPSE_MS) {
            if (
                version != autoCollapseVersion ||
                !host.isPageVisible() ||
                !expanded ||
                animating ||
                motion.phase != IslandGesturePhase.IDLE ||
                isCompareLobbyVisible() ||
                isTermLobbyVisible()
            ) return@schedule
            animating = true
            setExpanded(false)
            schedule(400) { animating = false }
        }
    }

    // ===== 详情路由交接与返回复位 =====

    fun forceCollapsedForDetailRoute() {
        setExpanded(false)
        resetMotion(snap = true)
    }

    private fun remountCollapsedForDetailRoute() {
        setMounted(false)
        forceCollapsedForDetailRoute()
        schedule(16) {
            setMounted(true)
            forceCollapsedForDetailRoute()
        }
    }

    private fun scheduleDetailReturnReset() {
        val resetVersion = ++detailRouteResetVersion
        // 交接早已结束，清掉可能因渲染暂停而延迟的遮罩，避免 pageDidDisappear
        // 的兜底归位被误拦。
        handoffMaskActive = false
        remountCollapsedForDetailRoute()
        val writeCollapsedFrame: () -> Unit = {
            if (detailRouteActive && resetVersion == detailRouteResetVersion) {
                forceCollapsedForDetailRoute()
            }
        }
        writeCollapsedFrame()
        intArrayOf(16, 80, 180, 360).forEach { delay -> schedule(delay) { writeCollapsedFrame() } }
        schedule(RETURN_RESET_MS) {
            if (resetVersion == detailRouteResetVersion) {
                forceCollapsedForDetailRoute()
                setMounted(true)
                detailRouteActive = false
            }
        }
    }

    private fun cancelDetailRouteReset() {
        if (!detailRouteActive) return
        detailRouteActive = false
        detailRouteResetVersion++
        handoffMaskActive = false
        setMounted(true)
    }

    fun resetMotion(snap: Boolean = false) {
        setMotion(IslandGestureMotion(revision = ++motionRevision, snap = snap))
        animating = false
    }

    // ===== 对比 lobby 可见性（供 DSL 与内部判定）=====
    //
    // 这两个方法会被 DSL 的 attr {} 闭包直接调用（ChatPage.isIslandCompareLobbyVisible
    // → StockIsland.compareVisible()），因此**必须读 StatePort 的 observable，而不是本类
    // 的镜像字段**。读镜像不会在 attr 中注册反应式依赖（R1），attr 不会重跑 —— 岛会停在
    // 上一套样式（2026-09-12 回归：拖入第二只股票后对比面板、槽位、AI 解读全部正常，
    // 唯独岛身仍是行情卡样式）。内部命令逻辑读镜像无此问题，但不值得为此维护两种读法：
    // 统一读 state，语义与镜像逐值等价（setter 同步写两者），且与 Welcome coordinator 一致。

    fun isCompareLobbyVisible(): Boolean =
        state.compareVisible && state.compareLeftSymbol.isNotEmpty()

    fun isTermLobbyVisible(): Boolean =
        state.termCompareVisible && state.termCompareLeftKey.isNotEmpty()

    // ===== 岛打开入口（股票 / 术语）=====

    fun openQuoteIsland(symbol: String) {
        resetMotion()
        setSymbol(symbol)
        onEffect(IslandEffect.RefreshWatchlisted)
        setCompareVisible(false)
        setTermKey("")
        onEffect(IslandEffect.RequestQuote(symbol))
        setExpanded(true)
        scheduleAutoCollapse()
    }

    fun openTermIsland(key: String) {
        resetMotion()
        setCompareVisible(false)
        setTermKey(key)
        onEffect(IslandEffect.EncounterTerm(key))
        setExpanded(true)
        scheduleAutoCollapse()
    }

    // ===== 拖入对比 =====

    fun addDraggedStock(symbol: String) {
        resetMotion()
        // 新对比会话开始：使上一次退出对比留下的收起兜底定时器失效。
        compareExperienceVersion++
        onEffect(IslandEffect.ClearCompareCandidate)
        // 对比会话互斥：开始股票对比即结束术语对比。
        setTermKey("")
        setTermCompareLeftKey("")
        setTermCompareRightKey("")
        setTermCompareVisible(false)
        when {
            compareLeftSymbol.isEmpty() -> {
                setCompareLeftSymbol(symbol)
                setCompareRightSymbol("")
                onEffect(IslandEffect.ClearCompareCard)
                onEffect(IslandEffect.ResetCompareInsight)
            }
            compareLeftSymbol == symbol || compareRightSymbol == symbol -> {
                onEffect(IslandEffect.Toast("请选择另一只股票进行对比"))
                setExpanded(true)
                return
            }
            compareRightSymbol.isNotEmpty() -> {
                setCompareLeftSymbol(compareRightSymbol)
                setCompareRightSymbol(symbol)
                onEffect(IslandEffect.ClearCompareCard)
                onEffect(IslandEffect.ResetCompareInsight)
            }
            else -> setCompareRightSymbol(symbol)
        }
        onEffect(IslandEffect.RequestQuote(symbol))
        onEffect(IslandEffect.RequestQuote(compareLeftSymbol))
        // 岛自首次投放下起拥有对比会话，直到用户显式退出。
        setCompareVisible(true)
        setExpanded(true)
        onEffect(IslandEffect.SyncCompareCard)
        onEffect(IslandEffect.Haptic)
    }

    fun addDraggedTerm(key: String) {
        resetMotion()
        // 对比会话互斥：开始术语对比即结束股票对比（反之亦然）。
        compareExperienceVersion++
        setCompareLeftSymbol("")
        setCompareRightSymbol("")
        setCompareVisible(false)
        onEffect(IslandEffect.ClearCompareCard)
        onEffect(IslandEffect.ResetCompareInsight)
        onEffect(IslandEffect.ClearCompareCandidate)
        when {
            termCompareLeftKey.isEmpty() -> {
                setTermCompareLeftKey(key)
                setTermCompareRightKey("")
            }
            termCompareLeftKey == key || termCompareRightKey == key -> {
                onEffect(IslandEffect.Toast("请拖入另一个术语进行对比"))
                setExpanded(true)
                return
            }
            termCompareRightKey.isNotEmpty() -> {
                setTermCompareLeftKey(termCompareRightKey)
                setTermCompareRightKey(key)
            }
            else -> setTermCompareRightKey(key)
        }
        setTermCompareVisible(true)
        setExpanded(true)
        onEffect(IslandEffect.SyncTermComparePanel)
        onEffect(IslandEffect.Haptic)
    }

    fun openComparePanel(hasCompareCard: Boolean) {
        // 术语对比的「查看对比」：面板由双槽位驱动，无需额外状态。
        if (termCompareLeftKey.isNotEmpty() && termCompareRightKey.isNotEmpty()) {
            resetMotion()
            setExpanded(true)
            return
        }
        if (!hasCompareCard) return
        resetMotion()
        setCompareVisible(true)
        setExpanded(true)
    }

    // ===== 退出对比 =====

    fun clearCompare() {
        if (termCompareLeftKey.isNotEmpty()) clearTermCompareExperience() else clearCompareExperience()
    }

    fun clearTermCompare() = clearTermCompareExperience()

    private fun clearCompareExperience() {
        resetMotion()
        // 退出对比是硬交互边界：失效全部拖拽字段，防终态长按事件丢失残留。
        onEffect(IslandEffect.ClearStockDrag)
        setCompareLeftSymbol("")
        setCompareRightSymbol("")
        setCompareVisible(false)
        onEffect(IslandEffect.ClearCompareCard)
        onEffect(IslandEffect.ResetCompareInsight)
        onEffect(IslandEffect.ClearCompareCandidate)
        setExpanded(false)
        // 对比 × 详情竞态仲裁：失效在途详情复位定时器，并做收起兜底。
        detailRouteResetVersion++
        detailRouteActive = false
        val version = ++compareExperienceVersion
        schedule(360) {
            if (version == compareExperienceVersion && !expanded && !isCompareLobbyVisible()) {
                forceCollapsedForDetailRoute()
            }
        }
    }

    private fun clearTermCompareExperience() {
        resetMotion()
        onEffect(IslandEffect.ClearTermDrag)
        setTermCompareLeftKey("")
        setTermCompareRightKey("")
        setTermCompareVisible(false)
        setTermKey("")
        setExpanded(false)
        onEffect(IslandEffect.ResetCompareInsight)
        val version = ++compareExperienceVersion
        schedule(360) {
            if (version == compareExperienceVersion && !expanded && !isTermLobbyVisible()) {
                forceCollapsedForDetailRoute()
            }
        }
    }

    /** 会话级 UI 复位（新对话/清空）：岛收起、运动归位、股票和术语对比槽均清空。 */
    fun resetForNewSession() {
        setExpanded(false)
        resetMotion()
        setCompareLeftSymbol("")
        setCompareRightSymbol("")
        setCompareVisible(false)
        setTermKey("")
        setTermCompareLeftKey("")
        setTermCompareRightKey("")
        setTermCompareVisible(false)
    }

    fun onDestroy() {
        detailRouteResetVersion++
        autoCollapseVersion++
        watchdogRevision++
        compareExperienceVersion++
        tasks.forEach(IslandScheduledTask::cancel)
        tasks.clear()
    }

    // ===== internals =====

    private fun dragDownLimit(): Float = (host.pageHeight() * 0.42f).coerceAtLeast(180f)

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }

    private fun setExpanded(value: Boolean) { expanded = value; state.expanded = value }
    private fun setSymbol(value: String) { symbol = value; state.symbol = value }
    private fun setMounted(value: Boolean) { mounted = value; state.mounted = value }
    private fun setMotion(value: IslandGestureMotion) { motion = value; state.motion = value }
    private fun setCompareVisible(value: Boolean) { compareVisible = value; state.compareVisible = value }
    private fun setCompareLeftSymbol(value: String) { compareLeftSymbol = value; state.compareLeftSymbol = value }
    private fun setCompareRightSymbol(value: String) { compareRightSymbol = value; state.compareRightSymbol = value }
    private fun setTermKey(value: String) { termKey = value; state.termKey = value }
    private fun setTermCompareLeftKey(value: String) { termCompareLeftKey = value; state.termCompareLeftKey = value }
    private fun setTermCompareRightKey(value: String) { termCompareRightKey = value; state.termCompareRightKey = value }
    private fun setTermCompareVisible(value: Boolean) { termCompareVisible = value; state.termCompareVisible = value }

    private companion object {
        const val CLOSE_THRESHOLD = -16f
        const val DETAIL_THRESHOLD = 20f
        const val DRAG_UP_LIMIT = 104f
        const val WATCHDOG_MS = 800
        const val AUTO_COLLAPSE_MS = 2_000
        const val SETTLE_FALLBACK_MS = 560
        const val HANDOFF_MASK_MS = 550
        const val RETURN_RESET_MS = 520
    }
}
