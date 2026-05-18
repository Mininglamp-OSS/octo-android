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

import android.content.Intent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.WKReader
import com.chat.uikit.R
import com.chat.uikit.databinding.ActGlobalLayoutBinding
import com.chat.base.entity.GlobalChannel
import com.chat.base.entity.GlobalSearchReq
import com.chat.base.search.GlobalSearchModel
import com.chat.uikit.chat.search.MessageRecordActivity
import com.chat.uikit.search.SearchUserActivity
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMessageSearchResult
import java.util.Objects

class GlobalActivity : WKBaseActivity<ActGlobalLayoutBinding>() {
    lateinit var adapter: GlobalAdapter
    private var keyword: String = ""
    private var page = 1
    override fun getViewBinding(): ActGlobalLayoutBinding {
        return ActGlobalLayoutBinding.inflate(layoutInflater)
    }

    override fun initView() {
        Theme.setColorFilter(this, wkVBinding.searchIv, R.color.popupTextColor)
        ViewCompat.setTransitionName(wkVBinding.searchIv, "searchView")
        Theme.setPressedBackground(wkVBinding.cancelTv)
        SoftKeyboardUtils.getInstance()
            .showSoftKeyBoard(this@GlobalActivity, wkVBinding.searchEt)

        wkVBinding.refreshLayout.setEnableRefresh(false)
        wkVBinding.refreshLayout.setEnableLoadMore(true)
        adapter = GlobalAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
    }

