package com.chat.uikit.chat.provider

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.R
import java.io.File
import java.util.concurrent.Executors

class XlsxPreviewActivity : AppCompatActivity() {
    private var filePath = ""
    private var displayName = ""
    private var workbook: XlsxParser.Workbook? = null
    private var activeSheet = 0
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var tabs: LinearLayout
    private lateinit var table: RecyclerView
    private lateinit var header: LinearLayout
    private lateinit var horizontal: HorizontalScrollView
    private lateinit var rowCount: TextView
    private lateinit var sheetNotice: TextView
    private var parseRequest = 0L
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        filePath = intent.getStringExtra("filePath").orEmpty()
        displayName = intent.getStringExtra("title").orEmpty()
        activeSheet = state?.getInt("activeSheet", 0) ?: 0
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = displayName.ifEmpty { "Excel" }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        progress = ProgressBar(this).apply { visibility = View.VISIBLE }
        message = TextView(this).apply { gravity = Gravity.CENTER; visibility = View.GONE; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        table = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@XlsxPreviewActivity) }
        header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        horizontal = HorizontalScrollView(this).apply {
            isFillViewport = true
            val body = LinearLayout(this@XlsxPreviewActivity).apply { orientation = LinearLayout.VERTICAL; addView(header, LinearLayout.LayoutParams(-2, -2)); addView(table, LinearLayout.LayoutParams(-2, 0, 1f)) }
            addView(body, LinearLayout.LayoutParams(-2, -1))
        }
        rowCount = TextView(this).apply { setPadding(dp(12), dp(6), dp(12), dp(6)) }
        sheetNotice = TextView(this).apply { setPadding(dp(12), dp(6), dp(12), dp(6)); visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-1, -2)); root.addView(message, LinearLayout.LayoutParams(-1, -2)); root.addView(sheetNotice, LinearLayout.LayoutParams(-1, -2)); root.addView(horizontal, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(HorizontalScrollView(this).apply { addView(tabs) }, LinearLayout.LayoutParams(-1, -2)); root.addView(rowCount, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        parseAsync()
    }

    private fun parseAsync() {
        val request = ++parseRequest
        tabs.removeAllViews(); header.removeAllViews(); workbook = null
        progress.visibility = View.VISIBLE; message.visibility = View.GONE; message.isEnabled = false; message.setOnClickListener(null); sheetNotice.visibility = View.GONE; sheetNotice.text = ""; table.visibility = View.GONE; table.adapter = null; header.removeAllViews(); rowCount.text = ""
        val file = File(filePath)
        if (!file.exists()) { showError(getString(com.chat.uikit.R.string.str_file_not_exist)); return }
        executor.execute {
            val result = runCatching { XlsxParser.parse(file) }
            runOnUiThread {
                if (isFinishing || isDestroyed || request != parseRequest) return@runOnUiThread
                progress.visibility = View.GONE
                result.onSuccess { renderWorkbook(it) }.onFailure { showError(getString(com.chat.uikit.R.string.xlsx_parse_failed)) }
            }
        }
    }

    private fun renderWorkbook(value: XlsxParser.Workbook) {
        val boundedSheets = value.sheets.take(XlsxParser.MAX_SHEETS).map { sheet ->
            sheet.copy(name = XlsxParser.boundedSheetName(sheet.name))
        }
        val boundedWorkbook = XlsxParser.Workbook(
            boundedSheets,
            value.sheetsTruncated || value.sheets.size > XlsxParser.MAX_SHEETS
        )
        workbook = boundedWorkbook
        sheetNotice.visibility = if (boundedWorkbook.sheetsTruncated) View.VISIBLE else View.GONE
        sheetNotice.text = if (boundedWorkbook.sheetsTruncated) getString(com.chat.uikit.R.string.xlsx_sheets_truncated, XlsxParser.MAX_SHEETS) else ""
        if (boundedWorkbook.sheets.isEmpty()) { tabs.removeAllViews(); showEmpty(); return }
        activeSheet = activeSheet.coerceIn(boundedWorkbook.sheets.indices)
        tabs.removeAllViews()
        if (boundedWorkbook.sheets.size > 1) boundedWorkbook.sheets.forEachIndexed { index, sheet ->
            tabs.addView(TextView(this).apply { text = sheet.name; setPadding(dp(18), dp(8), dp(18), dp(8)); setTextColor(if (index == activeSheet) Color.WHITE else Color.DKGRAY); setBackgroundColor(if (index == activeSheet) Color.rgb(60, 120, 210) else Color.LTGRAY); setOnClickListener { activeSheet = index; renderWorkbook(boundedWorkbook) } }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        val sheet = boundedWorkbook.sheets[activeSheet]
        if (sheet.rows.isEmpty()) {
            if (sheet.truncated) showTruncatedWithoutRows() else showEmpty()
            return
        }
        val columns = maxOf(1, sheet.rows.maxOfOrNull { it.size } ?: 0)
        val width = columns * (dp(120) + dp(1))
        header.layoutParams = LinearLayout.LayoutParams(width, -2); table.layoutParams = LinearLayout.LayoutParams(width, 0, 1f)
        header.removeAllViews(); addRow(header, sheet.rows.first(), true, columns, 0)
        table.adapter = SheetAdapter(sheet, columns) { value, isHeader, row, column -> cell(value, isHeader, row, column) }; table.visibility = View.VISIBLE; table.scrollToPosition(0); message.visibility = View.GONE; message.isEnabled = true
        rowCount.text = getString(com.chat.uikit.R.string.xlsx_rows, maxOf(0, sheet.rows.size - 1)) + if (sheet.truncated) "  ${getString(com.chat.uikit.R.string.xlsx_truncated)}" else ""
        horizontal.scrollTo(0, 0); (table.layoutManager as? LinearLayoutManager)?.scrollToPosition(0)
    }

    private fun showEmpty() { progress.visibility = View.GONE; header.removeAllViews(); table.adapter = null; table.visibility = View.GONE; rowCount.text = ""; horizontal.scrollTo(0, 0); message.visibility = View.VISIBLE; message.isEnabled = true; message.text = getString(com.chat.uikit.R.string.xlsx_empty); message.setOnClickListener(null) }
    private fun showTruncatedWithoutRows() { progress.visibility = View.GONE; header.removeAllViews(); table.adapter = null; table.visibility = View.GONE; rowCount.text = ""; horizontal.scrollTo(0, 0); message.visibility = View.VISIBLE; message.isEnabled = true; message.text = getString(com.chat.uikit.R.string.xlsx_truncated_without_content); message.setOnClickListener(null) }
    private fun showError(text: String) { progress.visibility = View.GONE; sheetNotice.visibility = View.GONE; sheetNotice.text = ""; tabs.removeAllViews(); header.removeAllViews(); table.adapter = null; table.visibility = View.GONE; rowCount.text = ""; horizontal.scrollTo(0, 0); message.visibility = View.VISIBLE; message.isEnabled = true; message.text = "$text\n\n${getString(com.chat.uikit.R.string.xlsx_retry)}"; message.setOnClickListener { message.isEnabled = false; parseAsync() } }
    override fun onSaveInstanceState(out: Bundle) { out.putInt("activeSheet", activeSheet); super.onSaveInstanceState(out) }
    override fun onCreateOptionsMenu(menu: Menu): Boolean { menu.add(0, 1001, 0, R.string.str_file_save_to); menu.add(0, 1002, 1, R.string.str_file_share); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) { android.R.id.home -> { finish(); true }; 1001 -> { saveFile(); true }; 1002 -> { shareFile(); true }; else -> super.onOptionsItemSelected(item) }
    private fun saveFile() { if (filePath.isNotEmpty() && File(filePath).exists()) startActivity(Intent(this, FileSaveActivity::class.java).putExtra("sourceFilePath", filePath).putExtra("fileName", WKFileProvider.boundedSaveName(displayName.ifEmpty { File(filePath).name }, "file"))) }
    private fun shareFile() { val file = File(filePath); if (!file.exists()) return; val name = displayName.ifEmpty { file.name }; runCatching { val uri = FileProvider.getUriForFile(this, "$packageName.fileProvider", file, name); startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = WKFileProvider.getMimeType(name); putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, name)) } }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }

    private fun addRow(row: LinearLayout, values: List<String>, header: Boolean, columns: Int, rowIndex: Int) { row.removeAllViews(); (0 until columns).forEach { index -> row.addView(cell(values.getOrNull(index).orEmpty(), header, rowIndex, index)) } }
    private fun cell(value: String, header: Boolean, rowIndex: Int, columnIndex: Int): TextView = TextView(this).apply {
        text = value.ifEmpty { "-" }; setPadding(dp(8), dp(6), dp(8), dp(6)); minHeight = dp(40)
        setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
        setBackgroundColor(if (header) Color.rgb(245,247,250) else Color.WHITE); layoutParams = LinearLayout.LayoutParams(dp(120), -2).apply { setMargins(0, 0, dp(1), dp(1)) }
        contentDescription = getString(com.chat.uikit.R.string.xlsx_cell_description, rowIndex + 1, columnIndex + 1, value)
        setOnClickListener { showFullText(value) }
    }
    private fun showFullText(value: String) {
        val text = TextView(this).apply { text = value; setTextIsSelectable(true); setPadding(dp(20), dp(16), dp(20), dp(16)) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(com.chat.uikit.R.string.xlsx_view_full_text).setView(ScrollView(this).apply { addView(text) }).setPositiveButton(android.R.string.ok, null).show()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private class SheetAdapter(private val sheet: XlsxParser.Sheet, private val columns: Int, private val factory: (String, Boolean, Int, Int) -> TextView) : RecyclerView.Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = RowHolder(LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL; minimumHeight = (40 * resources.displayMetrics.density).toInt() })
        override fun getItemCount() = maxOf(0, sheet.rows.size - 1)
        override fun onBindViewHolder(holder: RowHolder, position: Int) { holder.bind(sheet.rows[position + 1], columns, position + 1, factory) }
    }
    private class RowHolder(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        fun bind(values: List<String>, columns: Int, rowIndex: Int, factory: (String, Boolean, Int, Int) -> TextView) { row.removeAllViews(); (0 until columns).forEach { index -> row.addView(factory(values.getOrNull(index).orEmpty(), false, rowIndex, index)) } }
    }
}
