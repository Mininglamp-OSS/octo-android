package com.chat.uikit.chat.provider

import java.io.File
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object HtmlPreviewLoader {
    const val MAX_BYTES = 5 * 1024 * 1024

    sealed class Result {
        data class Content(val html: String) : Result()
        object Missing : Result()
        object Empty : Result()
        object TooLarge : Result()
        object Unreadable : Result()
    }

    fun isHtmlDisplayName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".html") || lower.endsWith(".htm")
    }

    fun read(file: File): Result {
        if (!file.exists()) return Result.Missing
        if (!file.isFile || !file.canRead()) return Result.Unreadable
        if (file.length() == 0L) return Result.Empty
        if (file.length() > MAX_BYTES) return Result.TooLarge
        return try {
            FileInputStream(file).use { input ->
                val out = ByteArrayOutputStream(minOf(file.length().toInt(), MAX_BYTES))
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) return Result.TooLarge
                    out.write(buffer, 0, count)
                }
                if (total == 0) Result.Empty else {
                    val bytes = out.toByteArray()
                    val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
                    Result.Content(String(bytes, offset, bytes.size - offset, StandardCharsets.UTF_8))
                }
            }
        } catch (_: Exception) {
            Result.Unreadable
        }
    }
}
