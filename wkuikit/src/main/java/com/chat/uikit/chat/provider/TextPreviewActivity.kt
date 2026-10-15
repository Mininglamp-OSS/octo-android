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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.chat.base.R
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TextPreviewActivity : AppCompatActivity() {

    private var filePath: String = ""
    /** 原始展示名（上游 putExtra("title", display) 传入）。磁盘名是 SHA-256 派生的
     *  64 位 hex，不能用 file.name 做保存建议名 / 分享标题 / MIME 判断。 */
    private var displayName: String = ""
    private val loadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("title") ?: "文件预览"
        displayName = intent.getStringExtra("title")?.takeIf { it.isNotEmpty() } ?: ""
        filePath = intent.getStringExtra("filePath") ?: ""

        // 优先取 caller 预读的 content（避免重复 IO）；缺失则从 filePath 兜底读取，
        // 这样"预览"按钮不用重复实现"读文件 + 截断"逻辑就能复用本 Activity。
        val extraContent = intent.getStringExtra("content")

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            this.title = title
        }

        val textView = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 13f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            text = extraContent ?: ""
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        setContentView(scrollView)

        // 没有预读 content 时后台线程 bounded read，避免 100MB 文件卡死主线程 (#92 review B5)。
        if (extraContent == null && filePath.isNotEmpty()) {
            loadExecutor.execute {
                val content = TextPreviewLoader.readBounded(File(filePath), MAX_CHARS)
                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post
                    textView.text = content
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loadExecutor.shutdownNow()
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

    /** 展示名优先，缺失时退回磁盘名（旧缓存/直接 Intent 进入的兜底）。 */
    private fun saveName(file: File): String =
        displayName.takeIf { it.isNotEmpty() } ?: file.name

    private fun saveFile() {
        if (filePath.isEmpty()) return
        val file = File(filePath)
        if (!file.exists()) return
        val intent = Intent(this, FileSaveActivity::class.java)
        intent.putExtra("sourceFilePath", file.absolutePath)
        intent.putExtra("fileName", WKFileProvider.safePathSegment(saveName(file), "file"))
        startActivity(intent)
    }

    private fun shareFile() {
        if (filePath.isEmpty()) return
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                this,
                packageName + ".fileProvider",
                file,
                saveName(file)
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = WKFileProvider.getMimeType(saveName(file))
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, saveName(file)))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MENU_SAVE = 1001
        private const val MENU_SHARE = 1002

        /** 与 WKFileProvider.TEXT_PREVIEW_EXTS 对齐的截断阈值（字符数）。 */
        private const val MAX_CHARS = 50_000
    }
}
