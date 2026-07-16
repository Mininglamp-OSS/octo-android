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
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.global.dto.GroupBucket
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
import java.util.Objects
import kotlinx.coroutines.launch

class GlobalActivity : WKBaseActivity<ActGlobalLayoutBinding>() {
    lateinit var adapter: GlobalAdapter
    private var keyword: String = ""

    private val viewModel: GlobalSearchViewModel by viewModels()

    // 三段结果缓存。任一段变化时 [rebuildList] 组合成最终列表提交给 adapter，
    // 避免联系人/群段（老 /search/global 异步）和聊天记录段（新 L1 异步）之间的顺序竞态。
    private var friendsList: List<GlobalChannel> = emptyList()
    private var groupsList: List<GlobalChannel> = emptyList()
    private var searchShortcut: DataVO? = null
    private var chatRecords: List<DataVO> = emptyList()

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
        // L1 聚合端点无逐条翻页（服务端契约 §2.2 next_cursor 恒空），关闭 loadMore。
        // 命中群 > maxGroups 时通过 state.hasMore 由 [rebuildList] 追加"缩小范围"提示项。
        wkVBinding.refreshLayout.setEnableLoadMore(false)
        adapter = GlobalAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
        observeChatRecordsState()
    }

    override fun initListener() {
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance()
                    .hideSoftKeyboard(this@GlobalActivity)
                viewModel.triggerNow()
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
                keyword = content
                // 聊天记录段：服务端 L1 + 竞态防护（debounce/sequence/cancel）由 ViewModel 统管。
                viewModel.setKeyword(content)
                if (TextUtils.isEmpty(content)) {
                    friendsList = emptyList()
                    groupsList = emptyList()
                    searchShortcut = null
                    chatRecords = emptyList()
                    adapter.setList(ArrayList())
                } else {
                    // 联系人/群段仍走本地 + 老 /search/global；见 [loadContactsAndGroups]。
                    loadContactsAndGroups()
                }
            }

        })
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(refreshLayout: RefreshLayout) {
                // L1 无翻页；enableLoadMore=false 后此回调不会被触发，保留仅为 API 完整性。
                refreshLayout.finishLoadMore()
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

    /**
     * 拉联系人 + 群段：本地会话搜索合并老 /search/global（保留原来的 friends/groups 逻辑）。
     * 老接口的 messages 段不再消费（服务端 L1 已覆盖），避免同一条消息被展示两次。
     */
    private fun loadContactsAndGroups() {
        val contentType = ArrayList<Int>()
        contentType.add(WKContentType.WK_TEXT)
        contentType.add(WKContentType.WK_FILE)
        val req = GlobalSearchReq(0, keyword, "", 0, "", "", contentType, 1, 20, 0, 0)
        val requestKeyword = keyword
        GlobalSearchModel.search(req) { code, msg, resp ->
            wkVBinding.refreshLayout.finishRefresh()
            wkVBinding.refreshLayout.finishLoadMore()
            // 关键词已变或已清空时丢弃这次响应，避免过期结果覆盖当前段
            if (requestKeyword != keyword || TextUtils.isEmpty(keyword)) return@search
            if (code == HttpResponseCode.success && resp != null) {
                // 合并本地会话搜索，弥补服务端 space_id 对群组过滤不完整
                val localResult = searchLocalConversations(keyword)
                friendsList = mergeChannels(resp.friends, localResult.first)
                groupsList = mergeChannels(resp.groups, localResult.second)
                searchShortcut = DataVO(DataVO.SEARCH, null, null, keyword)
                rebuildList()
            } else if (code != HttpResponseCode.success) {
                // 老接口失败也要退化：仅用本地会话结果，保证联系人/群段不因服务端问题空掉
                val localResult = searchLocalConversations(keyword)
                friendsList = localResult.first
                groupsList = localResult.second
                searchShortcut = DataVO(DataVO.SEARCH, null, null, keyword)
                rebuildList()
                if (!msg.isNullOrEmpty()) showToast(msg)
            }
        }
    }

    /** 订阅 [GlobalSearchViewModel.state]：把 L1 [GroupBucket] 映射为 [DataVO.LOCAL_MSG]，
     * 失败/离线时自动回退到 IMSDK 本地聚合（与频道内搜索 FALLBACK_TO_LOCAL 语义一致）。 */
    private fun observeChatRecordsState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // 关键词已被用户清空 → 强制清聊天记录段
                    if (state.keyword.isEmpty()) {
                        if (chatRecords.isNotEmpty()) {
                            chatRecords = emptyList()
                            rebuildList()
                        }
                        return@collect
                    }
                    chatRecords = when (state.uiAction()) {
                        null -> state.groups.map { bucketToDataVO(it, state.keyword) }
                        ChannelSearchUiAction.FALLBACK_TO_LOCAL -> localMessagesAsDataVO(state.keyword)
                        // 其他错误（限流 / 未启用 / 权限 / 校验 / 通用错误）：清空聊天记录段
                        // 让用户明确知道服务端没有返回结果；对应 toast 由 [showErrorToast] 处理
                        else -> {
                            showErrorToast(state)
                            emptyList()
                        }
                    }
                    rebuildList()
                }
            }
        }
    }

    private fun bucketToDataVO(bucket: GroupBucket, keyword: String): DataVO {
        val displayName = when {
            bucket.channel_type == WKChannelType.COMMUNITY_TOPIC && !bucket.thread_name.isNullOrEmpty() ->
                bucket.thread_name!!
            bucket.group_name.isNotEmpty() -> bucket.group_name
            else -> bucket.channel_id
        }
        val gc = GlobalChannel().apply {
            channel_id = bucket.channel_id
            channel_type = bucket.channel_type
            channel_name = displayName
        }
        // 单条命中直接展示 snippet；多条则展示"约 N 条"折叠。
        // 服务端 snippet 带 <mark>...</mark>，客户端 Adapter 走 keyword 高亮，先剥掉 mark 标签。
        val text = if (bucket.match_count <= 1L && bucket.preview.isNotEmpty()) {
            stripMarkTags(bucket.preview[0].snippet)
        } else {
            val prefix = if (bucket.match_count_approx) "约 " else ""
            "$prefix${bucket.match_count} 条相关聊天记录"
        }
        // messageCount>1 且 orderSeq=0 → 点击落到 MessageRecordActivity（GlobalActivity.kt:170 分支）
        val count = bucket.match_count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return DataVO(DataVO.LOCAL_MSG, gc, null, text, keyword, count, 0L)
    }

    /** 服务端失败/离线兜底：本地 IMSDK 消息聚合，输出 shape 与 L1 结果一致。 */
    private fun localMessagesAsDataVO(keyword: String): List<DataVO> {
        val local = WKIM.getInstance().msgManager.search(keyword) ?: return emptyList()
        return local.mapNotNull { result ->
            val ch = result.wkChannel ?: return@mapNotNull null
            val fullChannel = WKIM.getInstance().channelManager.getChannel(ch.channelID, ch.channelType) ?: ch
            val displayName = fullChannel.channelRemark?.takeIf { it.isNotEmpty() }
                ?: fullChannel.channelName?.takeIf { it.isNotEmpty() }
                ?: fullChannel.channelID
                ?: ""
            val gc = GlobalChannel().apply {
                channel_id = fullChannel.channelID ?: ""
                channel_type = fullChannel.channelType
                channel_name = displayName
            }
            val snippet = if (result.messageCount == 1 && !result.searchableWord.isNullOrEmpty()) {
                snippetFromText(result.searchableWord, keyword, 40)
            } else {
                "${result.messageCount} 条相关聊天记录"
            }
            DataVO(DataVO.LOCAL_MSG, gc, null, snippet, keyword, result.messageCount, result.orderSeq)
        }
    }

    private fun stripMarkTags(text: String): String =
        text.replace("<mark>", "").replace("</mark>", "")

    private fun showErrorToast(state: GlobalSearchViewModel.State) {
        val msg = when (state.uiAction()) {
            ChannelSearchUiAction.RATE_LIMITED ->
                if (state.retryAfterSec > 0) "请求过于频繁，${state.retryAfterSec}s 后重试"
                else "请求过于频繁，请稍后重试"
            ChannelSearchUiAction.FEATURE_DISABLED -> "搜索功能未启用"
            ChannelSearchUiAction.BLOCK_NOT_FOUND -> null   // 不打扰
            ChannelSearchUiAction.VALIDATION_ERROR -> null  // 触发条件不满足是常态
            else -> state.errorMessage?.takeIf { it.isNotEmpty() } ?: "搜索失败，请重试"
        }
        msg?.let { showToast(it) }
    }

    /** 组合三段 + 分隔符，一次提交给 adapter。空字符串场景由 [afterTextChanged] 直接 setList(空) 处理。 */
    private fun rebuildList() {
        if (TextUtils.isEmpty(keyword)) {
            adapter.setList(ArrayList())
            return
        }
        val list = ArrayList<DataVO>()
        if (WKReader.isNotEmpty(friendsList)) {
            list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.contacts)))
            for (channel in friendsList) {
                list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        if (WKReader.isNotEmpty(groupsList)) {
            list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.group_chat)))
            for (channel in groupsList) {
                list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        searchShortcut?.let {
            list.add(it)
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        if (chatRecords.isNotEmpty()) {
            list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.chat_records)))
            list.addAll(chatRecords)
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        adapter.setList(list)
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
