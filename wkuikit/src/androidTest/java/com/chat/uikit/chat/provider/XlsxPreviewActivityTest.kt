package com.chat.uikit.chat.provider

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.chat.uikit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxPreviewActivityTest {
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

    private fun findRecyclerVisibility(view: View): Int? {
        if (view is androidx.recyclerview.widget.RecyclerView) return view.visibility
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) findRecyclerVisibility(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun createWorkbook(sheetRows: String, directory: File): File {
        val file = File.createTempFile("xlsx-preview-test-", ".xlsx", directory)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Preview" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""")
            entry("xl/worksheets/sheet1.xml", """<worksheet><sheetData>$sheetRows</sheetData></worksheet>""")
        }
        return file
    }
}
