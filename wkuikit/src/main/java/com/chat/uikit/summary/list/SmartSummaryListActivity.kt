/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.list

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.base.WKBaseActivity
import com.chat.base.summary.model.SummaryListItem
import com.chat.base.summary.poller.SummaryStatusPoller
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActSmartSummaryListBinding
import kotlinx.coroutines.launch

/**
 * 智能总结列表页. 1:1 对齐 iOS [OctoSummaryListVC] (含 1fd3ecc 提交):
 *   - 顶 title bar (智能总结)
 *   - inline 搜索条 (300ms debounce, 由 ViewModel 内部完成)
 *   - 6 状态筛选 tab
 *   - 列表 + 下拉刷新 + 上拉加载 + 5s 轮询
 *   - 底部黑色胶囊 FAB "发起总结" (滚动方向感知显隐)
 *   - 真空态(无 keyword + 无数据): rich 引导; 搜索空(有 keyword + 无数据): 一行文案
 *   - 长按 ⋯ : 按状态决定菜单 (取消任务/重新生成/编辑/重试/删除)
 */
class SmartSummaryListActivity : WKBaseActivity<ActSmartSummaryListBinding>() {

    private val viewModel: SmartSummaryListViewModel by viewModels()
    private lateinit var listAdapter: SummaryListAdapter
    private lateinit var poller: SummaryStatusPoller

    /** 标志位: 当前 FAB 是否处于显示态, 避免重复动画。 */
    private var fabVisible: Boolean = true

    /** 创建总结页关闭回调: RESULT_OK 时拉一次列表让新任务出现在顶部. */
    private val createLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.reload()
        }
    }

    override fun getViewBinding(): ActSmartSummaryListBinding =
        ActSmartSummaryListBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.setText(R.string.summary_entry_smart_summary)
    }

    override fun initView() {
        listAdapter = SummaryListAdapter().apply {
            onItemClick { item -> openDetail(item) }
            onMoreClick { item, anchor -> showActionMenu(item, anchor) }
        }
        wkVBinding.listRv.apply {
            layoutManager = LinearLayoutManager(this@SmartSummaryListActivity)
            adapter = listAdapter
            addOnScrollListener(scrollAwareFabListener)
            addOnItemTouchListener(touchAwareFabListener)
        }

        wkVBinding.filterTabs.onSelected { viewModel.setFilter(it) }

        wkVBinding.refreshLayout.apply {
            setEnableRefresh(true)
            setEnableLoadMore(true)
            setEnableOverScrollDrag(true)
            setEnableOverScrollBounce(true)
            setOnRefreshListener { viewModel.reload() }
            setOnLoadMoreListener { viewModel.loadMore() }
        }

        wkVBinding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setKeyword(s?.toString().orEmpty())
            }
        })
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.searchEt)
                true
            } else false
        }

        wkVBinding.createFab.setOnClickListener {
            createLauncher.launch(
                com.chat.uikit.summary.create.SmartSummaryCreateActivity.newIntent(this),
            )
        }

        poller = SummaryStatusPoller(scope = lifecycleScope)
        poller.onUpdate = { changes -> viewModel.applyStatusChanges(changes) }

        observeState()

        viewModel.reload()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: SummaryListUiState) {
        listAdapter.keyword = state.keyword.trim().ifEmpty { null }
        listAdapter.submitList(state.items)
        wkVBinding.filterTabs.selected = state.filter

        // 下拉刷新 / 上拉加载 状态收尾
        if (!state.refreshing) wkVBinding.refreshLayout.finishRefresh()
        if (!state.loadingMore) {
            if (state.hasMore) wkVBinding.refreshLayout.finishLoadMore()
            else wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData()
        }

        wkVBinding.emptyState.root.isVisible = state.isInitialEmpty
        wkVBinding.emptySearchTv.isVisible = state.isSearchEmpty

        // 真空态强制 FAB 可见 — 用户唯一行动入口
        if (state.isInitialEmpty) setFabVisible(true)

        state.transientMessage?.let { msg ->
            val text = when (msg) {
                is TransientMessage.Text -> msg.message
                is TransientMessage.StringRes -> getString(msg.resId)
            }
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeTransient()
        }

        poller.setTaskIds(viewModel.activeStatusTaskIds())
    }

    override fun onStart() {
        super.onStart()
        poller.start()
    }

    override fun onResume() {
        super.onResume()
        poller.resume()
    }

    override fun onPause() {
        super.onPause()
        poller.pause()
    }

    override fun onStop() {
        super.onStop()
        poller.stop()
    }

    // region FAB scroll-aware

    /**
     * 1:1 对齐 iOS [OctoSummaryListVC scrollViewDidScroll:]:
     *   - 真空态(items.empty + keyword.empty) 永远 show, scroll-direction 不参与判断
     *   - dy > 4 (内容上移 / 下滚) → hide
     *   - dy < -4 (内容下移 / 上滚) → show
     *
     * RecyclerView.OnScrollListener.onScrolled 在 SmartRefreshLayout 嵌套场景下偶发不达,
     * 兜底再加一层 OnItemTouchListener 直接看 ACTION_MOVE 的位移; 两条任意一条触发即可。
     */
    private val scrollAwareFabListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy > 4) setFabVisible(false)
            else if (dy < -4) setFabVisible(true)
        }

        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            // 用户预期: 滚动停止 -> FAB 再现 (与 iOS 状态保持不同, 这里取用户偏好)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) setFabVisible(true)
        }
    }

    private val touchAwareFabListener = object : RecyclerView.OnItemTouchListener {
        private var lastY: Float = 0f
        private var tracking: Boolean = false

        override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    lastY = e.y
                    tracking = true
                }
                android.view.MotionEvent.ACTION_MOVE -> if (tracking) {
                    val dy = lastY - e.y
                    if (dy > 12) {
                        setFabVisible(false)
                        lastY = e.y
                    } else if (dy < -12) {
                        setFabVisible(true)
                        lastY = e.y
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> tracking = false
            }
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) = Unit
        override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) = Unit
    }

    private fun setFabVisible(visible: Boolean) {
        // 真空态强制 show — render() 已守门, 这里再做一次防御
        val state = viewModel.uiState.value
        if (!visible && state.isInitialEmpty) return
        if (visible == fabVisible) return
        fabVisible = visible
        wkVBinding.createFab.animate()
            .translationY(if (visible) 0f else dp(120f))
            .alpha(if (visible) 1f else 0f)
            .setDuration(220)
            .start()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    // endregion

    // region Action menu (按状态切菜单, 浮层贴 anchor)

    private fun showActionMenu(item: SummaryListItem, anchor: View) {
        SummaryItemActionPopup.show(
            anchor = anchor,
            item = item,
            onCancel = { viewModel.performCancel(item) },
            onRegenerate = { viewModel.performRegenerate(item) },
            onRetry = { viewModel.performRegenerate(item) },
            onDelete = { confirmDelete(item) },
        )
    }

    private fun openDetail(item: SummaryListItem) {
        startActivity(
            com.chat.uikit.summary.detail.SmartSummaryDetailActivity
                .newIntent(this, item.taskId)
        )
    }

    private fun confirmDelete(item: SummaryListItem) {
        com.chat.base.utils.WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.summary_list_confirm_delete),
            getString(R.string.summary_list_delete_irreversible),
            true,
            getString(R.string.summary_common_cancel),
            getString(R.string.summary_common_delete),
            0,
            getColor(R.color.summary_red),
        ) { idx ->
            if (idx == 1) viewModel.performDelete(item)
        }
    }

    // endregion
}
