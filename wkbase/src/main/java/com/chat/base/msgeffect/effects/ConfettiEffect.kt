package com.chat.base.msgeffect.effects

import android.graphics.*
import com.chat.base.msgeffect.MessageEffectType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ConfettiEffect(
    type: MessageEffectType,
    sourceRect: RectF,
    viewWidth: Int,
    viewHeight: Int
) : BaseEffect(type, sourceRect, viewWidth, viewHeight) {

    private data class ConfettiPiece(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var width: Float,
        var height: Float,
        var color: Int,
        var alpha: Float = 1f,
        var scaleX: Float = 1f,
        var wobblePhase: Float = 0f,
        var wobbleSpeed: Float = 0f,
        var turbulence: Float = 0f,
        var delay: Float = 0f
    )

    private val pieces = mutableListOf<ConfettiPiece>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gravity = 800f
    private val damping = 0.93f

    private val colors = intArrayOf(
        0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF2196F3.toInt(),
        0xFF00BCD4.toInt(), 0xFF4CAF50.toInt(), 0xFFFFEB3B.toInt(),
        0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFF3F51B5.toInt(),
        0xFF009688.toInt(), 0xFFFFC107.toInt(), 0xFF8BC34A.toInt()
    )

    override fun onStart() {
        generatePieces()
    }

    private fun generatePieces() {
        val totalPieces = 160
        val halfW = viewWidth.toFloat()

        for (i in 0 until totalPieces) {
            val fromLeft = i < totalPieces / 2
            val startX = if (fromLeft) -10f else viewWidth + 10f
            val startY = viewHeight * 0.9f + (Random.nextFloat() - 0.5f) * 100f

            val angle = if (fromLeft) {
                Random.nextFloat() * 60f - 80f // -80 to -20 degrees (upward right)
            } else {
                Random.nextFloat() * 60f + 200f // 200 to 260 degrees (upward left)
            }
            val speed = 500f + Random.nextFloat() * 600f
            val rad = Math.toRadians(angle.toDouble())

            val shapeType = Random.nextInt(4)
            val w: Float
            val h: Float
            when (shapeType) {
                0 -> { w = 8f; h = 8f } // circle-ish
                1 -> { w = 10f; h = 4f } // short strip
                2 -> { w = 14f; h = 4f } // medium strip
                else -> { w = 18f; h = 3f } // long strip
            }

            val typeDelay = when (shapeType) {
                0 -> 0f
                1 -> 10f
                2 -> 80f
                else -> 40f
            }

            pieces.add(ConfettiPiece(
                x = startX,
                y = startY,
                vx = (cos(rad) * speed).toFloat(),
                vy = (sin(rad) * speed).toFloat(),
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (1f + Random.nextFloat() * 5f) * (if (Random.nextBoolean()) 1f else -1f) * 360f,
                width = w * (0.8f + Random.nextFloat() * 0.8f),
                height = h * (0.8f + Random.nextFloat() * 0.8f),
                color = colors[Random.nextInt(colors.size)],
                wobblePhase = Random.nextFloat() * 6.28f,
                wobbleSpeed = 3f + Random.nextFloat() * 4f,
                turbulence = (Random.nextFloat() - 0.5f) * 960f,
                delay = typeDelay + Random.nextFloat() * 50f
            ))
        }
    }

    override fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        val t = elapsedMs.toFloat()
        val dt = deltaMs.toFloat().coerceAtLeast(1f) / 1000f

        for (piece in pieces) {
            if (t < piece.delay) continue

            val localT = t - piece.delay

            // Apply gravity
            piece.vy += gravity * dt

            // Damping when falling
            if (piece.vy > 0) {
                piece.vy *= damping
                piece.vx *= damping
            }

            // Turbulence
            piece.vx += piece.turbulence * dt * 0.3f

            // Slowdown phase (0.7s after local start)
            if (localT > 700f && localT < 1400f) {
                val slowP = ((localT - 700f) / 700f).coerceAtMost(1f)
                piece.vx *= (1f - slowP * 0.003f)
                piece.vy *= (1f - slowP * 0.003f)
            }

            // Update position
            piece.x += piece.vx * dt
            piece.y += piece.vy * dt
            piece.rotation += piece.rotationSpeed * dt

            // 3D wobble (simulated scale on X axis)
            piece.scaleX = cos(piece.wobblePhase + localT * 0.003f * piece.wobbleSpeed).coerceIn(-1f, 1f)

            // Fade when below screen
            if (piece.y > viewHeight - 30) {
                piece.alpha = ((viewHeight.toFloat() + 30f - piece.y) / 60f).coerceIn(0f, 1f)
            }

            // Fade at end of effect
            if (t > 8000f) {
                piece.alpha = piece.alpha.coerceAtMost(((10000f - t) / 2000f).coerceIn(0f, 1f))
            }
        }

        // Draw all pieces
        for (piece in pieces) {
            if (piece.alpha <= 0.01f) continue
            if (piece.y > viewHeight + 50 || piece.y < -50) continue

            canvas.save()
            canvas.translate(piece.x, piece.y)
            canvas.rotate(piece.rotation)
            canvas.scale(piece.scaleX, 1f)

            paint.color = piece.color
            paint.alpha = (piece.alpha * 255).toInt()

            val hw = piece.width / 2f
            val hh = piece.height / 2f
            if (piece.width == piece.height) {
                canvas.drawCircle(0f, 0f, hw, paint)
            } else {
                canvas.drawRoundRect(-hw, -hh, hw, hh, 2f, 2f, paint)
            }

            canvas.restore()
        }
    }

    override fun onEnd() {
        pieces.clear()
    }
}
