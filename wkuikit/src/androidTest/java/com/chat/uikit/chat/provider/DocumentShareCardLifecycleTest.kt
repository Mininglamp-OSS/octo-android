package com.chat.uikit.chat.provider

import android.app.Activity
import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.chat.uikit.chat.msgmodel.WKDocumentShareContent
import com.chat.uikit.R
import com.chat.base.WKBaseApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentShareCardLifecycleTest {
    @Test fun responsiveStatesAndThemeUseLocalizedContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        WKBaseApplication.getInstance().application = context.applicationContext as android.app.Application
        val activity = instrumentation.startActivitySync(Intent(context, DocumentSharePreviewTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as DocumentSharePreviewTestActivity
        val previousTheme = com.chat.base.ui.Theme.getTheme()
        try {
            instrumentation.runOnMainSync {
                val card = DocumentShareCardView(activity)
                val preview = card.getChildAt(1) as android.widget.LinearLayout
                val noop = object : DocumentShareCardView.Listener {
                    override fun onOpen() {}
                    override fun onCopy() {}
                    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {}
                    override fun onAttach(hasWindowFocus: Boolean) {}
                    override fun onDetach() {}
                    override fun onInstallLongClick() {}
                }
                card.bindMessage(WKDocumentShareContent().apply { title = "响应式标题"; kind = "board" }, noop)
                val expected: Map<DocumentSharePreviewRepository.State, Int> = mapOf(
                    DocumentSharePreviewRepository.State.loading to R.string.doc_share_checking,
                    DocumentSharePreviewRepository.State.denied to R.string.doc_share_requires_access,
                    DocumentSharePreviewRepository.State.unavailable to R.string.doc_share_unavailable,
                    DocumentSharePreviewRepository.State.error to R.string.doc_share_error,
                    DocumentSharePreviewRepository.State.empty to R.string.doc_share_can_view,
                    DocumentSharePreviewRepository.State.ready to R.string.doc_share_can_view
                )
                expected.forEach { (state, resource) ->
                    card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(state))
                    assertEquals(activity.getString(resource), (findTagged(card, "doc-status") as android.widget.TextView).text.toString())
                }
                com.chat.base.ui.Theme.setTheme(com.chat.base.ui.Theme.DARK_MODE)
                card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.ready, DocumentSharePreviewParser.Preview.Board(listOf("A", "B", "C", "D"))))
                assertEquals(0xff25252c.toInt(), (card.background as android.graphics.drawable.GradientDrawable).color?.defaultColor)
                for (widthDp in listOf(220, 240, 320, 480)) {
                    for (owner in listOf("", "文档作者")) {
                        card.bindMessage(WKDocumentShareContent().apply { title = "这是用于验证状态首排省略行为的中文长标题"; ownerName = owner }, noop)
                        for (state in listOf(DocumentSharePreviewRepository.State.loading, DocumentSharePreviewRepository.State.ready, DocumentSharePreviewRepository.State.denied)) {
                            card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(state, if (state == DocumentSharePreviewRepository.State.ready) DocumentSharePreviewParser.Preview.Board(listOf("A", "B", "C", "D")) else null))
                            measureCard(card, widthDp, activity.resources.displayMetrics.density)
                            assertBadgeSharesTitleRow(card)
                            assertEquals(activity.getString(when (state) {
                                DocumentSharePreviewRepository.State.loading -> R.string.doc_share_checking
                                DocumentSharePreviewRepository.State.ready -> R.string.doc_share_can_view
                                else -> R.string.doc_share_requires_access
                            }), (findTagged(card, "doc-status") as android.widget.TextView).text.toString())
                        }
                        card.bindMessage(WKDocumentShareContent().apply { title = "A very long English document title that should yield space to the status badge"; ownerName = owner }, noop)
                        for (state in listOf(DocumentSharePreviewRepository.State.loading, DocumentSharePreviewRepository.State.ready, DocumentSharePreviewRepository.State.denied)) {
                            card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(state, if (state == DocumentSharePreviewRepository.State.ready) DocumentSharePreviewParser.Preview.Board(listOf("A", "B", "C", "D")) else null))
                            measureCard(card, widthDp, activity.resources.displayMetrics.density)
                            assertBadgeSharesTitleRow(card)
                        }
                    }
                }
                val englishConfig = android.content.res.Configuration(activity.resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) }
                val englishContext = activity.createConfigurationContext(englishConfig)
                val englishCard = DocumentShareCardView(englishContext)
                englishCard.bindMessage(WKDocumentShareContent().apply { title = "English status" }, noop)
                englishCard.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.loading))
                assertEquals("Checking", (findTagged(englishCard, "doc-status") as android.widget.TextView).text.toString())
                for (widthDp in listOf(220, 480, 220)) {
                    englishCard.bindMessage(WKDocumentShareContent().apply { title = "A very long English document title that should yield space to the status badge"; ownerName = "Document author" }, noop)
                    val englishStatuses = listOf(
                        DocumentSharePreviewRepository.State.loading to R.string.doc_share_checking,
                        DocumentSharePreviewRepository.State.ready to R.string.doc_share_can_view,
                        DocumentSharePreviewRepository.State.denied to R.string.doc_share_requires_access
                    )
                    for ((status, resource) in englishStatuses) {
                        val boardPreview = if (status == DocumentSharePreviewRepository.State.ready) DocumentSharePreviewParser.Preview.Board(listOf("A", "B", "C", "D")) else null
                        englishCard.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(status, boardPreview))
                        measureCard(englishCard, widthDp, activity.resources.displayMetrics.density)
                        assertEquals(englishContext.getString(resource), (findTagged(englishCard, "doc-status") as android.widget.TextView).text.toString())
                        assertBadgeSharesTitleRow(englishCard)
                    }
                }
                card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.ready, DocumentSharePreviewParser.Preview.Board(listOf("A", "B", "C", "D"))))
                card.bindMessage(WKDocumentShareContent().apply { title = "No author" }, noop)
                card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.loading))
                assertEquals("检查中", (findTagged(card, "doc-status") as android.widget.TextView).text.toString())
                card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.ready, DocumentSharePreviewParser.Preview.Board(listOf("A", "B", "C", "D"))))
                val width = (320 * activity.resources.displayMetrics.density).toInt()
                card.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST))
                card.layout(0, 0, card.measuredWidth, card.measuredHeight)
                card.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST))
                card.layout(0, 0, card.measuredWidth, card.measuredHeight)
                run {
                    assertEquals((264 * activity.resources.displayMetrics.density).toInt(), preview.measuredHeight)
                    val board = (0 until preview.childCount).map { preview.getChildAt(it) }.filterIsInstance<android.widget.FrameLayout>().single()
                    assertEquals(5, board.childCount)
                    assertTrue("all four board nodes fit", (1 until board.childCount).all { board.getChildAt(it).left >= 0 && board.getChildAt(it).right <= board.width })
                }
                assertEquals((32 * activity.resources.displayMetrics.density).toInt(), (card.getChildAt(0) as android.widget.LinearLayout).getChildAt(0).measuredHeight)
                card.bindMessage(WKDocumentShareContent().apply { title = "作者行"; ownerName = "文档作者" }, noop)
                card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.loading))
                card.measure(android.view.View.MeasureSpec.makeMeasureSpec((320 * activity.resources.displayMetrics.density).toInt(), android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST))
                assertTrue("author row is not clipped", (card.getChildAt(0) as android.widget.LinearLayout).getChildAt(0).measuredHeight >= (32 * activity.resources.displayMetrics.density).toInt())
                card.layout(0, 0, card.measuredWidth, card.measuredHeight)
                assertBadgeSharesTitleRow(card)
                val largeConfig = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.5f }
                val largeCard = DocumentShareCardView(activity.createConfigurationContext(largeConfig))
                largeCard.bindMessage(WKDocumentShareContent().apply { title = "Large text" }, noop)
                largeCard.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.error))
                largeCard.measure(android.view.View.MeasureSpec.makeMeasureSpec((240 * activity.resources.displayMetrics.density).toInt(), android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST))
                assertTrue("large-font error text remains inside a growing preview", largeCard.getChildAt(1).measuredHeight >= (264 * activity.resources.displayMetrics.density).toInt())
                card.clearBinding()
            }
        } finally {
            com.chat.base.ui.Theme.setTheme(previousTheme)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun attachDetachClearsOldResultAndReattachRestoresBinding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        WKBaseApplication.getInstance().application = context.applicationContext as android.app.Application
        val activity = instrumentation.startActivitySync(
            Intent(context, DocumentSharePreviewTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as DocumentSharePreviewTestActivity
        var attachCount = 0
        var detachCount = 0
        var openCount = 0
        var copyCount = 0
        var longClickCount = 0
        val focusEvents = mutableListOf<Boolean>()
        lateinit var card: DocumentShareCardView
        lateinit var root: ViewGroup
        lateinit var preview: android.widget.LinearLayout
        lateinit var copy: android.view.View
        val density = context.resources.displayMetrics.density
        val normalWidth = (260 * density).toInt()
        val narrowWidth = (220 * density).toInt()
        val sheetPreview = DocumentSharePreviewParser.Preview.Sheet(listOf("姓名", "手机号", "邮箱"), listOf(listOf("张三", "13800138000", "zhang@example.com"), listOf("李四", "13912345678", "li.si@example.com"), listOf("王五", "021-88888888", "wang@example.com")))
        instrumentation.runOnMainSync {
            card = DocumentShareCardView(activity)
            val listener = object : DocumentShareCardView.Listener {
                override fun onOpen() { openCount++ }
                override fun onCopy() { copyCount++ }
                override fun onWindowFocusChanged(hasWindowFocus: Boolean) { focusEvents += hasWindowFocus }
                override fun onAttach(hasWindowFocus: Boolean) {
                    attachCount++
                    card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(
                        DocumentSharePreviewRepository.State.ready,
                        sheetPreview
                    ))
                }
                override fun onDetach() { detachCount++ }
                override fun onInstallLongClick() { card.setOnLongClickListener { longClickCount++; true } }
            }
            card.bindMessage(WKDocumentShareContent().apply { title = "共享在线表格测试"; kind = "sheet"; ownerName = "张三" }, listener)
            card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.ready, context = DocumentSharePreviewRepository.Context(1, "u", "t", "https://api.test/v1", "https://web.test")))
            root = activity.findViewById(android.R.id.content)
            root.addView(card, ViewGroup.LayoutParams(normalWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(normalWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST)
            )
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            assertEquals(1, attachCount)
            card.onWindowFocusChanged(false)
            card.onWindowFocusChanged(true)
            assertEquals(listOf(false, true), focusEvents.takeLast(2))
            assertEquals(normalWidth, card.measuredWidth)
            assertTrue(card.height > 0)
            preview = card.getChildAt(1) as android.widget.LinearLayout
            assertEquals((264 * density).toInt(), preview.measuredHeight)
            val header = card.getChildAt(0) as android.widget.LinearLayout
            val row = header.getChildAt(0) as android.widget.LinearLayout
            copy = findTagged(card, "doc-copy")
            val titleGroup = row.getChildAt(1) as android.widget.LinearLayout
            val titleView = titleGroup.getChildAt(0) as android.widget.TextView
            assertEquals(1, titleView.maxLines)
            assertEquals(android.text.TextUtils.TruncateAt.END, titleView.ellipsize)
            assertEquals((42 * density).toInt(), row.measuredHeight)
            val table = findTable(preview)
            assertEquals(4, table.childCount)
            val firstRow = table.getChildAt(0) as android.widget.LinearLayout
            assertEquals(3, firstRow.childCount)
            val referenceCells = (0 until firstRow.childCount).map { firstRow.getChildAt(it) }
            for (rowIndex in 0 until table.childCount) {
                val row = table.getChildAt(rowIndex) as android.widget.LinearLayout
                assertEquals(3, row.childCount)
                for (column in 0 until row.childCount) {
                    val cell = row.getChildAt(column)
                    assertTrue("cell must fit within row", cell.right <= row.width)
                    assertEquals("column left edges align", referenceCells[column].left, cell.left)
                    assertEquals("column right edges align", referenceCells[column].right, cell.right)
                }
            }
            assertTrue("sheet preview fits card", preview.right <= card.width)
            assertTrue("header fits card", header.right <= card.width)
            assertTrue("copy button fits header", copy.right <= header.width)
            assertTrue("badge fits header", findTagged(card, "doc-status").right <= row.width)
            assertBadgeSharesTitleRow(card)
            for (index in 0 until titleGroup.childCount) assertTrue("title text fits header", titleGroup.getChildAt(index).right <= titleGroup.width)
            val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
            card.draw(Canvas(bitmap))
            val screenshot = File(requireNotNull(activity.getExternalFilesDir(null)), "document-share-card-layout.png")
            screenshot.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val mediaValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "document-share-card-layout.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OctoTests")
            }
            val mediaUri = requireNotNull(activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues))
            activity.contentResolver.openOutputStream(mediaUri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            android.util.Log.i("DocumentShareCardTest", "layout bitmap saved: ${screenshot.absolutePath}; $mediaUri")
            val narrowContent = WKDocumentShareContent().apply { title = "共享在线表格测试：很长的标题需要在聊天卡片中安全省略"; kind = "sheet" }
            card.bindMessage(narrowContent, listener)
            card.renderResult(narrowContent, DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.ready, sheetPreview))
            card.measure(android.view.View.MeasureSpec.makeMeasureSpec(narrowWidth, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST))
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            assertEquals(narrowWidth, card.measuredWidth)
            assertTrue("narrow preview fits card", preview.right <= card.width)
            assertTrue("narrow copy fits header", copy.width > 0 && copy.right <= header.width)
            assertTrue("narrow badge fits header", findTagged(card, "doc-status").width > 0 && findTagged(card, "doc-status").right <= card.width)
            assertTrue("narrow title retains measurable width", titleGroup.width > 0 && titleView.width > 0)
            assertBadgeSharesTitleRow(card)
            val narrowTable = findTable(preview)
            for (rowIndex in 0 until narrowTable.childCount) {
                val tableRow = narrowTable.getChildAt(rowIndex) as android.widget.LinearLayout
                for (column in 0 until tableRow.childCount) assertTrue("narrow cell has width", tableRow.getChildAt(column).width > 0)
            }
            preview.performClick(); copy.performClick()
            preview.performLongClick(); copy.performLongClick()
        }
        assertEquals(1, openCount); assertEquals(1, copyCount); assertEquals(2, longClickCount)
        instrumentation.runOnMainSync {
            root.removeView(card)
            assertEquals(null, card.currentResult().context)
            assertEquals(DocumentSharePreviewRepository.State.loading, card.currentResult().state)
            root.addView(card, ViewGroup.LayoutParams(normalWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        assertEquals(2, detachCount)
        assertEquals(2, attachCount)
        instrumentation.runOnMainSync {
            preview.performClick(); copy.performClick()
            preview.performLongClick(); copy.performLongClick()
            assertEquals(2, openCount); assertEquals(2, copyCount); assertEquals(4, longClickCount)
            card.clearBinding()
            assertFalse(card.isClickable)
            assertFalse(card.isLongClickable)
            assertEquals(null, card.currentResult().context)
            root.removeView(card)
            root.addView(card, ViewGroup.LayoutParams(normalWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
            assertFalse("unbound card remains non-clickable after reattach", card.isClickable)
            assertFalse("unbound card remains non-long-clickable after reattach", card.isLongClickable)
            assertFalse("unbound copy remains non-clickable after reattach", copy.isClickable)
            assertFalse("unbound copy remains non-long-clickable after reattach", copy.isLongClickable)
            assertFalse("unbound preview remains non-clickable after reattach", preview.isClickable)
            assertFalse("unbound preview remains non-long-clickable after reattach", preview.isLongClickable)
            activity.finish()
        }
    }

    private fun assertBadgeSharesTitleRow(card: DocumentShareCardView) {
        val header = card.getChildAt(0) as android.widget.LinearLayout
        val row = header.getChildAt(0) as android.widget.LinearLayout
        val titleGroup = row.getChildAt(1) as android.widget.LinearLayout
        val title = titleGroup.getChildAt(0)
        val badge = findTagged(card, "doc-status")
        fun rect(view: android.view.View): android.graphics.Rect = android.graphics.Rect().also { it.set(0, 0, view.width, view.height); card.offsetDescendantRectToMyCoords(view, it) }
        val titleRect = rect(title)
        val badgeRect = rect(badge)
        assertTrue("badge and title centers align", kotlin.math.abs(badgeRect.centerY() - titleRect.centerY()) <= 1)
        assertTrue("badge remains single line", (badge as android.widget.TextView).maxLines == 1)
        val copy = findTagged(card, "doc-copy")
        val copyRect = rect(copy)
        assertTrue("title precedes badge", titleRect.right <= badgeRect.left)
        assertTrue("badge precedes copy", badgeRect.right <= copyRect.left)
    }

    private fun measureCard(card: DocumentShareCardView, widthDp: Int, density: Float) {
        val width = (widthDp * density).toInt()
        card.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST))
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)
        assertEquals(width, card.measuredWidth)
    }

    private fun findTagged(root: android.view.ViewGroup, value: String): android.view.View {
        if (root.tag == value) return root
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.tag == value) return child
            if (child is android.view.ViewGroup) runCatching { findTagged(child, value) }.getOrNull()?.let { return it }
        }
        error("View tag $value not found")
    }

    private fun findTable(root: android.view.ViewGroup): android.widget.LinearLayout {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is android.widget.LinearLayout && child.orientation == android.widget.LinearLayout.VERTICAL && child.childCount == 4) return child
            if (child is android.view.ViewGroup) runCatching { findTable(child) }.getOrNull()?.let { return it }
        }
        error("Preview table not found")
    }

}
