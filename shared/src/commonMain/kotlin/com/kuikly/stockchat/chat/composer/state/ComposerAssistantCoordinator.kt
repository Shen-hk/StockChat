package com.kuikly.stockchat.chat.composer.state

import com.kuikly.stockchat.composer.AtCandidate
import com.kuikly.stockchat.composer.CatalogEntry
import com.kuikly.stockchat.composer.MentionEntity
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
    val remoteEntries: MutableList<CatalogEntry>
    var remoteSearchGeneration: Int
    var quoteGeneration: Int
    val requestedQuoteSymbols: MutableSet<String>
    var lastTrackedTriggerKey: String
    var lastTrackedUnknownSlash: String
    val mentionEntities: MutableList<MentionEntity>
    val deepContextNotes: MutableList<String>
    var deepContextVersion: Int
    var routeFocusMention: MentionEntity?
    val recentMentions: MutableList<String>
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
    private var observableRemoteEntries: ObservableList<CatalogEntry> by observableList()
    override val atCandidates: MutableList<AtCandidate> get() = observableAtCandidates
    override val slashCandidates: MutableList<SlashCommand> get() = observableSlashCandidates
    override val paramPanelRenderKey: MutableList<Int> get() = observableParamRenderKey
    override val remoteEntries: MutableList<CatalogEntry> get() = observableRemoteEntries
    override var remoteSearchGeneration = 0
    override var quoteGeneration = 0
    override val requestedQuoteSymbols = mutableSetOf<String>()
    override var lastTrackedTriggerKey = ""
    override var lastTrackedUnknownSlash = ""
    override val mentionEntities = mutableListOf<MentionEntity>()
    override val deepContextNotes = mutableListOf<String>()
    override var deepContextVersion: Int by observable(0)
    override var routeFocusMention: MentionEntity? = null
    override val recentMentions = mutableListOf<String>()
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
    override val remoteEntries = mutableListOf<CatalogEntry>()
    override var remoteSearchGeneration = 0
    override var quoteGeneration = 0
    override val requestedQuoteSymbols = mutableSetOf<String>()
    override var lastTrackedTriggerKey = ""
    override var lastTrackedUnknownSlash = ""
    override val mentionEntities = mutableListOf<MentionEntity>()
    override val deepContextNotes = mutableListOf<String>()
    override var deepContextVersion = 0
    override var routeFocusMention: MentionEntity? = null
    override val recentMentions = mutableListOf<String>()
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

    fun nextRemoteSearchGeneration(): Int = ++state.remoteSearchGeneration

    fun isCurrentRemoteSearch(generation: Int): Boolean = generation == state.remoteSearchGeneration

    fun mergeRemoteEntries(entries: List<CatalogEntry>, limit: Int, isLocalSymbol: (String) -> Boolean): Boolean {
        var added = false
        entries.forEach { entry ->
            if (entry.symbol.isBlank() || isLocalSymbol(entry.symbol)) return@forEach
            if (state.remoteEntries.any { it.symbol == entry.symbol }) return@forEach
            if (state.remoteEntries.size >= limit) state.remoteEntries.clear()
            state.remoteEntries.add(entry)
            added = true
        }
        return added
    }

    fun nextQuoteGeneration(): Int = ++state.quoteGeneration

    fun isCurrentQuoteGeneration(generation: Int): Boolean = generation == state.quoteGeneration

    fun markQuoteRequested(symbol: String): Boolean = state.requestedQuoteSymbols.add(symbol)

    fun applyChgPct(symbol: String, pct: Float?): Boolean {
        if (pct == null) return false
        if (state.atCandidates.any { it.entry.symbol == symbol && it.entry.chgPct == null }) {
            val updated = state.atCandidates.map { candidate ->
                if (candidate.entry.symbol == symbol) candidate.copy(entry = candidate.entry.copy(chgPct = pct)) else candidate
            }
            state.atCandidates.clear()
            state.atCandidates.addAll(updated)
        }
        val remoteIndex = state.remoteEntries.indexOfFirst { it.symbol == symbol }
        if (remoteIndex >= 0) {
            val updated = state.remoteEntries.toMutableList()
            updated[remoteIndex] = updated[remoteIndex].copy(chgPct = pct)
            state.remoteEntries.clear()
            state.remoteEntries.addAll(updated)
        }
        return true
    }
}
