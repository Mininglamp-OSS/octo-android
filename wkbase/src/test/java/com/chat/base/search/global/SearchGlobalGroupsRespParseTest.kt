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
import com.chat.base.search.global.dto.SearchGlobalGroupsResp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 反序列化对齐 octo-server `search_global_groups.go` 的响应契约与
 * `04-aggregation-api-spec.md` §2.2 示例。字段名必须一一匹配，否则线上会静默丢字段。
 */
class SearchGlobalGroupsRespParseTest {

    @Test
    fun parse_full_response_with_group_thread_and_dm_buckets() {
        val json = """
            {
              "data": {
                "sequence": 1042,
                "query_id": "01J-abcd",
                "total_groups": 37,
                "total_groups_approx": true,
                "groups": [
                  {
                    "channel_id": "g_xxx",
                    "channel_type": 2,
                    "parent_group_no": "g_xxx",
                    "group_name": "项目群",
                    "match_count": 128,
                    "match_count_approx": true,
                    "latest_at": "2026-07-14T10:20:00+08:00",
                    "preview": [
                      {
                        "message_id": "m1",
                        "message_seq": 100,
                        "message_kind": "text",
                        "snippet": "hello <mark>foo</mark> world",
                        "sender_id": "u1",
                        "sender_name": "张三",
                        "sent_at": "2026-07-14T10:20:00+08:00",
                        "channel_id": "g_xxx",
                        "channel_type": 2
                      }
                    ]
                  },
                  {
                    "channel_id": "g_xxx____thr1",
                    "channel_type": 5,
                    "parent_group_no": "g_xxx",
                    "group_name": "项目群",
                    "thread_id": "g_xxx____thr1",
                    "thread_name": "需求评审",
                    "match_count": 12,
                    "match_count_approx": true,
                    "latest_at": "2026-07-13T09:00:00+08:00",
                    "preview": []
                  },
                  {
                    "channel_id": "u_peer123",
                    "channel_type": 1,
                    "group_name": "张三",
                    "match_count": 8,
                    "match_count_approx": true,
                    "latest_at": "2026-07-12T18:30:00+08:00",
                    "preview": []
                  }
                ]
              },
              "pagination": {
                "has_more": false,
                "next_cursor": ""
              }
            }
        """.trimIndent()

        val resp = JSON.parseObject(json, SearchGlobalGroupsResp::class.java)
        assertNotNull(resp)
        assertEquals(1042L, resp.data.sequence)
        assertEquals("01J-abcd", resp.data.query_id)
        assertEquals(37L, resp.data.total_groups)
        assertTrue(resp.data.total_groups_approx)
        assertEquals(3, resp.data.groups.size)

        // 群桶
        val group = resp.data.groups[0]
        assertEquals("g_xxx", group.channel_id)
        assertEquals(2, group.channel_type.toInt())
        assertEquals("g_xxx", group.parent_group_no)
        assertEquals("项目群", group.group_name)
        assertNull("群桶无 thread_id", group.thread_id)
        assertEquals(128L, group.match_count)
        assertTrue(group.match_count_approx)
        assertEquals("2026-07-14T10:20:00+08:00", group.latest_at)
        assertEquals(1, group.preview.size)
        val hit = group.preview[0]
        assertEquals("m1", hit.message_id)
        assertEquals("hello <mark>foo</mark> world", hit.snippet)
        assertEquals(2, hit.channel_type.toInt())

        // 子区桶
        val thread = resp.data.groups[1]
        assertEquals(5, thread.channel_type.toInt())
        assertEquals("g_xxx____thr1", thread.thread_id)
        assertEquals("需求评审", thread.thread_name)
        assertEquals("g_xxx", thread.parent_group_no)

        // DM 桶
        val dm = resp.data.groups[2]
        assertEquals(1, dm.channel_type.toInt())
        assertEquals("张三", dm.group_name)
        assertNull("DM 桶无 parent_group_no", dm.parent_group_no)
        assertNull("DM 桶无 thread_id", dm.thread_id)

        // pagination
        assertFalse(resp.pagination.has_more)
        assertEquals("", resp.pagination.next_cursor)
    }

    @Test
    fun parse_empty_groups() {
        val json = """
            {
              "data": {
                "sequence": 1,
                "query_id": "q1",
                "total_groups": 0,
                "total_groups_approx": true,
                "groups": []
              },
              "pagination": { "has_more": false, "next_cursor": "" }
            }
        """.trimIndent()
        val resp = JSON.parseObject(json, SearchGlobalGroupsResp::class.java)
        assertEquals(0L, resp.data.total_groups)
        assertTrue(resp.data.groups.isEmpty())
    }

    @Test
    fun parse_has_more_true_when_exceeding_max_groups() {
        val json = """
            {
              "data": {
                "sequence": 1,
                "query_id": "q1",
                "total_groups": 500,
                "total_groups_approx": true,
                "groups": []
              },
              "pagination": { "has_more": true, "next_cursor": "" }
            }
        """.trimIndent()
        val resp = JSON.parseObject(json, SearchGlobalGroupsResp::class.java)
        assertTrue("超过 maxGroups 时 has_more=true", resp.pagination.has_more)
    }
}