    override fun initListener() {
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance()
                    .hideSoftKeyboard(this@GlobalActivity)
                return@setOnEditorActionListener true
            }
            false
        }
        wkVBinding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                val content = s.toString()
                if (TextUtils.isEmpty(content)) {
                    adapter.setList(ArrayList())
                } else {
                    keyword = content
                    page = 1
                    getData(0)
                }
            }

        })
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(refreshLayout: RefreshLayout) {
                page++
                getData(1)
            }

            override fun onRefresh(refreshLayout: RefreshLayout) {}
        })
        wkVBinding.cancelTv.setOnClickListener { _ ->
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
            finish()
        }
        adapter.setOnItemClickListener { adapter, _, position ->
            val item = adapter.data[position]
            if (item is DataVO) {
                when (item.type) {
                    DataVO.CHANNEL -> {
                        EndpointManager.getInstance().invoke(
                            EndpointSID.chatView,
                            ChatViewMenu(
                                this@GlobalActivity,
                                item.channel!!.channel_id,
                                item.channel.channel_type,
                                0,
                                false
                            )
                        )
                    }

                    DataVO.SEARCH -> {
                        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                        val searchKey = Objects.requireNonNull(wkVBinding.searchEt.text).toString()
                        val intent = Intent(
                            this,
                            SearchUserActivity::class.java
                        )
                        intent.putExtra("searchKey", searchKey)
                        startActivity(intent)
                    }

                    DataVO.MESSAGE -> {
                        val orderSeq = WKIM.getInstance().msgManager.getMessageOrderSeq(
                            item.message!!.message_seq,
                            item.channel!!.channel_id,
                            item.channel.channel_type
                        )
                        EndpointManager.getInstance().invoke(
                            EndpointSID.chatView,
                            ChatViewMenu(
                                this@GlobalActivity,
                                item.channel.channel_id,
                                item.channel.channel_type,
                                orderSeq,
                                false
                            )
                        )
                    }

                    DataVO.LOCAL_MSG -> {
                        if (item.messageCount == 1 && item.orderSeq > 0) {
                            EndpointManager.getInstance().invoke(
                                EndpointSID.chatView,
                                ChatViewMenu(
                                    this@GlobalActivity,
                                    item.channel!!.channel_id,
                                    item.channel.channel_type,
                                    item.orderSeq,
                                    false
                                )
                            )
                        } else {
                            val intent = Intent(this@GlobalActivity, MessageRecordActivity::class.java)
                            intent.putExtra("channel_id", item.channel!!.channel_id)
                            intent.putExtra("channel_type", item.channel.channel_type)
                            intent.putExtra("keyword", item.keyword)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

    private fun getData(onlyMessage: Int) {
        val contentType = ArrayList<Int>()
        contentType.add(WKContentType.WK_TEXT)
        contentType.add(WKContentType.WK_FILE)
        val req = GlobalSearchReq(onlyMessage, keyword, "", 0, "", "", contentType, page, 20, 0, 0)
        GlobalSearchModel.search(req) { code, msg, resp ->
            wkVBinding.refreshLayout.finishRefresh()
            wkVBinding.refreshLayout.finishLoadMore()
            if (code == HttpResponseCode.success) {
                if (resp == null) {
                    return@search
                }
                if (page == 1) {
                    // 合并本地会话搜索结果（群组和联系人），弥补服务端 space_id 过滤不完整
                    val localResult = searchLocalConversations(keyword)
                    val allFriends = mergeChannels(resp.friends, localResult.first)
                    val allGroups = mergeChannels(resp.groups, localResult.second)

                    val list = ArrayList<DataVO>()
                    if (WKReader.isNotEmpty(allFriends)) {
                        list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.contacts)))
                        for (channel in allFriends) {
                            list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
                        }
                        list.add(DataVO(DataVO.SPAN, null, null, ""))
                    }
                    if (WKReader.isNotEmpty(allGroups)) {
                        list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.group_chat)))
                        for (channel in allGroups) {
                            list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
                        }
                        list.add(DataVO(DataVO.SPAN, null, null, ""))
                    }

                    list.add(DataVO(DataVO.SEARCH, null, null, keyword))
                    list.add(DataVO(DataVO.SPAN, null, null, ""))

                    // 本地消息内容搜索
                    val localMsgResults = WKIM.getInstance().msgManager.search(keyword)
                    if (WKReader.isNotEmpty(localMsgResults)) {
                        list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.chat_records)))
                        for (result in localMsgResults) {
                            val ch = result.wkChannel
                            val fullChannel = if (ch != null) {
                                WKIM.getInstance().channelManager.getChannel(ch.channelID, ch.channelType) ?: ch
                            } else null
                            val displayName = fullChannel?.channelRemark?.takeIf { it.isNotEmpty() }
                                ?: fullChannel?.channelName?.takeIf { it.isNotEmpty() }
                                ?: fullChannel?.channelID ?: ""
                            val gc = GlobalChannel().apply {
                                channel_id = fullChannel?.channelID ?: ""
                                channel_type = fullChannel?.channelType ?: 0
                                channel_name = displayName
                            }
                            val snippet = if (result.messageCount == 1 && !result.searchableWord.isNullOrEmpty()) {
                                snippetFromText(result.searchableWord, keyword, 40)
                            } else {
                                "${result.messageCount} 条相关聊天记录"
                            }
                            list.add(DataVO(DataVO.LOCAL_MSG, gc, null, snippet, keyword, result.messageCount, result.orderSeq))
                        }
                        list.add(DataVO(DataVO.SPAN, null, null, ""))
                    }

                    // API 远程消息搜索
                    if (WKReader.isNotEmpty(resp.messages)) {
                        if (!WKReader.isNotEmpty(localMsgResults)) {
                            list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.chat_records)))
                        }
                        for (message in resp.messages) {
                            list.add(DataVO(DataVO.MESSAGE, message.channel, message, ""))
                        }
                    }
                    adapter.setList(list)
                } else {
                    if (WKReader.isNotEmpty(resp.messages)) {
                        val list = ArrayList<DataVO>()
                        for (message in resp.messages) {
                            val messageVO =
                                DataVO(DataVO.MESSAGE, message.channel, message, "")
                            list.add(messageVO)
                        }
                        adapter.addData(list)
                    } else {
                        wkVBinding.refreshLayout.setEnableLoadMore(false)
                    }
                }
            } else {
                showToast(msg)
            }
        }
    }

    private fun snippetFromText(text: String, keyword: String, maxLength: Int): String {
        val idx = text.lowercase().indexOf(keyword.lowercase())
        if (idx < 0) return if (text.length > maxLength) "${text.substring(0, maxLength)}..." else text
        val radius = (maxLength - keyword.length) / 2
        val start = maxOf(0, idx - radius)
        val end = minOf(text.length, idx + keyword.length + radius)
        var snippet = text.substring(start, end)
        if (start > 0) snippet = "...$snippet"
        if (end < text.length) snippet = "$snippet..."
        return snippet
    }

    /**
     * 从本地会话列表按关键词搜索联系人和群组（与 iOS searchLocalConversations 一致）。
     * 服务端 space_id 过滤对群组支持不完整，本地搜索作为补充。
     * @return Pair<联系人列表, 群组列表>
     */
    private fun searchLocalConversations(keyword: String): Pair<List<GlobalChannel>, List<GlobalChannel>> {
        val friends = mutableListOf<GlobalChannel>()
        val groups = mutableListOf<GlobalChannel>()
        val conversations = WKIM.getInstance().conversationManager.all ?: return Pair(friends, groups)
        val lowerKeyword = keyword.lowercase()
        for (conv in conversations) {
            if (conv.channelType == WKChannelType.COMMUNITY_TOPIC) continue
            val channel = conv.getWkChannel() ?: continue
            val displayName = when {
                !channel.channelRemark.isNullOrEmpty() -> channel.channelRemark
                !channel.channelName.isNullOrEmpty() -> channel.channelName
                else -> continue
            }
            if (!displayName.lowercase().contains(lowerKeyword)) continue
            val gc = GlobalChannel().apply {
                channel_id = conv.channelID ?: ""
                channel_type = conv.channelType
                channel_name = displayName
                channel_remark = channel.channelRemark ?: ""
            }
            if (conv.channelType == WKChannelType.GROUP) {
                groups.add(gc)
            } else {
                friends.add(gc)
            }
        }
        return Pair(friends, groups)
    }

    /**
     * 合并服务端和本地搜索结果，按 channel_id 去重
     */
    private fun mergeChannels(remote: List<GlobalChannel>?, local: List<GlobalChannel>): List<GlobalChannel> {
        val result = mutableListOf<GlobalChannel>()
        val ids = mutableSetOf<String>()
        remote?.forEach {
            if (ids.add(it.channel_id)) result.add(it)
        }
        local.forEach {
            if (ids.add(it.channel_id)) result.add(it)
        }
        return result
    }
}