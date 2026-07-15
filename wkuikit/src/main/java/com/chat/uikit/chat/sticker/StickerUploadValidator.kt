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

import android.graphics.BitmapFactory
import com.chat.base.R as BaseR
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * 上传自己贴纸的客户端校验。与 iOS `WKStickerUploadService` 校验规则 1:1 对齐：
 *
 * - 格式白名单：gif / png / jpg / jpeg / webp（读文件头 magic bytes，不能只信后缀）
 * - 文件大小 ≤ [MAX_FILE_BYTES] (1 MB)
 * - 图片长边 ≤ [MAX_DIMENSION_PX] (512 px)
 *
 * 使用方式：[validate] 返回 [Result.success] 或带 stringResId 的 [Result.failure]，
 * 呼叫方 toast 对应字符串即可。BitmapFactory 用 inJustDecodeBounds 只读元数据不解码
 * 完整图像，走磁盘一次 IO 即可完成尺寸校验。
 */
object StickerUploadValidator {

    const val MAX_FILE_BYTES: Long = 1024 * 1024
    const val MAX_DIMENSION_PX: Int = 512

    enum class Format(val ext: String) {
        GIF("gif"), PNG("png"), JPEG("jpg"), WEBP("webp");
    }

    /** 校验结果：成功带 [Meta] 数据（宽/高/格式）；失败带 UI 层直接可用的 stringResId。 */
    data class Meta(val width: Int, val height: Int, val format: Format)

    sealed class Failure(val stringResId: Int) {
        object UnsupportedFormat : Failure(BaseR.string.str_sticker_format_unsupported)
        object TooLarge : Failure(BaseR.string.str_sticker_too_large)
        object DimensionTooLarge : Failure(BaseR.string.str_sticker_dim_too_large)
        object IoError : Failure(BaseR.string.str_sticker_upload_failed)
    }

    fun validate(file: File): Result<Meta> {
        if (!file.exists() || !file.isFile) return Result.failure(FailureException(Failure.IoError))
        if (file.length() > MAX_FILE_BYTES) return Result.failure(FailureException(Failure.TooLarge))

        val format = detectFormat(file) ?: return Result.failure(FailureException(Failure.UnsupportedFormat))

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val width = opts.outWidth
        val height = opts.outHeight
        if (width <= 0 || height <= 0) {
            // WebP animated 或损坏的图 → BitmapFactory 可能拿不到尺寸；宽容处理，让服务端兜底
            return Result.success(Meta(0, 0, format))
        }
        if (maxOf(width, height) > MAX_DIMENSION_PX) {
            return Result.failure(FailureException(Failure.DimensionTooLarge))
        }
        return Result.success(Meta(width, height, format))
    }

    /** 呼叫方拿 stringResId 用：`(result.exceptionOrNull() as? FailureException)?.failure?.stringResId` */
    class FailureException(val failure: Failure) : RuntimeException()

    /**
     * 读前 12 字节判 magic：
     *  GIF: "GIF87a" / "GIF89a"
     *  PNG: 89 50 4E 47 0D 0A 1A 0A
     *  JPEG: FF D8 FF
     *  WEBP: "RIFF" ???? "WEBP"（第 8-11 字节）
     */
    internal fun detectFormat(file: File): Format? {
        val buf = ByteArray(12)
        try {
            FileInputStream(file).use { it.read(buf) }
        } catch (e: IOException) {
            return null
        }
        return detectFormatFromBytes(buf)
    }

    /** 纯函数版本（测试用）：直接从字节数组识别。 */
    internal fun detectFormatFromBytes(buf: ByteArray): Format? {
        return when {
            startsWithAscii(buf, "GIF87a") || startsWithAscii(buf, "GIF89a") -> Format.GIF
            buf.size >= 8 && buf[0] == 0x89.toByte() && buf[1] == 0x50.toByte() &&
                buf[2] == 0x4E.toByte() && buf[3] == 0x47.toByte() -> Format.PNG
            buf.size >= 3 && buf[0] == 0xFF.toByte() && buf[1] == 0xD8.toByte() &&
                buf[2] == 0xFF.toByte() -> Format.JPEG
            buf.size >= 12 && startsWithAscii(buf, "RIFF") &&
                buf[8] == 'W'.code.toByte() && buf[9] == 'E'.code.toByte() &&
                buf[10] == 'B'.code.toByte() && buf[11] == 'P'.code.toByte() -> Format.WEBP
            else -> null
        }
    }

    private fun startsWithAscii(buf: ByteArray, prefix: String): Boolean {
        if (buf.size < prefix.length) return false
        for (i in prefix.indices) {
            if (buf[i] != prefix[i].code.toByte()) return false
        }
        return true
    }
}
