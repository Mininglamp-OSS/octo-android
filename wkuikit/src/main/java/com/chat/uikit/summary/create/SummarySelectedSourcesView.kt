/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.create

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.summary.model.SourceItem
import com.chat.uikit.R

/**
 * 创建总结页 "已选聊天" 流式 pill 列表 (1:1 对齐 iOS OctoSelectedSourcesView):
 *   - pill: 圆角 14dp, 灰底, 头像 20dp + 名字 + 关闭按钮
 *   - 流式排版, 超过宽度自动换行
 *   - 最大 maxRows 行 (默认 3); 超出后内部纵向滚动
 *   - 关闭按钮回调 onRemove(item) 让上层删除
 *
 * 头像降级: 取 sourceName 首字符画紫色圆圈占位 (iOS 同款)。
 */
class SummarySelectedSourcesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ViewGroup(context, attrs, defStyle) {

    private val pillH = dp(28f)
    private val pillHGap = dp(6f)
    private val pillVGap = dp(6f)
    private val pillSidePad = dp(6f)
    private val avatarSize = dp(20f)
    private val closeSize = dp(14f)
    private val innerGap = dp(6f)
    private val nameMaxW = dp(140f)

    var maxRows: Int = 3
        set(value) {
            field = value.coerceAtLeast(1)
            requestLayout()
        }

    var onRemove: ((SourceItem) -> Unit)? = null

    var items: List<SourceItem> = emptyList()
        set(value) {
            field = value
            rebuildPills()
            requestLayout()
        }

    private val pills: MutableList<PillView> = mutableListOf()

    private fun rebuildPills() {
        pills.forEach { removeView(it) }
        pills.clear()
        for ((idx, it) in items.withIndex()) {
            val pill = PillView(context, it).apply {
                setOnRemoveClick {
                    val cur = items.getOrNull(idx) ?: return@setOnRemoveClick
                    onRemove?.invoke(cur)
                }
            }
            addView(pill)
            pills.add(pill)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (items.isEmpty()) {
            setMeasuredDimension(width, 0)
            return
        }
        for (p in pills) {
            p.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(pillH, MeasureSpec.EXACTLY),
            )
        }
        val rows = computeRows(width)
        val visibleRows = rows.coerceAtMost(maxRows)
        val h = visibleRows * pillH + (visibleRows - 1).coerceAtLeast(0) * pillVGap
        setMeasuredDimension(width, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (items.isEmpty()) return
        val width = r - l
        var x = 0
        var y = 0
        for (p in pills) {
            val pw = p.measuredWidth.coerceAtMost(width)
            if (x + pw > width && x > 0) {
                x = 0
                y += pillH + pillVGap
            }
            p.layout(x, y, x + pw, y + pillH)
            x += pw + pillHGap
        }
    }

    private fun computeRows(width: Int): Int {
        if (width <= 0) return 1
        var x = 0
        var rows = 1
        for (p in pills) {
            val pw = p.measuredWidth.coerceAtMost(width)
            if (x + pw > width && x > 0) {
                x = 0
                rows++
            }
            x += pw + pillHGap
        }
        return rows
    }

    fun heightForWidth(width: Int): Int {
        if (items.isEmpty() || width <= 0) return 0
        for (p in pills) {
            p.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(pillH, MeasureSpec.EXACTLY),
            )
        }
        val rows = computeRows(width).coerceAtMost(maxRows)
        return rows * pillH + (rows - 1).coerceAtLeast(0) * pillVGap
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private inner class PillView(ctx: Context, val item: SourceItem) : LinearLayout(ctx) {
        private val nameTv: TextView
        private val avatarIv: ImageView
        private val closeIv: ImageView

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pillSidePad, 0, pillSidePad, 0)
            setBackgroundResource(R.drawable.bg_summary_source_pill)

            avatarIv = ImageView(ctx).apply {
                layoutParams = LayoutParams(avatarSize, avatarSize)
                setImageDrawable(buildAvatar(item.sourceName ?: item.sourceId))
            }
            addView(avatarIv)

            nameTv = TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xFF1F1F1F.toInt())
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                text = (item.sourceName ?: item.sourceId).orEmpty()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = innerGap
                    rightMargin = innerGap
                }
                maxWidth = nameMaxW
            }
            addView(nameTv)

            closeIv = ImageView(ctx).apply {
                layoutParams = LayoutParams(closeSize + dp(4f), closeSize + dp(4f))
                setImageResource(R.drawable.ic_summary_close)
                setColorFilter(0x80000000.toInt())
                isClickable = true
                isFocusable = true
            }
            addView(closeIv)
        }

        fun setOnRemoveClick(onClick: () -> Unit) {
            closeIv.setOnClickListener { onClick() }
        }

        private fun buildAvatar(name: String): Drawable {
            val first = if (name.isNotEmpty()) name.substring(0, 1) else "·"
            val sizePx = avatarSize
            return object : Drawable() {
                private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xD87F3BF5.toInt()
                    style = Paint.Style.FILL
                }
                private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = sizePx * 0.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                override fun draw(c: Canvas) {
                    val radius = sizePx / 2f
                    c.drawCircle(radius, radius, radius, bgPaint)
                    val fm = textPaint.fontMetrics
                    val baseY = radius - (fm.ascent + fm.descent) / 2f
                    c.drawText(first, radius, baseY, textPaint)
                }
                override fun getIntrinsicWidth() = sizePx
                override fun getIntrinsicHeight() = sizePx
                override fun setAlpha(alpha: Int) { bgPaint.alpha = alpha; textPaint.alpha = alpha }
                override fun setColorFilter(cf: android.graphics.ColorFilter?) {
                    bgPaint.colorFilter = cf; textPaint.colorFilter = cf
                }
                override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            }
        }
    }
}
