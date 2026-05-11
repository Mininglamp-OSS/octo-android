package com.chat.base.msgeffect

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1f,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    var scale: Float = 1f,
    var life: Float = 0f,
    var maxLife: Float = 1000f,
    var color: Int = 0xFFFFFFFF.toInt(),
    var size: Float = 10f,
    var gravity: Float = 0f,
    var drag: Float = 0f,
    var fadeStart: Float = 0.7f
) {
    val isDead: Boolean get() = life >= maxLife

    fun update(deltaMs: Float) {
        life += deltaMs
        vy += gravity * (deltaMs / 1000f)
        vx *= (1f - drag * deltaMs / 1000f)
        vy *= (1f - drag * deltaMs / 1000f)
        x += vx * (deltaMs / 1000f)
        y += vy * (deltaMs / 1000f)
        rotation += rotationSpeed * (deltaMs / 1000f)

        val lifeRatio = life / maxLife
        if (lifeRatio > fadeStart) {
            alpha = 1f - (lifeRatio - fadeStart) / (1f - fadeStart)
        }
    }
}

class ParticleSystem {
    val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun emit(
        count: Int,
        x: Float,
        y: Float,
        speedMin: Float,
        speedMax: Float,
        angleMin: Float = 0f,
        angleMax: Float = 360f,
        sizeMin: Float = 6f,
        sizeMax: Float = 12f,
        lifeMin: Float = 800f,
        lifeMax: Float = 1500f,
        color: Int = 0xFFFFFFFF.toInt(),
        gravity: Float = 800f,
        drag: Float = 0.5f,
        fadeStart: Float = 0.7f,
        rotationSpeedRange: Float = 360f
    ) {
        repeat(count) {
            val angle = Math.toRadians((Random.nextFloat() * (angleMax - angleMin) + angleMin).toDouble())
            val speed = Random.nextFloat() * (speedMax - speedMin) + speedMin
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    size = Random.nextFloat() * (sizeMax - sizeMin) + sizeMin,
                    maxLife = Random.nextFloat() * (lifeMax - lifeMin) + lifeMin,
                    color = color,
                    gravity = gravity,
                    drag = drag,
                    fadeStart = fadeStart,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 2f * rotationSpeedRange
                )
            )
        }
    }

    fun update(deltaMs: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.update(deltaMs)
            if (p.isDead) iterator.remove()
        }
    }

    fun draw(canvas: Canvas, drawShape: ((Canvas, Paint, Particle) -> Unit)? = null) {
        for (p in particles) {
            if (p.alpha <= 0f) continue
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)

            if (drawShape != null) {
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rotation)
                canvas.scale(p.scale, p.scale)
                drawShape(canvas, paint, p)
                canvas.restore()
            } else {
                canvas.drawCircle(p.x, p.y, p.size * p.scale, paint)
            }
        }
    }

    fun clear() {
        particles.clear()
    }

    val isAlive: Boolean get() = particles.isNotEmpty()
}
