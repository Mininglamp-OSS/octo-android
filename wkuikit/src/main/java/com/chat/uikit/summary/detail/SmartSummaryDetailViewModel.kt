/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.SummaryEvents
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.TaskStatus
import com.chat.base.summary.repository.SummaryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Activity 端把 ToastKind 映射为 string res id, ViewModel 不持有 Context. */
enum class DetailToastKind {
    LoadFailed,
    Cancelled,
    CancelFailed,
    RegenStarted,
    RegenFailed,
    DeleteFailed,
}

sealed interface DetailEffect {
    data class Toast(val kind: DetailToastKind) : DetailEffect
    object Close : DetailEffect
}

data class DetailUiState(
    val loading: Boolean = true,
    val detail: SummaryDetail? = null,
    val effect: DetailEffect? = null,
)

/**
 * 详情页 ViewModel:
 *   - 首次 loadDetail
 *   - status ∈ {processing, pending} 时每 8s 轮询 (与 iOS NSTimer 同周期)
 *   - cancel / regenerate / delete 走乐观链路: toast + reload / close
 */
class SmartSummaryDetailViewModel(
    initialTaskId: Long,
    private val repository: SummaryRepository = SummaryDeps.repository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state = _state.asStateFlow()

    /**
     * 当前跟踪的 taskId. regenerate 成功后后端给新 task_id (旧任务进终态),
     * 必须切到新 id 否则 loadDetail 会一直拉旧任务的终态, 用户看不到新生成的任务进度。
     * 与 list VM performRegenerate 切 item.taskId 同语义。
     */
    private var trackedTaskId: Long = initialTaskId

    private var pollJob: Job? = null

    init {
        loadDetail()
    }

    /**
     * 拉详情. [silent]=true 表示后台轮询 tick (8s 自动刷新), 不弹 LoadFailed toast,
     * 不动 loading 标志位; 失败时仍然按上一次已知的非终态 status 重排下一拍, 让一次瞬时
     * 网络抖动不至于把整个轮询链整个断掉 (iOS NSTimer fires unconditionally, 这里也对齐)。
     *
     * silent=false 是用户主动触发 (init / onResume / cancel-regenerate 收尾 reload),
     * 失败弹 toast, 同样按 last-known status 继续轮询 (避免 onResume 时一拍失败后再也不刷新)。
     */
    fun loadDetail(silent: Boolean = false) {
        viewModelScope.launch {
            val res = repository.getSummaryDetail(trackedTaskId)
            if (res.isFailure) {
                if (!silent) {
                    _state.update {
                        it.copy(loading = false, effect = DetailEffect.Toast(DetailToastKind.LoadFailed))
                    }
                }
                // 用上一次已知 status 决定是否继续轮询: 首次 load 失败 lastStatus=null 不重排,
                // 用户可下拉刷新 / 重进 activity 自然恢复; 已经拿到过 detail 的话保持原节奏。
                _state.value.detail?.status?.let { schedulePollIfNeeded(it) }
                return@launch
            }
            val detail = res.getOrThrow()
            // 调试 ("群聊总结好了 spinner 还在转"): 8s 轮询每拍都打 status, 让 detail 页 processingCard
            // 显隐状态在日志里追得到。release 包关掉, 高频轮询日志不该进 logcat.
            if (com.chat.uikit.BuildConfig.DEBUG) {
                android.util.Log.i(
                    "SummaryDebug",
                    "loadDetail tid=${detail.taskId} status=${detail.status} silent=$silent" +
                        " hasContent=${!detail.result?.content.isNullOrEmpty()}",
                )
            }
            _state.update { it.copy(loading = false, detail = detail) }
            schedulePollIfNeeded(detail.status)
        }
    }

    fun consumeEffect() {
        if (_state.value.effect != null) _state.update { it.copy(effect = null) }
    }

    fun performCancel() {
        val tid = currentTaskId() ?: return
        viewModelScope.launch {
            val res = repository.cancelSummary(tid)
            if (res.isFailure) {
                emit(DetailEffect.Toast(DetailToastKind.CancelFailed))
                return@launch
            }
            emit(DetailEffect.Toast(DetailToastKind.Cancelled))
            // 通知列表刷新 (cancel 不产生新条目, scrollToTop=false): 用户返回列表能立刻
            // 看到 Cancelled 状态翻新, 不必等列表 poller 5s 拍一拍.
            SummaryEvents.postListShouldRefresh(scrollToTop = false)
            loadDetail()
        }
    }

    fun performRegenerate() {
        val tid = currentTaskId() ?: return
        viewModelScope.launch {
            val res = repository.regenerateSummary(tid, null)
            if (res.isFailure) {
                emit(DetailEffect.Toast(DetailToastKind.RegenFailed))
                return@launch
            }
            // 后端给的新 task_id 必须切过去, 否则 loadDetail 一直拉旧任务的终态 (Cancelled),
            // 用户在详情页看不到新生成的任务从 Processing 走到 Completed (与 list VM 同思路)。
            val newId = res.getOrThrow()
            if (newId > 0 && newId != tid) {
                trackedTaskId = newId
                // 旧 detail 已无效, 立刻清空让 loading 卡显示, 避免短暂闪现旧 cancelled 内容。
                _state.update { it.copy(detail = null, loading = true) }
            }
            emit(DetailEffect.Toast(DetailToastKind.RegenStarted))
            // 通知列表刷新状态 (scrollToTop=false): server 的 regenerate 不动 created_at,
            // 列表按 created_at desc 排序时新一轮任务就在原位置, 不需要跳顶 (强行 scroll
            // 到 0 会把用户视野跳到另一条任务上, 反而看不到正在重生成的那条). 列表只需要
            // reload 一次让该条目状态从 Completed 翻回 Processing (5s poller 也能做到, 但
            // 主动 reload 让用户回到列表的瞬间就能看到状态翻新, 不必等 5s 拍一拍).
            //
            // 与 iOS [OctoSummaryDetailVC performRegenerate] 同语义 — iOS 没有列表刷新通知,
            // 但 iOS list 与 detail 共享 OctoSummaryListItem 引用, 状态原地变了就生效;
            // Android list / detail 是不同 ViewModel, 拷贝语义, 必须靠这条事件桥接.
            SummaryEvents.postListShouldRefresh(scrollToTop = false)
            loadDetail()
        }
    }

    fun performDelete() {
        val tid = currentTaskId() ?: return
        viewModelScope.launch {
            val res = repository.deleteSummary(tid)
            if (res.isFailure) {
                emit(DetailEffect.Toast(DetailToastKind.DeleteFailed))
                return@launch
            }
            // 删除后通知列表刷新 (delete 不产生新条目, scrollToTop=false), 让目标条目
            // 立即从列表消失 (与 iOS list 端 NSNotification 同节奏).
            SummaryEvents.postListShouldRefresh(scrollToTop = false)
            emit(DetailEffect.Close)
        }
    }

    private fun schedulePollIfNeeded(status: TaskStatus) {
        pollJob?.cancel()
        // 与 list VM activeStatusTaskIds 同口径: Pending / WaitingConfirm / Processing 都算活跃,
        // 详情页若停在 WaitingConfirm 状态, 其他参与者点确认后必须能自动 flip 到 Processing,
        // 不轮询的话用户得手动退出再进来才看得到, 与 list 行为不一致。
        val isActive = status == TaskStatus.Processing
            || status == TaskStatus.Pending
            || status == TaskStatus.WaitingConfirm
        if (!isActive) return
        pollJob = viewModelScope.launch {
            delay(POLL_INTERVAL_MS)
            // 后台 tick 走 silent=true: 失败不弹 toast 不打扰用户, 失败分支自身会重排
            // 让链路在瞬时抖动后自愈, 保持每 8s 一拍直到状态终态。
            loadDetail(silent = true)
        }
    }

    private fun emit(effect: DetailEffect) {
        _state.update { it.copy(effect = effect) }
    }

    private fun currentTaskId(): Long? = _state.value.detail?.taskId

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    companion object {
        const val POLL_INTERVAL_MS = 8_000L
    }
}
