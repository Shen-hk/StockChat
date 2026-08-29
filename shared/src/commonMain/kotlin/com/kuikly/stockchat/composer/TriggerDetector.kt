package com.kuikly.stockchat.composer

/**
 * 输入栏 @ / 触发会话（规范见 docs/10-输入栏@提及与斜杠指令交互规范_v1.0.md §3.1）。
 *
 * 从用户敲下触发字符（@ 或 /）到该次触发被终结之间的一段状态。
 * 同一时刻最多存在一个活动 TriggerSession。
 */
data class TriggerSession(
    /** 触发字符类型：'@' 或 '/' */
    val type: Char,
    /** 触发字符在文本中的偏移 */
    val triggerStart: Int,
    /** 触发词内容（不含触发字符本身） */
    val query: String,
    /** 触发会话被观测时的光标位置 */
    val cursor: Int,
)

/**
 * 光标词法判定（规范 §3.2）。
 *
 * 每次 textChanged / selectionChange 时运行：从光标位置向左扫描触发字符，
 * 途中只允许「词内字符」；触发字符的前一个字符必须是「边界」（空串/空白/换行）。
 * "abc@163.com" 与 "https://" 因此不会误触发。
 */
object TriggerDetector {

    /**
     * @param text 当前输入框全文
     * @param cursor 光标偏移（selectionEnd）
     * @return 活动触发会话；无则 null
     */
    fun detect(text: String, cursor: Int): TriggerSession? {
        if (cursor <= 0 || cursor > text.length) return null
        var i = cursor - 1
        while (i >= 0) {
            val c = text[i]
            if (c == '@' || c == '/') {
                // 触发字符的前一个字符必须是边界：行首或空白
                val prev = if (i == 0) ' ' else text[i - 1]
                if (!prev.isWhitespace()) return null
                val query = text.substring(i + 1, cursor)
                return TriggerSession(c, i, query, cursor)
            }
            if (c.isWhitespace()) return null
            i--
        }
        return null
    }
}
