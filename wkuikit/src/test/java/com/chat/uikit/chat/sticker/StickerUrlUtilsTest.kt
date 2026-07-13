/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 贴图 URL 后缀识别回归。渲染分支基于这里的判定，一旦回归贴图消息就会显示错。
 */
class StickerUrlUtilsTest {

    @Test
    fun `png jpg jpeg webp gif recognised as static image`() {
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.png"))
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.jpg"))
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.jpeg"))
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.webp"))
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.gif"))
    }

    @Test
    fun `uppercase extension recognised (case-insensitive)`() {
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.PNG"))
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.GIF"))
    }

    @Test
    fun `url with query string still recognised`() {
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.png?x=1&y=2"))
        assertTrue(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.gif#fragment"))
    }

    @Test
    fun `lim and json not static image`() {
        assertFalse(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.lim"))
        assertFalse(StickerUrlUtils.isStaticImage("https://cdn.example.com/a.json"))
    }

    @Test
    fun `lim url is lottie format`() {
        assertTrue(StickerUrlUtils.isLottieFormat("https://cdn.example.com/a.lim", null))
        assertTrue(StickerUrlUtils.isLottieFormat("https://cdn.example.com/a.json", null))
    }

    @Test
    fun `format field lim overrides url extension`() {
        // 服务端可能返回 .png URL 但 format 是 "lim"（历史数据 / 上传标准化产物）
        assertTrue(StickerUrlUtils.isLottieFormat("https://cdn.example.com/a.png", "lim"))
        assertTrue(StickerUrlUtils.isLottieFormat("https://cdn.example.com/a.png", "LIM"))
    }

    @Test
    fun `null or empty url handled safely`() {
        assertFalse(StickerUrlUtils.isStaticImage(null))
        assertFalse(StickerUrlUtils.isStaticImage(""))
        assertFalse(StickerUrlUtils.isLottieFormat(null, null))
        assertFalse(StickerUrlUtils.isLottieFormat("", ""))
    }

    @Test
    fun `no extension url returns null ext`() {
        assertNull(StickerUrlUtils.extractExt("https://cdn.example.com/nofile"))
        assertNull(StickerUrlUtils.extractExt("https://cdn.example.com/a."))  // trailing dot
        // 路径里没扩展名，query 里的 ".xxx" 不算
        assertNull(StickerUrlUtils.extractExt("https://cdn.example.com/nofile?type=lim"))
    }

    @Test
    fun `extract ext lowercased and no dot`() {
        assertEquals("png", StickerUrlUtils.extractExt("a.PNG"))
        assertEquals("gif", StickerUrlUtils.extractExt("/foo/bar/baz.gif"))
        assertEquals("lim", StickerUrlUtils.extractExt("http://a.b/c.LIM?x=1"))
    }

    @Test
    fun `normalizePath treats absolute and relative same-path as equal`() {
        val absolute = StickerUrlUtils.normalizePath("https://cdn.example.com/sticker/uid/abc.png")
        val relative = StickerUrlUtils.normalizePath("/sticker/uid/abc.png")
        assertEquals(absolute, relative)
    }

    @Test
    fun `normalizePath strips query and fragment`() {
        assertEquals(
            "/sticker/uid/a.png",
            StickerUrlUtils.normalizePath("https://cdn.example.com/sticker/uid/a.png?v=2")
        )
        assertEquals(
            "/sticker/uid/a.png",
            StickerUrlUtils.normalizePath("https://cdn.example.com/sticker/uid/a.png#frag")
        )
    }

    @Test
    fun `normalizePath scheme case-insensitive`() {
        assertEquals(
            StickerUrlUtils.normalizePath("https://cdn/a.png"),
            StickerUrlUtils.normalizePath("HTTPS://cdn/a.png")
        )
    }

    @Test
    fun `normalizePath null or empty returns null`() {
        assertNull(StickerUrlUtils.normalizePath(null))
        assertNull(StickerUrlUtils.normalizePath(""))
    }

    @Test
    fun `normalizePath scheme with no path yields empty`() {
        assertEquals("", StickerUrlUtils.normalizePath("https://cdn.example.com"))
    }
}
