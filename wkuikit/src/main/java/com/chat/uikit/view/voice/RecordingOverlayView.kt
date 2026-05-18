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

package com.chat.uikit.view.voice

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.chat.uikit.R
import kotlin.math.abs

/**
 * 录音覆盖层（1:1 对齐 iOS WKVoiceInputView recordingOverlay）。
 *
 * 视觉结构：
 *   - 径向椭圆渐变（xRadius = screenW*1.1, yRadius = screenH*0.475），中心在屏幕底部
 *   - 触摸光圈 + 12 拖尾（跟随手指）
 *   - 40 根白色竖条波形（距底 100dp）
 *   - 提示文字（screenH * 0.68）
 */
class RecordingOverlayView(context: Context) : View(context) {

    companion object {
        private const val BAR_COUNT = 40
        private const val TRAIL_COUNT = 12
        private const val ANIM_DURATION = 200L

        // ---- 对齐 iOS 的 20 色径向渐变 ----
        // 录音态（蓝）
        private val REC_COLORS = intArrayOf(
            Color.argb(255, 38, 95, 218),
            Color.argb(255, 40, 100, 220),
            Color.argb(255, 43, 105, 223),
            Color.argb(255, 46, 110, 226),
            Color.argb(255, 50, 116, 230),
            Color.argb(252, 55, 122, 234),
            Color.argb(247, 60, 128, 237),
            Color.argb(237, 66, 136, 240),
            Color.argb(219, 73, 144, 243),
            Color.argb(194, 82, 153, 245),
            Color.argb(163, 92, 162, 247),
            Color.argb(133, 103, 172, 249),
            Color.argb(102, 115, 182, 250),
            Color.argb(77, 128, 192, 252),
            Color.argb(54, 142, 200, 253),
            Color.argb(36, 156, 209, 254),
            Color.argb(20, 170, 217, 255),
            Color.argb(10, 185, 225, 255),
            Color.argb(3, 205, 236, 255),
            Color.TRANSPARENT
        )
        // 取消态（红）
        private val CANCEL_COLORS = intArrayOf(
            Color.argb(255, 205, 40, 40),
            Color.argb(255, 208, 46, 44),
            Color.argb(255, 211, 52, 48),
            Color.argb(255, 214, 58, 52),
            Color.argb(255, 218, 65, 58),
            Color.argb(252, 222, 72, 64),
            Color.argb(247, 226, 80, 72),
            Color.argb(237, 230, 90, 82),
            Color.argb(219, 234, 100, 93),
            Color.argb(194, 238, 112, 106),
            Color.argb(163, 241, 125, 118),
            Color.argb(133, 244, 138, 132),
            Color.argb(102, 246, 150, 145),
            Color.argb(77, 249, 163, 158),
            Color.argb(54, 251, 175, 170),
            Color.argb(36, 253, 186, 182),
            Color.argb(20, 254, 198, 195),
            Color.argb(10, 255, 210, 208),
            Color.argb(3, 255, 225, 223),
            Color.TRANSPARENT
        )
        // 对齐 iOS 的 20 个 location
        private val GRADIENT_POSITIONS = floatArrayOf(
            0f, 0.06f, 0.12f, 0.18f, 0.25f, 0.32f, 0.40f,
            0.47f, 0.53f, 0.59f, 0.64f, 0.69f, 0.74f, 0.79f, 0.83f, 0.87f, 0.91f, 0.94f, 0.97f, 1f
        )

        // 触摸光圈
        private val GLOW_COLOR_REC = Color.argb(102, 20, 60, 180)
        private val GLOW_COLOR_CANCEL = Color.argb(102, 180, 30, 30)
        private const val GLOW_RADIUS_DP = 80f
    }

    private val density = resources.displayMetrics.density

