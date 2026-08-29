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
            id = "fupan", name = "复盘", icon = "◔",
            desc = "盘后速览当日/当周行情与资金",
            aliases = listOf("review", "fupan", "fp"),
            params = listOf(
                CommandParam("target", "标的", ParamType.SECURITY, required = false, placeholder = "留空看大盘"),
                CommandParam("period", "周期", ParamType.ENUM, required = false, enumOptions = listOf("日", "周", "月")),
            ),
            execution = CommandExecution.PROMPT_TEMPLATE,
        ),
        SlashCommand(
            id = "compare", name = "对比", icon = "⇄",
            desc = "两只标的多维度对比表",
            aliases = listOf("vs", "duibi", "db"),
            params = listOf(
                CommandParam("left", "标的A", ParamType.SECURITY, required = true, placeholder = "输入代码或名称"),
                CommandParam("right", "标的B", ParamType.SECURITY, required = true, placeholder = "输入代码或名称"),
                CommandParam("dim", "维度", ParamType.ENUM, required = false, enumOptions = listOf("估值", "成长", "动量")),
            ),
            execution = CommandExecution.PROMPT_TEMPLATE,
        ),
        SlashCommand(
            id = "explain", name = "解读", icon = "◎",
            desc = "深度解读一只标的的基本面",
            aliases = listOf("jiedu", "jd"),
            params = listOf(
                CommandParam("target", "标的", ParamType.SECURITY, required = true, placeholder = "输入代码或名称"),
            ),
            execution = CommandExecution.PROMPT_TEMPLATE,
        ),
        SlashCommand(
            id = "monitor", name = "盯盘", icon = "◎",
            desc = "建立条件监控，触发即提醒",
            aliases = listOf("dingpan", "dp"),
            params = listOf(
                CommandParam("target", "标的", ParamType.SECURITY, required = true, placeholder = "输入代码或名称"),
                CommandParam("cond", "条件", ParamType.TEXT, required = true, placeholder = "如：跌破20日线"),
            ),
            execution = CommandExecution.PROMPT_TEMPLATE,
        ),
        SlashCommand(
            id = "plan", name = "预案", icon = "◎",
            desc = "生成操作预案（仓位/止损位）",
            aliases = listOf("yuan", "ya"),
            params = listOf(
                CommandParam("target", "标的", ParamType.SECURITY, required = true, placeholder = "输入代码或名称"),
            ),
            execution = CommandExecution.PROMPT_TEMPLATE,
        ),
        SlashCommand(
            id = "term", name = "术语", icon = "◎",
            desc = "解释一个金融术语",
            aliases = listOf("shuyu", "sy"),
            params = listOf(
                CommandParam("word", "词条", ParamType.TEXT, required = true, placeholder = "如：夏普比率"),
            ),
            execution = CommandExecution.PROMPT_TEMPLATE,
        ),
        SlashCommand(
            id = "deep", name = "深水区", icon = "◎",
            desc = "切换回答深度模式（上下文标记）",
            aliases = listOf("shenshui", "ssq"),
            params = emptyList(),
            execution = CommandExecution.CONTEXT_ONLY,
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
     * PROMPT_TEMPLATE 命令的本地模板渲染（规范 §5.5）。
     * @param args 已填参数（key → 值；SECURITY 槽的值为 "symbol name"）
     */
    fun renderPrompt(cmd: SlashCommand, args: Map<String, String>): String {
        val p = { key: String -> args[key]?.trim().orEmpty() }
        return when (cmd.id) {
            "fupan" -> {
                val target = p("target").ifEmpty { "大盘（上证指数）" }
                val period = p("period").ifEmpty { "日" }
                "请对${target}做${period}线级别复盘：行情走势、资金流向、关键位与次日关注点。"
            }
            "compare" -> {
                val dim = p("dim").ifEmpty { "估值、成长、动量" }
                "请从${dim}维度对比${p("left")}与${p("right")}，输出对比表与结论。"
            }
            "explain" -> "请深度解读${p("target")}：基本面、行业地位、估值水位与主要风险。"
            "monitor" -> "请为${p("target")}设计盯盘规则：触发条件「${p("cond")}」，给出信号确认方式与应对预案。"
            "plan" -> "请为${p("target")}生成操作预案：建仓区间、仓位建议、止损位与止盈参考。"
            "term" -> "请解释金融术语「${p("word")}」：一句话定义、一个例子、在投资决策中怎么用。"
            else -> p("target")
        }
    }
}
