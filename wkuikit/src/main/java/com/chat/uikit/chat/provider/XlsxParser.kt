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

package com.chat.uikit.chat.provider

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 极简 xlsx 解析器（零依赖）。只解析：sharedStrings + workbook.xml(.rels) + 每个 sheet 的 cell 值。
 * 不还原样式/格式化/公式表达式，但公式的**缓存值**（`<v>`）会被读到。
 *
 * xlsx 布局：
 *   [Content_Types].xml
 *   _rels/.rels                          -> 找 workbook.xml
 *   xl/workbook.xml                      -> sheet 列表 (name, r:id)
 *   xl/_rels/workbook.xml.rels           -> rId -> target 路径
 *   xl/sharedStrings.xml                 -> 字符串池（可选）
 *   xl/worksheets/sheet1.xml             -> 单元格
 *
 * 输入是不可信的聊天附件，因此对 zip entry 解压量、共享字符串条目数、单 sheet 行/列
 * 都做硬上限，避免 zip-bomb / 恶意坐标 / 缺字段 触发 OOM 或 IOOBE。
 */
object XlsxParser {

    const val MAX_ROWS = 500
    const val MAX_COLS = 50

    /** 单个 zip entry 解压后允许的最大字节数（防 zip-bomb）。50 MB 足够任何常规办公文档。 */
    private const val MAX_ENTRY_BYTES: Long = 50L * 1024L * 1024L

    /** sharedStrings 表最大条目数（防 KB 级 xml 展开为百 MB 字符串数组）。10 万条覆盖常规文档。 */
    private const val MAX_SHARED_STRINGS = 100_000

    data class Sheet(
        val name: String,
        val rows: List<List<String>>,
        val truncated: Boolean,
    )

    data class Workbook(val sheets: List<Sheet>)

    /** 抛异常给上层展示"预览失败"，不吞。 */
    fun parse(file: File): Workbook {
        ZipFile(file).use { zip ->
            val shared = zip.getEntry("xl/sharedStrings.xml")?.let { readSharedStrings(zip, it) } ?: emptyList()
            val rels = zip.getEntry("xl/_rels/workbook.xml.rels")?.let { readRels(zip, it) } ?: emptyMap()
            val workbookEntry = zip.getEntry("xl/workbook.xml")
                ?: throw IllegalStateException("missing xl/workbook.xml")
            val sheetRefs = readWorkbook(zip, workbookEntry)

            val sheets = sheetRefs.mapNotNull { (name, rid) ->
                val target = rels[rid] ?: return@mapNotNull null
                // target 可能是 "worksheets/sheet1.xml"，相对于 xl/
                val entryPath = if (target.startsWith("/")) target.trimStart('/') else "xl/$target"
                val entry = zip.getEntry(entryPath) ?: return@mapNotNull null
                readSheet(zip, entry, shared, name)
            }
            return Workbook(sheets)
        }
    }

