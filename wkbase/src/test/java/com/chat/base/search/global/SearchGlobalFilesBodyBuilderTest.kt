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

import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.SearchGlobalFilesReq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchGlobalFilesBodyBuilderTest {

    @Test
    fun build_minimal_with_file_exts_only() {
        val req = SearchGlobalFilesReq(
            keyword = "hello",
            filters = GlobalSearchFilters(fileExts = listOf("pdf", "docx")),
        )
        val body = SearchGlobalFilesBodyBuilder.build(req)

        assertEquals("hello", body["keyword"])
        assertEquals(20, body["page_size"])
        // 对齐 iOS `body[@"cursor"] = cursor.length > 0 ? cursor : @""`：首页恒写入空串。
        assertEquals("首页 cursor 为空串而非省略", "", body["cursor"])
        val filters = body.getJSONObject("filters")
        assertEquals(listOf("pdf", "docx"), filters["file_exts"])
    }

    @Test
    fun build_omits_empty_keyword_for_browse_mode() {
        val req = SearchGlobalFilesReq(
            keyword = null,
            filters = GlobalSearchFilters(fileExts = listOf("pdf")),
        )
        val body = SearchGlobalFilesBodyBuilder.build(req)
        assertFalse("空 keyword 是 browse 模式，不应写入", body.containsKey("keyword"))
    }

    /**
     * 浏览态完整 body 形状，逐字段对齐 iOS
     * `WKGlobalSearchV2API +searchGlobalFilesWithKeyword:filter:cursor:`：
     * keyword 省略、page_size/cursor/filters 恒在、无 sort 字段。
     */
    @Test
    fun build_browse_mode_body_shape_matches_ios() {
        val req = SearchGlobalFilesReq(
            keyword = "",
            filters = GlobalSearchFilters(fileExts = listOf("pdf")),
        )
        val body = SearchGlobalFilesBodyBuilder.build(req)

        assertEquals("恰好 3 个顶层字段", setOf("page_size", "cursor", "filters"), body.keys)
        assertFalse("空串 keyword 也要省略", body.containsKey("keyword"))
        assertEquals("", body["cursor"])
        assertEquals(20, body["page_size"])
        assertFalse("文件端点不发 sort", body.containsKey("sort"))
        assertEquals(listOf("pdf"), body.getJSONObject("filters")["file_exts"])
    }

    @Test
    fun build_writes_empty_filters_object_when_empty() {
        val req = SearchGlobalFilesReq(keyword = "x", filters = GlobalSearchFilters())
        val body = SearchGlobalFilesBodyBuilder.build(req)
        // 对齐 iOS `body[@"filters"] = filters`（filter 为 nil 时取 @{}）：恒写入，全空为 {}。
        assertTrue("filters 恒写入", body.containsKey("filters"))
        assertTrue("filters 全空时为空对象", body.getJSONObject("filters").isEmpty())
    }

    @Test
    fun build_writes_cursor_byte_exact() {
        val cursor = "eyJ0cyI6MTcwMDAwMDAwMH0.abcd1234"
        val req = SearchGlobalFilesReq(
            keyword = "x",
            filters = GlobalSearchFilters(fileExts = listOf("pdf")),
            cursor = cursor,
        )
        val body = SearchGlobalFilesBodyBuilder.build(req)
        assertEquals("游标必须原样回传不得改动", cursor, body["cursor"])
    }

    @Test
    fun build_passes_page_size() {
        val req = SearchGlobalFilesReq(
            keyword = "x",
            filters = GlobalSearchFilters(fileExts = listOf("pdf")),
            pageSize = 50,
        )
        val body = SearchGlobalFilesBodyBuilder.build(req)
        assertEquals(50, body["page_size"])
    }
}
