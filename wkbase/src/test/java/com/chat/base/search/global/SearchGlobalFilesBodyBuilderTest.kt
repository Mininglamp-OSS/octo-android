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

    /**
     * 序列化器层面的防御性契约，**不对应任何用户可达路径**：
     * UI 侧 [com.chat.uikit.search.remote.FilesSearchViewModel] 的门禁要求关键词非空，
     * 所以 keyword 为空的请求当前不会真的发出（产品决定：不输关键词不返回结果）。
     *
     * 仍然锁定这个行为，是因为服务端契约允许省略 keyword，一旦将来放开门禁，
     * 序列化必须是"省略整个 key"而不是发 `"keyword": ""`——后者语义不同。
     * 若判定该测试在误导读者（断言了不可达能力），可以连同门禁一起删除，
     * 但不要只删门禁留下测试。
     */
    @Test
    fun build_omits_empty_keyword_defensively() {
        // null 与空串都必须省略，不能退化成 "keyword": ""
        for (keyword in listOf(null, "")) {
            val req = SearchGlobalFilesReq(
                keyword = keyword,
                filters = GlobalSearchFilters(fileExts = listOf("pdf")),
            )
            val body = SearchGlobalFilesBodyBuilder.build(req)

            assertFalse("keyword=$keyword 应省略整个 key", body.containsKey("keyword"))
            assertEquals("恰好 3 个顶层字段", setOf("page_size", "cursor", "filters"), body.keys)
            assertEquals("", body["cursor"])
            assertEquals(20, body["page_size"])
            assertFalse("文件端点不发 sort", body.containsKey("sort"))
            assertEquals(listOf("pdf"), body.getJSONObject("filters")["file_exts"])
        }
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