    // -- paints --
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * density
        isFakeBoldText = true
    }
    private val barRect = RectF()
    private val shaderMatrix = Matrix()

    // -- state --
    private var isCancel = false
    private var currentPower = 0f
    private var breathScale = 1f
    // 每根柱子的当前高度（用于平滑过渡）
    private val barCurrentH = FloatArray(BAR_COUNT) { 15f }

    // -- touch glow --
    private var touchX = 0f
    private var touchY = 0f
    private var hasTouchPosition = false
    private var isFirstTouch = true
    private val trailX = FloatArray(TRAIL_COUNT)
    private val trailY = FloatArray(TRAIL_COUNT)

    // -- strings --
    private val textRecording: String = context.getString(R.string.release_to_send_slide_to_cancel)
    private val textCancel: String = context.getString(R.string.release_to_cancel)

    // -- dimensions --
    private val glowRadius = GLOW_RADIUS_DP * density
    private val barWidth = 4f * density
    private val barGap = 3.5f * density
    private val barMinH = 15f * density
    private val barMaxH = 45f * density
    private val barCornerR = 2f * density

    init {
        isClickable = false
        isFocusable = false
    }

    // ========== public API ==========

    fun show() {
        val activity = context as? Activity ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        alpha = 0f
        decorView.addView(this, lp)
        animate().alpha(1f).setDuration(ANIM_DURATION).start()
    }

    fun dismiss() {
        animate().alpha(0f).setDuration(ANIM_DURATION).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
        }.start()
    }

    fun updateState(cancel: Boolean) {
        if (isCancel == cancel) return
        isCancel = cancel
        invalidate()
    }

    fun updateAmplitude(amp: Float) {
        currentPower = amp.coerceIn(0f, 1f)
        breathScale = 1f + currentPower * 0.15f
        invalidate()
    }

    fun updateTouchPosition(rawX: Float, rawY: Float) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        touchX = rawX - loc[0]
        touchY = rawY - loc[1]
        hasTouchPosition = true

        if (isFirstTouch) {
            for (i in 0 until TRAIL_COUNT) {
                trailX[i] = touchX; trailY[i] = touchY
            }
            isFirstTouch = false
        } else {
            for (i in 0 until TRAIL_COUNT) {
                val factor = 0.15f + 0.07f * i
                trailX[i] += (touchX - trailX[i]) * factor
                trailY[i] += (touchY - trailY[i]) * factor
            }
        }
        invalidate()
    }

    // ========== drawing ==========

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        drawRadialGradient(canvas, w, h)
        drawTouchGlow(canvas)
        drawWaveformBars(canvas, w, h)
        drawHintText(canvas, w, h)
    }

    /**
     * 径向椭圆渐变。
     * 对齐 iOS：center = 屏幕底部中心,
     *   xRadius = screenW * 1.1, yRadius = screenH * 0.475
     *   → 仅覆盖屏幕下半部分，底部完全不透明。
     */
    private fun drawRadialGradient(canvas: Canvas, w: Float, h: Float) {
        val colors = if (isCancel) CANCEL_COLORS else REC_COLORS
        val yRadius = h * 0.475f * breathScale
        val xRadius = w * 1.1f

        // 单位圆 RadialGradient + Matrix 变换为椭圆
        val gradient = RadialGradient(
            0f, 0f, 1f,
            colors, GRADIENT_POSITIONS,
            Shader.TileMode.CLAMP
        )
        shaderMatrix.reset()
        shaderMatrix.setScale(xRadius, yRadius)
        shaderMatrix.postTranslate(w / 2f, h)
        gradient.setLocalMatrix(shaderMatrix)

        bgPaint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        bgPaint.shader = null
    }

    /** 触摸光圈 + 拖尾残影 */
    private fun drawTouchGlow(canvas: Canvas) {
        if (!hasTouchPosition) return
        val baseColor = if (isCancel) GLOW_COLOR_CANCEL else GLOW_COLOR_REC

        // 拖尾（从远到近）
        for (i in 0 until TRAIL_COUNT) {
            val sizeFraction = (i + 1f) / TRAIL_COUNT
            val trailR = glowRadius * (0.25f + 0.75f * sizeFraction)
            val alphaBase = 0.02f + 0.30f * sizeFraction
            val trailColor = adjustAlpha(baseColor, alphaBase)
            trailPaint.shader = RadialGradient(
                trailX[i], trailY[i], trailR,
                intArrayOf(trailColor, Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(trailX[i], trailY[i], trailR, trailPaint)
        }
        trailPaint.shader = null

        // 主光圈
        glowPaint.shader = RadialGradient(
            touchX, touchY, glowRadius,
            intArrayOf(baseColor, Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(touchX, touchY, glowRadius, glowPaint)
        glowPaint.shader = null
    }

    /**
     * 40 根竖条波形（对齐 iOS）。
     * 每根独立随机高度 = base + power * maxExtra * attenuation * random
     * 透明度从中心向两侧衰减 = (1 - dist)² * 0.95
     */
    private fun drawWaveformBars(canvas: Canvas, w: Float, h: Float) {
        val totalW = BAR_COUNT * barWidth + (BAR_COUNT - 1) * barGap
        val startX = (w - totalW) / 2f
        val baseY = h - 100f * density   // 距底 100dp（对齐 iOS screenH-100）

        val barColor = if (isCancel)
            Color.argb(204, 239, 68, 68)    // red 0.8
        else
            Color.argb(230, 255, 255, 255)  // white 0.9

        val center = BAR_COUNT / 2f
        for (i in 0 until BAR_COUNT) {
            val distFromCenter = abs(i - center) / center  // 0..1

            // 目标高度（静音阈值 0.08，对齐 iOS silence threshold）
            val attenuation = 1f - distFromCenter * 0.6f
            val targetH = if (currentPower < 0.08f) {
                barMinH
            } else {
                val randomFactor = 0.3f + Math.random().toFloat() * 0.7f
                (barMinH + currentPower * barMaxH * attenuation * randomFactor)
                    .coerceIn(barMinH, barMinH + barMaxH)
            }

            // 平滑插值：每帧向目标值靠近 35%（对齐 iOS animateWithDuration:0.1）
            barCurrentH[i] += (targetH - barCurrentH[i]) * 0.35f
            val barH = barCurrentH[i].coerceAtLeast(barMinH)

            // 透明度
            val fade = 1f - distFromCenter
            val alphaFactor = fade * fade * 0.95f
            barPaint.color = adjustAlpha(barColor, alphaFactor)

            val x = startX + i * (barWidth + barGap)
            barRect.set(x, baseY - barH, x + barWidth, baseY)
            canvas.drawRoundRect(barRect, barCornerR, barCornerR, barPaint)
        }
    }

    /** 提示文字（screenH * 0.68 位置） */
    private fun drawHintText(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = if (isCancel) Color.argb(255, 239, 68, 68) else Color.WHITE
        val text = if (isCancel) textCancel else textRecording
        canvas.drawText(text, w / 2f, h * 0.68f, textPaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
