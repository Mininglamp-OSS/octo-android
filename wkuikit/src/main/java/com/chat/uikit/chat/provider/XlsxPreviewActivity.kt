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
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.chat.base.R
import java.io.File
import java.util.concurrent.Executors

/**
 * App 内 xlsx 预览。零依赖解析 → HTML table → WebView 渲染（JS 关闭）。
 * 对齐 iOS `WKSafeFilePreviewVC` 走 WebKit 渲染 Office 的行为，但 Android WebView
 * 不能直接吃 xlsx，所以在客户端做一层轻量解析。
 *
 * 局限：只显示单元格文本值（`<v>` 或共享字符串），不还原样式 / 数字格式 / 日期格式。
 * 参考 [XlsxParser] 的 `resolveCellText`。
 */
class XlsxPreviewActivity : AppCompatActivity() {

    private var filePath: String = ""
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorTv: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("title") ?: "Excel"
        filePath = intent.getStringExtra("filePath") ?: ""

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            this.title = title
        }

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 只渲染受信 HTML 字符串，且解析产物无外链 → JS/文件访问全部关掉
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
        progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
        }
        errorTv = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
            textSize = 14f
            visibility = View.GONE
        }
        root.addView(webView)
        root.addView(progressBar)
        root.addView(errorTv)
        setContentView(root)

        parseAsync()
    }

    private fun parseAsync() {
        val file = File(filePath)
        if (!file.exists()) {
            showError(getString(R.string.str_file_not_exist))
            return
        }
        executor.execute {
            val result = runCatching { XlsxParser.parse(file) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progressBar.visibility = View.GONE
                result.onSuccess { renderWorkbook(it) }
                    .onFailure { showError("无法预览此 Excel 文件") }
            }
        }
    }

    private fun renderWorkbook(wb: XlsxParser.Workbook) {
        if (wb.sheets.isEmpty()) {
            showError("空表格")
            return
        }
        val html = buildHtml(wb)
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun showError(msg: String) {
        webView.visibility = View.GONE
        errorTv.text = msg
        errorTv.visibility = View.VISIBLE
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
            android.R.id.home -> { finish(); true }
            MENU_SAVE -> { saveFile(); true }
            MENU_SHARE -> { shareFile(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveFile() {
        if (filePath.isEmpty()) return
        val file = File(filePath)
        if (!file.exists()) return
        val intent = Intent(this, FileSaveActivity::class.java)
        intent.putExtra("sourceFilePath", file.absolutePath)
        intent.putExtra("fileName", file.name)
        startActivity(intent)
    }

    private fun shareFile() {
        if (filePath.isEmpty()) return
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileProvider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = WKFileProvider.getMimeType(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, file.name))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
        try { webView.destroy() } catch (_: Exception) {}
    }

    private fun buildHtml(wb: XlsxParser.Workbook): String {
        val sb = StringBuilder(4096)
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
        sb.append("<style>")
        sb.append("body{margin:0;padding:0;font-family:-apple-system,sans-serif;font-size:12px;color:#222;}")
        sb.append(".tabs{position:sticky;top:0;background:#F5F5F5;border-bottom:1px solid #DDD;padding:6px 8px;white-space:nowrap;overflow-x:auto;}")
        sb.append(".tabs a{display:inline-block;padding:4px 10px;margin-right:4px;border:1px solid #CCC;border-radius:4px;color:#333;text-decoration:none;background:#FFF;}")
        sb.append(".sheet{padding:8px;overflow-x:auto;}")
        sb.append(".sheet h3{margin:12px 0 6px 0;font-size:13px;color:#666;}")
        sb.append("table{border-collapse:collapse;}")
        sb.append("td{border:1px solid #DDD;padding:4px 8px;white-space:nowrap;max-width:240px;overflow:hidden;text-overflow:ellipsis;}")
        sb.append("tr:nth-child(even) td{background:#FAFAFA;}")
        sb.append(".trunc{color:#999;font-size:11px;padding:6px 4px;}")
        sb.append("</style></head><body>")

        if (wb.sheets.size > 1) {
            sb.append("<div class='tabs'>")
            wb.sheets.forEachIndexed { i, s ->
                sb.append("<a href='#s$i'>").append(escape(s.name)).append("</a>")
            }
            sb.append("</div>")
        }

        wb.sheets.forEachIndexed { i, s ->
            sb.append("<div class='sheet' id='s").append(i).append("'>")
            if (wb.sheets.size > 1) sb.append("<h3>").append(escape(s.name)).append("</h3>")
            sb.append("<table>")
            for (row in s.rows) {
                sb.append("<tr>")
                for (cell in row) {
                    sb.append("<td>").append(escape(cell)).append("</td>")
                }
                sb.append("</tr>")
            }
            sb.append("</table>")
            if (s.truncated) {
                sb.append("<div class='trunc'>已截断显示（>")
                sb.append(XlsxParser.MAX_ROWS).append("行 或 >")
                sb.append(XlsxParser.MAX_COLS).append("列），完整内容请用其它应用打开</div>")
            }
            sb.append("</div>")
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escape(s: String): String {
        if (s.isEmpty()) return ""
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    companion object {
        private const val MENU_SAVE = 1001
        private const val MENU_SHARE = 1002
    }
}
