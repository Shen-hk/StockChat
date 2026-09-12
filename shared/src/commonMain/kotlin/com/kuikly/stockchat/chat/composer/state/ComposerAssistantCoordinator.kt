package com.kuikly.stockchat.chat.composer.state

import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.SlashCommand
import com.kuikly.stockchat.composer.TriggerSession
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

internal enum class AssistantPanel { NONE, AT_MENTION, SLASH, COMMAND_PARAMS }

/** Reactive presentation state for @, slash and command-parameter panels. */
internal interface ComposerAssistantStatePort {
    var panel: AssistantPanel
    var triggerSession: TriggerSession?
    var composing: Boolean
    var atHighlight: Int
    var slashHighlight: Int
    var slashUnknown: String
    var paramCommand: SlashCommand?
    var validationMessage: String
    val atCandidates: MutableList<AtCandidate>
    val slashCandidates: MutableList<SlashCommand>
    val paramPanelRenderKey: MutableList<Int>
}

internal class ComposerAssistantState : ComposerAssistantStatePort {
    override var panel: AssistantPanel by observable(AssistantPanel.NONE)
    override var triggerSession: TriggerSession? = null
    override var composing: Boolean by observable(false)
    override var atHighlight: Int by observable(0)
    override var slashHighlight: Int by observable(0)
    override var slashUnknown: String by observable("")
    override var paramCommand: SlashCommand? by observable(null)
    override var validationMessage: String by observable("")
    private var observableAtCandidates: ObservableList<AtCandidate> by observableList()
    private var observableSlashCandidates: ObservableList<SlashCommand> by observableList()
    private var observableParamRenderKey: ObservableList<Int> by observableList()
    override val atCandidates: MutableList<AtCandidate> get() = observableAtCandidates
    override val slashCandidates: MutableList<SlashCommand> get() = observableSlashCandidates
    override val paramPanelRenderKey: MutableList<Int> get() = observableParamRenderKey
}

internal class PlainComposerAssistantState : ComposerAssistantStatePort {
    override var panel = AssistantPanel.NONE
    override var triggerSession: TriggerSession? = null
    override var composing = false
    override var atHighlight = 0
    override var slashHighlight = 0
    override var slashUnknown = ""
    override var paramCommand: SlashCommand? = null
    override var validationMessage = ""
    override val atCandidates = mutableListOf<AtCandidate>()
    override val slashCandidates = mutableListOf<SlashCommand>()
    override val paramPanelRenderKey = mutableListOf<Int>()
}

/** Owns panel reset and candidate replacement so no stale highlight survives a panel switch. */
internal class ComposerAssistantCoordinator(val state: ComposerAssistantStatePort) {
    fun showAt(candidates: List<AtCandidate>) {
        state.panel = AssistantPanel.AT_MENTION
        replaceAtCandidates(candidates)
    }

    fun showSlash(candidates: List<SlashCommand>, unknown: String) {
        state.panel = AssistantPanel.SLASH
        state.slashCandidates.clear()
        state.slashCandidates.addAll(candidates)
        state.slashHighlight = 0
        state.slashUnknown = unknown
    }

    fun showCommandParams(command: SlashCommand) {
        state.paramCommand = command
        state.panel = AssistantPanel.COMMAND_PARAMS
        state.slashCandidates.clear()
        state.slashUnknown = ""
    }

    fun replaceAtCandidates(candidates: List<AtCandidate>) {
        state.atCandidates.clear()
        state.atCandidates.addAll(candidates)
        state.atHighlight = 0
    }

    fun closePanel() {
        if (state.panel == AssistantPanel.NONE && state.triggerSession == null) return
        state.panel = AssistantPanel.NONE
        state.triggerSession = null
        state.atCandidates.clear()
        state.slashCandidates.clear()
        state.slashUnknown = ""
        state.atHighlight = 0
        state.slashHighlight = 0
    }

    fun bumpParamRenderKey() {
        state.paramPanelRenderKey.clear()
        state.paramPanelRenderKey.add(0)
    }
}
