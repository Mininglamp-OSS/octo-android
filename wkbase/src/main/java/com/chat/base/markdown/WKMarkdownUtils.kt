package com.chat.base.markdown

object WKMarkdownUtils {

    private val MARKDOWN_PATTERNS = arrayOf(
        "**", "__", "~~", "```", "`",
        "# ", "## ", "### ",
        "- ", "* ", "+ ",
        "> ",
        "[", "](",
        "---", "***",
        "| ", "|-"
    )

    /**
     * 检测文本是否包含 Markdown 语法
     */
    @JvmStatic
    fun containsMarkdown(text: String): Boolean {
        for (pattern in MARKDOWN_PATTERNS) {
            if (text.contains(pattern)) {
                return true
            }
        }
        return false
    }
}
