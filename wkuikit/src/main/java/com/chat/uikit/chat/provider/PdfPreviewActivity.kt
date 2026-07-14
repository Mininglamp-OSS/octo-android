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
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.R
import com.chat.base.utils.WKToastUtils
import java.io.File

/**
 * App 内 PDF 预览。用 [android.graphics.pdf.PdfRenderer]（API 21+ 内置），
 * 每页按屏幕宽度渲染成 Bitmap，RecyclerView 懒加载。
 *
 * 对齐 iOS `WKSafeFilePreviewVC.setupPDFViewInFrame:` 的行为：不走系统外部 App。
 */
class PdfPreviewActivity : AppCompatActivity() {

    private var filePath: String = ""
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pageWidthPx: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("title") ?: "PDF"
        filePath = intent.getStringExtra("filePath") ?: ""

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            this.title = title
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getMetrics(metrics)
        pageWidthPx = metrics.widthPixels

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PdfPreviewActivity)
            setBackgroundColor(Color.parseColor("#EFEFEF"))
        }
        setContentView(recyclerView)

        val opened = openPdf()
        if (!opened) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.str_file_not_exist))
            finish()
            return
        }
        val renderer = pdfRenderer ?: return
        recyclerView.adapter = PdfPageAdapter(renderer, pageWidthPx)
    }

    private fun openPdf(): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = fd
            pdfRenderer = PdfRenderer(fd)
            true
        } catch (_: Exception) {
            false
        }
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
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { fileDescriptor?.close() } catch (_: Exception) {}
    }

    private class PdfPageAdapter(
        private val renderer: PdfRenderer,
        private val pageWidthPx: Int,
    ) : RecyclerView.Adapter<PdfPageAdapter.VH>() {

        class VH(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val marginPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, parent.resources.displayMetrics
            ).toInt()
            val iv = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(marginPx, marginPx, marginPx, marginPx) }
                adjustViewBounds = true
                setBackgroundColor(Color.WHITE)
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = try { renderer.openPage(position) } catch (_: Exception) { return }
            try {
                val ratio = page.height.toFloat() / page.width.toFloat()
                val h = (pageWidthPx * ratio).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(pageWidthPx, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                holder.imageView.setImageBitmap(bmp)
            } finally {
                try { page.close() } catch (_: Exception) {}
            }
        }

        override fun onViewRecycled(holder: VH) {
            (holder.imageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
            holder.imageView.setImageDrawable(null)
        }

        override fun getItemCount(): Int = renderer.pageCount
    }

    companion object {
        private const val MENU_SAVE = 1001
        private const val MENU_SHARE = 1002
    }
}
