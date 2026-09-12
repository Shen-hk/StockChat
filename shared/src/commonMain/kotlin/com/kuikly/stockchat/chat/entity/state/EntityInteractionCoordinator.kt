package com.kuikly.stockchat.chat.entity.state

import com.kuikly.stockchat.richtext.EntityDropResolver
import com.kuikly.stockchat.richtext.EntityDropTarget
import com.kuikly.stockchat.richtext.EntitySpan
import com.kuikly.stockchat.richtext.EntityType

/**
 * 实体交互向页面输出的副作用。页面负责执行：路由、行情请求、灵动岛协作、
 * 输入栏注入、原生触感与埋点。协调器只做决策，不直接触达平台。
 */
internal sealed interface EntityEffect {
    /** 原生触感反馈。 */
    object Haptic : EntityEffect

    /** 埋点（事件名 + 键值对）。 */
    data class Track(val event: String, val params: Map<String, String>) : EntityEffect

    /** 请求行情（预览气泡与二义选择后的预览用）。 */
    data class RequestQuote(val symbol: String) : EntityEffect

    /** 打开个股详情页。 */
    data class OpenStockDetail(val symbol: String) : EntityEffect

    /** 打开行情灵动岛（静止长按 = 预览手势）。 */
    data class OpenStockIsland(val symbol: String) : EntityEffect

    /** 打开术语灵动岛（静止长按 = 术语讲解预览）。 */
    data class OpenTermIsland(val key: String) : EntityEffect

    /** 拖入灵动岛：股票加入对比。 */
    data class AddStockToIsland(val symbol: String) : EntityEffect

    /** 拖入灵动岛：术语加入对比。 */
    data class AddTermToIsland(val key: String) : EntityEffect

    /** 拖入输入栏：插入 @ 提及。 */
    data class InjectStockMention(val symbol: String) : EntityEffect

    /** 拖入输入栏（术语）：填入「X 是什么意思」问句。 */
    data class InjectQuestion(val text: String) : EntityEffect
}

/**
 * 页面提供的只读端口，全部在调用时惰性求值。
 *
 * 投放判定必须拿到与迁移前完全相同的几何与岛状态输入，否则拖拽手感会在
 * 边界处发生变化（`EntityDropResolver` 的岛/输入栏捕获区随键盘与岛展开态变高）。
 */
internal interface EntityHostPort {
    fun pageWidth(): Float
    fun pageHeight(): Float
    fun statusBarHeight(): Float
    fun safeAreaBottom(): Float
    fun keyboardHeight(): Float

    /**
     * 岛是否已展开或携带对比槽位
     * （`islandExpanded || compareLeftSymbol || termKey || termCompareLeftKey`），
     * 决定岛投放区高度 158 / 86。
     */
    fun isIslandExpanded(): Boolean

    /** 实体显示名（行情名 → 目录名 → 证券名录 → fallback）。 */
    fun displayName(symbol: String, fallback: String): String

    /** 术语名（`Glossary.byKey(key)?.term`）。 */
    fun termName(key: String): String?
}

/**
 * 实体交互的唯一状态 owner：静止长按预览、长按转拖拽、投放目标、二义实体选择。
 *
 * 阈值与时长与迁移前逐值一致：拖拽阈值沿用既有 `EntityDropResolver.DRAG_THRESHOLD = 10f`，
 * 预览呈现 16ms、淡出 180ms、自动收起 1500ms、长按补发 click 抑制 400ms。
 */
