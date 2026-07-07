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
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.uikit.R
import com.chat.uikit.chat.search.channel.adapter.ChannelCombinedHitAdapter
import com.xinbida.wukongim.WKIM

/**
 * 全部 tab：调 `/_search_all`，混排消息与文件。
 * 关键词为空时不发起请求，显示空态提示用户输入关键词。
 */
class ChannelSearchAllFragment : BaseChannelSearchFragment() {

    private lateinit var adapter: ChannelCombinedHitAdapter

    override val emptyKeywordHintRes = R.string.channel_search_empty_keyword
    override val emptyResultHintRes = R.string.nodata

    override fun setupRecyclerView(recyclerView: RecyclerView) {
        adapter = ChannelCombinedHitAdapter(channelID, channelType, resolveChannelName())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.setOnItemClickListener { a: BaseQuickAdapter<*, *>, _, position ->
            when (val entry = a.getItem(position)) {
                is ChannelCombinedHitAdapter.Entry.Message -> jumpToChat(entry.hit.message_seq)
                is ChannelCombinedHitAdapter.Entry.File -> jumpToChat(entry.hit.message_seq)
                else -> Unit
            }
        }
    }

    override fun resetList() {
        adapter.setList(emptyList())
    }

    override fun executeSearch(keyword: String, cursor: String?, isReset: Boolean) {
        val req = ChannelSearchReq(
            channelType = channelType,
            channelId = channelID,
            keyword = keyword,
            pageSize = ChannelSearchReq.DEFAULT_PAGE_SIZE,
            cursor = cursor,
        )
        val seqSnapshot = requestSeq
        ChannelSearchModel.searchAll(req) { outcome ->
            if (seqSnapshot != requestSeq) return@searchAll
            if (!outcome.ok) {
                handleNonSuccess(outcome, isReset, keyword)
                return@searchAll
            }
            val list = outcome.data!!
            val entries = ChannelCombinedHitAdapter.toEntries(list.data)
            if (isReset) adapter.setList(entries) else if (entries.isNotEmpty()) adapter.addData(entries)
            updatePaginationState(
                hasMore = list.pagination.has_more,
                nextCursor = list.pagination.next_cursor,
                isEmpty = adapter.data.isEmpty(),
                isReset = isReset,
            )
        }
    }

    override fun executeLocalFallback(keyword: String) {
        // /_search_all 的本地兜底等价于消息部分回退；文件不在本地索引中。
        // 当前简化处理：直接展示"服务端不可用"banner + 空态，引导用户切到消息 tab 看本地结果。
        showOfflineBanner(true)
        adapter.setList(emptyList())
        showEmpty(getString(emptyResultHintRes))
        hasMore = false
        nextCursor = null
        binding.refreshLayout.setEnableLoadMore(false)
    }

    private fun resolveChannelName(): String {
        val ch = WKIM.getInstance().channelManager.getChannel(channelID, channelType) ?: return ""
        return ch.channelRemark?.takeIf { it.isNotEmpty() } ?: ch.channelName ?: ""
    }

    private fun jumpToChat(messageSeq: Long) {
        val orderSeq = WKIM.getInstance().msgManager.getMessageOrderSeq(
            messageSeq, channelID, channelType
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
        fun newInstance(channelID: String, channelType: Byte): ChannelSearchAllFragment =
            ChannelSearchAllFragment().apply { arguments = makeArgs(channelID, channelType) }
    }
}
