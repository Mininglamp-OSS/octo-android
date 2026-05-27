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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.chat.base.ui.Theme
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import kotlin.math.max
import kotlin.math.min

class HoldToTalkOverlayView(context: Context) : FrameLayout(context) {

    enum class DragState { RECORDING, CANCEL, SEND_VOICE }

    interface Listener {
        fun onDragStateChanged(state: DragState)
    }

    var listener: Listener? = null

    private val bubbleView: BubbleWaveView
    private val cancelPill: TextView
    private val sendVoicePill: TextView
    private val hintLabel: TextView
    private val bottomArea: View

    private var currentDragState = DragState.RECORDING
    private val thinkingDots = mutableListOf<View>()
    private var thinkingAnimator: ValueAnimator? = null
    private var thinkingDotIndex = 0

    private val bubbleColor = Color.argb(255, 224, 240, 255)
    private val bubbleRedColor = Color.argb(255, 242, 89, 77)
    private val bubbleGreenColor = Color.argb(255, 77, 199, 102)

    init {
        setBackgroundColor(Color.argb(166, 0, 0, 0)) // 65% black

        // Bottom area (theme color arc)
        bottomArea = BottomArcView(context)
        val bottomH = AndroidUtilities.dp(100f) + getNavBarHeight()
        addView(bottomArea, LayoutParams(LayoutParams.MATCH_PARENT, bottomH, Gravity.BOTTOM))

        // Hint label
        hintLabel = TextView(context).apply {
            text = context.getString(R.string.voice_release_to_text)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val hintLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        hintLp.bottomMargin = AndroidUtilities.dp(40f) + getNavBarHeight()
        addView(hintLabel, hintLp)

        // Bubble
        bubbleView = BubbleWaveView(context)
        val bubbleW = (resources.displayMetrics.widthPixels * 0.62f).toInt()
        val bubbleLp = LayoutParams(bubbleW, AndroidUtilities.dp(85f))
        bubbleLp.gravity = Gravity.CENTER_HORIZONTAL
        bubbleLp.topMargin = (resources.displayMetrics.heightPixels * 0.38f).toInt()
        addView(bubbleView, bubbleLp)

        // Cancel pill (left)
        cancelPill = createPill(context.getString(R.string.voice_pill_cancel))
        val cancelLp = LayoutParams(
            (resources.displayMetrics.widthPixels * 0.42f).toInt(),
            AndroidUtilities.dp(55f)
        )
        cancelLp.gravity = Gravity.BOTTOM or Gravity.START
        cancelLp.bottomMargin = bottomH + AndroidUtilities.dp(15f)
        cancelLp.leftMargin = AndroidUtilities.dp(12f)
        addView(cancelPill, cancelLp)

        // Send voice pill (right)
        sendVoicePill = createPill(context.getString(R.string.voice_pill_send_original))
        val sendLp = LayoutParams(
            (resources.displayMetrics.widthPixels * 0.42f).toInt(),
            AndroidUtilities.dp(55f)
        )
        sendLp.gravity = Gravity.BOTTOM or Gravity.END
        sendLp.bottomMargin = bottomH + AndroidUtilities.dp(15f)
        sendLp.rightMargin = AndroidUtilities.dp(12f)
        addView(sendVoicePill, sendLp)
    }

    fun updateAmplitude(power: Float) {
        bubbleView.updatePower(power)
    }

    fun updateDragPosition(x: Float, y: Float) {
        val cancelRect = RectF(
            cancelPill.left.toFloat() - AndroidUtilities.dp(10f),
            cancelPill.top.toFloat() - AndroidUtilities.dp(20f),
            cancelPill.right.toFloat() + AndroidUtilities.dp(10f),
            cancelPill.bottom.toFloat() + AndroidUtilities.dp(20f)
        )
        val sendRect = RectF(
            sendVoicePill.left.toFloat() - AndroidUtilities.dp(10f),
            sendVoicePill.top.toFloat() - AndroidUtilities.dp(20f),
            sendVoicePill.right.toFloat() + AndroidUtilities.dp(10f),
            sendVoicePill.bottom.toFloat() + AndroidUtilities.dp(20f)
        )

        val newState = when {
            cancelRect.contains(x, y) -> DragState.CANCEL
            sendRect.contains(x, y) -> DragState.SEND_VOICE
            else -> DragState.RECORDING
        }

        if (newState != currentDragState) {
            currentDragState = newState
            updateDragUI()
            listener?.onDragStateChanged(newState)
        }
    }

    fun getDragState(): DragState = currentDragState

    fun showThinking() {
        bubbleView.visibility = View.GONE
        cancelPill.visibility = View.GONE
        sendVoicePill.visibility = View.GONE

        hintLabel.text = context.getString(R.string.voice_recognizing)

        // Add thinking dots
        thinkingDots.forEach { removeView(it) }
        thinkingDots.clear()

        val dotSize = AndroidUtilities.dp(10f)
        val dotGap = AndroidUtilities.dp(12f)
        val totalW = dotSize * 3 + dotGap * 2
        val startX = (resources.displayMetrics.widthPixels - totalW) / 2
        val dotY = (resources.displayMetrics.heightPixels * 0.42f).toInt()

        for (i in 0..2) {
            val dot = View(context).apply {
                setBackgroundResource(0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.argb(200, 89, 140, 217))
                }
            }
            val lp = LayoutParams(dotSize, dotSize)
            lp.leftMargin = startX + i * (dotSize + dotGap)
            lp.topMargin = dotY
            addView(dot, lp)
            thinkingDots.add(dot)
        }

        startThinkingAnimation()
    }

