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
 * 结构对称 [GlobalSearchViewModel] 与 [com.chat.uikit.search.remote.drill.GroupDrillViewModel]：
 *  - keyword debounce + 自增 sequence 防竞态
 *  - cursor 分页：首屏 cursor = null，翻页用 [State.nextCursor]（见 [loadMore]）
 *  - 没有本地兜底数据源——文件搜索失败时无法像消息那样回退到 IMSDK 本地聚合，
 *    任何失败都落 UI 的 Error 态（带重试），不产生本地结果列表。
 *
 * **无关键词不搜索**：关键词为空时不发请求、不返回任何结果，筛选条件只用于收窄
 * 关键词搜索的范围（见 [shouldRunSearch]）。请求体层面支持省略 keyword 的浏览态，
 * 但客户端刻意不暴露该能力。
 */
class FilesSearchViewModel(
    private val searchCaller: SearchCaller = DefaultSearchCaller,
    private val debounceMillis: Long = DEBOUNCE_MS,
) : ViewModel() {

    data class State(
        val keyword: String = "",
        val isLoading: Boolean = false,
        /** 翻页中（已有首屏结果，正在追加下一页）。与 [isLoading] 区分，避免翻页时整屏转圈。 */
        val isLoadingMore: Boolean = false,
        val files: List<FileHit> = emptyList(),
        val hasMore: Boolean = false,
        /** 服务端签发的下一页游标；null 表示没有下一页。 */
        val nextCursor: String? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val retryAfterSec: Int = 0,
        val sequence: Long = 0L,
        /**
         * 是否已发起过至少一次请求（含在途）。用于 UI 区分「空输入等待」与「搜到了但没结果」。
         * 不能用 isLoading 代替：debounce 窗口内 isLoading 为 true 但请求还没发出；
         * 也不能用 keyword 是否为空代替，那会把门禁规则复制到 UI 层。
         */
        val queryStarted: Boolean = false,
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

    /**
     * 更新筛选条件并立刻重搜（由调用方随后显式 [triggerNow]）。
     * 仅更新内部状态，不自动触发——与 [setKeyword] 的 debounce 时机解耦。
     */
    fun setFilters(filters: GlobalSearchFilters) {
        currentFilters = filters
    }

    /**
     * 搜索门禁：**必须有关键词**。
     *
     * 这是产品上的明确决定：不输入关键词时不返回任何结果，筛选条件只用于收窄关键词搜索的
     * 结果范围，不能单独作为查询条件。
     *
     * 服务端层面「浏览态」是支持的（`_search_global_files` 的 keyword 可空，
     * [SearchGlobalFilesBodyBuilder] 也会在 keyword 为空时省略该字段），
     * 但客户端刻意不暴露这个能力——按文件类型或日期范围无界浏览整个工作区的历史文件，
     * 结果量大且对用户无实际意义。因此这里只认关键词，不看 filters。
     *
     * 若后续要放开浏览态，改这一处即可：加上 `|| !currentFilters.isEmpty()`，
     * 同时需要让 [GlobalActivity] 的空态不再把"未发起搜索"等同于"请输入关键词"。
     */
    private fun shouldRunSearch(keyword: String): Boolean = keyword.isNotEmpty()

    fun setKeyword(value: String) {
        if (_state.value.keyword == value) return
        debounceJob?.cancel()
        // 关键词一变立即清空上一轮结果与分页游标，避免 debounce 窗口内旧结果渲染在新关键词之下。
        _state.value = _state.value.copy(
            keyword = value,
            isLoading = true,
            isLoadingMore = false,
            files = emptyList(),
            hasMore = false,
            nextCursor = null,
            errorCode = null,
            errorMessage = null,
            retryAfterSec = 0,
            queryStarted = true,
        )
        // 关键词为空 → 回到初始态，不发请求（见 [shouldRunSearch]：无关键词不返回任何结果）。
        if (!shouldRunSearch(value)) {
            _state.value = State()
            return
        }
        debounceJob = viewModelScope.launch {
            delay(debounceMillis)
            triggerSearch(value)
        }
    }

    /**
     * 显式强制触发：筛选"应用"、键盘搜索键、错误态重试等场景，跳过 debounce。
     * 无关键词时不发请求——筛选"应用"若在空搜索框下点击，等同于只记下条件、不出结果。
     */
    fun triggerNow() {
        val keyword = _state.value.keyword
        debounceJob?.cancel()
        if (!shouldRunSearch(keyword)) {
            // 无关键词 → 回到空输入态，并清掉可能残留的结果/错误
            _state.value = State()
            return
        }
        triggerSearch(keyword)
    }

    /**
     * 上滑触底加载下一页。仅在已有下一页游标、且无其它请求在途时执行。
     * 翻页结果**追加**到现有列表，不替换。
     */
    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.isLoading || s.isLoadingMore) return
        val cursor = s.nextCursor?.takeIf { it.isNotEmpty() } ?: return
        val mySeq = ++nextSequence
        _state.value = s.copy(isLoadingMore = true, sequence = mySeq)
        val req = SearchGlobalFilesReq(
            keyword = s.keyword.takeIf { it.isNotEmpty() },
            filters = currentFilters,
            cursor = cursor,
            sequence = mySeq,
        )
        searchCaller.call(req) { outcome -> handleResponse(mySeq, s.keyword, outcome, isAppend = true) }
    }

    private fun triggerSearch(keyword: String) {
        val mySeq = ++nextSequence
        _state.value = _state.value.copy(
            isLoading = true,
            isLoadingMore = false,
            // 新一轮请求发起即清空旧结果与游标，原因见 GlobalSearchViewModel.triggerSearch 的同类注释。
            files = emptyList(),
            hasMore = false,
            nextCursor = null,
            errorCode = null,
            errorMessage = null,
            retryAfterSec = 0,
            sequence = mySeq,
            queryStarted = true,
        )
        val req = SearchGlobalFilesReq(
            // keyword 恒非空（见 shouldRunSearch 门禁）；仍走 takeIf 以保持与 body builder
            // "空则省略"契约一致，避免门禁放开后这里漏改发出 "keyword": ""。
            keyword = keyword.takeIf { it.isNotEmpty() },
            filters = currentFilters,
            sequence = mySeq,
        )
        searchCaller.call(req) { outcome -> handleResponse(mySeq, keyword, outcome) }
    }

    private fun handleResponse(
        reqSeq: Long,
        requestKeyword: String,
        outcome: ChannelSearchOutcome<CursorList<FileHit>>,
        isAppend: Boolean = false,
    ) {
        // 乱序防护：已发出更新的请求 → 丢弃此响应
        if (reqSeq != nextSequence) return
        // keyword 已变 → 丢弃（用相等判定而非判空，门禁放开后空串对空串也能正确匹配）
        if (_state.value.keyword != requestKeyword) return

        if (outcome.ok) {
            val body = outcome.data ?: return
            val nextCursor = body.pagination.next_cursor.takeIf { it.isNotEmpty() }
            // 服务端回传同一个游标时强制停止翻页，避免无限循环拉同一页。
            val cursorStalled = isAppend && nextCursor != null && nextCursor == _state.value.nextCursor
            _state.value = _state.value.copy(
                isLoading = false,
                isLoadingMore = false,
                files = if (isAppend) _state.value.files + body.data else body.data,
                hasMore = if (cursorStalled) false else body.pagination.has_more,
                nextCursor = if (cursorStalled) null else nextCursor,
                errorCode = null,
                errorMessage = null,
                retryAfterSec = 0,
            )
        } else {
            // 翻页失败：保留已加载的页，并**保留游标与 hasMore**，让用户再次上滑即可重试这一页
            // （UI 侧 observeFilesTabState 会照常渲染已有结果，错误用 toast 提示）。
            // 若此处把 hasMore/nextCursor 清掉，翻页会永久停死，只能重新搜索才能继续。
            // 首屏失败则清空列表与游标，由空态接管展示「加载失败 + 重试」。
            _state.value = _state.value.copy(
                isLoading = false,
                isLoadingMore = false,
                files = if (isAppend) _state.value.files else emptyList(),
                hasMore = if (isAppend) _state.value.hasMore else false,
                nextCursor = if (isAppend) _state.value.nextCursor else null,
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
