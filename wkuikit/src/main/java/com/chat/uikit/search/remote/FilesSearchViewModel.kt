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

package com.chat.uikit.search.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.base.search.channel.ChannelSearchOutcome
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.search.channel.uiAction
import com.chat.base.search.global.SearchGlobalFilesModel
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.SearchGlobalFilesReq
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局搜索「文件」tab 的 ViewModel，对接 `_search_global_files`（[SearchGlobalFilesModel]，
 * 该接口此前已写好但零调用，本次首次接入 UI）。
 *
 * 结构对称 [GlobalSearchViewModel]：keyword debounce + sequence 防竞态；区别是 filters
 * 从一开始就是必需参数（文件 tab 的筛选不是后补能力），且没有本地兜底数据源——文件搜索
 * 失败时无法像消息那样回退到 IMSDK 本地聚合，`FALLBACK_TO_LOCAL` 态在 UI 层只能展示
 * "暂时无法搜索"的空态，不会产生本地结果列表。
 */
class FilesSearchViewModel(
    private val searchCaller: SearchCaller = DefaultSearchCaller,
    private val debounceMillis: Long = DEBOUNCE_MS,
) : ViewModel() {

    data class State(
        val keyword: String = "",
        val isLoading: Boolean = false,
        val files: List<FileHit> = emptyList(),
        val hasMore: Boolean = false,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val retryAfterSec: Int = 0,
        val sequence: Long = 0L,
    ) {
        fun uiAction(): com.chat.base.search.channel.ChannelSearchUiAction? =
            errorCode?.let {
                ChannelSearchOutcome.failure<Unit>(httpStatus = 0, errorCode = it).uiAction()
            }
    }

    fun interface SearchCaller {
        fun call(
            req: SearchGlobalFilesReq,
            callback: (ChannelSearchOutcome<CursorList<FileHit>>) -> Unit,
        )
    }

    private object DefaultSearchCaller : SearchCaller {
        override fun call(
            req: SearchGlobalFilesReq,
            callback: (ChannelSearchOutcome<CursorList<FileHit>>) -> Unit,
        ) {
            SearchGlobalFilesModel.searchFiles(req, callback)
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var debounceJob: Job? = null
    private var nextSequence: Long = 0L
    private var currentFilters: GlobalSearchFilters = GlobalSearchFilters()

    fun setFilters(filters: GlobalSearchFilters) {
        currentFilters = filters
    }

    fun setKeyword(value: String) {
        if (_state.value.keyword == value) return
        _state.value = _state.value.copy(keyword = value, isLoading = true)
        debounceJob?.cancel()
        if (value.isEmpty()) {
            _state.value = State()
            return
        }
        debounceJob = viewModelScope.launch {
            delay(debounceMillis)
            triggerSearch(value)
        }
    }

    /** 显式强制触发：筛选"应用"、键盘搜索键等场景，跳过 debounce。 */
    fun triggerNow() {
        val keyword = _state.value.keyword
        if (keyword.isEmpty()) return
        debounceJob?.cancel()
        triggerSearch(keyword)
    }

    private fun triggerSearch(keyword: String) {
        val mySeq = ++nextSequence
        _state.value = _state.value.copy(
            isLoading = true,
            // 新一轮请求发起即清空旧结果，原因见 GlobalSearchViewModel.triggerSearch 的同类注释。
            files = emptyList(),
            errorCode = null,
            errorMessage = null,
            retryAfterSec = 0,
            sequence = mySeq,
        )
        val req = SearchGlobalFilesReq(
            keyword = keyword,
            filters = currentFilters,
            sequence = mySeq,
        )
        searchCaller.call(req) { outcome -> handleResponse(mySeq, keyword, outcome) }
    }

    private fun handleResponse(
        reqSeq: Long,
        requestKeyword: String,
        outcome: ChannelSearchOutcome<CursorList<FileHit>>,
    ) {
        if (reqSeq != nextSequence) return
        if (_state.value.keyword.isEmpty()) return
        if (_state.value.keyword != requestKeyword) return

        if (outcome.ok) {
            val body = outcome.data ?: return
            _state.value = _state.value.copy(
                isLoading = false,
                files = body.data,
                hasMore = body.pagination.has_more,
                errorCode = null,
                errorMessage = null,
                retryAfterSec = 0,
            )
        } else {
            _state.value = _state.value.copy(
                isLoading = false,
                files = emptyList(),
                hasMore = false,
                errorCode = outcome.errorCode,
                errorMessage = outcome.errorMessage,
                retryAfterSec = outcome.retryAfterSec,
            )
        }
    }

    override fun onCleared() {
        debounceJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val DEBOUNCE_MS = 300L
    }
}
