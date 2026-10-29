package com.chat.uikit.chat.provider

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxParserTest {
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

    private fun createWorkbook(sheetRows: String): File {
        val file = File.createTempFile("xlsx-parser-test-", ".xlsx", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Coordinates" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""")
            entry("xl/worksheets/sheet1.xml", """<worksheet><sheetData>$sheetRows</sheetData></worksheet>""")
        }
        return file
    }
}
