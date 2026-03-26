package com.chat.base.markdown

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/**
 * 自定义表格渲染插件：将 Markdown 表格转为简洁的文本格式 + 等宽字体。
 * 替代 Markwon 默认的 TablePlugin（Span 方式在聊天气泡中会出现文字重叠）。
 */
class WKTablePlugin private constructor() : AbstractMarkwonPlugin() {

    companion object {
        @JvmStatic
        fun create(): WKTablePlugin = WKTablePlugin()
    }

    override fun configureParser(builder: Parser.Builder) {
        builder.extensions(listOf(TablesExtension.create()))
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(org.commonmark.ext.gfm.tables.TableBlock::class.java) { visitor, tableBlock ->
            val headerRows = mutableListOf<List<String>>()
            val bodyRows = mutableListOf<List<String>>()

            var child: Node? = tableBlock.firstChild
            while (child != null) {
                when (child) {
                    is TableHead -> {
                        var row: Node? = child.firstChild
                        while (row != null) {
                            if (row is TableRow) headerRows.add(extractCells(row))
                            row = row.next
                        }
                    }
                    is TableBody -> {
                        var row: Node? = child.firstChild
                        while (row != null) {
                            if (row is TableRow) bodyRows.add(extractCells(row))
                            row = row.next
                        }
                    }
                }
                child = child.next
            }

            val allRows = headerRows + bodyRows
            if (allRows.isEmpty()) return@on

            visitor.ensureNewLine()
            val tableStart = visitor.length()

            // 渲染表头
            for (row in headerRows) {
                val headerStart = visitor.length()
                visitor.builder().append(row.joinToString(" | "))
                visitor.builder().setSpan(
                    StyleSpan(Typeface.BOLD), headerStart, visitor.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                visitor.ensureNewLine()
                // 分隔线
                visitor.builder().append("─".repeat(row.joinToString(" | ").length.coerceAtMost(30)))
                visitor.ensureNewLine()
            }

            // 渲染表体
            for (row in bodyRows) {
                visitor.builder().append(row.joinToString(" | "))
                visitor.ensureNewLine()
            }

            // 整个表格使用等宽字体，确保对齐
            visitor.builder().setSpan(
                TypefaceSpan("monospace"), tableStart, visitor.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            visitor.ensureNewLine()
        }

        // 阻止子节点被默认处理
        builder.on(TableHead::class.java) { _, _ -> }
        builder.on(TableBody::class.java) { _, _ -> }
        builder.on(TableRow::class.java) { _, _ -> }
        builder.on(TableCell::class.java) { _, _ -> }
    }

    private fun extractCells(row: TableRow): List<String> {
        val cells = mutableListOf<String>()
        var cell: Node? = row.firstChild
        while (cell != null) {
            if (cell is TableCell) {
                cells.add(extractText(cell).trim())
            }
            cell = cell.next
        }
        return cells
    }

    private fun extractText(node: Node): String {
        val sb = StringBuilder()
        var child: Node? = node.firstChild
        while (child != null) {
            when (child) {
                is org.commonmark.node.Text -> sb.append(child.literal)
                is org.commonmark.node.Code -> sb.append(child.literal)
                is org.commonmark.node.SoftLineBreak -> sb.append(" ")
                else -> sb.append(extractText(child))
            }
            child = child.next
        }
        return sb.toString()
    }
}