    // ---- sharedStrings.xml ----
    private fun readSharedStrings(zip: ZipFile, entry: ZipEntry): List<String> {
        val out = ArrayList<String>()
        boundedStream(zip, entry).use { input ->
            val parser = newParser(input)
            var buf = StringBuilder()
            var inSi = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "si" -> { inSi = true; buf = StringBuilder() }
                        "t" -> if (inSi) buf.append(readText(parser))
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "si" && inSi) {
                        // 达到条目上限后继续解析（避免 parser 状态不定），但丢弃后续字符串。
                        // 索引超出的 shared[i] 读到会返回 ""（见 resolveCellText 的 in-bounds 判断）。
                        if (out.size < MAX_SHARED_STRINGS) out.add(buf.toString())
                        inSi = false
                    }
                }
            }
        }
        return out
    }

    // ---- _rels/workbook.xml.rels ----
    private fun readRels(zip: ZipFile, entry: ZipEntry): Map<String, String> {
        val out = HashMap<String, String>()
        boundedStream(zip, entry).use { input ->
            val parser = newParser(input)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (id != null && target != null) out[id] = target
                }
            }
        }
        return out
    }

    // ---- workbook.xml -> List<(name, rId)> ----
    private fun readWorkbook(zip: ZipFile, entry: ZipEntry): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        boundedStream(zip, entry).use { input ->
            val parser = newParser(input)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                    // FEATURE_PROCESS_NAMESPACES=false 时 r:id 是字面属性名"r:id"，
                    // getAttributeValue(null,"id") 拿不到；用 endsWith(":id") 兜任意前缀。
                    val rid = (0 until parser.attributeCount)
                        .firstOrNull {
                            val n = parser.getAttributeName(it)
                            n == "id" || n.endsWith(":id")
                        }
                        ?.let { parser.getAttributeValue(it) }
                    if (rid != null) out.add(name to rid)
                }
            }
        }
        return out
    }

    // ---- worksheet ----
    private fun readSheet(zip: ZipFile, entry: ZipEntry, shared: List<String>, name: String): Sheet {
        val rows = ArrayList<List<String>>()
        var truncated = false
        boundedStream(zip, entry).use { input ->
            val parser = newParser(input)
            var currentRow: ArrayList<String>? = null
            var cellType: String? = null
            var cellRef: String? = null
            var cellValue: String? = null
            var inlineText: StringBuilder? = null
            var inV = false

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> {
                            if (rows.size >= MAX_ROWS) { truncated = true }
                            currentRow = ArrayList()
                        }
                        "c" -> {
                            cellRef = parser.getAttributeValue(null, "r")
                            cellType = parser.getAttributeValue(null, "t")
                            cellValue = null
                            inlineText = if (cellType == "inlineStr") StringBuilder() else null
                        }
                        "v" -> inV = true
                        "t" -> if (inlineText != null) inlineText!!.append(readText(parser)) else if (inV) {
                            // <v><t> 不常见，忽略
                        }
                        "is" -> Unit
                    }
                    XmlPullParser.TEXT -> if (inV) {
                        cellValue = (cellValue ?: "") + parser.text
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "v" -> inV = false
                        "c" -> {
                            val row = currentRow
                            if (row != null && rows.size < MAX_ROWS) {
                                val col = columnIndex(cellRef)
                                // 三种情况：
                                //  col in 0 until MAX_COLS → 正常写入（此时 fillTo 最多补 MAX_COLS-1 项，安全）
                                //  col >= MAX_COLS         → 攻击性/超宽表，标 truncated 但不 fillTo（否则可 OOM）
                                //  col < 0                 → 缺 `r` 属性或非法值，OOXML 允许省略 `r`，跳过该 cell
                                if (col in 0 until MAX_COLS) {
                                    val text = resolveCellText(cellType, cellValue, inlineText, shared)
                                    fillTo(row, col)
                                    row[col] = text
                                } else if (col >= MAX_COLS) {
                                    truncated = true
                                }
                            }
                        }
                        "row" -> {
                            val row = currentRow
                            if (rows.size < MAX_ROWS && row != null) rows.add(row)
                            currentRow = null
                        }
                    }
                }
            }
        }
        return Sheet(name = name, rows = rows, truncated = truncated)
    }

    private fun resolveCellText(
        type: String?,
        value: String?,
        inlineBuf: StringBuilder?,
        shared: List<String>,
    ): String {
        if (inlineBuf != null) return inlineBuf.toString()
        val v = value ?: return ""
        return when (type) {
            "s" -> v.toIntOrNull()?.let { if (it in shared.indices) shared[it] else "" } ?: ""
            "b" -> if (v == "1") "TRUE" else "FALSE"
            "str", "e" -> v
            else -> v // number / date（原始数值，不做 formatCode 还原）
        }
    }

    /** "A1" -> 0, "B1" -> 1, "AA1" -> 26。ref 为空或不含字母时返回 -1（呼叫方需忽略）。 */
    private fun columnIndex(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var idx = 0
        var seen = false
        for (ch in ref) {
            if (ch in 'A'..'Z') { idx = idx * 26 + (ch - 'A' + 1); seen = true } else break
        }
        return if (seen) idx - 1 else -1
    }

    private fun fillTo(list: ArrayList<String>, index: Int) {
        while (list.size <= index) list.add("")
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        return parser
    }

    private fun readText(parser: XmlPullParser): String {
        // t 里可能有嵌套（rich text run），把所有 TEXT 事件拼起来直到 </t>
        val depth = parser.depth
        val buf = StringBuilder()
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && parser.depth == depth) break
            if (ev == XmlPullParser.TEXT) buf.append(parser.text)
        }
        return buf.toString()
    }

    /** 给 [ZipFile.getInputStream] 套一层 [MAX_ENTRY_BYTES] 字节上限，防 zip-bomb。 */
    private fun boundedStream(zip: ZipFile, entry: ZipEntry): InputStream =
        BoundedInputStream(zip.getInputStream(entry), MAX_ENTRY_BYTES)

    /** 只在超过上限时抛 [IOException]，让 parse() 整体失败上抛给 UI 展示"预览失败"。 */
    private class BoundedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long,
    ) : InputStream() {
        private var consumed: Long = 0L
        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) {
                consumed++
                if (consumed > maxBytes) throw IOException("xlsx entry exceeds $maxBytes bytes")
            }
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) {
                consumed += n
                if (consumed > maxBytes) throw IOException("xlsx entry exceeds $maxBytes bytes")
            }
            return n
        }
        override fun close() = delegate.close()
    }
}
