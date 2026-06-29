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

import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.search.channel.ChannelSearchModel
import com.chat.base.search.channel.dto.ChannelSearchReq
import com.chat.base.search.channel.dto.MediaHit
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.views.FullyGridLayoutManager
import com.chat.base.views.pinnedsectionitemdecoration.PinnedHeaderItemDecoration
import com.chat.uikit.R
import com.chat.uikit.chat.search.channel.adapter.ChannelMediaHitAdapter
import com.xinbida.wukongim.WKIM

/**
 * 媒体 tab：调 `/_search_media`（keyword 必须为空）。3 列网格 + 月份 sticky header。
 * 服务端按 sent_at 倒序返回；adapter 按 month_bucket 切段插入 header。
 */
class ChannelSearchMediaFragment : BaseChannelSearchFragment() {

    private lateinit var adapter: ChannelMediaHitAdapter
    private val allMedia = ArrayList<MediaHit>()

    override val emptyResultHintRes = R.string.nodata

    override fun setupRecyclerView(recyclerView: RecyclerView) {
        val cellSize = (resources.displayMetrics.widthPixels - AndroidUtilities.dp(4f)) / GRID_SPAN
        val layoutManager = FullyGridLayoutManager(requireContext(), GRID_SPAN)
        recyclerView.layoutManager = layoutManager
        adapter = ChannelMediaHitAdapter(cellSize)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            PinnedHeaderItemDecoration.Builder(ChannelMediaHitAdapter.ITEM_TYPE_HEADER)
                .enableDivider(false).create()
        )
        adapter.setOnItemClickListener { a: BaseQuickAdapter<*, *>, _, position ->
            val entry = a.getItem(position) as? ChannelMediaHitAdapter.Entry.Item ?: return@setOnItemClickListener
            jumpToChat(entry.hit)
        }
    }

    override fun resetList() {
        allMedia.clear()
        adapter.setList(emptyList())
    }

    override fun executeSearch(keyword: String, cursor: String?, isReset: Boolean) {
        // _search_media 必须不带 keyword；服务端如果收到非空 keyword 会 400。Model 内部强制清空，这里仅说明意图。
        val req = ChannelSearchReq(
            channelType = channelType,
            channelId = channelID,
            keyword = null,
            pageSize = ChannelSearchReq.DEFAULT_PAGE_SIZE,
            cursor = cursor,
        )
        val seqSnapshot = requestSeq
        ChannelSearchModel.searchMedia(req) { outcome ->
            if (seqSnapshot != requestSeq) return@searchMedia
            if (!outcome.ok) {
                handleNonSuccess(outcome, isReset, keyword)
                return@searchMedia
            }
            val list = outcome.data!!
            if (isReset) allMedia.clear()
            allMedia.addAll(list.data)
            adapter.setList(ChannelMediaHitAdapter.toEntries(allMedia))
            updatePaginationState(
                hasMore = list.pagination.has_more,
                nextCursor = list.pagination.next_cursor,
                isEmpty = allMedia.isEmpty(),
                isReset = isReset,
            )
        }
    }

    private fun jumpToChat(hit: MediaHit) {
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
        private const val GRID_SPAN = 3
        fun newInstance(channelID: String, channelType: Byte): ChannelSearchMediaFragment =
            ChannelSearchMediaFragment().apply { arguments = makeArgs(channelID, channelType) }
    }
}
