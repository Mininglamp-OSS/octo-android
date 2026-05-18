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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class HeartsEffect(
    type: MessageEffectType,
    sourceRect: RectF,
    viewWidth: Int,
    viewHeight: Int
) : BaseEffect(type, sourceRect, viewWidth, viewHeight) {

    private data class HeartItem(
        var x: Float, var y: Float,
        var targetX: Float, var targetY: Float,
        var startX: Float, var startY: Float,
        var scale: Float = 0.15f,
        var alpha: Float = 0f,
        var delay: Int = 0
    )

    private val hearts = mutableListOf<HeartItem>()
    private val heartPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heartColors = intArrayOf(
        0xFFE91E63.toInt(), 0xFFF44336.toInt(), 0xFFFF5252.toInt(),
        0xFFFF4081.toInt(), 0xFFD50000.toInt(), 0xFFFF1744.toInt()
    )

    private val heartSize = viewWidth * 0.04f
    private val shapeScale = viewWidth * 0.012f

    override fun onStart() {
        buildHeartShape()
        buildHeartPath()
    }

    private fun buildHeartShape() {
        val cx = viewWidth / 2f
        val cy = viewHeight * 0.4f
        val numHearts = 26

        for (i in 0 until numHearts) {
            val t = (i.toFloat() / numHearts) * (2 * Math.PI)
            // Parametric heart equation
            val hx = 16f * sin(t).toFloat().pow(3)
            val hy = -(13f * cos(t).toFloat() - 5f * cos(2 * t).toFloat() -
                    2f * cos(3 * t).toFloat() - cos(4 * t).toFloat())

            hearts.add(HeartItem(
                x = sourceRect.centerX(),
                y = sourceRect.centerY(),
                targetX = cx + hx * shapeScale,
                targetY = cy + hy * shapeScale,
                startX = sourceRect.centerX(),
                startY = sourceRect.centerY(),
                delay = (i % 6) * 12
            ))
        }
    }

    private fun buildHeartPath() {
        heartPath.reset()
        val s = heartSize
        heartPath.moveTo(0f, -s * 0.3f)
        heartPath.cubicTo(-s * 0.5f, -s, -s, -s * 0.4f, 0f, s * 0.5f)
        heartPath.moveTo(0f, -s * 0.3f)
        heartPath.cubicTo(s * 0.5f, -s, s, -s * 0.4f, 0f, s * 0.5f)
        heartPath.close()
    }

    override fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        val t = elapsedMs.toFloat()

        for ((index, heart) in hearts.withIndex()) {
            val localT = (t - heart.delay).coerceAtLeast(0f)

            when {
                // Phase 1: Launch to position (0-900ms)
                localT < 900f -> {
                    val p = localT / 900f
                    val eased = springEase(p)
                    heart.x = heart.startX + (heart.targetX - heart.startX) * eased
                    heart.y = heart.startY + (heart.targetY - heart.startY) * eased
                    heart.scale = 0.15f + 0.85f * eased
                    heart.alpha = (p * 0.95f).coerceAtMost(1f)
                }
                // Phase 2: Beat pulse (900-1650ms)
                localT < 1650f -> {
                    val p = (localT - 900f) / 750f
                    val beatScale = 1f + 0.14f * sin(p * Math.PI.toFloat() * 2f) * (1f - p)
                    heart.x = heart.targetX
                    heart.y = heart.targetY
                    heart.scale = beatScale
                    heart.alpha = 0.95f
                }
                // Phase 3: Scatter outward (1650-2350ms)
                localT < 2350f -> {
                    val p = (localT - 1650f) / 700f
                    val cx = viewWidth / 2f
                    val cy = viewHeight * 0.4f
                    val dx = heart.targetX - cx
                    val dy = heart.targetY - cy
                    val extension = 40f + Random.nextFloat() * 24f
                    heart.x = heart.targetX + dx / shapeScale * extension * p
                    heart.y = heart.targetY + dy / shapeScale * extension * p
                    heart.scale = 1f - 0.15f * p
                    heart.alpha = 0.95f * (1f - p)
                }
                else -> {
                    heart.alpha = 0f
                }
            }
        }

        // Draw all hearts
        for ((index, heart) in hearts.withIndex()) {
            if (heart.alpha <= 0f) continue
            canvas.save()
            canvas.translate(heart.x, heart.y)
            canvas.scale(heart.scale, heart.scale)
            paint.color = heartColors[index % heartColors.size]
            paint.alpha = (heart.alpha * 255).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawPath(heartPath, paint)
            canvas.restore()
        }
    }

    override fun onEnd() {
        hearts.clear()
    }

    private fun springEase(t: Float): Float {
        val damping = 0.72f
        return 1f - (1f - t).let { x ->
            (Math.E.toFloat().pow(-damping * 8f * t)) * cos(t * 12f) * (1f - t)
        }.let { 1f - t + it * 0.3f }.coerceIn(0f, 1.05f).coerceAtMost(1f)
    }
}
