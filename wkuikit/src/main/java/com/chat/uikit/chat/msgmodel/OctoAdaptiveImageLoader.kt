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
 *     的 view LRU 天然覆盖 bind 内的重复请求；跨 bind 的复用交给 OS/网络层缓存即可）
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

    /** 单元素判 SVG 时的头部读取字节数——够识别 `<?xml ...?><svg ...>` prelude。 */
    private const val SVG_SNIFF_HEAD_BYTES = 200

    override fun loadOnlineImage(url: String, async: GenericImageLoaderAsync?): HttpRequestResult<Bitmap> {
        if (url.isBlank()) {
            return HttpRequestResult(IllegalArgumentException("blank url"))
        }
        return try {
            val bytes = fetchBytes(url)
            val bitmap = if (looksLikeSvg(bytes)) {
                decodeSvg(bytes)
            } else {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("BitmapFactory decode 返回 null")
            }
            HttpRequestResult(bitmap)
        } catch (t: Throwable) {
            Log.w(TAG, "load 失败 url=$url", t)
            HttpRequestResult(t as? Exception ?: RuntimeException(t))
        }
    }

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
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
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
        val dw = svg.documentWidth
        val dh = svg.documentHeight
        return when {
            dw > 0f && dh > 0f -> Pair(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1))
            svg.documentViewBox != null -> {
                val vb = svg.documentViewBox
                Pair(vb.width().toInt().coerceAtLeast(1), vb.height().toInt().coerceAtLeast(1))
            }
            else -> Pair(FALLBACK_SIZE_PX, FALLBACK_SIZE_PX)
        }
    }
}
