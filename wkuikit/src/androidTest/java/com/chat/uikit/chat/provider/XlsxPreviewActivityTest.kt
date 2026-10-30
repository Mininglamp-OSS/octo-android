package com.chat.uikit.chat.provider

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.chat.uikit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxPreviewActivityTest {
    @Test fun activityBoundsDirectWorkbookAndClampsRestoredSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = createWorkbook("", context.cacheDir, sheetCount = 11)
        val scenario = ActivityScenario.launch<XlsxPreviewActivity>(
            Intent(context, XlsxPreviewActivity::class.java)
                .putExtra("filePath", file.absolutePath)
                .putExtra("title", "Preview test")
        )
        try {
            assertEquals(context.getString(R.string.xlsx_empty), awaitRenderedMessage(scenario, context.getString(R.string.xlsx_empty)).first)
            awaitRenderedMessage(scenario, context.getString(R.string.xlsx_sheets_truncated, 10))
            val sheets = (0..10).map { index ->
                XlsxParser.Sheet("Tab$index" + "😀".repeat(36), listOf(listOf("Header"), listOf("$index")), false)
            }
            scenario.onActivity { activity ->
                activity.javaClass.getDeclaredField("activeSheet").apply { isAccessible = true }.setInt(activity, 99)
                val method = activity.javaClass.getDeclaredMethod("renderWorkbook", XlsxParser.Workbook::class.java).apply { isAccessible = true }
                method.invoke(activity, XlsxParser.Workbook(sheets))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val tabLabels = findTexts(root).filter { it.startsWith("Tab") }
                assertEquals(10, tabLabels.size)
                assertTrue(tabLabels.all { it.codePointCount(0, it.length) <= 31 })
                assertTrue(findTexts(root).contains(context.getString(R.string.xlsx_sheets_truncated, 10)))
                assertEquals(9, activity.javaClass.getDeclaredField("activeSheet").apply { isAccessible = true }.getInt(activity))
                requireNotNull(findTextView(root, XlsxParser.boundedSheetName(sheets[0].name))).performClick()
                assertEquals(0, activity.javaClass.getDeclaredField("activeSheet").apply { isAccessible = true }.getInt(activity))
                activity.javaClass.getDeclaredField("activeSheet").apply { isAccessible = true }.setInt(activity, 4)
            }
            scenario.recreate()
            awaitRenderedMessage(scenario, context.getString(R.string.xlsx_sheets_truncated, 10))
            scenario.onActivity { activity ->
                assertEquals(4, activity.javaClass.getDeclaredField("activeSheet").apply { isAccessible = true }.getInt(activity))
                assertEquals(10, findTexts(activity.findViewById(android.R.id.content)).count { it.startsWith("Sheet") })
            }
        } finally {
            scenario.close()
            file.delete()
        }
    }

    @Test fun rowAndColumnOverflowShowTruncationMessageWhileEmptySheetShowsEmptyMessage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val cases = listOf(
            "<row r=\"501\"><c r=\"A501\"><v>outside-row-limit</v></c></row>" to R.string.xlsx_truncated_without_content,
            "<row r=\"1\"><c r=\"AY1\"><v>outside-column-limit</v></c></row>" to R.string.xlsx_truncated_without_content,
            "" to R.string.xlsx_empty
        )

        cases.forEach { (sheetRows, messageResource) ->
            val file = createWorkbook(sheetRows, context.cacheDir)
            val scenario = try {
                ActivityScenario.launch<XlsxPreviewActivity>(
                    Intent(context, XlsxPreviewActivity::class.java)
                        .putExtra("filePath", file.absolutePath)
                        .putExtra("title", "Preview test")
                )
            } catch (failure: Throwable) {
                file.delete()
                throw failure
            }
            try {
                val expectedMessage = context.getString(messageResource)
                val result = awaitRenderedMessage(scenario, expectedMessage)
                assertEquals(expectedMessage, result.first)
                if (messageResource == R.string.xlsx_truncated_without_content) {
                    assertFalse(result.first.contains(context.getString(R.string.xlsx_empty)))
                    assertEquals(View.GONE, result.second)
                }
            } finally {
                scenario.close()
                file.delete()
            }
        }
    }

    private fun awaitRenderedMessage(
        scenario: ActivityScenario<XlsxPreviewActivity>,
        expected: String
    ): Pair<String, Int> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var result: Pair<String, Int>? = null
        while (result == null && System.nanoTime() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val message = findTextView(root, expected)
                if (message != null && message.visibility == View.VISIBLE) {
                    result = message.text.toString() to requireNotNull(findRecyclerVisibility(root))
                }
            }
            if (result == null) CountDownLatch(1).await(100, TimeUnit.MILLISECONDS)
        }
        return result ?: throw AssertionError("preview did not render expected state: $expected")
    }

    private fun findTextView(view: View, expected: String): TextView? {
        if (view is TextView && view.text.toString() == expected) return view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) findTextView(view.getChildAt(index), expected)?.let { return it }
        }
        return null
    }

    private fun findTexts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) addAll(findTexts(view.getChildAt(index)))
    }

    private fun findRecyclerVisibility(view: View): Int? {
        if (view is androidx.recyclerview.widget.RecyclerView) return view.visibility
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) findRecyclerVisibility(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun createWorkbook(sheetRows: String, directory: File, sheetCount: Int = 1): File {
        val file = File.createTempFile("xlsx-preview-test-", ".xlsx", directory)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val declarations = (1..sheetCount).joinToString("") { "<sheet name=\"Sheet$it\" sheetId=\"$it\" r:id=\"rId$it\"/>" }
            val relationships = (1..sheetCount).joinToString("") { "<Relationship Id=\"rId$it\" Target=\"worksheets/sheet$it.xml\"/>" }
            entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$declarations</sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<Relationships>$relationships</Relationships>""")
            entry("xl/worksheets/sheet1.xml", """<worksheet><sheetData>$sheetRows</sheetData></worksheet>""")
            (2..sheetCount).forEach { index -> entry("xl/worksheets/sheet$index.xml", "<worksheet><sheetData><row r=\"1\"><c r=\"A1\"><v>$index</v></c></row></sheetData></worksheet>") }
        }
        return file
    }
}
