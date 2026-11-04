package com.chat.uikit.chat.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlPreviewDocumentPolicyTest {
    @Test fun responsePolicyAllowsInlineFeaturesAndBlocksConnectionsAndNestedContexts() {
        val csp = HtmlPreviewDocumentPolicy.responseHeaders().getValue("Content-Security-Policy")
        assertTrue(csp.contains("script-src 'unsafe-inline'"))
        assertTrue(csp.contains("style-src 'unsafe-inline'"))
        assertTrue(csp.contains("connect-src 'none'"))
        assertTrue(csp.contains("worker-src 'none'"))
        assertTrue(csp.contains("frame-src 'none'"))
        assertTrue(csp.contains("sandbox allow-scripts"))
        assertFalse(csp.contains("unsafe-eval"))
    }

    @Test fun onlyExactGetDocumentIsServed() {
        val url = HtmlPreviewDocumentPolicy.newDocumentUrl()
        assertTrue(url.endsWith(".html-preview.invalid/index.html"))
        assertTrue(HtmlPreviewDocumentPolicy.isMainDocumentRequest(url, url, "GET"))
        assertFalse(HtmlPreviewDocumentPolicy.isMainDocumentRequest(url, "$url?x=1", "GET"))
        assertFalse(HtmlPreviewDocumentPolicy.isMainDocumentRequest(url, url, "POST"))
        assertFalse(HtmlPreviewDocumentPolicy.isMainDocumentRequest(url, "https://example.org/", "GET"))
    }

    @Test fun responseIsNotCached() {
        assertEquals("no-store", HtmlPreviewDocumentPolicy.responseHeaders()["Cache-Control"])
    }
}
