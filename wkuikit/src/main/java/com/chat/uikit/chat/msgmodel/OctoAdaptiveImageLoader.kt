/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.msgmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import com.caverock.androidsvg.SVG
import io.adaptivecards.renderer.GenericImageLoaderAsync
import io.adaptivecards.renderer.IOnlineImageLoader
import io.adaptivecards.renderer.http.HttpRequestResult
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * AdaptiveCards SDK 用的图片加载扩展。**核心动机**：Web 端 InteractiveCard 里
 * 大量图标 / 头像走 Iconify + DiceBear 的 CDN 直返 SVG（`image/svg+xml`）——
 * AC SDK 默认的图片加载走 `BitmapFactory.decodeStream`，**不认 SVG**，所有
 * SVG 位置全部空白（文档图标 / 外链 / 头像 / 时钟 / 勾选 …）。
 *
 * 本 loader 注册到 [io.adaptivecards.renderer.registration.CardRendererRegistration]
 * 后，SDK 每次要取远端图前先问我们：
 *  1. HttpURLConnection 拉 bytes（一次，不做磁盘缓存 —— [InteractiveCardRenderer] 层
 *     的 view LRU 天然覆盖 bind 内的重复请求）。**注意**：Android 默认不装 HttpURLConnection
 *     响应缓存（需显式 HttpResponseCache.install），且 finally 里 disconnect 会断 keep-alive，
 *     故跨 bind / LRU 淘汰 / 夜间切换后同一图标会重新拉取。图标小、量少可接受；若成瓶颈再装缓存。
 *  2. 判 SVG（前若干字节里含 `<svg` 或 `<?xml`——URL 后缀不可靠，Iconify 用
 *     `.svg`，DiceBear 是 `/svg` 没后缀，都要认）
 *  3. SVG 走 AndroidSVG → 画到指定 target Bitmap；非 SVG 走 BitmapFactory
 *
 * ## 尺寸策略
 * SDK 会在 Image 元素上带 `width/height`（see goldens: `16px/28px/…`），但那是渲染
 * **占位**尺寸——传给 loader 的 URL 里不含它。所以这里按**位图目标像素** = SVG
 * documentSize（若有 viewBox 或 documentWidth/Height）；缺省 [FALLBACK_SIZE_PX]。
 * SDK 后续 stretch 到 element size 的双线性放大对图标这种矢量图 acceptable。
 *
 * ## 深色模式 tint（暂缓）
 * Iconify URL 里 `color=%236b7075` 是浅灰 hardcode，深色底看不清。真正修复方案：
 * loader 里拦截 URL、把 color query 替换成夜间可见色。**这是 P1**，等 P0 先看效果。
 *
 * ## 线程
 * SDK 在 [GenericImageLoaderAsync]（`AsyncTask<String, Void, HttpRequestResult<Bitmap>>`）
 * 的 `doInBackground` 里调 [loadOnlineImage]，天然子线程；不用自己再切线程。
 */
object OctoAdaptiveImageLoader : IOnlineImageLoader {

    private const val TAG = "OctoACImageLoader"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    /** SVG 无 viewBox / documentSize 时的目标像素。128 覆盖窄气泡内出现的 16-40dp icon 全场景。 */
    private const val FALLBACK_SIZE_PX = 128

    /**
     * SVG 目标位图单边像素上限。卡片里都是图标级素材（≤40dp），512 已远超实际需求；
     * clamp 是为了挡住畸形 SVG（如 `documentWidth="100000"`）——否则 [Bitmap.createBitmap]
     * 会申请超大内存直接 OOM crash 掉整个会话页。
     */
    private const val MAX_SIZE_PX = 512

    /**
     * 单张图允许读入内存的字节上限（2MB）。图标 / 头像正常都是几 KB，加上限是防某个 URL
     * 返回超大响应时 [readBytesCapped] 把整个 body 读进内存 OOM。超限即当加载失败降级空白。
     */
    private const val MAX_BYTES = 2 * 1024 * 1024

    /** 单元素判 SVG 时的头部读取字节数——够识别 `<?xml ...?><svg ...>` prelude。 */
    private const val SVG_SNIFF_HEAD_BYTES = 200

    override fun loadOnlineImage(url: String, async: GenericImageLoaderAsync?): HttpRequestResult<Bitmap> {
        if (url.isBlank()) {
            return HttpRequestResult(IllegalArgumentException("blank url"))
        }
        return try {
            val bytes = fetchBytes(url)
            HttpRequestResult(decodeImageBytes(bytes))
        } catch (t: Throwable) {
            Log.w(TAG, "load 失败 url=$url", t)
            HttpRequestResult(t as? Exception ?: RuntimeException(t))
        }
    }

