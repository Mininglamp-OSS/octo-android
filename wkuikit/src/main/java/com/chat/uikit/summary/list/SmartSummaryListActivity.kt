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

    /**
     * 第一次 onResume 跳过 reload — initView 里已经调过 viewModel.reload(),
     * 不希望首次启动跑两遍. 后续 onResume (用户从 detail / create 页返回) 才走 reload,
     * 让 spinner 滞留 / status 滞后这种 5s 轮询 lag 在用户回到列表的瞬间被一次性抹平
     * (与 iOS NSNotification 全程在线接收 + viewWillAppear 主动重拉同效果).
     */
    private var skipFirstResumeReload = true

    /**
     * 创建总结页关闭回调: RESULT_OK 时走 reloadAndScrollToTop, ViewModel 在新数据落 state
     * 后把 [SummaryListUiState.pendingScrollToTop] 翻 true, Activity 在 submitList 的 commit
     * callback 里读到 true 才 scrollToPosition(0) — 这样跳顶发生在 DiffUtil dispatch 已经
     * 落到 RV 内部状态之后, 不会被后续 layout 重置回原位置.
     */
    private val createLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.reloadAndScrollToTop()
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
        // 1:1 对齐 iOS [OctoSummaryListVC resolveCurrentUserName]: 把当前登录用户名喂给
        // adapter, 让 [SummaryCardBinder.initiatorTextFor] 在 creatorName == 当前用户名时
        // 走 "你发起" 分支 (用户报"目前 IOS 默认是 你发起了总结, 咱们写的是用户名").
        // WKConfig 里的名字是登录态本地缓存, 同步可读, 不需要异步拉 channelInfo.
        listAdapter.currentUserName =
            com.chat.base.config.WKConfig.getInstance().getUserName()?.takeIf { it.isNotEmpty() }
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
        // 详情页 cancel/regenerate/delete 完成后会推一条事件 (1:1 对齐 iOS NSNotificationCenter
        // 思路, 见 [SummaryEvents]). 列表收到立刻 reload 让条目状态 / regenerate 后的新 task
        // 实时翻新, 不依赖 5s 轮询 (用户报"详情页重新生成后返回列表看不到正在总结的状态刷新")。
        //
        // 关键: 这条 collect **不能**套 repeatOnLifecycle(STARTED) — 用户进 detail 页时 list
        // 处于 STOPPED, repeatOnLifecycle 会取消订阅, regenerate 当下 emit 没人接 (SharedFlow
        // replay=0 直接丢), 用户回 list 时 collect 重启也拿不到旧 emit. 直接挂 lifecycleScope
        // 让 collect 活到 activity 销毁; reload 只动 ViewModel state, 不碰 UI, STOPPED 状态期间
        // 也安全 — uiState 那条已经被 repeatOnLifecycle 守门, 用户返回 STARTED 时自然 render
        // 最新数据 (与 iOS NSNotification 全程在线接收同效果).
        lifecycleScope.launch {
            com.chat.base.summary.SummaryEvents.listShouldRefresh.collect { scrollToTop ->
                if (scrollToTop) viewModel.reloadAndScrollToTop() else viewModel.reload()
            }
        }
    }

    private fun render(state: SummaryListUiState) {
        listAdapter.keyword = state.keyword.trim().ifEmpty { null }
        // submitList 的 commit callback 在 DiffUtil dispatch 完后回主线程同步触发 —
        // 此时 RV 内部 ItemAnimator/layout 已感知新数据集, scrollToPosition(0) 才能
        // 真正落到新 top item. 直接 listRv.post 在 diff 派发前会被后续 layout 重置。
        //
        // pendingScrollToTop 烧在 state 里 (不是 SharedFlow): 跨 STOPPED/STARTED 切换不会丢
        // (StateFlow 始终 replay 最新值给新订阅者). 在 commit 后 consumePendingScrollToTop
        // 把 flag 翻回 false, 避免下次轻量状态变化 (例如 transient 消费、preview hydrate)
        // 重新 render 时再次跳顶.
        listAdapter.submitList(state.items) {
            if (state.pendingScrollToTop && state.items.isNotEmpty()) {
                wkVBinding.listRv.scrollToPosition(0)
                viewModel.consumePendingScrollToTop()
            }
        }
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
        // 跳过首次 onResume: initView 已经调过 reload(), 不重复跑一遍.
        // 后续 onResume 走 reload 让从 detail/create 页回来时, server 的最新 status
        // 立刻覆盖列表 — 用户报"明明已经总结完成了, 但是列表后面那个还在转圈": 根因
        // 是 list 在 STOPPED 期间 5s poller 被 pause 了, 回到前台第一拍要等几秒, 期间
        // 看到的是上一次 poll 的旧 status; 主动 reload 把 server-truth 立刻拉过来.
        if (skipFirstResumeReload) {
            skipFirstResumeReload = false
        } else {
            viewModel.reload()
        }
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
