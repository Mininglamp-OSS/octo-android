/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.context

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.chat.uikit.R

/**
 * 1:1 对齐 iOS [OctoContextSparkleIconView]:
 *   42dp 紫色 (#7F3BF5) 圆角 (12dp), 内含 24dp 居中 spark.png, 白 tint。
 *
 * 设计稿原本要求紫到白渐变 mask, iOS 实现也注释说 42pt 缩略尺寸下肉眼不可分辨,
 * 直接白 tint。Android 也走同方案保持一致。
 */
class SparkleIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        setBackgroundResource(R.drawable.bg_summary_sparkle_icon)

        val icon: ImageView = AppCompatImageView(context).apply {
            setImageResource(R.drawable.ic_summary_spark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val tint = ContextCompat.getColor(context, android.R.color.white)
            DrawableCompat.setTint(DrawableCompat.wrap(drawable).mutate(), tint)
        }
        val size = dp(24)
        addView(
            icon,
            LayoutParams(size, size, Gravity.CENTER),
        )
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
