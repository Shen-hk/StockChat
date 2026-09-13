package com.kuikly.stockchat.chat.welcome.state

import com.kuikly.stockchat.chat.welcome.data.WelcomeStarterStore
import com.tencent.kuikly.core.timer.Timer

internal interface WelcomeScheduledTask {
    fun cancel()
}

/** Small scheduling port makes welcome timing deterministic in common tests. */
internal fun interface WelcomeScheduler {
    fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): WelcomeScheduledTask
}

internal class KuiklyWelcomeScheduler : WelcomeScheduler {
    override fun schedule(delayMillis: Int, repeating: Boolean, task: () -> Unit): WelcomeScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, if (repeating) delayMillis.coerceAtLeast(16) else delayMillis.coerceAtLeast(16)) {
            task()
            if (!repeating) timer.cancel()
        }
        return object : WelcomeScheduledTask {
            override fun cancel() = timer.cancel()
        }
    }
}

internal enum class ChatWelcomeEffect { HAPTIC_IMPACT, OPEN_MARKET }

/**
 * Sole owner of welcome timing. All callbacks are guarded by lifecycle and
 * version fields, keeping delayed work from changing a hidden/destroyed page.
 */
internal class ChatWelcomeCoordinator(
    val state: ChatWelcomeStatePort,
    private val starterStore: WelcomeStarterStore,
    private val scheduler: WelcomeScheduler,
    private val reducedMotion: Boolean,
    private val onRefreshStarters: () -> Unit = {},
    private val onEffect: (ChatWelcomeEffect) -> Unit,
) {
    private val keywords = listOf("行情", "术语", "财报", "公告")
    private var keywordVersion = 0
    private var entranceVersion = 0
    private var marketVersion = 0
    // Mirrors of UI observables for timer guards. Timers write reactive state
    // but never read it: R1 permits observable reads only in reactive closures.
    private var cursorShown = false
    private var entrancePresented = false
    private var recommendationsShown = false
    private var composerGuideShown = false
    private var composerShown = false
    private var marketSelected = false
    // Only the market shortcut sets this; ordinary foregrounding must not replay welcome.
    private var refreshWelcomeAfterMarketReturn = false
    private var keywordTask: WelcomeScheduledTask? = null
    private var cursorTask: WelcomeScheduledTask? = null
    private var entranceTask: WelcomeScheduledTask? = null
    private var recommendationsTask: WelcomeScheduledTask? = null
    private var composerGuideTask: WelcomeScheduledTask? = null
    private var composerTask: WelcomeScheduledTask? = null
    private var entranceSafetyTask: WelcomeScheduledTask? = null
    private var marketOpenTask: WelcomeScheduledTask? = null
    private var marketResetTask: WelcomeScheduledTask? = null

    fun onAppear(sessionEmpty: Boolean, fullMode: Boolean) {
        state.pageActive = true
        state.sessionEmpty = sessionEmpty
        state.fullMode = fullMode
        state.destroyed = false
        startKeywordLoopIfNeeded()
        var restartedForMarketReturn = false
        if (refreshWelcomeAfterMarketReturn) {
            refreshWelcomeAfterMarketReturn = false
            if (state.sessionEmpty) {
                restartWelcomeEntrance(refreshStarters = true)
                restartedForMarketReturn = true
            }
            scheduleMarketTabReturnReset()
        }
        // If the page disappeared during the 32ms mounted -> presented window,
        // restart that phase on return. The component has already registered
        // its easeOut animation, so this preserves the R4/R5 entrance instead
        // of leaving the cards at their transparent initial state.
        if (state.sessionEmpty && state.welcomeMounted && !composerShown && !reducedMotion && !restartedForMarketReturn) {
            startWelcomeEntranceTimeline()
        }
        scheduleEntranceSafety()
    }

    fun onDisappear() {
        state.pageActive = false
        stopKeywordLoop(lock = false)
        cancelEntranceTasks()
        cancelMarketTasks()
        // 保留“看行情”选中态到页面回来；onAppear 后再滑回“问AI”，让用户能
        // 看见一次完整的返回归位，而不是在市场页遮住时静默重置。
    }

    fun onDestroy() {
        state.destroyed = true
        state.pageActive = false
        stopKeywordLoop(lock = false)
        cancelEntranceTasks()
        cancelMarketTasks()
    }

    /** Call immediately before an empty session mounts its welcome component. */
    fun onNewEmptySession() {
        state.sessionEmpty = true
        state.keywordStopped = false
        state.welcomeMounted = true
        // 新建会话时欢迎组件会重新挂载；先把滑块落在左侧再建树，绝不能让
        // 旧的“看行情”选中态在新会话里产生一次无语义的回滑动画。
        refreshWelcomeAfterMarketReturn = false
        cancelMarketTasks()
        setMarketSelected(false)
        restartWelcomeEntrance(refreshStarters = true)
        startKeywordLoopIfNeeded()
    }

    fun onSessionOpened(sessionEmpty: Boolean, fullMode: Boolean) {
        state.sessionEmpty = sessionEmpty
        state.fullMode = fullMode
        if (sessionEmpty) {
            state.keywordStopped = false
            startKeywordLoopIfNeeded()
        } else {
            stopKeywordLoop(lock = true)
        }
    }

    fun onConversationStarted() = stopKeywordLoop(lock = true)

    fun onStarterChosen(kind: String) {
        starterStore.markUsed(kind)
        onConversationStarted()
    }

    /** Two-phase mounted -> presented entrance; keep R4/R5 registration intact in the component. */
    fun onWelcomeMounted() {
        if (state.welcomeMounted) return
        state.welcomeMounted = true
        if (reducedMotion) {
            setEntrancePhasesPresented(true)
            return
        }
        startWelcomeEntranceTimeline()
    }

    fun onOpenMarketRequested() {
        if (marketSelected) return
        refreshWelcomeAfterMarketReturn = true
        setMarketSelected(true)
        val version = ++marketVersion
        marketOpenTask?.cancel()
        marketOpenTask = scheduler.schedule(240, repeating = false) {
            if (marketVersion == version && isActive()) {
                onEffect(ChatWelcomeEffect.HAPTIC_IMPACT)
                onEffect(ChatWelcomeEffect.OPEN_MARKET)
            }
        }
        marketResetTask?.cancel()
        marketResetTask = scheduler.schedule(420, repeating = false) {
            if (marketVersion == version && !state.destroyed) setMarketSelected(false)
        }
    }

    private fun startKeywordLoopIfNeeded() {
        if (state.keywordStopped || !state.sessionEmpty || !state.fullMode || !state.pageActive) return
        if (reducedMotion) {
            ++keywordVersion
            state.rotatingKeyword = ChatWelcomeState.DEFAULT_KEYWORD
            setCursorShown(false)
            return
        }
        val version = ++keywordVersion
        state.rotatingKeyword = keywords.first()
        setCursorShown(true)
        typeKeyword(version, wordIndex = 0, length = keywords.first().length)
        blinkCursor(version)
    }

    private fun stopKeywordLoop(lock: Boolean) {
        if (lock) state.keywordStopped = true
        ++keywordVersion
        keywordTask?.cancel()
        keywordTask = null
        cursorTask?.cancel()
        cursorTask = null
        state.rotatingKeyword = ChatWelcomeState.DEFAULT_KEYWORD
        setCursorShown(false)
    }

    private fun typeKeyword(version: Int, wordIndex: Int, length: Int) {
        if (!keywordShouldRun(version)) return
        val word = keywords[wordIndex % keywords.size]
        state.rotatingKeyword = word.take(length)
        if (length < word.length) {
            scheduleKeywordStep(version, 200) { typeKeyword(version, wordIndex, length + 1) }
        } else {
            scheduleKeywordStep(version, 1_200) { deleteKeyword(version, wordIndex, word.length - 1) }
        }
    }

    private fun deleteKeyword(version: Int, wordIndex: Int, length: Int) {
        if (!keywordShouldRun(version)) return
        val word = keywords[wordIndex % keywords.size]
        state.rotatingKeyword = word.take(length)
        if (length > 0) {
            scheduleKeywordStep(version, 120) { deleteKeyword(version, wordIndex, length - 1) }
        } else {
            scheduleKeywordStep(version, 300) { typeKeyword(version, wordIndex + 1, 1) }
        }
    }

    private fun scheduleKeywordStep(version: Int, delay: Int, task: () -> Unit) {
        keywordTask?.cancel()
        keywordTask = scheduler.schedule(delay, repeating = false) {
            if (keywordShouldRun(version)) task()
        }
    }

    private fun blinkCursor(version: Int) {
        cursorTask?.cancel()
        cursorTask = scheduler.schedule(550, repeating = true) {
            if (keywordShouldRun(version)) {
                setCursorShown(!cursorShown)
            } else {
                cursorTask?.cancel()
                cursorTask = null
            }
        }
    }

    private fun scheduleEntranceStep(version: Int, delay: Int, task: () -> Unit) {
        entranceTask?.cancel()
        entranceTask = scheduler.schedule(delay, repeating = false) {
            if (entranceVersion == version && isActive()) task()
        }
    }

    private fun scheduleRecommendationsStep(version: Int, delay: Int, task: () -> Unit) {
        recommendationsTask?.cancel()
        recommendationsTask = scheduler.schedule(delay, repeating = false) {
            if (entranceVersion == version && isActive()) task()
        }
    }

    private fun scheduleComposerGuideStep(version: Int, delay: Int, task: () -> Unit) {
        composerGuideTask?.cancel()
        composerGuideTask = scheduler.schedule(delay, repeating = false) {
            if (entranceVersion == version && isActive()) task()
        }
    }

    private fun scheduleComposerStep(version: Int, delay: Int, task: () -> Unit) {
        composerTask?.cancel()
        composerTask = scheduler.schedule(delay, repeating = false) {
            if (entranceVersion == version && isActive()) task()
        }
    }

    /** Gives the returned page one frame to paint the right tab before sliding it home. */
    private fun scheduleMarketTabReturnReset() {
        val version = ++marketVersion
        marketResetTask?.cancel()
        marketResetTask = scheduler.schedule(120, repeating = false) {
            if (marketVersion == version && isActive()) setMarketSelected(false)
        }
    }

    /** Replays the R4 mounted -> presented transition after the four cards are replaced. */
    private fun restartWelcomeEntrance(refreshStarters: Boolean) {
        if (refreshStarters) onRefreshStarters()
        startWelcomeEntranceTimeline()
    }

    /** Four independent R4 phases keep the welcome hierarchy readable. */
    private fun startWelcomeEntranceTimeline() {
        ++entranceVersion
        cancelEntrancePhaseTasks()
        if (reducedMotion) {
            setEntrancePhasesPresented(true)
            return
        }
        // 图标/主题句回弹 → “为你推荐” → 引导语 → 输入框。每段都先留一帧
        // 注册下一次变更要消费的动画，符合 R4/R5。
        setEntrancePhasesPresented(false)
        val version = entranceVersion
        scheduleEntranceStep(version, 32) { setEntrancePresented(true) }
        scheduleRecommendationsStep(version, 220) { setRecommendationsPresented(true) }
        scheduleComposerGuideStep(version, 380) { setComposerGuidePresented(true) }
        scheduleComposerStep(version, 520) { setComposerPresented(true) }
        scheduleEntranceSafety()
    }

    /** Version-guarded fallback so a lost mount/frame callback cannot leave cards transparent. */
    private fun scheduleEntranceSafety() {
        entranceSafetyTask?.cancel()
        val version = entranceVersion
        entranceSafetyTask = scheduler.schedule(600, repeating = false) {
            if (
                entranceVersion == version && isActive() && state.sessionEmpty &&
                (!entrancePresented || !recommendationsShown || !composerGuideShown || !composerShown)
            ) {
                state.welcomeMounted = true
                setEntrancePhasesPresented(true)
            }
        }
    }

    private fun cancelEntranceTasks() {
        ++entranceVersion
        cancelEntrancePhaseTasks()
        entranceSafetyTask?.cancel()
        entranceSafetyTask = null
    }

    private fun cancelEntrancePhaseTasks() {
        entranceTask?.cancel()
        entranceTask = null
        recommendationsTask?.cancel()
        recommendationsTask = null
        composerGuideTask?.cancel()
        composerGuideTask = null
        composerTask?.cancel()
        composerTask = null
    }

    private fun cancelMarketTasks() {
        ++marketVersion
        marketOpenTask?.cancel()
        marketOpenTask = null
        marketResetTask?.cancel()
        marketResetTask = null
    }

    private fun keywordShouldRun(version: Int) = version == keywordVersion && isActive() &&
        !state.keywordStopped && state.sessionEmpty && state.fullMode

    private fun isActive() = state.pageActive && !state.destroyed

    private fun setCursorShown(visible: Boolean) {
        cursorShown = visible
        state.cursorVisible = visible
    }

    private fun setEntrancePresented(visible: Boolean) {
        entrancePresented = visible
        state.entranceVisible = visible
    }

    private fun setEntrancePhasesPresented(visible: Boolean) {
        setEntrancePresented(visible)
        setRecommendationsPresented(visible)
        setComposerGuidePresented(visible)
        setComposerPresented(visible)
    }

    private fun setRecommendationsPresented(visible: Boolean) {
        recommendationsShown = visible
        state.recommendationsPresented = visible
    }

    private fun setComposerGuidePresented(visible: Boolean) {
        composerGuideShown = visible
        state.composerGuidePresented = visible
    }

    private fun setComposerPresented(visible: Boolean) {
        composerShown = visible
        state.composerPresented = visible
    }

    private fun setMarketSelected(selected: Boolean) {
        marketSelected = selected
        state.marketTabSelected = selected
    }
}
