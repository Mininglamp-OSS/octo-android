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

import org.junit.Assert.assertEquals
import org.junit.Test

class Rfc3339Test {

    @Test
    fun parses_utc_zulu() {
        // 2026-06-29T10:30:00Z → 2026-06-29 10:30:00 UTC → epoch 1782729000
        assertEquals(1782729000L, Rfc3339.toEpochSeconds("2026-06-29T10:30:00Z"))
    }

    @Test
    fun parses_positive_offset() {
        // 2026-06-29T18:30:00+08:00 == 2026-06-29T10:30:00Z
        assertEquals(1782729000L, Rfc3339.toEpochSeconds("2026-06-29T18:30:00+08:00"))
    }

    @Test
    fun parses_negative_offset() {
        // 2026-06-29T02:30:00-08:00 == 2026-06-29T10:30:00Z
        assertEquals(1782729000L, Rfc3339.toEpochSeconds("2026-06-29T02:30:00-08:00"))
    }

    @Test
    fun parses_naive_as_utc() {
        // 无时区时按 UTC 解释
        assertEquals(1782729000L, Rfc3339.toEpochSeconds("2026-06-29T10:30:00"))
    }

    @Test
    fun returns_zero_on_null_or_empty() {
        assertEquals(0L, Rfc3339.toEpochSeconds(null))
        assertEquals(0L, Rfc3339.toEpochSeconds(""))
    }

    @Test
    fun returns_zero_on_garbage() {
        assertEquals(0L, Rfc3339.toEpochSeconds("not-a-date"))
        assertEquals(0L, Rfc3339.toEpochSeconds("2026/06/29 10:30:00"))
    }
}
