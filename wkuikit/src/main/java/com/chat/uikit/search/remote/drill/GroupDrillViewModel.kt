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

package com.chat.uikit.search.remote.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.base.search.channel.ChannelSearchOutcome
import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.uiAction
import com.chat.base.search.global.SearchGlobalMessagesModel
import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.SearchGlobalMessagesReq
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * L2 深度浏览页 ViewModel。对应 `POST /v1/messages/_search_global_messages`。
 *
 * 职责：
 *  - 关键词输入 300ms debounce
 *  - 递增 [nextSequence] 用于新查询乱序防护（reset 递增；loadMore 不递增）
 *  - cursor 分页：首屏用 null cursor，翻页用 [State.nextCursor]
 *  - state 分发 loading / hits / hasMore / error 供 UI 订阅
 *
 * 服务端契约（详见 `04-aggregation-api-spec.md` §3）：
 *  - 传群 channel_id（type=2）自动展开群+子区所有可见命中
 *  - 传子区 channel_id（type=5）只搜该子区
 *  - 传 DM peer uid（type=1）搜该 DM
 *  - 返回 SearchAllHit 混排（message + file）+ 独立 cursor
 *
 * 不做本地兜底：`FALLBACK_TO_LOCAL` 场景由 UI 层根据 [State.uiAction] 决定
 * （L2 场景下本地几乎必空，一般也只能空态提示）。
 */
class GroupDrillViewModel(
    private val searchCaller: SearchCaller = DefaultSearchCaller,
    private val debounceMillis: Long = DEBOUNCE_MS,
) : ViewModel() {

    /** UI 订阅的完整状态。 */
    data class State(
        val keyword: String = "",
        /** 首屏加载中（keyword 变化触发的重置查询）。 */
        val isLoading: Boolean = false,
        /** 分页加载中。 */
        val isLoadingMore: Boolean = false,
        val hits: List<CombinedHit> = emptyList(),
        val hasMore: Boolean = false,
        val nextCursor: String? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val retryAfterSec: Int = 0,
        val sequence: Long = 0L,
    ) {
        /** null 时 UI 无需响应；非 null 时按枚举决定 toast / banner / 兜底。 */
        fun uiAction(): ChannelSearchUiAction? =
            errorCode?.let {
                ChannelSearchOutcome.failure<Unit>(
                    httpStatus = 0,
                    errorCode = it,
                ).uiAction()
            }
    }

    fun interface SearchCaller {
        fun call(
            req: SearchGlobalMessagesReq,
            callback: (ChannelSearchOutcome<CursorList<CombinedHit>>) -> Unit,
        )
    }

    private object DefaultSearchCaller : SearchCaller {
        override fun call(
            req: SearchGlobalMessagesReq,
            callback: (ChannelSearchOutcome<CursorList<CombinedHit>>) -> Unit,
        ) {
            SearchGlobalMessagesModel.searchMessages(req, callback)
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var debounceJob: Job? = null

    /** 单调递增的请求序号；只在 reset（新 keyword）时递增，loadMore 复用当前序号。 */
    private var nextSequence: Long = 0L

    /** 目标 channel（来自 GlobalActivity 的桶点击）。init 后不变。 */
    private var targetChannel: GlobalChannelRef? = null

    /**
     * Activity onCreate 时调用一次。触发首屏搜索（若 [initialKeyword] 非空）。
     */
    fun init(channelId: String, channelType: Byte, initialKeyword: String) {
        targetChannel = GlobalChannelRef(channelId, channelType)
        if (initialKeyword.isEmpty()) {
            _state.value = State(keyword = "")
            return
        }
        _state.value = State(keyword = initialKeyword)
        // 立即触发首屏（跳过 debounce），保证从 L1 点进来无空窗
        triggerSearch(initialKeyword, cursor = null, isReset = true)
    }

    /** 关键词变更；300ms debounce 后触发新查询，重置 cursor + hits。 */
    fun setKeyword(value: String) {
        if (_state.value.keyword == value) return
        _state.value = _state.value.copy(keyword = value)
        debounceJob?.cancel()
        if (value.isEmpty()) {
            _state.value = State(keyword = "")
            return
        }
        debounceJob = viewModelScope.launch {
            delay(debounceMillis)
            triggerSearch(value, cursor = null, isReset = true)
        }
    }

    /** 上滑触底加载更多。有 hasMore 且非 loading 时才执行。 */
    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.isLoading || s.isLoadingMore || s.keyword.isEmpty()) return
        val cursor = s.nextCursor ?: return
        triggerSearch(s.keyword, cursor = cursor, isReset = false)
    }

    fun reset() {
        debounceJob?.cancel()
        _state.value = State()
    }

    private fun triggerSearch(keyword: String, cursor: String?, isReset: Boolean) {
        val target = targetChannel ?: return
        val seq = if (isReset) ++nextSequence else nextSequence
        _state.value = _state.value.copy(
            isLoading = isReset,
            isLoadingMore = !isReset,
            errorCode = null,
            errorMessage = null,
            retryAfterSec = 0,
            sequence = seq,
        )
        val req = SearchGlobalMessagesReq(
            keyword = keyword,
            filters = GlobalSearchFilters(channelIds = listOf(target)),
            sort = SearchGlobalMessagesReq.SORT_TIME_DESC,
            pageSize = PAGE_SIZE,
            cursor = cursor,
            sequence = seq,
        )
        searchCaller.call(req) { outcome -> handleResponse(seq, isReset, outcome) }
    }

    private fun handleResponse(
        reqSeq: Long,
        isReset: Boolean,
        outcome: ChannelSearchOutcome<CursorList<CombinedHit>>,
    ) {
        // 乱序防护：本地已发出更新的请求 → 丢弃此响应（reset 递增 sequence 会覆盖 loadMore）
        if (reqSeq != nextSequence) return
        // keyword 已被清空 → 丢弃迟到响应
        if (_state.value.keyword.isEmpty()) return

        if (outcome.ok) {
            val body = outcome.data ?: return
            val newHits = if (isReset) body.data else _state.value.hits + body.data
            _state.value = _state.value.copy(
                isLoading = false,
                isLoadingMore = false,
                hits = newHits,
                hasMore = body.pagination.has_more,
                nextCursor = body.pagination.next_cursor.takeIf { it.isNotEmpty() },
                errorCode = null,
                errorMessage = null,
                retryAfterSec = 0,
            )
        } else {
            // 失败：reset 场景清空 hits；loadMore 场景保留已加载的
            _state.value = _state.value.copy(
                isLoading = false,
                isLoadingMore = false,
                hits = if (isReset) emptyList() else _state.value.hits,
                hasMore = if (isReset) false else _state.value.hasMore,
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
        const val PAGE_SIZE = 20
    }
}
