/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.style.ReplacementSpan

/**
 * 紫色圆角徽章 ImageSpan, 1:1 对齐 iOS [OctoCitationBadgeView]:
 *   背景 #287F3BF5 (紫 16%), 字色 #7F3BF5, 字号 11sp medium, 横向 padding 6, 圆角=半高。
 *
 * 持有原始 indices (单个或合并组), 由 [CitationPostProcessor] 在同位置叠 ClickableSpan
 * 触发点击, 此 span 只负责渲染。
 */
class CitationSpan(
    private val text: String,
    val indices: List<Int>,
    private val backgroundColor: Int,
    private val textColor: Int,
) : ReplacementSpan() {

    private val badgePadding = 6f      // 横向 padding (px-base 由 layout 阶段乘 density)
    private val verticalShift = -2f    // 与基线对齐,轻微下偏让徽章贴合行内

    override fun getSize(
        paint: Paint, charSequence: CharSequence?, start: Int, end: Int,
        fontMetrics: Paint.FontMetricsInt?,
    ): Int {
        val labelPaint = paintForLabel(paint)
        val textWidth = labelPaint.measureText(text)
        if (fontMetrics != null) {
            val fm = labelPaint.fontMetricsInt
            fontMetrics.ascent = fm.ascent
            fontMetrics.descent = fm.descent
            fontMetrics.top = fm.top
            fontMetrics.bottom = fm.bottom
        }
        return (textWidth + badgePadding * 2 + 0.5f).toInt()
    }

    override fun draw(
        canvas: Canvas, charSequence: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val labelPaint = paintForLabel(paint)
        val textWidth = labelPaint.measureText(text)
        val fm = labelPaint.fontMetricsInt
        val height = (fm.descent - fm.ascent).toFloat()
        val radius = height / 2f

        val rectTop = y + fm.ascent.toFloat() + verticalShift
        val rectBottom = rectTop + height
        val rectRight = x + textWidth + badgePadding * 2

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        val rect = RectF(x, rectTop, rectRight, rectBottom)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        labelPaint.color = textColor
        canvas.drawText(text, x + badgePadding, y.toFloat() + verticalShift, labelPaint)
    }

    private fun paintForLabel(base: Paint): Paint = Paint(base).apply {
        textSize = base.textSize * 0.88f
        typeface = Typeface.create(typeface, Typeface.NORMAL)
        isFakeBoldText = false
    }
}
