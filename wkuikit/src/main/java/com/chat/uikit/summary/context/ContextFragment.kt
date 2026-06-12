/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.context

import android.content.Intent
import android.content.res.Configuration
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.chat.base.base.WKBaseFragment
import com.chat.uikit.R
import com.chat.uikit.databinding.FragContextLayoutBinding
import com.chat.uikit.summary.list.SmartSummaryListActivity

/**
 * 上下文 Tab 主页, 1:1 对齐 iOS [OctoContextEntryVC] + WKNavigationBar(largeTitle):
 *   - 顶部 statusBarSpacer 跟随 WindowInsets (= iOS UIApplication statusBarFrame)
 *   - 59dp 高 title bar (iOS navHeight 44 + largeTitle 增量 15)
 *   - 4 列入口网格, 当前唯一入口 "智能总结" -> [SmartSummaryListActivity]
 *
 * 替换原 ContactsFragment 在 tab[1] 的位置 (联系人已并入"我的" tab)。
 */
class ContextFragment : WKBaseFragment<FragContextLayoutBinding>() {

    private lateinit var adapter: ContextEntryAdapter

    override fun getViewBinding(): FragContextLayoutBinding =
        FragContextLayoutBinding.inflate(layoutInflater)

    override fun initView() {
        applyStatusBarInset()

        val entries = buildEntries()
        adapter = ContextEntryAdapter(entries)
        adapter.onClick { entry -> onEntryTapped(entry.itemId) }
        wkVBinding.entriesRv.layoutManager = GridLayoutManager(requireContext(), 4)
        wkVBinding.entriesRv.adapter = adapter

        // SmartRefreshLayout 仅用 OverScroll 模拟 iOS alwaysBounceVertical, 不挂下拉语义。
        wkVBinding.bounceLayout.setEnableRefresh(false)
        wkVBinding.bounceLayout.setEnableLoadMore(false)
        wkVBinding.bounceLayout.setEnableOverScrollDrag(true)
        wkVBinding.bounceLayout.setEnableOverScrollBounce(true)
    }

    /**
     * Tab Activity 启用了沉浸式状态栏,Fragment 根布局没有 fitsSystemWindows;
     * 通过 WindowInsetsCompat 把 system bars top inset 注入到顶部 spacer,
     * 等价 iOS [UIApplication statusBarFrame]。装饰栏高度变 (横屏/notch) 自动跟随。
     */
    private fun applyStatusBarInset() {
        val spacer = wkVBinding.statusBarSpacer
        ViewCompat.setOnApplyWindowInsetsListener(wkVBinding.root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val lp = spacer.layoutParams
            if (lp.height != top) {
                lp.height = top
                spacer.layoutParams = lp
            }
            insets
        }
        // tab fragment 复用时, dispatch 一次确保首帧正确
        wkVBinding.root.post { ViewCompat.requestApplyInsets(wkVBinding.root) }
    }

    private fun buildEntries(): List<ContextEntryAdapter.Entry> = listOf(
        ContextEntryAdapter.Entry(
            itemId = "smart_summary",
            titleRes = R.string.summary_entry_smart_summary,
        ),
    )

    private fun onEntryTapped(itemId: String) {
        when (itemId) {
            "smart_summary" -> {
                val intent = Intent(requireContext(), SmartSummaryListActivity::class.java)
                startActivity(intent)
            }
        }
    }

    /**
     * 切语言后 Activity 重建会顺便重建 fragment, 但 Tab 内 fragment 也可能直接收到
     * onConfigurationChanged。这里强制刷标题 + grid label, 与 iOS [viewConfigChange] 同步。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (this::adapter.isInitialized) {
            wkVBinding.titleTv.setText(R.string.summary_tab_context)
            adapter.notifyDataSetChanged()
        }
    }

    @Suppress("unused")
    private val unusedView: View? = null
}
