package com.chat.uikit.chat.provider

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxParserTest {
    @Test fun sheetLimitCountsInvalidDeclarationsAndDoesNotParseTheEleventhSheet() {
        val declarations = buildString {
            append("<sheet name=\"valid\" sheetId=\"1\" r:id=\"rId1\"/>")
            append("<sheet name=\"missing-id\" sheetId=\"2\"/>")
            repeat(8) { append("<sheet name=\"invalid$it\" sheetId=\"${it + 3}\" r:id=\"missing$it\"/>") }
            append("<sheet name=\"sentinel\" sheetId=\"11\" r:id=\"rId11\"/>")
            append("<sheet name=\"malformed\" sheetId=\"12\" r:id=\"rId12\"/>")
        }
        val rels = "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId11\" Target=\"worksheets/sheet11.xml\"/><Relationship Id=\"rId12\" Target=\"worksheets/sheet12.xml\"/>"
        val file = createWorkbook(declarations, rels, mapOf(
            "xl/worksheets/sheet1.xml" to "<row r=\"1\"><c r=\"A1\"><v>kept</v></c></row>",
            "xl/worksheets/sheet11.xml" to "<row r=\"not-valid\"><c",
            "xl/worksheets/sheet12.xml" to "<row r=\"not-valid\"><c"
        ))
        try {
            val parsed = XlsxParser.parse(file)
            assertEquals(1, parsed.sheets.size)
            assertEquals("kept", parsed.sheets.single().rows[0][0])
            assertTrue(parsed.sheetsTruncated)
        } finally { file.delete() }
    }

    @Test fun thousandsOfSheetDeclarationsStopAfterTheBound() {
        val declarations = buildString {
            repeat(1_000) { index -> append("<sheet name=\"S$index\" sheetId=\"${index + 1}\" r:id=\"rId$index\"/>") }
        }
        val rels = (0 until 1_000).joinToString("") { "<Relationship Id=\"rId$it\" Target=\"worksheets/sheet.xml\"/>" }
        val file = createWorkbook(declarations, rels, mapOf("xl/worksheets/sheet.xml" to ""))
        try {
            val parsed = XlsxParser.parse(file)
            assertEquals(10, parsed.sheets.size)
            assertTrue(parsed.sheetsTruncated)
        } finally { file.delete() }
    }

    @Test fun sheetLimitAllowsExactlyTenAndBoundsNamesByCodePoint() {
        val declarations = buildString {
            repeat(10) { index ->
                val name = when (index) {
                    0 -> "a".repeat(31) + "tail"
                    1 -> "😀".repeat(31) + "tail"
                    else -> "Sheet$index"
                }
                append("<sheet name=\"$name\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
            }
        }
        val rels = (1..10).joinToString("") { "<Relationship Id=\"rId$it\" Target=\"worksheets/sheet$it.xml\"/>" }
        val worksheets = (1..10).associate { "xl/worksheets/sheet$it.xml" to "" }
        val file = createWorkbook(declarations, rels, worksheets)
        try {
            val parsed = XlsxParser.parse(file)
            assertEquals(10, parsed.sheets.size)
            assertEquals(false, parsed.sheetsTruncated)
            assertEquals(31, parsed.sheets[0].name.codePointCount(0, parsed.sheets[0].name.length))
            assertEquals(31, parsed.sheets[1].name.codePointCount(0, parsed.sheets[1].name.length))
            assertEquals("😀".repeat(31), parsed.sheets[1].name)
        } finally { file.delete() }
    }

    @Test fun parsesSparseMissingOutOfOrderAndDuplicateRowsWithExactCoordinates() {
        val rows = """
            <row r="3"><c r="C3" t="inlineStr"><is><t>third</t></is></c></row>
            <row><c r="A4"><v>missing-row-number</v></c></row>
            <row r="1"><c r="A1"><v>old</v></c><c r="B1"><v>also-old</v></c></row>
            <row r="1"><c r="B1"><v>replacement</v></c></row>
            <row r="2"><c r="A2"><v>second</v></c><c r="ZZZZZZZZ2"><v>long-column</v></c></row>
            <row r="0"><c r="A0"><v>ignored-zero</v></c></row>
            <row r="bad"><c r="A1"><v>ignored-invalid</v></c></row>
            <row r="999999999999999999999999"><c r="A1"><v>ignored-overflow</v></c></row>
            <row r="500"><c r="AX500"><v>edge-row-column</v></c></row>
            <row r="501"><c r="A501"><v>truncated-row</v></c></row>
            <row r="501" />
        """.trimIndent()
        val file = createWorkbook(rows)
        try {
            val parsed = XlsxParser.parse(file).sheets.single()
            assertEquals(500, parsed.rows.size)
            assertEquals(listOf("", "replacement"), parsed.rows[0].take(2))
            assertEquals("second", parsed.rows[1][0])
            assertEquals(listOf("", "", "third"), parsed.rows[2].take(3))
            assertEquals("missing-row-number", parsed.rows[3][0])
            assertEquals("edge-row-column", parsed.rows[499][49])
            assertTrue(parsed.truncated)
        } finally { file.delete() }
    }

    @Test fun truncationSurvivesLaterEmptyDuplicateRowsAndColumnOnlyOverflow() {
        val file = createWorkbook("""
            <row r="1"><c r="A1"><v>kept</v></c></row>
            <row r="1" />
            <row r="2"><c r="AY2"><v>51st-column</v></c></row>
            <row r="2" />
            <row r="3"><c r="C3"><v>tail</v></c></row>
        """.trimIndent())
        try {
            val parsed = XlsxParser.parse(file).sheets.single()
            assertEquals(3, parsed.rows.size)
            assertEquals(listOf("", "", ""), parsed.rows[0])
            assertEquals(listOf("", "", ""), parsed.rows[1])
            assertEquals("tail", parsed.rows[2][2])
            assertTrue(parsed.truncated)
        } finally { file.delete() }
    }

    @Test fun contentOnlyBeyondRowLimitProducesEmptyTruncatedPreview() {
        val file = createWorkbook("""<row r="501"><c r="A501"><v>outside-row-limit</v></c></row>""")
        try {
            val parsed = XlsxParser.parse(file).sheets.single()
            assertTrue(parsed.rows.isEmpty())
            assertTrue(parsed.truncated)
        } finally { file.delete() }
    }

    @Test fun contentOnlyBeyondColumnLimitProducesEmptyTruncatedPreview() {
        val file = createWorkbook("""<row r="1"><c r="AY1"><v>outside-column-limit</v></c></row>""")
        try {
            val parsed = XlsxParser.parse(file).sheets.single()
            assertTrue(parsed.rows.isEmpty())
            assertTrue(parsed.truncated)
        } finally { file.delete() }
    }

    @Test fun trulyEmptySheetIsNotTruncated() {
        val file = createWorkbook("")
        try {
            val parsed = XlsxParser.parse(file).sheets.single()
            assertTrue(parsed.rows.isEmpty())
            assertEquals(false, parsed.truncated)
        } finally { file.delete() }
    }

    private fun createWorkbook(sheetRows: String): File = createWorkbook(
        "<sheet name=\"Coordinates\" sheetId=\"1\" r:id=\"rId1\"/>",
        "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/>",
        mapOf("xl/worksheets/sheet1.xml" to sheetRows)
    )

    private fun createWorkbook(sheetDeclarations: String, relationships: String, worksheets: Map<String, String>): File {
        val file = File.createTempFile("xlsx-parser-test-", ".xlsx", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheetDeclarations</sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<Relationships>$relationships</Relationships>""")
            worksheets.forEach { (path, body) -> entry(path, """<worksheet><sheetData>$body</sheetData></worksheet>""") }
        }
        return file
    }
}
