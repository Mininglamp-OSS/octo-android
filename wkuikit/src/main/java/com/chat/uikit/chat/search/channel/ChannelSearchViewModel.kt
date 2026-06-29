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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.base.search.channel.dto.SearchFilters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity 级共享状态：keyword、filters、当前选中 tab。
 * 4 个子 Fragment 通过 `activityViewModels()` 订阅同一份 state，输入框只在 Activity 顶部。
 *
 * keyword 输入做 300ms debounce 后通过 [queryEvents] 通知所有 Fragment 重置查询，
 * 与服务端 5 QPS / 20 burst 限流配合。
 */
class ChannelSearchViewModel : ViewModel() {

    data class State(
        val keyword: String = "",
        val filters: SearchFilters? = null,
        val tabIndex: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    /**
     * Debounce 之后的"重置查询"事件。replay=1 保证后绑定的 Fragment 也能立即拿到当前关键词。
     * 每个 Fragment 收到事件后清掉自己的 cursor + 列表，按 [state] 当前值发起新一轮请求。
     */
    private val _queryEvents = MutableSharedFlow<State>(replay = 1)
    val queryEvents: SharedFlow<State> = _queryEvents.asSharedFlow()

    private var debounceJob: Job? = null

    fun setKeyword(value: String) {
        if (_state.value.keyword == value) return
        _state.value = _state.value.copy(keyword = value)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _queryEvents.emit(_state.value)
        }
    }

    fun setFilters(value: SearchFilters?) {
        if (_state.value.filters == value) return
        _state.value = _state.value.copy(filters = value)
        viewModelScope.launch { _queryEvents.emit(_state.value) }
    }

    fun setTabIndex(index: Int) {
        if (_state.value.tabIndex == index) return
        _state.value = _state.value.copy(tabIndex = index)
    }

    /** Fragment 首次绑定 / Tab 首次进入时，主动取一次当前状态触发查询。 */
    fun replayCurrent() {
        viewModelScope.launch { _queryEvents.emit(_state.value) }
    }

    companion object {
        const val DEBOUNCE_MS = 300L
    }
}
