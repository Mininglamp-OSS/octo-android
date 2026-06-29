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

package com.chat.base.search.channel

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * RFC3339 → epoch 秒。最小可用，不引入 java.time（项目 minSdk=23 且 coreLibraryDesugaring 未开）。
 *
 * 接受形态：`2026-06-29T10:30:00Z` / `2026-06-29T10:30:00+08:00` / 不带时区。
 * 解析失败返回 0。
 */
object Rfc3339 {
    private val OFFSET_COLON = Regex("([+-])(\\d{2}):(\\d{2})$")
    private val PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss",
    )

    fun toEpochSeconds(s: String?): Long {
        if (s.isNullOrEmpty()) return 0L
        // SimpleDateFormat 的 Z 模式接受 +0000，不接受 Z 字面量或 +08:00。先标准化。
        val normalized = s.replace("Z", "+0000")
            .let { OFFSET_COLON.replace(it) { m -> "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}" } }
        for (pattern in PATTERNS) {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            runCatching { sdf.parse(normalized)?.time?.div(1000) }.getOrNull()?.let { return it }
        }
        return 0L
    }
}
