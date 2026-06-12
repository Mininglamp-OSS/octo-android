/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.detail

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.SourceType
import com.chat.uikit.R

/**
 * 来源 chip 流式 ViewGroup, 1:1 对齐 iOS OctoDetailSourcesView:
 *   - chip 高 24dp, 横向间隙 6dp, 行间距 6dp, 内边距 10dp
 *   - 折叠态: 只显示第 0 行, 末尾留 "等" + 向下 chevron
 *   - 展开态: 多行铺开, chevron 翻转 180°
 *   - 多源溢出时才出现 chevron, 单行刚好放下不出
 *   - 群聊 chip 紫底紫字 / 子区蓝绿底蓝绿字 / 私聊灰底
 */
class SummaryDetailSourcesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ViewGroup(context, attrs, defStyle) {

    private val pillH = dp(24f).toInt()
    private val pillHGap = dp(6f).toInt()
    private val pillVGap = dp(6f).toInt()
    private val pillSidePad = dp(10f).toInt()
    private val toggleW = dp(28f).toInt()
    private val toggleGap = dp(4f).toInt()
    private val etcW = dp(24f).toInt()

    private val pills: MutableList<TextView> = mutableListOf()
    private val toggleBtn: ImageView
    private val etcLabel: TextView

    /** 用户切到展开/折叠时回调, 上层重新 measure 高度刷布局 */
    var onToggle: ((expanded: Boolean) -> Unit)? = null

    var expanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var items: List<SourceItem> = emptyList()
        set(value) {
            field = value
            rebuildPills()
            requestLayout()
        }

    init {
        toggleBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_summary_chevron_down)
            setColorFilter(0x80000000.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = GONE
            setOnClickListener {
                expanded = !expanded
                rotation = if (expanded) 180f else 0f
                onToggle?.invoke(expanded)
            }
        }
        addView(toggleBtn, LayoutParams(toggleW, pillH))

        etcLabel = TextView(context).apply {
            text = context.getString(R.string.summary_detail_more_etc)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x80000000.toInt())
            gravity = Gravity.CENTER
            visibility = GONE
        }
        addView(etcLabel, LayoutParams(etcW, pillH))
    }

    private fun rebuildPills() {
        pills.forEach { removeView(it) }
        pills.clear()
        for (it in items) {
            val pill = TextView(context).apply {
                text = displayNameFor(it)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(pillSidePad, 0, pillSidePad, 0)
                setBackgroundResource(chipBgFor(it.sourceType))
                setTextColor(chipFgFor(it.sourceType))
            }
            addView(pill, LayoutParams(LayoutParams.WRAP_CONTENT, pillH))
            pills.add(pill)
        }
    }

    private fun displayNameFor(s: SourceItem): String {
        val base = s.sourceName?.ifEmpty { null } ?: s.sourceId
        return when (s.sourceType) {
            SourceType.Thread -> context.getString(R.string.summary_chip_thread_prefix, base)
            else -> base
        }
    }

    private fun chipBgFor(t: SourceType): Int = when (t) {
        SourceType.GroupChat -> R.drawable.bg_summary_chip_purple
        SourceType.Thread -> R.drawable.bg_summary_chip_teal
        else -> R.drawable.bg_summary_chip_mute
    }

    private fun chipFgFor(t: SourceType): Int = when (t) {
        SourceType.GroupChat -> ContextCompat.getColor(context, R.color.summary_purple)
        SourceType.Thread -> 0xFF0E9B8C.toInt()
        else -> 0xFF1F1F1F.toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        if (items.isEmpty()) {
            setMeasuredDimension(w, 0)
            return
        }
        // 先量每个 pill 自身宽度
        for (p in pills) {
            p.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(pillH, MeasureSpec.EXACTLY),
            )
        }
        val (rows, _) = flowLayout(w, apply = false)
        val h = pillH * rows + pillVGap * (rows - 1).coerceAtLeast(0)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (items.isEmpty()) return
        flowLayout(r - l, apply = true)
    }

    /**
     * 单遍流式排版: apply=YES 落帧, NO 仅算行数。
     * 折叠态第 0 行右侧需为 toggle + etc 留位置。
     */
    private fun flowLayout(width: Int, apply: Boolean): Pair<Int, Boolean> {
        val reservedRight = toggleW + toggleGap + etcW + pillHGap
        val row0Avail = (width - reservedRight).coerceAtLeast(0)

        var x = 0
        var y = 0
        var row = 0
        var multiRow = false
        var lastRow0Right = 0
        for (p in pills) {
            val pw = p.measuredWidth.coerceAtMost((width).coerceAtLeast(60))
            val avail = if (row == 0) row0Avail else width
            if (x + pw > avail && x > 0) {
                x = 0
                y += pillH + pillVGap
                row++
                multiRow = true
            }
            if (apply) {
                val visible = expanded || row == 0
                p.layout(x, y, x + pw, y + pillH)
                p.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            }
            if (row == 0) lastRow0Right = x + pw
            x += pw + pillHGap
        }
        if (apply) {
            toggleBtn.visibility = if (multiRow) View.VISIBLE else View.GONE
            if (multiRow) {
                toggleBtn.layout(width - toggleW, 0, width, pillH)
                toggleBtn.rotation = if (expanded) 180f else 0f
            }
            val showEtc = multiRow && !expanded
            etcLabel.visibility = if (showEtc) View.VISIBLE else View.GONE
            if (showEtc) {
                val ex = lastRow0Right + pillHGap
                etcLabel.layout(ex, 0, ex + etcW, pillH)
            }
        }
        val rows = if (expanded) row + 1 else 1
        return rows to multiRow
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
