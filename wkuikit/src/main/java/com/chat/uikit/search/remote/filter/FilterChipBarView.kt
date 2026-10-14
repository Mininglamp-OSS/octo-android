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

package com.chat.uikit.search.remote.filter

import android.content.Context
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R

/**
 * 已应用筛选条件 chip 条，对齐 iOS `WKChannelHistoryFilterChipBar`：每个筛选维度一个胶囊，
 * 横向可滑动排列；点文字区域重新打开筛选弹窗，点"✕"单独清除该维度。无 chip 时整条隐藏。
 */
class FilterChipBarView(context: Context) : HorizontalScrollView(context) {

    data class ChipItem(
        val text: String,
        val onTap: () -> Unit,
        val onClear: () -> Unit,
    )

    private val row: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setChips(items: List<ChipItem>) {
        row.removeAllViews()
        if (items.isEmpty()) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        val chipHeight = AndroidUtilities.dp(32f)
        val hPadding = AndroidUtilities.dp(12f)
        val gap = AndroidUtilities.dp(8f)
        items.forEachIndexed { index, item ->
            val chip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.filter_chip_bg)
                setPadding(hPadding, 0, AndroidUtilities.dp(6f), 0)
                setOnClickListener { item.onTap() }
            }

            val label = TextView(context).apply {
                text = item.text
                textSize = 13f
                setTextColor(resources.getColor(com.chat.base.R.color.colorAccent, null))
            }
            chip.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            val clear = ImageView(context).apply {
                setImageResource(R.drawable.ic_summary_close)
                setColorFilter(resources.getColor(com.chat.base.R.color.colorAccent, null))
                setOnClickListener { item.onClear() }
            }
            val clearSize = AndroidUtilities.dp(14f)
            val clearParams = LinearLayout.LayoutParams(clearSize, clearSize).apply {
                marginStart = AndroidUtilities.dp(6f)
            }
            chip.addView(clear, clearParams)

            val chipParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight)
            if (index > 0) chipParams.marginStart = gap
            row.addView(chip, chipParams)
        }
    }
}
