package com.chat.base.msgeffect.effects

import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import com.chat.base.msgeffect.MessageEffectType
import com.chat.base.msgeffect.ParticleSystem
import com.tencent.bugly.crashreport.CrashReport
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.random.Random

class RocketEffect(
    type: MessageEffectType,
    sourceRect: RectF,
    viewWidth: Int,
    viewHeight: Int,
    private val avatarBitmap: Bitmap? = null
) : BaseEffect(type, sourceRect, viewWidth, viewHeight) {

    private var context: Context? = null
    private val rocketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smokeSys = ParticleSystem()
    private val sparkSys = ParticleSystem()
    private val heatSys = ParticleSystem()
    private val burstSys = ParticleSystem()

    // iOS: 64×138pt, ratio 1:2.15
    private val rocketW = viewWidth * 0.17f
    private val rocketH = rocketW * 2.15f
    private val noseH = rocketH * 0.26f
    private val bodyH = rocketH * 0.52f
    private val nozzleH = rocketW * 0.11f  // iOS: 7pt out of 64W
    private val finH = rocketW * 0.5f       // iOS: 32pt
    private val finOutW = rocketW * 0.48f   // iOS: bodyWidth*0.48
    private val centerFinW = rocketW * 0.28f
    private val flameW = rocketW * 0.36f
    private val flameH = rocketW * 1.1f
    private val hw = rocketW / 2f
    private val cornerR = rocketW * 0.18f
    private val portholeR = rocketW * 0.19f

    private var rocketX = 0f
    private var rocketY = 0f
    private var rocketAlpha = 1f
    private var rocketScale = 0f
    private var flameScaleX = 1f
    private var flameScaleY = 1f
    private var shakeX = 0f
    private var flameIntensity = 0f
    private var shimmerPhase = -1f

    // Sparkle stars
    private data class StarParticle(var x: Float, var y: Float, var delay: Float,
        var alpha: Float = 0f, var scale: Float = 0.3f)
    private val stars = mutableListOf<StarParticle>()
    private var starsEmitted = false

    private var circularAvatar: Bitmap? = null
    private var hasVibratedLight = false
    private var hasVibratedMedium = false
    private var hasBurst = false

    fun setContext(ctx: Context) { this.context = ctx }

    override fun onStart() {
        rocketX = sourceRect.centerX()
        rocketY = sourceRect.centerY()
        prepareAvatar()
    }

    private fun prepareAvatar() {
        val src = avatarBitmap ?: return
        val size = (portholeR * 1.6f).toInt()
        if (size <= 0) return
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(output)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(scaled, 0f, 0f, p)
        circularAvatar = output
    }

    override fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        try {
            val t = elapsedMs.toFloat()
            val delta = deltaMs.toFloat().coerceAtLeast(1f)

            updateState(t)
            emitEffects(t, delta)
            triggerHaptics(t)

            smokeSys.update(delta)
            heatSys.update(delta)
            sparkSys.update(delta)
            burstSys.update(delta)

        drawBurstSmoke(canvas)
        drawSmoke(canvas)
        drawHeat(canvas)
        sparkSys.draw(canvas)
        drawStars(canvas, t)

        if (rocketAlpha > 0f) drawRocket(canvas, t)
        } catch (e: Exception) {
            CrashReport.postCatchedException(e)
        }
    }

    private fun updateState(t: Float) {
        when {
            t < 300f -> {
                val p = t / 300f
                rocketScale = overshoot(p, 2f).coerceAtMost(1.05f)
                rocketAlpha = p.coerceAtMost(1f)
                rocketY = sourceRect.centerY()
                shakeX = 0f
                flameScaleX = 1.35f; flameScaleY = 0.65f
                flameIntensity = p * 0.4f
            }
            t < 1000f -> {
                val p = (t - 300f) / 700f
                rocketScale = 1f; rocketAlpha = 1f
                // iOS: shake values [-2, 2, -1.5, 1.5, -1, 1, 0], 6 cycles in 0.75s
                val cycle = (p * 6f).toInt().coerceAtMost(5)
                val shakeAmps = floatArrayOf(-2f, 2f, -1.5f, 1.5f, -1f, 1f, 0f)
                val cycleP = (p * 6f) - cycle
                val from = shakeAmps[cycle]
                val to = shakeAmps[(cycle + 1).coerceAtMost(6)]
                shakeX = from + (to - from) * cycleP
                // iOS: flame stays WIDE(1.35) SHORT(0.65) with flicker
                val flickerP = (t % 220f) / 220f  // 0.22s repeat
                val flickerValues = floatArrayOf(1f, 1.15f, 0.92f, 1.10f, 0.96f, 1.07f, 1f)
                val flickerTimes = floatArrayOf(0f, 0.18f, 0.32f, 0.52f, 0.68f, 0.84f, 1f)
                var flickerScale = 1f
                for (i in 0 until flickerTimes.size - 1) {
                    if (flickerP >= flickerTimes[i] && flickerP < flickerTimes[i + 1]) {
                        val lp = (flickerP - flickerTimes[i]) / (flickerTimes[i + 1] - flickerTimes[i])
                        flickerScale = flickerValues[i] + (flickerValues[i + 1] - flickerValues[i]) * lp
                        break
                    }
                }
                flameScaleX = 1.35f
                flameScaleY = 0.65f * flickerScale
                flameIntensity = 0.5f + p * 0.5f
                if (t > 500f && t < 620f) shimmerPhase = (t - 500f) / 120f
                else shimmerPhase = -1f
            }
            t < 2400f -> {
                val p = (t - 1000f) / 1400f
                rocketScale = 1f; rocketAlpha = 1f
                // Fly off top of screen: travel from sourceRect to well above screen top
                val totalTravel = sourceRect.centerY() + rocketH * 2f
                rocketY = sourceRect.centerY() - totalTravel * p.pow(4f)
                shakeX = 0f
                // iOS: scaleX 1.35→0.75 in 0.35s (first 25% of launch)
                val scaleXP = (p / 0.25f).coerceAtMost(1f)
                flameScaleX = 1.35f - 0.6f * scaleXP
                // iOS: scaleY 0.65→1.75
                flameScaleY = 0.65f + 1.1f * p.coerceAtMost(1f)
                flameIntensity = 1f
                if (t > 1400f && t < 1520f) shimmerPhase = (t - 1400f) / 120f
                else shimmerPhase = -1f
            }
            t < 5200f -> {
                val p = ((t - 2400f) / 800f).coerceAtMost(1f)
                rocketAlpha = (1f - p).coerceAtLeast(0f)
                rocketY = -(rocketH * 2f)
                flameIntensity = (1f - p * 0.7f).coerceAtLeast(0f)
                flameScaleX = 0.75f; flameScaleY = 1.75f
            }
            else -> { rocketAlpha = 0f; flameIntensity = 0f }
        }
    }

    private fun emitEffects(t: Float, delta: Float) {
        val nozzleY = rocketY + rocketH / 2f + nozzleH

        // === Charging smoke (250-1000ms): intensity ramps to 1.5 ===
        if (t in 250f..1000f) {
            val p = (t - 250f) / 750f
            val intensity = p * 1.5f
            val count = (intensity * 2.5f * delta / 16f).toInt()
            if (count > 0) {
                smokeSys.emit(count, rocketX + shakeX, nozzleY,
                    40f + intensity * 40f, 100f + intensity * 60f, 55f, 125f,
                    14f, 30f + intensity * 10f, 600f, 1300f,
                    0xFFF8F8F8.toInt(), -20f, 2.5f, 0.3f)
            }
            if (Random.nextFloat() < p * 0.3f) {
                sparkSys.emit(2, rocketX + shakeX, nozzleY,
                    80f, 200f, 65f, 115f, 2f, 4f, 150f, 350f,
                    0xFFFF8D26.toInt(), 300f, 1f, 0.3f)
            }
        }

        // === Launch burst at 1000ms: blast force scatters charged smoke ===
        if (t >= 1000f && !hasBurst) {
            hasBurst = true
            val burstY = sourceRect.centerY() + rocketH * 0.35f
            // Horizontal blast (iOS: applyBlastAtNozzlePoint, 0.9s duration)
            burstSys.emit(28, rocketX, burstY, 140f, 380f, 160f, 200f,
                28f, 60f, 1800f, 3200f, 0xFFF8F8F8.toInt(), -15f, 1.6f, 0.2f)
            burstSys.emit(28, rocketX, burstY, 140f, 380f, -20f, 20f,
                28f, 60f, 1800f, 3200f, 0xFFF8F8F8.toInt(), -15f, 1.6f, 0.2f)
            burstSys.emit(12, rocketX, burstY, 60f, 160f, 250f, 290f,
                20f, 45f, 1400f, 2400f, 0xFFF5F5F5.toInt(), -30f, 2f, 0.25f)
        }

        // === Launch trail smoke (1000-3300ms): iOS intensity 1.9→1.3→0.55→0.10→stop ===
        if (t in 1000f..3300f) {
            val intensity = when {
                t < 1500f -> 1.9f  // peak
                t < 1900f -> 1.9f - (t - 1500f) / 400f * 0.6f  // →1.3
                t < 2200f -> 1.3f - (t - 1900f) / 300f * 0.75f  // →0.55
                t < 2700f -> 0.55f - (t - 2200f) / 500f * 0.45f  // →0.10
                else -> 0.1f * (1f - (t - 2700f) / 600f)  // fade to 0
            }.coerceAtLeast(0f)
            val count = (intensity * 2.5f * delta / 16f).toInt().coerceIn(0, 6)
            if (count > 0) {
                val smokeY = if (t < 2400f) nozzleY else sourceRect.centerY()
                smokeSys.emit(count, rocketX + shakeX * (if (t < 2400f) 1f else 0f), smokeY,
                    70f * intensity, 180f * intensity, 60f, 120f,
                    16f, 35f * intensity, 800f, 1600f,
                    0xFFF0F0F0.toInt(), -30f, 2f, 0.3f)
            }
            // Heat particles (orange, near nozzle)
            if (t < 1450f && Random.nextFloat() < 0.5f) {
                heatSys.emit(2, rocketX + shakeX + (Random.nextFloat() - 0.5f) * rocketW * 0.3f,
                    nozzleY, 100f, 250f, 60f, 120f, 5f, 11f, 200f, 450f,
                    0xFFFF8D26.toInt(), -50f, 1.2f, 0.25f)
            }
        }

        // === Sparkle stars (2000-2400ms): 4 golden cross stars along flight path ===
        if (t >= 2000f && !starsEmitted) {
            starsEmitted = true
            // Position stars in the upper-middle area of visible screen
            val topArea = viewHeight * 0.15f  // start from 15% from top
            val pathLen = viewHeight * 0.3f   // spread over 30% of screen
            val xRange = rocketW * 0.5f
            for (i in 0..3) {
                val fraction = (i + 1f) / 4f
                val starY = topArea + pathLen * (1f - fraction)
                val starX = rocketX + (Random.nextFloat() - 0.5f) * 2f * xRange
                stars.add(StarParticle(starX, starY, i * 80f))
            }
        }
    }

    private fun triggerHaptics(t: Float) {
        if (t > 300f && !hasVibratedLight) { hasVibratedLight = true; vibrate(25L, 60) }
        if (t > 1000f && !hasVibratedMedium) { hasVibratedMedium = true; vibrate(50L, 200) }
    }

    private fun vibrate(ms: Long, amp: Int) {
        try {
            val v = (context ?: return).getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, amp))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (_: Exception) {}
    }

    private fun drawBurstSmoke(canvas: Canvas) {
        burstSys.draw(canvas) { c, p, particle ->
            val g = (238 + Random.nextInt(17)).coerceAtMost(255)
            p.color = Color.argb((p.alpha * 0.7f).toInt(), g, g, g)
            c.drawCircle(0f, 0f, particle.size, p)
            c.drawCircle(0f, 0f, particle.size * 0.7f, p)
        }
    }
    private fun drawSmoke(canvas: Canvas) {
        smokeSys.draw(canvas) { c, p, particle ->
            val g = (242 + Random.nextInt(13)).coerceAtMost(255)
            p.color = Color.argb((p.alpha * 0.7f).toInt(), g, g, g)
            c.drawCircle(0f, 0f, particle.size, p)
            c.drawCircle(0f, 0f, particle.size * 0.6f, p)
        }
    }
    private fun drawHeat(canvas: Canvas) {
        heatSys.draw(canvas) { c, p, particle ->
            p.color = Color.argb(p.alpha, 255, (140 + Random.nextInt(60)).coerceAtMost(255), 38)
            c.drawCircle(0f, 0f, particle.size, p)
        }
    }

    private fun drawStars(canvas: Canvas, t: Float) {
        if (stars.isEmpty()) return
        val starTime = t - 2000f
        if (starTime < 0f) return

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        // iOS: RGB(1.0, 0.95, 0.6) = #FFF299
        starPaint.color = 0xFFFFF299.toInt()
        starPaint.strokeWidth = rocketW * 0.032f  // iOS: 2pt at 64pt width
        starPaint.strokeCap = Paint.Cap.ROUND
        starPaint.style = Paint.Style.STROKE

        for (star in stars) {
            val localT = starTime - star.delay
            if (localT < 0f) continue

            when {
                localT < 200f -> {
                    val p = localT / 200f
                    star.alpha = p
                    star.scale = 0.3f + 0.7f * p
                }
                localT < 300f -> { star.alpha = 1f; star.scale = 1f }
                localT < 700f -> {
                    val p = (localT - 300f) / 400f
                    star.alpha = 1f - p
                    star.scale = 1f - 0.9f * p
                }
                else -> { star.alpha = 0f }
            }
            if (star.alpha <= 0f) continue

            starPaint.alpha = (star.alpha * 255).toInt()
            // iOS: 22×22pt, relative to 64W = 34%
            val r = rocketW * 0.17f * star.scale

            canvas.save()
            canvas.translate(star.x, star.y)
            canvas.drawLine(-r, 0f, r, 0f, starPaint)
            canvas.drawLine(0f, -r, 0f, r, starPaint)
            // iOS: center white dot 4×4
            starPaint.style = Paint.Style.FILL
            starPaint.color = 0xFFFFFFFF.toInt()
            starPaint.alpha = (star.alpha * 255).toInt()
            canvas.drawCircle(0f, 0f, rocketW * 0.03f * star.scale, starPaint)
            starPaint.style = Paint.Style.STROKE
            starPaint.color = 0xFFFFF299.toInt()
            canvas.restore()
        }
    }

    // ============ ROCKET DRAWING (1:1 iOS layout) ============

    private fun drawRocket(canvas: Canvas, t: Float) {
        canvas.save()
        canvas.translate(rocketX + shakeX, rocketY)
        canvas.scale(rocketScale, rocketScale)
        val a = (rocketAlpha * 255).toInt()

        // Origin: rocket center. Top = -rocketH/2, Bottom = +rocketH/2
        val top = -rocketH / 2f
        val noseBot = top + noseH
        val bodyBot = noseBot + bodyH
        val nozzleBot = bodyBot + nozzleH

        drawFlame(canvas, a, nozzleBot)
        drawFins(canvas, a, bodyBot)
        drawNozzle(canvas, a, bodyBot)
        drawCenterFin(canvas, a, bodyBot, nozzleBot)
        drawBody(canvas, a, noseBot, bodyBot)
        drawNose(canvas, a, top, noseBot)
        drawPorthole(canvas, a, noseBot)
        drawOctoText(canvas, a, noseBot)
        drawBodyDetails(canvas, a, noseBot, bodyBot)

        canvas.restore()
    }

    private fun drawNose(canvas: Canvas, a: Int, top: Float, bot: Float) {
        val path = Path()
        path.moveTo(-hw + 1, bot)
        path.quadTo(-hw * 0.76f, top + noseH * 0.25f, 0f, top + 1)
        path.quadTo(hw * 0.76f, top + noseH * 0.25f, hw - 1, bot)
        path.close()

        rocketPaint.shader = LinearGradient(-hw, top, hw, bot,
            intArrayOf(0xFFFF7A6C.toInt(), 0xFFE74C3C.toInt(), 0xFFA83234.toInt()),
            floatArrayOf(0.05f, 0.45f, 0.95f), Shader.TileMode.CLAMP)
        rocketPaint.alpha = a; rocketPaint.style = Paint.Style.FILL
        canvas.drawPath(path, rocketPaint)
        rocketPaint.shader = null

        // Outline
        rocketPaint.color = 0xFFA83234.toInt(); rocketPaint.alpha = a
        rocketPaint.style = Paint.Style.STROKE; rocketPaint.strokeWidth = 1f
        canvas.drawPath(path, rocketPaint)
        rocketPaint.style = Paint.Style.FILL

        // Top shine
        rocketPaint.color = 0x88FFFFFF.toInt(); rocketPaint.alpha = (a * 0.55f).toInt()
        canvas.drawCircle(-hw * 0.24f, top + noseH * 0.4f, rocketW * 0.04f, rocketPaint)
    }

    private fun drawBody(canvas: Canvas, a: Int, top: Float, bot: Float) {
        val rect = RectF(-hw + 1, top, hw - 1, bot)
        // iOS horizontal gradient: cyan → silver → purple
        rocketPaint.shader = LinearGradient(-hw, 0f, hw, 0f,
            intArrayOf(0xFF5CD0FA.toInt(), 0xFFEEF4FA.toInt(), 0xFFD3DDEB.toInt(), 0xFFBE8AE8.toInt()),
            floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
        rocketPaint.alpha = a; rocketPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, cornerR, cornerR, rocketPaint)
        rocketPaint.shader = null

        // Orange cartoon outline
        rocketPaint.color = 0xFFFF9A3B.toInt(); rocketPaint.alpha = a
        rocketPaint.style = Paint.Style.STROKE; rocketPaint.strokeWidth = rocketW * 0.013f
        canvas.drawRoundRect(rect, cornerR, cornerR, rocketPaint)
        rocketPaint.style = Paint.Style.FILL

        // Vertical shine
        rocketPaint.color = 0x8CFFFFFF.toInt()
        canvas.drawRect(-hw * 0.56f, top + bodyH * 0.05f, -hw * 0.46f, bot - bodyH * 0.05f, rocketPaint)
        rocketPaint.color = 0x38FFFFFF
        canvas.drawRect(-hw * 0.3f, top + bodyH * 0.05f, -hw * 0.25f, bot - bodyH * 0.05f, rocketPaint)
    }

    private fun drawNozzle(canvas: Canvas, a: Int, bodyBot: Float) {
        // iOS: trapezoid, top narrower(55%), bottom wider(70%)
        val topInset = rocketW * (1f - 0.55f) / 2f
        val botInset = rocketW * (1f - 0.70f) / 2f
        val path = Path()
        path.moveTo(-hw + topInset, bodyBot)
        path.lineTo(hw - topInset, bodyBot)
        path.lineTo(hw - botInset, bodyBot + nozzleH)
        path.lineTo(-hw + botInset, bodyBot + nozzleH)
        path.close()

        rocketPaint.color = 0xFFE87429.toInt(); rocketPaint.alpha = a
        rocketPaint.style = Paint.Style.FILL
        canvas.drawPath(path, rocketPaint)
        // Dark stroke
        rocketPaint.color = 0x59000000; rocketPaint.style = Paint.Style.STROKE
        rocketPaint.strokeWidth = 0.8f; canvas.drawPath(path, rocketPaint)
        rocketPaint.style = Paint.Style.FILL
    }

    private fun drawFins(canvas: Canvas, a: Int, bodyBot: Float) {
        val topTuck = rocketW * 0.125f  // 8pt at iOS scale
        val finTop = bodyBot - finH + topTuck

        // Left fin
        val lPath = Path()
        val ltx = -hw + rocketW * 0.08f  // top attach x
        val lox = -hw - finOutW + rocketW * 0.05f  // outer tip x
        val lby = bodyBot - rocketW * 0.03f  // bottom attach y
        lPath.moveTo(ltx, finTop)
        lPath.quadTo(ltx - finOutW * 0.95f, finTop + finH * 0.35f, lox, lby)
        lPath.quadTo(lox + finOutW * 0.25f, lby + rocketW * 0.03f, -hw + rocketW * 0.03f, bodyBot - rocketW * 0.015f)
        lPath.quadTo(ltx + rocketW * 0.05f, finTop + finH * 0.55f, ltx, finTop)
        lPath.close()

        rocketPaint.shader = LinearGradient(-hw - finOutW, finTop, -hw, bodyBot,
            intArrayOf(0xFFBE8AE8.toInt(), 0xFF8A5CD6.toInt(), 0xFFA83234.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        rocketPaint.alpha = a; rocketPaint.style = Paint.Style.FILL
        canvas.drawPath(lPath, rocketPaint)
        rocketPaint.shader = null
        rocketPaint.color = 0xFFFF9A3B.toInt(); rocketPaint.alpha = a
        rocketPaint.style = Paint.Style.STROKE; rocketPaint.strokeWidth = rocketW * 0.017f
        canvas.drawPath(lPath, rocketPaint); rocketPaint.style = Paint.Style.FILL

        // Right fin (mirror)
        val rPath = Path()
        val rtx = hw - rocketW * 0.08f
        val rox = hw + finOutW - rocketW * 0.05f
        rPath.moveTo(rtx, finTop)
        rPath.quadTo(rtx + finOutW * 0.95f, finTop + finH * 0.35f, rox, lby)
        rPath.quadTo(rox - finOutW * 0.25f, lby + rocketW * 0.03f, hw - rocketW * 0.03f, bodyBot - rocketW * 0.015f)
        rPath.quadTo(rtx - rocketW * 0.05f, finTop + finH * 0.55f, rtx, finTop)
        rPath.close()

        rocketPaint.shader = LinearGradient(hw, finTop, hw + finOutW, bodyBot,
            intArrayOf(0xFF8A5CD6.toInt(), 0xFFBE8AE8.toInt(), 0xFFA83234.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        rocketPaint.alpha = a; rocketPaint.style = Paint.Style.FILL
        canvas.drawPath(rPath, rocketPaint)
        rocketPaint.shader = null
        rocketPaint.color = 0xFFFF9A3B.toInt(); rocketPaint.alpha = a
        rocketPaint.style = Paint.Style.STROKE; rocketPaint.strokeWidth = rocketW * 0.017f
        canvas.drawPath(rPath, rocketPaint); rocketPaint.style = Paint.Style.FILL
    }

    private fun drawCenterFin(canvas: Canvas, a: Int, bodyBot: Float, nozzleBot: Float) {
        // iOS: width=bodyWidth*0.28, from bodyBot-8pt to nozzleBot-2pt (extends below nozzle)
        val cTop = bodyBot - rocketH * 0.058f
        val cBot = nozzleBot + rocketH * 0.06f  // extend BELOW nozzle for visibility
        val chw = centerFinW / 2f
        val path = Path()
        // Simple inverted triangle with slight rounded tip
        path.moveTo(-chw, cTop)
        path.lineTo(chw, cTop)
        path.lineTo(0f, cBot)
        path.close()

        rocketPaint.shader = LinearGradient(0f, cTop, 0f, cBot,
            intArrayOf(0xFF49BFEB.toInt(), 0xFF8A5CD6.toInt()), null, Shader.TileMode.CLAMP)
        rocketPaint.alpha = a; rocketPaint.style = Paint.Style.FILL
        canvas.drawPath(path, rocketPaint)
        rocketPaint.shader = null
        rocketPaint.color = 0xFFFF9A3B.toInt(); rocketPaint.alpha = a
        rocketPaint.style = Paint.Style.STROKE; rocketPaint.strokeWidth = rocketW * 0.016f
        rocketPaint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, rocketPaint); rocketPaint.style = Paint.Style.FILL
    }

    private fun drawPorthole(canvas: Canvas, a: Int, bodyTop: Float) {
        val py = bodyTop + portholeR + rocketW * 0.06f
        // Glass
        rocketPaint.color = 0xFF4AA5FF.toInt(); rocketPaint.alpha = a
        canvas.drawCircle(0f, py, portholeR, rocketPaint)
        // Purple frame
        rocketPaint.color = 0xFF8A5CD6.toInt(); rocketPaint.alpha = a
        rocketPaint.style = Paint.Style.STROKE; rocketPaint.strokeWidth = rocketW * 0.04f
        canvas.drawCircle(0f, py, portholeR, rocketPaint); rocketPaint.style = Paint.Style.FILL
        // Avatar
        val avatar = circularAvatar
        if (avatar != null) {
            val ap = Paint(Paint.ANTI_ALIAS_FLAG); ap.alpha = (a * 0.78f).toInt()
            val ar = portholeR * 0.82f
            canvas.drawBitmap(avatar, null, RectF(-ar, py - ar, ar, py + ar), ap)
            rocketPaint.color = 0x604AA5FF; canvas.drawCircle(0f, py, portholeR * 0.8f, rocketPaint)
        }
        // Highlight
        rocketPaint.color = 0x66FFFFFF
        canvas.drawCircle(-portholeR * 0.28f, py - portholeR * 0.28f, portholeR * 0.3f, rocketPaint)
        // Screws
        rocketPaint.color = 0xFF8A5CD6.toInt(); rocketPaint.alpha = a
        val sd = portholeR + rocketW * 0.035f; val sr = rocketW * 0.017f
        canvas.drawCircle(-sd * 0.7f, py - sd * 0.7f, sr, rocketPaint)
        canvas.drawCircle(sd * 0.7f, py - sd * 0.7f, sr, rocketPaint)
        canvas.drawCircle(-sd * 0.7f, py + sd * 0.7f, sr, rocketPaint)
        canvas.drawCircle(sd * 0.7f, py + sd * 0.7f, sr, rocketPaint)
        // Shimmer
        if (shimmerPhase in 0f..1f) {
            val sx = -portholeR + shimmerPhase * portholeR * 2f
            rocketPaint.shader = LinearGradient(sx - portholeR * 0.3f, py, sx + portholeR * 0.3f, py,
                intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.save(); canvas.clipPath(Path().apply { addCircle(0f, py, portholeR, Path.Direction.CW) })
            canvas.drawRect(-portholeR, py - portholeR, portholeR, py + portholeR, rocketPaint)
            canvas.restore(); rocketPaint.shader = null
        }
    }

    private fun drawOctoText(canvas: Canvas, a: Int, bodyTop: Float) {
        val py = bodyTop + portholeR + rocketW * 0.06f
        val ty = py + portholeR + rocketW * 0.22f
        textPaint.textSize = rocketW * 0.15f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER; textPaint.isFakeBoldText = true
        // Shadow
        textPaint.color = 0x80000000.toInt(); textPaint.alpha = (a * 0.4f).toInt()
        canvas.drawText("Octo", 0.5f, ty + 0.5f, textPaint)
        // Gradient text
        textPaint.shader = LinearGradient(0f, ty - textPaint.textSize, 0f, ty,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF8A5CD6.toInt(), 0xFF4A148C.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        textPaint.alpha = a; canvas.drawText("Octo", 0f, ty, textPaint); textPaint.shader = null

        // "2718" 数字标识 — 机身下半部分，字号稍大
        val numY = ty + rocketW * 0.28f
        textPaint.textSize = rocketW * 0.18f
        textPaint.color = 0x80000000.toInt(); textPaint.alpha = (a * 0.4f).toInt()
        canvas.drawText("2718", 0.5f, numY + 0.5f, textPaint)
        textPaint.shader = LinearGradient(0f, numY - textPaint.textSize, 0f, numY,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF8A5CD6.toInt(), 0xFF4A148C.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        textPaint.alpha = a; canvas.drawText("2718", 0f, numY, textPaint); textPaint.shader = null
    }

    private fun drawBodyDetails(canvas: Canvas, a: Int, bodyTop: Float, bodyBot: Float) {
        // Seam line
        val seamY = bodyBot - rocketW * 0.41f
        rocketPaint.color = 0x618A5CD6; rocketPaint.alpha = (a * 0.38f).toInt()
        rocketPaint.style = Paint.Style.STROKE; rocketPaint.strokeWidth = rocketW * 0.011f
        canvas.drawLine(-hw * 0.88f, seamY, hw * 0.88f, seamY, rocketPaint)
        rocketPaint.style = Paint.Style.FILL
        // Rivets
        rocketPaint.color = 0xFF8A5CD6.toInt(); rocketPaint.alpha = (a * 0.7f).toInt()
        val rr = rocketW * 0.017f
        for (i in 0..2) {
            val ry = bodyTop + bodyH * (0.18f + i * 0.18f)
            canvas.drawCircle(-hw * 0.75f, ry, rr, rocketPaint)
            canvas.drawCircle(hw * 0.75f, ry, rr, rocketPaint)
        }
    }

    private fun drawFlame(canvas: Canvas, a: Int, nozzleBot: Float) {
        if (flameIntensity <= 0f) return
        val fw = flameW * flameScaleX
        val fh = flameH * flameScaleY

        canvas.save()
        canvas.translate(0f, nozzleBot)

        // Glow (radial gradient: center purple → transparent)
        val glowCenterY = fh * 0.15f
        rocketPaint.shader = RadialGradient(
            0f, glowCenterY, fw * 1.2f,
            intArrayOf(
                Color.argb((60 * flameIntensity).toInt(), 140, 115, 255),
                Color.argb(0, 140, 115, 255)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        rocketPaint.alpha = a
        canvas.drawOval(RectF(-fw * 1.3f, glowCenterY - fw * 0.8f, fw * 1.3f, glowCenterY + fh * 0.4f), rocketPaint)
        rocketPaint.shader = null

        // Outer flame (orange)
        val outer = makeTeardrop(fw / 2f, fh)
        rocketPaint.shader = LinearGradient(0f, 0f, 0f, fh,
            intArrayOf(0xFFFF8D26.toInt(), 0xFFFF6D00.toInt(), 0x00FF6D00.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        rocketPaint.alpha = (a * flameIntensity).toInt()
        canvas.drawPath(outer, rocketPaint)

        // Inner flame (yellow)
        val iw = fw * 0.55f; val ih = fh * 0.8f
        val inner = makeTeardrop(iw / 2f, ih)
        rocketPaint.shader = LinearGradient(0f, fh * 0.04f, 0f, fh * 0.04f + ih,
            intArrayOf(0xFFFFD940.toInt(), 0xFFFF8D26.toInt(), 0x00FF8D26.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.save(); canvas.translate(0f, fh * 0.04f)
        canvas.drawPath(inner, rocketPaint); canvas.restore()

        // Hot core (white)
        val cw = iw * 0.55f; val ch = ih * 0.45f
        rocketPaint.shader = LinearGradient(0f, 0f, 0f, ch,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF9C4.toInt(), 0x00FFF9C4.toInt()),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(-cw / 2f, fh * 0.02f, cw / 2f, fh * 0.02f + ch), cw * 0.3f, cw * 0.3f, rocketPaint)
        rocketPaint.shader = null

        canvas.restore()
    }

    private fun makeTeardrop(halfW: Float, h: Float): Path {
        // iOS: WIDE at top (nozzle), tapers to POINT at bottom
        // iOS coords: moveTo(0,0) quadTo(W*0.08, H*0.6, W/2, H) quadTo(W*0.92, H*0.6, W, 0)
        // Centered: top-left=(-halfW,0), bottom-point=(0,H)
        // Controls at (-halfW*0.84, H*0.6) and (halfW*0.84, H*0.6)
        val p = Path()
        p.moveTo(-halfW, 0f)
        p.quadTo(-halfW * 0.84f, h * 0.6f, 0f, h)
        p.quadTo(halfW * 0.84f, h * 0.6f, halfW, 0f)
        p.close()
        return p
    }

    override fun onEnd() {
        smokeSys.clear(); sparkSys.clear(); heatSys.clear(); burstSys.clear()
        circularAvatar?.recycle(); circularAvatar = null
    }

    private fun overshoot(t: Float, amount: Float): Float {
        val t2 = t - 1f; return t2 * t2 * ((amount + 1f) * t2 + amount) + 1f
    }
}
