/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.list

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.summary.model.SummaryFilter
import com.chat.uikit.R

/**
 * smart-summary 列表顶部 6 个筛选 tab. 1:1 对齐 iOS [OctoSummaryFilterTabsView]:
 *   - 横向滚动, 起始 16dp, 间距 14dp
 *   - active: 18sp semibold + label color + 紫色 16x3dp 圆角 indicator (底部 -7dp)
 *   - inactive: 14sp regular + label 60% alpha + indicator 隐藏
 */
class SummaryFilterTabsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    fun interface OnSelected {
        fun onSelected(filter: SummaryFilter)
    }

    private val container: LinearLayout
    private val tabContainers = mutableListOf<View>()
    private val labels = mutableListOf<TextView>()
    private val indicators = mutableListOf<View>()

    private var listener: OnSelected? = null

    var selected: SummaryFilter = SummaryFilter.All
        set(value) {
            if (field == value) return
            field = value
            updateActiveStyles()
        }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT,
            )
        }
        addView(container)
        TAB_ORDER.forEachIndexed { idx, filter ->
            buildTab(filter, idx)
        }
        updateActiveStyles()
    }

    fun onSelected(listener: OnSelected) {
        this.listener = listener
    }

    private fun buildTab(filter: SummaryFilter, idx: Int) {
        val tab = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            isClickable = true
            isFocusable = true
            setOnClickListener { setFilterAndNotify(filter) }
            val lp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            if (idx > 0) lp.marginStart = dp(14)
            layoutParams = lp
            setPadding(0, 0, 0, dp(7))
        }

        val label = TextView(context).apply {
            text = labelOf(filter)
            includeFontPadding = false
        }
        val labelLp = LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        )
        tab.addView(label, labelLp)

        val indicator = View(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_summary_filter_indicator)
            visibility = INVISIBLE
        }
        val indLp = LinearLayout.LayoutParams(dp(16), dp(3))
        indLp.topMargin = dp(4)
        tab.addView(indicator, indLp)

        container.addView(tab)
        tabContainers += tab
        labels += label
        indicators += indicator
    }

    private fun setFilterAndNotify(filter: SummaryFilter) {
        if (selected == filter) return
        selected = filter
        listener?.onSelected(filter)
    }

    private fun updateActiveStyles() {
        val activeColor = ContextCompat.getColor(context, R.color.summary_text_primary)
        val inactiveColor = ContextCompat.getColor(context, R.color.summary_text_secondary)
        TAB_ORDER.forEachIndexed { idx, filter ->
            val active = filter == selected
            val label = labels.getOrNull(idx) ?: return@forEachIndexed
            label.textSize = if (active) 18f else 14f
            label.setTypeface(label.typeface, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            label.setTextColor(if (active) activeColor else inactiveColor)
            indicators.getOrNull(idx)?.visibility = if (active) VISIBLE else INVISIBLE
        }
    }

    private fun labelOf(filter: SummaryFilter): String = context.getString(
        when (filter) {
            SummaryFilter.All -> R.string.summary_filter_all
            SummaryFilter.Pending -> R.string.summary_filter_pending
            SummaryFilter.WaitingConfirm -> R.string.summary_filter_waiting_confirm
            SummaryFilter.Processing -> R.string.summary_filter_processing
            SummaryFilter.Completed -> R.string.summary_filter_completed
            SummaryFilter.Failed -> R.string.summary_filter_failed
        }
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        // 严格对齐 iOS OctoSummaryFilterIndex 顺序, 不要重排
        val TAB_ORDER = listOf(
            SummaryFilter.All,
            SummaryFilter.Pending,
            SummaryFilter.WaitingConfirm,
            SummaryFilter.Processing,
            SummaryFilter.Completed,
            SummaryFilter.Failed,
        )
    }
}