    fun dismiss() {
        stopThinkingAnimation()
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun updateDragUI() {
        when (currentDragState) {
            DragState.RECORDING -> {
                bubbleView.setBubbleColor(bubbleColor)
                bubbleView.translationX = 0f
                cancelPill.setBackgroundColor(Color.argb(230, 71, 71, 71))
                cancelPill.setTextColor(Color.WHITE)
                cancelPill.scaleX = 1f
                cancelPill.scaleY = 1f
                sendVoicePill.setBackgroundColor(Color.argb(230, 71, 71, 71))
                sendVoicePill.setTextColor(Color.WHITE)
                sendVoicePill.scaleX = 1f
                sendVoicePill.scaleY = 1f
                hintLabel.text = context.getString(R.string.voice_release_to_text)
            }
            DragState.CANCEL -> {
                bubbleView.setBubbleColor(bubbleRedColor)
                bubbleView.translationX = -AndroidUtilities.dp(25f).toFloat()
                cancelPill.setBackgroundColor(Color.argb(255, 242, 242, 242))
                cancelPill.setTextColor(Color.argb(255, 38, 38, 38))
                cancelPill.scaleX = 1.06f
                cancelPill.scaleY = 1.06f
                sendVoicePill.setBackgroundColor(Color.argb(230, 71, 71, 71))
                sendVoicePill.setTextColor(Color.WHITE)
                sendVoicePill.scaleX = 1f
                sendVoicePill.scaleY = 1f
                hintLabel.text = context.getString(R.string.voice_release_to_cancel)
            }
            DragState.SEND_VOICE -> {
                bubbleView.setBubbleColor(bubbleGreenColor)
                bubbleView.translationX = AndroidUtilities.dp(25f).toFloat()
                sendVoicePill.setBackgroundColor(Color.argb(255, 242, 242, 242))
                sendVoicePill.setTextColor(Color.argb(255, 38, 38, 38))
                sendVoicePill.scaleX = 1.06f
                sendVoicePill.scaleY = 1.06f
                cancelPill.setBackgroundColor(Color.argb(230, 71, 71, 71))
                cancelPill.setTextColor(Color.WHITE)
                cancelPill.scaleX = 1f
                cancelPill.scaleY = 1f
                hintLabel.text = context.getString(R.string.voice_release_to_send)
            }
        }
    }

    private fun createPill(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(
                AndroidUtilities.dp(16f), AndroidUtilities.dp(8f),
                AndroidUtilities.dp(16f), AndroidUtilities.dp(8f)
            )
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(28f).toFloat()
                setColor(Color.argb(230, 71, 71, 71))
            }
        }
    }

    private fun startThinkingAnimation() {
        thinkingDotIndex = 0
        thinkingAnimator = ValueAnimator.ofFloat(0f, 3f).apply {
            duration = 1050
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                val activeIndex = v.toInt() % 3
                for (i in thinkingDots.indices) {
                    val phase = ((v - i + 3) % 3)
                    val scale = if (phase < 0.5f) 1f + 0.4f * (phase / 0.5f)
                    else if (phase < 1f) 1.4f - 0.4f * ((phase - 0.5f) / 0.5f)
                    else 1f
                    thinkingDots[i].scaleX = scale
                    thinkingDots[i].scaleY = scale
                    thinkingDots[i].alpha = if (phase < 1f) 1f else 0.8f
                }
            }
            start()
        }
    }

    private fun stopThinkingAnimation() {
        thinkingAnimator?.cancel()
        thinkingAnimator = null
    }

    private fun getNavBarHeight(): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    // Inner view for the bubble with waveform
    private class BubbleWaveView(context: Context) : View(context) {
        private val barCount = 30
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(179, 89, 140, 217)
            strokeCap = Paint.Cap.ROUND
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 224, 240, 255)
        }
        private val tailPath = Path()
        private var currentPower = 0f
        private val barHeights = FloatArray(barCount)

        fun updatePower(power: Float) {
            currentPower = power
            val center = barCount / 2f
            val baseH = AndroidUtilities.dp(6f).toFloat()
            if (power < 0.08f) {
                for (i in 0 until barCount) barHeights[i] = baseH
            } else {
                val maxH = height.toFloat() - AndroidUtilities.dp(8f)
                for (i in 0 until barCount) {
                    val dist = kotlin.math.abs(i - center) / center
                    val attenuation = 1f - dist * 0.6f
                    val noise = 0.3f + (Math.random().toFloat() * 0.7f)
                    barHeights[i] = min(maxH, baseH + power * maxH * 0.7f * attenuation * noise)
                }
            }
            invalidate()
        }

        fun setBubbleColor(color: Int) {
            bgPaint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val r = AndroidUtilities.dp(16f).toFloat()
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, r, r, bgPaint)

            // Tail
            val tailW = AndroidUtilities.dp(12f).toFloat()
            val tailH = AndroidUtilities.dp(10f).toFloat()
            val cx = width / 2f
            tailPath.reset()
            tailPath.moveTo(cx - tailW / 2, height.toFloat())
            tailPath.lineTo(cx, height.toFloat() + tailH)
            tailPath.lineTo(cx + tailW / 2, height.toFloat())
            tailPath.close()
            canvas.drawPath(tailPath, bgPaint)

            // Waveform bars
            if (height <= 0) return
            val barW = AndroidUtilities.dp(3f).toFloat()
            val gap = AndroidUtilities.dp(2.5f).toFloat()
            val totalW = barCount * barW + (barCount - 1) * gap
            val startX = (width - totalW) / 2f
            val centerY = height / 2f

            for (i in 0 until barCount) {
                val h = max(AndroidUtilities.dp(6f).toFloat(), barHeights[i])
                val x = startX + i * (barW + gap) + barW / 2f
                barPaint.strokeWidth = barW
                canvas.drawLine(x, centerY - h / 2, x, centerY + h / 2, barPaint)
            }
        }
    }

    // Bottom arc view
    private inner class BottomArcView(context: Context) : View(context) {
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.colorAccount
        }

        override fun onDraw(canvas: Canvas) {
            val path = Path()
            val w = width.toFloat()
            val h = height.toFloat()
            path.moveTo(0f, AndroidUtilities.dp(15f).toFloat())
            path.quadTo(w / 2f, -AndroidUtilities.dp(15f).toFloat(), w, AndroidUtilities.dp(15f).toFloat())
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            canvas.drawPath(path, arcPaint)
        }
    }
}
