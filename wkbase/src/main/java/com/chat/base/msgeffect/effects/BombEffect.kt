package com.chat.base.msgeffect.effects

import android.graphics.*
import com.chat.base.msgeffect.MessageEffectType
import com.chat.base.msgeffect.ParticleSystem
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BombEffect(
    type: MessageEffectType,
    sourceRect: RectF,
    viewWidth: Int,
    viewHeight: Int
) : BaseEffect(type, sourceRect, viewWidth, viewHeight) {

    private val debrisSys = ParticleSystem()
    private val flashSys = ParticleSystem()
    private val smokeSys = ParticleSystem()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var bombX = 0f
    private var bombY = 0f
    private var shakePhase = 0f
    private var flashRadius = 0f
    private var flashAlpha = 0f
    private var shockwaveRadius = 0f
    private var shockwaveAlpha = 0f
    private var bombVisible = true

    private val debrisColors = intArrayOf(
        0xFFFF5722.toInt(), 0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(),
        0xFF795548.toInt(), 0xFF424242.toInt(), 0xFFFF6F00.toInt()
    )

    override fun onStart() {
        bombX = sourceRect.centerX()
        bombY = sourceRect.centerY()
    }

    override fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        val t = elapsedMs.toFloat()
        val delta = deltaMs.toFloat().coerceAtLeast(1f)

        updateState(t, delta)
        debrisSys.update(delta)
        flashSys.update(delta)
        smokeSys.update(delta)

        drawFlash(canvas, t)
        drawShockwave(canvas)
        smokeSys.draw(canvas) { c, p, particle ->
            p.maskFilter = BlurMaskFilter(particle.size * 0.4f, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(0f, 0f, particle.size, p)
            p.maskFilter = null
        }
        debrisSys.draw(canvas) { c, p, particle ->
            c.drawRect(-particle.size / 2, -particle.size / 3, particle.size / 2, particle.size / 3, p)
        }
        flashSys.draw(canvas)

        if (bombVisible && t < 700f) {
            drawBomb(canvas, t)
        }
    }

    private fun updateState(t: Float, delta: Float) {
        when {
            // Phase 1: Shake (0-700ms)
            t < 700f -> {
                shakePhase = t
                bombVisible = true
                val intensity = (t / 700f)
                val shakeOffset = sin(t * 0.05f) * 4f * intensity
                bombX = sourceRect.centerX() + shakeOffset
            }
            // Phase 2: Flash + Explosion (700-1200ms)
            t < 1200f -> {
                if (bombVisible) {
                    bombVisible = false
                    emitExplosion()
                }
                val p = (t - 700f) / 500f
                flashRadius = viewWidth * 0.8f * p
                flashAlpha = (1f - p).coerceAtLeast(0f)
                shockwaveRadius = viewWidth * 0.6f * p
                shockwaveAlpha = (1f - p * 1.2f).coerceAtLeast(0f)
            }
            // Phase 3: Debris flying (1200-5000ms)
            t < 5000f -> {
                flashAlpha = 0f
                shockwaveAlpha = 0f
                if (t < 2000f && Random.nextFloat() > 0.7f) {
                    emitSmoke()
                }
            }
            // Phase 4: Settle (5000-8500ms)
            else -> {
                flashAlpha = 0f
                shockwaveAlpha = 0f
            }
        }
    }

    private fun emitExplosion() {
        // Debris burst
        debrisSys.emit(
            count = 60,
            x = bombX,
            y = bombY,
            speedMin = 300f,
            speedMax = 900f,
            angleMin = 0f,
            angleMax = 360f,
            sizeMin = 6f,
            sizeMax = 16f,
            lifeMin = 2000f,
            lifeMax = 6000f,
            color = debrisColors[Random.nextInt(debrisColors.size)],
            gravity = 600f,
            drag = 0.8f,
            fadeStart = 0.6f,
            rotationSpeedRange = 720f
        )

        // Additional colored debris
        for (color in debrisColors) {
            debrisSys.emit(
                count = 8,
                x = bombX,
                y = bombY,
                speedMin = 200f,
                speedMax = 700f,
                angleMin = 0f,
                angleMax = 360f,
                sizeMin = 4f,
                sizeMax = 12f,
                lifeMin = 1500f,
                lifeMax = 5000f,
                color = color,
                gravity = 500f,
                drag = 1f,
                fadeStart = 0.5f,
                rotationSpeedRange = 540f
            )
        }

        // Flash particles (white hot center)
        flashSys.emit(
            count = 20,
            x = bombX,
            y = bombY,
            speedMin = 100f,
            speedMax = 400f,
            angleMin = 0f,
            angleMax = 360f,
            sizeMin = 4f,
            sizeMax = 10f,
            lifeMin = 200f,
            lifeMax = 600f,
            color = 0xFFFFFFFF.toInt(),
            gravity = 0f,
            drag = 3f,
            fadeStart = 0.3f
        )
    }

    private fun emitSmoke() {
        smokeSys.emit(
            count = 3,
            x = bombX + (Random.nextFloat() - 0.5f) * 100f,
            y = bombY + (Random.nextFloat() - 0.5f) * 80f,
            speedMin = 20f,
            speedMax = 80f,
            angleMin = 240f,
            angleMax = 300f,
            sizeMin = 15f,
            sizeMax = 35f,
            lifeMin = 800f,
            lifeMax = 1500f,
            color = 0xFF757575.toInt(),
            gravity = -60f,
            drag = 1.5f,
            fadeStart = 0.4f
        )
    }

    private fun drawFlash(canvas: Canvas, t: Float) {
        if (flashAlpha <= 0f) return
        paint.color = Color.WHITE
        paint.alpha = (flashAlpha * 200).toInt()
        paint.maskFilter = BlurMaskFilter(flashRadius * 0.3f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(bombX, bombY, flashRadius, paint)
        paint.maskFilter = null
    }

    private fun drawShockwave(canvas: Canvas) {
        if (shockwaveAlpha <= 0f) return
        paint.color = 0xFF424242.toInt()
        paint.alpha = (shockwaveAlpha * 100).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawCircle(bombX, bombY, shockwaveRadius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBomb(canvas: Canvas, t: Float) {
        val shake = sin(t * 0.08f) * 3f * (t / 700f)
        canvas.save()
        canvas.translate(bombX + shake, bombY)

        // Bomb body
        paint.color = 0xFF37474F.toInt()
        paint.alpha = 255
        val bombR = sourceRect.width().coerceAtMost(sourceRect.height()) * 0.3f
        canvas.drawCircle(0f, 0f, bombR, paint)

        // Fuse
        paint.color = 0xFF795548.toInt()
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        val fusePath = Path()
        fusePath.moveTo(0f, -bombR)
        fusePath.quadTo(bombR * 0.5f, -bombR * 1.4f, 0f, -bombR * 1.6f)
        canvas.drawPath(fusePath, paint)
        paint.style = Paint.Style.FILL

        // Fuse spark
        val sparkAlpha = ((sin(t * 0.02f) + 1f) / 2f * 255).toInt()
        paint.color = 0xFFFF6F00.toInt()
        paint.alpha = sparkAlpha
        canvas.drawCircle(0f, -bombR * 1.6f, 5f, paint)

        canvas.restore()
    }

    override fun onEnd() {
        debrisSys.clear()
        flashSys.clear()
        smokeSys.clear()
    }
}
