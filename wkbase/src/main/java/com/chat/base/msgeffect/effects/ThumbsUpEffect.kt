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

package com.chat.base.msgeffect.effects

import android.graphics.*
import com.chat.base.msgeffect.MessageEffectType
import com.chat.base.msgeffect.ParticleSystem
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ThumbsUpEffect(
    type: MessageEffectType,
    sourceRect: RectF,
    viewWidth: Int,
    viewHeight: Int
) : BaseEffect(type, sourceRect, viewWidth, viewHeight) {

    private data class ThumbIcon(
        var x: Float,
        var y: Float,
        var targetX: Float,
        var scale: Float = 0.5f,
        var alpha: Float = 0f,
        var swayPhase: Float = 0f,
        var swayAmplitude: Float = 0f,
        var speed: Float = 0f,
        var delay: Float = 0f
    )

    private val thumbs = mutableListOf<ThumbIcon>()
    private val starSys = ParticleSystem()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPath = Path()
    private val thumbSize = viewWidth * 0.06f

    private val starColors = intArrayOf(
        0xFFFFD700.toInt(), 0xFFFFA000.toInt(), 0xFFFFECB3.toInt(), 0xFFFFFF00.toInt()
    )

    override fun onStart() {
        buildThumbPath()
        generateThumbs()
    }

    private fun buildThumbPath() {
        val s = thumbSize
        thumbPath.reset()
        // Simplified thumb shape
        // Palm
        thumbPath.addRoundRect(
            RectF(-s * 0.35f, -s * 0.1f, s * 0.35f, s * 0.45f),
            s * 0.1f, s * 0.1f, Path.Direction.CW
        )
        // Thumb up part
        thumbPath.addRoundRect(
            RectF(-s * 0.12f, -s * 0.55f, s * 0.12f, 0f),
            s * 0.12f, s * 0.12f, Path.Direction.CW
        )
    }

    private fun generateThumbs() {
        val count = 18
        val stagger = 111f

        for (i in 0 until count) {
            val startX = sourceRect.centerX() + (Random.nextFloat() - 0.5f) * sourceRect.width() * 0.5f
            thumbs.add(ThumbIcon(
                x = startX,
                y = viewHeight + thumbSize,
                targetX = startX,
                swayPhase = Random.nextFloat() * 6.28f,
                swayAmplitude = 20f + Random.nextFloat() * 25f,
                speed = 150f + Random.nextFloat() * 80f,
                delay = i * stagger
            ))
        }
    }

    override fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        val t = elapsedMs.toFloat()
        val delta = deltaMs.toFloat().coerceAtLeast(1f)

        // Update thumbs
        for (thumb in thumbs) {
            val localT = t - thumb.delay
            if (localT < 0f) continue

            val riseT = localT / 1000f
            thumb.y = viewHeight + thumbSize - thumb.speed * riseT
            thumb.x = thumb.targetX + sin(thumb.swayPhase + riseT * 2.5f) * thumb.swayAmplitude

            // Scale pulse
            thumb.scale = 0.9f + sin(localT * 0.006f) * 0.1f

            // Alpha
            val totalLife = (viewHeight + thumbSize * 2) / thumb.speed * 1000f
            val lifeRatio = localT / totalLife
            thumb.alpha = when {
                lifeRatio < 0.1f -> lifeRatio / 0.1f
                lifeRatio > 0.7f -> (1f - (lifeRatio - 0.7f) / 0.3f).coerceAtLeast(0f)
                else -> 0.95f
            }
        }

        // Emit stars periodically
        if (t > 500f && t < 5000f && Random.nextFloat() > 0.6f) {
            val activeThumb = thumbs.firstOrNull { t - it.delay > 0 && it.alpha > 0.3f }
            if (activeThumb != null) {
                starSys.emit(
                    count = 1,
                    x = activeThumb.x + (Random.nextFloat() - 0.5f) * thumbSize,
                    y = activeThumb.y,
                    speedMin = 50f,
                    speedMax = 150f,
                    angleMin = 0f,
                    angleMax = 360f,
                    sizeMin = 3f,
                    sizeMax = 7f,
                    lifeMin = 500f,
                    lifeMax = 1000f,
                    color = starColors[Random.nextInt(starColors.size)],
                    gravity = -30f,
                    drag = 1f,
                    fadeStart = 0.4f
                )
            }
        }

        starSys.update(delta)

        // Draw stars behind thumbs
        starSys.draw(canvas) { c, p, particle ->
            drawStar(c, p, particle.size)
        }

        // Draw thumbs
        for (thumb in thumbs) {
            if (thumb.alpha <= 0f) continue
            if (thumb.y < -thumbSize || thumb.y > viewHeight + thumbSize) continue

            canvas.save()
            canvas.translate(thumb.x, thumb.y)
            canvas.scale(thumb.scale, thumb.scale)

            paint.color = 0xFFFFCA28.toInt()
            paint.alpha = (thumb.alpha * 255).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawPath(thumbPath, paint)

            // Outline
            paint.color = 0xFFF57F17.toInt()
            paint.alpha = (thumb.alpha * 200).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawPath(thumbPath, paint)
            paint.style = Paint.Style.FILL

            canvas.restore()
        }
    }

    private fun drawStar(canvas: Canvas, paint: Paint, size: Float) {
        val path = Path()
        for (i in 0 until 5) {
            val outerAngle = Math.toRadians((i * 72 - 90).toDouble())
            val innerAngle = Math.toRadians((i * 72 + 36 - 90).toDouble())
            val ox = (cos(outerAngle) * size).toFloat()
            val oy = (sin(outerAngle) * size).toFloat()
            val ix = (cos(innerAngle) * size * 0.4f).toFloat()
            val iy = (sin(innerAngle) * size * 0.4f).toFloat()
            if (i == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
            path.lineTo(ix, iy)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun onEnd() {
        thumbs.clear()
        starSys.clear()
    }
}
