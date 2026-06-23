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

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.chat.base.msgeffect.MessageEffectType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 🎉 / 🎊 庆祝特效 —— 与 iOS WKConfettiView.swift 1:1 对齐的全屏礼花演出。
 *
 * 时间线（毫秒，相对 onStart）：
 *
 *   t=0        顶部出现彩纸球（drop-in 起始：alpha 0、scale 0.3、y 偏移 -20px）
 *   t=0–200    spring drop-in（带阻尼）→ alpha 1, scale 1, 落位
 *   t=250–350  蓄势：scale 1 → 1.08
 *   t=350      ★ 爆开：球身 0.12s 放大到 1.4 + alpha→0；闪光圆 0.05s 极亮 →
 *              0.20s 扩散淡出；白色冲击环 0.40s scale 0.3→3.5 + opacity 0.9→0
 *   t=350–430  粒子集中喷射（瞬发期），随后停止生成
 *   t=350–~6500 已生成粒子按真物理演化：初速度 + 重力渐升 → 抛物线下落
 *   t=6500     特效自然收尾（粒子全部超出屏幕或淡出）
 *   t=10000    MessageEffectType.Confetti.durationMs 到期，外层移除本特效
 *
 * 与 iOS 的差异 / 取舍：
 *   - iOS 用 UIView 容器 + 多层 CALayer + CAEmitterLayer。Android 这边运行
 *     在 BaseEffect 的 Canvas 抽象里，统一走 2D 矢量绘制 + 物理积分。
 *   - 球身（shell + 内部 120 片彩纸 + 蝴蝶结 + 飘带）在 onStart 时一次性
 *     绘到一张 Bitmap 上，每帧只用矩阵变换把它 blit 出去——避免每帧重画
 *     120 个子元素的 CPU 开销。
 *   - 粒子物理参数完全照抄 iOS（initialVelocity 260, velocityRange 110,
 *     yAcceleration keyframe 0→200→550→700, scaleSpeed -0.04, alphaSpeed
 *     -0.18），保证抛物线手感一致。
 *
 * 严格遵守 brief 的「视觉/听觉边界」：
 *   - 本类不触碰任何业务逻辑（消息收发、存储、cell 渲染、多端同步）。
 *   - 不引入外部动画框架——只用 Canvas + 原生 MediaPlayer。
 *   - 音频通过 [ConfettiAudio] 跟随系统媒体音量；为 0 时不响。
 *   - onEnd 必须释放 audio，否则会话退出后会有声音泄露。
 */
