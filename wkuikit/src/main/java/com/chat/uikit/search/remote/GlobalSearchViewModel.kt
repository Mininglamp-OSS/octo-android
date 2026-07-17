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
import com.chat.base.search.channel.uiAction
import com.chat.base.search.global.SearchGlobalGroupsModel
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.GroupBucket
import com.chat.base.search.global.dto.SearchGlobalGroupsReq
import com.chat.base.search.global.dto.SearchGlobalGroupsResp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局搜索"聊天记录聚合"段 (L1 `_search_global_groups`) 的 ViewModel。
 *
 * 职责：
 *  - 输入 keyword 做 300ms debounce，与服务端 5 QPS / 20 burst 限流配合
 *  - 递增 [sequence] 回带服务端，只渲染最新序号的响应（§4 竞态防护）
 *  - state 分发 loading / groups / error 三态，UI 层订阅
 *
 * 不做本地兜底：`FALLBACK_TO_LOCAL` 语义由 UI 层根据 [State.uiAction] 决定是否
 * 调用 `WKIM.msgManager.search()` 显示离线结果。ViewModel 保持不依赖 IMSDK，便于纯 JVM 单测。
 *
 * 联系人/群段的搜索走独立本地路径，不在此 ViewModel。
 */
class GlobalSearchViewModel(
    /** 注入服务端调用，测试时替换为 fake。 */
    private val searchCaller: SearchCaller = DefaultSearchCaller,
    private val debounceMillis: Long = DEBOUNCE_MS,
) : ViewModel() {

    /** UI 侧观察的完整状态。所有派生数据（分组数量等）都可从此对象取得。 */
    data class State(
        val keyword: String = "",
        val isLoading: Boolean = false,
        val groups: List<GroupBucket> = emptyList(),
        val totalGroups: Long = 0L,
        val totalGroupsApprox: Boolean = true,
        val hasMore: Boolean = false,
        /** 服务端结构化错误码；无错误时为 null。 */
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val retryAfterSec: Int = 0,
        /** 当前渲染中的响应对应的 sequence。0 表示还未发起过请求。 */
        val sequence: Long = 0L,
    ) {
        /** null 时 UI 无需响应；非 null 时按枚举决定 toast / banner / 本地兜底。 */
        fun uiAction(): com.chat.base.search.channel.ChannelSearchUiAction? =
            errorCode?.let {
                ChannelSearchOutcome.failure<Unit>(
                    httpStatus = 0,
                    errorCode = it,
                ).uiAction()
            }
    }

    /** ViewModel 内部对服务端的调用抽象，仅为可测试性存在。 */
    fun interface SearchCaller {
        fun call(
            req: SearchGlobalGroupsReq,
            callback: (ChannelSearchOutcome<SearchGlobalGroupsResp>) -> Unit,
        )
    }

    private object DefaultSearchCaller : SearchCaller {
        override fun call(
            req: SearchGlobalGroupsReq,
            callback: (ChannelSearchOutcome<SearchGlobalGroupsResp>) -> Unit,
        ) {
            SearchGlobalGroupsModel.searchGroups(req, callback)
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var debounceJob: Job? = null

    /** 单调递增的请求序号。用于识别乱序响应。 */
    private var nextSequence: Long = 0L

    /**
     * 关键词变更入口。空串立即清空结果并取消所有在途 debounce；
     * 非空串等 [debounceMillis] 之后触发一次搜索。
     */
    fun setKeyword(value: String) {
        if (_state.value.keyword == value) return
        _state.value = _state.value.copy(keyword = value)
        debounceJob?.cancel()
        if (value.isEmpty()) {
            // 清空回到初始态，但保留 nextSequence 单调性
            _state.value = State()
            return
        }
        debounceJob = viewModelScope.launch {
            delay(debounceMillis)
            triggerSearch(value)
        }
    }

    /**
     * 显式强制触发（用户点击键盘搜索键、外部调用等）。跳过 debounce，
     * 但仍会取消上一个 debounce job 保持串行。
     */
    fun triggerNow() {
        val keyword = _state.value.keyword
        if (keyword.isEmpty()) return
        debounceJob?.cancel()
        triggerSearch(keyword)
    }

    /** 释放 debounce job，供 Activity onDestroy 之外的显式清理。 */
    fun reset() {
        debounceJob?.cancel()
        _state.value = State()
    }

    private fun triggerSearch(keyword: String) {
        val mySeq = ++nextSequence
        _state.value = _state.value.copy(
            isLoading = true,
            errorCode = null,
            errorMessage = null,
            retryAfterSec = 0,
            sequence = mySeq,
        )
        val req = SearchGlobalGroupsReq(keyword = keyword, sequence = mySeq)
        searchCaller.call(req) { outcome -> handleResponse(mySeq, keyword, outcome) }
    }

    private fun handleResponse(
        reqSeq: Long,
        requestKeyword: String,
        outcome: ChannelSearchOutcome<SearchGlobalGroupsResp>,
    ) {
        // 乱序防护 1：本地已发出更新的请求 → 丢弃此响应
        if (reqSeq != nextSequence) return
        // 乱序防护 2：keyword 已被用户清空 → 丢弃
        if (_state.value.keyword.isEmpty()) return
        // 乱序防护 3：keyword 已变（debounce 窗口内响应到达但用户已改词）→ 丢弃
        // 场景：setKeyword("foo") → seq=1 在飞；用户改 "bar" → state.keyword 立即更新但
        // ++nextSequence 要等 debounce 300ms 后 triggerSearch。foo 响应在此窗口回来时，
        // reqSeq(1)==nextSequence(1) 通过，此处 requestKeyword("foo")!=state.keyword("bar") → 丢弃。
        if (_state.value.keyword != requestKeyword) return

        if (outcome.ok) {
            val body = outcome.data ?: return
            val data = body.data
            // 乱序防护 3：服务端 echo 回的 sequence 与本次请求不匹配（理论上不应发生，防御性检查）
            if (data.sequence != reqSeq) return
            _state.value = _state.value.copy(
                isLoading = false,
                groups = data.groups,
                totalGroups = data.total_groups,
                totalGroupsApprox = data.total_groups_approx,
                hasMore = body.pagination.has_more,
                errorCode = null,
                errorMessage = null,
                retryAfterSec = 0,
            )
        } else {
            _state.value = _state.value.copy(
                isLoading = false,
                groups = emptyList(),
                totalGroups = 0L,
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

/** filters 触发门辅助：目前 [GlobalSearchViewModel] 只用 keyword 场景，此处保留兼容占位。 */
internal fun GlobalSearchFilters?.orEmptyForL1(): GlobalSearchFilters =
    this ?: GlobalSearchFilters()
