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

package com.chat.uikit.chat.search.channel.adapter

import com.chat.base.search.channel.dto.MediaHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMediaHitAdapterTest {

    private fun hit(monthBucket: String, idx: Int = 0): MediaHit = MediaHit().apply {
        message_id = "m$idx"
        message_seq = idx.toLong()
        media_kind = "image"
        thumb_url = "https://example.com/$idx.jpg"
        sender_id = "u$idx"
        sent_at = "2026-${monthBucket.takeLast(2)}-15T10:00:00Z"
        month_bucket = monthBucket
    }

    @Test
    fun toEntries_returns_empty_for_empty_input() {
        assertTrue(ChannelMediaHitAdapter.toEntries(emptyList()).isEmpty())
    }

    @Test
    fun toEntries_inserts_header_before_each_distinct_month() {
        val hits = listOf(hit("2026-06", 1), hit("2026-06", 2), hit("2026-05", 3))
        val entries = ChannelMediaHitAdapter.toEntries(hits)
        // 4 项：[Header 06, Item, Item, Header 05, Item]
        assertEquals(5, entries.size)
        assertTrue(entries[0] is ChannelMediaHitAdapter.Entry.Header)
        assertEquals("2026-06", (entries[0] as ChannelMediaHitAdapter.Entry.Header).monthBucket)
        assertTrue(entries[1] is ChannelMediaHitAdapter.Entry.Item)
        assertTrue(entries[2] is ChannelMediaHitAdapter.Entry.Item)
        assertTrue(entries[3] is ChannelMediaHitAdapter.Entry.Header)
        assertEquals("2026-05", (entries[3] as ChannelMediaHitAdapter.Entry.Header).monthBucket)
        assertTrue(entries[4] is ChannelMediaHitAdapter.Entry.Item)
    }

    @Test
    fun toEntries_single_month_emits_one_header() {
        val hits = (1..3).map { hit("2026-06", it) }
        val entries = ChannelMediaHitAdapter.toEntries(hits)
        assertEquals(4, entries.size)
        assertEquals(1, entries.count { it is ChannelMediaHitAdapter.Entry.Header })
        assertEquals(3, entries.count { it is ChannelMediaHitAdapter.Entry.Item })
    }
}
