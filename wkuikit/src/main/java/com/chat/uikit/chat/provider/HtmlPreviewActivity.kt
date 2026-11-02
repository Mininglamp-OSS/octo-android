/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.chat.provider

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.chat.uikit.R
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class HtmlPreviewActivity : AppCompatActivity() {
    private var filePath = ""
    private var displayName = ""
    private var webView: WebView? = null
    @Volatile private var rendererGone = false
    private lateinit var progress: ProgressBar
    private lateinit var error: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val documentUrl = HtmlPreviewDocumentPolicy.newDocumentUrl()
    private val document = AtomicReference<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayName = intent.getStringExtra("title")?.takeIf { it.isNotEmpty() } ?: ""
        filePath = intent.getStringExtra("filePath") ?: ""
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = displayName.ifEmpty { getString(R.string.str_html_preview) }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        val previewWebView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            // 与 iOS HTML 附件预览一致，保留报表、图表等页面的内联脚本交互。
            // 本页不是完全离线沙箱：附件脚本仍可能通过 WebRTC/ICE 等路径产生网络通信。
            // 文件访问、CSP 和导航限制分别约束对应能力，不构成所有网络出口的隔离。
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.domStorageEnabled = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            // 限制常规资源加载；不能据此推断 WebRTC/STUN 等非 URL 加载路径被禁止。
            settings.blockNetworkLoads = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean =
                    rendererGone || webView !== view || !isDocumentAnchor(view, request.url.toString())

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean =
                    rendererGone || webView !== view || !isDocumentAnchor(view, url)

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (request.isForMainFrame && HtmlPreviewDocumentPolicy.isMainDocumentRequest(
                            documentUrl, url, request.method
                        )
                    ) {
                        val html = document.get() ?: return emptyResponse()
                        return WebResourceResponse(
                            "text/html", "UTF-8", 200, "OK",
                            HtmlPreviewDocumentPolicy.responseHeaders(),
                            ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                        )
                    }
                    if (url.startsWith("data:", ignoreCase = true)) return null
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    if (!rendererGone && webView === view) this@HtmlPreviewActivity.progress.visibility = View.GONE
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {
                    if (webView !== view || rendererGone) return true
                    rendererGone = true
                    document.set(null)
                    executor.shutdownNow()
                    disposeWebView(view)
                    this@HtmlPreviewActivity.progress.visibility = View.GONE
                    error.text = getString(R.string.str_html_renderer_gone)
                    error.visibility = View.VISIBLE
                    return true
                }
            }
        }
        webView = previewWebView
        progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        error = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
            setPadding(32, 32, 32, 32)
            visibility = View.GONE
        }
        root.addView(previewWebView)
        root.addView(progress)
        root.addView(error)
        setContentView(root)
        if (filePath.isEmpty()) {
            showError(getString(R.string.str_file_not_exist))
            return
        }
        executor.execute {
            val result = HtmlPreviewLoader.read(File(filePath))
            runOnUiThread {
                if (isFinishing || isDestroyed || rendererGone) return@runOnUiThread
                val activeWebView = webView ?: return@runOnUiThread
                when (result) {
                    is HtmlPreviewLoader.Result.Content -> {
                        document.set(result.html)
                        if (!rendererGone && webView === activeWebView) activeWebView.loadUrl(documentUrl)
                    }
                    HtmlPreviewLoader.Result.Missing -> showError(getString(R.string.str_file_not_exist))
                    HtmlPreviewLoader.Result.Empty -> showError(getString(R.string.str_html_empty))
                    HtmlPreviewLoader.Result.TooLarge -> showError(getString(R.string.str_html_too_large))
                    HtmlPreviewLoader.Result.Unreadable -> showError(getString(R.string.str_html_unreadable))
                }
            }
        }
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
    )

    private fun isDocumentAnchor(view: WebView, target: String): Boolean {
        if (rendererGone || webView !== view) return false
        val current = view.url ?: return false
        val currentUri = Uri.parse(current)
        val targetUri = Uri.parse(target)
        return targetUri.fragment != null &&
            targetUri.buildUpon().fragment(null).build().toString() ==
            currentUri.buildUpon().fragment(null).build().toString()
    }

    private fun showError(message: String) {
        progress.visibility = View.GONE
        webView?.visibility = View.GONE
        error.text = message
        error.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SAVE, 0, R.string.str_file_save_to)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_SHARE, 1, R.string.str_file_share)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            MENU_SAVE -> {
                saveFile()
                true
            }
            MENU_SHARE -> {
                shareFile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveName(file: File) = displayName.ifEmpty { file.name }

    private fun saveFile() {
        if (filePath.isEmpty()) return
        val file = File(filePath)
        if (!file.exists()) return
        val intent = Intent(this, FileSaveActivity::class.java).apply {
            putExtra("sourceFilePath", file.absolutePath)
            putExtra("fileName", WKFileProvider.boundedSaveName(saveName(file), "file"))
        }
        startActivity(intent)
    }

    private fun shareFile() {
        if (filePath.isEmpty()) return
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val name = saveName(file)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileProvider", file, name)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = WKFileProvider.getMimeType(name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, name))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        rendererGone = true
        executor.shutdownNow()
        document.set(null)
        webView?.let(::disposeWebView)
        super.onDestroy()
    }

    private fun disposeWebView(view: WebView) {
        if (webView !== view) return
        webView = null
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    private companion object {
        const val MENU_SAVE = 1001
        const val MENU_SHARE = 1002
    }
}
