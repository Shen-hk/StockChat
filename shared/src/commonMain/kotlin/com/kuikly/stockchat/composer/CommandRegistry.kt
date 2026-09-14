package com.kuikly.stockchat.composer

/**
 * / 斜杠指令注册表（规范 §5.1）。
 *
 * 单一事实源：命令定义、别名、参数 schema、执行路径。
 * 未知命令优雅降级为普通文本，绝不阻塞发送。
 */
enum class CommandExecution { PROMPT_TEMPLATE, LOCAL_ACTION, CONTEXT_ONLY }

enum class ParamType { SECURITY, TEXT, ENUM }

data class CommandParam(
    val key: String,
    val label: String,
    val type: ParamType,
    val required: Boolean,
    val enumOptions: List<String> = emptyList(),
    val placeholder: String = "",
)

data class SlashCommand(
    val id: String,
    val name: String,
    val icon: String,
    val desc: String,
    val aliases: List<String>,
    val params: List<CommandParam>,
    val execution: CommandExecution,
) {
    val requiredParams: List<CommandParam> get() = params.filter { it.required }
    val hasParams: Boolean get() = params.isNotEmpty()
}

object CommandRegistry {

    val all: List<SlashCommand> = listOf(
        SlashCommand(
            id = "monitor", name = "盯盘", icon = "◎",
            desc = "发送后识别标的并建立默认风险预警",
            aliases = listOf("dingpan", "dp"),
            params = listOf(
                CommandParam("target", "标的", ParamType.SECURITY, required = false, placeholder = "输入 @股票名或代码后发送"),
            ),
            execution = CommandExecution.LOCAL_ACTION,
        ),
        SlashCommand(
            id = "clear", name = "清屏", icon = "◎",
            desc = "清空当前会话消息（本地动作）",
            aliases = listOf("qingping", "qp"),
            params = emptyList(),
            execution = CommandExecution.LOCAL_ACTION,
        ),
    )

    /** 名称 / 别名前缀过滤（面板用） */
    fun filter(query: String): List<SlashCommand> {
        if (query.isEmpty()) return all
        val ql = query.lowercase()
        return all.filter { cmd ->
            cmd.name.contains(query) || cmd.aliases.any { it.startsWith(ql) }
        }
    }

    /** 精确匹配命令名或别名（命令名定型用） */
    fun resolve(name: String): SlashCommand? {
        if (name.isEmpty()) return null
        val nl = name.lowercase()
        return all.firstOrNull { cmd ->
            cmd.name == name || cmd.aliases.any { it == nl }
        }
    }

    /** 未知命令的近似建议（简化编辑距离：双向包含任一字符） */
    fun suggest(query: String): List<SlashCommand> {
        if (query.isEmpty()) return emptyList()
        return all.filter { cmd -> cmd.name.any { query.contains(it) } }.take(2)
    }

    /**
     * 保留统一调用入口；当前保留下来的指令均为本地动作，不再生成“看似可用”的 AI 模板。
     */
    fun renderPrompt(cmd: SlashCommand, args: Map<String, String>): String {
        return args["target"]?.trim().orEmpty()
    }
}
