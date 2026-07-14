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
 * 内存保护：单 sheet 超过 [MAX_ROWS] 行截断，超过 [MAX_COLS] 列截断，[truncated] 标注。
 */
object XlsxParser {

    const val MAX_ROWS = 500
    const val MAX_COLS = 50

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
        zip.getInputStream(entry).use { input ->
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
                        out.add(buf.toString()); inSi = false
                    }
                }
            }
        }
        return out
    }

    // ---- _rels/workbook.xml.rels ----
    private fun readRels(zip: ZipFile, entry: ZipEntry): Map<String, String> {
        val out = HashMap<String, String>()
        zip.getInputStream(entry).use { input ->
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
        zip.getInputStream(entry).use { input ->
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
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var currentRow: ArrayList<String>? = null
            var cellType: String? = null
            var cellRef: String? = null
            var cellValue: String? = null
            var inlineText: StringBuilder? = null
            var inV = false
            var inIsT = false // <c t="inlineStr"><is><t>...</t></is></c>

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
                            if (currentRow != null && rows.size < MAX_ROWS) {
                                val col = columnIndex(cellRef)
                                val text = resolveCellText(cellType, cellValue, inlineText, shared)
                                fillTo(currentRow!!, col)
                                if (col < MAX_COLS) currentRow!![col] = text else truncated = true
                            }
                        }
                        "row" -> {
                            if (rows.size < MAX_ROWS && currentRow != null) rows.add(currentRow!!)
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

    /** "A1" -> 0, "B1" -> 1, "AA1" -> 26。ref 为空时用 -1（呼叫方需忽略）。 */
    private fun columnIndex(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') idx = idx * 26 + (ch - 'A' + 1) else break
        }
        return idx - 1
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
}
