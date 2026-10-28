package com.chat.uikit.chat.provider

import org.json.JSONArray
import org.json.JSONObject

object DocumentSharePreviewParser {
    sealed class Preview {
        data class Doc(val heading: String?, val paragraphs: List<String>) : Preview()
        data class Sheet(val headers: List<String>, val rows: List<List<String>>) : Preview()
        data class Board(val texts: List<String>) : Preview()
    }

    fun parseDoc(body: JSONObject): Preview.Doc? {
        val content = body.optJSONObject("doc")?.opt("content") ?: return null
        var heading: String? = null
        val paragraphs = ArrayList<String>(3)
        var visits = 0
        fun collect(value: Any?, depth: Int, out: StringBuilder): Boolean {
            if (depth > 20 || visits++ >= 1200 || out.length >= 500) return false
            when (value) {
                is JSONObject -> {
                    (value.opt("text") as? String)?.let { out.append(it, 0, minOf(it.length, 500 - out.length)) }
                    value.optJSONArray("content")?.let { children ->
                        for (i in 0 until children.length()) if (!collect(children.opt(i), depth + 1, out)) break
                    }
                }
                is JSONArray -> for (i in 0 until minOf(value.length(), 200)) {
                    if (!collect(value.opt(i), depth + 1, out)) break
                }
            }
            return out.length < 500 && visits < 1200
        }
        val blocks = if (content is JSONArray) content else JSONArray().put(content)
        for (i in 0 until minOf(blocks.length(), 200)) {
            if (paragraphs.size >= 3 || visits >= 1200) break
            val block = blocks.opt(i) as? JSONObject ?: continue
            val type = block.optString("type")
            val combinedBuilder = StringBuilder()
            collect(block.optJSONArray("content") ?: JSONArray(), 1, combinedBuilder)
            val combined = combinedBuilder.toString().trim()
            if (type == "heading" && heading == null && combined.isNotEmpty()) heading = combined
            if (type == "paragraph" && combined.isNotEmpty()) paragraphs.add(combined)
        }
        return if (heading == null && paragraphs.isEmpty()) null else Preview.Doc(heading, paragraphs)
    }

    fun parseSheet(body: JSONObject): Preview.Sheet? {
        val cells = body.optJSONObject("sheetCells") ?: return null
        val sheets = LinkedHashMap<String, MutableMap<Pair<Int, Int>, String>>()
        val regex = Regex("""^(.+)!([0-9]+):([0-9]+)$""")
        val keys = cells.keys()
        var scanned = 0
        while (keys.hasNext() && scanned++ < 4000) {
            val key = keys.next(); val match = regex.matchEntire(key) ?: continue
            val row = match.groupValues[2].toIntOrNull() ?: continue
            val col = match.groupValues[3].toIntOrNull() ?: continue
            if (row !in 0..3 || col !in 0..5) continue
            val raw = cells.optJSONObject(key)?.opt("v") ?: continue
            if (raw == JSONObject.NULL) continue
            val value = raw.toString().take(500)
            if (value.isEmpty()) continue
            sheets.getOrPut(match.groupValues[1]) { LinkedHashMap() }[row to col] = value
        }
        val chosen = sheets.values.maxByOrNull { it.values.count(String::isNotEmpty) } ?: return null
        val columns = chosen.keys.map { it.second }.distinct().sorted()
        if (columns.isEmpty()) return null
        val headers = columns.map { chosen[0 to it].orEmpty() }
        val rows = (1..3).filter { row -> columns.any { !chosen[row to it].isNullOrEmpty() } }
            .map { row -> columns.map { chosen[row to it].orEmpty() } }
        return Preview.Sheet(headers, rows)
    }

    fun parseBoard(body: JSONObject): Preview.Board? {
        val scene = body.optJSONObject("scene") ?: body
        val elements = scene.optJSONArray("elements") ?: body.optJSONArray("elements") ?: return null
        val texts = ArrayList<String>(6)
        for (i in 0 until minOf(elements.length(), 200)) {
            val value = elements.opt(i)
            val text = if (value is JSONObject) value.opt("text") as? String else value as? String
            if (!text.isNullOrBlank() && texts.size < 6) texts.add(text.trim().take(500))
        }
        return texts.takeIf { it.isNotEmpty() }?.let { Preview.Board(it) }
    }

    fun parseHtml(body: JSONObject): Preview.Doc? {
        val preview = body.optJSONObject("preview") ?: return null
        val heading = (preview.opt("heading") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
        val paragraphs = ArrayList<String>(3)
        val values = preview.optJSONArray("paragraphs") ?: JSONArray()
        for (i in 0 until values.length()) {
            val value = values.opt(i) as? String
            if (!value.isNullOrBlank()) paragraphs.add(value.trim().take(500))
            if (paragraphs.size == 3) break
        }
        return if (heading == null && paragraphs.isEmpty()) null else Preview.Doc(heading, paragraphs)
    }
}