internal class EntityInteractionCoordinator(
    val state: EntityStatePort,
    private val host: EntityHostPort,
    private val scheduler: EntityScheduler,
    private val onEffect: (EntityEffect) -> Unit,
) {
    // Mirrors of the observables that the coordinator itself reads. Timers write
    // reactive state but never read it: R1 permits observable reads only inside
    // reactive closures (same rule the Drawer / Welcome / Island coordinators document).
    private var peekSymbol = ""
    private var peekVisible = false
    private var dragActive = false
    private var dropTarget = EntityDropTarget.NONE
    private var ambiguousAction = EntityAction.PREVIEW

    // Non-observable gesture guards.
    private var peekVersion = 0
    private var pendingLongPressSymbol = ""
    private var pendingLongPressTermKey = ""
    private var suppressNextStockClickSymbol = ""
    private var suppressNextTermClick = ""
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val tasks = mutableListOf<EntityScheduledTask>()

    // ===== 行情预览气泡 =====

    fun showQuote(symbol: String) {
        val version = ++peekVersion
        state.clearAmbiguousCandidates()
        state.ambiguousText = ""
        setPeekSymbol(symbol)
        setPeekVisible(false)
        onEffect(EntityEffect.RequestQuote(symbol))
        schedule(PEEK_PRESENT_MS) {
            if (peekVersion == version && peekSymbol == symbol) setPeekVisible(true)
        }
    }

    fun dismissPeek() {
        val version = ++peekVersion
        setPeekVisible(false)
        schedule(PEEK_FADE_MS) {
            if (peekVersion == version && !peekVisible) setPeekSymbol("")
        }
    }

    fun schedulePeekDismissal() {
        val version = peekVersion
        schedule(PEEK_AUTO_DISMISS_MS) {
            if (peekVersion == version && peekVisible) dismissPeek()
        }
    }

    // ===== 股票实体：点击与长按 =====

    fun onStockClick(entity: EntitySpan) {
        if (suppressNextStockClickSymbol == entity.target) {
            suppressNextStockClickSymbol = ""
            return
        }
        handleEntity(entity, EntityAction.DETAIL)
    }

    fun onStockLongPress(entity: EntitySpan, phase: String, isCancel: Boolean, pageX: Float, pageY: Float) {
        when (phase) {
            "start" -> {
                if (isCancel || pendingLongPressSymbol == entity.target) return
                pendingLongPressSymbol = entity.target
                suppressNextStockClickSymbol = entity.target
                state.draggedEntity = entity
                state.dragName = host.displayName(entity.target, entity.text)
                dragStartX = pageX
                dragStartY = pageY
                state.dragX = pageX
                state.dragY = pageY
                setDragActive(false)
                setDropTarget(EntityDropTarget.NONE)
                // A stationary long press remains the quote-preview gesture.
                onEffect(EntityEffect.OpenStockIsland(entity.target))
                onEffect(EntityEffect.Haptic)
                onEffect(EntityEffect.Track("entity_hold_preview", mapOf("symbol" to entity.target)))
                return
            }
            "move" -> {
                if (pendingLongPressSymbol != entity.target) return
                if (!dragActive && EntityDropResolver.hasExceededDragThreshold(
                        dragStartX,
                        dragStartY,
                        pageX,
                        pageY,
                    )
                ) {
                    setDragActive(true)
                    onEffect(EntityEffect.Haptic)
                    onEffect(EntityEffect.Track("entity_drag_start", mapOf("symbol" to entity.target)))
                }
                if (dragActive) updateDragPosition(pageX, pageY)
                if (isCancel) {
                    if (dragActive) finishEntityDrag() else finishEntityHold(entity.target)
                }
                return
            }
            "end" -> {
                if (pendingLongPressSymbol != entity.target) return
                if (dragActive) {
                    updateDragPosition(pageX, pageY)
                    finishEntityDrag()
                } else {
                    finishEntityHold(entity.target)
                }
                return
            }
            else -> if (isCancel && pendingLongPressSymbol == entity.target) {
                if (dragActive) finishEntityDrag() else finishEntityHold(entity.target)
                return
            }
        }
    }

    // ===== 术语实体：长按与拖拽（与股票共用同一套拖拽管线）=====

    fun onTermLongPress(entity: EntitySpan, phase: String, isCancel: Boolean, pageX: Float, pageY: Float) {
        when (phase) {
            "start" -> {
                if (isCancel || pendingLongPressTermKey == entity.target) return
                pendingLongPressTermKey = entity.target
                suppressNextTermClick = entity.text
                state.draggedEntity = entity
                state.dragName = host.termName(entity.target) ?: entity.text
                dragStartX = pageX
                dragStartY = pageY
                state.dragX = pageX
                state.dragY = pageY
                setDragActive(false)
                setDropTarget(EntityDropTarget.NONE)
                // 静止长按 = 术语讲解预览。长按展开讲解与点击高亮一样算一次
                // 真实「遇到」（doc 24 §6.3：用户真实撞上术语才算）。
                onEffect(EntityEffect.OpenTermIsland(entity.target))
                onEffect(EntityEffect.Haptic)
                onEffect(EntityEffect.Track("term_hold_preview", mapOf("term" to entity.target)))
                return
            }
            "move" -> {
                if (pendingLongPressTermKey != entity.target) return
                if (!dragActive && EntityDropResolver.hasExceededDragThreshold(
                        dragStartX,
                        dragStartY,
                        pageX,
                        pageY,
                    )
                ) {
                    setDragActive(true)
                    onEffect(EntityEffect.Haptic)
                    onEffect(EntityEffect.Track("term_drag_start", mapOf("term" to entity.target)))
                }
                if (dragActive) updateDragPosition(pageX, pageY)
                if (isCancel) {
                    if (dragActive) finishEntityDrag() else finishTermHold()
                }
                return
            }
            "end" -> {
                if (pendingLongPressTermKey != entity.target) return
                if (dragActive) {
                    updateDragPosition(pageX, pageY)
                    finishEntityDrag()
                } else {
                    finishTermHold()
                }
                return
            }
            else -> if (isCancel && pendingLongPressTermKey == entity.target) {
                if (dragActive) finishEntityDrag() else finishTermHold()
                return
            }
        }
    }

    // ===== 二义实体（多个候选需要用户选择）=====

    fun chooseAmbiguous(symbol: String) = performAction(symbol, ambiguousAction)

    /**
     * 长按术语后部分 bridge 会补发 click：命中抑制词形则消耗掉并返回 true
     * （页面据此跳过「遇到术语」的提问分支）。事件回调内的读点，无反应式要求。
     */
    fun consumeTermClickSuppression(token: String): Boolean {
        if (suppressNextTermClick != token) return false
        suppressNextTermClick = ""
        return true
    }

    private fun handleEntity(entity: EntitySpan, action: EntityAction) {
        if (entity.candidates.size == 1) performAction(entity.target, action)
        else {
            state.ambiguousText = entity.text
            setAmbiguousAction(action)
            state.setAmbiguousCandidates(entity.candidates)
        }
    }

    private fun performAction(symbol: String, action: EntityAction) {
        when (action) {
            EntityAction.DETAIL -> onEffect(EntityEffect.OpenStockDetail(symbol))
            EntityAction.PREVIEW -> showQuote(symbol)
            EntityAction.ISLAND -> onEffect(EntityEffect.OpenStockIsland(symbol))
            EntityAction.MENTION -> onEffect(EntityEffect.InjectStockMention(symbol))
            EntityAction.COMPARE -> onEffect(EntityEffect.AddStockToIsland(symbol))
        }
    }

    // ===== 拖拽与投放 =====

    private fun updateDragPosition(pageX: Float, pageY: Float) {
        state.dragX = pageX
        state.dragY = pageY
        setDropTarget(
            EntityDropResolver.resolve(
                pageX = pageX,
                pageY = pageY,
                pageWidth = host.pageWidth(),
                pageHeight = host.pageHeight(),
                statusBarHeight = host.statusBarHeight(),
                safeAreaBottom = host.safeAreaBottom(),
                keyboardHeight = host.keyboardHeight(),
                islandExpanded = host.isIslandExpanded(),
            ),
        )
    }

    private fun finishEntityHold(symbol: String) {
        clearDrag()
        pendingLongPressSymbol = ""
        finishStockLongPress(symbol)
    }

    private fun finishTermHold() {
        clearDrag()
        finishTermLongPress()
    }

    /**
     * 术语静止长按与术语拖拽必须共用同一套收敛：两条路径都会留下 bridge
     * 可能补发的 click，但不能把 [pendingLongPressTermKey] 留到下一次手势。
     */
    private fun finishTermLongPress() {
        pendingLongPressTermKey = ""
        val suppressed = suppressNextTermClick
        schedule(CLICK_SUPPRESS_MS) {
            if (suppressNextTermClick == suppressed) {
                suppressNextTermClick = ""
            }
        }
    }

    /** 退出对比时清理股票拖拽残留（由 Island coordinator 的 `ClearStockDrag` Effect 触发）。 */
    fun clearStockDragResidue() {
        clearDrag()
        pendingLongPressSymbol = ""
    }

    /** 退出对比时清理术语拖拽残留（由 Island coordinator 的 `ClearTermDrag` Effect 触发）。 */
    fun clearTermDragResidue() {
        clearDrag()
        pendingLongPressTermKey = ""
    }

    private fun clearDrag() {
        state.draggedEntity = null
        setDragActive(false)
        setDropTarget(EntityDropTarget.NONE)
        state.dragName = ""
    }

    private fun finishEntityDrag() {
        val entity = state.draggedEntity
        val target = dropTarget
        val symbol = entity?.target ?: pendingLongPressSymbol
        clearDrag()
        pendingLongPressSymbol = ""

        if (entity != null) {
            if (entity.type == EntityType.TERM) {
                when (target) {
                    // 术语没有 @ 提及形态：拖到输入框 = 注入「X 是什么意思」问句。
                    EntityDropTarget.COMPOSER -> injectTermQuestion(entity)
                    EntityDropTarget.ISLAND -> onEffect(EntityEffect.AddTermToIsland(entity.target))
                    EntityDropTarget.NONE -> Unit
                }
            } else {
                when (target) {
                    EntityDropTarget.COMPOSER -> handleEntity(entity, EntityAction.MENTION)
                    EntityDropTarget.ISLAND -> handleEntity(entity, EntityAction.COMPARE)
                    EntityDropTarget.NONE -> Unit
                }
            }
            if (target != EntityDropTarget.NONE) {
                onEffect(
                    EntityEffect.Track(
                        "entity_drag_drop",
                        mapOf("symbol" to entity.target, "target" to target.name.lowercase()),
                    ),
                )
            }
        }
        if (entity?.type == EntityType.TERM) finishTermLongPress() else finishStockLongPress(symbol)
    }

    private fun finishStockLongPress(symbol: String) {
        // The island is outside the releasing finger's hit area, so no deferred
        // mount is needed. Keep only the short click-suppression guard.
        schedule(CLICK_SUPPRESS_MS) {
            if (suppressNextStockClickSymbol == symbol) {
                suppressNextStockClickSymbol = ""
            }
        }
    }

    private fun injectTermQuestion(entity: EntitySpan) {
        val name = host.termName(entity.target) ?: entity.text
        onEffect(EntityEffect.InjectQuestion("$name 是什么意思"))
        onEffect(EntityEffect.Haptic)
    }

    // ===== 生命周期 =====

    /** 新会话：清掉预览、拖拽与二义候选（对应页面 `resetSessionUiState`）。 */
    fun resetForNewSession() {
        peekVersion++
        setPeekSymbol("")
        setPeekVisible(false)
        clearDrag()
        state.clearAmbiguousCandidates()
        state.ambiguousText = ""
        pendingLongPressSymbol = ""
        pendingLongPressTermKey = ""
        suppressNextStockClickSymbol = ""
        suppressNextTermClick = ""
    }

    fun onDestroy() {
        peekVersion++
        tasks.forEach(EntityScheduledTask::cancel)
        tasks.clear()
    }

    // ===== 内部工具 =====

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }

    private fun setPeekSymbol(value: String) { peekSymbol = value; state.peekSymbol = value }
    private fun setPeekVisible(value: Boolean) { peekVisible = value; state.peekVisible = value }
    private fun setDragActive(value: Boolean) { dragActive = value; state.dragActive = value }
    private fun setDropTarget(value: EntityDropTarget) { dropTarget = value; state.dropTarget = value }
    private fun setAmbiguousAction(value: EntityAction) { ambiguousAction = value; state.ambiguousAction = value }

    private companion object {
        const val PEEK_PRESENT_MS = 16
        const val PEEK_FADE_MS = 180
        const val PEEK_AUTO_DISMISS_MS = 1_500
        const val CLICK_SUPPRESS_MS = 400
    }
}
