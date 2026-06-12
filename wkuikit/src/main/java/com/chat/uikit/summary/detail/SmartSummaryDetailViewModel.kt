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

    fun loadDetail() {
        viewModelScope.launch {
            val res = repository.getSummaryDetail(taskId)
            if (res.isFailure) {
                _state.update {
                    it.copy(loading = false, effect = DetailEffect.Toast(DetailToastKind.LoadFailed))
                }
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
            loadDetail()
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
