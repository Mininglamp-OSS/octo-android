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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.search.channel.ChannelSearchOutcome
import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.channel.uiAction
import com.chat.base.utils.WKToastUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.FragChannelSearchListBinding
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener
import com.xinbida.wukongim.entity.WKChannelType
import kotlinx.coroutines.launch

/**
 * 频道内搜索 Fragment 通用骨架。各 tab 复用 [FragChannelSearchListBinding]
 * (refreshLayout + recyclerView + offline banner + empty state)。
 *
 * 子类需实现：
 *  - [setupRecyclerView]：layoutManager + adapter
 *  - [resetList] / [appendList]：把服务端返回的数据落进自己的 adapter
 *  - [executeSearch]：发起一次服务端请求；reset=true 表示新关键词，false 表示翻页
 *  - [executeLocalFallback]：服务端 503 时的本地兜底（仅消息/全部 tab 实现）
 *
 * Activity 通过 [ChannelSearchViewModel.queryEvents] 通知所有 Fragment 重置查询。
 */
abstract class BaseChannelSearchFragment : Fragment() {

    protected var _binding: FragChannelSearchListBinding? = null
    protected val binding get() = _binding!!

    protected lateinit var channelID: String
    protected var channelType: Byte = WKChannelType.PERSONAL

    protected val viewModel: ChannelSearchViewModel by lazy {
        ViewModelProvider(requireActivity())[ChannelSearchViewModel::class.java]
    }

    protected var nextCursor: String? = null
    protected var hasMore: Boolean = true
    protected var requestSeq: Long = 0L

    /** 关键词为空时的引导文案。子类可覆写。 */
    protected open val emptyKeywordHintRes: Int = R.string.channel_search_empty_keyword

    /** 命中关键词但服务端返回空时的文案。 */
    protected open val emptyResultHintRes: Int = R.string.nodata

    /**
     * 关键词为空时是否仍然向服务端请求"按时间倒序浏览"。
     * 媒体 / 文件 tab 覆写为 true，与 web/iOS 行为对齐——清空关键词后展示该频道的全部媒体/文件。
     */
    protected open val supportsBrowseWithoutKeyword: Boolean = false

    protected abstract fun setupRecyclerView(recyclerView: RecyclerView)

    protected abstract fun resetList()

    protected abstract fun executeSearch(keyword: String, cursor: String?, isReset: Boolean)

    /** 默认不兜底；消息相关 tab 覆写。 */
    protected open fun executeLocalFallback(keyword: String) {
        showOfflineBanner(true)
        resetList()
        showEmpty(getString(emptyResultHintRes))
        hasMore = false
        nextCursor = null
        binding.refreshLayout.setEnableLoadMore(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragChannelSearchListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        channelID = requireArguments().getString(ARG_CHANNEL_ID).orEmpty()
        channelType = requireArguments().getByte(ARG_CHANNEL_TYPE, WKChannelType.PERSONAL)

        setupRecyclerView(binding.recyclerView)

        binding.refreshLayout.setEnableRefresh(false)
        binding.refreshLayout.setEnableLoadMore(false)
        binding.refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(refreshLayout: RefreshLayout) = loadMore()
            override fun onRefresh(refreshLayout: RefreshLayout) {}
        })

        observeQueryEvents()
    }

    private fun observeQueryEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.queryEvents.collect { state ->
                    triggerReset(state.keyword)
                }
            }
        }
    }

    private fun triggerReset(keyword: String) {
        nextCursor = null
        hasMore = true
        showOfflineBanner(false)
        if (keyword.isEmpty() && !supportsBrowseWithoutKeyword) {
            // 仅消息相关 tab 在清空关键词时回到引导文案；媒体 / 文件 tab 通过 supportsBrowseWithoutKeyword
            // 显式声明无关键词浏览能力，避免出现"清空后台仍在拉取"的视觉跳动。
            resetList()
            showEmpty(getString(emptyKeywordHintRes))
            binding.refreshLayout.setEnableLoadMore(false)
            return
        }
        showEmpty(null)
        binding.refreshLayout.setEnableLoadMore(true)
        requestSeq++
        executeSearch(keyword, null, isReset = true)
    }

    private fun loadMore() {
        if (!hasMore || nextCursor.isNullOrEmpty()) {
            binding.refreshLayout.finishLoadMore()
            binding.refreshLayout.setEnableLoadMore(false)
            return
        }
        val keyword = viewModel.state.value.keyword
        requestSeq++
        executeSearch(keyword, nextCursor, isReset = false)
    }

    /** 子类在拿到 outcome 时调用，处理通用错误 + 本地兜底；返回 true 表示已处理。 */
    protected fun handleNonSuccess(
        outcome: ChannelSearchOutcome<*>,
        isReset: Boolean,
        keyword: String,
    ): Boolean {
        binding.refreshLayout.finishRefresh()
        binding.refreshLayout.finishLoadMore()
        return when (outcome.uiAction()) {
            ChannelSearchUiAction.FALLBACK_TO_LOCAL -> {
                if (isReset) {
                    executeLocalFallback(keyword)
                } else {
                    hasMore = false
                    binding.refreshLayout.setEnableLoadMore(false)
                }
                true
            }
            ChannelSearchUiAction.RATE_LIMITED -> {
                toast(R.string.channel_search_rate_limited)
                true
            }
            ChannelSearchUiAction.FEATURE_DISABLED -> {
                toast(R.string.channel_search_disabled)
                true
            }
            ChannelSearchUiAction.BLOCK_NOT_FOUND -> {
                toast(R.string.channel_search_not_found)
                true
            }
            ChannelSearchUiAction.VALIDATION_ERROR,
            ChannelSearchUiAction.GENERIC_ERROR -> {
                toast(R.string.channel_search_generic_error)
                true
            }
        }
    }

    /** 子类拿到 success 后调用，统一处理 cursor + loadMore 开关 + 空态。 */
    protected fun updatePaginationState(hasMore: Boolean, nextCursor: String, isEmpty: Boolean, isReset: Boolean) {
        binding.refreshLayout.finishRefresh()
        binding.refreshLayout.finishLoadMore()
        this.hasMore = hasMore
        this.nextCursor = nextCursor.takeIf { it.isNotEmpty() }
        binding.refreshLayout.setEnableLoadMore(hasMore && !this.nextCursor.isNullOrEmpty())
        if (isReset && isEmpty) {
            showEmpty(getString(emptyResultHintRes))
        } else if (isReset) {
            showEmpty(null)
        }
    }

    protected fun showOfflineBanner(visible: Boolean) {
        binding.offlineBannerTv.visibility = if (visible) View.VISIBLE else View.GONE
    }

    protected fun showEmpty(text: String?) {
        if (text.isNullOrEmpty()) {
            binding.emptyTv.visibility = View.GONE
        } else {
            binding.emptyTv.text = text
            binding.emptyTv.visibility = View.VISIBLE
        }
    }

    private fun toast(resId: Int) {
        WKToastUtils.getInstance().showToast(getString(resId))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_CHANNEL_ID = "channel_id"
        const val ARG_CHANNEL_TYPE = "channel_type"

        fun makeArgs(channelID: String, channelType: Byte): Bundle = Bundle().apply {
            putString(ARG_CHANNEL_ID, channelID)
            putByte(ARG_CHANNEL_TYPE, channelType)
        }
    }
}
