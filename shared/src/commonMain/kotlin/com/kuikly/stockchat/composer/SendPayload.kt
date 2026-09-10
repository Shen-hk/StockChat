package com.kuikly.stockchat.composer

/**
 * 结构化发送协议（规范 §4.8）。
 *
 * 输入期固化的意图随消息一起发送：mentions 直接写入 system context，
 * EntityRecognizer 降级为回答内容识别的补充管线。
 */
data class MentionEntity(
    val symbol: String,
    val name: String,
    val type: MentionType,
    /** 输入框中的固化文本，如 "@贵州茅台"、"@白酒(板块)"；用于与文本对账 */
    val mentionText: String,
) {
    companion object {
        fun of(entry: CatalogEntry): MentionEntity {
            val text = when (entry.kind) {
                MentionType.BOARD -> "@${entry.name}(板块)"
                else -> "@${entry.name}"
            }
            return MentionEntity(entry.symbol, entry.name, entry.kind, text)
        }
    }
}

data class CommandInvocation(
    val commandId: String,
    val commandName: String,
    /** 已填参数：key → 人类可读值 */
    val args: Map<String, String>,
) {
    /** 用户气泡里的"人话"形式，不显示渲染后的长 prompt */
    val displayText: String
        get() = buildString {
            append('/').append(commandName)
            args.values.forEach { append(' ').append(it) }
        }
}

data class SendPayload(
    /** 输入框原文（含 @xxx 文本），用于 UI 气泡回显 */
    val text: String,
    /** 输入期固化并通过对账的结构化提及 */
    val mentions: List<MentionEntity>,
    /** 指令调用，可为空 */
    val command: CommandInvocation?,
    /** PROMPT_TEMPLATE 命令渲染后的最终 prompt（无命令时为 null） */
    val renderedPrompt: String?,
    /** 深水区等上下文标记 */
    val contextNotes: List<String> = emptyList(),
    /** 输入栏附件快照，随用户消息回显气泡（本轮不进 AI 请求与持久化） */
    val attachments: List<com.kuikly.stockchat.chat.MessageAttachment> = emptyList(),
) {
    /** 用户气泡显示文本 */
    val displayText: String
        get() = command?.displayText?.let { cmd ->
            // 指令 + 提及 + 余文拼成人话形式
            val rest = text
                .replaceFirst(Regex("^\\s*/[^\\s@]*"), "")
                .trim()
            buildString {
                append(cmd)
                if (rest.isNotEmpty()) append(' ').append(rest)
            }
        } ?: text.trim()

    /**
     * 注入 system context 的注记（规范 §4.8 服务端 prompt 组装）。
     * 空注记返回 null，保持无 @ 提问的 prompt 与改造前完全一致。
     */
    fun systemNote(): String? {
        val parts = mutableListOf<String>()
        if (mentions.isNotEmpty()) {
            val list = mentions.joinToString("；") { m ->
                "${m.symbol} ${m.name}（${labelOf(m.type)}）"
            }
            parts += "用户在本次提问中通过 @ 明确提及：$list。请优先围绕这些标的回答。"
            val boards = mentions.filter { it.type == MentionType.BOARD }
            if (boards.isNotEmpty()) {
                val boardContext = boards.mapNotNull { board ->
                    val constituents = ComposerCatalog.boardConstituents(board.symbol)
                    if (constituents.isEmpty()) null
                    else "${board.name}板块代表成分：${constituents.joinToString("、") { "${it.symbol} ${it.name}" }}"
                }
                if (boardContext.isNotEmpty()) {
                    parts += boardContext.joinToString("；") + "。"
                }
            }
        }
        if (command != null) {
            parts += "用户调用了指令「/${command.commandName}」，参数：${command.args}。请按指令意图作答。"
        }
        if (contextNotes.isNotEmpty()) {
            parts += "上下文标记：${contextNotes.joinToString("、")}。"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun labelOf(type: MentionType): String = when (type) {
        MentionType.STOCK -> "股票"
        MentionType.INDEX -> "指数"
        MentionType.BOARD -> "板块"
        MentionType.ETF -> "ETF"
    }
}

/**
 * 固化 token 注册表（规范 §4.6/§4.7）。
 *
 * 注册表只保存实体顺序；激活态（是否仍在文本中）每次由 [verify] 从当前文本
 * 重新判定——不做 range 偏移补偿，编辑后永远与文本对齐。
 *
 * 注：规范里的「视觉固化」（把 @贵州茅台 渲染成高亮 chip、退格一次删整只）依赖
 * TextArea 的 inputSpans 富文本能力，而该能力在 H5 渲染层被直接忽略，原生端
 * span 一旦与文本不同步又会损坏输入内容，故本轮只落地发送侧对账，视觉固化
 * 待有真机验证条件时再补。
 */
object SolidTokenRegistry {

    /**
     * 发送前对账（规范 §4.8 三道校验）：
     * 1. token 文本仍精确存在于文本中（用户改过则该提及剔除）；
     * 2. 同 symbol 去重；
     * 3. 返回通过校验的提及列表。
     */
    fun verify(entities: List<MentionEntity>, text: String): List<MentionEntity> {
        val out = mutableListOf<MentionEntity>()
        val seen = mutableSetOf<String>()
        for (entity in entities) {
            if (entity.symbol in seen) continue
            if (text.contains(entity.mentionText)) {
                out += entity
                seen += entity.symbol
            }
        }
        return out
    }
}
