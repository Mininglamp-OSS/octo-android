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
import com.chat.base.search.global.dto.SearchGlobalMessagesReq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SearchGlobalMessagesBodyBuilderTest {

    private fun channelIdsFilter(vararg refs: GlobalChannelRef): GlobalSearchFilters =
        GlobalSearchFilters(channelIds = refs.toList())

    @Test
    fun build_minimal_with_channel_id_only() {
        val req = SearchGlobalMessagesReq(
            keyword = "hello",
            filters = channelIdsFilter(GlobalChannelRef("g_xxx", 2)),
        )
        val body = SearchGlobalMessagesBodyBuilder.build(req)

        assertEquals("hello", body["keyword"])
        assertEquals("time_desc", body["sort"])
        assertEquals(20, body["page_size"])
        assertFalse("cursor 缺省时不应写入 body", body.containsKey("cursor"))
        val filters = body.getJSONObject("filters")
        val arr = filters.getJSONArray("channel_ids")
        assertEquals(1, arr.size)
        assertEquals("g_xxx", arr.getJSONObject(0)["channel_id"])
        assertEquals(2, arr.getJSONObject(0)["channel_type"])
    }

    @Test
    fun build_omits_empty_keyword_for_browse_mode() {
        val req = SearchGlobalMessagesReq(
            keyword = null,
            filters = channelIdsFilter(GlobalChannelRef("g_xxx", 2)),
        )
        val body = SearchGlobalMessagesBodyBuilder.build(req)
        assertFalse("空 keyword 是 browse 模式，不应写入", body.containsKey("keyword"))
    }

    @Test
    fun build_writes_cursor_byte_exact() {
        val cursor = "eyJ0cyI6MTcwMDAwMDAwMH0.abcd1234"
        val req = SearchGlobalMessagesReq(
            keyword = "x",
            filters = channelIdsFilter(GlobalChannelRef("g", 2)),
            cursor = cursor,
        )
        val body = SearchGlobalMessagesBodyBuilder.build(req)
        assertEquals("游标必须原样回传不得改动", cursor, body["cursor"])
    }

    @Test
    fun build_passes_sort_and_page_size() {
        val req = SearchGlobalMessagesReq(
            keyword = "x",
            filters = channelIdsFilter(GlobalChannelRef("g", 2)),
            sort = SearchGlobalMessagesReq.SORT_RELEVANCE,
            pageSize = 50,
        )
        val body = SearchGlobalMessagesBodyBuilder.build(req)
        assertEquals("relevance", body["sort"])
        assertEquals(50, body["page_size"])
    }

    @Test
    fun build_thread_channel_ref_serialised() {
        val req = SearchGlobalMessagesReq(
            keyword = "x",
            filters = channelIdsFilter(GlobalChannelRef("g_xxx____thr1", 5)),
        )
        val body = SearchGlobalMessagesBodyBuilder.build(req)
        val arr = body.getJSONObject("filters").getJSONArray("channel_ids")
        assertEquals("g_xxx____thr1", arr.getJSONObject(0)["channel_id"])
        assertEquals(5, arr.getJSONObject(0)["channel_type"])
    }

    @Test
    fun build_dm_channel_ref_serialised() {
        val req = SearchGlobalMessagesReq(
            keyword = "x",
            filters = channelIdsFilter(GlobalChannelRef("u_peer123", 1)),
        )
        val body = SearchGlobalMessagesBodyBuilder.build(req)
        val arr = body.getJSONObject("filters").getJSONArray("channel_ids")
        assertEquals("u_peer123", arr.getJSONObject(0)["channel_id"])
        assertEquals(1, arr.getJSONObject(0)["channel_type"])
    }

    @Test
    fun build_full_filters_combined() {
        val req = SearchGlobalMessagesReq(
            keyword = "x",
            filters = GlobalSearchFilters(
                senderIds = listOf("u1"),
                channelIds = listOf(GlobalChannelRef("g", 2)),
                contentTypes = listOf(1, 8),
                sentAtFrom = "2026-06-01",
                sentAtTo = "2026-06-30",
            ),
        )
        val body = SearchGlobalMessagesBodyBuilder.build(req)
        val filters = body.getJSONObject("filters")
        assertEquals(listOf("u1"), filters["sender_ids"])
        assertEquals(listOf(1, 8), filters["content_types"])
        assertEquals("2026-06-01", filters["sent_at_from"])
        assertEquals("2026-06-30", filters["sent_at_to"])
    }
}
