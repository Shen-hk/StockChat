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
    private var marketSelected = false
    private var keywordTask: WelcomeScheduledTask? = null
    private var cursorTask: WelcomeScheduledTask? = null
    private var entranceTask: WelcomeScheduledTask? = null
    private var entranceSafetyTask: WelcomeScheduledTask? = null
    private var marketOpenTask: WelcomeScheduledTask? = null
    private var marketResetTask: WelcomeScheduledTask? = null

    fun onAppear(sessionEmpty: Boolean, fullMode: Boolean) {
        state.pageActive = true
        state.sessionEmpty = sessionEmpty
        state.fullMode = fullMode
        state.destroyed = false
        startKeywordLoopIfNeeded()
        // If the page disappeared during the 32ms mounted -> presented window,
        // restart that phase on return. The component has already registered
        // its easeOut animation, so this preserves the R4/R5 entrance instead
        // of leaving the cards at their transparent initial state.
        if (state.sessionEmpty && state.welcomeMounted && !entrancePresented && !reducedMotion) {
            val version = ++entranceVersion
            scheduleEntranceStep(version, 32) { setEntrancePresented(true) }
        }
        scheduleEntranceSafety()
    }

    fun onDisappear() {
        state.pageActive = false
        stopKeywordLoop(lock = false)
        cancelEntranceTasks()
        cancelMarketTasks()
        // 「看行情」的自动复位定时器（420ms）活不过跳转：openPage 市场页会让
        // 本页先走 onDisappear，复位任务被 cancelMarketTasks 取消后
        // marketTabSelected 停在 true，返回时滑块就卡在「看行情」半格。
        // 消失即复位：跳转动画期滑块会先滑回「问AI」，回程必然落在默认态。
        setMarketSelected(false)
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
        ++entranceVersion
        entranceTask?.cancel()
        entranceTask = null
        setEntrancePresented(true)
        scheduleEntranceSafety()
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
            setEntrancePresented(true)
            return
        }
        setEntrancePresented(false)
        val version = ++entranceVersion
        scheduleEntranceStep(version, 32) { setEntrancePresented(true) }
        scheduleEntranceSafety()
    }

    fun onOpenMarketRequested() {
        if (marketSelected) return
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

    /** Version-guarded fallback so a lost mount/frame callback cannot leave cards transparent. */
    private fun scheduleEntranceSafety() {
        entranceSafetyTask?.cancel()
        val version = entranceVersion
        entranceSafetyTask = scheduler.schedule(600, repeating = false) {
            if (entranceVersion == version && isActive() && state.sessionEmpty && !entrancePresented) {
                state.welcomeMounted = true
                setEntrancePresented(true)
            }
        }
    }

    private fun cancelEntranceTasks() {
        ++entranceVersion
        entranceTask?.cancel()
        entranceTask = null
        entranceSafetyTask?.cancel()
        entranceSafetyTask = null
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

    private fun setMarketSelected(selected: Boolean) {
        marketSelected = selected
        state.marketTabSelected = selected
    }
}
