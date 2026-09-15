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
import com.chat.base.entity.GlobalChannel
import com.chat.base.entity.GlobalMessage
import com.chat.base.search.channel.ChannelSearchModel
import com.chat.base.search.channel.dto.ChannelSearchReq
import com.chat.base.search.channel.toGlobalMessage
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.WKReader
import com.chat.uikit.R
import com.chat.uikit.chat.search.SearchMessageAdapter
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKMsg

/**
 * 消息 tab：仅文本/转发命中。服务端 503/网络 → 回退到 IMSDK 本地搜索 + 顶部 banner。
 */
class ChannelSearchMessageFragment : BaseChannelSearchFragment() {

    private lateinit var adapter: SearchMessageAdapter

    override val emptyKeywordHintRes = R.string.channel_search_empty_keyword
    override val emptyResultHintRes = R.string.nodata

    override fun setupRecyclerView(recyclerView: RecyclerView) {
        adapter = SearchMessageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.setOnItemClickListener { a: BaseQuickAdapter<*, *>, _, position ->
            val gm = a.getItem(position) as? GlobalMessage ?: return@setOnItemClickListener
            jumpToChat(gm)
        }
    }

    override fun resetList() {
        adapter.setList(emptyList())
    }

    override fun executeSearch(keyword: String, cursor: String?, isReset: Boolean) {
        adapter.keyword = keyword
        val req = ChannelSearchReq(
            channelType = channelType,
            channelId = channelID,
            keyword = keyword,
            pageSize = ChannelSearchReq.DEFAULT_PAGE_SIZE,
            cursor = cursor,
        )
        val seqSnapshot = requestSeq
        ChannelSearchModel.searchMessages(req) { outcome ->
            if (seqSnapshot != requestSeq) return@searchMessages
            if (!outcome.ok) {
                handleNonSuccess(outcome, isReset, keyword)
                return@searchMessages
            }
            showOfflineBanner(false)
            val list = outcome.data!!
            val mapped = list.data.map { it.toGlobalMessage(channelID, channelType) }
            if (isReset) adapter.setList(mapped) else if (mapped.isNotEmpty()) adapter.addData(mapped)
            updatePaginationState(
                hasMore = list.pagination.has_more,
                nextCursor = list.pagination.next_cursor,
                isEmpty = adapter.data.isEmpty(),
                isReset = isReset,
            )
        }
    }

    override fun executeLocalFallback(keyword: String) {
        showOfflineBanner(true)
        val localMsgs = WKIM.getInstance().msgManager.searchWithChannel(keyword, channelID, channelType)
        if (!WKReader.isNotEmpty(localMsgs)) {
            adapter.setList(emptyList())
            showEmpty(getString(emptyResultHintRes))
            hasMore = false
            nextCursor = null
            binding.refreshLayout.setEnableLoadMore(false)
            return
        }
        val mapped = ArrayList<GlobalMessage>(localMsgs.size)
        for (msg in localMsgs) {
            val gm = GlobalMessage()
            gm.message_seq = msg.messageSeq.toLong()
            gm.from_uid = msg.fromUID ?: ""
            gm.sender_name = resolveSenderName(msg)
            gm.timestamp = msg.timestamp
            val payload = HashMap<String, Any>()
            payload["type"] = msg.type
            payload["content"] = msg.searchableWord?.takeIf { it.isNotEmpty() }
                ?: msg.baseContentMsgModel?.getSearchableWord() ?: ""
            gm.payload = payload
            gm.channel = GlobalChannel().apply {
                channel_id = channelID
                channel_type = channelType
            }
            mapped.add(gm)
        }
        adapter.setList(mapped)
        showEmpty(null)
        hasMore = false
        nextCursor = null
        binding.refreshLayout.setEnableLoadMore(false)
    }

    /**
     * 群内发送人展示名，走仓库规范五级链路（对齐 [com.chat.base.msgitem.WKChatBaseProvider] 聊天页逻辑）：
     * from.channelRemark → memberOfFrom.remark → memberOfFrom.memberRemark → from.channelName → memberOfFrom.memberName。
     *
     * 群成员的备注/群昵称存于 channel_members 表，与该成员是否有 PERSONAL 频道缓存行无关——
     * 只查 PERSONAL 频道（2 参数 getChannel）拿不到这两级，本地没同步过该成员资料时会退化成裸 UID。
     * searchWithChannel 已经把 msg.from（PERSONAL 频道）和 msg.memberOfFrom（群成员）查好挂在消息上，
     * 这里直接复用，不再重复查库，避免在主线程未命中缓存时触发同步 DB 查询。
     */
    private fun resolveSenderName(msg: WKMsg): String {
        val uid = msg.fromUID ?: ""
        val from = msg.from
        val member = msg.memberOfFrom
        return from?.channelRemark?.takeIf { it.isNotEmpty() }
            ?: member?.remark?.takeIf { it.isNotEmpty() }
            ?: member?.memberRemark?.takeIf { it.isNotEmpty() }
            ?: from?.channelName?.takeIf { it.isNotEmpty() }
            ?: member?.memberName?.takeIf { it.isNotEmpty() }
            ?: uid
    }

    private fun jumpToChat(gm: GlobalMessage) {
        val orderSeq = WKIM.getInstance().msgManager.getMessageOrderSeq(
            gm.message_seq, gm.channel.channel_id, gm.channel.channel_type
        )
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(requireActivity())
        EndpointManager.getInstance().invoke(
            EndpointSID.chatView,
            ChatViewMenu(
                requireActivity(),
                gm.channel.channel_id,
                gm.channel.channel_type,
                orderSeq,
                false,
            )
        )
    }

    companion object {
        fun newInstance(channelID: String, channelType: Byte): ChannelSearchMessageFragment =
            ChannelSearchMessageFragment().apply { arguments = makeArgs(channelID, channelType) }
    }
}