    /**
     * 字节 → Bitmap。SVG 走 AndroidSVG，其余走降采样位图解码。
     *
     * 供 [OctoDataUriResolver] 复用——data URI 与 http 图片只在"取字节"这一步不同，
     * 解码分支完全一样。
     */
    internal fun decodeImageBytes(bytes: ByteArray): Bitmap =
        if (looksLikeSvg(bytes)) decodeSvg(bytes) else decodeRasterDownsampled(bytes)

    // ─────────────────────────────── Fetch ───────────────────────────────

    private fun fetchBytes(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            // Iconify / DiceBear 对无 UA 的请求会返 403，加个稳定 UA。
            setRequestProperty("User-Agent", "OctoIM-Android/AdaptiveCard")
            setRequestProperty("Accept", "image/svg+xml,image/*,*/*")
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code for $url")
            }
            conn.inputStream.use { readBytesCapped(it) }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 读到 [MAX_BYTES] 就抛 [java.io.IOException]，避免超大响应把整个 body 读进内存 OOM。
     * 正常图标 / 头像几 KB，不会触到上限。
     */
    private fun readBytesCapped(input: java.io.InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) {
                throw java.io.IOException("响应超过 ${MAX_BYTES} 字节上限")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    // ─────────────────────────────── Raster decode ───────────────────────────────

    /**
     * raster（PNG/JPEG/WebP…）降采样解码。**动机**：[MAX_BYTES] 只挡住"压缩后"体积，
     * 但高压缩比大图（如 2MB 内的 8000×8000 PNG）全尺寸解码成 ARGB_8888 可达数百 MB → OOM，
     * 靠 `catch(OutOfMemoryError)` 兜底并不可靠（真正触顶的可能是其它线程的并发分配）。
     * 故先 [BitmapFactory.Options.inJustDecodeBounds] 只读尺寸（不分配像素），据此算 2 的幂
     * [BitmapFactory.Options.inSampleSize] 把单边降到 [MAX_SIZE_PX] 以内再真正解码，与 SVG 路径
     * 的 clamp 对齐。卡片里都是图标 / 头像级素材，降采样对观感无影响。
     */
    private fun decodeRasterDownsampled(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IllegalStateException("raster 尺寸非法 ${w}x$h")
        val opts = BitmapFactory.Options().apply { inSampleSize = computeInSampleSize(w, h, MAX_SIZE_PX) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("BitmapFactory decode 返回 null")
    }

    /** 返回最小的 2 的幂，使 [width]/[height] 降采样后单边 ≤ [maxPx]。上限内直接返回 1。 */
    internal fun computeInSampleSize(width: Int, height: Int, maxPx: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w > maxPx || h > maxPx) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    // ─────────────────────────────── SVG sniff & decode ───────────────────────────────

    /**
     * URL 后缀判 SVG 不可靠（DiceBear 是 `/svg` 无后缀，某些代理会剥掉 query）。
     * 直接看首字节：允许 leading whitespace / BOM / `<?xml prelude>` 后含 `<svg`。
     */
    internal fun looksLikeSvg(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val head = String(
            bytes,
            0,
            minOf(bytes.size, SVG_SNIFF_HEAD_BYTES),
            Charsets.UTF_8,
        )
        // 大小写不敏感；xml prelude 后可能有换行 / meta 才到 svg tag。
        val lower = head.lowercase()
        return lower.contains("<svg")
    }

    private fun decodeSvg(bytes: ByteArray): Bitmap {
        val svg = ByteArrayInputStream(bytes).use { SVG.getFromInputStream(it) }
        val (w, h) = pickSvgTargetSize(svg)
        // ARGB_8888 保透明；很多 lucide 图标是纯色 stroke，无透明区域会浪费但 icon 尺寸小可忽略。
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // renderToCanvas 会尊重 SVG 的 viewBox / preserveAspectRatio 做缩放；无需手动 setDocumentSize。
        svg.renderToCanvas(canvas)
        return bitmap
    }

    private fun pickSvgTargetSize(svg: SVG): Pair<Int, Int> {
        // documentWidth / Height 单位是 px 或依赖 SVG 声明；-1 表示没设。
        // 一律 coerceIn(1, MAX_SIZE_PX)：下界防 0/负导致 createBitmap 抛异常，上界防畸形 SVG OOM。
        val dw = svg.documentWidth
        val dh = svg.documentHeight
        return when {
            dw > 0f && dh > 0f -> Pair(dw.toInt().coerceIn(1, MAX_SIZE_PX), dh.toInt().coerceIn(1, MAX_SIZE_PX))
            svg.documentViewBox != null -> {
                val vb = svg.documentViewBox
                Pair(vb.width().toInt().coerceIn(1, MAX_SIZE_PX), vb.height().toInt().coerceIn(1, MAX_SIZE_PX))
            }
            else -> Pair(FALLBACK_SIZE_PX, FALLBACK_SIZE_PX)
        }
    }
}
