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
 * L1 请求。对应 `POST /v1/messages/_search_global_groups`。
 *
 * - [keyword] ≤ 64 rune；见 [GlobalSearchFilters.canTriggerL1] 触发门。
 * - [sequence] 前端自增，服务端原样回带，UI 层用于丢弃过期响应（§4 竞态防护）。
 * - 无 sort / page_size / cursor：L1 一次返回聚合总览，桶固定按 latest_at 倒序，
 *   preview 条数由服务端 config 分配（`previewBudget=500` / `perGroupMax=20`）。
 */
class SearchGlobalGroupsReq(
    val keyword: String? = null,
    val sequence: Long = 0L,
    val filters: GlobalSearchFilters? = null,
) {
    companion object {
        const val MAX_KEYWORD_RUNES = 64
    }
}
