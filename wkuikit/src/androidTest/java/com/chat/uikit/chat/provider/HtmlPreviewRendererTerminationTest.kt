package com.chat.uikit.chat.provider

import android.content.Intent
import android.os.Build
import android.os.Process
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HtmlPreviewRendererTerminationTest {
    @Test fun terminateRenderer_detachesAndAllowsReopen() {
        assumeTrue("Renderer termination requires API 29+", Build.VERSION.SDK_INT >= 29)
        exerciseTermination(terminate = true)
    }

    @Test fun crashRenderer_detachesAndAllowsReopen() {
        assumeTrue("Independent renderer crash test requires API 29+", Build.VERSION.SDK_INT >= 29)
        exerciseTermination(terminate = false)
    }

    private fun exerciseTermination(terminate: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File(instrumentation.targetContext.cacheDir, "html-renderer-${System.nanoTime()}.html")
        fixture.writeText("<!doctype html><meta charset=utf-8><button id=b onclick=\"document.body.dataset.clicked='yes'\">ready</button>")
        val intent = Intent(instrumentation.targetContext, HtmlPreviewActivity::class.java)
            .putExtra("filePath", fixture.absolutePath)
            .putExtra("title", "Renderer termination test")
        try {
            ActivityScenario.launch<HtmlPreviewActivity>(intent).use { scenario ->
                val activityRef = AtomicReference<HtmlPreviewActivity>()
                val viewRef = AtomicReference<WebView>()
                val processId = IntArray(1)
                awaitActivity(scenario) { activity ->
                    activityRef.set(activity)
                    processId[0] = Process.myPid()
                    val field = HtmlPreviewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }
                    val view = field.get(activity) as? WebView
                    if (view != null) viewRef.set(view)
                    view
                }
                val oldView = requireNotNull(viewRef.get())
                val renderer = AtomicReference<android.webkit.WebViewRenderProcess>()
                instrumentation.runOnMainSync { renderer.set(oldView.webViewRenderProcess) }
                assumeTrue("No independently controllable renderer handle; case skipped", renderer.get() != null)
                if (terminate) {
                    val accepted = BooleanArray(1)
                    instrumentation.runOnMainSync { accepted[0] = requireNotNull(renderer.get()).terminate() }
                    assertTrue("Renderer termination request was rejected", accepted[0])
                } else {
                    // Invoke the framework crash URL on the production WebViewClient. loadUrl is
                    // a native call and does not pass through shouldOverrideUrlLoading.
                    instrumentation.runOnMainSync { oldView.loadUrl("chrome://crash") }
                }

                val gone = CountDownLatch(1)
                val pollHolder = arrayOfNulls<Runnable>(1)
                val errorHolder = AtomicReference<TextView>()
                instrumentation.runOnMainSync {
                    val activity = activityRef.get()
                    val error = HtmlPreviewActivity::class.java.getDeclaredField("error").apply { isAccessible = true }
                        .get(activity) as TextView
                    errorHolder.set(error)
                    val field = HtmlPreviewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }
                    val deadline = android.os.SystemClock.uptimeMillis() + 15_000
                    val poll = object : Runnable {
                        override fun run() {
                            if (field.get(activity) == null && error.visibility == android.view.View.VISIBLE) gone.countDown()
                            else if (android.os.SystemClock.uptimeMillis() < deadline) error.postDelayed(this, 100)
                        }
                    }
                    pollHolder[0] = poll
                    poll.run()
                }
                try {
                    assertTrue("onRenderProcessGone did not show native error and clear WebView", gone.await(16, TimeUnit.SECONDS))
                } finally {
                    instrumentation.runOnMainSync { pollHolder[0]?.let { errorHolder.get().removeCallbacks(it) } }
                }
                val state = BooleanArray(3)
                instrumentation.runOnMainSync {
                    val activity = activityRef.get()
                    val error = HtmlPreviewActivity::class.java.getDeclaredField("error").apply { isAccessible = true }
                        .get(activity) as TextView
                    state[0] = !activity.isFinishing && !activity.isDestroyed
                    state[1] = error.visibility == android.view.View.VISIBLE
                    state[2] = oldView.parent == null
                }
                assertTrue("Activity should remain alive with native error", state[0] && state[1])
                assertTrue("Failed WebView should be detached", state[2])
                assertEquals("Host process PID changed after renderer exit", processId[0], Process.myPid())
            }

            val reopenFixture = File(instrumentation.targetContext.cacheDir, "html-renderer-reopen-${System.nanoTime()}.html")
            reopenFixture.writeText("<!doctype html><button id=b onclick=\"document.body.dataset.clicked='yes'\">ready</button>")
            try {
                ActivityScenario.launch<HtmlPreviewActivity>(intent.putExtra("filePath", reopenFixture.absolutePath)).use { reopened ->
                    val webViewRef = AtomicReference<WebView>()
                    awaitActivity(reopened) { activity ->
                        val field = HtmlPreviewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }
                        (field.get(activity) as? WebView)?.also(webViewRef::set)
                    }
                    val webView = requireNotNull(webViewRef.get())
                    val result = CountDownLatch(1)
                    instrumentation.runOnMainSync {
                        webView.evaluateJavascript("document.getElementById('b').click();document.body.dataset.clicked") {
                            if (it == "\"yes\"") result.countDown()
                        }
                    }
                    assertTrue("Reopened preview did not execute inline interaction", result.await(10, TimeUnit.SECONDS))
                }
            } finally {
                reopenFixture.delete()
            }
        } finally {
            fixture.delete()
        }
    }

    private fun awaitActivity(
        scenario: ActivityScenario<HtmlPreviewActivity>,
        getView: (HtmlPreviewActivity) -> WebView?
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val domReady = java.util.concurrent.atomic.AtomicBoolean(false)
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val viewRef = AtomicReference<WebView>()
        val pollRef = AtomicReference<Runnable>()
        scenario.onActivity { activity ->
            val view = getView(activity)
            if (view == null) {
                ready.countDown()
            } else {
                viewRef.set(view)
                val deadline = android.os.SystemClock.uptimeMillis() + 15_000
                val poll = object : Runnable {
                    override fun run() {
                        view.evaluateJavascript("document.querySelector('button') ? 'READY' : 'WAIT'") { value ->
                            if (cancelled.get()) return@evaluateJavascript
                            if (value == "\"READY\"") {
                                domReady.set(true)
                                ready.countDown()
                            } else if (android.os.SystemClock.uptimeMillis() < deadline) {
                                view.postDelayed(this, 100)
                            } else ready.countDown()
                        }
                    }
                }
                pollRef.set(poll)
                poll.run()
            }
        }
        try {
            assertTrue("Timed out waiting for preview DOM", ready.await(16, TimeUnit.SECONDS))
            assertTrue("Preview DOM never became ready", domReady.get())
            assertNotNull("Preview WebView was unavailable", viewRef.get())
        } finally {
            cancelled.set(true)
            instrumentation.runOnMainSync { pollRef.get()?.let { viewRef.get()?.removeCallbacks(it) } }
        }
    }
}
