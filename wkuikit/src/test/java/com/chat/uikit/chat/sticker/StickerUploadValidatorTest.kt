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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 上传贴纸文件的 magic bytes 识别回归。校验规则不依赖 Android BitmapFactory 的
 * 部分（格式识别）走 JVM 单测；尺寸/大小校验依赖 BitmapFactory 需 instrumented。
 */
class StickerUploadValidatorTest {

    @Test
    fun `gif 87a magic bytes are recognized`() {
        val buf = "GIF87a".toByteArray(Charsets.US_ASCII) + ByteArray(6)
        assertEquals(
            StickerUploadValidator.Format.GIF,
            StickerUploadValidator.detectFormatFromBytes(buf)
        )
    }

    @Test
    fun `gif 89a magic bytes are recognized`() {
        val buf = "GIF89a".toByteArray(Charsets.US_ASCII) + ByteArray(6)
        assertEquals(
            StickerUploadValidator.Format.GIF,
            StickerUploadValidator.detectFormatFromBytes(buf)
        )
    }

    @Test
    fun `png magic bytes are recognized`() {
        val buf = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0
        )
        assertEquals(
            StickerUploadValidator.Format.PNG,
            StickerUploadValidator.detectFormatFromBytes(buf)
        )
    }

    @Test
    fun `jpeg magic bytes are recognized`() {
        val buf = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(),
            0xE0.toByte(), 0, 0x10, 'J'.code.toByte(), 'F'.code.toByte(),
            'I'.code.toByte(), 'F'.code.toByte(), 0, 0
        )
        assertEquals(
            StickerUploadValidator.Format.JPEG,
            StickerUploadValidator.detectFormatFromBytes(buf)
        )
    }

    @Test
    fun `webp magic bytes are recognized`() {
        val buf = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
        )
        assertEquals(
            StickerUploadValidator.Format.WEBP,
            StickerUploadValidator.detectFormatFromBytes(buf)
        )
    }

    @Test
    fun `bmp is rejected`() {
        // BMP magic: 42 4D
        val buf = byteArrayOf(0x42, 0x4D, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertNull(StickerUploadValidator.detectFormatFromBytes(buf))
    }

    @Test
    fun `plain text is rejected`() {
        val buf = "hello world\n".toByteArray(Charsets.US_ASCII)
        assertNull(StickerUploadValidator.detectFormatFromBytes(buf))
    }

    @Test
    fun `riff without webp signature is rejected`() {
        // RIFF but not WEBP (e.g., .wav)
        val buf = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()
        )
        assertNull(StickerUploadValidator.detectFormatFromBytes(buf))
    }

    @Test
    fun `empty buffer is rejected`() {
        assertNull(StickerUploadValidator.detectFormatFromBytes(ByteArray(0)))
    }

    @Test
    fun `short buffer with png prefix only is rejected`() {
        // 少于 8 字节 → PNG magic 未完整判定失败
        val buf = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertNull(StickerUploadValidator.detectFormatFromBytes(buf))
    }
}
