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

import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FilterDraft] 的筛选语义测试，口径对齐 iOS `WKChannelHistoryFilterVC`
 * 的 `toggleChatTypeRow:` / `toggleContentTypeAt:` / `toggleFileTypeAt:`。
 */
class FilterDraftTest {

    @Test
    fun `chat type single toggles on and off`() {
        val d = FilterDraft(GlobalSearchFilters())

        d.toggleChatTypeSingle()
        assertTrue(d.isChatTypeSingleSelected())
        assertEquals(listOf<Byte>(1), d.toFilters().channelTypes)

        // 关键回归：再点一次必须能取消（旧实现写死赋值，永远取消不掉）
        d.toggleChatTypeSingle()
        assertFalse(d.isChatTypeSingleSelected())
        assertNull("两行都不选 → channelTypes 省略 = 不限", d.toFilters().channelTypes)
    }

    @Test
    fun `chat type group writes 2 and 5 as an atomic pair`() {
        val d = FilterDraft(GlobalSearchFilters())

        d.toggleChatTypeGroup()
        assertTrue(d.isChatTypeGroupSelected())
        assertEquals(listOf<Byte>(2, 5), d.toFilters().channelTypes)

        d.toggleChatTypeGroup()
        assertFalse(d.isChatTypeGroupSelected())
        assertNull(d.toFilters().channelTypes)
    }

    /** 对齐 iOS：群聊选中判定为「2 或 5 任一存在」，只给 [2] 时也算选中。 */
    @Test
    fun `group considered selected when only type 2 present`() {
        val d = FilterDraft(GlobalSearchFilters(channelTypes = listOf(2)))
        assertTrue(d.isChatTypeGroupSelected())
        // 取消时 2 和 5 一并移除
        d.toggleChatTypeGroup()
        assertNull(d.toFilters().channelTypes)
    }

    @Test
    fun `both chat types can be selected simultaneously`() {
        val d = FilterDraft(GlobalSearchFilters())
        d.toggleChatTypeSingle()
        d.toggleChatTypeGroup()
        assertTrue(d.isChatTypeSingleSelected())
        assertTrue(d.isChatTypeGroupSelected())
        assertEquals(listOf<Byte>(1, 2, 5), d.toFilters().channelTypes)
    }

    /** legacy 单选字段无 UI，但必须原样透传，不能被往返静默清空。 */
    @Test
    fun `memberUid survives a draft round trip`() {
        val original = GlobalSearchFilters(memberUid = "u1")
        val roundTripped = FilterDraft(original).toFilters()
        assertEquals("u1", roundTripped.memberUid)
        assertFalse(roundTripped.isEmpty())
    }

    @Test
    fun `reset clears every editable dimension`() {
        val d = FilterDraft(
            GlobalSearchFilters(
                senderIds = listOf("s1"),
                memberUids = listOf("m1"),
                channelIds = listOf(GlobalChannelRef("c1", 2)),
                channelTypes = listOf(1),
                contentTypes = listOf(1),
                sentAtFrom = "2026-01-01",
                sentAtTo = "2026-02-01",
                fileExts = listOf("pdf"),
            ),
        )
        d.reset()
        val f = d.toFilters()
        assertTrue("reset 后应无任何实质筛选条件", f.isEmpty())
        assertTrue("reset 回到默认倒序", d.sortDescending)
    }

    /** 排序不是筛选条件：只改排序不应让 isEmpty 变 false（对齐 iOS hasEffectiveFilters 排除 sort）。 */
    @Test
    fun `sort alone does not count as an effective filter`() {
        val d = FilterDraft(GlobalSearchFilters())
        d.sortDescending = false
        val f = d.toFilters()
        assertTrue("sort 不计入实质筛选", f.isEmpty())
        assertTrue("但 sortTimeAsc 要如实透传", f.sortTimeAsc)
    }

    @Test
    fun `sort round trips through filters`() {
        val asc = FilterDraft(GlobalSearchFilters(sortTimeAsc = true))
        assertFalse(asc.sortDescending)
        assertTrue(asc.toFilters().sortTimeAsc)

        val desc = FilterDraft(GlobalSearchFilters(sortTimeAsc = false))
        assertTrue(desc.sortDescending)
        assertFalse(desc.toFilters().sortTimeAsc)
    }

    @Test
    fun `withChannelIds preserves sort and memberUid`() {
        val base = GlobalSearchFilters(
            memberUid = "u1",
            sentAtFrom = "2026-01-01",
            sortTimeAsc = true,
        )
        val merged = base.withChannelIds(listOf(GlobalChannelRef("c1", 2)))
        assertEquals("u1", merged.memberUid)
        assertEquals("2026-01-01", merged.sentAtFrom)
        assertTrue("下钻时排序须继续生效", merged.sortTimeAsc)
        assertEquals(1, merged.channelIds?.size)
    }
}
