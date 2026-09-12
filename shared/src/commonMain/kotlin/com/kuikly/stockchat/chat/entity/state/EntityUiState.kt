package com.kuikly.stockchat.chat.entity.state

import com.kuikly.stockchat.richtext.EntityDropTarget
import com.kuikly.stockchat.richtext.EntitySpan
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

/**
 * 实体交互动作（自 `page/ChatPage` 迁入，原为文件内 `private enum`，ownership 归实体交互状态层）。
 * 二义实体被选中后要按同一动作继续执行，故需随状态一起保存（`ambiguousAction`）。
 */
internal enum class EntityAction { DETAIL, PREVIEW, ISLAND, MENTION, COMPARE }

/**
 * 实体交互可渲染状态端口。Kuikly 实现用 `observable`，测试用 Plain 实现。
 *
 * `draggedEntity` 迁移前即为**非 observable** 裸字段（拖拽浮层挂在
 * `vif({ entityDragActive })` 下，只在 dragActive 翻转时求值），此处保持普通属性
 * 以维持逐字节一致的行为；其余字段与迁移前的 observable 一一对应。
 *
 * 二义候选只在 `setAmbiguousCandidates` / `clearAmbiguousCandidates` 两处写入，
 * 对外只暴露只读 `List` 视图（DSL 读它是为了 `isNotEmpty()` 判定与 `forEach` 渲染）。
 */
internal interface EntityStatePort {
    // ===== 行情预览气泡（静止长按 / 预览动作）=====
    var peekSymbol: String
    var peekVisible: Boolean

    // ===== 拖拽 =====
    var draggedEntity: EntitySpan?
    var dragActive: Boolean
    var dragX: Float
    var dragY: Float
    var dragName: String
    var dropTarget: EntityDropTarget

    // ===== 二义实体选择 =====
    val ambiguousSymbols: List<String>
    fun setAmbiguousCandidates(candidates: List<String>)
    fun clearAmbiguousCandidates()
    var ambiguousText: String
    var ambiguousAction: EntityAction
}

internal class EntityState : EntityStatePort {
    override var peekSymbol: String by observable("")
    override var peekVisible: Boolean by observable(false)

    override var draggedEntity: EntitySpan? = null
    override var dragActive: Boolean by observable(false)
    override var dragX: Float by observable(0f)
    override var dragY: Float by observable(0f)
    override var dragName: String by observable("")
    override var dropTarget: EntityDropTarget by observable(EntityDropTarget.NONE)

    private var symbols: ObservableList<String> by observableList()
    override val ambiguousSymbols: List<String> get() = symbols
    override fun setAmbiguousCandidates(candidates: List<String>) {
        symbols.clear()
        symbols.addAll(candidates)
    }
    override fun clearAmbiguousCandidates() {
        symbols.clear()
    }
    override var ambiguousText: String by observable("")
    override var ambiguousAction: EntityAction by observable(EntityAction.PREVIEW)
}

internal class PlainEntityState : EntityStatePort {
    override var peekSymbol = ""
    override var peekVisible = false

    override var draggedEntity: EntitySpan? = null
    override var dragActive = false
    override var dragX = 0f
    override var dragY = 0f
    override var dragName = ""
    override var dropTarget = EntityDropTarget.NONE

    private val symbols = mutableListOf<String>()
    override val ambiguousSymbols: List<String> get() = symbols
    override fun setAmbiguousCandidates(candidates: List<String>) {
        symbols.clear()
        symbols.addAll(candidates)
    }
    override fun clearAmbiguousCandidates() {
        symbols.clear()
    }
    override var ambiguousText = ""
    override var ambiguousAction = EntityAction.PREVIEW
}
