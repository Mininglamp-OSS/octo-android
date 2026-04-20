package com.chat.base.markdown

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
 * 自定义表格插件：拦截 Markdown 表格 AST 节点，提取结构化数据（WKTableData），
 * 不在文本中渲染表格内容。表格数据由外部消费后渲染为原生卡片组件。
 */
class WKTablePlugin private constructor() : AbstractMarkwonPlugin() {

    companion object {
        /** 表格占位符：标记表格在渲染文本中的原始位置 */
        const val TABLE_PLACEHOLDER = "\uFFFC"

        /**
         * 使用 ThreadLocal 替代共享 mutableList，消除多线程竞争：
         * - 后台线程（buildUiMsgList）和主线程（实时新消息）可以并发渲染 Markdown
         * - 每个线程有独立的 pendingTables 缓冲区，无需加锁
         */
        private val pendingTables = ThreadLocal<MutableList<WKTableData>>()

        private fun getOrCreatePending(): MutableList<WKTableData> {
            var list = pendingTables.get()
            if (list == null) {
                list = mutableListOf()
                pendingTables.set(list)
            }
            return list
        }

        @JvmStatic
        fun create(): WKTablePlugin = WKTablePlugin()

        /**
         * 获取并清空当前线程上一次 Markwon 渲染过程中提取的所有表格数据。
         * 必须在 toMarkdown() 之后立即调用，否则数据会在下次渲染时被覆盖。
         */
        @JvmStatic
        fun consumeTableData(): List<WKTableData> {
            val list = pendingTables.get() ?: return emptyList()
            val result = ArrayList(list)
            list.clear()
            return result
        }

        private fun addTableData(data: WKTableData) {
            getOrCreatePending().add(data)
        }

        /** 渲染开始前清空缓冲区，防止残留 */
        internal fun clearPending() {
            pendingTables.get()?.clear()
        }
    }

    override fun configureParser(builder: Parser.Builder) {
        builder.extensions(listOf(TablesExtension.create()))
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(org.commonmark.ext.gfm.tables.TableBlock::class.java) { visitor, tableBlock ->
            val headers = mutableListOf<String>()
            val alignments = mutableListOf<TableCell.Alignment?>()
            val bodyRows = mutableListOf<List<String>>()

            var child: Node? = tableBlock.firstChild
            while (child != null) {
                when (child) {
                    is TableHead -> {
                        var row: Node? = child.firstChild
                        while (row != null) {
                            if (row is TableRow) {
                                val (cells, aligns) = extractCellsWithAlignment(row)
                                headers.addAll(cells)
                                alignments.addAll(aligns)
                            }
                            row = row.next
                        }
                    }
                    is TableBody -> {
                        var row: Node? = child.firstChild
                        while (row != null) {
                            if (row is TableRow) {
                                bodyRows.add(extractCells(row))
                            }
                            row = row.next
                        }
                    }
                }
                child = child.next
            }

            if (headers.isEmpty() && bodyRows.isEmpty()) return@on

            addTableData(WKTableData(headers, bodyRows, alignments))

            // 插入占位符标记表格在文本中的位置，供 UI 层按顺序交叉渲染
            visitor.ensureNewLine()
            visitor.builder().append(TABLE_PLACEHOLDER)
            visitor.ensureNewLine()
        }

        // 阻止子节点被默认处理
        builder.on(TableHead::class.java) { _, _ -> }
        builder.on(TableBody::class.java) { _, _ -> }
        builder.on(TableRow::class.java) { _, _ -> }
        builder.on(TableCell::class.java) { _, _ -> }
    }

    private fun extractCellsWithAlignment(row: TableRow): Pair<List<String>, List<TableCell.Alignment?>> {
        val cells = mutableListOf<String>()
        val alignments = mutableListOf<TableCell.Alignment?>()
        var cell: Node? = row.firstChild
        while (cell != null) {
            if (cell is TableCell) {
                cells.add(extractText(cell).trim())
                alignments.add(cell.alignment)
            }
            cell = cell.next
        }
        return Pair(cells, alignments)
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
