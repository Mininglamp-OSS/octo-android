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

package com.chat.base.act

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.chat.base.R
import com.chat.base.base.WKBaseActivity
import com.chat.base.databinding.ActMarkdownViewLayoutBinding
import com.chat.base.entity.PopupMenuItem
import com.chat.base.markdown.WKMarkwonProvider
import com.chat.base.markdown.WKTableData
import com.chat.base.markdown.WKTablePlugin
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKToastUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class WKMarkdownViewActivity : WKBaseActivity<ActMarkdownViewLayoutBinding>() {

    private var url: String = ""

    override fun getViewBinding(): ActMarkdownViewLayoutBinding {
        return ActMarkdownViewLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {}

    override fun initPresenter() {}

    override fun getBackResourceID(backIv: ImageView): Int {
        return R.mipmap.ic_ab_back
    }

    override fun getRightIvResourceId(imageView: ImageView): Int {
        return R.mipmap.ic_ab_other
    }

    override fun rightLayoutClick() {
        super.rightLayoutClick()
        val list = mutableListOf<PopupMenuItem>()
        list.add(PopupMenuItem(getString(R.string.copy_url), R.mipmap.search_links) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Label", url))
            WKToastUtils.getInstance().showToastNormal(getString(R.string.copyed))
        })
        list.add(PopupMenuItem(getString(R.string.refresh), R.mipmap.tool_rotate) {
            loadMarkdown()
        })
        list.add(PopupMenuItem(getString(R.string.open_system_browser), R.mipmap.msg_openin) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        })
        val rightIV = findViewById<ImageView>(R.id.titleRightIv)
        WKDialogUtils.getInstance().showScreenPopup(rightIV, list)
    }

    override fun initView() {
        url = intent.getStringExtra("url") ?: ""
        if (TextUtils.isEmpty(url)) {
            WKToastUtils.getInstance().showToast(getString(R.string.nodata))
            finish()
            return
        }
        val fileName = Uri.parse(url).lastPathSegment ?: "Markdown"
        findViewById<TextView>(R.id.titleCenterTv).text = fileName

        wkVBinding.contentTv.movementMethod = LinkMovementMethod.getInstance()
        loadMarkdown()
    }

    override fun initListener() {}

    private fun loadMarkdown() {
        wkVBinding.progress.visibility = View.VISIBLE
        wkVBinding.contentTv.text = ""
        removeTableViews()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    wkVBinding.progress.visibility = View.GONE
                    wkVBinding.contentTv.text = getString(R.string.nodata)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val content = response.body?.string() ?: ""
                runOnUiThread {
                    wkVBinding.progress.visibility = View.GONE
                    if (content.isEmpty()) {
                        wkVBinding.contentTv.text = getString(R.string.nodata)
                    } else {
                        renderMarkdownWithTables(content)
                    }
                }
            }
        })
    }

    private fun renderMarkdownWithTables(content: String) {
        val (rendered, tableDataList) = WKMarkwonProvider.toMarkdownWithTables(this, content)

        if (tableDataList.isEmpty()) {
            wkVBinding.contentTv.visibility = View.VISIBLE
            wkVBinding.contentTv.text = rendered
            return
        }

        val fullText = rendered.toString()
        val placeholderPositions = mutableListOf<Int>()
        var searchIdx = 0
        while (searchIdx < fullText.length) {
            val pos = fullText.indexOf(WKTablePlugin.TABLE_PLACEHOLDER, searchIdx)
            if (pos < 0) break
            placeholderPositions.add(pos)
            searchIdx = pos + 1
        }

        if (placeholderPositions.size != tableDataList.size) {
            wkVBinding.contentTv.visibility = View.VISIBLE
            wkVBinding.contentTv.text = rendered
            for (tableData in tableDataList) {
                wkVBinding.contentLayout.addView(buildTableCardView(tableData))
            }
            return
        }

        val segments = mutableListOf<CharSequence>()
        var start = 0
        for (pos in placeholderPositions) {
            segments.add(rendered.subSequence(start, pos))
            start = pos + WKTablePlugin.TABLE_PLACEHOLDER.length
        }
        segments.add(rendered.subSequence(start, rendered.length))

        val firstSegment = trimEdgeNewlines(segments[0])
        if (firstSegment.isBlank()) {
            wkVBinding.contentTv.visibility = View.GONE
        } else {
            wkVBinding.contentTv.visibility = View.VISIBLE
            wkVBinding.contentTv.text = firstSegment
        }

        val textColor = wkVBinding.contentTv.currentTextColor
        for (i in tableDataList.indices) {
            wkVBinding.contentLayout.addView(buildTableCardView(tableDataList[i]))

            val nextSegment = segments.getOrNull(i + 1) ?: continue
            val trimmed = trimEdgeNewlines(nextSegment)
            if (trimmed.isBlank()) continue

            val extraTv = TextView(this).apply {
                text = trimmed
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, wkVBinding.contentTv.textSize)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(4f * resources.displayMetrics.density, 1f)
                setTextIsSelectable(true)
                tag = "table_card"
            }
            wkVBinding.contentLayout.addView(extraTv)
        }
    }

    private fun removeTableViews() {
        val toRemove = mutableListOf<View>()
        for (i in 0 until wkVBinding.contentLayout.childCount) {
            val child = wkVBinding.contentLayout.getChildAt(i)
            if (child.tag == "table_card") {
                toRemove.add(child)
            }
        }
        toRemove.forEach { wkVBinding.contentLayout.removeView(it) }
    }

    private fun trimEdgeNewlines(cs: CharSequence): CharSequence {
        var s = 0
        var e = cs.length
        while (s < e && cs[s] == '\n') s++
        while (e > s && cs[e - 1] == '\n') e--
        return if (s == 0 && e == cs.length) cs else cs.subSequence(s, e)
    }

    private fun buildTableCardView(tableData: WKTableData): View {
        val cardView = LayoutInflater.from(this)
            .inflate(R.layout.layout_markdown_table_card, wkVBinding.contentLayout, false)
        cardView.tag = "table_card"

        val tableContent = cardView.findViewById<TableLayout>(R.id.tableContent)
        val tableScrollView = cardView.findViewById<HorizontalScrollView>(R.id.tableScrollView)
        val copyBtn = cardView.findViewById<ImageView>(R.id.tableCopyBtn)

        if (tableData.headers.isEmpty() && tableData.rows.isEmpty()) {
            tableContent.setStretchAllColumns(false)
            return cardView
        }

        tableScrollView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        val dp = resources.displayMetrics.density
        val cellPaddingH = (10 * dp).toInt()
        val cellPaddingV = (8 * dp).toInt()
        val textSize = 13f
        val headerBgColor = Color.parseColor("#F0F0F0")
        val evenRowBgColor = Color.parseColor("#FAFAFA")
        val headerTextColor = Color.parseColor("#333333")
        val cellTextColor = Color.parseColor("#555555")

        tableContent.setStretchAllColumns(true)

        if (tableData.headers.isNotEmpty()) {
            val headerRow = TableRow(this)
            headerRow.setBackgroundColor(headerBgColor)
            for ((colIdx, header) in tableData.headers.withIndex()) {
                headerRow.addView(
                    createCellTextView(
                        header.text, textSize, cellPaddingH, cellPaddingV,
                        headerTextColor, true, tableData, colIdx
                    )
                )
            }
            tableContent.addView(headerRow)
        }

        for ((rowIdx, row) in tableData.rows.withIndex()) {
            val tableRow = TableRow(this)
            if (rowIdx % 2 == 1) tableRow.setBackgroundColor(evenRowBgColor)
            for ((colIdx, cell) in row.withIndex()) {
                tableRow.addView(
                    createCellTextView(
                        cell.text, textSize, cellPaddingH, cellPaddingV,
                        cellTextColor, false, tableData, colIdx
                    )
                )
            }
            tableContent.addView(tableRow)
        }

        copyBtn.setOnClickListener {
            val sb = StringBuilder()
            if (tableData.headers.isNotEmpty()) {
                sb.appendLine(tableData.headers.joinToString("\t") { it.text })
            }
            for (row in tableData.rows) {
                sb.appendLine(row.joinToString("\t") { it.text })
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("table", sb.toString().trimEnd()))
            WKToastUtils.getInstance().showToastNormal(getString(R.string.str_table_copied))
        }

        return cardView
    }

    private fun createCellTextView(
        text: String,
        textSize: Float,
        paddingH: Int,
        paddingV: Int,
        textColor: Int,
        isBold: Boolean,
        tableData: WKTableData,
        colIdx: Int
    ): TextView {
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            setTextColor(textColor)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            if (isBold) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            minWidth = (60 * dp).toInt()
            maxWidth = (200 * dp).toInt()

            if (colIdx < tableData.alignments.size) {
                gravity = when (tableData.alignments[colIdx]) {
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER -> android.view.Gravity.CENTER
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT -> android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                    else -> android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                }
            }

            if (colIdx > 0) {
                val lp = TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins((0.5 * dp).toInt(), 0, 0, 0)
                layoutParams = lp
            }
        }
    }
}
