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
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * App 内 PDF 预览。用 [android.graphics.pdf.PdfRenderer]（API 21+ 内置），
 * 每页按屏幕宽度渲染成 Bitmap，RecyclerView 懒加载。
 *
 * 线程模型：`PdfRenderer` 文档要求"所有操作在同一线程"。此处所有 renderer 访问
 * （open / openPage / render / close）都走 [renderExecutor]（单线程）序列化，
 * 结果通过 [mainHandler] 回主线程 setImageBitmap。避免主线程解码超大页 → ANR/OOM。
 *
 * OOM 防护：大页按 [MAX_BITMAP_PIXELS] 等比缩放；`createBitmap`/`render` 失败
 * （包括 [OutOfMemoryError]）单页跳过，不影响其它页。
 */
class PdfPreviewActivity : AppCompatActivity() {

    private var filePath: String = ""
    @Volatile private var pdfRenderer: PdfRenderer? = null
    @Volatile private var fileDescriptor: ParcelFileDescriptor? = null
    @Volatile private var pageCount: Int = 0
    private var pageWidthPx: Int = 0
    private val renderExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

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

        renderExecutor.execute {
            val ok = openPdfOnExecutor()
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                if (!ok || pdfRenderer == null || pageCount <= 0) {
                    WKToastUtils.getInstance().showToastNormal(getString(R.string.str_file_not_exist))
                    finish()
                    return@post
                }
                recyclerView.adapter = PdfPageAdapter(
                    pdfRenderer!!, pageCount, pageWidthPx, renderExecutor, mainHandler
                )
            }
        }
    }

    /** 必须在 [renderExecutor] 上执行——PdfRenderer 线程绑定。 */
    private fun openPdfOnExecutor(): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = fd
            val renderer = PdfRenderer(fd)
            pdfRenderer = renderer
            pageCount = renderer.pageCount
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
        // renderer/fd 必须在 renderExecutor 上关闭（线程绑定），提交后再 shutdown。
        val r = pdfRenderer
        val fd = fileDescriptor
        pdfRenderer = null
        fileDescriptor = null
        renderExecutor.execute {
            try { r?.close() } catch (_: Exception) {}
            try { fd?.close() } catch (_: Exception) {}
        }
        renderExecutor.shutdown()
    }

    private class PdfPageAdapter(
        private val renderer: PdfRenderer,
        private val pageCount: Int,
        private val pageWidthPx: Int,
        private val renderExecutor: ExecutorService,
        private val mainHandler: Handler,
    ) : RecyclerView.Adapter<PdfPageAdapter.VH>() {

        class VH(val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
            /** 当前请求的页码；异步 render 完成时用于比对是否已被回收/复用。 */
            @Volatile var boundPage: Int = -1
        }

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
            holder.boundPage = position
            holder.imageView.setImageDrawable(null)
            renderExecutor.execute {
                val bmp = try {
                    renderPage(position)
                } catch (_: Exception) {
                    null
                }
                mainHandler.post {
                    if (holder.boundPage == position && bmp != null) {
                        holder.imageView.setImageBitmap(bmp)
                    }
                    // 若 holder 已被复用/回收，丢弃这张 bitmap（不主动 recycle，让 GC 处理，
                    // 避免"trying to use a recycled bitmap"在硬件加速 display list 里踩到）。
                }
            }
        }

        /** 在 [renderExecutor] 上执行——PdfRenderer 线程绑定。 */
        private fun renderPage(position: Int): Bitmap? {
            val page = try { renderer.openPage(position) } catch (_: Exception) { return null }
            return try {
                val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val h0 = (pageWidthPx * ratio).toInt().coerceAtLeast(1)
                val (w, h) = clampToMaxPixels(pageWidthPx, h0)
                // Bitmap.createBitmap 在极端尺寸下会抛 OutOfMemoryError，单页失败不影响
                // 其它页的渲染——所以 catch 掉返 null。
                try {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } catch (_: OutOfMemoryError) {
                    null
                } catch (_: Exception) {
                    null
                }
            } finally {
                try { page.close() } catch (_: Exception) {}
            }
        }

        /** 按最大总像素数等比缩放，避免 `createBitmap` 分配几百 MB。 */
        private fun clampToMaxPixels(w: Int, h: Int): Pair<Int, Int> {
            val total = w.toLong() * h.toLong()
            if (total <= MAX_BITMAP_PIXELS) return w to h
            val scale = sqrt(MAX_BITMAP_PIXELS.toDouble() / total.toDouble())
            return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
        }

        override fun onViewRecycled(holder: VH) {
            holder.boundPage = -1
            // 不主动 recycle bitmap：可能仍在 hardware display list 里被引用。
            // 清除 drawable 引用后交给 GC 回收更安全。
            holder.imageView.setImageDrawable(null)
        }

        override fun getItemCount(): Int = pageCount
    }

    companion object {
        private const val MENU_SAVE = 1001
        private const val MENU_SHARE = 1002

        /** 单张 bitmap 允许的最大总像素数。8M px * 4B/px (ARGB_8888) = 32 MB。 */
        private const val MAX_BITMAP_PIXELS: Long = 8_000_000L
    }
}
