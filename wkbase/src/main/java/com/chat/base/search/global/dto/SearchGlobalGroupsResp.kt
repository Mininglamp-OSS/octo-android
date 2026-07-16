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

import com.chat.base.search.channel.dto.Pagination

/**
 * L1 响应。外层信封 `{data, pagination}` 与频道内搜索保持一致（复用 [Pagination]）。
 *
 * `pagination.has_more=true` 表示命中群数超过服务端配置的 `maxGroups`（默认 200），
 * 仅返回最活跃前 N 群；`next_cursor` 恒为 ""（L1 无逐条翻页）。
 */
class SearchGlobalGroupsResp {
    var data: GroupsResult = GroupsResult()
    var pagination: Pagination = Pagination()
}

class GroupsResult {
    /** 客户端 [SearchGlobalGroupsReq.sequence] 的原样回带。 */
    var sequence: Long = 0L
    var query_id: String = ""
    /** 命中群/子区/DM 总数，HLL cardinality 近似。 */
    var total_groups: Long = 0L
    var total_groups_approx: Boolean = true
    var groups: List<GroupBucket> = emptyList()
}
