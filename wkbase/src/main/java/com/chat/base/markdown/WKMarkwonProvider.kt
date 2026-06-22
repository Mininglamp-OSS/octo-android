/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        val spanned = getInstance(context).toMarkdown(ensureTableTermination(text))
        val tables = WKTablePlugin.consumeTableData()
        return Pair(spanned, tables)
    }

    /**
     * commonmark-java 的 GFM tables 扩展对边界很敏感, 两端都要有空行才识别:
     *
     * - 表格之后: 非空非 `|` 行紧跟最后一行 `|...|` 会被当成 table body 多列, 后续段落被吞;
     * - 表格之前: header `|...|` 行紧贴上一行非空非 `|` 文字时, 整段被当作多行 paragraph,
     *   GFM TableBlockParser 要求 header 所在 paragraph 只能是单行, 否则放弃当表格解析,
     *   header 和 `|---|---|` 都退化成普通文字 (用户实测 msg=AB1E44DE rawHasPipe=true
     *   rawHasSep=true 但 tables=0, 就是这条路径)。
     *
     * 在两端缺空行时补一行, 不动正文。
     */
    private fun ensureTableTermination(text: String): String {
        val lines = text.split('\n')
        val result = StringBuilder()
        for (i in lines.indices) {
            // 进 |-header 行前: 上一行非空且不是 |-row, 且当前是 header (后跟 |---|---|),
            // 在 result 末尾插空行让 paragraph 闭合, header 起新块。
            if (i > 0
                && lines[i].trimStart().startsWith("|")
                && i + 1 < lines.size
                && isTableDelimiterLine(lines[i + 1])
            ) {
                val prevTrimmed = lines[i - 1].trim()
                if (prevTrimmed.isNotEmpty() && !prevTrimmed.startsWith("|")) {
                    result.append('\n')
                }
            }
            result.append(lines[i])
            if (i < lines.size - 1) {
                result.append('\n')
                if (lines[i].trimStart().startsWith("|")) {
                    val nextTrimmed = lines[i + 1].trimStart()
                    if (nextTrimmed.isNotEmpty() && !nextTrimmed.startsWith("|")) {
                        result.append('\n')
                    }
                }
            }
        }
        return result.toString()
    }

    /**
     * 判断是否 GFM 表格分隔行: `|---|---|` / `| :---: | ---: |` / `--- | ---` 等。
     * 只允许 `|`, `-`, `:`, 空白; 必须含 `-` 且 trim 后非空。`|` 可以缺 (开闭 `|` 都是可选的)。
     */
    private fun isTableDelimiterLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.contains('-')) return false
        return trimmed.all { c -> c == '|' || c == '-' || c == ':' || c == ' ' || c == '\t' }
    }

    @JvmStatic
    fun toMarkdown(context: Context, text: String): Spanned {
        return getInstance(context).toMarkdown(text)
    }

    private fun dp2px(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
