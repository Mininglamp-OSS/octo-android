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

package com.chat.uikit.search.remote.filter

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 筛选页"快速选择"日期预设 → [GlobalSearchFilters.sentAtFrom]/[sentAtTo]（`yyyy-MM-dd`）。
 *
 * 不用 `java.time`（项目 minSdk=23 且未开 coreLibraryDesugaring），用 [Calendar] 实现。
 * 纯函数，不依赖系统当前时间以外的任何状态，便于单测。
 *
 * 取值规则逐项对齐 iOS `WKChannelHistoryFilterVC.showPresetSheet` 的 `apply(days)`：
 *  - [Preset.TODAY]：起点为**今日 00:00**，不做 24h 回退。iOS 注释明确这是修过的 bug——
 *    只发 `yyyy-MM-dd` 的话，`now - 24h` 会拿到昨天的日期，让 from/to 跨两个自然日。
 *  - [Preset.LAST_7_DAYS] / [Preset.LAST_30_DAYS]：`now - days × 86400`（朴素时间间隔，
 *    非自然日运算），即 7 天得到 8 个自然日、30 天得到 31 个自然日的闭区间——与 iOS 一致。
 *  - [Preset.ALL_TIME]：两端都清空。
 */
object QuickDateRange {

    enum class Preset { TODAY, LAST_7_DAYS, LAST_30_DAYS, ALL_TIME }

    private const val FORMAT = "yyyy-MM-dd"
    private const val SECONDS_PER_DAY = 24 * 60 * 60L

    /** 返回 Pair(sentAtFrom, sentAtTo)；[Preset.ALL_TIME] 返回 Pair(null, null)（清空两字段）。 */
    fun resolve(preset: Preset, now: Calendar = Calendar.getInstance()): Pair<String?, String?> {
        if (preset == Preset.ALL_TIME) return null to null
        val to = format(now)
        val from = (now.clone() as Calendar).apply {
            when (preset) {
                // 今日 00:00（iOS startOfDayForDate）——同一自然日，格式化后与 to 相同。
                Preset.TODAY -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                Preset.LAST_7_DAYS -> addSeconds(this, -7 * SECONDS_PER_DAY)
                Preset.LAST_30_DAYS -> addSeconds(this, -30 * SECONDS_PER_DAY)
                Preset.ALL_TIME -> Unit
            }
        }
        return format(from) to to
    }

    private fun addSeconds(calendar: Calendar, seconds: Long) {
        calendar.timeInMillis = calendar.timeInMillis + seconds * 1000
    }

    private fun format(calendar: Calendar): String =
        SimpleDateFormat(FORMAT, Locale.US).format(calendar.time)
}
