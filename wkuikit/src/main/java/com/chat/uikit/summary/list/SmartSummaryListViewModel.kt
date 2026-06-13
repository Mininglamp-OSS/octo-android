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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.model.BatchStatusItem
import com.chat.base.summary.model.SummaryFilter
import com.chat.base.summary.model.SummaryListItem
import com.chat.base.summary.model.TaskStatus
import com.chat.base.summary.repository.SummaryRepository
import com.chat.uikit.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 智能总结列表页 ViewModel, 1:1 对齐 iOS [OctoSummaryListVC] 的列表 / 搜索 / 过滤 /
 * 乐观更新链路。
 *
 * 关键节奏:
 *   - keyword: setKeyword 立即更新 state(让搜索条 UI 反馈), 真正调 list 带 300ms debounce
 *   - reload / loadMore: 串行守门 [loading] 防并发抖动
 *   - cancel / regenerate / delete: 乐观更新 + 失败回滚 (与 iOS reloadRowForTaskId 同思路)
 */
class SmartSummaryListViewModel(
    private val repository: SummaryRepository = SummaryDeps.repository,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryListUiState())
    val uiState = _uiState.asStateFlow()

    private var keywordDebounceJob: Job? = null

    /**
     * In-flight 列表请求 (reload / loadMore / setFilter / setKeyword 触发的). 仅 1 个,
     * setKeyword 与 setFilter 在 launch 新请求前会取消旧的, 防止 keyword=A 的 loadMore
     * 完成后把 A 的下一页 append 到已经切到 keyword=B 的列表上 (review 标记的"混页")。
     * reload / loadMore 保留各自的 loading 守门, 不互相打断 (下拉刷新与上拉加载更多互斥)。
     */
    private var loadJob: Job? = null

    /** 已拉取过 preview 的 taskId → 清洗后正文 (200 字以内). 列表 item 间共享, 减少二次拉取. */
    private val previewCache = HashMap<Long, String>()

    /** 正在 in-flight 的 taskId, 防止同一个 task 被并发拉两次. */
    private val previewInFlight = HashSet<Long>()

    private val current: SummaryListUiState get() = _uiState.value

    /** 首次进入页面或下拉刷新触发. */
    fun reload() {
        if (current.loading) return
        val s = current
        _uiState.update { it.copy(loading = true, refreshing = true, page = 1) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            performLoad(page = 1, append = false, filter = s.filter, keyword = s.keyword.trim())
        }
    }

    fun loadMore() {
        if (current.loading || !current.hasMore) return
        val s = current
        val nextPage = s.page + 1
        _uiState.update { it.copy(loading = true, loadingMore = true, page = nextPage) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            performLoad(page = nextPage, append = true, filter = s.filter, keyword = s.keyword.trim())
        }
    }

    fun setFilter(filter: SummaryFilter) {
        if (current.filter == filter) return
        // 不立即清空 items: 切 tab 时保留旧列表, 等新数据回来再 atomically 替换,
        // 避免出现 "旧→空→新" 三段过渡造成的视觉卡顿; 加载完成后 performLoad
        // 用 newItems 整体覆盖。同时不开启下拉刷新动画, 切 tab 是隐式刷新, 不要弹 header。
        _uiState.update { it.copy(filter = filter, loading = true, refreshing = false, page = 1) }
        loadJob?.cancel()  // 抢占 in-flight (旧 filter 的请求结果若回来会被丢弃)
        val keyword = current.keyword.trim()
        loadJob = viewModelScope.launch {
            performLoad(page = 1, append = false, filter = filter, keyword = keyword)
        }
    }

    /**
     * 输入框每次 onChanged 都调一次, ViewModel 内部 300ms debounce 后才真正 reload。
     * 与 iOS [OctoSummaryListVC onSearchChanged:] 节奏一致。
     */
    fun setKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed == current.keyword) {
            _uiState.update { it.copy(keyword = keyword) }
            return
        }
        _uiState.update { it.copy(keyword = keyword) }
        keywordDebounceJob?.cancel()
        keywordDebounceJob = viewModelScope.launch {
            delay(KEYWORD_DEBOUNCE_MILLIS)
            // debounce 期间 keyword 还可能再变, 用提交时刻的快照
            val applied = current.keyword.trim()
            if (applied != effectiveKeyword(applied)) return@launch
            // 关键 (review B1): keyword 改变是 fresh page-1 query, 必须 supersede in-flight,
            // 不能走 reload() 的 loading 守门 (会把 debounce 完成时正好 in-flight 的 keyword 吞掉,
            // 用户输入消失再无其它信号唤醒). 与 setFilter 同口径直接 force-launch + 抢占 loadJob,
            // 避免旧 keyword 的 loadMore 回来 append 到新 keyword 列表 (混页 race)。
            val filter = current.filter
            _uiState.update {
                it.copy(loading = true, refreshing = false, loadingMore = false, page = 1)
            }
            loadJob?.cancel()
            loadJob = viewModelScope.launch {
                performLoad(page = 1, append = false, filter = filter, keyword = applied)
            }
        }
    }

    /** Activity 拿到 transientMessage 显示完后调一下,清掉避免重新订阅时再弹一次. */
    fun consumeTransient() {
        if (current.transientMessage != null) {
            _uiState.update { it.copy(transientMessage = null) }
        }
    }

    // region per-item actions (optimistic)

    fun performCancel(item: SummaryListItem) {
        val original = item.status
        replaceItem(item.taskId) { it.copy(status = TaskStatus.Cancelled) }
        viewModelScope.launch {
            val res = repository.cancelSummary(item.taskId)
            if (res.isFailure) {
                replaceItem(item.taskId) { it.copy(status = original) }
                emit(TransientMessage.StringRes(R.string.summary_cancel_failed))
            } else {
                emit(TransientMessage.StringRes(R.string.summary_cancelled))
            }
        }
    }

    fun performRegenerate(item: SummaryListItem, topic: String? = null) {
        val origStatus = item.status
        val origCompleted = item.completedAt
        val origTaskId = item.taskId
        replaceItem(origTaskId) { it.copy(status = TaskStatus.Processing, completedAt = null) }
        viewModelScope.launch {
            val res = repository.regenerateSummary(origTaskId, topic)
            if (res.isFailure) {
                replaceItem(origTaskId) {
                    it.copy(status = origStatus, completedAt = origCompleted)
                }
                emit(TransientMessage.StringRes(R.string.summary_regenerate_failed))
                return@launch
            }
            val newId = res.getOrThrow()
            if (newId > 0 && newId != origTaskId) {
                // 后端给了新 task_id, 把 item.taskId 切到新 id, 后续 poller 跟踪新任务
                replaceItem(origTaskId) {
                    it.copy(
                        taskId = newId,
                        status = TaskStatus.Processing,
                        completedAt = null,
                        summaryPreview = null,
                    )
                }
            }
            emit(TransientMessage.StringRes(R.string.summary_regenerate_started))
        }
    }

    fun performDelete(item: SummaryListItem) {
        viewModelScope.launch {
            val res = repository.deleteSummary(item.taskId)
            if (res.isFailure) {
                emit(TransientMessage.StringRes(R.string.summary_list_delete_failed))
                return@launch
            }
            _uiState.update { state ->
                state.copy(items = state.items.filterNot { it.taskId == item.taskId })
            }
        }
    }

    /** poller 5s 轮询变更回填,把变化项 status 同步入 state. */
    fun applyStatusChanges(changes: Map<Long, BatchStatusItem>) {
        if (changes.isEmpty()) return
        var anyNewlyCompleted = false
        _uiState.update { state ->
            val newItems = state.items.map { it ->
                val upd = changes[it.taskId] ?: return@map it
                if (it.status == upd.status) return@map it
                if (upd.status == TaskStatus.Completed && it.status != TaskStatus.Completed) {
                    anyNewlyCompleted = true
                }
                it.copy(status = upd.status)
            }
            state.copy(items = newItems)
        }
        // processing -> completed 的 cell 立刻拉一次详情回填 preview, 不必等用户手动刷新
        if (anyNewlyCompleted) hydratePreview()
    }

    fun activeStatusTaskIds(): List<Long> = current.items
        .filter {
            it.status == TaskStatus.Pending ||
                it.status == TaskStatus.WaitingConfirm ||
                it.status == TaskStatus.Processing
        }
        .map { it.taskId }

    // endregion

    /**
     * 网络拉取并合并入 state. [filter] / [keyword] 必须在 launch 处快照传入, 不要在
     * 函数体内读 current.filter / current.keyword — 否则 setKeyword/setFilter 改完
     * state 后, in-flight 请求带的还是旧值, 但回写时按新 state.items 做 append/replace,
     * 会出现 "新 keyword 的 items + 旧 keyword 的下一页" 这种混页 race (review B1)。
     */
    private suspend fun performLoad(
        page: Int,
        append: Boolean,
        filter: SummaryFilter,
        keyword: String,
    ) {
        val res = repository.listSummaries(
            page = page,
            pageSize = pageSize,
            filter = filter,
            keyword = keyword.ifEmpty { null },
        )
        // 抢占式 cancel 时这里走 CancellationException 抛出 (callEnvelope 的 runCatching
        // 会包成 Result.failure, 不过协程已 cancel 不会再走到 _uiState.update). 即使包装了,
        // 下面的 isFailure 分支也只会写入 transientMessage, 没有 items 污染。
        if (res.isFailure) {
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    loadingMore = false,
                    page = if (append) (it.page - 1).coerceAtLeast(1) else it.page,
                    transientMessage = TransientMessage.StringRes(R.string.summary_common_network_error),
                )
            }
            return
        }
        val pageRes = res.getOrThrow()
        _uiState.update { state ->
            // 二次校验: state.filter / state.keyword 在我们 launch 后被 setFilter/setKeyword
            // 改过, 说明这次响应已 stale, 丢弃 items 不合并 (loading flag 仍清掉避免卡死)。
            // 实际 cancel 已经覆盖 99% 场景, 这里是兜底防止 cancel 信号还没传到时响应已 emit。
            val stateStale = state.filter != filter || state.keyword.trim() != keyword
            if (stateStale) {
                state.copy(loading = false, refreshing = false, loadingMore = false)
            } else {
                val merged = if (append) state.items + pageRes.items else pageRes.items
                state.copy(
                    items = merged,
                    loading = false,
                    refreshing = false,
                    loadingMore = false,
                    hasMore = pageRes.items.size >= pageSize,
                )
            }
        }
        hydratePreview()
    }

    /**
     * 1:1 对齐 iOS [OctoSummaryListVC hydratePreview]:
     *   列表 API 不带正文,对所有 completed 且 summaryPreview 为空的 item 并发拉详情,
     *   清洗 markdown 后取前 200 字回填到 item.summaryPreview。
     *   后端若新增 summary_preview 字段, 整段可删。
     */
    private fun hydratePreview() {
        val needFetch = current.items.filter { item ->
            if (item.status != TaskStatus.Completed) return@filter false
            if (!item.summaryPreview.isNullOrEmpty()) return@filter false
            previewCache[item.taskId]?.let { cached ->
                replaceItem(item.taskId) { it.copy(summaryPreview = cached) }
                return@filter false
            }
            if (item.taskId in previewInFlight) return@filter false
            true
        }
        if (needFetch.isEmpty()) return
        needFetch.forEach { previewInFlight += it.taskId }

        for (item in needFetch) {
            val tid = item.taskId
            viewModelScope.launch {
                try {
                    val res = repository.getSummaryDetail(tid)
                    val detail = res.getOrNull() ?: return@launch
                    val raw = detail.result?.content.orEmpty()
                    val clean = cleanPreviewFromMarkdown(raw)
                    if (clean.isEmpty()) return@launch
                    previewCache[tid] = clean
                    replaceItem(tid) { it.copy(summaryPreview = clean) }
                } finally {
                    previewInFlight -= tid
                }
            }
        }
    }

    private fun cleanPreviewFromMarkdown(content: String): String {
        if (content.isEmpty()) return ""
        var s = content.replace("###", "").replace("##", "")
        s = s.replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("^[-*•]\\s+"), "")
        return if (s.length > 200) s.substring(0, 200) else s
    }

    private fun replaceItem(taskId: Long, mutate: (SummaryListItem) -> SummaryListItem) {
        _uiState.update { state ->
            val newItems = state.items.map { if (it.taskId == taskId) mutate(it) else it }
            state.copy(items = newItems)
        }
    }

    private fun emit(msg: TransientMessage) {
        _uiState.update { it.copy(transientMessage = msg) }
    }

    /** 留给 setKeyword debounce 一致性比对用的 hook,纯 identity, 测试可覆写。 */
    private fun effectiveKeyword(keyword: String): String = keyword

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val KEYWORD_DEBOUNCE_MILLIS = 300L
    }
}
