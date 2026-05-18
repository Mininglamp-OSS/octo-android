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

package com.chat.uikit.chat.provider

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import androidx.annotation.ColorInt

/**
 * 继承ImageSpan，绘制图片背景
 * https://developer.android.google.cn/reference/android/text/style/DynamicDrawableSpan
 *
 * Create by gnmmdk
 */
class SelectImageSpan(drawable: Drawable, @ColorInt var bgColor: Int, verticalAlignment: Int) :
    ImageSpan(drawable, verticalAlignment) {

    /**
     * 重写 draw 方法
     * 绘制背景
     */
    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // From super.draw(canvas, text, start, end, x, top, y, bottom, paint)
        val d = drawable
        canvas.save()

        // From gnmmdk 修改canvas paint颜色实现
        paint.color = bgColor
        canvas.drawRect(x, top.toFloat(), x + d.bounds.right, bottom.toFloat(), paint)

        // From super.draw(canvas, text, start, end, x, top, y, bottom, paint)
        var transY = bottom - d.bounds.bottom
        if (mVerticalAlignment == ALIGN_BASELINE) {
            transY -= paint.fontMetricsInt.descent
        } else if (mVerticalAlignment == ALIGN_CENTER) {
            transY = top + (bottom - top) / 2 - d.bounds.height() / 2
        }
        canvas.translate(x, transY.toFloat())
        d.draw(canvas)
        canvas.restore()
    }

}