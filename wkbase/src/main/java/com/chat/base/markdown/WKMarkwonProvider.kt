package com.chat.base.markdown

import android.content.Context
import android.content.res.Configuration
import android.text.Spanned
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import org.commonmark.node.SoftLineBreak

object WKMarkwonProvider {

    @Volatile
    private var markwon: Markwon? = null

    @Volatile
    private var cachedIsDarkMode: Boolean? = null

    private fun isDarkMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getInstance(context: Context): Markwon {
        val currentDarkMode = isDarkMode(context)
        val cached = markwon
        if (cached != null && cachedIsDarkMode == currentDarkMode) {
            return cached
        }
        synchronized(this) {
            val currentDarkModeSync = isDarkMode(context)
            val cachedSync = markwon
            if (cachedSync != null && cachedIsDarkMode == currentDarkModeSync) {
                return cachedSync
            }
            return createMarkwon(context.applicationContext, currentDarkModeSync).also {
                markwon = it
                cachedIsDarkMode = currentDarkModeSync
            }
        }
    }

    private fun createMarkwon(context: Context, isDark: Boolean): Markwon {
        val prism4j = WKPrism4jFactory.create()
        val codeBlockPlugin = WKCodeBlockPlugin.create(context)
        val prism4jTheme = if (isDark) {
            Prism4jThemeDarkula.create(codeBlockPlugin.codeBlockBackgroundColor)
        } else {
            Prism4jThemeDefault.create(codeBlockPlugin.codeBlockBackgroundColor)
        }

        // 将单换行（soft break）渲染为实际换行，而非 CommonMark 默认的空格合并
        val softBreakPlugin = object : AbstractMarkwonPlugin() {
            override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                builder.on(SoftLineBreak::class.java) { visitor, _ ->
                    visitor.ensureNewLine()
                }
            }
        }

        return Markwon.builder(context)
            .usePlugin(codeBlockPlugin)
            .usePlugin(softBreakPlugin)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(WKTablePlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, prism4jTheme))
            .build()
    }

    /**
     * 渲染 Markdown 文本，同时提取表格数据。
     * @return Pair<Spanned, List<WKTableData>> - 渲染后的文本（不含表格）和表格数据列表
     */
    @JvmStatic
    fun toMarkdownWithTables(context: Context, text: String): Pair<Spanned, List<WKTableData>> {
        WKTablePlugin.clearPending()
        val spanned = getInstance(context).toMarkdown(text)
        val tables = WKTablePlugin.consumeTableData()
        return Pair(spanned, tables)
    }

    @JvmStatic
    fun toMarkdown(context: Context, text: String): Spanned {
        return getInstance(context).toMarkdown(text)
    }

    private fun dp2px(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
