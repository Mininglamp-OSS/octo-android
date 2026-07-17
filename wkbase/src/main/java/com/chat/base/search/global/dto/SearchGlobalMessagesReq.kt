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

package com.chat.base.search.global.dto

/**
 * L2 请求。对应 `POST /v1/messages/_search_global_messages`。
 *
 * 契约与频道内 `_search` / `_search_all` 一致（详见 `04-aggregation-api-spec.md` §3 与
 * `global-search-api.md`）：
 *  - [keyword] ≤ 64 rune；服务端做验证。
 *  - [filters] 必须包含 `channel_ids`（选群/子区/DM），传群 id 服务端会自动展开群+子区。
 *  - [sort] 支持 `time_desc` / `time_asc` / `relevance`（仅在有 keyword 时启用）。
 *  - [pageSize] ∈ [1, 100]，默认 20。
 *  - [cursor] 服务端 HMAC 签名的不透明字符串，翻页时原样回传；首次请求为空。
 *  - [sequence] 前端自增，服务端**不解析**（L2 不做 echo 回带，客户端仅在 UI 层用于本地竞态）。
 */
class SearchGlobalMessagesReq(
    val keyword: String? = null,
    val filters: GlobalSearchFilters,
    val sort: String = SORT_TIME_DESC,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null,
    val sequence: Long = 0L,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_KEYWORD_RUNES = 64

        const val SORT_TIME_DESC = "time_desc"
        const val SORT_TIME_ASC = "time_asc"
        const val SORT_RELEVANCE = "relevance"
    }
}
