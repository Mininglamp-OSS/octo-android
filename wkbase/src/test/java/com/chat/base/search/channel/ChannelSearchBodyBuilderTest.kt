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

import com.chat.base.search.channel.dto.AroundRequest
import com.chat.base.search.channel.dto.ChannelSearchReq
import com.chat.base.search.channel.dto.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSearchBodyBuilderTest {

    @Test
    fun build_minimal_request_only_required_fields() {
        val req = ChannelSearchReq(channelType = 2, channelId = "g1234", keyword = "hello")
        val body = ChannelSearchBodyBuilder.build(req)

        assertEquals(2, body["channel_type"])
        assertEquals("g1234", body["channel_id"])
        assertEquals("hello", body["keyword"])
        assertEquals("time_desc", body["sort"])
        assertEquals(20, body["page_size"])
        assertFalse("cursor 缺省时不应写入 body", body.containsKey("cursor"))
        assertFalse("filters 为空时不应写入 body", body.containsKey("filters"))
    }

    @Test
    fun build_omits_empty_keyword() {
        val req = ChannelSearchReq(
            channelType = 2,
            channelId = "g1",
            keyword = null,
            filters = SearchFilters(senderIds = listOf("u1")),
        )
        val body = ChannelSearchBodyBuilder.build(req)
        assertFalse(body.containsKey("keyword"))
    }

    @Test
    fun build_strips_empty_filters_block() {
        val req = ChannelSearchReq(
            channelType = 2,
            channelId = "g1",
            keyword = "x",
            filters = SearchFilters(senderIds = emptyList(), sentAtFrom = "", sentAtTo = ""),
        )
        val body = ChannelSearchBodyBuilder.build(req)
        assertFalse(body.containsKey("filters"))
    }

    @Test
    fun build_passes_filters_snake_case() {
        val req = ChannelSearchReq(
            channelType = 1,
            channelId = "u1@u2",
            keyword = "hello",
            filters = SearchFilters(
                senderIds = listOf("u1", "u2"),
                sentAtFrom = "2026-06-01",
                sentAtTo = "2026-06-30",
            ),
        )
        val body = ChannelSearchBodyBuilder.build(req)
        val filters = body.getJSONObject("filters")
        assertEquals(listOf("u1", "u2"), filters["sender_ids"])
        assertEquals("2026-06-01", filters["sent_at_from"])
        assertEquals("2026-06-30", filters["sent_at_to"])
    }

    @Test
    fun build_writes_cursor_byte_exact() {
        val cursor = "eyJ0cyI6MTcwMDAwMDAwMH0.abcd1234"   // 模拟服务端不透明游标
        val req = ChannelSearchReq(channelType = 2, channelId = "g1", keyword = "x", cursor = cursor)
        val body = ChannelSearchBodyBuilder.build(req)
        assertEquals("游标必须原样回传不得改动", cursor, body["cursor"])
    }

    @Test
    fun build_search_media_strips_keyword_via_allowKeyword_false() {
        val req = ChannelSearchReq(channelType = 2, channelId = "g1", keyword = "should-be-dropped")
        val body = ChannelSearchBodyBuilder.build(req, allowKeyword = false)
        assertFalse("media 端点 keyword 必须为空", body.containsKey("keyword"))
    }

    @Test
    fun buildAround_minimal() {
        val req = AroundRequest(channelType = 2, channelId = "g1", anchorMessageId = "12345")
        val body = ChannelSearchBodyBuilder.buildAround(req)
        assertEquals(2, body["channel_type"])
        assertEquals("g1", body["channel_id"])
        assertEquals("12345", body["anchor_message_id"])
        assertEquals(20, body["page_size"])
        assertNull(body["keyword"])
        assertNull(body["cursor"])
        assertNull(body["sort"])
    }
}
