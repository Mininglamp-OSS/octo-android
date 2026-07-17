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

package com.chat.base.search.global

import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.SearchGlobalGroupsReq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchGlobalGroupsBodyBuilderTest {

    @Test
    fun build_minimal_keyword_only() {
        val req = SearchGlobalGroupsReq(keyword = "hello", sequence = 42L)
        val body = SearchGlobalGroupsBodyBuilder.build(req)

        assertEquals("hello", body["keyword"])
        assertEquals(42L, body["sequence"])
        assertFalse("filters 缺省时不应写入 body", body.containsKey("filters"))
    }

    @Test
    fun build_omits_empty_keyword() {
        val req = SearchGlobalGroupsReq(
            keyword = null,
            sequence = 1L,
            filters = GlobalSearchFilters(senderIds = listOf("u1")),
        )
        val body = SearchGlobalGroupsBodyBuilder.build(req)
        assertFalse(body.containsKey("keyword"))
        assertTrue(body.containsKey("filters"))
    }

    @Test
    fun build_always_writes_sequence_even_zero() {
        val req = SearchGlobalGroupsReq(keyword = "x", sequence = 0L)
        val body = SearchGlobalGroupsBodyBuilder.build(req)
        assertEquals("sequence 恒回带，即便 0 也应写入", 0L, body["sequence"])
    }

    @Test
    fun build_strips_empty_filters_block() {
        val req = SearchGlobalGroupsReq(
            keyword = "x",
            sequence = 1L,
            filters = GlobalSearchFilters(
                senderIds = emptyList(),
                sentAtFrom = "",
                sentAtTo = "",
            ),
        )
        val body = SearchGlobalGroupsBodyBuilder.build(req)
        assertFalse(body.containsKey("filters"))
    }

    @Test
    fun build_passes_filters_snake_case() {
        val req = SearchGlobalGroupsReq(
            keyword = "hello",
            sequence = 100L,
            filters = GlobalSearchFilters(
                senderIds = listOf("u1", "u2"),
                memberUids = listOf("u3"),
                channelTypes = listOf(1, 2, 5),
                contentTypes = listOf(1, 2, 8),
                sentAtFrom = "2026-06-01",
                sentAtTo = "2026-06-30",
            ),
        )
        val body = SearchGlobalGroupsBodyBuilder.build(req)
        val filters = body.getJSONObject("filters")
        assertEquals(listOf("u1", "u2"), filters["sender_ids"])
        assertEquals(listOf("u3"), filters["member_uids"])
        // channel_types 序列化为 Int 列表（服务端 uint8 期望 JSON number）
        assertEquals(listOf(1, 2, 5), filters["channel_types"])
        assertEquals(listOf(1, 2, 8), filters["content_types"])
        assertEquals("2026-06-01", filters["sent_at_from"])
        assertEquals("2026-06-30", filters["sent_at_to"])
    }

    @Test
    fun build_channel_ids_serialised_as_ref_objects() {
        val req = SearchGlobalGroupsReq(
            keyword = "x",
            sequence = 1L,
            filters = GlobalSearchFilters(
                channelIds = listOf(
                    GlobalChannelRef("g_xxx", 2),
                    GlobalChannelRef("g_xxx____thr1", 5),
                ),
            ),
        )
        val body = SearchGlobalGroupsBodyBuilder.build(req)
        val filters = body.getJSONObject("filters")
        val arr = filters.getJSONArray("channel_ids")
        assertEquals(2, arr.size)
        val first = arr.getJSONObject(0)
        assertEquals("g_xxx", first["channel_id"])
        assertEquals(2, first["channel_type"])
        val second = arr.getJSONObject(1)
        assertEquals("g_xxx____thr1", second["channel_id"])
        assertEquals(5, second["channel_type"])
    }

    @Test
    fun build_omits_empty_memberUid_legacy() {
        val req = SearchGlobalGroupsReq(
            keyword = "x",
            sequence = 1L,
            filters = GlobalSearchFilters(memberUid = ""),
        )
        val body = SearchGlobalGroupsBodyBuilder.build(req)
        assertFalse(body.containsKey("filters"))
    }

    @Test
    fun build_writes_legacy_memberUid_when_present() {
        val req = SearchGlobalGroupsReq(
            keyword = "x",
            sequence = 1L,
            filters = GlobalSearchFilters(memberUid = "legacy_uid"),
        )
        val body = SearchGlobalGroupsBodyBuilder.build(req)
        assertEquals("legacy_uid", body.getJSONObject("filters")["member_uid"])
    }
}
