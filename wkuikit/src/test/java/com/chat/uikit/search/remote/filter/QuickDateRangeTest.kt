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

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickDateRangeTest {

    private fun calendarOf(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `today returns same from and to`() {
        val now = calendarOf(2026, 6, 15)
        val (from, to) = QuickDateRange.resolve(QuickDateRange.Preset.TODAY, now)
        assertEquals("2026-06-15", from)
        assertEquals("2026-06-15", to)
    }

    /** 对齐 iOS `apply(7)` = `now - 7*86400`：得到 6/08~6/15（8 个自然日），不是 6/09。 */
    @Test
    fun `last 7 days matches ios interval math`() {
        val now = calendarOf(2026, 6, 15)
        val (from, to) = QuickDateRange.resolve(QuickDateRange.Preset.LAST_7_DAYS, now)
        assertEquals("2026-06-08", from)
        assertEquals("2026-06-15", to)
    }

    /** 对齐 iOS `apply(30)` = `now - 30*86400`：5/16~6/15。 */
    @Test
    fun `last 30 days matches ios interval math`() {
        val now = calendarOf(2026, 6, 15)
        val (from, to) = QuickDateRange.resolve(QuickDateRange.Preset.LAST_30_DAYS, now)
        assertEquals("2026-05-16", from)
        assertEquals("2026-06-15", to)
    }

    @Test
    fun `last 7 days crosses month boundary`() {
        val now = calendarOf(2026, 3, 3)
        val (from, to) = QuickDateRange.resolve(QuickDateRange.Preset.LAST_7_DAYS, now)
        assertEquals("2026-02-24", from)
        assertEquals("2026-03-03", to)
    }

    /** TODAY 取今日 00:00：即使 now 是当天任意时刻，from 也不能回退到昨天（iOS PR #64 修的 bug）。 */
    @Test
    fun `today does not roll back to yesterday near midnight`() {
        val now = Calendar.getInstance().apply {
            set(2026, 5, 15, 0, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (from, to) = QuickDateRange.resolve(QuickDateRange.Preset.TODAY, now)
        assertEquals("2026-06-15", from)
        assertEquals("2026-06-15", to)
    }

    @Test
    fun `all time clears both fields`() {
        val now = calendarOf(2026, 6, 15)
        val (from, to) = QuickDateRange.resolve(QuickDateRange.Preset.ALL_TIME, now)
        assertEquals(null, from)
        assertEquals(null, to)
    }
}
