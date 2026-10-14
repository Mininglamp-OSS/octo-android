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

/**
 * "全部" tab 的分段组装逻辑，抽成纯函数以便单测——原逻辑内联在 `GlobalActivity.rebuildList()`
 * 里，依赖 Activity/string resource 无法单测。
 *
 * 每个结果分段（联系人/群组/聊天记录/文件）截取前 [SECTION_PREVIEW_LIMIT] 条；原始数据量超过
 * 该值时在分段末尾追加一个 [DataVO.MORE] 行，点击后交给调用方切到对应分类 tab。快捷入口
 * （[searchShortcut]）不参与截断、不出"更多"，原样插入在群组段之后、聊天记录段之前。
 */
object AllTabSectionBuilder {

    const val SECTION_PREVIEW_LIMIT = 3

    fun build(
        friendsList: List<GlobalChannel>,
        groupsList: List<GlobalChannel>,
        searchShortcut: DataVO?,
        chatRecords: List<DataVO>,
        filesPreview: List<FileHit>,
        contactsHeader: String,
        groupsHeader: String,
        chatRecordsHeader: String,
        filesHeader: String,
        moreText: String,
        contactsTab: Int,
        groupsTab: Int,
        chatsTab: Int,
        filesTab: Int,
    ): List<DataVO> {
        val list = ArrayList<DataVO>()

        if (friendsList.isNotEmpty()) {
            list.add(DataVO(DataVO.TEXT, null, null, contactsHeader))
            for (channel in friendsList.take(SECTION_PREVIEW_LIMIT)) {
                list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
            }
            if (friendsList.size > SECTION_PREVIEW_LIMIT) {
                list.add(DataVO(DataVO.MORE, null, null, moreText, jumpTab = contactsTab))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }

        if (groupsList.isNotEmpty()) {
            list.add(DataVO(DataVO.TEXT, null, null, groupsHeader))
            for (channel in groupsList.take(SECTION_PREVIEW_LIMIT)) {
                list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
            }
            if (groupsList.size > SECTION_PREVIEW_LIMIT) {
                list.add(DataVO(DataVO.MORE, null, null, moreText, jumpTab = groupsTab))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }

        searchShortcut?.let {
            list.add(it)
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }

        if (chatRecords.isNotEmpty()) {
            list.add(DataVO(DataVO.TEXT, null, null, chatRecordsHeader))
            list.addAll(chatRecords.take(SECTION_PREVIEW_LIMIT))
            if (chatRecords.size > SECTION_PREVIEW_LIMIT) {
                list.add(DataVO(DataVO.MORE, null, null, moreText, jumpTab = chatsTab))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }

        if (filesPreview.isNotEmpty()) {
            list.add(DataVO(DataVO.TEXT, null, null, filesHeader))
            for (hit in filesPreview.take(SECTION_PREVIEW_LIMIT)) {
                list.add(DataVO(DataVO.FILE, null, null, "", fileHit = hit))
            }
            if (filesPreview.size > SECTION_PREVIEW_LIMIT) {
                list.add(DataVO(DataVO.MORE, null, null, moreText, jumpTab = filesTab))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }

        return list
    }
}
