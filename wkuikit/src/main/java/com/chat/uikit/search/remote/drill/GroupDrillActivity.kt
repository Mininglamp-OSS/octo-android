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

package com.chat.uikit.search.remote.drill

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
import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.ui.Theme
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActGroupDrillLayoutBinding
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener
import com.xinbida.wukongim.WKIM
import kotlinx.coroutines.launch

/**
 * L2 深度浏览页。从 [com.chat.uikit.search.remote.GlobalActivity] 聊天记录段点某桶进入，
 * 展示该群/子区/DM 内所有关键词命中（服务端 `_search_global_messages`，
 * 传群 id 自动展开群+子区）。
 *
 * Intent extras:
 *  - "channel_id" String（必填）
 *  - "channel_type" Byte（必填）
 *  - "keyword" String（必填，非空）
 */
class GroupDrillActivity : WKBaseActivity<ActGroupDrillLayoutBinding>() {

    private val viewModel: GroupDrillViewModel by viewModels()

    private lateinit var adapter: GroupDrillAdapter
    private var channelId: String = ""
    private var channelType: Byte = 0

    // Toast 去重：同一个 errorCode 只弹一次，避免用户下一个按键触发 state 变化时重复打扰。
    // errorCode 变化（新一轮请求发起清空为 null 后再次出错）会重新弹。
    private var lastToastedErrorCode: String? = null

    override fun getViewBinding(): ActGroupDrillLayoutBinding =
        ActGroupDrillLayoutBinding.inflate(layoutInflater)

    override fun initPresenter() {
        channelId = intent.getStringExtra("channel_id") ?: ""
        channelType = intent.getByteExtra("channel_type", 0)
    }

    override fun initView() {
        Theme.setColorFilter(this, wkVBinding.searchIv, R.color.popupTextColor)
        ViewCompat.setTransitionName(wkVBinding.searchIv, "searchView")
        Theme.setPressedBackground(wkVBinding.cancelTv)

        wkVBinding.refreshLayout.setEnableRefresh(false)
        wkVBinding.refreshLayout.setEnableLoadMore(true)

        adapter = GroupDrillAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)

        val initialKeyword = intent.getStringExtra("keyword") ?: ""
        wkVBinding.searchEt.setText(initialKeyword)
        if (initialKeyword.isNotEmpty()) {
            wkVBinding.searchEt.setSelection(initialKeyword.length)
        }
        // 立即触发首屏搜索（skip debounce）
        viewModel.init(channelId, channelType, initialKeyword)

        SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, wkVBinding.searchEt)
        observeState()
    }

    override fun initListener() {
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                return@setOnEditorActionListener true
            }
            false
        }
        wkVBinding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setKeyword(s.toString())
            }
        })
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(refreshLayout: RefreshLayout) {
                viewModel.loadMore()
            }

            override fun onRefresh(refreshLayout: RefreshLayout) = Unit
        })
        wkVBinding.cancelTv.setOnClickListener {
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
            finish()
        }
        adapter.setOnItemClickListener { _, _, position ->
            val item = adapter.data.getOrNull(position) ?: return@setOnItemClickListener
            openHit(item)
        }
    }

    /**
     * 点击命中：跳 ChatActivity 尝试定位到具体消息。
     *
     * 本地有 orderSeq → 定位成功；本地无 → ChatActivity 打开但正文空
     * （已知历史问题，跟本次 L2 无关，追踪见 GlobalActivity 类似分支的 KDoc）。
     */
    private fun openHit(item: CombinedHit) {
        val (hitChannelId, hitChannelType, hitMessageSeq) = when {
            item.isMessage() && item.message != null -> {
                val m = item.message!!
                Triple(m.channel_id, m.channel_type, m.message_seq)
            }
            item.isFile() && item.file != null -> {
                val f = item.file!!
                Triple(f.channel_id, f.channel_type, f.message_seq)
            }
            else -> return
        }
        if (hitChannelId.isEmpty()) return
        val orderSeq = WKIM.getInstance().msgManager.getMessageOrderSeq(
            hitMessageSeq, hitChannelId, hitChannelType,
        )
        EndpointManager.getInstance().invoke(
            EndpointSID.chatView,
            ChatViewMenu(this, hitChannelId, hitChannelType, orderSeq, false),
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    wkVBinding.refreshLayout.finishLoadMore()
                    wkVBinding.refreshLayout.setEnableLoadMore(state.hasMore)

                    if (state.keyword.isEmpty()) {
                        adapter.setList(emptyList())
                        return@collect
                    }
                    adapter.setList(state.hits)

                    val action = state.uiAction()
                    if (action == null) {
                        // 新请求成功或刚发起时 errorCode 被清空 → 允许下次 toast
                        lastToastedErrorCode = null
                    } else if (state.errorCode != lastToastedErrorCode) {
                        lastToastedErrorCode = state.errorCode
                        when (action) {
                            ChannelSearchUiAction.RATE_LIMITED -> {
                                val msg = if (state.retryAfterSec > 0)
                                    "请求过于频繁，${state.retryAfterSec}s 后重试"
                                else "请求过于频繁，请稍后重试"
                                showToast(msg)
                            }
                            ChannelSearchUiAction.FEATURE_DISABLED -> showToast("搜索功能未启用")
                            ChannelSearchUiAction.FALLBACK_TO_LOCAL -> {
                                // L2 场景本地几乎不可能有匹配（L1 已经是跨群），仅 toast 提示
                                showToast("网络异常，请稍后重试")
                            }
                            ChannelSearchUiAction.BLOCK_NOT_FOUND,
                            ChannelSearchUiAction.VALIDATION_ERROR -> Unit
                            ChannelSearchUiAction.GENERIC_ERROR -> {
                                showToast(state.errorMessage?.takeIf { it.isNotEmpty() } ?: "搜索失败，请重试")
                            }
                        }
                    }
                }
            }
        }
    }
}
