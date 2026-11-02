package com.chat.uikit.chat.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HtmlPreviewLoaderTest {
    @Test fun recognizesDisplayExtensionsCaseInsensitively() {
        assertTrue(HtmlPreviewLoader.isHtmlDisplayName("页面.HTM"))
        assertTrue(HtmlPreviewLoader.isHtmlDisplayName("a.HTML"))
        assertTrue(!HtmlPreviewLoader.isHtmlDisplayName("hash-cache"))
        assertTrue(!HtmlPreviewLoader.isHtmlDisplayName("a.txt"))
    }

    @Test fun readsUtf8BomAndMoreThanOldTextLimit() {
        val file = File.createTempFile("html-preview", ".html")
        try {
            val text = "<h1>多语言 🌍</h1>" + "x".repeat(60_000)
            file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8))
            val result = HtmlPreviewLoader.read(file)
            assertTrue(result is HtmlPreviewLoader.Result.Content)
            assertEquals(text, (result as HtmlPreviewLoader.Result.Content).html)
        } finally { file.delete() }
    }

    @Test fun enforcesFiveMiBAndReportsEmptyOrMissing() {
        val file = File.createTempFile("html-preview", ".html")
        try {
            file.writeBytes(ByteArray(HtmlPreviewLoader.MAX_BYTES))
            assertTrue(HtmlPreviewLoader.read(file) is HtmlPreviewLoader.Result.Content)
            file.appendBytes(byteArrayOf(1))
            assertTrue(HtmlPreviewLoader.read(file) is HtmlPreviewLoader.Result.TooLarge)
            file.writeBytes(byteArrayOf())
            assertTrue(HtmlPreviewLoader.read(file) is HtmlPreviewLoader.Result.Empty)
        } finally { file.delete() }
        assertTrue(HtmlPreviewLoader.read(File(file.path + ".missing")) is HtmlPreviewLoader.Result.Missing)
    }
}
