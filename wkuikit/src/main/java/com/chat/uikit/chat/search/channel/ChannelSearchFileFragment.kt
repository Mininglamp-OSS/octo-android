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

package com.chat.uikit.chat.search.channel

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.search.channel.ChannelSearchModel
import com.chat.base.search.channel.dto.ChannelSearchReq
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.uikit.R
import com.chat.uikit.chat.search.channel.adapter.ChannelFileHitAdapter
import com.xinbida.wukongim.WKIM

/**
 * 文件 tab：调 `/_search_files`。关键词为空时按时间倒序浏览全部文件。
 * 点击单条暂跳转到聊天页对应消息（与消息 tab 行为一致）；后续可改为直接走 [WKFileProvider] 预览/下载。
 */
class ChannelSearchFileFragment : BaseChannelSearchFragment() {

    private lateinit var adapter: ChannelFileHitAdapter

    override val emptyResultHintRes = R.string.nodata
    override val supportsBrowseWithoutKeyword: Boolean = true

    override fun setupRecyclerView(recyclerView: RecyclerView) {
        adapter = ChannelFileHitAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.setOnItemClickListener { a: BaseQuickAdapter<*, *>, _, position ->
            val hit = a.getItem(position) as? FileHit ?: return@setOnItemClickListener
            jumpToChat(hit)
        }
    }

    override fun resetList() {
        adapter.setList(emptyList())
    }

    override fun executeSearch(keyword: String, cursor: String?, isReset: Boolean) {
        val req = ChannelSearchReq(
            channelType = channelType,
            channelId = channelID,
            keyword = keyword.takeIf { it.isNotEmpty() },
            pageSize = ChannelSearchReq.DEFAULT_PAGE_SIZE,
            cursor = cursor,
        )
        val seqSnapshot = requestSeq
        ChannelSearchModel.searchFiles(req) { outcome ->
            if (seqSnapshot != requestSeq) return@searchFiles
            if (!outcome.ok) {
                handleNonSuccess(outcome, isReset, keyword)
                return@searchFiles
            }
            val list = outcome.data!!
            if (isReset) adapter.setList(list.data) else if (list.data.isNotEmpty()) adapter.addData(list.data)
            updatePaginationState(
                hasMore = list.pagination.has_more,
                nextCursor = list.pagination.next_cursor,
                isEmpty = adapter.data.isEmpty(),
                isReset = isReset,
            )
        }
    }

    private fun jumpToChat(hit: FileHit) {
        val orderSeq = WKIM.getInstance().msgManager.getMessageOrderSeq(
            hit.message_seq, channelID, channelType
        )
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(requireActivity())
        EndpointManager.getInstance().invoke(
            EndpointSID.chatView,
            ChatViewMenu(
                requireActivity(),
                channelID,
                channelType,
                orderSeq,
                false,
            )
        )
    }

    companion object {
        fun newInstance(channelID: String, channelType: Byte): ChannelSearchFileFragment =
            ChannelSearchFileFragment().apply { arguments = makeArgs(channelID, channelType) }
    }
}
