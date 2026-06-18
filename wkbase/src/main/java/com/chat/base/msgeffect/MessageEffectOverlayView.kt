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

package com.chat.base.msgeffect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.chat.base.msgeffect.effects.BaseEffect
import com.chat.base.msgeffect.effects.RocketEffect
import com.chat.base.msgeffect.effects.BombEffect
import com.chat.base.msgeffect.effects.HeartsEffect
import com.chat.base.msgeffect.effects.ConfettiEffect
import com.chat.base.msgeffect.effects.ThumbsUpEffect
import com.tencent.bugly.crashreport.CrashReport

class MessageEffectOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val activeEffects = mutableListOf<BaseEffect>()
    private var isRunning = false
    private var lastFrameTimeMs = 0L

    private val frameCallback: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    fun playEffect(type: MessageEffectType, sourceRect: RectF, avatarBitmap: Bitmap? = null) {
        try {
            if (activeEffects.size >= MAX_CONCURRENT_EFFECTS) return

            val effect = createEffect(type, sourceRect, avatarBitmap) ?: return
            val now = SystemClock.elapsedRealtime()
            effect.start(now)
            activeEffects.add(effect)

            visibility = VISIBLE
            if (!isRunning) {
                isRunning = true
                lastFrameTimeMs = now
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        } catch (e: Exception) {
            CrashReport.postCatchedException(e)
        }
    }

    private fun createEffect(type: MessageEffectType, sourceRect: RectF, avatarBitmap: Bitmap?): BaseEffect? {
        var w = width
        var h = height
        if (w <= 0 || h <= 0) {
            val parent = parent as? View
            w = parent?.width ?: 0
            h = parent?.height ?: 0
        }
        if (w <= 0 || h <= 0) return null
        return when (type) {
            is MessageEffectType.Rocket -> RocketEffect(type, sourceRect, w, h, avatarBitmap).also {
                it.setContext(context)
            }
            is MessageEffectType.Bomb -> BombEffect(type, sourceRect, w, h)
            is MessageEffectType.Hearts -> HeartsEffect(type, sourceRect, w, h)
            is MessageEffectType.Confetti -> ConfettiEffect(type, sourceRect, w, h)
            is MessageEffectType.ThumbsUp -> ThumbsUpEffect(type, sourceRect, w, h)
            is MessageEffectType.ActionVideo -> null
            is MessageEffectType.ClassyVideo -> null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeEffects.isEmpty()) return

        try {
            val now = SystemClock.elapsedRealtime()
            val deltaMs = (now - lastFrameTimeMs).coerceIn(0, 50)
            lastFrameTimeMs = now

            val iterator = activeEffects.iterator()
            while (iterator.hasNext()) {
                val effect = iterator.next()
                val elapsed = now - effect.startTimeMs
                if (effect.isFinished(elapsed)) {
                    effect.onEnd()
                    iterator.remove()
                } else {
                    effect.onFrame(canvas, elapsed, deltaMs)
                }
            }

            if (activeEffects.isEmpty()) {
                stopLoop()
            }
        } catch (e: Exception) {
            CrashReport.postCatchedException(e)
            activeEffects.clear()
            stopLoop()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAll()
    }

    fun cancelAll() {
        for (effect in activeEffects) {
            effect.onEnd()
        }
        activeEffects.clear()
        stopLoop()
    }

    private fun stopLoop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        visibility = INVISIBLE
    }

    companion object {
        private const val MAX_CONCURRENT_EFFECTS = 8
    }
}
