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

import com.alibaba.fastjson.JSON
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.CursorList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 反序列化对齐 octo-server `search_global_messages.go` 的响应契约。
 * L2 响应壳与 `_search_all` 完全一致 = `{data: List<SearchAllHit>, pagination}`，
 * 其中 SearchAllHit 就是 [CombinedHit]，此处只需覆盖 message + file 混排 + channel_type 字段。
 */
class SearchGlobalMessagesRespParseTest {

    private val respType by lazy {
        object : com.alibaba.fastjson.TypeReference<CursorList<CombinedHit>>() {}.type
    }

    @Test
    fun parse_mixed_message_and_file_hits_with_channel_type() {
        val json = """
            {
              "data": [
                {
                  "result_type": "message",
                  "sorted_at": "2026-07-01T09:59:12Z",
                  "message": {
                    "message_id": "m1",
                    "message_seq": 100,
                    "message_kind": "text",
                    "snippet": "hello <mark>foo</mark> world",
                    "sender_id": "u1",
                    "sender_name": "张三",
                    "sent_at": "2026-07-01T09:59:12Z",
                    "channel_id": "g_xxx",
                    "channel_type": 2
                  }
                },
                {
                  "result_type": "file",
                  "sorted_at": "2026-06-30T10:00:00Z",
                  "file": {
                    "message_id": "m2",
                    "message_seq": 99,
                    "file_name": "report.xlsx",
                    "file_size_bytes": 12345,
                    "file_ext": "xlsx",
                    "download_url": "https://cdn/report.xlsx",
                    "sender_id": "u2",
                    "sender_name": "李四",
                    "sent_at": "2026-06-30T10:00:00Z",
                    "channel_id": "g_xxx",
                    "channel_type": 2
                  }
                }
              ],
              "pagination": { "has_more": true, "next_cursor": "opaque-cursor-xyz" }
            }
        """.trimIndent()

        val resp: CursorList<CombinedHit>? = JSON.parseObject(json, respType)
        assertNotNull(resp)
        assertEquals(2, resp!!.data.size)

        val msgHit = resp.data[0]
        assertTrue(msgHit.isMessage())
        assertFalse(msgHit.isFile())
        assertNotNull(msgHit.message)
        assertNull(msgHit.file)
        assertEquals("m1", msgHit.message!!.message_id)
        assertEquals("g_xxx", msgHit.message!!.channel_id)
        assertEquals(2, msgHit.message!!.channel_type.toInt())
        assertEquals("hello <mark>foo</mark> world", msgHit.message!!.snippet)

        val fileHit = resp.data[1]
        assertTrue(fileHit.isFile())
        assertFalse(fileHit.isMessage())
        assertNotNull(fileHit.file)
        assertNull(fileHit.message)
        assertEquals("report.xlsx", fileHit.file!!.file_name)
        assertEquals(12345L, fileHit.file!!.file_size_bytes)
        assertEquals("xlsx", fileHit.file!!.file_ext)
        assertEquals("g_xxx", fileHit.file!!.channel_id)
        assertEquals(2, fileHit.file!!.channel_type.toInt())

        assertTrue(resp.pagination.has_more)
        assertEquals("opaque-cursor-xyz", resp.pagination.next_cursor)
    }

    @Test
    fun parse_empty_result() {
        val json = """{"data": [], "pagination": {"has_more": false, "next_cursor": ""}}"""
        val resp: CursorList<CombinedHit>? = JSON.parseObject(json, respType)
        assertNotNull(resp)
        assertTrue(resp!!.data.isEmpty())
        assertFalse(resp.pagination.has_more)
        assertEquals("", resp.pagination.next_cursor)
    }
}
