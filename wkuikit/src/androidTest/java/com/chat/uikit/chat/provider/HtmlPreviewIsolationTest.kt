package com.chat.uikit.chat.provider

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class HtmlPreviewIsolationTest {
    @Test fun productionActivityLoadsFixtureAndExecutesInlineInteraction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = InstrumentationRegistry.getArguments().getString("htmlPreviewHttpsProbe")
        val websocket = InstrumentationRegistry.getArguments().getString("htmlPreviewWssProbe")
        val fixture = File(instrumentation.targetContext.cacheDir, "html-preview-isolation.html")
        val html = instrumentation.context.assets.open("html-preview-isolation.html")
            .bufferedReader().use { it.readText() }
        val runId = "html-preview-${UUID.randomUUID()}"
        val testHtml = if (!target.isNullOrBlank() && !websocket.isNullOrBlank()) {
            require(target.startsWith("https://") && websocket.startsWith("wss://")) {
                "Probes must use controlled HTTPS and WSS endpoints"
            }
            val httpUrl = Uri.parse(target).buildUpon().appendQueryParameter("runId", runId).build().toString()
            val wsUrl = Uri.parse(websocket).buildUpon().appendQueryParameter("runId", runId).build().toString()
            html.replace("__HTML_PREVIEW_HTTP_JSON__", JSONObject.quote(httpUrl))
                .replace("__HTML_PREVIEW_WSS_JSON__", JSONObject.quote(wsUrl))
        } else html.replace("__HTML_PREVIEW_HTTP_JSON__", JSONObject.quote("https://html-preview-probe.invalid/probe?runId=$runId"))
            .replace("__HTML_PREVIEW_WSS_JSON__", JSONObject.quote("wss://html-preview-probe.invalid/socket?runId=$runId"))
        fixture.writeText(testHtml)
        fixture.deleteOnExit()
        val intent = Intent(instrumentation.targetContext, HtmlPreviewActivity::class.java)
            .putExtra("filePath", fixture.absolutePath)
            .putExtra("title", "HTML isolation test")
        try {
          ActivityScenario.launch<HtmlPreviewActivity>(intent).use { scenario ->
              val result = AtomicReference<String>()
              val latch = CountDownLatch(1)
              scenario.onActivity { activity ->
                val field = HtmlPreviewActivity::class.java.getDeclaredField("webView")
                field.isAccessible = true
                val webView = requireNotNull(field.get(activity) as? WebView) { "HTML WebView was not created" }
                val deadline = android.os.SystemClock.uptimeMillis() + 12_000
                val poll = object : Runnable {
                    override fun run() {
                        webView.evaluateJavascript("(function(){if(!document.querySelector('button')||!window.cspEvents.includes('connect-src')||!window.cspEvents.includes('worker-src')||!window.cspEvents.includes('frame-src')||!window.cspEvents.includes('srcdoc-connect-src')||!window.cspSchemes.includes('https')||!window.cspSchemes.includes('wss'))return 'WAIT';document.querySelector('button').click();return JSON.stringify({count:document.getElementById('count').textContent,host:location.hostname,events:window.cspEvents,https:window.cspSchemes.includes('https'),wss:window.cspSchemes.includes('wss'),socketOpened:window.socketOpened})})()") { value ->
                            if (value != null && value != "null" && value != "\"WAIT\"") {
                                result.set(value); latch.countDown()
                            } else if (android.os.SystemClock.uptimeMillis() >= deadline) {
                                result.set("TIMEOUT:$value"); latch.countDown()
                            } else webView.postDelayed(this, 100)
                        }
                    }
                }
                poll.run()
              }
              assertTrue("Activity did not render the test fixture", latch.await(13, TimeUnit.SECONDS))
              val encoded = result.get()
              assertTrue("CSP fixture did not become ready: $encoded", encoded != null && encoded.startsWith("\"{"))
              val report = JSONObject(JSONArray("[$encoded]").getString(0))
              assertTrue("Inline interaction failed: $report", report.getString("count") == "1")
              assertTrue("Expected random isolated document hostname: $report", report.getString("host").endsWith("html-preview.invalid"))
              val events = report.getJSONArray("events").join(",")
              assertTrue("Expected connect-src, worker-src, frame-src and srcdoc connect-src events: $report", events.contains("connect-src") && events.contains("worker-src") && events.contains("frame-src") && events.contains("srcdoc-connect-src"))
              assertTrue("Expected both HTTPS and WSS CSP reports: $report", report.getBoolean("https") && report.getBoolean("wss"))
              assertTrue("WebSocket must not open: $report", !report.getBoolean("socketOpened"))
            }
        } finally {
            fixture.delete()
        }
    }
}
