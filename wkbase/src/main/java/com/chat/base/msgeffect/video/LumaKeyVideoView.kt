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

package com.chat.base.msgeffect.video

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 把不带 alpha 通道的深色背景视频实时 luma-key 抠像成"透明视频"叠放播放。
 *
 * 算法逐行对齐 iOS `WKLumaKeyVideoView` 的 CIColorKernel（见该文件 setupKernel）：
 *   luma  = dot(rgb, (0.299, 0.587, 0.114))
 *   bgA   = bgFloor + clamp(luma / thr, 0, 1) * (bgCeil - bgFloor)
 *   edge  = smoothstep(thr, thr + tol, luma)
 *   a     = mix(bgA, 1, edge)
 *   a     = max(a, centerProtect * centerStrength)
 *   a     = max(a, eyeProtect * eyeStrength)
 *
 * 抠像参数由 [params] 提供，默认值等价于参数化改造前的写死常量。
 */
class LumaKeyVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onVideoEnd: (() -> Unit)? = null
    var onFirstFrame: (() -> Unit)? = null

    /**
     * 抠像参数。必须在 [startVideo] 之前设置——保护圈的几何遮罩会在首帧按当时的
     * params 预计算并缓存，中途改只有 strength 时间门控会跟着变，几何不会重建。
     */
    var params: LumaKeyParams = LumaKeyParams.DEFAULT

    private var mediaPlayer: MediaPlayer? = null
    private var pendingAfd: AssetFileDescriptor? = null
    private var firstFrameNotified = false
    private var captureBitmap: Bitmap? = null
    private var displayBitmap: Bitmap? = null
    private var pixelBuffer: IntArray? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var processing = false

    private val decoderView: TextureView
    private val renderView: View

    // 素材原始尺寸，由 MediaPlayer 回报。抠像缓冲的宽高比与绘制目标矩形都依赖它，
    // 拿到之前用兜底尺寸，拿到后 processFrame 会自动重建缓冲与遮罩。
    @Volatile
    private var srcWidth = 0
    @Volatile
    private var srcHeight = 0

    // 放大绘制时开双线性过滤，避免最近邻的锯齿。
    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dstRect = RectF()

    // 保护圈的几何遮罩（值域 0..1，不含时间强度）。按处理尺寸预计算一次，
    // 每帧只乘一个标量 strength，避免逐帧重算距离场。
    //
    // 改造前这里是 `by lazy` + buildCenterMask() 返回值被丢弃，尺寸变化后遮罩
    // 永远不会重建（因为处理尺寸是常量所以没暴露出来）。改成显式可空字段 + rebuild。
    private var centerMask: FloatArray? = null
    private var eyeMask: FloatArray? = null
    private var maskW = 0
    private var maskH = 0

    init {
        workerThread = HandlerThread("LumaKeyWorker").apply { start() }
        workerHandler = Handler(workerThread!!.looper)

        decoderView = TextureView(context)
        decoderView.alpha = 0f
        decoderView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                if (pendingAfd != null) initMediaPlayer(st)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                processFrame()
            }
        }
        addView(decoderView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        renderView = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                val bmp = displayBitmap ?: return
                if (width <= 0 || height <= 0) return
                // 布局对齐 iOS WKClassyVideoEffect / WKShangfangVideoEffect：
                // 宽度铺满、高度按素材比例等比、垂直居中；比屏更"高"时上下溢出被裁掉
                // （边缘本来就是抠透明的黑边，裁掉无妨）。
                //
                // 改造前这里是把 bitmap 直接拉伸填满整个 view（MATCH_PARENT），
                // 素材比例与屏幕比例不一致时会非等比变形，半透明黑背景的覆盖范围
                // 也随之和 iOS 对不上。
                val aspect = sourceAspect(bmp)
                val dstW = width.toFloat()
                val dstH = dstW / aspect
                val top = (height - dstH) * 0.5f
                dstRect.set(0f, top, dstW, top + dstH)
                canvas.drawBitmap(bmp, null, dstRect, drawPaint)
            }
        }
        addView(renderView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun startVideo(afd: AssetFileDescriptor) {
        pendingAfd = afd
        val st = decoderView.surfaceTexture
        if (st != null) initMediaPlayer(st)
    }

    private var videoSurface: Surface? = null

    private fun initMediaPlayer(st: SurfaceTexture) {
        val afd = pendingAfd ?: return
        pendingAfd = null
        try {
            videoSurface = Surface(st)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setSurface(videoSurface)
                setVolume(0f, 0f)
                isLooping = false
                setOnVideoSizeChangedListener { _, w, h ->
                    if (w > 0 && h > 0) {
                        srcWidth = w
                        srcHeight = h
                    }
                }
                setOnPreparedListener {
                    if (it.videoWidth > 0 && it.videoHeight > 0) {
                        srcWidth = it.videoWidth
                        srcHeight = it.videoHeight
                    }
                    it.start()
                }
                setOnErrorListener { _, _, _ ->
                    post { onVideoEnd?.invoke() }
                    true
                }
                setOnCompletionListener { post { onVideoEnd?.invoke() } }
                prepareAsync()
            }
            afd.close()
        } catch (e: Exception) {
            post { onVideoEnd?.invoke() }
        }
    }

    private fun processFrame() {
        if (processing) return
        processing = true

        val (bw, bh) = processSize()
        // 尺寸变化时只丢引用、不 recycle：displayBitmap 可能正被 renderView.onDraw 使用，
        // 提前 recycle 会触发 "trying to use a recycled bitmap"。整个播放期间尺寸最多
        // 变一次（兜底尺寸 → 素材真实尺寸），交给 GC 回收即可。
        if (captureBitmap == null || captureBitmap!!.width != bw || captureBitmap!!.height != bh) {
            captureBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        }
        if (displayBitmap == null || displayBitmap!!.width != bw || displayBitmap!!.height != bh) {
            displayBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        }
        decoderView.getBitmap(captureBitmap!!)

        // 播放进度必须在主线程取（MediaPlayer 非线程安全），随帧一起丢给 worker。
        // 对齐 iOS 用 item time 驱动 centerStrength 的做法。
        val positionMs = try {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
        val centerStrength = centerStrengthAt(positionMs)
        val eyeStrength = if (params.eyeProtectRadius > 0f) 1f else 0f

        workerHandler?.post {
            try {
                applyLumaKey(captureBitmap!!, centerStrength, eyeStrength)
            } catch (_: Exception) {}
            mainHandler.post {
                val temp = displayBitmap
                displayBitmap = captureBitmap
                captureBitmap = temp
                processing = false
                if (!firstFrameNotified) {
                    firstFrameNotified = true
                    onFirstFrame?.invoke()
                }
                renderView.invalidate()
            }
        }
    }

    /**
     * 抠像缓冲尺寸：**宽高比始终跟随素材**，长边不超过 [LumaKeyParams.processMaxLongSide]。
     *
     * 这一点是清晰度与形状正确性的关键。改造前缓冲写死 540×960（比例 0.5625），
     * 而素材是 884×1920（比例 0.4604）——帧被非等比压进缓冲、再非等比拉回屏幕，
     * 于是 buildRadialMask 画的正圆在屏幕上变成约 1.22:1 的竖椭圆，
     * 保护区形状不对，黑色背景的留存范围也就和 iOS 对不上。
     *
     * 素材尺寸未知时（首帧之前）退回原来的兜底尺寸。
     */
    private fun processSize(): Pair<Int, Int> {
        val vw = srcWidth
        val vh = srcHeight
        if (vw <= 0 || vh <= 0) return FALLBACK_PROCESS_WIDTH to FALLBACK_PROCESS_HEIGHT
        val cap = params.processMaxLongSide.coerceAtLeast(1)
        val longSide = maxOf(vw, vh)
        if (longSide <= cap) return vw to vh
        val scale = cap.toFloat() / longSide
        return maxOf(1, (vw * scale).toInt()) to maxOf(1, (vh * scale).toInt())
    }

    /** 绘制目标矩形用的素材宽高比，素材尺寸未知时退回缓冲自身比例。 */
    private fun sourceAspect(bmp: Bitmap): Float {
        val vw = srcWidth
        val vh = srcHeight
        return if (vw > 0 && vh > 0) vw.toFloat() / vh else bmp.width.toFloat() / bmp.height
    }

    /**
     * 中心保护盘的时间门控强度，对齐 iOS `WKLumaKeyVideoView.m` 的 centerStrength 计算：
     * startTime <= 0 全程 1；position <= startTime 为 0；之后在 ramp 内线性到 1。
     */
    private fun centerStrengthAt(positionMs: Long): Float {
        val start = params.centerProtectStartTimeMs
        if (start <= 0L) return 1f
        if (positionMs <= start) return 0f
        val ramp = params.centerProtectRampDurationMs
        if (ramp <= 0L) return 1f
        return min(1f, (positionMs - start).toFloat() / ramp.toFloat())
    }

    private fun applyLumaKey(bitmap: Bitmap, centerStrength: Float, eyeStrength: Float) {
        val w = bitmap.width
        val h = bitmap.height
        val size = w * h
        if (pixelBuffer == null || pixelBuffer!!.size != size) {
            pixelBuffer = IntArray(size)
        }
        val pixels = pixelBuffer!!
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        if (maskW != w || maskH != h || centerMask == null || eyeMask == null) {
            buildMasks(w, h)
        }
        val cMask = centerMask
        val eMask = eyeMask

        val thr = params.lumaThreshold
        val tol = params.lumaTolerance
        val bgFloor = params.backgroundAlphaFloor
        val bgCeil = params.backgroundAlphaCeil
        val t = maxOf(thr, 0.0001f)
        val edgeHi = thr + maxOf(tol, 0.0001f)
        val cs = centerStrength.coerceIn(0f, 1f)
        val es = eyeStrength.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

            val bgA = bgFloor + (luma / t).coerceIn(0f, 1f) * (bgCeil - bgFloor)
            val edge = smoothstep(thr, edgeHi, luma)
            var a = bgA + (1f - bgA) * edge

            if (cs > 0f && cMask != null && i < cMask.size) {
                a = maxOf(a, cMask[i] * cs)
            }
            if (es > 0f && eMask != null && i < eMask.size) {
                a = maxOf(a, eMask[i] * es)
            }

            val alpha = (a * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun buildMasks(w: Int, h: Int) {
        maskW = w
        maskH = h
        centerMask = buildRadialMask(
            w, h,
            params.centerProtectCenterX, params.centerProtectCenterY,
            params.centerProtectRadius, params.centerProtectSoftness
        )
        eyeMask = buildRadialMask(
            w, h,
            params.eyeProtectCenterX, params.eyeProtectCenterY,
            params.eyeProtectRadius, params.eyeProtectSoftness
        )
    }

    /**
     * 生成一张径向保护遮罩：圆内 1、圆外 0、边缘 smoothstep 过渡。
     *
     * 半径/软度是相对画面短边的比例（与 iOS 一致）。圆心为归一化坐标、原点左上——
     * Bitmap 本身就是左上原点，所以这里不做 iOS 那样的 y 翻转。
     * 半径 <= 0 直接返回全 0（关闭该保护圈）。
     */
    private fun buildRadialMask(
        w: Int,
        h: Int,
        centerX: Float,
        centerY: Float,
        radiusRatio: Float,
        softnessRatio: Float
    ): FloatArray {
        val mask = FloatArray(w * h)
        if (radiusRatio <= 0f) return mask
        val cx = centerX * w
        val cy = centerY * h
        val shortSide = min(w, h).toFloat()
        val r = radiusRatio * shortSide
        val soft = maxOf(softnessRatio * shortSide, 0.0001f)
        for (y in 0 until h) {
            val rowBase = y * w
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val d = sqrt(dx * dx + dy * dy)
                mask[rowBase + x] = 1f - smoothstep(r, r + soft, d)
            }
        }
        return mask
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun release() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        try { videoSurface?.release() } catch (_: Exception) {}
        videoSurface = null
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        captureBitmap?.recycle()
        captureBitmap = null
        displayBitmap?.recycle()
        displayBitmap = null
        pixelBuffer = null
        centerMask = null
        eyeMask = null
    }

    companion object {
        // 素材尺寸拿到之前的兜底缓冲尺寸；正常情况下 onPrepared 先于首帧回调，
        // 真正生效的是 processSize() 按素材比例算出来的尺寸。
        private const val FALLBACK_PROCESS_WIDTH = 540
        private const val FALLBACK_PROCESS_HEIGHT = 960
    }
}
