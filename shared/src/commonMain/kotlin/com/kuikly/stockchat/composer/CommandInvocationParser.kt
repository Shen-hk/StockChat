package com.kuikly.stockchat.composer

/**
 * Parses slash-command text into the structured invocation consumed by the send pipeline.
 *
 * Keeping this policy outside the page makes the composer UI a coordinator rather than the
 * owner of command syntax. Security lookup stays injectable so parsing has no repository or
 * platform dependency and can be tested deterministically.
 */
object CommandInvocationParser {

    fun parse(
        text: String,
        mentions: List<MentionEntity>,
        isExactSecurity: (String) -> Boolean,
    ): CommandInvocation? {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("/")) return null

        val name = trimmed.removePrefix("/").substringBefore(' ').substringBefore('@')
        val command = CommandRegistry.resolve(name) ?: return null
        val remainder = remainder(trimmed, name)

        var stripped = remainder
        for (mention in mentions) stripped = stripped.replace(mention.mentionText, MENTION_SEPARATOR)
        val fragments = stripped
            .split(FRAGMENT_SEPARATOR)
            .filter { it.isNotBlank() }

        val securityMentions = mentions.iterator()
        var fragmentIndex = 0
        val args = LinkedHashMap<String, String>()
        for (param in command.params) {
            when (param.type) {
                ParamType.SECURITY -> {
                    val mention = if (securityMentions.hasNext()) securityMentions.next() else null
                    val fragment = fragments.getOrNull(fragmentIndex)
                    args[param.key] = when {
                        mention != null -> mention.name
                        fragment != null && isExactSecurity(fragment) -> {
                            fragmentIndex++
                            fragment
                        }
                        else -> ""
                    }
                }

                ParamType.ENUM -> {
                    val fragment = fragments.getOrNull(fragmentIndex)
                    args[param.key] = if (fragment != null && fragment in param.enumOptions) {
                        fragmentIndex++
                        fragment
                    } else {
                        ""
                    }
                }

                ParamType.TEXT -> {
                    args[param.key] = fragments.drop(fragmentIndex).joinToString(" ")
                    fragmentIndex = fragments.size
                }
            }
        }
        return CommandInvocation(command.id, command.name, args)
    }

    fun remainder(text: String, commandName: String): String {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("/")) return ""
        val typedName = trimmed.removePrefix("/").substringBefore(' ').substringBefore('@')
        val resolved = CommandRegistry.resolve(typedName) ?: return ""
        if (resolved.name != commandName) return ""
        return trimmed.removePrefix("/$typedName").trimStart()
    }

    fun currentParameterQuery(text: String, commandName: String): String {
        val tail = remainder(text, commandName).trimEnd()
        return tail.substringAfterLast(' ').removePrefix("@")
    }

    fun missingRequiredParams(
        command: SlashCommand,
        args: Map<String, String>,
    ): List<CommandParam> = command.params.filter { it.required && args[it.key].isNullOrBlank() }

    private const val MENTION_SEPARATOR = "\u0001"
    private val FRAGMENT_SEPARATOR = Regex("\\s+|$MENTION_SEPARATOR+")
}
