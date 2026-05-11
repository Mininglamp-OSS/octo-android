package com.chat.base.msgeffect.effects

import android.graphics.Canvas
import android.graphics.RectF
import com.chat.base.msgeffect.MessageEffectType

abstract class BaseEffect(
    val type: MessageEffectType,
    val sourceRect: RectF,
    val viewWidth: Int,
    val viewHeight: Int
) {
    var startTimeMs: Long = 0L
        private set

    fun start(currentTimeMs: Long) {
        startTimeMs = currentTimeMs
        onStart()
    }

    abstract fun onStart()

    abstract fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long)

    open fun onEnd() {}

    fun isFinished(elapsedMs: Long): Boolean = elapsedMs >= type.durationMs
}
