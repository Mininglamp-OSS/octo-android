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
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import kotlin.math.sqrt

class LumaKeyVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onVideoEnd: (() -> Unit)? = null
    var onFirstFrame: (() -> Unit)? = null

    private var mediaPlayer: MediaPlayer? = null
    private var pendingAfd: AssetFileDescriptor? = null
    private var firstFrameNotified = false
    private var frameBitmap: Bitmap? = null
    private val drawMatrix = Matrix()

    private val decoderView: TextureView
    private val renderView: View

    private val centerMask: FloatArray by lazy { buildCenterMask() }
    private var maskW = 0
    private var maskH = 0

    init {
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
                val bmp = frameBitmap ?: return
                drawMatrix.reset()
                drawMatrix.setScale(
                    width.toFloat() / bmp.width,
                    height.toFloat() / bmp.height
                )
                canvas.drawBitmap(bmp, drawMatrix, null)
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
                setOnPreparedListener { it.start() }
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
        val bw = PROCESS_WIDTH
        val bh = PROCESS_HEIGHT

        if (frameBitmap == null || frameBitmap!!.width != bw || frameBitmap!!.height != bh) {
            frameBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        }

        decoderView.getBitmap(frameBitmap!!)
        applyLumaKey(frameBitmap!!)

        if (!firstFrameNotified) {
            firstFrameNotified = true
            onFirstFrame?.invoke()
        }

        renderView.invalidate()
    }

    private fun applyLumaKey(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        if (maskW != w || maskH != h) {
            maskW = w
            maskH = h
            buildCenterMask()
        }
        val mask = centerMask

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

            val bgA = BG_FLOOR + (luma / THRESHOLD).coerceIn(0f, 1f) * (BG_CEIL - BG_FLOOR)
            val edge = smoothstep(THRESHOLD, THRESHOLD + TOLERANCE, luma)
            var a = bgA + (1f - bgA) * edge

            if (i < mask.size) a = maxOf(a, mask[i])

            val alpha = (a * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun buildCenterMask(): FloatArray {
        val w = if (maskW > 0) maskW else PROCESS_WIDTH
        val h = if (maskH > 0) maskH else PROCESS_HEIGHT
        val mask = FloatArray(w * h)
        val cx = w / 2f
        val cy = h / 2f
        val shortSide = minOf(w, h).toFloat()
        val protectR = shortSide * CENTER_PROTECT_RADIUS
        val protectSoft = shortSide * CENTER_PROTECT_SOFTNESS
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = x - cx
                val dy = y - cy
                val d = sqrt(dx * dx + dy * dy)
                mask[y * w + x] = 1f - smoothstep(protectR, protectR + protectSoft, d)
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
        frameBitmap?.recycle()
        frameBitmap = null
    }

    companion object {
        private const val PROCESS_WIDTH = 540
        private const val PROCESS_HEIGHT = 960
        private const val THRESHOLD = 0.10f
        private const val TOLERANCE = 0.12f
        private const val BG_FLOOR = 0.05f
        private const val BG_CEIL = 0.45f
        private const val CENTER_PROTECT_RADIUS = 0.30f
        private const val CENTER_PROTECT_SOFTNESS = 0.14f
    }
}
