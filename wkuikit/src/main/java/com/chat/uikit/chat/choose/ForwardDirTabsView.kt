/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.choose

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R

/**
 * 3 tab 紫色下划线 indicator, 1:1 对齐 iOS WKForwardDirectoryVC tab 样式.
 * 选中 = 紫字 + 底部 16dp 紫色短线; 未选中 = 灰字.
 */
class ForwardDirTabsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    fun interface OnTabSelectedListener {
        fun onTabSelected(index: Int)
    }

    private val tabs = arrayOfNulls<TextView>(3)
    private var selectedIndex = 0
    private var listener: OnTabSelectedListener? = null

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.summary_purple)
    }
    private val indicatorWidth = AndroidUtilities.dp(20f).toFloat()
    private val indicatorHeight = AndroidUtilities.dp(3f).toFloat()
    private val indicatorRadius = indicatorHeight / 2f
    private var indicatorCenterX = 0f

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
    }

    fun setTabs(titles: Array<String>) {
        require(titles.size == 3) { "ForwardDirTabsView: 必须传 3 个 tab title" }
        removeAllViews()
        for (i in 0..2) {
            val tv = TextView(context).apply {
                text = titles[i]
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
            }
            val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            tv.layoutParams = lp
            tv.setOnClickListener { selectTab(i) }
            tabs[i] = tv
            addView(tv)
        }
        updateStyles()
    }

    fun setOnTabSelectedListener(l: OnTabSelectedListener) {
        listener = l
    }

    fun selectTab(index: Int) {
        if (index == selectedIndex || index !in 0..2) return
        selectedIndex = index
        updateStyles()
        animateIndicator()
        listener?.onTabSelected(index)
    }

    private fun updateStyles() {
        for (i in 0..2) {
            val tv = tabs[i] ?: continue
            if (i == selectedIndex) {
                tv.setTextColor(ContextCompat.getColor(context, R.color.summary_purple))
                tv.paint.isFakeBoldText = true
            } else {
                tv.setTextColor(ContextCompat.getColor(context, R.color.color999))
                tv.paint.isFakeBoldText = false
            }
        }
    }

    private fun animateIndicator() {
        val target = tabs[selectedIndex] ?: return
        val newCx = target.left + target.width / 2f
        if (indicatorCenterX == 0f) {
            indicatorCenterX = newCx
            invalidate()
            return
        }
        val animator = ValueAnimator.ofFloat(indicatorCenterX, newCx)
        animator.duration = 200
        animator.addUpdateListener { a ->
            indicatorCenterX = a.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val target = tabs[selectedIndex] ?: return
        if (indicatorCenterX == 0f) indicatorCenterX = target.left + target.width / 2f
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val left = indicatorCenterX - indicatorWidth / 2f
        val top = height - indicatorHeight - AndroidUtilities.dp(2f)
        canvas.drawRoundRect(
            left, top, left + indicatorWidth, top + indicatorHeight,
            indicatorRadius, indicatorRadius, indicatorPaint,
        )
    }
}
