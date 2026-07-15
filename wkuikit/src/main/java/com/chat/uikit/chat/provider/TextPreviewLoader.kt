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

package com.chat.uikit.chat.provider

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Bounded 文本文件读取。避免 [File.readText] 全文加载后再截断带来的 OOM/ANR
 * （聊天允许 100 MB 文件，直接 readText 一个 100 MB 的 .log 会 OOM/ANR）。
 *
 * 只读到 [maxChars] 字符就停止；若确实还有内容，末尾追加"文件过大"提示。
 * 不做线程调度——呼叫方决定跑主线程还是后台。bounded 后 50k 字符 (≈200KB UTF-8)
 * 磁盘 IO 通常在 10ms 以内，短文件场景主线程调用可接受。
 */
object TextPreviewLoader {

    private const val BUF_SIZE = 4096

    /** 追加在文本末尾提示被截断的模板。占位符是最终字符数（[maxChars]）。 */
    private const val TRUNCATE_HINT_ZH = "\n\n... (文件过大，仅显示前%d字符)"

    /**
     * @param file 要读取的文件；不存在或读取抛异常时返回已累积的 [String]（可能为空）
     * @param maxChars 上限字符数（Unicode code unit，即 Java `char`）
     * @return 最多 [maxChars] 字符的内容，若被截断则末尾追加提示
     */
    fun readBounded(file: File, maxChars: Int): String {
        if (!file.exists() || maxChars <= 0) return ""
        val sb = StringBuilder()
        val buf = CharArray(BUF_SIZE)
        var truncated = false
        try {
            InputStreamReader(FileInputStream(file), Charsets.UTF_8).use { reader ->
                while (sb.length < maxChars) {
                    val toRead = minOf(buf.size, maxChars - sb.length)
                    val n = reader.read(buf, 0, toRead)
                    if (n <= 0) break
                    sb.append(buf, 0, n)
                }
                // 探测后面是否还有内容——决定是否要追加"文件过大"提示。
                if (sb.length >= maxChars && reader.read(buf, 0, 1) > 0) {
                    truncated = true
                }
            }
        } catch (_: Exception) {
            // 静默失败：返回目前累积到的内容（呼叫方通常以空串处理）
        }
        if (truncated) sb.append(TRUNCATE_HINT_ZH.format(maxChars))
        return sb.toString()
    }
}