class ConfettiEffect(
    type: MessageEffectType,
    sourceRect: RectF,
    viewWidth: Int,
    viewHeight: Int,
) : BaseEffect(type, sourceRect, viewWidth, viewHeight) {

    // ---------------------------------------------------------------------- //
    // Timing constants (ms, relative to onStart) — 与 iOS 1:1 对齐
    // ---------------------------------------------------------------------- //

    private companion object {
        const val DROP_IN_DURATION = 200L
        const val WINDUP_START = 250L
        const val WINDUP_DURATION = 100L
        const val BURST_AT = 350L
        const val BALL_FADE_DURATION = 120L
        const val FLASH_PHASE_1 = 50L
        const val FLASH_PHASE_2 = 200L
        const val RING_DURATION = 400L
        const val PARTICLE_CLEANUP_AT = 6500L
        // iOS 数学值 = 18 cells × birthRate 85/s × 0.08s ≈ 122 颗。但 iOS 用
        // CAEmitterLayer 走 Metal 硬件粒子渲染 + 染色白纹理（边缘 anti-alias
        // 自带光晕），单颗粒子辨识度很高。Android Canvas 软件填充硬边界，
        // 同样数学值视觉密度大约只有 iOS 的 1/3。实测对比 iOS 截图后，把数量
        // 翻到 400 才能让主观「散出去的花片密度」跟 iOS 持平。Choreographer
        // 60fps 下 400 颗矩形/星/三角的 Canvas 开销可忽略。
        const val TOTAL_PARTICLES = 500

        /**
         * 粒子视觉补偿系数。iOS CAEmitterLayer 用染色白纹理（自带 alpha 边缘
         * 渐变 → 视觉上比硬边界大），实测单颗目测尺寸约是数学计算尺寸的 2-3 倍。
         * Android Canvas 软件 fill 没这 halo 效果，得手动放大到匹配 iOS 实际
         * 显示尺寸，否则粒子小得几乎看不见（iOS ~20 px / Android 数学算 ~6-9 px）。
         */
        const val PARTICLE_VISUAL_BOOST = 1.9f

        // 与 iOS WKConfettiView.ballPalette 一一对应
        val PALETTE = intArrayOf(
            0xFFFFC75C.toInt(), // 黄
            0xFF7AC8A4.toInt(), // 绿
            0xFF4CC2D9.toInt(), // 青
            0xFF9463BF.toInt(), // 紫
            0xFFF3D933.toInt(), // 金
            0xFFF8455B.toInt(), // 粉红
        )

        const val BALL_SHELL_COLOR = 0x99ED403C.toInt()   // alpha 0.60 红
        const val BALL_BORDER_COLOR = 0x8CFFFFFF.toInt()  // alpha 0.55 白
        const val BALL_BOTTOM_SHADOW = 0x59650D14.toInt() // alpha 0.35 深红
        const val BOW_RED = 0xFFF23348.toInt()
        const val BOW_DARK = 0xFF8C0D1A.toInt()
        const val BOW_SHINE = 0x8CFFFFFF.toInt()           // alpha 0.55 白高光
        const val RIBBON_GOLD = 0xF2FFD633.toInt()         // alpha 0.95 金色
    }

    // ---------------------------------------------------------------------- //
    // Geometry — 全部按 iOS pt 值 × density 计算，不再用 viewWidth/viewHeight
    // 的百分比，这样在任何尺寸 Android 设备上都能跟 iOS 1:1 视觉对齐。
    // ---------------------------------------------------------------------- //

    /**
     * 像素密度（px / dp）。iOS 粒子 / 球 / 闪光 / 冲击环参数都以 pt 为单位
     * （iPhone 在 density 3 上 1 pt = 3 px），我们这边以 px 工作，所以速度 /
     * 重力 / 几何尺寸都得乘这个系数才能在不同密度 Android 设备上跟 iOS 对齐。
     */
    private val density: Float = Resources.getSystem().displayMetrics.density
        .coerceIn(1.5f, 4.5f)

    /** iOS `ballDiameter: CGFloat = 64`。 */
    private val ballDiameter: Float = 64f * density

    /** iOS `extraTop: CGFloat = 28` —— 蝴蝶结所占额外顶部空间。 */
    private val bowExtraTop: Float = 28f * density

    /**
     * 球心位置：水平居中，垂直位置按 overlay 高度比例放在上方 16% 处。
     *
     * 为什么不直接对齐 iOS 的 `80 * density`：
     * iOS WKMessageEffectView 覆盖整个屏幕（包括状态栏和导航栏），所以 80pt
     * 是「从屏幕物理顶部 80pt」。Android 这边 [MessageEffectOverlayView] 被
     * `ChatActivity.java:783` 加到 `android.R.id.content`，**起点在 Toolbar
     * 下方**——如果照搬 80 * density 会让球出现在 Toolbar 下方一点点，
     * 视觉上比 iOS 高一截、紧贴 Toolbar 不自然。
     *
     * 用 viewHeight × 0.16 跟 overlay 实际高度走，能在不同手机上稳定停在
     * 内容区顶部偏上的位置（约会话顶 1/6 处），跟 iOS 在物理屏幕上的相对
     * 高度感比较接近。
     */
    private val ballCenterX: Float = viewWidth / 2f
    private val ballCenterY: Float = (viewHeight * 0.16f)
        .coerceAtLeast(ballDiameter * 0.5f + bowExtraTop + 16f * density)

    // ---------------------------------------------------------------------- //
    // Pre-rendered ball bitmap（onStart 时一次性烘焙，后续帧只 blit + 变换）
    // ---------------------------------------------------------------------- //

    private var ballBitmap: Bitmap? = null
    /** ball bitmap 的中心相对位置（球心在 bitmap 内的坐标）。 */
    private var ballAnchorX: Float = 0f
    private var ballAnchorY: Float = 0f

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val matrix = Matrix()

    // ---------------------------------------------------------------------- //
    // Particles
    // ---------------------------------------------------------------------- //

    private enum class Shape { RECT, STAR, TRIANGLE }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var scale: Float,
        var alpha: Float,
        val shape: Shape,
        val color: Int,
    )

    private val particles = ArrayList<Particle>(TOTAL_PARTICLES)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPath = Path()
    private val trianglePath = Path()
    private val rectRectF = RectF()

    // ---------------------------------------------------------------------- //
    // Audio
    // ---------------------------------------------------------------------- //

    private val audio = ConfettiAudio()
    private var burstSoundFired = false

    // ---------------------------------------------------------------------- //
    // Lifecycle
    // ---------------------------------------------------------------------- //

    override fun onStart() {
        ballBitmap = bakeBallBitmap()
        spawnParticles()
        // 入场音不在 onStart 立即播——iOS 是在爆开瞬间响。这里只是预先 reset。
    }

    override fun onEnd() {
        audio.release()
        ballBitmap?.recycle()
        ballBitmap = null
        particles.clear()
    }

    override fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        // 在 BURST 瞬间触发音效（先视觉爆开一帧再响声，体感更对齐）。
        if (!burstSoundFired && elapsedMs >= BURST_AT) {
            burstSoundFired = true
            audio.play()
        }

        // 球身阶段：drop-in → 蓄势 → 爆开瞬间放大消失
        val ballPhaseEnd = BURST_AT + BALL_FADE_DURATION
        if (elapsedMs < ballPhaseEnd) {
            drawBall(canvas, elapsedMs)
        }

        // 爆开后视觉元素
        if (elapsedMs >= BURST_AT) {
            val burstT = elapsedMs - BURST_AT
            if (burstT < FLASH_PHASE_1 + FLASH_PHASE_2) drawFlash(canvas, burstT)
            if (burstT < RING_DURATION) drawShockRing(canvas, burstT)
            updateAndDrawParticles(canvas, elapsedMs, deltaMs)
        }
    }

    // ---------------------------------------------------------------------- //
    // Ball drop-in / windup / burst-fade
    // ---------------------------------------------------------------------- //

    private fun drawBall(canvas: Canvas, t: Long) {
        val bmp = ballBitmap ?: return

        val scale: Float
        val alpha: Float
        val yOffset: Float

        when {
            t < DROP_IN_DURATION -> {
                // 0.62 阻尼的 spring drop-in（近似——纯几何 ease-out 加一点反弹）
                val p = (t.toFloat() / DROP_IN_DURATION).coerceIn(0f, 1f)
                val springed = springOut(p, damping = 0.62f)
                scale = lerp(0.30f, 1f, springed)
                alpha = p.coerceIn(0f, 1f)
                yOffset = lerp(-20f, 0f, springed)
            }
            t < WINDUP_START -> {
                scale = 1f
                alpha = 1f
                yOffset = 0f
            }
            t < BURST_AT -> {
                val p = ((t - WINDUP_START).toFloat() / WINDUP_DURATION).coerceIn(0f, 1f)
                val eased = easeInOut(p)
                scale = lerp(1f, 1.08f, eased)
                alpha = 1f
                yOffset = 0f
            }
            else -> {
                // 爆开瞬间：球身放大到 1.4 + alpha → 0
                val p = ((t - BURST_AT).toFloat() / BALL_FADE_DURATION).coerceIn(0f, 1f)
                val eased = easeOut(p)
                scale = lerp(1.08f, 1.4f, eased)
                alpha = 1f - eased
                yOffset = 0f
            }
        }

        matrix.reset()
        matrix.postTranslate(-ballAnchorX, -ballAnchorY)
        matrix.postScale(scale, scale)
        matrix.postTranslate(ballCenterX, ballCenterY + yOffset)

        ballPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, matrix, ballPaint)
    }

    // ---------------------------------------------------------------------- //
    // Burst visuals
    // ---------------------------------------------------------------------- //

    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun drawFlash(canvas: Canvas, burstT: Long) {
        // iOS `flashSize: CGFloat = 90` — 闪光圆的最终直径（在 0.05s 内从 0.30 倍冲到 1.0 倍）
        val flashDiameter = 90f * density
        val (scale, alpha) = if (burstT < FLASH_PHASE_1) {
            val p = burstT.toFloat() / FLASH_PHASE_1
            val eased = easeOut(p)
            lerp(0.30f, 1f, eased) to eased
        } else {
            val p = ((burstT - FLASH_PHASE_1).toFloat() / FLASH_PHASE_2).coerceIn(0f, 1f)
            val eased = easeIn(p)
            lerp(1f, 2f, eased) to (1f - eased)
        }

        flashPaint.color = Color.WHITE
        flashPaint.alpha = (alpha * 217f).toInt().coerceIn(0, 255) // 0.85 base
        canvas.drawCircle(ballCenterX, ballCenterY, flashDiameter * scale / 2f, flashPaint)
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // 用 coerced 后的 density 字段（[1.5, 4.5] 区间），跟其它几何参数对齐——
        // 极端密度设备上线宽不会跟粒子尺寸脱节。
        strokeWidth = 2f * density
        color = Color.WHITE
    }

    private fun drawShockRing(canvas: Canvas, burstT: Long) {
        // iOS `ringInitialSize: CGFloat = 60` — 冲击环初始直径，scale 0.3 → 3.5
        val ringInitialDiameter = 60f * density
        val p = (burstT.toFloat() / RING_DURATION).coerceIn(0f, 1f)
        val eased = easeOut(p)
        val scale = lerp(0.30f, 3.5f, eased)
        val alpha = lerp(0.90f, 0f, eased)

        ringPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(ballCenterX, ballCenterY, ringInitialDiameter * scale / 2f, ringPaint)
    }

    // ---------------------------------------------------------------------- //
    // Particles — pre-computed once, updated per frame
    // ---------------------------------------------------------------------- //

    private fun spawnParticles() {
        val random = Random(0x1F389)  // 固定 seed → 视觉稳定，便于回归对比
        val shapes = Shape.values()
        repeat(TOTAL_PARTICLES) { idx ->
            // 中心方向 -π/2（向上），但 emissionRange 整 360° → 各方向随机
            val angle = random.nextDouble(0.0, 2.0 * PI) - PI / 2.0
            // iOS: velocity = 260 pt/s, velocityRange = 110 → [150, 370] pt/s
            // 乘 density 换算到 px/s（密度 3 时 = 450–1110 px/s，跨屏可达 1/3–1 屏）
            val speedPxPerSec = (260f + (random.nextFloat() - 0.5f) * 220f) * density
            val vx = (cos(angle) * speedPxPerSec).toFloat()
            val vy = (sin(angle) * speedPxPerSec).toFloat()

            particles += Particle(
                x = ballCenterX,
                y = ballCenterY,
                vx = vx,
                vy = vy,
                rotation = random.nextFloat() * 360f,
                // iOS: cell.spin = 4 rad/s, spinRange = 8 → 实际自旋 4 ± 8 = -4 到 12 rad/s
                // 之前误把 rad 当"圈"再乘 360°，导致粒子转得比 iOS 快 6-19 倍 → 视觉上
                // 是高速模糊的色块，单颗形状辨识不出来，主观觉得"少且乱"。这里直接
                // 按 rad/s 算 ± range，再用 (180/π) 换成 deg/s 给 canvas.rotate 用。
                rotationSpeed = (4f + (random.nextFloat() - 0.5f) * 16f) * (180f / PI.toFloat()),
                // iOS: scale = 0.28, scaleRange = 0.10 → [0.18, 0.38]（两侧对称，不是 +only）
                // density 在 drawParticle 里再叠加，这里保持无量纲
                scale = 0.28f + (random.nextFloat() - 0.5f) * 0.20f,
                alpha = 1f,
                // ⚠️ 所有粒子都在 t=0（爆开瞬间）一次性出现在中心。原本想用 spawnDelay
                // 散布在 0-80ms 里来模拟 iOS CAEmitterLayer 的连续发射，但 60fps 下"连续"
                // 被采样成离散帧 → 第 1 帧只有 5-10 颗出生在中心、立刻散开 → 中心永远
                // 是"刚生 2-3 颗 + 飞走的几十颗"的稀疏感。iOS CAEmitterLayer 在硬件层
                // 是真连续 + 染色白纹理晕开 → 中心一直被花片填满。Android 模拟不了
                // 那种真连续渲染，**一次性全爆**是最贴近 iOS"从中心爆出来"体感的近似。
                shape = shapes[idx % shapes.size],
                color = PALETTE[idx % PALETTE.size],
            )
        }
    }

    /**
     * iOS 重力 keyframe (yAcceleration 单位 pt/s²，duration=4.5s):
     *   t=0           0
     *   t=0.02 * 4.5  200
     *   t=0.20 * 4.5  550
     *   t=1.00 * 4.5  700
     */
    private fun gravityAt(secSinceBurst: Float): Float = when {
        secSinceBurst <= 0f -> 0f
        secSinceBurst <= 0.09f -> lerp(0f, 200f, secSinceBurst / 0.09f)
        secSinceBurst <= 0.90f -> lerp(200f, 550f, (secSinceBurst - 0.09f) / 0.81f)
        secSinceBurst <= 4.50f -> lerp(550f, 700f, (secSinceBurst - 0.90f) / 3.60f)
        else -> 700f
    }

    private fun updateAndDrawParticles(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        val burstT = (elapsedMs - BURST_AT).coerceAtLeast(0L)
        val dt = deltaMs.coerceIn(1, 50).toFloat() / 1000f
        // iOS gravity 也是 pt/s²，需要乘 density 换算到 px/s²
        val gravity = gravityAt(burstT / 1000f) * density

        for (p in particles) {
            // 物理积分（所有粒子从 t=0 起就在中心存在，无 spawnDelay 概念）
            p.vy += gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.rotationSpeed * dt

            // alpha / scale 渐变（与 iOS alphaSpeed -0.18, scaleSpeed -0.04 对齐）
            p.alpha = (p.alpha - 0.18f * dt).coerceAtLeast(0f)
            p.scale = (p.scale - 0.04f * dt).coerceAtLeast(0f)

            // 长尾淡出：超过 PARTICLE_CLEANUP_AT 后 1s 内压平
            if (elapsedMs > PARTICLE_CLEANUP_AT) {
                val fadeP = ((elapsedMs - PARTICLE_CLEANUP_AT).toFloat() / 1000f).coerceIn(0f, 1f)
                p.alpha = min(p.alpha, 1f - fadeP)
            }
        }

        for (p in particles) {
            if (p.alpha <= 0.01f || p.scale <= 0.01f) continue
            if (p.y > viewHeight + 60f) continue
            drawParticle(canvas, p)
        }
    }

    private fun drawParticle(canvas: Canvas, p: Particle) {
        particlePaint.color = p.color
        particlePaint.alpha = (p.alpha * 255f).toInt().coerceIn(0, 255)
        particlePaint.style = Paint.Style.FILL

        val save = canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.rotation)
        // iOS: 形状纹理基准 14pt × cell.scale(0.18-0.38) → 数学尺寸 2.5-5.3pt
        // Android: 用 dp 当 pt 等价物，× density 换算到像素，× p.scale × 视觉
        // 补偿系数（PARTICLE_VISUAL_BOOST）才是最终缩放。补偿系数是因为 iOS
        // CAEmitterLayer 染色白纹理自带 alpha 边缘光晕，单颗视觉尺寸约是数学
        // 值的 2-3 倍；Android Canvas 硬边界没这效果，得手动放大。
        val effectiveScale = p.scale * density * PARTICLE_VISUAL_BOOST
        canvas.scale(effectiveScale, effectiveScale)

        when (p.shape) {
            Shape.RECT -> {
                // iOS rectParticleImage = 8×14pt（参见 WKConfettiView.rectParticleImage）
                rectRectF.set(-4f, -7f, 4f, 7f)
                canvas.drawRoundRect(rectRectF, 1.5f, 1.5f, particlePaint)
            }
            Shape.STAR -> {
                // iOS starParticleImage = 14×14pt, outer=6.5, inner=2.7
                buildStarPath(starPath, cx = 0f, cy = 0f, outer = 6.5f, inner = 2.7f)
                canvas.drawPath(starPath, particlePaint)
            }
            Shape.TRIANGLE -> {
                // iOS triangleParticleImage = 12×11pt, vertices (6,1)(11,10)(1,10) → 居中后 (0,-5)(5,5)(-5,5)
                trianglePath.reset()
                trianglePath.moveTo(0f, -5f)
                trianglePath.lineTo(5f, 5f)
                trianglePath.lineTo(-5f, 5f)
                trianglePath.close()
                canvas.drawPath(trianglePath, particlePaint)
            }
        }
        canvas.restoreToCount(save)
    }

    // ---------------------------------------------------------------------- //
    // Ball bitmap baking — runs once at onStart
    // ---------------------------------------------------------------------- //

    private fun bakeBallBitmap(): Bitmap {
        val w = ballDiameter.toInt().coerceAtLeast(1)
        val h = (ballDiameter + bowExtraTop).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val shellLeft = 0f
        val shellTop = bowExtraTop
        val shellRect = RectF(shellLeft, shellTop, shellLeft + ballDiameter, shellTop + ballDiameter)
        val shellCx = shellRect.centerX()
        val shellCy = shellRect.centerY()
        val shellR = ballDiameter / 2f

        ballAnchorX = shellCx
        ballAnchorY = shellCy

        drawBallShell(canvas, shellRect)
        drawBallBottomShadow(canvas, shellRect, shellR)
        drawBallInnerPieces(canvas, shellCx, shellCy, shellR)
        drawBallHighlight(canvas, shellRect)
        drawBallBorder(canvas, shellRect)
        drawBowAndRibbons(canvas, shellCx, bowCenterY = shellTop)

        return bmp
    }

    private fun drawBallShell(canvas: Canvas, shellRect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = BALL_SHELL_COLOR
        canvas.drawOval(shellRect, paint)
    }

    private fun drawBallBottomShadow(canvas: Canvas, shellRect: RectF, shellR: Float) {
        val save = canvas.save()
        val clip = Path().apply { addOval(shellRect, Path.Direction.CW) }
        canvas.clipPath(clip)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = BALL_BOTTOM_SHADOW
        val shadowRect = RectF(
            shellRect.left,
            shellRect.top + shellR * 1.1f,
            shellRect.right,
            shellRect.bottom,
        )
        canvas.drawOval(shadowRect, paint)
        canvas.restoreToCount(save)
    }

    private fun drawBallInnerPieces(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val save = canvas.save()
        val clip = Path().apply {
            addCircle(cx, cy, radius - 1.5f, Path.Direction.CW)
        }
        canvas.clipPath(clip)

        val random = Random(0xC0FFE)  // 固定 seed → 球面纹理稳定
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val innerR = radius - 3f
        val shapes = Shape.values()
        val pieceCount = 120

        repeat(pieceCount) { i ->
            val theta = random.nextDouble(0.0, 2.0 * PI)
            val r = innerR * sqrt(random.nextFloat())
            val px = cx + (r * cos(theta)).toFloat()
            val py = cy + (r * sin(theta)).toFloat()
            val baseSize = 7f + random.nextFloat() * 2f
            val rot = random.nextFloat() * 360f
            paint.color = PALETTE[i % PALETTE.size]

            val s = canvas.save()
            canvas.translate(px, py)
            canvas.rotate(rot)
            when (shapes[i % shapes.size]) {
                Shape.RECT -> {
                    val rect = RectF(-baseSize * 0.30f, -baseSize * 0.55f, baseSize * 0.30f, baseSize * 0.55f)
                    canvas.drawRoundRect(rect, 0.8f, 0.8f, paint)
                }
                Shape.STAR -> {
                    buildStarPath(starPath, cx = 0f, cy = 0f, outer = baseSize * 0.55f, inner = baseSize * 0.25f)
                    canvas.drawPath(starPath, paint)
                }
                Shape.TRIANGLE -> {
                    val p = Path()
                    p.moveTo(0f, -baseSize * 0.50f)
                    p.lineTo(baseSize * 0.45f, baseSize * 0.45f)
                    p.lineTo(-baseSize * 0.45f, baseSize * 0.45f)
                    p.close()
                    canvas.drawPath(p, paint)
                }
            }
            canvas.restoreToCount(s)
        }
        canvas.restoreToCount(save)
    }

    private fun drawBallHighlight(canvas: Canvas, shellRect: RectF) {
        // 顶部高光：白色径向渐变椭圆，模拟从右上方打光
        val save = canvas.save()
        val clip = Path().apply { addOval(shellRect, Path.Direction.CW) }
        canvas.clipPath(clip)

        val cx = shellRect.left + shellRect.width() * 0.30f
        val cy = shellRect.top + shellRect.height() * 0.28f
        val r = shellRect.width() * 0.45f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(0x80FFFFFF.toInt(), 0x2EFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        canvas.restoreToCount(save)
    }

    private fun drawBallBorder(canvas: Canvas, shellRect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = BALL_BORDER_COLOR
        canvas.drawOval(shellRect, paint)
    }

    /** 蝴蝶结 = 左环 + 右环 + 中心结 + 两根金色飘带（飘带在结之下）。 */
    private fun drawBowAndRibbons(canvas: Canvas, bowCenterX: Float, bowCenterY: Float) {
        val loopW = ballDiameter * 0.28f
        val loopH = ballDiameter * 0.22f
        val overlap = loopW * 0.10f
        val tilt = 14.0

        // 飘带先画（在结下方）
        drawRibbon(canvas, bowCenterX, bowCenterY, -1f)
        drawRibbon(canvas, bowCenterX, bowCenterY, +1f)

        // 左环 + 右环
        drawBowLoop(canvas, bowCenterX - loopW / 2f + overlap, bowCenterY, loopW, loopH, -tilt)
        drawBowLoop(canvas, bowCenterX + loopW / 2f - overlap, bowCenterY, loopW, loopH, +tilt)

        // 中心结（最上层）
        drawKnot(canvas, bowCenterX, bowCenterY)
    }

    private fun drawBowLoop(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, tiltDeg: Double) {
        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tiltDeg.toFloat())

        // 投影
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.color = 0x52000000.toInt() // alpha 0.32 black
        canvas.drawOval(RectF(-w / 2f, -h / 2f + 2f, w / 2f, h / 2f + 2f), shadowPaint)

        // 环本体（红椭圆）
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        ringPaint.color = BOW_RED
        canvas.drawOval(RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), ringPaint)

        // 环洞（深红椭圆）
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        holePaint.color = BOW_DARK
        holePaint.alpha = (0.55f * 255f).toInt()
        canvas.drawOval(RectF(-w / 2f + w * 0.18f, -h / 2f + h * 0.20f, w / 2f - w * 0.18f, h / 2f - h * 0.20f), holePaint)

        // 顶部高光（白色小椭圆）
        val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        hlPaint.color = BOW_SHINE
        val hlW = w * 0.55f
        val hlH = h * 0.25f
        canvas.drawOval(RectF(-hlW / 2f, -h / 2f + 1.5f, hlW / 2f, -h / 2f + 1.5f + hlH), hlPaint)

        canvas.restoreToCount(save)
    }

    private fun drawKnot(canvas: Canvas, cx: Float, cy: Float) {
        val w = ballDiameter * 0.11f
        val h = ballDiameter * 0.27f

        // 投影
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.color = 0x52000000.toInt()
        val shadowRect = RectF(cx - w / 2f, cy - h / 2f + 2f, cx + w / 2f, cy + h / 2f + 2f)
        canvas.drawRoundRect(shadowRect, 2.5f, 2.5f, shadowPaint)

        // 结本体
        val knotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        knotPaint.color = BOW_DARK
        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        canvas.drawRoundRect(rect, 2.5f, 2.5f, knotPaint)

        // 左侧高光条
        val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        hlPaint.color = 0x66FFFFFF.toInt() // alpha 0.40
        val hlRect = RectF(cx - w / 2f + 1.2f, cy - h / 2f + 2.5f, cx - w / 2f + 2.7f, cy + h / 2f - 2.5f)
        canvas.drawRoundRect(hlRect, 0.75f, 0.75f, hlPaint)
    }

    private fun drawRibbon(canvas: Canvas, knotCx: Float, knotCy: Float, sign: Float) {
        val knotH = ballDiameter * 0.27f
        val topX = knotCx + sign * 1.5f
        val topY = knotCy + knotH / 2f - 1f
        val bottomX = knotCx + sign * ballDiameter * 0.16f
        val bottomY = topY + ballDiameter * 0.47f
        val topWidth = ballDiameter * 0.055f
        val bottomWidth = ballDiameter * 0.086f

        val dx = bottomX - topX
        val dy = bottomY - topY
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0.001f) return
        val nx = -dy / len
        val ny = dx / len

        val notchDepth = ballDiameter * 0.08f
        val notchX = bottomX - dx / len * notchDepth
        val notchY = bottomY - dy / len * notchDepth

        val path = Path()
        path.moveTo(topX - nx * topWidth / 2f, topY - ny * topWidth / 2f)
        path.lineTo(bottomX - nx * bottomWidth / 2f, bottomY - ny * bottomWidth / 2f)
        path.lineTo(notchX, notchY)
        path.lineTo(bottomX + nx * bottomWidth / 2f, bottomY + ny * bottomWidth / 2f)
        path.lineTo(topX + nx * topWidth / 2f, topY + ny * topWidth / 2f)
        path.close()

        // 投影
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        shadow.color = 0x47000000.toInt() // alpha 0.28
        val shadowPath = Path(path)
        shadowPath.offset(0f, 1.5f)
        canvas.drawPath(shadowPath, shadow)

        // 飘带本体
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = RIBBON_GOLD
        canvas.drawPath(path, paint)
    }

    private fun buildStarPath(path: Path, cx: Float, cy: Float, outer: Float, inner: Float) {
        path.reset()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = i * PI.toFloat() / 5f - PI.toFloat() / 2f
            val px = cx + radius * cos(angle)
            val py = cy + radius * sin(angle)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    // ---------------------------------------------------------------------- //
    // Easing helpers
    // ---------------------------------------------------------------------- //

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun easeIn(t: Float): Float = t * t

    private fun easeInOut(t: Float): Float =
        if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f

    /**
     * 模拟 UIView spring drop-in：阻尼系数 ~0.62 时会过冲再收敛。
     * 数学：欠阻尼弹簧 1 - e^(-ζω₀t) * cos(ω₁t)。这里简化为带衰减的余弦反弹。
     */
    private fun springOut(t: Float, damping: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val omega = (1f - damping) * 8f + 6f  // 频率：低阻尼 → 高频反弹
        val decay = kotlin.math.exp(-damping * omega.toDouble() * t.toDouble()).toFloat()
        return 1f - decay * cos(omega * t.toDouble()).toFloat()
    }
}
