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
    private lateinit var webView: WebView
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
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.domStorageEnabled = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.blockNetworkLoads = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean =
                    !isDocumentAnchor(view, request.url.toString())

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean =
                    !isDocumentAnchor(view, url)

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
                    this@HtmlPreviewActivity.progress.visibility = View.GONE
                }
            }
        }
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
        root.addView(webView)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (result) {
                    is HtmlPreviewLoader.Result.Content -> {
                        document.set(result.html)
                        webView.loadUrl(documentUrl)
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
        val current = view.url ?: return false
        val currentUri = Uri.parse(current)
        val targetUri = Uri.parse(target)
        return targetUri.fragment != null &&
            targetUri.buildUpon().fragment(null).build().toString() ==
            currentUri.buildUpon().fragment(null).build().toString()
    }

    private fun showError(message: String) {
        progress.visibility = View.GONE
        webView.visibility = View.GONE
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
        executor.shutdownNow()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        document.set(null)
        super.onDestroy()
    }

    private companion object {
        const val MENU_SAVE = 1001
        const val MENU_SHARE = 1002
    }
}
