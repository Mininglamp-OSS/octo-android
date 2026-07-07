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

package com.chat.uikit.chat.search

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chat.base.adapter.WKFragmentStateAdapter
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.SearchChatContentMenu
import com.chat.base.ui.Theme
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.views.FullyGridLayoutManager
import com.chat.uikit.R
import com.chat.uikit.chat.search.channel.ChannelSearchAllFragment
import com.chat.uikit.chat.search.channel.ChannelSearchFileFragment
import com.chat.uikit.chat.search.channel.ChannelSearchMediaFragment
import com.chat.uikit.chat.search.channel.ChannelSearchMessageFragment
import com.chat.uikit.chat.search.channel.ChannelSearchViewModel
import com.chat.uikit.databinding.ActMessageRecordLayoutBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType

/**
 * 频道内搜索（聊天页"查找聊天记录"入口）。
 *
 * Phase 2.1 与微信对齐：
 *  - 空态：显示原有「按成员 / 日期 / 图片 / 文件」快捷过滤入口（[searchTypeLayout]）；
 *  - 用户输入关键词后：隐藏快捷入口，切换到 4-tab 结果页（[searchResultLayout]：全部 / 消息 / 媒体 / 文件）。
 *
 * 内部仍走 [ChannelSearchViewModel]：keyword 经 300ms debounce 后通过 SharedFlow 通知 Fragment 重置查询。
 * Intent 协议与 Phase 1 完全兼容：`channel_id` + `channel_type` (+ 可选 `keyword`)。
 */
class MessageRecordActivity : WKBaseActivity<ActMessageRecordLayoutBinding>() {
    private lateinit var channelID: String
    private var channelType: Byte = WKChannelType.PERSONAL
    private var initialKeyword: String = ""

    private lateinit var searchTypeAdapter: SearchTypeAdapter
    private var resultPagerReady = false

    private val viewModel: ChannelSearchViewModel by viewModels()

    override fun getViewBinding(): ActMessageRecordLayoutBinding {
        return ActMessageRecordLayoutBinding.inflate(layoutInflater)
    }

    override fun initPresenter() {
        channelID = intent.getStringExtra("channel_id")!!
        channelType = intent.getByteExtra("channel_type", WKChannelType.PERSONAL)
        initialKeyword = intent.getStringExtra("keyword") ?: ""
    }

    override fun initView() {
        Theme.setPressedBackground(wkVBinding.cancelTv)
        wkVBinding.searchIv.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(this, R.color.popupTextColor), PorterDuff.Mode.MULTIPLY
        )

        setupTypeFilterRow()
        // 4-tab 容器懒装配：只在用户首次输入关键词时才 attach，避免空态加载 Fragment 浪费。
    }

    private fun setupTypeFilterRow() {
        val channel = WKChannel().apply {
            channelID = this@MessageRecordActivity.channelID
            channelType = this@MessageRecordActivity.channelType
        }
        val list = EndpointManager.getInstance()
            .invokes<SearchChatContentMenu>(EndpointCategory.wkSearchChatContent, channel)
        var i = 0
        while (i < list.size) {
            if (list[i] == null || TextUtils.isEmpty(list[i].text)) {
                list.removeAt(i)
                continue
            }
            i++
        }
        searchTypeAdapter = SearchTypeAdapter(list)
        wkVBinding.typeRecyclerView.layoutManager = FullyGridLayoutManager(this, 3)
        wkVBinding.typeRecyclerView.adapter = searchTypeAdapter
    }

    private fun ensureResultPagerAttached() {
        if (resultPagerReady) return
        val fragments = listOf<Fragment>(
            ChannelSearchAllFragment.newInstance(channelID, channelType),
            ChannelSearchMessageFragment.newInstance(channelID, channelType),
            ChannelSearchMediaFragment.newInstance(channelID, channelType),
            ChannelSearchFileFragment.newInstance(channelID, channelType),
        )
        val titles = listOf(
            getString(R.string.channel_search_tab_all),
            getString(R.string.channel_search_tab_message),
            getString(R.string.channel_search_tab_media),
            getString(R.string.channel_search_tab_file),
        )
        wkVBinding.searchPager.adapter = WKFragmentStateAdapter(this, fragments)
        wkVBinding.searchPager.offscreenPageLimit = fragments.size
        TabLayoutMediator(wkVBinding.searchTabLayout, wkVBinding.searchPager) { tab, pos ->
            tab.text = titles[pos]
        }.attach()
        wkVBinding.searchTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTabIndex(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        resultPagerReady = true
    }

    override fun initListener() {
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this@MessageRecordActivity)
                return@setOnEditorActionListener true
            }
            false
        }
        wkVBinding.cancelTv.setOnClickListener { finish() }

        wkVBinding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val text = s.toString()
                // 进入过 4-tab 结果页之后就锁死；清空关键词不要再退回快捷入口，
                // 让媒体 / 文件 tab 继续按 supportsBrowseWithoutKeyword 浏览全部。
                if (text.isEmpty() && !resultPagerReady) {
                    showEmptyState()
                } else {
                    showResultState()
                }
                viewModel.setKeyword(text)
            }
        })

        searchTypeAdapter.setOnItemClickListener { adapter1: BaseQuickAdapter<*, *>, _, position ->
            val menu = adapter1.data[position] as SearchChatContentMenu?
            if (menu?.iClick != null) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                menu.iClick.onClick(channelID, channelType)
            }
        }

        if (!TextUtils.isEmpty(initialKeyword)) {
            wkVBinding.searchEt.setText(initialKeyword)
            wkVBinding.searchEt.setSelection(initialKeyword.length)
            // setText 已触发 TextWatcher，会走 showResultState + setKeyword
        } else {
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        wkVBinding.searchTypeLayout.visibility = View.VISIBLE
        wkVBinding.searchResultLayout.visibility = View.GONE
    }

    private fun showResultState() {
        wkVBinding.searchTypeLayout.visibility = View.GONE
        ensureResultPagerAttached()
        wkVBinding.searchResultLayout.visibility = View.VISIBLE
    }
}
