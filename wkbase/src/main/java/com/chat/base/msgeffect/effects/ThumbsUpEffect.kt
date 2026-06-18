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

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.msgeffect.BubblePulseHelper
import com.chat.base.msgeffect.MessageEffectType
import java.lang.ref.WeakReference
import kotlin.random.Random

/**
 * 「点赞上升」对齐 iOS WKStarburstEffect.m：
 * 直接渲染系统字体的 👍 emoji 字符，多枚不同字号从底部外侧匀速上升到顶部外侧，
 * X 方向做关键帧式正弦摆动，整体 1s 内做 0.9→1.1→0.95→1.05→1.0 的脉冲缩放，
 * 末段 30% 淡出。不再叠星星粒子（iOS 原版没有）。
 *
 * 参数（与 iOS 保持一致）：
 *  - 18 枚 thumb，2.0s 内分批生成（stagger ≈ 111ms + 0~120ms 随机抖动）
 *  - fontSize 22..46sp
 *  - 单枚生命 2.8..4.3s
 *  - 总淡出窗口 7.5s（durationMs，与 iOS scheduleRemovalAfterDelay 一致）
 */
class ThumbsUpEffect(
    type: MessageEffectType,
    sourceRect: RectF,
    viewWidth: Int,
    viewHeight: Int
) : BaseEffect(type, sourceRect, viewWidth, viewHeight) {

    private data class Thumb(
        val fontSizePx: Float,
        val xStart: Float,
        val swayAmplitude: Float,
        val durationMs: Float,
        val delayMs: Float,
        val pulsePhaseMs: Float
    )

    private val thumbs = mutableListOf<Thumb>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    /**
     * Hit-test targets：overlay 自身 + 聊天 RecyclerView。粒子飘到屏幕上的位置都以
     * overlay 为参考；命中检测时拿 RV 可见 cell 转换到 overlay 坐标后求相交。
     * 对应 iOS WKStarburstEffect.onHitCheckTimer。
     */
    private var overlayRef: WeakReference<View>? = null
    private var recyclerRef: WeakReference<RecyclerView>? = null
    /** 已经命中过的 (thumbIndex, cellViewIdentity) 集合，去重——同一对粒子/气泡只脉冲一次。 */
    private val hitKeys = HashSet<Long>()
    private var lastHitCheckMs = -1L
    private val tmpOverlayLoc = IntArray(2)
    private val tmpCellLoc = IntArray(2)

    fun attachBubbleTargets(overlay: View?, recycler: RecyclerView?) {
        overlayRef = overlay?.let { WeakReference(it) }
        recyclerRef = recycler?.let { WeakReference(it) }
    }

    override fun onStart() {
        spawnThumbs()
    }

    private fun spawnThumbs() {
        // iOS WKStarburstEffect 用 fontSize 22..46pt（≈ 5.6%..11.7% × viewW）。
        // 这里改为按 viewWidth 的比例算字号，密度/折叠屏/分辨率全自适应——
        // 先前用 sp×3 的硬编码会在 ldpi/xxxhdpi 上偏离 iOS 视觉。
        val totalCount = 18
        val spawnDuration = 2000f
        val fontMinPx = viewWidth * 0.056f
        val fontMaxPx = viewWidth * 0.117f

        for (i in 0 until totalCount) {
            val fontPx = fontMinPx + Random.nextFloat() * (fontMaxPx - fontMinPx)
            val size = fontPx * 1.4f
            val xMargin = size / 2f
            val xRange = (viewWidth - size).coerceAtLeast(0f)
            val xStart = if (xRange > 0f) Random.nextFloat() * xRange + xMargin else viewWidth / 2f
            val swayAmp = 20f + Random.nextFloat() * 25f
            val baseDelay = i.toFloat() * (spawnDuration / totalCount)
            val jitter = Random.nextFloat() * 120f
            val duration = 2800f + Random.nextFloat() * 1500f

            thumbs.add(
                Thumb(
                    fontSizePx = fontPx,
                    xStart = xStart,
                    swayAmplitude = swayAmp,
                    durationMs = duration,
                    delayMs = baseDelay + jitter,
                    pulsePhaseMs = Random.nextFloat() * 1000f
                )
            )
        }
    }

    override fun onFrame(canvas: Canvas, elapsedMs: Long, deltaMs: Long) {
        val t = elapsedMs.toFloat()
        // overlay 坐标系下的粒子矩形——按 60ms 节流跑一次气泡命中检测，与 iOS 0.06s timer 对齐。
        val shouldHitCheck = (elapsedMs - lastHitCheckMs) >= HIT_CHECK_INTERVAL_MS
        if (shouldHitCheck) lastHitCheckMs = elapsedMs

        for (i in thumbs.indices) {
            val thumb = thumbs[i]
            val localT = t - thumb.delayMs
            if (localT < 0f) continue
            if (localT > thumb.durationMs) continue

            val progress = (localT / thumb.durationMs).coerceIn(0f, 1f)

            // Y 匀速上升：yStart = viewH + size, yEnd = -size
            val size = thumb.fontSizePx * 1.4f
            val yStart = viewHeight + size
            val yEnd = -size
            val y = yStart + (yEnd - yStart) * progress

            // X 关键帧摆动 (与 iOS 保持一致的 5 段贝塞尔近似，linear 连接即可)：
            // [0, +amp, 0, -0.8amp, +0.5amp, 0]
            val x = thumb.xStart + keyframeSway(progress, thumb.swayAmplitude)

            // Alpha：前 70% 保持 0.95，后 30% 线性淡出到 0
            val alpha = if (progress < 0.7f) 0.95f
            else (0.95f * (1f - (progress - 0.7f) / 0.3f)).coerceAtLeast(0f)

            // Scale 脉冲：1s 周期循环 (0.9, 1.1, 0.95, 1.05, 1.0)
            val pulseT = ((localT + thumb.pulsePhaseMs) % 1000f) / 1000f
            val scale = pulseScale(pulseT)

            paint.textSize = thumb.fontSizePx * scale
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            // 让"👍"基线大致位于 y（视觉中线）。Paint.Align.CENTER 已对齐 X，
            // Y 用 textSize/3 近似 emoji 的下沉量做小补偿。
            canvas.drawText("👍", x, y + thumb.fontSizePx * 0.35f, paint)

            if (shouldHitCheck && alpha > 0.3f) {
                checkBubbleHits(i, x, y, size)
            }
        }
    }

    private fun checkBubbleHits(thumbIndex: Int, cx: Float, cy: Float, size: Float) {
        val overlay = overlayRef?.get() ?: return
        val rv = recyclerRef?.get() ?: return
        if (!rv.isAttachedToWindow) return

        overlay.getLocationOnScreen(tmpOverlayLoc)
        val particleScreenLeft = tmpOverlayLoc[0] + cx - size / 2f
        val particleScreenTop = tmpOverlayLoc[1] + cy - size / 2f
        val particleScreenRight = particleScreenLeft + size
        val particleScreenBottom = particleScreenTop + size

        for (i in 0 until rv.childCount) {
            val cell = rv.getChildAt(i) ?: continue
            cell.getLocationOnScreen(tmpCellLoc)
            val cellLeft = tmpCellLoc[0]
            val cellTop = tmpCellLoc[1]
            val cellRight = cellLeft + cell.width
            val cellBottom = cellTop + cell.height
            if (cell.width <= 0 || cell.height <= 0) continue

            val intersects = particleScreenRight > cellLeft &&
                    particleScreenLeft < cellRight &&
                    particleScreenBottom > cellTop &&
                    particleScreenTop < cellBottom
            if (!intersects) continue

            // 用 thumbIndex << 32 | cell.identityHashCode 作为去重 key——同一粒子穿过同一 cell 只脉冲一次。
            val cellKey = System.identityHashCode(cell).toLong() and 0xFFFFFFFFL
            val hitKey = (thumbIndex.toLong() shl 32) or cellKey
            if (hitKeys.add(hitKey)) {
                BubblePulseHelper.pulse(cell)
            }
        }
    }

    private fun keyframeSway(progress: Float, amp: Float): Float {
        // iOS values: [+0, +amp, 0, -0.8amp, +0.5amp, 0] over duration
        val keyValues = floatArrayOf(0f, amp, 0f, -amp * 0.8f, amp * 0.5f, 0f)
        val seg = keyValues.size - 1  // 5 segments
        val segLen = 1f / seg
        val idx = (progress / segLen).toInt().coerceIn(0, seg - 1)
        val local = (progress - idx * segLen) / segLen
        return keyValues[idx] + (keyValues[idx + 1] - keyValues[idx]) * local
    }

    private fun pulseScale(t: Float): Float {
        // 5 keyframes: 0.9, 1.1, 0.95, 1.05, 1.0
        val keys = floatArrayOf(0.9f, 1.1f, 0.95f, 1.05f, 1.0f)
        val seg = keys.size - 1
        val segLen = 1f / seg
        val idx = (t / segLen).toInt().coerceIn(0, seg - 1)
        val local = (t - idx * segLen) / segLen
        return keys[idx] + (keys[idx + 1] - keys[idx]) * local
    }

    override fun onEnd() {
        thumbs.clear()
        hitKeys.clear()
        overlayRef = null
        recyclerRef = null
    }

    companion object {
        private const val HIT_CHECK_INTERVAL_MS = 60L
    }
}
