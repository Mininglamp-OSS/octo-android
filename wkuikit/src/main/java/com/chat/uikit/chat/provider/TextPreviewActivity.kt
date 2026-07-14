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
import android.view.Menu
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.chat.base.R
import java.io.File

class TextPreviewActivity : AppCompatActivity() {

    private var filePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("title") ?: "文件预览"
        filePath = intent.getStringExtra("filePath") ?: ""

        // 优先取 caller 预读的 content（避免重复 IO）；缺失则从 filePath 兜底读取，
        // 这样"预览"按钮不用重复实现"读文件 + 截断"逻辑就能复用本 Activity。
        val extraContent = intent.getStringExtra("content")
        val content = extraContent ?: readFromFilePath(filePath)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            this.title = title
        }

        val textView = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 13f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            text = content
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        setContentView(scrollView)
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
            val uri = FileProvider.getUriForFile(
                this,
                packageName + ".fileProvider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = WKFileProvider.getMimeType(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, file.name))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MENU_SAVE = 1001
        private const val MENU_SHARE = 1002

        /** 与 WKFileProvider.TEXT_PREVIEW_EXTS 对齐的截断阈值（字符数）。 */
        private const val MAX_CHARS = 50_000

        private fun readFromFilePath(filePath: String): String {
            if (filePath.isEmpty()) return ""
            return try {
                val text = File(filePath).readText(Charsets.UTF_8)
                if (text.length > MAX_CHARS) {
                    text.substring(0, MAX_CHARS) + "\n\n... (文件过大，仅显示前${MAX_CHARS}字符)"
                } else text
            } catch (_: Exception) {
                ""
            }
        }
    }
}
