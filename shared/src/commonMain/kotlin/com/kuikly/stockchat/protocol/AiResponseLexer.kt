package com.kuikly.stockchat.protocol

sealed interface MixedBlock {
    val id: String
}

data class TextBlock(override val id: String, val content: String) : MixedBlock
data class CardBlock(override val id: String, val type: String, val payload: String) : MixedBlock
data class SkeletonBlock(override val id: String, val type: String) : MixedBlock

object AiResponseLexer {
    private const val CARD_PREFIX = "```card:"

    fun lex(content: String, finished: Boolean = true): List<MixedBlock> {
        if (content.isEmpty()) return emptyList()
        val blocks = mutableListOf<MixedBlock>()
        var cursor = 0
        var index = 0
        while (cursor < content.length) {
            val fenceStart = content.indexOf(CARD_PREFIX, cursor)
            if (fenceStart < 0) {
                addText(blocks, index, content.substring(cursor))
                break
            }
            addText(blocks, index++, content.substring(cursor, fenceStart))
            val typeStart = fenceStart + CARD_PREFIX.length
            val headerEnd = content.indexOf('\n', typeStart)
            if (headerEnd < 0) {
                if (!finished) blocks += SkeletonBlock("skeleton:$index", content.substring(typeStart).trim())
                else addText(blocks, index, content.substring(fenceStart))
                break
            }
            val type = content.substring(typeStart, headerEnd).trim()
            val fenceEnd = content.indexOf("```", headerEnd + 1)
            if (fenceEnd < 0) {
                if (!finished) blocks += SkeletonBlock("skeleton:$index", type)
                else blocks += CardBlock("card:$index:$type", type, content.substring(headerEnd + 1).trim())
                break
            }
            val payload = content.substring(headerEnd + 1, fenceEnd).trim()
            blocks += CardBlock("card:$index:$type", type, payload)
            index++
            cursor = fenceEnd + 3
        }
        return blocks
    }

    private fun addText(blocks: MutableList<MixedBlock>, index: Int, raw: String) {
        val value = raw.trim()
        if (value.isNotEmpty()) blocks += TextBlock("text:$index", value)
    }
}
