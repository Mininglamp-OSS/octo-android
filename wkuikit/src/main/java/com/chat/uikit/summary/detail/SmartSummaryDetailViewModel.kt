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
    private val taskId: Long,
    private val repository: SummaryRepository = SummaryDeps.repository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state = _state.asStateFlow()

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
            val res = repository.getSummaryDetail(taskId)
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
            emit(DetailEffect.Toast(DetailToastKind.RegenStarted))
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
            emit(DetailEffect.Close)
        }
    }

    private fun schedulePollIfNeeded(status: TaskStatus) {
        pollJob?.cancel()
        if (status != TaskStatus.Processing && status != TaskStatus.Pending) return
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
