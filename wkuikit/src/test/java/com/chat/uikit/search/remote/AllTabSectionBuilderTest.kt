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

package com.chat.uikit.search.remote

import com.chat.base.entity.GlobalChannel
import com.chat.base.search.channel.dto.FileHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllTabSectionBuilderTest {

    private fun channel(id: String) = GlobalChannel().apply { channel_id = id; channel_name = id }

    private fun fileHit(id: String) = FileHit().apply {
        message_id = id
        file_name = id
        sender_id = "u1"
        sent_at = "2026-01-01T00:00:00Z"
    }

    private fun build(
        friends: List<GlobalChannel> = emptyList(),
        groups: List<GlobalChannel> = emptyList(),
        shortcut: DataVO? = null,
        chats: List<DataVO> = emptyList(),
        files: List<FileHit> = emptyList(),
    ) = AllTabSectionBuilder.build(
        friendsList = friends,
        groupsList = groups,
        searchShortcut = shortcut,
        chatRecords = chats,
        filesPreview = files,
        contactsHeader = "联系人",
        groupsHeader = "群组",
        chatRecordsHeader = "聊天记录",
        filesHeader = "文件",
        moreText = "更多",
        contactsTab = 2,
        groupsTab = 3,
        chatsTab = 1,
        filesTab = 4,
    )

    @Test
    fun `section with 3 or fewer items has no more row`() {
        val list = build(friends = listOf(channel("a"), channel("b"), channel("c")))
        assertEquals(0, list.count { it.type == DataVO.MORE })
        // header + 3 channel rows + span
        assertEquals(5, list.size)
    }

    @Test
    fun `section with more than 3 items is truncated and gets a more row`() {
        val list = build(friends = (1..5).map { channel("c$it") })
        val channelRows = list.filter { it.type == DataVO.CHANNEL }
        assertEquals(3, channelRows.size)
        val moreRows = list.filter { it.type == DataVO.MORE }
        assertEquals(1, moreRows.size)
        assertEquals(2, moreRows[0].jumpTab)
    }

    @Test
    fun `groups section more row jumps to groups tab`() {
        val list = build(groups = (1..4).map { channel("g$it") })
        val moreRow = list.first { it.type == DataVO.MORE }
        assertEquals(3, moreRow.jumpTab)
    }

    @Test
    fun `chat records section counts buckets not messages and caps at 3`() {
        val buckets = (1..4).map { DataVO(DataVO.LOCAL_MSG, channel("ch$it"), null, "snippet") }
        val list = build(chats = buckets)
        val rendered = list.filter { it.type == DataVO.LOCAL_MSG }
        assertEquals(3, rendered.size)
        val moreRow = list.first { it.type == DataVO.MORE }
        assertEquals(1, moreRow.jumpTab)
    }

    @Test
    fun `files section renders FILE items and caps at 3 with more row jumping to files tab`() {
        val list = build(files = (1..5).map { fileHit("f$it") })
        val fileRows = list.filter { it.type == DataVO.FILE }
        assertEquals(3, fileRows.size)
        assertEquals("f1", fileRows[0].fileHit?.file_name)
        val moreRow = list.first { it.type == DataVO.MORE }
        assertEquals(4, moreRow.jumpTab)
    }

    @Test
    fun `empty section is omitted entirely`() {
        val list = build(friends = emptyList())
        assertTrue(list.none { it.type == DataVO.TEXT && it.text == "联系人" })
    }

    @Test
    fun `shortcut is inserted as-is without truncation or a more row`() {
        val shortcut = DataVO(DataVO.SEARCH, null, null, "find user")
        val list = build(
            groups = listOf(channel("g1")),
            shortcut = shortcut,
            chats = listOf(DataVO(DataVO.LOCAL_MSG, channel("ch1"), null, "snippet")),
        )
        val shortcutIndex = list.indexOf(shortcut)
        assertTrue(shortcutIndex >= 0)
        // 快捷入口前一个非 SPAN 元素应是群组段的最后一行（群组只有 1 条，不出 MORE）
        assertEquals(DataVO.CHANNEL, list[shortcutIndex - 2].type)
        // 快捷入口后一个应是 SPAN 分隔符，再之后是聊天记录段的 header
        assertEquals(DataVO.SPAN, list[shortcutIndex + 1].type)
        assertEquals(DataVO.TEXT, list[shortcutIndex + 2].type)
        assertEquals("聊天记录", list[shortcutIndex + 2].text)
    }

    @Test
    fun `section order is contacts groups shortcut chats files`() {
        val list = build(
            friends = listOf(channel("f1")),
            groups = listOf(channel("g1")),
            shortcut = DataVO(DataVO.SEARCH, null, null, "find user"),
            chats = listOf(DataVO(DataVO.LOCAL_MSG, channel("ch1"), null, "snippet")),
            files = listOf(fileHit("file1")),
        )
        val headerTexts = list.filter { it.type == DataVO.TEXT }.map { it.text }
        assertEquals(listOf("联系人", "群组", "聊天记录", "文件"), headerTexts)
    }

    @Test
    fun `all sections empty yields empty list`() {
        assertEquals(0, build().size)
    }

    @Test
    fun `no shortcut and no sections yields empty list without a dangling span`() {
        val list = build(shortcut = null)
        assertNull(list.firstOrNull())
    }
}
