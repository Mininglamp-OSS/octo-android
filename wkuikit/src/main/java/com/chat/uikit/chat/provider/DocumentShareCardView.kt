package com.chat.uikit.chat.provider

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.view.ViewGroup
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.uikit.R
import com.chat.uikit.chat.msgmodel.WKDocumentShareContent
import com.chat.base.ui.Theme

class DocumentShareCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    interface Listener { fun onOpen(); fun onCopy(); fun onWindowFocusChanged(hasWindowFocus: Boolean); fun onAttach(hasWindowFocus: Boolean); fun onDetach(); fun onInstallLongClick() }
    private val icon = ImageView(context)
    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val badge = TextView(context)
    private val preview = LinearLayout(context)
    private val copy = ImageView(context)
    private lateinit var header: LinearLayout
    private lateinit var headerRow: LinearLayout
    private lateinit var text: LinearLayout
    private var listener: Listener? = null
    private var subscription: DocumentSharePreviewRepository.Subscription? = null
    private var state = DocumentSharePreviewRepository.State.loading
    private var resultContext: DocumentSharePreviewRepository.Context? = null
    private var currentKind: String = "doc"
    private var renderedPreviewType: String = ""
    private var lifecycleVersion = 0L

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply { cornerRadius = dp(8).toFloat() }
        icon.layoutParams = LayoutParams(dp(32), dp(32))
        header = LinearLayout(context).apply { orientation = VERTICAL }
        headerRow = LinearLayout(context).apply { gravity = Gravity.TOP }
        text = LinearLayout(context).apply { orientation = VERTICAL; layoutParams = LayoutParams(0, -2); setPadding(dp(8), 0, dp(6), 0) }
        title.maxLines = 1; title.ellipsize = android.text.TextUtils.TruncateAt.END; title.textSize = 14f; title.gravity = Gravity.CENTER_VERTICAL; title.setTypeface(title.typeface, Typeface.BOLD)
        subtitle.maxLines = 1; subtitle.ellipsize = android.text.TextUtils.TruncateAt.END; subtitle.textSize = 12f; subtitle.setPadding(0, dp(4), 0, 0)
        text.addView(title); text.addView(subtitle)
        badge.maxLines = 1; badge.ellipsize = android.text.TextUtils.TruncateAt.END; badge.textSize = 11f; badge.gravity = Gravity.CENTER; badge.minWidth = dp(54); badge.minHeight = dp(20)
        badge.setPadding(dp(6), dp(2), dp(6), dp(2))
        copy.setImageResource(R.drawable.ic_copy); copy.contentDescription = context.getString(R.string.doc_share_copy)
        copy.setPadding(dp(2), dp(2), dp(2), dp(2)); copy.alpha = 0.58f
        copy.tag = "doc-copy"
        badge.tag = "doc-status"
        headerRow.addView(icon); headerRow.addView(text); headerRow.addView(badge); headerRow.addView(copy, LayoutParams(dp(28), dp(28)))
        header.addView(headerRow)
        preview.orientation = VERTICAL; preview.minimumHeight = dp(264); preview.layoutParams = LayoutParams(-1, dp(264)); preview.gravity = Gravity.CENTER; preview.clipChildren = true; preview.clipToPadding = true
        preview.setPadding(dp(24), dp(24), dp(24), dp(24)); preview.clipToOutline = true
        preview.outlineProvider = ViewOutlineProvider.BACKGROUND
        addView(header, LayoutParams(-1, -2)); addView(preview, LayoutParams(-1, dp(264)).apply { setMargins(0, dp(12), 0, 0) })
        setIcon("doc")
        installInteractions()
    }

    fun bindMessage(content: WKDocumentShareContent, listener: Listener): Long {
        clearBinding()
        bindingVersion++
        this.listener = listener
        installInteractions()
        isClickable = true
        isLongClickable = true
        title.text = content.title.takeUnless { it.isNullOrEmpty() } ?: context.getString(R.string.doc_share_untitled)
        badge.text = context.getString(R.string.doc_share_checking)
        badge.contentDescription = badge.text
        subtitle.text = if (content.ownerName.isNullOrEmpty()) "" else context.getString(R.string.doc_share_created_by, content.ownerName)
        subtitle.visibility = if (subtitle.text.isNullOrEmpty()) GONE else VISIBLE
        headerRow.minimumHeight = 0
        text.removeView(subtitle); text.removeView(title); text.addView(title); if (subtitle.visibility == VISIBLE) text.addView(subtitle)
        setIcon(DocumentSharePreviewRepository.normalizeKind(content.kind))
        currentKind = DocumentSharePreviewRepository.normalizeKind(content.kind)
        applyTheme()
        setOnClickListener(null)
        return bindingVersion
    }

    fun renderResult(content: WKDocumentShareContent, result: DocumentSharePreviewRepository.Result) {
        state = result.state
        resultContext = result.context
        val value = result.preview
        renderedPreviewType = if (result.state == DocumentSharePreviewRepository.State.ready) when (value) {
            is DocumentSharePreviewParser.Preview.Board -> "board"
            is DocumentSharePreviewParser.Preview.Sheet -> "sheet"
            is DocumentSharePreviewParser.Preview.Doc -> "doc"
            else -> ""
        } else ""
        badge.text = when (result.state) {
            DocumentSharePreviewRepository.State.loading -> context.getString(R.string.doc_share_checking)
            DocumentSharePreviewRepository.State.denied -> context.getString(R.string.doc_share_requires_access)
            DocumentSharePreviewRepository.State.unavailable -> context.getString(R.string.doc_share_unavailable)
            DocumentSharePreviewRepository.State.error -> context.getString(R.string.doc_share_error)
            DocumentSharePreviewRepository.State.empty -> context.getString(R.string.doc_share_can_view)
            DocumentSharePreviewRepository.State.ready -> context.getString(R.string.doc_share_can_view)
        }
        badge.contentDescription = badge.text
        applyTheme()
        preview.removeAllViews()
        preview.setPadding(dp(24), dp(24), dp(24), dp(24))
        (preview.layoutParams as? LayoutParams ?: LayoutParams(-1, dp(264))).apply {
            width = -1; height = dp(264); setMargins(0, dp(12), 0, 0); preview.layoutParams = this
        }
        preview.gravity = Gravity.CENTER
        if (result.state == DocumentSharePreviewRepository.State.ready && value != null) {
            when (value) {
                is DocumentSharePreviewParser.Preview.Doc -> {
                    preview.gravity = Gravity.TOP or Gravity.START
                    addPreviewTitle(title.text.toString())
                    value.heading?.let(::addHeading); value.paragraphs.take(3).forEach(::addLine)
                    addDocumentOrnaments()
                }
                is DocumentSharePreviewParser.Preview.Sheet -> { preview.gravity = Gravity.TOP or Gravity.START; renderSheet(value) }
                is DocumentSharePreviewParser.Preview.Board -> renderBoard(value)
            }
        } else addPlaceholder(result.state)
        val available = listener != null && result.state != DocumentSharePreviewRepository.State.unavailable
        preview.setOnClickListener(if (available) View.OnClickListener { listener?.onOpen() } else null)
        preview.isEnabled = available
        preview.isClickable = available
        contentDescription = title.text
    }

    private var bindingVersion = 0L
    fun isCurrent(version: Long): Boolean = version == bindingVersion && isAttachedToWindow
    fun isCurrent(version: Long, lifecycle: Long): Boolean = version == bindingVersion && lifecycle == lifecycleVersion && isAttachedToWindow
    fun lifecycleVersion(): Long = lifecycleVersion
    fun isOpenable(): Boolean = state != DocumentSharePreviewRepository.State.unavailable
    fun currentResult(): DocumentSharePreviewRepository.Result = DocumentSharePreviewRepository.Result(state, context = resultContext)

    fun setSubscription(value: DocumentSharePreviewRepository.Subscription?) { subscription?.cancel(); subscription = value }
    fun clearBinding() { listener?.onDetach(); bindingVersion++; subscription?.cancel(); subscription = null; listener = null; headerRow.touchDelegate = null; preview.removeAllViews(); preview.setOnClickListener(null); preview.setOnLongClickListener(null); preview.isEnabled = false; preview.isClickable = false; preview.isLongClickable = false; copy.setOnClickListener(null); copy.setOnLongClickListener(null); copy.isClickable = false; copy.isLongClickable = false; setOnClickListener(null); setOnLongClickListener(null); isClickable = false; isLongClickable = false; title.text = ""; subtitle.text = ""; subtitle.visibility = GONE; badge.text = ""; state = DocumentSharePreviewRepository.State.loading; renderedPreviewType = ""; resultContext = null; setIcon("doc") }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); lifecycleVersion++; installInteractions(); listener?.onAttach(hasWindowFocus()) }
    override fun onDetachedFromWindow() { lifecycleVersion++; subscription?.cancel(); subscription = null; headerRow.touchDelegate = null; preview.removeAllViews(); preview.setOnClickListener(null); preview.setOnLongClickListener(null); preview.isEnabled = false; preview.isClickable = false; preview.isLongClickable = false; copy.setOnClickListener(null); copy.setOnLongClickListener(null); copy.isClickable = false; copy.isLongClickable = false; setOnClickListener(null); setOnLongClickListener(null); isClickable = false; isLongClickable = false; resultContext = null; state = DocumentSharePreviewRepository.State.loading; listener?.onDetach(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); listener?.onWindowFocusChanged(hasWindowFocus) }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateCopyTouchDelegate()
    }

    private fun updateCopyTouchDelegate() {
        if (listener == null || !copy.isLaidOut) {
            headerRow.touchDelegate = null
            return
        }
        val requestedLeft = copy.left - dp(8)
        val badgeEdge = if (badge.visibility == VISIBLE) badge.right else Int.MIN_VALUE
        val left = maxOf(requestedLeft, badgeEdge)
        val rect = android.graphics.Rect(left, copy.top - dp(8), copy.right + dp(8), copy.bottom + dp(8))
        headerRow.touchDelegate = if (rect.width() > 0 && rect.height() > 0) android.view.TouchDelegate(rect, copy) else null
    }

    private fun renderSheet(sheet: DocumentSharePreviewParser.Preview.Sheet) {
        preview.setPadding(dp(16), dp(16), dp(16), dp(16))
        val table = LinearLayout(context).apply { orientation = VERTICAL; setBackgroundColor(palette().line) }
        val columns = 3
        addSheetTitle(title.text.toString())
        addRow(table, sheet.headers.take(columns), true, columns); sheet.rows.take(3).forEach { addRow(table, it.take(columns), false, columns) }; preview.addView(table, LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }
    private fun addSheetTitle(value: String) { preview.addView(TextView(context).apply { text = value; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(palette().primary); gravity = Gravity.CENTER_VERTICAL; layoutParams = LayoutParams(-1, dp(18)) }) }
    private fun addRow(table: LinearLayout, values: List<String>, header: Boolean, columns: Int) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        (0 until columns).forEach { index -> row.addView(TextView(context).apply { text = values.getOrNull(index).orEmpty(); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 12f; setTextColor(if (header) palette().primary else palette().secondary); if (header) setTypeface(typeface, Typeface.BOLD); setBackgroundColor(if (header) if (Theme.isDark()) 0xff3a3a42.toInt() else 0xfff1f1f3.toInt() else palette().surface); setPadding(dp(8), dp(6), dp(8), dp(6)); layoutParams = LayoutParams(0, dp(30), listOf(1.2f, 1f, 1f)[index]).apply { setMargins(0, 0, dp(1), dp(1)) } }) }
        table.addView(row)
    }
    private fun addPlaceholder(state: DocumentSharePreviewRepository.State) {
        preview.setPadding(dp(24), dp(24), dp(24), dp(24))
        val lp = preview.layoutParams as? LayoutParams ?: LayoutParams(-1, dp(264))
        lp.height = if (resources.configuration.fontScale > 1f) -2 else dp(264)
        lp.width = -1; lp.setMargins(0, dp(12), 0, 0); preview.layoutParams = lp
        if (resources.configuration.fontScale > 1f) preview.minimumHeight = dp(264) else preview.minimumHeight = dp(264)
        val title = when (state) {
            DocumentSharePreviewRepository.State.denied -> context.getString(R.string.doc_share_denied_title)
            DocumentSharePreviewRepository.State.unavailable -> context.getString(R.string.doc_share_unavailable_title)
            DocumentSharePreviewRepository.State.error -> context.getString(R.string.doc_share_error_title)
            DocumentSharePreviewRepository.State.loading -> context.getString(R.string.doc_share_loading_title)
            else -> context.getString(R.string.doc_share_empty_title)
        }
        val text = when (state) {
            DocumentSharePreviewRepository.State.denied -> context.getString(R.string.doc_share_access_description)
            DocumentSharePreviewRepository.State.error -> context.getString(R.string.doc_share_error_description)
            DocumentSharePreviewRepository.State.unavailable -> context.getString(R.string.doc_share_unavailable_description)
            DocumentSharePreviewRepository.State.loading -> context.getString(R.string.doc_share_checking_description)
            else -> context.getString(R.string.doc_share_no_preview)
        }
        preview.addView(LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER; addView(ImageView(context).apply { setImageDrawable(KindDrawable(if (state == DocumentSharePreviewRepository.State.denied) "lock" else if (state == DocumentSharePreviewRepository.State.error || state == DocumentSharePreviewRepository.State.unavailable) "warning" else "info", statusColor(state))); background = GradientDrawable().apply { setColor(statusBackground(state)); shape = GradientDrawable.OVAL }; setPadding(dp(8), dp(8), dp(8), dp(8)); layoutParams = LayoutParams(dp(40), dp(40)) }); addView(TextView(context).apply { this.text = title; textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(palette().primary); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(4)) }); if (state != DocumentSharePreviewRepository.State.empty && state != DocumentSharePreviewRepository.State.loading) addView(TextView(context).apply { this.text = text; textSize = 12f; setTextColor(palette().secondary); gravity = Gravity.CENTER; maxLines = 4 }) })
    }
    private fun addPreviewTitle(value: String) { preview.addView(TextView(context).apply { text = value; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 12f; setTextColor(palette().secondary); setPadding(0, dp(3), 0, dp(8)) }) }
    private fun addHeading(value: String) { preview.addView(TextView(context).apply { text = value; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(palette().primary); setPadding(0, dp(4), 0, dp(6)) }) }
    private fun addLine(value: String) { preview.addView(TextView(context).apply { text = value; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 12f; setTextColor(palette().secondary); setPadding(0, dp(3), 0, dp(5)) }) }
    private fun addDocumentOrnaments() {
        val color = if (Theme.isDark()) 0x1affffff else 0x10000000
        preview.addView(object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val full = width.toFloat()
                listOf(1f, .84f, .64f).forEachIndexed { index, fraction ->
                    val top = dp(16 + index * 12).toFloat()
                    canvas.drawRoundRect(0f, top, full * fraction, top + dp(4), dp(2).toFloat(), dp(2).toFloat(), paint)
                }
            }
        }, LayoutParams(-1, dp(44)))
    }
    private fun renderBoard(board: DocumentSharePreviewParser.Preview.Board) {
        preview.setPadding(0, 0, 0, 0)
        preview.background = GradientDrawable().apply { setColor(brandTintColor()); setStroke(dp(1), previewBorderColor()); cornerRadius = dp(5).toFloat() }
        val frame = object : FrameLayout(context) {
            private fun layoutNodes(w: Int) {
                val actual = w.coerceAtLeast(0)
                val gap = minOf(dp(64), actual / 3)
                val nodeWidth = minOf(dp(112), ((actual - gap) / 2).coerceAtLeast(0))
                val left = ((w - (2 * nodeWidth + gap)) / 2).coerceAtLeast(0)
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    val lp = child.layoutParams as? FrameLayout.LayoutParams ?: continue
                    var changed = false
                    if (child.tag == "wire") {
                        val wireWidth = 2 * nodeWidth + gap
                        if (lp.width != wireWidth || lp.leftMargin != left) { lp.width = wireWidth; lp.leftMargin = left; changed = true }
                        (child.background as? BoardLines)?.setGeometry(nodeWidth)
                    } else if (child.tag is Int) {
                        val i = child.tag as Int
                        val nodeLeft = left + (i % 2) * (nodeWidth + gap)
                        val nodeTop = dp(64) + (i / 2) * dp(88)
                        if (lp.width != nodeWidth || lp.leftMargin != nodeLeft || lp.topMargin != nodeTop) {
                            lp.width = nodeWidth; lp.leftMargin = nodeLeft; lp.topMargin = nodeTop; changed = true
                        }
                    }
                    if (changed) child.layoutParams = lp
                }
            }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { layoutNodes(MeasureSpec.getSize(widthMeasureSpec)); super.onMeasure(widthMeasureSpec, heightMeasureSpec) }
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); layoutNodes(w) }
        }
        val nodes = board.texts.take(4)
        val measured = preview.width.coerceAtLeast(0)
        val gap = minOf(dp(64), measured / 3)
        val w = minOf(dp(112), ((measured - gap) / 2).coerceAtLeast(0))
        val total = 2 * w + gap
        val left = ((measured - total) / 2).coerceAtLeast(0)
        val top = dp(64)
        val line = View(context).apply { tag = "wire"; background = BoardLines(palette().line, dp(1), dp(24), dp(112), dp(68)); layoutParams = FrameLayout.LayoutParams(total, dp(136)).apply { leftMargin = left; topMargin = top } }
        frame.addView(line)
        nodes.forEachIndexed { i, value ->
            val col = i % 2; val row = i / 2
            val node = TextView(context).apply { text = value; textSize = 12f; maxLines = 2; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(palette().primary); setPadding(dp(6), dp(4), dp(6), dp(4)); background = GradientDrawable().apply { setColor(palette().surface); setStroke(dp(1), palette().line); cornerRadius = dp(4).toFloat() } }
            node.tag = i
            frame.addView(node, FrameLayout.LayoutParams(w.coerceAtLeast(0), dp(48)).apply { leftMargin = left + col * (w + gap); topMargin = top + row * dp(88) })
        }
        preview.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        preview.addView(frame, LayoutParams(-1, dp(264)))
    }
    private class BoardLines(private val color: Int, private val stroke: Int, private val firstCenter: Int, private val secondCenter: Int, private val horizontalCenter: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = stroke.toFloat() }
        private var nodeWidth = 0
        fun setGeometry(width: Int) { nodeWidth = width; invalidateSelf() }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val center = b.centerX().toFloat()
            val x1 = nodeWidth / 2f
            val x2 = b.width() - nodeWidth / 2f
            canvas.drawLine(x1, horizontalCenter.toFloat(), x2, horizontalCenter.toFloat(), paint)
            canvas.drawLine(center, firstCenter.toFloat(), center, secondCenter.toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }; override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }; override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
            val iconWidth = dp(32)
            val copyWidth = dp(28)
            val badgeDesired = (kotlin.math.ceil(badge.paint.measureText(badge.text?.toString().orEmpty()).toDouble()).toInt() + badge.paddingLeft + badge.paddingRight).coerceAtLeast(dp(54))
            val fixedWithoutBadge = iconWidth + copyWidth
            val badgeWidth = badgeDesired.coerceAtMost((available - fixedWithoutBadge - dp(14)).coerceAtLeast(0))
            val titleWidth = (available - iconWidth - copyWidth - badgeWidth).coerceAtLeast(0)
            title.minimumHeight = dp(28)
            text.layoutParams = (text.layoutParams as LayoutParams).apply { width = titleWidth; weight = 0f }
            badge.maxWidth = badgeWidth
            badge.minWidth = minOf(dp(54), badgeWidth)
            text.gravity = Gravity.TOP
            copy.layoutParams = (copy.layoutParams as LayoutParams).apply { gravity = Gravity.TOP }
            icon.layoutParams = (icon.layoutParams as LayoutParams).apply { gravity = Gravity.TOP }
            headerRow.minimumHeight = 0
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val lineHeight = maxOf(title.measuredHeight, badge.measuredHeight, copy.measuredHeight)
        title.minimumHeight = lineHeight
        headerRow.minimumHeight = lineHeight
        icon.layoutParams = (icon.layoutParams as LayoutParams).apply { gravity = Gravity.CENTER_VERTICAL }
        badge.layoutParams = (badge.layoutParams as LayoutParams).apply { gravity = Gravity.TOP; topMargin = ((lineHeight - badge.measuredHeight) / 2).coerceAtLeast(0) }
        copy.layoutParams = (copy.layoutParams as LayoutParams).apply { gravity = Gravity.TOP; topMargin = ((lineHeight - copy.measuredHeight) / 2).coerceAtLeast(0) }
        if (headerRow.minimumHeight > 0) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun installInteractions() {
        if (listener == null) return
        copy.setOnClickListener { listener?.onCopy() }
        copy.setOnLongClickListener { this@DocumentShareCardView.performLongClick() }
        preview.setOnLongClickListener { this@DocumentShareCardView.performLongClick() }
        copy.isClickable = true; copy.isLongClickable = true
        preview.isLongClickable = true
        isClickable = true; isLongClickable = true
        listener?.onInstallLongClick()
    }
    private fun setIcon(kind: String) {
        icon.background = GradientDrawable().apply { setColor(palette().surface); cornerRadius = dp(7).toFloat() }
        icon.setPadding(dp(4), dp(4), dp(4), dp(4))
        icon.setImageDrawable(KindDrawable(kind, palette().accent))
        icon.contentDescription = when (kind) { "sheet" -> context.getString(R.string.doc_share_kind_sheet); "board" -> context.getString(R.string.doc_share_kind_board); "html" -> context.getString(R.string.doc_share_kind_html); else -> context.getString(R.string.doc_share_kind_doc) }
    }
    private data class Palette(val card: Int, val surface: Int, val primary: Int, val secondary: Int, val line: Int, val accent: Int)
    private fun statusColor(value: DocumentSharePreviewRepository.State): Int = when (value) {
        DocumentSharePreviewRepository.State.denied, DocumentSharePreviewRepository.State.loading, DocumentSharePreviewRepository.State.empty, DocumentSharePreviewRepository.State.ready -> if (Theme.isDark()) Color.argb(204,255,255,255) else Color.argb(153,0,0,0)
        DocumentSharePreviewRepository.State.error, DocumentSharePreviewRepository.State.unavailable -> if (Theme.isDark()) 0xffff5c72.toInt() else 0xfff54a45.toInt()
    }
    private fun previewBorderColor(): Int = if (Theme.isDark()) Color.argb(18,255,255,255) else Color.argb(20,0,0,0)
    private fun brandTintColor(): Int = if (Theme.isDark()) Color.argb(10, 255, 255, 255) else Color.argb(8, 28, 28, 35)
    private fun statusBackground(value: DocumentSharePreviewRepository.State): Int = when (value) {
        DocumentSharePreviewRepository.State.error, DocumentSharePreviewRepository.State.unavailable -> android.graphics.Color.argb(if (Theme.isDark()) 26 else 20, Color.red(statusColor(value)), Color.green(statusColor(value)), Color.blue(statusColor(value)))
        else -> palette().surface
    }
    private fun palette(): Palette {
        val dark = Theme.isDark()
        val borderCard = android.graphics.Color.argb(if (dark) 10 else 13, if (dark) 255 else 0, if (dark) 255 else 0, if (dark) 255 else 0)
        return if (dark) Palette(0xff25252c.toInt(),0xff303038.toInt(),Color.WHITE,0xffb5b5bd.toInt(),borderCard,Theme.colorAccount)
        else Palette(0xffeeeff1.toInt(),Color.WHITE,0xff292d32.toInt(),0xff8a8d92.toInt(),borderCard,Theme.colorAccount)
    }
    private fun applyTheme() {
        val p = palette(); val dark = Theme.isDark()
        background = GradientDrawable().apply { setColor(p.card); setStroke(dp(1), p.line); cornerRadius = dp(8).toFloat() }
        val tintedPreview = state != DocumentSharePreviewRepository.State.ready || renderedPreviewType == "board"
        preview.background = GradientDrawable().apply { setColor(if (tintedPreview) brandTintColor() else p.surface); setStroke(dp(1), previewBorderColor()); cornerRadius = dp(5).toFloat() }
        title.setTextColor(p.primary); subtitle.setTextColor(p.secondary)
        val badgeColor = when (state) {
            DocumentSharePreviewRepository.State.denied -> if (dark) 0xffffad33.toInt() else 0xfffc8800.toInt()
            DocumentSharePreviewRepository.State.error, DocumentSharePreviewRepository.State.unavailable -> statusColor(state)
            else -> p.secondary
        }
        badge.setTextColor(badgeColor)
        val badgeBg = when (state) {
            DocumentSharePreviewRepository.State.denied -> android.graphics.Color.argb(if (dark) 26 else 20, Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor))
            DocumentSharePreviewRepository.State.error, DocumentSharePreviewRepository.State.unavailable -> statusBackground(state)
            else -> p.surface
        }
        badge.background = GradientDrawable().apply { setColor(badgeBg); cornerRadius = dp(10).toFloat() }
        copy.setImageDrawable(KindDrawable("copy", p.secondary)); setIcon(currentKind)
    }
    private class KindDrawable(private val kind: String, color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val save = canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            canvas.scale(b.width() / 24f, b.height() / 24f)
            val p = Path()
            when (kind) {
                "doc" -> {
                    p.moveTo(6f,3f); p.lineTo(14f,3f); p.lineTo(18f,7f); p.lineTo(18f,21f); p.lineTo(6f,21f); p.close()
                    p.moveTo(9f,8f); p.lineTo(12f,8f); p.moveTo(9f,12f); p.lineTo(15f,12f); p.moveTo(9f,16f); p.lineTo(15f,16f); canvas.drawPath(p,paint)
                }
                "board" -> {
                    canvas.drawRoundRect(3f,4f,21f,18f,2f,2f,paint)
                    canvas.drawLine(7f,9f,11f,9f,paint); canvas.drawLine(7f,13f,14f,13f,paint)
                }
                "sheet" -> {
                    canvas.drawRoundRect(4f,4f,20f,20f,2f,2f,paint)
                    canvas.drawLine(4f,10f,20f,10f,paint); canvas.drawLine(4f,15f,20f,15f,paint); canvas.drawLine(10f,4f,10f,20f,paint)
                }
                "html" -> {
                    canvas.drawRoundRect(3f,4f,21f,20f,2f,2f,paint)
                    p.moveTo(9f,10f);p.lineTo(6.5f,12f);p.lineTo(9f,14f);p.moveTo(15f,10f);p.lineTo(17.5f,12f);p.lineTo(15f,14f);p.moveTo(13f,9f);p.lineTo(11f,15f);canvas.drawPath(p,paint)
                }
                "copy" -> {
                    canvas.drawRoundRect(9f,9f,20f,20f,2f,2f,paint)
                    p.moveTo(5f,15f);p.lineTo(5f,5f);p.quadTo(5f,3f,7f,3f);p.lineTo(15f,3f);canvas.drawPath(p,paint)
                }
                "lock" -> {
                    canvas.drawRoundRect(5f,11f,19f,20f,2f,2f,paint)
                    p.moveTo(8f,11f);p.lineTo(8f,8f);p.cubicTo(8f,1f,16f,1f,16f,8f);p.lineTo(16f,11f);canvas.drawPath(p,paint)
                }
                "warning" -> {
                    p.moveTo(12f,3f);p.lineTo(2f,20f);p.lineTo(22f,20f);p.close();p.moveTo(12f,10f);p.lineTo(12f,14f);p.moveTo(12f,17f);p.lineTo(12.01f,17f);canvas.drawPath(p,paint)
                }
                else -> {
                    canvas.drawCircle(12f,12f,9f,paint);p.moveTo(12f,11f);p.lineTo(12f,16f);p.moveTo(12f,8f);p.lineTo(12.01f,8f);canvas.drawPath(p,paint)
                }
            }
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }; override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }; override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
